package com.leon.lightningdragon.ai.goals;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Combat AI for Lightning Dragon - NOW WITH PROPER GROUND CHASING
 */
public class DragonCombatGoal extends Goal {
    private final LightningDragonEntity dragon;
    private int attackCooldown = 0;
    private int abilityChoiceCooldown = 0;

    public DragonCombatGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return dragon.getTarget() != null &&
                dragon.getTarget().isAlive() &&
                dragon.distanceToSqr(dragon.getTarget()) < 625; // 25 block range
    }

    @Override
    public boolean canContinueToUse() {
        return dragon.getTarget() != null &&
                dragon.getTarget().isAlive() &&
                dragon.distanceToSqr(dragon.getTarget()) < 900; // 30 block range
    }

    @Override
    public void start() {
        LivingEntity target = dragon.getTarget();
        if (target != null) {
            double distance = dragon.distanceTo(target);

            // Only force flying for very long range combat (20+ blocks)
            if (distance > 20.0 && !dragon.isFlying()) {
                dragon.setFlying(true);
                dragon.setTakeoff(true);
            }
        }

        attackCooldown = 0;
        abilityChoiceCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = dragon.getTarget();
        if (target == null) return;

        // Look at target
        dragon.getLookControl().setLookAt(target, 30f, 30f);

        // Reduce cooldowns
        if (attackCooldown > 0) attackCooldown--;
        if (abilityChoiceCooldown > 0) abilityChoiceCooldown--;

        // THE MISSING PIECE: Actually move towards the target!
        handleMovement(target);

        // Only choose abilities when we can actually use them
        if (dragon.canUseAbility() && attackCooldown <= 0 && abilityChoiceCooldown <= 0) {
            chooseAndExecuteAbility(target);
        }

        // Hover when attacking (but only if flying)
        if (dragon.isFlying() && dragon.getActiveAbility() != null) {
            dragon.setHovering(true);
        } else {
            dragon.setHovering(false);
        }
    }

    /**
     * THE FIX: Handle movement towards target based on flight state
     */
    private void handleMovement(LivingEntity target) {
        double distance = dragon.distanceTo(target);

        if (dragon.isFlying()) {
            // Flying movement - use move control for positioning
            if (distance > 8.0) { // Stay at medium range when flying
                dragon.getMoveControl().setWantedPosition(
                        target.getX(),
                        target.getY() + 6, // Fly above target
                        target.getZ(),
                        1.2 // Speed
                );
            }
        } else {
            // GROUND MOVEMENT - this was missing!
            if (distance > 2.5) { // Chase if not in melee range
                // Set running if far away
                if (distance <= 12.0) {
                    Vec3 targetPos = target.position();
                    Vec3 dragonPos = dragon.position();
                    Vec3 direction = targetPos.subtract(dragonPos).normalize();

                    boolean shouldRun = distance > 6.0;
                    dragon.setRunning(shouldRun);

                    double speed = shouldRun ? 0.3 : 0.2;
                    Vec3 movement = direction.scale(speed);
                    dragon.setDeltaMovement(dragon.getDeltaMovement().add(movement.x, 0, movement.z));

                    // Make sure body rotation follows movement
                    float moveYaw = (float)(Math.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F;
                    dragon.yBodyRot = Mth.approachDegrees(dragon.yBodyRot, moveYaw, 10.0F);

                    if (!dragon.level().isClientSide) {
                        System.out.println("Dragon direct chase - Distance: " + String.format("%.1f", distance) + ", Running: " + shouldRun);
                    }
                } else {
                    dragon.setRunning(true);
                    dragon.getNavigation().moveTo(target, 1.8); // Normal speed
                }

                if (!dragon.level().isClientSide) {
                    System.out.println("Dragon chasing on ground - Distance: " + String.format("%.1f", distance) + ", Running: " + dragon.isRunning());
                }
            } else {
                // Close enough - stop moving
                dragon.getNavigation().stop();
                dragon.setRunning(false);
                dragon.setDeltaMovement(dragon.getDeltaMovement().multiply(0.8, 1.0, 0.8)); // Slow down gradually
            }
        }

        // Dynamic flight decisions
        if (distance > 15.0 && !dragon.isFlying() && dragon.getRandom().nextFloat() < 0.3f) {
            // Far target - consider taking off
            dragon.setFlying(true);
            dragon.setTakeoff(true);
            if (!dragon.level().isClientSide) {
                System.out.println("Dragon taking off - target too far");
            }
        } else if (distance <= 10.0 && dragon.isFlying() && dragon.getRandom().nextFloat() < 0.4f) {
            // Close target - consider landing for ground combat
            dragon.setLanding(true);
            if (!dragon.level().isClientSide) {
                System.out.println("Dragon landing for close combat");
            }
        }
    }

    private void chooseAndExecuteAbility(LivingEntity target) {
        double distance = dragon.distanceTo(target);
        boolean flying = dragon.isFlying();

        // Debug output
        if (!dragon.level().isClientSide) {
            System.out.println("Dragon Combat - Distance: " + String.format("%.1f", distance) +
                    ", Flying: " + flying + ", Target: " + target.getName().getString());
        }

        boolean abilityUsed = false;

        // Close range (0-6 blocks) - prioritize melee attacks
        if (distance <= 6.0) {
            if (!flying && distance <= 4.0) {
                // Electric bite for ground combat
                dragon.sendAbilityMessage(LightningDragonEntity.ELECTRIC_BITE_ABILITY);
                attackCooldown = 30;
                abilityChoiceCooldown = 15;
                abilityUsed = true;
                if (!dragon.level().isClientSide) System.out.println("→ Used ELECTRIC_BITE");
            } else if (flying) {
                // Wing lightning for aerial combat
                dragon.sendAbilityMessage(LightningDragonEntity.WING_LIGHTNING_ABILITY);
                attackCooldown = 40;
                abilityChoiceCooldown = 20;
                abilityUsed = true;
                if (!dragon.level().isClientSide) System.out.println("→ Used WING_LIGHTNING");
            }
        }

        // Medium range (6-15 blocks)
        if (!abilityUsed && distance <= 15.0) {
            if (!flying && dragon.onGround()) {
                // Thunder stomp for ground AOE
                dragon.sendAbilityMessage(LightningDragonEntity.THUNDER_STOMP_ABILITY);
                attackCooldown = 60;
                abilityChoiceCooldown = 30;
                abilityUsed = true;
                if (!dragon.level().isClientSide) System.out.println("→ Used THUNDER_STOMP");
            } else {
                // Lightning breath for aerial attacks
                dragon.sendAbilityMessage(LightningDragonEntity.LIGHTNING_BREATH_ABILITY);
                attackCooldown = 50;
                abilityChoiceCooldown = 25;
                abilityUsed = true;
                if (!dragon.level().isClientSide) System.out.println("→ Used LIGHTNING_BREATH (medium)");
            }
        }

        // Long range (15+ blocks) - always lightning breath
        if (!abilityUsed) {
            dragon.sendAbilityMessage(LightningDragonEntity.LIGHTNING_BREATH_ABILITY);
            attackCooldown = 80;
            abilityChoiceCooldown = 40;
            abilityUsed = true;
            if (!dragon.level().isClientSide) System.out.println("→ Used LIGHTNING_BREATH (long)");
        }

        // If nothing worked, shorter cooldown to try again sooner
        if (!abilityUsed) {
            attackCooldown = 20;
            abilityChoiceCooldown = 10;
            if (!dragon.level().isClientSide) System.out.println("→ No ability used, trying again soon");
        }
    }

    @Override
    public void stop() {
        dragon.setHovering(false);
        dragon.setRunning(false); // Stop running when combat ends
        dragon.getNavigation().stop(); // Stop all movement
        attackCooldown = 0;
        abilityChoiceCooldown = 0;

        if (!dragon.level().isClientSide) {
            System.out.println("Dragon combat ended");
        }
    }
}