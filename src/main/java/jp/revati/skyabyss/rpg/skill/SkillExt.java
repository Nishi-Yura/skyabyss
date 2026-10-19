package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.entity.player.Player;

// 橋・淵・前世の技・紋の盤の「数字の口」(docs/design/31 の A09・A14・B08・B09)。SkillBuild が最初から数に入れる (SkillModifier として)。
// 変異 (A08) は数字を変えず形だけ変える (MutationActions)。ここの足しはすべて SkillBuild の上限 (力 +100%・待ち −40%〜+50%・マナ ±50%) の内側に入る。
// 回復・無敵は作らない
final class SkillExt implements SkillModifier {
    static final SkillExt INSTANCE = new SkillExt();

    private SkillExt() {
    }

    private static int rebirth(Player player) {
        return SkillManager.get(player).rebirth;
    }

    // 覚えている技だけで数えたタグの数 (外からの足しを含まない。淵の貢ぎが使う)
    static int baseTags(SkillData d, SkillTag tag) {
        int n = 0;
        for (Skill s : Skill.values()) {
            if (d.knows(s) && (SkillTag.of(s) == tag || SkillTag.second(s) == tag)) {
                n++;
            }
        }
        return n;
    }

    // 淵の貢ぎ: いちばん多いタグ以外で、1 つ以上あるタグのうち多い順に rank 種へ +1
    static List<SkillTag> tithed(SkillData d) {
        int r = d.rank(Skill.DEEP_TITHE);
        List<SkillTag> out = new ArrayList<>();
        if (r <= 0) {
            return out;
        }
        List<SkillTag> tags = new ArrayList<>(List.of(SkillTag.values()));
        tags.sort(Comparator.<SkillTag>comparingInt(t -> -baseTags(d, t)).thenComparingInt(Enum::ordinal));
        for (int i = 1; i < tags.size() && out.size() < r; i++) {
            if (baseTags(d, tags.get(i)) >= 1) {
                out.add(tags.get(i));
            }
        }
        return out;
    }

    // 1 つ以上あるタグの種類の数 (淵の鏡・淵の誓いの条件)
    static int distinctTags(Player player) {
        int n = 0;
        for (SkillTag t : SkillTag.values()) {
            if (SkillBuild.tagCount(player, t) >= 1) {
                n++;
            }
        }
        return n;
    }

    @Override
    public double power(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        double v = Glyph.sum(d, d.rebirth, skill, Glyph.Effect.POWER);
        int mirror = d.rank(Skill.DEEP_MIRROR);
        if (mirror > 0 && distinctTags(player) >= SkillRules.MIRROR_TAGS) {
            v += SkillRules.mirrorPower(mirror);
        }
        if (d.knows(Skill.DEEP_OATH) && distinctTags(player) >= SkillTag.values().length) {
            v += SkillRules.OATH_DEEP_POWER;
        }
        v += SkillRules.memoryPower(d.rank(Skill.MEMORY_EDGE), d.rebirth);
        if (d.echoUntil > player.level().getGameTime()) {
            v += SkillRules.echoPower(Math.max(1, d.rank(Skill.DEEP_ECHO)));
        }
        int aim = d.rank(Skill.STONE_AIM);
        if (aim > 0 && SkillTag.of(skill) == SkillTag.BONE && d.stillTicks >= SkillRules.AIM_TICKS) {
            v += SkillRules.aimPower(aim);
        }
        return v;
    }

    @Override
    public double cooldown(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        return Glyph.sum(d, d.rebirth, skill, Glyph.Effect.COOLDOWN) + (d.knows(Skill.SOUL_THREAD) ? SkillRules.THREAD_COOLDOWN : 0);
    }

    @Override
    public double manaCost(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        return Glyph.sum(d, d.rebirth, skill, Glyph.Effect.MANA);
    }

    @Override
    public int extraTags(Player player, SkillTag tag) {
        SkillData d = SkillManager.get(player);
        int n = Glyph.extraTags(d, d.rebirth, tag);
        if (tithed(d).contains(tag)) {
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- 属性・マナ回復 (SkillBuild.attrs / regenAdd が足す)
    record Attrs(double armor, double move, double mana) {
    }

    static Attrs attrs(Player player) {
        SkillData d = SkillManager.get(player);
        int vigil = d.rank(Skill.OLD_VIGIL);
        int aim = d.rank(Skill.STONE_AIM);
        double armor = SkillRules.vigilArmor(vigil) + (aim > 0 && d.stillTicks >= SkillRules.AIM_TICKS ? SkillRules.aimArmor(aim) : 0);
        return new Attrs(armor, SkillRules.seaMove(d.rank(Skill.SEA_BREEZE)) * (d.rank(Skill.SEA_BREEZE) > 0 ? 1 : 0),
                SkillRules.vigilMana(vigil));
    }

    static double regenAdd(Player player) {
        SkillData d = SkillManager.get(player);
        double v = d.rank(Skill.SEA_BREEZE) > 0 ? SkillRules.seaRegen(d.rank(Skill.SEA_BREEZE)) : 0;
        if (d.knows(Skill.DEEP_OATH) && distinctTags(player) >= SkillTag.values().length) {
            v += SkillRules.OATH_DEEP_REGEN;
        }
        return v;
    }
}
