package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.common.registry.ModAbilities;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Advanced air combat goal with multiple dynamic attack patterns:
 * - Strafe runs for fast hit-and-run attacks  
 * - Circle strafing for sustained pressure
 * - Dive bombing for powerful strikes from above
 * - Ranged circles for long-range beam attacks (NO static hovering!)
 * 
 * Fixes the "helicopter mode" problem by keeping dragon in constant motion.
 */
public class DragonAirCombatGoal extends Goal {

    private final LightningDragonEntity dragon;
    
    // Attack patterns
    public enum AirAttackMode {
        STRAFE_RUN,      // Fast pass attack then reposition
        CIRCLE_STRAFE,   // Circle around target while attacking  
        DIVE_BOMB,       // Dive from above for powerful attack
        RANGED_CIRCLE    // Wide circle for ranged beam attacks (no static hovering!)
    }
    
    // Attack phases for proper FSM
    public enum AttackPhase {
        SETUP,      // Move to entry point
        ALIGN,      // Align for attack
        WINDUP,     // Telegraph attack
        COMMIT,     // Execute attack
        PEEL,       // Fly away safely
        RECOVER     // Cooldown phase
    }

    private AirAttackMode currentAttackMode;
    private AttackPhase currentPhase;
    private Vec3 attackTarget;
    private Vec3 entryPoint;
    private Vec3 exitPoint;
    private int phaseTimer = 0;
    private int modeSwitchCooldown = 0;
    
    // Persistent per-mode parameters
    private Boolean circleClockwise = null;
    private boolean strafeClockwise = false;


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
                dragon.distanceToSqr(target) < 3600; // 60 block range - wider for aerial maneuvers
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                (dragon.stateManager.isFlying() || dragon.stateManager.isTakeoff()) &&
                !dragon.stateManager.isLanding() &&
                dragon.distanceToSqr(target) < 6400; // 80 block range - generous buffer for wide circles
    }

    @Override
    public void start() {
        currentPhase = AttackPhase.SETUP;
        phaseTimer = 0;
        dragon.stateManager.setHovering(false); // Start with movement
        clearAttackFlags();
    }

    @Override
    public void tick() {
        LivingEntity target = dragon.getTarget();
        if (target == null) return;

        // Handle phase timing
        phaseTimer++;
        if (modeSwitchCooldown > 0) modeSwitchCooldown--;

        // If still taking off, use holding pattern until fully airborne
        if (dragon.stateManager.isTakeoff() && !dragon.stateManager.isFlying()) {
            executeHoldingPattern(target);
            return;
        }

        // Just transitioned from takeoff to full flight - reset states
        if (dragon.stateManager.isFlying() && currentAttackMode == null) {
            currentPhase = AttackPhase.SETUP;
            phaseTimer = 0;
            dragon.stateManager.setHovering(false); // Clean transition from holding pattern
        }

        // Main air combat logic - execute attack FSM
        if (dragon.stateManager.isFlying()) {
            executeAirCombatFSM(target);
        }
    }


    private void executeAirCombatFSM(LivingEntity target) {
        double distance = dragon.distanceTo(target);
        
        // Choose attack mode if none set or we're in SETUP after completing a full cycle
        if (currentAttackMode == null || (currentPhase == AttackPhase.SETUP && modeSwitchCooldown <= 0)) {
            currentAttackMode = chooseAttackMode(target, distance);
            currentPhase = AttackPhase.SETUP;
            phaseTimer = 0;
            modeSwitchCooldown = 60; // Don't switch modes too frequently
            resetModeParameters(); // Reset per-mode state
        }
        
        // Execute current attack pattern with FSM
        switch (currentAttackMode) {
            case STRAFE_RUN -> executeStrafeRunFSM(target);
            case CIRCLE_STRAFE -> executeCircleStrafeFSM(target);
            case DIVE_BOMB -> executeDiveBombFSM(target);
            case RANGED_CIRCLE -> executeRangedCircleFSM(target);
        }
    }
    
    private AirAttackMode chooseAttackMode(LivingEntity target, double distance) {
        Vec3 dragonPos = dragon.position();
        Vec3 targetPos = target.position();
        double heightDiff = dragonPos.y - targetPos.y;
        
        // Prefer ranged circle for beam-friendly scenarios (distant, aerial, or ranged targets)
        if (distance > 30.0 || !target.onGround() || target.isInWater()) {
            return AirAttackMode.RANGED_CIRCLE;
        }
        
        // Prefer dive bomb if significantly above target and close enough for melee
        if (heightDiff > 12.0 && distance < 20.0) {
            return AirAttackMode.DIVE_BOMB;
        }
        
        // For medium-range targets, prefer circle strafe to utilize beam/bite combination
        if (distance > 15.0) {
            return AirAttackMode.CIRCLE_STRAFE;
        }
        
        // Close combat - use strafe runs for hit-and-run
        return AirAttackMode.STRAFE_RUN;
    }


    @Override
    public void stop() {
        dragon.stateManager.setHovering(false);
        dragon.getNavigation().stop();
        attackTarget = null;
        entryPoint = null;
        exitPoint = null;
        currentAttackMode = null;
        currentPhase = null;
        phaseTimer = 0;
        clearAttackFlags();
        dragon.forceEndActiveAbility();
    }

    private void executeHoldingPattern(LivingEntity target) {
        // Dynamic holding pattern during takeoff - circle instead of static hover
        dragon.stateManager.setHovering(false);
        
        float radius = 20.0f; // Medium circle during takeoff
        float speed = 0.06f; // Moderate speed while positioning
        boolean clockwise = true; // Consistent direction during takeoff
        
        Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, clockwise, phaseTimer, 0);
        Vec3 holdPosition = circlePos.add(0, 12, 0); // Circle above target
        
        dragon.getMoveControl().setWantedPosition(holdPosition.x, holdPosition.y, holdPosition.z, 0.8);

        // Don't attack during takeoff - just position
    }

    // ===== NEW FSM-BASED ATTACK PATTERNS =====
    
    private void executeStrafeRunFSM(LivingEntity target) {
        dragon.stateManager.setHovering(false);
        
        switch (currentPhase) {
            case SETUP -> {
                // Set clockwise direction once per strafe
                if (phaseTimer == 0) {
                    strafeClockwise = dragon.getRandom().nextBoolean();
                }
                
                // Use DragonMathUtil for proper entry point calculation
                Vec3 entryPos = DragonMathUtil.circleEntityPosition(target, 25.0f, 0.0f, strafeClockwise, 0, 0.0f);
                entryPoint = entryPos.add(0, 8, 0);
                
                // Use calculateFlightVector for smooth approach
                Vec3 flightVec = DragonMathUtil.calculateFlightVector(dragon, entryPoint, 1.25, 0.1);
                dragon.setDeltaMovement(flightVec);
                
                // Look in movement direction during travel
                Vec3 lookDir = flightVec.normalize();
                dragon.getLookControl().setLookAt(
                    dragon.getX() + lookDir.x * 5, 
                    dragon.getY() + lookDir.y * 5, 
                    dragon.getZ() + lookDir.z * 5, 30f, 30f
                );
                
                if (dragon.distanceToSqr(entryPoint) < 4.0) {
                    currentPhase = AttackPhase.ALIGN;
                    phaseTimer = 0;
                }
            }
            case ALIGN -> {
                // Use smooth look at for precise alignment
                DragonMathUtil.smoothLookAt(dragon, target, 20f, 20f);
                
                // Check alignment gates
                float yawError = getYawErrorTo(target);
                boolean los = hasLineOfSight(target);
                
                if (yawError <= 12f && los) {
                    setAttackFlags(3, 1); // STRAFE_BITE, WINDUP
                    currentPhase = AttackPhase.WINDUP;
                    phaseTimer = 0;
                } else if (phaseTimer > 60) {
                    // Timeout - return to setup
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
            case WINDUP -> {
                // Telegraph attack
                if (phaseTimer >= 10) {
                    setAttackFlags(3, 2); // COMMIT phase
                    currentPhase = AttackPhase.COMMIT;
                    phaseTimer = 0;
                    
                    // Fire ability once
                    if (dragon.combatManager.canStart(ModAbilities.BITE)) {
                        dragon.combatManager.tryUseAbility(ModAbilities.BITE);
                    }
                }
            }
            case COMMIT -> {
                // Controlled strafe pass with repulsion to avoid target collision
                Vec3 passPoint = target.position().add(
                    dragon.getLookAngle().cross(new Vec3(0, 1, 0)).normalize()
                    .scale(strafeClockwise ? 12 : -12)
                ).add(0, 2, 0);
                
                Vec3 desired = DragonMathUtil.calculateFlightVector(dragon, passPoint, 1.4, 0.15);
                
                // Add repulsion from target to avoid face-planting
                Vec3 repulsion = DragonMathUtil.calculateRepulsionForce(target, dragon, 3.0, 0.1);
                desired = desired.add(repulsion);
                
                dragon.setDeltaMovement(DragonMathUtil.clampVectorLength(desired, 1.6));
                
                if (phaseTimer >= 8) {
                    currentPhase = AttackPhase.PEEL;
                    phaseTimer = 0;
                    
                    // Calculate smooth exit point
                    Vec3 forward = dragon.getLookAngle().normalize();
                    exitPoint = target.position().add(forward.scale(20)).add(0, 10, 0);
                }
            }
            case PEEL -> {
                // Fly to safe distance
                dragon.getMoveControl().setWantedPosition(exitPoint.x, exitPoint.y, exitPoint.z, 1.4);
                
                if (dragon.distanceToSqr(exitPoint) < 6.25) { // 2.5 blocks
                    setAttackFlags(3, 3); // RECOVERY
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 20) {
                    clearAttackFlags();
                    currentAttackMode = null; // Allow mode switch
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
        }
    }
    
    private void executeCircleStrafeFSM(LivingEntity target) {
        dragon.stateManager.setHovering(false);
        
        // Persist clockwise direction for this mode
        if (circleClockwise == null) {
            circleClockwise = dragon.getRandom().nextBoolean();
        }
        
        switch (currentPhase) {
            case SETUP -> {
                // Move to circle starting position
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.max(15.0, distance * 0.8);
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, 0, circleClockwise, 0, 0);
                entryPoint = circlePos.add(0, 8, 0);
                
                dragon.getMoveControl().setWantedPosition(entryPoint.x, entryPoint.y, entryPoint.z, 1.2);
                
                if (dragon.distanceToSqr(entryPoint) < 9.0) {
                    currentPhase = AttackPhase.ALIGN;
                    phaseTimer = 0;
                }
            }
            case ALIGN -> {
                // Begin circling and align for attacks
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.max(15.0, distance * 0.8);
                float speed = 0.08f;
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, circleClockwise, phaseTimer, 0);
                attackTarget = circlePos.add(0, 8, 0);
                
                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.3);
                dragon.getLookControl().setLookAt(target, 30f, 30f);
                
                if (phaseTimer > 20 && hasLineOfSight(target) && getYawErrorTo(target) <= 12f) {
                    if (distance > 12.0) {
                        setAttackFlags(5, 1); // BEAM, WINDUP
                    } else {
                        setAttackFlags(3, 1); // BITE, WINDUP
                    }
                    currentPhase = AttackPhase.WINDUP;
                    phaseTimer = 0;
                }
                
                // Continue circling for a reasonable time
                if (phaseTimer > 200) {
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case WINDUP -> {
                // Continue circling while winding up
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.max(15.0, distance * 0.8);
                float speed = 0.06f; // Slower during attack
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, circleClockwise, phaseTimer, 0);
                attackTarget = circlePos.add(0, 8, 0);
                
                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.1);
                dragon.getLookControl().setLookAt(target, 30f, 30f);
                
                if (phaseTimer >= 15) {
                    if (distance > 12.0) {
                        setAttackFlags(5, 2); // BEAM, COMMIT
                        if (dragon.combatManager.canStart(ModAbilities.LIGHTNING_BEAM)) {
                            dragon.combatManager.tryUseAbility(ModAbilities.LIGHTNING_BEAM);
                        }
                    } else {
                        setAttackFlags(3, 2); // BITE, COMMIT
                        if (dragon.combatManager.canStart(ModAbilities.BITE)) {
                            dragon.combatManager.tryUseAbility(ModAbilities.BITE);
                        }
                    }
                    currentPhase = AttackPhase.COMMIT;
                    phaseTimer = 0;
                }
            }
            case COMMIT -> {
                // Continue circling during attack
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.max(15.0, distance * 0.8);
                float speed = 0.08f;
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, circleClockwise, phaseTimer, 0);
                attackTarget = circlePos.add(0, 8, 0);
                
                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.3);
                
                if (phaseTimer >= 10) {
                    setAttackFlags(distance > 12.0 ? 5 : 3, 3); // RECOVERY
                    currentPhase = AttackPhase.PEEL;
                    phaseTimer = 0;
                }
            }
            case PEEL -> {
                // Continue circling to safe position
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.max(18.0, distance * 0.9); // Wider circle
                float speed = 0.08f;
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, circleClockwise, phaseTimer, 0);
                exitPoint = circlePos.add(0, 10, 0);
                
                dragon.getMoveControl().setWantedPosition(exitPoint.x, exitPoint.y, exitPoint.z, 1.4);
                
                if (phaseTimer >= 30) {
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 40) {
                    clearAttackFlags();
                    circleClockwise = null; // Reset for next mode
                    currentAttackMode = null;
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
        }
    }
    
    private void executeDiveBombFSM(LivingEntity target) {
        dragon.stateManager.setHovering(false);
        
        switch (currentPhase) {
            case SETUP -> {
                // Climb to dive altitude with altitude clamping
                Vec3 targetPos = target.position();
                double groundLevel = getGroundLevel(targetPos);
                double clampedAltitude = DragonMathUtil.clampAltitude(targetPos.y + 22, groundLevel, 15, 35);
                entryPoint = new Vec3(targetPos.x, clampedAltitude, targetPos.z);
                
                // Smooth climb to dive position
                Vec3 climbVec = DragonMathUtil.calculateFlightVector(dragon, entryPoint, 1.0, 0.08);
                dragon.setDeltaMovement(climbVec);

                // Look in flight direction during climb
                Vec3 lookDir = climbVec.normalize();
                dragon.getLookControl().setLookAt(
                    dragon.getX() + lookDir.x * 4,
                    dragon.getY() + lookDir.y * 4,
                    dragon.getZ() + lookDir.z * 4, 20f, 20f
                );
                
                if (dragon.distanceToSqr(entryPoint) < 9.0) {
                    currentPhase = AttackPhase.ALIGN;
                    phaseTimer = 0;
                }
            }
            case ALIGN -> {
                // Smooth look at target for dive alignment
                DragonMathUtil.smoothLookAt(dragon, target, 25f, 25f);
                
                // Check alignment and LOS gates
                float yawError = getYawErrorTo(target);
                boolean los = hasLineOfSight(target);
                
                if (yawError <= 15f && los && phaseTimer > 10) {
                    setAttackFlags(4, 1); // DIVE, WINDUP
                    currentPhase = AttackPhase.WINDUP;
                    phaseTimer = 0;
                } else if (phaseTimer > 80) {
                    // Timeout - return to setup
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
            case WINDUP -> {
                // Telegraph dive with steady flight vector
                Vec3 steadyVec = DragonMathUtil.calculateFlightVector(dragon, dragon.position().add(dragon.getLookAngle()), 0.4, 0.12);
                dragon.setDeltaMovement(steadyVec);
                
                if (phaseTimer >= 12) {
                    setAttackFlags(4, 2); // DIVE, COMMIT
                    currentPhase = AttackPhase.COMMIT;
                    phaseTimer = 0;
                    
                    // Controlled dive impulse with clamping
                    Vec3 diveImpulse = dragon.getLookAngle().normalize().scale(0.9).add(0, -0.6, 0);
                    Vec3 clampedDive = DragonMathUtil.clampVectorLength(diveImpulse, 1.1);
                    dragon.setDeltaMovement(dragon.getDeltaMovement().add(clampedDive));
                }
            }
            case COMMIT -> {
                // Diving attack
                double distanceToGround = getDistanceToGroundAhead();
                double distanceToTarget = dragon.distanceTo(target);
                
                // Fire ability when close
                if (distanceToTarget <= 8.0 && phaseTimer < 5) {
                    if (dragon.combatManager.canStart(ModAbilities.BITE)) {
                        dragon.combatManager.tryUseAbility(ModAbilities.BITE);
                    }
                }
                
                // Check for pull-up conditions
                if (distanceToGround < 4.0 || phaseTimer > 25 || distanceToTarget < 3.0) {
                    currentPhase = AttackPhase.PEEL;
                    phaseTimer = 0;
                    
                    // Calculate pull-up exit point
                    Vec3 forward = dragon.getLookAngle().normalize();
                    exitPoint = dragon.position().add(forward.scale(15)).add(0, 18, 0);
                }
            }
            case PEEL -> {
                // Smooth pull-up and climb using flight vector
                Vec3 peelVec = DragonMathUtil.calculateFlightVector(dragon, exitPoint, 1.4, 0.15);
                dragon.setDeltaMovement(peelVec);
                
                // Ensure upward momentum during pull-up
                if (dragon.getDeltaMovement().y < 0.05) {
                    Vec3 upwardBoost = new Vec3(0, 0.08, 0);
                    dragon.setDeltaMovement(dragon.getDeltaMovement().add(upwardBoost));
                }
                
                // Look in climb direction
                Vec3 climbDir = peelVec.normalize();
                dragon.getLookControl().setLookAt(
                    dragon.getX() + climbDir.x * 6,
                    dragon.getY() + climbDir.y * 6,
                    dragon.getZ() + climbDir.z * 6, 30f, 30f
                );
                
                if (dragon.distanceToSqr(exitPoint) < 16.0) {
                    setAttackFlags(4, 3); // RECOVERY
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 30) {
                    clearAttackFlags();
                    currentAttackMode = null;
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
        }
    }
    
    private void executeRangedCircleFSM(LivingEntity target) {
        dragon.stateManager.setHovering(false);
        
        // Persist clockwise direction
        if (circleClockwise == null) {
            circleClockwise = (phaseTimer / 200) % 2 == 0;
        }
        
        switch (currentPhase) {
            case SETUP -> {
                // Move to ranged circle position
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.min(35.0, Math.max(25.0, distance * 0.7));
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, 0, circleClockwise, 0, 0);
                entryPoint = circlePos.add(0, 15, 0);
                
                dragon.getMoveControl().setWantedPosition(entryPoint.x, entryPoint.y, entryPoint.z, 1.0);
                
                if (dragon.distanceToSqr(entryPoint) < 16.0) {
                    currentPhase = AttackPhase.ALIGN;
                    phaseTimer = 0;
                }
            }
            case ALIGN -> {
                // Circle at range and look for beam opportunities
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.min(35.0, Math.max(25.0, distance * 0.7));
                float speed = 0.04f; // Slow, methodical
                
                // Get circle position and use flight vector
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, circleClockwise, phaseTimer, 0);
                attackTarget = circlePos.add(0, 15, 0);
                
                Vec3 circleVec = DragonMathUtil.calculateFlightVector(dragon, attackTarget, 1.0, 0.06);
                dragon.setDeltaMovement(circleVec);
                
                // Smooth look at target for ranged alignment
                DragonMathUtil.smoothLookAt(dragon, target, 15f, 15f);
                
                // Check for beam opportunity with strict gates
                float yawError = getYawErrorTo(target);
                boolean los = hasLineOfSight(target);
                
                if (phaseTimer > 30 && distance <= 45.0 && los && yawError <= 6f) {
                    setAttackFlags(5, 1); // BEAM, WINDUP
                    currentPhase = AttackPhase.WINDUP;
                    phaseTimer = 0;
                } else if (phaseTimer > 280) {
                    // Timeout - too long circling
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case WINDUP -> {
                // Stabilize for precise beam aim
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.min(35.0, Math.max(25.0, distance * 0.7));
                
                // Very slow circle for stability
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, 0.015f, circleClockwise, phaseTimer, 0);
                attackTarget = circlePos.add(0, 15, 0);
                
                // Use steady flight vector for precision
                Vec3 steadyVec = DragonMathUtil.calculateFlightVector(dragon, attackTarget, 0.6, 0.04);
                dragon.setDeltaMovement(steadyVec);
                
                // Very precise look at target
                DragonMathUtil.smoothLookAt(dragon, target, 8f, 8f);
                
                if (phaseTimer >= 20) {
                    // Final accuracy check before firing
                    float yawError = getYawErrorTo(target);
                    boolean los = hasLineOfSight(target);
                    
                    setAttackFlags(5, 2); // BEAM, COMMIT
                    currentPhase = AttackPhase.COMMIT;
                    phaseTimer = 0;
                    
                    // Fire beam only with perfect alignment
                    if (los && yawError <= 5f && dragon.combatManager.canStart(ModAbilities.LIGHTNING_BEAM)) {
                        dragon.combatManager.tryUseAbility(ModAbilities.LIGHTNING_BEAM);
                    }
                }
            }
            case COMMIT -> {
                // Hold steady position during beam with minor stabilization
                Vec3 holdVec = DragonMathUtil.calculateFlightVector(dragon, dragon.position(), 0.2, 0.02);
                dragon.setDeltaMovement(holdVec);
                
                // Maintain precise target lock during beam
                DragonMathUtil.smoothLookAt(dragon, target, 5f, 5f);
                
                if (phaseTimer >= 15) {
                    setAttackFlags(5, 3); // RECOVERY
                    currentPhase = AttackPhase.PEEL;
                    phaseTimer = 0;
                }
            }
            case PEEL -> {
                // Wide circle away
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.min(45.0, Math.max(35.0, distance * 0.8));
                float speed = 0.06f;
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, circleClockwise, phaseTimer, 0);
                exitPoint = circlePos.add(0, 18, 0);
                
                dragon.getMoveControl().setWantedPosition(exitPoint.x, exitPoint.y, exitPoint.z, 1.2);
                
                if (phaseTimer >= 40) {
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 60) {
                    clearAttackFlags();
                    circleClockwise = null;
                    currentAttackMode = null;
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
        }
    }
    
    // ===== UTILITY METHODS =====
    
    private void resetModeParameters() {
        circleClockwise = null;
        strafeClockwise = false;
        entryPoint = null;
        exitPoint = null;
    }
    
    private void setAttackFlags(int attackKind, int attackPhase) {
        /*
         * IMPLEMENTATION NEEDED: Set animation flags on dragon for proper sync
         * 
         * Attack Kinds:
         * 3 = STRAFE_BITE - Quick pass bite attack
         * 4 = DIVE_BOMB - Dive attack from above  
         * 5 = LIGHTNING_BEAM - Ranged beam attack
         * 
         * Attack Phases:
         * 0 = IDLE - No attack
         * 1 = WINDUP - Telegraph/preparation phase
         * 2 = COMMIT - Active attack/damage phase  
         * 3 = RECOVERY - Cool down phase
         * 
         * These flags should be synced to client for proper animation timing.
         * The dragon entity needs methods like:
         * dragon.setAttackKind(attackKind);
         * dragon.setAttackPhase(attackPhase);
         */
        
        // Placeholder implementation - replace with actual dragon methods
        // dragon.setAttackKind(attackKind);
        // dragon.setAttackPhase(attackPhase);
    }
    
    private void clearAttackFlags() {
        /*
         * IMPLEMENTATION NEEDED: Clear animation flags
         * Should reset both attack kind and phase to idle state
         */
        
        // Placeholder implementation - replace with actual dragon methods  
        // dragon.setAttackKind(0);
        // dragon.setAttackPhase(0);
    }
    
    private boolean hasLineOfSight(LivingEntity target) {
        return DragonMathUtil.hasLineOfSight(dragon, target);
    }
    
    private float getYawErrorTo(LivingEntity target) {
        return DragonMathUtil.yawErrorToTarget(dragon, target);
    }
    
    private double getDistanceToGroundAhead() {
        Vec3 start = dragon.position();
        Vec3 direction = dragon.getLookAngle().normalize();
        Vec3 end = start.add(direction.scale(8.0));
        
        // Simple raycast down to find ground
        for (int i = 0; i < 40; i++) {
            Vec3 testPos = end.add(0, -i, 0);
            if (!dragon.level().getBlockState(net.minecraft.core.BlockPos.containing(testPos)).isAir()) {
                return i;
            }
        }
        return 40; // Default if no ground found
    }
    
    private double getGroundLevel(Vec3 position) {
        // Find ground level at the given position
        for (int y = (int) position.y; y > dragon.level().getMinBuildHeight(); y--) {
            if (!dragon.level().getBlockState(new net.minecraft.core.BlockPos((int) position.x, y, (int) position.z)).isAir()) {
                return y + 1; // Return the level above the first solid block
            }
        }
        return dragon.level().getMinBuildHeight(); // Fallback to min build height
    }
    
    // Public getter for debugging/other systems
    public AirAttackMode getCurrentAttackMode() {
        return currentAttackMode;
    }
    
    public AttackPhase getCurrentPhase() {
        return currentPhase;
    }
}