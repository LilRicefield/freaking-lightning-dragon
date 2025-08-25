package com.leon.lightningdragon.entity.handler;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

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
        
        // Handle Enhanced Lightning Beam (bit 4) - Y key - Ice & Fire style
        boolean yKeyHeld = (controlState & 16) != 0;
        if (yKeyHeld) {
            // Y key is held - reset fireStopTicks to 10 (like Ice & Fire) and start ability
            dragon.getLightningSystem().setFireStopTicks(10);
            dragon.getLightningSystem().setBreathingFire(true);
            
            // Start Enhanced Lightning Beam ability if not already active
            if (dragon.combatManager.getActiveAbility() == null || 
                !(dragon.combatManager.getActiveAbility() instanceof com.leon.lightningdragon.ai.abilities.combat.EnhancedLightningBeamAbility)) {
                dragon.combatManager.sendAbilityMessage(dragon.LIGHTNING_BEAM_ABILITY);
            }
        }
        // Note: The fireStopTicks countdown and ability ending is handled in DragonLightningSystem
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
     * Called when player presses G key or left mouse while riding
     */
    public void onKeyPacket(Entity keyPresser, int type) {
        if (keyPresser.isPassengerOfSameVehicle(dragon)) {
            // Key types
            // 2 = G key (beam attack)
            // 3 = Left mouse (melee attack)
            if (type == 2) {
                // G key - Lightning Beam ability
                if (dragon.isTame() && !dragon.level().isClientSide) {
                    dragon.yBodyRot = keyPresser.getYHeadRot();
                    dragon.setYRot(keyPresser.getYHeadRot());
                    dragon.useRidingAbility("lightning_beam");
                }
            }
            if (type == 3) {
                // Left mouse - Melee attacks
                if (dragon.isTame() && !dragon.level().isClientSide) {
                    if (dragon.isFlying()) {
                        dragon.useRidingAbility("wing_lightning");
                    } else {
                        dragon.useRidingAbility("electric_bite");
                    }
                }
            }
        }
    }
}