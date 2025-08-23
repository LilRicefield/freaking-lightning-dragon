package com.leon.lightningdragon.ai.abilities;

import net.minecraft.world.entity.LivingEntity;

/**
 * Ability type registry for creating abilities
 */
public class AbilityType<T extends LivingEntity, A extends Ability<T>> {
    private final String name;
    private final java.util.function.BiFunction<AbilityType<T, A>, T, A> constructor;

    public AbilityType(String name, java.util.function.BiFunction<AbilityType<T, A>, T, A> constructor) {
        this.name = name;
        this.constructor = constructor;
    }

    public String getName() {
        return name;
    }

    public A createAbility(T user) {
        return constructor.apply(this, user);
    }
}