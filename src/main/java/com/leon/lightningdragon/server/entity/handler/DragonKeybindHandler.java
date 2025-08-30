package com.leon.lightningdragon.server.entity.handler;

import com.leon.lightningdragon.common.registry.ModAbilities;
import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import net.minecraft.world.entity.Entity;

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
        boolean prevAttack = (previous & 4) != 0;
        if (!prevAttack && attack) {
            // Use combat manager (clean path)
            dragon.combatManager.tryUseAbility(ModAbilities.BITE);
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
