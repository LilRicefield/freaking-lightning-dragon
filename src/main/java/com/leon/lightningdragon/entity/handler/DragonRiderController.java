package com.leon.lightningdragon.entity.handler;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles all riding mechanics for the Lightning Dragon
 * Including input processing, movement, and banking calculations
 */
public class DragonRiderController {
    private final LightningDragonEntity dragon;
    
    public DragonRiderController(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }
    
    // ===== RIDING UTILITIES =====
    
    @Nullable
    public Player getRidingPlayer() {
        if (dragon.getControllingPassenger() instanceof Player player) {
            return player;
        }
        return null;
    }
    
    public boolean isBeingRidden() {
        return getRidingPlayer() != null;
    }
    
    public double getFlightSpeedModifier() {
        return dragon.getAttributeValue(Attributes.FLYING_SPEED);
    }
    
    // ===== RIDER INPUT PROCESSING =====
    
    /**
     * Processes rider input and converts to movement vector
     */
    public Vec3 getRiddenInput(Player player, Vec3 deltaIn) {
        float f = player.zza < 0.0F ? 0.5F : 1.0F;
        
        if (dragon.isFlying()) {
            // Flying movement - more responsive than ground, includes pitch influence
            Vec3 lookVec = player.getLookAngle();
            
            // Disable pitch influence during abilities that require hovering (like Lightning Beam)
            float y = 0.0F;
            if (!dragon.isHovering() && dragon.combatManager.getActiveAbility() == null) {
                y = (float) lookVec.y * 0.3F; // Gentle pitch influence only when not using abilities
            }
            
            return new Vec3(player.xxa * 0.4F, y, player.zza * 1.0F * f);
        } else {
            // Ground movement - no vertical component, responsive controls
            return new Vec3(player.xxa * 0.5F, 0.0D, player.zza * 0.9F * f);
        }
    }
    
    /**
     * Main rider tick method - handles rotation and banking
     */
    public void tickRidden(Player player, Vec3 travelVector) {
        // Clear target when being ridden to prevent AI interference
        dragon.setTarget(null);
        
        // Store previous rotation for banking calculation
        float prevYaw = dragon.getYRot();
        
        // Make dragon responsive to player look direction
        if (dragon.isFlying()) {
            // Flying: BYPASS THE SLOW MATH! Nearly instant response!
            float targetYaw = player.getYRot();
            float targetPitch = player.getXRot() * 0.5f; // Scale down pitch for stability
            
            // DIRECT ASSIGNMENT with tiny smoothing - no more slow curves!
            float currentYaw = dragon.getYRot();
            float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
            float newYaw = currentYaw + (yawDiff * 0.85f); // 85% instant response
            dragon.setYRot(newYaw);
            
            float currentPitch = dragon.getXRot();
            float pitchDiff = targetPitch - currentPitch;
            float newPitch = currentPitch + (pitchDiff * 0.8f); // 80% instant response
            dragon.setXRot(newPitch);
                
            // Update banking based on turning
            handleRiderBanking(dragon.getYRot(), prevYaw);
        } else {
            // Ground: Also direct response 
            float yawDiff = Math.abs(player.getYRot() - dragon.getYRot());
            if (player.zza != 0 || player.xxa != 0 || yawDiff > 5.0f) {
                float currentYaw = dragon.getYRot();
                float targetYaw = player.getYRot();
                float rawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
                float newYaw = currentYaw + (rawDiff * 0.75f); // 75% instant response
                dragon.setYRot(newYaw);
                dragon.setXRot(0); // Keep level on ground
            }
        }
        
        // Update body and head rotation
        dragon.yBodyRot = dragon.getYRot();
        dragon.yHeadRot = dragon.getYRot();
    }
    
    /**
     * Handle banking when player is riding and turning
     */
    private void handleRiderBanking(float currentYaw, float prevYaw) {
        if (!dragon.isFlying()) return;
        
        // Banking now handled by Molang query.yaw_speed in animation.json
    }
    
    /**
     * Calculate riding speed based on current state
     */
    public float getRiddenSpeed(Player rider) {
        if (dragon.isFlying()) {
            // Flying speed - use ONLY the attributed flying speed, no modifiers
            return (float) dragon.getAttributeValue(Attributes.FLYING_SPEED);
        } else {
            // Ground speed - use movement speed attribute with acceleration multipliers
            float baseSpeed = (float) dragon.getAttributeValue(Attributes.MOVEMENT_SPEED);
            
            if (dragon.isAccelerating()) {
                // L-Ctrl pressed - trigger run animation and boost speed
                dragon.setRunning(true);
                return baseSpeed * 0.9F;
            } else {
                // Normal ground speed - use walk animation, stop running
                dragon.setRunning(false);
                return baseSpeed * 0.7F;
            }
        }
    }
    
    // ===== RIDING MOVEMENT =====
    
    /**
     * Handle rider movement - called from travel() method
     */
    public void handleRiderMovement(Player player, Vec3 motion) {
        // Clear any AI navigation when being ridden
        if (dragon.getNavigation().getPath() != null) {
            dragon.getNavigation().stop();
        }
        
        if (dragon.isFlying()) {
            // Flying movement - handle like Ice & Fire dragons
            dragon.moveRelative(getRiddenSpeed(player), motion);
            Vec3 delta = dragon.getDeltaMovement();
            
            // Handle vertical movement from rider input
            if (dragon.isGoingUp()) {
                delta = delta.add(0, 0.15D, 0);
            } else if (dragon.isGoingDown()) {
                delta = delta.add(0, -0.15D, 0);
            }
            
            dragon.move(MoverType.SELF, delta);
            // Less friction for more responsive flight
            dragon.setDeltaMovement(delta.scale(0.91D));
            dragon.calculateEntityAnimation(true);
        } else {
            // Ground movement - call dragon's super.travel()
            // Note: We can't call super.travel() directly from here, so this will be handled in the main entity
        }
    }
    
    // ===== RIDING SUPPORT =====
    
    public double getPassengersRidingOffset() {
        return (double) dragon.getBbHeight() * 0.75D;
    }
    
    public void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction moveFunction) {
        if (dragon.hasPassenger(passenger)) {
            // Position the rider on the dragon's back
            double offsetY = getPassengersRidingOffset();
            Vec3 ridingPosition = dragon.position().add(0, offsetY, 0);
            moveFunction.accept(passenger, ridingPosition.x, ridingPosition.y, ridingPosition.z);
        }
    }
    
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        // Find a safe dismount location near the dragon
        Vec3 direction = dragon.getViewVector(1.0F);
        return dragon.position().add(direction.scale(2.0));
    }
    
    @Nullable 
    public LivingEntity getControllingPassenger() {
        Entity entity = dragon.getFirstPassenger();
        if (entity instanceof Player player && dragon.isTame() && dragon.isOwnedBy(player)) {
            return player;
        }
        return null;
    }
    
    /**
     * Forces the dragon to take off when being ridden. Called when player presses Space while on ground.
     */
    public void requestRiderTakeoff() {
        if (!dragon.isTame() || getRidingPlayer() == null || dragon.isFlying()) return;
        
        // Reset all flight states for a fresh takeoff
        dragon.timeFlying = 0;
        dragon.landingFlag = false;
        dragon.landingTimer = 0;
        
        // Initiate takeoff sequence
        dragon.setFlying(true);
        dragon.setTakeoff(true);
        dragon.setHovering(false);
        dragon.setLanding(false);
    }
}