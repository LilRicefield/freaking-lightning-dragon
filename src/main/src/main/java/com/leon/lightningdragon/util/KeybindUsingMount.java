package com.leon.lightningdragon.util;

import net.minecraft.world.entity.Entity;

/**
 * Interface for entities that can receive key inputs while being ridden
 * Based on Ice & Fire mod's approach for dragon riding
 */
public interface KeybindUsingMount {
    
    /**
     * Called when a key is pressed by a rider
     * @param keyPresser The entity that pressed the key (usually a player)
     * @param type The type of key pressed:
     *             - 2 = G key (special ability)
     *             - 3 = Left mouse button (attack)
     *             - Add more as needed
     */
    void onKeyPacket(Entity keyPresser, int type);
}