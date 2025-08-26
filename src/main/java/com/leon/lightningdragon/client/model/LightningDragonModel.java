package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.client.model.enhanced.EnhancedGeoBone;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.Optional;

/**
 * - Bone reset system for clean animations
 * - Enhanced bone access utilities
 * - Future physics chain support
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
    
    // ===== ENHANCED BONE SYSTEM =====
    
    /**
     * Get enhanced bone with advanced features (when available)
     */
    public EnhancedGeoBone getEnhancedBone(String boneName) {
        Optional<GeoBone> bone = this.getBone(boneName);
        if (bone.isPresent() && bone.get() instanceof EnhancedGeoBone) {
            return (EnhancedGeoBone) bone.get();
        }
        return null;
    }
    
    /**
     * Resets bone to its initial snapshot values for clean animations
     */
    public void resetBoneToSnapshot(CoreGeoBone bone) {
        BoneSnapshot initialSnapshot = bone.getInitialSnapshot();
        
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
    
    /**
     * Ensures bone states are reset before applying new animations
     */
    @Override
    public void applyMolangQueries(LightningDragonEntity animatable, double animTime) {
        // Reset all registered bones to their initial state before applying new animations
        getAnimationProcessor().getRegisteredBones().forEach(this::resetBoneToSnapshot);
        super.applyMolangQueries(animatable, animTime);
    }
    
    /**
     * This is where head tracking and other procedural animations happen
     * Same approach as ModelUmvuthana.setCustomAnimations()
     */
    @Override
    public void setCustomAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        
        if (entity.isAlive()) {
            GeoBone headBone = getBone("head").orElse(null);
            GeoBone neckBone = getBone("neck").orElse(null); // Optional neck bone
            
            if (headBone != null) {
                EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
                float headYaw = Mth.wrapDegrees(entityData.netHeadYaw());
                float headPitch = Mth.wrapDegrees(entityData.headPitch());
                
                // Clamp to reasonable ranges
                headYaw = Mth.clamp(headYaw, -75.0f, 75.0f);
                headPitch = Mth.clamp(headPitch, -60.0f, 60.0f);
                
                //  CONVERSION AND SPLIT SYSTEM
                float yawRad = headYaw * ((float) Math.PI / 180F);
                float pitchRad = headPitch * ((float) Math.PI / 180F);
                
                if (neckBone != null) {
                    // Apply a base downward pitch to counteract upward-angled neck
                    float neckBasePitch = -0.15f;
                    float headBasePitch = 0; // No changes
                    
                    // Split rotation 50/50 between head and neck with base corrections
                    headBone.updateRotation((pitchRad / 2f) + headBasePitch, yawRad / 2f, 0);
                    neckBone.updateRotation((pitchRad / 2f) + neckBasePitch, yawRad / 2f, 0);
                } else {
                    // No neck bone, apply full rotation to head with slight downward correction
                    headBone.updateRotation(pitchRad - 0.2f, yawRad, 0);
                }
            }
        }
    }
    
    /**
     * Get controller value for animation blending (future use)
     */
    public float getControllerValue(String controllerName) {
        if (!isInitialized()) return 0.0f;
        Optional<GeoBone> bone = getBone(controllerName);
        if (bone.isEmpty()) return 0.0f;
        return bone.get().getPosX();
    }
    
    /**
     * Get inverted controller value (1.0 - value)
     */
    public float getControllerValueInverted(String controllerName) {
        if (!isInitialized()) return 1.0f;
        Optional<GeoBone> bone = getBone(controllerName);
        if (bone.isEmpty()) return 1.0f;
        return 1.0f - bone.get().getPosX();
    }
    
    /**
     * Check if model is properly initialized
     */
    public boolean isInitialized() {
        return !this.getAnimationProcessor().getRegisteredBones().isEmpty();
    }
    
    // ===== UTILITY METHODS FOR FUTURE PHYSICS =====
    
    /**
     * Get all tail bones for potential dynamic chain setup
     */
    public GeoBone[] getTailChainBones() {
        String[] tailNames = {"tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7", "tail8", "tail9", "tail10"};
        return java.util.Arrays.stream(tailNames)
                .map(name -> getBone(name).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toArray(GeoBone[]::new);
    }
    
    /**
     * Get wing bones for potential physics...?
     */
    public GeoBone[] getWingBones() {
        String[] wingNames = {"leftwing", "rightwing"};
        return java.util.Arrays.stream(wingNames)
                .map(name -> getBone(name).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toArray(GeoBone[]::new);
    }
}