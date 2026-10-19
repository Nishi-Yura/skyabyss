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
        // 岩鯨の初見の場面が終わったら、すぐ「岩鯨を見た」の頁を記す (スキップでも)。頁の自然の解放は岩鯨が 160 ブロック以内に来たときで、
        // 場面は遠い所 (WHALE_NEAR より遠く) から始まるので、場面のあと 10 秒以上も帯が「夕方、岩鯨を待とう」のままだった (first_hour.txt の通しで発見)
        jp.revati.skyabyss.cinematic.Cinematics.onEnd(jp.revati.skyabyss.cinematic.Scenes.WHALE_FIRST_SIGHT, (player, skipped) -> {
            if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                jp.revati.skyabyss.lore.Lore.unlock(player, "chapter1/stone_whale", true);
            }
        });
        ATTACHMENTS.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
