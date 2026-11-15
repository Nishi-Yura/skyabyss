package jp.revati.skyabyss.client.fx;

// 光の表現の見せ方の案 (A/B/C)。選定のあいだは撮影の台本 (`lightfx <部分> <a|b|c>`) で切り替える。決まったら既定を選んだ案にする。
//   rift  裂け目       A 光の縁 (なめらかな縁と柔らかい光) / B 割れ目 (ぎざぎざの縁、走るひび、飛ぶ欠片) / C 渦 (縁を回る光の帯)
//   beam  光線・溜め   A まっすぐな光の帯 / B 帯に巻きつく細い光 / C 帯を走る光の塊
//   ring  当たりの輪   A 一本の光の輪 / B 二重の輪 (少し遅れて二本目) / C 輪と立ち上がる光の幕
//   glow  核の光       A 柔らかいにじみ / B にじみと光の筋 (十字) / C にじみと回る光の粒
//   orbit 軌道の線     A 傾いた細い輪 / B 輪を走る星の玉 / C 足もとに重なる輪
public final class LightStyles {
    private LightStyles() {
    }

    public static char rift = 'A';
    public static char beam = 'A';
    public static char ring = 'A';
    public static char glow = 'A';
    public static char orbit = 'A';

    // 撮影: 動きを止める時刻 (tick。0 未満 = 止めない)。裂け目の開き具合を案ごとに同じ所で撮るため
    public static float freezeAge = -1f;

    public static void set(String part, String style) {
        char c = Character.toUpperCase(style.isEmpty() ? 'A' : style.charAt(0));
        if (c < 'A' || c > 'C') {
            c = 'A';
        }
        switch (part) {
            case "rift" -> rift = c;
            case "beam" -> beam = c;
            case "ring" -> ring = c;
            case "glow" -> glow = c;
            case "orbit" -> orbit = c;
            case "all" -> {
                rift = c;
                beam = c;
                ring = c;
                glow = c;
                orbit = c;
            }
            default -> {
            }
        }
    }
}
