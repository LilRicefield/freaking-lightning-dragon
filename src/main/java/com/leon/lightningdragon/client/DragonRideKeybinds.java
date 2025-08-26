package com.leon.lightningdragon.client;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.network.MessageDragonRideInput;
import com.leon.lightningdragon.network.MessageDragonControl;
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
    
    
    
    // State tracking
    private static boolean wasAscendPressed = false;
    private static boolean wasDescendPressed = false;
    private static boolean wasLightningBeamPressed = false; // Track Y key state for hold-to-fire
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
        
        // Handle control state system (Ice & Fire style)
        handleDragonControlState(dragon, player);
        
        // Handle individual ability inputs
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
    
    /**
     * Handle control state system like Ice & Fire dragons
     */
    private static void handleDragonControlState(LightningDragonEntity dragon, LocalPlayer player) {
        // Build control state bitfield
        byte controlState = 0;
        
        // Bit 0: Up/ascend
        if (DRAGON_ASCEND.isDown()) {
            controlState |= 1;
        }
        
        // Bit 1: Down/descend
        if (DRAGON_DESCEND.isDown()) {
            controlState |= 2;
        }
        
        // Bit 2: Attack (melee attacks using mouse)
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyAttack.isDown()) {
            controlState |= 4;
        }
        
        // Bit 4: Lightning Beam (Y key held)
        if (DRAGON_ABILITY_3.isDown()) {
            controlState |= 16;
        }
        
        // Bit 5: Dismount (handled by vanilla system already)
        if (mc.options.keyShift.isDown()) {
            controlState |= 32;
        }
        
        // Only send if control state changed
        byte previousState = dragon.getControlState();
        if (controlState != previousState) {
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), 
                new MessageDragonControl(dragon.getId(), controlState, 
                    dragon.getX(), dragon.getY(), dragon.getZ()));
        }
    }
    
    private static void handleAbilityInput(LightningDragonEntity dragon) {
        String abilityToUse = null;
        
        boolean lightningBeamHeld = DRAGON_ABILITY_3.isDown(); // Y key held
        
        
        // Aerial abilities (only if flying)
        if (dragon.isFlying()) {
            if (DRAGON_ABILITY_1.consumeClick()) {
                abilityToUse = "lightning_burst";
            } else if (DRAGON_ABILITY_2.consumeClick()) {
                abilityToUse = "lightning_breath";
            } else if (lightningBeamHeld && !wasLightningBeamPressed) {
                // Only trigger Lightning Beam once when key is first pressed
                // BUT also check if there's already an active beam ability
                if (dragon.combatManager.getActiveAbility() == null || 
                    !(dragon.combatManager.getActiveAbility() instanceof com.leon.lightningdragon.ai.abilities.combat.EnhancedLightningBeamAbility)) {
                    abilityToUse = "lightning_beam";
                } else {
                }
            }
        }
        // Ground abilities (only if not flying) - use mouse click
        else {
            Minecraft mc = Minecraft.getInstance();
            // Check for left mouse click when on ground and riding dragon
            if (mc.mouseHandler != null && mc.options.keyAttack.consumeClick()) {
                // Use electric_bite for ground attacks
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
        
        // Update Lightning Beam state tracking
        wasLightningBeamPressed = lightningBeamHeld;
    }
}