package com.leon.lightningdragon.server.ai.goals;

import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * - picks the projectile MOST moving toward us (dot product)
 * - chooses the better lateral side (left/right)
 * - predicts impact line and initiates a multi-tick dodge burst via entity.beginDodge(...)
 */
public class DragonDodgeGoal extends Goal {
    private final LightningDragonEntity dragon;

    // tuning
    private static final double SCAN_RADIUS_H = 30.0;
    private static final double SCAN_RADIUS_V = 15.0;
    private static final int    SCAN_INTERVAL = 4;     // ticks
    private static final int    DODGE_TICKS   = 8;     // how long entity applies impulse internally
    private static final int    COOLDOWN      = 12;    // min gap between dodges (ticks)

    private static final double DOT_THREAT    = 0.80;  // how "toward me" the projectile must be (0..1)
    private static final double MIN_SPEED2    = 0.0025; // ignore near-stationary projectiles

    // Dodge impulse constants - flight only
    private static final double DODGE_LAT_IMPULSE = 0.80;
    private static final double DODGE_UP_IMPULSE  = 0.40;

    private long nextScanTime = 0L;
    private long nextAllowedDodgeTime = 0L; // <-- time-based cooldown

    private List<Projectile> getCachedThreats() {
        return dragon.getCachedNearbyProjectiles().stream()
                .filter(p -> p.isAlive() &&
                        p.getOwner() != dragon &&
                        p.getOwner() != dragon.getOwner() &&
                        p.getDeltaMovement().lengthSqr() > MIN_SPEED2)
                .collect(Collectors.toList());
    }

    public DragonDodgeGoal(LightningDragonEntity dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!dragon.isAlive()) return false;
        if (dragon.isTame() && dragon.isVehicle()) return false;
        if (dragon.stateManager.isDodging()) return false;
        if (!dragon.stateManager.isFlying()) return false;

        long now = dragon.level().getGameTime();
        if (now < nextScanTime) return false;
        nextScanTime = now + SCAN_INTERVAL;

        if (now < nextAllowedDodgeTime) return false;

        // Use cached nearby projectiles for better performance
        List<Projectile> threats = getCachedThreats().stream()
                .filter(p -> DragonMathUtil.hasLineOfSight(dragon, p)) // Use LOS function
                .collect(Collectors.toList());

        Projectile mostThreatening = mostMovingTowardMeFromList(threats, dragon);
        if (mostThreatening == null) return false;

        Vec3 dv = new Vec3(
                mostThreatening.getX() - mostThreatening.xo,
                mostThreatening.getY() - mostThreatening.yo,
                mostThreatening.getZ() - mostThreatening.zo
        );

        if (dv.lengthSqr() < MIN_SPEED2) return false;

        // Use dodge calculation function instead of manually doing it
        Vec3 dodgeDirection = DragonMathUtil.calculateDodgeDirection(dragon, mostThreatening);

        if (dodgeDirection.equals(Vec3.ZERO)) return false;

        // Since we only dodge when flying, use flight constants directly
        Vec3 dodgeVec = new Vec3(
                dodgeDirection.x * DODGE_LAT_IMPULSE,
                DODGE_UP_IMPULSE,
                dodgeDirection.z * DODGE_LAT_IMPULSE
        );

        dodgeVec = DragonMathUtil.clampVectorLength(dodgeVec, 1.5); // Max dodge strength

        dragon.stateManager.beginDodge(dodgeVec, DODGE_TICKS);
        nextAllowedDodgeTime = now + COOLDOWN;
        return true;
    }

    @Override
    public void start() {
        dragon.getNavigation().stop();
        // Trigger dodge animation every time
        dragon.triggerDodgeAnimation();
        // Play annoyed sound when dodging attacks
        dragon.playAnnoyedSound();
    }

    @Override public boolean canContinueToUse() { return false; }
    @Override public void stop() { /* nothing; cooldown is time-based */ }

    // ========== HELPERS ==========

    private Vec3 guessProjectileDestination(Projectile projectile) {
        Vec3 from = projectile.position();
        Vec3 vel  = projectile.getDeltaMovement();
        if (vel.lengthSqr() < 1.0e-6) return from;
        Vec3 to   = from.add(vel.scale(50)); // long ray
        return projectile.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile)).getLocation();
    }

    @Nullable
    private <T extends Projectile> T mostMovingTowardMe(Class<? extends T> cls,
                                                        Predicate<? super T> pred,
                                                        LivingEntity me,
                                                        AABB box) {
        return mostMovingTowardMeFromList(me.level().getEntitiesOfClass(cls, box, pred), me);
    }

    private <T extends Projectile> T mostMovingTowardMeFromList(List<? extends T> entities, LivingEntity me) {
        double best = DOT_THREAT;
        T bestEnt = null;
        for (T p : entities) {
            Vec3 dv = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
            double ls = dv.lengthSqr();
            if (ls < MIN_SPEED2) continue;
            double dot = dv.normalize().dot(me.position().subtract(p.position()).normalize());
            if (dot > best) { best = dot; bestEnt = p; }
        }
        return bestEnt;
    }
}