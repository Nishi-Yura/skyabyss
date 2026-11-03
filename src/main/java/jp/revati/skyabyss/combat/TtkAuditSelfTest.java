package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.wild.WildGearTable;
import jp.revati.skyabyss.item.wild.WildGearTypes;
import jp.revati.skyabyss.weapon.MoveSet;
import jp.revati.skyabyss.weapon.MoveSets;
import jp.revati.skyabyss.world.continent.ContinentDanger;
import jp.revati.skyabyss.world.continent.RegionSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

// 倒すまでの時間の見回り (2026-11-04。近接は満ちた一撃だけが強くなった (MeleeHits) ので、土地の敵を満ちた一撃の拍子で倒しきる秒数を測る)。段の名前は "ttk audit"。
// 土地 (草原・岩鯨・潮・嵐・灰) ごとに、湧きの表の敵をその土地の強さで出し (体力・防具)、その土地の目安のレベルの近接の野の武器
// (WildGearTable の弓でない物で、振りのある物。目安の幅 [下 − 2, 上]) で、満ちた一撃を振りの長さごとに当てたときの、
// 一撃の数と秒 (中央値) を出す。比べに、前の連打 (毎秒 8 回・溜め 0 でも 2 割) の秒も出す。
// 決まり: ふつうの敵は満ちた一撃 MAX_HITS 回までで倒れ、MAX_SECONDS 秒を超えない。夜にまれに出る番人 (体力 ELITE_HP 以上) は ELITE_SECONDS 秒まで
// (技・鍛え・木の伸びを入れない、素の武器だけの見積もり。2026-11-04 の測り: ふつうの敵は 1〜5 回・3.3 秒まで、番人は 13〜15 秒 (前の連打で 11〜13 秒))
public final class TtkAuditSelfTest {
    public static final int MAX_HITS = 6;
    public static final double MAX_SECONDS = 4.5;
    public static final double ELITE_HP = 100;
    public static final double ELITE_SECONDS = 18;
    static final String[] REGIONS = {"meadow", "island", "whale", "tide", "storm", "ember"};
    // 土地の番号ごとの目安のレベル {下, 上} (ContinentAreas の REGION_LEVELS と同じ)
    static final int[][] LEVELS = {{1, 4}, {1, 1}, {4, 12}, {12, 18}, {18, 24}, {24, 30}};

    private TtkAuditSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("ttk audit", TtkAuditSelfTest::run));
    }

    record Foe(String id, double hp, double armor, double toughness) {
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        List<List<RegionSpawner.Entry>> table = RegionSpawner.table();
        BlockPos at = level.getSharedSpawnPos().atY(300);
        for (int region = 0; region < REGIONS.length; region++) {
            if (region == 1) {
                continue;
            }
            List<double[]> weapons = weapons(LEVELS[region][0], LEVELS[region][1]);
            if (weapons.isEmpty()) {
                f.add(REGIONS[region] + ": no melee wild weapons in its level band");
                continue;
            }
            List<String> seen = new ArrayList<>();
            for (RegionSpawner.Entry e : table.get(region)) {
                if (seen.contains(e.entity())) {
                    continue;
                }
                seen.add(e.entity());
                Foe foe = foe(level, e.entity(), region, at);
                if (foe == null) {
                    f.add(REGIONS[region] + ": could not make " + e.entity());
                    continue;
                }
                List<Double> hits = new ArrayList<>();
                List<Double> secs = new ArrayList<>();
                List<Double> spam = new ArrayList<>();
                for (double[] w : weapons) {
                    double dmg = 1 + w[0];
                    double delay = 20.0 / w[1];
                    double per = afterArmor(dmg, foe);
                    int n = (int) Math.ceil(foe.hp() / Math.max(0.01, per));
                    hits.add((double) n);
                    secs.add((n - 1) * delay / 20.0);
                    // 前の連打: 毎秒 8 回 (2.5 tick ごと)、溜め f = (2.5 + 0.5) / 振りの長さ、傷 = 0.2 + 0.8 f²
                    double ff = Math.min(1, 3.0 / delay);
                    double click = afterArmor(dmg * (0.2 + 0.8 * ff * ff), foe);
                    int c = (int) Math.ceil(foe.hp() / Math.max(0.01, click));
                    spam.add((c - 1) * 2.5 / 20.0);
                }
                double mh = median(hits);
                double ms = median(secs);
                SkyAbyss.LOGGER.info(String.format(Locale.ROOT, "SELFTEST ttk audit %-6s %-28s hp %6.1f armor %4.1f | full: %4.1f hits %5.2f s | old spam %5.2f s (%d weapons Lv %d-%d)",
                        REGIONS[region], e.entity(), foe.hp(), foe.armor(), mh, ms, median(spam), weapons.size(), LEVELS[region][0], LEVELS[region][1]));
                boolean elite = foe.hp() >= ELITE_HP;
                if (elite ? ms > ELITE_SECONDS : (mh > MAX_HITS || ms > MAX_SECONDS)) {
                    f.add(String.format(Locale.ROOT, "%s %s takes %.1f full hits / %.2f s at Lv %d-%d (max %s)", REGIONS[region], e.entity(), mh, ms,
                            LEVELS[region][0], LEVELS[region][1], elite ? ELITE_SECONDS + " s for a warden" : MAX_HITS + " hits / " + MAX_SECONDS + " s"));
                }
            }
        }
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ttk audit FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ttk audit PASSED (region foes fall within {} full hits / {} s, wardens within {} s, to a plain weapon of the region's level)",
                    MAX_HITS, MAX_SECONDS, ELITE_SECONDS);
        }
    }

    // 目安の幅 [lo − 2, hi] の、弓でない振りのある野の武器 {攻撃の上乗せ, 攻撃速度}
    static List<double[]> weapons(int lo, int hi) {
        List<double[]> out = new ArrayList<>();
        for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
            if (w.isBow() || w.level() < lo - 2 || w.level() > hi) {
                continue;
            }
            MoveSet ms = MoveSets.get(w.id());
            if (ms == null || ms.strike <= 0f) {
                continue;
            }
            out.add(new double[] {w.attack(), w.speed()});
        }
        return out;
    }

    // 土地の強さを掛けた敵の体力と防具 (空の高い所に一度出して読む)
    static Foe foe(ServerLevel level, String id, int region, BlockPos at) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id)).orElse(null);
        Entity en = type == null ? null : type.create(level);
        if (!(en instanceof Mob mob)) {
            return null;
        }
        mob.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        boolean scaled = "minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace())
                || mob instanceof jp.revati.skyabyss.entity.foe.ContinentFoe;
        if (scaled) {
            ContinentDanger.apply(mob, region);
        }
        Foe foe = new Foe(id, mob.getMaxHealth(), mob.getAttributeValue(Attributes.ARMOR), mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        mob.discard();
        return foe;
    }

    // バニラの防具の減り (CombatRules.getDamageAfterAbsorb の、武器の魔法を除いた式)
    static double afterArmor(double dmg, Foe foe) {
        double t = 2 + foe.toughness() / 4;
        double a = Math.max(foe.armor() * 0.2, Math.min(20, foe.armor() - dmg / t));
        return dmg * (1 - a / 25);
    }

    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        s.sort(Double::compare);
        int n = s.size();
        return n == 0 ? 0 : n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }
}
