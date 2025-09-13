package com.leon.lightningdragon.client.particle;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.common.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LightningDragonMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ParticleClientRegistry {
    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.LIGHTNING_STORM.get(), LightningStormParticle.Factory::new);
    }
}
