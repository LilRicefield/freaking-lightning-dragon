package com.leon.lightningdragon.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("navigation")
    PathNavigation getNavigationAccessor();

    @Accessor("navigation")
    void setNavigationAccessor(PathNavigation navigation);

    @Accessor("moveControl")
    MoveControl getMoveControlAccessor();

    @Accessor("moveControl")
    void setMoveControlAccessor(MoveControl moveControl);
}