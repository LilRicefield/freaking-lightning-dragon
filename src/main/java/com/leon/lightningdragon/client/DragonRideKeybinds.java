package com.leon.lightningdragon.client;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.common.network.MessageDragonRideInput;
import com.leon.lightningdragon.common.network.DragonRiderAction;
import com.leon.lightningdragon.common.network.MessageDragonControl;
import com.leon.lightningdragon.common.network.NetworkHandler;
// no client-driven beam/rider anchor sync
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

    // Hold-to-fire Lightning Beam (G)
    public static final KeyMapping DRAGON_BEAM = new KeyMapping(
            "key.lightningdragon.ability3",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            "key.categories.lightningdragon"
    );

    
    // State tracking
    private static boolean wasAscendPressed = false;
    private static boolean wasBeamDown = false;
    // (no debug anchor toggle)
    
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEventHandler {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(DRAGON_ASCEND);
            event.register(DRAGON_DESCEND);
            event.register(DRAGON_ACCELERATE);
            event.register(DRAGON_BEAM);
            // no debug key
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

        // Seat syncing disabled; use server-deterministic seat placement
        
        // Handle control state system (Ice & Fire style)
        handleDragonControlState(dragon);
        
        // Handle ascend key both for flying and takeoff from ground
        boolean currentAscend = DRAGON_ASCEND.isDown();
        boolean currentDescend = DRAGON_DESCEND.isDown();
        boolean currentAccelerate = DRAGON_ACCELERATE.isDown();
        boolean beamDown = DRAGON_BEAM.isDown();
        
        if (dragon.isFlying()) {
            // Flying controls - send every tick for responsive flight
            DragonRiderAction action = currentAccelerate ? DragonRiderAction.ACCELERATE : DragonRiderAction.STOP_ACCELERATE;
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new MessageDragonRideInput(currentAscend, currentDescend, action, null, 0, 0, 0));
        } else {
            // Ground takeoff - only trigger once per key press
            if (currentAscend && !wasAscendPressed) {
                // Request takeoff from ground
                NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                        new MessageDragonRideInput(false, false, DragonRiderAction.TAKEOFF_REQUEST, null, 0, 0, 0));
            }
            // Ground acceleration - send every tick to handle both press and release
            DragonRiderAction groundAction = currentAccelerate ? DragonRiderAction.ACCELERATE : DragonRiderAction.STOP_ACCELERATE;
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new MessageDragonRideInput(false, false, groundAction, null, 0, 0, 0));
        }

        // Handle hold-to-fire beam start/stop (send transitions only)
        // Start on press
        if (beamDown && !wasBeamDown) {
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new MessageDragonRideInput(false, false, DragonRiderAction.ABILITY_USE, "lightning_beam", 0, 0, 0));
        }
        // Stop on release
        if (!beamDown && wasBeamDown) {
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new MessageDragonRideInput(false, false, DragonRiderAction.ABILITY_STOP, "lightning_beam", 0, 0, 0));
        }

        
        wasAscendPressed = currentAscend;
        wasBeamDown = beamDown;
    }
    
    /**
     * Handle control state system like Ice & Fire dragons
     */
    private static void handleDragonControlState(LightningDragonEntity dragon) {
        byte controlState = buildControlState();
        byte previousState = dragon.getControlState();
        if (controlState != previousState) {
            NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new MessageDragonControl(dragon.getId(), controlState));
            dragon.setControlState(controlState);
        }
    }

    private static byte buildControlState() {
        byte controlState = 0;
        if (DRAGON_ASCEND.isDown()) controlState |= 1;   // Bit 0: Up/ascend
        if (DRAGON_DESCEND.isDown()) controlState |= 2;  // Bit 1: Down/descend

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyAttack.isDown()) controlState |= 4; // Bit 2: Attack
        if (mc.options.keyShift.isDown()) controlState |= 32; // Bit 5: Dismount
        return controlState;
    }
}
