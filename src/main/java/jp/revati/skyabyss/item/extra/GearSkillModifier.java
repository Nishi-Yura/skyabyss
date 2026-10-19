package jp.revati.skyabyss.item.extra;

import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillBuild;
import jp.revati.skyabyss.rpg.skill.SkillModifier;
import jp.revati.skyabyss.rpg.skill.SkillTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// 装備の技の修飾 (A06 ルーン・A03/A07 属性タグ) を SkillBuild の SkillModifier へつなぐ。
//  - ルーン: その技のルーンは、身につけている装備の中で最もよい一枚だけ (1 つの技に 1 枚)。力は +10〜30%、待ちは −10〜20%
//  - 属性タグ: 差した欠片一つが、その木のタグを 1 つ数える (数えた数の効きは SkillBuild.tagPower)
//  - 技の力の合計は SkillBuild で +100% (×2.0) を超えない
public final class GearSkillModifier implements SkillModifier {
    public static final GearSkillModifier INSTANCE = new GearSkillModifier();

    private GearSkillModifier() {
    }

    public static void register() {
        SkillBuild.register(INSTANCE);
    }

    @Override
    public double power(Player player, Skill skill) {
        double best = 0;
        for (ItemStack s : GearFreedom.worn(player)) {
            for (Gem g : GearFreedom.extras(s).gems()) {
                if (Gem.RUNE_POWER.equals(g.kind()) && g.arg().equals(skill.id)) {
                    best = Math.max(best, g.multiplier() - 1);
                }
            }
        }
        return best;
    }

    @Override
    public double cooldown(Player player, Skill skill) {
        double best = 0;
        for (ItemStack s : GearFreedom.worn(player)) {
            for (Gem g : GearFreedom.extras(s).gems()) {
                if (Gem.RUNE_COOLDOWN.equals(g.kind()) && g.arg().equals(skill.id)) {
                    best = Math.min(best, g.multiplier() - 1);
                }
            }
        }
        return best;
    }

    @Override
    public int extraTags(Player player, SkillTag tag) {
        int n = 0;
        for (ItemStack s : GearFreedom.worn(player)) {
            for (Element e : GearFreedom.tags(s)) {
                if (e.tree == tag.tree) {
                    n++;
                }
            }
        }
        return n;
    }
}
