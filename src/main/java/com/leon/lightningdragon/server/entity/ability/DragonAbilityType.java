package com.leon.lightningdragon.server.entity.ability;

import net.minecraft.world.entity.LivingEntity;

/**
 * Factory pattern for creating dragon abilities
 */
public class DragonAbilityType<M extends LivingEntity, T extends DragonAbility<M>> implements Comparable<DragonAbilityType<M, T>> {
    private final IFactory<M, T> factory;
    private final String name;

    public DragonAbilityType(String name, IFactory<M, T> factoryIn) {
        this.factory = factoryIn;
        this.name = name;
    }

    public T makeInstance(LivingEntity user) {
        if (factory == null) {
            throw new IllegalStateException("Factory is null for ability type: " + name);
        }
        try {
            return factory.create(this, (M) user);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Invalid entity type for ability: " + name + ", expected compatible with " + user.getClass().getSimpleName(), e);
        }
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