package com.leon.lightningdragon.server.entity.handler;

import com.leon.lightningdragon.common.registry.ModAbilities;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;

/**
 * Handles all keybind and control state logic for the Lightning Dragon
 * Extracted from LightningDragonEntity for better code organization
 */
public class DragonKeybindHandler {
    private final LightningDragonEntity dragon;
    
    // Control state system (like Ice & Fire)
    private byte controlState = 0;
    
    public DragonKeybindHandler(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }
    
    // ===== CONTROL STATE SYSTEM =====
    
    public byte getControlState() {
        return controlState;
    }
    
    /**
     * Main control state handler - processes keybind input and triggers abilities
     * This is the core method that handles Y key press/release for Enhanced Lightning Beam
     */
    public void setControlState(byte controlState) {
        byte previous = this.controlState;
        this.controlState = controlState;

        // Extract individual control states from bitfield
        boolean up = (controlState & 1) != 0;
        boolean down = (controlState & 2) != 0;
        boolean attack = (controlState & 4) != 0;

        dragon.setGoingUp(up);
        dragon.setGoingDown(down);
        dragon.setRiderAttacking(attack);

        // Rising-edge detect attack to trigger bite while ridden
        // Only trigger on air clicks - entity attacks are handled separately
        boolean prevAttack = (previous & 4) != 0;
        if (!prevAttack && attack && dragon.getRidingPlayer() != null) {
            // Check if the rider is targeting an entity
            var rider = dragon.getRidingPlayer();
            var hitResult = rider.pick(6.0, 1.0f, false);
            
            // Only use bite ability for air clicks, not entity clicks
            if (hitResult.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
                // Align dragon to rider's current view so bite aims where the player is looking
                float yaw = rider.getYRot();
                float pitch = rider.getXRot();
                // Clamp pitch to keep aim reasonable
                if (pitch > 35f) pitch = 35f;
                if (pitch < -35f) pitch = -35f;
                dragon.setYRot(yaw);
                dragon.yBodyRot = yaw;
                dragon.yHeadRot = yaw;
                dragon.setXRot(pitch);
                
                // Use combat manager (clean path)
                dragon.combatManager.tryUseAbility(ModAbilities.BITE);
            }
        }
    }
    
    private void setStateField(int bit, boolean value) {
        if (value) {
            controlState |= (byte) (1 << bit);
        } else {
            controlState &= (byte) ~(1 << bit);
        }
    }
    
    public void up(boolean up) {
        setStateField(0, up);
    }
    
    public void down(boolean down) {
        setStateField(1, down);
    }
    
    public void attack(boolean attack) {
        setStateField(2, attack);
    }
    
    public void strike(boolean strike) {
        setStateField(3, strike);
    }
    
    // No direct packet handling; unified rider input uses MessageDragonRideInput/MessageDragonControl
}
