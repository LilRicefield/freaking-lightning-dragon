package com.leon.lightningdragon.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
    // This creates a public bridge to the protected "setYRot" method
    @Invoker("setYRot")
    void invokeSetYRot(float yRot);
}