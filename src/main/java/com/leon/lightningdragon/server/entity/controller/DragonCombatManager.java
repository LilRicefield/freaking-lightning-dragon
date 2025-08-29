package com.leon.lightningdragon.server.entity.controller;

import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;

/**
 * Handles all combat-related logic for the Lightning Dragon
 * TODO: Integrate with new Dragon ability system
 */
public class DragonCombatManager {
    private final LightningDragonEntity dragon;
    
    private DragonAbility<?> activeAbility;
    private int abilityCooldown = 0;

    public DragonCombatManager(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }

    /**
     * TODO: Implement with new Dragon ability system
     */
    public boolean tryUseRangedAbility() {
        // TODO: Implement new ability system
        return false;
    }

    public DragonAbility<?> getActiveAbility() {
        return activeAbility;
    }

    public DragonAbilityType<?, ?> getActiveAbilityType() {
        return activeAbility != null ? activeAbility.getAbilityType() : null;
    }

    public boolean canUseAbility() {
        return abilityCooldown == 0 && (activeAbility == null || !activeAbility.isUsing());
    }

    /**
     * TODO: Replace with new Dragon ability system
     */
    public void sendAbilityMessage(Object abilityType) {
        // TODO: Implement new ability system
    }

    public void forceEndActiveAbility() {
        if (activeAbility != null) {
            activeAbility.interrupt();
            activeAbility = null;
        }
    }

    public void validateCurrentTarget() {
        // TODO: Implement target validation
    }

    public void tick() {
        if (abilityCooldown > 0) {
            abilityCooldown--;
        }
        
        if (activeAbility != null) {
            if (activeAbility.isUsing()) {
                activeAbility.tick();
            } else {
                activeAbility = null;
            }
        }
    }

    public int getAbilityCooldown() {
        return abilityCooldown;
    }

    public void setAbilityCooldown(int cooldown) {
        this.abilityCooldown = cooldown;
    }
}