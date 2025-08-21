package com.leon.lightningdragon.client.renderer;

import com.leon.lightningdragon.client.model.LightningDragonModel;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;
/**
 * FIXED Lightning Dragon Renderer with proper head bone tracking
 */
@OnlyIn(Dist.CLIENT)
public class LightningDragonRenderer extends GeoEntityRenderer<LightningDragonEntity> {
    private final LightningRender lightningRender = new LightningRender();

    public LightningDragonRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new LightningDragonModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public boolean shouldRender(@NotNull LightningDragonEntity dragon, @NotNull Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(dragon, camera, camX, camY, camZ)) {
            return true;
        }

        // Also render if lightning beam is active and visible
        if (dragon.hasLightningTarget()) {
            Vec3 headPos = dragon.getMouthPosition();
            Vec3 targetPos = new Vec3(dragon.getLightningTargetX(), dragon.getLightningTargetY(), dragon.getLightningTargetZ());
            return camera.isVisible(new AABB(headPos.x, headPos.y, headPos.z, targetPos.x, targetPos.y, targetPos.z));
        }

        return false;
    }

    @Override
    public void preRender(PoseStack poseStack,
                          LightningDragonEntity entity,
                          BakedGeoModel model,
                          MultiBufferSource bufferSource,
                          VertexConsumer buffer,
                          boolean isReRender,
                          float partialTick,
                          int packedLight,
                          int packedOverlay,
                          float red, float green, float blue, float alpha) {
        // Basic setup, scale first
        float scale = 4.5f;
        poseStack.scale(scale, scale, scale);
        this.shadowRadius = 0.8f * scale;

        // Then call super.preRender
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        // Apply all animations
        applyPhysicsBasedAnimations(entity, model, partialTick);
        applyHeadTracking(entity, model, partialTick);

        // Get the head bone from the model.
        model.getBone("head").ifPresent(headBone -> {
            org.joml.Vector3d bonePosDouble = headBone.getWorldPosition();
            org.joml.Vector3f bonePosFloat = new org.joml.Vector3f(
                    (float) bonePosDouble.x,
                    (float) bonePosDouble.y,
                    (float) bonePosDouble.z
            );
            Vector4f bonePosition = new Vector4f(bonePosFloat, 1.0f);

            // Get the transformation matrix from the PoseStack
            Matrix4f transform = poseStack.last().pose();

            // Apply the transformation to get world coordinates
            bonePosition.mul(transform);

            // Calculate the head's Y-position relative to the entity's base Y-position
            float relativeHeadY = bonePosition.y() - (float)entity.getY();

            // Cache this new eye height in the entity
            entity.setCachedEyeHeight(relativeHeadY);
        });
    }

    /**
     * ICE & FIRE STYLE Bounded Scale Function
     * Maps input scale to a bounded range for consistent lightning sizing
     */
    private static float getBoundedScale(float scale) {
        float min = 0.5F;
        float max = 2.0F;
        return min + scale * (max - min);
    }

    @Override
    public void postRender(PoseStack poseStack, LightningDragonEntity entity, BakedGeoModel model,
                           MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                           float partialTick, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha) {

        super.postRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        // PROFESSIONAL ICE & FIRE LIGHTNING RENDERING
        if (entity.hasLightningTarget()) {
            renderLightning(entity, poseStack, bufferSource, partialTick);
        }
    }


    /**
     * ACTUAL HEAD BONE TRACKING like vanilla mobs
     * This manipulates the head bones directly in the model
     */
    private void applyHeadTracking(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        // Only do head tracking when on ground
        if (entity.isFlying()) {
            // Reset head to neutral when flying
            resetHeadBones(model);
            return;
        }

        // Get the head bone
        GeoBone headBone = model.getBone("head").orElse(null);
        if (headBone == null) return;

        // Calculate head rotation based on entity's look direction
        float headYaw = Mth.lerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float bodyYaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // Calculate relative head rotation (head relative to body)
        float relativeYaw = Mth.wrapDegrees(headYaw - bodyYaw);

        // Clamp to reasonable head movement range
        relativeYaw = Mth.clamp(relativeYaw, -75.0f, 75.0f);
        headPitch = Mth.clamp(headPitch, -60.0f, 60.0f);

        // Convert to radians and apply to head bone
        float yawRad = (float) Math.toRadians(-relativeYaw); // NEGATIVE to fix inversion!
        float pitchRad = (float) Math.toRadians(-headPitch); // Negative because of model orientation

        // Apply rotation to head bone
        headBone.updateRotation(pitchRad, yawRad, 0);
    }

    /**
     * Reset head bones to neutral position
     */
    private void resetHeadBones(BakedGeoModel model) {
        model.getBone("head").ifPresent(headBone -> headBone.updateRotation(0, 0, 0));
    }

    /**
     * AUTHENTIC ICE & FIRE LIGHTNING RENDERING
     * Exact implementation style from RenderLightningDragon.java with professional bolt generation
     */
    private void renderLightning(LightningDragonEntity entity, PoseStack poseStack,
                                         MultiBufferSource bufferSource, float partialTick) {
        // Distance check like Ice & Fire
        double dist = entity.distanceTo(net.minecraft.client.Minecraft.getInstance().player);
        if (dist > Math.max(256, net.minecraft.client.Minecraft.getInstance().options.renderDistance().get() * 16F)) {
            return;
        }
        
        Vec3 headPos = entity.getMouthPosition();
        Vec3 targetPos = new Vec3(entity.getLightningTargetX(), entity.getLightningTargetY(), entity.getLightningTargetZ());
        
        poseStack.pushPose();
        poseStack.translate(-entity.getX(), -entity.getY(), -entity.getZ());
        
        // PROFESSIONAL ICE & FIRE BOLT GENERATION
        float energyScale = 0.4F * entity.getScale();
        LightningBolt bolt = new LightningBolt(
                LightningBolt.BoltRenderInfo.ELECTRICITY,
                headPos, 
                targetPos, 
                15
        ).size(0.05F * getBoundedScale(energyScale))
                .lifespan(4)
                .spawn(LightningBolt.SpawnFunction.NO_DELAY);
        
        lightningRender.update(entity, bolt, partialTick);
        lightningRender.render(partialTick, poseStack, bufferSource);
        
        poseStack.popPose();
    }

    /**
     * Isaac Newton lmao physics yay i love me some gravity
     */
    private void applyPhysicsBasedAnimations(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        // Banking tilt - this is real-time flight physics, keep it procedural
        if (entity.isFlying() && !entity.isLanding()) {
            applyBankingTilt(entity, model, partialTick);
            applyBankingTailResponse(entity, model, partialTick);
        }
    }

    /**
     * Banking tilt based on real-time flight physics
     */
    private void applyBankingTilt(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        float currentBanking = entity.getBanking();
        float prevBanking = entity.getPrevBanking();
        float interpolatedBanking = Mth.lerp(partialTick, prevBanking, currentBanking);
        float bankingRad = (float) Math.toRadians(interpolatedBanking);

        model.getBone("body").ifPresent(body -> body.updateRotation(0, 0, bankingRad));
    }

    /**
     * Subtle tail response to banking (idek if this works or not)
     */
    private void applyBankingTailResponse(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        Vec3 velocity = entity.getDeltaMovement();
        float speed = (float) velocity.length();

        if (speed < 0.01f) return;

        float banking = Mth.lerp(partialTick, entity.getPrevBanking(), entity.getBanking());
        float bankingEffect = (float) Math.toRadians(banking * 0.1f);

        // Apply banking response to tail segments
        String[] tailSegments = {"tail3", "tail4", "tail5", "tail6", "tail7", "tail8", "tail9", "tail10"};

        for (int i = 0; i < tailSegments.length; i++) {
            GeoBone tailBone = model.getBone(tailSegments[i]).orElse(null);
            if (tailBone != null) {
                float segmentFactor = (float) (i + 1) / tailSegments.length;
                float tailResponse = bankingEffect * segmentFactor;
                tailBone.updateRotation(0, tailResponse * 0.3f, 0);
            }
        }
    }
}