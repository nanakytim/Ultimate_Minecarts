package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.common.utils.ChainConstraint;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {

    protected NewMinecartBehaviorMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void ultimate_minecarts$modMaxSpeed(ServerLevel level, CallbackInfoReturnable<Double> cir) {
        double ceiling = Math.max(
                UltimateMinecartsConfig.getFurnaceMinecartSpeed(),
                UltimateMinecartsConfig.getOtherMinecartSpeed());
        cir.setReturnValue(ceiling * (this.minecart.isInWater() ? 0.5 : 1.0));
    }

    @Inject(method = "stepAlongTrack", at = @At("RETURN"))
    private void ultimate_minecarts$recordStep(BlockPos pos, RailShape shape, double movementLeft,
                                               CallbackInfoReturnable<Double> cir) {
        if (!(this.level() instanceof ServerLevel)) return;
    }

    @Inject(method = "getSlowdownFactor", at = @At("HEAD"), cancellable = true)
    private void ultimate_minecarts$firmSlowdown(CallbackInfoReturnable<Double> cir) {
        if (this.minecart instanceof MinecartFurnace) return;
        if (this.minecart instanceof Linkable l
                && (l.getLinkedParent() != null || l.getLinkedChild() != null)) return;
        cir.setReturnValue(UltimateMinecartsConfig.MINECART_SLOWDOWN);
    }

    @Inject(method = "calculateSlopeSpeed", at = @At("HEAD"), cancellable = true)
    private void ultimate_minecarts$noSlopeForChildren(Vec3 deltaMovement, RailShape shape,
                                                    CallbackInfoReturnable<Vec3> cir) {
        if (this.minecart instanceof Linkable l && l.getLinkedParent() != null) {
            cir.setReturnValue(deltaMovement);
        }
    }

    @Inject(method = "calculateBoostTrackSpeed", at = @At("RETURN"), cancellable = true)
    private void ultimate_minecarts$clampBoost(Vec3 deltaMovement, BlockPos pos, BlockState state,
                                               CallbackInfoReturnable<Vec3> cir) {
        Vec3 result = cir.getReturnValue();
        if (result == deltaMovement) return;

        if (this.minecart instanceof Linkable linkable && linkable.getLinkedParent() != null) {
            cir.setReturnValue(deltaMovement);
            return;
        }

        if (!(this.minecart.level() instanceof ServerLevel serverLevel)) return;
        double max = ((AbstractMinecartInvoker) (Object) this.minecart).invokeGetMaxSpeed(serverLevel);
        double h = result.horizontalDistance();
        if (h > max && h > 1e-7) {
            double s = max / h;
            cir.setReturnValue(new Vec3(result.x * s, result.y, result.z * s));
        }
    }
}