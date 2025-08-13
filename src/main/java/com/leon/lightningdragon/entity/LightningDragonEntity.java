package com.leon.lightningdragon.entity;

import com.leon.lightningdragon.ai.navigation.DragonFlightMoveHelper;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

import java.util.EnumSet;

/**
 * Lightning Dragon with PROPER state management and unfucked navigation
 */
public class LightningDragonEntity extends TamableAnimal implements GeoEntity, FlyingAnimal {

    // ===== CONSTANTS (no more magic numbers bullshit) =====
    private static final double HOVER_UPWARD_FORCE = 0.075D;
    private static final double LANDING_DOWNWARD_FORCE = 0.3D;
    private static final double FALLING_RESISTANCE = 0.6D;
    private static final float BANKING_DECAY_RATE = 0.95f;
    private static final float BANKING_THRESHOLD = 0.5f;
    private static final int HOVER_TIME_THRESHOLD = 30;
    private static final int LANDING_TIME_THRESHOLD = 40;
    private static final float ANIMATION_PROGRESS_SPEED = 0.2F;
    private static final int MAX_ANIMATION_PROGRESS = 5;

    // ===== LANDING SYSTEM CONSTANTS =====
    private static final int CIRCLING_TIME = 60;      // ticks to circle and search
    private static final int APPROACHING_TIME = 40;   // ticks to approach landing spot
    private static final int DESCENDING_TIME = 60;    // ticks for descent phase
    private static final int TOUCHDOWN_TIME = 30;     // ticks for touchdown absorption
    private static final double LANDING_SEARCH_RADIUS = 16.0;
    private static final double APPROACH_DISTANCE = 8.0;
    private static final double DESCENT_HEIGHT = 5.0;

    // ===== DATA ACCESSORS =====
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOVERING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_BANKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PREV_BANKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LANDING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);

    // ===== LANDING SYSTEM ENUM =====
    public enum LandingPhase {
        CIRCLING,     // Looking for a good landing spot, circling area
        APPROACHING,  // Found spot, approaching it
        DESCENDING,   // Close to spot, descending with landing configuration
        TOUCHDOWN     // Touching down, impact absorption
    }

    // ===== FLIGHT TRACKING =====
    public int timeFlying = 0;
    public Vec3 lastFlightTargetPos;
    public boolean landingFlag = false;

    // ===== LANDING SYSTEM STATE =====
    private LandingPhase currentLandingPhase = LandingPhase.CIRCLING;
    private int landingTimer = 0;
    private float landingProgress = 0.0f;
    private float prevLandingProgress = 0.0f;
    private Vec3 targetLandingSpot = null;
    private Vec3 circleCenter = null;
    private float circleRadius = 8.0f;

    // ===== ANIMATION PROGRESS =====
    private float flyProgress = 0;
    private float prevFlyProgress = 0;
    private float hoverProgress = 0;
    private float prevHoverProgress = 0;

    // ===== DODGE SYSTEM =====
    private boolean dodging = false;
    private int dodgeTicksLeft = 0;
    private Vec3 dodgeVec = Vec3.ZERO;

    // ===== PROPER NAVIGATION (unfucked version) =====
    private GroundPathNavigation groundNav;
    private FlyingPathNavigation airNav;
    private boolean usingAirNav = false;

    // ===== GECKOLIB ANIMATIONS =====
    private final AnimatableInstanceCache geckoCache = new SingletonAnimatableInstanceCache(this);

    private static final RawAnimation IDLE_GROUND = RawAnimation.begin().thenLoop("animation.lightning_dragon.idle_ground");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.lightning_dragon.walk");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.lightning_dragon.sit");
    private static final RawAnimation FLY_FORWARD = RawAnimation.begin().thenLoop("animation.lightning_dragon.fly_idle");
    private static final RawAnimation FLY_HOVER = RawAnimation.begin().thenLoop("animation.lightning_dragon.fly_hovering");
    private static final RawAnimation LANDING_CIRCLE = RawAnimation.begin().thenLoop("animation.lightning_dragon.landing_circle");
    private static final RawAnimation LANDING_APPROACH = RawAnimation.begin().thenPlay("animation.lightning_dragon.landing_approach");
    private static final RawAnimation LANDING_DESCEND = RawAnimation.begin().thenPlay("animation.lightning_dragon.landing_descend");
    private static final RawAnimation LANDING_TOUCHDOWN = RawAnimation.begin().thenPlay("animation.lightning_dragon.landing_touchdown");
    private static final RawAnimation TARGET_TRACKING = RawAnimation.begin().thenLoop("animation.lightning_dragon.target_tracking");

    public LightningDragonEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.25F);

        // Initialize BOTH navigators once (unfucked approach)
        this.groundNav = new GroundPathNavigation(this, level);
        this.airNav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }
        };
        this.airNav.setCanOpenDoors(false);
        this.airNav.setCanFloat(false);
        this.airNav.setCanPassDoors(false);

        // Start with ground navigation
        this.navigation = this.groundNav;
        this.moveControl = new MoveControl(this);
        this.lookControl = new DragonLookController(this);

        // Pathfinding setup
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.FENCE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.FLYING_SPEED, 0.60D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_HOVERING, false);
        this.entityData.define(DATA_BANKING, 0.0f);
        this.entityData.define(DATA_PREV_BANKING, 0.0f);
        this.entityData.define(DATA_LANDING, false);
    }

    /**
     * UNFUCKED navigation switching - preserves pathfinding state
     */
    private void switchToAirNavigation() {
        if (!this.usingAirNav) {
            this.navigation = this.airNav;
            this.moveControl = new DragonFlightMoveHelper(this);
            this.usingAirNav = true;
        }
    }

    private void switchToGroundNavigation() {
        if (this.usingAirNav) {
            this.navigation = this.groundNav;
            this.moveControl = new MoveControl(this);
            this.usingAirNav = false;
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        // This method is called by parent constructor, so we return ground nav as default
        return new GroundPathNavigation(this, level);
    }

    // ===== CENTRALIZED STATE MANAGEMENT =====
    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying) {
        if (flying && this.isBaby()) {
            flying = false;
        }

        boolean wasFlying = isFlying();
        this.entityData.set(DATA_FLYING, flying);

        // Handle navigation switching when state actually changes
        if (wasFlying != flying) {
            if (flying) {
                switchToAirNavigation();
            } else {
                switchToGroundNavigation();
                // Reset banking when landing
                setBanking(0.0f);
                setPrevBanking(0.0f);
            }
        }
    }

    public boolean isHovering() {
        return this.entityData.get(DATA_HOVERING);
    }

    public void setHovering(boolean hovering) {
        if (hovering && this.isBaby()) {
            hovering = false;
        }
        this.entityData.set(DATA_HOVERING, hovering);
    }

    // ===== BANKING SYSTEM =====
    public float getBanking() {
        return this.entityData.get(DATA_BANKING);
    }

    public void setBanking(float banking) {
        this.entityData.set(DATA_BANKING, banking);
    }

    public float getPrevBanking() {
        return this.entityData.get(DATA_PREV_BANKING);
    }

    public void setPrevBanking(float prevBanking) {
        this.entityData.set(DATA_PREV_BANKING, prevBanking);
    }

    // ===== LANDING SYSTEM =====
    public boolean isLanding() {
        return this.entityData.get(DATA_LANDING);
    }

    public void setLanding(boolean landing) {
        boolean wasLanding = isLanding();
        this.entityData.set(DATA_LANDING, landing);

        if (landing && !wasLanding) {
            // Start landing sequence
            initiateLanding();
        } else if (!landing && wasLanding) {
            // Cancel landing
            cancelLanding();
        }
    }

    public LandingPhase getLandingPhase() {
        return currentLandingPhase;
    }

    public float getLandingProgress(float partialTick) {
        return Mth.lerp(partialTick, prevLandingProgress, landingProgress);
    }

    // ===== ANIMATION PROGRESS GETTERS =====
    public float getFlyProgress(float partialTick) {
        return (prevFlyProgress + (flyProgress - prevFlyProgress) * partialTick) * ANIMATION_PROGRESS_SPEED;
    }

    public float getHoverProgress(float partialTick) {
        return (prevHoverProgress + (hoverProgress - prevHoverProgress) * partialTick) * ANIMATION_PROGRESS_SPEED;
    }

    // ===== DODGE SYSTEM =====
    public boolean isDodging() {
        return dodging;
    }

    public void beginDodge(Vec3 vec, int ticks) {
        this.dodging = true;
        this.dodgeVec = vec;
        this.dodgeTicksLeft = Math.max(1, ticks);
        this.getNavigation().stop();
        this.hasImpulse = true;
    }

    public boolean isChargingBigAttack() {
        return false;
    }

    public boolean isVulnerable() {
        return false;
    }

    // ===== SPAWN CHECK =====
    public static boolean canSpawnHere(EntityType<LightningDragonEntity> type,
                                       net.minecraft.world.level.LevelAccessor level,
                                       MobSpawnType reason,
                                       BlockPos pos,
                                       net.minecraft.util.RandomSource random) {
        BlockPos below = pos.below();
        boolean solidGround = level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
        boolean feetFree = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        boolean headFree = level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
        return solidGround && feetFree && headFree;
    }

    // ===== UNFUCKED TICK METHOD =====
    @Override
    public void tick() {
        super.tick();

        // Handle dodge movement first (highest priority)
        if (!level().isClientSide && dodging) {
            handleDodgeMovement();
            return;
        }

        // Update animation progress
        updateAnimationProgress();

        // Handle flight logic
        handleFlightLogic();

        // Handle banking (only when not flying to avoid conflicts)
        if (!level().isClientSide) {
            updateBanking();
        }
    }

    /**
     * Handles dodge movement physics
     */
    private void handleDodgeMovement() {
        Vec3 current = this.getDeltaMovement();
        Vec3 boosted = current.add(dodgeVec.scale(0.25));
        this.setDeltaMovement(boosted.multiply(0.92, 0.95, 0.92));
        this.hasImpulse = true;

        if (--dodgeTicksLeft <= 0) {
            dodging = false;
            dodgeVec = Vec3.ZERO;
        }
    }

    /**
     * Updates animation progress values
     */
    private void updateAnimationProgress() {
        prevFlyProgress = flyProgress;
        prevHoverProgress = hoverProgress;

        if (isFlying() && flyProgress < MAX_ANIMATION_PROGRESS) {
            flyProgress++;
        }
        if (!isFlying() && flyProgress > 0F) {
            flyProgress--;
        }
        if (isHovering() && hoverProgress < MAX_ANIMATION_PROGRESS) {
            hoverProgress++;
        }
        if (!isHovering() && hoverProgress > 0F) {
            hoverProgress--;
        }
    }

    private void updateBanking() {
        if (!isFlying() || isLanding()) {
            // Use the smooth approach function you actually wrote
            float smoothedBanking = DragonMathUtil.approachSmooth(
                    getBanking(),      // current value
                    getPrevBanking(),  // previous value
                    0.0f,              // target: return to neutral
                    2.0f,              // desired speed of approach
                    0.1f               // acceleration/deceleration rate
            );

            setPrevBanking(getBanking());
            setBanking(smoothedBanking);
        }
    }

    /**
     * Handles all flight-related logic
     */
    private void handleFlightLogic() {
        if (isFlying()) {
            handleFlyingTick();
        } else {
            handleGroundedTick();
        }

        // Handle landing system (can occur during flight)
        if (isLanding()) {
            handleLandingSystem();
        }
    }

    /**
     * Logic for when dragon is flying
     */
    private void handleFlyingTick() {
        timeFlying++;

        // Reduce falling speed while flying
        if (this.getDeltaMovement().y < 0 && this.isAlive()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1, FALLING_RESISTANCE, 1));
        }

        // Auto-land when sitting or being a passenger
        if (this.isOrderedToSit() || this.isPassenger()) {
            this.setHovering(false);
            this.setFlying(false);
            return;
        }

        // Server-side logic
        if (!level().isClientSide) {
            handleServerFlightLogic();
        }
    }

    /**
     * Server-side flight logic
     */
    private void handleServerFlightLogic() {
        // Update hovering state
        this.setHovering(shouldHover() && isFlying());

        // Handle hovering physics
        if (this.isHovering() && isFlying() && this.isAlive()) {
            if (timeFlying < HOVER_TIME_THRESHOLD) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, HOVER_UPWARD_FORCE, 0));
            }
            if (landingFlag) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, -LANDING_DOWNWARD_FORCE, 0));
            }
        }

        // Landing logic when touching ground
        if (!this.isHovering() && this.isFlying() && timeFlying > LANDING_TIME_THRESHOLD && this.onGround()) {
            // Auto-land if on ground and not hovering
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                this.setFlying(false);
            }
        }
    }

    /**
     * Logic for when dragon is grounded
     */
    private void handleGroundedTick() {
        timeFlying = 0;
    }

    /**
     * Handles banking decay when not flying
     */
    private void handleBankingDecay() {
        float currentBanking = getBanking();
        if (Math.abs(currentBanking) > BANKING_THRESHOLD) {
            setPrevBanking(currentBanking);
            setBanking(currentBanking * BANKING_DECAY_RATE);
        } else {
            setBanking(0.0f);
            setPrevBanking(0.0f);
        }
    }

    /**
     * Determines if dragon should hover
     */
    private boolean shouldHover() {
        return landingFlag || timeFlying < HOVER_TIME_THRESHOLD || (getTarget() != null && getTarget().isAlive());
    }

    // ===== LANDING SYSTEM IMPLEMENTATION =====

    /**
     * Initiates the landing sequence
     */
    private void initiateLanding() {
        if (!isFlying()) {
            // Already on ground, no need to land
            setLanding(false);
            return;
        }

        currentLandingPhase = LandingPhase.CIRCLING;
        landingTimer = 0;
        landingProgress = 0.0f;
        prevLandingProgress = 0.0f;
        circleCenter = this.position();
        circleRadius = 8.0f + random.nextFloat() * 8.0f; // Vary circle size
        targetLandingSpot = null;

        // Stop current navigation to take manual control
        this.getNavigation().stop();
    }

    /**
     * Cancels the landing sequence
     */
    private void cancelLanding() {
        currentLandingPhase = LandingPhase.CIRCLING;
        landingTimer = 0;
        landingProgress = 0.0f;
        prevLandingProgress = 0.0f;
        targetLandingSpot = null;
        circleCenter = null;
        landingFlag = false;
    }

    /**
     * Main landing system state machine
     */
    private void handleLandingSystem() {
        if (!level().isClientSide) {
            prevLandingProgress = landingProgress;
            landingTimer++;

            switch (currentLandingPhase) {
                case CIRCLING -> handleCirclingPhase();
                case APPROACHING -> handleApproachingPhase();
                case DESCENDING -> handleDescendingPhase();
                case TOUCHDOWN -> handleTouchdownPhase();
            }
        }
    }

    /**
     * CIRCLING: Look for landing spot while circling
     */
    private void handleCirclingPhase() {
        landingProgress = Math.min(1.0f, landingTimer / (float) CIRCLING_TIME);

        // Circle around the area looking for a good landing spot
        if (circleCenter != null) {
            double angle = (landingTimer * 0.1) % (Math.PI * 2);
            Vec3 circlePos = circleCenter.add(
                    Math.cos(angle) * circleRadius,
                    2.0, // Stay a bit above circle center
                    Math.sin(angle) * circleRadius
            );

            // Use move controller to circle
            if (this.moveControl instanceof DragonFlightMoveHelper) {
                this.getMoveControl().setWantedPosition(circlePos.x, circlePos.y, circlePos.z, 0.8);
            }

            // Look for landing spots while circling
            if (landingTimer % 10 == 0) { // Check every 10 ticks
                Vec3 potentialSpot = findLandingSpot();
                if (potentialSpot != null) {
                    targetLandingSpot = potentialSpot;
                    advanceToApproaching();
                    return;
                }
            }
        }

        // If we've circled long enough without finding a spot, pick one anyway
        if (landingTimer >= CIRCLING_TIME) {
            targetLandingSpot = findFallbackLandingSpot();
            advanceToApproaching();
        }
    }

    /**
     * APPROACHING: Approach the selected landing spot
     */
    private void handleApproachingPhase() {
        landingProgress = Math.min(1.0f, landingTimer / (float) APPROACHING_TIME);

        if (targetLandingSpot != null) {
            // Approach the landing spot from above
            Vec3 approachPos = targetLandingSpot.add(0, DESCENT_HEIGHT, 0);

            if (this.moveControl instanceof DragonFlightMoveHelper) {
                this.getMoveControl().setWantedPosition(approachPos.x, approachPos.y, approachPos.z, 1.0);
            }

            // Check if we're close enough to start descending
            double distToApproach = this.position().distanceTo(approachPos);
            if (distToApproach < APPROACH_DISTANCE || landingTimer >= APPROACHING_TIME) {
                advanceToDescending();
            }
        } else {
            // No target spot, find one quickly
            targetLandingSpot = findFallbackLandingSpot();
        }
    }

    /**
     * DESCENDING: Final approach and descent to landing spot
     */
    private void handleDescendingPhase() {
        landingProgress = Math.min(1.0f, landingTimer / (float) DESCENDING_TIME);

        if (targetLandingSpot != null) {
            // Descend to the landing spot
            if (this.moveControl instanceof DragonFlightMoveHelper) {
                this.getMoveControl().setWantedPosition(
                        targetLandingSpot.x,
                        targetLandingSpot.y + 1.0, // Slightly above ground
                        targetLandingSpot.z,
                        0.6 // Slower for controlled descent
                );
            }

            // Apply landing physics - increase downward motion
            Vec3 currentMovement = this.getDeltaMovement();
            double descentForce = 0.08 * landingProgress; // Gradually increase descent rate
            this.setDeltaMovement(currentMovement.add(0, -descentForce, 0));

            // Check if we're close to ground or time is up
            double distToGround = this.getY() - targetLandingSpot.y;
            if (distToGround < 2.0 || this.onGround() || landingTimer >= DESCENDING_TIME) {
                advanceToTouchdown();
            }
        }
    }

    /**
     * TOUCHDOWN: Final impact and settling
     */
    private void handleTouchdownPhase() {
        landingProgress = Math.min(1.0f, landingTimer / (float) TOUCHDOWN_TIME);

        // Absorption/settling physics
        if (!this.onGround()) {
            // Still falling, apply final descent
            Vec3 currentMovement = this.getDeltaMovement();
            this.setDeltaMovement(currentMovement.add(0, -0.15, 0));
        } else {
            // On ground, absorb impact and settle
            Vec3 currentMovement = this.getDeltaMovement();
            this.setDeltaMovement(currentMovement.multiply(0.8, 0.5, 0.8)); // Friction and settling
        }

        // Complete landing
        if (landingTimer >= TOUCHDOWN_TIME || (this.onGround() && this.getDeltaMovement().length() < 0.1)) {
            completeLanding();
        }
    }

    // ===== LANDING PHASE TRANSITIONS =====

    private void advanceToApproaching() {
        currentLandingPhase = LandingPhase.APPROACHING;
        landingTimer = 0;
        landingProgress = 0.0f;
    }

    private void advanceToDescending() {
        currentLandingPhase = LandingPhase.DESCENDING;
        landingTimer = 0;
        landingProgress = 0.0f;
    }

    private void advanceToTouchdown() {
        currentLandingPhase = LandingPhase.TOUCHDOWN;
        landingTimer = 0;
        landingProgress = 0.0f;
    }

    private void completeLanding() {
        setLanding(false);
        setFlying(false);
        landingFlag = false;

        // Allow normal AI to resume
        this.getNavigation().stop();
    }

    // ===== LANDING SPOT FINDING =====

    /**
     * Finds a suitable landing spot near the dragon
     */
    private Vec3 findLandingSpot() {
        Vec3 dragonPos = this.position();

        for (int attempts = 0; attempts < 8; attempts++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 4.0 + random.nextDouble() * LANDING_SEARCH_RADIUS;

            double x = dragonPos.x + Math.cos(angle) * distance;
            double z = dragonPos.z + Math.sin(angle) * distance;

            BlockPos groundPos = findGroundLevel(new BlockPos((int)x, (int)dragonPos.y, (int)z));

            if (isValidLandingSpot(groundPos)) {
                return Vec3.atCenterOf(groundPos.above());
            }
        }

        return null;
    }

    /**
     * Finds any landing spot as fallback (less picky)
     */
    private Vec3 findFallbackLandingSpot() {
        Vec3 dragonPos = this.position();

        // Try current position first
        BlockPos groundPos = findGroundLevel(new BlockPos((int)dragonPos.x, (int)dragonPos.y, (int)dragonPos.z));
        if (level().getBlockState(groundPos).isSolid()) {
            return Vec3.atCenterOf(groundPos.above());
        }

        // Emergency fallback - just land wherever
        return new Vec3(dragonPos.x, dragonPos.y - 5, dragonPos.z);
    }

    private BlockPos findGroundLevel(BlockPos startPos) {
        BlockPos.MutableBlockPos pos = startPos.mutable();

        // Scan down to find ground
        for (int i = 0; i < 32; i++) {
            if (level().getBlockState(pos).isSolid()) {
                return pos.immutable();
            }
            pos.move(0, -1, 0);

            if (pos.getY() <= level().getMinBuildHeight()) {
                break;
            }
        }

        // Fallback
        return new BlockPos(startPos.getX(), (int)this.getY() - 10, startPos.getZ());
    }

    private boolean isValidLandingSpot(BlockPos pos) {
        // Must be solid ground
        if (!level().getBlockState(pos).isSolid()) {
            return false;
        }

        // Must have space above for dragon
        for (int i = 1; i <= 4; i++) {
            if (!level().getBlockState(pos.above(i)).isAir()) {
                return false;
            }
        }

        // Don't land in dangerous fluids
        if (!level().getFluidState(pos).isEmpty()) {
            return false;
        }

        return true;
    }

    // ===== TRAVEL METHOD =====
    @Override
    public void travel(Vec3 motion) {
        // Don't interfere with dodge movement
        if (dodging) {
            super.travel(motion);
            return;
        }

        if (isFlying()) {
            if (isHovering()) {
                handleHovering(motion);
            } else {
                handleFlying(motion);
            }
        } else {
            super.travel(motion);
        }
    }

    // Gliding physics for normal flying
    private void handleFlying(Vec3 motion) {
        Vec3 vec3 = this.getDeltaMovement();
        if (vec3.y > -0.5D) {
            this.fallDistance = 1.0F;

        }

        Vec3 moveDirection = this.getLookAngle();
        moveDirection = moveDirection.normalize();
        float f6 = this.getXRot() * ((float) Math.PI / 180F);
        double d9 = Math.sqrt(moveDirection.x * moveDirection.x + moveDirection.z * moveDirection.z);
        double d11 = Math.sqrt(vec3.horizontalDistanceSqr());
        double d12 = moveDirection.length();
        float f3 = Mth.cos(f6);
        f3 = (float) ((double) f3 * (double) f3 * Math.min(1.0D, d12 / 0.4D));

        double d0 = 0.08D; // gravity
        vec3 = this.getDeltaMovement().add(0.0D, d0 * (-1.0D + (double) f3 * 0.75D), 0.0D);

        if (vec3.y < 0.0D && d9 > 0.0D) {
            double d3 = vec3.y * -0.1D * (double) f3;
            vec3 = vec3.add(moveDirection.x * d3 / d9, d3, moveDirection.z * d3 / d9);
        }

        if (f6 < 0.0F && d9 > 0.0D) {
            double d13 = d11 * (double) (-Mth.sin(f6)) * 0.04D;
            vec3 = vec3.add(-moveDirection.x * d13 / d9, d13 * 3.2D, -moveDirection.z * d13 / d9);
        }

        if (d9 > 0.0D) {
            vec3 = vec3.add((moveDirection.x / d9 * d11 - vec3.x) * 0.1D, 0.0D, (moveDirection.z / d9 * d11 - vec3.z) * 0.1D);
        }

        this.setDeltaMovement(vec3.multiply(0.99F, 0.98F, 0.99F));
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    // EXACT COPY of Naga's hovering physics
    private void handleHovering(Vec3 motion) {
        BlockPos ground = new BlockPos((int) this.getX(), (int) (this.getBoundingBox().minY - 1.0D), (int) this.getZ());
        float f = 0.91F;
        if (this.onGround()) {
            f = this.level().getBlockState(ground).getFriction(level(), ground, this) * 0.91F;
        }

        float f1 = 0.16277137F / (f * f * f);
        f = 0.91F;
        if (this.onGround()) {
            f = this.level().getBlockState(ground).getFriction(level(), ground, this) * 0.91F;
        }

        this.moveRelative(this.onGround() ? 0.1F * f1 : 0.02F, motion);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(f));

        // Stop when close to destination like Naga
        BlockPos destination = this.getNavigation().getTargetPos();
        if (destination != null) {
            double dx = destination.getX() - this.getX();
            double dy = destination.getY() - this.getY();
            double dz = destination.getZ() - this.getZ();
            double distanceToDest = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distanceToDest < 0.1) {
                setDeltaMovement(0, 0, 0);
            }
        }
    }

    // ===== CUSTOM LOOK CONTROLLER =====
    public class DragonLookController extends LookControl {
        public DragonLookController(LightningDragonEntity dragon) {
            super(dragon);
        }

        @Override
        public void tick() {
            if (this.lookAtCooldown > 0 && this.getYRotD().isPresent()) {
                --this.lookAtCooldown;
                this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.getYRotD().get(), this.yMaxRotSpeed);
            } else {
                this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
            }

            if (!this.mob.getNavigation().isDone()) {
                this.mob.yHeadRot = Mth.rotateIfNecessary(this.mob.yHeadRot, this.mob.yBodyRot, (float) this.mob.getMaxHeadYRot());
            }
        }
    }

    // ===== AI GOALS =====
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new com.leon.lightningdragon.ai.goals.DragonDodgeGoal(this));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new FloatGoal(this));
        this.goalSelector.addGoal(4, new DragonFollowOwnerGoal());
        this.goalSelector.addGoal(5, new com.leon.lightningdragon.ai.goals.DragonGroundWanderGoal(this, 1.0, 60));
        this.goalSelector.addGoal(6, new com.leon.lightningdragon.ai.goals.DragonFlightGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> target instanceof Player p && !this.isTame() && !p.isCreative()));
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (damageSource.is(DamageTypes.FALL)) {
            return false; // Nope, fuck fall damage
        }
        return super.hurt(damageSource, amount);
    }

    // ===== TAMING & INTERACTION =====
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!this.isTame()) {
            if (this.isFood(itemstack)) {
                if (!player.getAbilities().instabuild) itemstack.shrink(1);

                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.setOrderedToSit(true);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                    return InteractionResult.SUCCESS;
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                    return InteractionResult.CONSUME;
                }
            }
        } else {
            if (player.equals(this.getOwner())) {
                if (player.isCrouching() && hand == InteractionHand.MAIN_HAND) {
                    if (isFlying()) {
                        this.setLanding(true); // Use new landing system
                    } else {
                        this.setFlying(true);
                    }
                    return InteractionResult.SUCCESS;
                } else if (itemstack.isEmpty()) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void setOrderedToSit(boolean sitting) {
        super.setOrderedToSit(sitting);
        if (sitting) {
            if (isFlying()) {
                this.setLanding(true); // Use new landing system
            }
            this.getNavigation().stop();
        }
    }

    public boolean isFlightControllerStuck() {
        if (this.moveControl instanceof DragonFlightMoveHelper flightHelper) {
            return flightHelper.hasGivenUp();
        }
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT);
    }

    public boolean isFlightPathClear(Vec3 target) {
        if (target == null) return false;

        BlockHitResult result = this.level().clip(new ClipContext(
                this.getEyePosition(),
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        return result.getType() == HitResult.Type.MISS ||
                result.getLocation().distanceTo(this.position()) > target.distanceTo(this.position()) * 0.9;
    }

    // ===== SAVE/LOAD =====
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", isFlying());
        tag.putBoolean("Hovering", isHovering());
        tag.putBoolean("Landing", isLanding());
        tag.putInt("TimeFlying", timeFlying);
        tag.putFloat("Banking", getBanking());
        tag.putBoolean("UsingAirNav", usingAirNav);
        tag.putString("LandingPhase", currentLandingPhase.name());
        tag.putInt("LandingTimer", landingTimer);
        tag.putFloat("LandingProgress", landingProgress);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setFlying(tag.getBoolean("Flying"));
        this.setHovering(tag.getBoolean("Hovering"));
        this.setLanding(tag.getBoolean("Landing"));
        this.timeFlying = tag.getInt("TimeFlying");
        this.setBanking(tag.getFloat("Banking"));
        this.usingAirNav = tag.getBoolean("UsingAirNav");

        // Restore landing system state
        if (tag.contains("LandingPhase")) {
            try {
                this.currentLandingPhase = LandingPhase.valueOf(tag.getString("LandingPhase"));
            } catch (IllegalArgumentException e) {
                this.currentLandingPhase = LandingPhase.CIRCLING;
            }
        }
        this.landingTimer = tag.getInt("LandingTimer");
        this.landingProgress = tag.getFloat("LandingProgress");
        this.prevLandingProgress = this.landingProgress;

        // Restore proper navigation after loading
        if (this.usingAirNav) {
            switchToAirNavigation();
        } else {
            switchToGroundNavigation();
        }
    }

    // ===== GECKOLIB =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementAnimationPredicate));
    }

    private PlayState movementAnimationPredicate(AnimationState<LightningDragonEntity> state) {
        if (this.isOrderedToSit()) {
            state.setAndContinue(SIT);
        } else if (isLanding()) {
            // Landing animations based on phase
            switch (getLandingPhase()) {
                case CIRCLING -> state.setAndContinue(LANDING_CIRCLE);
                case APPROACHING -> state.setAndContinue(LANDING_APPROACH);
                case DESCENDING -> state.setAndContinue(LANDING_DESCEND);
                case TOUCHDOWN -> state.setAndContinue(LANDING_TOUCHDOWN);
            }
        } else if (isFlying()) {
            // Check for target tracking first
            if (getTarget() != null && getTarget().isAlive()) {
                state.setAndContinue(TARGET_TRACKING);
            }
            // Normal flight states (banking is handled by physics, not animations)
            else if (isHovering()) {
                state.setAndContinue(FLY_HOVER);
            } else {
                state.setAndContinue(FLY_FORWARD);
            }
        } else {
            // Ground animations
            if (this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6) {
                state.setAndContinue(WALK);
            } else {
                state.setAndContinue(IDLE_GROUND);
            }
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geckoCache;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    // ===== SIMPLIFIED FOLLOW GOAL =====
    class DragonFollowOwnerGoal extends Goal {
        private static final double START_FOLLOW_DIST = 12.0;
        private static final double STOP_FOLLOW_DIST = 6.0;
        private static final double TELEPORT_DIST = 64.0;

        public DragonFollowOwnerGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!isTame() || isOrderedToSit()) return false;
            LivingEntity owner = getOwner();
            if (owner == null || !owner.isAlive()) return false;
            if (owner.level() != level()) return false;

            double dist = distanceToSqr(owner);
            return dist > START_FOLLOW_DIST * START_FOLLOW_DIST;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity owner = getOwner();
            if (owner == null || !owner.isAlive() || isOrderedToSit()) return false;
            if (owner.level() != level()) return false;

            double dist = distanceToSqr(owner);
            return dist > STOP_FOLLOW_DIST * STOP_FOLLOW_DIST;
        }

        @Override
        public void start() {
            if (!isFlying()) {
                setFlying(true);
                setHovering(true);
            }
        }

        @Override
        public void tick() {
            LivingEntity owner = getOwner();
            if (owner == null) return;

            double dist = distanceToSqr(owner);

            if (dist > TELEPORT_DIST * TELEPORT_DIST) {
                teleportTo(owner.getX(), owner.getY() + 3, owner.getZ());
                setFlying(true);
                return;
            }

            getLookControl().setLookAt(owner, 10.0f, 10.0f);

            if (isFlying()) {
                getMoveControl().setWantedPosition(owner.getX(), owner.getY() + owner.getBbHeight(), owner.getZ(), 1.2);
            } else {
                getNavigation().moveTo(owner, 1.0);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}