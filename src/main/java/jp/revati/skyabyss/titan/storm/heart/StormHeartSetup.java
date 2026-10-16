package jp.revati.skyabyss.titan.storm.heart;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 雷翼の心臓 (第三の巨神「雷翼の巨神」の最終ボス。12 の 5.3節) の登録: 心臓と音。SkyAbyss のコンストラクタから register を呼ぶ。
// 共有の登録 (ModEntities、ModSounds、assets/skyabyss/sounds.json) には手を入れず、ここにまとめる。
// 音の登録の名前は skyabyss:storm_heart_<名前>、音の場所は心臓だけの名前空間 skyabyss_storm_heart:<名前>
// (assets/skyabyss_storm_heart/sounds.json。tools/gen_storm_heart_sounds.py が書く)。
// 経験値の入り口は GrowthRewards.MOD_ENEMIES に一行 (thunder_wing_heart: 480、推奨 Lv24)
public final class StormHeartSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);
    public static final String SOUND_NAMESPACE = "skyabyss_storm_heart";

    // 判定は 3 x 4。模型は描画で 2 倍 (段階 2 の開いた翼の幅は約 6 ブロック)
    public static final DeferredHolder<EntityType<?>, EntityType<StormHeart>> STORM_HEART = ENTITIES.register("thunder_wing_heart",
            () -> EntityType.Builder.<StormHeart>of(StormHeart::new, MobCategory.MONSTER)
                    .sized(3.0f, 4.0f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":thunder_wing_heart"));

    public static final DeferredHolder<SoundEvent, SoundEvent> BEAT = sound("beat");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = sound("roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = sound("summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIKE = sound("strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE = sound("blade");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAIN = sound("chain");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLIT = sound("split");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINGBEAT = sound("wingbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = sound("charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> FEATHER = sound("feather");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASH = sound("flash");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEB = sound("web");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE = sound("barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("death");

    private StormHeartSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("storm_heart_" + name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SOUND_NAMESPACE, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(StormHeartSetup::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(STORM_HEART.get(), StormHeart.createAttributes().build());
    }
}
