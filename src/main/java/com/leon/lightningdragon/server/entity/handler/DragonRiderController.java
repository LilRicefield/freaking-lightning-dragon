package com.leon.lightningdragon.server.entity.handler;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
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
    // ===== SEAT TUNING CONSTANTS =====
    // Baseline vertical offset relative to dragon height
    private static final double SEAT_BASE_FACTOR = 0.50D; // 0.0..1.0 of bbHeight
    // Additional vertical lift to avoid clipping
    private static final double SEAT_LIFT = 0.2D;
    // Forward/back relative to body (blocks). +forward = toward head, - = toward tail
    private static final double SEAT_FORWARD = 3.5D;
    // Sideways relative to body (blocks). +side = to the dragon's right, - = left
    private static final double SEAT_SIDE = 0.00D;

    // ===== FLIGHT VERTICAL RATES =====
    // Up/down rates while flying controlled by keybinds
    private static final double ASCEND_RATE = 0.15D;
    private static final double DESCEND_RATE = 0.30D; // faster descent

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
            // Flying movement – NO pitch-based vertical movement.
            // Vertical is controlled exclusively by ascend/descend keybinds.
            return new Vec3(player.xxa * 0.4F, 0.0F, player.zza * 1.0F * f);
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
                delta = delta.add(0, ASCEND_RATE, 0);
            } else if (dragon.isGoingDown()) {
                delta = delta.add(0, -DESCEND_RATE, 0);
            }

            dragon.move(MoverType.SELF, delta);
            // Less friction for more responsive flight
            dragon.setDeltaMovement(delta.scale(0.91D));
            dragon.calculateEntityAnimation(true);
        }
    }

    // ===== RIDING SUPPORT =====
    
    public double getPassengersRidingOffset() {
        return (double) dragon.getBbHeight() * SEAT_BASE_FACTOR;
    }
    
    public void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction moveFunction) {
        if (dragon.hasPassenger(passenger)) {
            // Stable, server-deterministic placement using body yaw and fixed offsets
            double offsetY = getPassengersRidingOffset() + SEAT_LIFT; // lift to avoid clipping
            double forward = SEAT_FORWARD; // toward head
            double side = SEAT_SIDE;    // to the right if positive
            double rad = Math.toRadians(dragon.yBodyRot);
            double dx = -Math.sin(rad) * forward + Math.cos(rad) * side;
            double dz =  Math.cos(rad) * forward + Math.sin(rad) * side;
            moveFunction.accept(passenger, dragon.getX() + dx, dragon.getY() + offsetY, dragon.getZ() + dz);
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
