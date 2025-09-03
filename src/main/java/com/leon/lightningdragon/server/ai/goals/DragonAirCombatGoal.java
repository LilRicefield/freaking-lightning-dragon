package com.leon.lightningdragon.server.ai.goals;

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

        // Main air combat logic - execute attack patterns
        if (dragon.stateManager.isFlying()) {
            executeAirCombat(target);
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

    private void executeCircleStrafe(LivingEntity target) {
        dragon.stateManager.setHovering(false);
        
        double distance = dragon.distanceTo(target);
        
        // Use Leon's proper circle calculation with dynamic radius
        float radius = (float) Math.max(15.0, distance * 0.8); // Maintain good distance
    float speed = 0.08f; // Circle speed
        boolean clockwise = dragon.getRandom().nextBoolean(); // Random direction per engagement
        
        // Get circle position using Leon's utility
        Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, clockwise, attackTimer, 0);
        attackTarget = circlePos.add(0, 8, 0); // Stay 8 blocks above target
        
        dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.3);
        
        // Alternate between beam and bite attacks during circle strafe
        if (distance <= 22.0 && attackCooldown <= 0 && (attackTimer % 40) == 0) {
            if (dragon.combatManager.canUseAbility()) {
                // Use beam for distant attacks, bite for close encounters
                if (distance > 12.0) {
                    dragon.combatManager.tryUseAbility(com.leon.lightningdragon.common.registry.ModAbilities.LIGHTNING_BEAM);
                    attackCooldown = 50; // Beam cooldown
                } else {
                    dragon.combatManager.tryUseAbility(com.leon.lightningdragon.common.registry.ModAbilities.BITE);
                    attackCooldown = 35; // Bite cooldown
                }
            }
        }
    }
    
    private void executeDiveBomb(LivingEntity target) {
        dragon.stateManager.setHovering(false);
        
        Vec3 targetPos = target.position();
        double distance = dragon.distanceTo(target);
        
        if (attackTimer < 20) {
            // Position high above target for dive
            attackTarget = targetPos.add(0, 20, 0);
            dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.0);
        } else if (attackTimer < 60) {
            // Execute dive bomb - use Leon's smooth flight vector calculation  
            Vec3 targetVelocity = target.getDeltaMovement();
            Vec3 predictedPos = targetPos.add(targetVelocity.scale(10)); // Predict 0.5 seconds ahead
            
            // Use Leon's smooth flight calculation for the dive
            Vec3 flightVector = DragonMathUtil.calculateFlightVector(dragon, predictedPos, 1.8, 0.3);
            attackTarget = dragon.position().add(flightVector);
            
            dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.8);
            
            // Attack during dive
            if (distance <= 8.0 && attackCooldown <= 0) {
                if (dragon.combatManager.canUseAbility()) {
                    dragon.combatManager.tryUseAbility(com.leon.lightningdragon.common.registry.ModAbilities.BITE);
                    attackCooldown = 80; // Longer cooldown after powerful dive
                }
            }
        } else {
            // Pull up and reposition
            attackTarget = targetPos.add(0, 15, 0);
            dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.4);
            
            // Reset for next attack pattern
            if (attackTimer > 100) {
                currentAttackMode = null; // Force mode selection next tick
            }
        }
    }
    
    private void executeRangedCircle(LivingEntity target) {
        // Keep dragon in flying state, not static hover
        dragon.stateManager.setHovering(false);
        
        Vec3 targetPos = target.position();
        double distance = dragon.distanceTo(target);
        
        // Circle at controlled range instead of static hovering - much more dynamic  
        float radius = (float) Math.min(30.0, Math.max(20.0, distance * 0.7)); // Controlled circle size
        float speed = 0.05f; // Slower, methodical circle for beam accuracy
        boolean clockwise = (attackTimer / 200) % 2 == 0; // Switch direction every 10 seconds
        
        Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, clockwise, attackTimer, 0);
        attackTarget = circlePos.add(0, 12, 0); // High altitude for ranged superiority
        
        dragon.getMoveControl().setWantedPosition(attackTarget.x, attackTarget.y, attackTarget.z, 1.0);
        
        // Use lightning beam for long-range attacks - increased range for wide circles
        if (distance <= 50.0 && attackCooldown <= 0) {
            if (dragon.combatManager.canUseAbility()) {
                dragon.combatManager.tryUseAbility(com.leon.lightningdragon.common.registry.ModAbilities.LIGHTNING_BEAM);
                attackCooldown = 60; // Longer cooldown for powerful beam attacks
            }
        }
    }

    private void executeAirCombat(LivingEntity target) {
        double distance = dragon.distanceTo(target);
        
        // Choose attack mode if none set or switch cooldown expired
        if (currentAttackMode == null || (modeSwitchCooldown <= 0 && attackTimer > 100)) {
            currentAttackMode = chooseAttackMode(target, distance);
            attackTimer = 0;
            modeSwitchCooldown = 80; // Don't switch modes too frequently
        }
        
        // Execute current attack pattern
        switch (currentAttackMode) {
            case STRAFE_RUN -> executeStrafeRun(target);
            case CIRCLE_STRAFE -> executeCircleStrafe(target);
            case DIVE_BOMB -> executeDiveBomb(target);
            case RANGED_CIRCLE -> executeRangedCircle(target);
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
        if (distance > 15.0 && distance <= 30.0) {
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
        currentAttackMode = null;
        attackTimer = 0;
        attackCooldown = 0;
        dragon.forceEndActiveAbility();
    }

    private void executeHoldingPattern(LivingEntity target) {
        // Dynamic holding pattern during takeoff - circle instead of static hover
        dragon.stateManager.setHovering(false);
        
        float radius = 20.0f; // Medium circle during takeoff
        float speed = 0.06f; // Moderate speed while positioning
        boolean clockwise = true; // Consistent direction during takeoff
        
        Vec3 circlePos = DragonMathUtil.circleEntityPosition(target, radius, speed, clockwise, attackTimer, 0);
        Vec3 holdPosition = circlePos.add(0, 12, 0); // Circle above target
        
        dragon.getMoveControl().setWantedPosition(holdPosition.x, holdPosition.y, holdPosition.z, 0.8);

        // Don't attack during takeoff - just position
    }

    // Public getter for debugging/other systems
    public AirAttackMode getCurrentAttackMode() {
        return currentAttackMode;
    }
}