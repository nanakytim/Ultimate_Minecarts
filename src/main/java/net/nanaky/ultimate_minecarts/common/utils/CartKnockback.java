package net.nanaky.ultimate_minecarts.common.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.mixin.AbstractMinecartInvoker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class CartKnockback {
    private static final double CART_SHARE = 0.5;
    private static final long COOLDOWN_TICKS = 5;
    private static final long HOLD_TICKS = 5;
    private static final double MIN_CLOSING = 0.02;
    private static final double APPROACH_EPS = 0.01;
    private static final Map<AbstractMinecart, Long> LAST = new WeakHashMap<>();
    private static final Map<AbstractMinecart, Long> HELD = new WeakHashMap<>();
    private CartKnockback() {}
    public static double livingKnockSpeed(double speedBPS, int attachedCarts) {
        return (3.0 + speedBPS) * (0.25 + Math.log1p(attachedCarts) * 0.15);
    }

    public static boolean overridesVanilla(AbstractMinecart a, AbstractMinecart b) {
        AbstractMinecart ra = root(a);
        AbstractMinecart rb = root(b);
        if (ra == rb) return false;
        return unstoppable(ra) || unstoppable(rb) || isTrain(a) || isTrain(b);
    }

    public static boolean isHeld(AbstractMinecart cart, long now) {
        Long until = HELD.get(cart);
        return until != null && now < until;
    }

    public static void resolve(ServerLevel level, AbstractMinecart self, AbstractMinecart other) {
        AbstractMinecart rs = root(self);
        AbstractMinecart ro = root(other);
        if (rs == ro) return;

        boolean su = unstoppable(rs);
        boolean ou = unstoppable(ro);
        if (!su && !ou && !isTrain(self) && !isTrain(other)) return;

        long now = level.getGameTime();
        if (recent(rs, now) || recent(ro, now)) return;

        Vec3 n = horizontal(other.position().subtract(self.position()));
        if (n == null) return;

        Vec3 vs = velocity(self);
        Vec3 vo = velocity(other);
        double selfClose = vs.x * n.x + vs.z * n.z;
        double otherClose = -(vo.x * n.x + vo.z * n.z);
        if (selfClose + otherClose < MIN_CLOSING) return;

        LAST.put(rs, now);
        LAST.put(ro, now);

        if (su && ou) {
            halt(rs, now, true);
            halt(ro, now, true);
            return;
        }

        if (su || ou) {
            AbstractMinecart winnerRoot = su ? rs : ro;
            AbstractMinecart loserRoot = su ? ro : rs;
            Vec3 wv = su ? vs : vo;
            double loserClose = su ? otherClose : selfClose;
            Vec3 toLoser = su ? n : n.scale(-1.0);

            if (loserClose > APPROACH_EPS) halt(loserRoot, now, false);
            knock(level, winnerRoot, loserRoot, travelDir(wv, toLoser), wv.horizontalDistance());
            return;
        }

        boolean selfHits = selfClose >= otherClose;
        AbstractMinecart hitterRoot = selfHits ? rs : ro;
        AbstractMinecart targetRoot = selfHits ? ro : rs;
        Vec3 hv = selfHits ? vs : vo;
        double targetClose = selfHits ? otherClose : selfClose;
        Vec3 toTarget = selfHits ? n : n.scale(-1.0);

        halt(hitterRoot, now, false);
        if (targetClose > APPROACH_EPS) {
            halt(targetRoot, now, false);
        } else {
            knock(level, hitterRoot, targetRoot, travelDir(hv, toTarget), hv.horizontalDistance());
        }
    }

    private static void knock(ServerLevel level, AbstractMinecart sourceRoot, AbstractMinecart targetRoot,
                              Vec3 dir, double sourceSpeed) {
        int attached = chainOf(sourceRoot).size() - 1;
        double magnitude = Math.min(sourceSpeed,
                CART_SHARE * livingKnockSpeed(sourceSpeed * 20.0, attached) / 20.0);
        if (magnitude < 1e-4) return;

        Vec3 impulse = dir.scale(magnitude);
        for (AbstractMinecart cart : chainOf(targetRoot)) {
            push(level, cart, impulse);
        }
    }

    private static void halt(AbstractMinecart root, long now, boolean hold) {
        for (AbstractMinecart cart : chainOf(root)) {
            Vec3 v = cart.getDeltaMovement();
            cart.setDeltaMovement(0.0, v.y, 0.0);
            if (hold) HELD.put(cart, now + HOLD_TICKS);
        }
    }

    private static void push(ServerLevel level, AbstractMinecart cart, Vec3 impulse) {
        if (cart.isRemoved() || !cart.isOnRails()) return;

        Vec3 axis = railAxis(cart);
        if (axis == null) return;

        double along = impulse.x * axis.x + impulse.z * axis.z;
        if (Math.abs(along) < 1e-4) return;

        Vec3 v = cart.getDeltaMovement();
        double nx = v.x + axis.x * along;
        double nz = v.z + axis.z * along;

        double max = ((AbstractMinecartInvoker) (Object) cart).invokeGetMaxSpeed(level);
        double h = Math.hypot(nx, nz);
        if (h > max && h > 1e-7) {
            double s = max / h;
            nx *= s;
            nz *= s;
        }
        ChainConstraint.markPushed(cart, level.getGameTime());
        cart.setDeltaMovement(nx, v.y, nz);
    }

    private static Vec3 railAxis(AbstractMinecart cart) {
        BlockPos pos = cart.getCurrentBlockPosOrRailBelow();
        BlockState state = cart.level().getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock rail)) return null;

        RailShape shape = state.getValue(rail.getShapeProperty());
        var exits = AbstractMinecart.exits(shape);
        Vec3i a = exits.getFirst();
        Vec3i b = exits.getSecond();
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return null;
        return new Vec3(dx / len, 0.0, dz / len);
    }

    private static Vec3 velocity(AbstractMinecart cart) {
        return cart instanceof MinecartPhysicsAccess p
                ? p.ultimate_minecarts$getPreTickVelocity()
                : cart.getDeltaMovement();
    }

    private static Vec3 horizontal(Vec3 v) {
        double h = Math.hypot(v.x, v.z);
        return h < 1e-4 ? null : new Vec3(v.x / h, 0.0, v.z / h);
    }

    private static Vec3 travelDir(Vec3 velocity, Vec3 fallback) {
        Vec3 h = horizontal(velocity);
        return h != null ? h : fallback;
    }

    private static boolean recent(AbstractMinecart root, long now) {
        Long last = LAST.get(root);
        return last != null && now - last < COOLDOWN_TICKS;
    }

    private static boolean isTrain(AbstractMinecart cart) {
        Linkable l = (Linkable) cart;
        return l.getLinkedParent() != null || l.getLinkedChild() != null;
    }

    private static boolean unstoppable(AbstractMinecart root) {
        return root instanceof MinecartFurnace
                && root instanceof IFurnaceMinecart f
                && f.ultimate_minecarts$getFuel() > 0;
    }

    private static AbstractMinecart root(AbstractMinecart cart) {
        AbstractMinecart cursor = cart;
        AbstractMinecart up;
        int depth = 0;
        while (depth++ < 64 && (up = ((Linkable) cursor).getLinkedParent()) != null) cursor = up;
        return cursor;
    }

    private static List<AbstractMinecart> chainOf(AbstractMinecart root) {
        List<AbstractMinecart> chain = new ArrayList<>();
        AbstractMinecart cursor = root;
        int depth = 0;
        while (cursor != null && depth++ < 64) {
            chain.add(cursor);
            cursor = ((Linkable) cursor).getLinkedChild();
        }
        return chain;
    }
}