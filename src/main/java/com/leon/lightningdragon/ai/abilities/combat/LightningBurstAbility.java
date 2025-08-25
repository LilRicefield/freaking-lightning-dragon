package com.leon.lightningdragon.ai.abilities.combat;

import com.leon.lightningdragon.ai.abilities.Ability;
import com.leon.lightningdragon.ai.abilities.AbilityType;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LightningBurstAbility extends Ability<LightningDragonEntity> {

    // Timing constants
    private static final int CHARGE_TIME = 60; // 3 seconds at 20 TPS
    private static final int BURST_TIME = 10; // Half second burst
    
    // Attack properties
    private static final double BURST_RANGE = 40.0; // Max range
    private static final float BURST_DAMAGE = 20.0F; // High damage
    private static final float MISS_CHANCE = 0.01F; // 1% miss chance
    
    // State tracking
    private int phase = 0; // 0 = charging, 1 = bursting
    private Vec3 targetPosition = null;
    private LivingEntity burstTarget = null;

    public LightningBurstAbility(AbilityType<LightningDragonEntity, LightningBurstAbility> abilityType, LightningDragonEntity user) {
        super(abilityType, user);
        setAnimation(LightningDragonEntity.LIGHTNING_BURST);
    }

    @Override
    public boolean tryAbility() {
        // If ridden by player, check Lightning Burst cooldown via combat manager
        if (getUser().getRidingPlayer() != null) {
            return getUser().combatManager.canUseLightningBurst();
        }
        
        // For AI, also check cooldown plus range requirements
        if (!getUser().combatManager.canUseLightningBurst()) return false;
        
        LivingEntity target = getUser().getTarget();
        if (target == null || !target.isAlive()) return false;
        
        double distance = getUser().distanceTo(target);
        return distance >= 20.0 && distance <= 30.0 && 
               getUser().hasLineOfSight(target); // Can be used while flying or on ground
    }

    @Override
    public void start() {
        super.start();
        burstTarget = getUser().getTarget();
        phase = 0;
        
        if (getUser().getRidingPlayer() != null) {
            // For rider mode, aim where the player is looking
            Vec3 playerEyePos = getUser().getRidingPlayer().getEyePosition();
            Vec3 lookVec = getUser().getRidingPlayer().getLookAngle();
            targetPosition = playerEyePos.add(lookVec.scale(BURST_RANGE));
        } else if (burstTarget != null) {
            // Initial target position with slight prediction
            Vec3 targetVel = burstTarget.getDeltaMovement();
            targetPosition = burstTarget.position().add(targetVel.scale(2.0)); // Predict 2 ticks ahead
        } else {
            // Default forward aim
            Vec3 dragonPos = getUser().getEyePosition();
            Vec3 lookVec = getUser().getLookAngle();
            targetPosition = dragonPos.add(lookVec.scale(BURST_RANGE));
        }
        
        // Look at target
        if (burstTarget != null) {
            getUser().getLookControl().setLookAt(burstTarget.getEyePosition());
        } else if (targetPosition != null) {
            getUser().getLookControl().setLookAt(targetPosition);
        }
    }

    @Override
    protected void tickUsing() {
        super.tickUsing();
        
        if (phase == 0) {
            tickCharging();
        } else if (phase == 1) {
            tickBurst();
        }
    }
    
    private void tickCharging() {
        // Update target tracking during charge
        if (burstTarget != null && burstTarget.isAlive()) {
            // Continuously track target with high precision (99% accuracy)
            Vec3 targetVel = burstTarget.getDeltaMovement();
            Vec3 predictedPos = burstTarget.position().add(targetVel.scale(1.5));
            
            // Apply 1% miss chance
            if (getUser().getRandom().nextFloat() < MISS_CHANCE) {
                // Miss - add random offset
                double missOffset = 3.0 + getUser().getRandom().nextDouble() * 4.0;
                double angle = getUser().getRandom().nextDouble() * Math.PI * 2;
                predictedPos = predictedPos.add(
                    Math.cos(angle) * missOffset,
                    getUser().getRandom().nextDouble() * 2.0 - 1.0,
                    Math.sin(angle) * missOffset
                );
            }
            
            targetPosition = predictedPos;
            getUser().getLookControl().setLookAt(targetPosition);
        }
        
        // Charge effects - blue electric sparks around mouth
        if (!getUser().level().isClientSide && getTicksInUse() % 3 == 0) {
            // Electric charge particles around mouth
            Vec3 mouthPos = getUser().getCachedMouthPosition();
            ServerLevel serverLevel = (ServerLevel) getUser().level();
            
            // Create more intensive charging effect
            for (int i = 0; i < 5; i++) {
                double offsetX = (getUser().getRandom().nextDouble() - 0.5) * 1.5;
                double offsetY = (getUser().getRandom().nextDouble() - 0.5) * 1.5;
                double offsetZ = (getUser().getRandom().nextDouble() - 0.5) * 1.5;
                
                // Use both custom and vanilla particles for variety
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    mouthPos.x + offsetX, mouthPos.y + offsetY, mouthPos.z + offsetZ,
                    1, 0, 0, 0, 0.05);
                    
                // Add some soul fire flame for blue tint
                if (getUser().getRandom().nextFloat() < 0.3F) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        mouthPos.x + offsetX * 0.5, mouthPos.y + offsetY * 0.5, mouthPos.z + offsetZ * 0.5,
                        1, 0, 0, 0, 0.02);
                }
            }
        }
        
        // Transition to burst
        if (getTicksInUse() >= CHARGE_TIME) {
            phase = 1;
            
            // Final target lock
            if (targetPosition != null) {
                fireLightningBurst();
            }
        }
    }
    
    private void tickBurst() {
        // Burst effects
        if (!getUser().level().isClientSide && getTicksInUse() <= CHARGE_TIME + BURST_TIME) {
            createBurstEffects();
        }
        
        // End burst
        if (getTicksInUse() >= CHARGE_TIME + BURST_TIME) {
            end();
        }
    }
    
    private void fireLightningBurst() {
        if (getUser().level().isClientSide || targetPosition == null) return;
        
        Vec3 startPos = getUser().getCachedMouthPosition();
        Vec3 endPos = targetPosition;
        
        // Raycast to find actual hit point
        ClipContext clipContext = new ClipContext(startPos, endPos, 
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, getUser());
        var hitResult = getUser().level().clip(clipContext);
        
        if (hitResult.getType() != HitResult.Type.MISS) {
            endPos = hitResult.getLocation();
        }
        
        // Create lightning effect
        ServerLevel serverLevel = (ServerLevel) getUser().level();
        
        // Create advanced lightning burst particles
        createAdvancedLightningEffect(startPos, endPos, serverLevel);
        
        // Damage entities in the path
        damageEntitiesInPath(startPos, endPos);
    }
    
    private void damageEntitiesInPath(Vec3 start, Vec3 end) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        direction = direction.normalize();
        
        // Check every 0.5 blocks along the path
        for (double d = 0; d < distance; d += 0.5) {
            Vec3 checkPos = start.add(direction.scale(d));
            AABB checkBox = new AABB(checkPos.subtract(1.5, 1.5, 1.5), checkPos.add(1.5, 1.5, 1.5));
            
            for (LivingEntity living : getUser().level().getEntitiesOfClass(LivingEntity.class, checkBox)) {
                if (living == getUser() || living == getUser().getOwner()) continue;
                if (getUser().isTame() && getUser().getOwner() != null && living == getUser().getOwner()) continue;
                
                // Create custom lightning damage
                DamageSource lightningDamage = getUser().damageSources().mobAttack(getUser());
                
                if (living.hurt(lightningDamage, BURST_DAMAGE)) {
                    // Knockback
                    Vec3 knockbackDir = living.position().subtract(getUser().position()).normalize();
                    living.setDeltaMovement(living.getDeltaMovement().add(knockbackDir.scale(1.2)));
                    
                    // Electric effect
                    if (!getUser().level().isClientSide) {
                        ServerLevel serverLevel = (ServerLevel) getUser().level();
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                            8, living.getBbWidth(), living.getBbHeight() * 0.5, living.getBbWidth(), 0.3);
                    }
                }
            }
        }
    }
    
    private void createAdvancedLightningEffect(Vec3 startPos, Vec3 endPos, ServerLevel serverLevel) {
        // EPIC PARTICLE BEAM - inspired by Alex's Caves but using particles!
        Vec3 direction = endPos.subtract(startPos);
        double distance = direction.length();
        direction = direction.normalize();
        
        // Create dense particle beam trail
        for (double d = 0; d < distance; d += 0.3) { // Denser particles
            Vec3 particlePos = startPos.add(direction.scale(d));
            
            // Create "cross" pattern like Alex's Caves beam model
            for (int cross = 0; cross < 4; cross++) {
                double angle = (cross * Math.PI / 2.0); // 90-degree intervals
                double offsetX = Math.cos(angle) * 0.5;
                double offsetZ = Math.sin(angle) * 0.5;
                
                Vec3 crossPos = particlePos.add(offsetX, 0, offsetZ);
                
                // Core beam particles (bright blue)
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    crossPos.x, crossPos.y, crossPos.z, 
                    1, 0.1, 0.1, 0.1, 0.2);
                    
                // Outer glow (soul fire)
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    crossPos.x, crossPos.y, crossPos.z,
                    1, 0.15, 0.15, 0.15, 0.1);
            }
            
            // Center core beam (extra bright)
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                particlePos.x, particlePos.y, particlePos.z, 
                3, 0.05, 0.05, 0.05, 0.3);
        }
        
        // Add some dramatic lightning-like branching
        for (int branch = 0; branch < 3; branch++) {
            double branchPoint = 0.3 + (branch * 0.35); // Branch at 30%, 65%, 100%
            Vec3 branchStart = startPos.add(direction.scale(distance * branchPoint));
            
            // Random branch direction
            double branchAngle = getUser().getRandom().nextDouble() * Math.PI * 2;
            double branchLength = 2.0 + getUser().getRandom().nextDouble() * 3.0;
            Vec3 branchDir = new Vec3(
                Math.cos(branchAngle) * 0.7,
                (getUser().getRandom().nextDouble() - 0.5) * 0.5,
                Math.sin(branchAngle) * 0.7
            ).normalize();
            
            for (double bd = 0; bd < branchLength; bd += 0.5) {
                Vec3 branchPos = branchStart.add(branchDir.scale(bd));
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    branchPos.x, branchPos.y, branchPos.z, 
                    1, 0.2, 0.2, 0.2, 0.15);
            }
        }
        
        // Custom lightning burst particles for extra effect
        for (int i = 0; i < 12; i++) {
            serverLevel.sendParticles(ModParticles.LIGHTNING_BURST.get(),
                endPos.x, endPos.y, endPos.z,
                1, getUser().getId(), 0, 0, 0);
        }
    }
    
    private void createBurstEffects() {
        if (targetPosition == null) return;
        
        ServerLevel serverLevel = (ServerLevel) getUser().level();
        
        // Explosion-like particles at target with blue theme
        for (int i = 0; i < 8; i++) {
            double offsetX = (getUser().getRandom().nextDouble() - 0.5) * 3.0;
            double offsetY = (getUser().getRandom().nextDouble() - 0.5) * 3.0;
            double offsetZ = (getUser().getRandom().nextDouble() - 0.5) * 3.0;
            
            // Electric sparks
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                targetPosition.x + offsetX, targetPosition.y + offsetY, targetPosition.z + offsetZ,
                1, 0.3, 0.3, 0.3, 0.15);
                
            // Blue soul fire flames
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                targetPosition.x + offsetX * 0.7, targetPosition.y + offsetY * 0.7, targetPosition.z + offsetZ * 0.7,
                1, 0.2, 0.2, 0.2, 0.1);
        }
    }

    @Override
    public void end() {
        super.end();
        phase = 0;
        targetPosition = null;
        burstTarget = null;
        
        // No beam entity cleanup needed - pure particles!
    }

    @Override
    public boolean canCancelActiveAbility() {
        return false; // Cannot be interrupted once started
    }
}