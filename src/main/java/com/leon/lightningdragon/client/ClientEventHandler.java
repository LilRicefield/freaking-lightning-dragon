package com.leon.lightningdragon.client;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.client.particle.LightningBurstParticle;
import com.leon.lightningdragon.network.DragonKeyPacket;
import com.leon.lightningdragon.network.NetworkHandler;
import com.leon.lightningdragon.registry.ModParticles;
import com.leon.lightningdragon.util.KeybindUsingMount;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handler for Lightning Dragon mod
 */
@Mod.EventBusSubscriber(modid = LightningDragonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        // Register Lightning Burst Particle
        event.registerSpriteSet(ModParticles.LIGHTNING_BURST.get(), LightningBurstParticle.Factory::new);
    }

}