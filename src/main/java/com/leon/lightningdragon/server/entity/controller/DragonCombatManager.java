package com.leon.lightningdragon.server.entity.controller;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Single responsibility: Track active ability and global cooldowns
 */
public class DragonCombatManager {
    private final LightningDragonEntity dragon;
    
    private DragonAbility<?> activeAbility;
    private int globalCooldown = 0; // Global cooldown between any abilities
    private boolean processingAbility = false; // Prevent re-entry during ability start
    
    // Per-ability cooldown tracking
    private final Map<DragonAbilityType<?, ?>, Integer> abilityCooldowns = new HashMap<>();

    public DragonCombatManager(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }

    public DragonAbility<?> getActiveAbility() {
        return activeAbility;
    }
    
    public void setActiveAbility(DragonAbility<?> ability) {
        this.activeAbility = ability;
    }

    public DragonAbilityType<?, ?> getActiveAbilityType() {
        return activeAbility != null ? activeAbility.getAbilityType() : null;
    }

    public boolean canUseAbility() {
        return globalCooldown == 0 && (activeAbility == null || !activeAbility.isUsing()) && !processingAbility;
    }
    
    /**
     * Check if a specific ability type can be started (includes per-ability cooldown)
     */
    public boolean canStart(DragonAbilityType<?, ?> abilityType) {
        return globalCooldown == 0
            && activeAbility == null
            && !processingAbility
            && isAbilityCooldownReady(abilityType);
    }
    
    /**
     * Check if a specific ability's cooldown is ready
     */
    public boolean isAbilityCooldownReady(DragonAbilityType<?, ?> abilityType) {
        return abilityCooldowns.getOrDefault(abilityType, 0) <= 0;
    }
    
    /**
     * Set cooldown for a specific ability type
     */
    public void setAbilityCooldown(DragonAbilityType<?, ?> abilityType, int cooldownTicks) {
        abilityCooldowns.put(abilityType, cooldownTicks);
    }

    public boolean tryUseAbility(DragonAbilityType<?, ?> abilityType) {
        if (!canUseAbility()) return false;
        
        processingAbility = true; // Guard against re-entry
        try {
            @SuppressWarnings("unchecked")
            var ability = ((DragonAbilityType<LightningDragonEntity, ?>) abilityType).makeInstance(dragon);
            
            if (ability.tryAbility()) {
                // Set ability active IMMEDIATELY to prevent race conditions
                setActiveAbility(ability);
                ability.start();
                return true;
            }
            return false;
        } finally {
            processingAbility = false;
        }
    }

    public void forceEndActiveAbility() {
        if (activeAbility != null) {
            activeAbility.interrupt();
            activeAbility = null;
        }
    }

    // Removed unused target validation stub

    public void tick() {
        if (globalCooldown > 0) {
            globalCooldown--;
        }
        
        // Tick down per-ability cooldowns
        abilityCooldowns.entrySet().removeIf(entry -> {
            int newValue = entry.getValue() - 1;
            if (newValue <= 0) {
                return true; // Remove from map when cooldown reaches 0
            } else {
                entry.setValue(newValue);
                return false;
            }
        });
        
        if (activeAbility != null) {
            if (activeAbility.isUsing()) {
                activeAbility.tick();
            } else {
                // Ability finished, set a small fixed global cooldown between abilities
                int baseCooldown = 6; // ~0.3s between abilities
                globalCooldown = baseCooldown;
                activeAbility = null;
            }
        }
    }

    public int getGlobalCooldown() {
        return globalCooldown;
    }

    public void setGlobalCooldown(int cooldown) {
        this.globalCooldown = cooldown;
    }
}
