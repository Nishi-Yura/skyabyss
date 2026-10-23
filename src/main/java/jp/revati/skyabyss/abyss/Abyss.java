package jp.revati.skyabyss.abyss;

import java.util.UUID;

import jp.revati.skyabyss.rebirth.RebirthView;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.trial.Trial.Mods;
import net.minecraft.world.entity.player.Player;

// 深淵 (docs/design/31 の B04・29 の 2.5・各心臓の設計書の「深淵」の節)。極の上の修飾の段と、深淵の心臓。
// 守ること: 数字の標準だけを上げる / 一人で解ける / 時間や状態で閉じない / 全部予告 (Telegraph) が見える。
// 転生の回数は RebirthView.count だけを読む。選ぶのはグループごと (試練の板のつまみ)。選べるのは転生 3 回以上の人だけだが、
// 入ったあとは誰が遊んでもよい (未転生の仲間も一緒に遊べる。報酬は全員に入る)。
public final class Abyss {
    private Abyss() {
    }

    // ---------------------------------------------------------------- 開く条件・グループの印
    public static final int OPEN_AT = 3;
    // グループの記録 (keep なので試練のリセットでは消えない)。1 = 深淵を選んでいる
    public static final String FLAG = "keep.trial.abyss";

    public static boolean open(int rebirths) {
        return rebirths >= OPEN_AT;
    }

    public static boolean open(Player player) {
        return open(RebirthView.count(player));
    }

    public static boolean on(BackGroupData.Record r) {
        return r != null && r.getInt(FLAG) == 1;
    }

    public static void set(BackGroupData.Record r, boolean on) {
        if (on) {
            r.putInt(FLAG, 1);
        } else {
            r.remove(FLAG);
        }
    }

    // 深淵のグループか (心臓が湧くとき・報いを数えるときに引く)
    public static boolean groupOn(net.minecraft.server.level.ServerLevel level, UUID group) {
        return group != null && on(jp.revati.skyabyss.titan.group.GroupProgress.of(level, group));
    }

    // ---------------------------------------------------------------- 試練の数字 (極の「一番きびしい」のさらに上)
    // 極 = 段 2 (一番きびしい)。深淵はその上に 15% の底上げ。待ち・猶予は短く、数・長さは増やす。底は解ける線 (FLOOR_*) で止める
    public static final double NUMBER_STEP = 1.15;
    public static final int FLOOR_TICKS = 60;
    public static final double FLOOR_WINDOW = 0.40;
    // 素材 (極 +2 の上。稀・極の上乗せの代わりに +3)
    public static final int MATERIAL_BONUS = 3;
    // 共鳴の欠片が出る確率 (極 8%)
    public static final float SHARD_CHANCE = 0.12f;

    // 待ち・猶予 (tick)。深淵なら短く (下限あり)
    public static int shorter(Mods m, int ticks) {
        return m.abyss() ? Math.max(FLOOR_TICKS, (int) Math.round(ticks / NUMBER_STEP)) : ticks;
    }

    // 拍の窓 (割合)。深淵なら狭く (下限あり)
    public static double narrower(Mods m, double window) {
        return m.abyss() ? Math.max(FLOOR_WINDOW, window / NUMBER_STEP) : window;
    }

    // 保つ時間 (tick)。深淵なら少し長く
    public static int longer(Mods m, int ticks) {
        return m.abyss() ? (int) Math.round(ticks * NUMBER_STEP) : ticks;
    }

    // 数・長さ。深淵なら 1 つ増える
    public static int plusOne(Mods m, int n) {
        return m.abyss() ? n + 1 : n;
    }

    // ---------------------------------------------------------------- 深淵の心臓の数字 (心臓ごとの手ごたえは HeartBalance の帯の中に収める)
    // 体力 (実質。受ける傷を 1/HEART_HP にする)・傷・技の間・領域の速さ。どれも帯 (戦いの長さ 3〜5 分・一撃 28% 前後) を壊さない範囲
    public static final float HEART_HP = 1.20f;
    public static final float HEART_DAMAGE = 1.12f;
    public static final double HEART_GAP = 0.85;
    // 領域の仕掛けの猶予・周期は 1 / これ (速くなる)。仕掛けを解く手順は変えない
    public static final double DOMAIN_PACE = 1.30;
    // 深淵の技 (追加の一つ。心臓ごとの形) の周期 (tick)。一人でも避けられる予告を必ず付ける
    public static final int ATTACK_EVERY = 240;
    public static final int ATTACK_EVERY_FURY = 170;
    public static final int ATTACK_WARN = 34;
    public static final float ATTACK_DAMAGE = 17f;

    public static int gap(boolean abyss, int gap) {
        return abyss ? Math.max(8, (int) Math.round(gap * HEART_GAP)) : gap;
    }

    public static int domainTicks(boolean abyss, int ticks) {
        return abyss ? Math.max(8, (int) Math.round(ticks / DOMAIN_PACE)) : ticks;
    }

    // アーティファクトが出る確率 (深淵の心臓)。連続で出ないと LEGEND_PITY 回目で保証 (AbyssLoot)
    public static final float LEGEND_CHANCE = 0.25f;
    public static final int LEGEND_PITY = 4;

    // ---------------------------------------------------------------- 深淵のアーティファクトの書き換え (docs/design/27 の 4.2 と同格。数字は小さく、形で遊ぶ)
    // 奈落断ち (巨神斬り): 遅れた第二の刃
    public static final int ECHO_DELAY = 24;
    public static final double ECHO_SCALE = 0.6;
    // 沈む唄 (守りの唄): 残った吸収が体力に沈む。いちばん傷ついた仲間には、その合計の半分
    public static final double SUNK_SONG_SHARE = 0.5;
    // 天落とし (雲穿ち): 止まった所に雷の柱
    public static final int SKY_FALL_DELAY = 16;
    public static final double SKY_FALL_RADIUS = 2.5;
    public static final double SKY_FALL_SCALE = 0.9;
    // 這う灰 (灰散らし): 灰かぶりが 2 秒おきに 4 マス先の敵へ、最大 3 度
    public static final int ASH_EVERY = 40;
    public static final int ASH_STEPS = 3;
    public static final double ASH_RADIUS = 4.0;
}
