package com.leon.lightningdragon.registry;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LightningDragonMod.MOD_ID);

    public static final RegistryObject<EntityType<LightningDragonEntity>> LIGHTNING_DRAGON =
            REGISTER.register("lightning_dragon", () ->
                    EntityType.Builder.<LightningDragonEntity>of(LightningDragonEntity::new, MobCategory.CREATURE)
                            .sized(2.4f, 1.8f) // hitbox (keep modest for now)
                            .clientTrackingRange(80) // Increased for flying entities to prevent rubber-banding
                            .updateInterval(1) // Update every tick for smooth flight
                            .build(LightningDragonMod.MOD_ID + ":lightning_dragon")
            );
}