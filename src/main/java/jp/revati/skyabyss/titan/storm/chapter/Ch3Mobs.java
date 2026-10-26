package jp.revati.skyabyss.titan.storm.chapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.storm.chapter.mob.LightningGleaner;
import jp.revati.skyabyss.titan.storm.chapter.mob.PeakFledgling;
import jp.revati.skyabyss.titan.storm.chapter.mob.ScorchedWarden;
import jp.revati.skyabyss.titan.storm.chapter.mob.StaticPuff;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

// 三章の地上の敵 (docs/design/21 の 5.1、P16) の登録と湧かせ方。エンティティ・スポーンエッグ・音はこのクラスだけで閉じる
// (StormMobSetup.register から register(bus) を呼ぶ。ひとり歩きは FMLCommonSetupEvent で登録)。
//   帯電毛玉 (static_puff)        ひとり歩き。峰・高台 (山の地形)。昼夜。雨と雷雨のときは多く湧く (晴れた昼はおとなしい = StaticPuff.calm)。焼けた見張り塔の中の間
//   峰の雛 (peak_fledgling)       ひとり歩き。峰 (尖った峰・岩の峰・雪の斜面)。昼。焼けた見張り塔の入口の間
//   雷拾い (lightning_gleaner)    ひとり歩き。高台 (草地・風の丘)。昼。雷拾いの原には人が近くにいる間、2 羽まで (fieldTick。保存しない)
//   焼けた羽守 (scorched_warden)  湧き台だけ。焼けた見張り塔の頂の間 (名づき)
// 落とす素材は guide/Ch3Gear の品 (ID で引く。まだ無ければ落とさない)。絵・動き・音の別名・文は tools/gen/ch3/gen_ch3_ground_mobs.py
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch3Mobs {
    public static final String STATIC_PUFF = "skyabyss:static_puff";
    public static final String PEAK_FLEDGLING = "skyabyss:peak_fledgling";
    public static final String LIGHTNING_GLEANER = "skyabyss:lightning_gleaner";
    public static final String SCORCHED_WARDEN = "skyabyss:scorched_warden";
    public static final String SCORCHED_FEATHER = "skyabyss:scorched_feather";
    public static final String STATIC_COTTON = "skyabyss:static_cotton";
    public static final String COPPER_WIRE = "skyabyss:copper_wire";
    public static final String THUNDER_WARD_THREAD = "skyabyss:thunder_ward_thread";

    // ひとり歩きの薄さ (2 秒ごとの試行のうち、この割合だけ湧く)。帯電毛玉は雨・雷雨で PUFF_STORM_THIN に増える
    public static final double PUFF_THIN = 0.2;
    public static final double PUFF_STORM_THIN = 0.55;
    public static final double FLEDGLING_THIN = 0.25;
    public static final double GLEANER_THIN = 0.12;
    // 雷拾いの原の雷拾い: 人が FIELD_NEAR 以内にいる間、原の中心から FIELD_KEEP 以内に FIELD_BIRDS 羽まで
    public static final int FIELD_BIRDS = 2;
    public static final double FIELD_NEAR = 48;
    public static final double FIELD_KEEP = 16;
    public static final String FIELD_TAG = "skyabyss_gleaner_field";

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<StaticPuff>> STATIC_PUFF_TYPE = ENTITIES.register("static_puff",
            () -> EntityType.Builder.of(StaticPuff::new, MobCategory.MONSTER).sized(0.7f, 0.7f).eyeHeight(0.45f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":static_puff"));
    public static final DeferredHolder<EntityType<?>, EntityType<PeakFledgling>> PEAK_FLEDGLING_TYPE = ENTITIES.register("peak_fledgling",
            () -> EntityType.Builder.of(PeakFledgling::new, MobCategory.MONSTER).sized(0.6f, 0.7f).eyeHeight(0.55f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":peak_fledgling"));
    public static final DeferredHolder<EntityType<?>, EntityType<LightningGleaner>> LIGHTNING_GLEANER_TYPE = ENTITIES.register("lightning_gleaner",
            () -> EntityType.Builder.of(LightningGleaner::new, MobCategory.MONSTER).sized(0.6f, 1.5f).eyeHeight(1.4f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":lightning_gleaner"));
    public static final DeferredHolder<EntityType<?>, EntityType<ScorchedWarden>> SCORCHED_WARDEN_TYPE = ENTITIES.register("scorched_warden",
            () -> EntityType.Builder.of(ScorchedWarden::new, MobCategory.MONSTER).sized(0.8f, 2.4f).eyeHeight(2.05f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":scorched_warden"));

    public static final DeferredItem<Item> STATIC_PUFF_EGG = ITEMS.register("static_puff_spawn_egg",
            () -> new DeferredSpawnEggItem(STATIC_PUFF_TYPE, 0xE6E6F0, 0x96D6FF, new Item.Properties()));
    public static final DeferredItem<Item> PEAK_FLEDGLING_EGG = ITEMS.register("peak_fledgling_spawn_egg",
            () -> new DeferredSpawnEggItem(PEAK_FLEDGLING_TYPE, 0x2A2628, 0x96D6FF, new Item.Properties()));
    public static final DeferredItem<Item> LIGHTNING_GLEANER_EGG = ITEMS.register("lightning_gleaner_spawn_egg",
            () -> new DeferredSpawnEggItem(LIGHTNING_GLEANER_TYPE, 0x6C7C9A, 0xB8682E, new Item.Properties()));
    public static final DeferredItem<Item> SCORCHED_WARDEN_EGG = ITEMS.register("scorched_warden_spawn_egg",
            () -> new DeferredSpawnEggItem(SCORCHED_WARDEN_TYPE, 0x2C3040, 0x96D6FF, new Item.Properties()));

    // ---------------------------------------------------------------- 音 (既存の音の出来事の別名。sounds.json は tools/gen/ch3/gen_ch3_ground_mobs.py)
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFF_AMBIENT = sound("static_puff_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFF_CHARGE = sound("static_puff_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFF_DISCHARGE = sound("static_puff_discharge");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFF_HURT = sound("static_puff_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFF_DEATH = sound("static_puff_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLEDGLING_AMBIENT = sound("peak_fledgling_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLEDGLING_CRY = sound("peak_fledgling_cry");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLEDGLING_DIVE = sound("peak_fledgling_dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLEDGLING_THUD = sound("peak_fledgling_thud");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLEDGLING_HURT = sound("peak_fledgling_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLEDGLING_DEATH = sound("peak_fledgling_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLEANER_AMBIENT = sound("lightning_gleaner_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLEANER_FLAP = sound("lightning_gleaner_flap");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLEANER_DROP = sound("lightning_gleaner_drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLEANER_HURT = sound("lightning_gleaner_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLEANER_DEATH = sound("lightning_gleaner_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLEANER_STEP = sound("lightning_gleaner_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_AMBIENT = sound("scorched_warden_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_HURT = sound("scorched_warden_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_DEATH = sound("scorched_warden_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_RING = sound("scorched_warden_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_RING_BURST = sound("scorched_warden_ring_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_LEAP = sound("scorched_warden_leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_LAND = sound("scorched_warden_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_STRUCK = sound("scorched_warden_struck");

    private Ch3Mobs() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    // StormMobSetup.register から
    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(Ch3Mobs::onAttributes);
        modEventBus.addListener(Ch3Mobs::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(STATIC_PUFF_TYPE.get(), StaticPuff.createAttributes().build());
        event.put(PEAK_FLEDGLING_TYPE.get(), PeakFledgling.createAttributes().build());
        event.put(LIGHTNING_GLEANER_TYPE.get(), LightningGleaner.createAttributes().build());
        event.put(SCORCHED_WARDEN_TYPE.get(), ScorchedWarden.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(STATIC_PUFF_EGG.get());
            event.accept(PEAK_FLEDGLING_EGG.get());
            event.accept(LIGHTNING_GLEANER_EGG.get());
            event.accept(SCORCHED_WARDEN_EGG.get());
        }
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Ch3Mobs::registerWanderers);
    }

    // ひとり歩きの登録 (何度呼んでもよい)
    public static void registerWanderers() {
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch3_static_puff", Chapters.STORM, STATIC_PUFF, 5, ChapterMobSpawner.Time.ANY,
                b -> highBiome(b) && ThreadLocalRandom.current().nextDouble() < (wetNow() ? PUFF_STORM_THIN : PUFF_THIN)));
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch3_peak_fledgling", Chapters.STORM, PEAK_FLEDGLING, 4, ChapterMobSpawner.Time.DAY,
                thin(Ch3Mobs::peakBiome, FLEDGLING_THIN)));
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch3_lightning_gleaner", Chapters.STORM, LIGHTNING_GLEANER, 3, ChapterMobSpawner.Time.DAY,
                thin(Ch3Mobs::plateauBiome, GLEANER_THIN)));
    }

    // ---------------------------------------------------------------- 地形と天気
    // 峰と高台 (山の地形すべてと風の丘)。帯電毛玉
    public static boolean highBiome(Holder<Biome> b) {
        return b.is(BiomeTags.IS_MOUNTAIN) || plateauBiome(b);
    }

    // 峰 (尖った峰・岩の峰・凍った峰・雪の斜面)。峰の雛
    public static boolean peakBiome(Holder<Biome> b) {
        return b.is(Biomes.JAGGED_PEAKS) || b.is(Biomes.STONY_PEAKS) || b.is(Biomes.FROZEN_PEAKS) || b.is(Biomes.SNOWY_SLOPES);
    }

    // 高台 (草地・風の丘・木立)。雷拾い
    public static boolean plateauBiome(Holder<Biome> b) {
        return b.is(Biomes.MEADOW) || b.is(Biomes.WINDSWEPT_HILLS) || b.is(Biomes.WINDSWEPT_GRAVELLY_HILLS) || b.is(Biomes.WINDSWEPT_FOREST)
                || b.is(Biomes.CHERRY_GROVE);
    }

    // 雨か雷雨か (サーバーが無ければ false)
    static boolean wetNow() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.overworld().isRaining();
    }

    private static Predicate<Holder<Biome>> thin(Predicate<Holder<Biome>> biome, double chance) {
        return b -> biome.test(b) && ThreadLocalRandom.current().nextDouble() < chance;
    }

    // ---------------------------------------------------------------- 素材
    @Nullable
    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }

    // ---------------------------------------------------------------- 雷拾いの原
    // 建った原の中心 (なければ空)
    public static List<BlockPos> fields(MinecraftServer server) {
        List<BlockPos> list = new ArrayList<>();
        for (SiteData.Entry e : SiteData.get(server.overworld()).allEntries()) {
            if (e.siteId.equals(Ch3Ground.FIELD) && e.built) {
                list.add(e.pos);
            }
        }
        return list;
    }

    // いちばん近い原 (range より遠ければ null)。雷拾いの逃げる先
    @Nullable
    public static BlockPos nearestField(MinecraftServer server, double x, double z, double range) {
        BlockPos best = null;
        double bd = range * range;
        for (BlockPos p : fields(server)) {
            double d = (p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // 原に人が近ければ、雷拾いを FIELD_BIRDS 羽まで足す。足した数
    public static int fieldTick(ServerLevel level) {
        int added = 0;
        for (BlockPos c : fields(level.getServer())) {
            if (!level.isLoaded(c)) {
                continue;
            }
            boolean near = false;
            for (ServerPlayer p : Players.snapshot(level.players())) {
                near |= !p.isSpectator() && p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) < FIELD_NEAR * FIELD_NEAR;
            }
            AABB box = new AABB(c).inflate(FIELD_KEEP, 8, FIELD_KEEP);
            List<LightningGleaner> birds = level.getEntitiesOfClass(LightningGleaner.class, box, b -> b.getTags().contains(FIELD_TAG));
            if (!near) {
                birds.forEach(LightningGleaner::discard);
                continue;
            }
            for (int i = birds.size(); i < FIELD_BIRDS; i++) {
                LightningGleaner bird = LIGHTNING_GLEANER_TYPE.get().create(level);
                if (bird == null) {
                    break;
                }
                double a = level.random.nextDouble() * Math.PI * 2;
                int x = c.getX() + (int) Math.round(Math.cos(a) * 4);
                int z = c.getZ() + (int) Math.round(Math.sin(a) * 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                bird.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0);
                bird.finalizeSpawn(level, level.getCurrentDifficultyAt(bird.blockPosition()), MobSpawnType.EVENT, null);
                level.addFreshEntity(bird);
                // 場所の印は入れたあとに付ける (保存しない。入ってくる個体の印は SiteSpawner が戻りものとして消すため)
                bird.addTag(FIELD_TAG);
                bird.addTag(SiteSpawner.TAG);
                bird.setHome(c);
                added++;
            }
        }
        return added;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 200 != 57 || server.overworld() == null) {
            return;
        }
        try {
            fieldTick(server.overworld());
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("[ch3 mobs] field tick failed", e);
        }
    }
}
