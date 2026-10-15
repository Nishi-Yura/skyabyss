package jp.revati.skyabyss.item.affix;

import org.jetbrains.annotations.Nullable;

// ランダムな特性 (Affix) の種類。表と数値の理由は docs/design/03_gear_and_endgame.md 8章。
//  - 武器の特性は手に持っているときだけ、防具の特性はその部位に着ているときだけ効く
//  - 値の幅はレアのもの。伝説は幅の上下を LEGENDARY_SCALE 倍する
//  - percent の値は割合 (0.05 = 5%) で持つ
public enum AffixType {
    // ---- 武器 (メインハンド)
    SHARP("sharp", Slot.WEAPON, 30, 0.04, 0.08, 0.005, true),
    SWIFT("swift", Slot.WEAPON, 25, 0.04, 0.10, 0.005, true),
    THIRST("thirst", Slot.WEAPON, 15, 0.02, 0.04, 0.005, true),
    RESONANT("resonant", Slot.WEAPON, 20, 4, 10, 1, false),
    KEEN("keen", Slot.WEAPON, 15, 0.04, 0.10, 0.005, true),
    // ---- 防具 (着ている部位)
    BULWARK("bulwark", Slot.ARMOR, 30, 0.5, 1.0, 0.5, false),
    VIGOR("vigor", Slot.ARMOR, 25, 0.5, 1.0, 0.5, false),
    STRIDE("stride", Slot.ARMOR, 20, 0.015, 0.03, 0.005, true),
    WARD("ward", Slot.ARMOR, 15, 0.015, 0.03, 0.005, true),
    CALM("calm", Slot.ARMOR, 15, 3, 6, 1, false),
    STEADY("steady", Slot.ARMOR, 15, 0.05, 0.10, 0.01, true);

    // 伝説は値の幅を広げる (下も上も 1.25 倍)
    public static final double LEGENDARY_SCALE = 1.25;

    public enum Slot {
        WEAPON, ARMOR
    }

    public final String id;
    public final Slot slot;
    public final int weight;
    public final double min;
    public final double max;
    public final double step;
    public final boolean percent;

    AffixType(String id, Slot slot, int weight, double min, double max, double step, boolean percent) {
        this.id = id;
        this.slot = slot;
        this.weight = weight;
        this.min = min;
        this.max = max;
        this.step = step;
        this.percent = percent;
    }

    public double min(boolean legendary) {
        return legendary ? round(min * LEGENDARY_SCALE) : min;
    }

    public double max(boolean legendary) {
        return legendary ? round(max * LEGENDARY_SCALE) : max;
    }

    // 刻み (step) にそろえる。浮動小数の誤差で 0.0450000001 にならないよう、小数 4 桁で丸める
    public double round(double v) {
        double snapped = Math.round(v / step) * step;
        return Math.round(snapped * 10000) / 10000.0;
    }

    public String translationKey() {
        return "affix.skyabyss." + id;
    }

    @Nullable
    public static AffixType byId(String id) {
        for (AffixType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
