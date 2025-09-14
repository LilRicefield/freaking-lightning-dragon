package com.leon.lightningdragon.common.registry;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.common.particle.LightningStormData;
import com.leon.lightningdragon.common.particle.LightningArcData;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, LightningDragonMod.MOD_ID);

    public static final RegistryObject<ParticleType<LightningStormData>> LIGHTNING_STORM =
            REGISTER.register("lightning_storm",
                    () -> new ParticleType<LightningStormData>(false, LightningStormData.DESERIALIZER) {
                        @Override
                        public com.mojang.serialization.Codec<LightningStormData> codec() {
                            return LightningStormData.CODEC(this);
                        }
                    });

    public static final RegistryObject<ParticleType<LightningArcData>> LIGHTNING_ARC =
            REGISTER.register("lightning_arc",
                    () -> new ParticleType<LightningArcData>(false, LightningArcData.DESERIALIZER) {
                        @Override
                        public com.mojang.serialization.Codec<LightningArcData> codec() {
                            return LightningArcData.CODEC(this);
                        }
                    });
}
