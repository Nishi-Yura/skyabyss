package jp.revati.skyabyss.rpg.skill;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jp.revati.skyabyss.item.accessory.Accessories;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// ビルドの数え方: タグ・木のセット・キーストーン・構え・条件の特性・連鎖・熟練の刻印を、技の力・待ち・マナ・属性の修飾へまとめる。
// 数字はすべて SkillRules の定数と、ここの小さな表 (docs/design/31 の A03・A04・A11・A15・A20)。回復・無敵は作らない。
// 装備の側 (アスペクト・ソケット・のちの変異や紋の盤) は SkillModifier を register して数字を足す
public final class SkillBuild {
    private static final List<SkillModifier> MODIFIERS = new CopyOnWriteArrayList<>();

    private SkillBuild() {
    }

    public static void register(SkillModifier modifier) {
        MODIFIERS.add(modifier);
    }

    // ---------------------------------------------------------------- タグ
    // そのタグを数えた数: 覚えている技 (旅人の技も持ち味のタグで) ＋ 付けている装飾 ＋ 手の武器の印 ＋ 外からの足し
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

    public static double tagPower(Player player, SkillTag tag) {
        return tag == null ? 0 : SkillRules.TAG_POWER_PER * Math.min(SkillRules.TAG_COUNT_CAP, tagCount(player, tag));
    }

    // ---------------------------------------------------------------- 木のセット (A04)
    public enum SetKind {
        ARMOR, KNOCKBACK, MOVE, ATTACK_SPEED, MANA, REGEN, DAMAGE, PROJECTILE, COOLDOWN, FIRE
    }

    public record SetLine(SetKind kind, double value) {
    }

    private static final SetLine[][] SETS = {
            // 岩守り: 防ぎ
            {new SetLine(SetKind.ARMOR, 1), new SetLine(SetKind.KNOCKBACK, 0.10), new SetLine(SetKind.ARMOR, 2)},
            // 風読み: 足
            {new SetLine(SetKind.MOVE, 0.02), new SetLine(SetKind.ATTACK_SPEED, 0.03), new SetLine(SetKind.MOVE, 0.03)},
            // 潮歌い: マナ
            {new SetLine(SetKind.MANA, 4), new SetLine(SetKind.REGEN, 0.05), new SetLine(SetKind.MANA, 6)},
            // 灰の鍛え人: 傷と火
            {new SetLine(SetKind.DAMAGE, 0.02), new SetLine(SetKind.FIRE, 0.10), new SetLine(SetKind.DAMAGE, 0.03)},
            // 骨の射手: 矢と待ち
            {new SetLine(SetKind.PROJECTILE, 0.03), new SetLine(SetKind.COOLDOWN, -0.04), new SetLine(SetKind.PROJECTILE, 0.04)}};

    public static SetLine setLine(SkillTree tree, int tier) {
        SkillTag tag = SkillTag.of(tree);
        return tag == null || tier < 0 || tier > 2 ? null : SETS[tag.ordinal()][tier];
    }

    // その木に使った点で届いているセットの段 (0〜3)
    public static int setTier(SkillData d, SkillTree tree) {
        int p = d.treePoints(tree);
        int t = 0;
        for (int at : SkillRules.SET_AT) {
            if (p >= at) {
                t++;
            }
        }
        return t;
    }

    private static double setSum(SkillData d, SetKind kind) {
        double v = 0;
        for (SkillTag tag : SkillTag.values()) {
            int tier = setTier(d, tag.tree);
            for (int i = 0; i < tier; i++) {
                SetLine l = SETS[tag.ordinal()][i];
                if (l.kind == kind) {
                    v += l.value;
                }
            }
        }
        return v;
    }

    // ---------------------------------------------------------------- タグの 4・7 の足し
    // 4 つで 1 つ目、7 つで 2 つ目 (同じ値をもう一度)。数字は小さく、種類はタグごとに 1 つ
    private static SetLine tagStep(SkillTag tag) {
        return switch (tag) {
            case ROCK -> new SetLine(SetKind.ARMOR, 1);
            case WIND -> new SetLine(SetKind.MOVE, 0.02);
            case TIDE -> new SetLine(SetKind.MANA, 5);
            case ASH -> new SetLine(SetKind.DAMAGE, 0.02);
            case BONE -> new SetLine(SetKind.PROJECTILE, 0.03);
        };
    }

    public static SetLine tagStepLine(SkillTag tag) {
        return tagStep(tag);
    }

    private static double tagStepSum(Player player, SetKind kind) {
        double v = 0;
        for (SkillTag tag : SkillTag.values()) {
            SetLine l = tagStep(tag);
            if (l.kind != kind) {
                continue;
            }
            int n = tagCount(player, tag);
            int steps = (n >= SkillRules.TAG_STEP1 ? 1 : 0) + (n >= SkillRules.TAG_STEP2 ? 1 : 0);
            v += l.value * steps;
        }
        return v;
    }

    // ---------------------------------------------------------------- 条件の特性 (A15)
    // 岩: HP 3❤ 以下で防具 / 風: 予告をよけた直後 / 潮: マナが満タン / 灰: 無傷で完璧によけた直後 / 骨: HP 3❤ 以下で矢
    public static boolean lowHp(Player player) {
        return player.isAlive() && player.getHealth() <= SkillRules.COND_LOW_HP;
    }

    public static boolean fullMana(Player player, SkillData d) {
        return d.mana >= SkillManager.maxMana(player) * SkillRules.COND_FULL_MANA && d.mana > 0;
    }

    private static double condValue(Player player, SkillTag tag, double small, double big) {
        int n = tagCount(player, tag);
        return n >= SkillRules.COND_BIG_TAGS ? big : n >= SkillRules.COND_MIN_TAGS ? small : 0;
    }

    // 条件が今満ちているか (表示にも使う)
    public static boolean condActive(Player player, SkillTag tag) {
        SkillData d = SkillManager.get(player);
        long now = player.level().getGameTime();
        return switch (tag) {
            case ROCK, BONE -> lowHp(player);
            case WIND -> d.dodgeUntil >= now;
            case TIDE -> fullMana(player, d);
            case ASH -> d.perfectUntil >= now;
        };
    }

    public static double condDamage(Player player, boolean projectile) {
        double v = 0;
        if (condActive(player, SkillTag.WIND)) {
            v += condValue(player, SkillTag.WIND, 0.05, 0.08);
        }
        if (condActive(player, SkillTag.TIDE)) {
            v += condValue(player, SkillTag.TIDE, 0.04, 0.07);
        }
        if (condActive(player, SkillTag.ASH)) {
            v += condValue(player, SkillTag.ASH, 0.06, 0.10);
        }
        if (projectile && condActive(player, SkillTag.BONE)) {
            v += condValue(player, SkillTag.BONE, 0.08, 0.12);
        }
        return v;
    }

    private static double condArmor(Player player) {
        return condActive(player, SkillTag.ROCK) ? condValue(player, SkillTag.ROCK, 3, 4) : 0;
    }

    // ---------------------------------------------------------------- キーストーン・構え
    public static boolean has(SkillData d, SkillKeystone k) {
        return d.keystones.contains(k.id);
    }

    private static SkillStance stance(SkillData d) {
        return SkillStance.of(d.stance);
    }

    // ---------------------------------------------------------------- 傷
    // 近接・飛び道具・技の別なく、自分の与える傷に掛ける割合 (足す)。キーストーン・構え・条件の特性・セット・タグの足し
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
        SkillStance st = stance(d);
        if (st == SkillStance.GUARD) {
            v += SkillRules.STANCE_GUARD_DAMAGE;
        } else if (st == SkillStance.SONG) {
            v += SkillRules.STANCE_SONG_DAMAGE;
        }
        v += setSum(d, SetKind.DAMAGE) + tagStepSum(player, SetKind.DAMAGE);
        if (projectile) {
            v += setSum(d, SetKind.PROJECTILE) + tagStepSum(player, SetKind.PROJECTILE);
        }
        v += condDamage(player, projectile);
        return Math.max(-0.5, v);
    }

    // 技 (傷・支えの量) だけに掛かる割合: そのタグの数・熟練の刻印・連鎖・外の修飾
    public static double skillPower(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        double v = tagPower(player, SkillTag.of(skill));
        if (SkillManager.runeOf(player, skill) == Rune.MASTERY) {
            v += SkillRules.MASTERY_RUNE_POWER;
        }
        if (chained(player, skill)) {
            v += SkillRules.CHAIN_POWER;
        }
        for (SkillModifier m : MODIFIERS) {
            v += m.power(player, skill);
        }
        return Math.min(SkillRules.POWER_CAP, v);
    }

    // 支えの量にだけ足す割合 (歌の構え)
    public static double supportExtra(Player player) {
        return stance(SkillManager.get(player)) == SkillStance.SONG ? SkillRules.STANCE_SONG_SUPPORT : 0;
    }

    public static boolean chained(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        return skill.id.equals(d.chainSkill) && player.level().getGameTime() <= d.chainUntil;
    }

    // 火の傷を減らす割合 (灰の木のセット)
    public static double fireReduction(Player player) {
        return setSum(SkillManager.get(player), SetKind.FIRE);
    }

    // ---------------------------------------------------------------- 待ち・マナ
    public static double cooldownScale(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        double v = setSum(d, SetKind.COOLDOWN);
        SkillStance st = stance(d);
        if (st == SkillStance.SWIFT) {
            v += SkillRules.STANCE_SWIFT_COOLDOWN;
        }
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
        return 1 + Math.max(SkillRules.COOLDOWN_MIN, Math.min(SkillRules.COOLDOWN_MAX, v));
    }

    public static double manaScale(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        double v = has(d, SkillKeystone.ASH_PACT) ? SkillRules.KEY_ASH_MANA_COST : 0;
        for (SkillModifier m : MODIFIERS) {
            v += m.manaCost(player, skill);
        }
        return 1 + Math.max(-SkillRules.MANA_COST_CAP, Math.min(SkillRules.MANA_COST_CAP, v));
    }

    // マナの回復に足す割合 (歌の構え・満ち潮・潮のセット)
    public static double regenAdd(Player player) {
        SkillData d = SkillManager.get(player);
        double v = setSum(d, SetKind.REGEN);
        if (stance(d) == SkillStance.SONG) {
            v += SkillRules.STANCE_SONG_REGEN;
        }
        if (has(d, SkillKeystone.FULL_TIDE)) {
            v += SkillRules.KEY_TIDE_REGEN;
        }
        return v;
    }

    // ---------------------------------------------------------------- 属性の修飾 (SkillManager.applyModifiers が付ける)
    public record Attrs(double armor, double knockback, double move, double attackSpeed, double manaAdd, double manaMult) {
    }

    public static Attrs attrs(Player player) {
        SkillData d = SkillManager.get(player);
        SkillStance st = stance(d);
        double armor = setSum(d, SetKind.ARMOR) + tagStepSum(player, SetKind.ARMOR) + condArmor(player);
        double kb = setSum(d, SetKind.KNOCKBACK);
        double move = setSum(d, SetKind.MOVE) + tagStepSum(player, SetKind.MOVE);
        double aspeed = setSum(d, SetKind.ATTACK_SPEED);
        double mana = setSum(d, SetKind.MANA) + tagStepSum(player, SetKind.MANA);
        double manaMult = 0;
        if (st == SkillStance.GUARD) {
            armor += SkillRules.STANCE_GUARD_ARMOR;
            kb += SkillRules.STANCE_GUARD_KNOCKBACK;
        } else if (st == SkillStance.SWIFT) {
            move += SkillRules.STANCE_SWIFT_MOVE;
            armor += SkillRules.STANCE_SWIFT_ARMOR;
        }
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
        return new Attrs(armor, kb, move, aspeed, mana, manaMult);
    }
}
