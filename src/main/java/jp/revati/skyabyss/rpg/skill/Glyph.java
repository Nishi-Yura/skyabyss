package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;

// 紋 (A14。Diablo IV の紋章盤のように、穴に差した紋が中心の技とその隣の技を強める)。docs/design/31
// 紋は 5 つのタグ (岩・風・潮・灰・骨) に 1 種ずつ、段は 1〜3。試練を越えるともらう (SkillGlyphs.award)。
// 盤の穴は最初 3、転生 1 回ごとに 1 つ開いて最大 6。穴ごとに「中心の技」(覚えている技) を選ぶ。
// 中心の技は全部の値、隣の技 (木の前提の線でつながる技・橋とその親の頂点) は隣の値を受ける。差している間、そのタグを +1 数える (A03 の数)。
// 合計には上限 (力 +20%・マナ −10%・待ち −20%) があり、傷や回復を直接足さない (技の力・待ち・マナの修飾だけ)
public enum Glyph {
    ROCK("rock", SkillTag.ROCK, Effect.POWER, 0.08, 0.04),
    WIND("wind", SkillTag.WIND, Effect.COOLDOWN, -0.08, -0.04),
    TIDE("tide", SkillTag.TIDE, Effect.MANA, -0.12, -0.06),
    ASH("ash", SkillTag.ASH, Effect.POWER, 0.12, 0.0),
    BONE("bone", SkillTag.BONE, Effect.COOLDOWN, -0.12, 0.0);

    public enum Effect {
        POWER, COOLDOWN, MANA
    }

    public static final int MAX_LEVEL = 3;
    private static final double[] LEVEL_SCALE = {1.0, 1.25, 1.5};

    // 持っている紋 1 つ (種類と段)
    public record Item(Glyph glyph, int level) {
        public String code() {
            return glyph.id + "/" + level;
        }
    }

    // 盤に差した紋 (中心の技つき)
    public record Placed(Item item, Skill center) {
        public String code() {
            return item.code() + "@" + center.id;
        }
    }

    public final String id;
    public final SkillTag tag;
    public final Effect effect;
    // 段 1 のときの、中心の技と隣の技への値 (力は +、待ち・マナは −)
    public final double center;
    public final double neighbor;

    Glyph(String id, SkillTag tag, Effect effect, double center, double neighbor) {
        this.id = id;
        this.tag = tag;
        this.effect = effect;
        this.center = center;
        this.neighbor = neighbor;
    }

    public double centerValue(int level) {
        return center * LEVEL_SCALE[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
    }

    public double neighborValue(int level) {
        return neighbor * LEVEL_SCALE[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
    }

    public String nameKey() {
        return "glyph.skyabyss." + id;
    }

    public String effectKey() {
        return "glyph.skyabyss." + id + ".effect";
    }

    public static Glyph byId(String id) {
        for (Glyph g : values()) {
            if (g.id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    public static Item parse(String code) {
        if (code == null) {
            return null;
        }
        int slash = code.indexOf('/');
        if (slash <= 0 || code.indexOf('@') >= 0) {
            return null;
        }
        Glyph g = byId(code.substring(0, slash));
        try {
            int level = Integer.parseInt(code.substring(slash + 1));
            return g == null || level < 1 || level > MAX_LEVEL ? null : new Item(g, level);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Placed parsePlaced(String code) {
        if (code == null) {
            return null;
        }
        int at = code.indexOf('@');
        if (at <= 0) {
            return null;
        }
        Item item = parse(code.substring(0, at));
        Skill center = Skill.byId(code.substring(at + 1));
        return item == null || center == null ? null : new Placed(item, center);
    }

    // 隣の技: 木の前提の線でつながる技 (上下)
    public static List<Skill> neighbors(Skill center) {
        // 木 v2 (48): 隣は木の線でつながる技の節 (間の基礎・常に効く数の節は一つ飛ばして数えない)
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(center.tree);
        if (tree != null) {
            List<Skill> out = new ArrayList<>();
            jp.revati.skyabyss.rpg.skill.tree.AbilityNode mine = tree.skillNode(center);
            if (mine == null) {
                return out;
            }
            for (jp.revati.skyabyss.rpg.skill.tree.AbilityNode n : tree.nodes()) {
                if (n.skill == null || n.skill == center || n == tree.skillNode(n.skill) == false) {
                    continue;
                }
                if (n.requires.contains(mine.id) || mine.requires.contains(n.id)) {
                    out.add(n.skill);
                }
            }
            return out;
        }
        List<Skill> list = new ArrayList<>(center.requires());
        for (Skill s : Skill.values()) {
            if (s.requires().contains(center)) {
                list.add(s);
            }
        }
        return list;
    }

    // その技を中心に差している紋 (開いている穴の中。無ければ null)。木の画面の印
    @javax.annotation.Nullable
    public static Item centerGlyph(SkillData d, int rebirth, Skill skill) {
        int open = SkillRules.glyphSockets(rebirth);
        for (int i = 0; i < Math.min(open, d.glyphSockets.length); i++) {
            Placed p = parsePlaced(d.glyphSockets[i]);
            if (p != null && p.center == skill) {
                return p.item;
            }
        }
        return null;
    }

    // 開いている穴の数だけ見て、その技が受ける合計 (上限つき。力は +、待ち・マナは − の割合)
    public static double sum(SkillData d, int rebirth, Skill skill, Effect effect) {
        double v = 0;
        int open = SkillRules.glyphSockets(rebirth);
        for (int i = 0; i < Math.min(open, d.glyphSockets.length); i++) {
            Placed p = parsePlaced(d.glyphSockets[i]);
            if (p == null || p.item.glyph.effect != effect || !d.knows(p.center)) {
                continue;
            }
            if (p.center == skill) {
                v += p.item.glyph.centerValue(p.item.level);
            } else if (neighbors(p.center).contains(skill)) {
                v += p.item.glyph.neighborValue(p.item.level);
            }
        }
        return switch (effect) {
            case POWER -> Math.min(SkillRules.GLYPH_POWER_CAP, v);
            case COOLDOWN -> Math.max(-SkillRules.GLYPH_COOLDOWN_CAP, v);
            case MANA -> Math.max(-SkillRules.GLYPH_MANA_CAP, v);
        };
    }

    // 差している紋が数えさせるタグ (その紋のタグ 1 つにつき +1)
    public static int extraTags(SkillData d, int rebirth, SkillTag tag) {
        int n = 0;
        int open = SkillRules.glyphSockets(rebirth);
        for (int i = 0; i < Math.min(open, d.glyphSockets.length); i++) {
            Placed p = parsePlaced(d.glyphSockets[i]);
            if (p != null && p.item.glyph.tag == tag && d.knows(p.center)) {
                n++;
            }
        }
        return n;
    }
}
