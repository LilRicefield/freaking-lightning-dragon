package com.leon.lightningdragon.server.entity.controller;

import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Handles all flight-related logic for the Lightning Dragon including:
 * - Takeoff and landing mechanics
 * - Flight physics and navigation switching
 * - Banking and pitch control
 * - Gliding and hovering behavior
 */
public class DragonFlightController {
    private final LightningDragonEntity dragon;

    // Flight constants
    private static final double TAKEOFF_UPWARD_FORCE = 0.075D;
    private static final double LANDING_DOWNWARD_FORCE = 0.4D;
    private static final double FALLING_RESISTANCE = 0.6D;
    private static final int TAKEOFF_TIME_THRESHOLD = 30;
    private static final int LANDING_TIME_THRESHOLD = 40;

    public DragonFlightController(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }

    /**
     * Main flight logic handler called every tick
     */
    public void handleFlightLogic() {
        if (dragon.stateManager.isFlying()) {
            handleFlyingTick();
        } else {
            handleGroundedTick();
        }

        if (dragon.stateManager.isLanding()) {
            handleSimpleLanding();
        }
    }

    /**
     * Switches dragon to air navigation mode
     */
    public void switchToAirNavigation() {
        dragon.switchToAirNavigation();
    }

    /**
     * Switches dragon to ground navigation mode
     */
    public void switchToGroundNavigation() {
        dragon.switchToGroundNavigation();
    }

    /**
     * Handles takeoff sequence with upward force and timing
     */
    public void handleTakeoff() {
        if (!dragon.stateManager.isFlying()) {
            dragon.stateManager.transitionToFlying();
            dragon.stateManager.setTakeoff(true);
            switchToAirNavigation();
        }
    }

    /**
     * Checks if it's safe to land at current position
     */
    public boolean canLandSafely() {
        if (!dragon.stateManager.isFlying()) return true;

        BlockPos currentPos = dragon.blockPosition();
        for (int y = currentPos.getY() - 1; y >= currentPos.getY() - 10; y--) {
            BlockPos checkPos = new BlockPos(currentPos.getX(), y, currentPos.getZ());
            if (!dragon.level().getBlockState(checkPos).isAir() &&
                    dragon.level().getBlockState(checkPos.above()).getCollisionShape(dragon.level(), checkPos.above()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forces aggressive landing for combat situations
     */
    public void initiateAggressiveLanding() {
        if (!dragon.stateManager.isFlying()) return;

        dragon.stateManager.initiateLanding();
        dragon.stateManager.setRunning(true);
        dragon.getNavigation().stop();
        dragon.stateManager.setHovering(true);
        dragon.landingTimer = 0;
    }

    /**
     * Handles flight travel behavior based on current flight state
     */
    public void handleFlightTravel(Vec3 motion) {
        if (dragon.stateManager.isTakeoff() || dragon.stateManager.isHovering()) {
            handleHoveringTravel(motion);
        } else {
            handleGlidingTravel(motion);
        }
    }

    private void handleFlyingTick() {
        dragon.timeFlying++;

        // Reduce falling speed while flying
        if (dragon.getDeltaMovement().y < 0 && dragon.isAlive()) {
            dragon.setDeltaMovement(dragon.getDeltaMovement().multiply(1, FALLING_RESISTANCE, 1));
        }

        // Auto-land when sitting or being a passenger
        if (dragon.isOrderedToSit() || dragon.isPassenger()) {
            dragon.stateManager.setTakeoff(false);
            dragon.stateManager.setHovering(false);
            dragon.stateManager.setFlying(false);
            return;
        }

        // Server-side logic
        if (!dragon.level().isClientSide) {
            handleServerFlightLogic();
            handleFlightPitchControl();
        }
    }

    private void handleServerFlightLogic() {
        // Update takeoff state
        dragon.stateManager.setTakeoff(shouldTakeoff() && dragon.stateManager.isFlying());

        // Handle takeoff physics
        if (dragon.stateManager.isTakeoff() && dragon.stateManager.isFlying() && dragon.isAlive()) {
            if (dragon.timeFlying < TAKEOFF_TIME_THRESHOLD) {
                dragon.setDeltaMovement(dragon.getDeltaMovement().add(0, TAKEOFF_UPWARD_FORCE, 0));
            }
            if (dragon.landingFlag) {
                dragon.setDeltaMovement(dragon.getDeltaMovement().add(0, -LANDING_DOWNWARD_FORCE, 0));
            }
        }

        // Landing logic when touching ground
        if (!dragon.stateManager.isTakeoff() && dragon.stateManager.isFlying() && dragon.timeFlying > LANDING_TIME_THRESHOLD && dragon.onGround()) {
            LivingEntity target = dragon.getTarget();
            if (target == null || !target.isAlive()) {
                dragon.stateManager.setFlying(false);
            }
        }
    }

    private void handleGroundedTick() {
        dragon.timeFlying = 0;
    }

    private boolean shouldTakeoff() {
        return dragon.landingFlag || dragon.timeFlying < TAKEOFF_TIME_THRESHOLD || (dragon.getTarget() != null && dragon.getTarget().isAlive());
    }

    private void handleFlightPitchControl() {
        if (!dragon.stateManager.isFlying() || dragon.stateManager.isLanding() || dragon.stateManager.isHovering()) return;

        Vec3 velocity = dragon.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        if (horizontalSpeed > 0.05) {
            float desiredPitch = (float) (Math.atan2(-velocity.y, horizontalSpeed) * 57.295776F);
            desiredPitch = Mth.clamp(desiredPitch, -25.0F, 35.0F);
            dragon.setXRot(Mth.approachDegrees(dragon.getXRot(), desiredPitch, 3.0F));
        }
    }

    private void handleSimpleLanding() {
        if (!dragon.level().isClientSide) {
            dragon.landingTimer++;

            if (dragon.landingTimer > 60 || dragon.onGround()) {
                dragon.stateManager.completeLanding();
                switchToGroundNavigation();
            }
        }
    }

    @SuppressWarnings("unused") // Motion parameter for method signature consistency
    private void handleGlidingTravel(Vec3 motion) {
        Vec3 vec3 = dragon.getDeltaMovement();

        if (vec3.y > -0.5D) {
            dragon.fallDistance = 1.0F;
        }

        Vec3 moveDirection = dragon.getLookAngle().normalize();
        float pitchRad = dragon.getXRot() * ((float) Math.PI / 180F);

        // Enhanced gliding physics that responds to animation state
        vec3 = applyGlidingPhysics(vec3, moveDirection, pitchRad);

        // Dynamic friction based on flight state
        float horizontalFriction = 0.99F;
        float verticalFriction = 0.98F;
        dragon.setDeltaMovement(vec3.multiply(horizontalFriction, verticalFriction, horizontalFriction));
        dragon.move(MoverType.SELF, dragon.getDeltaMovement());
    }

    private Vec3 applyGlidingPhysics(Vec3 currentVel, Vec3 moveDirection, float pitchRad) {
        double horizontalSpeed = Math.sqrt(moveDirection.x * moveDirection.x + moveDirection.z * moveDirection.z);
        if (horizontalSpeed < 0.001) {
            return currentVel;
        }

        double currentHorizontalSpeed = Math.sqrt(currentVel.horizontalDistanceSqr());
        double lookDirectionLength = moveDirection.length();

        float pitchFactor = Mth.cos(pitchRad);
        pitchFactor = (float) ((double) pitchFactor * (double) pitchFactor * Math.min(1.0D, lookDirectionLength / 0.4D));

        double gravity = getGravity();
        Vec3 result = currentVel.add(0.0D, gravity * (-1.0D + (double) pitchFactor * 0.75D), 0.0D);

        // Enhanced lift calculation with animation influence
        if (result.y < 0.0D && horizontalSpeed > 0.0D) {
            double liftFactor = getLiftFactor(result, pitchFactor);
            result = result.add(
                    moveDirection.x * liftFactor / horizontalSpeed,
                    liftFactor,
                    moveDirection.z * liftFactor / horizontalSpeed
            );
        }

        // Dive calculation
        if (pitchRad < 0.0F && horizontalSpeed > 0.0D) {
            double diveFactor = currentHorizontalSpeed * (double) (-Mth.sin(pitchRad)) * 0.04D;
            result = result.add(
                    -moveDirection.x * diveFactor / horizontalSpeed,
                    diveFactor * 3.2D,
                    -moveDirection.z * diveFactor / horizontalSpeed
            );
        }

        // Directional alignment
        if (horizontalSpeed > 0.0D) {
            double alignmentFactor = 0.1D;
            result = result.add(
                    (moveDirection.x / horizontalSpeed * currentHorizontalSpeed - result.x) * alignmentFactor,
                    0.0D,
                    (moveDirection.z / horizontalSpeed * currentHorizontalSpeed - result.z) * alignmentFactor
            );
        }

        return result;
    }

    private double getLiftFactor(Vec3 result, double pitchFactor) {
        double baseLiftFactor = result.y * -0.1D * pitchFactor;

        double liftMultiplier = 1.0;
        if (dragon.getFlappingFraction() > 0.3f) {
            liftMultiplier += dragon.getFlappingFraction() * 0.6;
        }
        if (dragon.getGlidingFraction() > 0.5f) {
            liftMultiplier += dragon.getGlidingFraction() * 0.4;
        }

        return baseLiftFactor * liftMultiplier;
    }

    private double getGravity() {
        double gravity = 0.08D;

        if (dragon.getFlappingFraction() > 0.2f) {
            gravity *= (1.0 - dragon.getFlappingFraction() * 0.5);
        } else if (dragon.getHoveringFraction() > 0.4f) {
            gravity *= (1.0 - dragon.getHoveringFraction() * 0.3);
        }

        if (dragon.getGlidingFraction() > 0.5f) {
            gravity *= (1.0 - dragon.getGlidingFraction() * 0.2);
        }

        return gravity;
    }

    private void handleHoveringTravel(Vec3 motion) {
        BlockPos ground = new BlockPos((int) dragon.getX(), (int) (dragon.getBoundingBox().minY - 1.0D), (int) dragon.getZ());
        float friction = 0.91F;

        if (dragon.onGround()) {
            friction = dragon.level().getBlockState(ground).getFriction(dragon.level(), ground, dragon) * 0.91F;
        }

        float frictionFactor = 0.16277137F / (friction * friction * friction);
        friction = 0.91F;

        if (dragon.onGround()) {
            friction = dragon.level().getBlockState(ground).getFriction(dragon.level(), ground, dragon) * 0.91F;
        }

        dragon.moveRelative(dragon.onGround() ? 0.1F * frictionFactor : 0.02F, motion);
        dragon.move(MoverType.SELF, dragon.getDeltaMovement());
        dragon.setDeltaMovement(dragon.getDeltaMovement().scale(friction));

        BlockPos destination = dragon.getNavigation().getTargetPos();
        if (destination != null) {
            double dx = destination.getX() - dragon.getX();
            double dy = destination.getY() - dragon.getY();
            double dz = destination.getZ() - dragon.getZ();
            double distanceToDest = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distanceToDest < 0.1) {
                dragon.setDeltaMovement(0, 0, 0);
            }
        }
    }
}