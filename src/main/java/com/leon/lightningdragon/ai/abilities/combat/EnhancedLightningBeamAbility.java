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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enhanced Lightning Beam - Replaces vanilla lightning bolts with continuous beam effect
 * Creates a proper lightning stream similar to Ice & Fire but more sophisticated
 */
public class EnhancedLightningBeamAbility extends Ability<LightningDragonEntity> {
    private static final int CHARGE_TIME = 20;
    private static final int BEAM_TIME = 40;
    private static final int TOTAL_TIME = CHARGE_TIME + BEAM_TIME;
    private static final double MAX_RANGE = 32.0;

    private Vec3 beamTarget;
    private int phase = 0; // 0=charge, 1=beam
    private int burnProgress = 0;
    private Set<LivingEntity> recentlyDamaged = new HashSet<>();
    private Vec3 lastBeamEnd;

    public EnhancedLightningBeamAbility(AbilityType<LightningDragonEntity, EnhancedLightningBeamAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.LIGHTNING_BREATH);
    }

    @Override
    public boolean tryAbility() {
        return getUser().getTarget() != null &&
                getUser().distanceToSqr(getUser().getTarget()) >= 256 && // 16+ blocks
                getUser().distanceToSqr(getUser().getTarget()) <= 1024;  // up to 32 blocks
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);

        if (getUser().isFlying()) {
            getUser().setHovering(true);
        }

        // Set initial target
        if (getUser().getTarget() != null) {
            beamTarget = getUser().getTarget().getEyePosition();
        }

        burnProgress = 0;
        phase = 0;
        recentlyDamaged.clear();

        // Charging sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0f, 0.8f);
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        // Always look at target during beam
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
        }

        if (getTicksInUse() < CHARGE_TIME) {
            tickCharging();
        } else {
            if (phase == 0) {
                phase = 1;
                // Beam start sound
                getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0f, 1.2f);
            }
            tickBeam();
        }

        if (getTicksInUse() >= TOTAL_TIME) {
            this.isUsing = false;
        }
    }

    private void tickCharging() {
        // Update target position
        if (getUser().getTarget() != null) {
            beamTarget = getUser().getTarget().getEyePosition();
        }

        // Charging particles
        if (getUser().level().isClientSide) {
            Vec3 headPos = getHeadPosition();
            for (int i = 0; i < 3; i++) {
                getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        headPos.x + (getUser().getRandom().nextDouble() - 0.5) * 1.5,
                        headPos.y + (getUser().getRandom().nextDouble() - 0.5) * 1.5,
                        headPos.z + (getUser().getRandom().nextDouble() - 0.5) * 1.5,
                        0, 0, 0);
            }
        }

        // Charging sound buildup
        if (getTicksInUse() % 8 == 0) {
            float pitch = 0.8f + (getTicksInUse() / (float)CHARGE_TIME) * 0.6f;
            getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.5f, pitch);
        }
    }

    private void tickBeam() {
        burnProgress++;

        // Update target position gradually for smooth tracking
        if (getUser().getTarget() != null) {
            Vec3 newTarget = getUser().getTarget().getEyePosition();
            if (beamTarget != null) {
                // Smooth interpolation for realistic beam tracking
                beamTarget = beamTarget.lerp(newTarget, 0.1);
            } else {
                beamTarget = newTarget;
            }
        }

        if (beamTarget != null) {
            // Set rendering target for your existing system
            getUser().setHasLightningTarget(true);
            getUser().setLightningTargetVec((float)beamTarget.x, (float)beamTarget.y, (float)beamTarget.z);

            // Create the enhanced beam effect
            createLightningBeam(beamTarget);

            // Handle damage
            if (!getUser().level().isClientSide) {
                damageEntitiesInBeam(beamTarget);
            }
        }

        // Continuous beam sounds
        if (getTicksInUse() % 6 == 0) {
            getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.0f, 1.8f);
        }
    }

    /**
     * Creates a sophisticated continuous lightning beam effect
     * Much better than spamming vanilla lightning bolts
     */
    private void createLightningBeam(Vec3 target) {
        Vec3 headPos = getHeadPosition();
        Vec3 direction = target.subtract(headPos);
        double distance = direction.length();

        if (distance < 0.5) return;

        direction = direction.normalize();

        // Create main beam with multiple branches
        createMainBeam(headPos, target, direction, distance);

        // Create side branches for more realistic lightning
        createBeamBranches(headPos, direction, distance);

        // Store last beam end for lingering effects
        lastBeamEnd = target;
    }

    private void createMainBeam(Vec3 start, Vec3 end, Vec3 direction, double distance) {
        int steps = (int)(distance * 4); // Higher density for smoother beam

        for (int i = 0; i <= steps; i++) {
            double progress = (double)i / steps;
            Vec3 beamPos = start.add(direction.scale(progress * distance));

            // Add natural lightning zigzag pattern
            double zigzagAmplitude = 0.4 * Math.sin(progress * Math.PI * 8);
            Vec3 perpendicular = direction.cross(new Vec3(0, 1, 0)).normalize();
            beamPos = beamPos.add(perpendicular.scale(zigzagAmplitude));

            // Add random variations for organic look
            beamPos = beamPos.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3
            );

            // Main beam particles - bright and dense
            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    beamPos.x, beamPos.y, beamPos.z,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.05,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.05,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.05);

            // Add extra intensity particles every few steps
            if (i % 3 == 0) {
                for (int j = 0; j < 2; j++) {
                    getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                            beamPos.x + (getUser().getRandom().nextDouble() - 0.5) * 0.2,
                            beamPos.y + (getUser().getRandom().nextDouble() - 0.5) * 0.2,
                            beamPos.z + (getUser().getRandom().nextDouble() - 0.5) * 0.2,
                            0, 0, 0);
                }
            }
        }
    }

    private void createBeamBranches(Vec3 start, Vec3 mainDirection, double mainDistance) {
        // Create 2-3 branch points along the main beam
        int numBranches = 2 + getUser().getRandom().nextInt(2);

        for (int branch = 0; branch < numBranches; branch++) {
            double branchPoint = 0.3 + getUser().getRandom().nextDouble() * 0.4; // Branch 30-70% along beam
            Vec3 branchStart = start.add(mainDirection.scale(branchPoint * mainDistance));

            // Create random branch direction
            Vec3 branchDir = new Vec3(
                    (getUser().getRandom().nextDouble() - 0.5) * 2,
                    (getUser().getRandom().nextDouble() - 0.5) * 1,
                    (getUser().getRandom().nextDouble() - 0.5) * 2
            ).normalize();

            double branchLength = 3 + getUser().getRandom().nextDouble() * 4;
            int branchSteps = (int)(branchLength * 2);

            // Create the branch
            for (int i = 0; i <= branchSteps; i++) {
                double progress = (double)i / branchSteps;
                Vec3 branchPos = branchStart.add(branchDir.scale(progress * branchLength));

                // Branches fade out as they extend
                if (getUser().getRandom().nextDouble() < 0.7) {
                    getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                            branchPos.x, branchPos.y, branchPos.z, 0, 0, 0);
                }
            }
        }
    }

    /**
     * Damage entities along the beam path - more precise than vanilla lightning
     */
    private void damageEntitiesInBeam(Vec3 target) {
        Vec3 headPos = getHeadPosition();
        Vec3 direction = target.subtract(headPos).normalize();
        double distance = headPos.distanceTo(target);

        // Check for entities along beam path
        int segments = Math.max(1, (int)(distance / 2.0)); // Check every 2 blocks

        for (int i = 0; i <= segments; i++) {
            double progress = (double)i / segments;
            Vec3 checkPos = headPos.add(direction.scale(progress * distance));

            List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(checkPos.subtract(1.5, 1.5, 1.5), checkPos.add(1.5, 1.5, 1.5)),
                    entity -> entity != getUser() && entity != getUser().getOwner() &&
                            !recentlyDamaged.contains(entity));

            for (LivingEntity entity : nearbyEntities) {
                // Damage with intensity based on distance from beam center
                double distFromBeam = entity.position().distanceTo(checkPos);
                float damage = (float)(8.0 * Math.max(0.1, 1.0 - distFromBeam / 1.5));

                if (entity.hurt(getUser().damageSources().mobAttack(getUser()), damage)) {
                    // Electric effects
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));

                    // Knockback along beam direction
                    Vec3 knockback = direction.scale(0.5);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                    // Prevent multiple hits this tick
                    recentlyDamaged.add(entity);

                    // Create hit effect
                    createHitEffect(entity.position());
                }
            }
        }

        // Clear damage set periodically to allow re-hitting
        if (getTicksInUse() % 10 == 0) {
            recentlyDamaged.clear();
        }
    }

    private void createHitEffect(Vec3 pos) {
        // Explosion of electric particles when beam hits something
        for (int i = 0; i < 8; i++) {
            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y + 1, pos.z,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3,
                    getUser().getRandom().nextDouble() * 0.3,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.3);
        }
    }

    private Vec3 getHeadPosition() {
        // Get position of dragon's head for beam origin
        Vec3 basePos = getUser().position().add(0, getUser().getBbHeight() * 0.8, 0);
        Vec3 lookVec = getUser().getLookAngle();
        return basePos.add(lookVec.scale(1.5)); // Slightly forward from head
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
        recentlyDamaged.clear();

        // Final impact effect at beam end
        if (lastBeamEnd != null && !getUser().level().isClientSide) {
            createFinalImpact(lastBeamEnd);
        }
    }

    private void createFinalImpact(Vec3 pos) {
        // Create a final explosion effect where the beam was hitting
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20;
            double radius = 2.0;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x, pos.y, z, 0, 0.2, 0);
        }

        // Impact sound
        getUser().level().playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5f, 1.0f);
    }

    @Override
    public boolean canCancelActiveAbility() {
        return phase == 0 && getTicksInUse() < 15;
    }
}