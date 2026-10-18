package jp.revati.skyabyss;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 最大体力の表示。内部の値は体力の点 (1 点 = ハートの半分) で、画面ではハート (値 ÷ 2) にして ❤ を付ける。
// 1 点 → 0.5❤、2 点 → 1❤、5 点 → 2.5❤。小数は 2 桁までで、末尾の 0 と小数点は消す
public final class Hearts {
    public static final String SYMBOL = "❤";

    private Hearts() {
    }

    // 体力の点を、ハートの数だけにする (記号なし)。「0.5」「2.5」「1」
    public static String number(double healthPoints) {
        BigDecimal hearts = BigDecimal.valueOf(healthPoints / 2).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return hearts.signum() == 0 ? "0" : hearts.toPlainString();
    }

    // 体力の点を、ハートの数に ❤ を付けた文にする。「0.5❤」「1❤」「2.5❤」
    public static String of(double healthPoints) {
        return number(healthPoints) + SYMBOL;
    }
}
