//FOR FUTURE USE CASEs LIKE NEW DRAGONS??? more zap van dinks or some fire dragon named Lava Tickler, idk

package com.leon.lightningdragon.entity.base;

import com.leon.lightningdragon.ai.abilities.Ability;
import com.leon.lightningdragon.ai.abilities.AbilityType;
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
    
    // Ability system
    private Ability<?> activeAbility = null;
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

    // ===== ABILITY SYSTEM =====
    /**
     * Get the currently active ability, if any
     */
    @SuppressWarnings("unchecked")
    public <T extends DragonEntity> Ability<T> getActiveAbility() {
        return (Ability<T>) activeAbility;
    }

    /**
     * Set the active ability
     */
    public void setActiveAbility(Ability<?> ability) {
        this.activeAbility = ability;
    }

    /**
     * Check if dragon can use abilities (not on cooldown, not already using one)
     */
    public boolean canUseAbility() {
        return abilityCooldown <= 0 && (activeAbility == null || !activeAbility.isUsing());
    }

    /**
     * Try to activate an ability
     */
    public <T extends DragonEntity> boolean tryActivateAbility(AbilityType<T, ?> abilityType) {
        if (!canUseAbility()) return false;
        
        @SuppressWarnings("unchecked")
        T castedThis = (T) this;
        Ability<T> ability = abilityType.createAbility(castedThis);
        
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
     * Tick ability system
     */
    protected void tickAbilities() {
        // Handle ability cooldown
        if (abilityCooldown > 0) {
            abilityCooldown--;
        }
        
        // Tick active ability
        if (activeAbility != null) {
            activeAbility.tick();
            
            // Check if ability should finish
            if (!activeAbility.isUsing() || activeAbility.isFinished()) {
                activeAbility.end();
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