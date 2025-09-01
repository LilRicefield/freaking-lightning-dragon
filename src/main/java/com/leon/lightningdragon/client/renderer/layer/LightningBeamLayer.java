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
import com.leon.lightningdragon.client.vfx.BeamVfxController;

/**
 * Lightning beam render layer.
 * - Draws a segmented, jittered "ribbon cross" beam using a tileable lightning texture
 * - Adds simple start/end billboards as caps
 * Server handles damage; this is visual-only.
 */
public class LightningBeamLayer extends GeoRenderLayer<LightningDragonEntity> {
    // Core textures: inner (crisp) + outer (soft)
    private static final ResourceLocation INNER_TEX = ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_beam_inner.png");
    private static final ResourceLocation OUTER_TEX = ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_beam_outer.png");
    // End cap animated frames
    private static final ResourceLocation[] END_TEX = new ResourceLocation[] {
            ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_beam_end_0.png"),
            ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_beam_end_1.png"),
            ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_beam_end_2.png")
    };
    private static final float CAP_START_SCALE = 0.15f;
    private static final float CAP_END_SCALE = 0.15f;
    // Beam tuning knobs
    private static final float BEAM_HALF_WIDTH = 0.05f;     // inner half-width (smaller = thinner)
    private static final float OUTER_WIDTH_MULT = 1.0f;    // outer width multiplier
    private static final int INNER_ALPHA_MAIN = 220;
    private static final int INNER_ALPHA_SIDE = 210;
    private static final int OUTER_ALPHA_MAIN = 150;
    private static final int OUTER_ALPHA_SIDE = 120;
    private static final float INNER_SCROLL_SPEED = 5.0f;
    private static final float OUTER_SCROLL_SPEED = 2.0f;

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
        float halfWidth = BEAM_HALF_WIDTH; // base thickness
        float time = (animatable.tickCount + partialTick) * 0.04f;

        // (Pinned halo removed; outer layer handles soft glow)

        // Segmenting: repeat the 64x64 ribbon per segment so V stays 0..1
        int baseSegments = Math.max(8, Math.min(32, (int) (len * 6))); // density scales with length
        VertexConsumer outerVC = bufferSource.getBuffer(RenderType.entityTranslucent(OUTER_TEX));
        VertexConsumer innerVC = bufferSource.getBuffer(RenderType.entityTranslucent(INNER_TEX));
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

            // UV scroll per segment (outer slower, inner faster)
            float u0 = 0f, u1 = 1f;
            float vOuter = fract(-time * OUTER_SCROLL_SPEED);
            float vInner = fract(-time * INNER_SCROLL_SPEED);
            float v0o = vOuter, v1o = vOuter + 1f;
            float v0i = vInner, v1i = vInner + 1f;

            // Segment center for tiny camera bias
            float mxs = (p0.x + p1.x) * 0.5f;
            float mys = (p0.y + p1.y) * 0.5f;
            float mzs = (p0.z + p1.z) * 0.5f;
            Vector3f toCamN = new Vector3f(cx - mxs, cy - mys, cz - mzs);
            if (toCamN.lengthSquared() > 1e-6f) toCamN.normalize();
            float eps = 0.0015f;
            float bx = toCamN.x * eps, by = toCamN.y * eps, bz = toCamN.z * eps;

            // Build square tube faces instead of intersecting planes
            // OFFSETS to face centers
            Vector3f offR = new Vector3f(right).normalize().mul(halfWidth);
            Vector3f offL = new Vector3f(right).normalize().mul(-halfWidth);
            Vector3f offU = new Vector3f(up).normalize().mul(halfWidth);
            Vector3f offD = new Vector3f(up).normalize().mul(-halfWidth);

            // OUTER faces (wider, softer)
            float outerMul = OUTER_WIDTH_MULT;
            Vector3f oOffR = new Vector3f(right).normalize().mul(halfWidth * outerMul);
            Vector3f oOffL = new Vector3f(right).normalize().mul(-halfWidth * outerMul);
            Vector3f oOffU = new Vector3f(up).normalize().mul(halfWidth * outerMul);
            Vector3f oOffD = new Vector3f(up).normalize().mul(-halfWidth * outerMul);
            Vector3f upVec = new Vector3f(up).normalize().mul(halfWidth * outerMul);
            Vector3f rightVec = new Vector3f(right).normalize().mul(halfWidth * outerMul);

            // RIGHT face (outer): centered at +right; width along up
            submitQuad(outerVC, mat,
                    p0.x + oOffR.x - upVec.x + bx, p0.y + oOffR.y - upVec.y + by, p0.z + oOffR.z - upVec.z + bz,
                    p0.x + oOffR.x + upVec.x + bx, p0.y + oOffR.y + upVec.y + by, p0.z + oOffR.z + upVec.z + bz,
                    p1.x + oOffR.x + upVec.x + bx, p1.y + oOffR.y + upVec.y + by, p1.z + oOffR.z + upVec.z + bz,
                    p1.x + oOffR.x - upVec.x + bx, p1.y + oOffR.y - upVec.y + by, p1.z + oOffR.z - upVec.z + bz,
                    u0, v0o, u1, v1o, packedLight, OUTER_ALPHA_MAIN);
            // LEFT face (outer): centered at -right; width along up
            submitQuad(outerVC, mat,
                    p0.x + oOffL.x - upVec.x + bx, p0.y + oOffL.y - upVec.y + by, p0.z + oOffL.z - upVec.z + bz,
                    p0.x + oOffL.x + upVec.x + bx, p0.y + oOffL.y + upVec.y + by, p0.z + oOffL.z + upVec.z + bz,
                    p1.x + oOffL.x + upVec.x + bx, p1.y + oOffL.y + upVec.y + by, p1.z + oOffL.z + upVec.z + bz,
                    p1.x + oOffL.x - upVec.x + bx, p1.y + oOffL.y - upVec.y + by, p1.z + oOffL.z - upVec.z + bz,
                    u0, v0o, u1, v1o, packedLight, OUTER_ALPHA_MAIN);
            // UP face (outer): centered at +up; width along right
            submitQuad(outerVC, mat,
                    p0.x + oOffU.x - rightVec.x + bx, p0.y + oOffU.y - rightVec.y + by, p0.z + oOffU.z - rightVec.z + bz,
                    p0.x + oOffU.x + rightVec.x + bx, p0.y + oOffU.y + rightVec.y + by, p0.z + oOffU.z + rightVec.z + bz,
                    p1.x + oOffU.x + rightVec.x + bx, p1.y + oOffU.y + rightVec.y + by, p1.z + oOffU.z + rightVec.z + bz,
                    p1.x + oOffU.x - rightVec.x + bx, p1.y + oOffU.y - rightVec.y + by, p1.z + oOffU.z - rightVec.z + bz,
                    u0, v0o, u1, v1o, packedLight, OUTER_ALPHA_SIDE);
            // DOWN face (outer): centered at -up; width along right
            submitQuad(outerVC, mat,
                    p0.x + oOffD.x - rightVec.x + bx, p0.y + oOffD.y - rightVec.y + by, p0.z + oOffD.z - rightVec.z + bz,
                    p0.x + oOffD.x + rightVec.x + bx, p0.y + oOffD.y + rightVec.y + by, p0.z + oOffD.z + rightVec.z + bz,
                    p1.x + oOffD.x + rightVec.x + bx, p1.y + oOffD.y + rightVec.y + by, p1.z + oOffD.z + rightVec.z + bz,
                    p1.x + oOffD.x - rightVec.x + bx, p1.y + oOffD.y - rightVec.y + by, p1.z + oOffD.z - rightVec.z + bz,
                    u0, v0o, u1, v1o, packedLight, OUTER_ALPHA_SIDE);

            // INNER faces (crisp core)
            Vector3f inUp = new Vector3f(up).normalize().mul(halfWidth);
            Vector3f inRight = new Vector3f(right).normalize().mul(halfWidth);
            // RIGHT
            submitQuad(innerVC, mat,
                    p0.x + offR.x - inUp.x + bx, p0.y + offR.y - inUp.y + by, p0.z + offR.z - inUp.z + bz,
                    p0.x + offR.x + inUp.x + bx, p0.y + offR.y + inUp.y + by, p0.z + offR.z + inUp.z + bz,
                    p1.x + offR.x + inUp.x + bx, p1.y + offR.y + inUp.y + by, p1.z + offR.z + inUp.z + bz,
                    p1.x + offR.x - inUp.x + bx, p1.y + offR.y - inUp.y + by, p1.z + offR.z - inUp.z + bz,
                    u0, v0i, u1, v1i, packedLight, INNER_ALPHA_MAIN);
            // LEFT
            submitQuad(innerVC, mat,
                    p0.x + offL.x - inUp.x + bx, p0.y + offL.y - inUp.y + by, p0.z + offL.z - inUp.z + bz,
                    p0.x + offL.x + inUp.x + bx, p0.y + offL.y + inUp.y + by, p0.z + offL.z + inUp.z + bz,
                    p1.x + offL.x + inUp.x + bx, p1.y + offL.y + inUp.y + by, p1.z + offL.z + inUp.z + bz,
                    p1.x + offL.x - inUp.x + bx, p1.y + offL.y - inUp.y + by, p1.z + offL.z - inUp.z + bz,
                    u0, v0i, u1, v1i, packedLight, INNER_ALPHA_MAIN);
            // UP
            submitQuad(innerVC, mat,
                    p0.x + offU.x - inRight.x + bx, p0.y + offU.y - inRight.y + by, p0.z + offU.z - inRight.z + bz,
                    p0.x + offU.x + inRight.x + bx, p0.y + offU.y + inRight.y + by, p0.z + offU.z + inRight.z + bz,
                    p1.x + offU.x + inRight.x + bx, p1.y + offU.y + inRight.y + by, p1.z + offU.z + inRight.z + bz,
                    p1.x + offU.x - inRight.x + bx, p1.y + offU.y - inRight.y + by, p1.z + offU.z - inRight.z + bz,
                    u0, v0i, u1, v1i, packedLight, INNER_ALPHA_SIDE);
            // DOWN
            submitQuad(innerVC, mat,
                    p0.x + offD.x - inRight.x + bx, p0.y + offD.y - inRight.y + by, p0.z + offD.z - inRight.z + bz,
                    p0.x + offD.x + inRight.x + bx, p0.y + offD.y + inRight.y + by, p0.z + offD.z + inRight.z + bz,
                    p1.x + offD.x + inRight.x + bx, p1.y + offD.y + inRight.y + by, p1.z + offD.z + inRight.z + bz,
                    p1.x + offD.x - inRight.x + bx, p1.y + offD.y - inRight.y + by, p1.z + offD.z - inRight.z + bz,
                    u0, v0i, u1, v1i, packedLight, INNER_ALPHA_SIDE);
        }

        // Start and end caps - beam-aligned like Tremorzilla
        drawCapAligned(poseStack, bufferSource, mx, my, mz, dir, packedLight, animatable, partialTick, true, halfWidth);
        Vector3f endDir = new Vector3f(dir).negate(); // end cap faces opposite direction
        drawCapAligned(poseStack, bufferSource, ex, ey, ez, endDir, packedLight, animatable, partialTick, false, halfWidth);

        // Drive modular particle VFX (impact ring) via controller
        if (animatable.level().isClientSide) {
            var controller = BeamVfxController.get(animatable);
            controller.update(animatable,
                    new net.minecraft.world.phys.Vec3(mouthWorld.x, mouthWorld.y, mouthWorld.z),
                    new net.minecraft.world.phys.Vec3(end.x, end.y, end.z));
        }
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

    // Draws a beam-aligned cap at a position (exactly like Tremorzilla's end model)
    private static void drawCapAligned(PoseStack poseStack, MultiBufferSource bufferSource,
                                       float x, float y, float z, Vector3f beamDir, int packedLight,
                                       LightningDragonEntity entity, float partialTick, boolean isStart, float beamWidth) {
        // Hide start cap for local first-person rider
        boolean hideStart = false;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (isStart && mc.player != null && entity.getControllingPassenger() == mc.player && mc.options.getCameraType().isFirstPerson()) {
            hideStart = true;
        }
        if (hideStart) return;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        
        // Apply Tremorzilla's exact transformation sequence:
        // 45° Z rotation, then 90° X rotation (no beam direction alignment needed!)
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45f));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        
        // Scale by beam width like Tremorzilla
        float scale = (isStart ? CAP_START_SCALE : CAP_END_SCALE) * beamWidth;
        poseStack.scale(scale, scale, scale);

        Matrix4f m = poseStack.last().pose();
        int frame = (int) (entity.tickCount / 2) % END_TEX.length;
        ResourceLocation capTex = END_TEX[frame];
        VertexConsumer cap = bufferSource.getBuffer(RenderType.entityTranslucent(capTex));

        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
        int alpha = isStart ? 160 : 220;
        
        // Draw quad in local space (transformed by the rotations above)
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
