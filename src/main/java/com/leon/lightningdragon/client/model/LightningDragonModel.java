package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
/**
 * Lightning Dragon model with enhanced bone system and procedural animations
 * Now uses DefaultedEntityGeoModel for entity-focused conveniences (default asset paths, hooks)
 */
public class LightningDragonModel extends DefaultedEntityGeoModel<LightningDragonEntity> {
    public LightningDragonModel() {
        // Defaulted paths under entity/ and built-in head rotation for "head" bone
        super(LightningDragonMod.rl("lightning_dragon"), "head");
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
            // Clamp built-in head rotation to sane limits first
            applyHeadClamp(entity);

            // Light neck follow based on clamped head rotation
            applyNeckFollow();

            // Tail physics retained
            applyTailPhysics(entity, animationState);
        }
    }

    /**
     * Clamp head yaw/pitch deltas so the dragon cannot look fully backwards.
     * Uses deltas relative to the initial snapshot to avoid drift.
     */
    private void applyHeadClamp(LightningDragonEntity entity) {
        var headOpt = getBone("head");
        if (headOpt.isEmpty()) return;

        GeoBone head = headOpt.get();
        var snap = head.getInitialSnapshot();

        float deltaY = head.getRotY() - snap.getRotY();
        float deltaX = head.getRotX() - snap.getRotX();

        float yawClamp = entity.isFlying() ? 0.9f : 0.6f;   // ~51° / ~34°
        float pitchClamp = entity.isFlying() ? 1.0f : 0.5f; // ~57° / ~29°

        deltaY = Mth.clamp(deltaY, -yawClamp, yawClamp);
        deltaX = Mth.clamp(deltaX, -pitchClamp, pitchClamp);

        head.setRotY(snap.getRotY() + deltaY);
        head.setRotX(snap.getRotX() + deltaX);
    }

    /**
     * Light neck follow: gently propagates head rotation down neck1 to neck4.
     * Uses small weights and smoothing to avoid over-rotation.
     */
    private void applyNeckFollow() {
        var headOpt = getBone("head");
        if (headOpt.isEmpty()) return;

        GeoBone head = headOpt.get();

        float headDeltaX = head.getRotX() - head.getInitialSnapshot().getRotX();
        float headDeltaY = head.getRotY() - head.getInitialSnapshot().getRotY();

        // Gentle distribution from head down the neck (tip follows more than base)
        applyNeckBoneFollow("neck4", headDeltaX, headDeltaY, 0.16f);
        applyNeckBoneFollow("neck3", headDeltaX, headDeltaY, 0.12f);
        applyNeckBoneFollow("neck2", headDeltaX, headDeltaY, 0.08f);
        applyNeckBoneFollow("neck1", headDeltaX, headDeltaY, 0.05f);
    }

    private void applyNeckBoneFollow(String boneName, float headDeltaX, float headDeltaY, float weight) {
        var boneOpt = getBone(boneName);
        if (boneOpt.isEmpty()) return;

        GeoBone bone = boneOpt.get();
        var snap = bone.getInitialSnapshot();

        // Cap how much any single neck bone can add from the head
        float maxNeckPitch = 0.35f; // ~20°
        float maxNeckYaw   = 0.35f; // ~20°
        float addX = Mth.clamp(headDeltaX * weight, -maxNeckPitch, maxNeckPitch);
        float addY = Mth.clamp(headDeltaY * weight, -maxNeckYaw,   maxNeckYaw);

        float targetX = snap.getRotX() + addX;
        float targetY = snap.getRotY() + addY;

        // Smooth towards target to keep motion stable
        float lerpA = 0.18f;
        bone.setRotX(Mth.lerp(lerpA, bone.getRotX(), targetX));
        bone.setRotY(Mth.lerp(lerpA, bone.getRotY(), targetY));
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
}
