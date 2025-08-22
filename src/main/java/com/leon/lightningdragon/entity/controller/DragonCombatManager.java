package com.leon.lightningdragon.entity.controller;

import com.leon.lightningdragon.ai.abilities.Ability;
import com.leon.lightningdragon.ai.abilities.AbilityType;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.network.MessageDragonUseAbility;
import com.leon.lightningdragon.network.NetworkHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Handles all combat-related logic for the Lightning Dragon including:
 * - Strategic ability selection based on range and target type
 * - Combat target validation and threat assessment
 * - Ultimate ability usage decisions
 * - Active ability management
 */
public class DragonCombatManager {
    private final LightningDragonEntity dragon;
    
    // Combat distance constants
    private static final double BEAM_RANGE = 30.0;
    private static final double BURST_RANGE = 20.0;
    private static final double BREATH_RANGE = 8.0;
    private static final double MELEE_RANGE = 6.0;

    // Cooldown constants
    private static final int ABILITY_COOLDOWN_TICKS = 60;
    private static final int ULTIMATE_COOLDOWN_TICKS = 1200;

    private Ability<LightningDragonEntity> activeAbility;
    private int abilityCooldown = 0;

    public DragonCombatManager(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }

    /**
     * Attempts to use a ranged ability based on strategic distance-based combat decisions
     */
    public boolean tryUseRangedAbility() {
        LivingEntity target = dragon.getTarget();
        if (target == null || !canUseAbility()) return false;

        double distance = dragon.distanceTo(target);

        // Strategic distance-based combat - prioritize frequent abilities
        if (distance >= BEAM_RANGE) {
            // Very long range - prefer beam attack, burst only as special case
            Ability<LightningDragonEntity> beamAbility = LightningDragonEntity.LIGHTNING_BEAM_ABILITY.createAbility(dragon);
            if (beamAbility.tryAbility()) {
                sendAbilityMessage(LightningDragonEntity.LIGHTNING_BEAM_ABILITY);
                return true;
            }
            
            // Only use burst if target has high health (strategic ultimate)
            if (shouldUseUltimate()) {
                Ability<LightningDragonEntity> burstAbility = LightningDragonEntity.LIGHTNING_BURST_ABILITY.createAbility(dragon);
                if (burstAbility.tryAbility()) {
                    sendAbilityMessage(LightningDragonEntity.LIGHTNING_BURST_ABILITY);
                    return true;
                }
            }
        } else if (distance >= BURST_RANGE) {
            // Long range - prefer beam, breath as secondary, burst only for tough targets
            Ability<LightningDragonEntity> beamAbility = LightningDragonEntity.LIGHTNING_BEAM_ABILITY.createAbility(dragon);
            if (beamAbility.tryAbility()) {
                sendAbilityMessage(LightningDragonEntity.LIGHTNING_BEAM_ABILITY);
                return true;
            }
            
            // Secondary: breath attack
            Ability<LightningDragonEntity> breathAbility = LightningDragonEntity.LIGHTNING_BREATH_ABILITY.createAbility(dragon);
            if (breathAbility.tryAbility()) {
                sendAbilityMessage(LightningDragonEntity.LIGHTNING_BREATH_ABILITY);
                return true;
            }
            
            // Ultimate: burst only for tough targets
            if (shouldUseUltimate()) {
                Ability<LightningDragonEntity> burstAbility = LightningDragonEntity.LIGHTNING_BURST_ABILITY.createAbility(dragon);
                if (burstAbility.tryAbility()) {
                    sendAbilityMessage(LightningDragonEntity.LIGHTNING_BURST_ABILITY);
                    return true;
                }
            }
        } else if (distance >= BREATH_RANGE) {
            // Medium range - breath attack preferred, beam as backup
            Ability<LightningDragonEntity> breathAbility = LightningDragonEntity.LIGHTNING_BREATH_ABILITY.createAbility(dragon);
            if (breathAbility.tryAbility()) {
                sendAbilityMessage(LightningDragonEntity.LIGHTNING_BREATH_ABILITY);
                return true;
            }
            
            // Fallback to beam for consistent attacks
            Ability<LightningDragonEntity> beamAbility = LightningDragonEntity.LIGHTNING_BEAM_ABILITY.createAbility(dragon);
            if (beamAbility.tryAbility()) {
                sendAbilityMessage(LightningDragonEntity.LIGHTNING_BEAM_ABILITY);
                return true;
            }
        } else if (distance >= MELEE_RANGE) {
            // Close range - try ground abilities or consider landing
            if (dragon.isFlying()) {
                // Try aerial abilities first
                Ability<LightningDragonEntity> wingLightning = LightningDragonEntity.WING_LIGHTNING_ABILITY.createAbility(dragon);
                if (wingLightning.tryAbility()) {
                    sendAbilityMessage(LightningDragonEntity.WING_LIGHTNING_ABILITY);
                    return true;
                }
                
                // Consider landing for better combat options
                if (dragon.flightController.canLandSafely()) {
                    dragon.flightController.initiateAggressiveLanding();
                    return false; // Will attack after landing
                }
            } else {
                // Ground-based close combat
                Ability<LightningDragonEntity> thunderStomp = LightningDragonEntity.THUNDER_STOMP_ABILITY.createAbility(dragon);
                if (thunderStomp.tryAbility()) {
                    sendAbilityMessage(LightningDragonEntity.THUNDER_STOMP_ABILITY);
                    return true;
                }
            }
            
            // Fallback to breath at close range
            Ability<LightningDragonEntity> breathAbility = LightningDragonEntity.LIGHTNING_BREATH_ABILITY.createAbility(dragon);
            if (breathAbility.tryAbility()) {
                sendAbilityMessage(LightningDragonEntity.LIGHTNING_BREATH_ABILITY);
                return true;
            }
        } else {
            // Very close range - melee combat
            if (dragon.isFlying()) {
                // Must land for melee
                if (dragon.flightController.canLandSafely()) {
                    dragon.flightController.initiateAggressiveLanding();
                }
                return false;
            } else {
                // Ground melee attacks
                Ability<LightningDragonEntity> electricBite = LightningDragonEntity.ELECTRIC_BITE_ABILITY.createAbility(dragon);
                if (electricBite.tryAbility()) {
                    sendAbilityMessage(LightningDragonEntity.ELECTRIC_BITE_ABILITY);
                    return true;
                }
                
                // Thunder stomp as backup
                Ability<LightningDragonEntity> thunderStomp = LightningDragonEntity.THUNDER_STOMP_ABILITY.createAbility(dragon);
                if (thunderStomp.tryAbility()) {
                    sendAbilityMessage(LightningDragonEntity.THUNDER_STOMP_ABILITY);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Strategic ultimate ability usage decision-making
     */
    public boolean shouldUseUltimate() {
        LivingEntity target = dragon.getTarget();
        if (target == null) return false;
        
        // Check if ultimate is on cooldown (Lightning Burst specifically)
        Ability<LightningDragonEntity> burstAbility = LightningDragonEntity.LIGHTNING_BURST_ABILITY.createAbility(dragon);
        if (!burstAbility.tryAbility()) return false; // Still on cooldown or can't be used
        
        // Only use ultimate for tough targets with high health
        float targetHealthPercent = target.getHealth() / target.getMaxHealth();
        
        // Use ultimate if:
        // - Target has >75% health (fresh fight)
        // - Target has >40 max health (strong enemy)  
        // - Combat has been going on for a while (frustrated dragon)
        return targetHealthPercent > 0.75f || 
               target.getMaxHealth() > 40.0f ||
               (dragon.tickCount % 600 == 0); // Every 30 seconds as desperation move
    }

    /**
     * Validates current target and removes invalid ones
     */
    public void validateCurrentTarget() {
        LivingEntity currentTarget = dragon.getTarget();
        if (currentTarget instanceof Player player) {
            if (player.isCreative() || (dragon.isTame() && dragon.isOwnedBy(player))) {
                dragon.setTarget(null);
                forceEndActiveAbility();
            }
        }
    }

    /**
     * Checks if dragon can use abilities right now
     */
    public boolean canUseAbility() {
        return abilityCooldown <= 0 && (activeAbility == null || activeAbility.canCancelActiveAbility());
    }

    /**
     * Sends ability activation message and manages cooldowns
     */
    public void sendAbilityMessage(AbilityType<LightningDragonEntity, ?> abilityType) {
        if (canUseAbility()) {
            @SuppressWarnings("unchecked")
            AbilityType<LightningDragonEntity, Ability<LightningDragonEntity>> castType =
                    (AbilityType<LightningDragonEntity, Ability<LightningDragonEntity>>) abilityType;

            Ability<LightningDragonEntity> newAbility = castType.createAbility(dragon);

            if (newAbility.tryAbility()) {
                setActiveAbility(newAbility);
                
                // Set longer cooldown for ultimate abilities
                if (abilityType == LightningDragonEntity.LIGHTNING_BURST_ABILITY) {
                    abilityCooldown = ULTIMATE_COOLDOWN_TICKS; // 60 seconds for ultimate
                } else {
                    abilityCooldown = ABILITY_COOLDOWN_TICKS; // 3 seconds for normal abilities
                }

                // Send packet to clients
                if (!dragon.level().isClientSide) {
                    NetworkHandler.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> dragon),
                            new MessageDragonUseAbility(dragon.getId(), abilityType.getName())
                    );
                }
            }
        }
    }

    /**
     * Gets the currently active ability
     */
    public Ability<LightningDragonEntity> getActiveAbility() {
        return activeAbility;
    }

    /**
     * Gets the type of the currently active ability
     */
    public AbilityType<LightningDragonEntity, ?> getActiveAbilityType() {
        return activeAbility != null ? activeAbility.getAbilityType() : null;
    }

    /**
     * Sets the active ability, properly ending the previous one
     */
    public void setActiveAbility(Ability<LightningDragonEntity> ability) {
        if (this.activeAbility != null) {
            this.activeAbility.end();
        }
        this.activeAbility = ability;
        if (ability != null) {
            ability.start();
        }
    }

    /**
     * Force ends the active ability and cleans up combat state
     */
    public void forceEndActiveAbility() {
        if (activeAbility != null && activeAbility.isUsing()) {
            activeAbility.end();
            setActiveAbility(null);
        }

        // Clean up attack states
        dragon.setAttacking(false);
        dragon.setHasLightningTarget(false);

        // Reset ability cooldown to prevent immediate re-use
        if (abilityCooldown < 20) {
            abilityCooldown = 20; // Brief cooldown after forced end
        }
    }

    /**
     * Ticks the combat manager - handles active abilities and cooldowns
     */
    public void tick() {
        // Ability system tick
        if (activeAbility != null) {
            if (activeAbility.isUsing()) {
                activeAbility.tick();
            } else {
                activeAbility = null;
            }
        }
        
        // Cooldown management
        if (abilityCooldown > 0) {
            abilityCooldown--;
        }
    }

    /**
     * Gets current ability cooldown
     */
    public int getAbilityCooldown() {
        return abilityCooldown;
    }

    /**
     * Sets ability cooldown (for save/load)
     */
    public void setAbilityCooldown(int cooldown) {
        this.abilityCooldown = cooldown;
    }
}