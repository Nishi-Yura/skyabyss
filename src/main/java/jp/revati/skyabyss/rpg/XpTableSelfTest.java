package jp.revati.skyabyss.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentPois;
import jp.revati.skyabyss.world.continent.ContinentSecrets;
import jp.revati.skyabyss.world.continent.RegionSpawner;
import net.minecraft.server.MinecraftServer;

// 経験値の表 (docs/design/04 の 3 章。2026-11-04 の表 4-2: クエスト 55%・初めての場所 20%・主 15%・狩り 10%)。段の名前は "xp table"。
// 章ごとに、上限のレベルの間 (一章 4→12、二章 12→18、三章 18→24、四章 24→30) に要る経験値を、四つの入り口で数えて割合を出す。
//   クエスト: その章のクエスト (本筋・脇・町の頼み・手ほどき。繰り返す頼みは数えない) の段と報酬の経験値
//   初めての場所: その章の土地 (一章 = 草原 + 岩鯨、二章 = 潮、三章 = 嵐、四章 = 灰) の見どころの数 × 見つけた経験値 + 隠れた発見 (その倍)
//   主: 心臓 (初回の上乗せ込み)・中ボス (同)・背の試練 3 つ・その土地の名のある敵・その土地のダンジョンを初めて越えた分
//   狩り: 章の狩りの目安の体数 (HUNT_KILLS) × その土地の湧きの表の敵の経験値 (重みの平均)
// 決まり: どの入り口も狙いの割合の ±TOLERANCE (割合の差。0.05 なら 55% に対して 50〜60%) に収まる
public final class XpTableSelfTest {
    public static final double[] SHARE = {0.55, 0.20, 0.15, 0.10};
    public static final String[] BUCKET = {"quest", "place", "boss", "hunt"};
    public static final double TOLERANCE = 0.05;
    // 章ごとの上限のレベル {下, 上}
    static final int[][] LEVELS = {{0, 0}, {4, 12}, {12, 18}, {18, 24}, {24, 30}};
    // 章ごとの狩りの目安の体数 (表 4-2)
    public static final int[] HUNT_KILLS = {0, 60, 80, 100, 120};
    static final String[] CHAPTER_IDS = {"", "titan_whale", "titan_tide", "titan_storm", "titan_ember"};

    private XpTableSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("xp table", XpTableSelfTest::run));
    }

    // その章の上限のレベルの間に要る経験値
    public static long need(int chapter) {
        long sum = 0;
        for (int lv = LEVELS[chapter][0]; lv < LEVELS[chapter][1]; lv++) {
            sum += GrowthRules.xpToNext(lv);
        }
        return sum;
    }

    // 章の土地 (ContinentMap の番号)
    static int[] regions(int chapter) {
        return switch (chapter) {
            case 1 -> new int[] {ContinentMap.MEADOW, ContinentMap.WHALE};
            case 2 -> new int[] {ContinentMap.TIDE};
            case 3 -> new int[] {ContinentMap.STORM};
            default -> new int[] {ContinentMap.EMBER};
        };
    }

    public static long quest(int chapter) {
        long sum = 0;
        for (QuestDef def : QuestManager.all()) {
            if (CHAPTER_IDS[chapter].equals(def.chapter()) && !def.repeats()) {
                sum += jp.revati.skyabyss.guide.ChapterXpBudget.questXp(def);
            }
        }
        return sum;
    }

    public static long place(int chapter) {
        long sum = 0;
        ContinentMap map = ContinentMap.get();
        for (int region : regions(chapter)) {
            int xp = ContinentPois.discoverXp(region);
            for (ContinentPois.Poi poi : ContinentPois.all()) {
                if (map.region(poi.x(), poi.z()) == region) {
                    sum += xp;
                }
            }
            for (ContinentSecrets.Secret s : ContinentSecrets.all()) {
                if (map.region(s.x(), s.z()) == region) {
                    sum += 2L * xp;
                }
            }
        }
        return sum;
    }

    public static long boss(int chapter) {
        return switch (chapter) {
            case 1 -> GrowthRewards.HEART_XP + GrowthRewards.HEART_FIRST_BONUS + GrowthRewards.CAPTAIN_XP + GrowthRewards.CAPTAIN_FIRST_BONUS
                    + 3L * GrowthRewards.TRIAL_XP + named(ContinentMap.WHALE) + dungeon(jp.revati.skyabyss.dungeon.Dungeon.BONE_PIT);
            case 2 -> jp.revati.skyabyss.titan.tide.heart.TideLoot.HEART_XP + jp.revati.skyabyss.titan.tide.heart.TideLoot.HEART_FIRST_BONUS
                    + jp.revati.skyabyss.trial.raid.mid.TideMidRoom.XP + jp.revati.skyabyss.trial.raid.mid.TideMidRoom.FIRST_BONUS
                    + 3L * jp.revati.skyabyss.titan.tide.back.TideBackTrials.TRIAL_XP + named(ContinentMap.TIDE) + dungeon(jp.revati.skyabyss.dungeon.Dungeon.TIDE_CAVE);
            case 3 -> jp.revati.skyabyss.titan.storm.heart.StormLoot.HEART_XP + jp.revati.skyabyss.titan.storm.heart.StormLoot.HEART_FIRST_BONUS
                    + jp.revati.skyabyss.titan.storm.raiju.RaijuLoot.GROWTH_XP + jp.revati.skyabyss.titan.storm.raiju.RaijuLoot.FIRST_BONUS
                    + 3L * jp.revati.skyabyss.titan.storm.back.StormBackTrials.TRIAL_XP + named(ContinentMap.STORM) + dungeon(jp.revati.skyabyss.dungeon.Dungeon.STORM_HOLLOW);
            default -> jp.revati.skyabyss.titan.ember.heart.EmberLoot.HEART_XP + jp.revati.skyabyss.titan.ember.heart.EmberLoot.HEART_FIRST_BONUS
                    + jp.revati.skyabyss.titan.ember.smith.EmberSmithSetup.GROWTH_XP + jp.revati.skyabyss.titan.ember.smith.EmberSmithSetup.FIRST_BONUS
                    + 3L * jp.revati.skyabyss.titan.ember.back.EmberBackTrials.TRIAL_XP + named(ContinentMap.EMBER) + dungeon(jp.revati.skyabyss.dungeon.Dungeon.MOLTEN_MINE);
        };
    }

    // 名のある敵の経験値 (撃破の表)
    static long named(int region) {
        jp.revati.skyabyss.world.continent.NamedFoes.Def d = jp.revati.skyabyss.world.continent.NamedFoes.def(region);
        GrowthRewards.Source s = d == null ? null : GrowthRewards.MOD_ENEMIES.get(d.entity());
        return s == null ? 0 : s.xp();
    }

    // ダンジョンを越えた分 (越えた報酬 + 中ボスと主の撃破)
    static long dungeon(jp.revati.skyabyss.dungeon.Dungeon d) {
        long sum = jp.revati.skyabyss.dungeon.Dungeons.clearXp(d);
        for (String id : new String[] {d.midBoss, d.boss}) {
            GrowthRewards.Source s = GrowthRewards.MOD_ENEMIES.get(id);
            sum += s == null ? 0 : s.xp();
        }
        return sum;
    }

    // 章の狩り: 目安の体数 × 土地の湧きの表の敵の経験値の重みの平均
    public static long hunt(int chapter) {
        double sum = 0;
        double weight = 0;
        List<List<RegionSpawner.Entry>> table = RegionSpawner.table();
        for (int region : regions(chapter)) {
            if (region == ContinentMap.MEADOW && chapter == 1) {
                continue;
            }
            for (RegionSpawner.Entry e : table.get(region)) {
                GrowthRewards.Source s = GrowthRewards.MOD_ENEMIES.get(e.entity());
                if (s != null) {
                    sum += (double) s.xp() * e.weight();
                    weight += e.weight();
                }
            }
        }
        return weight <= 0 ? 0 : Math.round(HUNT_KILLS[chapter] * sum / weight);
    }

    static long[] buckets(int chapter) {
        return new long[] {quest(chapter), place(chapter), boss(chapter), hunt(chapter)};
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        for (int c = 1; c <= 4; c++) {
            long need = need(c);
            long[] b = buckets(c);
            long total = b[0] + b[1] + b[2] + b[3];
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "SELFTEST xp table ch%d Lv %d-%d need %6d | total %6d (%3.0f%%)", c, LEVELS[c][0],
                    LEVELS[c][1], need, total, 100.0 * total / need));
            for (int i = 0; i < 4; i++) {
                double share = (double) b[i] / need;
                line.append(String.format(Locale.ROOT, " | %s %5d = %4.1f%% (want %2.0f%%, %5d)", BUCKET[i], b[i], 100 * share, 100 * SHARE[i],
                        Math.round(need * SHARE[i])));
                if (Math.abs(share - SHARE[i]) > TOLERANCE) {
                    f.add(String.format(Locale.ROOT, "ch%d %s is %.1f%% of the chapter's xp (want %.0f%% +- %.0f)", c, BUCKET[i], 100 * share, 100 * SHARE[i],
                            100 * TOLERANCE));
                }
            }
            SkyAbyss.LOGGER.info(line.toString());
            SkyAbyss.LOGGER.info("SELFTEST xp table ch{} small elements (ChapterXpBudget): new {} of {}", c,
                    jp.revati.skyabyss.guide.ChapterXpBudget.newXp(QuestManager.all(), c), jp.revati.skyabyss.guide.ChapterXpBudget.NEW_CAP[c]);
        }
        // 上限のレベルであふれた分は貯め (上限から BANK_LEVELS レベル分まで)、上限が上がると入る
        GrowthData d = new GrowthData();
        d.level = 12;
        Growth.addXpRaw(d, 1000, 12);
        long banked = d.banked;
        if (d.level != 12 || banked <= 0 || banked > 1000) {
            f.add("overflow at the cap was not banked (level " + d.level + ", banked " + banked + ")");
        }
        Growth.addXpRaw(d, 10_000_000L, 12);
        if (d.banked != Growth.bankMax(12)) {
            f.add("the bank is not capped at " + Growth.BANK_LEVELS + " levels (" + d.banked + " vs " + Growth.bankMax(12) + ")");
        }
        long bank = d.banked;
        d.banked = 0;
        int up = Growth.addXpRaw(d, bank, 18);
        if (up < 1 || d.level > 18) {
            f.add("banked xp did not raise the level once the cap rose (" + up + " levels to " + d.level + ")");
        }
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST xp table FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST xp table PASSED (each chapter: quests 55%, first-visit places 20%, bosses 15%, hunting 10% of its levels, +- 5; overflow at the cap is banked and comes in when the cap rises)");
        }
    }
}
