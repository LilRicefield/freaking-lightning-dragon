package com.leon.lightningdragon.ai.goals;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Simple ground wandering for Lightning Dragon when not flying
 */
public class DragonGroundWanderGoal extends RandomStrollGoal {

    private final LightningDragonEntity dragon;

    public DragonGroundWanderGoal(LightningDragonEntity dragon, double speed, int interval) {
        super(dragon, speed, interval);
        this.dragon = dragon;
    }

    @Override
    public boolean canUse() {
        // Only wander on ground when not flying
        if (dragon.isFlying()) {
            return false;
        }

        // Don't interfere with important behaviors
        if (dragon.isOrderedToSit() || dragon.isVehicle() || dragon.isPassenger()) {
            return false;
        }

        // Don't wander during combat
        if (dragon.getTarget() != null && dragon.getTarget().isAlive()) {
            return false;
        }

        // If tamed, don't wander too far from owner
        if (dragon.isTame()) {
            var owner = dragon.getOwner();
            if (owner != null && dragon.distanceToSqr(owner) < 8.0 * 8.0) {
                return false; // Stay close to owner
            }
        }

        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        // Stop if we start flying
        if (dragon.isFlying()) {
            return false;
        }

        // Stop if combat starts
        if (dragon.getTarget() != null && dragon.getTarget().isAlive()) {
            return false;
        }

        // Stop if ordered to sit
        if (dragon.isOrderedToSit()) {
            return false;
        }

        return super.canContinueToUse();
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        // If tamed and owner is far, bias movement towards owner
        if (dragon.isTame()) {
            var owner = dragon.getOwner();
            if (owner != null && dragon.distanceToSqr(owner) > 20.0 * 20.0) {
                // Move generally towards owner but not directly (maintain some independence)
                return DefaultRandomPos.getPosTowards(
                        this.mob,
                        16, // range
                        7,  // vertical range
                        owner.position(),
                        (double) ((float) Math.PI / 3F) // 60-degree cone towards owner
                );
            }
        }

        // Default random wandering
        return DefaultRandomPos.getPos(this.mob, 20, 8); // Slightly larger range for a dragon
    }
}