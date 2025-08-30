package com.leon.lightningdragon.common.network;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageDragonRideInput {
    private boolean goingUp;
    private boolean goingDown;
    private boolean attacking;
    private DragonRiderAction action;
    private String abilityName; // Only used when action == ABILITY_USE
    private float forward;
    private float strafe;
    private float yaw;
    
    public MessageDragonRideInput() {}
    
    public MessageDragonRideInput(boolean goingUp, boolean goingDown, boolean attacking, DragonRiderAction action, String abilityName, float forward, float strafe, float yaw) {
        this.goingUp = goingUp;
        this.goingDown = goingDown;
        this.attacking = attacking;
        this.action = action != null ? action : DragonRiderAction.NONE;
        this.abilityName = abilityName;
        this.forward = forward;
        this.strafe = strafe;
        this.yaw = yaw;
    }
    
    // Convenience constructor for string-based abilities (backwards compatibility)
    public MessageDragonRideInput(boolean goingUp, boolean goingDown, boolean attacking, String abilityName, float forward, float strafe, float yaw) {
        this(goingUp, goingDown, attacking, 
             abilityName != null && !abilityName.isEmpty() ? DragonRiderAction.ABILITY_USE : DragonRiderAction.NONE,
             abilityName, forward, strafe, yaw);
    }

    public static void encode(MessageDragonRideInput msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.goingUp);
        buf.writeBoolean(msg.goingDown);
        buf.writeBoolean(msg.attacking);
        buf.writeEnum(msg.action);
        // Only send ability name if it's an ability use action
        if (msg.action == DragonRiderAction.ABILITY_USE) {
            buf.writeUtf(msg.abilityName != null ? msg.abilityName : "");
        }
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.strafe);
        buf.writeFloat(msg.yaw);
    }
    
    public static MessageDragonRideInput decode(FriendlyByteBuf buf) {
        boolean goingUp = buf.readBoolean();
        boolean goingDown = buf.readBoolean();
        boolean attacking = buf.readBoolean();
        DragonRiderAction action = buf.readEnum(DragonRiderAction.class);
        String abilityName = null;
        // Only read ability name if it's an ability use action
        if (action == DragonRiderAction.ABILITY_USE) {
            abilityName = buf.readUtf();
            if (abilityName.isEmpty()) abilityName = null;
        }
        float forward = buf.readFloat();
        float strafe = buf.readFloat();
        float yaw = buf.readFloat();
        return new MessageDragonRideInput(goingUp, goingDown, attacking, action, abilityName, forward, strafe, yaw);
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
                    
                    // Handle actions using type-safe enum
                    switch (msg.action) {
                        case TAKEOFF_REQUEST:
                            // Handle takeoff request from ground
                            dragon.requestRiderTakeoff();
                            break;
                        case ACCELERATE:
                            // Handle acceleration input - L-Ctrl pressed
                            dragon.setAccelerating(true);
                            break;
                        case STOP_ACCELERATE:
                            // Handle acceleration stop - L-Ctrl released
                            dragon.setAccelerating(false);
                            break;
                        case ABILITY_USE:
                            // Handle ability usage through ability name mapping
                            if (msg.abilityName != null && !msg.abilityName.isEmpty()) {
                                dragon.useRidingAbility(msg.abilityName);
                            }
                            break;
                        case NONE:
                        default:
                            // No special action to handle
                            break;
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
