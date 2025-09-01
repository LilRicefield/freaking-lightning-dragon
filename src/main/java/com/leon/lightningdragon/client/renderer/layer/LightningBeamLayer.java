package com.leon.lightningdragon.client.renderer.layer;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Simple client beam layer: renders a thin tube from beam_origin to synced end.
 * Visual only; server handles damage. Replace with fancier mesh later.
 */
public class LightningBeamLayer extends GeoRenderLayer<LightningDragonEntity> {
    private static final ResourceLocation BEAM_TEX = ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/lightning_ball.png");

    public LightningBeamLayer() { super(null); }

    @Override
    public void render(@NotNull PoseStack poseStack, LightningDragonEntity animatable, BakedGeoModel bakedModel,
                       @NotNull RenderType renderType, @NotNull MultiBufferSource bufferSource, @NotNull VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        if (!animatable.isBeaming()) return;
        var end = animatable.getClientBeamEndPosition(partialTick);
        if (end == null) return;

        // Use server-synced beam start world position (matches damage origin)
        net.minecraft.world.phys.Vec3 mouthWorld = animatable.getClientBeamStartPosition(partialTick);
        if (mouthWorld == null) mouthWorld = animatable.computeHeadMouthOrigin(partialTick);

        // Transform into model space relative to entity origin for poseStack use
        // Convert world positions to render-local by subtracting lerped entity origin and dividing by model scale
        double ox = net.minecraft.util.Mth.lerp(partialTick, animatable.xo, animatable.getX());
        double oy = net.minecraft.util.Mth.lerp(partialTick, animatable.yo, animatable.getY());
        double oz = net.minecraft.util.Mth.lerp(partialTick, animatable.zo, animatable.getZ());
        float scale = com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity.MODEL_SCALE;
        float mx = (float) ((mouthWorld.x - ox) / scale);
        float my = (float) ((mouthWorld.y - oy) / scale);
        float mz = (float) ((mouthWorld.z - oz) / scale);
        float ex = (float) ((end.x - ox) / scale);
        float ey = (float) ((end.y - oy) / scale);
        float ez = (float) ((end.z - oz) / scale);

        // Direction and length (entity-local)
        Vector3f dir = new Vector3f(ex - mx, ey - my, ez - mz);
        float len = dir.length();
        if (len <= 0.001f) return;
        dir.normalize();

        // Build a simple cross-beam (two quads) aligned along dir using pose transforms
        poseStack.pushPose();
        poseStack.translate(mx, my, mz);

        // Compute yaw/pitch from dir and rotate so local +Z points along dir
        float yaw = (float) Math.atan2(dir.x, dir.z);
        float pitch = (float) Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
        // Rotate Y by +yaw, then X by -pitch (standard look-at)
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotation(-pitch));

        // No debug logging

        float halfWidth = 0.15f;
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(BEAM_TEX));
        Matrix4f m = poseStack.last().pose();

        // Quad A
        put(vc, m, -halfWidth, 0, 0,   0, 0, packedLight);
        put(vc, m,  halfWidth, 0, 0,   1, 0, packedLight);
        put(vc, m,  halfWidth, 0, len, 1, 1, packedLight);
        put(vc, m, -halfWidth, 0, len, 0, 1, packedLight);

        // Rotate 90 degrees around Z for Quad B
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation((float)Math.toRadians(90)));
        m = poseStack.last().pose();
        put(vc, m, -halfWidth, 0, 0,   0, 0, packedLight);
        put(vc, m,  halfWidth, 0, 0,   1, 0, packedLight);
        put(vc, m,  halfWidth, 0, len, 1, 1, packedLight);
        put(vc, m, -halfWidth, 0, len, 0, 1, packedLight);

        poseStack.popPose();
    }

    private static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int light) {
        vc.vertex(m, x, y, z)
          .color(255, 255, 255, 200)
          .uv(u, v)
          .overlayCoords(OverlayTexture.NO_OVERLAY)
          .uv2(light)
          .normal(0, 0, 1)
          .endVertex();
    }

    private static void drawCube(VertexConsumer vc, Matrix4f m, float size, int light) {
        float h = size * 0.5f;
        // Front (0,0,1)
        putFace(vc, m, -h, -h,  h,  h, -h,  h,  h,  h,  h, -h,  h,  h, 0,0, light, 0,0,1);
        // Back (0,0,-1)
        putFace(vc, m,  h, -h, -h, -h, -h, -h, -h,  h, -h,  h,  h, -h, 0,0, light, 0,0,-1);
        // Left (-1,0,0)
        putFace(vc, m, -h, -h, -h, -h, -h,  h, -h,  h,  h, -h,  h, -h, 0,0, light, -1,0,0);
        // Right (1,0,0)
        putFace(vc, m,  h, -h,  h,  h, -h, -h,  h,  h, -h,  h,  h,  h, 0,0, light, 1,0,0);
        // Top (0,1,0)
        putFace(vc, m, -h,  h,  h,  h,  h,  h,  h,  h, -h, -h,  h, -h, 0,0, light, 0,1,0);
        // Bottom (0,-1,0)
        putFace(vc, m, -h, -h, -h,  h, -h, -h,  h, -h,  h, -h, -h,  h, 0,0, light, 0,-1,0);
    }

    private static void putFace(VertexConsumer vc, Matrix4f m,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float u, float v, int light,
                                float nx, float ny, float nz) {
        vc.vertex(m, x1, y1, z1).color(255,255,255,255).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx,ny,nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(255,255,255,255).uv(1,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx,ny,nz).endVertex();
        vc.vertex(m, x3, y3, z3).color(255,255,255,255).uv(1,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx,ny,nz).endVertex();
        vc.vertex(m, x4, y4, z4).color(255,255,255,255).uv(0,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx,ny,nz).endVertex();
    }
}
