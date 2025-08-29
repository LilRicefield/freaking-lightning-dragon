/**
 * My name's Zap Van Dink. I'm a lightning dragon.
 */
package com.leon.lightningdragon.server.entity;

//Custom stuff
import com.leon.lightningdragon.server.ai.goals.*;
import com.leon.lightningdragon.server.ai.navigation.DragonFlightMoveHelper;
import com.leon.lightningdragon.server.entity.controller.DragonFlightPhysicsController;
import com.leon.lightningdragon.client.animation.EnhancedAnimationController;
import com.leon.lightningdragon.server.entity.base.DragonEntity;
import com.leon.lightningdragon.server.entity.controller.DragonCombatManager;
import com.leon.lightningdragon.server.entity.controller.DragonFlightController;
import com.leon.lightningdragon.server.entity.controller.DragonInteractionHandler;
import com.leon.lightningdragon.server.entity.controller.DragonStateManager;
import com.leon.lightningdragon.server.entity.handler.DragonKeybindHandler;
import com.leon.lightningdragon.server.entity.handler.DragonRiderController;
import com.leon.lightningdragon.server.entity.handler.DragonSoundHandler;
import com.leon.lightningdragon.util.DragonMathUtil;
import com.leon.lightningdragon.server.entity.ability.DragonAbility;
import com.leon.lightningdragon.common.registry.ModSounds;
import com.leon.lightningdragon.util.KeybindUsingMount;

//Minecraft
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

    // ===== ABILITY SYSTEM =====
    // TODO: Define new DragonAbilityType abilities here

    // ===== BONE POSITION TRACKING =====
    // Position tracking array for beam_origin bone
    public Vec3[] beamOriginPos = new Vec3[1];


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

    // Command system
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

    // Animation state tracking for smooth transitions
    private boolean wasWalking = false;
    private boolean wasRunning = false;

    // Smooth movement state tracking (prevents snapping during block jumps/drops)
    private int walkingStateTicks = 0;
    private int runningStateTicks = 0;
    private static final int MOVEMENT_STATE_PERSISTENCE = 8; // Ticks to maintain state during jumps

    // Walk/Run transition controller value (synced to model)
    public float walkRunTransitionValue = 0.0f;
    private float prevWalkRunTransitionValue = 0.0f;
    private static final float TRANSITION_SPEED = 0.08f; // How fast transitions happen

    // Glide transition controller value (synced to model)
    public float glideTransitionValue = 0.0f;
    private float prevGlideTransitionValue = 0.0f;
    private static final float GLIDE_TRANSITION_SPEED = 0.06f; // Slightly slower for smoother flight transitions



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
    private final DragonRiderController riderController;
    private final DragonSoundHandler soundHandler;

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
    private final DragonFlightPhysicsController animationController = new DragonFlightPhysicsController(this);

    /**
     * Get the animation controller for model integration
     */
    public DragonFlightPhysicsController getAnimationController() {
        return animationController;
    }


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
        this.riderController = new DragonRiderController(this);
        this.soundHandler = new DragonSoundHandler(this);

        // TODO: Re-implement tail physics with GeckoLib-compatible approach
        // if (level.isClientSide) {
        //     this.tailChain = new DragonDynamicChain(this, DragonDynamicChain.ChainType.TAIL);
        // }
    }

    // ===== HANDLER ACCESS METHODS =====

    public DragonKeybindHandler getKeybindHandler() {
        return keybindHandler;
    }

    public DragonRiderController getRiderController() {
        return riderController;
    }

    public DragonSoundHandler getSoundHandler() {
        return soundHandler;
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
        this.entityData.define(DATA_LANDING, false);
        this.entityData.define(DATA_RUNNING, false);
        this.entityData.define(DATA_ATTACKING, false);
        this.entityData.define(HAS_LIGHTNING_TARGET, false);
        this.entityData.define(LIGHTNING_TARGET_X, 0.0f);
        this.entityData.define(LIGHTNING_TARGET_Y, 0.0f);
        this.entityData.define(LIGHTNING_TARGET_Z, 0.0f);
        this.entityData.define(LIGHTNING_STREAM_PROGRESS, 0.0f);
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

    // ===== ABILITY SYSTEM METHODS =====
    public boolean tryUseRangedAbility() {
        return combatManager.tryUseRangedAbility();
    }

    // Strategic ultimate ability usage
    private boolean shouldUseUltimate() {
        LivingEntity target = getTarget();
        if (target == null) return false;

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

    @Override
    public <T extends DragonEntity> DragonAbility<T> getActiveAbility() {
        return (DragonAbility<T>) combatManager.getActiveAbility();
    }
    // setActiveAbility is now provided by base DragonEntity class
    public boolean canUseAbility() {
        return combatManager.canUseAbility();
    }
    // TODO: Implement new Dragon ability system
    public void sendAbilityMessage(Object abilityType) {
        combatManager.sendAbilityMessage(abilityType);
    }
    public void useRidingAbility(String abilityName) {
        // TODO: Implement new Dragon ability system for riding
    }

    /**
     * Forces the dragon to take off when being ridden. Called when player presses Space while on ground.
     */
    public void requestRiderTakeoff() {
        riderController.requestRiderTakeoff();
    }

    @Override
    public Vec3 getHeadPosition() {
        // Use GeckoLib bone position if available
        Vec3 bonePos = getBonePosition("head");
        if (bonePos != null) {
            return bonePos;
        }
        // Fallback to eye position
        return getEyePosition();
    }

    // Get mouth position using beam_origin bone from GeckoLib model
    @Override
    public Vec3 getMouthPosition() {
        // Try to get the beam_origin bone position (works on both client and server)
        Vec3 bonePos = getBonePosition("beam_origin");
        if (bonePos != null) {
            return bonePos;
        }
        Vec3 basePos = getHeadPosition();
        double localX = 15.0 / 16.0 * MODEL_SCALE;    // Forward from head
        double localY = 6.5 / 16.0 * MODEL_SCALE;     // Up from head base
        double localZ = -15.0 / 16.0 * MODEL_SCALE;   // Side offset (minimal for mouth)

        // Apply rotation based on entity's body rotation
        double radians = Math.toRadians(-yBodyRot); // Negative because Minecraft coordinate system
        double rotatedX = localX * Math.cos(radians) - localZ * Math.sin(radians);
        double rotatedZ = localX * Math.sin(radians) + localZ * Math.cos(radians);

        Vec3 lookDirection = getLookAngle();
        double pitchAdjustment = 0;
        if (isFlying()) {
            // When flying and looking down, adjust the beam origin to be more forward
            float pitch = getXRot(); // Positive pitch means looking down
            if (pitch > 0) {
                // The more the dragon looks down, the more forward the beam origin should be
                pitchAdjustment = (pitch / 90.0) * MODEL_SCALE * 0.5; // Scale with pitch
            }
        }

        // Final position calculation
        double finalX = basePos.x + rotatedX + (lookDirection.x * pitchAdjustment);
        double finalY = basePos.y + localY - (lookDirection.y * pitchAdjustment); // Subtract to go forward when looking down
        double finalZ = basePos.z + rotatedZ + (lookDirection.z * pitchAdjustment);

        return new Vec3(finalX, finalY, finalZ);
    }

    /**
     * Get bone position from GeckoLib model (works on both client and server)
     */
    public Vec3 getBonePosition(String boneName) {
        if (level().isClientSide) {
            return getClientBonePosition(boneName);
        } else {
            // Server-side: Use cached bone positions or fallback calculation
            return getServerBonePosition(boneName);
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private Vec3 getClientBonePosition(String boneName) {
        try {
            // If we have a tracked position from the renderer, use it
            if ("beam_origin".equals(boneName) && beamOriginPos[0] != null) {
                return beamOriginPos[0];
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Vec3 getServerBonePosition(String boneName) {
        if ("beam_origin".equals(boneName)) {
            // For now, return the same calculated position as before
            // This ensures compatibility while you set up the bone
            Vec3 basePos = getHeadPosition();
            double localX = 15.0 / 16.0 * MODEL_SCALE;
            double localY = 6.5 / 16.0 * MODEL_SCALE;
            double localZ = -15.0 / 16.0 * MODEL_SCALE;

            double radians = Math.toRadians(-yBodyRot);
            double rotatedX = localX * Math.cos(radians) - localZ * Math.sin(radians);
            double rotatedZ = localX * Math.sin(radians) + localZ * Math.cos(radians);

            return new Vec3(basePos.x + rotatedX, basePos.y + localY, basePos.z + rotatedZ);
        }
        return null;
    }

    /**
     * Get locator position from GeckoLib model (client-side only)
     */
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private Vec3 getLocatorPosition(String locatorName) {
        // Only available client-side where we can access the renderer and model
        if (!level().isClientSide) {
            return null;
        }

        try {
            // Access the client-side renderer to get the model
            net.minecraft.client.renderer.entity.EntityRenderer<?> renderer =
                    net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(this);

            if (renderer instanceof com.leon.lightningdragon.client.renderer.LightningDragonRenderer dragonRenderer) {
                // Get the current model from the renderer using the model location, not texture location
                software.bernie.geckolib.cache.object.BakedGeoModel model = dragonRenderer.getGeoModel().getBakedModel(dragonRenderer.getGeoModel().getModelResource(this));

                // Try to get the locator/bone by name
                return model.getBone(locatorName).map(bone -> {
                    org.joml.Vector3d bonePos = bone.getWorldPosition();
                    // Scale and transform the bone position to world coordinates
                    // The renderer applies MODEL_SCALE, so account for that
                    double scaledX = bonePos.x * MODEL_SCALE;
                    double scaledY = bonePos.y * MODEL_SCALE;
                    double scaledZ = bonePos.z * MODEL_SCALE;

                    // Transform relative to entity position and orientation
                    double radians = Math.toRadians(-yBodyRot); // Negative because Minecraft rotations
                    double rotatedX = scaledX * Math.cos(radians) - scaledZ * Math.sin(radians);
                    double rotatedZ = scaledX * Math.sin(radians) + scaledZ * Math.cos(radians);

                    double worldX = getX() + rotatedX;
                    double worldY = getY() + scaledY;
                    double worldZ = getZ() + rotatedZ;

                    return new Vec3(worldX, worldY, worldZ);
                }).orElse(null);
            }
        } catch (Exception e) {
            // If anything fails, return null to use fallback
        }

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
     * Check if dragon should use acceleration animation (run on ground only)
     */
    public boolean shouldUseAccelerationAnimation() {
        return this.isVehicle() && this.isAccelerating() && this.getRidingPlayer() != null && !isFlying();
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

    // Animation timing for beam_end
    private int beamEndTimer = 0;

    // Control state system
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

        // Sync enhanced animation controllers to flight physics
        if (level().isClientSide) {
            syncEnhancedAnimationsToPhysics();
        }

        super.tick();

        // Delegate to controllers
        combatManager.validateCurrentTarget();
        flightController.handleFlightLogic();
        combatManager.tick();
        stateManager.updateRunningAttributes();
        interactionHandler.updateSittingProgress();

        if (!level().isClientSide) {
            handleAmbientSounds();
        }

        // Ground movement particle effects
        if (level().isClientSide && stateManager.isGroundMoving() && tickCount % 8 == 0) {
            spawnGroundMovementParticles();
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

        // Note: Removed walk/run stop chains - switch-bone blending handles smooth transitions automatically

        // Update walk/run transition value
        updateWalkRunTransition();

        // Update glide transition value for flight transitions
        updateGlideTransition();


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
        if (isAggressive()) { // TODO: Add new Dragon ability system check
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
        } else if (stateManager.isGroundMoving()) {
            // Ground movement sounds - different based on speed
            if (stateManager.isRunning()) {
                soundToPlay = ModSounds.DRAGON_SNORT.get(); // Heavy breathing when running
                volume = 1.0f;
            } else {
                soundToPlay = ModSounds.DRAGON_CHUFF.get(); // Gentle snorts when walking
                volume = 0.7f;
            }
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

            // Let tickRidden handle rotation smoothly
            // No instant rotation here - all handled in tickRidden for responsiveness

            if (isFlying()) {
                // Flying movement
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
                // TODO: Replace with new Dragon ability system
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
            if (this.dragon.isAlive()) {
                super.tick();
            }
        }
    }
    // ===== AI GOALS =====
    @Override
    protected void registerGoals() {
        // Basic goals (no riding goal needed - handled by getRiddenInput)
        this.goalSelector.addGoal(3, new DragonPanicGoal(this));
        this.goalSelector.addGoal(2, new DragonDodgeGoal(this));
        this.goalSelector.addGoal(4, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(5, new FloatGoal(this));

        // Combat goals - Flight trigger comes FIRST
        this.goalSelector.addGoal(5, new DragonCombatFlightGoal(this));  // NEW: Takes flight for combat
        this.goalSelector.addGoal(5, new DragonAirCombatGoal(this));     // Air combat
        this.goalSelector.addGoal(5, new DragonGroundCombatGoal(this));  // Ground combat (fallback)

        // Basic movement
        this.goalSelector.addGoal(1, new DragonFollowOwnerGoal(this));
        this.goalSelector.addGoal(2, new DragonGroundWanderGoal(this, 1.0, 60));
        this.goalSelector.addGoal(5, new DragonFlightGoal(this));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));

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

        // TODO: Trigger hurt ability with new Dragon ability system

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
        return stack.is(Items.SALMON);
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity entityIn) {
        // TODO: Use new Dragon ability system for melee attacks
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
        tag.putBoolean("UsingAirNav", usingAirNav);
        tag.putFloat("SitProgress", sitProgress);
        tag.putInt("Command", getCommand());
        tag.putString("SyncedActiveAbilityType", getSyncedActiveAbilityType());
        tag.putFloat("WalkRunTransitionValue", walkRunTransitionValue);
        tag.putFloat("GlideTransitionValue", glideTransitionValue);
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
        this.usingAirNav = tag.getBoolean("UsingAirNav");
        this.sitProgress = tag.getFloat("SitProgress");
        this.setCommand(tag.getInt("Command"));
        this.prevSitProgress = this.sitProgress;
        this.setSyncedActiveAbilityType(tag.getString("SyncedActiveAbilityType"));
        this.walkRunTransitionValue = tag.getFloat("WalkRunTransitionValue");
        this.prevWalkRunTransitionValue = this.walkRunTransitionValue;
        this.glideTransitionValue = tag.getFloat("GlideTransitionValue");
        this.prevGlideTransitionValue = this.glideTransitionValue;
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
        // Main movement controller with enhanced features
        EnhancedAnimationController<LightningDragonEntity> movementController =
                new EnhancedAnimationController<>(this, "movement", 8, animationController::handleMovementAnimation);

        // Store reference for physics sync
        setEnhancedMovementController(movementController);

        // Dragon-specific controllers
        EnhancedAnimationController<LightningDragonEntity> walkRunController =
                new EnhancedAnimationController<>(this, "walkRunController", 4, this::walkRunPredicate);
        EnhancedAnimationController<LightningDragonEntity> flightBlendController =
                new EnhancedAnimationController<>(this, "glideController", 6, this::flightBlendPredicate);
        EnhancedAnimationController<LightningDragonEntity> bankingController =
                new EnhancedAnimationController<>(this, "bankingController", 12, this::bankingPredicate);
        EnhancedAnimationController<LightningDragonEntity> actionController =
                new EnhancedAnimationController<>(this, "action", 5, this::actionPredicate);

        // Add controllers in order (flight blend first for proper layering)
        controllers.add(flightBlendController);
        controllers.add(bankingController);
        controllers.add(walkRunController);
        controllers.add(movementController);
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
            state.setAndContinue(FLAP_SWITCH);
        } else {
            // Normal flight animation based on speed
            float flappingWeight = animationController.getFlappingFraction(state.getPartialTick());

            if (flappingWeight > 0.5f) {
                state.setAndContinue(FLAP_SWITCH); // Active flight when flapping
            } else {
                state.setAndContinue(GLIDE_SWITCH); // Glide when coasting
            }
        }
        return PlayState.CONTINUE;
    }

    private PlayState bankingPredicate(AnimationState<LightningDragonEntity> state) {
        // Only apply banking during flight
        if (!isFlying()) {
            state.setAndContinue(RawAnimation.begin().thenPlay("animation.lightning_dragon.banking_off"));
            return PlayState.CONTINUE;
        }

        // Check turning direction
        float yawChange = getYRot() - yRotO;
        float yawDiff = Math.abs(yawChange);

        if (yawDiff > 0.1f) {
            if (yawChange > 0) {
                state.setAndContinue(RawAnimation.begin().thenLoop("animation.lightning_dragon.banking_right"));
            } else {
                state.setAndContinue(RawAnimation.begin().thenLoop("animation.lightning_dragon.banking_left"));
            }
        } else {
            // Flying straight - use banking_off
            state.setAndContinue(RawAnimation.begin().thenPlay("animation.lightning_dragon.banking_off"));
        }
        return PlayState.CONTINUE;
    }

    private PlayState actionPredicate(AnimationState<LightningDragonEntity> state) {
        // This controller handles one-shot animations triggered by playAnimation()
        // It uses GeckoLib's triggerAnim system to play animations when requested
        return PlayState.CONTINUE;
    }

    //NO MORE PREDICATES
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
    // DYNAMIC EYE HEIGHT SYSTEM
    private float cachedEyeHeight = 0f; // Will be calculated dynamically from renderer

    public void setCachedEyeHeight(float height) {
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
        // Try 60% instead of 75%
        return dimensions.height * 0.6f;
    }

    @Override
    protected float getStandingEyeHeight(@NotNull Pose pose, @NotNull EntityDimensions dimensions) {
        // Always use cached value when available (both client and server need this)
        if (this.cachedEyeHeight > 0f) {
            return this.cachedEyeHeight;
        }

        // Fallback: Match the getEyeHeight calculation
        // Same as above


        return dimensions.height * 0.6f;
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


    // ===== ENHANCED ANIMATION INTEGRATION =====
    // Store direct reference to avoid casting issues
    private EnhancedAnimationController<LightningDragonEntity> enhancedMovementController = null;

    /**
     * Set the enhanced movement controller reference (called during registerControllers)
     */
    private void setEnhancedMovementController(EnhancedAnimationController<LightningDragonEntity> controller) {
        this.enhancedMovementController = controller;
    }

    /**
     * Sync enhanced animation controllers to flight physics and dragon state
     */
    private void syncEnhancedAnimationsToPhysics() {
        if (enhancedMovementController == null) return;

        try {
            // Sync to flight physics
            enhancedMovementController.syncToFlightPhysics(this);

            // Dynamic animation speed based on dragon's state
            float speedMultiplier = 1.0f;

            // Speed up during combat
            if (isAttacking() || getTarget() != null) {
                speedMultiplier *= 1.3f;
            }

            // Slow down when landing
            if (stateManager.isLanding()) {
                speedMultiplier *= 0.7f;
            }

            // Speed up when running/sprinting
            if (stateManager.isRunning()) {
                speedMultiplier *= 1.2f;
            }

            // Slow down when sitting or severely injured
            if (isInSittingPose() || getHealth() < getMaxHealth() * 0.3f) {
                speedMultiplier *= 0.6f;
            }

            // Additional health-based adjustments
            float healthRatio = getHealth() / getMaxHealth();
            if (healthRatio < 0.6f && healthRatio >= 0.3f) {
                speedMultiplier *= 0.85f; // 15% slower when moderately wounded
            }

            // Speed up when excited (being ridden)
            if (isVehicle()) {
                speedMultiplier *= 1.1f; // 10% faster when being ridden
            }

            enhancedMovementController.setAnimationSpeedMultiplier(speedMultiplier);
        } catch (Exception e) {
            // Fail silently - enhanced animations are optional
        }
    }

    /**
     * Get enhanced animation controller by name (for external access)
     */
    public EnhancedAnimationController<LightningDragonEntity> getEnhancedController(String name) {
        if ("movement".equals(name)) {
            return enhancedMovementController;
        }
        return null;
    }

    /**
     * Perform smooth animation transition with custom curve
     * @param animation The target animation to transition to
     * @param transitionTicks Duration of transition in ticks
     * @param curve The easing curve for the transition
     */
    public void transitionToAnimation(RawAnimation animation, int transitionTicks, EnhancedAnimationController.TransitionCurve curve) {
        if (enhancedMovementController != null) {
            enhancedMovementController.transitionToAnimation(animation, transitionTicks, curve);
        }
    }

    /**
     * Perform smooth animation transition with default easing
     * @param animation The target animation to transition to
     * @param transitionTicks Duration of transition in ticks
     */
    public void transitionToAnimation(RawAnimation animation, int transitionTicks) {
        transitionToAnimation(animation, transitionTicks, EnhancedAnimationController.TransitionCurve.EASE_IN_OUT);
    }

    /**
     * Quick transition to animation (4 tick default)
     * @param animation The target animation to transition to
     */
    public void transitionToAnimation(RawAnimation animation) {
        transitionToAnimation(animation, 4);
    }

    // REMOVED: checkMovementStateChanges() and stop chain system
    // The switch-bone blending pattern handles smooth transitions automatically
    // No need for discrete walk_stop/run_stop animations

    /**
     * Update walk/run transition value similar to ModelUmvuthana but simplified
     * This value is then used by the Model's walkRunTransitionController
     */
    private void updateWalkRunTransition() {
        if (isFlying()) {
            // Reset transition when flying
            walkRunTransitionValue = 0.0f;
            return;
        }

        // Calculate target transition value
        float targetValue = 0.0f;

        if (isActuallyRunning() || shouldUseAccelerationAnimation()) {
            targetValue = 1.0f; // Full run
        } else if (isWalking()) {
            targetValue = 0.0f; // Full walk
        }
        // If not moving, keep current value and let it decay naturally

        // Smooth transition like ModelUmvuthana
        prevWalkRunTransitionValue = walkRunTransitionValue;
        walkRunTransitionValue = Mth.lerp(TRANSITION_SPEED, walkRunTransitionValue, targetValue);
    }

    /**
     * Get the walk/run transition value for the model (like ModelUmvuthana's getControllerValue)
     */
    public float getWalkRunTransitionValue() {
        return walkRunTransitionValue;
    }

    /**
     * Get interpolated walk/run transition value for smooth rendering
     */
    public float getWalkRunTransitionValue(float partialTicks) {
        return Mth.lerp(partialTicks, prevWalkRunTransitionValue, walkRunTransitionValue);
    }

    /**
     * Update glide transition value similar to ModelUmvuthana for flight transitions
     * This value is used by the Model's glideController
     */
    private void updateGlideTransition() {
        if (!isFlying()) {
            // Reset transition when not flying
            glideTransitionValue = 0.0f;
            return;
        }

        // Calculate target transition value based on flight state
        float targetValue = 0.0f;

        // Check if dragon is ascending (needs flapping)
        if (getDeltaMovement().y > 0.02) {
            targetValue = 0.0f; // Full flap when going up
        } else {
            // Get flapping amount from animation controller for occasional flapping
            float flappingWeight = animationController.getFlappingFraction(1.0f);

            if (flappingWeight > 0.5f || shouldUseAccelerationAnimation()) {
                targetValue = 0.0f; // Occasional flap
            } else {
                targetValue = 1.0f; // Glide when level/descending
            }
        }

        prevGlideTransitionValue = glideTransitionValue;
        glideTransitionValue = Mth.lerp(GLIDE_TRANSITION_SPEED, glideTransitionValue, targetValue);
    }

    /**
     * Get the glide transition value for the model (like ModelUmvuthana's getControllerValue)
     */
    public float getGlideTransitionValue() {
        return glideTransitionValue;
    }

    /**
     * Get interpolated glide transition value for smooth rendering
     */
    public float getGlideTransitionValue(float partialTicks) {
        return Mth.lerp(partialTicks, prevGlideTransitionValue, glideTransitionValue);
    }


    /**
     * Spawn ground movement particles when dragon is walking/running
     */
    private void spawnGroundMovementParticles() {
        RandomSource random = getRandom();

        // Spawn dust particles behind the dragon
        Vec3 behindPos = position().add(getLookAngle().scale(-MODEL_SCALE * 0.8));
        behindPos = behindPos.add(0, -0.1, 0); // Lower to ground level

        // More particles when running
        int particleCount = stateManager.isRunning() ? 3 : 1;

        for (int i = 0; i < particleCount; i++) {
            double offsetX = (random.nextFloat() - 0.5F) * getBbWidth();
            double offsetZ = (random.nextFloat() - 0.5F) * getBbWidth();

            level().addParticle(
                    net.minecraft.core.particles.ParticleTypes.CLOUD,
                    behindPos.x + offsetX,
                    behindPos.y,
                    behindPos.z + offsetZ,
                    0.0, 0.01, 0.0
            );
        }
    }
}