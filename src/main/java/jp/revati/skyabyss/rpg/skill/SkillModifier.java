package jp.revati.skyabyss.rpg.skill;

import net.minecraft.world.entity.player.Player;

// 技の数字を外から変える口 (装備の側・のちの変異や紋の盤が付ける)。SkillBuild.register で登録する。
// 返すのは「足す割合」(0.1 = +10%)。技の力の合計は +100%、待ちは −40%〜+50%、マナは ±50% で頭打ちにする (SkillBuild)。
// 回復・無敵はここから作らない (docs/design/04 の 7.7・7.12)
public interface SkillModifier {
    // 技の傷・支えの量
    default double power(Player player, Skill skill) {
        return 0;
    }

    // 技の待ち (負で短く)
    default double cooldown(Player player, Skill skill) {
        return 0;
    }

    // 技のマナ (負で安く)
    default double manaCost(Player player, Skill skill) {
        return 0;
    }

    // そのタグに足して数える数 (ソケットの欠片・アスペクトなど)
    default int extraTags(Player player, SkillTag tag) {
        return 0;
    }
}
