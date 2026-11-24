package jp.revati.skyabyss.client.ui;

// 画面の絵柄。PAPER = 切り絵 (docs/design/56 の 2.1)。ほかの三つは画素の板の案 (2026-11-24、参照の動画を受けた試作):
//   BRONZE = 鐘の銅 (深い石の板に、銅の縁と緑青)、STONE = 空見の石板 (明るい石の板に暗い欄)、DEEP = 淵の石 (縁の細い暗い板。差し色は職の色)
// 板の絵柄は client/ui/Plate が描く。既定は BRONZE (ユーザーの選び)。ほかは見比べ用に残してあり、/skyabyss_ui look と台本の uilook で切り替える
public enum UiLook {
    PAPER, BRONZE, STONE, DEEP;

    // 2026-11-24 にユーザーが A 鐘の銅を選んだ
    private static UiLook cur = BRONZE;

    public static UiLook get() {
        return cur;
    }

    public static void set(UiLook look) {
        cur = look == null ? PAPER : look;
    }

    // 画素の板の案のどれかか
    public static boolean plate() {
        return cur != PAPER;
    }

    // 台本の名前 (paper / a / b / c、または列挙の名前)
    public static UiLook parse(String s) {
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            // STONE・DEEP は見比べのときの案。色の写し (Plate.paint・textColor) が銅だけなので、いまは選べない
            case "paper" -> PAPER;
            default -> BRONZE;
        };
    }
}
