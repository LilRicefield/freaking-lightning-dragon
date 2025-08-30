package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * CLEANED UP flight system that works with the new landing logic
 * Fixed the stupid infinite fly-land-fly loop
 */
public class DragonFlightGoal extends Goal {

    private final LightningDragonEntity dragon;
    private Vec3 targetPosition;
    private int stuckCounter = 0;
    private int timeSinceTargetChange = 0;

    // NEW: Landing cooldown to prevent immediate takeoff after landing
    private static final int LANDING_COOLDOWN_TICKS = 100; // 5 seconds minimum on ground
    private long lastLandingTime = 0;

    public DragonFlightGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Don't interfere with landing sequence
        if (dragon.stateManager.isLanding()) {
            return false;
        }

        // Don't interfere with important behaviors
        if (dragon.isVehicle() || dragon.isPassenger() || dragon.isOrderedToSit()) {
            return false;
        }

        // If tamed and close to owner, chill
        if (dragon.isTame()) {
            var owner = dragon.getOwner();
            if (owner != null && dragon.distanceToSqr(owner) < 15.0 * 15.0) {
                return false;
            }
        }

        // NEW: Check landing cooldown - don't take off immediately after landing
        long currentTime = dragon.level().getGameTime();
        if (!dragon.stateManager.isFlying() && (currentTime - lastLandingTime) < LANDING_COOLDOWN_TICKS) {
            return false;
        }

        // Must fly if over danger
        boolean isFlying;
        if (isOverDanger()) {
            isFlying = true;
        } else {
            // Weather-based flight decisions
            boolean isStormy = dragon.level().isRaining() || dragon.level().isThundering();

            if (dragon.stateManager.isFlying()) {
                isFlying = shouldKeepFlying(isStormy);
            } else {
                isFlying = shouldTakeOff(isStormy);
            }
        }

        if (isFlying) {
            this.targetPosition = findFlightTarget();
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // Let landing system take over
        if (dragon.stateManager.isLanding()) {
            return false;
        }

        // Stop if ordered to sit or something important comes up
        if (dragon.isOrderedToSit() || dragon.isVehicle()) {
            return false;
        }

        // Stop if combat starts
        if (dragon.getTarget() != null && dragon.getTarget().isAlive()) {
            return false;
        }

        // NEW: Check if dragon wants to land naturally
        if (dragon.stateManager.isFlying() && !shouldKeepFlying(dragon.level().isRaining() || dragon.level().isThundering())) {
            // Dragon wants to land - trigger landing sequence
            dragon.stateManager.initiateLanding();
            return false;
        }

        // Continue if we're flying and have a target
        return dragon.stateManager.isFlying() && targetPosition != null && dragon.distanceToSqr(targetPosition) > 9.0;
    }

    @Override
    public void start() {
        dragon.stateManager.transitionToFlying();
        if (targetPosition != null) {
            dragon.getMoveControl().setWantedPosition(targetPosition.x, targetPosition.y, targetPosition.z, 1.0);
        }
    }

    @Override
    public void tick() {
        timeSinceTargetChange++;

        // If dragon wants to land, let it handle that
        if (dragon.stateManager.isLanding()) {
            return;
        }

        // Check if we need a new target
        boolean needNewTarget = false;

        if (targetPosition == null) {
            needNewTarget = true;
        } else {
            double distanceToTarget = dragon.distanceToSqr(targetPosition);

            // Reached target - much larger completion distance
            if (distanceToTarget < 64.0) {
                needNewTarget = true;
            }

            // Check if move controller gave up (collision handling)
            if (dragon.isFlightControllerStuck() && distanceToTarget > 25.0) {
                needNewTarget = true;
                stuckCounter = 0;
            }

            // Better stuck detection
            if (dragon.horizontalCollision && timeSinceTargetChange % 5 == 0) {
                stuckCounter++;
                if (stuckCounter > 2) {
                    needNewTarget = true;
                    stuckCounter = 0;
                }
            } else if (!dragon.horizontalCollision) {
                stuckCounter = Math.max(0, stuckCounter - 1);
            }

            // Path validation every second
            if (timeSinceTargetChange % 20 == 0) {
                if (!isValidFlightTarget(targetPosition)) {
                    needNewTarget = true;
                }
            }

            // Been going to same target for too long
            if (timeSinceTargetChange > 300) {
                needNewTarget = true;
            }
        }

        if (needNewTarget) {
            targetPosition = findFlightTarget();
            timeSinceTargetChange = 0;
            dragon.getMoveControl().setWantedPosition(targetPosition.x, targetPosition.y, targetPosition.z, 1.0);
        }
    }

    @Override
    public void stop() {
        targetPosition = null;
        stuckCounter = 0;
        timeSinceTargetChange = 0;
        dragon.getNavigation().stop();

        // NEW: Record landing time for cooldown
        if (!dragon.stateManager.isFlying()) {
            lastLandingTime = dragon.level().getGameTime();
        }
    }

    // ===== FLIGHT TARGET FINDING =====

    private Vec3 findFlightTarget() {
        Vec3 dragonPos = dragon.position();

        // Try multiple attempts with progressively more desperate searching
        for (int attempts = 0; attempts < 16; attempts++) {
            Vec3 candidate = generateFlightCandidate(dragonPos, attempts);

            if (isValidFlightTarget(candidate)) {
                return candidate;
            }
        }

        // Fallback: safe position above current location
        return new Vec3(dragonPos.x, findSafeFlightHeight(dragonPos.x, dragonPos.z), dragonPos.z);
    }

    private Vec3 generateFlightCandidate(Vec3 dragonPos, int attempt) {
        boolean isStuck = dragon.horizontalCollision || stuckCounter > 0 || dragon.isFlightControllerStuck();

        float maxRot = isStuck ? 360 : 180;
        float range = isStuck ? 30.0f + dragon.getRandom().nextFloat() * 40.0f :
                50.0f + dragon.getRandom().nextFloat() * 80.0f; // Much larger range for exploration

        float yRotOffset;
        if (isStuck && attempt < 8) {
            yRotOffset = (float) Math.toRadians(180 + dragon.getRandom().nextFloat() * 120 - 60);
        } else {
            yRotOffset = (float) Math.toRadians(dragon.getRandom().nextFloat() * maxRot - (maxRot / 2));
        }

        float xRotOffset = (float) Math.toRadians((dragon.getRandom().nextFloat() - 0.5f) * 20);

        Vec3 lookVec = dragon.getLookAngle();
        Vec3 targetVec = lookVec.scale(range).yRot(yRotOffset).xRot(xRotOffset);
        Vec3 candidate = dragonPos.add(targetVec);

        // Adjust for storm - fly higher and more dramatically
        boolean isStormy = dragon.level().isThundering();
        double targetY = findSafeFlightHeight(candidate.x, candidate.z);

        if (isStormy) {
            targetY += 10 + dragon.getRandom().nextDouble() * 20;
        }

        candidate = new Vec3(candidate.x, targetY, candidate.z);

        if (!dragon.level().isLoaded(BlockPos.containing(candidate))) {
            return null;
        }

        return candidate;
    }

    private double findSafeFlightHeight(double x, double z) {
        BlockPos groundPos = findGroundLevel(new BlockPos((int)x, (int)dragon.getY(), (int)z));

        double baseHeight = groundPos.getY() + 15.0;
        double randomExtra = dragon.getRandom().nextDouble() * 20.0;
        double finalHeight = baseHeight + randomExtra;

        double maxHeight = groundPos.getY() + 80.0;
        return Math.min(finalHeight, maxHeight);
    }

    private boolean isValidFlightTarget(Vec3 target) {
        if (target == null) return false;

        BlockHitResult result = dragon.level().clip(new ClipContext(
                dragon.getEyePosition(),
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                dragon
        ));

        if (result.getType() == HitResult.Type.MISS) {
            return true;
        }

        double distanceToHit = result.getLocation().distanceTo(dragon.position());
        double distanceToTarget = target.distanceTo(dragon.position());

        return distanceToHit > distanceToTarget * 0.95;
    }

    // ===== DECISION MAKING (FIXED) =====

    private boolean shouldTakeOff(boolean isStormy) {
        if (isOverDanger()) {
            return true;
        }

        if (isStormy) {
            return dragon.getRandom().nextInt(60) == 0; // More likely during storms
        } else {
            return dragon.getRandom().nextInt(400) == 0; // Less likely in clear weather
        }
    }

    private boolean shouldKeepFlying(boolean isStormy) {
        if (isOverDanger()) {
            return true;
        }

        // FIXED: Much longer flight times for exploration
        if (isStormy) {
            // During storms, fly for much longer periods
            return dragon.getRandom().nextInt(1200) != 0; // ~1 minute average flight time
        } else {
            // Clear weather - still fly for reasonable periods
            return dragon.getRandom().nextInt(800) != 0; // ~40 seconds average flight time
        }
    }

    // ===== UTILITY METHODS =====

    private boolean isOverDanger() {
        BlockPos dragonPos = dragon.blockPosition();
        boolean foundSolid = false;

        for (int i = 1; i <= 25; i++) {
            BlockPos checkPos = dragonPos.below(i);

            if (dragon.level().getBlockState(checkPos).isSolid()) {
                foundSolid = true;
                break;
            }

            // Only consider fluids dangerous if we're close to them
            if (i <= 10 && !dragon.level().getFluidState(checkPos).isEmpty()) {
            }
        }

        // Only dangerous if we're REALLY high up (like over void) or near fluids
        return !foundSolid && dragonPos.getY() < dragon.level().getMinBuildHeight() + 20;
    }

    private BlockPos findGroundLevel(BlockPos startPos) {
        BlockPos.MutableBlockPos pos = startPos.mutable();

        for (int i = 0; i < 100; i++) {
            if (dragon.level().getBlockState(pos).isSolid()) {
                return pos.immutable();
            }
            pos.move(0, -1, 0);

            if (pos.getY() <= dragon.level().getMinBuildHeight()) {
                break;
            }
        }

        return new BlockPos(startPos.getX(), (int)dragon.getY(), startPos.getZ());
    }
}