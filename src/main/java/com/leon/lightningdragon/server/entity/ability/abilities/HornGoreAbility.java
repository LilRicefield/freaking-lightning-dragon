package com.leon.lightningdragon.server.entity.ability.abilities;

import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilitySection;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.leon.lightningdragon.server.entity.ability.DragonAbilitySection.*;

/**
 * Simple horn gore melee: modest damage + strong knockback in front of head.
 */
public class HornGoreAbility extends DragonAbility<LightningDragonEntity> {
    private static final float GORE_DAMAGE = 5.0f;
    private static final double GORE_RANGE = 6.5; // Increased from 3.8
    private static final double GORE_RANGE_RIDDEN = 8.0; // Increased from 5.2
    private static final double GORE_ANGLE_DEG = 90.0; // half-angle, increased from 75

    private static final DragonAbilitySection[] TRACK = new DragonAbilitySection[] {
            new AbilitySectionDuration(AbilitySectionType.STARTUP, 5),
            new AbilitySectionDuration(AbilitySectionType.ACTIVE, 2),
            new AbilitySectionDuration(AbilitySectionType.RECOVERY, 10)
    };

    // Track entities already hit during the ACTIVE window so we don't double hit in multi-tick ACTIVE
    private final java.util.Set<Integer> hitIdsThisUse = new java.util.HashSet<>();

    public HornGoreAbility(DragonAbilityType<LightningDragonEntity, HornGoreAbility> type, LightningDragonEntity user) {
        super(type, user, TRACK, 22);
    }

    @Override
    protected void beginSection(DragonAbilitySection section) {
        if (section == null) return;
        if (section.sectionType == AbilitySectionType.STARTUP) {
            // Trigger gore animation via normal GeckoLib action trigger
            getUser().triggerAnim("action", "horn_gore");
            hitIdsThisUse.clear();
        }
    }

    @Override
    public void tickUsing() {
        DragonAbilitySection section = getCurrentSection();
        if (section == null) return;
        if (section.sectionType != AbilitySectionType.ACTIVE) return;

        // Multi-target horn gore: hit all valid entities in cone that haven't been hit yet
        java.util.List<LivingEntity> candidates = findTargets();
        java.util.List<LivingEntity> newHits = new java.util.ArrayList<>();
        for (LivingEntity le : candidates) {
            if (hitIdsThisUse.add(le.getId())) {
                newHits.add(le);
            }
        }
        if (!newHits.isEmpty()) {
            for (LivingEntity le : newHits) {
                applyGore(le);
            }
        }
    }

    private java.util.List<LivingEntity> findTargets() {
        LightningDragonEntity dragon = getUser();
        Vec3 head = dragon.getHeadPosition();
        Vec3 look = dragon.getLookAngle().normalize();

        boolean ridden = dragon.getControllingPassenger() != null;
        double range = ridden ? GORE_RANGE_RIDDEN : GORE_RANGE;

        // Use dragon body bounding box inflated by range as broadphase
        AABB broad = dragon.getBoundingBox().inflate(range, range, range);
        List<LivingEntity> candidates = dragon.level().getEntitiesOfClass(LivingEntity.class, broad,
                e -> e != dragon && e.isAlive() && e.attackable() && !isAllied(dragon, e));

        double cosLimit = Math.cos(Math.toRadians(GORE_ANGLE_DEG));
        java.util.List<LivingEntity> hits = new java.util.ArrayList<>();

        for (LivingEntity e : candidates) {
            // Distance from head to target AABB
            double dist = distancePointToAABB(head, e.getBoundingBox());
            if (dist > range + 0.4) continue;

            // Angle test from head toward target center
            Vec3 toward = e.getBoundingBox().getCenter().subtract(head);
            double len = toward.length();
            if (len < 1.0e-4) continue;
            double dot = toward.normalize().dot(look);

            boolean close = dist < (range * 0.6);
            boolean angleOk = dot >= cosLimit;
            if (ridden) {
                // More lenient while ridden or accept within body range
                double bodyDist = distancePointToAABB(e.position(), dragon.getBoundingBox());
                angleOk = angleOk || dot >= (cosLimit * 0.7) || bodyDist <= range;
            }
            if (!(close || angleOk)) continue;
            hits.add(e);
        }
        return hits;
    }

    private void applyGore(LivingEntity target) {
        LightningDragonEntity dragon = getUser();
        DamageSource src = dragon.level().damageSources().mobAttack(dragon);
        target.hurt(src, GORE_DAMAGE);

        // Strong directional knockback away from dragon head
        Vec3 look = dragon.getLookAngle().normalize();
        double strength = 1.4; // tune
        // knockback(strength, x, z): applies horizontal knockback opposite to (x,z)
        target.knockback((float) strength, -look.x, -look.z);

        // Small vertical lift
        Vec3 dv = target.getDeltaMovement();
        target.setDeltaMovement(dv.x, Math.max(dv.y, 0.35), dv.z);

    }

    // ===== helpers =====
    private static double distancePointToAABB(Vec3 p, AABB box) {
        double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
}
