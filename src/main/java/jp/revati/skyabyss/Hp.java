package jp.revati.skyabyss;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 体力の表示。内部の値も画面の HP の帯も同じ「体力の点」(20 点 = 帯の 20/20)。マイクラの言葉 (ハート・黄ハート・❤) は使わず、数だけを出す。
// 0.5 点 → 「0.5」、2 点 → 「2」。小数は 2 桁までで、末尾の 0 と小数点は消す
public final class Hp {
    private Hp() {
    }

    // 体力の点を、数だけの文にする。「0.5」「2」「20」
    public static String number(double healthPoints) {
        BigDecimal v = BigDecimal.valueOf(healthPoints).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return v.signum() == 0 ? "0" : v.toPlainString();
    }

    public static String of(double healthPoints) {
        return number(healthPoints);
    }
}
