package com.leon.lightningdragon.entity.handler;

import com.leon.lightningdragon.ai.abilities.combat.EnhancedLightningBeamAbility;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import com.leon.lightningdragon.network.MessageDragonSyncFire;
import com.leon.lightningdragon.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Handles all lightning/fire mechanics for the Lightning Dragon
 * Including the Ice & Fire style fireStopTicks system and lightning effects
 */
public class DragonLightningSystem {
    private final LightningDragonEntity dragon;
    
    // Breathing fire state for continuous lightning abilities - Ice & Fire style
    private boolean breathingFire = false;
    private int fireStopTicks = 0; // Ice & Fire style hold-to-fire mechanism
    
    public DragonLightningSystem(LightningDragonEntity dragon) {
        this.dragon = dragon;
    }
    
    // ===== BREATHING FIRE STATE =====
    
    public boolean isBreathingFire() { 
        return breathingFire; 
    }
    
    public void setBreathingFire(boolean breathing) { 
        this.breathingFire = breathing; 
        if (breathing) {
            // Start continuous lightning when breathing fire
            riderShootFire();
        }
    }
    
    public int getFireStopTicks() { 
        return fireStopTicks; 
    }
    
    public void setFireStopTicks(int ticks) { 
        this.fireStopTicks = ticks; 
    }
    
    // ===== ICE & FIRE STYLE FIRESTOP LOGIC =====
    
    /**
     * Ice & Fire style fireStopTicks mechanism for hold-to-fire Lightning Beam
     * Based on Ice & Fire Dragons mod implementation - this is the MAIN countdown logic
     */
    public void updateLightningBeamFireStopLogic() {
        Player ridingPlayer = dragon.getRidingPlayer();
        if (ridingPlayer == null) {
            setBreathingFire(false);
            return;
        }

        // CRITICAL: Handle fireStopTicks countdown when breathing fire (like Ice & Fire)
        if (isBreathingFire() && fireStopTicks > 0) {
            fireStopTicks--;
        }
        
        // Ice & Fire stopping condition - when fireStopTicks reaches 0 during breath fire
        if (isBreathingFire() && fireStopTicks <= 0) {
            setBreathingFire(false);
            // Stop the Lightning Beam ability if it's currently active
            if (dragon.combatManager.getActiveAbility() instanceof EnhancedLightningBeamAbility) {
                dragon.combatManager.forceEndActiveAbility();
            }
            fireStopTicks = 0; // Ensure it doesn't go negative
        }
        
        // Clear lightning target when not breathing fire (like Ice & Fire)
        if (!isBreathingFire()) {
            dragon.setHasLightningTarget(false);
        }
    }
    
    // ===== LIGHTNING EFFECTS SYSTEM =====
    
    /**
     * Fire/Lightning synchronization method - handles both server->client and client->server fire sync
     * syncType 0: Normal lightning effect
     * syncType 1: Server->Client sync
     * syncType 2: Client->Server sync
     */
    public void stimulateFire(double burnX, double burnY, double burnZ, int syncType) {
        Level level = dragon.level();
        
        if (syncType == 1 && !level.isClientSide) {
            // Server->Client: Send fire sync to all clients within range
            MessageDragonSyncFire message = new MessageDragonSyncFire(dragon.getId(), burnX, burnY, burnZ, syncType);
            NetworkHandler.INSTANCE.send(PacketDistributor.NEAR.with(() -> 
                new PacketDistributor.TargetPoint(burnX, burnY, burnZ, 64.0, dragon.level().dimension())), message);
        } else if (syncType == 2 && level.isClientSide) {
            // Client->Server: Send fire sync to server
            Player rider = dragon.getRidingPlayer();
            if (rider instanceof ServerPlayer) {
                MessageDragonSyncFire message = new MessageDragonSyncFire(dragon.getId(), burnX, burnY, burnZ, syncType);
                NetworkHandler.INSTANCE.sendToServer(message);
            }
        }
        
        // Handle lightning effects based on position
        if (level.isClientSide) {
            // Client-side visual effects
            createLightningEffects(burnX, burnY, burnZ);
        } else {
            // Server-side block destruction and damage
            applyLightningDamage(burnX, burnY, burnZ);
        }
    }
    
    /**
     * Ray tracing for rider targeting - determines where the rider is looking
     */
    public HitResult rayTraceRider(Player rider, double maxDistance, float partialTicks) {
        if (rider == null) return null;
        
        // Get rider's eye position and look direction
        Vec3 eyePos = rider.getEyePosition(partialTicks);
        Vec3 lookVec = rider.getViewVector(partialTicks);
        Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));
        
        // Perform block and entity ray trace
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
            eyePos, endPos,
            net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            rider
        );
        
        return dragon.level().clip(context);
    }
    
    /**
     * Rider fire ability - based on Ice & Fire implementation
     */
    public void riderShootFire() {
        Player rider = dragon.getRidingPlayer();
        if (rider == null || dragon.level().isClientSide) return;
        
        // Random lightning charge (20% chance) vs lightning stream (80% chance)
        if (dragon.getRandom().nextInt(5) == 0) {
            // Create lightning charge projectile (20% chance)
            Vec3 lookVec = rider.getViewVector(1.0F);
            
            // For now, just create lightning at target location
            Vec3 targetPos = dragon.getEyePosition().add(lookVec.scale(15.0));
            stimulateFire(targetPos.x, targetPos.y, targetPos.z, 1);
        } else {
            // Lightning stream (80% chance) - use ray tracing
            HitResult hitResult = rayTraceRider(rider, 10.0, 1.0F);
            if (hitResult != null) {
                Vec3 hitPos = hitResult.getLocation();
                stimulateFire(hitPos.x, hitPos.y, hitPos.z, 1);
            }
        }
    }
    
    /**
     * Create client-side lightning visual effects
     */
    private void createLightningEffects(double x, double y, double z) {
        Level level = dragon.level();
        if (!level.isClientSide) return;
        
        // Create lightning particles and effects
        for (int i = 0; i < 3; i++) {
            level.addParticle(
                net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                x + (dragon.getRandom().nextDouble() - 0.5) * 2.0,
                y + (dragon.getRandom().nextDouble() - 0.5) * 2.0,
                z + (dragon.getRandom().nextDouble() - 0.5) * 2.0,
                (dragon.getRandom().nextDouble() - 0.5) * 0.1,
                (dragon.getRandom().nextDouble() - 0.5) * 0.1,
                (dragon.getRandom().nextDouble() - 0.5) * 0.1
            );
        }
        
        // Play lightning sound
        level.playLocalSound(x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, 
            SoundSource.HOSTILE, 0.5F, 1.0F + dragon.getRandom().nextFloat() * 0.2F, false);
    }
    
    /**
     * Apply server-side lightning damage and block effects
     */
    private void applyLightningDamage(double x, double y, double z) {
        Level level = dragon.level();
        if (level.isClientSide) return;
        
        // Damage entities in the area
        AABB damageArea = new AABB(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, damageArea);
        
        for (LivingEntity entity : entities) {
            if (entity != dragon && entity != dragon.getRidingPlayer()) {
                entity.hurt(dragon.damageSources().lightningBolt(), 8.0F);
                entity.setSecondsOnFire(3);
            }
        }
        
        // Optional: Block destruction (implement if needed)
        // destroyBlocks(new BlockPos((int)x, (int)y, (int)z));
    }
}