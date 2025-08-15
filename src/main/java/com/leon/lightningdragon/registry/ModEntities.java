package com.leon.lightningdragon.registry;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.entity.projectile.LightningBallEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LightningDragonMod.MOD_ID);

    public static final RegistryObject<EntityType<LightningDragonEntity>> LIGHTNING_DRAGON =
            REGISTER.register("lightning_dragon", () -> EntityType.Builder.of(LightningDragonEntity::new, MobCategory.CREATURE)
                    .sized(2.5F, 2.0F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("lightning_dragon"));

    public static final RegistryObject<EntityType<LightningBallEntity>> LIGHTNING_BALL =
            REGISTER.register("lightning_ball", () -> EntityType.Builder.<LightningBallEntity>of(LightningBallEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("lightning_ball"));
}