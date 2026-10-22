package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

// 心臓の下の果てない深み (docs/design/36 の 9) の区画の決まり。純粋な計算だけ (ワールドに触らない)。
//   区画 = 高さ HEIGHT (約 200) の円筒の部屋。番号 n (1 から) の区画は横へ SLOT × n ずらした所に建つ (重ならない)。
//   同じ世界の種と番号なら、いつも同じ区画 (型・仕掛け・修飾・珍しい部屋・足場の道)。
//   区画の中は「足場の道」: 螺旋に下りる足場が DROP ずつ下がって並ぶ。道は必ず一本つながる (AbyssDepthSelfTest が上から下まで確かめる)。
//   深さ: 区画の中の深さ (ly。上が 0) と、区画の番号から「心臓の下の深さ」(beyond) を出す。心臓の下の深さ 0 は心臓の間の底。
public final class DepthPlan {
    // 区画の横の間隔 (x = SLOT × 番号)。区画の半径 ROOM_R の二倍より十分広い
    public static final int SLOT = 4096;
    // 区画の上 (ly 0) の世界の y と高さ。心臓の間の底 (y -300) の下の果てない深み
    public static final int TOP_Y = -1280;
    public static final int HEIGHT = 200;
    public static final int ROOM_R = 26;
    // 心臓の間の底から、区画 1 の上までの深さ (表示の数字の土台。AbyssScaling の縦穴の上端 240 から心臓の底 -300 まで)
    public static final int HEART_DEPTH = 540;
    // 足場の道
    public static final int ROUTE_START = 8;
    public static final int DROP = 3;
    public static final double ROUTE_R = 10;
    public static final double ROUTE_STEP = 0.4;
    public static final double JUMP_MAX = 4.6;   // 足場の中心から中心までの、跳べる長さ (水平)
    public static final int JUMP_DROP_MAX = 4;   // 跳んで下りられる高さ
    public static final int LANDING_EVERY = 12;  // この数ごとに広い足場
    public static final int MAX_TIER = 4;
    public static final long SEED_SALT = 0x5DEECE66DL;

    // 区画の型 (景色。層の骨・塩・雷・灰は使わない)
    public enum Kind {
        INVERTED_FOREST, FLOATING_GRAVES, PULSE_CAVE, MIRROR_DARK, CHAIN_NET, LAMP_HALL, DRY_WELL, HOLLOW_STARS
    }

    // 仕掛け (区画ごとに 1〜3。深いほど多い)
    public enum Gimmick {
        LIGHTS_OUT,      // 灯りが消える: 壁の灯りが四分の一ずつ、二十秒ごとに消えて回る
        HEAVY_DESCENT,   // 下りの重さ: 落ちる速さが増す (上りは変わらない)
        BEAT_LEDGES,     // 拍で消える岩棚: 脇の岩棚が一秒半ごとに交互に消える
        CRUMBLE_LEDGES,  // 崩れる岩棚: 脇の岩棚に立つと二秒で崩れ、十秒で戻る
        COPY_FOES,       // 写しの敵: 湧く敵が「前の私の影」「名のない形」に寄る
        FOG_HIDE,        // 霧が敵を隠す: 灯りから離れた敵の姿が消える
        DARK_FRONT,      // 下から広がる闇: 底から一定の速さで闇が上がる。闇の中に入るとたまる。灯りの近くは守られる
        CHASE_FALL       // 上からの追手: 人の真上へ石が落ちる (予告の輪のあと)
    }

    // 修飾 (区画の数字を少し変える。区画の型・仕掛けとは別に、深いほど増える)
    public enum Modifier {
        MORE_FOES,    // 敵が多い (湧きの上限が 1.5 倍)
        FIERCE,       // 傷が深い (敵の傷 ×1.25)
        RICH_DROPS,   // 落とし物が多い (敵の落とし物の確率 +)
        SPARSE_LIGHT  // 灯りが少ない (壁の灯りが半分)
    }

    // 珍しい区画 (まれに。脇の部屋が一つ付く)
    public enum Rare {
        NONE, TREASURE_HALL, PAST_GRAVE, WARDEN_FRAGMENT
    }

    // 区画の決まり。index は 1 から
    public record Section(int index, long seed, Kind kind, List<Gimmick> gimmicks, List<Modifier> modifiers, Rare rare, int tier) {
        public int centerX() {
            return index * SLOT;
        }

        // 区画の上 (ly 0) の世界の y
        public int topY() {
            return TOP_Y;
        }

        public int bottomLy() {
            return HEIGHT - 1;
        }

        public boolean has(Gimmick g) {
            return gimmicks.contains(g);
        }

        public boolean has(Modifier m) {
            return modifiers.contains(m);
        }
    }

    // 足場 (区画の中の座標。dx・dz は区画の中心からの水平の距離、ly は足場の板の段)
    public record Platform(int k, int ly, double dx, double dz, double rad) {
        public boolean landing() {
            return rad >= 2.5;
        }
    }

    private DepthPlan() {
    }

    // 区画の段 (上へ行くほど低い段。0 から、区画 1 の 0、区画 2 の 1 …と上がる。上限あり)
    public static int tierOf(int index) {
        return Math.max(0, Math.min(MAX_TIER, (index - 1) / 2));
    }

    // 段ごとの仕掛けの数 (1〜3)・修飾の数 (0〜2)
    public static int gimmickCount(int tier) {
        return Math.min(3, 1 + Math.max(0, tier));
    }

    public static int modifierCount(int tier) {
        return Math.min(2, Math.max(0, tier));
    }

    public static Section plan(long worldSeed, int index) {
        long mixed = mix(worldSeed, index);
        Random r = new Random(mixed);
        Kind kind = Kind.values()[r.nextInt(Kind.values().length)];
        int tier = tierOf(index);
        List<Gimmick> gs = new ArrayList<>(Arrays.asList(Gimmick.values()));
        java.util.Collections.shuffle(gs, r);
        List<Gimmick> gimmicks = new ArrayList<>(gs.subList(0, gimmickCount(tier)));
        gimmicks.sort(null);
        List<Modifier> ms = new ArrayList<>(Arrays.asList(Modifier.values()));
        java.util.Collections.shuffle(ms, r);
        List<Modifier> modifiers = new ArrayList<>(ms.subList(0, modifierCount(tier)));
        modifiers.sort(null);
        Rare rare = Rare.NONE;
        if (index >= 2 && r.nextInt(5) == 0) {
            Rare[] all = Rare.values();
            rare = all[1 + r.nextInt(all.length - 1)];
        }
        return new Section(index, mixed, kind, List.copyOf(gimmicks), List.copyOf(modifiers), rare, tier);
    }

    // 足場の道 (上から下へ)。最後の一つは広い足場 (そこに裂け目の穴が開く)。区画ごとに決まる
    public static List<Platform> route(Section s) {
        Random r = new Random(s.seed() ^ SEED_SALT);
        double phase = r.nextDouble() * Math.PI * 2;
        int n = routeCount();
        List<Platform> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            int ly = ROUTE_START + DROP * k;
            double radius = ROUTE_R + (r.nextInt(3) - 1) * 0.5;
            double a = phase + k * ROUTE_STEP;
            double rad = k == n - 1 || k % LANDING_EVERY == LANDING_EVERY - 1 ? 2.6 : 1.6;
            out.add(new Platform(k, ly, Math.cos(a) * radius, Math.sin(a) * radius, rad));
        }
        return out;
    }

    // 道の足場の数 (最後は裂け目の広い足場)
    public static int routeCount() {
        return (HEIGHT - 1 - ROUTE_START - 4) / DROP + 1;
    }

    // 脇の岩棚 (道の偶数番の横。仕掛けの岩棚はこれ)。道の足場 k の横、一段下
    public static List<Platform> ledges(Section s, List<Platform> route) {
        List<Platform> out = new ArrayList<>();
        for (Platform p : route) {
            if (p.k() % 2 == 1 && !p.landing() && p.k() < route.size() - 2) {
                double a = Math.atan2(p.dz(), p.dx());
                out.add(new Platform(p.k(), p.ly() + 1, Math.cos(a) * 14, Math.sin(a) * 14, 1.0));
            }
        }
        return out;
    }

    // 道が上から下まで跳んで届くか (隣の足場へ。水平 JUMP_MAX 以内、下り JUMP_DROP_MAX 以内)。届かない組の番号を返す (届けば -1)
    public static int firstUnreachable(List<Platform> route) {
        for (int i = 1; i < route.size(); i++) {
            Platform a = route.get(i - 1);
            Platform b = route.get(i);
            double dx = a.dx() - b.dx();
            double dz = a.dz() - b.dz();
            double h = Math.sqrt(dx * dx + dz * dz);
            int drop = b.ly() - a.ly();
            if (h > JUMP_MAX || drop > JUMP_DROP_MAX || drop < 0) {
                return i;
            }
        }
        return -1;
    }

    // 区画の番号 → 心臓の下の深さの土台 (区画 n の上は、心臓の下 (n-1)×HEIGHT)
    public static int beyondOfTop(int index) {
        return (index - 1) * HEIGHT;
    }

    // 世界の y → 区画の中の ly
    public static int lyOf(int worldY) {
        return TOP_Y - worldY;
    }

    // 世界の y (区画の中) → 心臓の下の深さ (小数の段は切り捨て)
    public static int beyond(int index, int worldY) {
        return beyondOfTop(index) + Math.max(0, Math.min(HEIGHT, lyOf(worldY)));
    }

    // 区画の数字 (心臓の下の深さ) → 表示の深さ (心臓の間の底からの数字は HEART_DEPTH + beyond)
    public static int totalDepth(int beyond) {
        return HEART_DEPTH + Math.max(0, beyond);
    }

    static long mix(long seed, int index) {
        long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        return h;
    }
}
