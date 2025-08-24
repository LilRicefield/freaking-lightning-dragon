package com.leon.lightningdragon.entity;

import com.leon.lightningdragon.network.MessageDragonMultipart;
import com.leon.lightningdragon.network.NetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Dragon part entity for multi-part hitbox system.
 * Based on TremorzillaPartEntity from AlexsCaves.
 */
public class DragonPartEntity extends PartEntity<LightningDragonEntity> {
    public final LightningDragonEntity parentMob;
    public final String partName;
    private final float width;
    private final float height;
    
    // For chained parts (like tail segments)
    @Nullable
    public final DragonPartEntity parentPart;

    public DragonPartEntity(LightningDragonEntity parent, String partName, float width, float height) {
        this(parent, null, partName, width, height);
    }

    public DragonPartEntity(LightningDragonEntity parent, @Nullable DragonPartEntity parentPart, String partName, float width, float height) {
        super(parent);
        this.parentMob = parent;
        this.parentPart = parentPart;
        this.partName = partName;
        this.width = width;
        this.height = height;
        this.refreshDimensions();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // Handle client-server synchronization for damage
        if (this.parentMob != null && !this.isInvulnerableTo(damageSource)) {
            Entity attacker = damageSource.getEntity();
            
            // On client side, send damage message to server
            if (attacker != null && attacker.level().isClientSide) {
                MessageDragonMultipart message = new MessageDragonMultipart(
                    this.parentMob.getId(),  // Parent dragon ID
                    attacker.getId(),        // Attacker ID
                    1,                       // Type 1 = damage
                    amount                   // Damage amount
                );
                NetworkHandler.INSTANCE.sendToServer(message);
                return false; // Don't process damage on client
            }
            
            // On server side, process damage normally
            if (!attacker.level().isClientSide) {
                return this.parentMob.hurt(damageSource, amount);
            }
        }
        return false;
    }

    /**
     * Handle player interactions with this part
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.parentMob == null) {
            return InteractionResult.PASS;
        }

        // On client side, send interaction message to server
        if (player.level().isClientSide) {
            MessageDragonMultipart message = new MessageDragonMultipart(
                this.parentMob.getId(),  // Parent dragon ID
                player.getId(),          // Player ID
                0,                       // Type 0 = interaction
                0.0f                     // No damage amount for interactions
            );
            NetworkHandler.INSTANCE.sendToServer(message);
        }
        
        // Forward interaction to parent dragon
        return this.parentMob.interact(player, hand);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.parentMob == entity;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void defineSynchedData() {
        // No synced data needed for parts
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        // Parts don't save data
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        // Parts don't save data
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // Apply dragon's scale dynamically to dimensions
        if (this.parentMob != null) {
            float scale = this.parentMob.getScale();
            return EntityDimensions.scalable(this.width * scale, this.height * scale);
        }
        return EntityDimensions.scalable(this.width, this.height);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // Helper method to set position centered on Y axis
    public void setPosCenteredY(Vec3 position) {
        this.setPos(position.x, position.y - this.getBbHeight() * 0.5, position.z);
    }

    // Get the center position of this part
    public Vec3 centeredPosition() {
        return new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ());
    }

    // Refresh dimensions - force the entity to recalculate its bounding box
    public void refreshDimensions() {
        if (this.parentMob != null) {
            // Force dimension recalculation by calling the entity's refresh method
            // This will call getDimensions() which now includes scaling
            super.refreshDimensions();
        }
    }
}