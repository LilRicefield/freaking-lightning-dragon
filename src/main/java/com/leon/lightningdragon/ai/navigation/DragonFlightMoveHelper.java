package com.leon.lightningdragon.ai.navigation;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * HYBRID flight controller: Ice & Fire proven physics + DragonMathUtil enhancements
 * Best of both worlds without reinventing the wheel
 */
public class DragonFlightMoveHelper extends MoveControl {
    private final LightningDragonEntity dragon;

    // Ice & Fire proven constants (don't fuck with these)
    private static final float MAX_SPEED = 1.8F;
    private static final float MIN_SPEED = 0.2F;
    private static final float SPEED_CHANGE_RATE = 0.02F;    // Faster than before
    private static final float SLOW_SPEED_CHANGE_RATE = 0.05F; // Faster than before
    private static final float MAX_YAW_CHANGE = 4.0F;
    private static final float MOTION_CAP = 0.2F;
    private static final float BANKING_MAX = 45.0F;

    public DragonFlightMoveHelper(LightningDragonEntity dragon) {
        super(dragon);
        this.dragon = dragon;
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            return;
        }

        // === COLLISION HANDLING (Ice & Fire) ===
        if (dragon.horizontalCollision) {
            dragon.setYRot(dragon.getYRot() + 180.0F);
            this.speedModifier = MIN_SPEED;
            this.operation = Operation.WAIT;
            return;
        }

        // Calculate vectors to target
        float distX = (float) (this.wantedX - dragon.getX());
        float distY = (float) (this.wantedY - dragon.getY());
        float distZ = (float) (this.wantedZ - dragon.getZ());

        // Calculate distances
        double planeDist = Math.sqrt(distX * distX + distZ * distZ);
        double totalDist = Math.sqrt(distX * distX + distZ * distZ + distY * distY);

        // Check if we've reached the target
        if (totalDist < dragon.getBoundingBox().getSize()) {
            this.operation = Operation.WAIT;
            dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.5D));
            return;
        }

        // === YAW CALCULATION & SMOOTHING (Ice & Fire) ===
        float currentYaw = dragon.getYRot();
        float targetYaw = (float) Mth.atan2(distZ, distX) * (180.0F / (float) Math.PI) - 90.0F;

        // Smooth yaw approach
        dragon.setYRot(approachDegrees(currentYaw, targetYaw, MAX_YAW_CHANGE));
        dragon.yBodyRot = dragon.getYRot();

        // === ENHANCED BANKING (DragonMathUtil) ===
        if (!dragon.isLanding()) {
            float yawChange = Mth.wrapDegrees(dragon.getYRot() - currentYaw);
            float newBanking = DragonMathUtil.approachDegreesSmooth(
                    dragon.getBanking(),
                    dragon.getPrevBanking(),
                    yawChange * 2.0f,  // target banking based on turn rate
                    1.5f,              // desired speed
                    0.2f               // acceleration
            );
            newBanking = Mth.clamp(newBanking, -BANKING_MAX, BANKING_MAX);

            dragon.setPrevBanking(dragon.getBanking());
            dragon.setBanking(newBanking);
        }

        // === ENHANCED TARGET TRACKING (DragonMathUtil) ===
        if (dragon.getTarget() != null && !dragon.isLanding()) {
            DragonMathUtil.smoothLookAt(dragon, dragon.getTarget(), MAX_YAW_CHANGE, 2.0f);
        }

        // === SIMPLIFIED SPEED MODULATION (Less jank) ===
        float yawDifference = degreesDifferenceAbs(currentYaw, dragon.getYRot());

        if (yawDifference < 3.0F) {
            // We're facing the right direction - speed up
            this.speedModifier = approach(this.speedModifier, MAX_SPEED, SPEED_CHANGE_RATE);
        } else {
            // We're turning - slow down for better control
            this.speedModifier = approach(this.speedModifier, MIN_SPEED, SLOW_SPEED_CHANGE_RATE);
        }

        // ONLY distance-based reduction for very close targets (prevent overshooting)
        if (totalDist < 8.0) {
            this.speedModifier *= Math.max(0.5, totalDist / 8.0);
        }

        // Landing system speed adjustments (only when actually landing)
        if (dragon.isLanding()) {
            LightningDragonEntity.LandingPhase phase = dragon.getLandingPhase();
            switch (phase) {
                case CIRCLING -> this.speedModifier *= 0.8F;      // Slight reduction
                case APPROACHING -> this.speedModifier *= 0.7F;   // Moderate reduction
                case DESCENDING -> this.speedModifier *= 0.5F;    // Controlled descent
                case TOUCHDOWN -> this.speedModifier *= 0.3F;     // Final approach
            }
        }

        // Hovering gets SLIGHT reduction, not a massacre
        if (dragon.isHovering() && !dragon.isLanding()) {
            this.speedModifier *= 0.7F; // Was 0.4F - too much
        }

        // === PITCH CALCULATION (Ice & Fire) ===
        double yDistMod = 1.0D - (double) Mth.abs(distY * 0.7F) / planeDist;
        distX = (float) ((double) distX * yDistMod);
        distZ = (float) ((double) distZ * yDistMod);
        planeDist = Math.sqrt(distX * distX + distZ * distZ);

        float targetPitch = (float) (-(Mth.atan2(-distY, planeDist) * (180.0 / Math.PI)));
        dragon.setXRot(targetPitch);

        // === ICE & FIRE MOVEMENT APPLICATION (PROVEN TO WORK) ===
        // Calculate movement components
        float yawRad = (dragon.getYRot() + 90.0F) * ((float) Math.PI / 180.0F);
        double xComponent = this.speedModifier * Mth.cos(yawRad) * Math.abs(distX / totalDist);
        double zComponent = this.speedModifier * Mth.sin(yawRad) * Math.abs(distZ / totalDist);
        double yComponent = this.speedModifier * Mth.sin(targetPitch * ((float) Math.PI / 180.0F)) * Math.abs(distY / totalDist);

        // Apply movement ADDITIVELY with caps (Ice & Fire style)
        Vec3 currentMovement = dragon.getDeltaMovement();
        Vec3 addedMovement = new Vec3(
                Math.min(xComponent * 0.25D, MOTION_CAP), // Slightly more aggressive than 0.2D
                Math.min(yComponent * 0.25D, MOTION_CAP),
                Math.min(zComponent * 0.25D, MOTION_CAP)
        );

        // ENHANCED: Clamp final movement to prevent weirdness
        Vec3 finalMovement = DragonMathUtil.clampVectorLength(
                currentMovement.add(addedMovement),
                MAX_SPEED
        );

        dragon.setDeltaMovement(finalMovement);
    }

    // === UTILITY METHODS (Ice & Fire) ===
    private static float approach(double current, float target, double step) {
        step = Math.abs(step);
        return current < target ?
                (float) Mth.clamp(current + step, current, target) :
                (float) Mth.clamp(current - step, target, current);
    }

    private static float approachDegrees(float current, float target, float step) {
        float difference = Mth.wrapDegrees(target - current);
        return approach(current, current + difference, step);
    }

    private static float degreesDifferenceAbs(float f1, float f2) {
        return Math.abs(Mth.wrapDegrees(f2 - f1));
    }

    public Operation getCurrentOperation() {
        return this.operation;
    }

    public boolean hasGivenUp() {
        return this.operation == Operation.WAIT;
    }
}