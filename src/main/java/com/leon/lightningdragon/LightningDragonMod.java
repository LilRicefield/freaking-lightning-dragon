package com.leon.lightningdragon;

import com.leon.lightningdragon.client.renderer.LightningDragonRenderer;
import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import com.leon.lightningdragon.common.network.NetworkHandler;
import com.leon.lightningdragon.common.registry.ModEntities;
import com.leon.lightningdragon.common.registry.ModItems;
import com.leon.lightningdragon.common.registry.ModSounds;
import com.leon.lightningdragon.common.registry.ModParticles;
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
// FMLJavaModLoadingContext.get() is deprecated; the mod loader injects IEventBus into the constructor

@Mod(LightningDragonMod.MOD_ID)
public class LightningDragonMod {
    public static final String MOD_ID = "lightningdragon";
    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public LightningDragonMod(IEventBus modBus) {

        // Register deferred registers
        ModEntities.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);

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
        // Register entity renderers
        event.registerEntityRenderer(ModEntities.LIGHTNING_DRAGON.get(), LightningDragonRenderer::new);
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
    }
}
