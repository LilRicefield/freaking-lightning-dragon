package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Makes the dragon take flight when it has a target to fight
 * This runs BEFORE other combat goals to ensure the dragon is airborne
 */
public class DragonCombatFlightGoal extends Goal {
    private final LightningDragonEntity dragon;
    private LivingEntity target;
    private boolean hasTriggeredFlight = false;

    public DragonCombatFlightGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Only trigger if we have a target and we're not already flying
        LivingEntity target = dragon.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        // Don't interfere if we're already flying or landing
        if (dragon.isFlying() || dragon.isLanding() || dragon.isTakeoff()) {
            return false;
        }

        // Don't interfere with sitting
        if (dragon.isOrderedToSit()) {
            return false;
        }

        // Check if target is worth flying for
        double distance = dragon.distanceTo(target);

        // Take off if target is far away or above us
        boolean targetFarAway = distance > 20.0;
        boolean targetAbove = target.getY() - dragon.getY() > 5.0;
        boolean cantReachOnGround = distance > 10.0 && !dragon.getSensing().hasLineOfSight(target);

        return targetFarAway || targetAbove || cantReachOnGround;
    }

    @Override
    public boolean canContinueToUse() {
        // Continue until we're fully flying or the target is lost
        return dragon.getTarget() != null &&
                dragon.getTarget().isAlive() &&
                !dragon.isFlying() &&
                !dragon.isOrderedToSit() &&
                !dragon.isLanding();
    }

    @Override
    public void start() {
        this.target = dragon.getTarget();
        this.hasTriggeredFlight = false;
        
        // Trigger takeoff immediately - ONCE
        dragon.setFlying(true);
        dragon.setTakeoff(false);
        dragon.setLanding(false);
        dragon.setHovering(false);
        this.hasTriggeredFlight = true;
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            return;
        }

        // Just look at target while taking off - don't spam state transitions
        dragon.getLookControl().setLookAt(target, 30f, 30f);
    }

    @Override
    public void stop() {
        this.target = null;
        this.hasTriggeredFlight = false;
    }
}
