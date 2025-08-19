package com.leon.lightningdragon.client.renderer;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.entity.projectile.LightningBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renderer for lightning ball projectiles
 * Creates a glowing electric sphere effect
 */
public class LightningBallRenderer extends EntityRenderer<LightningBallEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LightningDragonMod.MOD_ID, "textures/lightning_ball.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    public LightningBallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LightningBallEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {

        poseStack.pushPose();

        // Scale based on entity age for growing effect
        float scale = 0.8f + (entity.tickCount % 20) * 0.02f; // Subtle pulsing
        poseStack.scale(scale, scale, scale);

        // Rotate to face camera
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        // Get vertex consumer for the glowing render type
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();

        // Render as a billboard quad
        renderQuad(matrix4f, matrix3f, vertexConsumer); // Full bright

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void renderQuad(Matrix4f matrix4f, Matrix3f matrix3f, VertexConsumer vertexConsumer) {
        float size = 0.5f;

        // Create a glowing quad
        vertexConsumer.vertex(matrix4f, -size, -size, 0.0F)
                .color(100, 150, 255, 200) // Electric blue with alpha
                .uv(0.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                .endVertex();

        vertexConsumer.vertex(matrix4f, size, -size, 0.0F)
                .color(100, 150, 255, 200)
                .uv(1.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                .endVertex();

        vertexConsumer.vertex(matrix4f, size, size, 0.0F)
                .color(100, 150, 255, 200)
                .uv(1.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                .endVertex();

        vertexConsumer.vertex(matrix4f, -size, size, 0.0F)
                .color(100, 150, 255, 200)
                .uv(0.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull LightningBallEntity entity) {
        return TEXTURE;
    }
}