package com.leon.lightningdragon.common.network;

/**
 * Enum for dragon rider actions to replace magic strings
 */
public enum DragonRiderAction {
    NONE,           // No special action
    TAKEOFF_REQUEST, // Request takeoff from ground
    ACCELERATE,     // Start acceleration (L-Ctrl pressed)
    STOP_ACCELERATE, // Stop acceleration (L-Ctrl released) 
    ABILITY_USE,    // Use a named ability (start/one-shot)
    ABILITY_STOP;   // Stop a named ability (for hold-to-use)
    
    public static DragonRiderAction fromOrdinal(int ordinal) {
        DragonRiderAction[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
