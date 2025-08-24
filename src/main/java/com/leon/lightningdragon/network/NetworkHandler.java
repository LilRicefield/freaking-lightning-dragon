// NetworkHandler.java
package com.leon.lightningdragon.network;

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
        INSTANCE.registerMessage(id++, MessageDragonUseAbility.class,
                MessageDragonUseAbility::encode,
                MessageDragonUseAbility::decode,
                MessageDragonUseAbility::handle);
        INSTANCE.registerMessage(id++, MessageDragonRideInput.class,
                MessageDragonRideInput::encode,
                MessageDragonRideInput::decode,
                MessageDragonRideInput::handle);
        INSTANCE.registerMessage(id++, DragonKeyPacket.class,
                DragonKeyPacket::encode,
                DragonKeyPacket::decode,
                DragonKeyPacket::handle);
        INSTANCE.registerMessage(id++, MessageDragonMultipart.class,
                MessageDragonMultipart::encode,
                MessageDragonMultipart::decode,
                MessageDragonMultipart::handle);
        INSTANCE.registerMessage(id++, MessageDragonControl.class,
                MessageDragonControl::encode,
                MessageDragonControl::decode,
                MessageDragonControl::handle);
        INSTANCE.registerMessage(id++, MessageDragonSyncFire.class,
                MessageDragonSyncFire::encode,
                MessageDragonSyncFire::decode,
                MessageDragonSyncFire::handle);
    }
}