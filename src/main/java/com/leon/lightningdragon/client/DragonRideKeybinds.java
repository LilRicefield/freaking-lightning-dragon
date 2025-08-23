package com.leon.lightningdragon.client;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.network.MessageDragonRideInput;
import com.leon.lightningdragon.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Random;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DragonRideKeybinds {
    
    // Keybind definitions
    public static final KeyMapping DRAGON_ASCEND = new KeyMapping(
            "key.lightningdragon.ascend",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE,
            "key.categories.lightningdragon"
    );
    
    public static final KeyMapping DRAGON_DESCEND = new KeyMapping(
            "key.lightningdragon.descend",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LALT,
            "key.categories.lightningdragon"
    );
    
    public static final KeyMapping DRAGON_ACCELERATE = new KeyMapping(
            "key.lightningdragon.accelerate",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LCONTROL,
            "key.categories.lightningdragon"
    );
    
    public static final KeyMapping DRAGON_ABILITY_1 = new KeyMapping(
            "key.lightningdragon.ability1",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            "key.categories.lightningdragon"
    );
    
    public static final KeyMapping DRAGON_ABILITY_2 = new KeyMapping(
            "key.lightningdragon.ability2",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_T,
            "key.categories.lightningdragon"
    );
    
    public static final KeyMapping DRAGON_ABILITY_3 = new KeyMapping(
            "key.lightningdragon.ability3",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Y,
            "key.categories.lightningdragon"
    );
    
    public static final KeyMapping DRAGON_GROUND_ABILITY = new KeyMapping(
            "key.lightningdragon.ground_ability",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            "key.categories.lightningdragon"
    );
    
    // State tracking
    private static boolean wasAscendPressed = false;
    private static boolean wasDescendPressed = false;
    private static final Random RANDOM = new Random();
    
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEventHandler {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(DRAGON_ASCEND);
            event.register(DRAGON_DESCEND);
            event.register(DRAGON_ACCELERATE);
            event.register(DRAGON_ABILITY_1);
            event.register(DRAGON_ABILITY_2);
            event.register(DRAGON_ABILITY_3);
            event.register(DRAGON_GROUND_ABILITY);
        }
    }
    
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null || player.getVehicle() == null) return;
        
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LightningDragonEntity dragon)) return;
        
        if (!dragon.isTame() || !dragon.isOwnedBy(player)) return;
        
        // For ground movement, we mostly rely on vanilla getRiddenInput()
        // Only handle special ability keybinds here
        handleAbilityInput(dragon);
        
        // Handle ascend key both for flying and takeoff from ground
        boolean currentAscend = DRAGON_ASCEND.isDown();
        boolean currentDescend = DRAGON_DESCEND.isDown();
        boolean currentAccelerate = DRAGON_ACCELERATE.isDown();
        
        if (dragon.isFlying()) {
            // Flying controls - send every tick for responsive flight
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new MessageDragonRideInput(currentAscend, currentDescend, false, 
                    currentAccelerate ? "accelerate" : "stop_accelerate", 0, 0, 0));
        } else {
            // Ground takeoff - only trigger once per key press
            if (currentAscend && !wasAscendPressed) {
                // Request takeoff from ground
                NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                        new MessageDragonRideInput(false, false, false, "takeoff_request", 0, 0, 0));
            }
            // Ground acceleration - send every tick to handle both press and release
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new MessageDragonRideInput(false, false, false, 
                    currentAccelerate ? "accelerate" : "stop_accelerate", 0, 0, 0));
        }
        
        wasAscendPressed = currentAscend;
        wasDescendPressed = currentDescend;
    }
    
    private static void handleAbilityInput(LightningDragonEntity dragon) {
        String abilityToUse = null;
        
        // Aerial abilities (only if flying)
        if (dragon.isFlying()) {
            if (DRAGON_ABILITY_1.consumeClick()) {
                abilityToUse = "lightning_burst";
            } else if (DRAGON_ABILITY_2.consumeClick()) {
                abilityToUse = "lightning_breath";
            } else if (DRAGON_ABILITY_3.consumeClick()) {
                abilityToUse = "lightning_beam";
            }
        }
        // Ground abilities (only if not flying)
        else {
            if (DRAGON_GROUND_ABILITY.consumeClick()) {
                // For ground testing, use a simple ability
                abilityToUse = "electric_bite";
            }
        }
        
        // Send ability usage packet
        if (abilityToUse != null) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                        new MessageDragonRideInput(false, false, true, abilityToUse, 0f, 0f, player.getYRot()));
            }
        }
    }
}