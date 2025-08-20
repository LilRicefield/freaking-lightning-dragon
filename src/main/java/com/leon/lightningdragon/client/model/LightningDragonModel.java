package com.leon.lightningdragon.client.model;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.Optional;

public class LightningDragonModel extends GeoModel<LightningDragonEntity> {
    private static final ResourceLocation MODEL =
            LightningDragonMod.rl("geo/lightning_dragon.geo.json");
    private static final ResourceLocation TEXTURE =
            LightningDragonMod.rl("textures/lightning_dragon.png");
    private static final ResourceLocation ANIM =
            LightningDragonMod.rl("animations/lightning_dragon.animation.json");


    @Override public ResourceLocation getModelResource(LightningDragonEntity a) { return MODEL; }
    @Override public ResourceLocation getTextureResource(LightningDragonEntity a) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(LightningDragonEntity a) { return ANIM; }

    @Override
    public void setCustomAnimations(LightningDragonEntity dragon, long instanceId, AnimationState<LightningDragonEntity> animationState) {
        super.setCustomAnimations(dragon, instanceId, animationState);

        // Read the controller value (0.0 = walk, 1.0 = run)
        float runBlend = getControllerValue();
        float walkBlend = 1.0f - runBlend;

        if (dragon.tickCount % 20 == 0) {System.out.println("Walk blend: " + walkBlend + ", Run blend: " + runBlend);} // DEBUG

        if (!dragon.isFlying() && animationState.isMoving()) {
            applySpeedBlending(runBlend, animationState);
        }
    }
    private void applySpeedBlending(float runBlend, AnimationState<LightningDragonEntity> animationState) {
        float limbSwing = animationState.getLimbSwing();
        float limbSwingAmount = animationState.getLimbSwingAmount();

        // Scale leg movement based on speed
        float legSpeedMultiplier = 1.0f + (runBlend * 0.8f); // 1.0x walk -> 1.8x run

        // Apply to leg bones (adjust bone names to match your model)
        Optional<GeoBone> leftLeg = getBone("leftleg");
        Optional<GeoBone> rightLeg = getBone("rightleg");

        if (leftLeg.isPresent() && rightLeg.isPresent()) {
            float legRotation = (float) Math.sin(limbSwing * legSpeedMultiplier) * limbSwingAmount * runBlend * 0.5f;

            leftLeg.get().setRotX(leftLeg.get().getRotX() + legRotation);
            rightLeg.get().setRotX(rightLeg.get().getRotX() - legRotation);
        }
    }

    private float getControllerValue() {
        Optional<GeoBone> bone = getBone("walkRunController");
        return bone.map(GeoBone::getPosX).orElse(0.0f);
    }
}