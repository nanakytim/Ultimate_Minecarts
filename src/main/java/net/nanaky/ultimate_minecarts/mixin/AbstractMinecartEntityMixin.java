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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncChainedMinecartPacket;
import net.nanaky.ultimate_minecarts.common.utils.MinecartPhysicsAccess;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Override
    public void setLinkedChain(Item chain) { this.ultimate_minecarts$linkedChain = chain; }

    @Override
    public Item getLinkedChain() { return ultimate_minecarts$linkedChain; }

    public AbstractMinecartEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public void ultimate_minecarts$startGrace() {
        ultimate_minecarts$graceTicks = 20;
    }

    @Inject(method = "moveAlongTrack", at = @At("HEAD"))
    private void ultimate_minecarts$isMovingOnRail(ServerLevel level, CallbackInfo info) {
        isMovingOnRail = true;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ultimate_minecarts$checkDismount(CallbackInfo ci) {
        if (!(level() instanceof ServerLevel)) return;
        boolean hasNow = !getPassengers().isEmpty();
        if (ultimate_minecarts$hadPassenger && !hasNow) {
            ultimate_minecarts$graceTicks = 20;
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
        AbstractMinecart parent = getLinkedParent();
        if (parent != null)
            info.setReturnValue(((AbstractMinecartInvoker) parent).invokeGetMaxSpeed(level));
        else
            info.setReturnValue(UltimateMinecartsConfig.getOtherMinecartSpeed());
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
        ultimate_minecarts$preTick_velocity = getDeltaMovement();
        ultimate_minecarts$preTick_speedBPS = getDeltaMovement().horizontalDistance() * 20.0;

        if (level() instanceof ServerLevel serverLevel) {
            ultimate_minecarts$resolvePendingLinks(serverLevel);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ultimate_minecarts$tickTail(CallbackInfo info) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        ultimate_minecarts$serverTick(serverLevel);
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
        if (!UltimateMinecartsConfig.get().minecartDamageEnabled) return;

        double speedBPS = ultimate_minecarts$preTick_speedBPS;
        double currentSpeedBPS = getDeltaMovement().horizontalDistance() * 20.0;
        if (speedBPS < 6.0) return;
        if (currentSpeedBPS < 6.0) return;

        int attachedCarts = 0;
        AbstractMinecart next = getLinkedChild();
        while (next != null) {
            attachedCarts++;
            next = ((Linkable) next).getLinkedChild();
        }

        float baseDamage = (float) (speedBPS * 1.5);
        float damage = baseDamage * (1.0f + (float) Math.log1p(attachedCarts) * 0.5f);

        for (Entity other : level().getEntities(this,
                getBoundingBox().inflate(0.1), this::canCollideWith)) {

            AbstractMinecart parent = getLinkedParent();
            if (other instanceof AbstractMinecart otherCart
                    && parent != null && !parent.equals(otherCart)) {
                otherCart.setDeltaMovement(getDeltaMovement());
            }

            if (!(other instanceof LivingEntity living)) continue;
            if (!living.isAlive() || living.isPassenger()) continue;
            if (living.invulnerableTime > 0) continue;
            if (ultimate_minecarts$graceTicks > 0) continue;

            Vec3 cartDir = ultimate_minecarts$preTick_velocity;
            double hDist = cartDir.horizontalDistance();
            Vec3 knockDir = hDist > 0.01
                    ? new Vec3(cartDir.x / hDist, 0.0, cartDir.z / hDist)
                    : Vec3.ZERO;

            double knockSpeed = (3.0 + speedBPS) * (0.25 + Math.log1p(attachedCarts) * 0.15);
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
        }
    }

    @Unique
    private static float ultimate_minecarts$chainDistance(Item chain) {
        String path = BuiltInRegistries.ITEM.getKey(chain).getPath();
        return switch (path) {
            case "copper_chain",
                 "waxed_copper_chain"           -> 1.6f;
            case "exposed_copper_chain",
                 "waxed_exposed_copper_chain"   -> 2f;
            case "weathered_copper_chain",
                 "waxed_weathered_copper_chain" -> 2.6f;
            case "oxidized_copper_chain",
                 "waxed_oxidized_copper_chain"  -> 3.2f;
            default                              -> 1.2f;
        };
    }

    @Unique
    private void ultimate_minecarts$serverTick(ServerLevel serverLevel) {
        AbstractMinecart parent = getLinkedParent();
        if (parent != null) {
            float targetDist = ultimate_minecarts$chainDistance(((Linkable) this).getLinkedChain());
            double distance = parent.distanceTo(this) - 1;

            if (distance <= targetDist + 4) {
                Vec3 parentVel = parent.getDeltaMovement();
                double parentSpeed = parentVel.horizontalDistance();
                final double minSpeed = 0.005;

                if (distance > targetDist + 0.1) {
                    Vec3 toParent = parent.position().subtract(position());
                    double hDist = toParent.horizontalDistance();
                    if (hDist > 0.01) {
                        double catchSpeed = Math.min(parentSpeed + 0.05, parentSpeed + (distance - targetDist) * 0.5);
                        setDeltaMovement(
                                (toParent.x / hDist) * catchSpeed,
                                getDeltaMovement().y,
                                (toParent.z / hDist) * catchSpeed
                        );
                    }

                } else if (distance < targetDist - 0.2 || distance <= 0) {
                    Vec3 ownVel = getDeltaMovement();
                    double ownSpeed = ownVel.horizontalDistance();
                    if (ownSpeed > minSpeed) {
                        double brakeSpeed = distance <= 0 ? 0.0 : Math.min(ownSpeed, parentSpeed);
                        double scale = brakeSpeed / ownSpeed;
                        setDeltaMovement(ownVel.x * scale, getDeltaMovement().y, ownVel.z * scale);
                    }

                } else {
                    Vec3 ownVel = getDeltaMovement();
                    double ownSpeed = ownVel.horizontalDistance();
                    if (ownSpeed > minSpeed) {
                        double scale = parentSpeed / ownSpeed;
                        setDeltaMovement(
                                ownVel.x * scale,
                                getDeltaMovement().y,
                                ownVel.z * scale
                        );
                    } else if (parentSpeed > minSpeed) {
                        Vec3 toParent = parent.position().subtract(position());
                        double hDist = toParent.horizontalDistance();
                        if (hDist > 0.01)
                            setDeltaMovement(
                                    (toParent.x / hDist) * minSpeed,
                                    getDeltaMovement().y,
                                    (toParent.z / hDist) * minSpeed
                            );
                    }
                }

            } else {
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

        applyCollisionDamage(serverLevel);
    }

    @Unique
    private static float ultimate_minecarts$clampAngle(float angle, float target, int maxDelta) {
        float diff = Mth.wrapDegrees(target - angle);
        diff = Mth.clamp(diff, -maxDelta, maxDelta);
        return angle + diff;
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