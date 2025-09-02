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
    
    // Particle effect tuning constants
    private static final double IMPACT_RING_RADIUS = 0.6;           // Size of impact ring particles
    private static final int IMPACT_RING_POINTS = 14;               // Number of particles in impact ring
    private static final int EXPLOSION_PARTICLE_COUNT = 6;          // Number of explosion particles at impact
    private static final int IMPACT_SPARK_COUNT = 8;                // Number of electric sparks at impact
    private static final int MOUTH_SPARK_COUNT = 4;                 // Number of sparks from dragon's mouth
    private static final double SPARK_DISTANCE_INTERVAL = 2.0;      // Distance between beam sparkles
    private static final int BEAM_SPARKLE_COUNT_MIN = 2;            // Minimum sparkles per beam update
    private static final int BEAM_SPARKLE_COUNT_MAX = 4;            // Maximum sparkles per beam update
    private static final float CONTINUOUS_SPARK_CHANCE = 0.3F;      // Chance for continuous spark effects
    private static final int SPARKLE_UPDATE_INTERVAL = 3;          // Ticks between sparkle updates

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
            spawnExplosionParticles(level, end);
            spawnLightningParticles(level, start, dragon);
            if (random.nextFloat() < CONTINUOUS_SPARK_CHANCE) {
                spawnContinuousSparkEffects(level, start, end);
            }
            ringCooldown = 6; // short cooldown to avoid spam
        }
        if (ringCooldown > 0) ringCooldown--;

        // Continuous beam sparkles along the path
        if (ticks % SPARKLE_UPDATE_INTERVAL == 0) {
            spawnBeamSparkles(level, start, end);
        }

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

        double radius = IMPACT_RING_RADIUS;
        int points = IMPACT_RING_POINTS;
        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * Mth.TWO_PI;
            Vec3 rim = right.scale(Math.cos(a) * radius).add(up.scale(Math.sin(a) * radius));
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    center.x + rim.x, center.y + rim.y, center.z + rim.z,
                    rim.x * 0.02, rim.y * 0.02, rim.z * 0.02);
        }
    }

    private void spawnExplosionParticles(Level level, Vec3 center) {
        // Explosion particles at impact point like Tremorzilla
        for (int i = 0; i < EXPLOSION_PARTICLE_COUNT; i++) {
            Vec3 particleVec = center.add(
                    (random.nextFloat() - 0.5F) * 2.0F,
                    (random.nextFloat() - 0.5F) * 2.0F,
                    (random.nextFloat() - 0.5F) * 2.0F
            );
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    particleVec.x, particleVec.y, particleVec.z,
                    (random.nextFloat() - 0.5F) * 0.1F,
                    random.nextFloat() * 0.1F,
                    (random.nextFloat() - 0.5F) * 0.1F);
        }
        
        // Additional electric sparks for impact
        for (int i = 0; i < IMPACT_SPARK_COUNT; i++) {
            Vec3 particleVec = center.add(
                    (random.nextFloat() - 0.5F) * 1.5F,
                    (random.nextFloat() - 0.5F) * 1.5F,
                    (random.nextFloat() - 0.5F) * 1.5F
            );
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    particleVec.x, particleVec.y, particleVec.z,
                    (random.nextFloat() - 0.5F) * 0.05F,
                    (random.nextFloat() - 0.5F) * 0.05F,
                    (random.nextFloat() - 0.5F) * 0.05F);
        }
    }

    private void spawnLightningParticles(Level level, Vec3 start, LightningDragonEntity dragon) {
        // Lightning particles from the dragon's mouth like Tremorzilla
        for (int i = 0; i < MOUTH_SPARK_COUNT; i++) {
            Vec3 particlePos = start.add(
                    (random.nextFloat() - 0.5F) * 0.8F,
                    (random.nextFloat() - 0.5F) * 0.8F,
                    (random.nextFloat() - 0.5F) * 0.8F
            );
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    particlePos.x, particlePos.y, particlePos.z,
                    (random.nextFloat() - 0.5F) * 0.02F,
                    (random.nextFloat() - 0.5F) * 0.02F,
                    (random.nextFloat() - 0.5F) * 0.02F);
        }
    }

    private void spawnContinuousSparkEffects(Level level, Vec3 start, Vec3 end) {
        // Continuous spark effects along the beam path
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        direction = direction.normalize();
        
        int sparkCount = (int)(length / SPARK_DISTANCE_INTERVAL) + 2;
        for (int i = 0; i < sparkCount; i++) {
            double progress = i / (double)sparkCount;
            Vec3 sparkPos = start.add(direction.scale(progress * length));
            
            // Add some randomness to spark position
            sparkPos = sparkPos.add(
                    (random.nextFloat() - 0.5F) * 0.3F,
                    (random.nextFloat() - 0.5F) * 0.3F,
                    (random.nextFloat() - 0.5F) * 0.3F
            );
            
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    sparkPos.x, sparkPos.y, sparkPos.z,
                    (random.nextFloat() - 0.5F) * 0.01F,
                    (random.nextFloat() - 0.5F) * 0.01F,
                    (random.nextFloat() - 0.5F) * 0.01F);
        }
    }

    private void spawnBeamSparkles(Level level, Vec3 start, Vec3 end) {
        // Continuous sparkles along the beam for visual flair
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        if (length < 0.1) return;
        
        direction = direction.normalize();
        
        // Spawn sparkles along the beam
        int sparkleCount = BEAM_SPARKLE_COUNT_MIN + random.nextInt(BEAM_SPARKLE_COUNT_MAX - BEAM_SPARKLE_COUNT_MIN + 1);
        for (int i = 0; i < sparkleCount; i++) {
            double progress = random.nextDouble();
            Vec3 sparklePos = start.add(direction.scale(progress * length));
            
            // Add perpendicular offset for more dynamic look
            Vec3 perpendicular = direction.cross(new Vec3(0, 1, 0));
            if (perpendicular.lengthSqr() < 1e-5) perpendicular = direction.cross(new Vec3(1, 0, 0));
            perpendicular = perpendicular.normalize().scale((random.nextFloat() - 0.5F) * 0.2F);
            
            sparklePos = sparklePos.add(perpendicular);
            
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    sparklePos.x, sparklePos.y, sparklePos.z,
                    0, 0, 0);
        }
    }
}
