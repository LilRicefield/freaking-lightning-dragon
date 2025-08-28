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

    // ===== STATE TRANSITIONS =====

    /**
     * Initiates takeoff sequence
     */
    public void initiatetakeoff() {
        setTakeoff(true);
        setFlying(false);
        setLanding(false);
        setHovering(false);
        setRunning(false);
    }

    /**
     * Transitions to full flight mode
     */
    public void transitionToFlying() {
        setFlying(true);
        setTakeoff(false);
        setLanding(false);
        setHovering(false);
    }

    /**
     * Begins hovering in place
     */
    public void beginHovering() {
        setHovering(true);
        setFlying(false);
        setTakeoff(false);
        setLanding(false);
    }

    /**
     * Initiates landing sequence
     */
    public void initiateLanding() {
        setLanding(true);
        setFlying(false);
        setTakeoff(false);
        setHovering(false);
    }

    /**
     * Completes landing and returns to ground mode
     */
    public void completeLanding() {
        setLanding(false);
        setFlying(false);
        setTakeoff(false);
        setHovering(false);
        setBanking(0.0f);
    }

    /**
     * Updates banking based on yaw rotation changes like EntityNaga
     */
    public void updateBankingFromRotation(float currentYaw, float prevYaw) {
        setPrevBanking(getBanking());
        
        // Calculate rotation difference with proper angle wrapping (like EntityNaga line 1226)
        float rotationDiff = currentYaw - prevYaw;
        
        // Wrap the angle difference to [-180, 180] range
        while (rotationDiff > 180f) rotationDiff -= 360f;
        while (rotationDiff < -180f) rotationDiff += 360f;
        
        // Scale the banking - EntityNaga uses the direct yaw change for smooth banking
        float targetBanking = rotationDiff * 4.0f; // Amplify for visible banking effect
        
        // Use DragonMathUtil.approachDegreesSmooth for smooth banking transition (like EntityNaga)
        float newBanking = com.leon.lightningdragon.util.DragonMathUtil.approachDegreesSmooth(
            getBanking(), getPrevBanking(), targetBanking, 0.5f, 0.1f
        );
        
        setBanking(newBanking);
    }

    /**
     * Resets banking to level flight
     */
    public void levelOutBanking() {
        setPrevBanking(getBanking());
        float currentBanking = getBanking();
        
        // Very gentle leveling out
        if (Math.abs(currentBanking) > 0.1f) {
            setBanking(currentBanking * 0.98f); // Even more gradual
        } else {
            setBanking(0.0f); // Snap to zero when very close
        }
    }

    // ===== UTILITY METHODS =====

    /**
     * Checks if dragon is in a valid sitting pose
     */
    public boolean isInSittingPose() {
        return dragon.isInSittingPose();
    }

    /**
     * Checks if dragon is in any flight state
     */
    public boolean isInAnyFlightState() {
        return isFlying() || isTakeoff() || isLanding() || isHovering();
    }

    /**
     * Checks if dragon is actively moving on ground
     */
    public boolean isGroundMoving() {
        return !isInAnyFlightState() && (isWalking() || isRunning());
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