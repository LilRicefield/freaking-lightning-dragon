package com.leon.lightningdragon.client.particle;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Custom Lightning Burst Particle - creates blue electric particles that travel from dragon mouth to target
 */
public class LightningBurstParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final int dragonId;
    private final Vec3 inMouthOffset;
    private final Vec3 inMouthOrigin;
    private final Vec3 beamPosOffset;
    private final Vec3 targetPosition;

    protected LightningBurstParticle(ClientLevel level, double x, double y, double z, int dragonId, Vec3 targetPos, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.friction = 0.96F;
        this.gravity = 0;
        this.speedUpWhenYMotionIsBlocked = true;
        this.sprites = sprites;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.quadSize *= 1.5F + random.nextFloat() * 1.5F;
        this.lifetime = 8; // Longer lifetime for burst effect
        this.setSpriteFromAge(sprites);
        this.hasPhysics = false; // No physics for beam particles
        this.dragonId = dragonId;
        this.targetPosition = targetPos;
        
        // Random offset within mouth area
        this.inMouthOffset = new Vec3(
            random.nextBoolean() ? 0.6F : -0.6F, 
            random.nextFloat() * 0.6F, 
            random.nextFloat() * 0.6F - 0.1F
        );
        
        // Random offset along beam path
        this.beamPosOffset = new Vec3(
            random.nextFloat() - 0.5F, 
            random.nextFloat() - 0.5F, 
            random.nextFloat() - 0.5F
        ).scale(2.0F);
        
        Vec3 vec3 = getInMouthPos(1.0F);
        this.x = vec3.x;
        this.y = vec3.y;
        this.z = vec3.z;
        this.inMouthOrigin = vec3;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        
        // Random rotation for electric effect
        this.roll = (float) Math.toRadians(random.nextInt(4) * 90F);
        this.oRoll = roll;
        
        // Blue coloring for lightning
        this.setColor(0.3F, 0.6F, 1.0F); // Electric blue color
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        
        if (dragonId != -1 && level.getEntity(dragonId) instanceof LightningDragonEntity dragon) {
            Vec3 inMouthPos = getInMouthPos(1.0F);
            
            if (targetPosition != null) {
                Vec3 dist = targetPosition.add(beamPosOffset).subtract(inMouthPos);
                int distInTicks = Math.max(8, (int) Math.ceil(dist.length() * 0.008F));
                this.lifetime = Math.max(this.lifetime, distInTicks);
                
                float progress = Mth.clamp(this.age / (float) lifetime, 0F, 1F);
                Vec3 setPosVec = inMouthPos.add(dist.scale(progress));
                this.setPos(setPosVec.x, setPosVec.y, setPosVec.z);
                
                // Add slight random movement for electric effect
                if (random.nextFloat() < 0.3F) {
                    double randomX = (random.nextDouble() - 0.5) * 0.2;
                    double randomY = (random.nextDouble() - 0.5) * 0.2;
                    double randomZ = (random.nextDouble() - 0.5) * 0.2;
                    this.setPos(this.x + randomX, this.y + randomY, this.z + randomZ);
                }
                
                // Fade out near the end
                float fadeProgress = 1.0F - (this.age / (float) this.lifetime);
                this.alpha = fadeProgress * 0.8F;
                
            } else {
                this.remove();
            }
        } else {
            this.remove();
        }
    }

    public Vec3 getInMouthPos(float partialTick) {
        if (dragonId != -1 && level.getEntity(dragonId) instanceof LightningDragonEntity dragon) {
            Vec3 mouthPos = dragon.getCachedMouthPosition();
            if (mouthPos != null) {
                Vec3 translate = mouthPos.add(inMouthOffset).yRot((float) (Math.PI - dragon.yBodyRot * ((float) Math.PI / 180F)));
                return new Vec3(dragon.getX() + translate.x, dragon.getY() + translate.y, dragon.getZ() + translate.z);
            }
        }
        return inMouthOrigin != null ? inMouthOrigin : Vec3.ZERO;
    }

    @Override
    public int getLightColor(float partialTicks) {
        // Bright blue light
        return 240; // Maximum brightness
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT; // Lit particles for glowing effect
    }

    /**
     * Factory for creating Lightning Burst Particles
     */
    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Factory(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(@NotNull SimpleParticleType typeIn, @NotNull ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            // xSpeed = dragonId, ySpeed/zSpeed used for target position
            Vec3 targetPos = new Vec3(x, ySpeed, zSpeed);
            return new LightningBurstParticle(worldIn, x, y, z, (int) xSpeed, targetPos, spriteSet);
        }
    }
}