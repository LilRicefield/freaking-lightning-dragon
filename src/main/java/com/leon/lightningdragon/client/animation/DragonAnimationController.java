package com.leon.lightningdragon.client.animation;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Handles all animation logic for the Lightning Dragon
 * Extracted from the main entity to keep that class clean as fuck
 */
public class DragonAnimationController {
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
            timer = Math.min(timer + 1f, maxTime);
        }

        public void decreaseTimer() {
            increasing = false;
            timer = Math.max(timer - 1f, 0f);
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

    // Wing beat intensity for sound timing
    private float wingBeatIntensity = 0f;
    private float prevWingBeatIntensity = 0f;

    // Sound timing constants
    private static final float BEAT_THRESHOLD = 0.7f;

    public DragonAnimationController(LightningDragonEntity dragon) {
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
    }
    public PlayState handleMovementAnimation(AnimationState<LightningDragonEntity> state) {
        // Ability animations still take priority
        if (dragon.getActiveAbility() != null && dragon.getActiveAbility().isUsing()) {
            state.getController().transitionLength(10);
            return dragon.getActiveAbility().animationPredicate(state);
        }
        
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

                // Use different thresholds for entering vs exiting states
                boolean shouldFlap = currentFlightAnimation == LightningDragonEntity.FLY_FORWARD ?
                        (flapWeight > 0.25f || hoverWeight > 0.3f) :  // Lower threshold to exit
                        (flapWeight > 0.6f || hoverWeight > 0.7f);    // Higher threshold to enter

                if (shouldFlap) {
                    currentFlightAnimation = LightningDragonEntity.FLY_FORWARD;
                    state.setAndContinue(LightningDragonEntity.FLY_FORWARD);
                } else {
                    currentFlightAnimation = LightningDragonEntity.FLY_GLIDE;
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
        boolean isClimbing = velocity.y > 0.12;
        boolean isTurning = Math.abs(dragon.getBanking()) > 20.0f;
        boolean isSlowSpeed = speedSqr < 0.0036f;
        boolean isHoveringMode = dragon.isHovering() || (dragon.getTarget() != null && speedSqr < 0.03f);
        boolean isDescending = velocity.y < -0.05 && !isDiving;

        // Determine primary flight mode
        if (isHoveringMode || dragon.isLanding()) {
            // HOVERING MODE - for combat and precise movement
            hoveringController.increaseTimer();
            glidingController.decreaseTimer();

            // Hover-flapping (gentle wing beats to maintain position)
            if (isClimbing || isSlowSpeed || Math.abs(velocity.y) > 0.05) {
                // Only update every 5 ticks for smoother transitions
                if (dragon.tickCount % 5 == 0) {
                    flappingController.increaseTimer();
                }
            } else {
                if (dragon.tickCount % 3 == 0) {
                    flappingController.decreaseTimer();
                }
            }

            // Discrete hover flaps - less frequent than combat flaps
            if (discreteFlapCooldown <= 0 && (isClimbing || Math.random() < 0.03)) {
                triggerDiscreteFlapAnimation();
                discreteFlapCooldown = 30;
            }

        } else {
            // GLIDING MODE - the bread and butter of dragon flight
            hoveringController.decreaseTimer();

            // Intelligent flap detection
            boolean needsActiveFlapping = isDiving || isClimbing || isTurning || isSlowSpeed || isDescending;

            if (needsActiveFlapping) {
                // Only update every 5 ticks to prevent jank
                if (dragon.tickCount % 5 == 0) {
                    flappingController.increaseTimer();
                }
                if (dragon.tickCount % 3 == 0) {
                    glidingController.decreaseTimer();
                }

                // Discrete flap trigger (only when physics demands it)
                if (discreteFlapCooldown <= 0) {
                    boolean shouldTriggerFlap = isDiving && velocity.y < -0.15;

                    if (isClimbing && velocity.y > 0.15) shouldTriggerFlap = true;
                    if (isTurning && Math.abs(dragon.getBanking()) > 30.0f) shouldTriggerFlap = true;
                    if (isDescending && Math.random() < 0.08) shouldTriggerFlap = true;

                    if (shouldTriggerFlap) {
                        triggerDiscreteFlapAnimation();
                        discreteFlapCooldown = 25 + dragon.getRandom().nextInt(15);
                    }
                }

            } else {
                // THE MAGIC MOMENT - pure gliding efficiency
                if (dragon.tickCount % 3 == 0) {
                    flappingController.decreaseTimer();
                }
                if (dragon.tickCount % 5 == 0) {
                    glidingController.increaseTimer();
                }

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

            // Modulate based on banking and speed
            Vec3 velocity = dragon.getDeltaMovement();
            double speed = velocity.horizontalDistanceSqr();
            float bankingFactor = Math.abs(dragon.getBanking()) / 45.0f;

            targetIntensity += (float) (speed * 2.0f + bankingFactor * 0.3f);
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
        // Only trigger if no critical animation is playing
        if (dragon.getActiveAbility() == null || dragon.getActiveAbility().canCancelActiveAbility()) {
            playFlappingSound();
        }
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
    }

    public void readFromNBT(net.minecraft.nbt.CompoundTag tag) {
        // Restore timers after load
        wingBeatIntensity = tag.getFloat("WingBeatIntensity");
        prevWingBeatIntensity = wingBeatIntensity;
    }
}