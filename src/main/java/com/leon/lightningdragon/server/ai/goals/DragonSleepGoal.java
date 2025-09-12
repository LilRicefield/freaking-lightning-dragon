package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Unified sleep goal for both tamed and wild dragons.
 * - Tamed: sleeps at night near owner, or immediately when owner sleeps.
 * - Wild: sleeps during daytime when sheltered; never during thunderstorms.
 * Goal runs only when calm (not ridden, not flying/dodging/aggro/targeting).
 */
public class DragonSleepGoal extends Goal {
    private final LightningDragonEntity dragon;

    public DragonSleepGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    private boolean isDay() { return dragon.level() != null && dragon.level().isDay(); }
    private boolean isNight() { return dragon.level() != null && !dragon.level().isDay(); }
    private boolean isThundering() { return dragon.level() != null && dragon.level().isThundering(); }
    private boolean ownerSleeping() {
        LivingEntity owner = dragon.getOwner();
        return owner instanceof net.minecraft.world.entity.player.Player p && p.isSleeping();
    }
    private boolean nearOwner(double radius) {
        LivingEntity owner = dragon.getOwner();
        return owner != null && dragon.distanceToSqr(owner) <= radius * radius;
    }
    private boolean isSheltered() {
        var level = dragon.level();
        if (level == null) return false;
        var pos = dragon.blockPosition();
        boolean noSky = !level.canSeeSky(pos);
        int light = level.getMaxLocalRawBrightness(pos);
        return noSky || light < 7;
    }
    private boolean calm() {
        // Do not allow sleeping in fluids or lava, or while panicking/aggro/etc.
        if (dragon.isInWaterOrBubble() || dragon.isInLava()) return false;
        return !dragon.isDying() && !dragon.isDodging() && !dragon.isFlying()
                && !dragon.isVehicle() && dragon.getTarget() == null && !dragon.isAggressive();
    }

    @Override
    public boolean canUse() {
        if (!calm()) return false;
        if (dragon.isSleepSuppressed()) return false;
        if (dragon.isTame()) {
            if (ownerSleeping()) return true;
            return isNight() && nearOwner(14);
        } else {
            return isDay() && !isThundering() && isSheltered();
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (!dragon.isSleeping() && !dragon.isSleepTransitioning()) return false;
        if (!calm()) return false;
        if (dragon.isTame()) {
            return ownerSleeping() || isNight();
        } else {
            return isDay() && !isThundering() && isSheltered();
        }
    }

    @Override
    public void start() {
        dragon.startSleepEnter();
    }

    @Override
    public void stop() {
        dragon.startSleepExit();
    }
}
