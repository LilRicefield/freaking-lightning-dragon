package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import software.bernie.geckolib.model.GeoModel;
import com.leon.lightningdragon.server.entity.LightningDragonEntity;
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
    public void setCustomAnimations(LightningDragonEntity entity, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        // Disable code-driven head look; authored animations control head/neck entirely
    }

    // Safe, minimal head-look: affect head only with tight clamps and state-based weighting.
    private void applySafeHeadLook(LightningDragonEntity entity, AnimationState<LightningDragonEntity> animationState) {
        if (!entity.isAlive()) return;
        var headOpt = getBone("head");
        if (headOpt.isEmpty()) return;

        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float yawDeg = Mth.wrapDegrees(entityData.netHeadYaw());
        float pitchDeg = Mth.wrapDegrees(entityData.headPitch());

        // Tight clamps to avoid fighting authored animations
        float maxYaw = 20f;
        float maxPitch = 12f;
        // Reduce influence in certain states
        float weight = 1.0f;
        if (entity.isFlying()) weight *= 0.4f;
        if (entity.isOrderedToSit()) weight *= 0.5f;

        float yawRad = (float) Math.toRadians(Mth.clamp(yawDeg, -maxYaw, maxYaw)) * weight;
        float pitchRad = (float) Math.toRadians(Mth.clamp(pitchDeg, -maxPitch, maxPitch)) * weight;

        var head = headOpt.get();
        head.setRotY(head.getRotY() + yawRad);
        head.setRotX(head.getRotX() + pitchRad);
    }
    /**
     * Handles smooth transitions between walking and running states
     * Read controller bone values
     */
}
