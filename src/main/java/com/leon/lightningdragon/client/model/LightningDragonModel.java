package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.client.model.enhanced.DragonGeoModel;
import com.leon.lightningdragon.client.model.enhanced.EnhancedGeoBone;
import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;
/**
 * Lightning Dragon model with enhanced bone system and procedural animations
 * Extends DragonGeoModel for advanced dragon-specific features
 */
public class LightningDragonModel extends DragonGeoModel<LightningDragonEntity> {
    private static final ResourceLocation MODEL =
            LightningDragonMod.rl("geo/lightning_dragon.geo.json");
    private static final ResourceLocation TEXTURE =
            LightningDragonMod.rl("textures/lightning_dragon.png");
    private static final ResourceLocation ANIM =
            LightningDragonMod.rl("animations/lightning_dragon.animation.json");

    @Override
    public ResourceLocation getModelResource(LightningDragonEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LightningDragonEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LightningDragonEntity entity) {
        return ANIM;
    }

    /**
     * This is where head tracking and other procedural animations happen
     */
    @Override
    public void setCustomAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        applyHeadTracking(entity, animationState);
        applyWalkRunTransitions(entity, animationState);
        applyGlideTransitions(entity, animationState);
        applyCustomAnimations(entity, instanceId, animationState);
        applyPhysicsAnimations(entity, instanceId, animationState);
    }

    /**
     * Handle head and neck tracking behavior
     * Uses raw GeoBone for reliable head tracking - save EnhancedGeoBone for advanced features
     */
    private void applyHeadTracking(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (!entity.isAlive()) return;

        // Use raw GeoBone for head tracking - it works reliably
        var headBoneOpt = getBone("head");
        if (headBoneOpt.isEmpty()) return;

        var neck1BoneOpt = getBone("neck1");
        var neck2BoneOpt = getBone("neck2");
        var neck3BoneOpt = getBone("neck3");
        var neck4BoneOpt = getBone("neck4");

        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float headYaw = Mth.wrapDegrees(entityData.netHeadYaw());
        float headPitch = Mth.wrapDegrees(entityData.headPitch());

        // Create neck chain for distributed head tracking
        java.util.List<software.bernie.geckolib.cache.object.GeoBone> neckChain = new java.util.ArrayList<>();
        neck1BoneOpt.ifPresent(neckChain::add);
        neck2BoneOpt.ifPresent(neckChain::add);
        neck3BoneOpt.ifPresent(neckChain::add);
        neck4BoneOpt.ifPresent(neckChain::add);
        neckChain.add(headBoneOpt.get()); // Head is always last

        // Check if dragon is flying to adjust head tracking behavior
        boolean isFlying = entity.isFlying() || !entity.onGround();

        // Reduce head tracking intensity during flight and clamp ranges
        if (isFlying) {
            headYaw = Mth.clamp(headYaw * 0.3f, -30.0f, 30.0f);   // 70% less yaw movement
            headPitch = Mth.clamp(headPitch * 0.4f, -20.0f, 20.0f); // 60% less pitch movement
        } else {
            headYaw = Mth.clamp(headYaw, -75.0f, 75.0f);
            headPitch = Mth.clamp(headPitch, -60.0f, 60.0f);
        }

        // Convert to radians and distribute across all neck segments + head
        float yawRad = headYaw * ((float) Math.PI / 180F);
        float pitchRad = headPitch * ((float) Math.PI / 180F);
        float yawPerBone = yawRad / neckChain.size();
        float pitchPerBone = pitchRad / neckChain.size();

        // Only apply head tracking if there's actual head movement to track
        if (Math.abs(headYaw) > 1.0f || Math.abs(headPitch) > 1.0f) {
            for (int i = 0; i < neckChain.size(); i++) {
                var bone = neckChain.get(i);

                // Simple approach - just add the rotation without base corrections
                float smoothYaw = yawPerBone;
                float smoothPitch = pitchPerBone;

                bone.setRotX(bone.getRotX() + smoothPitch);
                bone.setRotY(bone.getRotY() + smoothYaw);
            }
        }
        // If no significant head movement, let the dragon use its default pose from animations
    }
    /**
     * Handles smooth transitions between walking and running states
     * Read controller bone values
     */
    private void applyWalkRunTransitions(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (entity.isFlying()) return; // Only apply during ground movement

        // READ controller values from the animated controller bones (like ModelUmvuthana does)
        float runWeight = getControllerValue("walkRunController"); // 0=walk, 1=run (from switch animations)
        float walkWeight = 1.0f - runWeight;

        // Get animation data for dynamic movement-based adjustments
        float limbSwing = animationState.getLimbSwing();
        float limbSwingAmount = animationState.getLimbSwingAmount();
        float partialTick = animationState.getPartialTick();

        // Body posture adjustments with weight-based blending
        EnhancedGeoBone bodyBone = getEnhancedBone("body");
        if (bodyBone == null) {
            bodyBone = getEnhancedBone("root");
        }
        if (bodyBone != null) {
            // Walking posture: upright, relaxed
            float walkBodyLean = 0.0f;

            // Running posture: forward lean for momentum
            float runBodyLean = 0.08f; // More aggressive forward lean for charging

            // Add dynamic bobbing based on actual movement
            float movementBob = (float) Math.sin(limbSwing * 0.6f) * limbSwingAmount;
            float walkBob = movementBob * 0.02f; // Subtle bob when walking
            float runBob = movementBob * 0.04f;  // More pronounced bob when running

            // Blend between walk and run postures + dynamic movement
            float blendedLean = (walkBodyLean * walkWeight) + (runBodyLean * runWeight);
            float blendedBob = (walkBob * walkWeight) + (runBob * runWeight);

            bodyBone.addRotX(blendedLean + blendedBob);
        }
        // Gait height adjustments with weight-based blending
        EnhancedGeoBone hipsBone = getEnhancedBone("body");
        if (hipsBone != null) {
            // Walking height: normal, stable
            float walkHeight = 0.0f;

            // Running height: slightly lower, more aerodynamic
            float runHeight = -0.3f; // Lower stance for speed

            // Blend between walk and run heights
            float blendedHeight = (walkHeight * walkWeight) + (runHeight * runWeight);

            hipsBone.updatePosition(hipsBone.getPosX(), hipsBone.getPosY() + blendedHeight, hipsBone.getPosZ());
        }

        // Note: We READ from walkRunController (above), we don't store to it
        // The switch animations move the controller bone, we read its position
    }
    /**
     * Handles smooth transitions between flapping and gliding
     * Uses the Umvuthana-style blending pattern: read controller bone values
     */
    private void applyGlideTransitions(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (!entity.isFlying()) return; // Only apply during flight

        // READ controller values from the animated controller bones (like ModelUmvuthana does)
        float glideWeight = getControllerValue("flightController"); // 1=glide, 0=flap (from switch animations)
        float flapWeight = 1.0f - glideWeight;

        // Get animation timing for dynamic flight effects
        float animationTick = (float) animationState.getAnimationTick();
        float partialTick = animationState.getPartialTick();
        float flightTime = animationTick + partialTick;

        // Wing angle adjustments with weight-based blending
        EnhancedGeoBone leftWingBone = getEnhancedBone("leftwing");
        EnhancedGeoBone rightWingBone = getEnhancedBone("rightwing");

        if (leftWingBone != null && rightWingBone != null) {
            // Flapping position: wings closer to body, more dynamic
            float flapWingAngle = 0.0f;

            // Gliding position: wings extended, stable
            float glideWingAngle = 0.08f; // More extension for efficient gliding

            // Add subtle wing flutter during gliding for realism
            float glideFlutter = (float) Math.sin(flightTime * 0.2f) * glideWeight * 0.01f;

            // Add dynamic wing beat during flapping
            float flapBeat = (float) Math.sin(flightTime * 1.2f) * flapWeight * 0.03f;

            // Blend between flap and glide positions + dynamic motion
            float blendedWingExtension = (flapWingAngle * flapWeight) + (glideWingAngle * glideWeight);
            float dynamicMotion = glideFlutter + flapBeat;

            leftWingBone.setRotY(blendedWingExtension + dynamicMotion);
            rightWingBone.setRotZ(-blendedWingExtension - dynamicMotion);
        }

        // Body pitch adjustments with weight-based blending
        EnhancedGeoBone bodyBone = getEnhancedBone("body");
        if (bodyBone == null) {
            bodyBone = getEnhancedBone("root");
        }
        if (bodyBone != null) {
            // Flapping position: slight upward pitch for power
            float flapPitch = -0.01f;

            // Gliding position: nose-down for efficiency
            float glidePitch = 0.05f;

            // Add subtle body sway during flight
            float flightSway = (float) Math.sin(flightTime * 0.3f) * 0.005f;

            // Blend between flap and glide pitch + flight dynamics
            float blendedPitch = (flapPitch * flapWeight) + (glidePitch * glideWeight);

            bodyBone.addRotX(blendedPitch + flightSway);
        }
    }

    // ===== LIGHTNING DRAGON SPECIFIC ANIMATIONS =====
    /**
     * Apply lightning-specific procedural animations like electrical effects
     */
    @Override
    public void applyCustomAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        if (!isInitialized()) return;

        // Get animation values from controller bones (Mowzie pattern)
        float glidingAmount = getControllerValue("flightController");           // Normal controller value
        float flappingAmount = getControllerValueInverted("flightController");  // Inverted controller value
        float hoveringAmount = entity.getAnimationController().getHoveringFraction(animationState.getPartialTick());

        // Apply wing dynamics based on flight state
        EnhancedGeoBone leftWing = getEnhancedBone("leftwing");
        EnhancedGeoBone rightWing = getEnhancedBone("rightwing");
        EnhancedGeoBone bodyBone = getEnhancedBone("body");
        if (bodyBone == null) bodyBone = getEnhancedBone("root");

        if (leftWing != null && rightWing != null) {
            // Gliding - wings extended and stable
            if (glidingAmount > 0.1f) {
                leftWing.addRot(0, 0, glidingAmount * 0.3f);   // Slight upward angle
                rightWing.addRot(0, 0, -glidingAmount * 0.3f);
            }

            // Flapping - dynamic wing movement with body bobbing
            if (flappingAmount > 0.1f) {
                float time = entity.tickCount + animationState.getPartialTick();
                float flapOffset = (float) Math.sin(time * 0.8f) * flappingAmount;

                leftWing.addRot(flapOffset * 0.2f, 0, flapOffset * 0.6f);
                rightWing.addRot(flapOffset * 0.2f, 0, -flapOffset * 0.6f);

                // Add body bobbing synchronized with wing flaps
                if (bodyBone != null) {
                    float bodyBob = (float) Math.sin(time * 0.8f) * flappingAmount * 0.8f; // Subtle up/down movement
                    bodyBone.updatePosition(bodyBone.getPosX(), bodyBone.getPosY() + bodyBob, bodyBone.getPosZ());
                }
            }

            // Hovering - subtle wing adjustments
            if (hoveringAmount > 0.1f) {
                float hoverSway = (float) Math.sin((entity.tickCount + animationState.getPartialTick()) * 0.3f);
                leftWing.addRot(0, 0, hoverSway * hoveringAmount * 0.15f);
                rightWing.addRot(0, 0, -hoverSway * hoveringAmount * 0.15f);
            }
        }
    }
}