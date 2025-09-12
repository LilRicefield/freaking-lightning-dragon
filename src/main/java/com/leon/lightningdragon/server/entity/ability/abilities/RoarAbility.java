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
    private java.util.List<Integer> targetIds = java.util.Collections.emptyList();
    private int targetCursor = 0;

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
            // Lock takeoff only on ground, but allow running and other controls.
            // While flying, allow normal controls (including ascend/descend) during roar.
            if (!getUser().isFlying()) {
                getUser().lockTakeoff(ROAR_TOTAL_TICKS);
            }
            // Preselect targets and number of strikes
            selectLightningTargets();
            // If multiple targets, cover more with extra strikes; else 2-3 strikes on single target
            int count = targetIds.size();
            if (count > 1) {
                strikesLeft = Math.min(6, Math.max(3, count * 2));
            } else {
                strikesLeft = 2 + getUser().getRandom().nextInt(2); // 2-3 strikes
            }
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

    private void selectLightningTargets() {
        LightningDragonEntity dragon = getUser();
        net.minecraft.world.entity.LivingEntity rider = dragon.getControllingPassenger();

        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        java.util.List<net.minecraft.world.entity.Mob> chasers = java.util.Collections.emptyList();
        if (dragon.level() instanceof net.minecraft.server.level.ServerLevel server) {
            var box = dragon.getBoundingBox().inflate(24.0);
            chasers = server.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> {
                var t = m.getTarget();
                return m.isAlive() && (t == dragon || (rider != null && t == rider));
            });
            // Sort by distance ascending
            chasers.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(dragon)));
            for (var m : chasers) ids.add(m.getId());
        }

        // Add recent aggro and current target as fallbacks
        if (dragon.level() instanceof net.minecraft.server.level.ServerLevel server2) {
            java.util.List<net.minecraft.world.entity.LivingEntity> recent = dragon.getRecentAggro();
            recent.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(dragon)));
            for (var le : recent) if (le != null && le.isAlive()) ids.add(le.getId());
            var ct = dragon.getTarget();
            if (ct != null && ct.isAlive()) ids.add(ct.getId());
        }

        // Cap list size for performance
        this.targetIds = new java.util.ArrayList<>();
        int added = 0;
        for (Integer id : ids) {
            this.targetIds.add(id);
            if (++added >= 6) break; // up to 6 targets
        }
        // Primary targetId remains for backwards compat
        this.targetId = this.targetIds.isEmpty() ? -1 : this.targetIds.get(0);
        this.targetCursor = 0;
    }

    private void spawnLightningStrike() {
        LightningDragonEntity dragon = getUser();
        if (!(dragon.level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        net.minecraft.world.entity.LivingEntity target = nextValidTarget(server);
        if (target == null) return;

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
            // Apply a brief stun-like debuff to the struck target
            applyStun(target, 30); // ~1.5s
        }
    }

    private static void applyStun(net.minecraft.world.entity.LivingEntity target, int durationTicks) {
        // Movement slowdown (amplifier 5 ≈ -73% speed), brief weakness to sell stun
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 5, false, true));
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WEAKNESS, durationTicks, 0, false, true));
        // Optional: mild blindness to disorient
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.BLINDNESS, Math.min(durationTicks, 20), 0, false, true));
    }

    private net.minecraft.world.entity.LivingEntity nextValidTarget(net.minecraft.server.level.ServerLevel server) {
        // Cycle through list to find a live target
        int n = targetIds != null ? targetIds.size() : 0;
        for (int i = 0; i < n; i++) {
            int idx = (targetCursor + i) % n;
            net.minecraft.world.entity.Entity e = server.getEntity(targetIds.get(idx));
            if (e instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive()) {
                targetCursor = (idx + 1) % n;
                return le;
            }
        }
        return null;
    }
}
