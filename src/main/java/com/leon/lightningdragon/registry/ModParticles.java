package com.leon.lightningdragon.registry;

import com.leon.lightningdragon.LightningDragonMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = 
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, LightningDragonMod.MOD_ID);

    public static void register(IEventBus eventBus) {
        REGISTER.register(eventBus);
    }
}