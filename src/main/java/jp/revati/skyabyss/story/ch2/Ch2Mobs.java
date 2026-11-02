package jp.revati.skyabyss.story.ch2;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.tide.TideOcean;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

// 二章の地上の敵の差し込み口と湧かせ方 (P12)。
//   潮だまり貝 (tidepool_clam)  ひとり歩きの湧き。浜・石の浜 (水辺)、昼夜。沈んだ塩の蔵の入口の間 (湧き台は蔵の側が ID で置く)
//   砂潜り (sand_burrower)      ひとり歩きの湧き。砂の浜、干潮の刻だけ (TideOcean.isLowTide)。蔵の塩樽の間
//   宿借り (drift_hermit)       ひとり歩きの湧き。浜の昼。湧いた一匹が先頭になり、後ろに 2〜4 匹を連れて行列で歩く (DriftHermit)
//   潮の番人 (shore_warden)     湧き台だけ。沈んだ塩の蔵の奥の間 (名づき)
// 登録はこのクラスだけで閉じる (FMLCommonSetupEvent で register() を呼ぶ。ほかのファイルに一行も要らない)。
// 落とし物の素材 (小真珠・塩結晶・貝の欠け・真珠の護り糸) は別の作業が登録する。item(id) で引き、まだ無ければ落とさない。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch2Mobs {
    public static final String TIDEPOOL_CLAM = "skyabyss:tidepool_clam";
    public static final String SAND_BURROWER = "skyabyss:sand_burrower";
    public static final String DRIFT_HERMIT = "skyabyss:drift_hermit";
    public static final String SHORE_WARDEN = "skyabyss:shore_warden";
    // 落とす素材の ID (登録は別の作業。無いうちは落とさない)
    public static final String SMALL_PEARL = "skyabyss:small_pearl";
    public static final String SALT_CRYSTAL = "skyabyss:salt_crystal";
    public static final String SHELL_CHIP = "skyabyss:shell_chip";
    public static final String PEARL_WARD_THREAD = "skyabyss:pearl_ward_thread";

    // ひとり歩きは薄く: ChapterMobSpawner が選んだ試行のうち、この割合だけ湧く (2 秒ごとに 1 回試す)
    public static final double CLAM_THIN = 0.3;
    public static final double BURROWER_THIN = 0.35;
    public static final double HERMIT_THIN = 0.15;

    private Ch2Mobs() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Ch2Mobs::register);
    }

    // ひとり歩きの登録 (何度呼んでもよい。同じ id は置き換わる)
    public static void register() {
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch2_tidepool_clam", Chapters.TIDE, TIDEPOOL_CLAM, 5,
                ChapterMobSpawner.Time.ANY, thin(Ch2Mobs::shoreBiome, CLAM_THIN)));
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch2_sand_burrower", Chapters.TIDE, SAND_BURROWER, 5,
                ChapterMobSpawner.Time.ANY, thin(b -> sandBiome(b) && lowTideNow(), BURROWER_THIN)));
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch2_drift_hermit", Chapters.TIDE, DRIFT_HERMIT, 3,
                ChapterMobSpawner.Time.DAY, thin(Ch2Mobs::sandBiome, HERMIT_THIN)));
    }

    // ---------------------------------------------------------------- 地形と潮
    // 浜と石の浜 (水辺)。潮だまり貝
    public static boolean shoreBiome(Holder<Biome> b) {
        return b.is(Biomes.BEACH) || b.is(Biomes.STONY_SHORE) || b.is(Biomes.SNOWY_BEACH) || b.is(Biomes.MANGROVE_SWAMP);
    }

    // 砂の浜。砂潜り・宿借り
    public static boolean sandBiome(Holder<Biome> b) {
        return b.is(Biomes.BEACH) || b.is(Biomes.SNOWY_BEACH);
    }

    // いまワールドの潮が引いているか (サーバーが無ければ false)
    static boolean lowTideNow() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && TideOcean.isLowTide(server.overworld().getDayTime());
    }

    private static Predicate<Holder<Biome>> thin(Predicate<Holder<Biome>> biome, double chance) {
        return b -> biome.test(b) && ThreadLocalRandom.current().nextDouble() < chance;
    }

    // ---------------------------------------------------------------- 素材
    // ID の品 (登録されていなければ null)
    @Nullable
    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }
}
