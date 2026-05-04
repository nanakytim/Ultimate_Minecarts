package net.nanaky.ultimate_minecarts.mixin;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncFurnaceFuelPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MinecartFurnace.class)
public abstract class FurnaceMinecartEntityMixin extends AbstractMinecart implements IFurnaceMinecart {

    @Shadow private int fuel;
    @Shadow public Vec3 push;
    @Shadow protected abstract boolean hasFuel();
    @Unique private boolean ultimate_minecarts$prevChunkInitialized = false;
    @Unique private int prevChunkX;
    @Unique private int prevChunkZ;
    @Unique private double ultimate_minecarts$currentSpeed = 0.0;
    @Unique private double ultimate_minecarts$preTick_speedBPS = 0.0;
    @Unique private boolean ultimate_minecarts$fuelJustEmptied = false;

    @Unique
    public void ultimate_minecarts$setFuel(int value) {
        fuel = value;
    }

    @Unique
    public int ultimate_minecarts$getFuel() {
        return fuel;
    }

    @Unique
    public void ultimate_minecarts$extinguish(Player player, ServerLevel serverLevel) {
        fuel = 0;
        ultimate_minecarts$currentSpeed = 0.0;
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));

        if (!player.isCreative()) {
            player.getInventory().setItem(
                player.getInventory().getSelectedSlot(),
                new ItemStack(Items.BUCKET));
        }

        level().playSound(null, blockPosition(),
            SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS, 1.0f, 0.8f + random.nextFloat() * 0.4f);
            
            for (int i = 0; i < 24; i++) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX() + (random.nextFloat() - 0.5) * 1.0,
                    getY() + 0.3 + random.nextFloat() * 0.8,
                    getZ() + (random.nextFloat() - 0.5) * 1.0,
                    1, 0.0, 0.08, 0.0, 0.0);
            }
    }

    protected FurnaceMinecartEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }
    
    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    public void ultimate_minecarts$increaseSpeed(ServerLevel level, CallbackInfoReturnable<Double> info) {
        if (!hasFuel()) return;

        BlockPos pos = blockPosition();
        boolean onRail = this.level().getBlockState(pos).is(net.minecraft.tags.BlockTags.RAILS)
                      || this.level().getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.RAILS);
        if (!onRail) return;

        double fuelRatio = (double) fuel / UltimateMinecartsConfig.get().furnaceMaxBurnTime;
        double topSpeed = UltimateMinecartsConfig.getFurnaceMinecartSpeed();
        double targetSpeed = topSpeed * (0.2 + 0.8 * fuelRatio);

        ultimate_minecarts$currentSpeed += (targetSpeed - ultimate_minecarts$currentSpeed) * 0.02;

        info.setReturnValue(ultimate_minecarts$currentSpeed);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void ultimate_minecarts$drainFuel(CallbackInfo info) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (hasFuel()) {
            fuel = Math.max(0, fuel - 19);
            ultimate_minecarts$fuelJustEmptied = true;
        } else if (ultimate_minecarts$fuelJustEmptied) {
            ultimate_minecarts$fuelJustEmptied = false;
        } else {
            return;
        }


        ClientboundSyncFurnaceFuelPacket packet = new ClientboundSyncFurnaceFuelPacket(getId(), fuel);
        for (ServerPlayer player : PlayerLookup.tracking(serverLevel, blockPosition())) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void ultimate_minecarts$capturePreTickSpeed(CallbackInfo info) {
        if (!(level() instanceof ServerLevel)) return;
        ultimate_minecarts$preTick_speedBPS = getDeltaMovement().horizontalDistance() * 20.0;
        if (!hasFuel()) {
            ultimate_minecarts$currentSpeed = getDeltaMovement().horizontalDistance();
        }
    }

    @Inject(method = "applyNaturalSlowdown", at = @At("HEAD"), cancellable = true)
    public void ultimate_minecarts$useNormalSlowdown(Vec3 deltaMovement, CallbackInfoReturnable<Vec3> cir) {
        double slowdownFactor = isVehicle() ? 0.997 : 0.975;
        Vec3 result = deltaMovement.multiply(slowdownFactor, 0.0, slowdownFactor);
        if (isInWater()) result = result.scale(0.95F);
        cir.setReturnValue(result);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void ultimate_minecarts$fuelPropulsion(CallbackInfo info) {
        if (!(level() instanceof ServerLevel)) return;
        if (!hasFuel()) return;
        BlockPos pos = getCurrentBlockPosOrRailBelow();
        BlockState state = level().getBlockState(pos);
        if (state.is(net.minecraft.world.level.block.Blocks.POWERED_RAIL)
                && !state.getValue(net.minecraft.world.level.block.PoweredRailBlock.POWERED)) return;

        Vec3 movement = getDeltaMovement();
        double hSpeed = movement.horizontalDistance();

        if (hSpeed > 1e-5) {
            if (hSpeed < ultimate_minecarts$currentSpeed) {
                double nudge = Math.min(ultimate_minecarts$currentSpeed - hSpeed, 0.05);
                double scale = (hSpeed + nudge) / hSpeed;
                setDeltaMovement(movement.x * scale, movement.y, movement.z * scale);
                }
            } else if (push.lengthSqr() > 1e-7) {
                setDeltaMovement(push.normalize().scale(0.02));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void ultimate_minecarts$loadChunks(CallbackInfo info) {
        if (level() instanceof ServerLevel server) {

            if (!ultimate_minecarts$prevChunkInitialized) {
                prevChunkX = blockPosition().getX() >> 4;
                prevChunkZ = blockPosition().getZ() >> 4;
                ultimate_minecarts$prevChunkInitialized = true;
            }

            int curX = blockPosition().getX() >> 4;
            int curZ = blockPosition().getZ() >> 4;

            if (fuel > 0)
                server.setChunkForced(curX, curZ, true);
            if (curX != prevChunkX || curZ != prevChunkZ || fuel <= 0)
                server.setChunkForced(prevChunkX, prevChunkZ, false);

            prevChunkX = curX;
            prevChunkZ = curZ;
        }
    }

    @ModifyArgs(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
    ))
    public void ultimate_minecarts$changeSmokeParticle(Args args) {
        args.set(0, ParticleTypes.CAMPFIRE_COSY_SMOKE);
        args.set(1, getX() + (random.nextFloat() - 0.5) * 0.2);
        args.set(2, getY() + 2);
        args.set(3, getZ() + (random.nextFloat() - 0.5) * 0.2);
        args.set(5, 0.1);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"
    ))
    public int ultimate_minecarts$removeRandom(int i) {
        return 2;
    }

    @Inject(
        method = "interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"),
        cancellable = true
    )
    public void ultimate_minecarts$addOtherFuels(Player player, InteractionHand hand, Vec3 hitPos,
                                              CallbackInfoReturnable<InteractionResult> info) {
            ItemStack stack = player.getItemInHand(hand);
            FuelValues fuelValues = level().fuelValues();

            if (stack.is(Items.WATER_BUCKET) && fuel > 0) {
                fuel = 0;
                ultimate_minecarts$currentSpeed = 0.0;
                setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));

                if (!player.isCreative()) {
                    player.getInventory().setItem(
                        player.getInventory().getSelectedSlot(),
                        new ItemStack(Items.BUCKET));
                }

                level().playSound(player, blockPosition(),
                    SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 1.0f, 0.8f + random.nextFloat() * 0.4f);

                if (level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 8; i++) {
                        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                            getX() + (random.nextFloat() - 0.5) * 0.5,
                            getY() + 0.5 + random.nextFloat() * 0.5,
                            getZ() + (random.nextFloat() - 0.5) * 0.5,
                            1, 0.0, 0.05, 0.0, 0.0);
                    }
                }

                info.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            if (!stack.isEmpty() && fuelValues.isFuel(stack)) {
                int fuelTime = fuelValues.burnDuration(stack);

                if (fuel >= UltimateMinecartsConfig.get().furnaceMaxBurnTime) {
                    info.setReturnValue(InteractionResult.SUCCESS);
                    return;
                }

                if (!player.isCreative() && fuelTime > 0) {
                    if (stack.getItem() instanceof BucketItem) {
                        player.getInventory().setItem(
                                player.getInventory().getSelectedSlot(),
                                new ItemStack(Items.BUCKET));
                    } else {
                        stack.shrink(1);
                    }
                }

                if (stack.getItem() instanceof BucketItem) {
                    level().playSound(player, blockPosition(),
                            SoundEvents.LAVA_EXTINGUISH,
                            SoundSource.BLOCKS, 0.5f, 0.8f + random.nextFloat() * 0.4f);
                    level().playSound(player, blockPosition(),
                            SoundEvents.BUCKET_EMPTY_LAVA,
                            SoundSource.BLOCKS, 1.0f, 1.0f);
                }

                fuel = (int) Math.min(
                        UltimateMinecartsConfig.get().furnaceMaxBurnTime,
                        fuel + (fuelTime * 2));
                if (level().isClientSide() && fuelTime > 0) {
                    double scale = Math.min(5.0, Math.max(1.0, fuelTime / 1600.0));
                    int particleCount = (int)(2 * scale);

                    for (int i = 0; i < particleCount; i++) {
                        level().addParticle(ParticleTypes.LAVA,
                            getX() + (random.nextFloat() - 0.5) * 0.3 * scale,
                            getY() + 0.4 + random.nextFloat() * 0.3,
                            getZ() + (random.nextFloat() - 0.5) * 0.3 * scale,
                            0.0, 0.0, 0.0);
                    }
                    level().playLocalSound(getX(), getY(), getZ(),
                        SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL,
                        (float)(0.6 * Math.min(2.0, scale)),
                        1.7f + random.nextFloat() * 0.3f,
                        false);
                }
            }

            if (fuel > 0) {
                Vec3 currentMovement = getDeltaMovement();
                if (currentMovement.horizontalDistanceSqr() > 1.0E-4) {
                    push = currentMovement.normalize().scale(0.5);
                } else {
                    Vec3 awayFromPlayer = position().subtract(player.position());
                    double hDist = awayFromPlayer.horizontalDistance();
                    push = hDist > 0.01
                        ? new Vec3(awayFromPlayer.x / hDist, 0, awayFromPlayer.z / hDist).scale(0.5)
                        : new Vec3(0, 0, 1).scale(0.5);
                }
            }

            info.setReturnValue(InteractionResult.SUCCESS);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    public void ultimate_minecarts$writeNbt(ValueOutput output, CallbackInfo info) {
        output.putInt("RealFuel", fuel);
        long packed = ((long) prevChunkX << 32) | (prevChunkZ & 0xFFFFFFFFL);
        output.putLong("PrevChunkPos", packed);
        output.putDouble("CurrentSpeed", ultimate_minecarts$currentSpeed);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void ultimate_minecarts$readNbt(ValueInput input, CallbackInfo info) {
        int saved = input.getIntOr("RealFuel", -1);
        if (saved >= 0) fuel = saved;
        long packed = input.getLongOr("PrevChunkPos", 0L);
        prevChunkX = (int)(packed >> 32);
        prevChunkZ = (int)(packed & 0xFFFFFFFFL);
        ultimate_minecarts$prevChunkInitialized = true;
        ultimate_minecarts$currentSpeed = input.getDoubleOr("CurrentSpeed", 0.0);
    }
}