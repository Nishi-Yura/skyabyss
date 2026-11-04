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
            for (String e : n.blocks) {
                if (!nodes.containsKey(e)) {
                    throw new IllegalArgumentException(key + "/" + n.id + ": blocks unknown node " + e);
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

    // その節まで、幹から順に取る道 (開発・テスト用。forceRank もこれで節を入れる)。自分も含む。
    // 根から前提を逆にたどり、重みの小さい道を選ぶ: 変化と変異は目当てでなければ通らない (100)、常に効く数・閾値はなるべく避ける (3)、ほかは 1。
    // 横どうしの線があっても輪にならない
    public List<AbilityNode> pathTo(String id) {
        AbilityNode goal = nodes.get(id);
        if (goal == null) {
            return new ArrayList<>();
        }
        java.util.Map<String, Integer> dist = new java.util.HashMap<>();
        java.util.Map<String, String> parent = new java.util.HashMap<>();
        java.util.PriorityQueue<Object[]> queue = new java.util.PriorityQueue<>(java.util.Comparator.comparingInt(o -> (Integer) o[1]));
        for (AbilityNode n : nodes.values()) {
            if (n.requires.isEmpty()) {
                dist.put(n.id, weight(n, id));
                parent.put(n.id, null);
                queue.add(new Object[] {n, weight(n, id)});
            }
        }
        while (!queue.isEmpty()) {
            Object[] top = queue.poll();
            AbilityNode cur = (AbilityNode) top[0];
            int d = (Integer) top[1];
            if (d > dist.getOrDefault(cur.id, Integer.MAX_VALUE) || cur.id.equals(id)) {
                continue;
            }
            for (AbilityNode n : nodes.values()) {
                if (!n.requires.contains(cur.id)) {
                    continue;
                }
                int nd = d + weight(n, id);
                if (nd < dist.getOrDefault(n.id, Integer.MAX_VALUE)) {
                    dist.put(n.id, nd);
                    parent.put(n.id, cur.id);
                    queue.add(new Object[] {n, nd});
                }
            }
        }
        List<AbilityNode> list = new ArrayList<>();
        for (String at = id; at != null && parent.containsKey(at); at = parent.get(at)) {
            list.add(nodes.get(at));
        }
        Collections.reverse(list);
        return list;
    }

    private static int weight(AbilityNode n, String goal) {
        if (n.id.equals(goal)) {
            return 1;
        }
        return switch (n.kind) {
            case RUNE, MUTATION -> 100;
            case EFFECT, THRESHOLD -> 3;
            default -> 1;
        };
    }

    public String archKey(String arch) {
        return "arch.skyabyss." + key + "." + arch;
    }
}
