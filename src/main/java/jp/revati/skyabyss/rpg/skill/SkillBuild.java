package jp.revati.skyabyss.rpg.skill;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.rpg.skill.tree.NodeEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// ビルドの数え方: 技の木の節 (docs/design/48。NodeEffects)・型の要 (鍵石)・熟練の刻印・外の修飾 (装備・紋・淵) を、
// 技の力・待ち・マナ・属性の修飾へまとめる。数字はすべて SkillRules の定数と木の JSON。回復・無敵は作らない。
// 木のセット・タグの数の足し・条件の特性 (31 の A03・A04・A15) は 48 で消した。タグの数は淵の鏡・誓いと紋の条件にだけ残る
public final class SkillBuild {
    private static final List<SkillModifier> MODIFIERS = new CopyOnWriteArrayList<>();

    static {
        // 淵・前世の技・紋の盤の数字 (docs/design/31 の A09・A14・B08・B09)
        MODIFIERS.add(SkillExt.INSTANCE);
    }

    private SkillBuild() {
    }

    public static void register(SkillModifier modifier) {
        MODIFIERS.add(modifier);
    }

    // ---------------------------------------------------------------- タグ (数えるだけ。淵の鏡・誓い、紋の条件)
    // そのタグを数えた数: 覚えている技 ＋ 付けている装飾 ＋ 手の武器の印 ＋ 外からの足し
    public static int tagCount(Player player, SkillTag tag) {
        SkillData d = SkillManager.get(player);
        int n = 0;
        for (Skill s : Skill.values()) {
            if (d.knows(s) && SkillTag.of(s) == tag) {
                n++;
            }
        }
        n += extraTagCount(player, tag);
        return n;
    }

    // 技のほかから来るタグの数 (装飾の品・手の武器の印・外の修飾)
    public static int extraTagCount(Player player, SkillTag tag) {
        int n = 0;
        try {
            for (ItemStack stack : Accessories.get(player).items) {
                if (!stack.isEmpty() && stack.getItem() instanceof SkillTagged t && t.skillTag() == tag) {
                    n++;
                }
            }
        } catch (RuntimeException ignored) {
            // 装飾の記録がまだないとき (起動の最中) は数えない
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && SkillMarks.power(sp, tag.tree) > 0) {
            n++;
        }
        for (SkillModifier m : MODIFIERS) {
            n += m.extraTags(player, tag);
        }
        return n;
    }

    // 「低 HP」(3❤ 以下)。木の節 low_hp_armor の条件
    public static boolean lowHp(Player player) {
        return player.isAlive() && player.getHealth() <= SkillRules.COND_LOW_HP;
    }

    // ---------------------------------------------------------------- 型の要 (鍵石)
    public static boolean has(SkillData d, SkillKeystone k) {
        return d.keystones.contains(k.id);
    }

    // ---------------------------------------------------------------- 傷
    // 近接・飛び道具・技の別なく、自分の与える傷に掛ける割合 (足す)。型の要と木の節
    public static double generalDamage(Player player, boolean projectile) {
        SkillData d = SkillManager.get(player);
        double v = 0;
        if (has(d, SkillKeystone.GLASS_BLADE)) {
            v += SkillRules.KEY_GLASS_DAMAGE;
        }
        if (has(d, SkillKeystone.ASH_PACT)) {
            v += SkillRules.KEY_ASH_DAMAGE;
        }
        if (has(d, SkillKeystone.WIND_DANCE)) {
            v += SkillRules.KEY_DANCE_DAMAGE;
        }
        if (projectile && has(d, SkillKeystone.LONE_STRING)) {
            v += SkillRules.KEY_STRING_PROJECTILE;
        }
        v += NodeEffects.sum(player, "attack");
        if (projectile) {
            v += NodeEffects.sum(player, "projectile");
        }
        return Math.max(-0.5, v);
    }

    // 技 (傷・支えの量) だけに掛かる割合: 熟練の刻印・木の節・外の修飾
    public static double skillPower(Player player, Skill skill) {
        double v = 0;
        if (SkillManager.runeOf(player, skill) == Rune.MASTERY) {
            v += SkillRules.MASTERY_RUNE_POWER;
        }
        v += NodeEffects.sum(player, "skill_power");
        for (SkillModifier m : MODIFIERS) {
            v += m.power(player, skill);
        }
        return Math.min(SkillRules.POWER_CAP, v);
    }

    // 支えの量にだけ足す割合 (歌の構えは 43 で削った。今は 0)
    public static double supportExtra(Player player) {
        return 0;
    }

    // 火の傷を減らす割合 (木の節)
    public static double fireReduction(Player player) {
        return NodeEffects.sum(player, "fire_resist");
    }

    // ---------------------------------------------------------------- 待ち・マナ
    public static double cooldownScale(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        double v = 0;
        if (has(d, SkillKeystone.LONE_STRING)) {
            v += SkillRules.KEY_STRING_COOLDOWN;
        }
        if (has(d, SkillKeystone.WIND_DANCE)) {
            v += SkillRules.KEY_DANCE_COOLDOWN;
        }
        if (SkillManager.runeOf(player, skill) == Rune.MASTERY) {
            v += SkillRules.MASTERY_RUNE_COOLDOWN;
        }
        for (SkillModifier m : MODIFIERS) {
            v += m.cooldown(player, skill);
        }
        v += NodeEffects.sum(player, "cooldown");
        return 1 + Math.max(SkillRules.COOLDOWN_MIN, Math.min(SkillRules.COOLDOWN_MAX, v));
    }

    public static double manaScale(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        double v = has(d, SkillKeystone.ASH_PACT) ? SkillRules.KEY_ASH_MANA_COST : 0;
        for (SkillModifier m : MODIFIERS) {
            v += m.manaCost(player, skill);
        }
        v += NodeEffects.sum(player, "mana_cost");
        return 1 + Math.max(-SkillRules.MANA_COST_CAP, Math.min(SkillRules.MANA_COST_CAP, v));
    }

    // マナの回復に足す割合 (満ち潮・木の節・淵)
    public static double regenAdd(Player player) {
        SkillData d = SkillManager.get(player);
        double v = 0;
        if (has(d, SkillKeystone.FULL_TIDE)) {
            v += SkillRules.KEY_TIDE_REGEN;
        }
        v += NodeEffects.sum(player, "regen");
        return v + SkillExt.regenAdd(player);
    }

    // ---------------------------------------------------------------- 属性の修飾 (SkillManager.applyModifiers が付ける)
    public record Attrs(double armor, double knockback, double move, double attackSpeed, double manaAdd, double manaMult) {
    }

    public static Attrs attrs(Player player) {
        SkillData d = SkillManager.get(player);
        // 木 v2 の節 (docs/design/48)
        double armor = NodeEffects.sum(player, "armor") + (lowHp(player) ? NodeEffects.sum(player, "low_hp_armor") : 0);
        double kb = NodeEffects.sum(player, "knockback");
        double move = NodeEffects.sum(player, "move");
        double aspeed = NodeEffects.sum(player, "attack_speed");
        double mana = NodeEffects.sum(player, "mana");
        double manaMult = 0;
        if (has(d, SkillKeystone.BULWARK)) {
            armor += SkillRules.KEY_BULWARK_ARMOR;
            kb += SkillRules.KEY_BULWARK_KNOCKBACK;
            move += SkillRules.KEY_BULWARK_MOVE;
        }
        if (has(d, SkillKeystone.GLASS_BLADE)) {
            armor += SkillRules.KEY_GLASS_ARMOR;
        }
        if (has(d, SkillKeystone.WIND_DANCE)) {
            move += SkillRules.KEY_DANCE_MOVE;
        }
        if (has(d, SkillKeystone.FULL_TIDE)) {
            manaMult += SkillRules.KEY_TIDE_MANA;
        }
        SkillExt.Attrs ext = SkillExt.attrs(player);
        armor += ext.armor();
        move += ext.move();
        mana += ext.mana();
        return new Attrs(armor, kb, move, aspeed, mana, manaMult);
    }
}
