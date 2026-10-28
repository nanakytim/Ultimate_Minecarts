package net.nanaky.ultimate_minecarts.mixin;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.FuelAcceptor;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncFurnaceFuelPacket;
import net.nanaky.ultimate_minecarts.common.utils.CartKnockback;
import net.nanaky.ultimate_minecarts.common.utils.MinecartPhysicsAccess;
import net.nanaky.ultimate_minecarts.common.utils.ReturnItem;

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

import java.util.Optional;

@Mixin(MinecartFurnace.class)
public abstract class FurnaceMinecartEntityMixin extends AbstractMinecart implements IFurnaceMinecart, FuelAcceptor {

    @Shadow private int fuel;
    @Shadow public Vec3 push;
    @Shadow protected abstract boolean hasFuel();
    @Shadow protected abstract void setHasFuel(boolean hasFuel);
    @Unique private boolean ultimate_minecarts$prevChunkInitialized = false;
    @Unique private int prevChunkX;
    @Unique private int prevChunkZ;
    @Unique private double ultimate_minecarts$currentSpeed = 0.0;
    @Unique private double ultimate_minecarts$preTick_speedBPS = 0.0;
    @Unique private boolean ultimate_minecarts$fuelJustEmptied = false;

    @Unique private static final double ultimate_minecarts$KICK = 0.05;
    @Unique private static final double ultimate_minecarts$BLOCKS_PER_BPS = 1;
    @Unique private double ultimate_minecarts$headSpeed = 0.0;
    @Unique private Vec3 ultimate_minecarts$headPos = Vec3.ZERO;

    @Unique private boolean ultimate_minecarts$braked = false;
    @Unique private int ultimate_minecarts$fuelAtHead = 0;
    @Unique private int ultimate_minecarts$lastSyncedFuel = -1;
    @Unique private static final double ultimate_minecarts$BRAKE_SLOWDOWN = 0.833;
    @Unique private static final double ultimate_minecarts$BRAKE_HOLD_SPEED = 0.04;

    @Unique private static final long ultimate_minecarts$ACTIVATOR_COOLDOWN = 20L;
    @Unique private static final double ultimate_minecarts$NUDGE = 0.08;
    @Unique private BlockPos ultimate_minecarts$lastActivator = null;
    @Unique private long ultimate_minecarts$lastActivatorTick = Long.MIN_VALUE / 2;
    @Unique private Vec3 ultimate_minecarts$heading = Vec3.ZERO;

    @Unique
    private void ultimate_minecarts$syncLit() {
        if (level().isClientSide()) return;
        boolean lit = fuel > 0;
        if (hasFuel() != lit) setHasFuel(lit);
    }

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
        ultimate_minecarts$syncLit();
        if (!player.isCreative()) {
            InteractionHand hand = player.getMainHandItem().is(Items.WATER_BUCKET)
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ReturnItem.giveBack(player, hand, new ItemStack(Items.BUCKET));
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

    @Unique
    private LootContext ultimate_minecarts$getFuelLootContext(ServerLevel serverLevel) {
        BlockPos pos = blockPosition();
        BlockState furnaceState = Blocks.FURNACE.defaultBlockState();
        FurnaceBlockEntity proxyFurnace = new FurnaceBlockEntity(pos, furnaceState);
        return new LootContext.Builder(
                new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.BLOCK_STATE, furnaceState)
                    .withParameter(LootContextParams.BLOCK_ENTITY, proxyFurnace)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.CONTAINER, proxyFurnace)
                    .create(LootContextParamSets.CONTAINER_PROCESS)
            )
            .create(Optional.empty());
    }

    @Unique
    public double ultimate_minecarts$getCurrentSpeed() {
        return ultimate_minecarts$currentSpeed;
    }

    @Unique
    private boolean ultimate_minecarts$onRail() {
        BlockPos pos = blockPosition();
        return level().getBlockState(pos).is(net.minecraft.tags.BlockTags.RAILS)
            || level().getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.RAILS);
    }

    @Unique
    private boolean ultimate_minecarts$onDeadRail() {
        BlockState state = level().getBlockState(getCurrentBlockPosOrRailBelow());
        return state.is(Blocks.POWERED_RAIL) && !state.getValue(PoweredRailBlock.POWERED);
    }

    @Unique
    private boolean ultimate_minecarts$isBurning() {
        return fuel > 0 && !ultimate_minecarts$braked;
    }

    @Unique
    private boolean ultimate_minecarts$onLivePoweredRail() {
        BlockState state = level().getBlockState(getCurrentBlockPosOrRailBelow());
        return state.is(Blocks.POWERED_RAIL) && state.getValue(PoweredRailBlock.POWERED);
    }

    @Unique
    private void ultimate_minecarts$playSound(ServerLevel serverLevel, String id, float volume, float pitch) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(id));
        if (sound != null) {
            serverLevel.playSound(null, getX(), getY(), getZ(), sound, SoundSource.AMBIENT, volume, pitch);
        }
    }

    @Unique
    private void ultimate_minecarts$toggleBrake(ServerLevel serverLevel) {
        ultimate_minecarts$braked = !ultimate_minecarts$braked;

        if (!ultimate_minecarts$braked) {
            ultimate_minecarts$playSound(serverLevel, "minecraft:block.lever.click", 1.0f, 0.8f);
            return;
        }

        serverLevel.playSound(null, getX(), getY(), getZ(),
            SoundEvents.GRINDSTONE_USE, SoundSource.AMBIENT, 0.5f, 1.0f);
        ultimate_minecarts$playSound(serverLevel, "minecraft:item.spear.lunge_3", 1.0f, 0.5f);
    }

    @Unique
    private static int ultimate_minecarts$fuelValue(ItemStack stack) {
        var cfg = UltimateMinecartsConfig.get();
        if (stack.is(Items.LAVA_BUCKET)) return cfg.fuelLavaBucket;
        if (stack.is(Items.BLAZE_ROD)) return cfg.fuelBlazeRod;
        if (stack.is(Items.COAL_BLOCK)) return cfg.fuelCoalBlock;
        if (stack.is(ItemTags.COALS)) return cfg.fuelCoal;
        if (stack.is(ItemTags.LOGS_THAT_BURN)) return cfg.fuelLog;
        if (stack.is(ItemTags.PLANKS)) return cfg.fuelPlank;
        if (stack.is(Items.STICK)) return cfg.fuelStick;
        return 0;
    }

    @Unique
    private static boolean ultimate_minecarts$isWaterBottle(ItemStack stack) {
        if (!stack.is(Items.POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
    }

    protected FurnaceMinecartEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private void ultimate_minecarts$keepHeading() {
        Vec3 pre = ((MinecartPhysicsAccess) (Object) this).ultimate_minecarts$getPreTickVelocity();
        double preH = pre.horizontalDistance();
        if (preH < 1e-3) return;

        Vec3 now = getDeltaMovement();
        boolean reversed = now.x * pre.x + now.z * pre.z < 0.0;
        boolean stalled = now.horizontalDistance() < 1e-5;
        if (!reversed && !stalled) return;

        setDeltaMovement(pre.x, now.y, pre.z);
        push = new Vec3(pre.x / preH, 0.0, pre.z / preH).scale(0.5);
    }

    @Unique
    private BlockPos ultimate_minecarts$activatorAt(double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        BlockState state = level().getBlockState(at);
        if (!state.is(Blocks.ACTIVATOR_RAIL)) {
            at = at.below();
            state = level().getBlockState(at);
            if (!state.is(Blocks.ACTIVATOR_RAIL)) return null;
        }
        return state.getValue(PoweredRailBlock.POWERED) ? at : null;
    }

    @Unique
    private void ultimate_minecarts$activatorToggle(ServerLevel serverLevel) {
        boolean wasBraked = ultimate_minecarts$braked;
        ultimate_minecarts$toggleBrake(serverLevel);
        if (!wasBraked) return;
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistance() >= ultimate_minecarts$NUDGE) return;

        Vec3 dir = ultimate_minecarts$heading;
        if (dir.lengthSqr() < 1e-6) {
            double yaw = Math.toRadians(getYRot());
            dir = new Vec3(-Math.cos(yaw), 0.0, Math.sin(yaw));
        }
        setDeltaMovement(dir.x * ultimate_minecarts$NUDGE, v.y, dir.z * ultimate_minecarts$NUDGE);
        if (fuel > 0) push = dir.scale(0.5);
    }

    @Unique
    private void ultimate_minecarts$checkActivators(ServerLevel serverLevel) {
        Vec3 from = ultimate_minecarts$headPos;
        Vec3 to = position();
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len > 32.0) { from = to; d = Vec3.ZERO; len = 0.0; }

        double hLen = d.horizontalDistance();
        if (hLen > 1e-3) ultimate_minecarts$heading = new Vec3(d.x / hLen, 0.0, d.z / hLen);

        java.util.Set<BlockPos> seen = new java.util.LinkedHashSet<>();
        int n = Math.max(1, (int) Math.min(64, Math.ceil(len / 0.25)));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            BlockPos found = ultimate_minecarts$activatorAt(
                    from.x + d.x * t, from.y + d.y * t, from.z + d.z * t);
            if (found != null) seen.add(found);
        }

        if (ultimate_minecarts$lastActivator != null && !seen.contains(ultimate_minecarts$lastActivator)) {
            ultimate_minecarts$lastActivator = null;
        }

        long now = serverLevel.getGameTime();
        for (BlockPos pos : seen) {
            boolean sameTile = pos.equals(ultimate_minecarts$lastActivator);
            if (sameTile && now - ultimate_minecarts$lastActivatorTick < ultimate_minecarts$ACTIVATOR_COOLDOWN) {
                continue;
            }
            ultimate_minecarts$lastActivator = pos;
            ultimate_minecarts$lastActivatorTick = now;
            ultimate_minecarts$activatorToggle(serverLevel);
        }
    }

    @Unique
    private void ultimate_minecarts$nudgeAwayFrom(Vec3 from) {
        if (fuel <= 0 || ultimate_minecarts$braked) return;

        Vec3 currentMovement = getDeltaMovement();
        if (currentMovement.horizontalDistanceSqr() > 1.0E-4) {
            push = currentMovement.normalize().scale(0.5);
            return;
        }

        Vec3 away = position().subtract(from);
        double hDist = away.horizontalDistance();
        push = hDist > 0.01
            ? new Vec3(away.x / hDist, 0, away.z / hDist).scale(0.5)
            : new Vec3(0, 0, 1).scale(0.5);
    }

    @Unique
    private void ultimate_minecarts$burnFuel(ServerLevel serverLevel, ItemStack burned, int fuelTime) {
        if (burned.getItem() instanceof BucketItem) {
            serverLevel.playSound(null, blockPosition(),
                    SoundEvents.LAVA_EXTINGUISH,
                    SoundSource.BLOCKS, 0.5f, 0.8f + random.nextFloat() * 0.4f);
            serverLevel.playSound(null, blockPosition(),
                    SoundEvents.BUCKET_EMPTY_LAVA,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        if (burned.is(Items.BLAZE_ROD) || burned.is(Items.COAL_BLOCK)) {
            serverLevel.playSound(null, blockPosition(),
                    SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 1.0f, 0.8f + random.nextFloat() * 0.4f);
        }

        fuel = (int) Math.min(
                UltimateMinecartsConfig.get().furnaceMaxBurnTime,
                fuel + fuelTime);

        double scale = Math.min(5.0, Math.max(1.0, fuelTime / 1600.0));
        int particleCount = (int)(2 * scale);

        for (int i = 0; i < particleCount; i++) {
            serverLevel.sendParticles(ParticleTypes.LAVA,
                getX() + (random.nextFloat() - 0.5) * 0.3 * scale,
                getY() + 0.4 + random.nextFloat() * 0.3,
                getZ() + (random.nextFloat() - 0.5) * 0.3 * scale,
                1, 0.0, 0.0, 0.0, 0.0);
        }
        serverLevel.playSound(null, getX(), getY(), getZ(),
            SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL,
            (float)(0.6 * Math.min(2.0, scale)),
            1.7f + random.nextFloat() * 0.3f);

        ultimate_minecarts$syncLit();
    }

    @Unique
    public boolean ultimate_minecarts$acceptFuel(ItemStack stack) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        int fuelTime = ultimate_minecarts$fuelValue(stack);
        if (fuelTime <= 0) return false;
        if (fuel >= UltimateMinecartsConfig.get().furnaceMaxBurnTime) return false;
        ultimate_minecarts$burnFuel(serverLevel, stack, fuelTime);
        AbstractMinecart feeder = ((Linkable) (Object) this).getLinkedChild();
        if (feeder != null) ultimate_minecarts$nudgeAwayFrom(feeder.position());
        return true;
    }

    @Unique
    public boolean ultimate_minecarts$acceptCoolant(ItemStack stack) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        if (fuel <= 0) return false;

        int drain;
        float volume;
        float pitch;
        if (stack.is(Items.WATER_BUCKET)) {
            drain = UltimateMinecartsConfig.get().waterBucket;
            volume = 1.0f;
            pitch = 0.8f + random.nextFloat() * 0.4f;
        } else if (ultimate_minecarts$isWaterBottle(stack)) {
            drain = UltimateMinecartsConfig.get().waterBottle;
            volume = 0.8f;
            pitch = 1.2f + random.nextFloat() * 0.4f;
        } else {
            return false;
        }

        fuel = Math.max(0, fuel - drain);
        serverLevel.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, volume, pitch);
        for (int i = 0; i < 8; i++) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX() + (random.nextFloat() - 0.5) * 0.5,
                    getY() + 0.5 + random.nextFloat() * 0.5,
                    getZ() + (random.nextFloat() - 0.5) * 0.5,
                    1, 0.0, 0.05, 0.0, 0.0);
        }
        ultimate_minecarts$syncLit();
        return true;
    }

    @Unique private static final EntityDataAccessor<Boolean> ultimate_minecarts$DATA_BRAKED =
            SynchedEntityData.defineId(MinecartFurnace.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void ultimate_minecarts$defineBrakeData(SynchedEntityData.Builder entityData, CallbackInfo ci) {
        entityData.define(ultimate_minecarts$DATA_BRAKED, false);
    }

    @Unique
    public boolean ultimate_minecarts$isBraked() {
        return entityData.get(ultimate_minecarts$DATA_BRAKED);
    }

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    public void ultimate_minecarts$increaseSpeed(ServerLevel level, CallbackInfoReturnable<Double> info) {
        if (!ultimate_minecarts$onRail()) return;
        double top = UltimateMinecartsConfig.getFurnaceMinecartSpeed();
        if (ultimate_minecarts$isBurning()) {
            info.setReturnValue(top);
        } else {
            double coast = Math.max(UltimateMinecartsConfig.getOtherMinecartSpeed(), ultimate_minecarts$headSpeed);
            info.setReturnValue(Math.min(top, coast));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void ultimate_minecarts$capturePreTickSpeed(CallbackInfo info) {
        if (!(level() instanceof ServerLevel)) return;
        ultimate_minecarts$headSpeed = getDeltaMovement().horizontalDistance();
        ultimate_minecarts$headPos = position();
        ultimate_minecarts$preTick_speedBPS = ultimate_minecarts$headSpeed * 20.0;
        ultimate_minecarts$currentSpeed = ultimate_minecarts$headSpeed;
        ultimate_minecarts$fuelAtHead = fuel;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void ultimate_minecarts$drainFuel(CallbackInfo info) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        entityData.set(ultimate_minecarts$DATA_BRAKED, ultimate_minecarts$braked);

        boolean send;
        if (ultimate_minecarts$braked || ultimate_minecarts$onLivePoweredRail()) {
            fuel = ultimate_minecarts$fuelAtHead;
            send = fuel != ultimate_minecarts$lastSyncedFuel;
        } else if (fuel > 0) {
            fuel = Math.max(0, fuel - 39);
            ultimate_minecarts$fuelJustEmptied = true;
            send = true;
        } else if (ultimate_minecarts$fuelJustEmptied) {
            ultimate_minecarts$fuelJustEmptied = false;
            send = true;
        } else {
            send = false;
        }

        ultimate_minecarts$syncLit();

        if (!send) return;
        ultimate_minecarts$lastSyncedFuel = fuel;
        ClientboundSyncFurnaceFuelPacket packet = new ClientboundSyncFurnaceFuelPacket(getId(), fuel);
        for (ServerPlayer player : PlayerLookup.tracking(serverLevel, blockPosition())) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    @Inject(method = "applyNaturalSlowdown", at = @At("HEAD"), cancellable = true)
    public void ultimate_minecarts$useNormalSlowdown(Vec3 deltaMovement, CallbackInfoReturnable<Vec3> cir) {
        boolean burning = ultimate_minecarts$isBurning();
        double slowdownFactor = burning ? 1.0
                : ultimate_minecarts$braked ? ultimate_minecarts$BRAKE_SLOWDOWN
                : UltimateMinecartsConfig.MINECART_SLOWDOWN;

        Vec3 base = deltaMovement;
        if (burning && ultimate_minecarts$onDeadRail()) {
            Vec3 own = getDeltaMovement();
            double ownH = own.horizontalDistance();
            double dh = deltaMovement.horizontalDistance();
            if (ownH > 1e-5) {
                if (dh > 1e-5) {
                    double s = ownH / dh;
                    base = new Vec3(deltaMovement.x * s, deltaMovement.y, deltaMovement.z * s);
                } else {
                    base = new Vec3(own.x, deltaMovement.y, own.z);
                }
            }
        }

        Vec3 result = base.multiply(slowdownFactor, 0.0, slowdownFactor);
        if (isInWater()) result = result.scale(0.95F);

        if (ultimate_minecarts$braked
                && result.horizontalDistance() < ultimate_minecarts$BRAKE_HOLD_SPEED
                && !ultimate_minecarts$onLivePoweredRail()) {
            result = Vec3.ZERO;
        }

        cir.setReturnValue(result);
    }

@Inject(method = "tick", at = @At("TAIL"))
    public void ultimate_minecarts$fuelPropulsion(CallbackInfo info) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        if (CartKnockback.isHeld(this, serverLevel.getGameTime())) {
            Vec3 hv = getDeltaMovement();
            setDeltaMovement(0.0, hv.y, 0.0);
            return;
        }

        if (!ultimate_minecarts$isBurning() || !ultimate_minecarts$onRail()) return;
        
        ultimate_minecarts$keepHeading();

        double top = UltimateMinecartsConfig.getFurnaceMinecartSpeed();
        double head = ultimate_minecarts$headSpeed;
        double moved = Math.min(position().subtract(ultimate_minecarts$headPos).horizontalDistance(), head * 1.25);
        double gain = moved / ultimate_minecarts$BLOCKS_PER_BPS / 20.0;
        double allowed = Math.min(top, Math.max(ultimate_minecarts$KICK, head + gain));

        Vec3 v = getDeltaMovement();
        double h = v.horizontalDistance();
        if (h > 1e-5) {
            double s = allowed / h;
            setDeltaMovement(v.x * s, v.y, v.z * s);
        } else if (push.lengthSqr() > 1e-7) {
            Vec3 dir = push.normalize();
            setDeltaMovement(dir.x * allowed, v.y, dir.z * allowed);
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

    @Inject(method = "tick", at = @At("TAIL"))
    public void ultimate_minecarts$activatorBrake(CallbackInfo info) {
        if (!(level() instanceof ServerLevel serverLevel) || isRemoved()) return;
        ultimate_minecarts$checkActivators(serverLevel);
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
        boolean isWaterBucket = stack.is(Items.WATER_BUCKET);
        boolean isWaterBottle = ultimate_minecarts$isWaterBottle(stack);
        int fuelAmount = ultimate_minecarts$fuelValue(stack);

        if (isWaterBucket && fuel > 0) {
            fuel = Math.max(0, fuel - UltimateMinecartsConfig.get().waterBucket);
            ReturnItem.giveBack(player, hand, new ItemStack(Items.BUCKET));

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

            ultimate_minecarts$syncLit();
            info.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        if (isWaterBottle && fuel > 0) {
            fuel = Math.max(0, fuel - UltimateMinecartsConfig.get().waterBottle);
            ReturnItem.giveBack(player, hand, new ItemStack(Items.GLASS_BOTTLE));

            level().playSound(player, blockPosition(),
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 0.8f, 1.2f + random.nextFloat() * 0.4f);

            if (level() instanceof ServerLevel sl) {
                for (int i = 0; i < 8; i++) {
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX() + (random.nextFloat() - 0.5) * 0.5,
                        getY() + 0.5 + random.nextFloat() * 0.5,
                        getZ() + (random.nextFloat() - 0.5) * 0.5,
                        1, 0.0, 0.05, 0.0, 0.0);
                }
            }

            ultimate_minecarts$syncLit();
            info.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        if (fuelAmount == 0 && stack.has(DataComponents.COOKING_FUEL)) {
            info.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (fuelAmount > 0 && level() instanceof ServerLevel serverLevel) {
            int fuelTime = fuelAmount;

            if (fuel >= UltimateMinecartsConfig.get().furnaceMaxBurnTime) {
                info.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            ItemStack burned = stack.copyWithCount(1);

            if (!player.isCreative() && fuelTime > 0) {
                if (stack.getItem() instanceof BucketItem) {
                    ReturnItem.giveBack(player, hand, new ItemStack(Items.BUCKET));
                } else {
                    stack.shrink(1);
                }
            }

            ultimate_minecarts$burnFuel(serverLevel, burned, fuelTime);
        }

        if (fuelAmount == 0 && !stack.has(DataComponents.COOKING_FUEL)
                && !isWaterBucket && !isWaterBottle
                && level() instanceof ServerLevel serverLevel) {
            ultimate_minecarts$toggleBrake(serverLevel);
        }

        ultimate_minecarts$nudgeAwayFrom(player.position());

        info.setReturnValue(InteractionResult.SUCCESS);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    public void ultimate_minecarts$writeNbt(ValueOutput output, CallbackInfo info) {
        output.putInt("RealFuel", fuel);
        long packed = ((long) prevChunkX << 32) | (prevChunkZ & 0xFFFFFFFFL);
        output.putLong("PrevChunkPos", packed);
        output.putDouble("CurrentSpeed", ultimate_minecarts$currentSpeed);
        output.putBoolean("Braked", ultimate_minecarts$braked);
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
        ultimate_minecarts$braked = input.getBooleanOr("Braked", false);
    }
}