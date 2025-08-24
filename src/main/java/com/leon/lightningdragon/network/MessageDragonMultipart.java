package com.leon.lightningdragon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Network message for handling multi-part entity interactions and damage.
 * Based on AlexsCaves MultipartEntityMessage pattern.
 * 
 * Message types:
 * - Type 0: Player interaction with part (forwards to parent entity)
 * - Type 1: Part takes damage (forwards to parent entity)
 */
public class MessageDragonMultipart {
    private final int parentEntityId;
    private final int playerId;
    private final int messageType;
    private final float damageAmount;

    public MessageDragonMultipart(int parentEntityId, int playerId, int messageType, float damageAmount) {
        this.parentEntityId = parentEntityId;
        this.playerId = playerId;
        this.messageType = messageType;
        this.damageAmount = damageAmount;
    }

    public static void encode(MessageDragonMultipart message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.parentEntityId);
        buffer.writeInt(message.playerId);
        buffer.writeInt(message.messageType);
        buffer.writeFloat(message.damageAmount);
    }

    public static MessageDragonMultipart decode(FriendlyByteBuf buffer) {
        return new MessageDragonMultipart(
                buffer.readInt(),    // parentEntityId
                buffer.readInt(),    // playerId  
                buffer.readInt(),    // messageType
                buffer.readFloat()   // damageAmount
        );
    }

    public static void handle(MessageDragonMultipart message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        
        // Ensure we're processing this on the server thread
        context.enqueueWork(() -> {
            Player serverPlayer = context.getSender();
            if (serverPlayer == null) {
                return; // Invalid - should only be sent from client to server
            }

            // Get the parent entity (the dragon)
            Entity parentEntity = serverPlayer.level().getEntity(message.parentEntityId);
            if (parentEntity == null || !parentEntity.isMultipartEntity()) {
                return; // Parent entity doesn't exist or isn't multipart
            }

            // Get the player who performed the action
            Entity actionPlayer = serverPlayer.level().getEntity(message.playerId);
            if (!(actionPlayer instanceof Player player)) {
                return; // Action performer isn't a player
            }

            // Security check: ensure the player is close enough to interact
            if (player.distanceTo(parentEntity) > 16.0) {
                return; // Player is too far away
            }

            // Handle the different message types
            switch (message.messageType) {
                case 0: // Player interaction with part
                    handleInteraction(parentEntity, player);
                    break;
                    
                case 1: // Part takes damage
                    handleDamage(parentEntity, player, message.damageAmount);
                    break;
                    
                default:
                    // Unknown message type - ignore
                    break;
            }
        });
        
        context.setPacketHandled(true);
    }

    private static void handleInteraction(Entity parentEntity, Player player) {
        // Forward the interaction to the parent entity
        // The parent entity will handle the interaction logic
        parentEntity.interact(player, player.getUsedItemHand());
    }

    private static void handleDamage(Entity parentEntity, Player player, float damageAmount) {
        // Create a damage source from the player
        DamageSource damageSource = parentEntity.damageSources().playerAttack(player);
        
        // Forward the damage to the parent entity
        // The parent entity will handle damage calculation, reduction, etc.
        parentEntity.hurt(damageSource, damageAmount);
    }
}