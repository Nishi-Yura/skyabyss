package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// 順位表の並べ方 (C11)。計算だけで、同点は決まった順 (自己テストが当てる)。
//   転生数   : 多い順 → 同点は死が少ない順 → 先に着いた人 (到着の順) → 名前
//   最速     : 一番速かった人生の tick が少ない順 (一度も転生していない人は載らない) → 名前
//   最少の死 : 一人生の最少の死が少ない順 (転生していない人は載らない) → 速い順 → 名前
// 「載らない」人 (順位表を断った人) は呼ぶ側が除く
public final class RebirthLeaderboard {
    public enum Metric {
        REBIRTHS, FASTEST, FEWEST_DEATHS
    }

    // 1 人ぶんの数字。order = 転生に着いた順の通し番号 (小さいほど先)
    public record Row(UUID id, String name, int rebirths, long fastestLife, int fewestDeaths, int deathsTotal, long order) {
    }

    private RebirthLeaderboard() {
    }

    public static boolean ranked(Metric metric, Row r) {
        return switch (metric) {
            case REBIRTHS -> r.rebirths > 0;
            case FASTEST -> r.rebirths > 0 && r.fastestLife >= 0;
            case FEWEST_DEATHS -> r.rebirths > 0 && r.fewestDeaths >= 0;
        };
    }

    public static Comparator<Row> order(Metric metric) {
        Comparator<Row> byName = Comparator.comparing(r -> r.name.toLowerCase(java.util.Locale.ROOT));
        return switch (metric) {
            case REBIRTHS -> Comparator.<Row>comparingInt(r -> -r.rebirths).thenComparingInt(r -> r.deathsTotal)
                    .thenComparingLong(r -> r.order).thenComparing(byName);
            case FASTEST -> Comparator.<Row>comparingLong(r -> r.fastestLife).thenComparing(byName);
            case FEWEST_DEATHS -> Comparator.<Row>comparingInt(r -> r.fewestDeaths).thenComparingLong(r -> r.fastestLife).thenComparing(byName);
        };
    }

    public static List<Row> top(List<Row> rows, Metric metric, int limit) {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            if (ranked(metric, r)) {
                out.add(r);
            }
        }
        out.sort(order(metric));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    // 順位表の値の文字 (表示用。時間は 1:23:45 / 12:34)
    public static String time(long ticks) {
        long s = Math.max(0, ticks) / 20;
        long h = s / 3600;
        long m = (s / 60) % 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
