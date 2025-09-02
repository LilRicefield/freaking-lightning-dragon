package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import software.bernie.geckolib.model.GeoModel;
import com.leon.lightningdragon.server.entity.dragons.LightningDragonEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;
/**
 * Lightning Dragon model with enhanced bone system and procedural animations
 * Extends DragonGeoModel for advanced dragon-specific features
 */
public class LightningDragonModel extends GeoModel<LightningDragonEntity> {
    private static final ResourceLocation MODEL =
            LightningDragonMod.rl("geo/lightning_dragon.geo.json");
    private static final ResourceLocation TEXTURE =
            LightningDragonMod.rl("textures/lightning_dragon.png");
    private static final ResourceLocation ANIM =
            LightningDragonMod.rl("animations/lightning_dragon.animation.json");

    @Override
    public ResourceLocation getModelResource(LightningDragonEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LightningDragonEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LightningDragonEntity entity) {
        return ANIM;
    }

    /**
     * This is where head tracking happen
     */
    @Override
    public void applyMolangQueries(LightningDragonEntity animatable, double animTime) {
        // Only reset bones if not beaming to avoid conflicts
        if (!animatable.isBeaming()) {
            getBone("head").ifPresent(this::resetBoneToSnapshot);
            getBone("neck4").ifPresent(this::resetBoneToSnapshot);
        }
        
        // Apply standard Molang queries and animations
        super.applyMolangQueries(animatable, animTime);
    }

    @Override
    public void setCustomAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        
        // Mowzie-style head tracking: add rotation after animations are applied
        if (entity.isAlive()) {
            var headOpt = getBone("head");
            var neck4Opt = getBone("neck4");
            
            if (headOpt.isPresent()) {
                var head = headOpt.get();
                
                // If beaming, track beam target instead of player look direction
                if (entity.isBeaming()) {
                    var beamEnd = entity.getClientBeamEndPosition(animationState.getPartialTick());
                    if (beamEnd != null) {
                        // Reset bones first to avoid accumulation
                        resetBoneToSnapshot(head);
                        neck4Opt.ifPresent(this::resetBoneToSnapshot);
                        
                        // Get head world position
                        var headWorldPos = entity.computeHeadMouthOrigin(animationState.getPartialTick());
                        if (headWorldPos != null) {
                            // Calculate direction to beam target
                            var direction = beamEnd.subtract(headWorldPos).normalize();
                            
                            // Get dragon's body rotation
                            float bodyYaw = entity.getYRot() * Mth.DEG_TO_RAD;
                            
                            // Transform direction into dragon's local coordinate system
                            double cosBodyYaw = Math.cos(bodyYaw);
                            double sinBodyYaw = Math.sin(bodyYaw);
                            
                            // Rotate direction vector by inverse of body yaw to get local direction
                            double localX = direction.x * cosBodyYaw + direction.z * sinBodyYaw;
                            double localZ = -direction.x * sinBodyYaw + direction.z * cosBodyYaw;
                            double localY = direction.y;
                            
                            // Calculate local yaw and pitch from transformed direction
                            float localYaw = (float) Math.atan2(localX, localZ);
                            float localPitch = (float) Math.asin(localY);
                            
                            // Clamp to reasonable ranges
                            localYaw = Mth.clamp(localYaw, -1.5f, 1.5f);
                            localPitch = Mth.clamp(localPitch, -1.2f, 1.2f);
                            
                            // Apply beam tracking rotation with reduced influence
                            head.setRotX(head.getRotX() + localPitch * 0.3f);
                            head.setRotY(head.getRotY() + localYaw * 0.3f);
                            
                            // Apply to neck4 with less influence
                            if (neck4Opt.isPresent()) {
                                var neck4 = neck4Opt.get();
                                neck4.setRotX(neck4.getRotX() + localPitch * 0.15f);
                                neck4.setRotY(neck4.getRotY() + localYaw * 0.15f);
                            }
                        }
                    }
                } else {
                    // Normal player look tracking when not beaming
                    EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
                    float headYaw = Mth.wrapDegrees(entityData.netHeadYaw());
                    float headPitch = Mth.wrapDegrees(entityData.headPitch());
                    
                    // Add rotation with reduced influence, like Mowzie's approach
                    head.setRotX(head.getRotX() + headPitch * ((float) Math.PI / 180F) / 2f);
                    head.setRotY(head.getRotY() + headYaw * ((float) Math.PI / 180F) / 2f);
                    
                    // Also apply to neck4 with even less influence
                    if (neck4Opt.isPresent()) {
                        var neck4 = neck4Opt.get();
                        neck4.setRotX(neck4.getRotX() + headPitch * ((float) Math.PI / 180F) / 4f);
                        neck4.setRotY(neck4.getRotY() + headYaw * ((float) Math.PI / 180F) / 4f);
                    }
                }
            }
        }
    }

    private void resetBoneToSnapshot(software.bernie.geckolib.cache.object.GeoBone bone) {
        var initialSnapshot = bone.getInitialSnapshot();
        
        bone.setRotX(initialSnapshot.getRotX());
        bone.setRotY(initialSnapshot.getRotY());
        bone.setRotZ(initialSnapshot.getRotZ());
        
        bone.setPosX(initialSnapshot.getOffsetX());
        bone.setPosY(initialSnapshot.getOffsetY());
        bone.setPosZ(initialSnapshot.getOffsetZ());
        
        bone.setScaleX(initialSnapshot.getScaleX());
        bone.setScaleY(initialSnapshot.getScaleY());
        bone.setScaleZ(initialSnapshot.getScaleZ());
    }
}