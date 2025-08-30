package com.leon.lightningdragon.server.entity.ability.abilities;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilitySection;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.leon.lightningdragon.server.entity.ability.DragonAbilitySection.*;

/**
 * Bite + chain lightning ability for LightningDragon.
 */
public class LightningBiteAbility extends DragonAbility<LightningDragonEntity> {
    // Tuning knobs
    private static final float BITE_DAMAGE = 7.0f;
    private static final double BITE_RANGE = 3.0;
    private static final double BITE_ANGLE_DEG = 60.0; // half-angle of cone

    private static final float CHAIN_DAMAGE_BASE = 4.0f;
    private static final double CHAIN_RADIUS = 7.0;
    private static final int CHAIN_JUMPS = 4;
    private static final float CHAIN_FALLOFF = 0.75f;

    // Sections: startup (windup), active (hit frame), recovery
    private static final DragonAbilitySection[] TRACK = new DragonAbilitySection[] {
            new AbilitySectionDuration(AbilitySectionType.STARTUP, 6),
            new AbilitySectionDuration(AbilitySectionType.ACTIVE, 2),
            new AbilitySectionDuration(AbilitySectionType.RECOVERY, 10)
    };

    private boolean didHitThisActive = false;

    public LightningBiteAbility(DragonAbilityType<LightningDragonEntity, LightningBiteAbility> type, LightningDragonEntity user) {
        super(type, user, TRACK, 24); // ~1.2s cooldown
    }

    @Override
    protected void beginSection(DragonAbilitySection section) {
        if (section == null) return;
        if (section.sectionType == AbilitySectionType.STARTUP) {
            // Trigger bite animation via GeckoLib action controller
            // Server-side trigger to auto-sync to clients
            getUser().triggerAnim("action", "animation.lightning_dragon.bite");
            didHitThisActive = false;
        }
    }

    @Override
    public void tickUsing() {
        DragonAbilitySection section = getCurrentSection();
        if (section == null) return;

        if (section.sectionType == AbilitySectionType.ACTIVE && !didHitThisActive) {
            // Apply bite and chain once at the start of ACTIVE
            LivingEntity primary = findPrimaryTarget();
            if (primary != null) {
                bitePrimary(primary);
                chainFrom(primary);
            }
            didHitThisActive = true;
        }
    }

    // ===== Core mechanics =====

    private LivingEntity findPrimaryTarget() {
        LightningDragonEntity dragon = getUser();
        Vec3 mouth = dragon.getMouthPosition();
        Vec3 look = dragon.getLookAngle().normalize();

        // Broadphase: small box forward of mouth
        AABB box = new AABB(mouth, mouth).inflate(BITE_RANGE, 1.0, BITE_RANGE);
        List<LivingEntity> candidates = dragon.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != dragon && e.isAlive() && e.attackable() && !isAllied(dragon, e));

        double cosLimit = Math.cos(Math.toRadians(BITE_ANGLE_DEG));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (LivingEntity e : candidates) {
            Vec3 toward = e.getEyePosition().subtract(mouth);
            double dist = toward.length();
            if (dist > BITE_RANGE || dist <= 0.0001) continue;
            Vec3 dir = toward.scale(1.0 / dist);
            double dot = dir.dot(look);
            if (dot < cosLimit) continue; // outside cone
            if (!dragon.getSensing().hasLineOfSight(e)) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }

    private void bitePrimary(LivingEntity primary) {
        LightningDragonEntity dragon = getUser();
        DamageSource src = dragon.level().damageSources().mobAttack(dragon);
        primary.hurt(src, BITE_DAMAGE);
    }

    private void chainFrom(LivingEntity start) {
        LightningDragonEntity dragon = getUser();
        Set<LivingEntity> hit = new HashSet<>();
        hit.add(start);

        LivingEntity current = start;
        float damage = CHAIN_DAMAGE_BASE;

        for (int i = 0; i < CHAIN_JUMPS; i++) {
            LivingEntity next = findNearestChainTarget(current, hit);
            if (next == null) break;

            // Damage and VFX
            next.hurt(dragon.level().damageSources().lightningBolt(), damage);
            spawnArc(current.position().add(0, current.getBbHeight() * 0.5, 0),
                     next.position().add(0, next.getBbHeight() * 0.5, 0));

            hit.add(next);
            current = next;
            damage *= CHAIN_FALLOFF;
        }
    }

    private LivingEntity findNearestChainTarget(LivingEntity origin, Set<LivingEntity> exclude) {
        LightningDragonEntity dragon = getUser();
        List<LivingEntity> nearby = DragonMathUtil.getEntitiesNearby(origin, LivingEntity.class, CHAIN_RADIUS);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : nearby) {
            if (e == dragon || exclude.contains(e) || !e.isAlive() || !e.attackable() || isAllied(dragon, e)) continue;
            double d = e.distanceToSqr(origin);
            if (d < bestDist) {
                // Optional LOS check for coherence
                if (!dragon.getSensing().hasLineOfSight(e)) continue;
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private boolean isAllied(LightningDragonEntity dragon, Entity other) {
        if (other instanceof LightningDragonEntity od) {
            return dragon.isTame() && od.isTame() && dragon.getOwner() != null && dragon.getOwner().equals(od.getOwner());
        }
        if (other instanceof LivingEntity le) {
            if (dragon.isTame() && le.equals(dragon.getOwner())) return true;
            return dragon.isAlliedTo(le);
        }
        return false;
    }

    private void spawnArc(Vec3 from, Vec3 to) {
        if (!(getLevel() instanceof ServerLevel server)) return;
        // Spawn a simple electric spark trail along the segment
        Vec3 delta = to.subtract(from);
        int steps = Math.max(3, (int) (delta.length() * 6));
        Vec3 step = delta.scale(1.0 / steps);
        Vec3 pos = from;
        for (int i = 0; i <= steps; i++) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z,
                    1, 0.0, 0.0, 0.0, 0.0);
            pos = pos.add(step);
        }
    }
}
