package com.leon.lightningdragon.ai.goals;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Features smart flight triggering, performance optimizations, and realistic distances
 */
public class DragonFollowOwnerGoal extends Goal {
    private final LightningDragonEntity dragon;

    // Distance constants - tuned for better behavior
    private static final double START_FOLLOW_DIST = 15.0;
    private static final double STOP_FOLLOW_DIST = 8.0;
    private static final double TELEPORT_DIST = 2000.0; // Way more realistic than 64 blocks
    private static final double RUN_DIST = 25.0;
    private static final double FLIGHT_TRIGGER_DIST = 30.0; // Ice and Fire style
    private static final double FLIGHT_HEIGHT_DIFF = 8.0; // Fly if owner is way above

    // Performance optimization - don't re-path constantly
    private BlockPos previousOwnerPos;

    public DragonFollowOwnerGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Basic requirements
        if (!dragon.isTame() || dragon.isOrderedToSit()) {
            return false;
        }

        LivingEntity owner = dragon.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        // Must be in same dimension
        if (owner.level() != dragon.level()) {
            return false;
        }

        // Only follow if owner is far enough away
        double dist = dragon.distanceToSqr(owner);
        return dist > START_FOLLOW_DIST * START_FOLLOW_DIST;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = dragon.getOwner();
        if (owner == null || !owner.isAlive() || dragon.isOrderedToSit()) {
            return false;
        }

        if (owner.level() != dragon.level()) {
            return false;
        }

        // Keep following until we're close enough
        double dist = dragon.distanceToSqr(owner);
        return dist > STOP_FOLLOW_DIST * STOP_FOLLOW_DIST;
    }

    @Override
    public void start() {
        // Reset tracking
        previousOwnerPos = null;

        // Don't immediately fly - let tick() decide based on conditions
        if (!dragon.level().isClientSide) {
            System.out.println("Dragon started following owner");
        }
    }

    @Override
    public void tick() {
        LivingEntity owner = dragon.getOwner();
        if (owner == null) return;

        double distance = dragon.distanceTo(owner);

        // Emergency teleport if owner gets stupidly far away
        if (distance > TELEPORT_DIST) {
            dragon.teleportTo(owner.getX(), owner.getY() + 3, owner.getZ());
            dragon.setFlying(true);
            dragon.setTakeoff(true);

            if (!dragon.level().isClientSide) {
                System.out.println("Dragon teleported to owner (distance: " + String.format("%.1f", distance) + ")");
            }
            return;
        }

        // Always look at owner while following
        dragon.getLookControl().setLookAt(owner, 10.0f, 10.0f);

        // Smart flight decision making - Ice and Fire style
        boolean shouldFly = shouldTriggerFlight(owner, distance);

        if (shouldFly && !dragon.isFlying()) {
            // Take off to follow owner
            dragon.setFlying(true);
            dragon.setTakeoff(true);

            if (!dragon.level().isClientSide) {
                System.out.println("Dragon taking off to follow owner");
            }
        } else if (!shouldFly && dragon.isFlying() && dragon.onGround()) {
            // Land when we don't need to fly anymore
            dragon.setLanding(true);

            if (!dragon.level().isClientSide) {
                System.out.println("Dragon landing near owner");
            }
        }

        // Movement logic
        if (dragon.isFlying()) {
            handleFlightFollowing(owner);
        } else {
            handleGroundFollowing(owner, distance);
        }
    }

    /**
     * Handle following while flying
     */
    private void handleFlightFollowing(LivingEntity owner) {
        // Fly slightly above and behind owner
        double targetY = owner.getY() + owner.getBbHeight() + 3.0;

        // Add some offset so dragon doesn't crowd the owner
        Vec3 ownerLook = owner.getLookAngle();
        double offsetX = -ownerLook.x * 4.0; // Behind owner
        double offsetZ = -ownerLook.z * 4.0;

        dragon.getMoveControl().setWantedPosition(
                owner.getX() + offsetX,
                targetY,
                owner.getZ() + offsetZ,
                1.2 // Flight speed
        );
    }

    /**
     * Handle following on ground - with Ice and Fire's optimization
     */
    private void handleGroundFollowing(LivingEntity owner, double distance) {
        // Only move if we're far enough from owner
        if (distance > dragon.getBoundingBox().getSize()) {
            // Performance optimization: only re-path if owner moved significantly
            if (previousOwnerPos == null ||
                    previousOwnerPos.distSqr(owner.blockPosition()) > 9) {

                // Decide movement speed based on distance
                boolean shouldRun = distance > RUN_DIST;
                dragon.setRunning(shouldRun);

                double speed = shouldRun ? 1.8 : 1.0;
                dragon.getNavigation().moveTo(owner, speed);
                previousOwnerPos = owner.blockPosition();

                if (!dragon.level().isClientSide) {
                    System.out.println("Dragon ground follow - Distance: " + String.format("%.1f", distance) +
                            ", Running: " + shouldRun);
                }
            }
        } else {
            // Close enough - stop moving
            dragon.getNavigation().stop();
            dragon.setRunning(false);
        }
    }

    /**
     * Determine if dragon should take flight to follow owner
     * Uses Ice and Fire's logic for smarter flight decisions
     */
    private boolean shouldTriggerFlight(LivingEntity owner, double distance) {
        // Don't fly if already flying or can't fly
        if (dragon.isFlying() || dragon.isHovering() || !canTriggerFlight()) {
            return false;
        }

        // Fly if owner is far away OR significantly higher up
        boolean farAway = distance > FLIGHT_TRIGGER_DIST;
        boolean ownerAbove = owner.getY() - dragon.getY() > FLIGHT_HEIGHT_DIFF;

        return farAway || ownerAbove;
    }

    /**
     * Check if dragon is allowed to take flight
     * Uses existing dragon flight requirements
     */
    private boolean canTriggerFlight() {
        return !dragon.isOrderedToSit() &&
                !dragon.isBaby() &&
                (dragon.onGround() || dragon.isInWater()) &&
                dragon.getPassengers().isEmpty() &&
                dragon.getControllingPassenger() == null &&
                !dragon.isPassenger() &&
                dragon.getActiveAbility() == null; // Don't interrupt abilities
    }

    @Override
    public void stop() {
        dragon.setRunning(false);
        dragon.getNavigation().stop();
        previousOwnerPos = null;

        if (!dragon.level().isClientSide) {
            System.out.println("Dragon stopped following owner");
        }
    }
}