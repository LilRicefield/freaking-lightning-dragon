package com.leon.lightningdragon.client.renderer;

import com.leon.lightningdragon.client.model.LightningDragonModel;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * FIXED Lightning Dragon Renderer with proper head bone tracking
 */
@OnlyIn(Dist.CLIENT)
public class LightningDragonRenderer extends GeoEntityRenderer<LightningDragonEntity> {

    public LightningDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LightningDragonModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public void preRender(PoseStack poseStack,
                          LightningDragonEntity entity,
                          BakedGeoModel model,
                          MultiBufferSource bufferSource,
                          VertexConsumer buffer,
                          boolean isReRender,
                          float partialTick,
                          int packedLight,
                          int packedOverlay,
                          float red, float green, float blue, float alpha) {

        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        // Basic setup
        float scale = 4.5f;
        poseStack.scale(scale, scale, scale);
        this.shadowRadius = 0.8f * scale;

        // Apply all animations
        applyPhysicsBasedAnimations(entity, model, partialTick);
        applyHeadTracking(entity, model, partialTick);
    }

    /**
     * ACTUAL HEAD BONE TRACKING like vanilla mobs
     * This manipulates the head bones directly in the model
     */
    private void applyHeadTracking(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        // Only do head tracking when on ground
        if (entity.isFlying()) {
            // Reset head to neutral when flying
            resetHeadBones(model);
            return;
        }

        // Get the head bone
        GeoBone headBone = model.getBone("head").orElse(null);
        if (headBone == null) return;

        // Calculate head rotation based on entity's look direction
        float headYaw = Mth.lerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float bodyYaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // Calculate relative head rotation (head relative to body)
        float relativeYaw = Mth.wrapDegrees(headYaw - bodyYaw);

        // Clamp to reasonable head movement range
        relativeYaw = Mth.clamp(relativeYaw, -75.0f, 75.0f);
        headPitch = Mth.clamp(headPitch, -60.0f, 60.0f);

        // Convert to radians and apply to head bone
        float yawRad = (float) Math.toRadians(-relativeYaw); // NEGATIVE to fix inversion!
        float pitchRad = (float) Math.toRadians(-headPitch); // Negative because of model orientation

        // Apply rotation to head bone
        headBone.updateRotation(pitchRad, yawRad, 0);
    }

    /**
     * Reset head bones to neutral position
     */
    private void resetHeadBones(BakedGeoModel model) {
        GeoBone headBone = model.getBone("head").orElse(null);
        if (headBone != null) {
            headBone.updateRotation(0, 0, 0);
        }
    }

    /**
     * Banking tilt based on real-time flight physics
     */
    private void applyPhysicsBasedAnimations(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        // Banking tilt - this is real-time flight physics, keep it procedural
        if (entity.isFlying() && !entity.isLanding()) {
            applyBankingTilt(entity, model, partialTick);
            applyBankingTailResponse(entity, model, partialTick);
        }
    }

    /**
     * Banking tilt based on real-time flight physics
     */
    private void applyBankingTilt(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        float currentBanking = entity.getBanking();
        float prevBanking = entity.getPrevBanking();
        float interpolatedBanking = Mth.lerp(partialTick, prevBanking, currentBanking);
        float bankingRad = (float) Math.toRadians(interpolatedBanking);

        GeoBone body = model.getBone("body").orElse(null);
        if (body != null) {
            body.updateRotation(0, 0, bankingRad);
        }
    }

    /**
     * Subtle tail response to banking
     */
    private void applyBankingTailResponse(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        Vec3 velocity = entity.getDeltaMovement();
        float speed = (float) velocity.length();

        if (speed < 0.01f) return;

        float banking = Mth.lerp(partialTick, entity.getPrevBanking(), entity.getBanking());
        float bankingEffect = (float) Math.toRadians(banking * 0.1f);

        // Apply banking response to tail segments
        String[] tailSegments = {"tail3", "tail4", "tail5", "tail6", "tail7", "tail8", "tail9", "tail10"};

        for (int i = 0; i < tailSegments.length; i++) {
            GeoBone tailBone = model.getBone(tailSegments[i]).orElse(null);
            if (tailBone != null) {
                float segmentFactor = (float) (i + 1) / tailSegments.length;
                float tailResponse = bankingEffect * segmentFactor;
                tailBone.updateRotation(0, tailResponse * 0.3f, 0);
            }
        }
    }
}