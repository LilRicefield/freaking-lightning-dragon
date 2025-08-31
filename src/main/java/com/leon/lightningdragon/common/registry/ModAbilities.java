package com.leon.lightningdragon.common.registry;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import com.leon.lightningdragon.server.entity.ability.abilities.LightningBiteAbility;
import com.leon.lightningdragon.server.entity.ability.abilities.HornGoreAbility;

/**
 * Simple holder for dragon ability types.
 */
public final class ModAbilities {
    private ModAbilities() {}

    public static final DragonAbilityType<LightningDragonEntity, LightningBiteAbility> BITE =
            AbilityRegistry.register(new DragonAbilityType<>("bite", LightningBiteAbility::new));

    public static final DragonAbilityType<LightningDragonEntity, HornGoreAbility> HORN_GORE =
            AbilityRegistry.register(new DragonAbilityType<>("horn_gore", HornGoreAbility::new));
}
