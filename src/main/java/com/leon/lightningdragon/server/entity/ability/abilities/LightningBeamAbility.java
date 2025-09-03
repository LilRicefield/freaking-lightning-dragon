package com.leon.lightningdragon.server.entity.ability.abilities;

import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilitySection;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import static com.leon.lightningdragon.server.entity.ability.DragonAbilitySection.*;

/**
 * Hold-to-fire lightning beam ability.
 * No charge: starts immediately, remains active until interrupted.
 * Initial version: only toggles beaming state; damage/VFX added later.
 */
public class LightningBeamAbility extends DragonAbility<LightningDragonEntity> {

    // Track: optional startup (instant), active (infinite), recovery (short) on stop
    private static final DragonAbilitySection[] TRACK = new DragonAbilitySection[] {
            new AbilitySectionInstant(AbilitySectionType.STARTUP),
            new AbilitySectionInfinite(AbilitySectionType.ACTIVE),
            new AbilitySectionDuration(AbilitySectionType.RECOVERY, 5)
    };

    public LightningBeamAbility(DragonAbilityType<LightningDragonEntity, LightningBeamAbility> type, LightningDragonEntity user) {
        super(type, user, TRACK, 0); // No cooldown; gated by input
    }

    @Override
    protected void beginSection(DragonAbilitySection section) {
        if (section == null) return;
        if (section.sectionType == AbilitySectionType.STARTUP) {
            // Trigger beam start animation (optional) — will likely be replaced by continuous loop in renderer
            getUser().triggerAnim("action", "beam_start");
        } else if (section.sectionType == AbilitySectionType.ACTIVE) {
            getUser().setBeaming(true);
            // Optional continuous animation loop key if present
            getUser().triggerAnim("action", "lightning_beam");
        }
    }

    @Override
    protected void endSection(DragonAbilitySection section) {
        // When leaving ACTIVE (by interrupt/complete), play end and clear flag
        if (section != null && section.sectionType == AbilitySectionType.ACTIVE) {
            getUser().setBeaming(false);
            getUser().triggerAnim("action", "beam_end");
        }
    }

    @Override
    public void interrupt() {
        // Ensure beaming flag cleared even if interrupted mid-active
        getUser().setBeaming(false);
        // Always trigger beam end animation when interrupted
        getUser().triggerAnim("action", "beam_end");
        super.interrupt();
    }

    @Override
    public void tickUsing() {
        var section = getCurrentSection();
        if (section == null || section.sectionType != AbilitySectionType.ACTIVE) return;

        LightningDragonEntity dragon = getUser();
        if (dragon.level().isClientSide) return; // server-side authority only

        // Choose aim direction (rider when mounted, else dragon)
        net.minecraft.world.entity.Entity cp = dragon.getControllingPassenger();
        var look = (cp instanceof net.minecraft.world.entity.LivingEntity rider)
                ? rider.getLookAngle().normalize()
                : dragon.getLookAngle().normalize();

        // Build a stable local frame from the aim to place the muzzle near the head using beam_origin-like offsets
        final double SCALE = LightningDragonEntity.MODEL_SCALE;
        final double FORWARD = 14.65 / 16.0 * SCALE; // from geo pivot z magnitude
        final double UP = 6.6 / 16.0 * SCALE;       // from geo pivot y
        final double SIDE = 0.0;                    // pivot x is 0 in geo

        // Prefer recent client-reported animated mouth position when ridden; fallback to server mouth
        // I&F-style: compute from head yaw/pitch; do not trust client override
        var start = dragon.computeHeadMouthOrigin(1.0f);
        dragon.setBeamStartPosition(start);

        // Raycast along aim to determine endpoint
        final double MAX_DISTANCE = 32.0; // blocks
        var tentativeEnd = start.add(look.scale(MAX_DISTANCE));

        var hit = dragon.level().clip(new net.minecraft.world.level.ClipContext(
                start,
                tentativeEnd,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                dragon
        ));
        var end = hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? hit.getLocation() : tentativeEnd;
        dragon.setBeamEndPosition(end);

        // Damage sweep along the beam
        damageAlongBeam(dragon, start, end);

        // No debug logging
    }

    private void damageAlongBeam(LightningDragonEntity dragon, net.minecraft.world.phys.Vec3 start, net.minecraft.world.phys.Vec3 end) {
        if (!(dragon.level() instanceof net.minecraft.server.level.ServerLevel server)) return;

        final double STEP = 1.0;         // sample spacing
        final double RADIUS = 1.2;       // affect radius around beam core
        final float DAMAGE = 3.0f;       // per-tick damage per entity (tunes to DPS)

        var delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.0001) return;
        var dir = delta.scale(1.0 / len);

        java.util.HashSet<net.minecraft.world.entity.LivingEntity> hitThisTick = new java.util.HashSet<>();

        for (double d = 0; d <= len; d += STEP) {
            var p = start.add(dir.scale(d));
            var aabb = new net.minecraft.world.phys.AABB(p, p).inflate(RADIUS);
            var list = server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb,
                    e -> e != dragon && e.isAlive() && e.attackable() && !isAllied(dragon, e));
            for (var le : list) {
                if (hitThisTick.add(le)) {
                    le.hurt(dragon.level().damageSources().lightningBolt(), DAMAGE);
                    // Minor nudge away from beam
                    var away = le.position().subtract(p).normalize();
                    le.push(away.x * 0.05, 0.02, away.z * 0.05);
                    // no-op beyond damage and push
                }
            }
        }
    }

    private boolean isAllied(LightningDragonEntity dragon, net.minecraft.world.entity.Entity other) {
        if (other instanceof LightningDragonEntity od) {
            return dragon.isTame() && od.isTame() && dragon.getOwner() != null && dragon.getOwner().equals(od.getOwner());
        }
        if (other instanceof net.minecraft.world.entity.LivingEntity le) {
            if (dragon.isTame() && le.equals(dragon.getOwner())) return true;
            return dragon.isAlliedTo(le);
        }
        return false;
    }
}