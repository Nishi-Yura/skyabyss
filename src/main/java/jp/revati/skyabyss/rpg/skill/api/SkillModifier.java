package jp.revati.skyabyss.rpg.skill.api;

import jp.revati.skyabyss.rpg.skill.Skill;
import net.minecraft.world.entity.player.Player;

// 技の修飾の差し込み口。装備のルーン・欠片 (item/extra/GearSkillModifier) のように、技のコードの外から
// 「その技の力」「その技の待ち」へ倍率を足す。値は倍率 (1.0 = 変えない)。複数の提供元は掛け合わせ、
// 力は SkillModifiers.POWER_CAP (×2.0) で頭打ち、待ちは COOLDOWN_FLOOR (×0.5) で下止めする。
public interface SkillModifier {
    default double powerMultiplier(Player player, Skill skill) {
        return 1.0;
    }

    default double cooldownMultiplier(Player player, Skill skill) {
        return 1.0;
    }
}
