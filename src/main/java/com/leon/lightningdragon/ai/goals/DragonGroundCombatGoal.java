package com.leon.lightningdragon.ai.goals;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class DragonGroundCombatGoal extends Goal {
    private final LightningDragonEntity dragon;
    private int attackCooldown = 0;

    public DragonGroundCombatGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                !dragon.stateManager.isFlying() &&
                !dragon.stateManager.isHovering() &&
                dragon.distanceToSqr(target) < 1600; // 40 block range
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                !dragon.stateManager.isFlying() &&
                !dragon.stateManager.isHovering() &&
                dragon.distanceToSqr(target) < 2500; // 50 block range
    }

    @Override
    public void tick() {
        LivingEntity target = dragon.getTarget();
        if (target == null) return;


        dragon.stateManager.setRunning(true);
        dragon.getNavigation().moveTo(target, 1.0);

        // Look at target
        dragon.getLookControl().setLookAt(target, 30f, 30f);

        // Attack when close
        double distance = dragon.distanceTo(target);
        if (distance <= 8.0 && dragon.canUseAbility() && attackCooldown <= 0) {
            // TODO: Use new Dragon ability system for ground combat
            if (distance <= 4.0) {
                // TODO: Electric bite ability
            } else {
                // TODO: Thunder stomp ability
            }
            attackCooldown = 0; // Let enhanced abilities handle their own cooldowns
        }


        if (attackCooldown > 0) {
            attackCooldown--;
        }
    }

    @Override
    public void stop() {
        dragon.stateManager.setRunning(false);
        dragon.getNavigation().stop();
        attackCooldown = 0;
        dragon.forceEndActiveAbility();
    }
}