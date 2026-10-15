package jp.revati.skyabyss.titan.tide;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 第二の巨神「潮殻の巨神」の登録 (エンティティ、音)。SkyAbyss のコンストラクタから register を呼ぶ。
// 共有の登録 (ModEntities、ModSounds) には手を入れず、ここにまとめる
public final class TideSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 当たり判定は小さく、見た目は描画側で大きくする (岩鯨と同じ)。位置はモデルの底
    public static final DeferredHolder<EntityType<?>, EntityType<TideShellEntity>> TIDE_SHELL = ENTITIES.register("tide_shell",
            () -> EntityType.Builder.<TideShellEntity>of(TideShellEntity::new, MobCategory.MISC)
                    .sized(8.0f, 8.0f)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":tide_shell"));

    public static final DeferredHolder<SoundEvent, SoundEvent> SONG = sound("tide_song");
    public static final DeferredHolder<SoundEvent, SoundEvent> SURF = sound("tide_surf");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE = sound("tide_gate");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = sound("tide_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACK_WAVES = sound("tide_back_waves");

    private TideSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
