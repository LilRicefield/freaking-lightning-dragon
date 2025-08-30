// NetworkHandler.java
package com.leon.lightningdragon.common.network;

import com.leon.lightningdragon.LightningDragonMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LightningDragonMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        // TODO: Register new Dragon ability message when implemented
        INSTANCE.registerMessage(id++, MessageDragonRideInput.class,
                MessageDragonRideInput::encode,
                MessageDragonRideInput::decode,
                MessageDragonRideInput::handle);
        // DragonKeyPacket removed; use MessageDragonRideInput + MessageDragonControl for unified input
        INSTANCE.registerMessage(id++, MessageDragonControl.class,
                MessageDragonControl::encode,
                MessageDragonControl::decode,
                MessageDragonControl::handle);
    }
}
