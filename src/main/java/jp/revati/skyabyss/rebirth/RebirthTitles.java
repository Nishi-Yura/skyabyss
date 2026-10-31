package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

// 称号 (C04)。最初は 24 個、裏ボスの「やっつめを聞いた者」で 25 個。解ける条件は Facts (その人の事実) だけで決まる計算で、解けたものは消えない (解除は足すだけ)。
// 並び順は固定 (解除の印は並び順の番号の bit で持つので、足すなら末尾へ。順番を替えない)。
// 名前の文は lang の "title.skyabyss_rebirth.<id>" と、条件の説明 "title.skyabyss_rebirth.<id>.how"
public final class RebirthTitles {
    // 1 人の事実。lifeBest = 一番速かった人生 (tick。なければ -1)、fewestDeaths = 一人生の最少の死 (なければ -1)、titanBests = 記録のある巨神の数、
    // founder = 裏ボス「初代の空見」を退けた (docs/design/53 の 6 章)
    public record Facts(int rebirths, int flawless, int titansStopped, int deathsTotal, long lifeBest, int fewestDeaths, int titanBests, boolean founder) {
        public static final Facts NONE = new Facts(0, 0, 0, 0, -1, -1, 0);

        public Facts(int rebirths, int flawless, int titansStopped, int deathsTotal, long lifeBest, int fewestDeaths, int titanBests) {
            this(rebirths, flawless, titansStopped, deathsTotal, lifeBest, fewestDeaths, titanBests, false);
        }
    }

    public record Title(int index, String id, Predicate<Facts> rule) {
    }

    public static final int MAX = 25;
    private static final long HOUR = 72_000L;
    private static final List<Title> ALL = new ArrayList<>();

    static {
        add("reborn_1", f -> f.rebirths >= 1);
        add("reborn_2", f -> f.rebirths >= 2);
        add("reborn_3", f -> f.rebirths >= 3);
        add("reborn_5", f -> f.rebirths >= 5);
        add("reborn_7", f -> f.rebirths >= 7);
        add("reborn_10", f -> f.rebirths >= 10);
        add("reborn_15", f -> f.rebirths >= 15);
        add("reborn_20", f -> f.rebirths >= 20);
        add("flawless_1", f -> f.flawless >= 1);
        add("flawless_3", f -> f.flawless >= 3);
        add("flawless_5", f -> f.flawless >= 5);
        add("almost_flawless", f -> f.fewestDeaths >= 0 && f.fewestDeaths <= 1);
        add("swift_1h", f -> f.lifeBest >= 0 && f.lifeBest <= HOUR);
        add("swift_30m", f -> f.lifeBest >= 0 && f.lifeBest <= HOUR / 2);
        add("titan_1", f -> f.titansStopped >= 1);
        add("titan_2", f -> f.titansStopped >= 2);
        add("titan_3", f -> f.titansStopped >= 3);
        add("titan_4", f -> f.titansStopped >= 4);
        add("all_backs", f -> f.titansStopped >= 4 && f.rebirths >= 1);
        add("gold_backs", f -> f.titansStopped >= 4 && f.rebirths >= 5);
        add("fallen_10", f -> f.deathsTotal >= 10);
        add("fallen_50", f -> f.deathsTotal >= 50);
        add("pulse_counted", f -> f.titanBests >= 1);
        add("pulse_all", f -> f.titanBests >= 4);
        add("yattsume", f -> f.founder);
    }

    private RebirthTitles() {
    }

    private static void add(String id, Predicate<Facts> rule) {
        ALL.add(new Title(ALL.size(), id, rule));
    }

    public static List<Title> all() {
        return ALL;
    }

    public static Title byId(String id) {
        for (Title t : ALL) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    // 事実から解ける称号の印 (bit 集合)
    public static long unlockedMask(Facts f) {
        long mask = 0;
        for (Title t : ALL) {
            if (t.rule.test(f)) {
                mask |= 1L << t.index;
            }
        }
        return mask;
    }

    public static boolean has(long mask, Title t) {
        return (mask & (1L << t.index)) != 0;
    }

    public static boolean has(long mask, String id) {
        Title t = byId(id);
        return t != null && has(mask, t);
    }
}
