package com.leon.lightningdragon.entity.projectile;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lightning ball projectile shot by Lightning Dragon
 */
public class LightningBallEntity extends Projectile {
    private static final byte EXPLOSION_PARTICLES_ID = 42;

    public static float GRAVITY = 0.03f; // Less gravity than poison ball - lightning is energy!

    private LivingEntity shooter;
    private int ticksInAir = 0;
    private boolean hasHitTarget = false;

    public LightningBallEntity(EntityType<? extends LightningBallEntity> type, Level level) {
        super(type, level);
    }

    public LightningBallEntity(EntityType<? extends LightningBallEntity> type, Level level, LivingEntity shooter) {
        super(type, level);
        this.shooter = shooter;
        this.setOwner(shooter);
    }

    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        setDeltaMovement(x * velocity, y * velocity, z * velocity);
    }

    @Override
    public void tick() {
        super.tick();
        ticksInAir++;

        // Apply gravity
        setDeltaMovement(getDeltaMovement().subtract(0, GRAVITY, 0));

        // Check for entity hits before moving
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
        }

        // Move the projectile
        move(MoverType.SELF, getDeltaMovement());

        // Update rotation based on velocity
        setYRot(-((float) Mth.atan2(getDeltaMovement().x, getDeltaMovement().z)) * (180F / (float)Math.PI));
        setXRot((float) (Mth.atan2(getDeltaMovement().y, getDeltaMovement().horizontalDistance()) * (180F / Math.PI)));

        // Check for block collision
        if (!level().noCollision(this, getBoundingBox().inflate(0.1))) {
            explode();
        }

        // Create trail particles
        if (level().isClientSide) {
            createTrailParticles();
        }

        // Despawn after 100 ticks (5 seconds)
        if (ticksInAir > 100) {
            discard();
        }
    }

    private void createTrailParticles() {
        // Electric sparks trail
        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.5;
            double offsetY = (random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (random.nextDouble() - 0.5) * 0.5;

            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    getX() + offsetX, getY() + offsetY, getZ() + offsetZ,
                    0, 0, 0);
        }

        // Blue flame particles for the core
        if (random.nextFloat() < 0.8f) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY(), getZ(),
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!entity.isSpectator() && entity.isAlive() && entity.isPickable()) {
            Entity owner = this.getOwner();
            return entity != owner && (!(owner instanceof LightningDragonEntity) || !((LightningDragonEntity) owner).isOwnedBy((LivingEntity) entity));
        }
        return false;
    }

    @Override
    protected void onHitEntity(net.minecraft.world.phys.EntityHitResult result) {
        if (hasHitTarget) return;
        hasHitTarget = true;

        Entity hitEntity = result.getEntity();

        if (hitEntity instanceof ItemEntity) {
            return; // Don't hit items
        }

        // Deal lightning damage
        float damage = 6.0f; // Base damage
        DamageSource damageSource = this.damageSources().indirectMagic(this, getOwner());

        if (hitEntity.hurt(damageSource, damage) && hitEntity instanceof LivingEntity livingEntity) {
            // Apply electric effects
            livingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true)); // Paralysis effect

            // Chain lightning to nearby entities
            chainLightning(livingEntity);
        }

        explode();
    }

    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult result) {
        explode();
    }

    private void chainLightning(LivingEntity primaryTarget) {
        if (level().isClientSide) return;

        // Find nearby entities to chain to
        List<LivingEntity> nearbyEntities = level().getEntitiesOfClass(LivingEntity.class,
                primaryTarget.getBoundingBox().inflate(8.0),
                entity -> entity != primaryTarget && entity != getOwner() && entity.isAlive());

        int chainCount = Math.min(3, nearbyEntities.size()); // Chain to max 3 entities

        for (int i = 0; i < chainCount; i++) {
            LivingEntity target = nearbyEntities.get(i);

            // Deal reduced chain damage
            float chainDamage = 3.0f;
            DamageSource chainDamageSource = this.damageSources().indirectMagic(this, getOwner());
            target.hurt(chainDamageSource, chainDamage);

            // Visual lightning effect between entities
            if (level().isClientSide) {
                createLightningChain(primaryTarget.position(), target.position());
            }
        }
    }

    private void createLightningChain(Vec3 from, Vec3 to) {
        // Create visual lightning chain effect
        Vec3 direction = to.subtract(from);
        int steps = (int) (direction.length() * 2);

        for (int i = 0; i <= steps; i++) {
            Vec3 pos = from.add(direction.scale((double) i / steps));

            // Add some randomness to make it look like real lightning
            pos = pos.add(
                    (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.5
            );

            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    private void explode() {
        this.level().broadcastEntityEvent(this, EXPLOSION_PARTICLES_ID);

        // Play thunder sound
        level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE,
                1.0f, 1.2f + random.nextFloat() * 0.4f);

        // Deal area damage
        List<LivingEntity> nearbyEntities = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(3.0),
                entity -> entity != getOwner() && canHitEntity(entity));

        for (LivingEntity entity : nearbyEntities) {
            float damage = 4.0f;
            DamageSource damageSource = this.damageSources().indirectMagic(this, getOwner());

            if (entity.hurt(damageSource, damage)) {
                // Brief stun effect
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false));
            }
        }

        discard();
    }

    private void spawnExplosionParticles() {
        if (!level().isClientSide) return;

        // Electric explosion particles
        for (int i = 0; i < 20; i++) {
            Vec3 particleVel = new Vec3(
                    (random.nextDouble() - 0.5) * 2.0,
                    (random.nextDouble() - 0.5) * 2.0,
                    (random.nextDouble() - 0.5) * 2.0
            ).normalize().scale(random.nextDouble() * 1.5);

            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    getX(), getY(), getZ(),
                    particleVel.x, particleVel.y, particleVel.z);
        }

        // Blue explosion effect
        for (int i = 0; i < 15; i++) {
            Vec3 particleVel = new Vec3(
                    (random.nextDouble() - 0.5) * 1.5,
                    (random.nextDouble() - 0.5) * 1.5,
                    (random.nextDouble() - 0.5) * 1.5
            );

            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY(), getZ(),
                    particleVel.x, particleVel.y, particleVel.z);
        }

        // Some regular explosion particles for impact
        for (int i = 0; i < 10; i++) {
            level().addParticle(ParticleTypes.EXPLOSION,
                    getX() + (random.nextDouble() - 0.5) * 2.0,
                    getY() + (random.nextDouble() - 0.5) * 2.0,
                    getZ() + (random.nextDouble() - 0.5) * 2.0,
                    0, 0, 0);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EXPLOSION_PARTICLES_ID) {
            spawnExplosionParticles();
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void defineSynchedData() {
        // No additional synced data needed for now
    }
}