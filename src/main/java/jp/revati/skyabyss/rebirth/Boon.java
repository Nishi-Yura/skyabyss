package jp.revati.skyabyss.rebirth;

import org.jetbrains.annotations.Nullable;

// 転生ごとの 3 択の恩恵の候補 (B07)。毎回この中から 3 枚が出て、1 つを選ぶ (選びは永久。同じ恩恵は重ねて選べる)。
// 値は小さな数字 (1 番目〜10 番目の分。逓減は RebirthRules.effects)。名前と説明は lang の rebirth.skyabyss.boon.<id>[.desc]
public enum Boon {
    STRIDE("stride", 0, 0, 0, 0.02, 0, 0, 0, 0, 0, 0),
    CRYSTAL("crystal", 0, 0, 0, 0, 0, 0, 0, 0, 0.05, 0),
    SCHOLAR("scholar", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.03),
    HARDY("hardy", 0, 0, 0, 0, 0, 0, 2, 0, 0, 0),
    TIDEWELL("tidewell", 0, 0, 0, 0, 0, 4, 0, 0, 0, 0),
    ASHEN("ashen", 0.005, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    WARD("ward", 0, 0.005, 0, 0, 0, 0, 0, 0, 0, 0),
    HAUL("haul", 0, 0, 0, 0, 0, 0, 0, 0.5, 0, 0),
    SWIFTHAND("swifthand", 0, 0, 0, 0, 0.01, 0, 0, 0, 0, 0),
    CALM("calm", 0, 0, 0.005, 0, 0, 0, 0, 0, 0, 0);

    public final String id;
    public final double damage;
    public final double reduction;
    public final double cooldown;
    public final double move;
    public final double attack;
    public final double mana;
    public final double health;
    public final double pickup;
    public final double crystal;
    public final double xp;

    Boon(String id, double damage, double reduction, double cooldown, double move, double attack, double mana, double health,
            double pickup, double crystal, double xp) {
        this.id = id;
        this.damage = damage;
        this.reduction = reduction;
        this.cooldown = cooldown;
        this.move = move;
        this.attack = attack;
        this.mana = mana;
        this.health = health;
        this.pickup = pickup;
        this.crystal = crystal;
        this.xp = xp;
    }

    public String nameKey() {
        return "rebirth.skyabyss.boon." + id;
    }

    public String descKey() {
        return "rebirth.skyabyss.boon." + id + ".desc";
    }

    @Nullable
    public static Boon byId(String id) {
        for (Boon b : values()) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }
}
