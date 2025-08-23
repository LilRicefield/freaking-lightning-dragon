package com.leon.lightningdragon.util;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class SimpleDragonAiming {

    /**
     * Just make the dragon face the right general direction
     * No fancy prediction needed - the spread will handle the rest
     */
    public static Vec3 getGeneralDirection(LightningDragonEntity dragon) {
        LivingEntity target = dragon.getTarget();

        if (dragon.isVehicle() && dragon.getControllingPassenger() instanceof Player player) {
            // Player controlled - just use their look direction
            return player.getLookAngle();
        } else if (target != null) {
            // AI controlled - basic leading for fast targets
            Vec3 dragonPos = dragon.getEyePosition();
            Vec3 targetPos = target.getEyePosition();

            // Simple leading - if target is moving fast, aim slightly ahead
            Vec3 targetVel = target.getDeltaMovement();
            if (targetVel.lengthSqr() > 0.3) { // Only if moving fast
                double distance = dragonPos.distanceTo(targetPos);
                double timeToHit = distance / 1.2; // lightning ball speed
                targetPos = targetPos.add(targetVel.scale(timeToHit * 0.5)); // 50% leading
            }

            return targetPos.subtract(dragonPos).normalize();
        } else {
            // No target - just forward
            return dragon.getLookAngle();
        }
    }

    /**
     * Adjust spread based on distance - tighter for far, wider for close
     */
    public static float getSmartSpread(double distanceToTarget) {
        if (distanceToTarget > 20) {
            return 0.05f; // Tight spread for long range
        } else if (distanceToTarget > 10) {
            return 0.1f;  // Medium spread
        } else {
            return 0.2f;  // Wide spread for close range carnage
        }
    }

    /**
     * Make dragon look at target smoothly (way simpler than my previous mess)
     */
    public static void updateDragonLook(LightningDragonEntity dragon) {
        LivingEntity target = dragon.getTarget();
        if (target == null) return;

        // Just use the existing look control - it's fine
        dragon.getLookControl().setLookAt(target, 30f, 30f);
    }
}