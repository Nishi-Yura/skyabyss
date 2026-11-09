package jp.revati.skyabyss.titan.young;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.progress.Titans;
import net.minecraft.network.chat.Component;

// 巨神の「子」(docs/design/28)。四体それぞれの子が、その巨神の呼び笛で現れ、パーティ全員 (最大 6 人) を乗せて運ぶ。
// 巨神ごとの得意 (空・海・峰・地) で、運べる所と動き方が違う。
// どの子もどこへでも行ける (海の上に降りるのは潮殻の稚貝だけ)。違いは地形ごとの速さ:
//   WHALE (岩鯨の仔)   空を泳ぐ。どこでも同じ速さ (いちばん遅い)
//   TIDE  (潮殻の稚貝) 海の上を滑る。海の上がいちばん速く、陸は転がるので遅い
//   STORM (雷翼の雛)   低く飛び、稜線を渡る。陸の上がいちばん速い。海の上はふつう
//   EMBER (余燼の仔)   地面を駆ける・溶岩を渡る・湯気を上げて水を泳ぐ。陸は速く、水は遅い
public enum YoungKind {
    WHALE(Titans.STONE_WHALE, "young_stone_whale", Gait.SKY, 0.7, 0.7, 4000, 20.0, 3.625, 1.0f),
    TIDE(Titans.TIDE_SHELL, "young_tide_shell", Gait.SEA, 1.1, 2.2, 4000, 0.0, 1.0, 1.0f),
    STORM(Titans.THUNDER_WING, "young_thunder_wing", Gait.RIDGE, 1.5, 1.05, 4000, 9.0, 2.25, 1.0f),
    EMBER(Titans.EMBER, "young_ember", Gait.GROUND, 1.45, 1.0, 4000, 0.0, 2.5, 1.0f);

    public enum Gait { SKY, SEA, RIDGE, GROUND }

    // 乗れる人数 (パーティの最大)
    public static final int SEATS = 6;
    // 座席 (子の局所。横 = 左が +、前 = +)。2 列 × 3 行。先頭 (左前) が持ち主
    private static final double[][] SEAT_XZ = {
            {1.0, 2.0}, {-1.0, 2.0}, {1.0, 0.0}, {-1.0, 0.0}, {1.0, -2.0}, {-1.0, -2.0}
    };

    public final String titan;
    // geo / animations / texture / lowpoly の名前 (assets/skyabyss/.../<model>)
    public final String model;
    public final Gait gait;
    // 巡航の速さ (ブロック/tick)。陸の上と水の上 (2026-11-07 の声「鯨が強すぎて他の巨神を使わない」: どの子もどこへでも行け、得意の地形で速い)。
    // 2026-11-08 の声「鯨が強すぎるし、亀とトカゲの子供弱い」: 大陸が四つに分かれて、海も峰もまっすぐ越える鯨がさらに強くなったので、
    // 鯨を 1.0 → 0.7、亀を陸 0.75 → 1.1・海 1.7 → 2.2、トカゲ (余燼) を陸 1.3 → 1.45 (陸はいちばん速い雷翼の雛 1.5 のすぐ下)・水 0.75 → 1.0 に
    public final double land;
    public final double water;
    // 速いほう (見積りの上限)
    public final double speed;
    // この子が運ぶ水平距離の上限 (ブロック)
    public final int maxRange;
    // 空を飛ぶ子の、地形の上の高さ
    public final double clearance;
    // 座席の高さ (子の足もとから。モデルの鞍の上面)
    public final double seatY;
    public final float scale;

    YoungKind(String titan, String model, Gait gait, double land, double water, int maxRange, double clearance, double seatY, float scale) {
        this.titan = titan;
        this.model = model;
        this.gait = gait;
        this.land = land;
        this.water = water;
        this.speed = Math.max(land, water);
        this.maxRange = maxRange;
        this.clearance = clearance;
        this.seatY = seatY;
        this.scale = scale;
    }

    @Nullable
    public static YoungKind ofTitan(String titan) {
        for (YoungKind k : values()) {
            if (k.titan.equals(titan)) {
                return k;
            }
        }
        return null;
    }

    // その地形での巡航の速さ
    public double speedOn(boolean overWater) {
        return overWater ? water : land;
    }

    public boolean flies() {
        return gait == Gait.SKY || gait == Gait.RIDGE;
    }

    // 座席 slot の局所の位置 (横, 上, 前)
    public double[] seat(int slot) {
        double[] s = SEAT_XZ[Math.floorMod(slot, SEATS)];
        return new double[] {s[0], seatY, s[1]};
    }

    public Component displayName() {
        return Component.translatable("entity.skyabyss." + model);
    }

    // 子の言い回しの鍵 (message.skyabyss.young.<名前>.<鍵>)。巨神ごとの言い回しがあるものだけ
    public String text(String key) {
        return "message.skyabyss.young." + titan + "." + key;
    }
}
