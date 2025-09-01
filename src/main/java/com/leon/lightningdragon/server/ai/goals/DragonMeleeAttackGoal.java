package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.common.registry.ModAbilities;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Vanilla-like melee attack goal that uses Navigation, MoveControl, LookControl and Sensing,
 * but performs the dragon's custom abilities (bite / horn gore) instead of a vanilla attack.
 */
public class DragonMeleeAttackGoal extends MeleeAttackGoal {
    private final LightningDragonEntity dragon;
    private int debugTick = 0;
    private static final boolean DEBUG_AI = true; // set false to silence
    private boolean lastRun = true;
    private int pathReissueCooldown = 0;

    public DragonMeleeAttackGoal(LightningDragonEntity dragon, double speedModifier, boolean useLongMemory) {
        super(dragon, speedModifier, useLongMemory);
        this.dragon = dragon;
    }

    @Override
    public boolean canUse() {
        // Do not run while ridden
        if (dragon.getControllingPassenger() != null) return false;
        // Only run on ground - let combat flight goal handle flight decisions
        if (dragon.isFlying() || dragon.isHovering() || dragon.isTakeoff()) {
            return false;
        }
        return super.canUse();
    }

    @Override
    public void start() {
        super.start();
        dragon.stateManager.setRunning(true);
    }

    @Override
    public void stop() {
        super.stop();
        dragon.stateManager.setRunning(false);
        dragon.getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        // Lock running while chasing to ensure decisive pursuit
        LivingEntity target = this.mob.getTarget();
        if (target != null && target.isAlive()) {
            double dist = dragon.distanceTo(target);
            boolean shouldRun = true; // always run while a valid target exists
            // Apply run state and nudge navigation speed to match
            boolean prevRun = lastRun;
            lastRun = shouldRun;
            dragon.stateManager.setRunning(shouldRun);
            // Re-issue move with updated speed when state changes (or periodically by super.tick())
            if (prevRun != shouldRun) {
                this.mob.getNavigation().moveTo(target, shouldRun ? 1.35 : 1.0);
            }

            // Keep pursuing even while attacking: continuously drive path towards target
            if (--pathReissueCooldown <= 0 || this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().moveTo(target, 1);
                pathReissueCooldown = 10;
            }

            // Periodic debug to verify controllers/state while chasing
            if (DEBUG_AI && !dragon.level().isClientSide) {
                debugTick++;
                if ((debugTick & 15) == 0) { // every 16 ticks
                    String nav = dragon.getNavigation() != null ? dragon.getNavigation().getClass().getSimpleName() : "null";
                    String mc = dragon.getMoveControl() != null ? dragon.getMoveControl().getClass().getSimpleName() : "null";
                    boolean los = dragon.getSensing().hasLineOfSight(target);
                    boolean pathDone = dragon.getNavigation() == null || dragon.getNavigation().isDone();
                    double speed = dragon.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    System.out.println(
                            String.format(
                                    "[DRAGON_AI] dist=%.2f run=%s speed=%.3f nav=%s moveCtrl=%s pathDone=%s flying=%s takeoff=%s hover=%s los=%s target=(%.1f,%.1f,%.1f)",
                                    dist,
                                    shouldRun,
                                    speed,
                                    nav,
                                    mc,
                                    pathDone,
                                    dragon.isFlying(),
                                    dragon.isTakeoff(),
                                    dragon.isHovering(),
                                    los,
                                    target.getX(), target.getY(), target.getZ()
                            )
                    );
                }
            }
        }
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distanceToTargetSqr) {
        double reachSqr = this.getAttackReachSqr(target);
        if (distanceToTargetSqr <= reachSqr && this.isTimeToAttack()) {
            this.resetAttackCooldown();
            // Randomize attack choice; prefer bite if in air, though we force ground in canUse
            boolean useBite = dragon.isFlying() || dragon.getRandom().nextBoolean();
            if (useBite) {
                dragon.combatManager.tryUseAbility(ModAbilities.BITE);
            } else {
                dragon.combatManager.tryUseAbility(ModAbilities.HORN_GORE);
            }
        }
    }

    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        // Vanilla-like reach with consideration for large sizes
        double w = dragon.getBbWidth();
        double tw = target.getBbWidth();
        // Slightly generous factor to match the large dragon scale
        double reach = (w * 0.6) + (tw * 0.6) + 1.5;
        return reach * reach;
    }

    @Override
    protected boolean isTimeToAttack() {
        // Use MeleeAttackGoal's internal cooldown
        return super.isTimeToAttack();
    }

    @Override
    protected void resetAttackCooldown() {
        super.resetAttackCooldown();
    }
}
