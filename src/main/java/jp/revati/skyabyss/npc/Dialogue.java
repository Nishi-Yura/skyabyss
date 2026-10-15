package jp.revati.skyabyss.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jp.revati.skyabyss.quest.Action;
import jp.revati.skyabyss.quest.Condition;
import jp.revati.skyabyss.quest.StoryContext;
import net.minecraft.util.GsonHelper;

// 一人の NPC (役) の会話 (data/<名前空間>/dialogue/<役>.json)。読み込んだら変えない。
//   entries : 話しかけたとき、上から順に条件を調べ、最初に合った節から始める
//   nodes   : 節。本文 (翻訳キー)、顔、入ったときの結果 (do)、選択肢、または「続き」(next)
// 本文のキーは dialogue.<名前空間>.<役>.<節>、選択肢は dialogue.<名前空間>.<役>.<節>.<選択肢>。text を書けば上書きできる
public record Dialogue(String role, List<Entry> entries, Map<String, Node> nodes) {
    public static final String END = "end";
    // 入口に戻る (entries を調べ直す)。話題を選ぶ「ほかに何か」の節へ戻るのに使う
    public static final String ENTRY = "@entry";
    public static final Set<String> FACES = Set.of("neutral", "smile", "grave");
    public static final Set<String> KINDS = Set.of("talk", "quest", "give", "leave");

    public record Entry(Condition condition, String node) {
    }

    public record Node(String id, String face, String textKey, List<Action> actions, @Nullable String next, List<Option> options) {
    }

    // kind は画面での印 (talk=話題、quest=クエストに関わる、give=物を渡す、leave=立ち去る)
    public record Option(String id, String textKey, Condition condition, List<Action> actions, String target, String kind) {
    }

    @Nullable
    public Node entryNode(StoryContext ctx) {
        for (Entry e : entries) {
            if (e.condition().test(ctx)) {
                return nodes.get(e.node());
            }
        }
        return null;
    }

    public static String keyBase(String role) {
        int colon = role.indexOf(':');
        String ns = colon < 0 ? "skyabyss" : role.substring(0, colon);
        String path = colon < 0 ? role : role.substring(colon + 1);
        return "dialogue." + ns + "." + path;
    }

    // ---------------------------------------------------------------- JSON
    public static Dialogue parse(String fileRole, JsonObject o, List<String> errors) {
        String role = GsonHelper.getAsString(o, "npc", fileRole);
        String where = "dialogue " + role;
        String base = keyBase(role);
        Map<String, Node> nodes = new LinkedHashMap<>();
        if (o.has("nodes") && o.get("nodes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("nodes").entrySet()) {
                if (!en.getValue().isJsonObject()) {
                    errors.add(where + ": node " + en.getKey() + " must be an object");
                    continue;
                }
                nodes.put(en.getKey(), parseNode(en.getKey(), en.getValue().getAsJsonObject(), base, errors, where + " node " + en.getKey()));
            }
        }
        List<Entry> entries = new ArrayList<>();
        if (o.has("entries") && o.get("entries").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("entries")) {
                JsonObject eo = e.getAsJsonObject();
                String node = GsonHelper.getAsString(eo, "node", "");
                entries.add(new Entry(Condition.parse(eo.get("if"), errors, where + " entry " + node), node));
            }
        }
        if (entries.isEmpty()) {
            errors.add(where + ": no entries");
        } else if (entries.get(entries.size() - 1).condition() != Condition.ALWAYS) {
            errors.add(where + ": the last entry must have no condition (so that some node always opens)");
        }
        Dialogue d = new Dialogue(role, List.copyOf(entries), Map.copyOf(nodes));
        errors.addAll(d.problems());
        return d;
    }

    private static Node parseNode(String id, JsonObject o, String base, List<String> errors, String where) {
        String face = GsonHelper.getAsString(o, "face", "neutral");
        if (!FACES.contains(face)) {
            errors.add(where + ": unknown face " + face);
        }
        List<Option> options = new ArrayList<>();
        if (o.has("options") && o.get("options").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("options")) {
                JsonObject oo = e.getAsJsonObject();
                String oid = GsonHelper.getAsString(oo, "id", "");
                String w = where + " option " + oid;
                if (oid.isEmpty()) {
                    errors.add(where + ": option without id");
                }
                String kind = GsonHelper.getAsString(oo, "kind", "talk");
                if (!KINDS.contains(kind)) {
                    errors.add(w + ": unknown kind " + kind);
                }
                options.add(new Option(oid, GsonHelper.getAsString(oo, "text", base + "." + id + "." + oid),
                        Condition.parse(oo.get("if"), errors, w), Action.parseList(oo.get("do"), errors, w),
                        GsonHelper.getAsString(oo, "goto", END), kind));
            }
        }
        String next = o.has("next") ? GsonHelper.getAsString(o, "next") : null;
        if (next != null && !options.isEmpty()) {
            errors.add(where + ": a node has either 'next' or 'options', not both");
        }
        return new Node(id, face, GsonHelper.getAsString(o, "text", base + "." + id), Action.parseList(o.get("do"), errors, where), next,
                List.copyOf(options));
    }

    // 飛び先がない・同じ選択肢の ID が二つ、など
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        String where = "dialogue " + role;
        for (Entry e : entries) {
            if (!nodes.containsKey(e.node())) {
                problems.add(where + ": entry points to missing node " + e.node());
            }
        }
        for (Node n : nodes.values()) {
            if (n.next() != null && !target(n.next())) {
                problems.add(where + " node " + n.id() + ": next points to missing node " + n.next());
            }
            Set<String> ids = new java.util.HashSet<>();
            for (Option op : n.options()) {
                if (!ids.add(op.id())) {
                    problems.add(where + " node " + n.id() + ": duplicate option " + op.id());
                }
                if (!target(op.target())) {
                    problems.add(where + " node " + n.id() + " option " + op.id() + ": goto points to missing node " + op.target());
                }
            }
        }
        return problems;
    }

    private boolean target(String t) {
        return t.equals(END) || t.equals(ENTRY) || nodes.containsKey(t);
    }
}
