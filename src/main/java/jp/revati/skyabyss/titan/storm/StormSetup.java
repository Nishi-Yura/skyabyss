package jp.revati.skyabyss.titan.storm;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 第三の巨神「雷翼の巨神」の登録 (エンティティ、音)。SkyAbyss のコンストラクタから register を呼ぶ (12 の 2.1節、10.3節)。
// 背中を巨神に結びつける (Titans.bindBack) のも、ここで共通の準備のあとに行う。
public final class StormSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 当たり判定は小さく、見た目は描画側で 8 倍にする (潮殻と同じ)。位置はモデルの底
    public static final DeferredHolder<EntityType<?>, EntityType<StormWingEntity>> THUNDER_WING = ENTITIES.register("thunder_wing",
            () -> EntityType.Builder.<StormWingEntity>of(StormWingEntity::new, MobCategory.MISC)
                    .sized(8.0f, 8.0f)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":thunder_wing"));

    // 昇り風の柱。見えない当たり判定で、保存しない (止まり木のたびに作り直す)
    public static final DeferredHolder<EntityType<?>, EntityType<GaleColumnEntity>> GALE_COLUMN = ENTITIES.register("gale_column",
            () -> EntityType.Builder.<GaleColumnEntity>of(GaleColumnEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .fireImmune()
                    .noSave()
                    .build(SkyAbyss.MODID + ":gale_column"));

    public static final DeferredHolder<SoundEvent, SoundEvent> WING_CALL = sound("storm_wing_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_FAR = sound("storm_thunder_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> GALE = sound("storm_gale");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACK_WIND = sound("storm_back_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> RISE_WARN = sound("storm_rise_warn");
    public static final DeferredHolder<SoundEvent, SoundEvent> FALL_WARN = sound("storm_fall_warn");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROSSWIND = sound("storm_crosswind");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_TURN = sound("storm_mirror_turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECEIVER_LIT = sound("storm_receiver_lit");

    private StormSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(StormSetup::onCommonSetup);
    }

    // 背中のディメンションと巨神の結びつけ (14 の 5.2節の 1)。起動の共通の準備のなかで一度だけ
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> Titans.bindBack(StormTravel.STORM_BACK, Titans.THUNDER_WING, StormTravel::leave));
    }
}
