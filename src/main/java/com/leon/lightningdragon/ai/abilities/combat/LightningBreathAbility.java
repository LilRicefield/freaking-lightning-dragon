package com.leon.lightningdragon.ai.abilities.combat;

import com.leon.lightningdragon.ai.abilities.Ability;
import com.leon.lightningdragon.ai.abilities.AbilityType;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.util.SimpleDragonAiming;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lightning Breath Ability - THE MAIN EVENT
 */
public class LightningBreathAbility extends Ability<LightningDragonEntity> {
    private static final int WINDUP_TIME = 20;  // 1 second
    private static final int BREATH_TIME = 40;  // 2 seconds
    private static final int RECOVERY_TIME = 20; // 1 second

    private int phase = 0; // 0 = windup, 1 = breathing, 2 = recovery
    private Vec3 targetPos;

    public LightningBreathAbility(AbilityType<LightningDragonEntity, LightningBreathAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.LIGHTNING_BREATH);
    }



    @Override
    public boolean tryAbility() {
        // If ridden by player, always allow (rider-controlled ability)
        if (getUser().getRidingPlayer() != null) {
            // For rider mode, aim where they're looking
            targetPos = getUser().getRidingPlayer().getEyePosition()
                    .add(getUser().getRidingPlayer().getLookAngle().scale(20));
            return true;
        }
        
        // Otherwise, require a target within range (AI-controlled)
        LivingEntity target = getUser().getTarget();
        if (target != null) {
            double distanceSquared = getUser().distanceToSqr(target);
            // PREFER closer range - up to 18 blocks
            if (distanceSquared <= 324) { // 18 blocks squared
                targetPos = target.getEyePosition();
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);
        if (getUser().isFlying()) {
            getUser().setHovering(true);
        }
        // Look at target
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
        }
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        // Keep looking at target throughout the ability
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
        }

        if (getTicksInUse() <= WINDUP_TIME) {
            // WINDUP PHASE
            phase = 0;
            if (getTicksInUse() == 10) {
                // Play charge sound at keyframe
                getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.0f, 2.0f);
            }

            // Charge particles
            if (getTicksInUse() % 3 == 0 && getUser().level().isClientSide) {
                createChargeParticles();
            }

        } else if (getTicksInUse() <= WINDUP_TIME + BREATH_TIME) {
            // BREATHING PHASE
            if (phase != 1) {
                phase = 1;
                // Start breathing - play thunder sound
                getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.0f);
            }

            // Continuous breath effect every 4 ticks during breathing phase
            if ((getTicksInUse() - WINDUP_TIME) % 4 == 0) {
                breathLightning();
            }

        } else if (getTicksInUse() <= WINDUP_TIME + BREATH_TIME + RECOVERY_TIME) {
            // RECOVERY PHASE
            phase = 2;

        } else {
            // End ability
            this.isUsing = false;
        }
    }

    private void createChargeParticles() {
        Vec3 headPos = getUser().getEyePosition();
        Vec3 lookDir = getUser().getLookAngle();
        Vec3 mouthPos = headPos.add(lookDir.scale(2.0));

        for (int i = 0; i < 5; i++) {
            double offsetX = (getUser().getRandom().nextDouble() - 0.5) * 2.0;
            double offsetY = (getUser().getRandom().nextDouble() - 0.5) * 2.0;
            double offsetZ = (getUser().getRandom().nextDouble() - 0.5) * 2.0;

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    mouthPos.x + offsetX, mouthPos.y + offsetY, mouthPos.z + offsetZ,
                    -offsetX * 0.1, -offsetY * 0.1, -offsetZ * 0.1);
        }
    }

    private void breathLightning() {
        if (getUser().level().isClientSide) return;

        LivingEntity target = getUser().getTarget();
        if (target == null) return;

        Vec3 spawnPos = getUser().getMouthPosition();

        // SIMPLE direction calculation
        Vec3 baseDirection = SimpleDragonAiming.getGeneralDirection(getUser());
        double distance = getUser().getTarget() != null ?
                getUser().distanceTo(getUser().getTarget()) : 25.0;

        // SMART spread based on distance
        float spread = SimpleDragonAiming.getSmartSpread(distance);

        // Make dragon look at target (simple)
        SimpleDragonAiming.updateDragonLook(getUser());

        // Create multiple lightning bolts in a cone around the AIM DIRECTION
        for (int i = 0; i < 3; i++) {
            Vec3 spreadDirection = baseDirection.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 0.2, // Reduced spread
                    (getUser().getRandom().nextDouble() - 0.5) * 0.15,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.2
            ).normalize();

            // Create lightning ball projectile
            com.leon.lightningdragon.entity.projectile.LightningBallEntity lightningBall =
                    new com.leon.lightningdragon.entity.projectile.LightningBallEntity(
                            com.leon.lightningdragon.registry.ModEntities.LIGHTNING_BALL.get(), getUser().level(), getUser());

            lightningBall.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

            // Adjust for gravity and distance like the old system
            double speed = Math.min(1.2, Math.max(0.8, distance / 25.0));
            double gravityCompensation = distance * com.leon.lightningdragon.entity.projectile.LightningBallEntity.GRAVITY * 0.5;

            lightningBall.shoot(
                    spreadDirection.x * speed,
                    spreadDirection.y * speed + gravityCompensation,
                    spreadDirection.z * speed,
                    1.0f,
                    0.1f
            );
            getUser().level().addFreshEntity(lightningBall);
        }
        // Direct beam damage still uses look direction (for close range)
        Vec3 lookDir = getUser().getLookAngle();
        List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                getUser().getBoundingBox().expandTowards(lookDir.scale(8)).inflate(2.0),
                entity -> entity != getUser() && entity != getUser().getOwner() &&
                        getUser().getSensing().hasLineOfSight(entity));

        for (LivingEntity entity : nearbyEntities) {
            if (entity.hurt(getUser().damageSources().indirectMagic(getUser(), getUser()), 4.0f)) {
                // Add electric effect
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
            }
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
        if (getUser().isFlying()) {
            getUser().setHovering(false);
        }
    }
}