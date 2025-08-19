package com.leon.lightningdragon.ai.goals;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Air combat goal for Lightning Dragon - Ice & Fire inspired but adapted for lightning abilities
 */
public class DragonAirCombatGoal extends Goal {

    private final LightningDragonEntity dragon;

    // Attack patterns
    public enum AirAttackMode {
        HOVER_BLAST,    // Hover above target and rain lightning
        LIGHTNING_DIVE, // Dive at target with wing lightning
        STRAFE_RUN     // Fly past target while breathing lightning
    }

    private AirAttackMode currentAttackMode;
    private Vec3 attackTarget;
    private int attackCooldown = 0;
    private int attackTimer = 0;
    private int modeSwitchCooldown = 0;

    // Attack mode durations
    private static final int HOVER_DURATION = 100;  // 5 seconds
    private static final int DIVE_DURATION = 60;    // 3 seconds
    private static final int STRAFE_DURATION = 80;  // 4 seconds

    public DragonAirCombatGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }


    @Override
    public boolean canUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                dragon.isFlying() &&
                !dragon.isLanding() &&
                dragon.distanceToSqr(target) < 1600; // 40 block range
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                dragon.isFlying() &&
                !dragon.isLanding() &&
                dragon.distanceToSqr(target) < 2500; // 50 block range
    }

    @Override
    public void start() {
        selectAttackMode();
        attackTimer = 0;
        dragon.setHovering(false); // Start with movement
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

        // Execute current attack mode
        switch (currentAttackMode) {
            case HOVER_BLAST -> executeHoverBlast(target);
            case LIGHTNING_DIVE -> executeLightningDive(target);
            case STRAFE_RUN -> executeStrafeRun(target);
        }

        // Switch attack modes when appropriate
        if (shouldSwitchAttackMode()) {
            selectAttackMode();
            attackTimer = 0;
            modeSwitchCooldown = 40; // 2 second cooldown between switches
        }
    }

    private void executeHoverBlast(LivingEntity target) {
        double distance = dragon.distanceTo(target);

        // Position above target
        if (distance < 15) {
            // Too close - back off
            Vec3 awayFromTarget = dragon.position().subtract(target.position()).normalize();
            attackTarget = target.position().add(awayFromTarget.scale(20)).add(0, 15, 0);
        } else if (distance > 30) {
            // Too far - get closer
            attackTarget = target.position().add(0, 15, 0);
        } else {
            // Good distance - hover and attack
            dragon.setHovering(true);
            attackTarget = dragon.position(); // Stay in place

            // SMART attack selection based on distance
            if (attackCooldown <= 0) {
                if (dragon.tryUseRangedAbility()) {
                    attackCooldown = 40;
                }
            }
        }

        dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.2);
    }


    private void executeLightningDive(LivingEntity target) {
        dragon.setHovering(false);

        // Dive at target
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
        double distance = dragon.distanceTo(target);

        if (distance < 8) {
            // Close enough for wing lightning
            if (dragon.canUseAbility() && attackCooldown <= 0) {
                dragon.sendAbilityMessage(LightningDragonEntity.WING_LIGHTNING_ABILITY);
                attackCooldown = 60;
            }

            // Pull up after attack
            attackTarget = target.position().add(0, 20, 0);
        } else {
            // Still diving
            attackTarget = targetPos;
        }

        dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.5);
    }

    private void executeStrafeRun(LivingEntity target) {
        dragon.setHovering(false);

        double distance = dragon.distanceTo(target);

        if (attackTimer < 40) {
            // Position for strafe run - to the side of target
            Vec3 sideOffset = dragon.getLookAngle().cross(new Vec3(0, 1, 0)).normalize().scale(25);
            attackTarget = target.position().add(sideOffset).add(0, 10, 0);
        } else {
            // Execute strafe run - fly past target
            Vec3 throughTarget = target.position().subtract(dragon.position()).normalize();
            attackTarget = target.position().add(throughTarget.scale(30)).add(0, 5, 0);

            // Fire lightning breath while strafing
            if (distance < 25 && dragon.canUseAbility() && attackCooldown <= 0) {
                dragon.sendAbilityMessage(LightningDragonEntity.LIGHTNING_BREATH_ABILITY);
                attackCooldown = 40;
            }
        }

        dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.2);
    }

    private boolean shouldSwitchAttackMode() {
        if (modeSwitchCooldown > 0) return false;

        return switch (currentAttackMode) {
            case HOVER_BLAST -> attackTimer > HOVER_DURATION;
            case LIGHTNING_DIVE -> attackTimer > DIVE_DURATION;
            case STRAFE_RUN -> attackTimer > STRAFE_DURATION;
        };
    }

    private void selectAttackMode() {
        LivingEntity target = dragon.getTarget();
        if (target == null) return;

        double distance = dragon.distanceTo(target);
        double heightDiff = dragon.getY() - target.getY();

        // Enhanced attack mode selection
        if (distance > 25) {
            // Long range - definitely hover and use beam attacks
            currentAttackMode = AirAttackMode.HOVER_BLAST;
        } else if (distance < 12 && heightDiff > 8) {
            // Very close and above - dive attack
            currentAttackMode = AirAttackMode.LIGHTNING_DIVE;
        } else if (distance >= 12 && distance <= 25) {
            // Medium range - mix of hover and strafe
            if (dragon.getRandom().nextFloat() < 0.6f) {
                currentAttackMode = AirAttackMode.HOVER_BLAST; // Prefer hovering for beam attacks
            } else {
                currentAttackMode = AirAttackMode.STRAFE_RUN;
            }
        } else {
            // Default fallback
            currentAttackMode = AirAttackMode.STRAFE_RUN;
        }

        // Reduced randomness - only 15% chance to override smart selection
        if (dragon.getRandom().nextFloat() < 0.15f) {
            AirAttackMode[] modes = AirAttackMode.values();
            currentAttackMode = modes[dragon.getRandom().nextInt(modes.length)];
        }
    }

    @Override
    public void stop() {
        dragon.setHovering(false);
        dragon.getNavigation().stop();
        attackTarget = null;
        currentAttackMode = null;
        attackTimer = 0;
        attackCooldown = 0;
    }

    // Public getter for debugging/other systems
    public AirAttackMode getCurrentAttackMode() {
        return currentAttackMode;
    }
}