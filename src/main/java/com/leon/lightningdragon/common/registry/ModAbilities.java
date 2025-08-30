package com.leon.lightningdragon.common.registry;

import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import com.leon.lightningdragon.server.entity.ability.abilities.LightningBiteAbility;

/**
 * Simple holder for dragon ability types.
 */
public final class ModAbilities {
    private ModAbilities() {}

    public static final DragonAbilityType<LightningDragonEntity, LightningBiteAbility> BITE =
            AbilityRegistry.register(new DragonAbilityType<>("bite", LightningBiteAbility::new));
}
