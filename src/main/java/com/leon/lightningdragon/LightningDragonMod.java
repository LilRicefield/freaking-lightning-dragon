package com.leon.lightningdragon;

import com.leon.lightningdragon.client.renderer.LightningDragonRenderer;
import com.leon.lightningdragon.client.renderer.LightningBallRenderer;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.network.NetworkHandler;
import com.leon.lightningdragon.registry.ModEntities;
import com.leon.lightningdragon.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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

        // Register deferred registers
        ModEntities.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);

        // Register event handlers
        modBus.addListener(this::onEntityAttributes);
        modBus.addListener(this::onRegisterRenderers);
        modBus.addListener(this::onBuildCreativeTabContents);

        // Register spawn placements on the Forge event bus
        MinecraftForge.EVENT_BUS.addListener(this::onSpawnPlacements);

        NetworkHandler.register();
    }

    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        // Register entity attributes
        event.put(ModEntities.LIGHTNING_DRAGON.get(), LightningDragonEntity.createAttributes().build());
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Register entity renderers - this is where your renderer gets used!
        event.registerEntityRenderer(ModEntities.LIGHTNING_DRAGON.get(), LightningDragonRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHTNING_BALL.get(), LightningBallRenderer::new);

        LOGGER.info("[LD] Registered entity renderers: Dragon and Lightning Ball");
    }

    private void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        // Add to spawn eggs tab
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.LIGHTNING_DRAGON_SPAWN_EGG);
        }
    }

    private void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        // Register spawn placement rules
        event.register(
                ModEntities.LIGHTNING_DRAGON.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LightningDragonEntity::canSpawnHere,
                SpawnPlacementRegisterEvent.Operation.AND
        );

        LOGGER.info("[LD] Registered spawn placements");
    }
}