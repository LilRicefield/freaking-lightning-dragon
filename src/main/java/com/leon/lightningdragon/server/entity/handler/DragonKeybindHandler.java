package com.leon.lightningdragon.server.entity.handler;

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
        this.controlState = controlState;
        
        // Extract individual control states from bitfield
        dragon.setGoingUp((controlState & 1) != 0);
        dragon.setGoingDown((controlState & 2) != 0);
        dragon.setRiderAttacking((controlState & 4) != 0);
        
        // TODO: Add new Dragon ability system here
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
    
    // ===== KEYBIND PACKET HANDLING =====
    
    /**
     * Handles key packet input from riders
     * TODO: Implement new Dragon ability system here
     */
    public void onKeyPacket(Entity keyPresser, int type) {
        // TODO: Add new Dragon ability handling
    }
}