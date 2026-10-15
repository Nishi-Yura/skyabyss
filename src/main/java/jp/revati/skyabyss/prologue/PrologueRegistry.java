package jp.revati.skyabyss.prologue;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 序章の演出で使う登録物 (見る者のエンティティと音)。
// 共有の ModEntities / ModSounds を触らずに済むよう、ここでまとめて登録する
public final class PrologueRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 見る者: 見せる相手の画面の中にだけ置く (サーバーには出さない)。保存も召喚もしない
    public static final DeferredHolder<EntityType<?>, EntityType<WatcherEntity>> WATCHER = ENTITIES.register("watcher",
            () -> EntityType.Builder.<WatcherEntity>of(WatcherEntity::new, MobCategory.MISC)
                    .sized(0.6f, 2.6f)
                    .clientTrackingRange(10)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":watcher"));

    // 見る者を見ている間の、かすかな耳鳴りと遠い空気の音 (ループ)
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_PRESENCE = sound("watcher_presence");
    // 見る者が消えるときの、息を吸い込むような短い音
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_FADE = sound("watcher_fade");

    private PrologueRegistry() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
