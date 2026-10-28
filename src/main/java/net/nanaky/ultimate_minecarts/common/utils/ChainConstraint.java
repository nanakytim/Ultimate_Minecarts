package net.nanaky.ultimate_minecarts.common.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.Linkable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ChainConstraint {
    private static final double MAX_PULL = 0.5;
    private static final double MIN_ALIGNMENT = 0.95;
    private static final double Y_TOLERANCE = 0.05;
    private static final double MAX_RESEAT_DIST_SQR = 36.0;
    private static final double REVERSE_COS = -0.5;
    private static final long PUSH_GRACE_TICKS = 10;
    private static final List<AbstractMinecart> HEADS = new ArrayList<>();
    private static final Map<AbstractMinecart, Long> PUSHED = new WeakHashMap<>();
    private static final Map<AbstractMinecart, Seat> SEATS = new WeakHashMap<>();
    private record Seat(Vec3 pos, Vec3 vel, BlockPos rail) {}

    private ChainConstraint() {}

    public static void queueHead(AbstractMinecart head) {
        HEADS.add(head);
    }

    public static void markPushed(AbstractMinecart cart, long gameTime) {
        PUSHED.put(cart, gameTime);
    }

    private static boolean pushedRecently(AbstractMinecart cart, long now) {
        Long t = PUSHED.get(cart);
        return t != null && now - t <= PUSH_GRACE_TICKS;
    }


    public static void solve() {
        for (AbstractMinecart head : HEADS) {
            if (head.isRemoved()) continue;
            AbstractMinecart parent = head;
            AbstractMinecart child = ((Linkable) head).getLinkedChild();
            int depth = 0;
            while (child != null && !child.isRemoved() && depth++ < 64) {
                enforce(parent, child);
                parent = child;
                child = ((Linkable) child).getLinkedChild();
            }
        }
        HEADS.clear();
    }

    private static void enforce(AbstractMinecart parent, AbstractMinecart child) {
        Level level = child.level();
        if (parent.level() != level) return;

        keepOnRails(level, parent, child);
        pinToRail(level, parent, child);
        forbidReverse(level, parent, child);
        stretch(level, parent, child);
        remember(level, child);
    }

    private static BlockPos railAt(Level level, Vec3 p) {
        BlockPos at = BlockPos.containing(p);
        if (BaseRailBlock.isRail(level.getBlockState(at))) return at;
        BlockPos below = at.below();
        if (BaseRailBlock.isRail(level.getBlockState(below))) return below;
        return null;
    }

    private static BlockPos findRail(Level level, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        if (BaseRailBlock.isRail(level.getBlockState(at))) return at;
        BlockPos below = at.below();
        if (BaseRailBlock.isRail(level.getBlockState(below))) return below;
        BlockPos above = at.above();
        if (BaseRailBlock.isRail(level.getBlockState(above))) return above;
        return null;
    }

    private static double railHeight(Level level, BlockPos rail, Vec3 p) {
        BlockState state = level.getBlockState(rail);
        if (!(state.getBlock() instanceof BaseRailBlock block)) return rail.getY();
        RailShape shape = state.getValue(block.getShapeProperty());
        double lx = Mth.clamp(p.x - rail.getX(), 0.0, 1.0);
        double lz = Mth.clamp(p.z - rail.getZ(), 0.0, 1.0);
        double t = switch (shape) {
            case ASCENDING_EAST -> lx;
            case ASCENDING_WEST -> 1.0 - lx;
            case ASCENDING_SOUTH -> lz;
            case ASCENDING_NORTH -> 1.0 - lz;
            default -> 0.0;
        };
        return rail.getY() + t;
    }

    private static double seatedBase(Level level, AbstractMinecart cart) {
        BlockPos rail = railAt(level, cart.position());
        if (rail == null) return Double.NaN;
        return cart.getY() - railHeight(level, rail, cart.position());
    }

    private static boolean validBase(double base) {
        return !Double.isNaN(base) && base >= -0.01 && base <= 0.3;
    }

    private static void keepOnRails(Level level, AbstractMinecart parent, AbstractMinecart child) {
        if (railAt(level, child.position()) != null) return;
        Seat seat = SEATS.get(child);
        if (seat == null) return;
        if (railAt(level, parent.position()) == null) return;
        if (!BaseRailBlock.isRail(level.getBlockState(seat.rail()))) return;
        if (child.position().distanceToSqr(seat.pos()) > MAX_RESEAT_DIST_SQR) return;

        child.setPos(seat.pos());
        child.setDeltaMovement(seat.vel().x, 0.0, seat.vel().z);
        child.resetFallDistance();
    }

    private static void pinToRail(Level level, AbstractMinecart parent, AbstractMinecart child) {
        double base = seatedBase(level, parent);
        if (!validBase(base)) return;

        Vec3 p = child.position();
        BlockPos rail = railAt(level, p);
        if (rail == null) return;

        double y = railHeight(level, rail, p) + base;
        if (Math.abs(p.y - y) <= Y_TOLERANCE) return;

        child.setPos(p.x, y, p.z);
        Vec3 v = child.getDeltaMovement();
        child.setDeltaMovement(v.x, 0.0, v.z);
        child.resetFallDistance();
    }

    private static void remember(Level level, AbstractMinecart child) {
        BlockPos rail = railAt(level, child.position());
        if (rail != null) SEATS.put(child, new Seat(child.position(), child.getDeltaMovement(), rail));
    }

    private static void forbidReverse(Level level, AbstractMinecart parent, AbstractMinecart child) {
        if (pushedRecently(child, level.getGameTime())) return;

        Vec3 toParent = parent.position().subtract(child.position());
        double h = toParent.horizontalDistance();
        if (h < 1e-4) return;

        Vec3 cv = child.getDeltaMovement();
        double cs = cv.horizontalDistance();
        if (cs < 1e-4) return;

        double cos = (cv.x * toParent.x + cv.z * toParent.z) / (cs * h);
        if (cos >= REVERSE_COS) return;

        child.setDeltaMovement(0.0, cv.y, 0.0);
    }

    private static void stretch(Level level, AbstractMinecart parent, AbstractMinecart child) {
        UltimateMinecartsConfig cfg = UltimateMinecartsConfig.get();
        String path = BuiltInRegistries.ITEM.getKey(((Linkable) child).getLinkedChain()).getPath();
        float target = UltimateMinecartsConfig.getChainDistance(path);

        Vec3 delta = child.position().subtract(parent.position());
        double h = delta.horizontalDistance();
        if (h < 1e-4) return;
        double stretch = h - 1.0;
        if (stretch <= target) return;
        if (stretch > target + cfg.chainResistance) return;

        Vec3 toParent = new Vec3(-delta.x / h, 0.0, -delta.z / h);
        Vec3 cv = child.getDeltaMovement();
        double cs = cv.horizontalDistance();

        pull(level, parent, child, toParent, cv, cs, Math.min(Math.min(stretch - target, h), MAX_PULL));
        matchSpeed(parent, child, cv, cs);
    }

    private static void pull(Level level, AbstractMinecart parent, AbstractMinecart child,
                             Vec3 toParent, Vec3 cv, double cs, double dist) {
        if (dist <= 1e-4) return;
        if (cs < 1e-3) return;
        if ((cv.x * toParent.x + cv.z * toParent.z) / cs < MIN_ALIGNMENT) return;

        double base = seatedBase(level, parent);
        if (!validBase(base)) return;

        Vec3 cur = child.position();
        double nx = cur.x + toParent.x * dist;
        double nz = cur.z + toParent.z * dist;

        BlockPos rail = findRail(level, nx, cur.y, nz);
        if (rail == null) return;

        double ny = railHeight(level, rail, new Vec3(nx, cur.y, nz)) + base;
        Vec3 move = new Vec3(nx - cur.x, ny - cur.y, nz - cur.z);
        if (!level.noCollision(child, child.getBoundingBox().move(move))) return;

        child.setPos(nx, ny, nz);
    }

    private static void matchSpeed(AbstractMinecart parent, AbstractMinecart child, Vec3 cv, double cs) {
        Vec3 pv = parent.getDeltaMovement();
        double ps = pv.horizontalDistance();
        if (cs <= 1e-5 || cs >= ps) return;
        if (cv.x * pv.x + cv.z * pv.z <= 0.0) return;
        double s = ps / cs;
        child.setDeltaMovement(cv.x * s, cv.y, cv.z * s);
    }
}