package com.leon.lightningdragon.server.entity.controller;

import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Handles all player interaction logic for the Lightning Dragon including:
 * - Taming mechanics and validation
 * - Sitting behavior and commands
 * - Player interaction events and responses
 * - Food recognition and feeding
 */
public class DragonInteractionHandler {
    private final LightningDragonEntity dragon;

    public DragonInteractionHandler(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }

    /**
     * Handles player interactions with the dragon
     */
    public InteractionResult handlePlayerInteraction(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!dragon.isTame()) {
            return handleWildDragonInteraction(player, hand, itemstack);
        } else {
            return handleTamedDragonInteraction(player, hand, itemstack);
        }
    }

    /**
     * Handles interaction with wild (untamed) dragons
     */
    private InteractionResult handleWildDragonInteraction(Player player, InteractionHand hand, ItemStack itemstack) {
        if (isFood(itemstack)) {
            if (!player.getAbilities().instabuild) itemstack.shrink(1);

            if (dragon.getRandom().nextInt(3) == 0) {
                // Successful taming
                dragon.tame(player);
                dragon.setOrderedToSit(true);
                dragon.level().broadcastEntityEvent(dragon, (byte) 7);
                return InteractionResult.SUCCESS;
            } else {
                // Failed taming attempt
                dragon.level().broadcastEntityEvent(dragon, (byte) 6);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Handles interaction with tamed dragons
     */
    private InteractionResult handleTamedDragonInteraction(Player player, InteractionHand hand, ItemStack itemstack) {
        if (player.equals(dragon.getOwner())) {
            // Flight toggle - crouch + item in main hand
            if (player.isCrouching() && !itemstack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
                toggleFlightMode();
                return InteractionResult.SUCCESS;
            }
            // Sit toggle - empty hand
            else if (itemstack.isEmpty()) {
                toggleSitting();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Toggles between flying and landing
     */
    private void toggleFlightMode() {
        if (dragon.isFlying()) {
            dragon.setLanding(true);
        } else {
            dragon.setFlying(true);
        }
    }

    /**
     * Toggles sitting state
     */
    private void toggleSitting() {
        dragon.setOrderedToSit(!dragon.isOrderedToSit());
    }

    /**
     * Handles sitting behavior and state management
     */
    public void handleSittingLogic() {
        if (dragon.isOrderedToSit()) {
            // Force landing if flying when ordered to sit
            if (dragon.isFlying()) {
                dragon.setLanding(true);
            }
            dragon.setRunning(false);
            dragon.getNavigation().stop();
        } else {
            // Reset sit progress when standing up
            if (!dragon.level().isClientSide) {
                dragon.sitProgress = 0;
                dragon.getEntityData().set(LightningDragonEntity.DATA_SIT_PROGRESS, 0.0f);
            }
        }
    }

    /**
     * Handles entity events related to taming
     */
    public void handleEntityEvent(byte eventId) {
        if (eventId == 6) {
            // Failed taming - show smoke particles ONLY
            if (dragon.level().isClientSide) {
                showFailedTamingParticles();
            }
            // Don't call super to avoid default TamableAnimal behavior
        } else if (eventId == 7) {
            // Successful taming - show hearts only
            if (dragon.level().isClientSide) {
                showSuccessfulTamingParticles();
            }
            // Don't call super to avoid default TamableAnimal behavior
        }
        // For other events, let the main entity handle them
    }

    /**
     * Shows smoke particles for failed taming attempts
     */
    private void showFailedTamingParticles() {
        for (int i = 0; i < 7; ++i) {
            double d0 = dragon.getRandom().nextGaussian() * 0.02D;
            double d1 = dragon.getRandom().nextGaussian() * 0.02D;
            double d2 = dragon.getRandom().nextGaussian() * 0.02D;
            dragon.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    dragon.getRandomX(1.0D), dragon.getRandomY() + 0.5D, dragon.getRandomZ(1.0D), d0, d1, d2);
        }
    }

    /**
     * Shows heart particles for successful taming
     */
    private void showSuccessfulTamingParticles() {
        for (int i = 0; i < 7; ++i) {
            double d0 = dragon.getRandom().nextGaussian() * 0.02D;
            double d1 = dragon.getRandom().nextGaussian() * 0.02D;
            double d2 = dragon.getRandom().nextGaussian() * 0.02D;
            dragon.level().addParticle(net.minecraft.core.particles.ParticleTypes.HEART,
                    dragon.getRandomX(1.0D), dragon.getRandomY() + 0.5D, dragon.getRandomZ(1.0D), d0, d1, d2);
        }
    }

    /**
     * Determines if an item is suitable food for taming
     */
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT);
    }

    /**
     * Handles melee combat target interaction
     */
    public boolean handleMeleeAttack(Entity target) {
        // Use ability system for melee attacks
        if (dragon.combatManager.canUseAbility()) {
            // TODO: Use new Dragon ability system for electric bite
            return true;
        }
        return false;
    }

    /**
     * Updates sitting progress for smooth animations
     */
    public void updateSittingProgress() {
        if (dragon.isOrderedToSit() && dragon.sitProgress < dragon.maxSitTicks()) {
            dragon.sitProgress++;
            if (!dragon.level().isClientSide) {
                dragon.getEntityData().set(LightningDragonEntity.DATA_SIT_PROGRESS, dragon.sitProgress);
            }
        }
        if (!dragon.isOrderedToSit() && dragon.sitProgress > 0F) {
            dragon.sitProgress--;
            if (!dragon.level().isClientSide) {
                dragon.getEntityData().set(LightningDragonEntity.DATA_SIT_PROGRESS, dragon.sitProgress);
            }
        }
    }

    /**
     * Gets sitting progress for animation interpolation
     */
    public float getSitProgress(float partialTicks) {
        return dragon.getSitProgress(partialTicks);
    }

    /**
     * Checks if dragon is currently in sitting pose
     */
    public boolean isInSittingPose() {
        return dragon.isOrderedToSit() && !(dragon.isVehicle() || dragon.isPassenger() || dragon.isFlying());
    }
}