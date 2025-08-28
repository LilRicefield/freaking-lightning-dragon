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

        // Apply head tracking only - banking moved to postRender to avoid GeckoLib conflicts
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

        // Apply banking AFTER GeckoLib animations to avoid conflicts
        applyPhysicsBasedAnimations(entity, model, partialTick);
        
        // PROFESSIONAL ICE & FIRE LIGHTNING RENDERING (disabled - using EntityLightningBeam now)
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
        // TODO: SPECIAL CASE: Lightning beam ability (follow rider's look) - integrate with new Dragon ability system
        else if (false) {
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
     * Isaac Newton haha physics yay i love me some gravity
     */
    private void applyPhysicsBasedAnimations(LightningDragonEntity entity, BakedGeoModel model, float partialTick) {
        // Banking is now handled in the model's setCustomAnimations() method (proper GeckoLib approach)
        // Only physics animations that can't be done in model remain here
    }

    // Banking methods removed - now handled in LightningDragonModel.setCustomAnimations()
    
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
        // Track beam_origin bone position for lightning beam attacks (like Mowzie's headPos system)
        this.updateBeamOriginPosition(entity, partialTick);
        
        // Call normal rendering first
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
    
    /**
     * Update the beam_origin bone world position for lightning beam attacks
     * Based on Mowzie's sun_render bone tracking system for solar beams
     */
    private void updateBeamOriginPosition(LightningDragonEntity entity, float partialTick) {
        BakedGeoModel model = this.model.getBakedModel(this.model.getModelResource(entity));
        if (model == null) return;
        
        // Find the beam_origin bone (try bone first, then locator as fallback)
        GeoBone beamBone = model.getBone("beam_origin").orElse(null);
        if (beamBone == null) {
            // Try to find it as a locator if bone doesn't exist
            return;
        }
        
        // Get the world position of the beam_origin bone
        Vec3 worldPos = this.getBoneWorldPosition(entity, beamBone, partialTick);
        if (worldPos != null) {
            // Update the tracked position array (like Mowzie's headPos[0])
            entity.beamOriginPos[0] = worldPos;
        }
    }
    
    /**
     * Calculate world position of a specific bone
     * Based on GeckoLib's matrix transformation system
     */
    private Vec3 getBoneWorldPosition(LightningDragonEntity entity, GeoBone bone, float partialTick) {
        try {
            // Get the bone's world transformation matrix
            Matrix4f worldMatrix = bone.getWorldSpaceMatrix().mul(bone.getModelSpaceMatrix());
            
            // Extract translation from the matrix
            Vector4f pos = new Vector4f(0, 0, 0, 1);
            pos.mul(worldMatrix);
            
            // Convert to world coordinates
            double entityX = Mth.lerp(partialTick, entity.xo, entity.getX());
            double entityY = Mth.lerp(partialTick, entity.yo, entity.getY());
            double entityZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
            
            return new Vec3(
                entityX + pos.x / 16.0f, // GeckoLib uses 16x scale
                entityY + pos.y / 16.0f,
                entityZ + pos.z / 16.0f
            );
        } catch (Exception e) {
            return null;
        }
    }
}