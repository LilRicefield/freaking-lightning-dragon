/**
Lightning beam attack inspired by Ice & Fire: Dragons mod by Alexthe666 and his crew
PLEASE DON'T DMCA MY BUM HEADED-ASS MISTER ALEX. CREDIT WHERE CREDIT'S DUE. RIGHT???? I'M LEARNING FROM THE BEST!!!
*/

// Original implementation adapted and modified for Zap Van Dink (Leon’s Lightning Dragon)

package com.leon.lightningdragon.ai.abilities.combat;

import com.leon.lightningdragon.ai.abilities.Ability;
import com.leon.lightningdragon.ai.abilities.AbilityType;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnhancedLightningBeamAbility extends Ability<LightningDragonEntity> {
    private static final int CHARGE_TIME = 20;
    private static final int BEAM_TIME = 40;
    private static final int TOTAL_TIME = CHARGE_TIME + BEAM_TIME;
    private static final double MAX_RANGE = 32.0;

    private Vec3 beamTarget;
    private int phase = 0; // 0=charge, 1=beam
    private int burnProgress = 0;
    private final Set<LivingEntity> recentlyDamaged = new HashSet<>();

    public EnhancedLightningBeamAbility(AbilityType<LightningDragonEntity, EnhancedLightningBeamAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.LIGHTNING_BEAM);
    }

    @Override
    public boolean tryAbility() {
        // Check if player is riding dragon (owned or not)
        if (getUser().getFirstPassenger() instanceof net.minecraft.world.entity.player.Player) {
            return true;
        }
        
        // Check if tamed dragon has riding player
        if (getUser().getRidingPlayer() != null) {
            return true;
        }
        
        // Otherwise, require a target within specific range (AI-controlled)
        return getUser().getTarget() != null &&
                getUser().distanceToSqr(getUser().getTarget()) >= 256 && // 16+ blocks
                getUser().distanceToSqr(getUser().getTarget()) <= 1024;  // up to 32 blocks
    }

    @Override
    public void start() {
        super.start();
        getUser().setAttacking(true);
        getUser().setLightningStreamActive(true);

        if (getUser().isFlying()) {
            getUser().setHovering(true);
        }

        // Set initial target - use mouth_origin bone position for proper alignment
        if (getUser().getFirstPassenger() instanceof net.minecraft.world.entity.player.Player rider) {
            // Aim where the rider is looking from mouth position
            Vec3 mouthPos = getUser().getMouthPosition();
            Vec3 lookVec = rider.getLookAngle();
            beamTarget = mouthPos.add(lookVec.scale(MAX_RANGE));
        } else if (getUser().getTarget() != null) {
            beamTarget = getUser().getTarget().getEyePosition();
        }

        burnProgress = 0;
        phase = 0;
        recentlyDamaged.clear();
    }

    @Override
    public void tick() {
        super.tick();

        if (getTicksInUse() <= CHARGE_TIME) {
            // Charging phase - build up energy
            chargePhase();
        } else {
            // Beam phase - fire the lightning stream
            beamPhase();
        }
    }

    private void chargePhase() {
        // Update target position - use mouth_origin bone for consistent beam origin
        if (getUser().getFirstPassenger() instanceof net.minecraft.world.entity.player.Player rider) {
            // Continuously track where the rider is looking from mouth position
            Vec3 mouthPos = getUser().getMouthPosition();
            Vec3 lookVec = rider.getLookAngle();
            beamTarget = mouthPos.add(lookVec.scale(MAX_RANGE));
        } else if (getUser().getTarget() != null) {
            beamTarget = getUser().getTarget().getEyePosition();
        }

        // Charging sound every 10 ticks
        if (getTicksInUse() % 10 == 0) {
            getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.5f, 2.0f);
        }
    }

    private void beamPhase() {
        if (phase == 0) {
            // Just entered beam phase
            phase = 1;
            getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0f, 1.0f);
        }

        if (beamTarget == null) return;

        // Progressive beam firing (similar to Ice & Fire)
        stimulateLightning(beamTarget.x, beamTarget.y, beamTarget.z);
        burnProgress++;

        // Continue targeting - either player look direction or target (use mouth_origin)
        if (getUser().getFirstPassenger() instanceof net.minecraft.world.entity.player.Player rider && getTicksInUse() % 2 == 0) {
            // For riding mode, update beam direction frequently for responsive aiming
            Vec3 mouthPos = getUser().getMouthPosition();
            Vec3 lookVec = rider.getLookAngle();
            beamTarget = mouthPos.add(lookVec.scale(MAX_RANGE));
        } else if (getUser().getTarget() != null && getTicksInUse() % 5 == 0) {
            beamTarget = getUser().getTarget().getEyePosition();
        }

        // Beam sound every 7 ticks
        if (getTicksInUse() % 7 == 0) {
            getUser().level().playSound(null, getUser().getX(), getUser().getY(), getUser().getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0f, 1.5f);
        }
    }

    /**
     * AUTHENTIC ICE & FIRE stimulateFire
     * Progressive lightning beam that extends from mouth_origin to target incrementally with proper destruction
     */
    private void stimulateLightning(double burnX, double burnY, double burnZ) {
        // Use mouth_origin bone position for proper beam alignment
        Vec3 mouthPos = getUser().getMouthPosition();
        double d2 = burnX - mouthPos.x;
        double d3 = burnY - mouthPos.y;
        double d4 = burnZ - mouthPos.z;
        
        // Calculate distance and conquered distance based on burn progress
        double distance = Math.max(2.5F, mouthPos.distanceTo(new Vec3(burnX, burnY, burnZ)));
        double conqueredDistance = burnProgress / 40.0 * distance;
        int increment = (int) Math.ceil(conqueredDistance / 100);
        
        // Update stream progress for renderer
        float streamProgress = Math.min(1.0F, (float)(burnProgress / 40.0));
        getUser().setLightningStreamProgress(streamProgress);
        
        // Progressive beam rendering along path - THE CORE ICE & FIRE LOGIC
        for (int i = 0; i < conqueredDistance; i += increment) {
            double progressX = mouthPos.x + d2 * (i / distance);
            double progressY = mouthPos.y + d3 * (i / distance);
            double progressZ = mouthPos.z + d4 * (i / distance);
            
            if (canPositionBeSeen(progressX, progressY, progressZ)) {
                // Clear path - set lightning target for visual rendering (BOTH CLIENT AND SERVER)
                getUser().setHasLightningTarget(true);
                getUser().setLightningTargetVec((float)burnX, (float)burnY, (float)burnZ);
            } else {
                // Hit obstacle - calculate hit location for visual effect (BOTH CLIENT AND SERVER)
                HitResult result = getUser().level().clip(new ClipContext(
                        mouthPos,
                        new Vec3(progressX, progressY, progressZ),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        getUser()
                ));
                
                // Set target to hit location for visual effect (BOTH CLIENT AND SERVER)
                getUser().setHasLightningTarget(true);
                getUser().setLightningTargetVec(
                        (float) result.getLocation().x, 
                        (float) result.getLocation().y, 
                        (float) result.getLocation().z
                );
                
                // Server-only block destruction
                if (!getUser().level().isClientSide) {
                    BlockPos pos = BlockPos.containing(result.getLocation());
                    destroyAreaLightning(pos);
                }
                break;
            }
        }
        
        // Damage entities along the current beam length
        damageEntitiesInBeam(new Vec3(burnX, burnY, burnZ));
        
        // Final impact when beam reaches full distance (Ice & Fire style)
        if (burnProgress >= 40 && canPositionBeSeen(burnX, burnY, burnZ)) {
            // Add some randomness to final impact like Ice & Fire
            double spawnX = burnX + (getUser().level().random.nextFloat() * 3.0) - 1.5;
            double spawnY = burnY + (getUser().level().random.nextFloat() * 3.0) - 1.5;
            double spawnZ = burnZ + (getUser().level().random.nextFloat() * 3.0) - 1.5;
            
            getUser().setHasLightningTarget(true);
            getUser().setLightningTargetVec((float) spawnX, (float) spawnY, (float) spawnZ);
            
            if (!getUser().level().isClientSide) {
                // Massive destruction at final target
                destroyAreaLightning(BlockPos.containing(spawnX, spawnY, spawnZ));
            }
        }
    }

    private boolean canPositionBeSeen(double x, double y, double z) {
        // Ray trace from mouth_origin bone position for accurate line-of-sight
        Vec3 start = getUser().getMouthPosition();
        Vec3 end = new Vec3(x, y, z);

        HitResult result = getUser().level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, getUser()));

        return result.getType() == HitResult.Type.MISS;
    }

    /**
     * ENHANCED Lightning Area Destruction - Ice & Fire Style
     * More realistic destruction pattern with variable intensity
     */
    private void destroyAreaLightning(BlockPos center) {
        // Primary destruction - immediate area
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (getUser().level().getBlockState(pos).getDestroySpeed(getUser().level(), pos) >= 0) {
                        getUser().level().destroyBlock(pos, true);
                    }
                }
            }
        }
        
        // Secondary destruction - wider area with probability
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1) continue; // Skip already destroyed
                    
                    BlockPos pos = center.offset(x, y, z);
                    float distance = (float) Math.sqrt(x*x + y*y + z*z);
                    float destructionChance = Math.max(0.1f, 1.0f - (distance / 3.0f)); // Closer = higher chance
                    
                    if (getUser().level().random.nextFloat() < destructionChance) {
                        if (getUser().level().getBlockState(pos).getDestroySpeed(getUser().level(), pos) >= 0) {
                            getUser().level().destroyBlock(pos, getUser().level().random.nextFloat() < 0.7f);
                        }
                    }
                }
            }
        }
    }

    /**
     * ICE & FIRE STYLE Entity Damage System
     * Damages all entities along the full beam path to target
     */
    private void damageEntitiesInBeam(Vec3 target) {
        Vec3 mouthPos = getUser().getMouthPosition();
        Vec3 direction = target.subtract(mouthPos).normalize();
        double distance = mouthPos.distanceTo(target);

        // Segment the beam for entity detection
        int segments = Math.max(1, (int)(distance / 2.0));

        for (int i = 0; i <= segments; i++) {
            double progress = (double)i / segments;
            Vec3 checkPos = mouthPos.add(direction.scale(progress * distance));

            List<LivingEntity> nearbyEntities = getUser().level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(checkPos.subtract(1.5, 1.5, 1.5), checkPos.add(1.5, 1.5, 1.5)),
                    entity -> entity != getUser() && entity != getUser().getOwner() &&
                            !recentlyDamaged.contains(entity));

            for (LivingEntity entity : nearbyEntities) {
                double distFromBeam = entity.position().distanceTo(checkPos);
                float damage = (float)(12.0 * Math.max(0.1, 1.0 - distFromBeam / 1.5));

                if (entity.hurt(getUser().damageSources().mobAttack(getUser()), damage)) {
                    // Lightning effects like Ice & Fire
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));

                    // Knockback along beam direction
                    Vec3 knockback = direction.scale(0.8);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                    recentlyDamaged.add(entity);
                }
            }
        }

        // Clear damage set periodically
        if (getTicksInUse() % 8 == 0) {
            recentlyDamaged.clear();
        }
    }

    public boolean isFinished() {
        // Check if this is a continuous lightning beam from a riding player using control state
        if (getUser().getFirstPassenger() instanceof net.minecraft.world.entity.player.Player) {
            // Check if the rider is still holding the Y key (control state bit 4 = value 16)
            boolean stillPressingKey = (getUser().getControlState() & 16) != 0;
            
            if (!stillPressingKey) {
                // Key released, stop the beam
                return true;
            }
            
            // For continuous mode, allow extended duration but prevent infinite duration
            return getTicksInUse() >= TOTAL_TIME * 10; // Allow up to 10x normal duration for continuous
        }
        return getTicksInUse() >= TOTAL_TIME;
    }

    @Override
    public void end() {
        super.end();
        getUser().setAttacking(false);
        getUser().setHasLightningTarget(false);
        getUser().setLightningStreamActive(false);
        getUser().setLightningStreamProgress(0.0F);

        if (getUser().isFlying()) {
            getUser().setHovering(false);
        }

        recentlyDamaged.clear();
        burnProgress = 0;
        phase = 0;
    }

    @Override
    public boolean canCancelActiveAbility() {
        return phase == 0 && getTicksInUse() < 15;
    }
}