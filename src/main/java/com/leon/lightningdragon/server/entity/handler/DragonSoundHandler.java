package com.leon.lightningdragon.server.entity.handler;

import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import com.leon.lightningdragon.common.registry.ModSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;

/**
 * Handles all sound effects for the Lightning Dragon
 * Separates sound logic from entity class for cleaner organization
 */
public class DragonSoundHandler {
    private final LightningDragonEntity dragon;
    
    public DragonSoundHandler(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }
    
    /**
     * Handle keyframe-based sound effects during animations
     * Call this from animation controller sound handlers (legacy support)
     */
    public void handleAnimationSound(SoundKeyframeEvent<LightningDragonEntity> event) {
        if (event == null || event.getKeyframeData() == null) return;
        String raw = event.getKeyframeData().getSound();
        if (raw == null || raw.isEmpty()) return;
        String sound = raw.toLowerCase(java.util.Locale.ROOT);

        // Auto format: namespace:soundid or namespace:soundid|vol|pitch
        if (sound.contains(":")) {
            handleAutoSoundSpec(sound);
            return;
        }
        // Allow flexible keys from animation JSON: flap1, flap_right, step2, step_left, etc.
        if (sound.startsWith("flap")) { handleWingFlapSound(sound); return; }
        if (sound.startsWith("step")) { handleStepSound(sound); return; }
        switch (sound) {
            case "wing_flap" -> handleWingFlapSound(sound);
            case "dragon_step" -> handleStepSound(sound);
            case "takeoff_whoosh" -> handleTakeoffSound();
            case "landing_thud" -> handleLandingSound();
            default -> handleSoundByName(sound);
        }
    }

    /**
     * Handle sound by name - for direct sound triggering
     */
    public void handleSoundByName(String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        String key = soundName.toLowerCase(java.util.Locale.ROOT);
        if (key.startsWith("flap")) { handleWingFlapSound(key); return; }
        if (key.startsWith("step")) { handleStepSound(key); return; }
        switch (key) {
            case "wing_flap" -> handleWingFlapSound(key);
            case "dragon_step" -> handleStepSound(key);
            case "takeoff_whoosh" -> handleTakeoffSound();
            case "landing_thud" -> handleLandingSound();
            default -> {}
        }
    }

    /**
     * Plays a vocal sound and triggers a matching action animation.
     * Expected keys (examples): grumble1, grumble2, grumble3, purr, snort, chuff, content, annoyed,
     * growl_warning, roar, hurt
     */
    public void playVocal(String key) {
        if (key == null || key.isEmpty() || dragon.level().isClientSide) return;

        float vol = 1.0f;
        float pitch = 1.0f;

        switch (key) {
            case "grumble1" -> dragon.playSound(ModSounds.DRAGON_GRUMBLE_1.get(), 0.8f, 0.95f + dragon.getRandom().nextFloat() * 0.1f);
            case "grumble2" -> dragon.playSound(ModSounds.DRAGON_GRUMBLE_2.get(), 0.8f, 0.95f + dragon.getRandom().nextFloat() * 0.1f);
            case "grumble3" -> dragon.playSound(ModSounds.DRAGON_GRUMBLE_3.get(), 0.8f, 0.95f + dragon.getRandom().nextFloat() * 0.1f);
            case "purr"      -> dragon.playSound(ModSounds.DRAGON_PURR.get(),      0.8f, 1.05f + dragon.getRandom().nextFloat() * 0.05f);
            case "snort"     -> dragon.playSound(ModSounds.DRAGON_SNORT.get(),     0.9f, 0.9f + dragon.getRandom().nextFloat() * 0.2f);
            case "chuff"     -> dragon.playSound(ModSounds.DRAGON_CHUFF.get(),     0.9f, 0.9f + dragon.getRandom().nextFloat() * 0.2f);
            case "content"   -> dragon.playSound(ModSounds.DRAGON_CONTENT.get(),   0.8f, 1.0f + dragon.getRandom().nextFloat() * 0.1f);
            case "annoyed"   -> dragon.playSound(ModSounds.DRAGON_ANNOYED.get(),   1.0f, 0.9f + dragon.getRandom().nextFloat() * 0.2f);
            case "growl_warning" -> dragon.playSound(ModSounds.DRAGON_GROWL_WARNING.get(), 1.2f, 0.8f + dragon.getRandom().nextFloat() * 0.4f);
            case "roar"      -> dragon.playSound(ModSounds.DRAGON_ROAR.get(),      1.4f, 0.9f + dragon.getRandom().nextFloat() * 0.15f);
            default -> {
                // Unknown key - do nothing
            }
        }

        // Also trigger a matching vocal animation clip on the action controller.
        // Keep the action window open long enough for the clip to finish.
        int window = getVocalAnimationWindowTicks(key);
        if (window > 0) {
            dragon.playActionAnimationKey(key, window);
        }
    }

    /**
     * Returns an appropriate action-controller window length (in ticks) for a vocal animation key.
     * Values mirror the animation lengths defined in lightning_dragon.animation.json (rounded up).
     */
    private static int getVocalAnimationWindowTicks(String key) {
        if (key == null) return 0;
        // Map known keys to their approximate durations (seconds * 20)
        // grumble1: 5.9167s, grumble2: 8.6667s, grumble3: 2.625s
        // content: 4.7083s, purr: 5.2083s, snort: 0.875s, chuff: 1.2083s
        return switch (key) {
            case "grumble1" -> 120;  // ~5.9s
            case "grumble2" -> 180;  // ~8.7s
            case "grumble3" -> 60;   // ~2.6s
            case "content"  -> 100;  // ~4.7s
            case "purr"     -> 110;  // ~5.2s
            case "snort"    -> 24;   // ~0.9s
            case "chuff"    -> 28;   // ~1.2s
            // Fallback window for other simple one-shots
            default -> 40;            // ~2s
        };
    }
    
    /**
     * Wing flap sound with dynamic speed variation
     */
    private void handleWingFlapSound(String key) {
        double flightSpeed = dragon.getCachedHorizontalSpeed();
        float pitch = 1.0f + (float)(flightSpeed * 0.3f); // Higher pitch when flying faster
        float volume = Math.max(0.6f, 0.9f + (float)(flightSpeed * 0.2f));

        // Use custom flap sound (matches Blockbench keyframe label like "flap1")
        playRouted(dragon.level(), ModSounds.FLAP1.get(), volume, pitch);
    }
    
    /**
     * Dragon step sound with weight variation
     */
    private void handleStepSound(String key) {
        // Heavier steps when running or carrying rider
        float weight = 1.0f;
        if (dragon.isRunning()) weight *= 1.2f;
        if (dragon.isVehicle()) weight *= 1.1f;
        if (dragon.getHealth() < dragon.getMaxHealth() * 0.5f) weight *= 0.9f; // Slightly lighter when injured

        float volume = 0.65f * weight;
        float pitch = (0.9f + dragon.getRandom().nextFloat() * 0.2f) / weight; // Lower pitch for heavier steps

        // Choose step variant based on keyframe name (e.g., "step1" vs "step2")
        if (key != null && key.endsWith("2")) {
            playRouted(dragon.level(), ModSounds.STEP2.get(), volume, pitch);
        } else {
            playRouted(dragon.level(), ModSounds.STEP1.get(), volume, pitch);
        }
    }

    /**
     * Parses and plays sounds specified as namespace:soundid or namespace:soundid|vol|pitch
     */
    private void handleAutoSoundSpec(String spec) {
        if (spec == null) return;
        String[] parts = spec.split("\\|");
        String id = parts[0];
        float vol = 1.0f;
        float pitch = 1.0f;
        try {
            if (parts.length >= 2) vol = Float.parseFloat(parts[1]);
            if (parts.length >= 3) pitch = Float.parseFloat(parts[2]);
        } catch (Exception ignored) {}

        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return;
        net.minecraft.sounds.SoundEvent evt = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (evt == null) return;
        playRouted(dragon.level(), evt, vol, pitch);
    }

    /**
     * Plays sound properly on both client and server sides
     */
    private void playRouted(Level level, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (level == null) return;
        if (level.isClientSide) {
            // Client side: play local sound without distance delay
            level.playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(), sound, SoundSource.NEUTRAL, volume, pitch, false);
        } else {
            // Server side: broadcast to all nearby players
            level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
        }
    }
    
    /**
     * Takeoff sound with urgency variation
     */
    private void handleTakeoffSound() {
        float urgency = dragon.getTarget() != null ? 1.3f : 1.0f;
        // Use custom flap for takeoff to avoid vanilla ENDER_DRAGON_FLAP
        playRouted(dragon.level(), ModSounds.FLAP1.get(), urgency * 1.2f, 0.85f);
    }
    
    /**
     * Landing sound with impact variation
     */
    private void handleLandingSound() {
        double impactSpeed = Math.abs(dragon.getDeltaMovement().y);
        float volume = (float) Math.max(0.8f, 1.0f + impactSpeed * 2.0f);
        float pitch = (float) Math.max(0.7f, 1.0f - impactSpeed * 0.3f);
        
        dragon.playSound(SoundEvents.GENERIC_EXPLODE, volume * 0.6f, pitch);
    }
}
