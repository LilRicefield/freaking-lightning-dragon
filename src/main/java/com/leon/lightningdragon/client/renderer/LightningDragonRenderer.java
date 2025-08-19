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

    @Override
    public void postRender(PoseStack poseStack,
                           LightningDragonEntity entity,
                           BakedGeoModel model,
                           MultiBufferSource bufferSource,
                           VertexConsumer buffer,
                           boolean isReRender,
                           float partialTick,
                           int packedLight,
                           int packedOverlay,
                           float red, float green, float blue, float alpha) {

        super.postRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        // LIGHTNING BEAM RENDERING - renders AFTER the dragon model
        if (entity.hasLightningTarget()) {
            renderLightningBeam(poseStack, bufferSource, entity, partialTick);
        }
    }

    /**
     * Renders the lightning beam from dragon's mouth to target
     */
    private void renderLightningBeam(PoseStack poseStack, MultiBufferSource bufferSource,
                                     LightningDragonEntity entity, float partialTick) {

        // Get lightning target from entity data
        Vec3 target = new Vec3(
                entity.getLightningTargetX(),
                entity.getLightningTargetY(),
                entity.getLightningTargetZ()
        );

        // Get mouth position
        Vec3 mouthPos = entity.getMouthPosition();

        double distance = mouthPos.distanceTo(target);
        if (distance > 50.0) { // Max beam range
            return;
        }

        poseStack.pushPose();

        // Move to world origin for absolute positioning
        poseStack.translate(-entity.getX(), -entity.getY(), -entity.getZ());

        // Create the main lightning beam
        renderMainBeam(poseStack, bufferSource, mouthPos, target, partialTick, entity);

        // Add some branching lightning for realism
        renderBeamBranches(poseStack, bufferSource, mouthPos, target, partialTick, entity);

        poseStack.popPose();
    }

    private void renderMainBeam(PoseStack poseStack, MultiBufferSource bufferSource,
                                Vec3 start, Vec3 end, float partialTick, LightningDragonEntity entity) {

        // Calculate beam properties
        Vec3 direction = end.subtract(start);
        double distance = direction.length();

        if (distance < 0.5) return; // Too close, don't render

        direction = direction.normalize();

        // Create lightning beam using particles (much easier for 1.20.1)
        int segments = Math.max(8, (int)(distance * 2));

        for (int i = 0; i <= segments; i++) {
            double progress = (double)i / segments;

            // Add zigzag randomness for natural lightning look
            java.util.Random random = new java.util.Random(entity.tickCount / 3 + i);
            Vec3 offset = new Vec3(
                    (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.5
            );

            Vec3 beamPos = start.add(direction.scale(progress * distance)).add(offset);

            // Spawn electric particles for the beam effect
            if (entity.level().isClientSide) {
                // Main beam particles
                for (int j = 0; j < 3; j++) {
                    entity.level().addParticle(
                            net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            beamPos.x + (random.nextDouble() - 0.5) * 0.2,
                            beamPos.y + (random.nextDouble() - 0.5) * 0.2,
                            beamPos.z + (random.nextDouble() - 0.5) * 0.2,
                            0, 0, 0
                    );
                }

                // Add some white sparks for intensity
                if (random.nextFloat() < 0.7f) {
                    entity.level().addParticle(
                            net.minecraft.core.particles.ParticleTypes.FIREWORK,
                            beamPos.x, beamPos.y, beamPos.z,
                            (random.nextDouble() - 0.5) * 0.1,
                            (random.nextDouble() - 0.5) * 0.1,
                            (random.nextDouble() - 0.5) * 0.1
                    );
                }
            }
        }
    }

    private void renderBeamBranches(PoseStack poseStack, MultiBufferSource bufferSource,
                                    Vec3 start, Vec3 end, float partialTick, LightningDragonEntity entity) {

        // Only render branches occasionally to avoid lag
        if (entity.tickCount % 4 != 0) return;

        Vec3 direction = end.subtract(start);
        double distance = direction.length();

        // Create 2-3 random branches along the main beam
        java.util.Random random = new java.util.Random(entity.tickCount / 4 + 42);

        for (int branch = 0; branch < 2; branch++) {
            // Pick a random point along the beam
            double branchProgress = 0.3 + random.nextDouble() * 0.4; // Middle 40% of beam
            Vec3 branchStart = start.add(direction.scale(branchProgress));

            // Create a short branch in a random direction
            Vec3 branchDirection = new Vec3(
                    (random.nextDouble() - 0.5) * 2,
                    (random.nextDouble() - 0.5) * 2,
                    (random.nextDouble() - 0.5) * 2
            ).normalize();

            double branchLength = 1.0 + random.nextDouble() * 2.0; // 1-3 block branches
            int branchSegments = (int)(branchLength * 3);

            // Render branch using particles
            for (int i = 0; i <= branchSegments; i++) {
                double progress = (double)i / branchSegments;
                Vec3 branchPos = branchStart.add(branchDirection.scale(progress * branchLength));

                if (entity.level().isClientSide && random.nextFloat() < 0.6f) {
                    entity.level().addParticle(
                            net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            branchPos.x, branchPos.y, branchPos.z,
                            0, 0, 0
                    );
                }
            }
        }
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
        model.getBone("head").ifPresent(headBone -> headBone.updateRotation(0, 0, 0));
    }

    /**
     * Isaac Newton lmao physics yay i love me some gravity
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

        model.getBone("body").ifPresent(body -> body.updateRotation(0, 0, bankingRad));
    }

    /**
     * Subtle tail response to banking (idek if this works or not)
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