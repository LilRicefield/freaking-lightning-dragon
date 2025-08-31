package com.leon.lightningdragon.server.entity.handler;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.common.registry.ModAbilities;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Routes player left-clicks to the dragon's bite ability while riding.
 * This ensures clicks on any entity (including the dragon itself) trigger the bite.
 */
@Mod.EventBusSubscriber(modid = LightningDragonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonAttackEventHandler {

    @SubscribeEvent
    public static void onPlayerAttackEntity(AttackEntityEvent event) {
        if (event == null || event.getEntity() == null) return;

        // Only handle on server; client won't have authoritative combat
        if (event.getEntity().level().isClientSide) return;

        var player = event.getEntity();
        if (!(player.getVehicle() instanceof LightningDragonEntity dragon)) return;
        if (!dragon.isTame() || !dragon.isOwnedBy(player)) return;

        // While ridden, trigger the dragon's bite ability instead of vanilla player attack
        dragon.combatManager.tryUseAbility(ModAbilities.BITE);

        // Cancel vanilla attack so player cooldown/damage doesn't interfere
        event.setCanceled(true);
    }
}