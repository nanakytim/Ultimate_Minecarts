package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.nanaky.ultimate_minecarts.common.blocks.FurnaceMinecartModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartFurnace.class)
public abstract class FurnaceMinecartMixin {

    @Shadow protected abstract boolean hasFuel();
    @Shadow private int fuel;

    @Unique private Direction um$lastFacing = null;

    @Unique
    private static final Direction[] SECTOR_TO_FACING = {
        Direction.SOUTH,
        Direction.NORTH,
        Direction.NORTH,
        Direction.NORTH,
        Direction.NORTH,
        Direction.SOUTH,
        Direction.SOUTH,
        Direction.SOUTH,
    };

    private MinecartFurnace self() { return (MinecartFurnace)(Object)this; }

    @Inject(method = "tick", at = @At("HEAD"))
    private void um$tick(CallbackInfo ci) {
        MinecartFurnace cart = self();
        Vec3 movement = cart.getDeltaMovement();
        if (movement.horizontalDistanceSqr() < 4.0E-4) return;

        double angleDeg   = Math.toDegrees(Math.atan2(movement.x, -movement.z));
        double normalised = ((angleDeg % 360) + 360) % 360;
        int sector        = (int)((normalised + 22.5) / 45) % 8;
        um$lastFacing = SECTOR_TO_FACING[sector];
    }

    @Inject(method = "getDefaultDisplayBlockState", at = @At("HEAD"), cancellable = true)
    private void useCustomModel(CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(
            UltimateMinecarts.ULTIMATE_MINECART.defaultBlockState()
                .setValue(FurnaceMinecartModel.LIT, this.hasFuel())
                .setValue(FurnaceMinecartModel.FACING, um$lastFacing != null ? um$lastFacing : Direction.NORTH)
        );
    }

    @Inject(method = "interact", at = @At("HEAD"))
    private void onInteract(Player player, InteractionHand hand, Vec3 location,
                            CallbackInfoReturnable<InteractionResult> cir) {
        MinecartFurnace cart = self();
        if (cart.level().isClientSide()) return;
        if (!(cart.level() instanceof ServerLevel serverLevel)) return;

        ItemStack itemStack = player.getItemInHand(hand);
        if (!itemStack.is(net.minecraft.tags.ItemTags.FURNACE_MINECART_FUEL)) return;
        if (fuel + 3600 > 32000) return;

        serverLevel.sendParticles(ParticleTypes.LAVA,
            cart.getX(), cart.getY() + 0.5, cart.getZ(),
            1, 0.0, 0.0, 0.0, 0.0);
        serverLevel.sendParticles(ParticleTypes.LAVA,
            cart.getX() + 0.15, cart.getY() + 0.4, cart.getZ() + 0.15,
            1, 0.15, 0.05, 0.15, 0.3);

        serverLevel.playSound(null,
            cart.getX(), cart.getY(), cart.getZ(),
            SoundEvents.FIRECHARGE_USE,
            SoundSource.NEUTRAL,
            0.6f,
            1.7f + cart.getRandom().nextFloat() * 0.3f);
}
}