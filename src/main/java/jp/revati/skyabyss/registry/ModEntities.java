package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneSentinel;
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
                    .sized(1.6f, 10.0f)
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
                    .sized(3.4f, 4.2f).clientTrackingRange(16).updateInterval(2).fireImmune()
                    .build(SkyAbyss.MODID + ":stone_whale_heart"));
    public static final DeferredHolder<EntityType<?>, EntityType<BoneSpike>> BONE_SPIKE = ENTITIES.register("bone_spike",
            () -> EntityType.Builder.<BoneSpike>of(BoneSpike::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20)
                    .build(SkyAbyss.MODID + ":bone_spike"));

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
    }

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
