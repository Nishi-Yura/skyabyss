package jp.revati.skyabyss.rebirth;

import org.jetbrains.annotations.Nullable;

// 魂盤の 6 つの腕 (B01)。転生のたびにどれか 1 つへ節を 1 つ足す。1 つの節の値は小さな数字 (値は 1 番目〜10 番目の節の分。
// 逓減と要の節は RebirthRules.effects)。名前と説明は lang の rebirth.skyabyss.arm.<id>[.desc]
public enum SoulArm {
    // 岩: 耐久・吸収
    ROCK("rock", 0xB89B72, 0, 0, 0.004, 0, 0, 0, 0.5, 0, 0),
    // 風: 足・待ち
    WIND("wind", 0x9FE0D2, 0, 0, 0, 0.006, 0.003, 0, 0, 0, 0),
    // 潮: マナ
    TIDE("tide", 0x5BB8E8, 0, 0, 0, 0, 0, 2, 0, 0, 0),
    // 灰: 傷
    ASH("ash", 0xE8744A, 0.005, 0, 0, 0, 0, 0, 0, 0, 0),
    // 骨: 射程・罠 (飛び道具の傷)
    BONE("bone", 0xD9D4C2, 0, 0.008, 0, 0, 0, 0, 0, 0, 0),
    // 旅: 拾う範囲・結晶
    JOURNEY("journey", 0xE6B84A, 0, 0, 0, 0, 0, 0, 0, 0.4, 0.02);

    public final String id;
    public final int color;
    public final double damage;
    public final double projectile;
    public final double reduction;
    public final double cooldown;
    public final double move;
    public final double mana;
    public final double health;
    public final double pickup;
    public final double crystal;

    SoulArm(String id, int color, double damage, double projectile, double reduction, double cooldown, double move, double mana,
            double health, double pickup, double crystal) {
        this.id = id;
        this.color = color;
        this.damage = damage;
        this.projectile = projectile;
        this.reduction = reduction;
        this.cooldown = cooldown;
        this.move = move;
        this.mana = mana;
        this.health = health;
        this.pickup = pickup;
        this.crystal = crystal;
    }

    public String nameKey() {
        return "rebirth.skyabyss.arm." + id;
    }

    public String shortKey() {
        return "rebirth.skyabyss.arm." + id + ".short";
    }

    public String descKey() {
        return "rebirth.skyabyss.arm." + id + ".desc";
    }

    @Nullable
    public static SoulArm byId(String id) {
        for (SoulArm a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}
