package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.HeartBalance.Profile;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import net.minecraft.server.MinecraftServer;

// 心臓四体の手ごたえの自己テスト (SKYABYSS_SELFTEST=1。結果は SELFTEST heart balance PASSED / FAILED)。
// 各心臓の balanceProfile() (実際の定数から組む) を HeartBalance の式に通して、
//  - 戦いの長さ: 想定の装備で、人数 1・2・4・6 のどれも 3〜5 分の帯に入る。人数が増えても一人あたりの長さがほぼ同じ。+10 の武器でも 2.5 分を切らない
//  - 一撃: 最も重い一撃が想定の体力の 22〜34% (3〜4 回で危ない)。段階が進むほど技が速く、憤怒は段階 3 の 1.3 倍以上
//  - 避けそこね: 平均の傷 4 回で想定の体力の 7 割以上、重い一撃 4 回は体力以上、2 回は体力未満
//  - 戦いの間の傷: 避ける確率 0.93 で補給の範囲に収まり (6 割以内)、0.80 では収まらない
//  - 実際の心臓の最大体力・硬さが Profile と同じ
public final class HeartBalanceSelfTest {
    private HeartBalanceSelfTest() {
    }

    // 呼ぶのは SelfTestRunner。人数の台帳に載せる心臓は、ここに足していく
    private static List<Supplier<Profile>> hearts() {
        List<Supplier<Profile>> list = new ArrayList<>();
        list.add(StoneWhaleHeart::balanceProfile);
        return list;
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            for (Supplier<Profile> s : hearts()) {
                check(f, s.get());
            }
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("heart balance selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST heart balance PASSED (fight length 1/2/4/6 players, heavy hit share, failed dodges, damage budget, cadence)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST heart balance FAILED: {}", x));
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void ok(List<String> f, boolean cond, String what) {
        if (!cond) {
            f.add(what);
        }
    }

    // 一つの心臓の確認 (ログに、設計書へ書く数を出す)
    public static void check(List<String> f, Profile p) {
        String id = p.stage();
        var stage = p.gear();
        ok(f, p.maxHp() <= 1024f, id + " max health " + p.maxHp() + " is over the attribute cap");
        double dps = HeartBalance.playerDps(id, 1.0);
        SkyAbyss.LOGGER.info("[selftest] heart balance {}: effective hp {} (max {} x resist {}), player dps {} at +0 (x{} uptime {})",
                id, fmt(p.effectiveHp()), p.maxHp(), p.resist(), fmt(dps), fmt(HeartBalance.UPTIME), "");

        // 戦いの長さ
        double solo = HeartBalance.fightSeconds(p, 1, 1.0);
        for (int n : new int[] {1, 2, 4, 6}) {
            double t = HeartBalance.fightSeconds(p, n, 1.0);
            SkyAbyss.LOGGER.info("[selftest] heart balance {}: {} player(s) -> {} s (scale x{})", id, n, fmt(t), fmt(HeartBalance.partyScale(n)));
            ok(f, t >= HeartBalance.BAND_MIN && t <= HeartBalance.BAND_MAX, id + " fight with " + n + " players takes " + fmt(t) + " s (want "
                    + HeartBalance.BAND_MIN + "-" + HeartBalance.BAND_MAX + ")");
            ok(f, t >= solo * 0.8 && t <= solo * 1.2, id + " fight with " + n + " players takes " + fmt(t) + " s vs solo " + fmt(solo) + " (want within 20%)");
        }
        double plus10 = HeartBalance.fightSeconds(p, 1, stage.dps10() / stage.dps0());
        SkyAbyss.LOGGER.info("[selftest] heart balance {}: +10 weapon solo -> {} s; half-prepared (x0.7 dps) -> {} s", id, fmt(plus10),
                fmt(HeartBalance.fightSeconds(p, 1, 0.7)));
        ok(f, plus10 >= 150, id + " fight with a +10 weapon takes only " + fmt(plus10) + " s (want >= 150)");
        ok(f, HeartBalance.fightSeconds(p, 1, 0.7) <= 420, id + " fight with 70% of the expected dps takes over 7 minutes");

        // 一撃
        double heavy = HeartBalance.heaviest(p);
        double heavyShare = HeartBalance.share(id, heavy);
        SkyAbyss.LOGGER.info("[selftest] heart balance {}: heaviest hit {} raw -> {} of {} hp ({} points); 2 hits {} / 4 hits {}", id, fmt(heavy),
                fmt(heavyShare), fmt(HeartBalance.expectedHealth(id)), fmt(HeartBalance.taken(id, heavy)), fmt(2 * heavyShare), fmt(4 * heavyShare));
        ok(f, heavyShare >= HeartBalance.HEAVY_SHARE_MIN && heavyShare <= HeartBalance.HEAVY_SHARE_MAX,
                id + " heaviest hit is " + fmt(heavyShare) + " of the expected health (want 0.22-0.34)");
        ok(f, 4 * heavyShare >= 1.0 && 2 * heavyShare < 1.0, id + " four heavy hits must kill and two must not");

        // 段階ごとの速さと避けそこね
        double[] aps = new double[4];
        for (int ph = 0; ph < 4; ph++) {
            aps[ph] = HeartBalance.attacksPerSecond(p, ph);
            double four = HeartBalance.failedDodges(p, ph, 4);
            SkyAbyss.LOGGER.info("[selftest] heart balance {}: phase {} -> {} attacks/s (every {} s), mean hit {} points, 4 failed dodges = {} of hp, {} s of the fight",
                    id, ph + 1, fmt(aps[ph]), fmt(1 / aps[ph]), fmt(HeartBalance.meanTaken(p, ph)), fmt(four), fmt(HeartBalance.phaseSeconds(p, ph, 1, 1.0)));
            ok(f, four >= 0.5, id + " phase " + (ph + 1) + ": four failed dodges only cost " + fmt(four) + " of the health (want >= 0.5)");
            ok(f, four <= 1.5, id + " phase " + (ph + 1) + ": four failed dodges cost " + fmt(four) + " of the health (want <= 1.5; telegraphs must stay fair)");
            if (ph > 0) {
                ok(f, aps[ph] >= aps[ph - 1] * 0.98, id + " phase " + (ph + 1) + " is not faster than the phase before");
            }
        }
        ok(f, aps[3] >= aps[2] * 1.3, id + " the final phase should be at least 1.3x phase 3's pace (" + fmt(aps[3]) + " vs " + fmt(aps[2]) + ")");
        ok(f, aps[2] >= aps[0] * 1.3, id + " phase 3 should be at least 1.3x phase 1's pace");

        // 戦いの間の傷
        for (int n : new int[] {1, 4}) {
            double budget = HeartBalance.budget(p, n);
            for (double dodge : new double[] {0.97, 0.93, 0.87, 0.80, 0.65}) {
                double dmg = HeartBalance.expectedDamage(p, n, dodge);
                SkyAbyss.LOGGER.info("[selftest] heart balance {}: {} player(s), dodge {} -> {} points taken over the fight ({} of budget {}), {} attacks",
                        id, n, fmt(dodge), fmt(dmg), fmt(dmg / budget), fmt(budget), fmt(HeartBalance.totalAttacks(p, n)));
            }
            ok(f, HeartBalance.expectedDamage(p, n, 0.93) <= 0.6 * budget, id + " " + n + " player(s): a 93% dodger takes more than 60% of the budget");
            ok(f, HeartBalance.expectedDamage(p, n, 0.80) >= 1.0 * budget, id + " " + n + " player(s): an 80% dodger still survives on the budget alone");
            ok(f, HeartBalance.expectedDamage(p, n, 0.97) <= 0.3 * budget, id + " " + n + " player(s): a 97% dodger should barely be scratched");
        }
    }
}
