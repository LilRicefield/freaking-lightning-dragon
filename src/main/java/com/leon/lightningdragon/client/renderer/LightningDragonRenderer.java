package com.leon.lightningdragon.client.renderer;

import com.leon.lightningdragon.client.model.LightningDragonModel;
import com.leon.lightningdragon.server.entity.LightningDragonEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * ONLY handles: scaling, bone position tracking, and eye height caching
 * ALL animations are in LightningDragonModel.setCustomAnimations()
 */
@OnlyIn(Dist.CLIENT)
public class LightningDragonRenderer extends GeoEntityRenderer<LightningDragonEntity> {
    public LightningDragonRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new LightningDragonModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public boolean shouldRender(@NotNull LightningDragonEntity dragon, @NotNull Frustum camera, double camX, double camY, double camZ) {
        return super.shouldRender(dragon, camera, camX, camY, camZ);
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
        
        // Scale the dragon
        float scale = 4.5f;
        poseStack.scale(scale, scale, scale);
        this.shadowRadius = 0.8f * scale;

        // Call super.preRender
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        // Cache eye height based on head bone position (like Mowzie's headPos tracking)
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

    @Override
    public void render(@NotNull LightningDragonEntity entity, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        
        // Call normal rendering first
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        
        // Track beam origin position for lightning attacks (like Mowzie's headPos system)
        // This is done in render() to get the final world position after all transformations
        model.getBone("beam_origin").ifPresent(beamBone -> {
            org.joml.Vector3d worldPos = beamBone.getWorldPosition();
            entity.beamOriginPos = new Vec3(worldPos.x, worldPos.y, worldPos.z);
        });
    }
    
    @Override
    public RenderType getRenderType(LightningDragonEntity animatable, ResourceLocation texture, 
                                   @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}