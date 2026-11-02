package jp.revati.skyabyss.rpg.skill.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillTree;

// 木一本ぶんの技の木 v2 (docs/design/48)。職の木 (job あり) と、職をまたぐ木 (淵・前世。job は null)。節の表と、型 (枝) の名前。
// 読むだけ (記録は SkillData.nodes。鍵 key = 職の id か木の id)
public final class AbilityTree {
    @Nullable
    public final Job job;
    public final SkillTree tree;
    public final String key;
    public final List<String> archetypes;
    private final Map<String, AbilityNode> nodes = new LinkedHashMap<>();
    public final int width;
    public final int height;
    public final int totalCost;

    AbilityTree(String key, SkillTree tree, @Nullable Job job, List<String> archetypes, List<AbilityNode> list) {
        this.key = key;
        this.tree = tree;
        this.job = job;
        this.archetypes = Collections.unmodifiableList(archetypes);
        int w = 0;
        int h = 0;
        int cost = 0;
        for (AbilityNode n : list) {
            if (nodes.put(n.id, n) != null) {
                throw new IllegalArgumentException(key + ": duplicate node " + n.id);
            }
            w = Math.max(w, n.x + 1);
            h = Math.max(h, n.y + 1);
            cost += n.cost;
        }
        for (AbilityNode n : list) {
            for (String r : n.requires) {
                if (!nodes.containsKey(r)) {
                    throw new IllegalArgumentException(key + "/" + n.id + ": requires unknown node " + r);
                }
            }
            for (String e : n.excludes) {
                if (!nodes.containsKey(e)) {
                    throw new IllegalArgumentException(key + "/" + n.id + ": excludes unknown node " + e);
                }
            }
            if (n.arch != null && !archetypes.contains(n.arch)) {
                throw new IllegalArgumentException(key + "/" + n.id + ": unknown archetype " + n.arch);
            }
            if (n.skill != null && n.skill.tree != tree) {
                throw new IllegalArgumentException(key + "/" + n.id + ": skill " + n.skill.id + " is not in this tree");
            }
        }
        this.width = w;
        this.height = h;
        this.totalCost = cost;
    }

    static AbilityTree parse(String key, SkillTree tree, @Nullable Job job, JsonObject o) {
        List<String> arch = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("archetypes")) {
            arch.add(e.getAsString());
        }
        List<AbilityNode> list = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("nodes")) {
            list.add(AbilityNode.parse(key, e.getAsJsonObject()));
        }
        return new AbilityTree(key, tree, job, arch, list);
    }

    @Nullable
    public AbilityNode node(String id) {
        return nodes.get(id);
    }

    public Collection<AbilityNode> nodes() {
        return nodes.values();
    }

    // 同じ技の刻印の節 (排他の相手)
    public List<AbilityNode> runesOf(Skill skill) {
        List<AbilityNode> out = new ArrayList<>();
        for (AbilityNode n : nodes.values()) {
            if (n.kind == AbilityNode.Kind.RUNE && n.skill == skill) {
                out.add(n);
            }
        }
        return out;
    }

    // この技を取る節 (ACTIVE / PASSIVE / KEYSTONE。なければ null)
    @Nullable
    public AbilityNode skillNode(Skill skill) {
        for (AbilityNode n : nodes.values()) {
            if (n.skill == skill && (n.kind == AbilityNode.Kind.ACTIVE || n.kind == AbilityNode.Kind.PASSIVE || n.kind == AbilityNode.Kind.KEYSTONE)) {
                return n;
            }
        }
        return null;
    }

    // この技の段を上げる節 (順不同)
    // その技の変異の節 (無ければ null)
    @Nullable
    public AbilityNode mutationNode(Skill skill) {
        for (AbilityNode n : nodes.values()) {
            if (n.kind == AbilityNode.Kind.MUTATION && n.skill == skill) {
                return n;
            }
        }
        return null;
    }

    public List<AbilityNode> rankNodes(Skill skill) {
        List<AbilityNode> out = new ArrayList<>();
        for (AbilityNode n : nodes.values()) {
            if (n.skill == skill && n.kind == AbilityNode.Kind.RANK) {
                out.add(n);
            }
        }
        return out;
    }

    // 使う技の節
    public List<AbilityNode> actives() {
        List<AbilityNode> out = new ArrayList<>();
        for (AbilityNode n : nodes.values()) {
            if (n.kind == AbilityNode.Kind.ACTIVE) {
                out.add(n);
            }
        }
        return out;
    }

    // その節まで、幹から順に取る道 (前提は「どれか一つ」なので最初の前提をたどる)。自分も含む。開発・テスト用
    public List<AbilityNode> pathTo(String id) {
        LinkedHashSet<AbilityNode> out = new LinkedHashSet<>();
        collect(nodes.get(id), out, 0);
        List<AbilityNode> list = new ArrayList<>(out);
        Collections.reverse(list);
        return list;
    }

    private void collect(@Nullable AbilityNode n, Set<AbilityNode> out, int depth) {
        if (n == null || depth > 64 || out.contains(n)) {
            return;
        }
        out.add(n);
        if (!n.requires.isEmpty()) {
            collect(nodes.get(n.requires.get(0)), out, depth + 1);
        }
    }

    public String archKey(String arch) {
        return "arch.skyabyss." + key + "." + arch;
    }
}
