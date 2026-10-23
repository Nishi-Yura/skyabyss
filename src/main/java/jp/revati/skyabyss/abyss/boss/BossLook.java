package jp.revati.skyabyss.abyss.boss;

import jp.revati.skyabyss.entity.GuardMob;
import net.minecraft.world.entity.Entity;

// 層の主・歪んだ王の見た目 (docs/design/36 の 8.5・9.4、docs/design/15 の 9.7)。
// 主・王は深淵の敵の体 (AbyssMob / AbyssMobB) をそのまま使い、戦いの決まりはそのまま。見た目だけを GuardMob の同期の値で替える:
//   look = 型 (下の番号) | 周 << 8 | 段 << 12   … 0 ならふつうの敵の姿
//   act  = 技 (下の番号) | 予告の長さ tick << 8 | 当たり 1 << 16 | 通し番号 << 17
// 描画 (abyss/mobs/client の二つの描画) は look が 0 でないとき、型の低ポリ (lowpoly/<stem>.json) と動き (animations/entity/<animStem>.animation.json) に替える。
// 予告のあいだは溜めの動き (windup_<技>。2 秒で作り、予告の長さに合わせて速さを変える)、当たりで strike_<技> (0.8 秒)。
public final class BossLook {
    public static final int LORD_RIB = 1;
    public static final int LORD_SALT = 2;
    public static final int LORD_STORM = 3;
    public static final int LORD_ASH = 4;
    public static final int KING_BONE = 11;
    public static final int KING_SALT = 12;
    public static final int KING_STORM = 13;
    public static final int KING_ASH = 14;
    public static final int KING_SHADOW = 15;

    // 技 (王の 5 つは DepthBoss.Move の順 + 1。主の 4 つは仕掛け)
    public static final int SLAM = 1;
    public static final int CHARGE = 2;
    public static final int SUMMON = 3;
    public static final int BOLTS = 4;
    public static final int SWEEP = 5;
    public static final int SNUFF = 6;
    public static final int CRUSH = 7;
    public static final int SPLIT = 8;
    public static final int ROAR = 9;
    static final String[] ACT = {"", "slam", "charge", "summon", "bolts", "sweep", "snuff", "crush", "split", "roar"};

    // 動きの長さ (秒)。アニメの JSON もこの長さで書く (tools/gen/abyss/gen_abyss_boss_anims.py)
    public static final double WINDUP_SEC = 2.0;
    public static final int STRIKE_TICKS = 16;
    public static final int PHASE_TICKS = 40;
    // 倒れる動きの長さ (tick)。ふつうの 20 tick より長く体を残す
    public static final int DEATH_TICKS = 44;

    private static final String[] LORD_STEM = {"lord_rib", "lord_salt", "lord_storm", "lord_ash"};
    private static final String[] KING_STEM = {"king_bone", "king_salt", "king_storm", "king_ash", "king_shadow"};
    // モデルは「SCALE をかけた後に案の大きさ」になるよう作ってある (層の主は AbyssLords.SCALE、王は一周目の 1.6)
    private static final float[] LORD_AUTHOR = {1.9f, 2.0f, 1.7f, 1.8f};
    private static final float KING_AUTHOR = 1.6f;

    private BossLook() {
    }

    public static int pack(int id, int cycle, int phase) {
        return (id & 0xff) | (Math.max(0, Math.min(15, cycle)) << 8) | (Math.max(0, Math.min(15, phase)) << 12);
    }

    public static int id(int look) {
        return look & 0xff;
    }

    public static int cycle(int look) {
        return (look >> 8) & 0xf;
    }

    public static int phase(int look) {
        return (look >> 12) & 0xf;
    }

    public static boolean isKing(int look) {
        return id(look) >= KING_BONE && id(look) <= KING_SHADOW;
    }

    // メッシュ・geo・アトラスの名前 (王は周ごとに別の姿。三周目より先は三周目の姿)
    public static String stem(int look) {
        int i = id(look);
        if (i >= LORD_RIB && i <= LORD_ASH) {
            return LORD_STEM[i - LORD_RIB];
        }
        if (isKing(look)) {
            return KING_STEM[i - KING_BONE] + "_c" + Math.min(2, cycle(look));
        }
        return null;
    }

    // 動きの名前 (王は三つの周で同じ骨・同じ動き)
    public static String animStem(int look) {
        int i = id(look);
        if (i >= LORD_RIB && i <= LORD_ASH) {
            return LORD_STEM[i - LORD_RIB];
        }
        if (isKing(look)) {
            return KING_STEM[i - KING_BONE];
        }
        return null;
    }

    public static float authorScale(int look) {
        int i = id(look);
        if (i >= LORD_RIB && i <= LORD_ASH) {
            return LORD_AUTHOR[i - LORD_RIB];
        }
        return KING_AUTHOR;
    }

    public static String actName(int code) {
        return code > 0 && code < ACT.length ? ACT[code] : "";
    }

    // ---------------------------------------------------------------- サーバー側 (戦いの決まりから呼ぶ。決まり自体は変えない)
    public static void set(Entity e, int look) {
        if (e instanceof GuardMob g) {
            g.setBossLook(look);
        }
    }

    public static void setPhase(Entity e, int phase) {
        if (e instanceof GuardMob g && g.bossLook() != 0 && phase(g.bossLook()) != phase) {
            int l = g.bossLook();
            g.setBossLook(pack(id(l), cycle(l), phase));
        }
    }

    // 予告が始まった (ticks = 予告の長さ)
    public static void windup(Entity e, int code, int ticks) {
        if (e instanceof GuardMob g && g.bossLook() != 0 && e.isAlive()) {
            g.setBossAct(code, ticks, false);
        }
    }

    // 当たりの一瞬 (予告のあとの動き)。予告なしの技は直接これを呼ぶ
    public static void strike(Entity e, int code) {
        if (e instanceof GuardMob g && g.bossLook() != 0 && e.isAlive()) {
            g.setBossAct(code, 0, true);
        }
    }

    public static void clear(Entity e) {
        if (e instanceof GuardMob g && g.bossLook() != 0) {
            g.clearBossAct();
        }
    }
}
