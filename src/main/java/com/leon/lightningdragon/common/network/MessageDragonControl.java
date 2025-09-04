package com.leon.lightningdragon.common.network;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageDragonControl {
    
    private int dragonId;
    private byte controlState;
    private double posX;
    private double posY;
    private double posZ;
    
    public MessageDragonControl() {
    }
    
    public MessageDragonControl(int dragonId, byte controlState, double posX, double posY, double posZ) {
        this.dragonId = dragonId;
        this.controlState = controlState;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }
    
    public static void encode(MessageDragonControl message, FriendlyByteBuf buf) {
        buf.writeInt(message.dragonId);
        buf.writeByte(message.controlState);
        buf.writeDouble(message.posX);
        buf.writeDouble(message.posY);
        buf.writeDouble(message.posZ);
    }
    
    public static MessageDragonControl decode(FriendlyByteBuf buf) {
        return new MessageDragonControl(
            buf.readInt(),
            buf.readByte(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );
    }
    
    public static void handle(MessageDragonControl message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player != null && player.level() != null) {
                Entity entity = player.level().getEntity(message.dragonId);
                if (entity instanceof LightningDragonEntity dragon) {
                    if (dragon.isOwnedBy(player)) {
                        dragon.setControlState(message.controlState);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}