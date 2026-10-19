package jp.revati.skyabyss.titan.young;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.progress.Titans;
import net.minecraft.network.chat.Component;

// 巨神の「子」(docs/design/28)。四体それぞれの子が、その巨神の呼び笛で現れ、パーティ全員 (最大 6 人) を乗せて運ぶ。
// 巨神ごとの得意 (空・海・峰・地) で、運べる所と動き方が違う。
//   WHALE (岩鯨の仔)   空を泳ぐ。どこへでも
//   TIDE  (潮殻の稚貝) 海の上を滑る。海辺から海辺へ。陸は短く転がるだけ
//   STORM (雷翼の雛)   低く飛び、稜線を渡る。海の上へは降りない
//   EMBER (余燼の仔)   地面を駆ける・溶岩を渡る。深い水は渡らない
public enum YoungKind {
    WHALE(Titans.STONE_WHALE, "young_stone_whale", Gait.SKY, 1.25, 2400, 20.0, 3.625, 1.0f),
    TIDE(Titans.TIDE_SHELL, "young_tide_shell", Gait.SEA, 0.95, 3200, 0.0, 1.0, 1.0f),
    STORM(Titans.THUNDER_WING, "young_thunder_wing", Gait.RIDGE, 1.05, 2600, 9.0, 2.25, 1.0f),
    EMBER(Titans.EMBER, "young_ember", Gait.GROUND, 0.8, 1800, 0.0, 1.5, 1.0f);

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
    // 巡航の速さ (ブロック/tick)
    public final double speed;
    // この子が運ぶ水平距離の上限 (ブロック)
    public final int maxRange;
    // 空を飛ぶ子の、地形の上の高さ
    public final double clearance;
    // 座席の高さ (子の足もとから。モデルの鞍の上面)
    public final double seatY;
    public final float scale;

    YoungKind(String titan, String model, Gait gait, double speed, int maxRange, double clearance, double seatY, float scale) {
        this.titan = titan;
        this.model = model;
        this.gait = gait;
        this.speed = speed;
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
