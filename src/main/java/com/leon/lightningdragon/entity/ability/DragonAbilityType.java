package com.leon.lightningdragon.entity.ability;

import net.minecraft.world.entity.LivingEntity;

/**
 * Factory pattern for creating dragon abilities
 * Inspired by Mowzie's Mobs ability system
 */
public class DragonAbilityType<M extends LivingEntity, T extends DragonAbility<M>> implements Comparable<DragonAbilityType<M, T>> {
    private final IFactory<M, T> factory;
    private final String name;

    public DragonAbilityType(String name, IFactory<M, T> factoryIn) {
        this.factory = factoryIn;
        this.name = name;
    }

    public T makeInstance(LivingEntity user) {
        return factory.create(this, (M) user);
    }

    public interface IFactory<M extends LivingEntity, T extends DragonAbility<M>> {
        T create(DragonAbilityType<M, T> abilityType, M user);
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(DragonAbilityType<M, T> o) {
        return this.getName().compareTo(o.getName());
    }
}