package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.api.ChainRenderStateAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.AbstractMinecartRenderer")
public class ChainExtractRenderStateMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void ultimate_minecarts$extractChainState(Entity entity,
                                                    EntityRenderState state,
                                                    float tickDelta,
                                                    CallbackInfo info) {
        if (!(entity instanceof AbstractMinecart minecart)) return;
        if (!(state instanceof MinecartRenderState minecartState)) return;

        ChainRenderStateAccessor accessor = (ChainRenderStateAccessor)(Object) minecartState;
        AbstractMinecart linkedChild = minecart instanceof Linkable l ? l.getLinkedChild() : null;
        accessor.ultimate_minecarts$setSelfUUID(minecart.getUUID());

        if (linkedChild != null && ((Linkable) linkedChild).getLinkedParent() == minecart) {
            double selfX = Mth.lerp(tickDelta, minecart.xOld, minecart.getX());
            double selfY = Mth.lerp(tickDelta, minecart.yOld, minecart.getY());
            double selfZ = Mth.lerp(tickDelta, minecart.zOld, minecart.getZ());

            double childX = Mth.lerp(tickDelta, linkedChild.xOld, linkedChild.getX());
            double childY = Mth.lerp(tickDelta, linkedChild.yOld, linkedChild.getY());
            double childZ = Mth.lerp(tickDelta, linkedChild.zOld, linkedChild.getZ());

            accessor.ultimate_minecarts$setParentX(childX - selfX);
            accessor.ultimate_minecarts$setParentY(childY - selfY);
            accessor.ultimate_minecarts$setParentZ(childZ - selfZ);
            accessor.ultimate_minecarts$setHasParent(true);
            accessor.ultimate_minecarts$setChainItem(((Linkable) linkedChild).getLinkedChain());
        } else {
            accessor.ultimate_minecarts$setHasParent(false);
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            double selfX = Mth.lerp(tickDelta, minecart.xOld, minecart.getX());
            double selfY = Mth.lerp(tickDelta, minecart.yOld, minecart.getY());
            double selfZ = Mth.lerp(tickDelta, minecart.zOld, minecart.getZ());
            accessor.ultimate_minecarts$setPlayerDX(client.player.getX() - selfX);
            accessor.ultimate_minecarts$setPlayerDY(client.player.getEyeY() - 0.5 - selfY);
            accessor.ultimate_minecarts$setPlayerDZ(client.player.getZ() - selfZ);
        }
    }
}