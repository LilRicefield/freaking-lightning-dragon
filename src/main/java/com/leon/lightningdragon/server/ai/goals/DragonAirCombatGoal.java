package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Air combat goal
 */
public class DragonAirCombatGoal extends Goal {

    private final LightningDragonEntity dragon;

    // Attack patterns
    public enum AirAttackMode {
//TODO: Make something.
    }

    private AirAttackMode currentAttackMode;
    private Vec3 attackTarget;
    private int attackCooldown = 0;
    private int attackTimer = 0;
    private int modeSwitchCooldown = 0;


    public DragonAirCombatGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }


    @Override
    public boolean canUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                (dragon.stateManager.isFlying() || dragon.stateManager.isTakeoff()) &&
                !dragon.stateManager.isLanding() &&
                dragon.distanceToSqr(target) < 1600; // 40 block range
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                (dragon.stateManager.isFlying() || dragon.stateManager.isTakeoff()) &&
                !dragon.stateManager.isLanding() &&
                dragon.distanceToSqr(target) < 2500; // 50 block range
    }

    @Override
    public void start() {
        attackTimer = 0;
        dragon.stateManager.setHovering(false); // Start with movement
    }

    @Override
    public void tick() {
        LivingEntity target = dragon.getTarget();
        if (target == null) return;

        // Always look at target
        dragon.getLookControl().setLookAt(target, 30f, 30f);

        // Handle attack mode timing
        attackTimer++;
        if (attackCooldown > 0) attackCooldown--;
        if (modeSwitchCooldown > 0) modeSwitchCooldown--;

        // If still taking off, use holding pattern until fully airborne
        if (dragon.stateManager.isTakeoff() && !dragon.stateManager.isFlying()) {
            executeHoldingPattern(target);
            return;
        }

        // Just transitioned from takeoff to full flight - reset hover state
        if (dragon.stateManager.isFlying() && currentAttackMode == null) {
            attackTimer = 0;
            dragon.stateManager.setHovering(false); // Clean transition from holding pattern
        }
    }

    private void executeStrafeRun(LivingEntity target) {
        dragon.stateManager.setHovering(false);

        double distance = dragon.distanceTo(target);

        if (attackTimer < 40) {
            // Position for strafe run - to the side of target
            Vec3 sideOffset = dragon.getLookAngle().cross(new Vec3(0, 1, 0)).normalize().scale(25);
            attackTarget = target.position().add(sideOffset).add(0, 10, 0);
        } else {
            // Execute strafe run - fly past target
            Vec3 throughTarget = target.position().subtract(dragon.position()).normalize();
            attackTarget = target.position().add(throughTarget.scale(30)).add(0, 5, 0);

        }

        dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.2);
    }


    @Override
    public void stop() {
        dragon.stateManager.setHovering(false);
        dragon.getNavigation().stop();
        attackTarget = null;
        currentAttackMode = null;
        attackTimer = 0;
        attackCooldown = 0;
        dragon.forceEndActiveAbility();
    }

    private void executeHoldingPattern(LivingEntity target) {
        // Simple holding pattern during takeoff - just hover near target
        Vec3 holdPosition = target.position().add(0, 12, 0); // Above target
        dragon.getMoveControl().setWantedPosition(holdPosition.x, holdPosition.y, holdPosition.z, 0.8);
        dragon.stateManager.setHovering(true);

        // Don't attack during takeoff - just position
    }

    // Public getter for debugging/other systems
    public AirAttackMode getCurrentAttackMode() {
        return currentAttackMode;
    }
}