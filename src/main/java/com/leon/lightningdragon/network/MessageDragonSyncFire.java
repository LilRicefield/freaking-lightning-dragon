package com.leon.lightningdragon.network;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageDragonSyncFire {
    
    private int dragonId;
    private double posX;
    private double posY;
    private double posZ;
    private int syncType;
    
    public MessageDragonSyncFire() {
    }
    
    public MessageDragonSyncFire(int dragonId, double posX, double posY, double posZ, int syncType) {
        this.dragonId = dragonId;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.syncType = syncType;
    }
    
    public static void encode(MessageDragonSyncFire message, FriendlyByteBuf buf) {
        buf.writeInt(message.dragonId);
        buf.writeDouble(message.posX);
        buf.writeDouble(message.posY);
        buf.writeDouble(message.posZ);
        buf.writeInt(message.syncType);
    }
    
    public static MessageDragonSyncFire decode(FriendlyByteBuf buf) {
        return new MessageDragonSyncFire(
            buf.readInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readInt()
        );
    }
    
    public static void handle(MessageDragonSyncFire message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null) {
                return;
            }
            
            if (player.level() != null) {
                Entity entity = player.level().getEntity(message.dragonId);
                if (entity instanceof LightningDragonEntity dragon) {
                    dragon.stimulateFire(message.posX, message.posY, message.posZ, message.syncType);
                }
            }
        });
        context.setPacketHandled(true);
    }
}