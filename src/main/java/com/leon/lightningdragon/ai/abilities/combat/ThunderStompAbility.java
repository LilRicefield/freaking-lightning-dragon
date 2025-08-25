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
 * Thunder Stomp Ability - Ground AOE attack
 */
public class ThunderStompAbility extends Ability<LightningDragonEntity> {
    private static final int TOTAL_TIME = 30;

    public ThunderStompAbility(AbilityType<LightningDragonEntity, ThunderStompAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.THUNDER_STOMP);
    }

    @Override
    public boolean tryAbility() {
        // If ridden by player, allow when on ground (rider-controlled ability)
        if (getUser().getRidingPlayer() != null) {
            return !getUser().isFlying() && getUser().onGround();
        }
        
        // For AI, also require targets nearby
        return !getUser().isFlying() && getUser().onGround() && getUser().getTarget() != null;
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() == 15) { // Stomp impact
            thunderStomp();
        }

        if (getTicksInUse() >= TOTAL_TIME) {
            this.isUsing = false;
        }
    }

    private void thunderStomp() {
        if (getUser().level().isClientSide) return;

        // Play thunder sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 0.8f);

        // Damage nearby entities
        List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                getUser().getBoundingBox().inflate(8.0),
                entity -> entity != getUser() && entity != getUser().getOwner());

        for (LivingEntity entity : nearbyEntities) {
            double distance = getUser().distanceTo(entity);
            float damage = (float) (8.0 - distance); // Damage falloff

            if (damage > 0 && entity.hurt(getUser().damageSources().mobAttack(getUser()), damage)) {
                // Knockback
                Vec3 knockback = entity.position().subtract(getUser().position()).normalize().scale(1.5);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, 0.5, knockback.z));

                // Electric effect
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
            }
        }

        // Create electric shockwave particles
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20;
            double radius = 6.0;
            double x = getUser().getX() + Math.cos(angle) * radius;
            double z = getUser().getZ() + Math.sin(angle) * radius;

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x, getUser().getY() + 0.1, z, 0, 0.2, 0);
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
    }
}