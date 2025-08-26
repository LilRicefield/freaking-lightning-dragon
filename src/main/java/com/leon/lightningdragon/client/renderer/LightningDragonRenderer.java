package com.leon.lightningdragon.client.renderer;

import com.leon.lightningdragon.client.model.LightningDragonModel;
import com.leon.lightningdragon.client.model.enhanced.EnhancedGeoBone;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.client.Minecraft;

/**
 * - Professional head bone tracking & lightning rendering
 * - Advanced bone control system for future physics 
 * - Matrix override support for special effects
 * - Enhanced inheritance controls
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
     * Uses vanilla look control calculations + GeckoLib bone manipulation
     * This is the professional approach used by Mowzie's Mobs. THANK YOU, MR. MOWZIE. SHOUT OUT TO YOU AND YOUR TEAM.
     */
    private void applyHeadTracking(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        // NOTE: Server-side look control should be handled in the entity class
        // This method applies the calculated rotations to the bones
        
        // Get head and neck bones for split rotation (like Mowzie's approach)
        GeoBone headBone = model.getBone("head").orElse(null);
        GeoBone neckBone = model.getBone("neck").orElse(null);
        
        if (headBone == null) return;

        boolean shouldTrackHead = false;
        float headYaw = 0;
        float headPitch = 0;

        // Apply head tracking when on ground (normal behavior)
        if (!entity.isFlying()) {
            shouldTrackHead = true;
            // Get the calculated head rotations (these should come from vanilla look control)
            headYaw = Mth.wrapDegrees(entity.getYHeadRot() - entity.yBodyRot);
            headPitch = Mth.wrapDegrees(entity.getXRot());
        }
        // SPECIAL CASE: Lightning beam ability (follow rider's look)
        else if (entity.isFlying() && entity.getActiveAbility() != null && 
                 entity.getActiveAbilityType() == entity.LIGHTNING_BEAM_ABILITY && 
                 entity.getRidingPlayer() != null) {
            shouldTrackHead = true;
            
            // Use the rider's look direction for head tracking
            net.minecraft.world.entity.player.Player rider = entity.getRidingPlayer();
            headYaw = Mth.wrapDegrees(rider.getYHeadRot() - entity.yBodyRot);
            headPitch = Mth.wrapDegrees(rider.getXRot());
        }

        if (shouldTrackHead) {
            // Clamp to reasonable head movement range
            headYaw = Mth.clamp(headYaw, -75.0f, 75.0f);
            headPitch = Mth.clamp(headPitch, -60.0f, 60.0f);


            float yawRad = headYaw * ((float) Math.PI / 180F);
            float pitchRad = headPitch * ((float) Math.PI / 180F);

            if (neckBone != null) {
                headBone.updateRotation(pitchRad / 2f, yawRad / 2f, 0);
                neckBone.updateRotation(pitchRad / 2f, yawRad / 2f, 0);
            } else {
                // No neck bone, apply full rotation to head
                headBone.updateRotation(pitchRad, yawRad, 0);
            }
        } else {
            // Reset to neutral when not tracking
            resetHeadBones(model);
        }
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
        // Distance check
        assert Minecraft.getInstance().player != null;
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
            if (tailBone != null && !isDynamicBone(tailBone)) {
                float segmentFactor = (float) (i + 1) / tailSegments.length;
                float tailResponse = bankingEffect * segmentFactor;
                tailBone.updateRotation(0, tailResponse * 0.3f, 0);
            }
        }
    }
    
    @Override
    public RenderType getRenderType(LightningDragonEntity animatable, ResourceLocation texture, 
                                   @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
    
    @Override
    public void renderRecursively(PoseStack poseStack, LightningDragonEntity entity, GeoBone bone,
                                 RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                 boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                 float red, float green, float blue, float alpha) {
        
        if (bone == null) return;
        
        poseStack.pushPose();
        if (bone instanceof EnhancedGeoBone enhancedBone && enhancedBone.isForceMatrixTransform()) {
            applyMatrixTransformation(poseStack, entity, enhancedBone, partialTick);
        } else {
            applyStandardTransformation(poseStack, entity, bone, partialTick);
        }
        
        // Render bone geometry
        renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        
        // Apply render layers
        if (!isReRender) {
            applyRenderLayersForBone(poseStack, entity, bone, renderType, bufferSource, buffer,
                                   partialTick, packedLight, packedOverlay);
        }
        
        // Render child bones
        renderChildBones(poseStack, entity, bone, renderType, bufferSource, buffer, isReRender,
                        partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        
        poseStack.popPose();
    }
    
    /**
     * Applies direct world-space matrix transformations for physics bones
     */
    private void applyMatrixTransformation(PoseStack poseStack, LightningDragonEntity entity, 
                                          EnhancedGeoBone bone, float partialTick) {
        PoseStack.Pose last = poseStack.last();
        
        // Get interpolated entity transform data
        float bodyRot = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        double entityX = entity.getX();
        double entityY = entity.getY(); 
        double entityZ = entity.getZ();
        
        // Create transformation matrix
        Matrix4f matrix4f = new Matrix4f();
        matrix4f = matrix4f.translate(0, -0.01f, 0); // Slight offset
        matrix4f = matrix4f.translate((float) -entityX, (float) -entityY, (float) -entityZ);
        matrix4f = matrix4f.mul(bone.getWorldSpaceMatrix());
        matrix4f = matrix4f.rotate(com.mojang.math.Axis.YP.rotationDegrees(-180f - bodyRot));
        
        // Apply to pose stack
        last.pose().mul(matrix4f);
        last.normal().mul(bone.getWorldSpaceNormal());
        
        RenderUtils.translateAwayFromPivotPoint(poseStack, bone);
    }
    
    /**
     * Handles inheritance controls and rotation overrides
     */
    private void applyStandardTransformation(PoseStack poseStack, LightningDragonEntity entity, 
                                           GeoBone bone, float partialTick) {
        boolean rotOverride = false;
        
        if (bone instanceof EnhancedGeoBone enhancedBone) {
            rotOverride = enhancedBone.rotationOverride != null;
        }
        
        // Standard GeckoLib transformation
        RenderUtils.translateMatrixToBone(poseStack, bone);
        RenderUtils.translateToPivotPoint(poseStack, bone);
        
        // INHERITANCE SYSTEM
        if (bone instanceof EnhancedGeoBone enhancedBone) {
            if (!enhancedBone.inheritRotation && !enhancedBone.inheritTranslation) {
                // No inheritance - use identity matrix with entity base transform
                poseStack.last().pose().identity();
                poseStack.last().pose().mul(this.entityRenderTranslations);
            } else if (!enhancedBone.inheritRotation) {
                // Translation only - extract and preserve translation
                Vector4f translation = new Vector4f().mul(poseStack.last().pose());
                poseStack.last().pose().identity();
                poseStack.translate(translation.x, translation.y, translation.z);
            } else if (!enhancedBone.inheritTranslation) {
                // Rotation only - remove translation
                EnhancedGeoBone.removeMatrixTranslation(poseStack.last().pose());
                poseStack.last().pose().mul(this.entityRenderTranslations);
            }
        }
        
        // Apply rotation override if present
        if (rotOverride && bone instanceof EnhancedGeoBone enhancedBone) {
            poseStack.last().pose().mul(enhancedBone.rotationOverride);
            poseStack.last().normal().mul(new Matrix3f(enhancedBone.rotationOverride));
        } else {
            RenderUtils.rotateMatrixAroundBone(poseStack, bone);
        }
        
        RenderUtils.scaleMatrixForBone(poseStack, bone);
        
        // Update matrix tracking for physics system
        if (bone.isTrackingMatrices()) {
            updateBoneMatrixTracking(poseStack, bone);
        }
        
        RenderUtils.translateAwayFromPivotPoint(poseStack, bone);
    }
    
    /**
     * Update bone matrix tracking for physics system
     */
    private void updateBoneMatrixTracking(PoseStack poseStack, GeoBone bone) {
        Matrix4f poseState = new Matrix4f(poseStack.last().pose());
        Matrix4f localMatrix = RenderUtils.invertAndMultiplyMatrices(poseState, this.entityRenderTranslations);
        
        bone.setModelSpaceMatrix(RenderUtils.invertAndMultiplyMatrices(poseState, this.modelRenderTranslations));
        bone.setLocalSpaceMatrix(RenderUtils.translateMatrix(localMatrix, getRenderOffset(this.animatable, 1).toVector3f()));
        bone.setWorldSpaceMatrix(RenderUtils.translateMatrix(new Matrix4f(localMatrix), this.animatable.position().toVector3f()));
    }
    
    @Override
    public void renderChildBones(PoseStack poseStack, LightningDragonEntity entity, GeoBone bone,
                                RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                float red, float green, float blue, float alpha) {
        
        for (GeoBone childBone : bone.getChildBones()) {
            // Render dynamic joints even when parent is hidden
            if (!bone.isHidingChildren() || 
                (childBone instanceof EnhancedGeoBone enhancedChild && enhancedChild.isDynamicJoint())) {
                
                renderRecursively(poseStack, entity, childBone, renderType, bufferSource, buffer,
                                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            }
        }
    }
    
    /**
     * Check if bone is handled by dynamic physics system
     */
    private boolean isDynamicBone(GeoBone bone) {
        return bone instanceof EnhancedGeoBone enhancedBone && enhancedBone.isDynamicJoint();
    }
    
    @Override
    public void render(@NotNull LightningDragonEntity entity, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        // Call normal rendering first
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}