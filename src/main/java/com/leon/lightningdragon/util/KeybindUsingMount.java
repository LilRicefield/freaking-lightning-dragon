package com.leon.lightningdragon.util;

import net.minecraft.world.entity.Entity;
public interface KeybindUsingMount {
    
    /**
     * Called when a key is pressed by a rider
     * @param keyPresser The entity that pressed the key (usually a player)
     * @param type The type of key pressed:
     *             - 2 = G key (special ability)
     *             - 3 = Left mouse button (attack)
     *             - 4 = Y key press (Enhanced Lightning Beam start)
     *             - 5 = Y key release (Enhanced Lightning Beam stop)
     */
    void onKeyPacket(Entity keyPresser, int type);
}