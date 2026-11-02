package jp.revati.skyabyss.titan.tide.heart;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 潮殻の心臓 (潮殻の巨神の最終ボス) の登録: 心臓、泡の牢、真珠、音。SkyAbyss のコンストラクタから register を呼ぶ。
// 共有の登録 (ModEntities、ModSounds) と、本体の登録 (TideSetup) には手を入れず、ここにまとめる
public final class TideHeartSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 判定は 4.5 x 6.3 (前の 1.5 倍。迫力のため)。模型は描画で 3 倍 (巻貝の殻の幅は約 6.75 ブロック)。TideHeart.BODY_SCALE と TideHeartRenderer の SCALE を合わせる
    public static final DeferredHolder<EntityType<?>, EntityType<TideHeart>> TIDE_HEART = ENTITIES.register("tide_heart",
            () -> EntityType.Builder.<TideHeart>of(TideHeart::new, MobCategory.MONSTER)
                    .sized(4.5f, 6.3f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":tide_heart"));
    // 泡の牢: 人を包んで浮かせる。叩くと割れる
    public static final DeferredHolder<EntityType<?>, EntityType<TideBubble>> TIDE_BUBBLE = ENTITIES.register("tide_bubble",
            () -> EntityType.Builder.<TideBubble>of(TideBubble::new, MobCategory.MISC)
                    .sized(1.4f, 2.4f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":tide_bubble"));
    // 真珠の脈: 人を追う真珠
    public static final DeferredHolder<EntityType<?>, EntityType<TidePearl>> TIDE_PEARL = ENTITIES.register("tide_pearl",
            () -> EntityType.Builder.<TidePearl>of(TidePearl::new, MobCategory.MISC)
                    .sized(0.7f, 0.7f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":tide_pearl"));

    public static final DeferredHolder<SoundEvent, SoundEvent> BEAT = sound("tide_heart_beat");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = sound("tide_heart_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH = sound("tide_heart_breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELL = sound("tide_heart_shell");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHIRL = sound("tide_heart_whirl");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOOD = sound("tide_heart_flood");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR = sound("tide_heart_spear");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE = sound("tide_heart_bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> PEARL = sound("tide_heart_pearl");
    public static final DeferredHolder<SoundEvent, SoundEvent> EBB = sound("tide_heart_ebb");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAVE = sound("tide_heart_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN = sound("tide_heart_rain");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("tide_heart_death");

    private TideHeartSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(TideHeartSetup::attributes);
        modEventBus.addListener(TideHeartSetup::commonSetup);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(TIDE_HEART.get(), TideHeart.createAttributes().build());
    }

    // 試練の外で呼ばれた心臓 (スポーンエッグ・コマンド) は、ふつうの敵として経験値を分ける (推奨レベルの量)
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":tide_heart",
                new GrowthRewards.Source(TideLoot.HEART_XP, TideLoot.TIDE_LEVEL)));
    }
}
