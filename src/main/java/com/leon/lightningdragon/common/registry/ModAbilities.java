package com.leon.lightningdragon.common.registry;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import com.leon.lightningdragon.server.entity.ability.abilities.LightningBiteAbility;
import com.leon.lightningdragon.server.entity.ability.abilities.HornGoreAbility;
import com.leon.lightningdragon.server.entity.ability.abilities.LightningBeamAbility;
import com.leon.lightningdragon.server.entity.ability.abilities.RoarAbility;
import com.leon.lightningdragon.server.entity.ability.abilities.HurtAbility;
import com.leon.lightningdragon.server.entity.ability.abilities.DieAbility;

/**
 * Simple holder for dragon ability types.
 */
public final class ModAbilities {
    private ModAbilities() {}

    public static final DragonAbilityType<LightningDragonEntity, LightningBiteAbility> BITE =
            AbilityRegistry.register(new DragonAbilityType<>("bite", LightningBiteAbility::new));

    public static final DragonAbilityType<LightningDragonEntity, HornGoreAbility> HORN_GORE =
            AbilityRegistry.register(new DragonAbilityType<>("horn_gore", HornGoreAbility::new));

    public static final DragonAbilityType<LightningDragonEntity, LightningBeamAbility> LIGHTNING_BEAM =
            AbilityRegistry.register(new DragonAbilityType<>("lightning_beam", LightningBeamAbility::new));

    public static final DragonAbilityType<LightningDragonEntity, RoarAbility> ROAR =
            AbilityRegistry.register(new DragonAbilityType<>("roar", RoarAbility::new));

    // Passive/triggered on damage; one-shot animation + sound
    public static final DragonAbilityType<LightningDragonEntity, HurtAbility> HURT =
            AbilityRegistry.register(new DragonAbilityType<>("hurt", HurtAbility::new));

    public static final DragonAbilityType<LightningDragonEntity, DieAbility> DIE =
            AbilityRegistry.register(new DragonAbilityType<>("die", DieAbility::new));
}
