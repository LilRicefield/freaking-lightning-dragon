package com.leon.lightningdragon.common.network;

import com.leon.lightningdragon.util.KeybindUsingMount;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DragonKeyPacket {
    private final int keyType;

    public DragonKeyPacket(int keyType) {
        this.keyType = keyType;
    }

    public static void encode(DragonKeyPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.keyType);
    }

    public static DragonKeyPacket decode(FriendlyByteBuf buffer) {
        return new DragonKeyPacket(buffer.readInt());
    }

    public static void handle(DragonKeyPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.isPassenger()) {
                Entity vehicle = player.getVehicle();
                if (vehicle instanceof KeybindUsingMount mount) {
                    mount.onKeyPacket(player, message.keyType);
                }
            }
        });
        context.setPacketHandled(true);
    }
}