package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneHauler;
import jp.revati.skyabyss.entity.BoneSentinel;
import jp.revati.skyabyss.entity.ElderSentry;
import jp.revati.skyabyss.entity.RockShellBeetle;
import jp.revati.skyabyss.entity.SpineHare;
import jp.revati.skyabyss.entity.BoneSpike;
import jp.revati.skyabyss.entity.MossCrawler;
import jp.revati.skyabyss.entity.RibArcher;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WaterwayMaw;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.WhaleChainEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Mod独自のエンティティ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);

    // 第一の巨神「岩鯨」。当たり判定は小さく、見た目は描画側で大きくする
    public static final DeferredHolder<EntityType<?>, EntityType<StoneWhaleEntity>> STONE_WHALE = ENTITIES.register("stone_whale",
            () -> EntityType.Builder.<StoneWhaleEntity>of(StoneWhaleEntity::new, MobCategory.MISC)
                    .sized(6.0f, 6.0f)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":stone_whale"));

    // 地上へ垂れた鎖の、右クリックでつかめる部分
    public static final DeferredHolder<EntityType<?>, EntityType<WhaleChainEntity>> WHALE_CHAIN = ENTITIES.register("whale_chain",
            () -> EntityType.Builder.<WhaleChainEntity>of(WhaleChainEntity::new, MobCategory.MISC)
                    .sized(2.6f, 12.0f)
                    .clientTrackingRange(10)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":whale_chain"));

    // 岩鯨の背の番兵たち
    public static final DeferredHolder<EntityType<?>, EntityType<BoneSentinel>> BONE_SENTINEL = monster("bone_sentinel",
            BoneSentinel::new, 0.8f, 2.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<RibArcher>> RIB_ARCHER = monster("rib_archer",
            RibArcher::new, 0.7f, 2.2f);
    // 苔芋虫は長く低い (描画は 1.3 倍で、体の長さ 2.4 マス・幅 0.85・頭の高さ 0.8)。当たりは正方形の箱なので、体の幅より少し広く取る
    public static final DeferredHolder<EntityType<?>, EntityType<MossCrawler>> MOSS_CRAWLER = ENTITIES.register("moss_crawler",
            () -> EntityType.Builder.of(MossCrawler::new, MobCategory.MONSTER)
                    .sized(1.2f, 0.9f).eyeHeight(0.5f).clientTrackingRange(8).build(SkyAbyss.MODID + ":moss_crawler"));
    public static final DeferredHolder<EntityType<?>, EntityType<WaterwayMaw>> WATERWAY_MAW = monster("waterway_maw",
            WaterwayMaw::new, 1.2f, 0.7f);
    public static final DeferredHolder<EntityType<?>, EntityType<WhaleboneCaptain>> WHALEBONE_CAPTAIN = ENTITIES.register("whalebone_captain",
            () -> EntityType.Builder.<WhaleboneCaptain>of(WhaleboneCaptain::new, MobCategory.MONSTER)
                    .sized(1.8f, 2.9f).clientTrackingRange(10).fireImmune()
                    .build(SkyAbyss.MODID + ":whalebone_captain"));
    // 岩鯨の心臓: 最終ボス。石と骨の殻に包まれ、空中にとどまる
    public static final DeferredHolder<EntityType<?>, EntityType<StoneWhaleHeart>> STONE_WHALE_HEART = ENTITIES.register("stone_whale_heart",
            () -> EntityType.Builder.<StoneWhaleHeart>of(StoneWhaleHeart::new, MobCategory.MONSTER)
                    .sized(5.1f, 6.3f).clientTrackingRange(16).updateInterval(2).fireImmune()
                    .build(SkyAbyss.MODID + ":stone_whale_heart"));
    public static final DeferredHolder<EntityType<?>, EntityType<BoneSpike>> BONE_SPIKE = ENTITIES.register("bone_spike",
            () -> EntityType.Builder.<BoneSpike>of(BoneSpike::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20)
                    .build(SkyAbyss.MODID + ":bone_spike"));

    // 一章の地上の敵 (docs/design/21 の 3.1。tools/gen_ch1_ground_mobs.py)
    public static final DeferredHolder<EntityType<?>, EntityType<RockShellBeetle>> ROCK_SHELL_BEETLE = ENTITIES.register("rock_shell_beetle",
            () -> EntityType.Builder.of(RockShellBeetle::new, MobCategory.MONSTER)
                    .sized(1.3f, 1.0f).eyeHeight(0.6f).clientTrackingRange(8).build(SkyAbyss.MODID + ":rock_shell_beetle"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpineHare>> SPINE_HARE = ENTITIES.register("spine_hare",
            () -> EntityType.Builder.of(SpineHare::new, MobCategory.MONSTER)
                    .sized(0.6f, 0.75f).eyeHeight(0.6f).clientTrackingRange(8).build(SkyAbyss.MODID + ":spine_hare"));
    public static final DeferredHolder<EntityType<?>, EntityType<BoneHauler>> BONE_HAULER = ENTITIES.register("bone_hauler",
            () -> EntityType.Builder.of(BoneHauler::new, MobCategory.MONSTER)
                    .sized(0.9f, 1.1f).eyeHeight(0.7f).clientTrackingRange(8).build(SkyAbyss.MODID + ":bone_hauler"));
    public static final DeferredHolder<EntityType<?>, EntityType<ElderSentry>> ELDER_SENTRY = ENTITIES.register("elder_sentry",
            () -> EntityType.Builder.of(ElderSentry::new, MobCategory.MONSTER)
                    .sized(0.9f, 2.45f).clientTrackingRange(10).build(SkyAbyss.MODID + ":elder_sentry"));

    // 二章の地上の敵 (docs/design/21 の 4.1、P12。tools/gen_ch2_ground_mobs.py)
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.TidepoolClam>> TIDEPOOL_CLAM = ENTITIES.register("tidepool_clam",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.TidepoolClam::new, MobCategory.MONSTER)
                    .sized(0.95f, 0.55f).eyeHeight(0.4f).clientTrackingRange(8).build(SkyAbyss.MODID + ":tidepool_clam"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.SandBurrower>> SAND_BURROWER = ENTITIES.register("sand_burrower",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.SandBurrower::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.45f).eyeHeight(0.35f).clientTrackingRange(8).build(SkyAbyss.MODID + ":sand_burrower"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.DriftHermit>> DRIFT_HERMIT = ENTITIES.register("drift_hermit",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.DriftHermit::new, MobCategory.MONSTER)
                    .sized(0.6f, 0.6f).eyeHeight(0.4f).clientTrackingRange(8).build(SkyAbyss.MODID + ":drift_hermit"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.ShoreWarden>> SHORE_WARDEN = ENTITIES.register("shore_warden",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.ShoreWarden::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.7f).eyeHeight(2.35f).clientTrackingRange(10).build(SkyAbyss.MODID + ":shore_warden"));

    private static <T extends net.minecraft.world.entity.Mob> DeferredHolder<EntityType<?>, EntityType<T>> monster(
            String name, EntityType.EntityFactory<T> factory, float width, float height) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MONSTER)
                .sized(width, height).clientTrackingRange(8).build(SkyAbyss.MODID + ":" + name));
    }

    @SubscribeEvent
    static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BONE_SENTINEL.get(), BoneSentinel.createAttributes().build());
        event.put(RIB_ARCHER.get(), RibArcher.createAttributes().build());
        event.put(MOSS_CRAWLER.get(), MossCrawler.createAttributes().build());
        event.put(WATERWAY_MAW.get(), WaterwayMaw.createAttributes().build());
        event.put(WHALEBONE_CAPTAIN.get(), WhaleboneCaptain.createAttributes().build());
        event.put(STONE_WHALE_HEART.get(), StoneWhaleHeart.createAttributes().build());
        event.put(ROCK_SHELL_BEETLE.get(), RockShellBeetle.createAttributes().build());
        event.put(SPINE_HARE.get(), SpineHare.createAttributes().build());
        event.put(BONE_HAULER.get(), BoneHauler.createAttributes().build());
        event.put(ELDER_SENTRY.get(), ElderSentry.createAttributes().build());
        event.put(TIDEPOOL_CLAM.get(), jp.revati.skyabyss.entity.TidepoolClam.createAttributes().build());
        event.put(SAND_BURROWER.get(), jp.revati.skyabyss.entity.SandBurrower.createAttributes().build());
        event.put(DRIFT_HERMIT.get(), jp.revati.skyabyss.entity.DriftHermit.createAttributes().build());
        event.put(SHORE_WARDEN.get(), jp.revati.skyabyss.entity.ShoreWarden.createAttributes().build());
    }

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        Ch1BackEntities.register(modEventBus);
    }
}
