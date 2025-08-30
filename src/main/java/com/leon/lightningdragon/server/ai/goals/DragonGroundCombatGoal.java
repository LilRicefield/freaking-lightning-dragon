package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.common.registry.ModAbilities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class DragonGroundCombatGoal extends Goal {
    private final LightningDragonEntity dragon;

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

        // Close distance to target
        dragon.getNavigation().moveTo(target, 1.2);

        // Look at target
        dragon.getLookControl().setLookAt(target, 30f, 30f);

        // Compute an effective melee reach based on sizes (big dragon needs bigger reach)
        double reach = Math.max(3.5,
                Math.min(7.5, dragon.getBbWidth() * 0.8 + target.getBbWidth() + 1.5));

        double dist = dragon.distanceTo(target);

        // Slow down and stop pathing when within reach to avoid overshooting
        if (dist <= reach + 0.5) {
            dragon.getNavigation().stop();
        }

        // Attack when within reach
        if (dist <= reach) {
            // Try to use bite ability through combat manager
            dragon.combatManager.tryUseAbility(ModAbilities.BITE);
        }
    }

    @Override
    public void stop() {
        dragon.stateManager.setRunning(false);
        dragon.getNavigation().stop();
        dragon.combatManager.forceEndActiveAbility();
    }
}
