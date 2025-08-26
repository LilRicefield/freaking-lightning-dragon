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
        // Enhanced trail particles like Naga's poison ball
        if (ticksInAir % 3 != 0) return; // Every 3rd tick for more consistent trail

        // Create multiple particle types for richer effect
        Vec3 motion = getDeltaMovement();
        
        // Electric sparks (primary trail)
        for (int i = 0; i < 2; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.4;
            double offsetY = (random.nextDouble() - 0.5) * 0.4;
            double offsetZ = (random.nextDouble() - 0.5) * 0.4;
            
            // Position particles slightly behind the ball
            double trailX = getX() - motion.x * 0.3 + offsetX;
            double trailY = getY() - motion.y * 0.3 + offsetY;
            double trailZ = getZ() - motion.z * 0.3 + offsetZ;

            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    trailX, trailY, trailZ,
                    offsetX * 0.1, offsetY * 0.1, offsetZ * 0.1);
        }
        
        // Add some soul fire flame particles for electric energy effect
        if (random.nextFloat() < 0.6f) {
            double offsetX = (random.nextDouble() - 0.5) * 0.2;
            double offsetY = (random.nextDouble() - 0.5) * 0.2;
            double offsetZ = (random.nextDouble() - 0.5) * 0.2;
            
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX() + offsetX, getY() + offsetY, getZ() + offsetZ,
                    0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!entity.isSpectator() && entity.isAlive() && entity.isPickable()) {
            Entity owner = this.getOwner();
            // Check if entity is a LivingEntity before casting
            if (owner instanceof LightningDragonEntity && entity instanceof LivingEntity) {
                return entity != owner && !((LightningDragonEntity) owner).isOwnedBy((LivingEntity) entity);
            }
            return entity != owner;
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

        // Enhanced damage system like Naga (direct hit vs explosion)
        float directDamage = 8.0f; // Higher direct hit damage
        DamageSource damageSource = this.damageSources().indirectMagic(this, getOwner());

        if (hitEntity.hurt(damageSource, directDamage) && hitEntity instanceof LivingEntity livingEntity) {
            // Apply stronger electric effects for direct hit
            livingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, true)); // Stronger paralysis
            livingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, false)); // Weakness from shock

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
        // Add impact sound
        level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE,
                0.8f, 1.5f + random.nextFloat() * 0.3f);

        // Enhanced area damage with different ranges like Naga
        // Inner blast (higher damage)
        List<LivingEntity> innerEntities = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(2.0),
                entity -> entity != getOwner() && canHitEntity(entity));

        for (LivingEntity entity : innerEntities) {
            float damage = 5.0f; // Higher inner damage
            DamageSource damageSource = this.damageSources().indirectMagic(this, getOwner());

            if (entity.hurt(damageSource, damage)) {
                // Strong electric effects
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, false, false));
            }
        }
        
        // Outer blast (lower damage)
        List<LivingEntity> outerEntities = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(4.0),
                entity -> entity != getOwner() && canHitEntity(entity) && !innerEntities.contains(entity));

        for (LivingEntity entity : outerEntities) {
            float damage = 3.0f; // Lower outer damage
            DamageSource damageSource = this.damageSources().indirectMagic(this, getOwner());

            if (entity.hurt(damageSource, damage)) {
                // Mild electric effects
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
            }
        }

        discard();
    }

    private void spawnExplosionParticles() {
        if (!level().isClientSide) return;

        // Enhanced explosion particles like Naga's poison ball
        float explodeSpeed = 2.5f;
        
        // Electric sparks (main explosion effect)
        for (int i = 0; i < 30; i++) {
            Vec3 particlePos = new Vec3(random.nextFloat() * 0.3, 0, 0);
            particlePos = particlePos.yRot((float) (random.nextFloat() * 2 * Math.PI));
            particlePos = particlePos.xRot((float) (random.nextFloat() * 2 * Math.PI));
            
            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    getX(), getY(), getZ(),
                    particlePos.x * explodeSpeed, particlePos.y * explodeSpeed, particlePos.z * explodeSpeed);
        }
        
        // Soul fire flames for energy discharge
        for (int i = 0; i < 20; i++) {
            Vec3 particlePos = new Vec3(random.nextFloat() * 0.25, 0, 0);
            particlePos = particlePos.yRot((float) (random.nextFloat() * 2 * Math.PI));
            particlePos = particlePos.xRot((float) (random.nextFloat() * 2 * Math.PI));
            
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX() + particlePos.x, getY() + particlePos.y, getZ() + particlePos.z,
                    particlePos.x * explodeSpeed * 0.8, particlePos.y * explodeSpeed * 0.8, particlePos.z * explodeSpeed * 0.8);
        }
        
        // Some explosion particles for impact
        for (int i = 0; i < 15; i++) {
            Vec3 particlePos = new Vec3(random.nextFloat() * 0.2, 0, 0);
            particlePos = particlePos.yRot((float) (random.nextFloat() * 2 * Math.PI));
            particlePos = particlePos.xRot((float) (random.nextFloat() * 2 * Math.PI));
            
            level().addParticle(ParticleTypes.EXPLOSION,
                    getX() + particlePos.x, getY() + particlePos.y, getZ() + particlePos.z,
                    particlePos.x * explodeSpeed * 0.5, particlePos.y * explodeSpeed * 0.5, particlePos.z * explodeSpeed * 0.5);
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