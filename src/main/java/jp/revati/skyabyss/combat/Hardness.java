package jp.revati.skyabyss.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

// 主の固さは体力だけで出す (2026-11-09 の声「耐えはこのゲームから消していい。hp 増やすのだけで敵を固くするでいい」)。
// 前は体力の上限 1024 を越えられないので「人から受ける傷を割る」(人数・硬さ・型ごと・レベル・心臓の硬さ) で固くしていた。
// 上限を上げた (mixin/MaxHealthCapMixin) ので、それぞれの倍率を名前つきで持ち、最大体力 = 元の体力 × 倍率の積 にする。
//   倍率の名: party (人数。PartyScale)・resist (主の硬さ。PartyScale)・type (型ごと。PartyScale.typeResist)・level (入る人のレベル。LevelScale)・
//            own (心臓などが自分で決める硬さ)・abyss (深淵の心臓)。元の体力は最初に掛けたときに覚える (読み直しても重ならない)
// 体力の割合は保つ (掛け直しても、削った分はそのまま)
public final class Hardness {
    public static final String BASE = "skyabyss_base_hp";
    public static final String FACTORS = "skyabyss_hardness";
    static final String LAST = "skyabyss_hp_set";

    private Hardness() {
    }

    // 倍率を一つ書いて、体力を掛け直す (1 なら消す)
    public static void set(LivingEntity e, String name, double k) {
        CompoundTag t = e.getPersistentData().getCompound(FACTORS);
        boolean one = !(k > 0) || Math.abs(k - 1.0) <= 1.0E-6;
        if (one ? !t.contains(name) : t.contains(name) && Math.abs(t.getDouble(name) - k) < 1.0E-4) {
            return;
        }
        if (k > 0 && Math.abs(k - 1.0) > 1.0E-6) {
            t.putDouble(name, k);
        } else {
            t.remove(name);
        }
        e.getPersistentData().put(FACTORS, t);
        refresh(e);
    }

    public static double get(LivingEntity e, String name) {
        CompoundTag t = e.getPersistentData().getCompound(FACTORS);
        return t.contains(name) ? t.getDouble(name) : 1.0;
    }

    // 倍率の積
    public static double total(LivingEntity e) {
        CompoundTag t = e.getPersistentData().getCompound(FACTORS);
        double k = 1;
        for (String n : t.getAllKeys()) {
            k *= t.getDouble(n);
        }
        return k;
    }

    // 最大体力 = 元の体力 × 倍率の積。割合は保つ
    public static void refresh(LivingEntity e) {
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) {
            return;
        }
        // 元の体力: 最初は今の値。最後に掛けた値から誰か (主の作り直し・段) が変えていたら、その値を新しい元にする
        var data = e.getPersistentData();
        double base = data.contains(BASE) ? data.getDouble(BASE) : hp.getBaseValue();
        if (data.contains(LAST) && Math.abs(hp.getBaseValue() - data.getDouble(LAST)) > 0.01) {
            base = hp.getBaseValue();
        }
        data.putDouble(BASE, base);
        float frac = e.getMaxHealth() > 0 ? e.getHealth() / e.getMaxHealth() : 1f;
        hp.setBaseValue(base * total(e));
        data.putDouble(LAST, hp.getBaseValue());
        if (e.isAlive()) {
            e.setHealth(Math.max(1f, frac * e.getMaxHealth()));
        }
    }

    // 元の体力を今の値に置き直す (体力を作り直す主: 段で数字を入れ直すなど)。倍率は掛け直す
    public static void rebase(LivingEntity e, double base) {
        e.getPersistentData().putDouble(BASE, base);
        refresh(e);
    }
}
