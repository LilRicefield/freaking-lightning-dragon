package com.leon.lightningdragon.client;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * CLEANED UP Lightning Dragon Renderer
 * Only handles physics-based procedural animations (banking)
 * All other animations should be proper Blockbench animations
 */
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

        // ONLY apply physics-based procedural animations
        applyPhysicsBasedAnimations(entity, model, partialTick);
    }

    /**
     * ONLY physics-based animations that can't be keyframed
     * Everything else should be proper Blockbench animations
     */
    private void applyPhysicsBasedAnimations(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        // Banking tilt - this is real-time flight physics, keep it procedural
        if (entity.isFlying() && !entity.isLanding()) {
            applyBankingTilt(entity, model, partialTick);

            // Tail response to banking - also physics-based
            applyBankingTailResponse(entity, model, partialTick);
        }

        // That's it. Everything else = proper animations triggered by your animation controller
    }

    /**
     * Banking tilt based on real-time flight physics
     * This should stay procedural since it's calculated from actual movement
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
     * Subtle tail response to banking - physics-based procedural animation
     * Tail segments follow the banking motion like real physics
     */
    private void applyBankingTailResponse(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        Vec3 velocity = entity.getDeltaMovement();
        float speed = (float) velocity.length();

        if (speed < 0.01f) return;

        float banking = Mth.lerp(partialTick, entity.getPrevBanking(), entity.getBanking());
        float bankingEffect = (float) Math.toRadians(banking * 0.1f);

        // Apply banking response to tail segments - each segment responds more than the last
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