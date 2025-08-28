package com.leon.lightningdragon.network;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageDragonRideInput {
    private boolean goingUp;
    private boolean goingDown;
    private boolean attacking;
    private String abilityName;
    private float forward;
    private float strafe;
    private float yaw;
    
    public MessageDragonRideInput() {}
    
    public MessageDragonRideInput(boolean goingUp, boolean goingDown, boolean attacking, String abilityName, float forward, float strafe, float yaw) {
        this.goingUp = goingUp;
        this.goingDown = goingDown;
        this.attacking = attacking;
        this.abilityName = abilityName;
        this.forward = forward;
        this.strafe = strafe;
        this.yaw = yaw;
    }

    public static void encode(MessageDragonRideInput msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.goingUp);
        buf.writeBoolean(msg.goingDown);
        buf.writeBoolean(msg.attacking);
        buf.writeUtf(msg.abilityName != null ? msg.abilityName : "");
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.strafe);
        buf.writeFloat(msg.yaw);
    }
    
    public static MessageDragonRideInput decode(FriendlyByteBuf buf) {
        boolean goingUp = buf.readBoolean();
        boolean goingDown = buf.readBoolean();
        boolean attacking = buf.readBoolean();
        String abilityName = buf.readUtf();
        float forward = buf.readFloat();
        float strafe = buf.readFloat();
        float yaw = buf.readFloat();
        return new MessageDragonRideInput(goingUp, goingDown, attacking, abilityName.isEmpty() ? null : abilityName, forward, strafe, yaw);
    }
    
    public static void handle(MessageDragonRideInput msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity vehicle = player.getVehicle();
                if (vehicle instanceof LightningDragonEntity dragon && dragon.isTame() && dragon.isOwnedBy(player)) {
                    // Always handle vertical movement commands when flying
                    if (dragon.isFlying()) {
                        dragon.setGoingUp(msg.goingUp);
                        dragon.setGoingDown(msg.goingDown);
                    } else {
                        // Clear vertical movement when not flying
                        dragon.setGoingUp(false);
                        dragon.setGoingDown(false);
                    }
                    
                    // Handle ability usage or special commands
                    if (msg.abilityName != null && !msg.abilityName.isEmpty()) {
                        if ("takeoff_request".equals(msg.abilityName)) {
                            // Handle takeoff request from ground
                            dragon.requestRiderTakeoff();
                        } else if ("accelerate".equals(msg.abilityName)) {
                            // Handle acceleration input - L-Ctrl pressed
                            dragon.setAccelerating(true);
                        } else if ("stop_accelerate".equals(msg.abilityName)) {
                            // Handle acceleration stop - L-Ctrl released
                            dragon.setAccelerating(false);
                        } else {
                            // Handle normal ability usage
                            dragon.useRidingAbility(msg.abilityName);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}