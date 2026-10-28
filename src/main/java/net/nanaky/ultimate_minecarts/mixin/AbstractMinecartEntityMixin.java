package net.nanaky.ultimate_minecarts.mixin;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncChainedMinecartPacket;
import net.nanaky.ultimate_minecarts.common.utils.CartKnockback;
import net.nanaky.ultimate_minecarts.common.utils.ChainConstraint;
import net.nanaky.ultimate_minecarts.common.utils.DismountGrace;
import net.nanaky.ultimate_minecarts.common.utils.HopperFeed;
import net.nanaky.ultimate_minecarts.common.utils.MinecartPhysicsAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.entity.vehicle.DismountHelper;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.UUID;


@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartEntityMixin extends Entity
        implements Linkable, MinecartPhysicsAccess {

    @Unique private @Nullable UUID parentUuid;
    @Unique private @Nullable UUID childUuid;
    @Unique private int parentIdClient = -1;
    @Unique private int childIdClient  = -1;
    @Unique private boolean isMovingOnRail;

    @Unique private @Nullable UUID pendingParentUuid;
    @Unique private @Nullable UUID pendingChildUuid;
    @Unique private @Nullable String pendingChainPath;

    @Unique private Item ultimate_minecarts$linkedChain = Items.IRON_CHAIN;

    @Unique private double ultimate_minecarts$preTick_speedBPS = 0.0;
    @Unique private Vec3 ultimate_minecarts$preTick_velocity = Vec3.ZERO;

    @Unique private int ultimate_minecarts$graceTicks = 0;
    @Unique private boolean ultimate_minecarts$hadPassenger = false;

    @Unique private @Nullable AABB ultimate_minecarts$preTick_box = null;
    @Unique private final ArrayDeque<Vec3> ultimate_minecarts$tailHistory = new ArrayDeque<>();
    @Unique private static final int ultimate_minecarts$WINDOW_TICKS = 5;
    @Unique private static final double ultimate_minecarts$MIN_DAMAGE_BPS = 8.0;

    @Unique private long ultimate_minecarts$lastTick = -1L;
    @Unique private java.util.List<UUID> ultimate_minecarts$lastPassengers = java.util.List.of();

    @Unique private @Nullable AbstractMinecart ultimate_minecarts$lastParent = null;
    @Unique private final ArrayDeque<Vec3> ultimate_minecarts$momentum = new ArrayDeque<>();
    @Unique private static final int ultimate_minecarts$MOMENTUM_TICKS = 15;

    @Override
    public void setLinkedChain(Item chain) { this.ultimate_minecarts$linkedChain = chain; }

    @Override
    public Item getLinkedChain() { return ultimate_minecarts$linkedChain; }

    @Override
    public long ultimate_minecarts$getLastTick() { return ultimate_minecarts$lastTick; }

    @Override
    public Vec3 ultimate_minecarts$getPreTickVelocity() { return ultimate_minecarts$preTick_velocity; }

    public AbstractMinecartEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public void ultimate_minecarts$startGrace() {
        ultimate_minecarts$graceTicks = 40;
    }

    @Inject(method = "useExperimentalMovement", at = @At("HEAD"), cancellable = true)
    private static void ultimate_minecarts$alwaysNewMovement(Level level, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void ultimate_minecarts$noPushLinked(Entity other, CallbackInfo ci) {
        if (isLinkedTo(other)
                || (other instanceof AbstractMinecart cart
                    && CartKnockback.overridesVanilla((AbstractMinecart) (Object) this, cart))) {
            ci.cancel();
        }
    }

    @Inject(method = "moveAlongTrack", at = @At("HEAD"), cancellable = true)
    private void ultimate_minecarts$isMovingOnRail(ServerLevel level, CallbackInfo info) {
        isMovingOnRail = true;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ultimate_minecarts$checkDismount(CallbackInfo ci) {
        if (!(level() instanceof ServerLevel sl)) return;
        var current = getPassengers();
        if (!ultimate_minecarts$lastPassengers.isEmpty() || !current.isEmpty()) {
            for (UUID id : ultimate_minecarts$lastPassengers) {
                if (current.stream().noneMatch(p -> p.getUUID().equals(id))) {
                    DismountGrace.grant(id, sl.getGameTime());
                }
            }
            ultimate_minecarts$lastPassengers = current.stream().map(Entity::getUUID).toList();
        }
        boolean hasNow = !getPassengers().isEmpty();
        if (ultimate_minecarts$hadPassenger && !hasNow) {
            ultimate_minecarts$graceTicks = 40;
            AbstractMinecart parent = getLinkedParent();
            if (parent instanceof MinecartPhysicsAccess p) p.ultimate_minecarts$startGrace();
        }
        if (ultimate_minecarts$graceTicks > 0) ultimate_minecarts$graceTicks--;
        ultimate_minecarts$hadPassenger = hasNow;
    }

    @Inject(method = "moveAlongTrack", at = @At("RETURN"))
    private void ultimate_minecarts$isNotMovingOnRail(ServerLevel level, CallbackInfo info) {
        isMovingOnRail = false;
    }

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void ultimate_minecarts$getMaxSpeed(ServerLevel level, CallbackInfoReturnable<Double> info) {
        if ((Object) this instanceof MinecartFurnace) return;
        boolean linked = getLinkedParent() != null;
        AbstractMinecart root = getLinkedParent();
        int depth = 0;
        while (root != null && !(root instanceof MinecartFurnace) && depth++ < 64) {
            AbstractMinecart next = ((Linkable) root).getLinkedParent();
            if (next == null) break;
            root = next;
        }

        double base = root instanceof MinecartFurnace
                ? ((AbstractMinecartInvoker) root).invokeGetMaxSpeed(level)
                : UltimateMinecartsConfig.getOtherMinecartSpeed();

        info.setReturnValue(linked ? base * 1.5 : base);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void ultimate_minecarts$writeLinks(ValueOutput output, CallbackInfo info) {
        if (parentUuid != null) {
            output.store("UMLinkedParent", UUIDUtil.CODEC, parentUuid);
            output.store("UMLinkedChain",  Identifier.CODEC,
                BuiltInRegistries.ITEM.getKey(ultimate_minecarts$linkedChain));
        }
        if (childUuid != null) {
            output.store("UMLinkedChild", UUIDUtil.CODEC, childUuid);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ultimate_minecarts$readLinks(ValueInput input, CallbackInfo info) {
        pendingParentUuid = input.read("UMLinkedParent", UUIDUtil.CODEC).orElse(null);
        pendingChildUuid  = input.read("UMLinkedChild",  UUIDUtil.CODEC).orElse(null);
        input.read("UMLinkedChain", Identifier.CODEC)
             .ifPresent(id -> pendingChainPath = id.toString());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ultimate_minecarts$tickHead(CallbackInfo info) {
        if (level() instanceof ServerLevel sl) {
            ultimate_minecarts$releaseOrphan(sl);
        }

        ultimate_minecarts$preTick_velocity = getDeltaMovement();
        ultimate_minecarts$preTick_speedBPS = getDeltaMovement().horizontalDistance() * 20.0;
        ultimate_minecarts$preTick_box = getBoundingBox();

        if (level() instanceof ServerLevel serverLevel) {
            ultimate_minecarts$lastTick = serverLevel.getGameTime();
            ultimate_minecarts$resolvePendingLinks(serverLevel);
            ultimate_minecarts$recordMomentum();
            ultimate_minecarts$steerToParent(serverLevel);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ultimate_minecarts$tickTail(CallbackInfo info) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if ((Object) this instanceof MinecartHopper hopper) HopperFeed.tick(serverLevel, hopper);
        ultimate_minecarts$serverTick(serverLevel);
        if (getLinkedParent() == null && getLinkedChild() != null) {
            ChainConstraint.queueHead((AbstractMinecart) (Object) this);
        }
        if (!((Object) this instanceof MinecartFurnace) && getLinkedParent() == null) {
            double cap = UltimateMinecartsConfig.getOtherMinecartSpeed();
            Vec3 vel = getDeltaMovement();
            double speed = vel.horizontalDistance();
            if (speed > cap + 1e-5) {
                double scale = cap / speed;
                setDeltaMovement(vel.x * scale, vel.y, vel.z * scale);
            }
        }
    }

    @Unique
    private void ultimate_minecarts$resolvePendingLinks(ServerLevel serverLevel) {
        if (pendingParentUuid != null) {
            Entity found = serverLevel.getEntity(pendingParentUuid);
            if (found instanceof AbstractMinecart parentCart) {
                if (pendingChainPath != null) {
                    Identifier id = Identifier.tryParse(pendingChainPath);
                    if (id != null) {
                        Item restored = BuiltInRegistries.ITEM.getValue(id);
                        ultimate_minecarts$linkedChain =
                            (restored == Items.AIR) ? Items.IRON_CHAIN : restored;
                    }
                    pendingChainPath = null;
                }
                setLinkedParent(parentCart);
                pendingParentUuid = null;
            }
        }

        if (pendingChildUuid != null) {
            Entity found = serverLevel.getEntity(pendingChildUuid);
            if (found instanceof AbstractMinecart childCart) {
                setLinkedChild(childCart);
                pendingChildUuid = null;
            }
        }
    }

    @Unique
    private void ultimate_minecarts$syncToClients() {
        if (!(level() instanceof ServerLevel)) return;
        int parentId = getLinkedParent() != null ? getLinkedParent().getId() : -1;
        int chainId  = BuiltInRegistries.ITEM.getId(ultimate_minecarts$linkedChain);
        ClientboundSyncChainedMinecartPacket pkt =
                new ClientboundSyncChainedMinecartPacket(parentId, getId(), chainId);
        PlayerLookup.tracking(this).forEach(p -> ServerPlayNetworking.send(p, pkt));
    }

    @Unique
    @Override
    public void applyCollisionDamage(ServerLevel serverLevel) {
        Vec3 cur = position();
        Vec3 last = ultimate_minecarts$tailHistory.peekLast();
        if (last != null && cur.distanceToSqr(last) > 64.0) {
            ultimate_minecarts$tailHistory.clear();
            last = null;
        }
        ultimate_minecarts$tailHistory.addLast(cur);
        while (ultimate_minecarts$tailHistory.size() > ultimate_minecarts$WINDOW_TICKS + 1)
            ultimate_minecarts$tailHistory.removeFirst();

        if (!UltimateMinecartsConfig.get().minecartDamageEnabled) return;
        if (last == null) return;

        Vec3 step = cur.subtract(last);
        Vec3 oldest = ultimate_minecarts$tailHistory.peekFirst();
        int ticks = ultimate_minecarts$tailHistory.size() - 1;
        double windowSpeed = cur.subtract(oldest).horizontalDistance() / ticks;
        double stepSpeed = step.horizontalDistance();

        double speedBPS = Math.min(stepSpeed, windowSpeed) * 20.0;
        if (speedBPS < ultimate_minecarts$MIN_DAMAGE_BPS) return;

        double stepDist = step.horizontalDistance();
        Vec3 knockDir = new Vec3(step.x / stepDist, 0.0, step.z / stepDist);

        int attachedCarts = 0;
        AbstractMinecart next = getLinkedChild();
        while (next != null) {
            attachedCarts++;
            next = ((Linkable) next).getLinkedChild();
        }

        float baseDamage = (float) (speedBPS * 1.5);
        float damage = baseDamage * (1.0f + (float) Math.log1p(attachedCarts) * 0.5f);

        AABB sweep = ultimate_minecarts$preTick_box != null
                ? ultimate_minecarts$preTick_box.minmax(getBoundingBox())
                : getBoundingBox();
        sweep = sweep.inflate(0.2);

        if (ultimate_minecarts$graceTicks > 0) return;

        boolean hitSomething = false;
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, sweep,
                e -> e.isAlive() && !e.isPassenger())) {
            if (DismountGrace.isImmune(living, serverLevel.getGameTime())) continue;
            if (living.invulnerableTime > 0) continue;

            double knockSpeed = CartKnockback.livingKnockSpeed(speedBPS, attachedCarts);
            Vec3 knockback = new Vec3(
                    knockDir.x * knockSpeed,
                    0.4 + attachedCarts * 0.1,
                    knockDir.z * knockSpeed
            );

            living.hurtMarked = true;
            living.invulnerableTime = 0;
            living.hurt(
                level().damageSources().source(
                    UltimateMinecarts.MINECART_DAMAGE, this, getFirstPassenger()),
                damage);
            living.setDeltaMovement(knockback);
            hitSomething = true;
        }

        if (hitSomething) {
            Vec3 now = getDeltaMovement();
            if (now.horizontalDistance() < stepDist * 0.9) {
                setDeltaMovement(step.x * 0.95, now.y, step.z * 0.95);
            }
        }
    }

    @Unique
    private static float ultimate_minecarts$chainDistance(Item chain) {
        String path = BuiltInRegistries.ITEM.getKey(chain).getPath();
        return UltimateMinecartsConfig.getChainDistance(path);
    }

    @Unique
    private void ultimate_minecarts$steerToParent(ServerLevel serverLevel) {
        AbstractMinecart parent = getLinkedParent();
        if (parent == null || !((AbstractMinecart) (Object) this).isOnRails()) return;

        float targetDist = ultimate_minecarts$chainDistance(getLinkedChain());
        Vec3 toParent = parent.position().subtract(position());
        double hDist = toParent.horizontalDistance();
        if (hDist < 1e-4) return;

        boolean parentTicked = !(parent instanceof MinecartPhysicsAccess pa)
                || pa.ultimate_minecarts$getLastTick() == serverLevel.getGameTime();
        double lead = (parentTicked || !parent.isOnRails())
                ? 0.0
                : parent.getDeltaMovement().horizontalDistance();

        double step = hDist + lead - 1.0 - targetDist;
        Vec3 own = getDeltaMovement();

        if (step <= 1e-4) {
            setDeltaMovement(0.0, own.y, 0.0);
            return;
        }

        double slowdown = isVehicle() ? 0.997 : UltimateMinecartsConfig.MINECART_SLOWDOWN;
        double speed = step / slowdown;

        Vec3 toward = new Vec3(toParent.x / hDist, 0.0, toParent.z / hDist);
        double ownSpeed = own.horizontalDistance();
        Vec3 dir;
        if (ownSpeed > 1e-4) {
            if (own.x * toward.x + own.z * toward.z <= 0.0) return;
            dir = new Vec3(own.x / ownSpeed, 0.0, own.z / ownSpeed);
        } else {
            double yaw = Math.toRadians(getYRot());
            Vec3 axis = new Vec3(-Math.cos(yaw), 0.0, Math.sin(yaw));
            dir = (axis.x * toward.x + axis.z * toward.z) >= 0.0 ? axis : axis.scale(-1.0);
        }
        setDeltaMovement(dir.x * speed, own.y, dir.z * speed);
    }

    @Unique
    private void ultimate_minecarts$recordMomentum() {
        if (ultimate_minecarts$lastParent == null) {
            ultimate_minecarts$momentum.clear();
            return;
        }
        ultimate_minecarts$momentum.addLast(getDeltaMovement());
        while (ultimate_minecarts$momentum.size() > ultimate_minecarts$MOMENTUM_TICKS)
            ultimate_minecarts$momentum.removeFirst();
    }

    @Unique
    private void ultimate_minecarts$releaseOrphan(ServerLevel serverLevel) {
        AbstractMinecart parent = getLinkedParent();
        if (parent != null) {
            ultimate_minecarts$lastParent = parent;
            return;
        }

        AbstractMinecart gone = ultimate_minecarts$lastParent;
        if (gone == null) return;
        ultimate_minecarts$lastParent = null;

        Entity.RemovalReason reason = gone.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) return;

        setLinkedParent(null);

        Vec3 best = Vec3.ZERO;
        double bestSpeed = 0.0;
        for (Vec3 v : ultimate_minecarts$momentum) {
            double s = v.horizontalDistance();
            if (s > bestSpeed) { bestSpeed = s; best = v; }
        }
        ultimate_minecarts$momentum.clear();

        Vec3 now = getDeltaMovement();
        double nowSpeed = now.horizontalDistance();
        if (bestSpeed <= nowSpeed + 1e-4) return;

        boolean sameWay = nowSpeed > 1e-4 && (now.x * best.x + now.z * best.z) > 0.0;
        Vec3 dir = sameWay
                ? new Vec3(now.x / nowSpeed, 0.0, now.z / nowSpeed)
                : new Vec3(best.x / bestSpeed, 0.0, best.z / bestSpeed);
        setDeltaMovement(dir.x * bestSpeed, now.y, dir.z * bestSpeed);
    }

    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void ultimate_minecarts$dismountBeside(LivingEntity passenger, CallbackInfoReturnable<Vec3> cir) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        DismountGrace.grant(passenger.getUUID(), serverLevel.getGameTime());

        Vec3 vel = getDeltaMovement();
        boolean alongX = vel.horizontalDistanceSqr() > 1.0E-4
                ? Math.abs(vel.x) >= Math.abs(vel.z)
                : getMotionDirection().getAxis() == Direction.Axis.X;

        int sx = alongX ? 0 : 1;
        int sz = alongX ? 1 : 0;

        Vec3 look = passenger.getLookAngle();
        int first = (look.x * sx + look.z * sz) >= 0 ? 1 : -1;

        BlockPos origin = blockPosition();
        for (int dist = 1; dist <= 3; dist++) {
            for (int sign : new int[] { first, -first }) {
                BlockPos target = origin.offset(sx * sign * dist, 0, sz * sign * dist);
                Vec3 spot = DismountHelper.findSafeDismountLocation(
                        passenger.getType(), level(), target, true);
                if (spot == null) continue;
                if (BaseRailBlock.isRail(level().getBlockState(BlockPos.containing(spot)))) continue;
                cir.setReturnValue(spot);
                return;
            }
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void ultimate_minecarts$notePush(Entity other, CallbackInfo ci) {
        if (level() instanceof ServerLevel sl) {
            ChainConstraint.markPushed((AbstractMinecart) (Object) this, sl.getGameTime());
        }
    }

    @Unique
    private void ultimate_minecarts$serverTick(ServerLevel serverLevel) {
        AbstractMinecart parent = getLinkedParent();
        if (parent != null) {
                float targetDist = ultimate_minecarts$chainDistance(((Linkable) this).getLinkedChain());
                double distance = parent.position().subtract(position()).horizontalDistance() - 1.0;

                if (distance > targetDist + UltimateMinecartsConfig.get().chainResistance) {
                    Linkable.unsetParentChild((Linkable) parent, this);
                    Item chainItem = ((Linkable) this).getLinkedChain();
                    spawnAtLocation(serverLevel, new ItemStack(chainItem));
                    serverLevel.playSound(null, getX(), getY(), getZ(),
                            SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            getX(), getY() + 0.5, getZ(),
                            8, 0.2, 0.2, 0.2, 0.05);
                    return;
                }

            if (parent.isRemoved())
                Linkable.unsetParentChild((Linkable) parent, this);
        }

        AbstractMinecart child = getLinkedChild();
        if (child != null && child.isRemoved())
            Linkable.unsetParentChild(this, (Linkable) child);

        ultimate_minecarts$collideWithCarts(serverLevel);
        if (isRemoved()) return;
        applyCollisionDamage(serverLevel);
    }

    @Unique
    private static float ultimate_minecarts$clampAngle(float angle, float target, int maxDelta) {
        float diff = Mth.wrapDegrees(target - angle);
        diff = Mth.clamp(diff, -maxDelta, maxDelta);
        return angle + diff;
    }

    @Unique
    private void ultimate_minecarts$collideWithCarts(ServerLevel serverLevel) {
        AABB box = ultimate_minecarts$preTick_box != null
                ? ultimate_minecarts$preTick_box.minmax(getBoundingBox())
                : getBoundingBox();
        box = box.inflate(0.2);
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        for (Entity e : level().getEntities(this, box,
                o -> o instanceof AbstractMinecart && !o.isRemoved() && canCollideWith(o))) {
            if (isRemoved()) return;
            AbstractMinecart other = (AbstractMinecart) e;
            if (other.isRemoved()) continue;
            if (ultimate_minecarts$breakIfFast(serverLevel, other)) continue;
            CartKnockback.resolve(serverLevel, self, other);
        }
    }

    @Unique
    private double ultimate_minecarts$recentSpeedBPS() {
        int n = ultimate_minecarts$tailHistory.size();
        if (n < 2) return 0.0;
        Vec3 cur = ultimate_minecarts$tailHistory.peekLast();
        Vec3 oldest = ultimate_minecarts$tailHistory.peekFirst();
        var it = ultimate_minecarts$tailHistory.descendingIterator();
        it.next();
        Vec3 prev = it.next();
        double stepSpeed = cur.subtract(prev).horizontalDistance();
        double windowSpeed = cur.subtract(oldest).horizontalDistance() / (n - 1);
        return Math.min(stepSpeed, windowSpeed) * 20.0;
    }

    @Unique
    private boolean ultimate_minecarts$sameChain(AbstractMinecart other) {
        AbstractMinecart cursor = (AbstractMinecart) (Object) this;
        AbstractMinecart up;
        int depth = 0;
        while (depth++ < 64 && (up = ((Linkable) cursor).getLinkedParent()) != null) cursor = up;
        depth = 0;
        while (cursor != null && depth++ < 64) {
            if (cursor == other) return true;
            cursor = ((Linkable) cursor).getLinkedChild();
        }
        return false;
    }

    @Unique
    private boolean ultimate_minecarts$breakIfFast(ServerLevel serverLevel, AbstractMinecart other) {
        if (!UltimateMinecartsConfig.get().cartCollisionBreaking) return false;
        if (!other.isOnRails() || ultimate_minecarts$sameChain(other)) return false;
        if (ultimate_minecarts$recentSpeedBPS() < ultimate_minecarts$MIN_DAMAGE_BPS) return false;

        AABB path = ultimate_minecarts$preTick_box != null
                ? ultimate_minecarts$preTick_box.minmax(getBoundingBox())
                : getBoundingBox();
        if (!path.inflate(0.1).intersects(other.getBoundingBox())) return false;
        Vec3 v = ultimate_minecarts$preTick_velocity;
        double h = v.horizontalDistance();
        if (h < 1e-5) return false;
        Vec3 to = other.position().subtract(position());
        double along   = (to.x * v.x + to.z * v.z) / h;
        double lateral = Math.abs(to.x * v.z - to.z * v.x) / h;
        if (along < -0.5 || lateral > 0.75) return false;

        AbstractMinecart self = (AbstractMinecart) (Object) this;
        boolean otherFast = other.getDeltaMovement().horizontalDistance() * 20.0 >= ultimate_minecarts$MIN_DAMAGE_BPS;
        ultimate_minecarts$smash(serverLevel, other);
        if (otherFast && self.isOnRails()) ultimate_minecarts$smash(serverLevel, self);
        return true;
    }

    @Unique
    private static void ultimate_minecarts$smash(ServerLevel level, AbstractMinecart victim) {
        if (victim.isRemoved()) return;

        ItemStack drop = victim.getPickResult();
        if (victim instanceof Container container) {
            Containers.dropContents(level, victim.blockPosition(), container);
            container.clearContent();
        }
        victim.remove(Entity.RemovalReason.KILLED);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.IRON_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);

        ItemEntity item = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), drop);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    @Override
    public AbstractMinecart getLinkedParent() {
        Entity e = level() instanceof ServerLevel sl && parentUuid != null
                ? sl.getEntity(parentUuid)
                : level().getEntity(parentIdClient);
        return e instanceof AbstractMinecart m ? m : null;
    }

    @Override
    public void setLinkedParent(@Nullable AbstractMinecart parent) {
        if (parent != null) {
            parentUuid     = parent.getUUID();
            parentIdClient = parent.getId();
        } else {
            parentUuid     = null;
            parentIdClient = -1;
        }
        if (level() instanceof ServerLevel) {
            ultimate_minecarts$syncToClients();
        }
    }

    @Override
    public void setLinkedParentClient(int id) { this.parentIdClient = id; }

    @Override
    public @Nullable AbstractMinecart getLinkedChild() {
        Entity e = level() instanceof ServerLevel sl && childUuid != null
                ? sl.getEntity(childUuid)
                : level().getEntity(childIdClient);
        return e instanceof AbstractMinecart m ? m : null;
    }

    @Override
    public void setLinkedChild(@Nullable AbstractMinecart child) {
        if (child != null) {
            childUuid     = child.getUUID();
            childIdClient = child.getId();
        } else {
            childUuid     = null;
            childIdClient = -1;
        }
    }

    @Override
    public void setLinkedChildClient(int id) { this.childIdClient = id; }

    @Override
    public boolean isSelfMovingOnRail() { return isMovingOnRail; }
}