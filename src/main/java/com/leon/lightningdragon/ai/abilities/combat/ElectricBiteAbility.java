package com.leon.lightningdragon.ai.abilities.combat;

import com.leon.lightningdragon.ai.abilities.Ability;
import com.leon.lightningdragon.ai.abilities.AbilityType;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Electric Bite Ability - Melee attack with chain lightning
 */
public class ElectricBiteAbility extends Ability<LightningDragonEntity> {
    private static final int TOTAL_TIME = 20;

    public ElectricBiteAbility(AbilityType<LightningDragonEntity, ElectricBiteAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.ELECTRIC_BITE);
    }

    @Override
    public boolean tryAbility() {
        return getUser().getTarget() != null &&
                getUser().distanceToSqr(getUser().getTarget()) <= 16; // 4 block range
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);

        // Look at target
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
        }
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() == 10) { // Bite impact
            electricBite();
        }

        if (getTicksInUse() >= TOTAL_TIME) {
            this.isUsing = false;
        }
    }

    private void electricBite() {
        if (getUser().level().isClientSide) return;

        LivingEntity target = getUser().getTarget();
        if (target == null) return;

        // Play bite sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.0f, 1.5f);

        // Primary bite damage
        if (getUser().distanceToSqr(target) <= 16 &&
                target.hurt(getUser().damageSources().mobAttack(getUser()), 10.0f)) {

            // Chain lightning to nearby entities
            List<LivingEntity> chainTargets = getUser().level().getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(8.0),
                    entity -> entity != getUser() && entity != getUser().getOwner() &&
                            entity != target && entity.isAlive());

            int chains = Math.min(3, chainTargets.size());
            for (int i = 0; i < chains; i++) {
                LivingEntity chainTarget = chainTargets.get(i);

                // Chain damage (reduced)
                chainTarget.hurt(getUser().damageSources().indirectMagic(getUser(), getUser()), 5.0f);

                // Visual lightning chain
                createLightningChain(target.position().add(0, target.getBbHeight()/2, 0),
                        chainTarget.position().add(0, chainTarget.getBbHeight()/2, 0));

                // Stun effect
                chainTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
            }
        }
    }

    private void createLightningChain(Vec3 from, Vec3 to) {
        // Create visual lightning effect between two points
        Vec3 direction = to.subtract(from);
        int steps = (int) (direction.length() * 2);

        for (int i = 0; i <= steps; i++) {
            Vec3 pos = from.add(direction.scale((double) i / steps));

            // Add randomness for lightning effect
            pos = pos.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 0.5,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.5,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.5
            );

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
    }
}