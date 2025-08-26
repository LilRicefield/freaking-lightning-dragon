package com.leon.lightningdragon.ai.abilities;

import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Simple hurt reaction ability
 */
public class HurtAbility<T extends LivingEntity> extends Ability<T> {
    private final int duration;
    private final int iframes;

    public HurtAbility(AbilityType<T, ?> abilityType, T user, RawAnimation animation, int duration, int iframes) {
        super(abilityType, user);
        setAnimation(animation);
        this.duration = duration;
        this.iframes = iframes;
    }

    @Override
    public boolean tryAbility() {
        return true;
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() >= duration) {
            this.isUsing = false;
        }
    }

    @Override
    public boolean canCancelActiveAbility() {
        return false; // Hurt cannot be cancelled
    }
}