package com.leon.lightningdragon.client;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.common.network.DragonKeyPacket;
import com.leon.lightningdragon.common.network.NetworkHandler;
import com.leon.lightningdragon.util.KeybindUsingMount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side input handling for dragon riding
 * Based on Ice & Fire mod's approach
 */
@Mod.EventBusSubscriber(modid = LightningDragonMod.MOD_ID, value = Dist.CLIENT)
public class DragonClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isPassenger()) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof KeybindUsingMount)) {
            return;
        }

        int keyType = -1;
        
        // Map keys to types (based on TremorzillaEntity reference)
        if (event.getKey() == GLFW.GLFW_KEY_G && event.getAction() == GLFW.GLFW_PRESS) {
            keyType = 2; // G key for special ability (press only)
        } else if (event.getKey() == GLFW.GLFW_KEY_Y) {
            // Y key for Enhanced Lightning Beam - handle both press and release
            if (event.getAction() == GLFW.GLFW_PRESS) {
                keyType = 4; // Y key press (start beam)
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                keyType = 5; // Y key release (stop beam)
            }
        }
        
        if (keyType != -1) {
            NetworkHandler.INSTANCE.sendToServer(new DragonKeyPacket(keyType));
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isPassenger()) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof KeybindUsingMount)) {
            return;
        }

        // Only handle button press events (not release)
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        int keyType = -1;
        
        // Map mouse buttons to types
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            keyType = 3; // Left mouse for attack
        }
        
        if (keyType != -1) {
            NetworkHandler.INSTANCE.sendToServer(new DragonKeyPacket(keyType));
        }
    }
}