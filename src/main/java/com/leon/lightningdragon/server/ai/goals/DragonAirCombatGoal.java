package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.common.registry.ModAbilities;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
public class DragonAirCombatGoal extends Goal {

    private final LightningDragonEntity dragon;
    
    // Attack kind/phase flags (kept compatible with existing animations)
    private static final int ATTACK_KIND_BITE = 3;
    private static final int ATTACK_KIND_DIVE = 4;
    private static final int ATTACK_KIND_BEAM = 5;

    private static final int PHASE_WINDUP = 1;
    private static final int PHASE_COMMIT = 2;
    private static final int PHASE_RECOVER = 3;

    // Aim thresholds with hysteresis
    private static final float STRAFE_YAW_ENTER = 15f;
    private static final float STRAFE_YAW_MAINTAIN = 10f;
    private static final float BEAM_YAW_ENTER = 12f;
    private static final float BEAM_YAW_FIRE = 6f;
    
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
                (dragon.isFlying() || dragon.isTakeoff()) &&
                !dragon.isLanding() &&
                dragon.distanceToSqr(target) < 3600; // 60 block range - wider for aerial maneuvers
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = dragon.getTarget();
        return target != null &&
                target.isAlive() &&
                (dragon.isFlying() || dragon.isTakeoff()) &&
                !dragon.isLanding() &&
                dragon.distanceToSqr(target) < 6400; // 80 block range - generous buffer for wide circles
    }

    @Override
    public void start() {
        currentPhase = AttackPhase.SETUP;
        phaseTimer = 0;
        dragon.setHovering(false); // Start with movement
        clearAttackFlags();
    }

    @Override
    public void tick() {
        LivingEntity target = dragon.getTarget();
        if (target == null) return;

        // Handle phase timing
        phaseTimer++;
        if (modeSwitchCooldown > 0) modeSwitchCooldown--;

        // Clean transition into flight combat logic
        if ((dragon.isFlying() || dragon.isTakeoff()) && currentAttackMode == null) {
            currentPhase = AttackPhase.SETUP;
            phaseTimer = 0;
            dragon.setHovering(false); // Clean transition from holding pattern
        }

        // Main air combat logic - execute attack FSM
        if (dragon.isFlying() || dragon.isTakeoff()) {
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
            modeSwitchCooldown = 50; // Reduce mode thrash; dwell before switching
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

        // 1) Dive-bomb first if we have vertical advantage and are in range
        if (heightDiff > 6.0 && distance < 30.0) {
            return AirAttackMode.DIVE_BOMB;
        }

        // 2) Only prefer ranged-circle at large ranges or when target airborne at range
        if (distance > 36.0 || (!target.onGround() && distance > 28.0) || target.isInWater()) {
            return AirAttackMode.RANGED_CIRCLE;
        }

        // 3) Medium range: circle-strafe to set up bite/beam choices
        if (distance > 12.0) {
            return AirAttackMode.CIRCLE_STRAFE;
        }

        // 4) Close: strafe run for quick bite
        return AirAttackMode.STRAFE_RUN;
    }


    @Override
    public void stop() {
        dragon.setHovering(false);
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
        dragon.setHovering(false);
        
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
        dragon.setHovering(false);
        
        switch (currentPhase) {
            case SETUP -> {
                // Set clockwise direction once per strafe
                if (phaseTimer == 0) {
                    strafeClockwise = dragon.getRandom().nextBoolean();
                }
                
                // Use DragonMathUtil for proper entry point calculation
                Vec3 entryPos = DragonMathUtil.circleEntityPosition(target, 25.0f, 0.0f, strafeClockwise, 0, 0.0f);
                entryPoint = entryPos.add(0, 8, 0);
                
                // Approach using MoveControl for single movement authority
                dragon.getMoveControl().setWantedPosition(entryPoint.x, entryPoint.y, entryPoint.z, 1.0);

                // Look in movement direction during travel
                Vec3 lookDir = entryPoint.subtract(dragon.position()).normalize();
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
                
                if (yawError <= STRAFE_YAW_ENTER && los) {
                    setAttackFlags(ATTACK_KIND_BITE, PHASE_WINDUP); // STRAFE_BITE, WINDUP
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
                    setAttackFlags(ATTACK_KIND_BITE, PHASE_COMMIT); // COMMIT phase
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
                // Use target-centric lateral basis for cleaner pass path around the target
                Vec3 toTarget = target.position().subtract(dragon.position());
                Vec3 lateral = toTarget.cross(new Vec3(0, 1, 0));
                if (lateral.lengthSqr() < 1.0e-6) {
                    lateral = dragon.getLookAngle().cross(new Vec3(0, 1, 0));
                }
                lateral = lateral.normalize().scale(strafeClockwise ? 12 : -12);
                Vec3 passPoint = target.position().add(lateral).add(0, 2, 0);
                dragon.getMoveControl().setWantedPosition(passPoint.x, passPoint.y, passPoint.z, 1.0);
                
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
                dragon.getMoveControl().setWantedPosition(exitPoint.x, exitPoint.y, exitPoint.z, 1.0);
                
                if (dragon.distanceToSqr(exitPoint) < 6.25) { // 2.5 blocks
                    setAttackFlags(ATTACK_KIND_BITE, PHASE_RECOVER); // RECOVERY
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 8) {
                    clearAttackFlags();
                    currentAttackMode = null; // Allow mode switch
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
        }
    }
    
    private void executeCircleStrafeFSM(LivingEntity target) {
        dragon.setHovering(false);
        
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
                
                dragon.getMoveControl().setWantedPosition(entryPoint.x, entryPoint.y, entryPoint.z, 1.0);
                
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
                
                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.0);
                dragon.getLookControl().setLookAt(target, 30f, 30f);
                
                if (phaseTimer > 20 && hasLineOfSight(target) && getYawErrorTo(target) <= STRAFE_YAW_MAINTAIN) {
                    if (distance > 12.0) {
                        setAttackFlags(ATTACK_KIND_BEAM, PHASE_WINDUP); // BEAM, WINDUP
                    } else {
                        setAttackFlags(ATTACK_KIND_BITE, PHASE_WINDUP); // BITE, WINDUP
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
                
                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.0);
                dragon.getLookControl().setLookAt(target, 30f, 30f);
                
                if (phaseTimer >= 15) {
                    if (distance > 18.0) {
                        setAttackFlags(ATTACK_KIND_BEAM, PHASE_COMMIT); // BEAM, COMMIT
                        if (dragon.combatManager.canStart(ModAbilities.LIGHTNING_BEAM)) {
                            dragon.combatManager.tryUseAbility(ModAbilities.LIGHTNING_BEAM);
                        }
                    } else {
                        setAttackFlags(ATTACK_KIND_BITE, PHASE_COMMIT); // BITE, COMMIT
                        if (dragon.combatManager.canStart(ModAbilities.BITE)) {
                            dragon.combatManager.tryUseAbility(ModAbilities.BITE);
                        }
                    }
                    currentPhase = AttackPhase.COMMIT;
                    phaseTimer = 0;
                }
            }
            case COMMIT -> {
                // Continue circling during attack with velocity control + repulsion, then clamp
                double distance = dragon.distanceTo(target);
                float radius = (float) Math.max(15.0, distance * 0.8);
                float speed = 0.08f;
                Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, circleClockwise, phaseTimer, 0);
                attackTarget = circlePos.add(0, 8, 0);

                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.0);
                
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
                
                dragon.getMoveControl().setWantedPosition(exitPoint.x, exitPoint.y, exitPoint.z, 1.0);
                
                if (phaseTimer >= 30) {
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 12) {
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
        dragon.setHovering(false);
        
        switch (currentPhase) {
            case SETUP -> {
                // Climb to dive altitude with altitude clamping
                Vec3 targetPos = target.position();
                double groundLevel = getGroundLevel(targetPos);
                double clampedAltitude = DragonMathUtil.clampAltitude(targetPos.y + 22, groundLevel, 15, 35);
                entryPoint = new Vec3(targetPos.x, clampedAltitude, targetPos.z);
                
                // Climb to dive position via MoveControl
                dragon.getMoveControl().setWantedPosition(entryPoint.x, entryPoint.y, entryPoint.z, 1.0);

                // Look in flight direction during climb
                Vec3 lookDir = entryPoint.subtract(dragon.position()).normalize();
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
                
                if (yawError <= STRAFE_YAW_ENTER && los && phaseTimer > 10) {
                    setAttackFlags(ATTACK_KIND_DIVE, PHASE_WINDUP); // DIVE, WINDUP
                    currentPhase = AttackPhase.WINDUP;
                    phaseTimer = 0;
                } else if (phaseTimer > 80) {
                    // Timeout - return to setup
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
            case WINDUP -> {
                // Telegraph dive: hold look and prepare target point ahead/below
                Vec3 ahead = dragon.getLookAngle().normalize().scale(12);
                Vec3 diveAim = dragon.position().add(ahead).add(0, -6, 0);
                dragon.getMoveControl().setWantedPosition(diveAim.x, diveAim.y, diveAim.z, 1.0);
                
                if (phaseTimer >= 12) {
                    setAttackFlags(ATTACK_KIND_DIVE, PHASE_COMMIT); // DIVE, COMMIT
                    currentPhase = AttackPhase.COMMIT;
                    phaseTimer = 0;
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
                } else {
                    // Continue diving toward target
                    Vec3 diveAimCommit = target.position().add(0, 1, 0);
                    dragon.getMoveControl().setWantedPosition(diveAimCommit.x, diveAimCommit.y, diveAimCommit.z, 1.0);
                }
            }
            case PEEL -> {
                // Climb to exit point using MoveControl; look control keeps alignment
                dragon.getMoveControl().setWantedPosition(exitPoint.x, exitPoint.y, exitPoint.z, 1.0);
                
                if (dragon.distanceToSqr(exitPoint) < 16.0) {
                    setAttackFlags(ATTACK_KIND_DIVE, PHASE_RECOVER); // RECOVERY
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 12) {
                    clearAttackFlags();
                    currentAttackMode = null;
                    currentPhase = AttackPhase.SETUP;
                    phaseTimer = 0;
                }
            }
        }
    }
    
    private void executeRangedCircleFSM(LivingEntity target) {
        dragon.setHovering(false);
        
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
                
                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.0);
                
                // Smooth look at target for ranged alignment
                DragonMathUtil.smoothLookAt(dragon, target, 15f, 15f);
                
                // Check for beam opportunity with slightly relaxed gates
                float yawError = getYawErrorTo(target);
                boolean los = hasLineOfSight(target);

                if (phaseTimer > 15 && distance <= 45.0 && los && yawError <= BEAM_YAW_ENTER) {
                    setAttackFlags(ATTACK_KIND_BEAM, PHASE_WINDUP); // BEAM, WINDUP
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
                
                // Use MoveControl target for precision without velocity writes
                dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.0);
                
                // Very precise look at target
                DragonMathUtil.smoothLookAt(dragon, target, 8f, 8f);
                
                if (phaseTimer >= 20) {
                    // Final accuracy check before firing
                    float yawError = getYawErrorTo(target);
                    boolean los = hasLineOfSight(target);
                    
                    setAttackFlags(ATTACK_KIND_BEAM, PHASE_COMMIT); // BEAM, COMMIT
                    currentPhase = AttackPhase.COMMIT;
                    phaseTimer = 0;
                    
                    // Fire beam only with perfect alignment
                    if (los && yawError <= BEAM_YAW_FIRE && dragon.combatManager.canStart(ModAbilities.LIGHTNING_BEAM)) {
                        dragon.combatManager.tryUseAbility(ModAbilities.LIGHTNING_BEAM);
                    }
                }
            }
            case COMMIT -> {
                // Hold steady position during beam with minor stabilization
                dragon.getMoveControl().setWantedPosition(dragon.getX(), dragon.getY(), dragon.getZ(), 0.0);
                
                // Maintain precise target lock during beam
                DragonMathUtil.smoothLookAt(dragon, target, 5f, 5f);
                
                if (phaseTimer >= 15) {
                    setAttackFlags(ATTACK_KIND_BEAM, PHASE_RECOVER); // RECOVERY
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
                
                if (phaseTimer >= 15) {
                    currentPhase = AttackPhase.RECOVER;
                    phaseTimer = 0;
                }
            }
            case RECOVER -> {
                if (phaseTimer >= 15) {
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
        // Sync attack kind/phase to entity for animations/telemetry
        dragon.setAttackKind(attackKind);
        dragon.setAttackPhase(attackPhase);
    }
    
    private void clearAttackFlags() {
        dragon.setAttackKind(0);
        dragon.setAttackPhase(0);
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
