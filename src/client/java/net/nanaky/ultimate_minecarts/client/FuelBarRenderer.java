package net.nanaky.ultimate_minecarts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.nanaky.ultimate_minecarts.api.FuelRenderStateAccessor;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class FuelBarRenderer {

    private static final Identifier BG_TEXTURE =
        Identifier.fromNamespaceAndPath("ultimate_minecarts",
            "textures/gui/sprites/fuel_bar/fuel_bar_background.png");
    private static final Identifier FILL_TEXTURE =
        Identifier.fromNamespaceAndPath("ultimate_minecarts",
            "textures/gui/sprites/fuel_bar/fuel_bar_progress.png");

    private static final float BAR_W = 8f;
    private static final float BAR_H = 60f;
    private static final float NAME_TAG_Y_FALLBACK = 1.2f;

    private FuelBarRenderer() {}

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

        if (mc.player == null) return;
        Entity entity = mc.level.getEntity(accessor.ultimate_minecarts$getEntityId());
        if (entity == null) return;
        if (mc.player.distanceToSqr(entity) > 9.0) return;
        if (!(entity instanceof IFurnaceMinecart)) return;
        int liveFuel = accessor.ultimate_minecarts$getFuel();
        int maxFuel  = accessor.ultimate_minecarts$getMaxFuel();
        float nameTagY = accessor.ultimate_minecarts$getNameTagY();

        FuelBarRenderer.render(liveFuel, maxFuel, nameTagY, poseStack, collector, minecartState.lightCoords);
    }

    public static void render(int fuel, int maxFuel, float nameTagY,
                              PoseStack poseStack,
                              SubmitNodeCollector collector,
                              int packedLight) {

        if (nameTagY == 0f) nameTagY = NAME_TAG_Y_FALLBACK;
        if (fuel < 0 || maxFuel <= 0) return;

        float progress = (float) fuel / maxFuel;

        poseStack.pushPose();
        poseStack.translate(0, nameTagY - 0.5, 0);
        poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        poseStack.scale(0.025f, -0.025f, 0.025f);
        poseStack.translate(0, 0, 32f);

        float x0 = -BAR_H / 2f;
        float x1 =  BAR_H / 2f;
        float y0 = -BAR_W / 6f;
        float y1 =  BAR_W / 6f;

        RenderType bgType = RenderTypes.entityCutout(BG_TEXTURE);
        collector.submitCustomGeometry(poseStack, bgType, (pose, vc) ->
            blitQuad(vc, pose, x0, y0, x1, y1, 0f, 1f, 0f, 1f, packedLight, 0f));

        if (progress > 0f) {
            float fillW = BAR_H * progress;
            float uMax  = progress;
            RenderType fillType = RenderTypes.entityCutout(FILL_TEXTURE);
            collector.submitCustomGeometry(poseStack, fillType, (pose, vc) ->
                blitQuad(vc, pose, x0, y0, x0 + fillW, y1, 0f, uMax, 0f, 1f, packedLight, 0.001f));
        }

        poseStack.popPose();
    }

    private static void blitQuad(VertexConsumer vc, PoseStack.Pose entry,
                                  float x0, float y0, float x1, float y1,
                                  float u0, float u1, float v0, float v1,
                                  int light, float z) {
        Matrix4f m = entry.pose();
        vc.addVertex(m, x0, y0, z).setColor(255, 255, 255, 255).setUv(u0, v0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, 0f, 1f);
        vc.addVertex(m, x0, y1, z).setColor(255, 255, 255, 255).setUv(u0, v1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, 0f, 1f);
        vc.addVertex(m, x1, y1, z).setColor(255, 255, 255, 255).setUv(u1, v1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, 0f, 1f);
        vc.addVertex(m, x1, y0, z).setColor(255, 255, 255, 255).setUv(u1, v0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, 0f, 1f);
    }
}