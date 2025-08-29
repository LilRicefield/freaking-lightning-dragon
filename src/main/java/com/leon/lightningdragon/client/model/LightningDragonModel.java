package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.client.model.enhanced.DragonGeoModel;
import com.leon.lightningdragon.client.model.enhanced.EnhancedGeoBone;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.GeoBone;
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
        applyGeckoLibBanking(entity, animationState);
        applyWalkRunTransitions(entity, animationState);
        applyGlideTransitions(entity, animationState);
        applyCustomAnimations(entity, instanceId, animationState);
        applyPhysicsAnimations(entity, instanceId, animationState);
    }

    /**
     * Handle head and neck tracking behavior
     */
    private void applyHeadTracking(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (!entity.isAlive()) return;
        EnhancedGeoBone headBone = getEnhancedBone("head");
        EnhancedGeoBone neckBone1 = getEnhancedBone("neck1");
        EnhancedGeoBone neckBone2 = getEnhancedBone("neck2");
        EnhancedGeoBone neckBone3 = getEnhancedBone("neck3");
        EnhancedGeoBone neckBone4 = getEnhancedBone("neck4");
        GeoBone[] lookPiece = new GeoBone[]{neckBone1, neckBone2, neckBone3, neckBone4, headBone};

        if (headBone != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            float headYaw = Mth.wrapDegrees(entityData.netHeadYaw());
            float headPitch = Mth.wrapDegrees(entityData.headPitch());

            // Check if dragon is flying to adjust head tracking behavior
            boolean isFlying = entity.isFlying() || !entity.onGround();

            // Reduce head tracking intensity during flight and clamp ranges
            if (isFlying) {
                // Much more restricted movement during flight
                headYaw = Mth.clamp(headYaw * 0.3f, -30.0f, 30.0f);   // 70% less yaw movement
                headPitch = Mth.clamp(headPitch * 0.4f, -20.0f, 20.0f); // 60% less pitch movement
            } else {
                // Full range when grounded
                headYaw = Mth.clamp(headYaw, -75.0f, 75.0f);
                headPitch = Mth.clamp(headPitch, -60.0f, 60.0f);
            }

            // Convert to radians and apply split system
            float yawRad = headYaw * ((float) Math.PI / 180F);
            float pitchRad = headPitch * ((float) Math.PI / 180F);

            if (neckBone1 != null) {
                // Apply a base downward pitch to counteract upward-angled neck
                float neckBasePitch = -0.15f;
                float headBasePitch = 0;

                // During flight, reduce neck participation to prevent extreme bending
                if (isFlying) {
                    // Flight mode: Head does 70% of the rotation, neck only 30%
                    headBone.addRotX((pitchRad * 0.7f) + headBasePitch);
                    headBone.addRotY(yawRad * 0.7f);
                    neckBone1.addRotX((pitchRad * 0.3f) + neckBasePitch);
                    neckBone1.addRotY(yawRad * 0.3f);
                } else {
                    // Ground mode: Split rotation 50/50 between head and neck
                    headBone.addRotX((pitchRad / 2f) + headBasePitch);
                    headBone.addRotY(yawRad / 2f);
                    neckBone1.addRotX((pitchRad / 2f) + neckBasePitch);
                    neckBone1.addRotY(yawRad / 2f);
                }
            } else {
                // No neck bone, apply rotation to head with slight downward correction
                float intensity = isFlying ? 0.5f : 1.0f; // 50% intensity during flight
                headBone.addRotX((pitchRad * intensity) - 0.2f);
                headBone.addRotY(yawRad * intensity);
            }
        }
    }

    /**
     * Banking now handled purely by animation
     */
    private void applyGeckoLibBanking(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
    }

    /**
     * Handles smooth transitions between walking and running states
     */
    private void applyWalkRunTransitions(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (entity.isFlying()) return; // Only apply during ground movement


        float runWeight = entity.getWalkRunTransitionValue(animationState.getPartialTick());
        float walkWeight = 1.0f - runWeight;

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

            // Blend between walk and run postures
            float blendedLean = (walkBodyLean * walkWeight) + (runBodyLean * runWeight);

            bodyBone.addRotX(blendedLean);
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

        // Store the transition value for potential use by GeckoLib controllers
        // This makes the value available to JSON animations that might reference "walkRunController"
        storeControllerValue("walkRunController", runWeight);
    }

    /**
     * Handles smooth transitions between flapping and gliding
     */
    private void applyGlideTransitions(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (!entity.isFlying()) return; // Only apply during flight

        float glideWeight = entity.getGlideTransitionValue(animationState.getPartialTick());
        float flapWeight = 1.0f - glideWeight;

        // Wing angle adjustments with weight-based blending
        EnhancedGeoBone leftWingBone = getEnhancedBone("leftwing");
        EnhancedGeoBone rightWingBone = getEnhancedBone("rightwing");

        if (leftWingBone != null && rightWingBone != null) {
            // Flapping position: wings closer to body, more dynamic
            float flapWingAngle = 0.0f;

            // Gliding position: wings extended, stable
            float glideWingAngle = 0.08f; // More extension for efficient gliding

            // Blend between flap and glide positions
            float blendedWingExtension = (flapWingAngle * flapWeight) + (glideWingAngle * glideWeight);

            leftWingBone.addRotZ(blendedWingExtension);
            rightWingBone.addRotZ(-blendedWingExtension);
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

            // Blend between flap and glide pitch
            float blendedPitch = (flapPitch * flapWeight) + (glidePitch * glideWeight);

            bodyBone.addRotX(blendedPitch);
        }

        // Store the transition value for potential use by GeckoLib controllers
        // This makes the value available to JSON animations that might reference "glideController"
        storeControllerValue("glideController", glideWeight);
    }

    /**
     * Store controller values for potential JSON animation access
     */
    private final java.util.Map<String, Float> controllerValues = new java.util.HashMap<>();

    private void storeControllerValue(String controllerName, float value) {
        controllerValues.put(controllerName, value);
    }

    /**
     * This would be used if you want to reference controllers from JSON animations
     */
    public float getControllerValue(String controllerName) {
        return controllerValues.getOrDefault(controllerName, 0.0f);
    }

    // ===== LIGHTNING DRAGON SPECIFIC ANIMATIONS =====

    /**
     * Apply lightning-specific procedural animations like electrical effects
     */
    @Override
    public void applyCustomAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        if (!isInitialized()) return;

        // Get animation values from DragonAnimationController (like Umvuthi does with entity.legsUp)
        float glidingAmount = entity.getAnimationController().getGlidingFraction(animationState.getPartialTick());
        float flappingAmount = entity.getAnimationController().getFlappingFraction(animationState.getPartialTick());
        float hoveringAmount = entity.getAnimationController().getHoveringFraction(animationState.getPartialTick());

        // Apply wing dynamics based on flight state
        EnhancedGeoBone leftWing = getEnhancedBone("leftwing");
        EnhancedGeoBone rightWing = getEnhancedBone("rightwing");

        if (leftWing != null && rightWing != null) {
            // Gliding - wings extended and stable
            if (glidingAmount > 0.1f) {
                leftWing.addRot(0, 0, glidingAmount * 0.3f);   // Slight upward angle
                rightWing.addRot(0, 0, -glidingAmount * 0.3f);
            }

            // Flapping - dynamic wing movement
            if (flappingAmount > 0.1f) {
                float time = entity.tickCount + animationState.getPartialTick();
                float flapOffset = (float) Math.sin(time * 0.8f) * flappingAmount;

                leftWing.addRot(flapOffset * 0.2f, 0, flapOffset * 0.6f);
                rightWing.addRot(flapOffset * 0.2f, 0, -flapOffset * 0.6f);
            }

            // Hovering - subtle wing adjustments
            if (hoveringAmount > 0.1f) {
                float hoverSway = (float) Math.sin((entity.tickCount + animationState.getPartialTick()) * 0.3f);
                leftWing.addRot(0, 0, hoverSway * hoveringAmount * 0.15f);
                rightWing.addRot(0, 0, -hoverSway * hoveringAmount * 0.15f);
            }
        }
    }

    /**
     * Apply physics-based animations for wings, tail, etc.
     */
    @Override
    public void applyPhysicsAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        if (!isInitialized()) return;

        else {
            // Get enhanced tail bones for physics
            EnhancedGeoBone[] tailBones = getEnhancedTailChainBones();

            if (tailBones.length > 0) {
                Vec3 entityVelocity = entity.getDeltaMovement();
                float velocityMagnitude = (float) entityVelocity.length();

                // Apply physics-based tail movement based on velocity
                if (velocityMagnitude > 0.01f) {
                    // Tail should lag behind movement direction
                    Vec3 normalizedVelocity = entityVelocity.normalize();

                    for (int i = 0; i < tailBones.length; i++) {
                        EnhancedGeoBone tailBone = tailBones[i];
                        float segmentInfluence = (float) (i + 1) / tailBones.length; // More influence on later segments

                        // Calculate drag effect - tail segments should bend opposite to movement
                        float dragX = (float) (-normalizedVelocity.x * velocityMagnitude * segmentInfluence * 0.8f);
                        float dragY = (float) (-normalizedVelocity.y * velocityMagnitude * segmentInfluence * 0.4f);

                        // Apply spring-like physics for realistic tail movement
                        if (tailBone.isTailSegment()) {
                            tailBone.smoothRotateTowards(
                                    new Vector3d(dragX, dragY, 0), // Target rotation based on drag
                                    3.0f, // Speed of rotation
                                    animationState.getPartialTick()
                            );
                        }
                    }
                }
            }

            // Apply banking physics to wings during flight
            if (entity.isFlying()) {
                float bankingAmount = 0.0f;
                EnhancedGeoBone leftWing = getEnhancedBone("leftwing");
                EnhancedGeoBone rightWing = getEnhancedBone("rightwing");

                if (leftWing != null && rightWing != null && Math.abs(bankingAmount) > 0.05f) {
                    // Banking physics - outer wing lifts, inner wing drops
                    // Banking physics - outer wing lifts, inner wing drops
                    leftWing.addRot(-Math.abs(bankingAmount) * 0.2f, 0, bankingAmount * 0.4f);
                    rightWing.addRot(-Math.abs(bankingAmount) * 0.2f, 0, bankingAmount * 0.4f);
                }
            }
        }
    }
    
    /**
     * Get tail bones array (like ModelNaga's tailOriginal array)
     */
    private EnhancedGeoBone[] getTailBones(LightningDragonEntity dragon) {
        String[] tailBoneNames = {
                "tail1", "tail2", "tail3", "tail4", "tail5",
                "tail6", "tail7", "tail8", "tail9", "tail10"
        };

        EnhancedGeoBone[] tailBones = new EnhancedGeoBone[tailBoneNames.length];
        int found = 0;

        for (int i = 0; i < tailBoneNames.length; i++) {
            GeoBone bone = getBone(tailBoneNames[i]).orElse(null);
            if (bone != null) {
                if (bone instanceof EnhancedGeoBone enhancedBone) {
                    tailBones[i] = enhancedBone;
                } else {
                    tailBones[i] = new EnhancedGeoBone(bone);
                }
                found++;
            }
        }

        return tailBones;
    }
}