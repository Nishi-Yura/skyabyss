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

    // 一章の地上の敵 (docs/design/21 の 3.1。tools/gen/ch1/gen_ch1_ground_mobs.py)
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

    // 二章の地上の敵 (docs/design/21 の 4.1、P12。tools/gen/ch2/gen_ch2_ground_mobs.py)
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

    // 大陸の新しい敵 (docs/design/44。tools/gen/foes/gen_foes_one.py)
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.Bandit>> BANDIT = ENTITIES.register("bandit",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.Bandit::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.85f).clientTrackingRange(8).build(SkyAbyss.MODID + ":bandit"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.BanditArcher>> BANDIT_ARCHER = ENTITIES.register("bandit_archer",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.BanditArcher::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.85f).clientTrackingRange(8).build(SkyAbyss.MODID + ":bandit_archer"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.BanditChief>> BANDIT_CHIEF = ENTITIES.register("bandit_chief",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.BanditChief::new, MobCategory.MONSTER)
                    .sized(0.75f, 2.2f).clientTrackingRange(8).build(SkyAbyss.MODID + ":bandit_chief"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.BoneFootman>> BONE_FOOTMAN = ENTITIES.register("bone_footman",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.BoneFootman::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.85f).clientTrackingRange(8).build(SkyAbyss.MODID + ":bone_footman"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.BoneBowman>> BONE_BOWMAN = ENTITIES.register("bone_bowman",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.BoneBowman::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.85f).clientTrackingRange(8).build(SkyAbyss.MODID + ":bone_bowman"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.BoneStandard>> BONE_STANDARD = ENTITIES.register("bone_standard",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.BoneStandard::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.15f).clientTrackingRange(8).build(SkyAbyss.MODID + ":bone_standard"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.GrainRat>> GRAIN_RAT = ENTITIES.register("grain_rat",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.GrainRat::new, MobCategory.MONSTER)
                    .sized(0.6f, 0.45f).eyeHeight(0.35f).clientTrackingRange(8).build(SkyAbyss.MODID + ":grain_rat"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.StrayWisp>> STRAY_WISP = ENTITIES.register("stray_wisp",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.StrayWisp::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.5f).eyeHeight(1.1f).clientTrackingRange(8).build(SkyAbyss.MODID + ":stray_wisp"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.FeralDog>> FERAL_DOG = ENTITIES.register("feral_dog",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.FeralDog::new, MobCategory.MONSTER)
                    .sized(0.7f, 0.9f).eyeHeight(0.75f).clientTrackingRange(8).build(SkyAbyss.MODID + ":feral_dog"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.MossDead>> MOSS_DEAD = ENTITIES.register("moss_dead",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.MossDead::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.0f).clientTrackingRange(8).build(SkyAbyss.MODID + ":moss_dead"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.MistAngler>> MIST_ANGLER = ENTITIES.register("mist_angler",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.MistAngler::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.0f).clientTrackingRange(8).build(SkyAbyss.MODID + ":mist_angler"));
    public static final DeferredHolder<EntityType<?>, EntityType<jp.revati.skyabyss.entity.foe.ThunderWolf>> THUNDER_WOLF = ENTITIES.register("thunder_wolf",
            () -> EntityType.Builder.of(jp.revati.skyabyss.entity.foe.ThunderWolf::new, MobCategory.MONSTER)
                    .sized(0.9f, 1.1f).eyeHeight(0.9f).clientTrackingRange(8).build(SkyAbyss.MODID + ":thunder_wolf"));

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
        event.put(BANDIT.get(), jp.revati.skyabyss.entity.foe.Bandit.createAttributes().build());
        event.put(BANDIT_ARCHER.get(), jp.revati.skyabyss.entity.foe.BanditArcher.createAttributes().build());
        event.put(BANDIT_CHIEF.get(), jp.revati.skyabyss.entity.foe.BanditChief.createAttributes().build());
        event.put(BONE_FOOTMAN.get(), jp.revati.skyabyss.entity.foe.BoneFootman.createAttributes().build());
        event.put(BONE_BOWMAN.get(), jp.revati.skyabyss.entity.foe.BoneBowman.createAttributes().build());
        event.put(BONE_STANDARD.get(), jp.revati.skyabyss.entity.foe.BoneStandard.createAttributes().build());
        event.put(GRAIN_RAT.get(), jp.revati.skyabyss.entity.foe.GrainRat.createAttributes().build());
        event.put(STRAY_WISP.get(), jp.revati.skyabyss.entity.foe.StrayWisp.createAttributes().build());
        event.put(FERAL_DOG.get(), jp.revati.skyabyss.entity.foe.FeralDog.createAttributes().build());
        event.put(MOSS_DEAD.get(), jp.revati.skyabyss.entity.foe.MossDead.createAttributes().build());
        event.put(MIST_ANGLER.get(), jp.revati.skyabyss.entity.foe.MistAngler.createAttributes().build());
        event.put(THUNDER_WOLF.get(), jp.revati.skyabyss.entity.foe.ThunderWolf.createAttributes().build());
    }

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        Ch1BackEntities.register(modEventBus);
    }
}
