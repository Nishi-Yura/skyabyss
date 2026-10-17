package jp.revati.skyabyss.opening;

import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 序盤の拍子の登録 (プレイヤーごとの状態、音)。SkyAbyss のコンストラクタから register を呼ぶ。音は tools/gen_opening_sounds.py で合成している
public final class OpeningSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // プレイヤーの記録。死んでも引き継ぐ
    public static final Supplier<AttachmentType<OpeningState>> STATE = ATTACHMENTS.register("opening",
            () -> AttachmentType.builder(OpeningState::new).serialize(OpeningState.CODEC).copyOnDeath().build());

    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = sound("opening_heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL = sound("opening_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = sound("opening_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLE = sound("opening_rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("opening_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONG = sound("opening_song");

    private OpeningSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
