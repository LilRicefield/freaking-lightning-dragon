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
    }
}
