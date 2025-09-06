package com.leon.lightningdragon.client;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LightningDragonMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onComputeCamera(ViewportEvent.ComputeCameraAngles event) {
        Entity player = Minecraft.getInstance().getCameraEntity();
        if (player != null && player.isPassenger() && player.getVehicle() instanceof LightningDragonEntity && event.getCamera().isDetached()) {
            event.getCamera().move(-event.getCamera().getMaxZoom(10F), 0, 0);
        }
    }
}