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
 * Lightning beam render layer.
 * - Draws a segmented, jittered "ribbon cross" beam using a tileable lightning texture
 * - Adds simple start/end billboards as caps
 * Server handles damage; this is visual-only.
 */
public class LightningBeamLayer extends GeoRenderLayer<LightningDragonEntity> {
    // Core ribbon texture (64x64) you are creating
    private static final ResourceLocation RIBBON_TEX = ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_ribbon.png");
    // Cap sprite (reuses existing asset for now)
    private static final ResourceLocation CAP_TEX = ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_start_end.png");
    private static final float CAP_START_SCALE = 0.15f;
    private static final float CAP_END_SCALE = 0.15f;
    private static final float BEAM_HALF_WIDTH = 0.30f; // thickness of the ribbon

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

        // Build orthonormal basis around the beam direction (without mutating dir)
        Vector3f upRef = new Vector3f(0, 1, 0);
        if (Math.abs(dir.dot(upRef)) > 0.95f) upRef.set(1, 0, 0);
        Vector3f right = new Vector3f(dir).cross(upRef).normalize();
        Vector3f up = new Vector3f(right).cross(dir).normalize();

        // Visual parameters
        float halfWidth = BEAM_HALF_WIDTH; // thickness per ribbon
        float time = (animatable.tickCount + partialTick) * 0.04f;

        // Segmenting: repeat the 64x64 ribbon per segment so V stays 0..1
        int baseSegments = Math.max(8, Math.min(32, (int) (len * 6))); // density scales with length
        VertexConsumer ribbon = bufferSource.getBuffer(RenderType.entityTranslucent(RIBBON_TEX));
        Matrix4f mat = poseStack.last().pose();

        // Camera position in the same local/model space as our vertices
        var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 camWorld = cam.getPosition();
        float cx = (float) ((camWorld.x - ox) / scale);
        float cy = (float) ((camWorld.y - oy) / scale);
        float cz = (float) ((camWorld.z - oz) / scale);

        // Helper vectors reused per segment
        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();

        // For jitter
        // Disable jitter for a straight, stable ribbon
        float baseAmp = 0.0f;

        for (int i = 0; i < baseSegments; i++) {
            float s0 = (float) i / baseSegments;
            float s1 = (float) (i + 1) / baseSegments;

            // Base points along straight line
            p0.set(mx + (ex - mx) * s0, my + (ey - my) * s0, mz + (ez - mz) * s0);
            p1.set(mx + (ex - mx) * s1, my + (ey - my) * s1, mz + (ez - mz) * s1);

            // No jitter applied (kept hooks above if we re-enable later)

            // UV scroll per segment
            // Texture authored horizontally, but we want it vertical in-game: scroll along V
            // Invert and speed up scroll
            float vOffset = fract(-time * 5.0f);
            float u0 = 0f, u1 = 1f;
            float v0 = vOffset, v1 = vOffset + 1f; // relies on wrap; acceptable visual even if clamped

            // Single ribbon that faces camera around the beam axis
            float mxs = (p0.x + p1.x) * 0.5f;
            float mys = (p0.y + p1.y) * 0.5f;
            float mzs = (p0.z + p1.z) * 0.5f;
            // Stable single ribbon in a fixed plane around the beam: use precomputed 'right'
            Vector3f widthDir = new Vector3f(right);
            Vector3f w = widthDir.mul(halfWidth);

            submitQuad(ribbon, mat,
                    p0.x - w.x, p0.y - w.y, p0.z - w.z,
                    p0.x + w.x, p0.y + w.y, p0.z + w.z,
                    p1.x + w.x, p1.y + w.y, p1.z + w.z,
                    p1.x - w.x, p1.y - w.y, p1.z - w.z,
                    u0, v0, u1, v1, packedLight, 220);
        }

        // Start and end caps (start uses placeholder lightning_ball)
        drawCapBillboard(poseStack, bufferSource, mx, my, mz, packedLight, animatable, partialTick, true);
        drawCapBillboard(poseStack, bufferSource, ex, ey, ez, packedLight, animatable, partialTick, false);
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

    // Submit a quad with full UVs and light/alpha control
    private static void submitQuad(VertexConsumer vc, Matrix4f m,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float x4, float y4, float z4,
                                   float u0, float v0, float u1, float v1,
                                   int packedLight, int alpha) {
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, alpha).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, alpha).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, alpha).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        vc.vertex(m, x4, y4, z4).color(255, 255, 255, alpha).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
    }

    // Draws a camera-facing cap at a position
    private static void drawCapBillboard(PoseStack poseStack, MultiBufferSource bufferSource,
                                         float x, float y, float z, int packedLight,
                                         LightningDragonEntity entity, float partialTick, boolean isStart) {
        // Hide start cap for local first-person rider
        boolean hideStart = false;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (isStart && mc.player != null && entity.getControllingPassenger() == mc.player && mc.options.getCameraType().isFirstPerson()) {
            hideStart = true;
        }
        if (hideStart) return;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        // Face camera
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float scale = isStart ? CAP_START_SCALE : CAP_END_SCALE; // cap size
        poseStack.scale(scale, scale, scale);

        Matrix4f m = poseStack.last().pose();
        VertexConsumer cap = bufferSource.getBuffer(RenderType.entityTranslucent(CAP_TEX));

        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
        int alpha = isStart ? 160 : 220;
        cap.vertex(m, -1f, -1f, 0f).color(255, 255, 255, alpha).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        cap.vertex(m, -1f,  1f, 0f).color(255, 255, 255, alpha).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        cap.vertex(m,  1f,  1f, 0f).color(255, 255, 255, alpha).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        cap.vertex(m,  1f, -1f, 0f).color(255, 255, 255, alpha).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();

        poseStack.popPose();
    }

    private static float fract(float x) {
        float fx = x - (float) Math.floor(x);
        return fx < 0 ? fx + 1f : fx;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = net.minecraft.util.Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
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
