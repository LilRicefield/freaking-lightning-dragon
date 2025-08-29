package com.leon.lightningdragon.client.model.enhanced;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;

import javax.annotation.Nullable;

/**
 * Enhanced GeoBone with Bob Mowzie's advanced features for lightning dragon
 * Includes dynamic physics, matrix overrides, and enhanced bone control
 */
public class EnhancedGeoBone extends GeoBone {
    
    // Advanced transformation control
    public Matrix4f rotationOverride;
    public boolean inheritRotation = true;
    public boolean inheritTranslation = true;
    protected boolean forceMatrixTransform = false;
    
    // Dynamic physics system
    private boolean isDynamicJoint = false;
    
    public EnhancedGeoBone(@Nullable GeoBone parent, String name, Boolean mirror, @Nullable Double inflate, @Nullable Boolean dontRender, @Nullable Boolean reset) {
        super(parent, name, mirror, inflate, dontRender, reset);
        rotationOverride = null;
    }
    
    /**
     * Create EnhancedGeoBone from regular GeoBone for physics chains
     */
    public EnhancedGeoBone(GeoBone sourceBone) {
        super(null, sourceBone.getName(), sourceBone.getMirror(), sourceBone.getInflate(), sourceBone.shouldNeverRender(), sourceBone.getReset());
        
        // Copy transformation data using available methods
        this.setPosX(sourceBone.getPosX());
        this.setPosY(sourceBone.getPosY());
        this.setPosZ(sourceBone.getPosZ());
        this.setRotX(sourceBone.getRotX());
        this.setRotY(sourceBone.getRotY());
        this.setRotZ(sourceBone.getRotZ());
        this.setPivotX(sourceBone.getPivotX());
        this.setPivotY(sourceBone.getPivotY());
        this.setPivotZ(sourceBone.getPivotZ());
        this.setScaleX(sourceBone.getScaleX());
        this.setScaleY(sourceBone.getScaleY());
        this.setScaleZ(sourceBone.getScaleZ());
        
        // Copy geometry
        this.getCubes().addAll(sourceBone.getCubes());
        this.saveInitialSnapshot();
        this.getChildBones().addAll(sourceBone.getChildBones());
        
        // Enhanced features initialization
        this.forceMatrixTransform = false;
        this.rotationOverride = null;
        this.isDynamicJoint = false;
    }
    
    /**
     * Create dynamic copy of existing bone for physics chains
     */
    public EnhancedGeoBone(EnhancedGeoBone sourceBone) {
        super(null, sourceBone.getName() + "_dynamic", sourceBone.getMirror(), sourceBone.getInflate(), sourceBone.shouldNeverRender(), sourceBone.getReset());
        
        // Copy all transformation data
        this.setPos(sourceBone.getPos());
        this.setRot(sourceBone.getRot());
        this.setPivotX(sourceBone.getPivotX());
        this.setPivotY(sourceBone.getPivotY());
        this.setPivotZ(sourceBone.getPivotZ());
        this.setScale(sourceBone.getScale());
        
        // Copy geometry
        this.getCubes().addAll(sourceBone.getCubes());
        this.saveInitialSnapshot();
        this.getChildBones().addAll(sourceBone.getChildBones());
        
        // Mark as dynamic
        this.isDynamicJoint = true;
    }
    
    public EnhancedGeoBone getParent() {
        GeoBone parent = super.getParent();
        if (parent instanceof EnhancedGeoBone enhancedParent) {
            return enhancedParent;
        }
        return null; // Parent is not an EnhancedGeoBone or is null
    }
    
    // ===== ENHANCED POSITION UTILITIES =====
    
    public void addPos(Vec3 vec) {
        addPos((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void addPos(float x, float y, float z) {
        setPosX(getPosX() + x);
        setPosY(getPosY() + y);
        setPosZ(getPosZ() + z);
    }
    
    public void setPos(Vec3 vec) {
        setPos((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void setPos(float x, float y, float z) {
        setPosX(x);
        setPosY(y);
        setPosZ(z);
    }
    
    public Vec3 getPos() {
        return new Vec3(getPosX(), getPosY(), getPosZ());
    }
    
    // ===== ENHANCED ROTATION UTILITIES =====
    
    public void addRot(Vec3 vec) {
        addRot((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void addRot(float x, float y, float z) {
        setRotX(getRotX() + x);
        setRotY(getRotY() + y);
        setRotZ(getRotZ() + z);
    }
    
    public void setRot(Vector3d vec) {
        setRot((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void setRot(Vec3 vec) {
        setRot((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void setRot(float x, float y, float z) {
        setRotX(x);
        setRotY(y);
        setRotZ(z);
    }
    
    public Vector3d getRot() {
        return new Vector3d(getRotX(), getRotY(), getRotZ());
    }
    
    // ===== MOWZIE-STYLE ADDITIVE ROTATION METHODS =====
    
    /**
     * Add to X rotation (pitch) - works with bone reset system like Mowzie's approach
     */
    public void addRotX(float rotX) {
        setRotX(getRotX() + rotX);
    }
    
    /**
     * Add to Y rotation (yaw) - works with bone reset system like Mowzie's approach  
     */
    public void addRotY(float rotY) {
        setRotY(getRotY() + rotY);
    }
    
    /**
     * Add to Z rotation (roll) - works with bone reset system like Mowzie's approach
     */
    public void addRotZ(float rotZ) {
        setRotZ(getRotZ() + rotZ);
    }
    
    // ===== ENHANCED SCALE UTILITIES =====
    
    public void multiplyScale(Vec3 vec) {
        multiplyScale((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void multiplyScale(float x, float y, float z) {
        setScaleX(getScaleX() * x);
        setScaleY(getScaleY() * y);
        setScaleZ(getScaleZ() * z);
    }
    
    public void setScale(Vec3 vec) {
        setScale((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void setScale(Vector3d vec) {
        setScale((float) vec.x(), (float) vec.y(), (float) vec.z());
    }
    
    public void setScale(float x, float y, float z) {
        setScaleX(x);
        setScaleY(y);
        setScaleZ(z);
    }
    
    public void setScale(float scale) {
        setScale(scale, scale, scale);
    }
    
    public Vector3d getScale() {
        return new Vector3d(getScaleX(), getScaleY(), getScaleZ());
    }
    
    // ===== ADVANCED BONE MANIPULATION =====
    
    /**
     * Copy rotation offset from another bone (useful for bone chains)
     */
    public void addRotationOffsetFromBone(EnhancedGeoBone source) {
        setRotX(getRotX() + source.getRotX() - source.getInitialSnapshot().getRotX());
        setRotY(getRotY() + source.getRotY() - source.getInitialSnapshot().getRotY());
        setRotZ(getRotZ() + source.getRotZ() - source.getInitialSnapshot().getRotZ());
    }
    
    /**
     * Force this bone to use matrix transformation instead of regular GeckoLib transforms
     */
    public void setForceMatrixTransform(boolean forceMatrixTransform) {
        this.forceMatrixTransform = forceMatrixTransform;
    }
    
    public boolean isForceMatrixTransform() {
        return forceMatrixTransform;
    }
    
    /**
     * Get model rotation matrix without translation
     */
    public Matrix4f getModelRotationMat() {
        Matrix4f matrix = new Matrix4f(getModelSpaceMatrix());
        removeMatrixTranslation(matrix);
        return matrix;
    }
    
    /**
     * Remove translation from matrix (keep only rotation/scale)
     */
    public static void removeMatrixTranslation(Matrix4f matrix) {
        matrix.m30(0);
        matrix.m31(0);
        matrix.m32(0);
    }
    
    /**
     * Override the bone's transformation matrix directly
     */
    public void setModelXformOverride(Matrix4f mat) {
        rotationOverride = mat;
    }
    
    /**
     * Set bone position in world coordinates relative to entity
     */
    public void setWorldPos(Entity entity, Vec3 worldPos, float delta) {
        PoseStack matrixStack = new PoseStack();
        
        // Entity interpolated position
        float dx = (float) (entity.xOld + (entity.getX() - entity.xOld) * delta);
        float dy = (float) (entity.yOld + (entity.getY() - entity.yOld) * delta);
        float dz = (float) (entity.zOld + (entity.getZ() - entity.zOld) * delta);
        matrixStack.translate(dx, dy, dz);
        
        // Entity rotation
        float dYaw = Mth.rotLerp(delta, entity.yRotO, entity.getYRot());
        matrixStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-dYaw + 180));
        
        // Standard GeckoLib coordinate conversion
        matrixStack.scale(-1, -1, 1);
        matrixStack.translate(0, -1.5f, 0);
        
        // Get inverse transform
        PoseStack.Pose matrixEntry = matrixStack.last();
        Matrix4f matrix4f = matrixEntry.pose();
        matrix4f.invert();
        
        // Transform world position to bone space
        Vector4f vec = new Vector4f((float) worldPos.x(), (float) worldPos.y(), (float) worldPos.z(), 1);
        vec.mul(matrix4f);
        
        // Set bone position (scale by 16 for GeckoLib units)
        setPosX(vec.x() * 16);
        setPosY(vec.y() * 16);
        setPosZ(vec.z() * 16);
    }
    
    // ===== DYNAMIC JOINT SYSTEM =====
    
    public void setDynamicJoint(boolean dynamicJoint) {
        isDynamicJoint = dynamicJoint;
    }
    
    public boolean isDynamicJoint() {
        return isDynamicJoint;
    }
    
    // ===== PHYSICS HELPERS =====
    
    /**
     * Apply spring physics to bone position
     */
    public void applySpringForce(Vec3 targetPos, float springStiffness, float damping, float deltaTime) {
        Vec3 currentPos = getPos();
        Vec3 force = targetPos.subtract(currentPos).scale(springStiffness);
        
        // Simple spring physics integration
        Vec3 velocity = force.scale(deltaTime);
        Vec3 dampedVelocity = velocity.scale(1.0 - damping);
        Vec3 newPos = currentPos.add(dampedVelocity.scale(deltaTime));
        
        setPos(newPos);
    }
    
    /**
     * Smoothly interpolate bone rotation towards target
     */
    public void smoothRotateTowards(Vector3d targetRot, float speed, float deltaTime) {
        Vector3d currentRot = getRot();
        
        float rotX = (float) Mth.approachDegrees((float)Math.toDegrees(currentRot.x), (float)Math.toDegrees(targetRot.x), speed * deltaTime);
        float rotY = (float) Mth.approachDegrees((float)Math.toDegrees(currentRot.y), (float)Math.toDegrees(targetRot.y), speed * deltaTime);
        float rotZ = (float) Mth.approachDegrees((float)Math.toDegrees(currentRot.z), (float)Math.toDegrees(targetRot.z), speed * deltaTime);
        
        setRot((float)Math.toRadians(rotX), (float)Math.toRadians(rotY), (float)Math.toRadians(rotZ));
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Reset bone to its initial snapshot values
     */
    public void resetToInitialSnapshot() {
        var snapshot = getInitialSnapshot();
        setRotX(snapshot.getRotX());
        setRotY(snapshot.getRotY());
        setRotZ(snapshot.getRotZ());
        setPosX(snapshot.getOffsetX());
        setPosY(snapshot.getOffsetY());
        setPosZ(snapshot.getOffsetZ());
        setScaleX(snapshot.getScaleX());
        setScaleY(snapshot.getScaleY());
        setScaleZ(snapshot.getScaleZ());
    }
    
    /**
     * Check if this bone is a tail segment
     */
    public boolean isTailSegment() {
        return getName().toLowerCase().contains("tail");
    }
    
    /**
     * Check if this bone is a wing component  
     */
    public boolean isWingBone() {
        String name = getName().toLowerCase();
        return name.contains("wing") || name.contains("membrane");
    }
    
    /**
     * Get the segment number for chain-based bones (tail1 -> 1, wing2 -> 2, etc)
     */
    public int getSegmentNumber() {
        String name = getName().toLowerCase();
        for (int i = 1; i <= 20; i++) {
            if (name.contains(String.valueOf(i))) {
                return i;
            }
        }
        return 0;
    }
}