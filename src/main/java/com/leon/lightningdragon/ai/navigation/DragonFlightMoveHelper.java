package com.leon.lightningdragon.ai.navigation;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Key: Banking calculation happens HERE, not in entity tick
 */
public class DragonFlightMoveHelper extends MoveControl {
    private final LightningDragonEntity dragon;
    private float speedFactor = 1.0F;

    // Constants for smooth movement
    private static final float MAX_YAW_CHANGE = 4.0F;
    private static final float MAX_PITCH_CHANGE = 8.0F;
    private static final float SPEED_FACTOR_MIN = 0.1F;
    private static final float SPEED_FACTOR_MAX = 1.8F;

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

        // === BANKING CALCULATION (THE KEY SAUCE) ===
        float yawChange = dragon.getYRot() - currentYaw;
        float newBanking = DragonMathUtil.approachDegreesSmooth(
                dragon.getBanking(),
                dragon.getPrevBanking(),
                yawChange * 2.0f,  // Banking follows yaw change
                0.5f,              // Smooth approach speed
                0.1f               // Acceleration
        );
        dragon.setPrevBanking(dragon.getBanking());
        dragon.setBanking(Mth.clamp(newBanking, -45.0f, 45.0f));

        // Body rotation follows head
        dragon.yBodyRot = dragon.getYRot();

        // === PITCH CALCULATION ===
        float desiredPitch = (float) (-(Mth.atan2(-distY, horizontalDist) * 57.295776F));
        dragon.setXRot(Mth.approachDegrees(dragon.getXRot(), desiredPitch, MAX_PITCH_CHANGE));

        // === SPEED MODULATION ===
        float yawDifference = Math.abs(Mth.wrapDegrees(dragon.getYRot() - currentYaw));
        if (yawDifference < 3.0F) {
            // Facing right direction - speed up
            this.speedFactor = Mth.approach(this.speedFactor, SPEED_FACTOR_MAX, 0.05F);
        } else {
            // Turning - slow down for control
            this.speedFactor = Mth.approach(this.speedFactor, 0.6F, 0.1F);
        }



        // === MOVEMENT APPLICATION ===
        float yawRad = (dragon.getYRot() + 90.0F) * 0.017453292F; // Convert to radians
        double xMotion = (double) (this.speedFactor * Mth.cos(yawRad)) * Math.abs(distX / totalDist);
        double zMotion = (double) (this.speedFactor * Mth.sin(yawRad)) * Math.abs(distZ / totalDist);
        double yMotion = (double) (this.speedFactor * Mth.sin(desiredPitch * 0.017453292F)) * Math.abs(distY / totalDist);

        // Additive movement (smoother than direct setting)
        Vec3 currentMotion = dragon.getDeltaMovement();
        Vec3 addedMotion = new Vec3(xMotion, yMotion, zMotion);
        dragon.setDeltaMovement(currentMotion.add(addedMotion.subtract(currentMotion).scale(0.1D)));
    }

    /**
     * Hovering movement - simpler, more direct control
     */
    private void handleHoveringMovement() {
        // Look at target if we have one
        if (dragon.getTarget() != null && dragon.distanceToSqr(dragon.getTarget()) < 1600.0D) {
            dragon.getLookControl().setLookAt(dragon.getTarget(), 100F, 100F);
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