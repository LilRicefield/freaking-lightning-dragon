package com.leon.lightningdragon.client.model.enhanced;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;

import java.util.Optional;

/**
 * Enhanced GeoModel base class for dragon entities
 * Provides advanced bone manipulation, physics support, and animation utilities
 */
public abstract class DragonGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    public DragonGeoModel() {
        super();
    }
    
    // ===== ENHANCED BONE ACCESS =====
    
    /**
     * Get enhanced bone with advanced features
     * Automatically wraps regular GeoBones in EnhancedGeoBone for convenience
     * Returns null if bone doesn't exist
     */
    public EnhancedGeoBone getEnhancedBone(String boneName) {
        Optional<GeoBone> bone = this.getBone(boneName);
        if (bone.isPresent()) {
            if (bone.get() instanceof EnhancedGeoBone) {
                return (EnhancedGeoBone) bone.get();
            } else {
                // Wrap regular GeoBone in EnhancedGeoBone for advanced features
                return new EnhancedGeoBone(bone.get());
            }
        }
        return null;
    }
    
    /**
     * Get regular bone but cast as EnhancedGeoBone for convenience
     * Use with caution - only if you're sure your bones are enhanced
     */
    public EnhancedGeoBone getEnhancedBoneUnsafe(String boneName) {
        Optional<GeoBone> bone = this.getBone(boneName);
        return (EnhancedGeoBone) bone.orElse(null);
    }
    
    // ===== BONE RESET SYSTEM =====
    
    /**
     * Resets bone to its initial snapshot values for clean animations
     * Essential for preventing animation accumulation over time
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
        
        // ALSO reset EnhancedGeoBone if this is one
        if (bone instanceof EnhancedGeoBone enhancedBone) {
            enhancedBone.resetToInitialSnapshot();
        }
    }
    
    /**
     * CRITICAL: Reset bones to snapshots before applying new animations (like MowzieGeoModel)
     * This prevents animation drift and ensures consistent results
     * This is what makes the switch-bone → value → blend pattern work reliably!
     */
    @Override
    public void applyMolangQueries(T animatable, double animTime) {
        // Reset ALL bones to their initial snapshots to prevent drift
        getAnimationProcessor().getRegisteredBones().forEach(this::resetBoneToSnapshot);
        super.applyMolangQueries(animatable, animTime);
    }
    
    // ===== ANIMATION UTILITIES =====
    
    /**
     * Check if model is properly initialized and ready for bone manipulation
     */
    public boolean isInitialized() {
        return !this.getAnimationProcessor().getRegisteredBones().isEmpty();
    }
    
    /**
     * Get controller value for animation blending (like MowzieGeoModel)
     * Controllers are special bones that store animation state values
     * This is the CORE of the switch-bone → value → blend pattern!
     */
    public float getControllerValue(String controllerName) {
        if (!isInitialized()) return 0.0f;
        Optional<GeoBone> bone = getBone(controllerName);
        if (bone.isEmpty()) return 0.0f;
        // Read the X position of the controller bone (0.0 to 1.0)
        // This value comes from your switch animations moving the bone
        return bone.get().getPosX();
    }
    
    /**
     * Get inverted controller value (1.0 - value)
     * Useful for blend animations where you need the opposite value
     */
    public float getControllerValueInverted(String controllerName) {
        if (!isInitialized()) return 1.0f;
        Optional<GeoBone> bone = getBone(controllerName);
        if (bone.isEmpty()) return 1.0f;
        return 1.0f - bone.get().getPosX();
    }
    // ===== ANIMATION HELPER METHODS =====
    
    /**
     * Smoothly blend between two rotations using linear interpolation
     */
    public static float lerpRotation(float current, float target, float speed) {
        float diff = target - current;
        // Normalize to [-180, 180] range
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        
        return current + diff * speed;
    }
    
    /**
     * Apply sine wave motion to a bone (useful for idle breathing, wing flapping, etc.)
     */
    public static float applyWaveMotion(float time, float frequency, float amplitude, float offset) {
        return amplitude * (float) Math.sin(time * frequency) + offset;
    }
    
    /**
     * Apply smooth step interpolation (ease in/out)
     */
    public static float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
    
    // ===== FUTURE EXTENSION POINTS =====
    
    /**
     * Override this method in your dragon models for custom procedural animations
     * This is called after all normal animations are applied
     */
    public void applyCustomAnimations(T entity, long instanceId, AnimationState<T> animationState) {
        // Default implementation does nothing
        // Override in subclasses for specific dragon behaviors
    }
    
    /**
     * Override this method to apply physics-based animations
     * Called after custom animations
     */
    public void applyPhysicsAnimations(T entity, long instanceId, AnimationState<T> animationState) {
        // Default implementation does nothing  
        // Override for tail physics, wing dynamics, etc.
    }
}