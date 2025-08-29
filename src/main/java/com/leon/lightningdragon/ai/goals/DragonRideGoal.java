package com.leon.lightningdragon.ai.goals;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Dragon riding goal that takes complete control when a player is riding
 * Based on Ice & Fire's DragonAIRide but adapted for 1.20.1 and your banking system
 */
public class DragonRideGoal extends Goal {
    
    private final LightningDragonEntity dragon;
    private Player ridingPlayer;
    
    // Movement constants
    private static final double BASE_FLIGHT_SPEED = 1.8;
    private static final double SIDEWAYS_SPEED_MODIFIER = 0.25;
    private static final double BACKWARD_SPEED_MODIFIER = 0.15;
    private static final double VERTICAL_SPEED_MODIFIER = 10.0;
    
    public DragonRideGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    
    @Override
    public boolean canUse() {
        ridingPlayer = dragon.getRidingPlayer();
        return ridingPlayer != null && dragon.isTame() && dragon.isOwnedBy(ridingPlayer);
    }
    
    @Override
    public boolean canContinueToUse() {
        return canUse() && ridingPlayer.isAlive() && !dragon.isOrderedToSit();
    }
    
    @Override
    public void start() {
        // Stop all other navigation when riding starts
        dragon.getNavigation().stop();
        dragon.setTarget(null);
        
        // Clear any existing goals that might interfere
        dragon.forceEndActiveAbility();
        
        // Ensure dragon is not sitting
        if (dragon.isOrderedToSit()) {
            dragon.setOrderedToSit(false);
        }
    }
    
    @Override
    public void stop() {
        // Reset control states when riding ends
        dragon.setGoingUp(false);
        dragon.setGoingDown(false);
        dragon.setRiderAttacking(false);
        
        // Clear navigation
        dragon.getNavigation().stop();
    }
    
    @Override
    public void tick() {
        if (ridingPlayer == null) return;
        
        // Always stop normal navigation while being ridden
        dragon.getNavigation().stop();
        dragon.setTarget(null);
        
        // Handle movement input
        handleMovementInput();
        
        // Handle look direction for both player and dragon
        handleLookControl();
    }
    
    private void handleMovementInput() {
        // Get current position
        double x = dragon.getX();
        double y = dragon.getY();
        double z = dragon.getZ();
        
        // Calculate base speed modified by dragon's flight speed attribute
        double speed = BASE_FLIGHT_SPEED * dragon.getFlightSpeedModifier();
        
        // Get player's look direction
        Vec3 lookVec = ridingPlayer.getLookAngle();
        
        // This goal is deprecated - movement now handled by getRiddenInput()
        // Remove references to old input system
        float forward = 0f; // Placeholder
        float strafe = 0f; // Placeholder
        
        // Handle directional movement based on input (Ice & Fire logic)
        if (forward < 0) {
            // Moving backward
            lookVec = lookVec.yRot((float) Math.PI);
        } else if (strafe > 0) {
            // Moving right (strafe)
            lookVec = lookVec.yRot((float) Math.PI * 0.5f);
        } else if (strafe < 0) {
            // Moving left (strafe)  
            lookVec = lookVec.yRot((float) Math.PI * -0.5f);
        }
        
        // Apply speed modifiers (Ice & Fire logic)
        if (Math.abs(strafe) > 0.0) {
            speed *= SIDEWAYS_SPEED_MODIFIER;
        }
        if (forward < 0.0) {
            speed *= BACKWARD_SPEED_MODIFIER;
        }
        
        // Handle vertical movement
        if (dragon.isGoingUp()) {
            lookVec = lookVec.add(0, 1, 0);
        } else if (dragon.isGoingDown()) {
            lookVec = lookVec.add(0, -1, 0);
        }
        
        // Apply horizontal movement (Ice & Fire logic)
        if (forward != 0 || strafe != 0 || dragon.isFlying()) {
            x += lookVec.x * 10;
            z += lookVec.z * 10;
        }
        
        // Apply vertical movement (Ice & Fire logic)
        if ((dragon.isFlying() || dragon.isHovering()) && (dragon.isGoingUp() || dragon.isGoingDown())) {
            y += lookVec.y * VERTICAL_SPEED_MODIFIER;
        }
        
        // Handle automatic descent (Ice & Fire logic)
        if (lookVec.y == -1 || (!dragon.isFlying() && !dragon.isHovering() && !dragon.onGround())) {
            y -= 1;
        }
        
        // Apply smooth banking during turns
        if (dragon.isFlying()) {
            updateBankingFromInput();
        }
        
        // Set the movement target
        dragon.getMoveControl().setWantedPosition(x, y, z, speed);
    }
    
    private void updateBankingFromInput() {
        // This goal is deprecated - banking now handled by getRiddenInput() system
        // Placeholder method
        float targetBanking = 0.0f;
        float strafe = 0f; // Placeholder
        
        if (Math.abs(strafe) > 0.1f) {
            // Banking based on strafe input
            float maxBanking = 25.0f; // Maximum banking angle
            targetBanking = strafe * maxBanking;
        }
        
        // Banking now handled by Molang query.yaw_speed in animation.json
    }
    
    private void handleLookControl() {
        // Make dragon follow player's look direction smoothly
        float targetYaw = ridingPlayer.getYRot();
        float targetPitch = ridingPlayer.getXRot();
        
        // Apply smooth rotation towards target
        dragon.setYRot(Mth.approachDegrees(dragon.getYRot(), targetYaw, 8.0f));
        dragon.setXRot(Mth.approachDegrees(dragon.getXRot(), targetPitch, 6.0f));
        
        // Keep body and head aligned during riding
        dragon.yBodyRot = dragon.getYRot();
        dragon.yHeadRot = dragon.getYRot();
    }
    
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}