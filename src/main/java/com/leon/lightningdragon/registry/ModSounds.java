package com.leon.lightningdragon.registry;

import com.leon.lightningdragon.LightningDragonMod;
import com.leon.lightningdragon.entity.LightningDragonEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * All the custom sounds for our lightning dragon
 * Because vanilla sounds are boring as heck
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LightningDragonMod.MOD_ID);

    // ===== AMBIENT SOUNDS =====
    public static final RegistryObject<SoundEvent> DRAGON_GRUMBLE_1 = registerSound("dragon_grumble_1");
    public static final RegistryObject<SoundEvent> DRAGON_GRUMBLE_2 = registerSound("dragon_grumble_2");
    public static final RegistryObject<SoundEvent> DRAGON_GRUMBLE_3 = registerSound("dragon_grumble_3");

    public static final RegistryObject<SoundEvent> DRAGON_PURR = registerSound("dragon_purr");
    public static final RegistryObject<SoundEvent> DRAGON_SNORT = registerSound("dragon_snort");
    public static final RegistryObject<SoundEvent> DRAGON_CHUFF = registerSound("dragon_chuff");

    // ===== EMOTIONAL SOUNDS =====
    public static final RegistryObject<SoundEvent> DRAGON_CONTENT = registerSound("dragon_content");
    public static final RegistryObject<SoundEvent> DRAGON_ANNOYED = registerSound("dragon_annoyed");
    public static final RegistryObject<SoundEvent> DRAGON_EXCITED = registerSound("dragon_excited");

    // ===== COMBAT SOUNDS =====
    public static final RegistryObject<SoundEvent> DRAGON_ROAR_SMALL = registerSound("dragon_roar_small");
    public static final RegistryObject<SoundEvent> DRAGON_GROWL_WARNING = registerSound("dragon_growl_warning");

    // ===== STEP SOUNDS =====
    public static final RegistryObject<SoundEvent> DRAGON_STEP = registerSound("dragon_step") ;

    /**
     * Helper method to register sounds without writing the same shit over and over
     */
    private static RegistryObject<SoundEvent> registerSound(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(LightningDragonMod.MOD_ID, name)));
    }
}