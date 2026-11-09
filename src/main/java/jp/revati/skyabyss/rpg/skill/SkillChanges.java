package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

// 今その技の動きを変えているもの (2026-11-01 の声「場合によってスキルが変わる時あるけど、スキル画面だったりで見た目が変わらないので分かりづらい」)。
// 技の帯 (client/skill/SkillHud) の印と、技の画面 (SkillScreen) の説明の行が同じものを見る。サーバーでもクライアントでも動く
//   RUNE      刻印 1〜3 (極めて選んだ形)
//   REWRITE   刻印 4 (逸品・伝説・神器の書き換え)。その武器を利き手に持つときだけ効く (持っていなければ active = false)
//   MUTATION  変異 (極めた技の形の違う中身)
//   SECRET    枠に付けた秘伝 (SecretArts)
public final class SkillChanges {
    public enum Kind {
        RUNE(0xF2D78A, "gui.skyabyss.skill.change.rune"),
        REWRITE(0xFFB45A, "gui.skyabyss.skill.change.rewrite"),
        MUTATION(0xC8A0FF, "gui.skyabyss.skill.change.mutation"),
        SECRET(0x9CE0F0, "gui.skyabyss.skill.change.secret");

        public final int rgb;
        public final String key;

        Kind(int rgb, String key) {
            this.rgb = rgb;
            this.key = key;
        }
    }

    public record Change(Kind kind, Component name, boolean active, Component effect) {
        public Change(Kind kind, Component name, boolean active) {
            this(kind, name, active, null);
        }

        // 説明の一行: 「書き換え: 天穹の手」(効いていなければ「(その武器を持つと効く)」を添える)
        public Component line() {
            Component c = Component.translatable(kind.key, name);
            return active ? c : Component.translatable("gui.skyabyss.skill.change.inactive", c);
        }
    }

    private SkillChanges() {
    }

    public static List<Change> of(Player p, Skill s) {
        List<Change> out = new ArrayList<>();
        if (p == null || s == null) {
            return out;
        }
        SkillData d = SkillManager.get(p);
        Mutation m = SkillExtOps.active(p, s);
        if (m != null) {
            out.add(new Change(Kind.MUTATION, Component.translatable(m.nameKey()), true, Component.translatable(m.nameKey() + ".effect")));
        }
        int r = d.rune(s);
        if (r >= 1 && r <= 3 && d.runeUsable(s, r)) {
            Rune rune = Rune.of(s, r);
            if (rune != null) {
                out.add(new Change(Kind.RUNE, Component.translatable(rune.nameKey()), true, Component.translatable(rune.effectKey())));
            }
        }
        // 武器の書き換え (持つあいだだけ。選ぶ手間はない)
        Rune held = SkillManager.legendRune(p, s);
        if (held != null) {
            out.add(new Change(Kind.REWRITE, Component.translatable(held.nameKey()), true, Component.translatable(held.effectKey())));
        }
        for (SecretArts.Held h : SecretArts.heldFor(p)) {
            if (h.art().skill == s) {
                out.add(new Change(Kind.SECRET, SecretArts.name(h.art(), h.tier()), true));
            }
        }
        return out;
    }

    // 今の形の絵: 武器の書き換えが効いていれば書き換えの絵 (持ち替えるとすぐ替わる。2026-11-09 の声「武器のスキル変化もリアルタイムで右下のアイコン変わってほしい」)、
    // 変異していれば変異の絵 (案 A)、ほかは元の技の絵
    public static int icon(Player p, Skill s) {
        if (p == null || s == null) {
            return s == null ? 0 : s.iconIndex();
        }
        Rune held = SkillManager.legendRune(p, s);
        if (held != null) {
            return held.iconIndex();
        }
        Mutation m = SkillExtOps.active(p, s);
        return m != null ? m.iconIndex() : s.iconIndex();
    }

    // 絵の縁の色: 効いている刻印・書き換え・秘伝のうち一番上 (変異は絵が替わるので縁は付けない。無ければ 0)
    public static int frameColor(Player p, Skill s) {
        for (Change c : of(p, s)) {
            if (c.active() && c.kind() != Kind.MUTATION && c.kind() != Kind.REWRITE) {
                return c.kind().rgb;
            }
        }
        return 0;
    }

    // 帯の印の色: 効いている変化のうち一番上 (無ければ 0)
    public static int markColor(Player p, Skill s) {
        for (Change c : of(p, s)) {
            if (c.active()) {
                return c.kind().rgb;
            }
        }
        return 0;
    }
}
