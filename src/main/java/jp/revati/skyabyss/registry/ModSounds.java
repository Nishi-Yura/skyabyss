package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Mod独自の効果音。音源は tools/gen/sound/gen_sky_sounds.py で合成している
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SKY_WINGBEAT = register("sky_wingbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKY_DRONE = register("sky_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKY_CALL = register("sky_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_SONG = register("titan_song");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_WIND = register("titan_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_CHAIN = register("titan_chain");
    public static final DeferredHolder<SoundEvent, SoundEvent> RESONANCE = register("resonance");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
