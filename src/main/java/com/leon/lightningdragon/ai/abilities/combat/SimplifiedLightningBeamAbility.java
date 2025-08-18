package com.leon.lightningdragon.ai.abilities.combat;

import com.leon.lightningdragon.ai.abilities.Ability;
import com.leon.lightningdragon.ai.abilities.AbilityType;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FIXED Lightning Beam - No more duplicate data accessors!
 */
public class SimplifiedLightningBeamAbility extends Ability<LightningDragonEntity> {
    private static final int CHARGE_TIME = 30;
    private static final int BEAM_TIME = 80;
    private static final int RECOVERY_TIME = 20;

    private int phase = 0;
    private Vec3 beamTarget;
    private int burnProgress = 0;
    private Set<LivingEntity> recentlyDamaged = new HashSet<>();

    public SimplifiedLightningBeamAbility(AbilityType<LightningDragonEntity, SimplifiedLightningBeamAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
    }

    @Override
    public boolean tryAbility() {
        LivingEntity target = getUser().getTarget();
        return target != null && getUser().distanceToSqr(target) <= 2500;
    }

    @Override
    public void start() {
        super.start();
        phase = 0;
        burnProgress = 0;
        recentlyDamaged.clear(); // Fresh start

        getUser().setAttacking(true);
        if (getUser().isFlying()) {
            getUser().setHovering(true);
        }

        // Initial target
        LivingEntity target = getUser().getTarget();
        if (target != null) {
            beamTarget = target.getEyePosition();
        }

        // Start charge sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0f, 2.0f);

        if (!getUser().level().isClientSide) {
            System.out.println("⚡ LIGHTNING BEAM: Starting...");
        }
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() <= CHARGE_TIME) {
            tickCharging();
        } else if (getTicksInUse() <= CHARGE_TIME + BEAM_TIME) {
            if (phase != 1) {
                startBeam();
            }
            tickBeam();
        } else {
            this.isUsing = false;
        }
    }

    private void tickCharging() {
        phase = 0;

        // Look at target
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
            beamTarget = getUser().getTarget().getEyePosition();
        }

        // Charge particles
        if (getUser().level().isClientSide && getTicksInUse() % 3 == 0) {
            spawnChargeParticles();
        }
    }

    private void startBeam() {
        phase = 1;
        burnProgress = 0;

        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.5f, 1.0f);

        if (!getUser().level().isClientSide) {
            System.out.println("💀 LIGHTNING BEAM: Firing!");
        }
    }

    private void tickBeam() {
        burnProgress++;

        // Update target
        if (getUser().getTarget() != null) {
            beamTarget = getUser().getTarget().getEyePosition();
        }

        if (beamTarget != null) {
            // VANILLA LIGHTNING SPAM - way cooler than particles
            if (!getUser().level().isClientSide && getTicksInUse() % 8 == 0) {
                spawnLightningStrikes(beamTarget);
            }

            // Use the entity's lightning system for damage
            stimulateLightning(beamTarget.x, beamTarget.y, beamTarget.z);
        }

        // Beam sounds
        if (getTicksInUse() % 7 == 0) {
            getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5f, 1.5f);
        }
    }

    /**
     * Adapted from Ice & Fire's stimulateFire method but for lightning
     * Now properly uses the entity's data accessors instead of defining our own
     */
    private void stimulateLightning(double burnX, double burnY, double burnZ) {
        // Set lightning target for rendering - use entity's methods
        getUser().setHasLightningTarget(true);
        getUser().setLightningTargetVec((float)burnX, (float)burnY, (float)burnZ);

        Vec3 headPos = getHeadPosition();
        double d2 = burnX - headPos.x;
        double d3 = burnY - headPos.y;
        double d4 = burnZ - headPos.z;

        double distance = Math.max(2.5F * getUser().distanceToSqr(burnX, burnY, burnZ), 0);
        double conqueredDistance = burnProgress / 40D * distance;
        int increment = (int) Math.ceil(conqueredDistance / 100);

        for (int i = 0; i < conqueredDistance; i += increment) {
            double progressX = headPos.x + d2 * (i / (float) distance);
            double progressY = headPos.y + d3 * (i / (float) distance);
            double progressZ = headPos.z + d4 * (i / (float) distance);

            if (canPositionBeSeen(progressX, progressY, progressZ)) {
                // Damage entities at this position
                damageAtPosition(new Vec3(progressX, progressY, progressZ));
            } else {
                // Hit a block - break it and stop
                if (!getUser().level().isClientSide) {
                    HitResult result = getUser().level().clip(new ClipContext(
                            new Vec3(getUser().getX(), getUser().getY() + getUser().getEyeHeight(), getUser().getZ()),
                            new Vec3(progressX, progressY, progressZ), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, getUser()));

                    BlockPos pos = BlockPos.containing(result.getLocation());
                    if (getUser().level().getBlockState(pos).getDestroySpeed(getUser().level(), pos) >= 0) {
                        getUser().level().destroyBlock(pos, true);
                    }

                    getUser().setLightningTargetVec((float) result.getLocation().x,
                            (float) result.getLocation().y,
                            (float) result.getLocation().z);
                }
            }
        }

        if (burnProgress >= 40D && canPositionBeSeen(burnX, burnY, burnZ)) {
            double spawnX = burnX + (getUser().getRandom().nextFloat() * 3.0) - 1.5;
            double spawnY = burnY + (getUser().getRandom().nextFloat() * 3.0) - 1.5;
            double spawnZ = burnZ + (getUser().getRandom().nextFloat() * 3.0) - 1.5;

            getUser().setLightningTargetVec((float) spawnX, (float) spawnY, (float) spawnZ);

            // Final impact damage
            damageAtPosition(new Vec3(spawnX, spawnY, spawnZ));
        }
    }

    private void damageAtPosition(Vec3 pos) {
        AABB damageBox = new AABB(pos.subtract(2, 2, 2), pos.add(2, 2, 2));

        List<LivingEntity> entities = getUser().level().getEntitiesOfClass(LivingEntity.class, damageBox);

        for (LivingEntity entity : entities) {
            if (entity != getUser() && !getUser().isAlliedTo(entity) && entity.distanceToSqr(pos) <= 4.0) {

                // Damage cooldown - don't hit the same entity multiple times rapidly
                if (!recentlyDamaged.contains(entity)) {
                    if (entity.hurt(getUser().damageSources().indirectMagic(getUser(), getUser()), 4.0f)) {
                        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));

                        Vec3 knockback = entity.position().subtract(pos).normalize().scale(1.0);
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, 0.3, knockback.z));

                        // Add to recently damaged set
                        recentlyDamaged.add(entity);
                    }
                }
            }
        }
    }

    private boolean canPositionBeSeen(double x, double y, double z) {
        Vec3 start = new Vec3(getUser().getX(), getUser().getY() + getUser().getEyeHeight(), getUser().getZ());
        Vec3 end = new Vec3(x, y, z);

        HitResult result = getUser().level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, getUser()));

        return result.getType() == HitResult.Type.MISS;
    }

    private Vec3 getHeadPosition() {
        // Simple head position - just forward from dragon
        Vec3 eyePos = getUser().getEyePosition();
        Vec3 lookDir = getUser().getLookAngle();
        return eyePos.add(lookDir.scale(1.5));
    }

    private void spawnChargeParticles() {
        Vec3 headPos = getHeadPosition();

        for (int i = 0; i < 8; i++) {
            Vec3 offset = new Vec3(
                    (getUser().getRandom().nextDouble() - 0.5) * 3.0,
                    (getUser().getRandom().nextDouble() - 0.5) * 2.0,
                    (getUser().getRandom().nextDouble() - 0.5) * 3.0
            );

            Vec3 particlePos = headPos.add(offset);
            Vec3 particleVel = offset.scale(-0.2);

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    particlePos.x, particlePos.y, particlePos.z,
                    particleVel.x, particleVel.y, particleVel.z);
        }
    }

    private void spawnBeamParticles(Vec3 target) {
        Vec3 headPos = getHeadPosition();
        Vec3 direction = target.subtract(headPos);
        double distance = direction.length();
        Vec3 step = direction.normalize().scale(0.5); // Particle every 0.5 blocks

        for (double d = 0; d < distance; d += 0.5) {
            Vec3 particlePos = headPos.add(step.scale(d));

            // Add some randomness to make it look like real lightning
            particlePos = particlePos.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3
            );

            // Main beam particles
            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);

            // Some extra electric effects
            if (getUser().getRandom().nextFloat() < 0.3f) {
                getUser().level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        particlePos.x, particlePos.y, particlePos.z,
                        (getUser().getRandom().nextDouble() - 0.5) * 0.1,
                        (getUser().getRandom().nextDouble() - 0.5) * 0.1,
                        (getUser().getRandom().nextDouble() - 0.5) * 0.1);
            }
        }
    }

    private void spawnLightningStrikes(Vec3 target) {
        Vec3 headPos = getHeadPosition();
        Vec3 direction = target.subtract(headPos);
        double distance = direction.length();

        // Create lightning strikes along the beam path
        int numStrikes = Math.min(8, (int)(distance / 3.0)); // One strike every 3 blocks

        for (int i = 0; i < numStrikes; i++) {
            double progress = (double)i / numStrikes;
            Vec3 strikePos = headPos.add(direction.scale(progress));

            // Add some randomness to strike positions
            strikePos = strikePos.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 2.0,
                    0, // Don't randomize Y too much or strikes go underground
                    (getUser().getRandom().nextDouble() - 0.5) * 2.0
            );

            // Spawn the actual lightning bolt
            net.minecraft.world.entity.LightningBolt lightning =
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(getUser().level());

            if (lightning != null) {
                lightning.moveTo(strikePos.x, strikePos.y, strikePos.z);
                lightning.setVisualOnly(true); // Visual only - no fire/damage from the bolt itself
                getUser().level().addFreshEntity(lightning);
            }
        }

        // Final strike at the target location
        net.minecraft.world.entity.LightningBolt finalStrike =
                net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(getUser().level());

        if (finalStrike != null) {
            finalStrike.moveTo(target.x, target.y, target.z);
            finalStrike.setVisualOnly(true);
            getUser().level().addFreshEntity(finalStrike);
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
        if (getUser().isFlying()) {
            getUser().setHovering(false);
        }

        // Clear lightning target
        getUser().setHasLightningTarget(false);

        // Clear damage cooldown set
        recentlyDamaged.clear();

        if (!getUser().level().isClientSide) {
            System.out.println("✅ LIGHTNING BEAM: Complete!");
        }
    }

    @Override
    public boolean canCancelActiveAbility() {
        return phase == 0 && getTicksInUse() < 15;
    }
}