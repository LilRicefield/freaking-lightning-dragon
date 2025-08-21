/**
 * My name's Zap Van Dink. I'm a lightning dragon.
 */
package com.leon.lightningdragon.entity;

//Custom stuff
import com.leon.lightningdragon.ai.goals.*;
import com.leon.lightningdragon.ai.navigation.DragonFlightMoveHelper;
import com.leon.lightningdragon.client.animation.DragonAnimationController;
import com.leon.lightningdragon.entity.controller.*;
import com.leon.lightningdragon.util.DragonMathUtil;
import com.leon.lightningdragon.ai.abilities.*;
import com.leon.lightningdragon.ai.abilities.combat.*;
import com.leon.lightningdragon.ai.goals.DragonAirCombatGoal;
import com.leon.lightningdragon.ai.goals.DragonCombatFlightGoal;
import com.leon.lightningdragon.registry.ModSounds;

//Minecraft
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

//GeckoLib

import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;

//WHO ARE THESE SUCKAS
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

import java.util.*;
import java.util.function.Supplier;

//Just everything
public class LightningDragonEntity extends TamableAnimal implements GeoEntity, FlyingAnimal, RangedAttackMob {
    // Cache wrapper class
    private static class CachedValue<T> {
        private T value;
        private long lastUpdate;
        private final int ttlTicks;

        public CachedValue(int ttlTicks) {
            this.ttlTicks = ttlTicks;
        }

        public boolean isExpired(long currentTick) {
            return currentTick - lastUpdate >= ttlTicks;
        }

        public void set(T value, long currentTick) {
            this.value = value;
            this.lastUpdate = currentTick;
        }
        // Cache system
        public T get() { return value; }
    }
    private final Map<String, CachedValue<?>> cache = new HashMap<>();
    // ===== AMBIENT SOUND SYSTEM =====
    private int ambientSoundTimer = 0;
    private int nextAmbientSoundDelay = 0;

    // Sound frequency constants (in ticks)
    private static final int MIN_AMBIENT_DELAY = 200;  // 10 seconds
    private static final int MAX_AMBIENT_DELAY = 600;  // 30 seconds

    // ===== CORE ANIMATIONS =====
    public static final RawAnimation GROUND_IDLE = RawAnimation.begin().thenLoop("animation.lightning_dragon.ground_idle");
    public static final RawAnimation GROUND_WALK = RawAnimation.begin().thenLoop("animation.lightning_dragon.walk");
    public static final RawAnimation GROUND_RUN = RawAnimation.begin().thenLoop("animation.lightning_dragon.run");
    public static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.lightning_dragon.sit");

    public static final RawAnimation TAKEOFF = RawAnimation.begin().thenPlay("animation.lightning_dragon.takeoff");
    public static final RawAnimation FLY_GLIDE = RawAnimation.begin().thenLoop("animation.lightning_dragon.fly_gliding");
    public static final RawAnimation FLY_FORWARD = RawAnimation.begin().thenLoop("animation.lightning_dragon.fly_forward");
    public static final RawAnimation LANDING = RawAnimation.begin().thenPlay("animation.lightning_dragon.landing");

    public static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.lightning_dragon.hurt");
    public static final RawAnimation DODGE = RawAnimation.begin().thenPlay("animation.lightning_dragon.dodge");
    public static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.lightning_dragon.roar");

    //Controller animations
    public static final RawAnimation WALK_SWITCH = RawAnimation.begin().thenLoop("animation.lightning_dragon.walk_switch");
    public static final RawAnimation RUN_SWITCH = RawAnimation.begin().thenLoop("animation.lightning_dragon.run_switch");
    public static final RawAnimation GLIDE_SWITCH = RawAnimation.begin().thenLoop("animation.lightning_dragon.glide_switch");
    public static final RawAnimation FLAP_SWITCH = RawAnimation.begin().thenLoop("animation.lightning_dragon.flap_switch");

    // Attack animations - these will be defined in the ability classes
    public static final RawAnimation LIGHTNING_BREATH = RawAnimation.begin().thenPlay("animation.lightning_dragon.lightning_breath");
    public static final RawAnimation THUNDER_STOMP = RawAnimation.begin().thenPlay("animation.lightning_dragon.thunder_stomp");
    public static final RawAnimation WING_LIGHTNING = RawAnimation.begin().thenPlay("animation.lightning_dragon.wing_lightning");
    public static final RawAnimation ELECTRIC_BITE = RawAnimation.begin().thenPlay("animation.lightning_dragon.electric_bite");

    // ===== ABILITY SYSTEM =====
    public static final AbilityType<LightningDragonEntity, EnhancedLightningBeamAbility> LIGHTNING_BEAM_ABILITY =
            new AbilityType<>("lightning_beam", EnhancedLightningBeamAbility::new);
    public static final AbilityType<LightningDragonEntity, LightningBreathAbility> LIGHTNING_BREATH_ABILITY =
            new AbilityType<>("lightning_breath", LightningBreathAbility::new);
    public static final AbilityType<LightningDragonEntity, ThunderStompAbility> THUNDER_STOMP_ABILITY =
            new AbilityType<>("thunder_stomp", ThunderStompAbility::new);
    public static final AbilityType<LightningDragonEntity, WingLightningAbility> WING_LIGHTNING_ABILITY =
            new AbilityType<>("wing_lightning", WingLightningAbility::new);
    public static final AbilityType<LightningDragonEntity, ElectricBiteAbility> ELECTRIC_BITE_ABILITY =
            new AbilityType<>("electric_bite", ElectricBiteAbility::new);
    public static final AbilityType<LightningDragonEntity, LightningBurstAbility> LIGHTNING_BURST_ABILITY =
            new AbilityType<>("lightning_burst", LightningBurstAbility::new);
    public static final AbilityType<LightningDragonEntity, HurtAbility<LightningDragonEntity>> HURT_ABILITY =
            new AbilityType<>("dragon_hurt", (type, entity) -> new HurtAbility<>(type, entity,
                    HURT, 16, 0));

    // ===== CONSTANTS =====
    private static final double TAKEOFF_UPWARD_FORCE = 0.075D;
    private static final double LANDING_DOWNWARD_FORCE = 0.4D;
    private static final double FALLING_RESISTANCE = 0.6D;
    private static final int TAKEOFF_TIME_THRESHOLD = 30;
    private static final int LANDING_TIME_THRESHOLD = 40;
    public static final float MODEL_SCALE = 4.5f;


    // Speed constants
    private static final double WALK_SPEED = 0.25D;
    private static final double RUN_SPEED = 0.45D;

    // Combat constants
    private static final int ABILITY_COOLDOWN_TICKS = 60; // 3 seconds between abilities
    private static final int ULTIMATE_COOLDOWN_TICKS = 1200; // 60 seconds for ultimate abilities

    // ===== DATA ACCESSORS (Package-private for controller access) =====
    static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_TAKEOFF =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_HOVERING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Float> DATA_BANKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> DATA_PREV_BANKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> DATA_LANDING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_RUNNING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_ATTACKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> HAS_LIGHTNING_TARGET =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Float> LIGHTNING_TARGET_X =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> LIGHTNING_TARGET_Y =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> LIGHTNING_TARGET_Z =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> LIGHTNING_STREAM_PROGRESS =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> LIGHTNING_STREAM_ACTIVE =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);

    // Sitting progress for animation only - logic handled by TamableAnimal
    public static final EntityDataAccessor<Float> DATA_SIT_PROGRESS =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);


    // ===== STATE VARIABLES (Package-private for controller access) =====
    public int timeFlying = 0;
    public Vec3 lastFlightTargetPos = Vec3.ZERO; // For flight path smoothing
    public boolean landingFlag = false;

    public int landingTimer = 0;
    int runningTicks = 0;
    float prevSitProgress = 0f;
    public float sitProgress = 0f;
    public boolean hasRunningAttributes = false;
    
    // Banking tracking like EntityNaga
    private float prevRotationYaw = 0f;

    // Dodge system
    boolean dodging = false;
    int dodgeTicksLeft = 0;
    Vec3 dodgeVec = Vec3.ZERO;

    // Navigation (Package-private for controller access)
    public final GroundPathNavigation groundNav;
    public final FlyingPathNavigation airNav;
    public boolean usingAirNav = false;

    // ===== CONTROLLER INSTANCES =====
    public final DragonFlightController flightController;
    public final DragonCombatManager combatManager;
    public final DragonInteractionHandler interactionHandler;
    public final DragonStateManager stateManager;

    // ===== CUSTOM SITTING SYSTEM =====
    // Completely replace TamableAnimal's broken sitting behavior

    public float maxSitTicks() {
        return 15.0F; // Takes 15 ticks to fully sit (about 0.75 seconds)
    }

    public float getSitProgress(float partialTicks) {
        // Use synchronized data on client side, local data on server side
        float currentProgress = level().isClientSide ? this.entityData.get(DATA_SIT_PROGRESS) : sitProgress;
        float previousProgress = level().isClientSide ? currentProgress : prevSitProgress;
        return (previousProgress + (currentProgress - previousProgress) * partialTicks) / maxSitTicks();
    }

    @Override
    public boolean isInSittingPose() {
        return super.isInSittingPose() && !(this.isVehicle() || this.isPassenger() || this.isFlying());
    }
    // GeckoLib
    private final AnimatableInstanceCache geckoCache = new SingletonAnimatableInstanceCache(this);

    //FLIGHT
    public float getGlidingFraction() {
        return animationController.glidingFraction;
    }
    public float getFlappingFraction() {
        return animationController.flappingFraction;
    }
    public float getHoveringFraction() {
        return animationController.hoveringFraction;
    }
    private final DragonAnimationController animationController = new DragonAnimationController(this);

    public LightningDragonEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.25F);

        // Initialize both navigators
        this.groundNav = new GroundPathNavigation(this, level);
        this.airNav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(@NotNull BlockPos pos) {
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

        // Initialize controllers
        this.flightController = new DragonFlightController(this);
        this.combatManager = new DragonCombatManager(this);
        this.interactionHandler = new DragonInteractionHandler(this);
        this.stateManager = new DragonStateManager(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.FOLLOW_RANGE, 80.0D)
                .add(Attributes.FLYING_SPEED, 0.60D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
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
        this.entityData.define(HAS_LIGHTNING_TARGET, false);
        this.entityData.define(LIGHTNING_TARGET_X, 0.0F);
        this.entityData.define(LIGHTNING_TARGET_Y, 0.0F);
        this.entityData.define(LIGHTNING_TARGET_Z, 0.0F);
        this.entityData.define(LIGHTNING_STREAM_PROGRESS, 0.0F);
        this.entityData.define(LIGHTNING_STREAM_ACTIVE, false);
        this.entityData.define(DATA_SIT_PROGRESS, 0.0f);
    }

    public void setHasLightningTarget(boolean lightning_target) {
        this.entityData.set(HAS_LIGHTNING_TARGET, lightning_target);
    }

    public boolean hasLightningTarget() {
        return this.entityData.get(HAS_LIGHTNING_TARGET);
    }

    public void setLightningTargetVec(float x, float y, float z) {
        this.entityData.set(LIGHTNING_TARGET_X, x);
        this.entityData.set(LIGHTNING_TARGET_Y, y);
        this.entityData.set(LIGHTNING_TARGET_Z, z);
    }

    public float getLightningTargetX() {
        return this.entityData.get(LIGHTNING_TARGET_X);
    }

    public float getLightningTargetY() {
        return this.entityData.get(LIGHTNING_TARGET_Y);
    }

    public float getLightningTargetZ() {
        return this.entityData.get(LIGHTNING_TARGET_Z);
    }

    public void setLightningStreamProgress(float progress) {
        this.entityData.set(LIGHTNING_STREAM_PROGRESS, progress);
    }

    public float getLightningStreamProgress() {
        return this.entityData.get(LIGHTNING_STREAM_PROGRESS);
    }

    public void setLightningStreamActive(boolean active) {
        this.entityData.set(LIGHTNING_STREAM_ACTIVE, active);
    }

    public boolean isLightningStreamActive() {
        return this.entityData.get(LIGHTNING_STREAM_ACTIVE);
    }

    public Vec3 getLightningTargetVec() {
        return new Vec3(getLightningTargetX(), getLightningTargetY(), getLightningTargetZ());
    }

    // Combat distance constants
    private static final double BEAM_RANGE = 30.0;      // Use beam at very long range
    private static final double BURST_RANGE = 20.0;     // Use burst at long range
    private static final double BREATH_RANGE = 8.0;     // Use breath at medium range
    private static final double MELEE_RANGE = 6.0;      // Land and use melee at close range

    // ===== ABILITY SYSTEM METHODS =====
    public boolean tryUseRangedAbility() {
        return combatManager.tryUseRangedAbility();
    }

    // Strategic ultimate ability usage
    private boolean shouldUseUltimate() {
        LivingEntity target = getTarget();
        if (target == null) return false;

        // Check if ultimate is on cooldown (Lightning Burst specifically)
        Ability<LightningDragonEntity> burstAbility = LIGHTNING_BURST_ABILITY.createAbility(this);
        if (!burstAbility.tryAbility()) return false; // Still on cooldown or can't be used

        // Only use ultimate for tough targets with high health
        float targetHealthPercent = target.getHealth() / target.getMaxHealth();

        // Use ultimate if:
        // - Target has >75% health (fresh fight)
        // - Target has >40 max health (strong enemy)
        // - Combat has been going on for a while (frustrated dragon)
        return targetHealthPercent > 0.75f ||
                target.getMaxHealth() > 40.0f ||
                (tickCount % 600 == 0); // Every 30 seconds as desperation move
    }

    // Landing safety and combat landing methods
    private boolean canLandSafely() {
        if (!isFlying()) return true; // Already on ground

        // Check if there's solid ground within reasonable distance below
        BlockPos currentPos = blockPosition();
        for (int y = currentPos.getY() - 1; y >= currentPos.getY() - 10; y--) {
            BlockPos checkPos = new BlockPos(currentPos.getX(), y, currentPos.getZ());
            if (!level().getBlockState(checkPos).isAir() &&
                    level().getBlockState(checkPos.above()).getCollisionShape(level(), checkPos.above()).isEmpty()) {
                // Found solid ground with space above
                return true;
            }
        }
        return false; // No safe landing spot found
    }

    private void initiateAggressiveLanding() {
        if (!isFlying()) return;

        // Force landing for combat
        setLanding(true);
        setRunning(true); // Make approach more aggressive

        // Stop current navigation and hover briefly before descent
        getNavigation().stop();
        setHovering(true);

        // Brief delay before full descent
        landingTimer = 0;
    }

    public Ability<LightningDragonEntity> getActiveAbility() {
        return combatManager.getActiveAbility();
    }

    public AbilityType<LightningDragonEntity, ?> getActiveAbilityType() {
        return combatManager.getActiveAbilityType();
    }

    public void setActiveAbility(Ability<LightningDragonEntity> ability) {
        combatManager.setActiveAbility(ability);
    }

    public boolean canUseAbility() {
        return combatManager.canUseAbility();
    }

    // In LightningDragonEntity.java
    public void sendAbilityMessage(AbilityType<LightningDragonEntity, ?> abilityType) {
        combatManager.sendAbilityMessage(abilityType);
    }
    // For general head position (eye beams, head tracking, etc.)
    // Uses actual head bone coordinates from lightning_dragon.geo.json
    public Vec3 getHeadPosition() {
        float scale = MODEL_SCALE; // Use the constant instead of magic number
        float bodyYaw = (float) Math.toRadians(this.yBodyRot);
        float headPitch = (float) Math.toRadians(this.getXRot());

        // Head bone pivot from geo file: [0, 6.75, -13.35] with rotation [2.5, 0, 0]
        double headForward = (-13.35 / 16.0) * scale;
        double headUp = (6.75 / 16.0) * scale;

        // Apply head rotation (2.5 degrees base pitch from geo file)
        double basePitch = Math.toRadians(2.5);
        double totalPitch = basePitch + headPitch;

        // Calculate position with proper transformation
        double cosYaw = Math.cos(bodyYaw);
        double sinYaw = Math.sin(bodyYaw);
        double cosPitch = Math.cos(totalPitch);
        double sinPitch = Math.sin(totalPitch);

        // Transform head bone position considering both yaw and pitch
        double offsetX = sinYaw * headForward * cosPitch;
        double offsetY = headUp + headForward * sinPitch;
        double offsetZ = cosYaw * headForward * cosPitch;

        return this.position().add(offsetX, offsetY, offsetZ);
    }

    // For breath attacks specifically
    // Uses mouth_origin bone coordinates from lightning_dragon.geo.json
    public Vec3 getMouthPosition() {
        float scale = MODEL_SCALE; // Use the constant
        float bodyYaw = (float) Math.toRadians(this.yBodyRot);
        float headPitch = (float) Math.toRadians(this.getXRot());

        // mouth_origin bone pivot from geo file: [0, 6.5, -14.35] (child of head)
        // Head bone base pitch: 2.5 degrees
        double mouthForward = (-14.35 / 16.0) * scale;
        double mouthUp = (6.5 / 16.0) * scale;

        // Apply head rotation
        double basePitch = Math.toRadians(2.5);
        double totalPitch = basePitch + headPitch;

        double cosYaw = Math.cos(bodyYaw);
        double sinYaw = Math.sin(bodyYaw);
        double cosPitch = Math.cos(totalPitch);
        double sinPitch = Math.sin(totalPitch);

        // Transform mouth position considering both yaw and pitch
        double offsetX = sinYaw * mouthForward * cosPitch;
        double offsetY = mouthUp + mouthForward * sinPitch;
        double offsetZ = cosYaw * mouthForward * cosPitch;

        return this.position().add(offsetX, offsetY, offsetZ);
    }

    public void forceEndActiveAbility() {
        combatManager.forceEndActiveAbility();
    }

    // ===== NAVIGATION SWITCHING =====
    public void switchToAirNavigation() {
        if (!this.usingAirNav) {
            this.navigation = this.airNav;
            this.moveControl = new DragonFlightMoveHelper(this);
            this.usingAirNav = true;
        }
    }

    public void switchToGroundNavigation() {
        if (this.usingAirNav) {
            this.navigation = this.groundNav;
            this.moveControl = new MoveControl(this);
            this.usingAirNav = false;
        }
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
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
            Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(RUN_SPEED);
        } else {
            Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(WALK_SPEED);
        }
    }

    public boolean isWalking() {
        double speed = getDeltaMovement().horizontalDistanceSqr();
        return speed > 0.005 && speed <= 0.08 && !isRunning() && !isFlying();
    }

    public boolean isActuallyRunning() {
        return isRunning() && !isFlying();
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

    private void validateCurrentTarget() {
        LivingEntity currentTarget = getTarget();
        if (currentTarget instanceof Player player) {
            if (player.isCreative() || (isTame() && isOwnedBy(player))) {
                setTarget(null);
                forceEndActiveAbility();
            }
        }
    }

    // ===== MAIN TICK METHOD =====
    @Override
    public void tick() {
        animationController.tick();
        super.tick();

        // Delegate to controllers
        combatManager.validateCurrentTarget();
        flightController.handleFlightLogic();
        combatManager.tick();
        stateManager.updateRunningAttributes();
        interactionHandler.updateSittingProgress();

        // Update banking based on rotation changes (EntityNaga approach)
        if (!level().isClientSide && stateManager.isFlying()) {
            stateManager.updateBankingFromRotation(getYRot(), prevRotationYaw);
            prevRotationYaw = getYRot();
        } else if (!level().isClientSide && (!stateManager.isFlying() || stateManager.isLanding())) {
            updateBankingReset();
        }
        if (!level().isClientSide) {
            handleAmbientSounds();
        }

        // Handle dodge movement first
        if (!level().isClientSide && stateManager.isDodging()) {
            handleDodgeMovement();
            return;
        }

        // Track running time for animations
        if (stateManager.isRunning()) {
            runningTicks++;
        } else {
            runningTicks = Math.max(0, runningTicks - 2);
        }

        // Update client-side sit progress from synchronized data
        if (level().isClientSide) {
            prevSitProgress = sitProgress;
            sitProgress = this.entityData.get(DATA_SIT_PROGRESS);
        }

        stateManager.autoStopRunning();
    }
    /**
     * Plays appropriate ambient sound based on dragon's current mood and state
     */
    private void playCustomAmbientSound() {
        RandomSource random = getRandom();

        // Don't make ambient sounds if we're in combat or using abilities
        if (isAggressive() || (getActiveAbility() != null && getActiveAbility().isUsing())) {
            return;
        }
        SoundEvent soundToPlay = null;
        float volume = 0.8f;
        float pitch = 1.0f + (random.nextFloat() - 0.5f) * 0.2f; // Slight pitch variation

        // Choose sound based on current state and mood
        if (isOrderedToSit()) {
            // Content sitting sounds
            if (random.nextFloat() < 0.6f) {
                soundToPlay = ModSounds.DRAGON_CONTENT.get();
            } else {
                soundToPlay = ModSounds.DRAGON_PURR.get();
                volume = 0.6f; // Quieter purring
            }
        } else if (isFlying()) {
            // Occasional aerial sounds
            if (random.nextFloat() < 0.3f) {
                soundToPlay = ModSounds.DRAGON_CHUFF.get();
                volume = 1.2f; // Louder in the air
            }
        } else if (isWalking() || isRunning()) {
            // Focused movement sounds
            soundToPlay = ModSounds.DRAGON_SNORT.get();
        } else {
            // Regular idle grumbling
            float grumbleChance = random.nextFloat();
            if (grumbleChance < 0.4f) {
                soundToPlay = ModSounds.DRAGON_GRUMBLE_1.get();
            } else if (grumbleChance < 0.7f) {
                soundToPlay = ModSounds.DRAGON_GRUMBLE_2.get();
            } else if (grumbleChance < 0.9f) {
                soundToPlay = ModSounds.DRAGON_GRUMBLE_3.get();
            } else {
                soundToPlay = ModSounds.DRAGON_PURR.get();
                volume = 0.5f;
            }
        }
        // Play the sound if we chose one
        if (soundToPlay != null) {
            this.playSound(soundToPlay, volume, pitch); // Use entity's own playSound method
        }
    }
    /**
     * Handles all the ambient grumbling and personality sounds
     * Because a silent dragon is a boring dragon
     */
    private void handleAmbientSounds() {
        ambientSoundTimer++;

        // Time to make some noise?
        if (ambientSoundTimer >= nextAmbientSoundDelay) {
            playCustomAmbientSound(); // Renamed to avoid conflict with Mob.playAmbientSound()
            resetAmbientSoundTimer();
        }
    }
    /**
     * Resets the ambient sound timer with some randomness
     */
    private void resetAmbientSoundTimer() {
        RandomSource random = getRandom();
        ambientSoundTimer = 0;
        nextAmbientSoundDelay = MIN_AMBIENT_DELAY + random.nextInt(MAX_AMBIENT_DELAY - MIN_AMBIENT_DELAY);
    }
    /**
     * Call this method when dragon gets excited/happy (like when player approaches)
     */
    public void playExcitedSound() {
        if (!level().isClientSide) {
            this.playSound(ModSounds.DRAGON_EXCITED.get(), 1.0f, 1.0f + getRandom().nextFloat() * 0.3f);
        }
    }

    /**
     * Call this when dragon gets annoyed (like when attacked by something weak)
     */
    public void playAnnoyedSound() {
        if (!level().isClientSide) {
            this.playSound(ModSounds.DRAGON_ANNOYED.get(), 1.2f, 0.8f + getRandom().nextFloat() * 0.4f);
        }
    }

    //Targeting
    // CACHE EXPENSIVE CALCULATIONS
    private Vec3 cachedLookDirection = Vec3.ZERO;
    private int lookDirectionCacheTime = 0;
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
    public void travel(@NotNull Vec3 motion) {
        if (this.isInSittingPose() || this.isDodging()) {
            if (this.getNavigation().getPath() != null) {
                this.getNavigation().stop();
            }
            motion = Vec3.ZERO;
        }
        if (dodging) {
            super.travel(motion);
            return;
        }
        if (isFlying()) {
            flightController.handleFlightTravel(motion);
        } else {
            super.travel(motion);
        }
    }

    // ===== RANGED ATTACK IMPLEMENTATION =====
    @Override
    public void performRangedAttack(@NotNull LivingEntity target, float distanceFactor) {
        // Let the combat goal handle ability selection
        // Only use this as absolute fallback if combat goal isn't working
        if (canUseAbility() && getActiveAbility() == null) {
            // Check if combat goal is running - if not, use fallback
            boolean combatGoalActive = this.goalSelector.getRunningGoals()
                    .anyMatch(goal -> goal.getGoal() instanceof DragonGroundCombatGoal);

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

    @SuppressWarnings("unused") // Forge interface requires these parameters
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
    public int getMaxHeadXRot() {
        return 60;
    }

    // ===== LOOK CONTROLLER =====
    public static class DragonLookController extends LookControl {
        private final LightningDragonEntity dragon;

        public DragonLookController(LightningDragonEntity dragon) {
            super(dragon);
            this.dragon = dragon;
        }

        @Override
        public void tick() {
            if (this.dragon.getTarget() != null) {
                // SINGLE simple look control - no isAttacking() conditions
                LivingEntity target = this.dragon.getTarget();

                double dx = target.getX() - this.dragon.getX();
                double dz = target.getZ() - this.dragon.getZ();
                double dy = target.getEyeY() - this.dragon.getEyeY();
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);

                float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                float targetPitch = (float) (-(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI)));

                this.dragon.setYRot(Mth.approachDegrees(this.dragon.getYRot(), targetYaw, 15.0F));
                this.dragon.setXRot(Mth.approachDegrees(this.dragon.getXRot(), targetPitch, 8.0F));

                this.dragon.yBodyRot = this.dragon.getYRot();
                this.dragon.yHeadRot = this.dragon.getYRot();

            } else {
                // No target - handle normal flying/ground behavior
                if (this.dragon.isFlying()) {
                    // Flying - only smooth when NOT in combat
                    this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
                } else {
                    // Ground behavior
                    super.tick();
                }
            }
        }
    }

    // ===== AI GOALS =====
    @Override
    protected void registerGoals() {
        // Basic goals
        this.goalSelector.addGoal(1, new DragonPanicGoal(this));
        this.goalSelector.addGoal(2, new DragonDodgeGoal(this));
        this.goalSelector.addGoal(3, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(4, new FloatGoal(this));

        // Combat goals - Flight trigger comes FIRST
        this.goalSelector.addGoal(5, new DragonCombatFlightGoal(this));  // NEW: Takes flight for combat
        this.goalSelector.addGoal(6, new DragonAirCombatGoal(this));     // Air combat
        this.goalSelector.addGoal(7, new DragonGroundCombatGoal(this));  // Ground combat (fallback)

        // Basic movement
        this.goalSelector.addGoal(8, new DragonFollowOwnerGoal(this));
        this.goalSelector.addGoal(9, new DragonGroundWanderGoal(this, 1.0, 60));
        this.goalSelector.addGoal(10, new DragonFlightGoal(this));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 8.0F));

        // Target selection
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, false,
                target -> !isTame() && !((Player)target).isCreative()));
    }


    // ===== PANIC GOAL =====
    public static class DragonPanicGoal extends Goal {
        private final LightningDragonEntity dragon;
        private double posX, posY, posZ;

        public DragonPanicGoal(LightningDragonEntity dragon) {
            this.dragon = dragon;
            this.setFlags(EnumSet.of(Flag.MOVE));
            dragon.setRunning(true);
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
                dragon.setRunning(true);
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
            dragon.setRunning(false);
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
    public @NotNull InteractionResult mobInteract(Player player, @NotNull InteractionHand hand) {
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
                        flightController.handleTakeoff();
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
            // Force landing if flying when ordered to sit
            if (isFlying()) {
                this.setLanding(true);
            }
            this.setRunning(false);
            this.getNavigation().stop();
        } else {
            // Reset sit progress when standing up
            if (!level().isClientSide) {
                sitProgress = 0;
                this.entityData.set(DATA_SIT_PROGRESS, 0.0f);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte eventId) {
        if (eventId == 6) {
            // Failed taming - show smoke particles ONLY, no sitting behavior at all
            if (level().isClientSide) {
                // Show smoke particles for failed taming
                for (int i = 0; i < 7; ++i) {
                    double d0 = this.random.nextGaussian() * 0.02D;
                    double d1 = this.random.nextGaussian() * 0.02D;
                    double d2 = this.random.nextGaussian() * 0.02D;
                    this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                            this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
                }
            }
            // IMPORTANT: Don't call super for event 6 - it might trigger sitting behavior
            return; // Completely skip any default TamableAnimal behavior
        } else if (eventId == 7) {
            // Successful taming - show hearts only, sitting is handled separately
            if (level().isClientSide) {
                // Show heart particles for successful taming
                for (int i = 0; i < 7; ++i) {
                    double d0 = this.random.nextGaussian() * 0.02D;
                    double d1 = this.random.nextGaussian() * 0.02D;
                    double d2 = this.random.nextGaussian() * 0.02D;
                    this.level().addParticle(net.minecraft.core.particles.ParticleTypes.HEART,
                            this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
                }
            }
            // IMPORTANT: Don't call super for event 7 either - sitting is explicitly handled in mobInteract
            return; // Completely skip any default TamableAnimal behavior
        } else {
            // Call super for all other entity events (NOT 6 or 7)
            super.handleEntityEvent(eventId);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT);
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity entityIn) {
        // Use ability system for melee attacks
        if (canUseAbility()) {
            sendAbilityMessage(ELECTRIC_BITE_ABILITY);
            return true;
        }
        return false;
    }

    // ===== SAVE/LOAD =====
    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", isFlying());
        tag.putBoolean("Takeoff", isTakeoff());
        tag.putBoolean("Hovering", isHovering());
        tag.putBoolean("Landing", isLanding());
        tag.putBoolean("Running", isRunning());
        tag.putInt("TimeFlying", timeFlying);
        tag.putFloat("Banking", getBanking());
        tag.putBoolean("UsingAirNav", usingAirNav);
        tag.putInt("AbilityCooldown", combatManager.getAbilityCooldown());
        tag.putFloat("SitProgress", sitProgress);
        tag.putFloat("LightningStreamProgress", getLightningStreamProgress());
        tag.putBoolean("LightningStreamActive", isLightningStreamActive());
        animationController.writeToNBT(tag);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setFlying(tag.getBoolean("Flying"));
        this.setTakeoff(tag.getBoolean("Takeoff"));
        this.setHovering(tag.getBoolean("Hovering"));
        this.setLanding(tag.getBoolean("Landing"));
        this.setRunning(tag.getBoolean("Running"));
        this.timeFlying = tag.getInt("TimeFlying");
        this.setBanking(tag.getFloat("Banking"));
        this.usingAirNav = tag.getBoolean("UsingAirNav");
        combatManager.setAbilityCooldown(tag.getInt("AbilityCooldown"));
        this.sitProgress = tag.getFloat("SitProgress");
        this.prevSitProgress = this.sitProgress;
        this.setLightningStreamProgress(tag.getFloat("LightningStreamProgress"));
        this.setLightningStreamActive(tag.getBoolean("LightningStreamActive"));
        animationController.readFromNBT(tag);

        if (this.usingAirNav) {
            switchToAirNavigation();
        } else {
            switchToGroundNavigation();
        }
    }


    // ===== GECKOLIB =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<LightningDragonEntity> movementController =
                new AnimationController<>(this, "movement", 8, animationController::handleMovementAnimation);
        AnimationController<LightningDragonEntity> walkRunController =
                new AnimationController<>(this, "walk_run_switch", 4, this::walkRunPredicate);
        AnimationController<LightningDragonEntity> flightBlendController =
                new AnimationController<>(this, "flight_blend", 6, this::flightBlendPredicate);
        controllers.add(flightBlendController);
        movementController.setOverrideEasingType(EasingType.EASE_IN_OUT_SINE);

        // Add sound keyframe handler (keep your existing sound code)
        movementController.setSoundKeyframeHandler(state -> {
            String sound = state.getKeyframeData().getSound();
            switch (sound) {
                case "lightning_blast" ->
                        level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                                SoundSource.HOSTILE, 1.5f, 1.0f);
                case "wing_flap" -> level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP,
                        SoundSource.HOSTILE, 0.8f, 1.2f);
                case "electric_charge" ->
                        level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                                SoundSource.HOSTILE, 1.0f, 2.0f);
                case "dragon_step" ->
                        this.playSound(ModSounds.DRAGON_STEP.get(), 0.9f, 0.9f + getRandom().nextFloat() * 0.2f);
            }
        });
        controllers.add(walkRunController);
        controllers.add(movementController);
    }

    //PREDICATES
    private PlayState walkRunPredicate(AnimationState<LightningDragonEntity> state) {
        if (isFlying()) return PlayState.STOP;
        if (isActuallyRunning()) {
            state.setAndContinue(RUN_SWITCH);
        } else {
            state.setAndContinue(WALK_SWITCH);
        }
        return PlayState.CONTINUE;
    }

    private PlayState flightBlendPredicate(AnimationState<LightningDragonEntity> state) {
        if (!isFlying()) return PlayState.STOP;

        float flappingWeight = animationController.getFlappingFraction(state.getPartialTick());

        if (flappingWeight > 0.5f) {
            state.setAndContinue(FLAP_SWITCH);  // Sets glideController to [0,0,0]
        } else {
            state.setAndContinue(GLIDE_SWITCH); // Sets glideController to [1,0,0]
        }
        return PlayState.CONTINUE;
    }
    //NO MORE PREDICATES
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geckoCache;
    }

    // Cache frequently used calculations
    public double getCachedDistanceToOwner() {
        return getCachedValue("ownerDistance", 5, () -> {
            LivingEntity owner = getOwner();
            return owner != null ? distanceToSqr(owner) : Double.MAX_VALUE;
        });
    }

    public List<Projectile> getCachedNearbyProjectiles() {
        return getCachedValue("nearbyProjectiles", 3, () ->
                DragonMathUtil.getEntitiesNearby(this, Projectile.class, 30.0));
    }

    @OnlyIn(Dist.CLIENT)
    private float cachedEyeHeight = this.getEyeHeight(Pose.STANDING); // Default to standing height

    @OnlyIn(Dist.CLIENT)
    public void setCachedEyeHeight(float height) {
        this.cachedEyeHeight = height;
    }

    // Override the getEyePosition method.
    @Override
    public float getEyeHeight(Pose pose) {
        // On the client, return our dynamically calculated eye height.
        if (this.level().isClientSide) { // Notice the change to level()
            return this.cachedEyeHeight;
        }
        // On the server, fall back to the default behavior.
        return super.getEyeHeight(pose);
    }

    // Cache head position - used by abilities and rendering
    public Vec3 getCachedHeadPosition() {
        return getCachedValue("headPosition", 2, this::getHeadPosition);
    }


    // Cache mouth position - used by breath/beam attacks
    public Vec3 getCachedMouthPosition() {
        return getCachedValue("mouthPosition", 2, this::getMouthPosition);
    }

    // Cache distance to current target - used in combat decisions
    public double getCachedDistanceToTarget() {
        return getCachedValue("targetDistance", 3, () -> {
            LivingEntity target = getTarget();
            return target != null ? distanceTo(target) : Double.MAX_VALUE;
        });
    }

    // Cache current block position - used for pathfinding/ground checks
    public BlockPos getCachedBlockPosition() {
        return getCachedValue("blockPosition", 5, this::blockPosition);
    }

    // Cache horizontal flight speed - used in physics calculations
    public double getCachedHorizontalSpeed() {
        return getCachedValue("horizontalSpeed", 1, () -> {
            Vec3 velocity = getDeltaMovement();
            return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        });
    }

    // Cache body rotation trigonometry - used in position calculations
    private Vec3 cachedBodyRotationVec = Vec3.ZERO;
    private int bodyRotationCacheTime = -1;
    public Vec3 getCachedBodyRotationVector() {
        if (tickCount != bodyRotationCacheTime) {
            float bodyYaw = (float) Math.toRadians(this.yBodyRot);
            cachedBodyRotationVec = new Vec3(Math.sin(bodyYaw), 0, Math.cos(bodyYaw));
            bodyRotationCacheTime = tickCount;
        }
        return cachedBodyRotationVec;
    }

    // Cache ground level check - expensive terrain scanning
    private Boolean cachedCanLandSafely = null;
    private int landSafetyCacheTime = -1;
    public boolean getCachedCanLandSafely() {
        if (tickCount - landSafetyCacheTime >= 10 || cachedCanLandSafely == null) {
            cachedCanLandSafely = canLandSafely();
            landSafetyCacheTime = tickCount;
        }
        return cachedCanLandSafely;
    }

    // Cache nearby hostile entities - used for threat assessment
    public List<LivingEntity> getCachedNearbyHostiles() {
        return getCachedValue("nearbyHostiles", 8, () -> {
            return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16.0),
                    entity -> entity != this && entity != getOwner() &&
                            entity.isAlive() && !isTame() ||
                            (entity instanceof Player player && !player.isCreative()));
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T getCachedValue(String key, int ttl, Supplier<T> supplier) {
        long currentTick = tickCount;
        CachedValue<T> cached = (CachedValue<T>) cache.get(key);

        if (cached == null || cached.isExpired(currentTick)) {
            if (cached == null) {
                cached = new CachedValue<>(ttl);
                cache.put(key, cached);
            }
            cached.set(supplier.get(), currentTick);
        }
        return cached.get();
    }

    private Vec3 getCachedLookDirection() {
        // Cache look direction for 2 ticks since it's used multiple times per tick
        if (tickCount != lookDirectionCacheTime) {
            cachedLookDirection = getLookAngle();
            lookDirectionCacheTime = tickCount;
        }
        return cachedLookDirection;
    }
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(net.minecraft.server.level.@NotNull ServerLevel level, @NotNull AgeableMob otherParent) {
        return null;
    }
}