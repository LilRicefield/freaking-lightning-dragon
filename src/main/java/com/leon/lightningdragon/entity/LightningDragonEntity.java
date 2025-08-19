/**
 * My name's Zap Van Dink. I'm a lightning dragon.
 */
package com.leon.lightningdragon.entity;

//Custom stuff
import com.leon.lightningdragon.ai.goals.*;
import com.leon.lightningdragon.ai.navigation.DragonFlightMoveHelper;
import com.leon.lightningdragon.client.animation.DragonAnimationController;
import com.leon.lightningdragon.network.MessageDragonUseAbility;
import com.leon.lightningdragon.network.NetworkHandler;
import com.leon.lightningdragon.util.DragonMathUtil;
import com.leon.lightningdragon.ai.abilities.*;
import com.leon.lightningdragon.ai.abilities.combat.*;
import com.leon.lightningdragon.ai.goals.DragonAirCombatGoal;
import com.leon.lightningdragon.ai.goals.DragonCombatFlightGoal;
import com.leon.lightningdragon.registry.ModSounds;

//Minecraft
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

//GeckoLib

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

//WHO ARE THESE SUCKAS
import org.jetbrains.annotations.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public static final AbilityType<LightningDragonEntity, HurtAbility<LightningDragonEntity>> HURT_ABILITY =
            new AbilityType<>("dragon_hurt", (type, entity) -> new HurtAbility<>(type, entity,
                    RawAnimation.begin().thenPlay("hurt"), 16, 0));

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
    private static final EntityDataAccessor<Boolean> HAS_LIGHTNING_TARGET =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> LIGHTNING_TARGET_X =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LIGHTNING_TARGET_Y =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LIGHTNING_TARGET_Z =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.FLOAT);


    // ===== STATE VARIABLES =====
    public int timeFlying = 0;
    public Vec3 lastFlightTargetPos;
    public boolean landingFlag = false;

    private int landingTimer = 0;
    private int runningTicks = 0;
    private float prevSitProgress = 0f;
    private float sitProgress = 0f;
    private boolean hasRunningAttributes = false;

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

    // Sitting, I guess
    public float maxSitTicks() {
        return 15.0F; // Takes 15 ticks to fully sit (about 0.75 seconds)
    }
    public float getSitProgress(float partialTicks) {
        return (prevSitProgress + (sitProgress - prevSitProgress) * partialTicks) / maxSitTicks();
    }

    @Override
    public boolean isInSittingPose() {
        // Enhanced sitting pose detection like DinosaurEntity
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

    }

    public void setHasLightningTarget(boolean lightning_target) {
        this.entityData.set(HAS_LIGHTNING_TARGET, lightning_target);
    }

    public boolean hasLightningTarget() {
        return this.entityData.get(HAS_LIGHTNING_TARGET).booleanValue();
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

    // ===== ABILITY SYSTEM METHODS =====
    public boolean tryUseRangedAbility() {
        LivingEntity target = getTarget();
        if (target == null || !canUseAbility()) return false;

        double distance = distanceTo(target);

        // Priority order based on distance - more decisive than probability
        if (distance >= 20) {
            // Long range - try beam first, then breath as fallback
            Ability<LightningDragonEntity> beamAbility = LIGHTNING_BEAM_ABILITY.createAbility(this);
            if (beamAbility.tryAbility()) {
                sendAbilityMessage(LIGHTNING_BEAM_ABILITY);
                return true;
            }

            Ability<LightningDragonEntity> breathAbility = LIGHTNING_BREATH_ABILITY.createAbility(this);
            if (breathAbility.tryAbility()) {
                sendAbilityMessage(LIGHTNING_BREATH_ABILITY);
                return true;
            }
        } else {
            // Close range - try breath first, then beam as fallback
            Ability<LightningDragonEntity> breathAbility = LIGHTNING_BREATH_ABILITY.createAbility(this);
            if (breathAbility.tryAbility()) {
                sendAbilityMessage(LIGHTNING_BREATH_ABILITY);
                return true;
            }

            Ability<LightningDragonEntity> beamAbility = LIGHTNING_BEAM_ABILITY.createAbility(this);
            if (beamAbility.tryAbility()) {
                sendAbilityMessage(LIGHTNING_BEAM_ABILITY);
                return true;
            }
        }

        return false;
    }


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

    // In LightningDragonEntity.java
    public void sendAbilityMessage(AbilityType<LightningDragonEntity, ?> abilityType) {
        if (canUseAbility()) {
            @SuppressWarnings("unchecked")
            AbilityType<LightningDragonEntity, Ability<LightningDragonEntity>> castType =
                    (AbilityType<LightningDragonEntity, Ability<LightningDragonEntity>>) abilityType;

            Ability<LightningDragonEntity> newAbility = castType.createAbility(this);

            if (newAbility.tryAbility()) {
                setActiveAbility(newAbility);
                abilityCooldown = ABILITY_COOLDOWN_TICKS;

                // NEW: Send packet to clients
                if (!level().isClientSide) {
                    NetworkHandler.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this),
                            new MessageDragonUseAbility(getId(), abilityType.getName())
                    );
                }
            }
        }
    }
    // For general head position (eye beams, head tracking, etc.)
    public Vec3 getHeadPosition() {
        float scale = 4.5f;
        float bodyYaw = (float) Math.toRadians(this.yBodyRot);

        // head bone pivot
        double headForward = (-13.35 / 16.0) * scale;
        double headUp = (6.75 / 16.0) * scale;

        double offsetX = Math.sin(bodyYaw) * headForward;
        double offsetZ = Math.cos(bodyYaw) * headForward;

        return this.position().add(offsetX, headUp, offsetZ);
    }

    // For breath attacks specifically
    public Vec3 getMouthPosition() {
        float scale = 4.5f;
        float bodyYaw = (float) Math.toRadians(this.yBodyRot);

        // mouth_origin bone pivot
        double mouthForward = (-15.0 / 16.0) * scale;
        double mouthUp = (6.25 / 16.0) * scale;

        double offsetX = Math.sin(bodyYaw) * mouthForward;
        double offsetZ = Math.cos(bodyYaw) * mouthForward;

        return this.position().add(offsetX, mouthUp, offsetZ);
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

    // ===== MAIN TICK METHOD =====
    @Override
    public void tick() {
        animationController.tick();
        super.tick();
        handleFlightLogic();
        if (isRunning() && !hasRunningAttributes) {
            hasRunningAttributes = true;
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.45D); // Fast running
        }
        if (!isRunning() && hasRunningAttributes) {
            hasRunningAttributes = false;
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25D); // Normal walking
        }
        if (isInSittingPose() && sitProgress < maxSitTicks()) {
            sitProgress++;
        }
        if (!isInSittingPose() && sitProgress > 0F) {
            sitProgress--;
        }
        if (!level().isClientSide && (!isFlying() || isLanding())) {
            updateBankingReset();
        }
        if (!level().isClientSide) {
            handleAmbientSounds();
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
    }
    /**
     * Plays appropriate ambient sound based on dragon's current mood and state
     */
    private void playCustomAmbientSound() {
        RandomSource random = getRandom();

        // Don't make ambient sounds if we're in combat or using abilities
        if (isAggressive() || (activeAbility != null && activeAbility.isUsing())) {
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

        // Enhanced gliding physics that responds to animation state
        vec3 = applyGlidingPhysics(vec3, moveDirection, pitchRad);

        // Dynamic friction based on flight state
        float horizontalFriction = 0.99F;
        float verticalFriction = 0.98F;
        this.setDeltaMovement(vec3.multiply(horizontalFriction, verticalFriction, horizontalFriction));
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    private Vec3 applyGlidingPhysics(Vec3 currentVel, Vec3 moveDirection, float pitchRad) {
        double horizontalSpeed = Math.sqrt(moveDirection.x * moveDirection.x + moveDirection.z * moveDirection.z);
        // Early exit if not moving
        if (horizontalSpeed < 0.001) {
            return currentVel;
        }

        double currentHorizontalSpeed = Math.sqrt(currentVel.horizontalDistanceSqr());
        double lookDirectionLength = moveDirection.length();

        float pitchFactor = Mth.cos(pitchRad);
        pitchFactor = (float) ((double) pitchFactor * (double) pitchFactor * Math.min(1.0D, lookDirectionLength / 0.4D));

        // Enhanced gravity system that responds to flight animation state
        double baseGravity = 0.08D;
        double gravity = baseGravity;

        if (getFlappingFraction() > 0.2f) {
            gravity *= (1.0 - getFlappingFraction() * 0.5); // Up to 50% gravity reduction
        } else if (getHoveringFraction() > 0.4f) {
            gravity *= (1.0 - getHoveringFraction() * 0.3); // Up to 30% gravity reduction
        }

// Gliding efficiency bonus
        if (getGlidingFraction() > 0.5f) {
            gravity *= (1.0 - getGlidingFraction() * 0.2); // Efficient gliding reduces sink rate
        }

        Vec3 result = currentVel.add(0.0D, gravity * (-1.0D + (double) pitchFactor * 0.75D), 0.0D);

        // Enhanced lift calculation with animation influence
        if (result.y < 0.0D && horizontalSpeed > 0.0D) {
            double baseLiftFactor = result.y * -0.1D * (double) pitchFactor;

            // FIX: Restore lift enhancement based on wing state
            double liftMultiplier = 1.0;
            if (getFlappingFraction() > 0.3f) {
                liftMultiplier += getFlappingFraction() * 0.6; // Active flapping boosts lift
            }
            if (getGlidingFraction() > 0.5f) {
                liftMultiplier += getGlidingFraction() * 0.4; // Efficient gliding improves lift/drag
            }

            double liftFactor = baseLiftFactor * liftMultiplier;

            result = result.add(
                    moveDirection.x * liftFactor / horizontalSpeed,
                    liftFactor,
                    moveDirection.z * liftFactor / horizontalSpeed
            );
        }

        // Dive calculation (same as before but with animation influence)
        if (pitchRad < 0.0F && horizontalSpeed > 0.0D) {
            double diveFactor = currentHorizontalSpeed * (double) (-Mth.sin(pitchRad)) * 0.04D;
            result = result.add(
                    -moveDirection.x * diveFactor / horizontalSpeed,
                    diveFactor * 3.2D,
                    -moveDirection.z * diveFactor / horizontalSpeed
            );
        }

        // Directional alignment (enhanced)
        if (horizontalSpeed > 0.0D) {
            double alignmentFactor = 0.1D;

            // Better alignment when gliding efficiently

            result = result.add(
                    (moveDirection.x / horizontalSpeed * currentHorizontalSpeed - result.x) * alignmentFactor,
                    0.0D,
                    (moveDirection.z / horizontalSpeed * currentHorizontalSpeed - result.z) * alignmentFactor
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
    public class DragonPanicGoal extends Goal {
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
            // Force landing if flying when ordered to sit
            if (isFlying()) {
                this.setLanding(true);
            }
            this.setRunning(false);
            this.getNavigation().stop();

            // Debug output
            if (!level().isClientSide) {
                System.out.println("Dragon ordered to sit - current pose: " + isInSittingPose() + ", progress: " + getSitProgress(1.0f));
            }
        } else {
            if (!level().isClientSide) {
                System.out.println("Dragon ordered to stand - current pose: " + isInSittingPose() + ", progress: " + getSitProgress(1.0f));
            }
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
        tag.putFloat("SitProgress", sitProgress);
        animationController.writeToNBT(tag);
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
        this.sitProgress = tag.getFloat("SitProgress");
        this.prevSitProgress = this.sitProgress;
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
        // Create your movement controller
        AnimationController<LightningDragonEntity> movementController =
                new AnimationController<>(this, "movement", 8, animationController::handleMovementAnimation);

        // THE MAGIC LINE - overrides all the linear bullshit in your JSON files
        movementController.setOverrideEasingType(EasingType.EASE_IN_OUT_SINE);

        // Add sound keyframe handler (keep your existing sound code)
        movementController.setSoundKeyframeHandler(state -> {
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
            } else if (sound.equals("dragon_step")) { // 🔧 ADD THIS
                this.playSound(ModSounds.DRAGON_STEP.get(), 0.9f, 0.9f + getRandom().nextFloat() * 0.2f);
            }
        });
        controllers.add(movementController);
    }
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
        return getCachedValue("nearbyProjectiles", 3, () -> {
            AABB scanBox = getBoundingBox().inflate(30.0, 15.0, 30.0);
            return level().getEntitiesOfClass(Projectile.class, scanBox);
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
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level, AgeableMob otherParent) {
        return null;
    }
}