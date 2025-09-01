package com.leon.lightningdragon.client;

import com.leon.lightningdragon.LightningDragonMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handler for Lightning Dragon mod
 */
@Mod.EventBusSubscriber(modid = LightningDragonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {}