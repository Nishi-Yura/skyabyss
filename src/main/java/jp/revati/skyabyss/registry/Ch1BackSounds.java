package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 岩鯨の背の新しい敵 (骨の砲手・骨鳥・老番兵) の音。音源は tools/gen/ch1/gen_ch1_back_mob_sounds.py、行は sounds.json の同じ名前
public final class Ch1BackSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_AMBIENT = register("bone_mortar_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_HURT = register("bone_mortar_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_DEATH = register("bone_mortar_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_STEP = register("bone_mortar_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_CHARGE = register("bone_mortar_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_FIRE = register("bone_mortar_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_LAND = register("bone_mortar_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULL_AMBIENT = register("bone_gull_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULL_HURT = register("bone_gull_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULL_DEATH = register("bone_gull_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULL_CRY = register("bone_gull_cry");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULL_DIVE = register("bone_gull_dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_AMBIENT = register("elder_sentinel_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_HURT = register("elder_sentinel_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_DEATH = register("elder_sentinel_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_STEP = register("elder_sentinel_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_RUSH = register("elder_sentinel_rush");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_STOMP = register("elder_sentinel_stomp");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_FLUTE = register("elder_sentinel_flute");

    private Ch1BackSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
