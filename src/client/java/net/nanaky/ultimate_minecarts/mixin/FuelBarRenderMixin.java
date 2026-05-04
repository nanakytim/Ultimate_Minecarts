package net.nanaky.ultimate_minecarts.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.FuelRenderStateAccessor;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import net.nanaky.ultimate_minecarts.client.FuelBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.AbstractMinecartRenderer", priority = 900)
public class FuelBarRenderMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("TAIL")
    )
    private void ultimate_minecarts$renderFuelBar(EntityRenderState renderState,
                                                   PoseStack poseStack,
                                                   SubmitNodeCollector collector,
                                                   CameraRenderState cameraState,
                                                   CallbackInfo ci) {
        if (!(renderState instanceof MinecartRenderState minecartState)) return;
        FuelRenderStateAccessor accessor = (FuelRenderStateAccessor)(Object) minecartState;
        if (accessor.ultimate_minecarts$getFuel() < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.crosshairPickEntity == null) return;
        if (mc.crosshairPickEntity.getId() != accessor.ultimate_minecarts$getEntityId()) return;

        if (mc.player == null) return;
        Entity entity = mc.level.getEntity(accessor.ultimate_minecarts$getEntityId());
        if (entity == null) return;
        if (mc.player.distanceToSqr(entity) > 9.0) return;
        if (!(entity instanceof IFurnaceMinecart furnace)) return;

        int liveFuel = furnace.ultimate_minecarts$getFuel();
        int maxFuel  = UltimateMinecartsConfig.get().furnaceMaxBurnTime;
        float nameTagY = accessor.ultimate_minecarts$getNameTagY();

        FuelBarRenderer.render(liveFuel, maxFuel, nameTagY, poseStack, collector, minecartState.lightCoords);
    }
}