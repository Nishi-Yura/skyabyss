package jp.revati.skyabyss.sky;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 1章の空の演出の音 (共有の ModSounds は触らない)。音は tools/gen/sound/gen_chapter_sky_sounds.py で合成した
public final class ChapterSkyRegistry {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 山頂が削られるときの、遠い岩の擦れと崩れ (遠くまで届く)
    public static final DeferredHolder<SoundEvent, SoundEvent> PEAK_SCRAPE = SOUNDS.register("peak_scrape",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "peak_scrape"), 360f));
    // 雲の目が開いている間の、雲の上の深い気配 (ループ。本人のクライアントだけで鳴る)
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_PRESENCE = SOUNDS.register("sky_eye_presence",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "sky_eye_presence")));

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);
    // この Mod が入ってから生成されたチャンクの印 (チャンクと一緒に保存される)。
    // 印のないチャンクは Mod を入れる前からあったもので、プレイヤーが置いた記録 (ChapterSkyData) がないので、削られた山頂ではより慎重に扱う
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> FRESH_CHUNK = ATTACHMENTS.register("fresh_chunk",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    private ChapterSkyRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }
}
