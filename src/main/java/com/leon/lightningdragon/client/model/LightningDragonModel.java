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
        // Only reset bones if not beaming to avoid conflicts
        if (!animatable.isBeaming()) {
            getBone("head").ifPresent(this::resetBoneToSnapshot);
            getBone("neck4").ifPresent(this::resetBoneToSnapshot);
            getBone("neck3").ifPresent(this::resetBoneToSnapshot);
            getBone("neck2").ifPresent(this::resetBoneToSnapshot);
            getBone("neck1").ifPresent(this::resetBoneToSnapshot);
        }
        
        // Apply standard Molang queries and animations
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
                    resetBoneToSnapshot(head);
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
                        
                        // Clamp to reasonable ranges for better flexibility
                        localYaw = Mth.clamp(localYaw, -2.0f, 2.0f);    // Wider yaw range
                        localPitch = Mth.clamp(localPitch, -1.8f, 1.8f); // Much wider pitch range
                        
                        // Distribute rotation across all neck segments + head for smoother curve
                        // Higher influence on segments closer to the head
                        head.setRotX(head.getRotX() + localPitch * 0.25f);      // Head gets 25%
                        head.setRotY(head.getRotY() + localYaw * 0.25f);
                        
                        if (neck4Opt.isPresent()) {
                            var neck4 = neck4Opt.get();
                            neck4.setRotX(neck4.getRotX() + localPitch * 0.22f);    // Neck4 gets 22%
                            neck4.setRotY(neck4.getRotY() + localYaw * 0.22f);
                        }
                        
                        if (neck3Opt.isPresent()) {
                            var neck3 = neck3Opt.get();
                            neck3.setRotX(neck3.getRotX() + localPitch * 0.20f);    // Neck3 gets 20%
                            neck3.setRotY(neck3.getRotY() + localYaw * 0.20f);
                        }
                        
                        if (neck2Opt.isPresent()) {
                            var neck2 = neck2Opt.get();
                            neck2.setRotX(neck2.getRotX() + localPitch * 0.18f);    // Neck2 gets 18%
                            neck2.setRotY(neck2.getRotY() + localYaw * 0.18f);
                        }
                        
                        if (neck1Opt.isPresent()) {
                            var neck1 = neck1Opt.get();
                            neck1.setRotX(neck1.getRotX() + localPitch * 0.15f);    // Neck1 gets 15%
                            neck1.setRotY(neck1.getRotY() + localYaw * 0.15f);
                        }
                        // Total: 100% rotation distributed smoothly across all segments
                    }
                }
            } else {
                // Normal player look tracking when not beaming - also use all neck segments
                EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
                float headYaw = Mth.wrapDegrees(entityData.netHeadYaw());
                float headPitch = Mth.wrapDegrees(entityData.headPitch());
                
                // Convert to radians and distribute across all segments
                float yawRad = headYaw * ((float) Math.PI / 180F);
                float pitchRad = headPitch * ((float) Math.PI / 180F);
                
                // Distribute normal head tracking across all segments too
                head.setRotX(head.getRotX() + pitchRad * 0.25f);
                head.setRotY(head.getRotY() + yawRad * 0.25f);
                
                if (neck4Opt.isPresent()) {
                    var neck4 = neck4Opt.get();
                    neck4.setRotX(neck4.getRotX() + pitchRad * 0.22f);
                    neck4.setRotY(neck4.getRotY() + yawRad * 0.22f);
                }
                
                if (neck3Opt.isPresent()) {
                    var neck3 = neck3Opt.get();
                    neck3.setRotX(neck3.getRotX() + pitchRad * 0.20f);
                    neck3.setRotY(neck3.getRotY() + yawRad * 0.20f);
                }
                
                if (neck2Opt.isPresent()) {
                    var neck2 = neck2Opt.get();
                    neck2.setRotX(neck2.getRotX() + pitchRad * 0.18f);
                    neck2.setRotY(neck2.getRotY() + yawRad * 0.18f);
                }
                
                if (neck1Opt.isPresent()) {
                    var neck1 = neck1Opt.get();
                    neck1.setRotX(neck1.getRotX() + pitchRad * 0.15f);
                    neck1.setRotY(neck1.getRotY() + yawRad * 0.15f);
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
        
        // Base idle swaying
        float idleSwaying = (float) (Math.sin(ageInTicks * 0.03F) * 5F);
        
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
                
                // Add movement-based swing (tail follows turn direction, not opposite)
                float movementSwing = bodyYawDelta * segmentInfluence * 0.8F; // Reduced and corrected direction
                
                // Natural wave motion for organic feel
                float waveMotion = (float) (Math.sin((ageInTicks * 0.06F) + (segmentIndex * 0.4F)) * 1F * segmentInfluence); // Reduced amplitude
                
                // Flying gets more dramatic movement
                float flyModifier = isFlying ? 1.3F : 1.0F; // Reduced modifier
                
                // Combine all Y (horizontal) rotations
                float totalYaw = (targetYaw + sway + movementSwing + waveMotion) * flyModifier;
                
                // Apply rotation smoothly without resetting to snapshot
                float currentRotY = tailBones[i].getRotY();
                float targetRotY = tailBones[i].getInitialSnapshot().getRotY() + (totalYaw * Mth.DEG_TO_RAD);
                tailBones[i].setRotY(Mth.lerp(0.15f, currentRotY, targetRotY)); // Smooth interpolation
                
                // Add subtle vertical motion - same for both flying and grounded

                // Remove speed bounce and flying modifiers for vertical motion
                float totalPitch = (float) (Math.sin((ageInTicks * 0.04F) + (segmentIndex * 0.3F)) * 0.5F * segmentInfluence); // Keep it simple and consistent
                
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
}