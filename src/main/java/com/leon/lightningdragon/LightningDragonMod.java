package com.leon.lightningdragon;

import com.leon.lightningdragon.client.LightningDragonRenderer;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.registry.ModEntities;
import com.leon.lightningdragon.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.time.Instant;


@Mod(LightningDragonMod.MOD_ID)
public class LightningDragonMod {
    public static final String MOD_ID = "lightningdragon";
    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        var src = LightningDragonMod.class.getProtectionDomain().getCodeSource();
        LOGGER.warn("[LD] BOOT {} FROM {}", Instant.now(), src == null ? "<?>"
                : src.getLocation());
    }



    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public LightningDragonMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);

        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onRegisterRenderers);

        MinecraftForge.EVENT_BUS.addListener(this::onSpawnPlacements);

        LOGGER.warn("[LD] CONSTRUCT {}", Instant.now().toEpochMilli() % 100000);

    }

    private void onAttributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.LIGHTNING_DRAGON.get(), LightningDragonEntity.createAttributes().build());
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.LIGHTNING_DRAGON.get(), LightningDragonRenderer::new);
    }

    private void onSpawnPlacements(SpawnPlacementRegisterEvent e) {
        e.register(
                ModEntities.LIGHTNING_DRAGON.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LightningDragonEntity::canSpawnHere,
                SpawnPlacementRegisterEvent.Operation.AND
        );
    }
}