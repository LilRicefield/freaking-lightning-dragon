package com.leon.lightningdragon.server.entity.handler;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.common.registry.ModSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;

/**
 * Handles all sound effects for the Lightning Dragon
 * Separates sound logic from entity class for cleaner organization
 */
public class DragonSoundHandler {
    private final LightningDragonEntity dragon;
    
    public DragonSoundHandler(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }
    
    /**
     * Handle keyframe-based sound effects during animations
     * Call this from animation controller sound handlers
     */
    public void handleAnimationSound(SoundKeyframeEvent<LightningDragonEntity> event) {
        String sound = event.getKeyframeData().getSound();
        
        switch (sound) {
            case "wing_flap" -> handleWingFlapSound();
            case "dragon_step" -> handleStepSound();
            case "takeoff_whoosh" -> handleTakeoffSound();
            case "landing_thud" -> handleLandingSound();
        }
    }
    
    /**
     * Handle sound by name - for direct sound triggering
     */
    public void handleSoundByName(String soundName) {
        switch (soundName) {
            case "wing_flap" -> handleWingFlapSound();
            case "dragon_step" -> handleStepSound();
            case "takeoff_whoosh" -> handleTakeoffSound();
            case "landing_thud" -> handleLandingSound();
        }
    }
    
    /**
     * Wing flap sound with dynamic speed variation
     */
    private void handleWingFlapSound() {
        double flightSpeed = dragon.getCachedHorizontalSpeed();
        float pitch = 1.0f + (float)(flightSpeed * 0.3f); // Higher pitch when flying faster
        float volume = Math.max(0.6f, 0.8f + (float)(flightSpeed * 0.2f));
        
        dragon.level().playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), 
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, volume, pitch);
    }
    
    /**
     * Dragon step sound with weight variation
     */
    private void handleStepSound() {
        // Heavier steps when running or carrying rider
        float weight = 1.0f;
        if (dragon.isRunning()) weight *= 1.2f;
        if (dragon.isVehicle()) weight *= 1.1f;
        if (dragon.getHealth() < dragon.getMaxHealth() * 0.5f) weight *= 0.8f; // Lighter when injured
        
        float volume = 0.7f * weight;
        float pitch = (0.9f + dragon.getRandom().nextFloat() * 0.2f) / weight; // Lower pitch for heavier steps
        
        dragon.playSound(ModSounds.DRAGON_STEP.get(), volume, pitch);
    }
    
    /**
     * Takeoff sound with urgency variation
     */
    private void handleTakeoffSound() {
        float urgency = dragon.getTarget() != null ? 1.3f : 1.0f;
        dragon.playSound(SoundEvents.ENDER_DRAGON_FLAP, urgency * 1.2f, 0.8f);
    }
    
    /**
     * Landing sound with impact variation
     */
    private void handleLandingSound() {
        double impactSpeed = Math.abs(dragon.getDeltaMovement().y);
        float volume = (float) Math.max(0.8f, 1.0f + impactSpeed * 2.0f);
        float pitch = (float) Math.max(0.7f, 1.0f - impactSpeed * 0.3f);
        
        dragon.playSound(SoundEvents.GENERIC_EXPLODE, volume * 0.6f, pitch);
    }
}