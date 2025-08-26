/**
 * My name's Zap Van Dink. I'm a lightning dragon.
 */
package com.leon.lightningdragon.entity;

//Custom stuff
import com.leon.lightningdragon.ai.goals.*;
import com.leon.lightningdragon.ai.navigation.DragonFlightMoveHelper;
import com.leon.lightningdragon.client.animation.DragonAnimationController;
import com.leon.lightningdragon.entity.base.DragonEntity;
import com.leon.lightningdragon.entity.controller.*;
import com.leon.lightningdragon.entity.handler.*;
import com.leon.lightningdragon.util.DragonMathUtil;
import com.leon.lightningdragon.ai.abilities.*;
import com.leon.lightningdragon.ai.abilities.combat.*;
import com.leon.lightningdragon.ai.goals.DragonAirCombatGoal;
import com.leon.lightningdragon.ai.goals.DragonCombatFlightGoal;
import com.leon.lightningdragon.registry.ModSounds;
import com.leon.lightningdragon.util.KeybindUsingMount;

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
import software.bernie.geckolib.core.animation.*;


//WHO ARE THESE SUCKAS
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.function.Supplier;

//Just everything
public class LightningDragonEntity extends DragonEntity implements FlyingAnimal, RangedAttackMob, KeybindUsingMount {
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
    public static final RawAnimation BEAM_START = RawAnimation.begin().thenPlay("animation.lightning_dragon.beam_start");
    public static final RawAnimation LIGHTNING_BEAM = RawAnimation.begin().thenLoop("animation.lightning_dragon.lightning_beam");
    public static final RawAnimation BEAM_END = RawAnimation.begin().thenPlay("animation.lightning_dragon.beam_end");
    public static final RawAnimation LIGHTNING_BURST = RawAnimation.begin().thenPlay("animation.lightning_dragon.lightning_burst");
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

    // Command system (like Ice & Fire dragons)
    static final EntityDataAccessor<Integer> DATA_COMMAND =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.INT);

    // Riding control state accessors
    static final EntityDataAccessor<Boolean> DATA_GOING_UP =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_GOING_DOWN =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_RIDER_ATTACKING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_ACCELERATING =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Byte> DATA_CONTROL_STATE =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<String> DATA_ACTIVE_ABILITY_TYPE =
            SynchedEntityData.defineId(LightningDragonEntity.class, EntityDataSerializers.STRING);


    // ===== STATE VARIABLES (Package-private for controller access) =====
    public int timeFlying = 0;
    public Vec3 lastFlightTargetPos = Vec3.ZERO; // For flight path smoothing
    public boolean landingFlag = false;

    public int landingTimer = 0;
    int runningTicks = 0;
    float prevSitProgress = 0f;
    public float sitProgress = 0f;
    public boolean hasRunningAttributes = false;

    // Banking tracking
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

    // ===== SPECIALIZED HANDLER SYSTEMS =====
    private final DragonKeybindHandler keybindHandler;
    private final DragonLightningSystem lightningSystem;
    private final DragonRiderController riderController;

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
    // GeckoLib cache is now handled by base DragonEntity class

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

        // Initialize specialized handler systems
        this.keybindHandler = new DragonKeybindHandler(this);
        this.lightningSystem = new DragonLightningSystem(this);
        this.riderController = new DragonRiderController(this);

    }

    // ===== HANDLER ACCESS METHODS =====

    public DragonKeybindHandler getKeybindHandler() {
        return keybindHandler;
    }

    public DragonLightningSystem getLightningSystem() {
        return lightningSystem;
    }

    public DragonRiderController getRiderController() {
        return riderController;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 180.0D)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.FOLLOW_RANGE, 80.0D)
                .add(Attributes.FLYING_SPEED, 0.30D)
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
        this.entityData.define(DATA_COMMAND, 0);
        this.entityData.define(DATA_GOING_UP, false);
        this.entityData.define(DATA_GOING_DOWN, false);
        this.entityData.define(DATA_RIDER_ATTACKING, false);
        this.entityData.define(DATA_ACCELERATING, false);
        this.entityData.define(DATA_CONTROL_STATE, (byte) 0);
        this.entityData.define(DATA_ACTIVE_ABILITY_TYPE, "");
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

    // setActiveAbility is now provided by base DragonEntity class

    public boolean canUseAbility() {
        return combatManager.canUseAbility();
    }

    // In LightningDragonEntity.java
    public void sendAbilityMessage(AbilityType<LightningDragonEntity, ?> abilityType) {
        combatManager.sendAbilityMessage(abilityType);
    }

    public void useRidingAbility(String abilityName) {
        if (!isTame() || getRidingPlayer() == null) {
            return;
        }

        AbilityType<LightningDragonEntity, ?> abilityType = switch (abilityName) {
            case "lightning_burst" -> LIGHTNING_BURST_ABILITY;
            case "lightning_breath" -> LIGHTNING_BREATH_ABILITY;
            case "lightning_beam" -> LIGHTNING_BEAM_ABILITY;
            case "thunder_stomp" -> THUNDER_STOMP_ABILITY;
            case "electric_bite" -> ELECTRIC_BITE_ABILITY;
            default -> null;
        };
        if (abilityType != null) {
            sendAbilityMessage(abilityType);
        }
    }

    /**
     * Forces the dragon to take off when being ridden. Called when player presses Space while on ground.
     */
    public void requestRiderTakeoff() {
        riderController.requestRiderTakeoff();
    }

    // Simplified Ice & Fire style head position using your dragon's available properties
    @Override
    public Vec3 getHeadPosition() {
        // Basic flight adjustments using your dragon's state
        float sitProg = 0; // Add sit progress if you have it
        float flyProg = this.isFlying() ? 0.01F : 0;
        float hoverProg = this.isHovering() ? 0.03F : 0;
        float pitchY = 0;

        // Use your dragon's actual pitch for head positioning
        float dragonPitch = -this.getXRot(); // Use entity's pitch
        if (this.isFlying() || this.isHovering()) {
            if (dragonPitch > 0) {
                pitchY = (dragonPitch / 90F) * 1.2F;
            } else {
                pitchY = (dragonPitch / 90F) * 3F;
            }
        }

        float flightXz = 1.0F + flyProg + hoverProg;
        float absPitch = Math.abs(dragonPitch) / 90F; // 1 down/up, 0 straight
        float minXZ = dragonPitch > 20 ? (dragonPitch - 20) * 0.009F : 0;

        // Use your MODEL_SCALE and basic size calculations
        float renderSize = MODEL_SCALE * 0.3F; // Approximate render size
        float xzMod = (0.58F - hoverProg * 0.45F + flyProg * 0.2F + absPitch * 0.3F - sitProg) * flightXz * renderSize;
        float xzModSine = xzMod * (Math.max(0.25F, Mth.cos((float) Math.toRadians(dragonPitch))) - minXZ);

        // THE CRITICAL PART - Use Ice & Fire's coordinate system (yBodyRot + 90)
        float headPosX = (float) (getX() + (xzModSine) * Mth.cos((float) ((yBodyRot + 90) * Math.PI / 180)));
        float headPosY = (float) (getY() + (0.7F + (sitProg * 5F) + hoverProg + flyProg + pitchY) * renderSize);
        float headPosZ = (float) (getZ() + (xzModSine) * Mth.sin((float) ((yBodyRot + 90) * Math.PI / 180)));

        return new Vec3(headPosX, headPosY, headPosZ);
    }

    // Get mouth position using beam_origin locator from GeckoLib model
    @Override  
    public Vec3 getMouthPosition() {
        // Try to get the beam_origin locator position from the client-side model
        if (level().isClientSide) {
            // On client side, we can access the model directly
            Vec3 locatorPos = getLocatorPosition("beam_origin");
            if (locatorPos != null) {
                return locatorPos;
            }
        }
        
        // Fallback: Use head position with proper forward offset calculation
        Vec3 headPos = getHeadPosition();
        
        // Get the dragon's look direction (forward vector)
        Vec3 lookDirection = getLookAngle();
        
        // Move forward from head position by appropriate distance
        // The beam_origin is -15 units forward in the model, scaled by MODEL_SCALE
        double forwardOffset = (15.0 / 16.0) * MODEL_SCALE; // Convert from geo units to world units
        
        // Move forward from head position
        Vec3 mouthPos = headPos.add(lookDirection.scale(forwardOffset));
        
        // Add slight vertical offset from locator Y position (6.5 / 16.0 scaled)
        double verticalOffset = (6.5 / 16.0) * MODEL_SCALE * 0.1; // Small multiplier for subtle adjustment
        
        return mouthPos.add(0, verticalOffset, 0);
    }
    
    /**
     * Get locator position from GeckoLib model (client-side only)
     */
    private Vec3 getLocatorPosition(String locatorName) {
        // This would need to be implemented to get actual bone/locator positions
        // from the rendered model. For now, return null to use fallback.
        // You could implement this by accessing the model in the renderer.
        return null;
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

        // Reset acceleration state when transitioning between ground and flight modes
        // This prevents ground sprinting from affecting flight speed and vice versa
        if (wasFlying != flying) {
            this.setAccelerating(false);
        }

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

    /**
     * Check if dragon should use acceleration animation (run on ground, FLY_FORWARD in air)
     */
    public boolean shouldUseAccelerationAnimation() {
        return this.isVehicle() && this.isAccelerating() && this.getRidingPlayer() != null;
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

    // Riding control states
    public boolean isGoingUp() { return this.entityData.get(DATA_GOING_UP); }
    public void setGoingUp(boolean goingUp) { this.entityData.set(DATA_GOING_UP, goingUp); }

    public boolean isGoingDown() { return this.entityData.get(DATA_GOING_DOWN); }
    public void setGoingDown(boolean goingDown) { this.entityData.set(DATA_GOING_DOWN, goingDown); }

    public boolean isRiderAttacking() { return this.entityData.get(DATA_RIDER_ATTACKING); }
    public void setRiderAttacking(boolean attacking) { this.entityData.set(DATA_RIDER_ATTACKING, attacking); }

    public boolean isAccelerating() { return this.entityData.get(DATA_ACCELERATING); }
    public void setAccelerating(boolean accelerating) { this.entityData.set(DATA_ACCELERATING, accelerating); }

    // Breathing fire state for continuous lightning abilities - Ice & Fire style
    private boolean breathingFire = false;
    private int fireStopTicks = 0; // Ice & Fire style hold-to-fire mechanism
    
    // Animation timing for beam_end
    private int beamEndTimer = 0;

    public boolean isBreathingFire() { return breathingFire; }
    public void setBreathingFire(boolean breathing) {
        this.breathingFire = breathing;
        if (breathing) {
            // Start continuous lightning when breathing fire
            riderShootFire();
        }
    }

    public int getFireStopTicks() { return fireStopTicks; }
    public void setFireStopTicks(int ticks) { this.fireStopTicks = ticks; }

    // Control state system (like Ice & Fire)
    private byte controlState = 0;

    public byte getControlState() {
        return controlState;
    }

    public void setControlState(byte controlState) {
        this.controlState = controlState;  // Update main entity's control state
        keybindHandler.setControlState(controlState);  // Delegate to handler for processing
    }

    // Synchronized active ability type for client-side animation
    public String getSyncedActiveAbilityType() {
        return this.entityData.get(DATA_ACTIVE_ABILITY_TYPE);
    }

    public void setSyncedActiveAbilityType(String abilityTypeName) {
        // EntityData.set() automatically syncs to clients when called on server
        this.entityData.set(DATA_ACTIVE_ABILITY_TYPE, abilityTypeName);
        
        System.out.println("DEBUG - setSyncedActiveAbilityType: " + abilityTypeName + " (isClient: " + level().isClientSide + ")");
        
        // Start timer for beam_end animation
        if ("beam_end".equals(abilityTypeName)) {
            beamEndTimer = 40; // Give beam_end animation 2 seconds to play
        } else if (abilityTypeName.isEmpty()) {
            // Clear timer immediately when animation is manually cleared
            beamEndTimer = 0;
        }
    }

    private void setStateField(int bit, boolean value) {
        if (value) {
            controlState |= (byte) (1 << bit);
        } else {
            controlState &= (byte) ~(1 << bit);
        }
    }

    public void up(boolean up) {
        setStateField(0, up);
    }

    public void down(boolean down) {
        setStateField(1, down);
    }

    public void attack(boolean attack) {
        setStateField(2, attack);
    }

    public void strike(boolean strike) {
        setStateField(3, strike);
    }

    // Riding utilities
    @Nullable
    public Player getRidingPlayer() {
        if (this.getControllingPassenger() instanceof Player player) {
            return player;
        }
        return null;
    }

    public boolean isBeingRidden() {
        return getRidingPlayer() != null;
    }

    // Old rider input system removed - now using getRiddenInput() vanilla approach

    public double getFlightSpeedModifier() {
        return this.getAttributeValue(Attributes.FLYING_SPEED);
    }

    // ===== COMMAND SYSTEM =====
    public int getCommand() {
        return this.entityData.get(DATA_COMMAND);
    }

    public void setCommand(int command) {
        this.entityData.set(DATA_COMMAND, command);
        // Automatically handle sitting when command is 1
        this.setOrderedToSit(command == 1);
    }

    public boolean canOwnerMount(Player player) {
        return !this.isBaby();
    }

    public boolean canOwnerCommand(Player ownerPlayer) {
        return ownerPlayer.isCrouching(); // Shift key pressed
    }

    // ===== RIDING SUPPORT =====
    @Override
    public double getPassengersRidingOffset() {
        return riderController.getPassengersRidingOffset();
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction moveFunction) {
        riderController.positionRider(passenger, moveFunction);
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        return riderController.getDismountLocationForPassenger(passenger);
    }

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

        // Update banking based on rotation changes
        if (!level().isClientSide && stateManager.isFlying() && !this.isVehicle()) {
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


        // Ice & Fire style fireStopTicks mechanism for hold-to-fire
        if (!level().isClientSide) {
            updateLightningBeamFireStopLogic();
            
            // Handle beam_end animation timer
            if (beamEndTimer > 0) {
                beamEndTimer--;
                if (beamEndTimer <= 0 && "beam_end".equals(getSyncedActiveAbilityType())) {
                    // Clear the beam_end animation when timer expires
                    setSyncedActiveAbilityType("");
                    System.out.println("DEBUG - beam_end timer expired - clearing animation for reuse");
                }
            }
            
            // Extra safety - clear animation if no active ability but animation is still set
            if (getActiveAbility() == null && !getSyncedActiveAbilityType().isEmpty()) {
                // Allow some time for beam_end to play, but not indefinitely
                if (!"beam_end".equals(getSyncedActiveAbilityType()) || beamEndTimer <= 0) {
                    setSyncedActiveAbilityType("");
                    System.out.println("DEBUG - No active ability but animation still set - clearing");
                }
            }
        }
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

    // ===== TRAVEL METHOD =====
    @Override
    public void travel(@NotNull Vec3 motion) {
        // Handle sitting/dodging states first
        if (this.isInSittingPose() || this.isDodging()) {
            if (this.getNavigation().getPath() != null) {
                this.getNavigation().stop();
            }
            motion = Vec3.ZERO;
            super.travel(motion);
            return;
        }

        if (dodging) {
            super.travel(motion);
            return;
        }

        // Riding logic
        if (this.isVehicle() && this.getControllingPassenger() instanceof Player player) {
            // Clear any AI navigation when being ridden
            if (this.getNavigation().getPath() != null) {
                this.getNavigation().stop();
            }

            // Let tickRidden handle rotation and banking smoothly
            // No instant rotation here - all handled in tickRidden for responsiveness

            if (isFlying()) {
                // Flying movement - handle like Ice & Fire dragons
                this.moveRelative(this.getRiddenSpeed(player), motion);
                Vec3 delta = this.getDeltaMovement();

                // Handle vertical movement from rider input
                if (this.isGoingUp()) {
                    delta = delta.add(0, 0.15D, 0);
                } else if (this.isGoingDown()) {
                    delta = delta.add(0, -0.15D, 0);
                }

                this.move(MoverType.SELF, delta);
                // Less friction for more responsive flight
                this.setDeltaMovement(delta.scale(0.91D));
                this.calculateEntityAnimation(true);
            } else {
                // Ground movement - use vanilla system which calls getRiddenInput()
                super.travel(motion);
            }
        } else {
            // Normal AI movement
            if (isFlying()) {
                // AI flight movement
                flightController.handleFlightTravel(motion);
            } else {
                // AI ground movement
                super.travel(motion);
            }
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
            // SPECIAL CASE: During lightning beam ability with rider, use rider's look direction
            if (this.dragon.getActiveAbility() != null && 
                this.dragon.getActiveAbilityType() == LIGHTNING_BEAM_ABILITY &&
                this.dragon.getRidingPlayer() != null) {
                
                Player rider = this.dragon.getRidingPlayer();
                
                // Set dragon's head rotation to match rider's look direction
                this.dragon.yHeadRot = rider.yHeadRot;
                this.dragon.yHeadRotO = rider.yHeadRotO;
                this.dragon.setXRot(rider.getXRot());
                this.dragon.xRotO = rider.xRotO;
                
                // Keep body rotation separate - don't force it to match head
                // This allows head-only movement
                return; // Skip normal look controller logic
            }
            
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
        // Basic goals (no riding goal needed - handled by getRiddenInput)
        this.goalSelector.addGoal(2, new DragonPanicGoal(this));
        this.goalSelector.addGoal(3, new DragonDodgeGoal(this));
        this.goalSelector.addGoal(4, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(5, new FloatGoal(this));

        // Combat goals - Flight trigger comes FIRST
        this.goalSelector.addGoal(6, new DragonCombatFlightGoal(this));  // NEW: Takes flight for combat
        this.goalSelector.addGoal(7, new DragonAirCombatGoal(this));     // Air combat
        this.goalSelector.addGoal(8, new DragonGroundCombatGoal(this));  // Ground combat (fallback)

        // Basic movement
        this.goalSelector.addGoal(9, new DragonFollowOwnerGoal(this));
        this.goalSelector.addGoal(10, new DragonGroundWanderGoal(this, 1.0, 60));
        this.goalSelector.addGoal(11, new DragonFlightGoal(this));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(13, new LookAtPlayerGoal(this, Player.class, 8.0F));

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
                // Command cycling - Shift+Right-click cycles through commands
                if (canOwnerCommand(player) && itemstack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
                    int currentCommand = getCommand();
                    int nextCommand = (currentCommand + 1) % 3; // 0=Follow, 1=Sit, 2=Wander
                    setCommand(nextCommand);

                    // Send feedback message to player
                    String commandName = switch (nextCommand) {
                        case 0 -> "Follow";
                        case 1 -> "Sit";
                        case 2 -> "Wander";
                        default -> "Unknown";
                    };
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Dragon Command: " + commandName));

                    return InteractionResult.SUCCESS;
                }
                // Mounting - Right-click without shift
                else if (!player.isCrouching() && itemstack.isEmpty() && hand == InteractionHand.MAIN_HAND && canOwnerMount(player)) {
                    if (!this.isVehicle()) {
                        // Force the dragon to stand if sitting
                        if (this.isOrderedToSit()) {
                            this.setOrderedToSit(false);
                        }
                        // Start riding
                        if (player.startRiding(this)) {
                            // Play excited sound when mounting
                            this.playExcitedSound();
                            // Player can manually take off using Space key when ready
                            return InteractionResult.SUCCESS;
                        }
                    }
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
        tag.putInt("Command", getCommand());
        tag.putFloat("LightningStreamProgress", getLightningStreamProgress());
        tag.putBoolean("LightningStreamActive", isLightningStreamActive());
        tag.putString("SyncedActiveAbilityType", getSyncedActiveAbilityType());
        tag.putInt("BeamEndTimer", beamEndTimer);
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
        this.setCommand(tag.getInt("Command"));
        this.prevSitProgress = this.sitProgress;
        this.setLightningStreamProgress(tag.getFloat("LightningStreamProgress"));
        this.setLightningStreamActive(tag.getBoolean("LightningStreamActive"));
        this.setSyncedActiveAbilityType(tag.getString("SyncedActiveAbilityType"));
        this.beamEndTimer = tag.getInt("BeamEndTimer");
        animationController.readFromNBT(tag);

        if (this.usingAirNav) {
            switchToAirNavigation();
        } else {
            switchToGroundNavigation();
        }
    }



    // ===== ANIMATION TRIGGERS =====
    /**
     * Triggers the dodge animation - called when dragon dodges projectiles
     */
    public void triggerDodgeAnimation() {
        // Trigger animation through the action controller using animation name string
        triggerAnim("action", "animation.lightning_dragon.dodge");
    }

    @Override
    public void playAnimation(RawAnimation animation) {
        if (level().isClientSide && animation != null) {
            // Extract animation name from the RawAnimation
            // GeckoLib 4.7.4 stores animation stages, get the first one
            if (!animation.getAnimationStages().isEmpty()) {
                String animationName = animation.getAnimationStages().get(0).animationName();
                // Trigger animation through the action controller
                triggerAnim("action", animationName);
            }
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
        AnimationController<LightningDragonEntity> headController =
                new AnimationController<>(this, "head_abilities", 10, this::headAbilityPredicate);
        AnimationController<LightningDragonEntity> actionController =
                new AnimationController<>(this, "action", 5, this::actionPredicate);

        // Set easing types before adding controllers
        movementController.setOverrideEasingType(EasingType.EASE_IN_OUT_SINE);
        headController.setOverrideEasingType(EasingType.EASE_IN_OUT_SINE);
        actionController.setOverrideEasingType(EasingType.EASE_IN_OUT_SINE);

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
        
        // Add all controllers in proper order
        controllers.add(flightBlendController);
        controllers.add(walkRunController);
        controllers.add(movementController);
        controllers.add(headController);
        controllers.add(actionController);
    }

    //PREDICATES
    private PlayState walkRunPredicate(AnimationState<LightningDragonEntity> state) {
        if (isFlying()) return PlayState.STOP;

        // Check if we should use run animation (either AI running or rider acceleration)
        if (isActuallyRunning() || shouldUseAccelerationAnimation()) {
            state.setAndContinue(RUN_SWITCH);
        } else {
            state.setAndContinue(WALK_SWITCH);
        }
        return PlayState.CONTINUE;
    }

    private PlayState flightBlendPredicate(AnimationState<LightningDragonEntity> state) {
        if (!isFlying()) return PlayState.STOP;

        // Use FLY_FORWARD animation when accelerating while flying
        if (shouldUseAccelerationAnimation()) {
            state.setAndContinue(FLAP_SWITCH); // Use flapping for acceleration (FLY_FORWARD)
        } else {
            // Normal flight animation based on speed
            float flappingWeight = animationController.getFlappingFraction(state.getPartialTick());

            if (flappingWeight > 0.5f) {
                state.setAndContinue(FLAP_SWITCH);  // Sets glideController to [0,0,0]
            } else {
                state.setAndContinue(GLIDE_SWITCH); // Sets glideController to [1,0,0]
            }
        }
        return PlayState.CONTINUE;
    }

    private PlayState headAbilityPredicate(AnimationState<LightningDragonEntity> state) {
        // Use synced ability type for client-side animation
        String syncedAbilityType = getSyncedActiveAbilityType();
        
        // DEBUG: Always log what animation we're trying to play AND controller state
        String currentAnim = state.getController().getCurrentAnimation() != null ? 
            state.getController().getCurrentAnimation().animation().name() : "none";
        
        System.out.println("DEBUG CLIENT - Head predicate called: syncedType='" + syncedAbilityType + 
                          "', currentAnim='" + currentAnim + "', controller state=" + state.getController().getAnimationState());
        
        // Handle lightning beam phases with proper transitions
        if ("beam_start".equals(syncedAbilityType)) {
            // Charge phase - play beam_start animation (head pulling back)
            System.out.println("DEBUG CLIENT - Playing BEAM_START animation");
            // Force controller reset for new animation
            state.getController().forceAnimationReset();
            state.getController().transitionLength(8); // Longer transition for smooth charge
            state.setAndContinue(BEAM_START);
            return PlayState.CONTINUE;
        } else if ("lightning_beam".equals(syncedAbilityType)) {
            // Beam phase - play lightning_beam animation (actual firing)
            System.out.println("DEBUG CLIENT - Playing LIGHTNING_BEAM animation");
            state.getController().transitionLength(6); // Moderate transition from charge to firing
            state.setAndContinue(LIGHTNING_BEAM);
            return PlayState.CONTINUE;
        } else if ("beam_end".equals(syncedAbilityType)) {
            // End phase - play beam_end animation (head settling back)
            System.out.println("DEBUG CLIENT - Playing BEAM_END animation");
            state.getController().transitionLength(10); // Longer transition for smooth ending
            state.setAndContinue(BEAM_END);
            return PlayState.CONTINUE;
        }

        // Clear animation when no ability is active
        if (syncedAbilityType.isEmpty()) {
            System.out.println("DEBUG CLIENT - Clearing animation (empty syncedAbilityType)");
            state.getController().transitionLength(15); // Extra smooth transition back to normal
            return PlayState.STOP;
        }

        // Default case - no recognized animation
        System.out.println("DEBUG CLIENT - Unknown animation type: '" + syncedAbilityType + "'");
        state.getController().transitionLength(10);
        return PlayState.STOP;
    }

    private PlayState actionPredicate(AnimationState<LightningDragonEntity> state) {
        // This controller handles one-shot animations triggered by playAnimation()
        // It uses GeckoLib's triggerAnim system to play animations when requested
        return PlayState.CONTINUE;
    }
    //NO MORE PREDICATES
    // getAnimatableInstanceCache() is now provided by base DragonEntity class
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
    // MOWZIE-ENHANCED DYNAMIC EYE HEIGHT SYSTEM
    private float cachedEyeHeight = 0f; // Will be calculated dynamically from renderer
    
    public void setCachedEyeHeight(float height) {
        // DEBUG: Show what eye height we're setting
        if (Math.abs(height - this.cachedEyeHeight) > 0.1f) {
            System.out.println("DEBUG - Setting cachedEyeHeight: " + height + " (was: " + this.cachedEyeHeight + ")");
        }
        this.cachedEyeHeight = height;
    }
    
    @Override
    public float getEyeHeight(@NotNull Pose pose) {
        // Always use dynamically calculated eye height when available
        if (this.cachedEyeHeight > 0f) {
            return this.cachedEyeHeight;
        }
        
        // Fallback: Much lower multiplier - your dragon's head is probably lower than 75%
        EntityDimensions dimensions = getDimensions(pose);
        float fallback = dimensions.height * 0.6f; // Try 60% instead of 75%
        
        if (level().isClientSide) {
            System.out.println("DEBUG - Using fallback eye height: " + fallback + " (dimensions height: " + dimensions.height + ")");
        }
        
        return fallback;
    }
    
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        // Always use cached value when available (both client and server need this)
        if (this.cachedEyeHeight > 0f) {
            return this.cachedEyeHeight;
        }
        
        // Fallback: Match the getEyeHeight calculation
        float fallback = dimensions.height * 0.6f; // Same as above
        
        if (level().isClientSide) {
            System.out.println("DEBUG - Using fallback standing eye height: " + fallback);
        }
        
        return fallback;
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
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(net.minecraft.server.level.@NotNull ServerLevel level, @NotNull AgeableMob otherParent) {
        return null;
    }

    // ===== KEYBIND USING MOUNT IMPLEMENTATION =====
    @Override
    public void onKeyPacket(Entity keyPresser, int type) {
        keybindHandler.onKeyPacket(keyPresser, type);
    }
    @Override
    protected @NotNull Vec3 getRiddenInput(@NotNull Player player, @NotNull Vec3 deltaIn) {
        return riderController.getRiddenInput(player, deltaIn);
    }
    @Override
    protected void tickRidden(@NotNull Player player, @NotNull Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        riderController.tickRidden(player, travelVector);
    }
    /**
     * Handle banking when player is riding and turning
     */

    @Override
    protected float getRiddenSpeed(@NotNull Player rider) {
        return riderController.getRiddenSpeed(rider);
    }
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previousTarget = this.getTarget();
        super.setTarget(target);

        // Play growl when entering combat (new target and no previous target)
        if (target != null && previousTarget == null && !this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.DRAGON_GROWL_WARNING.get(), SoundSource.HOSTILE,
                    1.2f, 0.8f + this.random.nextFloat() * 0.4f);
        }
    }
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return riderController.getControllingPassenger();
    }

    // ===== DRAGON LIGHTNING SYSTEM =====

    /**
     * Fire/Lightning synchronization method - handles both server->client and client->server fire sync
     * syncType 0: Normal lightning effect
     * syncType 1: Server->Client sync
     * syncType 2: Client->Server sync
     */
    public void stimulateFire(double burnX, double burnY, double burnZ, int syncType) {
        lightningSystem.stimulateFire(burnX, burnY, burnZ, syncType);
    }
    /**
     * Ray tracing for rider targeting - determines where the rider is looking
     */
    public net.minecraft.world.phys.HitResult rayTraceRider(Player rider, double maxDistance, float partialTicks) {
        return lightningSystem.rayTraceRider(rider, maxDistance, partialTicks);
    }
    public void riderShootFire() {
        lightningSystem.riderShootFire();
    }
    private void updateLightningBeamFireStopLogic() {
        lightningSystem.updateLightningBeamFireStopLogic();
    }
}