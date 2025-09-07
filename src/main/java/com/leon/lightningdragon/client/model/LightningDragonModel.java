package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import software.bernie.geckolib.model.GeoModel;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
/**
 * Lightning Dragon model with enhanced bone system and procedural animations
 * Extends DragonGeoModel for advanced dragon-specific features
 */
public class LightningDragonModel extends GeoModel<LightningDragonEntity> {
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
     * This is where head tracking happen
     */
    @Override
    public void applyMolangQueries(LightningDragonEntity animatable, double animTime) {
        // Do not hard-reset head/neck each frame; smoothing handles stability
        super.applyMolangQueries(animatable, animTime);
    }

    @Override
    public void setCustomAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        // Apply procedural animations when alive
        if (entity.isAlive()) {
            // Apply head tracking first
            applyHeadTracking(entity, animationState);

            // Apply tail physics
            applyTailPhysics(entity, animationState);
        }
    }

    /**
     * Apply head tracking to neck segments and head bone
     */
    private void applyHeadTracking(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        var headOpt = getBone("head");
        var neck4Opt = getBone("neck4");
        var neck3Opt = getBone("neck3");
        var neck2Opt = getBone("neck2");
        var neck1Opt = getBone("neck1");

        if (headOpt.isPresent()) {
            var head = headOpt.get();



            // If beaming, track beam target instead of player look direction
            if (entity.isBeaming()) {
                var beamEnd = entity.getClientBeamEndPosition(animationState.getPartialTick());
                if (beamEnd != null) {
                    // Reset all neck bones first to avoid accumulation
                    headOpt.ifPresent(this::resetBoneToSnapshot);
                    neck4Opt.ifPresent(this::resetBoneToSnapshot);
                    neck3Opt.ifPresent(this::resetBoneToSnapshot);
                    neck2Opt.ifPresent(this::resetBoneToSnapshot);
                    neck1Opt.ifPresent(this::resetBoneToSnapshot);

                    // Get head world position
                    var headWorldPos = entity.computeHeadMouthOrigin(animationState.getPartialTick());
                    if (headWorldPos != null) {
                        // Calculate direction to beam target
                        var direction = beamEnd.subtract(headWorldPos).normalize();

                        // Get dragon's body rotation
                        float bodyYaw = entity.getYRot() * Mth.DEG_TO_RAD;

                        // Transform direction into dragon's local coordinate system
                        double cosBodyYaw = Math.cos(bodyYaw);
                        double sinBodyYaw = Math.sin(bodyYaw);

                        // Rotate direction vector by inverse of body yaw to get local direction
                        double localX = direction.x * cosBodyYaw + direction.z * sinBodyYaw;
                        double localZ = -direction.x * sinBodyYaw + direction.z * cosBodyYaw;
                        double localY = direction.y;

                        // Calculate local yaw and pitch from transformed direction
                        float localYaw = (float) Math.atan2(localX, localZ);
                        float localPitch = (float) Math.asin(localY);

                        // Clamp to gentle ranges to prevent jitter; allow much larger pitch while flying
                        boolean flying = entity.isFlying();
                        float yawClamp = 0.8f; // ~±45° yaw
                        float pitchClamp = flying ? 1.5f : 0.6f; // up to ~±86° pitch in flight
                        localYaw = Mth.clamp(localYaw, -yawClamp, yawClamp);
                        localPitch = Mth.clamp(localPitch, -pitchClamp, pitchClamp);

                        // Distribute rotation across all neck segments + head with smoothing toward targets
                        // Higher influence on segments closer to the head
                        // Dynamic distribution: in flight, lean more on necks for big vertical movement
                        float headWYaw = flying ? 0.22f : 0.25f;
                        float headWPitch = flying ? 0.18f : 0.25f;
                        float n4WYaw = flying ? 0.26f : 0.22f;
                        float n4WPitch = flying ? 0.26f : 0.22f;
                        float n3WYaw = flying ? 0.24f : 0.20f;
                        float n3WPitch = flying ? 0.24f : 0.20f;
                        float n2WYaw = flying ? 0.20f : 0.18f;
                        float n2WPitch = flying ? 0.20f : 0.18f;
                        float n1WYaw = flying ? 0.12f : 0.15f;
                        float n1WPitch = flying ? 0.12f : 0.15f;

                        var headSnap = head.getInitialSnapshot();
                        float headTargetX = headSnap.getRotX() + localPitch * headWPitch;
                        float headTargetY = headSnap.getRotY() + localYaw * headWYaw;
                        float headAlphaX = adaptiveLerpAlpha(head.getRotX(), headTargetX);
                        float headAlphaY = adaptiveLerpAlpha(head.getRotY(), headTargetY);
                        head.setRotX(Mth.lerp(headAlphaX, head.getRotX(), headTargetX));
                        head.setRotY(Mth.lerp(headAlphaY, head.getRotY(), headTargetY));


                        if (neck4Opt.isPresent()) {
                            var neck4 = neck4Opt.get();
                            var s4 = neck4.getInitialSnapshot();
                            float t4x = s4.getRotX() + localPitch * n4WPitch;
                            float t4y = s4.getRotY() + localYaw * n4WYaw;
                            float a4x = adaptiveLerpAlpha(neck4.getRotX(), t4x);
                            float a4y = adaptiveLerpAlpha(neck4.getRotY(), t4y);
                            neck4.setRotX(Mth.lerp(a4x, neck4.getRotX(), t4x));
                            neck4.setRotY(Mth.lerp(a4y, neck4.getRotY(), t4y));
                        }

                        if (neck3Opt.isPresent()) {
                            var neck3 = neck3Opt.get();
                            var s3 = neck3.getInitialSnapshot();
                            float t3x = s3.getRotX() + localPitch * n3WPitch;
                            float t3y = s3.getRotY() + localYaw * n3WYaw;
                            float a3x = adaptiveLerpAlpha(neck3.getRotX(), t3x);
                            float a3y = adaptiveLerpAlpha(neck3.getRotY(), t3y);
                            neck3.setRotX(Mth.lerp(a3x, neck3.getRotX(), t3x));
                            neck3.setRotY(Mth.lerp(a3y, neck3.getRotY(), t3y));
                        }

                        if (neck2Opt.isPresent()) {
                            var neck2 = neck2Opt.get();
                            var s2 = neck2.getInitialSnapshot();
                            float t2x = s2.getRotX() + localPitch * n2WPitch;    // Neck2 gets 18%
                            float t2y = s2.getRotY() + localYaw * n2WYaw;
                            float a2x = adaptiveLerpAlpha(neck2.getRotX(), t2x);
                            float a2y = adaptiveLerpAlpha(neck2.getRotY(), t2y);
                            neck2.setRotX(Mth.lerp(a2x, neck2.getRotX(), t2x));
                            neck2.setRotY(Mth.lerp(a2y, neck2.getRotY(), t2y));
                        }

                        if (neck1Opt.isPresent()) {
                            var neck1 = neck1Opt.get();
                            var s1 = neck1.getInitialSnapshot();
                            float t1x = s1.getRotX() + localPitch * n1WPitch;    // Neck1 gets 15%
                            float t1y = s1.getRotY() + localYaw * n1WYaw;
                            float a1x = adaptiveLerpAlpha(neck1.getRotX(), t1x);
                            float a1y = adaptiveLerpAlpha(neck1.getRotY(), t1y);
                            neck1.setRotX(Mth.lerp(a1x, neck1.getRotX(), t1x));
                            neck1.setRotY(Mth.lerp(a1y, neck1.getRotY(), t1y));
                        }
                        // Total: 100% rotation distributed smoothly across all segments
                    }
                }
            } else {
                // Normal player look tracking when not beaming - also use all neck segments
                EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
                float headYaw = Mth.wrapDegrees(entityData.netHeadYaw());
                float headPitch = Mth.wrapDegrees(entityData.headPitch());

                // Convert to radians and apply dynamic clamp (more pitch while flying)
                float yawRad = headYaw * ((float) Math.PI / 180F);
                float pitchRad = headPitch * ((float) Math.PI / 180F);
                boolean flying = entity.isFlying();
                float yawClamp = 0.8f; // ~±45°
                float pitchClamp = flying ? 1.35f : 0.6f; // allow near-vertical aim in flight
                yawRad = Mth.clamp(yawRad, -yawClamp, yawClamp);
                pitchRad = Mth.clamp(pitchRad, -pitchClamp, pitchClamp);

                // Dynamic distribution similar to beaming
                float headWYaw = flying ? 0.22f : 0.25f;
                float headWPitch = flying ? 0.18f : 0.25f;
                float n4WYaw = flying ? 0.26f : 0.22f;
                float n4WPitch = flying ? 0.26f : 0.22f;
                float n3WYaw = flying ? 0.24f : 0.20f;
                float n3WPitch = flying ? 0.24f : 0.20f;
                float n2WYaw = flying ? 0.20f : 0.18f;
                float n2WPitch = flying ? 0.20f : 0.18f;
                float n1WYaw = flying ? 0.12f : 0.15f;
                float n1WPitch = flying ? 0.12f : 0.15f;

                // Distribute normal head tracking across all segments too (smoothed)
                var headSnap = head.getInitialSnapshot();
                float headTargetX = headSnap.getRotX() + pitchRad * headWPitch;
                float headTargetY = headSnap.getRotY() + yawRad * headWYaw;
                float headAlphaX = adaptiveLerpAlpha(head.getRotX(), headTargetX);
                float headAlphaY = adaptiveLerpAlpha(head.getRotY(), headTargetY);
                head.setRotX(Mth.lerp(headAlphaX, head.getRotX(), headTargetX));
                head.setRotY(Mth.lerp(headAlphaY, head.getRotY(), headTargetY));


                if (neck4Opt.isPresent()) {
                    var neck4 = neck4Opt.get();
                    var s4 = neck4.getInitialSnapshot();
                    float t4x = s4.getRotX() + pitchRad * n4WPitch;
                    float t4y = s4.getRotY() + yawRad * n4WYaw;
                    float a4x = adaptiveLerpAlpha(neck4.getRotX(), t4x);
                    float a4y = adaptiveLerpAlpha(neck4.getRotY(), t4y);
                    neck4.setRotX(Mth.lerp(a4x, neck4.getRotX(), t4x));
                    neck4.setRotY(Mth.lerp(a4y, neck4.getRotY(), t4y));
                }

                if (neck3Opt.isPresent()) {
                    var neck3 = neck3Opt.get();
                    var s3 = neck3.getInitialSnapshot();
                    float t3x = s3.getRotX() + pitchRad * n3WPitch;
                    float t3y = s3.getRotY() + yawRad * n3WYaw;
                    float a3x = adaptiveLerpAlpha(neck3.getRotX(), t3x);
                    float a3y = adaptiveLerpAlpha(neck3.getRotY(), t3y);
                    neck3.setRotX(Mth.lerp(a3x, neck3.getRotX(), t3x));
                    neck3.setRotY(Mth.lerp(a3y, neck3.getRotY(), t3y));
                }

                if (neck2Opt.isPresent()) {
                    var neck2 = neck2Opt.get();
                    var s2 = neck2.getInitialSnapshot();
                    float t2x = s2.getRotX() + pitchRad * n2WPitch;
                    float t2y = s2.getRotY() + yawRad * n2WYaw;
                    float a2x = adaptiveLerpAlpha(neck2.getRotX(), t2x);
                    float a2y = adaptiveLerpAlpha(neck2.getRotY(), t2y);
                    neck2.setRotX(Mth.lerp(a2x, neck2.getRotX(), t2x));
                    neck2.setRotY(Mth.lerp(a2y, neck2.getRotY(), t2y));
                }

                if (neck1Opt.isPresent()) {
                    var neck1 = neck1Opt.get();
                    var s1 = neck1.getInitialSnapshot();
                    float t1x = s1.getRotX() + pitchRad * n1WPitch;
                    float t1y = s1.getRotY() + yawRad * n1WYaw;
                    float a1x = adaptiveLerpAlpha(neck1.getRotX(), t1x);
                    float a1y = adaptiveLerpAlpha(neck1.getRotY(), t1y);
                    neck1.setRotX(Mth.lerp(a1x, neck1.getRotX(), t1x));
                    neck1.setRotY(Mth.lerp(a1y, neck1.getRotY(), t1y));
                }
            }
        }
    }

    /**
     * Apply physics-based tail animation using GeoBone chain approach
     * Adapted for aerial/terrestrial dragon movement with smooth following behavior
     */
    private void applyTailPhysics(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        float partialTicks = animationState.getPartialTick();
        float ageInTicks = (entity.tickCount + partialTicks);

        // Flight phase influence from entity flight controller
        float glideFrac = entity.getGlidingFraction();
        float flapFrac = entity.getFlappingFraction();
        float hoverFrac = entity.getHoveringFraction();

        // Get all tail bones
        GeoBone[] tailBones = new GeoBone[10];
        for (int i = 0; i < 10; i++) {
            var boneOpt = getBone("tail" + (i + 1));
            if (boneOpt.isPresent()) {
                tailBones[i] = boneOpt.get();
            }
        }
        // Calculate dragon's current body rotation and movement
        float bodyYaw = entity.yBodyRot;
        float previousBodyYaw = entity.yBodyRotO;
        float bodyYawDelta = Mth.wrapDegrees(bodyYaw - previousBodyYaw);

        // Movement-based values
        boolean isFlying = !entity.onGround() && entity.getDeltaMovement().y > -0.1;

        // Base idle swaying (calmer while gliding, stronger while flapping/hovering)
        float idleBase = 5F;
        float idleScale = Mth.lerp(glideFrac, 1.0f, 0.6f) + flapFrac * 0.3f + hoverFrac * 0.1f;
        float idleSwaying = (float) (Math.sin(ageInTicks * 0.03F) * idleBase * idleScale);

        // Apply chain physics simulation - each segment follows the previous
        for (int i = 0; i < tailBones.length; i++) {
            if (tailBones[i] != null) {
                float segmentIndex = i + 1;
                float segmentInfluence = segmentIndex / 10.0F; // 0.1 to 1.0

                // Tail following body rotation with increasing delay down the chain
                float followDelay = segmentIndex * 0.5F; // Reduced delay for less sensitivity
                float delayedBodyYaw = bodyYaw - (bodyYawDelta * followDelay * 0.05F);
                float targetYaw = Mth.wrapDegrees(delayedBodyYaw - bodyYaw) * segmentInfluence * 0.3F; // Reduced influence

                // Add idle swaying that increases toward tail tip
                float sway = idleSwaying * segmentInfluence * 0.5F; // Reduced sway

                // Add movement-based swing (tail follows turn direction); emphasize when flapping
                float movementSwing = bodyYawDelta * segmentInfluence * (0.8F + 0.3f * flapFrac);

                // Natural wave motion for organic feel; calmer while gliding
                float waveAmp = Mth.lerp(glideFrac, 1.0f, 0.5f);
                float waveMotion = (float) (Math.sin((ageInTicks * 0.06F) + (segmentIndex * 0.4F)) * waveAmp * segmentInfluence);

                // Flying gets more dramatic movement; modulate by flap/hover
                float flyModifier = isFlying ? (1.3F + 0.25f * flapFrac + 0.1f * hoverFrac) : 1.0F;

                // Combine all Y (horizontal) rotations
                float totalYaw = (targetYaw + sway + movementSwing + waveMotion) * flyModifier;

                // Apply rotation smoothly without resetting to snapshot
                float currentRotY = tailBones[i].getRotY();
                float targetRotY = tailBones[i].getInitialSnapshot().getRotY() + (totalYaw * Mth.DEG_TO_RAD);
                tailBones[i].setRotY(Mth.lerp(0.15f, currentRotY, targetRotY)); // Smooth interpolation

                // Add subtle vertical motion - same for both flying and grounded

                // Subtle vertical motion; slightly stronger while flapping, calmer while gliding
                float basePitchAmp = 0.5F * (1.0f + 0.25f * flapFrac);
                basePitchAmp = basePitchAmp * Mth.lerp(glideFrac, 1.0f, 0.7f);
                float totalPitch = (float) (Math.sin((ageInTicks * 0.04F) + (segmentIndex * 0.3F)) * basePitchAmp * segmentInfluence);

                // Apply X rotation smoothly without resetting to snapshot
                float currentRotX = tailBones[i].getRotX();
                float targetRotX = tailBones[i].getInitialSnapshot().getRotX() + (totalPitch * Mth.DEG_TO_RAD);
                tailBones[i].setRotX(Mth.lerp(0.15f, currentRotX, targetRotX)); // Smooth interpolation
            }
        }
    }
    private void resetBoneToSnapshot(software.bernie.geckolib.cache.object.GeoBone bone) {
        var initialSnapshot = bone.getInitialSnapshot();

        bone.setRotX(initialSnapshot.getRotX());
        bone.setRotY(initialSnapshot.getRotY());
        bone.setRotZ(initialSnapshot.getRotZ());

        bone.setPosX(initialSnapshot.getOffsetX());
        bone.setPosY(initialSnapshot.getOffsetY());
        bone.setPosZ(initialSnapshot.getOffsetZ());

        bone.setScaleX(initialSnapshot.getScaleX());
        bone.setScaleY(initialSnapshot.getScaleY());
        bone.setScaleZ(initialSnapshot.getScaleZ());
    }

    private static float adaptiveLerpAlpha(float current, float target) {
        float err = Math.abs(target - current);
        // Map error (radians) to a lerp alpha [min,max]
        // Small error -> softer smoothing; large error -> faster catch-up
        float minA = 0.18f;
        float maxA = 0.8f;
        // Scale error: ~0.0..1.0 for up to ~30 degrees (0.52 rad)
        float scaled = Mth.clamp(err / 0.52f, 0.0f, 1.0f);
        return Mth.lerp(scaled, minA, maxA);
    }
}