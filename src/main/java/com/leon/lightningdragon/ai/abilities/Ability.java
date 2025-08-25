package com.leon.lightningdragon.ai.abilities;

import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Base ability class for all dragon abilities
 */
public abstract class Ability<T extends LivingEntity> {
    protected final AbilityType<T, ?> abilityType;
    protected final T user;
    protected int ticksInUse = 0;
    protected boolean isUsing = false;
    protected RawAnimation animation;

    public Ability(AbilityType<T, ?> abilityType, T user) {
        this.abilityType = abilityType;
        this.user = user;
    }

    public abstract boolean tryAbility();

    public void start() {
        this.isUsing = true;
        this.ticksInUse = 0;
    }

    public void tick() {
        if (isUsing) {
            ticksInUse++;
            tickUsing();
        }
    }

    protected void tickUsing() {
        // Override in subclasses
    }

    public void end() {
        this.isUsing = false;
    }

    public boolean canCancelActiveAbility() {
        return true;
    }

    /**
     * Check if this ability should be finished.
     * Override in subclasses to implement custom finish conditions.
     */
    public boolean isFinished() {
        return false; // Default: abilities don't finish automatically
    }

    public AbilityType<T, ?> getAbilityType() {
        return abilityType;
    }

    public T getUser() {
        return user;
    }

    public int getTicksInUse() {
        return ticksInUse;
    }

    public boolean isUsing() {
        return isUsing;
    }

    public RawAnimation getAnimation() {
        return animation;
    }

    protected void setAnimation(RawAnimation animation) {
        this.animation = animation;
    }
    public <E extends GeoEntity> PlayState animationPredicate(AnimationState<E> state) {
        if (animation != null && isUsing) {
            state.setAndContinue(animation);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    /**
     * Play an animation on the user entity (if it's a dragon)
     */
    protected void playAnimation(RawAnimation animation) {
        if (user instanceof com.leon.lightningdragon.entity.base.DragonEntity dragon) {
            dragon.playAnimation(animation);
        }
        setAnimation(animation);
    }

    /**
     * Trigger a different animation during ability execution (useful for multi-phase abilities)
     */
    protected void triggerAnimation(RawAnimation animation) {
        if (user instanceof com.leon.lightningdragon.entity.base.DragonEntity dragon) {
            dragon.playAnimation(animation);
        }
    }
}