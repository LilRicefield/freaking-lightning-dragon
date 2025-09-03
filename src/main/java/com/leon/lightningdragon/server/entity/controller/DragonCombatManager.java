package com.leon.lightningdragon.server.entity.controller;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;

/**
 * Single responsibility: Track active ability and global cooldowns
 */
public class DragonCombatManager {
    private final LightningDragonEntity dragon;
    
    private DragonAbility<?> activeAbility;
    private int globalCooldown = 0; // Global cooldown between any abilities
    private boolean processingAbility = false; // Prevent re-entry during ability start

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
     * Try to use an ability (Umvuthana style)
     */
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

    public void validateCurrentTarget() {
        // TODO: Implement target validation
    }

    public void tick() {
        if (globalCooldown > 0) {
            globalCooldown--;
        }
        
        if (activeAbility != null) {
            if (activeAbility.isUsing()) {
                activeAbility.tick();
            } else {
                // Ability finished, set global cooldown
                globalCooldown = 10; // 0.5 second between abilities
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