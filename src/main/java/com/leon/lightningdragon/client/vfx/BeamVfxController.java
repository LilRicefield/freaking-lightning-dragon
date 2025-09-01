package com.leon.lightningdragon.client.vfx;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Client-only helper that manages lightweight particle dressing for the lightning beam.
 * Keeps core ribbon rendering in the layer; handles sparks and impact rings as particles.
 */
public class BeamVfxController {
    private static final Map<Integer, BeamVfxController> ACTIVE = new HashMap<>();

    public static BeamVfxController get(LightningDragonEntity dragon) {
        return ACTIVE.computeIfAbsent(dragon.getId(), id -> new BeamVfxController());
    }

    public static void remove(LightningDragonEntity dragon) {
        ACTIVE.remove(dragon.getId());
    }

    private final Random random = new Random();
    private Vec3 lastEnd = null;
    private int ticks;
    private int ringCooldown;

    public void update(LightningDragonEntity dragon, Vec3 start, Vec3 end) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        ticks++;

        // If endpoint changed noticeably, spawn a quick impact ring burst
        if (shouldSpawnRing(end)) {
            spawnImpactRing(level, end, start);
            ringCooldown = 6; // short cooldown to avoid spam
        }
        if (ringCooldown > 0) ringCooldown--;

        lastEnd = end;
    }

    public void stop(LightningDragonEntity dragon) {
        // Currently particles are short-lived; nothing to clean up.
        remove(dragon);
    }

    private boolean shouldSpawnRing(Vec3 end) {
        if (ringCooldown > 0) return false;
        if (lastEnd == null) return true;
        return lastEnd.distanceToSqr(end) > 0.20 * 0.20; // ~0.2 blocks
    }

    private static float fract(float x) {
        float fx = x - (float) Math.floor(x);
        return fx < 0 ? fx + 1f : fx;
    }

    private void spawnImpactRing(Level level, Vec3 center, Vec3 start) {
        // Build a ring in the plane perpendicular to the beam direction
        Vec3 dir = center.subtract(start);
        if (dir.lengthSqr() < 1e-6) return;
        dir = dir.normalize();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-5) right = dir.cross(new Vec3(1, 0, 0));
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();

        double radius = 0.6;
        int points = 14;
        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * Mth.TWO_PI;
            Vec3 rim = right.scale(Math.cos(a) * radius).add(up.scale(Math.sin(a) * radius));
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    center.x + rim.x, center.y + rim.y, center.z + rim.z,
                    rim.x * 0.02, rim.y * 0.02, rim.z * 0.02);
        }
    }
}
