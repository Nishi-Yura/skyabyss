package jp.revati.skyabyss.rpg.skill.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillKeystone;

// 技の木 v2 の節 (docs/design/48 の 2.4)。assets/skyabyss/skill_tree/<職>.json の nodes の一つ。
//   kind      : ACTIVE (使う技。取れるのは 4 本まで) / RANK (その技の段 +1) / PASSIVE (常に効く技) / KEYSTONE (型の要 = 要の技)
//               RUNE (技を変える節 = 刻印) / STAT (基礎 +amount) / THRESHOLD (基礎が at 以上のとき effect) / EFFECT (常に effect)
//               TRAIT (古い鍵石をそのまま型の要にしたもの。keystone)
//   cost      : 技の点
//   x, y      : 格子の位置 (左上が 0, 0。画面は x * 48, y * 48)
//   arch      : 型 (枝) の id。幹は null。archPoints = その型の点がこれ以上で取れる
//   requires  : つながる節 (どれか一つ取っていればよい)。空なら幹の入口
//   excludes  : 排他 (どれかを取っていると取れない)。刻印どうしは同じ技なら自動で排他
//   effect    : 常に効く数 (NodeEffects の鍵 → 値)。armor・knockback・move・attack_speed・attack・projectile・skill_power・cooldown・
//               mana_cost・mana・regen・damage_taken・damage_taken_still・fire_resist・big_target・lifesteal・low_hp_armor
public final class AbilityNode {
    public enum Kind {
        ACTIVE, RANK, PASSIVE, KEYSTONE, RUNE, STAT, THRESHOLD, EFFECT, TRAIT;

        public static Kind byId(String id) {
            return valueOf(id.toUpperCase(Locale.ROOT));
        }

        public String key() {
            return "gui.skyabyss.tree.kind." + name().toLowerCase(Locale.ROOT);
        }
    }

    public final String id;
    public final Kind kind;
    public final int cost;
    public final int x;
    public final int y;
    @Nullable
    public final String arch;
    public final int archPoints;
    public final List<String> requires;
    public final List<String> excludes;
    @Nullable
    public final Skill skill;
    public final int rune;
    @Nullable
    public final Stat stat;
    public final int amount;
    public final int at;
    public final Map<String, Double> effect;
    @Nullable
    public final SkillKeystone keystone;

    AbilityNode(String id, Kind kind, int cost, int x, int y, @Nullable String arch, int archPoints, List<String> requires, List<String> excludes,
            @Nullable Skill skill, int rune, @Nullable Stat stat, int amount, int at, Map<String, Double> effect, @Nullable SkillKeystone keystone) {
        this.id = id;
        this.kind = kind;
        this.cost = cost;
        this.x = x;
        this.y = y;
        this.arch = arch;
        this.archPoints = archPoints;
        this.requires = Collections.unmodifiableList(requires);
        this.excludes = Collections.unmodifiableList(excludes);
        this.skill = skill;
        this.rune = rune;
        this.stat = stat;
        this.amount = amount;
        this.at = at;
        this.effect = Collections.unmodifiableMap(effect);
        this.keystone = keystone;
    }

    static AbilityNode parse(String job, JsonObject o) {
        String id = o.get("id").getAsString();
        Kind kind = Kind.byId(o.get("kind").getAsString());
        int cost = o.has("cost") ? o.get("cost").getAsInt() : 1;
        int x = o.get("x").getAsInt();
        int y = o.get("y").getAsInt();
        String arch = o.has("arch") && !o.get("arch").isJsonNull() ? o.get("arch").getAsString() : null;
        int archPoints = o.has("arch_points") ? o.get("arch_points").getAsInt() : 0;
        List<String> requires = strings(o.get("requires"));
        List<String> excludes = strings(o.get("excludes"));
        Skill skill = null;
        if (o.has("skill")) {
            skill = Skill.byId(o.get("skill").getAsString());
            if (skill == null) {
                throw new IllegalArgumentException(job + "/" + id + ": unknown skill " + o.get("skill").getAsString());
            }
        }
        int rune = o.has("rune") ? o.get("rune").getAsInt() : 0;
        Stat stat = o.has("stat") ? Stat.byId(o.get("stat").getAsString()) : null;
        int amount = o.has("amount") ? o.get("amount").getAsInt() : 0;
        int at = o.has("at") ? o.get("at").getAsInt() : 0;
        Map<String, Double> effect = new LinkedHashMap<>();
        if (o.has("effect")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("effect").entrySet()) {
                effect.put(e.getKey(), e.getValue().getAsDouble());
            }
        }
        SkillKeystone keystone = o.has("keystone") ? SkillKeystone.byId(o.get("keystone").getAsString()) : null;
        // 種類ごとの決まり
        switch (kind) {
            case ACTIVE, RANK, PASSIVE, KEYSTONE -> {
                if (skill == null) {
                    throw new IllegalArgumentException(job + "/" + id + ": " + kind + " needs a skill");
                }
                if (kind == Kind.ACTIVE && !skill.active || kind == Kind.PASSIVE && (skill.active || skill.keystone())
                        || kind == Kind.KEYSTONE && !skill.keystone()) {
                    throw new IllegalArgumentException(job + "/" + id + ": " + kind + " does not match skill " + skill.id);
                }
            }
            case RUNE -> {
                if (skill == null || !Rune.valid(skill, rune) || rune >= 4) {
                    throw new IllegalArgumentException(job + "/" + id + ": RUNE needs a skill and a rune 1..3");
                }
            }
            case STAT -> {
                if (stat == null || amount <= 0) {
                    throw new IllegalArgumentException(job + "/" + id + ": STAT needs stat and amount");
                }
            }
            case THRESHOLD -> {
                if (stat == null || at <= 0 || effect.isEmpty()) {
                    throw new IllegalArgumentException(job + "/" + id + ": THRESHOLD needs stat, at and effect");
                }
            }
            case EFFECT -> {
                if (effect.isEmpty()) {
                    throw new IllegalArgumentException(job + "/" + id + ": EFFECT needs effect");
                }
            }
            case TRAIT -> {
                if (keystone == null) {
                    throw new IllegalArgumentException(job + "/" + id + ": TRAIT needs keystone");
                }
            }
            default -> {
            }
        }
        return new AbilityNode(id, kind, cost, x, y, arch, archPoints, requires, excludes, skill, rune, stat, amount, at, effect, keystone);
    }

    private static List<String> strings(@Nullable JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) {
            return out;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) {
                out.add(x.getAsString());
            }
        } else {
            out.add(e.getAsString());
        }
        return out;
    }

    // 使う技を取る節か (4 本までの数え方)
    public boolean countsAsActive() {
        return kind == Kind.ACTIVE;
    }

    // 技に関わる節か
    public boolean aboutSkill() {
        return skill != null;
    }

    // 名前の翻訳キー (節ごとの文は node.skyabyss.<職>.<id>)
    public String nameKey(String job) {
        return switch (kind) {
            case ACTIVE, PASSIVE, KEYSTONE, RANK -> skill.nameKey();
            case RUNE -> Rune.of(skill, rune).nameKey();
            case STAT -> stat.nameKey();
            case TRAIT -> keystone.nameKey();
            default -> "node.skyabyss." + job + "." + id;
        };
    }

    public String effectKey(String job) {
        return switch (kind) {
            case RUNE -> Rune.of(skill, rune).effectKey();
            case TRAIT -> keystone.effectKey();
            case ACTIVE, PASSIVE, KEYSTONE, RANK -> skill.effectKey();
            default -> "node.skyabyss." + job + "." + id + ".effect";
        };
    }
}
