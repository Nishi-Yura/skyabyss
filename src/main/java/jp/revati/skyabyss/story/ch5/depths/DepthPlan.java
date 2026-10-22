package jp.revati.skyabyss.story.ch5.depths;

import net.minecraft.util.Mth;

// 心臓の下の果てない深み (docs/design/36 の 9) の階の決まり。純粋な計算だけ (ワールドに触らない)。
//   階 = 不思議のダンジョンのように、入るたびに部屋と通路を組み直す一枚の迷宮。下り口を探して次の階へ下りる。
//   5 階ごとが「歪んだ王」の階。王を倒すと下り口が開き、次の階が再開の階 (チェックポイント) になる。
//   組 (パーティ) ごとに場所を持つ (slot)。階は一つの場所に建て直す。二つの場所を交互に使い、次の階を先に用意する (parity)。
public final class DepthPlan {
    // 階の広さ (3×3 の区画、一つ CELL 角) と高さ
    public static final int CELL = 26;
    public static final int GRID = 3;
    public static final int SIZE = CELL * GRID;
    public static final int ROOM_H = 6;        // 部屋の中の高さ (空気)
    public static final int FLOOR_Y = -1300;   // 床の板の y (足は +1)
    public static final int BELOW = 5;         // 床の下 (下り口の穴) の余白
    public static final int ABOVE = ROOM_H + 1; // 床の上の余白 (天井まで)
    public static final int BOX_H = BELOW + 1 + ABOVE;
    // 場所: 組の番号 slot で z、二つの場所 parity で x をずらす
    public static final int ORIGIN_X = 6000;
    public static final int PAR_STEP = 160;
    public static final int SLOT_STEP = 160;

    public static final int BOSS_EVERY = 5;
    // 心臓の下の深さの数字は階ごとに FLOOR_BEYOND ずつ増える (記録・節目・HUD)
    public static final int FLOOR_BEYOND = 40;
    // 心臓の間の底から区画 (階) 1 の上までの深さ (表示の数字の土台)
    public static final int HEART_DEPTH = 540;
    public static final long SALT = 0x5DEECE66DL;

    // 階の型 (景色。八つの深淵の気配。層の骨・塩・雷・灰は使わない)
    public enum Kind {
        INVERTED_FOREST, FLOATING_GRAVES, PULSE_CAVE, MIRROR_DARK, CHAIN_NET, LAMP_HALL, DRY_WELL, HOLLOW_STARS
    }

    private DepthPlan() {
    }

    public static boolean isBoss(int floor) {
        return floor >= BOSS_EVERY && floor % BOSS_EVERY == 0;
    }

    // 王の通し番号 (5 階 = 1、10 階 = 2 …)
    public static int bossNo(int floor) {
        return floor / BOSS_EVERY;
    }

    // 再開の階 (王を倒した階の次、または 1)。floor まで届いた組の、いちばん新しい再開の階
    public static int checkpointFor(int reachedFloor) {
        return Math.max(1, reachedFloor / BOSS_EVERY * BOSS_EVERY + 1);
    }

    // 階の型は種と階で決まる。隣り合う階で同じ型は続けない
    public static Kind kindOf(long seed, int floor) {
        Kind[] all = Kind.values();
        int k = (int) Math.floorMod(mix(seed, floor), all.length);
        if (floor > 1 && k == (int) Math.floorMod(mix(seed, floor - 1), all.length)) {
            k = (k + 1) % all.length;
        }
        return all[k];
    }

    // 敵の強さの段 (AbyssScaling の DEEP の段、小数)。階が下がるほどゆっくり増える。急な段差はない (1 階で 0.1 段)
    public static double stepsOf(int floor) {
        return Mth.clamp(0.1 * (floor - 1), 0, jp.revati.skyabyss.abyss.mobs.AbyssScaling.DEEP_CAP);
    }

    // 敵の形 (2 = 歪み、3 = 崩れ) が崩れになる確率。深いほど歪んだ姿が増える
    public static double warpedChance(int floor) {
        return Mth.clamp(0.1 + 0.02 * (floor - 1), 0.1, 0.9);
    }

    // 階にいる敵の数の上限 (湧き直しの上限)
    public static int mobCap(int floor) {
        return Math.min(16, 8 + floor / 5);
    }

    // 階の部屋ごとの敵の数 (最初の置き)
    public static int roomMobs(int floor) {
        return Math.min(3, 1 + floor / 12);
    }

    // 罠の数
    public static int trapCount(int floor) {
        return Math.min(8, 2 + floor / 8);
    }

    // 心臓の下の深さ (階の入口)。階 1 が 0
    public static int beyondOfFloor(int floor) {
        return Math.max(0, floor - 1) * FLOOR_BEYOND;
    }

    // 区画の数字 (心臓の下の深さ) → 表示の深さ (心臓の間の底からの数字)
    public static int totalDepth(int beyond) {
        return HEART_DEPTH + Math.max(0, beyond);
    }

    // 階の種 (世界の種・組の場所・階・潜りの通し番号 salt から)。同じなら同じ階
    public static long floorSeed(long worldSeed, int slot, int floor, int salt) {
        return mix(mix(worldSeed ^ (slot * 0x9E3779B1L), floor), salt);
    }

    // 世界の座標の枠 (階の床の左下の角)
    public static int originX(int parity) {
        return ORIGIN_X + parity * PAR_STEP;
    }

    public static int originZ(int slot) {
        return slot * SLOT_STEP;
    }

    public static int parityOf(int floor) {
        return floor & 1;
    }

    public static int boxMinY() {
        return FLOOR_Y - BELOW;
    }

    public static int boxMaxY() {
        return FLOOR_Y + ABOVE;
    }

    static long mix(long seed, long index) {
        long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        return h;
    }
}
