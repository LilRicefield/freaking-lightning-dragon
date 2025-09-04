package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.common.registry.ModAbilities;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * FSM-based melee attack goal with deterministic move selection and precise timing phases.
 * Phases: APPROACH -> ALIGN -> WINDUP -> COMMIT -> RECOVER
 */
public class DragonMeleeAttackGoal extends Goal {
    private final LightningDragonEntity dragon;
    
    enum Move { NONE, HORN, BITE }
    enum Phase { APPROACH, ALIGN, WINDUP, COMMIT, RECOVER }
    
    private Move move = Move.NONE;
    private Phase phase = Phase.APPROACH;
    private int timer = 0;
    private int pathCooldown = 0;

    public DragonMeleeAttackGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Do not run while ridden
        if (dragon.getControllingPassenger() != null) return false;
        // Only run on ground - let combat flight goal handle flight decisions
        if (dragon.isFlying() || dragon.isHovering() || dragon.isTakeoff()) return false;
        
        LivingEntity target = dragon.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        phase = Phase.APPROACH;
        timer = 0;
        move = Move.NONE;
        setRun(true);
    }

    @Override
    public void stop() {
        setRun(false);
        dragon.getNavigation().stop();
        setAttackFlags(0, 0);
        phase = Phase.APPROACH;
        move = Move.NONE;
        timer = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = dragon.getTarget();
        if (target == null || !target.isAlive()) {
            stop();
            return;
        }

        double dist = dragon.distanceTo(target);
        double angle = angleToTargetDeg(dragon, target);
        boolean los = dragon.getSensing().hasLineOfSight(target);

        switch (phase) {
            case APPROACH -> {
                // Ensure running during approach
                setRun(true);
                
                // Throttled re-path with higher speed for approach
                if (pathCooldown-- <= 0 || dragon.getNavigation().isDone()) {
                    double speed = dist > 6 ? 1.35 : 1.2; // Higher speeds for approach
                    dragon.getNavigation().moveTo(target, speed);
                    pathCooldown = 8;
                }
                // When in either horn or bite band, pick a move
                if (chooseMove(dist, angle, los)) {
                    phase = Phase.ALIGN;
                    timer = 0;
                }
            }

            case ALIGN -> {
                dragon.getNavigation().stop();
                faceTargetRateLimited(target, 20.0f); // Faster turning
                if (isAligned(angle)) {
                    phase = Phase.WINDUP;
                    timer = 0;
                    setAttackFlags(kindId(move), 1); // WINDUP
                } else if (++timer > 15 || !los) { // Much shorter timeout - commit faster!
                    phase = Phase.APPROACH;
                    move = Move.NONE;
                }
            }

            case WINDUP -> {
                dragon.getNavigation().stop();
                faceTargetHard(target);
                int windup = (move == Move.HORN) ? 6 : 4; // Much faster windup times!
                if (++timer >= windup) {
                    phase = Phase.COMMIT;
                    timer = 0;
                    setAttackFlags(kindId(move), 2); // COMMIT
                }
            }

            case COMMIT -> {
                if (timer == 0) {
                    if (move == Move.HORN) dashForward(dragon, 0.85); // short impulse
                    // fire ability exactly once
                    triggerAbility(move);
                }
                int commit = (move == Move.HORN) ? 6 : 4;
                if (++timer >= commit) {
                    setAbilityCooldown(move);
                    phase = Phase.RECOVER;
                    timer = 0;
                    setAttackFlags(kindId(move), 3); // RECOVER
                }
            }

            case RECOVER -> {
                // Immediately chase during recovery - no standing around!
                if (pathCooldown-- <= 0 || dragon.getNavigation().isDone()) {
                    dragon.getNavigation().moveTo(target, 1.35);
                    pathCooldown = 6;
                }
                
                if (++timer >= ((move == Move.HORN) ? 8 : 6)) { // Much shorter recovery!
                    setAttackFlags(0, 0);
                    move = Move.NONE;
                    phase = Phase.APPROACH;
                    timer = 0;
                }
            }
        }
    }

    private boolean chooseMove(double dist, double angle, boolean los) {
        boolean hornReady = dragon.combatManager.canStart(ModAbilities.HORN_GORE);
        boolean biteReady = dragon.combatManager.canStart(ModAbilities.BITE);

        // Much more generous overlapping ranges - commit faster!
        boolean hornOk = hornReady && los && dist >= 2.0 && dist <= 8.0 && Math.abs(angle) <= 45.0;
        boolean biteOk = biteReady && los && dist >= 1.0 && dist <= 6.0 && Math.abs(angle) <= 65.0;

        // prefer horn if reasonably lined up; else bite for everything else
        if (hornOk) {
            move = Move.HORN;
            return true;
        }
        if (biteOk) {
            move = Move.BITE;
            return true;
        }
        return false;
    }

    private boolean isAligned(double angle) {
        double limit = (move == Move.HORN) ? 45.0 : 65.0; // Match the generous selection ranges
        return Math.abs(angle) <= limit;
    }

    private void triggerAbility(Move m) {
        if (m == Move.HORN) dragon.combatManager.tryUseAbility(ModAbilities.HORN_GORE);
        else if (m == Move.BITE) dragon.combatManager.tryUseAbility(ModAbilities.BITE);
    }

    private void setAbilityCooldown(Move m) {
        if (m == Move.HORN) dragon.combatManager.setAbilityCooldown(ModAbilities.HORN_GORE, 80);
        else if (m == Move.BITE) dragon.combatManager.setAbilityCooldown(ModAbilities.BITE, 40);
    }

    private static int kindId(Move m) {
        return m == Move.HORN ? 1 : m == Move.BITE ? 2 : 0;
    }

    private void setRun(boolean v) {
        dragon.stateManager.setRunning(v);
    }

    private void setAttackFlags(int kind, int phase) {
        dragon.stateManager.setAttackKind(kind);
        dragon.stateManager.setAttackPhase(phase);
    }

    // ===== Helper methods =====

    private static double angleToTargetDeg(LivingEntity self, LivingEntity target) {
        Vec3 fwd = self.getForward().normalize();
        Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0)
                .subtract(self.position().add(0, self.getBbHeight() * 0.5, 0)).normalize();
        double dot = Mth.clamp(fwd.dot(dir), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private void faceTargetRateLimited(LivingEntity target, float degPerTick) {
        dragon.getLookControl().setLookAt(target, degPerTick, degPerTick);
    }

    private void faceTargetHard(LivingEntity target) {
        dragon.getLookControl().setLookAt(target, 90.0f, 90.0f);
    }

    private static void dashForward(LightningDragonEntity dragon, double strength) {
        Vec3 fwd = dragon.getForward().normalize();
        dragon.setDeltaMovement(dragon.getDeltaMovement().add(fwd.scale(strength)));
    }
}