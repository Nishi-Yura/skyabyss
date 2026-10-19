package jp.revati.skyabyss.item.extra;

import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.api.SkillModifier;
import jp.revati.skyabyss.rpg.skill.api.SkillModifiers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// 装備の技の修飾 (A06 ルーン・A03/A07 属性タグ) を、技の側 (SkillModifier) へつなぐ。
//  - ルーン: その技のルーンは、身につけている装備の中で最もよい一枚だけ (1 つの技に 1 枚)
//  - 属性タグ: 差した欠片一つにつき、その木の技の力 +3%
//  - 合計の力は SkillModifiers で ×2.0 を超えない
public final class GearSkillModifier implements SkillModifier {
    public static final GearSkillModifier INSTANCE = new GearSkillModifier();

    private GearSkillModifier() {
    }

    public static void register() {
        SkillModifiers.register(INSTANCE);
    }

    @Override
    public double powerMultiplier(Player player, Skill skill) {
        double best = 1.0;
        double tag = 0;
        for (ItemStack s : GearFreedom.worn(player)) {
            for (Gem g : GearFreedom.extras(s).gems()) {
                if (Gem.RUNE_POWER.equals(g.kind()) && g.arg().equals(skill.id)) {
                    best = Math.max(best, g.multiplier());
                } else if (g.isShard()) {
                    Element e = Element.byId(g.arg());
                    if (e != null && e.tree == skill.tree) {
                        tag += Element.TAG_POWER;
                    }
                }
            }
        }
        return best * (1 + tag);
    }

    @Override
    public double cooldownMultiplier(Player player, Skill skill) {
        double best = 1.0;
        for (ItemStack s : GearFreedom.worn(player)) {
            for (Gem g : GearFreedom.extras(s).gems()) {
                if (Gem.RUNE_COOLDOWN.equals(g.kind()) && g.arg().equals(skill.id)) {
                    best = Math.min(best, g.multiplier());
                }
            }
        }
        return best;
    }
}
