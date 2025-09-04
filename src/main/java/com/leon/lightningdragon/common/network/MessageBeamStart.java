package com.leon.lightningdragon.common.network;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server packet to sync the beam start (mouth) position while beaming.
 * Server validates and prefers this value for damage origin.
 */
public class MessageBeamStart {
    private int dragonId;
    private double x;
    private double y;
    private double z;

    public MessageBeamStart() {}

    public MessageBeamStart(int dragonId, Vec3 start) {
        this.dragonId = dragonId;
        this.x = start.x;
        this.y = start.y;
        this.z = start.z;
    }

    public static void encode(MessageBeamStart msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.dragonId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static MessageBeamStart decode(FriendlyByteBuf buf) {
        MessageBeamStart m = new MessageBeamStart();
        m.dragonId = buf.readInt();
        m.x = buf.readDouble();
        m.y = buf.readDouble();
        m.z = buf.readDouble();
        return m;
    }

    public static void handle(MessageBeamStart msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.level() == null) return;
            Entity e = player.level().getEntity(msg.dragonId);
            if (!(e instanceof LightningDragonEntity dragon)) return;
            // Basic validation: player must be riding and own the dragon
            if (!dragon.isTame() || !dragon.isOwnedBy(player) || player.getVehicle() != dragon) return;

            Vec3 proposed = new Vec3(msg.x, msg.y, msg.z);
            if (!isFinite(proposed)) return;

            // Sanity checks:
            // 1) Must be within a reasonable radius from the dragon's head (large model scale!)
            Vec3 head = dragon.getHeadPosition();
            if (head != null && head.distanceToSqr(proposed) > 64.0) {
                return; // too far from head
            }
            // 2) Must be close to server's expected mouth pos this tick
            Vec3 expected = dragon.computeHeadMouthOrigin(1.0f);
            if (expected != null && expected.distanceToSqr(proposed) > 9.0) { // within ~3 blocks
                return; // suspicious offset; reject
            }

            dragon.setBeamStartPosition(proposed);
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
