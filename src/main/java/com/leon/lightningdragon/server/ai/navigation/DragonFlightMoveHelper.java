package com.leon.lightningdragon.server.ai.navigation;

import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Dragon flight movement controller - handles AI flight pathfinding
 * Banking is now handled by DragonStateManager in entity tick
 */
public class DragonFlightMoveHelper extends MoveControl {
    private final LightningDragonEntity dragon;
    private float speedFactor = 1.0F;

    // Constants for smooth movement
    private static final float MAX_YAW_CHANGE = 4.0F;
    private static final float MAX_PITCH_CHANGE = 8.0F;
    private static final float SPEED_FACTOR_MIN = 0.3F;
    private static final float SPEED_FACTOR_MAX = 2.5F; // Increased max speed

    public DragonFlightMoveHelper(LightningDragonEntity dragon) {
        super(dragon);
        this.dragon = dragon;
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            return;
        }

        // Handle different flight modes
        if (dragon.isHovering() || dragon.isLanding()) {
            handleHoveringMovement();
        } else {
            handleGlidingMovement();
        }
    }

    /**
     * Gliding movement - this is where the magic happens
     */
    private void handleGlidingMovement() {
        // Collision handling - simple 180 turn
        if (dragon.horizontalCollision) {
            dragon.setYRot(dragon.getYRot() + 180.0F);
            this.speedFactor = SPEED_FACTOR_MIN;
            dragon.getNavigation().stop();
            return;
        }

        // Calculate movement vectors to target
        float distX = (float) (this.wantedX - dragon.getX());
        float distY = (float) (this.wantedY - dragon.getY());
        float distZ = (float) (this.wantedZ - dragon.getZ());

        // Reduce Y influence on horizontal movement
        double horizontalDist = Math.sqrt(distX * distX + distZ * distZ);
        double yFractionReduction = 1.0D - (double) Mth.abs(distY * 0.7F) / horizontalDist;
        distX = (float) ((double) distX * yFractionReduction);
        distZ = (float) ((double) distZ * yFractionReduction);

        horizontalDist = Math.sqrt(distX * distX + distZ * distZ);
        double totalDist = Math.sqrt(distX * distX + distZ * distZ + distY * distY);

        // === YAW CALCULATION ===
        float currentYaw = dragon.getYRot();
        float desiredYaw = (float) Mth.atan2(distZ, distX) * 57.295776F; // Convert to degrees

        // Smooth yaw approach
        float wrappedCurrentYaw = Mth.wrapDegrees(currentYaw + 90.0F);
        float wrappedDesiredYaw = Mth.wrapDegrees(desiredYaw);
        dragon.setYRot(Mth.approachDegrees(wrappedCurrentYaw, wrappedDesiredYaw, MAX_YAW_CHANGE) - 90.0F);

        // Banking is now handled by DragonStateManager.updateBankingFromRotation() in entity tick
        // MoveHelper only handles movement - no banking calculation needed

        // Body rotation follows head
        dragon.yBodyRot = dragon.getYRot();

        // === PITCH CALCULATION ===
        float desiredPitch = (float) (-(Mth.atan2(-distY, horizontalDist) * 57.295776F));
        dragon.setXRot(Mth.approachDegrees(dragon.getXRot(), desiredPitch, MAX_PITCH_CHANGE));

        // === ENHANCED SPEED MODULATION ===
        float yawDifference = Math.abs(Mth.wrapDegrees(dragon.getYRot() - currentYaw));
        
        // Base speed factor adjustments
        float targetSpeedFactor;
        if (yawDifference < 3.0F) {
            // Facing right direction - speed up
            targetSpeedFactor = SPEED_FACTOR_MAX;
        } else {
            // Turning - slow down based on turn severity using yaw difference
            float turnSeverity = Mth.clamp(yawDifference / 15.0f, 0.0f, 1.0f); // Normalize to 0-1
            targetSpeedFactor = DragonMathUtil.lerpSmooth(0.6f, SPEED_FACTOR_MAX, 1.0f - turnSeverity, 
                    DragonMathUtil.EasingFunction.EASE_OUT_SINE);
        }
        
        // Distance-based approach speed scaling - only slow down very close to target
        float approachDistance = 8.0f; // Only slow down within 8 blocks of target
        if (totalDist < approachDistance) {
            float approachFactor = (float) (totalDist / approachDistance);
            // Use smooth easing for natural approach behavior
            approachFactor = DragonMathUtil.easeOutSine(approachFactor);
            targetSpeedFactor *= Mth.clamp(approachFactor + 0.6f, 0.6f, 1.0f); // Don't go below 60% speed
        }
        
        this.speedFactor = Mth.approach(this.speedFactor, targetSpeedFactor, 0.15F); // Faster speed transitions

        // === ENHANCED 3D MOVEMENT APPLICATION (Naga Style) ===
        float rotationYaw = dragon.getYRot() + 90.0F;
        
        // Decompose motion into X, Y, Z components with proper 3D scaling
        double xMotion = (double) (this.speedFactor * Mth.cos(rotationYaw * 0.017453292F)) * Math.abs((double) distX / totalDist);
        double yMotion = (double) (this.speedFactor * Mth.sin(rotationYaw * 0.017453292F)) * Math.abs((double) distZ / totalDist);
        double zMotion = (double) (this.speedFactor * Mth.sin(desiredPitch * 0.017453292F)) * Math.abs((double) distY / totalDist);
        
        // Apply the motion with smooth acceleration (like Naga's 0.1D scaling)
        Vec3 motion = dragon.getDeltaMovement();
        Vec3 newMotion = new Vec3(xMotion, zMotion, yMotion);
        dragon.setDeltaMovement(motion.add(newMotion.subtract(motion).scale(0.1D)));
    }

    /**
     * Hovering movement - simpler, more direct control
     */
    private void handleHoveringMovement() {
        // Look at target if we have one - use smooth looking
        if (dragon.getTarget() != null && dragon.distanceToSqr(dragon.getTarget()) < 1600.0D) {
            DragonMathUtil.smoothLookAt(dragon, dragon.getTarget(), 10.0f, 10.0f);
        }

        if (this.operation == Operation.MOVE_TO) {
            Vec3 targetVec = new Vec3(
                    this.wantedX - dragon.getX(),
                    this.wantedY - dragon.getY(),
                    this.wantedZ - dragon.getZ()
            );
            double distance = targetVec.length();
            targetVec = targetVec.normalize();

            // Simple collision check for hovering
            if (checkCollisions(targetVec, Mth.ceil(distance))) {
                dragon.setDeltaMovement(dragon.getDeltaMovement().add(targetVec.scale(0.1D)));
            } else {
                this.operation = Operation.WAIT;
            }
        }
    }

    /**
     * Collision checking for hovering mode
     */
    private boolean checkCollisions(Vec3 direction, int steps) {
        var boundingBox = dragon.getBoundingBox();
        for (int i = 1; i < steps; ++i) {
            boundingBox = boundingBox.move(direction);
            if (!dragon.level().noCollision(dragon, boundingBox)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasGivenUp() {
        return this.operation == Operation.WAIT;
    }
}