package com.leon.lightningdragon.server.entity.controller;

import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Handles all animation logic for the Lightning Dragon
 */
public class DragonFlightPhysicsController {
    private final LightningDragonEntity dragon;

    // ===== FLIGHT ANIMATION CONTROLLERS =====
    public static class FlightAnimationController {
        private float timer = 0f;
        private final float maxTime;
        private boolean increasing = false;

        public FlightAnimationController(float maxTime) {
            this.maxTime = maxTime;
        }

        public void increaseTimer() {
            increasing = true;
            timer = Math.min(timer + 0.2f, maxTime);
        }

        public void decreaseTimer() {
            increasing = false;
            timer = Math.max(timer - 0.2f, 0f);
        }

        public void restoreState(float fraction, boolean wasIncreasing) {
            this.increasing = wasIncreasing;
            // Reverse calculate timer from fraction
            if (fraction > 0) {
                this.timer = (float) (Math.asin(fraction) / (Math.PI * 0.5) * maxTime);
            } else {
                this.timer = 0f;
            }
        }

        public float getAnimationFraction() {
            float fraction = timer / maxTime;
            // Smooth sine curve - gives that buttery smoothness
            return (float) Math.sin(fraction * Math.PI * 0.5);
        }

        public boolean isIncreasing() { return increasing; }
        public float getTimer() { return timer; }
    }

    // Animation controllers
    public final FlightAnimationController glidingController = new FlightAnimationController(25f);
    public final FlightAnimationController flappingController = new FlightAnimationController(20f);
    public final FlightAnimationController hoveringController = new FlightAnimationController(15f);

    // Animation fraction values for smooth blending
    public float glidingFraction = 0f;
    public float prevGlidingFraction = 0f;
    public float flappingFraction = 0f;
    public float prevFlappingFraction = 0f;
    public float hoveringFraction = 0f;
    public float prevHoveringFraction = 0f;

    // Flight animation state tracking
    private RawAnimation currentFlightAnimation = LightningDragonEntity.FLY_GLIDE;

    // Enhanced flap timing
    private int discreteFlapCooldown = 0;
    private boolean hasPlayedFlapSound = false;
    private static final int FLAP_MIN_HOLD_TICKS = 28; // ensure near-full visible cycle incl. blend
    private static final int GLIDE_MIN_HOLD_TICKS = 14; // brief calm before re-flap
    // Temporary lock to force flapping (e.g., when starting to climb)
    private int flapLockTicks = 0;
    // Temporary lock to hold glide state briefly
    private int glideLockTicks = 0;

    // Wing beat intensity for sound timing
    private float wingBeatIntensity = 0f;
    private float prevWingBeatIntensity = 0f;

    // Sound timing constants
    private static final float BEAT_THRESHOLD = 0.7f;

    public DragonFlightPhysicsController(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }

    /**
     * Main tick method - call this from your entity's tick()
     */
    public void tick() {
        // Store previous values for interpolation
        prevGlidingFraction = glidingFraction;
        prevFlappingFraction = flappingFraction;
        prevHoveringFraction = hoveringFraction;
        prevWingBeatIntensity = wingBeatIntensity;

        // Update flight animation controllers
        updateFlightAnimationControllers();

        // Update animation fractions for renderer
        glidingFraction = glidingController.getAnimationFraction();
        flappingFraction = flappingController.getAnimationFraction();
        hoveringFraction = hoveringController.getAnimationFraction();

        // Wing beat intensity for sound timing
        updateWingBeatIntensity();

        // Handle flap cooldowns
        if (discreteFlapCooldown > 0) {
            discreteFlapCooldown--;
        }
        if (flapLockTicks > 0) {
            flapLockTicks--;
        }
        if (glideLockTicks > 0) {
            glideLockTicks--;
        }
    }
    public PlayState handleMovementAnimation(AnimationState<LightningDragonEntity> state) {
        // TODO: Handle new Dragon ability system animations

        // Ensure transition length is set for all other animations too
        state.getController().transitionLength(10);
        if (dragon.isOrderedToSit()) {
            state.setAndContinue(LightningDragonEntity.SIT);
        } else if (dragon.isDodging()) {
            state.setAndContinue(LightningDragonEntity.DODGE);
        } else if (dragon.isLanding()) {
            state.setAndContinue(LightningDragonEntity.LANDING);
        } else if (dragon.isFlying()) {
            if (dragon.timeFlying < 30) {
                state.setAndContinue(LightningDragonEntity.TAKEOFF);
            } else {
                // HYSTERESIS - prevent rapid switching between animations
                float hoverWeight = hoveringFraction;
                float flapWeight = flappingFraction;

                // Base thresholds for entering/exiting flap (without locks)
                boolean shouldFlapBase = (currentFlightAnimation == LightningDragonEntity.FLY_FORWARD)
                        ? (flapWeight > 0.22f || hoverWeight > 0.28f) // Lower threshold to exit
                        : (flapWeight > 0.55f || hoverWeight > 0.65f); // Higher threshold to enter

                // Strong climb can override glide lock to start flapping immediately
                boolean strongClimb = dragon.getDeltaMovement().y > 0.12;
                boolean canStartFlap = (glideLockTicks == 0) || strongClimb;

                // If we're about to enter flapping, enforce a minimum hold so the cycle doesn't cut off
                if (flapLockTicks == 0 && currentFlightAnimation != LightningDragonEntity.FLY_FORWARD && shouldFlapBase && canStartFlap) {
                    flapLockTicks = FLAP_MIN_HOLD_TICKS;
                }

                boolean shouldFlap = flapLockTicks > 0 || (shouldFlapBase && canStartFlap);

                if (shouldFlap) {
                    if (currentFlightAnimation != LightningDragonEntity.FLY_FORWARD) {
                        // Slightly quicker blend into flap so the beat reads
                        state.getController().transitionLength(6);
                        currentFlightAnimation = LightningDragonEntity.FLY_FORWARD;
                        glideLockTicks = 0; // clear glide lock when entering flap
                    }
                    state.setAndContinue(LightningDragonEntity.FLY_FORWARD);
                } else {
                    if (currentFlightAnimation != LightningDragonEntity.FLY_GLIDE) {
                        // Smooth but not too long blend out of flap
                        state.getController().transitionLength(6);
                        currentFlightAnimation = LightningDragonEntity.FLY_GLIDE;
                        glideLockTicks = GLIDE_MIN_HOLD_TICKS; // brief hold before re-flap
                    }
                    state.setAndContinue(LightningDragonEntity.FLY_GLIDE);
                }
            }
        } else {
            // Ground movement logic remains the same
            if (dragon.isActuallyRunning()) {
                state.setAndContinue(LightningDragonEntity.GROUND_RUN); // Sets walkRunController to 1.0
            } else if (dragon.isWalking()) {
                state.setAndContinue(LightningDragonEntity.GROUND_WALK); // Sets walkRunController to 0.0
            } else {
                state.setAndContinue(LightningDragonEntity.GROUND_IDLE);
            }
        }
        return PlayState.CONTINUE;
    }

    private void updateFlightAnimationControllers() {
        if (!dragon.isFlying()) {
            // Ground state - smoothly fade out all flight animations
            glidingController.decreaseTimer();
            flappingController.decreaseTimer();
            hoveringController.decreaseTimer();
            return;
        }

        Vec3 velocity = dragon.getDeltaMovement();
        Vec3 lookDirection = dragon.getLookAngle();
        double speedSqr = velocity.horizontalDistanceSqr();

        // Enhanced flight condition analysis
        boolean isDiving = lookDirection.y < -0.15 && velocity.y < -0.08;
        // More sensitive climb detection to encourage flaps
        boolean isClimbing = velocity.y > 0.06;
        // Banking detection removed - now handled by animation.json
        boolean isTurning = false; // Will be handled by animation system
        boolean isSlowSpeed = speedSqr < 0.0036f;
        boolean isHoveringMode = dragon.isHovering() || (dragon.getTarget() != null && speedSqr < 0.03f);
        boolean isDescending = velocity.y < -0.04 && !isDiving;

        // Determine primary flight mode
        if (isHoveringMode || dragon.isLanding()) {
            // HOVERING MODE - for combat and precise movement
            hoveringController.increaseTimer();
            glidingController.decreaseTimer();

            // Hover-flapping (gentle wing beats to maintain position)
            if (isClimbing || isSlowSpeed || Math.abs(velocity.y) > 0.05) {
                // Smooth every-tick updates with reduced increment
                flappingController.increaseTimer();
                // Force a brief flap burst on initiating a climb
                if (isClimbing && flapLockTicks == 0 && discreteFlapCooldown <= 0) {
                    flapLockTicks = FLAP_MIN_HOLD_TICKS;
                    discreteFlapCooldown = FLAP_MIN_HOLD_TICKS;
                }
            } else {
                flappingController.decreaseTimer();
            }

            // Discrete hover flaps - less frequent than combat flaps
            if (discreteFlapCooldown <= 0 && (isClimbing || dragon.getRandom().nextFloat() < 0.03)) {
                triggerDiscreteFlapAnimation();
                discreteFlapCooldown = 30;
            }

        } else {
            // GLIDING MODE - the bread and butter of dragon flight
            hoveringController.decreaseTimer();

            // Intelligent flap detection
            boolean needsActiveFlapping = isDiving || isClimbing || isTurning || isSlowSpeed || isDescending;

            if (needsActiveFlapping) {
                // Smooth every-tick updates with reduced increment
                flappingController.increaseTimer();
                glidingController.decreaseTimer();

                // Discrete flap trigger (only when physics demands it)
                if (discreteFlapCooldown <= 0) {
                    boolean shouldTriggerFlap = isDiving && velocity.y < -0.15;

                    if (isClimbing && velocity.y > 0.15) shouldTriggerFlap = true;
                    // Banking trigger removed - handled by animation.json
                    if (isDescending && dragon.getRandom().nextFloat() < 0.08) shouldTriggerFlap = true;

                    if (shouldTriggerFlap) {
                        triggerDiscreteFlapAnimation();
                        discreteFlapCooldown = 28 + dragon.getRandom().nextInt(16);
                        flapLockTicks = Math.max(flapLockTicks, 10);
                    }
                }

            } else {
                // THE MAGIC MOMENT - pure gliding efficiency
                flappingController.decreaseTimer();
                glidingController.increaseTimer();

                // Reset flap sound flag during smooth gliding
                hasPlayedFlapSound = false;
            }
        }
    }

    private void updateWingBeatIntensity() {
        // Calculate wing beat intensity for realistic sound timing
        float targetIntensity = 0f;

        if (dragon.isFlying()) {
            // Base intensity on flight state
            if (hoveringFraction > 0.5f) {
                targetIntensity = 0.6f + flappingFraction * 0.4f; // Steady hover beats
            } else if (flappingFraction > 0.3f) {
                targetIntensity = 0.4f + flappingFraction * 0.6f; // Active flight beats
            } else {
                targetIntensity = glidingFraction * 0.2f; // Minimal gliding adjustments
            }

            // Modulate based on speed (banking now handled by animation.json)
            Vec3 velocity = dragon.getDeltaMovement();
            double speed = velocity.horizontalDistanceSqr();

            targetIntensity += (float) (speed * 2.0f);
            targetIntensity = Mth.clamp(targetIntensity, 0f, 1f);
        }

        // Smooth approach to target intensity
        wingBeatIntensity = Mth.approach(wingBeatIntensity, targetIntensity, 0.05f);

        // Sound triggering logic
        if (dragon.isFlying() && !dragon.level().isClientSide) {
            handleFlightSounds();
        }
    }

    private void handleFlightSounds() {
        if (wingBeatIntensity <= BEAT_THRESHOLD || hasPlayedFlapSound) {
            if (wingBeatIntensity < 0.3f) {
                hasPlayedFlapSound = false;
            }
            return;
        }

        // Wing beat sound timing
        boolean shouldPlaySound = false;

        if (hoveringFraction > 0.5f) {
            shouldPlaySound = discreteFlapCooldown <= 0;
        } else if (flappingFraction > 0.4f) {
            shouldPlaySound = true;
        }

        if (shouldPlaySound) {
            playFlappingSound();
            hasPlayedFlapSound = true;
            discreteFlapCooldown = Math.max(discreteFlapCooldown, 15);
        }
    }

    private void triggerDiscreteFlapAnimation() {
        // TODO: Check with new Dragon ability system if animation can be triggered
        playFlappingSound();
    }

    private void playFlappingSound() {
        if (!dragon.level().isClientSide) {
            // Vary sound based on flight state
            float volume = 0.6f + wingBeatIntensity * 0.4f;
            float pitch = 0.9f + dragon.getRandom().nextFloat() * 0.4f;

            // Different sounds for different flight modes
            if (hoveringFraction > 0.5f) {
                pitch *= 1.1f; // Higher pitch for hovering
                volume *= 0.8f; // Softer for hovering
            } else if (glidingFraction > 0.5f) {
                volume *= 0.6f; // Very quiet for gliding adjustments
                pitch *= 0.9f; // Lower pitch for gliding
            }

            dragon.level().playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE,
                    volume, pitch);
        }
    }

    // ===== GETTERS FOR RENDERER =====
    public float getGlidingFraction(float partialTick) {
        return Mth.lerp(partialTick, prevGlidingFraction, glidingFraction);
    }

    public float getFlappingFraction(float partialTick) {
        return Mth.lerp(partialTick, prevFlappingFraction, flappingFraction);
    }

    public float getHoveringFraction(float partialTick) {
        return Mth.lerp(partialTick, prevHoveringFraction, hoveringFraction);
    }

    public float getWingBeatIntensity(float partialTick) {
        return Mth.lerp(partialTick, prevWingBeatIntensity, wingBeatIntensity);
    }

    // ===== SAVE/LOAD SUPPORT =====
    public void writeToNBT(net.minecraft.nbt.CompoundTag tag) {
        tag.putFloat("GlidingTimer", glidingController.getTimer());
        tag.putFloat("FlappingTimer", flappingController.getTimer());
        tag.putFloat("HoveringTimer", hoveringController.getTimer());
        tag.putFloat("WingBeatIntensity", wingBeatIntensity);
        tag.putFloat("GlidingFraction", glidingFraction);
        tag.putFloat("FlappingFraction", flappingFraction);
        tag.putFloat("HoveringFraction", hoveringFraction);
        tag.putBoolean("GlidingIncreasing", glidingController.isIncreasing());
        tag.putBoolean("FlappingIncreasing", flappingController.isIncreasing());
        tag.putBoolean("HoveringIncreasing", hoveringController.isIncreasing());
        tag.putInt("DiscreteFlapCooldown", discreteFlapCooldown);
    }

    public void readFromNBT(net.minecraft.nbt.CompoundTag tag) {
        // Restore all animation state after load
        wingBeatIntensity = tag.getFloat("WingBeatIntensity");
        prevWingBeatIntensity = wingBeatIntensity;

        glidingFraction = tag.getFloat("GlidingFraction");
        flappingFraction = tag.getFloat("FlappingFraction");
        hoveringFraction = tag.getFloat("HoveringFraction");

        prevGlidingFraction = glidingFraction;
        prevFlappingFraction = flappingFraction;
        prevHoveringFraction = hoveringFraction;

        discreteFlapCooldown = tag.getInt("DiscreteFlapCooldown");

        // Restore controller states by rebuilding timers from saved fractions
        glidingController.restoreState(glidingFraction, tag.getBoolean("GlidingIncreasing"));
        flappingController.restoreState(flappingFraction, tag.getBoolean("FlappingIncreasing"));
        hoveringController.restoreState(hoveringFraction, tag.getBoolean("HoveringIncreasing"));
    }
}