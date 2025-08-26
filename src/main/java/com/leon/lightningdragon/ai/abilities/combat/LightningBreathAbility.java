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

    public LightningBreathAbility(AbilityType<LightningDragonEntity, LightningBreathAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.LIGHTNING_BREATH);
    }



    @Override
    public boolean tryAbility() {
        // If ridden by player, always allow (rider-controlled ability)
        Vec3 targetPos;
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
            // CLOSE-MEDIUM range only - 4 to 15 blocks (avoid overlap with beam ability)
            if (distanceSquared >= 16 && distanceSquared <= 225) { // 4-15 blocks squared
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
        Vec3 mouthPos = calculatePreciseSpawnPosition();
        
        // Create converging particles like Naga's charging animation
        int particleCount = Math.min(8, getTicksInUse()); // More particles as charge builds
        
        for (int i = 0; i < particleCount; i++) {
            // Create particles in a sphere around the mouth that move inward
            Vec3 particleStart = new Vec3(4.0, 0, 0);
            particleStart = particleStart.yRot((float) (getUser().getRandom().nextFloat() * 2 * Math.PI));
            particleStart = particleStart.xRot((float) (getUser().getRandom().nextFloat() * 2 * Math.PI));
            Vec3 startPos = mouthPos.add(particleStart);
            
            // Calculate velocity toward mouth (converging effect)
            Vec3 velocity = mouthPos.subtract(startPos).normalize().scale(0.15);
            
            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    startPos.x, startPos.y, startPos.z,
                    velocity.x, velocity.y, velocity.z);
        }
        
        // Add some soul fire particles for energy buildup
        for (int i = 0; i < 3; i++) {
            double offsetX = (getUser().getRandom().nextDouble() - 0.5) * 1.5;
            double offsetY = (getUser().getRandom().nextDouble() - 0.5) * 1.5;
            double offsetZ = (getUser().getRandom().nextDouble() - 0.5) * 1.5;

            getUser().level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    mouthPos.x + offsetX, mouthPos.y + offsetY, mouthPos.z + offsetZ,
                    -offsetX * 0.05, -offsetY * 0.05, -offsetZ * 0.05);
        }
        
        // Explosion effect at mouth when about to fire (like Naga)
        if (getTicksInUse() == WINDUP_TIME - 2) {
            for (int i = 0; i < 15; i++) {
                Vec3 particleVel = new Vec3(
                        (getUser().getRandom().nextDouble() - 0.5) * 1.5,
                        (getUser().getRandom().nextDouble() - 0.5) * 1.5,
                        (getUser().getRandom().nextDouble() - 0.5) * 1.5
                );
                
                getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        mouthPos.x, mouthPos.y, mouthPos.z,
                        particleVel.x, particleVel.y, particleVel.z);
            }
        }
    }

    private void breathLightning() {
        if (getUser().level().isClientSide) return;

        LivingEntity target = getUser().getTarget();
        if (target == null) return;

        // Calculate precise spawn position like Naga
        Vec3 spawnPos = calculatePreciseSpawnPosition();

        // Create multiple lightning bolts with proper physics (keep spamming!)
        for (int i = 0; i < 3; i++) {
            // Create lightning ball projectile
            com.leon.lightningdragon.entity.projectile.LightningBallEntity lightningBall =
                    new com.leon.lightningdragon.entity.projectile.LightningBallEntity(
                            com.leon.lightningdragon.registry.ModEntities.LIGHTNING_BALL.get(), getUser().level(), getUser());

            lightningBall.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

            // Use Naga-style ballistic physics calculation
            Vec3 targetPos = target.getEyePosition();
            float dy = (float)(spawnPos.y - targetPos.y);
            float dx = (float)(spawnPos.x - targetPos.x); 
            float dz = (float)(spawnPos.z - targetPos.z);
            float horizontalDist = (float)Math.sqrt(dx * dx + dz * dz);
            
            // Physics prediction like Naga - calculate optimal trajectory
            float timeGuess = (float)Math.sqrt(2 * Math.abs(dy) / com.leon.lightningdragon.entity.projectile.LightningBallEntity.GRAVITY);
            float speed = Math.min(horizontalDist / timeGuess, 1.2f);
            
            // Normalize horizontal direction
            Vec3 horizontalDir = new Vec3(-dx, 0, -dz).normalize();
            
            // Add spread for multiple projectiles
            Vec3 spreadDirection = horizontalDir.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3, // Slightly more spread for chaos
                    (getUser().getRandom().nextDouble() - 0.5) * 0.2,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3
            ).normalize();

            // Apply ballistic physics with proper Y velocity
            double yVelocity = 0.1;
            if (dy < 0) { // Target is below - add upward velocity
                yVelocity += Math.abs(dy) * 0.03;
            }

            lightningBall.shoot(
                    spreadDirection.x * speed,
                    yVelocity,
                    spreadDirection.z * speed,
                    1.0f,
                    0.05f // Reduced inaccuracy since we have proper physics
            );
            getUser().level().addFreshEntity(lightningBall);
        }
        
        // Keep the direct beam damage for close range
        performDirectBeamDamage();
    }

    private Vec3 calculatePreciseSpawnPosition() {
        // Calculate precise mouth position like Naga
        Vec3 basePos = getUser().getEyePosition();
        Vec3 lookDir = getUser().getLookAngle();
        
        // Offset forward from head (like Naga's mouth position)
        Vec3 mouthOffset = new Vec3(1.5, -0.5, 0); // Adjust based on your dragon model
        mouthOffset = mouthOffset.yRot((float)Math.toRadians(-getUser().getYRot() - 90));
        
        // Add head rotation offset
        Vec3 headOffset = new Vec3(0, 0, 1)
                .xRot((float)Math.toRadians(-getUser().getXRot()))
                .yRot((float)Math.toRadians(-getUser().yHeadRot));
        
        return basePos.add(mouthOffset).add(headOffset);
    }

    private void performDirectBeamDamage() {
        // Direct beam damage for very close range
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