// MessageDragonUseAbility.java
package com.leon.lightningdragon.network;

import com.leon.lightningdragon.ai.abilities.AbilityType;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageDragonUseAbility {
    private int entityID;
    private String abilityName; // Using name instead of index for simplicity

    public MessageDragonUseAbility() {}

    public MessageDragonUseAbility(int entityID, String abilityName) {
        this.entityID = entityID;
        this.abilityName = abilityName;
    }

    public static void encode(MessageDragonUseAbility msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityID);
        buf.writeUtf(msg.abilityName);
    }

    public static MessageDragonUseAbility decode(FriendlyByteBuf buf) {
        return new MessageDragonUseAbility(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(MessageDragonUseAbility msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // CLIENT SIDE ONLY - Just activate the ability directly, don't send another message
            assert Minecraft.getInstance().level != null;
            Entity entity = Minecraft.getInstance().level.getEntity(msg.entityID);
            if (entity instanceof LightningDragonEntity dragon) {
                // Find the ability type by name and activate it directly
                AbilityType<LightningDragonEntity, ?> abilityType = getAbilityTypeByName(msg.abilityName);
                if (abilityType != null) {
                    // Create and start the ability directly on client
                    @SuppressWarnings("unchecked")
                    AbilityType<LightningDragonEntity, com.leon.lightningdragon.ai.abilities.Ability<LightningDragonEntity>> castType =
                            (AbilityType<LightningDragonEntity, com.leon.lightningdragon.ai.abilities.Ability<LightningDragonEntity>>) abilityType;
                    
                    com.leon.lightningdragon.ai.abilities.Ability<LightningDragonEntity> ability = castType.createAbility(dragon);
                    if (ability.tryAbility()) {
                        dragon.setActiveAbility(ability);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static AbilityType<LightningDragonEntity, ?> getAbilityTypeByName(String name) {
        // Match ability names - could be cleaner with a registry
        return switch (name) {
            case "lightning_breath" -> LightningDragonEntity.LIGHTNING_BREATH_ABILITY;
            case "lightning_beam" -> LightningDragonEntity.LIGHTNING_BEAM_ABILITY;
            case "lightning_burst" -> LightningDragonEntity.LIGHTNING_BURST_ABILITY;
            case "electric_bite" -> LightningDragonEntity.ELECTRIC_BITE_ABILITY;
            case "thunder_stomp" -> LightningDragonEntity.THUNDER_STOMP_ABILITY;
            case "wing_lightning" -> LightningDragonEntity.WING_LIGHTNING_ABILITY;
            case "dragon_hurt" -> LightningDragonEntity.HURT_ABILITY;
            default -> null;
        };
    }
}