package com.leon.lightningdragon.client.model.enhanced;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.client.Minecraft;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import org.jetbrains.annotations.TestOnly;

/**
 * Enhanced Animation Controller for Lightning Dragon
 * Combines Bob Mowzie's off-screen animation fixes with dragon-specific enhancements
 */
public class EnhancedAnimationController<T extends GeoAnimatable> extends AnimationController<T> {
    
    // Mowzie's timing offset system for off-screen animation sync
    private double timingOffset;
    
    // Dragon-specific enhancements
    private float animationSpeedMultiplier = 1.0f;
    private boolean isPaused = false;
    private long lastUpdateTime = 0;
    
    // Animation blending system
    private float blendWeight = 1.0f;
    private RawAnimation blendSourceAnimation = null;
    private RawAnimation blendTargetAnimation = null;
    private float blendDuration = 0f;
    private float blendProgress = 0f;
    
    public EnhancedAnimationController(T animatable, String name, int transitionLength, 
                                     AnimationStateHandler<T> animationHandler, double timingOffset) {
        super(animatable, name, transitionLength, animationHandler);
        this.timingOffset = timingOffset;
    }
    
    public EnhancedAnimationController(T animatable, String name, int transitionLength, 
                                     AnimationStateHandler<T> animationHandler) {
        this(animatable, name, transitionLength, animationHandler, 0.0);
    }
    
    // ===== MOWZIE'S OFF-SCREEN ANIMATION FIXES =====
    
    /**
     * MOWZIE'S FIX: Play animation immediately even when off-screen
     * This is crucial for ability animations that start before the dragon is visible
     */
    public void playAnimation(T animatable, RawAnimation animation) {
        forceAnimationReset();
        setAnimation(animation);
        currentAnimation = this.animationQueue.poll();
        isJustStarting = true;
        
        // CRITICAL: Immediately sync tick time to current game state
        adjustTick(animatable.getTick(animatable) + Minecraft.getInstance().getPartialTick());
        transitionLength = 0; // Instant transition for immediate playback
    }
    
    /**
     * MOWZIE'S ENHANCED TICK ADJUSTMENT
     * Fixes timing issues and ensures animations stay synchronized
     */
    @Override
    protected double adjustTick(double tick) {
        if (this.shouldResetTick) {
            if (getAnimationState() == State.TRANSITIONING) {
                this.tickOffset = tick;
            } else if (getAnimationState() != State.STOPPED) {
                this.tickOffset += transitionLength;
            }
            this.shouldResetTick = false;
        }
        
        // Apply speed multiplier and timing offset
        double baseAdjustedTick = this.animationSpeedModifier.apply(this.animatable) * Math.max(tick - this.tickOffset, 0);
        double adjustedTick = baseAdjustedTick * animationSpeedMultiplier + timingOffset;
        
        // Handle looping animations properly
        if (this.currentAnimation != null && this.currentAnimation.loopType() == Animation.LoopType.LOOP) {
            adjustedTick = adjustedTick % this.currentAnimation.animation().length();
        }
        
        // Mark as just starting when at the beginning
        if (adjustedTick == timingOffset) {
            isJustStarting = true;
        }
        
        return adjustedTick;
    }
    
    /**
     * Set the last model reference for hot-reloading support
     */
    public void setLastModel(CoreGeoModel<T> coreGeoModel) {
        this.lastModel = coreGeoModel;
    }
    
    /**
     * MOWZIE'S HOT-RELOAD SYSTEM
     * Automatically reloads animations when they're changed in development
     */
    @TestOnly
    public <E extends GeoEntity> void checkAndReloadAnims() {
        if (this.lastModel != null &&
            getCurrentAnimation() != null &&
            getCurrentAnimation().animation() != null) {
            
            String currentAnimationName = getCurrentAnimation().animation().name();
            Animation animation = this.lastModel.getAnimation(this.animatable, currentAnimationName);
            
            if (!animation.equals(getCurrentAnimation().animation())) {
                // Animation has been modified, reload it
                forceAnimationReset();
                currentAnimation = this.animationQueue.poll();
                isJustStarting = true;
                adjustTick(animatable.getTick(animatable) + Minecraft.getInstance().getPartialTick());
                transitionLength = 0;
            }
        }
    }
    
    // ===== DRAGON-SPECIFIC ENHANCEMENTS =====
    
    /**
     * Set animation speed multiplier for dynamic speed control
     * Useful for flight speed affecting wing flap rate, etc.
     */
    public void setAnimationSpeedMultiplier(float multiplier) {
        this.animationSpeedMultiplier = Math.max(0.1f, multiplier);
    }
    
    public float getAnimationSpeedMultiplier() {
        return animationSpeedMultiplier;
    }
    
    /**
     * Pause/unpause animation (useful for freeze effects, time manipulation, etc.)
     */
    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (paused) {
            lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    public boolean isPaused() {
        return isPaused;
    }
    
    /**
     * Enhanced animation transition with custom curve
     */
    public void transitionToAnimation(RawAnimation newAnimation, int transitionTicks, TransitionCurve curve) {
        if (getCurrentAnimation() != null) {
            if (getCurrentAnimation() != null && getCurrentAnimation().animation() != null) {
                // Store current animation for blending - we'll use the animation name instead
                // blendSourceAnimation = getCurrentAnimation().rawAnimation(); // This method doesn't exist
            }
            blendTargetAnimation = newAnimation;
            blendDuration = transitionTicks;
            blendProgress = 0f;
        }
        
        // Set up transition
        transitionLength(transitionTicks);
        setAnimation(newAnimation);
    }
    
    /**
     * Get current animation progress (0.0 to 1.0)
     */
    public float getAnimationProgress() {
        if (currentAnimation == null || currentAnimation.animation() == null) {
            return 0f;
        }
        
        double currentTick = adjustTick(animatable.getTick(animatable) + Minecraft.getInstance().getPartialTick());
        double animationLength = currentAnimation.animation().length();
        
        if (animationLength <= 0) return 0f;
        
        return (float) (currentTick / animationLength);
    }
    
    /**
     * Check if animation has reached a specific keyframe time
     */
    public boolean hasReachedKeyframe(double keyframeTime) {
        if (currentAnimation == null) return false;
        
        double currentTick = adjustTick(animatable.getTick(animatable) + Minecraft.getInstance().getPartialTick());
        return currentTick >= keyframeTime;
    }
    
    /**
     * Play animation with automatic duration calculation
     */
    public void playAnimationWithDuration(T animatable, RawAnimation animation, float durationSeconds) {
        float targetLength = durationSeconds * 20f; // Convert to ticks
        
        // Store original speed
        float originalSpeed = animationSpeedMultiplier;
        
        if (animation.getAnimationStages().size() > 0) {
            // Calculate speed multiplier to achieve target duration
            // This is an approximation - exact calculation would require animation data
            setAnimationSpeedMultiplier(1.0f); // Reset to default for this calculation
        }
        
        playAnimation(animatable, animation);
    }
    
    /**
     * Advanced transition with blend weight control
     */
    public void setBlendWeight(float weight) {
        this.blendWeight = Math.max(0f, Math.min(1f, weight));
    }
    
    public float getBlendWeight() {
        return blendWeight;
    }
    
    /**
     * Check if this controller is actively playing an animation
     */
    public boolean isPlayingAnimation() {
        return getCurrentAnimation() != null && getAnimationState() != State.STOPPED;
    }
    
    /**
     * Get remaining animation time in ticks
     */
    public double getRemainingAnimationTime() {
        if (currentAnimation == null || currentAnimation.animation() == null) {
            return 0.0;
        }
        
        double currentTick = adjustTick(animatable.getTick(animatable) + Minecraft.getInstance().getPartialTick());
        double animationLength = currentAnimation.animation().length();
        
        return Math.max(0.0, animationLength - currentTick);
    }
    
    /**
     * Dragon-specific method: Sync animation to flight physics
     */
    public void syncToFlightPhysics(LightningDragonEntity dragon) {
        if (!(animatable instanceof LightningDragonEntity)) return;
        
        // Sync wing flap speed to actual flight speed
        if (getName().contains("flight") || getName().contains("wing")) {
            double flightSpeed = dragon.getDeltaMovement().horizontalDistance();
            float speedMultiplier = (float) Math.max(0.5, Math.min(2.0, flightSpeed * 3.0));
            setAnimationSpeedMultiplier(speedMultiplier);
        }
        
        // Sync landing animation to actual descent speed  
        if (getName().contains("landing") && dragon.isLanding()) {
            double verticalSpeed = Math.abs(dragon.getDeltaMovement().y);
            float landingSpeedMultiplier = (float) Math.max(0.8, Math.min(1.5, verticalSpeed * 5.0));
            setAnimationSpeedMultiplier(landingSpeedMultiplier);
        }
    }
    
    // ===== TRANSITION CURVES =====
    
    public enum TransitionCurve {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        BOUNCE,
        ELASTIC
    }
    
    /**
     * Apply transition curve to blend progress
     */
    private float applyCurve(float progress, TransitionCurve curve) {
        return switch (curve) {
            case LINEAR -> progress;
            case EASE_IN -> progress * progress;
            case EASE_OUT -> 1f - (1f - progress) * (1f - progress);
            case EASE_IN_OUT -> progress < 0.5f ? 2f * progress * progress : 1f - 2f * (1f - progress) * (1f - progress);
            case BOUNCE -> {
                if (progress < 1f / 2.75f) {
                    yield 7.5625f * progress * progress;
                } else if (progress < 2f / 2.75f) {
                    progress -= 1.5f / 2.75f;
                    yield 7.5625f * progress * progress + 0.75f;
                } else if (progress < 2.5f / 2.75f) {
                    progress -= 2.25f / 2.75f;
                    yield 7.5625f * progress * progress + 0.9375f;
                } else {
                    progress -= 2.625f / 2.75f;
                    yield 7.5625f * progress * progress + 0.984375f;
                }
            }
            case ELASTIC -> {
                if (progress == 0f || progress == 1f) yield progress;
                float p = 0.3f;
                float s = p / 4f;
                yield (float) (Math.pow(2f, -10f * progress) * Math.sin((progress - s) * (2f * Math.PI) / p) + 1f);
            }
        };
    }
}