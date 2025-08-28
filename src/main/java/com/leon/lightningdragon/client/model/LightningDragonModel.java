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
        
        // Apply head tracking
        applyHeadTracking(entity, animationState);
        
        // Apply EntityNaga-style body banking (most important!)
        applyBodyBanking(entity, animationState);
        
        // Apply custom dragon-specific animations
        applyCustomAnimations(entity, instanceId, animationState);
        
        // Apply physics-based animations
        applyPhysicsAnimations(entity, instanceId, animationState);
    }
    
    /**
     * Handle head and neck tracking behavior
     */
    private void applyHeadTracking(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (!entity.isAlive()) return;
        
        GeoBone headBone = getBone("head").orElse(null);
        GeoBone neckBone = getBone("neck").orElse(null);
        
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
            
            if (neckBone != null) {
                // Apply a base downward pitch to counteract upward-angled neck
                float neckBasePitch = -0.15f;
                float headBasePitch = 0;
                
                // During flight, reduce neck participation to prevent extreme bending
                if (isFlying) {
                    // Flight mode: Head does 70% of the rotation, neck only 30%
                    headBone.updateRotation((pitchRad * 0.7f) + headBasePitch, yawRad * 0.7f, 0);
                    neckBone.updateRotation((pitchRad * 0.3f) + neckBasePitch, yawRad * 0.3f, 0);
                } else {
                    // Ground mode: Split rotation 50/50 between head and neck
                    headBone.updateRotation((pitchRad / 2f) + headBasePitch, yawRad / 2f, 0);
                    neckBone.updateRotation((pitchRad / 2f) + neckBasePitch, yawRad / 2f, 0);
                }
            } else {
                // No neck bone, apply rotation to head with slight downward correction
                float intensity = isFlying ? 0.5f : 1.0f; // 50% intensity during flight
                headBone.updateRotation((pitchRad * intensity) - 0.2f, yawRad * intensity, 0);
            }
        }
    }
    
    /**
     * Apply EntityNaga-style body banking using Z-axis rotation
     * This is the EXACT approach from ModelNaga line 1234
     */
    private void applyBodyBanking(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (!entity.isFlying()) return; // Only during flight like EntityNaga
        
        // Get body/root bone (try both names from renderer)
        GeoBone bodyBone = getBone("body").orElse(getBone("root").orElse(null));
        if (bodyBone == null) return;
        
        // EXACT EntityNaga approach (ModelNaga line 1233-1234):
        // float banking = entity.prevBanking + (entity.banking - entity.prevBanking) * delta;
        // body.rotateAngleZ -= 10 * Math.toRadians(banking) * nonHoverAnim;
        
        float banking = Mth.lerp(animationState.getPartialTick(), entity.getPrevBanking(), entity.getBanking());
        float nonHoverAnim = entity.isHovering() ? 0.0f : 1.0f; // Reduce banking during hover like EntityNaga
        
        // Apply EntityNaga's exact formula: 10 * Math.toRadians(banking)
        float bankingRadians = (float) (5 * Math.toRadians(banking) * nonHoverAnim);
        
        // Apply Z-axis rotation (roll) to body bone
        // EntityNaga uses -= so we negate it
        bodyBone.updateRotation(bodyBone.getRotX(), bodyBone.getRotY(), bodyBone.getRotZ() - bankingRadians);
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
        
        // Apply wing dynamics based on flight state (like Umvuthi's leg movement)
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
        
        // Apply electrical charge effects to spine/horn bones
        EnhancedGeoBone[] spineBones = getEnhancedTailChainBones(); // We can reuse this for spine
        if (spineBones.length > 0) {
            float electricalCharge = entity.isLightningStreamActive() ? 1.0f : 0.0f;
            if (electricalCharge > 0) {
                float time = entity.tickCount + animationState.getPartialTick();
                
                for (int i = 0; i < Math.min(3, spineBones.length); i++) { // Only first 3 for spine
                    EnhancedGeoBone spineBone = spineBones[i];
                    float sparkleOffset = (float) Math.sin(time * 2.0f + i * 0.5f) * electricalCharge;
                    spineBone.addRot(0, sparkleOffset * 0.1f, 0);
                }
            }
        }
    }
    
    /**
     * Apply physics-based animations for wings, tail, etc.
     */
    @Override
    public void applyPhysicsAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        if (!isInitialized()) return;
        
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
                    float segmentInfluence = (float)(i + 1) / tailBones.length; // More influence on later segments
                    
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
            float bankingAmount = entity.getBanking();
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