package com.leon.lightningdragon.client;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adjusts third-person camera distance when riding the Lightning Dragon.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientCameraHandler {
    // Simple FOV multiplier approach to simulate zooming out when riding
    private static final double DRAGON_FOV_MULTIPLIER = 1.25; // 25% wider FOV when mounted

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        var camera = event.getCamera();
        var entity = camera.getEntity();
        if (!(entity instanceof LocalPlayer player)) return;
        if (!(player.getVehicle() instanceof LightningDragonEntity)) return;

        // Respect perspective: only adjust third-person views
        var camType = Minecraft.getInstance().options.getCameraType();
        if (camType == CameraType.THIRD_PERSON_BACK || camType == CameraType.THIRD_PERSON_FRONT) {
            double base = event.getFOV();
            event.setFOV(base * DRAGON_FOV_MULTIPLIER);
        }
    }
}
