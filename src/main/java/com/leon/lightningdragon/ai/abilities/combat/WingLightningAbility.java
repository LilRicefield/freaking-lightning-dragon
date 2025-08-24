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
 * Wing Lightning Ability - Close range electric wing attack
 */
public class WingLightningAbility extends Ability<LightningDragonEntity> {
    private static final int TOTAL_TIME = 25;

    public WingLightningAbility(AbilityType<LightningDragonEntity, WingLightningAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.WING_LIGHTNING);
    }

    @Override
    public boolean tryAbility() {
        // If ridden by player, allow when flying (rider-controlled ability)
        if (getUser().getRidingPlayer() != null) {
            return getUser().isFlying();
        }
        
        // For AI, require target within range
        return getUser().isFlying() && getUser().getTarget() != null &&
                getUser().distanceToSqr(getUser().getTarget()) <= 36; // 6 block range
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);
        getUser().setHovering(true);
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() == 12) { // Wing strike
            wingStrike();
        }

        if (getTicksInUse() >= TOTAL_TIME) {
            this.isUsing = false;
        }
    }

    private void wingStrike() {
        if (getUser().level().isClientSide) return;

        // Play wing flap sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.5f, 1.5f);

        // Create electric field around dragon
        List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                getUser().getBoundingBox().inflate(6.0, 3.0, 6.0),
                entity -> entity != getUser() && entity != getUser().getOwner());

        for (LivingEntity entity : nearbyEntities) {
            if (entity.hurt(getUser().damageSources().mobAttack(getUser()), 6.0f)) {
                // Strong knockback
                Vec3 knockback = entity.position().subtract(getUser().position()).normalize().scale(2.0);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, 0.8, knockback.z));

                // Paralysis effect
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, false));
            }
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
        getUser().setHovering(false);
    }
}