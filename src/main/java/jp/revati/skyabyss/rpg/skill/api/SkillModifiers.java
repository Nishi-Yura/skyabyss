package jp.revati.skyabyss.rpg.skill.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jp.revati.skyabyss.rpg.skill.Skill;
import net.minecraft.world.entity.player.Player;

// SkillModifier の登録と、掛け合わせ。技のコードはここの power / cooldown だけを呼ぶ。
//  - SkillEffects.damagePower / supportPower: 既存の足し算 (共鳴の声・木の印…) の結果に power を掛ける
//  - SkillManager.cooldownFor: 待ちに cooldown を掛ける
public final class SkillModifiers {
    // 修飾の力の倍率の合計の上限 (docs/design/31 の A06)
    public static final double POWER_CAP = 2.0;
    public static final double COOLDOWN_FLOOR = 0.5;

    private static final List<SkillModifier> PROVIDERS = new CopyOnWriteArrayList<>();

    private SkillModifiers() {
    }

    public static void register(SkillModifier modifier) {
        if (!PROVIDERS.contains(modifier)) {
            PROVIDERS.add(modifier);
        }
    }

    public static double power(Player player, Skill skill) {
        if (PROVIDERS.isEmpty()) {
            return 1.0;
        }
        double v = 1.0;
        for (SkillModifier m : PROVIDERS) {
            v *= m.powerMultiplier(player, skill);
        }
        return Math.min(POWER_CAP, v);
    }

    public static double cooldown(Player player, Skill skill) {
        if (PROVIDERS.isEmpty()) {
            return 1.0;
        }
        double v = 1.0;
        for (SkillModifier m : PROVIDERS) {
            v *= m.cooldownMultiplier(player, skill);
        }
        return Math.max(COOLDOWN_FLOOR, v);
    }
}
