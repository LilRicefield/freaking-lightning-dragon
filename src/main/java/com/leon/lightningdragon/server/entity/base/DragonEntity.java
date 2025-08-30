//FOR FUTURE USE CASEs LIKE NEW DRAGONS??? more zap van dinks or some fire dragon named Lava Tickler, idk

package com.leon.lightningdragon.server.entity.base;

import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for all dragon entities in the Lightning Dragon mod.
 * Provides common GeckoLib integration, ability management, and basic dragon functionality.
 */
public abstract class DragonEntity extends TamableAnimal implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
    // Dragon ability system
    private DragonAbility<?> activeAbility = null;
    private int abilityCooldown = 0;
    
    protected DragonEntity(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public abstract void registerControllers(AnimatableManager.ControllerRegistrar controllers);

    // ===== DRAGON ABILITY SYSTEM =====
    /**
     * Get the currently active Dragon ability, if any
     */
    @SuppressWarnings("unchecked")
    public <T extends DragonEntity> DragonAbility<T> getActiveAbility() {
        return (DragonAbility<T>) activeAbility;
    }

    /**
     * Set the active Dragon ability
     */
    public void setActiveAbility(DragonAbility<?> ability) {
        this.activeAbility = ability;
    }

    /**
     * Check if dragon can use abilities (not on cooldown, not already using one)
     */
    public boolean canUseAbility() {
        return abilityCooldown <= 0 && (activeAbility == null || !activeAbility.isUsing());
    }

    /**
     * Try to activate a Dragon ability
     */
    public <T extends DragonEntity> boolean tryActivateAbility(DragonAbilityType<T, ?> abilityType) {
        if (!canUseAbility()) return false;
        
        @SuppressWarnings("unchecked")
        T castedThis = (T) this;
        DragonAbility<T> ability = abilityType.makeInstance(castedThis);
        
        if (ability.tryAbility()) {
            setActiveAbility(ability);
            ability.start();
            return true;
        }
        return false;
    }

    /**
     * Trigger an animation on this entity.
     * Override in subclasses to provide proper animation handling.
     */
    public void playAnimation(RawAnimation animation) {
        // Default implementation - subclasses should override this
        // The animation parameter contains the animation data, implementation is entity-specific
    }

    /**
     * Tick Dragon ability system
     */
    protected void tickAbilities() {
        // Handle ability cooldown
        if (abilityCooldown > 0) {
            abilityCooldown--;
        }
        
        // Tick active ability
        if (activeAbility != null) {
            activeAbility.tick();
            
            // Check if ability finished
            if (!activeAbility.isUsing()) {
                activeAbility = null;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        tickAbilities();
    }

    // ===== ABSTRACT METHODS =====

    /**
     * Get a bone/locator position from the GeckoLib model.
     * Override this in subclasses to provide proper bone position lookup.
     */
    public Vec3 getBonePosition(String boneName) {
        // Default implementation - subclasses should override this
        return Vec3.ZERO;
    }

    /**
     * Get head position for targeting and ability positioning.
     * Override this in subclasses to provide accurate head positioning.
     */
    public abstract Vec3 getHeadPosition();

    /**
     * Get mouth position for beam/breath attacks.
     * Override this in subclasses to provide accurate mouth positioning.
     */
    public abstract Vec3 getMouthPosition();
}