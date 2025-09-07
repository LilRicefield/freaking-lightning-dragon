package com.leon.lightningdragon.server.entity.ability.abilities;

import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.server.entity.ability.DragonAbilitySection;
import com.leon.lightningdragon.server.entity.ability.DragonAbilityType;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;

import static com.leon.lightningdragon.server.entity.ability.DragonAbilitySection.*;

/**
 * Simple one-shot roar ability: plays a roar vocal and triggers the matching
 * action animation. No damage/effects here; purely audiovisual.
 */
public class RoarAbility extends DragonAbility<LightningDragonEntity> {

    // Brief startup to sync with animation pose, a short active window
    // so the ability keeps itself alive while the clip plays, then recover.
    private static final DragonAbilitySection[] TRACK = new DragonAbilitySection[] {
            new AbilitySectionDuration(AbilitySectionType.STARTUP, 6),
            new AbilitySectionDuration(AbilitySectionType.ACTIVE, 28),
            new AbilitySectionDuration(AbilitySectionType.RECOVERY, 12)
    };

    private boolean roarQueued = false;
    private static final int ROAR_DELAY_TICKS = 3;   // ~0.15s (closest to 0.17s at 20 TPS)
    private static final int ROAR_TOTAL_TICKS = 69;  // ~3.45s (animation length ~3.4167s)
    private int strikesLeft = 0;
    private int strikeCooldown = 0;
    private int targetId = -1;

    public RoarAbility(DragonAbilityType<LightningDragonEntity, RoarAbility> type, LightningDragonEntity user) {
        // Cooldown ~ 4 seconds to prevent spam
        super(type, user, TRACK, 80);
    }

    @Override
    protected void beginSection(DragonAbilitySection section) {
        if (section == null) return;
        if (section.sectionType == AbilitySectionType.STARTUP) {
            // Choose variant based on flight state
            boolean flying = getUser().isFlying();
            String trigger = flying ? "roar_air" : "roar_ground";
            // Trigger the action animation immediately
            getUser().triggerAnim("action", trigger);
            // Queue the roar sound slightly delayed to sync with mouth opening
            roarQueued = true;
            // Lock rider controls on ground so player can't take off mid-roar.
            // While flying, allow normal controls (including ascend/descend) during roar.
            if (!getUser().isFlying()) {
                getUser().lockRiderControls(ROAR_TOTAL_TICKS);
            }
            // Preselect target and number of strikes
            selectLightningTarget();
            strikesLeft = 2 + getUser().getRandom().nextInt(2); // 2-3 strikes
            strikeCooldown = 0; // strike asap when ACTIVE begins
        }
    }

    @Override
    public void tickUsing() {
        var section = getCurrentSection();
        if (section == null) return;

        // Play the roar sound after a tiny delay from animation start
        if (section.sectionType == AbilitySectionType.STARTUP && roarQueued) {
            if (getTicksInSection() >= ROAR_DELAY_TICKS && !getUser().level().isClientSide) {
                // Play only the sound (avoid retriggering the animation)
                var dragon = getUser();
                float pitch = 0.9f + dragon.getRandom().nextFloat() * 0.15f;
                dragon.playSound(com.leon.lightningdragon.common.registry.ModSounds.DRAGON_ROAR.get(), 1.4f, pitch);
                roarQueued = false;
            }
        }

        // During ACTIVE section, spawn lightning strikes at the selected target
        if (section.sectionType == AbilitySectionType.ACTIVE && strikesLeft > 0 && !getUser().level().isClientSide) {
            if (strikeCooldown > 0) {
                strikeCooldown--;
            } else {
                spawnLightningStrike();
                strikesLeft--;
                strikeCooldown = 6 + getUser().getRandom().nextInt(6); // 0.3s to 0.6s between strikes
            }
        }
    }

    private void selectLightningTarget() {
        LightningDragonEntity dragon = getUser();
        net.minecraft.world.entity.LivingEntity rider = dragon.getControllingPassenger();

        // Candidates: mobs actively targeting rider or dragon within 24 blocks
        java.util.List<net.minecraft.world.entity.Mob> chasers = java.util.Collections.emptyList();
        if (dragon.level() instanceof net.minecraft.server.level.ServerLevel server) {
            var box = dragon.getBoundingBox().inflate(24.0);
            chasers = server.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> {
                var t = m.getTarget();
                return m.isAlive() && (t == dragon || (rider != null && t == rider));
            });
        }

        // Prefer chasers near the dragon
        net.minecraft.world.entity.LivingEntity best = null;
        if (!chasers.isEmpty()) {
            for (var m : chasers) {
                if (m == null || !m.isAlive()) continue;
                if (best == null || m.distanceToSqr(dragon) < best.distanceToSqr(dragon)) best = m;
            }
        }
        // Fall back to recently aggroed entities
        if (best == null) {
            java.util.List<net.minecraft.world.entity.LivingEntity> recent = dragon.getRecentAggro();
            for (var le : recent) {
                if (le == null || !le.isAlive()) continue;
                if (best == null || le.distanceToSqr(dragon) < best.distanceToSqr(dragon)) best = le;
            }
        }
        // Fallback to current target
        if (best == null) {
            var t = dragon.getTarget();
            if (t != null && t.isAlive()) best = t;
        }
        targetId = best != null ? best.getId() : -1;
    }

    private void spawnLightningStrike() {
        LightningDragonEntity dragon = getUser();
        if (!(dragon.level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        net.minecraft.world.entity.Entity tgt = targetId != -1 ? server.getEntity(targetId) : null;
        if (!(tgt instanceof net.minecraft.world.entity.LivingEntity target) || !target.isAlive()) return;

        double ox = (dragon.getRandom().nextDouble() - 0.5) * 2.0; // slight scatter
        double oz = (dragon.getRandom().nextDouble() - 0.5) * 2.0;
        double x = target.getX() + ox;
        double z = target.getZ() + oz;
        double y = target.getY();

        var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(x, y, z);
            var owner = dragon.getOwner();
            if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
                bolt.setCause(sp);
            }
            server.addFreshEntity(bolt);
        }
    }
}
