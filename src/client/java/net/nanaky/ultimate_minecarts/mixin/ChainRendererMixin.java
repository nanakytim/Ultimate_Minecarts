package net.nanaky.ultimate_minecarts.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.nanaky.ultimate_minecarts.api.ChainRenderStateAccessor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "net.minecraft.client.renderer.entity.EntityRenderer", priority = 900)
public class ChainRendererMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("TAIL")
    )
    public void ultimate_minecarts$render(EntityRenderState state, PoseStack stack,
                                          SubmitNodeCollector collector,
                                          net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
                                          CallbackInfo info) {
        if (!(state instanceof MinecartRenderState minecartState)) return;

        ChainRenderStateAccessor accessor = (ChainRenderStateAccessor)(Object) minecartState;
        int light = minecartState.lightCoords;

        // --- Existing parent chain ---
        if (accessor.ultimate_minecarts$hasParent()) {
            float dx = (float) -accessor.ultimate_minecarts$parentX();
            float dy = (float) -accessor.ultimate_minecarts$parentY();
            float dz = (float) -accessor.ultimate_minecarts$parentZ();
            float distance = Mth.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance >= 0.01f) {
                float length = distance - 1f;
                if (length > 0) {
                    double hAngle = Math.toDegrees(Math.atan2(dz, dx));
                    hAngle += Math.ceil(-hAngle / 360.0) * 360.0;
                    double vAngle = Math.asin(Mth.clamp(dy / distance, -1f, 1f));

                    RenderType chainLayer = RenderTypes.entityCutout(
                        ultimate_minecarts$chainTexture(accessor.ultimate_minecarts$chainItem()));

                    final float finalLength = length;
                    final int finalLight = light;

                    stack.pushPose();
                    stack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-(float)hAngle - 90f)));
                    stack.mulPose(new Quaternionf().rotationX((float) vAngle));
                    stack.translate(0, 0, 0.5);

                    collector.submitCustomGeometry(stack, chainLayer, (pose, vc) -> {
                        Matrix4f m = pose.pose();
                        ultimate_minecarts$emitQuad(vc, m, pose,
                            0f, 0.25f,
                            Mth.sin(Mth.TWO_PI) * 0.125f, Mth.cos(Mth.TWO_PI) * 0.125f,
                            0f, 0.1875f, 0f, finalLength / 10f,
                            finalLight, finalLength);
                    });

                    stack.translate(0.19, 0.19, 0);
                    stack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(90)));

                    collector.submitCustomGeometry(stack, chainLayer, (pose, vc) -> {
                        Matrix4f m = pose.pose();
                        ultimate_minecarts$emitQuad(vc, m, pose,
                            0f, 0.25f,
                            Mth.sin(Mth.TWO_PI) * 0.125f, Mth.cos(Mth.TWO_PI) * 0.125f,
                            0f, 0.1875f, 0f, finalLength / 10f,
                            finalLight, finalLength);
                    });

                    stack.popPose();
                }
            }
        }

        // --- Pending link chain: cart → player ---
        // Requires ultimate_minecarts$selfUUID() on ChainRenderStateAccessor,
        // populated wherever you set parentX/Y/Z in your render state mixin.
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        UUID selfUUID = accessor.ultimate_minecarts$selfUUID();
        if (selfUUID == null) return;

        UUID pendingUUID = null;
        for (InteractionHand hand : InteractionHand.values()) {
            UUID u = client.player.getItemInHand(hand).get(UltimateMinecarts.PARENT_ID);
            if (u != null) { pendingUUID = u; break; }
        }
        if (!selfUUID.equals(pendingUUID)) return;

        // Delta from cart (origin in pose space) to player
        float pdx = (float) -accessor.ultimate_minecarts$playerDX();
        float pdy = (float) -accessor.ultimate_minecarts$playerDY();
        float pdz = (float) -accessor.ultimate_minecarts$playerDZ();
        float pdist = Mth.sqrt(pdx * pdx + pdy * pdy + pdz * pdz);
        if (pdist < 0.01f) return;

        float plength = pdist - 0.5f;
        if (plength <= 0) return;

        double phAngle = Math.toDegrees(Math.atan2(pdz, pdx));
        phAngle += Math.ceil(-phAngle / 360.0) * 360.0;
        double pvAngle = Math.asin(Mth.clamp(pdy / pdist, -1f, 1f));

        RenderType pendingLayer = RenderTypes.entityCutout(
        ultimate_minecarts$chainTexture(client.player.getItemInHand(InteractionHand.MAIN_HAND).get(UltimateMinecarts.PARENT_ID) != null
            ? client.player.getItemInHand(InteractionHand.MAIN_HAND).getItem()
            : client.player.getItemInHand(InteractionHand.OFF_HAND).getItem()));

        final float finalPLength = plength;
        final int finalPLight = light;

        stack.pushPose();
        stack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-(float)phAngle - 90f)));
        stack.mulPose(new Quaternionf().rotationX((float) pvAngle));
        stack.translate(0, 0, 0.5);

        collector.submitCustomGeometry(stack, pendingLayer, (pose, vc) -> {
            Matrix4f m = pose.pose();
            ultimate_minecarts$emitQuad(vc, m, pose,
                0f, 0.25f,
                Mth.sin(Mth.TWO_PI) * 0.125f, Mth.cos(Mth.TWO_PI) * 0.125f,
                0f, 0.1875f, 0f, finalPLength / 10f,
                finalPLight, finalPLength);
        });

        stack.translate(0.19, 0.19, 0);
        stack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(90)));

        collector.submitCustomGeometry(stack, pendingLayer, (pose, vc) -> {
            Matrix4f m = pose.pose();
            ultimate_minecarts$emitQuad(vc, m, pose,
                0f, 0.25f,
                Mth.sin(Mth.TWO_PI) * 0.125f, Mth.cos(Mth.TWO_PI) * 0.125f,
                0f, 0.1875f, 0f, finalPLength / 10f,
                finalPLight, finalPLength);
        });

        stack.popPose();
    }

    @Unique
    private static Identifier ultimate_minecarts$chainTexture(Item chainItem) {
        var key = BuiltInRegistries.ITEM.getKey(chainItem);
        if (key == null) return UltimateMinecarts.id("textures/entity/iron_chain.png");

        String path = key.getPath();
        return switch (path) {
            case "copper_chain",
                "waxed_copper_chain"           -> UltimateMinecarts.id("textures/entity/copper_chain.png");
            case "exposed_copper_chain",
                "waxed_exposed_copper_chain"   -> UltimateMinecarts.id("textures/entity/exposed_copper_chain.png");
            case "weathered_copper_chain",
                "waxed_weathered_copper_chain" -> UltimateMinecarts.id("textures/entity/weathered_copper_chain.png");
            case "oxidized_copper_chain",
                "waxed_oxidized_copper_chain"  -> UltimateMinecarts.id("textures/entity/oxidized_copper_chain.png");
            default                             -> UltimateMinecarts.id("textures/entity/iron_chain.png");
        };
    }

    @Unique
    private static void ultimate_minecarts$emitQuad(VertexConsumer vc, Matrix4f m, PoseStack.Pose entry,
                                                    float vx1, float vy1, float vx2, float vy2,
                                                    float minU, float maxU, float minV, float maxV,
                                                    int light, float length) {
        vc.addVertex(m, vx1, vy1, 0f)
          .setColor(255, 255, 255, 255).setUv(minU, minV)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, -1f, 0f);
        vc.addVertex(m, vx1, vy1, length)
          .setColor(255, 255, 255, 255).setUv(minU, maxV)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, -1f, 0f);
        vc.addVertex(m, vx2, vy2, length)
          .setColor(255, 255, 255, 255).setUv(maxU, maxV)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, -1f, 0f);
        vc.addVertex(m, vx2, vy2, 0f)
          .setColor(255, 255, 255, 255).setUv(maxU, minV)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0f, -1f, 0f);
    }
}