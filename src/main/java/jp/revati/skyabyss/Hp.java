package jp.revati.skyabyss;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 体力の表示。マイクラの言葉 (ハート・黄ハート・❤) は使わず、数だけを出す。
// 2026-11-02 の声「Wynncraft もっと攻撃力出たりしてたよね。攻撃力の迫力がない」: 画面に出す体力と傷と攻撃力は、中の値の SHOWN 倍 (10 倍) で見せる。
//   中の値 (体力 20・傷 3.5) は変えない。帯の「200/200」、浮く傷の「35」、武器の「攻撃力 150」、説明の「体力 +5」はどれもこの倍率で出す。
//   マナ・防具・割合・秒・ブロックは倍にしない
// 小数は 1 桁までで、末尾の 0 と小数点は消す (0.05 → 「0.5」、2 → 「20」)
public final class Hp {
    // 画面に出すときの倍率 (中の値 × SHOWN)
    public static final double SHOWN = 10;

    private Hp() {
    }

    // 中の体力・傷を、画面に出す数に
    public static double shown(double healthPoints) {
        return healthPoints * SHOWN;
    }

    // 体力・傷を、画面に出す数の文にする。中の 0.5 → 「5」、2 → 「20」、20 → 「200」
    public static String number(double healthPoints) {
        BigDecimal v = BigDecimal.valueOf(shown(healthPoints)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return v.signum() == 0 ? "0" : v.toPlainString();
    }

    public static String of(double healthPoints) {
        return number(healthPoints);
    }

    // 整数に丸めた画面の数 (帯や浮く数字)
    public static int whole(double healthPoints) {
        return (int) Math.round(shown(healthPoints));
    }
}
