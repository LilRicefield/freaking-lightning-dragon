package com.leon.lightningdragon.client.renderer.layer;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.util.Mth;

/**
 * - Draws a multi-layered cylindrical beam with inner core and outer glow
 * - Uses proper 3D beam end caps with the LightningBeamModel
 * Server handles damage; this is visual-only.
 */
public class LightningBeamLayer extends GeoRenderLayer<LightningDragonEntity> {
    // Core textures: inner (crisp) + outer (soft)
    private static final ResourceLocation INNER_TEX = ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_beam_inner.png");
    private static final ResourceLocation OUTER_TEX = ResourceLocation.fromNamespaceAndPath("lightningdragon", "textures/effects/lightning_beam_outer.png");
    // Beam tuning constants - adjust these to change beam appearance
    private static final float BASE_BEAM_WIDTH = 0.1F;        // Base width of the beam
    private static final float OUTER_BEAM_BONUS = 0.05F;      // Extra width for outer glow layer
    private static final float INNER_SPEED_MULTIPLIER = 0.25F; // Animation speed for inner beam
    private static final float OUTER_SPEED_MULTIPLIER = 0.25F; // Animation speed for outer beam
    private static final float BEAM_SHAKE_INTENSITY = 0.01F; // Intensity of beam shake effect
    // Removed end-cap; only inner/outer beam remain
    
    // Beam positioning constants - adjust these to move the beam relative to mouth origin
    private static final float BEAM_OFFSET_RIGHT = 0.0F;      // Positive moves beam to the right
    private static final float BEAM_OFFSET_LEFT = 0.02F;       // Positive moves beam to the left (negative of right)
    private static final float BEAM_OFFSET_UP = 0.0F;         // Positive moves beam upward
    private static final float BEAM_OFFSET_DOWN = 0.0F;       // Positive moves beam downward (negative of up)

    public LightningBeamLayer() { super(null); }

    @Override
    public void render(@NotNull PoseStack poseStack, LightningDragonEntity animatable, BakedGeoModel bakedModel,
                       @NotNull RenderType renderType, @NotNull MultiBufferSource bufferSource, @NotNull VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        if (!animatable.isBeaming()) return;

        // Use live mouth origin to avoid visual lag when moving fast
        // Damage still uses server-synced positions; minor divergence is acceptable
        net.minecraft.world.phys.Vec3 mouthWorld = animatable.computeHeadMouthOrigin(partialTick);

        // Predict visual beam end on the client to reduce trailing during fast movement/turns
        net.minecraft.world.phys.Vec3 predictedEnd = predictBeamEnd(animatable, mouthWorld, partialTick);
        // Server-synced end (authoritative for damage)
        net.minecraft.world.phys.Vec3 serverEnd = animatable.getClientBeamEndPosition(partialTick);
        net.minecraft.world.phys.Vec3 end;
        if (serverEnd == null) {
            end = predictedEnd;
        } else {
            // Blend weight increases with speed/turn rate; clamps to [0,1]
            double hspeed = animatable.getDeltaMovement().horizontalDistance();
            float turnRate = Math.abs(net.minecraft.util.Mth.degreesDifference(animatable.yHeadRotO, animatable.yHeadRot));
            float weight = net.minecraft.util.Mth.clamp((float) (hspeed * 3.0 + (turnRate / 90.0f)), 0.0f, 1.0f);
            end = lerpVec(serverEnd, predictedEnd, weight);
        }

        // Transform into model space relative to entity origin for poseStack use
        double ox = net.minecraft.util.Mth.lerp(partialTick, animatable.xo, animatable.getX());
        double oy = net.minecraft.util.Mth.lerp(partialTick, animatable.yo, animatable.getY());
        double oz = net.minecraft.util.Mth.lerp(partialTick, animatable.zo, animatable.getZ());
        float scale = com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity.MODEL_SCALE;
        
        // Calculate beam direction and prepare transformation
        net.minecraft.world.phys.Vec3 rawBeamPosition = end.subtract(mouthWorld);
        // PoseStack here operates in model space; translation below divides by MODEL_SCALE.
        // Do the same for beam length so visuals match server/world distance.
        float length = (float) (rawBeamPosition.length() / scale);
        if (length <= 0.001f) return;
        
        net.minecraft.world.phys.Vec3 vec3 = rawBeamPosition.normalize();
        float xRot = (float) Math.acos(vec3.y);
        float yRot = (float) Math.atan2(vec3.z, vec3.x);
        float width = BASE_BEAM_WIDTH; // configurable beam width
        
        // Small shake effect for visual flair
        float ageInTicks = animatable.tickCount + partialTick;
        float shakeByX = (float) Math.sin(ageInTicks * 4F) * BEAM_SHAKE_INTENSITY;
        float shakeByY = (float) Math.sin(ageInTicks * 4F + 1.2F) * BEAM_SHAKE_INTENSITY;
        float shakeByZ = (float) Math.sin(ageInTicks * 4F + 2.4F) * BEAM_SHAKE_INTENSITY;
        
        // Transform to mouth position in model space
        float mx = (float) ((mouthWorld.x - ox) / scale);
        float my = (float) ((mouthWorld.y - oy) / scale);
        float mz = (float) ((mouthWorld.z - oz) / scale);

        // Apply beam positioning offsets
        float offsetX = BEAM_OFFSET_RIGHT - BEAM_OFFSET_LEFT;
        float offsetY = BEAM_OFFSET_UP - BEAM_OFFSET_DOWN;

        poseStack.pushPose();
        poseStack.translate(mx + shakeByX + offsetX, my + shakeByY + offsetY, mz + shakeByZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(((Mth.PI / 2F) - yRot) * Mth.RAD_TO_DEG));
        poseStack.mulPose(Axis.XP.rotationDegrees((-(Mth.PI / 2F) + xRot) * Mth.RAD_TO_DEG));
        poseStack.mulPose(Axis.ZP.rotationDegrees(45));
        
        // Render inner beam
        renderBeam(animatable, poseStack, bufferSource, partialTick, width, length, true);
        // Render outer beam
        renderBeam(animatable, poseStack, bufferSource, partialTick, width, length, false);
        
        poseStack.popPose();

        // No particle VFX; keep visuals minimal (inner/outer beam only)
    }

    private void renderBeam(LightningDragonEntity entity, PoseStack poseStack, MultiBufferSource source, float partialTicks, float width, float length, boolean inner) {
        poseStack.pushPose();
        int vertices;
        VertexConsumer vertexconsumer;
        float speed;
        float startAlpha = 1.0F;
        float endAlpha = 1.0F;
        if (inner) {
            vertices = 4;
            vertexconsumer = source.getBuffer(RenderType.entityTranslucent(INNER_TEX));
            speed = INNER_SPEED_MULTIPLIER;
        } else {
            vertices = 8;
            vertexconsumer = source.getBuffer(RenderType.entityTranslucent(OUTER_TEX));
            width += OUTER_BEAM_BONUS; // configurable outer beam bonus width
            speed = OUTER_SPEED_MULTIPLIER;
            endAlpha = 0.0F;
        }

        float v = ((float) entity.tickCount + partialTicks) * -0.25F * speed;
        float v1 = v + length * (inner ? 0.5F : 0.15F);
        float f4 = -width;
        float f5 = 0;
        float f6 = 0.0F;
        PoseStack.Pose posestack$pose = poseStack.last();
        Matrix4f matrix4f = posestack$pose.pose();
        
        for (int j = 0; j <= vertices; ++j) {
            Matrix3f matrix3f = posestack$pose.normal();
            float f7 = Mth.cos((float) Math.PI + (float) j * ((float) Math.PI * 2F) / (float) vertices) * width;
            float f8 = Mth.sin((float) Math.PI + (float) j * ((float) Math.PI * 2F) / (float) vertices) * width;
            float f9 = (float) j + 1;
            vertexconsumer.vertex(matrix4f, f4 * 0.55F, f5 * 0.55F, 0.0F).color(1.0F, 1.0F, 1.0F, startAlpha).uv(f6, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(matrix3f, 0.0F, -1.0F, 0.0F).endVertex();
            vertexconsumer.vertex(matrix4f, f4, f5, length).color(1.0F, 1.0F, 1.0F, endAlpha).uv(f6, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(matrix3f, 0.0F, -1F, 0.0F).endVertex();
            vertexconsumer.vertex(matrix4f, f7, f8, length).color(1.0F, 1.0F, 1.0F, endAlpha).uv(f9, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(matrix3f, 0.0F, -1F, 0.0F).endVertex();
            vertexconsumer.vertex(matrix4f, f7 * 0.55F, f8 * 0.55F, 0.0F).color(1.0F, 1.0F, 1.0F, startAlpha).uv(f9, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(240).normal(matrix3f, 0.0F, -1.0F, 0.0F).endVertex();
            f4 = f7;
            f5 = f8;
            f6 = f9;
        }
        // End-cap removed
        poseStack.popPose();
    }

    private static net.minecraft.world.phys.Vec3 lerpVec(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, float t) {
        t = net.minecraft.util.Mth.clamp(t, 0.0f, 1.0f);
        return a.add(b.subtract(a).scale(t));
    }

    private static net.minecraft.world.phys.Vec3 predictBeamEnd(LightningDragonEntity dragon, net.minecraft.world.phys.Vec3 mouthWorld, float partialTicks) {
        // Choose aim direction: rider look -> target center -> head look
        net.minecraft.world.phys.Vec3 aimDir;
        net.minecraft.world.entity.Entity cp = dragon.getControllingPassenger();
        if (cp instanceof net.minecraft.world.entity.LivingEntity rider) {
            aimDir = rider.getViewVector(partialTicks).normalize();
        } else {
            net.minecraft.world.entity.LivingEntity tgt = dragon.getTarget();
            if (tgt != null && tgt.isAlive()) {
                net.minecraft.world.phys.Vec3 aimPoint = tgt.getEyePosition(partialTicks).add(0, -0.25, 0);
                aimDir = aimPoint.subtract(mouthWorld).normalize();
            } else {
                float yaw = net.minecraft.util.Mth.lerp(partialTicks, dragon.yHeadRotO, dragon.yHeadRot);
                float pitch = net.minecraft.util.Mth.lerp(partialTicks, dragon.xRotO, dragon.getXRot());
                aimDir = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw).normalize();
            }
        }

        final double MAX_DISTANCE = 32.0; // blocks
        net.minecraft.world.phys.Vec3 tentativeEnd = mouthWorld.add(aimDir.scale(MAX_DISTANCE));
        var hit = dragon.level().clip(new net.minecraft.world.level.ClipContext(
                mouthWorld,
                tentativeEnd,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                dragon
        ));
        return hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? hit.getLocation() : tentativeEnd;
    }
}
