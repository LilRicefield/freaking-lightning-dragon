package com.leon.lightningdragon.entity.controller;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Manages all state data for the Lightning Dragon, wrapping entity data accessors
 * to reduce clutter in the main entity class. Handles:
 * - Flight states (flying, takeoff, landing, hovering)  
 * - Movement states (running, banking)
 * - Combat states (attacking, lightning targets)
 * - Animation progress states
 */
public class DragonStateManager {
    private final LightningDragonEntity dragon;
    
    // Speed constants
    private static final double WALK_SPEED = 0.25D;
    private static final double RUN_SPEED = 0.45D;

    public DragonStateManager(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }

    // ===== FLIGHT STATES =====
    
    public boolean isFlying() { 
        return dragon.isFlying(); 
    }

    public void setFlying(boolean flying) {
        dragon.setFlying(flying);
    }

    public boolean isTakeoff() { 
        return dragon.isTakeoff(); 
    }
    
    public void setTakeoff(boolean takeoff) {
        dragon.setTakeoff(takeoff);
    }

    public boolean isHovering() { 
        return dragon.isHovering(); 
    }
    
    public void setHovering(boolean hovering) {
        dragon.setHovering(hovering);
    }

    public boolean isLanding() { 
        return dragon.isLanding(); 
    }

    public void setLanding(boolean landing) {
        dragon.setLanding(landing);
    }

    // ===== BANKING STATES =====
    
    public float getBanking() { 
        return dragon.getBanking(); 
    }
    
    public void setBanking(float banking) { 
        dragon.setBanking(banking); 
    }
    
    public float getPrevBanking() { 
        return dragon.getPrevBanking(); 
    }
    
    public void setPrevBanking(float prevBanking) { 
        dragon.setPrevBanking(prevBanking); 
    }

    // ===== MOVEMENT STATES =====
    
    public boolean isRunning() { 
        return dragon.isRunning(); 
    }

    public void setRunning(boolean running) {
        dragon.setRunning(running);
    }

    public boolean isWalking() {
        return dragon.isWalking();
    }

    public boolean isActuallyRunning() {
        return dragon.isActuallyRunning();
    }

    // ===== COMBAT STATES =====
    
    public boolean isAttacking() { 
        return dragon.isAttacking(); 
    }
    
    public void setAttacking(boolean attacking) { 
        dragon.setAttacking(attacking); 
    }

    // ===== LIGHTNING TARGET STATES =====
    
    public void setHasLightningTarget(boolean lightning_target) {
        dragon.setHasLightningTarget(lightning_target);
    }

    public boolean hasLightningTarget() {
        return dragon.hasLightningTarget();
    }

    public void setLightningTargetVec(float x, float y, float z) {
        dragon.setLightningTargetVec(x, y, z);
    }

    public float getLightningTargetX() {
        return dragon.getLightningTargetX();
    }

    public float getLightningTargetY() {
        return dragon.getLightningTargetY();
    }

    public float getLightningTargetZ() {
        return dragon.getLightningTargetZ();
    }

    public Vec3 getLightningTargetVec() {
        return dragon.getLightningTargetVec();
    }

    public void setLightningStreamProgress(float progress) {
        dragon.setLightningStreamProgress(progress);
    }

    public float getLightningStreamProgress() {
        return dragon.getLightningStreamProgress();
    }

    public void setLightningStreamActive(boolean active) {
        dragon.setLightningStreamActive(active);
    }

    public boolean isLightningStreamActive() {
        return dragon.isLightningStreamActive();
    }

    // ===== SITTING PROGRESS STATE =====
    
    public float getSitProgress(float partialTicks) {
        return dragon.getSitProgress(partialTicks);
    }

    // ===== DODGE STATES =====
    
    public boolean isDodging() { 
        return dragon.isDodging(); 
    }

    public void beginDodge(Vec3 vec, int ticks) {
        dragon.beginDodge(vec, ticks);
    }

    // ===== UTILITY METHODS =====
    
    /**
     * Checks if dragon is in a valid sitting pose
     */
    public boolean isInSittingPose() {
        return dragon.isInSittingPose();
    }

    /**
     * Updates running attributes when state changes
     */
    public void updateRunningAttributes() {
        if (isRunning() && !dragon.hasRunningAttributes) {
            dragon.hasRunningAttributes = true;
            Objects.requireNonNull(dragon.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(RUN_SPEED);
        }
        if (!isRunning() && dragon.hasRunningAttributes) {
            dragon.hasRunningAttributes = false;
            Objects.requireNonNull(dragon.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(WALK_SPEED);
        }
    }

    /**
     * Auto-stops running if not moving much
     */
    public void autoStopRunning() {
        if (isRunning() && dragon.getDeltaMovement().horizontalDistanceSqr() < 0.01) {
            setRunning(false);
        }
    }
}