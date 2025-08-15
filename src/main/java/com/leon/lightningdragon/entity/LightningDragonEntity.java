package com.leon.lightningdragon.entity;

import com.leon.lightningdragon.ai.goals.DragonCombatGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import com.leon.lightningdragon.ai.navigation.DragonFlightMoveHelper;
import com.leon.lightningdragon.util.DragonMathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import java.util.List;

// ===== ABILITY SYSTEM IMPORTS =====
// Note: These would need to be created based on Mowzie's system
// For now, I'll include the basic structure

/**
 * Lightning Dragon Entity - COMPLETE REWRITE
 * Now featuring a proper ability system inspired by Mowzie's Sculptor
 * But tailored for a lightning wyvern instead of a humanoid boss
 */
public class LightningDragonEntity extends TamableAnimal implements GeoEntity, FlyingAnimal, RangedAttackMob {

    // ===== ABILITY SYSTEM =====
    // Core abilities every dragon should have
    public static final AbilityType<LightningDragonEntity, LightningBreathAbility> LIGHTNING_BREATH_ABILITY =
            new AbilityType<>("lightning_breath", LightningBreathAbility::new);
    public static final AbilityType<LightningDragonEntity, ThunderStompAbility> THUNDER_STOMP_ABILITY =
            new AbilityType<>("thunder_stomp", ThunderStompAbility::new);
    public static final AbilityType<LightningDragonEntity, WingLightningAbility> WING_LIGHTNING_ABILITY =
            new AbilityType<>("wing_lightning", WingLightningAbility::new);
    public static final AbilityType<LightningDragonEntity, ElectricBiteAbility> ELECTRIC_BITE_ABILITY =
            new AbilityType<>("electric_bite", ElectricBiteAbility::new);
    public static final AbilityType<LightningDragonEntity, HurtAbility<LightningDragonEntity>> HURT_ABILITY =
            new AbilityType<>("dragon_hurt", (type, entity) -> new HurtAbility<>(type, entity,
                    RawAnimation.begin().thenPlay("hurt"), 16, 0));

    // ===== CONSTANTS =====
    private static final double TAKEOFF_UPWARD_FORCE = 0.075D;
    private static final double LANDING_DOWNWARD_FORCE = 0.4D;
    private static final double FALLING_RESISTANCE = 0.6D;
    private static final int TAKEOFF_TIME_THRESHOLD = 30;
    private static final int LANDING_TIME_THRESHOLD = 40;

    // Speed constants
    private static final double WALK_SPEED = 0.25D;
    private static final double RUN_SPEED = 0.45D;
    private static final double SPRINT_SPEED = 0.65D;

    // Combat constants
    private static final int ABILITY_COOLDOWN_TICKS = 60; // 3 seconds between abilities

    // ===== DATA ACCESSORS =====
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TAKEOFF =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOVERING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_BANKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PREV_BANKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LANDING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RUNNING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ATTACKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);

    // ===== STATE VARIABLES =====
    public int timeFlying = 0;
    public Vec3 lastFlightTargetPos;
    public boolean landingFlag = false;

    private int landingTimer = 0;
    private float flyProgress = 0;
    private float prevFlyProgress = 0;
    private float takeoffProgress = 0;
    private float prevTakeoffProgress = 0;
    private float hoverProgress = 0;
    private float prevHoverProgress = 0;
    private int runningTicks = 0;
    private boolean wasFlapping = false;
    private int flightAnimTimer = 0;

    // Dodge system
    private boolean dodging = false;
    private int dodgeTicksLeft = 0;
    private Vec3 dodgeVec = Vec3.ZERO;

    // Navigation
    private GroundPathNavigation groundNav;
    private FlyingPathNavigation airNav;
    private boolean usingAirNav = false;

    // Ability system state
    private Ability<LightningDragonEntity> activeAbility;
    private int abilityCooldown = 0;

    // Combat targeting - simplified from Sculptor
    public float targetDistance = -1;
    public float targetAngle = -1;

    // GeckoLib
    private final AnimatableInstanceCache geckoCache = new SingletonAnimatableInstanceCache(this);

    // ===== CORE ANIMATIONS =====
    public static final RawAnimation GROUND_IDLE = RawAnimation.begin().thenLoop("animation.lightning_dragon.ground_idle");
    public static final RawAnimation GROUND_WALK = RawAnimation.begin().thenLoop("animation.lightning_dragon.walk");
    public static final RawAnimation GROUND_RUN = RawAnimation.begin().thenLoop("animation.lightning_dragon.run");
    public static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.lightning_dragon.sit");

    public static final RawAnimation TAKEOFF = RawAnimation.begin().thenPlay("animation.lightning_dragon.takeoff");
    public static final RawAnimation FLY_GLIDE = RawAnimation.begin().thenLoop("animation.lightning_dragon.fly_gliding");
    public static final RawAnimation FLY_FORWARD = RawAnimation.begin().thenLoop("animation.lightning_dragon.fly_forward");
    public static final RawAnimation FLY_HOVER = RawAnimation.begin().thenLoop("animation.lightning_dragon.fly_hovering");
    public static final RawAnimation LANDING = RawAnimation.begin().thenPlay("animation.lightning_dragon.landing");

    public static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.lightning_dragon.hurt");
    public static final RawAnimation DODGE = RawAnimation.begin().thenPlay("animation.lightning_dragon.dodge");
    public static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.lightning_dragon.roar");

    // Attack animations - these will be defined in the ability classes
    public static final RawAnimation LIGHTNING_BREATH = RawAnimation.begin().thenPlay("animation.lightning_dragon.lightning_breath");
    public static final RawAnimation THUNDER_STOMP = RawAnimation.begin().thenPlay("animation.lightning_dragon.thunder_stomp");
    public static final RawAnimation WING_LIGHTNING = RawAnimation.begin().thenPlay("animation.lightning_dragon.wing_lightning");
    public static final RawAnimation ELECTRIC_BITE = RawAnimation.begin().thenPlay("animation.lightning_dragon.electric_bite");

    public LightningDragonEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.25F);

        // Initialize both navigators
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
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.FLYING_SPEED, 0.60D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_TAKEOFF, false);
        this.entityData.define(DATA_HOVERING, false);
        this.entityData.define(DATA_BANKING, 0.0f);
        this.entityData.define(DATA_PREV_BANKING, 0.0f);
        this.entityData.define(DATA_LANDING, false);
        this.entityData.define(DATA_RUNNING, false);
        this.entityData.define(DATA_ATTACKING, false);
    }

    // ===== ABILITY SYSTEM METHODS =====

    public Ability<LightningDragonEntity> getActiveAbility() {
        return activeAbility;
    }

    public AbilityType<LightningDragonEntity, ?> getActiveAbilityType() {
        return activeAbility != null ? activeAbility.getAbilityType() : null;
    }

    public void setActiveAbility(Ability<LightningDragonEntity> ability) {
        if (this.activeAbility != null) {
            this.activeAbility.end();
        }
        this.activeAbility = ability;
        if (ability != null) {
            ability.start();
        }
    }

    public boolean canUseAbility() {
        return abilityCooldown <= 0 && (activeAbility == null || activeAbility.canCancelActiveAbility());
    }

    public void sendAbilityMessage(AbilityType<LightningDragonEntity, ?> abilityType) {
        if (!level().isClientSide && canUseAbility()) {
            @SuppressWarnings("unchecked")
            AbilityType<LightningDragonEntity, Ability<LightningDragonEntity>> castType =
                    (AbilityType<LightningDragonEntity, Ability<LightningDragonEntity>>) abilityType;

            Ability<LightningDragonEntity> newAbility = castType.createAbility(this);

            if (newAbility.tryAbility()) {
                setActiveAbility(newAbility);
                abilityCooldown = ABILITY_COOLDOWN_TICKS;
            }
        }
    }

    // ===== NAVIGATION SWITCHING =====
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
        return new GroundPathNavigation(this, level);
    }

    // ===== STATE MANAGEMENT =====
    public boolean isFlying() { return this.entityData.get(DATA_FLYING); }

    public void setFlying(boolean flying) {
        if (flying && this.isBaby()) flying = false;

        boolean wasFlying = isFlying();
        this.entityData.set(DATA_FLYING, flying);

        if (wasFlying != flying) {
            if (flying) {
                switchToAirNavigation();
                setRunning(false);
            } else {
                switchToGroundNavigation();
                setBanking(0.0f);
                setPrevBanking(0.0f);
                wasFlapping = false;
                flightAnimTimer = 0;
            }
        }
    }

    public boolean isTakeoff() { return this.entityData.get(DATA_TAKEOFF); }
    public void setTakeoff(boolean takeoff) {
        if (takeoff && this.isBaby()) takeoff = false;
        this.entityData.set(DATA_TAKEOFF, takeoff);
    }

    public boolean isHovering() { return this.entityData.get(DATA_HOVERING); }
    public void setHovering(boolean hovering) {
        if (hovering && this.isBaby()) hovering = false;
        this.entityData.set(DATA_HOVERING, hovering);
    }

    public float getBanking() { return this.entityData.get(DATA_BANKING); }
    public void setBanking(float banking) { this.entityData.set(DATA_BANKING, banking); }
    public float getPrevBanking() { return this.entityData.get(DATA_PREV_BANKING); }
    public void setPrevBanking(float prevBanking) { this.entityData.set(DATA_PREV_BANKING, prevBanking); }

    public boolean isRunning() { return this.entityData.get(DATA_RUNNING); }

    public void setRunning(boolean running) {
        this.entityData.set(DATA_RUNNING, running);
        if (running) {
            runningTicks = 0;
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RUN_SPEED);
        } else {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(WALK_SPEED);
        }
    }

    public void setSprinting(boolean sprinting) {
        if (sprinting) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPRINT_SPEED);
            this.entityData.set(DATA_RUNNING, true);
        } else {
            setRunning(false);
        }
    }

    public boolean isWalking() {
        double speed = getDeltaMovement().horizontalDistanceSqr();
        return speed > 0.005 && speed <= 0.08 && !isRunning() && !isFlying();
    }

    public boolean isActuallyRunning() {
        return isRunning() && getDeltaMovement().horizontalDistanceSqr() > 0.08 && !isFlying();
    }

    public boolean isLanding() { return this.entityData.get(DATA_LANDING); }

    public void setLanding(boolean landing) {
        this.entityData.set(DATA_LANDING, landing);
        if (landing) {
            landingTimer = 0;
            this.getNavigation().stop();
            this.setTakeoff(true);
            this.landingFlag = true;
        } else {
            this.landingFlag = false;
        }
    }

    public boolean isAttacking() { return this.entityData.get(DATA_ATTACKING); }
    public void setAttacking(boolean attacking) { this.entityData.set(DATA_ATTACKING, attacking); }

    // Dodge system
    public boolean isDodging() { return dodging; }

    public void beginDodge(Vec3 vec, int ticks) {
        this.dodging = true;
        this.dodgeVec = vec;
        this.dodgeTicksLeft = Math.max(1, ticks);
        this.getNavigation().stop();
        this.hasImpulse = true;
    }

    // ===== MAIN TICK METHOD =====
    @Override
    public void tick() {
        super.tick();

        // Update target distance for combat
        if (getTarget() != null) {
            targetDistance = distanceTo(getTarget()) - getTarget().getBbWidth() / 2f;
            targetAngle = (float) getAngleBetweenEntities(this, getTarget());
        }

        // Ability system tick
        if (activeAbility != null) {
            if (activeAbility.isUsing()) {
                activeAbility.tick();
            } else {
                activeAbility = null;
            }
        }

        // Cooldown management
        if (abilityCooldown > 0) {
            abilityCooldown--;
        }

        // Handle dodge movement first
        if (!level().isClientSide && dodging) {
            handleDodgeMovement();
            return;
        }

        // Track running time for animations
        if (isRunning()) {
            runningTicks++;
        } else {
            runningTicks = Math.max(0, runningTicks - 2);
        }

        // Auto-stop running if not moving much
        if (isRunning() && getDeltaMovement().horizontalDistanceSqr() < 0.01) {
            setRunning(false);
        }

        // Update animation progress
        updateAnimationProgress();

        // Handle flight logic
        handleFlightLogic();

        // Reset banking when not flying
        if (!level().isClientSide && (!isFlying() || isLanding())) {
            updateBankingReset();
        }
    }

    public double getAngleBetweenEntities(Entity first, Entity second) {
        return Math.atan2(second.getZ() - first.getZ(), second.getX() - first.getX()) * (180 / Math.PI) + 90;
    }

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

    private void updateAnimationProgress() {
        prevFlyProgress = flyProgress;
        prevTakeoffProgress = takeoffProgress;
        prevHoverProgress = hoverProgress;

        if (isFlying() && flyProgress < 5) flyProgress++;
        if (!isFlying() && flyProgress > 0F) flyProgress--;

        if (isTakeoff() && takeoffProgress < 5) takeoffProgress++;
        if (!isTakeoff() && takeoffProgress > 0F) takeoffProgress--;

        if (isHovering() && hoverProgress < 5) hoverProgress++;
        if (!isHovering() && hoverProgress > 0F) hoverProgress--;
    }

    private void updateBankingReset() {
        float smoothedBanking = DragonMathUtil.approachSmooth(
                getBanking(), getPrevBanking(), 0.0f, 2.0f, 0.1f
        );
        setPrevBanking(getBanking());
        setBanking(smoothedBanking);
    }

    private void handleFlightLogic() {
        if (isFlying()) {
            handleFlyingTick();
        } else {
            handleGroundedTick();
        }

        if (isLanding()) {
            handleSimpleLanding();
        }
    }

    private void handleFlyingTick() {
        timeFlying++;

        // Reduce falling speed while flying
        if (this.getDeltaMovement().y < 0 && this.isAlive()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1, FALLING_RESISTANCE, 1));
        }

        // Auto-land when sitting or being a passenger
        if (this.isOrderedToSit() || this.isPassenger()) {
            this.setTakeoff(false);
            this.setHovering(false);
            this.setFlying(false);
            return;
        }

        // Server-side logic
        if (!level().isClientSide) {
            handleServerFlightLogic();
            handleFlightPitchControl();
        }
    }

    private void handleServerFlightLogic() {
        // Update takeoff state
        this.setTakeoff(shouldTakeoff() && isFlying());

        // Handle takeoff physics
        if (this.isTakeoff() && isFlying() && this.isAlive()) {
            if (timeFlying < TAKEOFF_TIME_THRESHOLD) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, TAKEOFF_UPWARD_FORCE, 0));
            }
            if (landingFlag) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, -LANDING_DOWNWARD_FORCE, 0));
            }
        }

        // Landing logic when touching ground
        if (!this.isTakeoff() && this.isFlying() && timeFlying > LANDING_TIME_THRESHOLD && this.onGround()) {
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                this.setFlying(false);
            }
        }
    }

    private void handleGroundedTick() {
        timeFlying = 0;
    }

    private boolean shouldTakeoff() {
        return landingFlag || timeFlying < TAKEOFF_TIME_THRESHOLD || (getTarget() != null && getTarget().isAlive());
    }

    private void handleFlightPitchControl() {
        if (!isFlying() || isLanding() || isHovering()) return;

        Vec3 velocity = getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        if (horizontalSpeed > 0.05) {
            float desiredPitch = (float) (Math.atan2(-velocity.y, horizontalSpeed) * 57.295776F);
            desiredPitch = Mth.clamp(desiredPitch, -25.0F, 35.0F);
            this.setXRot(Mth.approachDegrees(this.getXRot(), desiredPitch, 3.0F));
        }
    }

    private void handleSimpleLanding() {
        if (!level().isClientSide) {
            landingTimer++;

            if (landingTimer > 60 || this.onGround()) {
                setLanding(false);
                setFlying(false);
                setTakeoff(false);
                setHovering(false);
            }
        }
    }

    // ===== TRAVEL METHOD =====
    @Override
    public void travel(Vec3 motion) {
        if (dodging) {
            super.travel(motion);
            return;
        }

        if (isFlying()) {
            handleFlightTravel(motion);
        } else {
            super.travel(motion);
        }
    }

    private void handleFlightTravel(Vec3 motion) {
        if (isTakeoff() || isHovering()) {
            handleHoveringTravel(motion);
        } else {
            handleGlidingTravel(motion);
        }
    }

    private void handleGlidingTravel(Vec3 motion) {
        Vec3 vec3 = this.getDeltaMovement();

        if (vec3.y > -0.5D) {
            this.fallDistance = 1.0F;
        }

        Vec3 moveDirection = this.getLookAngle().normalize();
        float pitchRad = this.getXRot() * ((float) Math.PI / 180F);

        // Apply gliding physics
        vec3 = applyGlidingPhysics(vec3, moveDirection, pitchRad);

        this.setDeltaMovement(vec3.multiply(0.99F, 0.98F, 0.99F));
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    private Vec3 applyGlidingPhysics(Vec3 currentVel, Vec3 moveDirection, float pitchRad) {
        double horizontalSpeed = Math.sqrt(moveDirection.x * moveDirection.x + moveDirection.z * moveDirection.z);
        double currentHorizontalSpeed = Math.sqrt(currentVel.horizontalDistanceSqr());
        double lookDirectionLength = moveDirection.length();

        float pitchFactor = Mth.cos(pitchRad);
        pitchFactor = (float) ((double) pitchFactor * (double) pitchFactor * Math.min(1.0D, lookDirectionLength / 0.4D));

        double gravity = 0.08D;
        Vec3 result = currentVel.add(0.0D, gravity * (-1.0D + (double) pitchFactor * 0.75D), 0.0D);

        // Lift calculation
        if (result.y < 0.0D && horizontalSpeed > 0.0D) {
            double liftFactor = result.y * -0.1D * (double) pitchFactor;
            result = result.add(
                    moveDirection.x * liftFactor / horizontalSpeed,
                    liftFactor,
                    moveDirection.z * liftFactor / horizontalSpeed
            );
        }

        // Dive calculation
        if (pitchRad < 0.0F && horizontalSpeed > 0.0D) {
            double diveFactor = currentHorizontalSpeed * (double) (-Mth.sin(pitchRad)) * 0.04D;
            result = result.add(
                    -moveDirection.x * diveFactor / horizontalSpeed,
                    diveFactor * 3.2D,
                    -moveDirection.z * diveFactor / horizontalSpeed
            );
        }

        // Directional alignment
        if (horizontalSpeed > 0.0D) {
            result = result.add(
                    (moveDirection.x / horizontalSpeed * currentHorizontalSpeed - result.x) * 0.1D,
                    0.0D,
                    (moveDirection.z / horizontalSpeed * currentHorizontalSpeed - result.z) * 0.1D
            );
        }

        return result;
    }

    private void handleHoveringTravel(Vec3 motion) {
        BlockPos ground = new BlockPos((int) this.getX(), (int) (this.getBoundingBox().minY - 1.0D), (int) this.getZ());
        float friction = 0.91F;

        if (this.onGround()) {
            friction = this.level().getBlockState(ground).getFriction(level(), ground, this) * 0.91F;
        }

        float frictionFactor = 0.16277137F / (friction * friction * friction);
        friction = 0.91F;

        if (this.onGround()) {
            friction = this.level().getBlockState(ground).getFriction(level(), ground, this) * 0.91F;
        }

        this.moveRelative(this.onGround() ? 0.1F * frictionFactor : 0.02F, motion);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(friction));

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

    // ===== RANGED ATTACK IMPLEMENTATION =====
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // Let the combat goal handle ability selection
        // Only use this as absolute fallback if combat goal isn't working
        if (canUseAbility() && getActiveAbility() == null) {
            // Check if combat goal is running - if not, use fallback
            boolean combatGoalActive = this.goalSelector.getRunningGoals()
                    .anyMatch(goal -> goal.getGoal() instanceof com.leon.lightningdragon.ai.goals.DragonCombatGoal);

            if (!combatGoalActive) {
                sendAbilityMessage(LIGHTNING_BREATH_ABILITY);
            }
        }
    }


    // ===== UTILITY METHODS =====
    public boolean isFlightControllerStuck() {
        if (this.moveControl instanceof DragonFlightMoveHelper flightHelper) {
            return flightHelper.hasGivenUp();
        }
        return false;
    }

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

    @Override
    public int getMaxHeadYRot() {
        return 75;
    }

    @Override
    public int getMaxHeadXRot() {
        return 60;
    }

    // ===== LOOK CONTROLLER =====
    public class DragonLookController extends LookControl {
        private final LightningDragonEntity dragon;

        public DragonLookController(LightningDragonEntity dragon) {
            super(dragon);
            this.dragon = dragon;
        }

        @Override
        public void tick() {
            if (this.dragon.isFlying()) {
                // Flying - reduce head movement for smoother flight
                this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
                return;
            }

            // GROUND MOVEMENT - enhanced for combat
            if (this.dragon.getTarget() != null && this.dragon.distanceTo(this.dragon.getTarget()) < 20.0) {
                // In combat - always face target directly
                LivingEntity target = this.dragon.getTarget();

                // Calculate angles to target
                double dx = target.getX() - this.dragon.getX();
                double dz = target.getZ() - this.dragon.getZ();
                double dy = target.getEyeY() - this.dragon.getEyeY();
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);

                float targetYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                float targetPitch = (float)(-(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI)));

                // Faster rotation during combat for responsiveness
                float yawSpeed = this.dragon.isRunning() ? 15.0F : 10.0F;
                float pitchSpeed = 8.0F;

                this.dragon.setYRot(Mth.approachDegrees(this.dragon.getYRot(), targetYaw, yawSpeed));
                this.dragon.setXRot(Mth.approachDegrees(this.dragon.getXRot(), targetPitch, pitchSpeed));

                // IMPORTANT: Make body follow head during combat
                this.dragon.yBodyRot = this.dragon.getYRot();
                this.dragon.yHeadRot = this.dragon.getYRot();

            } else {
                // Normal ground behavior - use default look controller
                super.tick();
            }
        }
    }

    // ===== AI GOALS =====
    @Override
    protected void registerGoals() {
        // Basic goals
        this.goalSelector.addGoal(1, new DragonPanicGoal(this));
        this.goalSelector.addGoal(2, new com.leon.lightningdragon.ai.goals.DragonDodgeGoal(this));
        this.goalSelector.addGoal(3, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(4, new FloatGoal(this));

        // Combat goals - NOW USING EXTERNAL CLASS
        this.goalSelector.addGoal(5, new DragonCombatGoal(this));

        // Basic movement
        this.goalSelector.addGoal(7, new DragonFollowOwnerGoal());
        this.goalSelector.addGoal(8, new com.leon.lightningdragon.ai.goals.DragonGroundWanderGoal(this, 1.0, 60));
        this.goalSelector.addGoal(9, new com.leon.lightningdragon.ai.goals.DragonFlightGoal(this));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F));

        // Target selection
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, false,
                target -> !isTame() && !((Player)target).isCreative()));
    }


    // ===== PANIC GOAL FOR SPRINTING =====
    public class DragonPanicGoal extends Goal {
        private final LightningDragonEntity dragon;
        private double posX, posY, posZ;

        public DragonPanicGoal(LightningDragonEntity dragon) {
            this.dragon = dragon;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return dragon.getLastHurtByMob() != null && dragon.getHealth() < dragon.getMaxHealth() * 0.3f;
        }

        @Override
        public void start() {
            Vec3 vec = DefaultRandomPos.getPos(dragon, 16, 7);
            if (vec != null) {
                this.posX = vec.x;
                this.posY = vec.y;
                this.posZ = vec.z;
                dragon.setSprinting(true);
            }
        }

        @Override
        public void tick() {
            dragon.getNavigation().moveTo(posX, posY, posZ, 2.0);
        }

        @Override
        public boolean canContinueToUse() {
            return !dragon.getNavigation().isDone() && dragon.getHealth() < dragon.getMaxHealth() * 0.5f;
        }

        @Override
        public void stop() {
            dragon.setSprinting(false);
            this.posX = this.posY = this.posZ = 0.0D;
        }
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (damageSource.is(DamageTypes.FALL)) {
            return false;
        }

        // Trigger hurt ability
        if (!level().isClientSide && canUseAbility()) {
            sendAbilityMessage(HURT_ABILITY);
        }

        return super.hurt(damageSource, amount);
    }

    // ===== INTERACTION =====
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
                // Flight toggle
                if (player.isCrouching() && !itemstack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
                    if (isFlying()) {
                        this.setLanding(true);
                    } else {
                        this.setFlying(true);
                    }
                    return InteractionResult.SUCCESS;
                }
                // Sit toggle
                else if (itemstack.isEmpty()) {
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
                this.setLanding(true);
            }
            this.setRunning(false);
            this.getNavigation().stop();
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT);
    }

    @Override
    public boolean doHurtTarget(Entity entityIn) {
        // Use ability system for melee attacks
        if (canUseAbility()) {
            sendAbilityMessage(ELECTRIC_BITE_ABILITY);
            return true;
        }
        return false;
    }

    // ===== SAVE/LOAD =====
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", isFlying());
        tag.putBoolean("Takeoff", isTakeoff());
        tag.putBoolean("Hovering", isHovering());
        tag.putBoolean("Landing", isLanding());
        tag.putBoolean("Running", isRunning());
        tag.putInt("TimeFlying", timeFlying);
        tag.putFloat("Banking", getBanking());
        tag.putBoolean("UsingAirNav", usingAirNav);
        tag.putInt("AbilityCooldown", abilityCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setFlying(tag.getBoolean("Flying"));
        this.setTakeoff(tag.getBoolean("Takeoff"));
        this.setHovering(tag.getBoolean("Hovering"));
        this.setLanding(tag.getBoolean("Landing"));
        this.setRunning(tag.getBoolean("Running"));
        this.timeFlying = tag.getInt("TimeFlying");
        this.setBanking(tag.getFloat("Banking"));
        this.usingAirNav = tag.getBoolean("UsingAirNav");
        this.abilityCooldown = tag.getInt("AbilityCooldown");

        if (this.usingAirNav) {
            switchToAirNavigation();
        } else {
            switchToGroundNavigation();
        }
    }

    // ===== GECKOLIB =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 3, this::movementAnimationPredicate));

        // Add sound keyframe handler like the Sculptor
        AnimationController<LightningDragonEntity> controller = new AnimationController<>(this, "movement", 3, this::movementAnimationPredicate);
        controller.setSoundKeyframeHandler(state -> {
            String sound = state.getKeyframeData().getSound();
            if (sound.equals("lightning_blast")) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.HOSTILE, 1.5f, 1.0f);
            } else if (sound.equals("wing_flap")) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP,
                        SoundSource.HOSTILE, 0.8f, 1.2f);
            } else if (sound.equals("electric_charge")) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                        SoundSource.HOSTILE, 1.0f, 2.0f);
            }
        });

        controllers.add(controller);
    }

    private PlayState movementAnimationPredicate(AnimationState<LightningDragonEntity> state) {
        // Ability animations take priority
        if (activeAbility != null && activeAbility.getAnimation() != null) {
            state.setAndContinue(activeAbility.getAnimation());
            return PlayState.CONTINUE;
        }

        if (this.isOrderedToSit()) {
            state.setAndContinue(SIT);
        } else if (isDodging()) {
            state.setAndContinue(DODGE);
        } else if (isLanding()) {
            state.setAndContinue(LANDING);
        } else if (isFlying()) {
            if (timeFlying < 30) {
                state.setAndContinue(TAKEOFF);
            } else if (isHovering()) {
                state.setAndContinue(FLY_FORWARD);
            } else {
                // Smart gliding vs flapping logic
                Vec3 velocity = getDeltaMovement();
                Vec3 lookDirection = getLookAngle();
                float speed = (float) velocity.horizontalDistanceSqr();
                boolean isDiving = lookDirection.y < -0.15 && velocity.y < -0.08;
                boolean isClimbing = velocity.y > 0.12;
                boolean isTurning = Math.abs(getBanking()) > 20.0f;
                boolean isSlowSpeed = speed < 0.08f;

                boolean shouldFlap = isDiving || isClimbing || isTurning || isSlowSpeed;

                if (shouldFlap && !wasFlapping) {
                    wasFlapping = true;
                    flightAnimTimer = 20;
                } else if (!shouldFlap && wasFlapping) {
                    flightAnimTimer--;
                    if (flightAnimTimer <= 0) {
                        wasFlapping = false;
                        flightAnimTimer = 15;
                    }
                } else if (shouldFlap && wasFlapping) {
                    flightAnimTimer = Math.max(flightAnimTimer, 10);
                } else if (!shouldFlap && !wasFlapping) {
                    flightAnimTimer = Math.max(0, flightAnimTimer - 1);
                }

                if (wasFlapping) {
                    state.setAndContinue(FLY_FORWARD); // Use flying forward for flapping
                } else {
                    state.setAndContinue(FLY_GLIDE);
                }
            }
        } else {
            // Ground movement
            if (isActuallyRunning()) {
                state.setAndContinue(GROUND_RUN);
            } else if (isWalking()) {
                state.setAndContinue(GROUND_WALK);
            } else {
                state.setAndContinue(GROUND_IDLE);
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

    // ===== ENHANCED FOLLOW GOAL WITH RUNNING =====
    class DragonFollowOwnerGoal extends Goal {
        private static final double START_FOLLOW_DIST = 12.0;
        private static final double STOP_FOLLOW_DIST = 6.0;
        private static final double TELEPORT_DIST = 64.0;
        private static final double RUN_DIST = 20.0;

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
                setTakeoff(true);
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
                boolean shouldRun = dist > RUN_DIST * RUN_DIST;
                setRunning(shouldRun);

                double speed = shouldRun ? 1.5 : 1.0;
                getNavigation().moveTo(owner, speed);
            }
        }

        @Override
        public void stop() {
            setRunning(false);
            getNavigation().stop();
        }
    }
}

// ===== ABILITY CLASSES =====
// NOTE: These would typically be in separate files, but including here for completeness

/**
 * Base ability class - simplified from Mowzie's system
 */
abstract class Ability<T extends LivingEntity> {
    protected final AbilityType<T, ?> abilityType;
    protected final T user;
    protected int ticksInUse = 0;
    protected boolean isUsing = false;
    protected RawAnimation animation;

    public Ability(AbilityType<T, ?> abilityType, T user) {
        this.abilityType = abilityType;
        this.user = user;
    }

    public abstract boolean tryAbility();

    public void start() {
        this.isUsing = true;
        this.ticksInUse = 0;
    }

    public void tick() {
        if (isUsing) {
            ticksInUse++;
            tickUsing();
        }
    }

    protected void tickUsing() {
        // Override in subclasses
    }

    public void end() {
        this.isUsing = false;
    }

    public boolean canCancelActiveAbility() {
        return true;
    }

    public AbilityType<T, ?> getAbilityType() {
        return abilityType;
    }

    public T getUser() {
        return user;
    }

    public int getTicksInUse() {
        return ticksInUse;
    }

    public boolean isUsing() {
        return isUsing;
    }

    public RawAnimation getAnimation() {
        return animation;
    }

    protected void setAnimation(RawAnimation animation) {
        this.animation = animation;
    }
}

/**
 * Simple ability type registry - FIXED VERSION
 */
class AbilityType<T extends LivingEntity, A extends Ability<T>> {
    private final String name;
    private final java.util.function.BiFunction<AbilityType<T, A>, T, A> constructor;

    public AbilityType(String name, java.util.function.BiFunction<AbilityType<T, A>, T, A> constructor) {
        this.name = name;
        this.constructor = constructor;
    }

    public String getName() {
        return name;
    }

    public A createAbility(T user) {
        return constructor.apply(this, user);
    }
}

/**
 * Lightning Breath Ability - THE MAIN EVENT
 */
class LightningBreathAbility extends Ability<LightningDragonEntity> {
    private static final int WINDUP_TIME = 20;  // 1 second
    private static final int BREATH_TIME = 40;  // 2 seconds
    private static final int RECOVERY_TIME = 20; // 1 second

    private int phase = 0; // 0 = windup, 1 = breathing, 2 = recovery
    private Vec3 targetPos;

    public LightningBreathAbility(AbilityType<LightningDragonEntity, LightningBreathAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.LIGHTNING_BREATH);
    }

    @Override
    public boolean tryAbility() {
        // Can only use if we have a target and they're in range
        LivingEntity target = getUser().getTarget();
        if (target != null && getUser().distanceToSqr(target) <= 625) { // 25 block range
            targetPos = target.getEyePosition();
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);
        if (getUser().isFlying()) {
            getUser().setHovering(true);
        }
        // Look at target
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
        }
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        // Keep looking at target throughout the ability
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
        }

        if (getTicksInUse() <= WINDUP_TIME) {
            // WINDUP PHASE
            phase = 0;
            if (getTicksInUse() == 10) {
                // Play charge sound at keyframe
                getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.0f, 2.0f);
            }

            // Charge particles
            if (getTicksInUse() % 3 == 0 && getUser().level().isClientSide) {
                createChargeParticles();
            }

        } else if (getTicksInUse() <= WINDUP_TIME + BREATH_TIME) {
            // BREATHING PHASE
            if (phase != 1) {
                phase = 1;
                // Start breathing - play thunder sound
                getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.0f);
            }

            // Continuous breath effect every 4 ticks during breathing phase
            if ((getTicksInUse() - WINDUP_TIME) % 4 == 0) {
                breathLightning();
            }

        } else if (getTicksInUse() <= WINDUP_TIME + BREATH_TIME + RECOVERY_TIME) {
            // RECOVERY PHASE
            phase = 2;

        } else {
            // End ability
            this.isUsing = false;
        }
    }

    private void createChargeParticles() {
        Vec3 headPos = getUser().getEyePosition();
        Vec3 lookDir = getUser().getLookAngle();
        Vec3 mouthPos = headPos.add(lookDir.scale(2.0));

        for (int i = 0; i < 5; i++) {
            double offsetX = (getUser().getRandom().nextDouble() - 0.5) * 2.0;
            double offsetY = (getUser().getRandom().nextDouble() - 0.5) * 2.0;
            double offsetZ = (getUser().getRandom().nextDouble() - 0.5) * 2.0;

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    mouthPos.x + offsetX, mouthPos.y + offsetY, mouthPos.z + offsetZ,
                    -offsetX * 0.1, -offsetY * 0.1, -offsetZ * 0.1);
        }
    }

    private void breathLightning() {
        if (getUser().level().isClientSide) return;

        LivingEntity target = getUser().getTarget();
        if (target == null) return;

        Vec3 headPos = getUser().getEyePosition();
        Vec3 spawnPos = headPos.add(getUser().getLookAngle().scale(2.5)); // Spawn in front of mouth

        // Calculate ACTUAL direction to target with prediction
        Vec3 targetPos = target.getEyePosition();
        Vec3 targetVel = target.getDeltaMovement();
        double timeToTarget = targetPos.subtract(spawnPos).length() / 1.2; // Projectile speed estimate
        Vec3 predictedPos = targetPos.add(targetVel.scale(timeToTarget));
        Vec3 aimDirection = predictedPos.subtract(spawnPos).normalize();

        // Create multiple lightning bolts in a cone around the AIM DIRECTION
        for (int i = 0; i < 3; i++) {
            Vec3 spreadDir = aimDirection.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 0.2, // Reduced spread
                    (getUser().getRandom().nextDouble() - 0.5) * 0.15,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.2
            ).normalize();

            // Create lightning ball projectile
            com.leon.lightningdragon.entity.projectile.LightningBallEntity lightningBall =
                    new com.leon.lightningdragon.entity.projectile.LightningBallEntity(
                            com.leon.lightningdragon.registry.ModEntities.LIGHTNING_BALL.get(), getUser().level(), getUser());

            lightningBall.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

            // Adjust for gravity and distance like the old system
            double distance = spawnPos.distanceTo(predictedPos);
            double speed = Math.min(1.2, Math.max(0.8, distance / 25.0));
            double gravityCompensation = distance * com.leon.lightningdragon.entity.projectile.LightningBallEntity.GRAVITY * 0.5;

            lightningBall.shoot(
                    spreadDir.x * speed,
                    spreadDir.y * speed + gravityCompensation,
                    spreadDir.z * speed,
                    1.0f,
                    0.1f
            );

            getUser().level().addFreshEntity(lightningBall);
        }

        // Direct beam damage still uses look direction (for close range)
        Vec3 lookDir = getUser().getLookAngle();
        List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                getUser().getBoundingBox().expandTowards(lookDir.scale(8)).inflate(2.0),
                entity -> entity != getUser() && entity != getUser().getOwner() &&
                        getUser().getSensing().hasLineOfSight(entity));

        for (LivingEntity entity : nearbyEntities) {
            if (entity.hurt(getUser().damageSources().indirectMagic(getUser(), getUser()), 4.0f)) {
                // Add electric effect
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
            }
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
        if (getUser().isFlying()) {
            getUser().setHovering(false);
        }
    }
}

/**
 * Thunder Stomp Ability - Ground AOE attack
 */
class ThunderStompAbility extends Ability<LightningDragonEntity> {
    private static final int TOTAL_TIME = 30;

    public ThunderStompAbility(AbilityType<LightningDragonEntity, ThunderStompAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.THUNDER_STOMP);
    }

    @Override
    public boolean tryAbility() {
        return !getUser().isFlying() && getUser().onGround();
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() == 15) { // Stomp impact
            thunderStomp();
        }

        if (getTicksInUse() >= TOTAL_TIME) {
            this.isUsing = false;
        }
    }

    private void thunderStomp() {
        if (getUser().level().isClientSide) return;

        // Play thunder sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 0.8f);

        // Damage nearby entities
        List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                getUser().getBoundingBox().inflate(8.0),
                entity -> entity != getUser() && entity != getUser().getOwner());

        for (LivingEntity entity : nearbyEntities) {
            double distance = getUser().distanceTo(entity);
            float damage = (float) (8.0 - distance); // Damage falloff

            if (damage > 0 && entity.hurt(getUser().damageSources().mobAttack(getUser()), damage)) {
                // Knockback
                Vec3 knockback = entity.position().subtract(getUser().position()).normalize().scale(1.5);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, 0.5, knockback.z));

                // Electric effect
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
            }
        }

        // Create electric shockwave particles
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20;
            double radius = 6.0;
            double x = getUser().getX() + Math.cos(angle) * radius;
            double z = getUser().getZ() + Math.sin(angle) * radius;

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x, getUser().getY() + 0.1, z, 0, 0.2, 0);
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
    }
}

/**
 * Wing Lightning Ability - Close range electric wing attack
 */
class WingLightningAbility extends Ability<LightningDragonEntity> {
    private static final int TOTAL_TIME = 25;

    public WingLightningAbility(AbilityType<LightningDragonEntity, WingLightningAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.WING_LIGHTNING);
    }

    @Override
    public boolean tryAbility() {
        return getUser().isFlying() && getUser().getTarget() != null &&
                getUser().distanceToSqr(getUser().getTarget()) <= 36; // 6 block range
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);
        getUser().setHovering(true);
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() == 12) { // Wing strike
            wingStrike();
        }

        if (getTicksInUse() >= TOTAL_TIME) {
            this.isUsing = false;
        }
    }

    private void wingStrike() {
        if (getUser().level().isClientSide) return;

        // Play wing flap sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.5f, 1.5f);

        // Create electric field around dragon
        List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                getUser().getBoundingBox().inflate(6.0, 3.0, 6.0),
                entity -> entity != getUser() && entity != getUser().getOwner());

        for (LivingEntity entity : nearbyEntities) {
            if (entity.hurt(getUser().damageSources().mobAttack(getUser()), 6.0f)) {
                // Strong knockback
                Vec3 knockback = entity.position().subtract(getUser().position()).normalize().scale(2.0);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, 0.8, knockback.z));

                // Paralysis effect
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, false));
            }
        }

        // Wing tip lightning particles
        for (int wing = 0; wing < 2; wing++) {
            Vec3 wingTip = getUser().position().add(
                    wing == 0 ? 4.0 : -4.0, // Left/right wing
                    1.0,
                    0
            );

            for (int i = 0; i < 10; i++) {
                getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        wingTip.x + (getUser().getRandom().nextDouble() - 0.5) * 2,
                        wingTip.y + (getUser().getRandom().nextDouble() - 0.5) * 2,
                        wingTip.z + (getUser().getRandom().nextDouble() - 0.5) * 2,
                        0, 0, 0);
            }
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
        getUser().setHovering(false);
    }
}

/**
 * Electric Bite Ability - Melee attack with chain lightning
 */
class ElectricBiteAbility extends Ability<LightningDragonEntity> {
    private static final int TOTAL_TIME = 20;

    public ElectricBiteAbility(AbilityType<LightningDragonEntity, ElectricBiteAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.ELECTRIC_BITE);
    }

    @Override
    public boolean tryAbility() {
        return getUser().getTarget() != null &&
                getUser().distanceToSqr(getUser().getTarget()) <= 16; // 4 block range
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);

        // Look at target
        if (getUser().getTarget() != null) {
            getUser().getLookControl().setLookAt(getUser().getTarget(), 30f, 30f);
        }
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() == 10) { // Bite impact
            electricBite();
        }

        if (getTicksInUse() >= TOTAL_TIME) {
            this.isUsing = false;
        }
    }

    private void electricBite() {
        if (getUser().level().isClientSide) return;

        LivingEntity target = getUser().getTarget();
        if (target == null) return;

        // Play bite sound
        getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.0f, 1.5f);

        // Primary bite damage
        if (getUser().distanceToSqr(target) <= 16 &&
                target.hurt(getUser().damageSources().mobAttack(getUser()), 10.0f)) {

            // Chain lightning to nearby entities
            List<LivingEntity> chainTargets = getUser().level().getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(8.0),
                    entity -> entity != getUser() && entity != getUser().getOwner() &&
                            entity != target && entity.isAlive());

            int chains = Math.min(3, chainTargets.size());
            for (int i = 0; i < chains; i++) {
                LivingEntity chainTarget = chainTargets.get(i);

                // Chain damage (reduced)
                chainTarget.hurt(getUser().damageSources().indirectMagic(getUser(), getUser()), 5.0f);

                // Visual lightning chain
                createLightningChain(target.position().add(0, target.getBbHeight()/2, 0),
                        chainTarget.position().add(0, chainTarget.getBbHeight()/2, 0));

                // Stun effect
                chainTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
            }
        }
    }

    private void createLightningChain(Vec3 from, Vec3 to) {
        // Create visual lightning effect between two points
        Vec3 direction = to.subtract(from);
        int steps = (int) (direction.length() * 2);

        for (int i = 0; i <= steps; i++) {
            Vec3 pos = from.add(direction.scale((double) i / steps));

            // Add randomness for lightning effect
            pos = pos.add(
                    (getUser().getRandom().nextDouble() - 0.5) * 0.5,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.5,
                    (getUser().getRandom().nextDouble() - 0.5) * 0.5
            );

            getUser().level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
    }
}

/**
 * Simple Hurt Ability
 */
class HurtAbility<T extends LivingEntity> extends Ability<T> {
    private final int duration;
    private final int iframes;

    public HurtAbility(AbilityType<T, ?> abilityType, T user, RawAnimation animation, int duration, int iframes) {
        super(abilityType, user);
        setAnimation(animation);
        this.duration = duration;
        this.iframes = iframes;
    }

    @Override
    public boolean tryAbility() {
        return true;
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();

        if (getTicksInUse() >= duration) {
            this.isUsing = false;
        }
    }

    @Override
    public boolean canCancelActiveAbility() {
        return false; // Hurt cannot be cancelled
    }
}