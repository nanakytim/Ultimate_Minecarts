package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.entity.Entity;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.FuelRenderStateAccessor;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.AbstractMinecartRenderer")
public class FuelExtractRenderStateMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void ultimate_minecarts$extractFuelState(Entity entity,
                                                      EntityRenderState state,
                                                      float tickDelta,
                                                      CallbackInfo info) {
        if (!(state instanceof MinecartRenderState minecartState)) return;
        FuelRenderStateAccessor accessor = (FuelRenderStateAccessor)(Object) minecartState;

        accessor.ultimate_minecarts$setEntityId(entity.getId());
        accessor.ultimate_minecarts$setNameTagY(1.2f);

        if (entity instanceof IFurnaceMinecart furnace) {
            accessor.ultimate_minecarts$setFuel(furnace.ultimate_minecarts$getFuel());
            accessor.ultimate_minecarts$setMaxFuel(UltimateMinecartsConfig.get().furnaceMaxBurnTime);
        } else {
            accessor.ultimate_minecarts$setFuel(-1);
        }
    }
}