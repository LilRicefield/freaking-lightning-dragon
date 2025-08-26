package com.leon.lightningdragon.client.model.enhanced;

import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Dynamic Physics Chain System for Lightning Dragon
 * Provides realistic tail/wing physics using spring-damper dynamics
 * Based on Bob Mowzie's GeckoDynamicChain but optimized for dragons
 */
public class DragonDynamicChain {
    
    // Physics state arrays
    public Vec3[] positions;           // Current positions
    public Vec3[] previousPositions;   // Previous frame positions  
    public Vec3[] velocities;          // Current velocities
    public Vec3[] accelerations;       // Current accelerations
    public float[] masses;             // Mass per segment
    public float[] distances;          // Distance constraints between segments
    public Vec3[] startingDirections;  // Original bone directions
    
    // Render interpolation
    public Vec3[] renderPositions;
    public Vec3[] prevRenderPositions;
    
    // Original bone positions (reset reference)
    public Vec3[] originalPositions;
    
    // Bone references
    public EnhancedGeoBone[] originalChain;  // Static animation bones
    public EnhancedGeoBone[] dynamicChain;   // Physics-driven bones
    
    // Entity reference
    private final LightningDragonEntity dragon;
    
    // Physics parameters
    private boolean isSimulating = true;
    private int prevUpdateTick = -1;
    private float prevUpdateTime = 0;
    
    // Chain type for different behaviors
    public enum ChainType {
        TAIL,           // Long flexible tail
        WING_MEMBRANE,  // Wing membrane physics  
        NECK,           // Neck flexibility
        CUSTOM          // User-defined
    }
    
    private final ChainType chainType;
    
    public DragonDynamicChain(LightningDragonEntity dragon, ChainType type) {
        this.dragon = dragon;
        this.chainType = type;
        
        // Initialize empty arrays
        positions = new Vec3[0];
        previousPositions = new Vec3[0];
        velocities = new Vec3[0];
        accelerations = new Vec3[0];
        masses = new float[0];
        distances = new float[0];
        startingDirections = new Vec3[0];
        originalPositions = new Vec3[0];
        renderPositions = new Vec3[0];
        prevRenderPositions = new Vec3[0];
        prevUpdateTick = -1;
        prevUpdateTime = 0;
        
        isSimulating = true;
    }
    
    /**
     * Set up the physics chain from bone arrays
     */
    public void setChainArrays(EnhancedGeoBone[] originalChain, EnhancedGeoBone[] dynamicChain) {
        this.originalChain = originalChain;
        this.dynamicChain = dynamicChain;
        
        // Enable matrix tracking on original bones
        for (EnhancedGeoBone bone : originalChain) {
            bone.setTrackingMatrices(true);
        }
    }
    
    /**
     * Initialize the physics chain
     */
    public void setChain(EnhancedGeoBone[] originalChain, EnhancedGeoBone[] dynamicChain) {
        setChainArrays(originalChain, dynamicChain);
        initializeChain();
    }
    
    /**
     * Initialize or reinitialize the physics simulation
     */
    public void initializeChain() {
        if (originalChain == null || dynamicChain == null) {
            return;
        }
        
        int chainLength = originalChain.length;
        
        // Check if we need to resize arrays
        if (positions.length != chainLength || Double.isNaN(positions.length > 0 ? positions[0].x : 0) || dynamicChain[0] == null) {
            
            // Initialize arrays
            positions = new Vec3[chainLength];
            previousPositions = new Vec3[chainLength];
            velocities = new Vec3[chainLength];
            accelerations = new Vec3[chainLength];
            masses = new float[chainLength];
            distances = new float[chainLength];
            startingDirections = new Vec3[chainLength];
            originalPositions = new Vec3[chainLength];
            renderPositions = new Vec3[chainLength];
            prevRenderPositions = new Vec3[chainLength];
            
            // Initialize physics state from current bone positions
            for (int i = 0; i < chainLength; i++) {
                Vector3d bonePos = originalChain[i].getWorldPosition();
                Vec3 pos = new Vec3(bonePos.x, bonePos.y, bonePos.z);
                
                originalPositions[i] = pos;
                positions[i] = pos;
                previousPositions[i] = pos;
                renderPositions[i] = pos;
                prevRenderPositions[i] = pos;
                
                velocities[i] = Vec3.ZERO;
                accelerations[i] = Vec3.ZERO;
                
                // Set mass based on chain type and position
                masses[i] = calculateSegmentMass(i, chainLength);
                
                // Calculate distance constraints
                if (i > 0) {
                    distances[i] = (float) originalPositions[i].distanceTo(originalPositions[i - 1]);
                    
                    // Calculate starting direction vector
                    Vec3 dir = originalPositions[i].subtract(originalPositions[i - 1]).normalize();
                    startingDirections[i - 1] = dir;
                } else {
                    distances[i] = 0f;
                }
            }
            
            // Create dynamic bones if needed
            for (int i = 0; i < chainLength; i++) {
                if (dynamicChain[i] == null) {
                    dynamicChain[i] = new EnhancedGeoBone(originalChain[i]);
                }
            }
        }
    }
    
    /**
     * Calculate mass for segment based on chain type and position
     */
    private float calculateSegmentMass(int index, int totalLength) {
        float baseMass = 1.0f;
        float positionFactor = (float) (index + 1) / (float) totalLength;
        
        return switch (chainType) {
            case TAIL -> baseMass * (1.0f - positionFactor * 0.3f); // Lighter at tip
            case WING_MEMBRANE -> baseMass * 0.8f; // Generally lighter
            case NECK -> baseMass * 1.2f; // Heavier, more stable
            case CUSTOM -> baseMass;
        };
    }
    
    /**
     * Update the physics simulation
     */
    public void updateChain(float deltaTime, float gravityAmount, float stiffness, float stiffnessFalloff, 
                           float damping, int numUpdates, boolean useFloor) {
        
        if (originalChain == null || dynamicChain == null || positions.length != originalChain.length || Double.isNaN(positions[0].x)) {
            return;
        }
        
        float currentTime = dragon.tickCount + deltaTime;
        
        // Store previous render positions for interpolation
        for (int i = 0; i < originalChain.length; i++) {
            prevRenderPositions[i] = new Vec3(renderPositions[i].x, renderPositions[i].y, renderPositions[i].z);
        }
        
        // Update original positions from animated bones
        for (int i = 0; i < originalChain.length; i++) {
            Vector3d pos = originalChain[i].getWorldPosition();
            originalPositions[i] = new Vec3(pos.x, pos.y, pos.z);
            
            // Update distance constraints dynamically
            if (i > 0) {
                distances[i] = (float) originalPositions[i].distanceTo(originalPositions[i - 1]);
            } else {
                distances[i] = 0f;
            }
        }
        
        // Run physics simulation
        if (!Minecraft.getInstance().isPaused()) {
            updateSpringPhysics(gravityAmount, damping, stiffness, stiffnessFalloff, numUpdates, currentTime - prevUpdateTime);
        }
        
        // Update render positions
        for (int i = 0; i < originalChain.length; i++) {
            renderPositions[i] = new Vec3(positions[i].x, positions[i].y, positions[i].z);
        }
        
        prevUpdateTime = currentTime;
        
        // Handle pause state
        if (Minecraft.getInstance().isPaused()) {
            deltaTime = 0.5f;
        }
        
        // Apply physics results to dynamic bones
        applyPhysicsToChain(originalChain, dynamicChain, deltaTime);
    }
    
    /**
     * Core spring physics simulation with constraint solving
     */
    public void updateSpringPhysics(float gravityAmount, float dampAmount, float stiffness, float stiffnessFalloff, 
                                   int numUpdates, float deltaTime) {
        
        if (!isSimulating) {
            // When not simulating, smoothly return to original positions
            for (int i = 1; i < positions.length; i++) {
                previousPositions[i] = positions[i];
                Vec3 diff = originalPositions[i].subtract(positions[i]);
                positions[i] = originalPositions[i].add(diff.scale(deltaTime));
            }
            previousPositions[0] = new Vec3(positions[0].x, positions[0].y, positions[0].z);
            return;
        }
        
        float deltaTimePerUpdate = deltaTime / (float) numUpdates;
        
        // Multiple physics sub-steps for stability
        for (int j = 0; j < numUpdates; j++) {
            
            // Root follows original animation with interpolation
            positions[0] = originalPositions[0].add(originalPositions[0].subtract(previousPositions[0]).scale((double) (j + 1) / (double) numUpdates));
            
            // Simulate each physics segment
            for (int i = 1; i < positions.length; i++) {
                Vec3 prevPosition = new Vec3(positions[i].x, positions[i].y, positions[i].z);
                
                // Force accumulation
                Vec3 force = Vec3.ZERO;
                
                // Gravity (modified by chain type)
                Vec3 gravity = new Vec3(0, -gravityAmount * getGravityMultiplier(), 0);
                force = force.add(gravity);
                
                // Spring force back to original position (reduces with distance from root)
                float springStrength = stiffness / (1 + i * i * stiffnessFalloff);
                Vec3 springForce = originalPositions[i].subtract(positions[i]).scale(springStrength);
                force = force.add(springForce);
                
                // Wind/movement forces for flying dragon
                if (dragon.isFlying()) {
                    Vec3 windForce = calculateWindForce(i);
                    force = force.add(windForce);
                }
                
                // Banking forces for tail responsiveness
                if (chainType == ChainType.TAIL && Math.abs(dragon.getBanking()) > 5.0f) {
                    Vec3 bankingForce = calculateBankingForce(i);
                    force = force.add(bankingForce);
                }
                
                // Physics integration (Verlet integration for stability)
                accelerations[i] = force.scale(1.0f / masses[i]);
                
                // Verlet integration with damping
                Vec3 newPos = positions[i]
                    .add(positions[i].subtract(previousPositions[i]).scale(1.0 - dampAmount))
                    .add(accelerations[i].scale(deltaTimePerUpdate * deltaTimePerUpdate).scale(1.0 - dampAmount));
                
                // Distance constraint enforcement (keeps bones connected)
                Vec3 vectorToPrevious = newPos.subtract(positions[i - 1]);
                vectorToPrevious = vectorToPrevious.normalize().scale(distances[i]);
                positions[i] = positions[i - 1].add(vectorToPrevious);
                
                previousPositions[i] = prevPosition;
            }
        }
        
        previousPositions[0] = new Vec3(originalPositions[0].x, originalPositions[0].y, originalPositions[0].z);
    }
    
    /**
     * Calculate wind forces for flying dragons
     */
    private Vec3 calculateWindForce(int segmentIndex) {
        Vec3 velocity = dragon.getDeltaMovement();
        float speed = (float) velocity.length();
        
        if (speed < 0.01f) return Vec3.ZERO;
        
        // Wind resistance proportional to flight speed
        Vec3 windDirection = velocity.normalize().scale(-1); // Opposite to movement
        float windStrength = speed * speed * 0.02f; // Quadratic with speed
        
        // Stronger effect on tail tip
        float segmentFactor = (float) segmentIndex / (float) positions.length;
        windStrength *= segmentFactor;
        
        return windDirection.scale(windStrength);
    }
    
    /**
     * Calculate banking forces for tail response to turns
     */
    private Vec3 calculateBankingForce(int segmentIndex) {
        float banking = dragon.getBanking();
        
        // Banking creates centrifugal force
        float bankingForce = banking * 0.005f;
        
        // Stronger effect on tail segments further from root
        float segmentFactor = (float) segmentIndex / (float) positions.length;
        bankingForce *= segmentFactor;
        
        // Force direction perpendicular to banking direction
        Vec3 bankingDirection = new Vec3(
            Math.sin(Math.toRadians(banking)),
            0,
            Math.cos(Math.toRadians(banking))
        );
        
        return bankingDirection.scale(bankingForce);
    }
    
    /**
     * Get gravity multiplier based on chain type
     */
    private float getGravityMultiplier() {
        return switch (chainType) {
            case TAIL -> 1.0f;
            case WING_MEMBRANE -> 0.3f; // Wings are lighter
            case NECK -> 0.8f;
            case CUSTOM -> 1.0f;
        };
    }
    
    /**
     * Apply computed physics positions to the dynamic bone chain
     */
    private void applyPhysicsToChain(EnhancedGeoBone[] originalChain, EnhancedGeoBone[] dynamicChain, float delta) {
        for (int i = dynamicChain.length - 1; i >= 0; i--) {
            if (dynamicChain[i] == null) return;
            
            // Set up dynamic bone properties
            dynamicChain[i].setForceMatrixTransform(true);
            dynamicChain[i].setHidden(false);
            originalChain[i].setHidden(true); // Hide original, show dynamic
            originalChain[i].setDynamicJoint(true);
            
            // Create transformation matrix
            Matrix4f transform = new Matrix4f();
            
            // Translation from physics simulation
            Vec3 pos = positions[i];
            transform = transform.translate((float) pos.x, (float) pos.y, (float) pos.z);
            
            // Rotation based on physics direction
            if (i < originalChain.length - 1) {
                Quaternionf rotation = calculateSegmentRotation(i);
                transform.rotate(rotation);
            }
            
            // Apply final transformation
            dynamicChain[i].setWorldSpaceMatrix(transform);
        }
    }
    
    /**
     * Calculate rotation for bone segment based on physics positions
     */
    private Quaternionf calculateSegmentRotation(int segmentIndex) {
        if (segmentIndex >= positions.length - 1) {
            return new Quaternionf(); // Identity rotation
        }
        
        // Calculate direction from current segment to next
        Vector3d currentPos = new Vector3d(positions[segmentIndex].x, positions[segmentIndex].y, positions[segmentIndex].z);
        Vector3d nextPos = new Vector3d(positions[segmentIndex + 1].x, positions[segmentIndex + 1].y, positions[segmentIndex + 1].z);
        Vector3d desiredDir = nextPos.sub(currentPos, new Vector3d()).normalize();
        
        // Original direction (usually downward for tails)
        Vector3d originalDir = new Vector3d(0, -1, 0);
        
        // Calculate rotation between original and desired direction
        double dot = desiredDir.dot(originalDir);
        
        if (dot > 0.9999999) {
            return new Quaternionf(); // No rotation needed
        } else {
            Vector3d cross = originalDir.cross(desiredDir, new Vector3d());
            double w = Math.sqrt(desiredDir.lengthSquared() * originalDir.lengthSquared()) + dot;
            return new Quaternionf(cross.x, cross.y, cross.z, w).normalize();
        }
    }
    
    /**
     * Enable/disable physics simulation
     */
    public void setSimulating(boolean simulating) {
        isSimulating = simulating;
    }
    
    /**
     * Check if physics is active
     */
    public boolean isSimulating() {
        return isSimulating;
    }
    
    /**
     * Get chain type
     */
    public ChainType getChainType() {
        return chainType;
    }
    
    /**
     * Force immediate reset to animated positions
     */
    public void resetToAnimatedPositions() {
        if (originalPositions == null || positions == null) return;
        
        for (int i = 0; i < positions.length && i < originalPositions.length; i++) {
            positions[i] = originalPositions[i];
            previousPositions[i] = originalPositions[i];
            velocities[i] = Vec3.ZERO;
        }
    }
}