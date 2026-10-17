package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;

// クエストの定義 (data/<名前空間>/quest/<名前>.json)。読み込んだら変えない。
// 参加者へは表示に要る分 (段階・目的・報酬) だけを送る (条件と結果はサーバーだけが持つ)
//   repeat   : "" (一度きり) / "daily" (ゲーム内の 1 日ごと) / "weekly" (7 日ごと)。周期は Repeats が決める
//   board    : 言伝ての板 (quest.free.Board) に出す
//   minStage : 受けられる物語の段階 (止めた心臓の数)。ログと板で「一つ先」を伏せて見せるのにも使う
//   unlock   : 受けられる条件 (段階のほか。サーバーだけが持つ)
public record QuestDef(String id, String category, int order, @Nullable String giver, Condition startWhen, boolean loopReset,
                       List<Stage> stages, Reward reward, String repeat, boolean board, int minStage, Condition unlock) {
    public static final Set<String> CATEGORIES = Set.of("main", "sub");
    public static final Set<String> REPEATS = Set.of("", "daily", "weekly");
    public static final Set<String> OBJECTIVE_TYPES = Set.of("talk", "kill", "collect", "lore", "visit", "near", "signal", "condition",
            "turn_in", "herd");

    // 段階。mode が all なら目的をすべて、any ならどれか一つで次へ。skipIf が真なら、着いたとたんに済ませる
    public record Stage(String id, String mode, List<Objective> objectives, Condition skipIf, int xp, List<Action> actions) {
        public boolean any() {
            return mode.equals("any");
        }
    }

    // 目的。targets は種類ごとの対象 (talk=NPC の役、kill=生き物の ID、collect=アイテム、lore=項目、visit=ディメンション、
    // near=生き物、signal=合図の名前)。count は必要な数 (lore は「targets のうちいくつ」)
    // party: 倒す・合図の目的を、パーティの仲間 (経験値の分け前と同じ条件) も数える。token: 品の印 (QuestItems。collect / turn_in)
    // turn_in: 品を言伝ての板 (か依頼主の会話) で納める。herd: 受けた場所 (板) の radius 以内に、その生き物を count 頭そろえる
    public record Objective(String id, String type, List<String> targets, @Nullable String topic, int count, double radius, Condition condition,
                            boolean party, @Nullable String token) {
    }

    // 品の報酬。token / name / lore を書くと、印・名前・説明の付いた品 (見た目の小物や、運ぶ品) になる (QuestItems)
    public record ItemReward(String item, int count, @Nullable String token, @Nullable String name, List<String> lore) {
        public ItemReward(String item, int count) {
            this(item, count, null, null, List.of());
        }
    }

    public record Reward(int xp, List<ItemReward> items, Map<String, Integer> affinity, List<Action> actions) {
        static final Reward NONE = new Reward(0, List.of(), Map.of(), List.of());
    }

    public boolean main() {
        return category.equals("main");
    }

    public boolean repeats() {
        return !repeat.isEmpty();
    }

    @Nullable
    public Stage stage(String stageId) {
        for (Stage s : stages) {
            if (s.id().equals(stageId)) {
                return s;
            }
        }
        return null;
    }

    public int stageIndex(String stageId) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).id().equals(stageId)) {
                return i;
            }
        }
        return -1;
    }

    // 翻訳キーの頭。"skyabyss:main_song" → "quest.skyabyss.main_song"
    public static String keyBase(String id) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "skyabyss" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return "quest." + ns + "." + path.replace('/', '.');
    }

    public String titleKey() {
        return keyBase(id) + ".title";
    }

    public String summaryKey() {
        return keyBase(id) + ".summary";
    }

    public String doneKey() {
        return keyBase(id) + ".done";
    }

    public String journalKey(Stage stage) {
        return keyBase(id) + "." + stage.id() + ".journal";
    }

    public String objectiveKey(Stage stage, Objective objective) {
        return keyBase(id) + "." + stage.id() + "." + objective.id();
    }

    // ---------------------------------------------------------------- JSON
    public static QuestDef parse(String id, JsonObject o, List<String> errors) {
        String where = "quest " + id;
        String category = GsonHelper.getAsString(o, "category", "sub");
        if (!CATEGORIES.contains(category)) {
            errors.add(where + ": category must be main or sub");
        }
        List<Stage> stages = new ArrayList<>();
        if (o.has("stages") && o.get("stages").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("stages")) {
                if (!e.isJsonObject()) {
                    errors.add(where + ": stage must be an object");
                    continue;
                }
                stages.add(parseStage(e.getAsJsonObject(), errors, where));
            }
        }
        if (stages.isEmpty()) {
            errors.add(where + ": no stages");
        }
        Set<String> seen = new java.util.HashSet<>();
        for (Stage s : stages) {
            if (!seen.add(s.id())) {
                errors.add(where + ": duplicate stage " + s.id());
            }
        }
        Reward reward = o.has("reward") && o.get("reward").isJsonObject() ? parseReward(o.getAsJsonObject("reward"), errors, where) : Reward.NONE;
        String repeat = GsonHelper.getAsString(o, "repeat", "");
        if (!REPEATS.contains(repeat)) {
            errors.add(where + ": repeat must be daily or weekly");
        }
        if (!repeat.isEmpty()) {
            // 繰り返しの経験値は報酬でだけ渡す (段階の経験値は一人一回の鍵なので、二度目から出ない)
            stages.stream().filter(s -> s.xp() > 0).forEach(s -> errors.add(where + ": a repeating quest gives xp only in its reward (" + s.id() + ")"));
            if (o.has("start_when")) {
                errors.add(where + ": a repeating quest cannot start by itself");
            }
        }
        int minStage = GsonHelper.getAsInt(o, "min_stage", 0);
        if (minStage < 0 || minStage > 4) {
            errors.add(where + ": min_stage must be 0-4");
        }
        return new QuestDef(id, category, GsonHelper.getAsInt(o, "order", 100), o.has("giver") ? GsonHelper.getAsString(o, "giver") : null,
                o.has("start_when") ? Condition.parse(o.get("start_when"), errors, where + " start_when") : null,
                GsonHelper.getAsBoolean(o, "loop_reset", false), List.copyOf(stages), reward, repeat,
                GsonHelper.getAsBoolean(o, "board", false), minStage,
                o.has("unlock") ? Condition.parse(o.get("unlock"), errors, where + " unlock") : Condition.ALWAYS);
    }

    private static Stage parseStage(JsonObject o, List<String> errors, String where) {
        String id = GsonHelper.getAsString(o, "id", "");
        String w = where + " stage " + id;
        if (id.isEmpty()) {
            errors.add(where + ": stage without id");
        }
        String mode = GsonHelper.getAsString(o, "mode", "all");
        if (!mode.equals("all") && !mode.equals("any")) {
            errors.add(w + ": mode must be all or any");
        }
        List<Objective> objectives = new ArrayList<>();
        if (o.has("objectives") && o.get("objectives").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("objectives")) {
                if (e.isJsonObject()) {
                    objectives.add(parseObjective(e.getAsJsonObject(), errors, w));
                }
            }
        }
        if (objectives.isEmpty()) {
            errors.add(w + ": no objectives");
        }
        return new Stage(id, mode, List.copyOf(objectives),
                o.has("skip_if") ? Condition.parse(o.get("skip_if"), errors, w + " skip_if") : null,
                GsonHelper.getAsInt(o, "xp", 0), Action.parseList(o.get("do"), errors, w + " do"));
    }

    private static Objective parseObjective(JsonObject o, List<String> errors, String where) {
        String id = GsonHelper.getAsString(o, "id", "");
        String type = GsonHelper.getAsString(o, "type", "");
        String w = where + " objective " + id;
        if (id.isEmpty()) {
            errors.add(where + ": objective without id");
        }
        if (!OBJECTIVE_TYPES.contains(type)) {
            errors.add(w + ": unknown objective type '" + type + "'");
        }
        List<String> targets = new ArrayList<>();
        if (o.has("target")) {
            targets.add(GsonHelper.getAsString(o, "target"));
        }
        if (o.has("targets") && o.get("targets").isJsonArray()) {
            o.getAsJsonArray("targets").forEach(e -> targets.add(e.getAsString()));
        }
        if (targets.isEmpty() && !type.equals("condition")) {
            errors.add(w + ": no target");
        }
        if ((type.equals("collect") || type.equals("turn_in")) && targets.size() != 1) {
            errors.add(w + ": " + type + " needs exactly one item");
        }
        if ((type.equals("collect") || type.equals("turn_in")) && targets.size() == 1 && !QuestItems.known(targets.get(0))) {
            errors.add(w + ": unknown item or tag " + targets.get(0));
        }
        int fallback = type.equals("lore") ? targets.size() : 1;
        int count = GsonHelper.getAsInt(o, "count", fallback);
        if (count < 1 || (type.equals("lore") && count > targets.size())) {
            errors.add(w + ": bad count " + count);
        }
        Condition condition = type.equals("condition") ? Condition.parse(o.get("if"), errors, w + " if") : Condition.ALWAYS;
        if (type.equals("condition") && !o.has("if")) {
            errors.add(w + ": condition objective needs 'if'");
        }
        double radius = GsonHelper.getAsDouble(o, "radius", type.equals("near") ? 48 : type.equals("herd") ? 10 : 32);
        return new Objective(id, type, List.copyOf(targets), o.has("topic") ? GsonHelper.getAsString(o, "topic") : null, count,
                radius, condition, GsonHelper.getAsBoolean(o, "party", type.equals("kill") || type.equals("signal")),
                o.has("token") ? GsonHelper.getAsString(o, "token") : null);
    }

    private static Reward parseReward(JsonObject o, List<String> errors, String where) {
        List<ItemReward> items = new ArrayList<>();
        if (o.has("items") && o.get("items").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("items")) {
                JsonObject io = e.getAsJsonObject();
                String item = GsonHelper.getAsString(io, "item", "");
                // optional: 別の担当がまだ登録していない品 (雷翼の欠片・風見鶏など)。ないうちは渡さず、読み込みも止めない
                if (Condition.item(item) == null && !GsonHelper.getAsBoolean(io, "optional", false)) {
                    errors.add(where + ": unknown reward item " + item);
                }
                List<String> lore = new ArrayList<>();
                if (io.has("lore") && io.get("lore").isJsonArray()) {
                    io.getAsJsonArray("lore").forEach(l -> lore.add(l.getAsString()));
                }
                items.add(new ItemReward(item, GsonHelper.getAsInt(io, "count", 1), io.has("token") ? GsonHelper.getAsString(io, "token") : null,
                        io.has("name") ? GsonHelper.getAsString(io, "name") : null, List.copyOf(lore)));
            }
        }
        Map<String, Integer> affinity = new LinkedHashMap<>();
        if (o.has("affinity") && o.get("affinity").isJsonObject()) {
            o.getAsJsonObject("affinity").entrySet().forEach(en -> affinity.put(en.getKey(), en.getValue().getAsInt()));
        }
        return new Reward(GsonHelper.getAsInt(o, "xp", 0), List.copyOf(items), Map.copyOf(affinity), Action.parseList(o.get("do"), errors, where + " reward"));
    }

    // ---------------------------------------------------------------- 参加者へ送る (表示に要る分だけ)
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(category);
        buf.writeVarInt(order);
        buf.writeUtf(giver == null ? "" : giver);
        buf.writeUtf(repeat);
        buf.writeBoolean(board);
        buf.writeVarInt(minStage);
        buf.writeVarInt(stages.size());
        for (Stage s : stages) {
            buf.writeUtf(s.id());
            buf.writeUtf(s.mode());
            buf.writeVarInt(s.xp());
            buf.writeVarInt(s.objectives().size());
            for (Objective ob : s.objectives()) {
                buf.writeUtf(ob.id());
                buf.writeUtf(ob.type());
                buf.writeVarInt(ob.count());
                buf.writeVarInt(ob.targets().size());
                ob.targets().forEach(buf::writeUtf);
                buf.writeUtf(ob.token() == null ? "" : ob.token());
                buf.writeBoolean(ob.party());
            }
        }
        buf.writeVarInt(reward.xp());
        buf.writeVarInt(reward.items().size());
        for (ItemReward r : reward.items()) {
            buf.writeUtf(r.item());
            buf.writeVarInt(r.count());
            buf.writeUtf(r.name() == null ? "" : r.name());
        }
    }

    public static QuestDef read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String category = buf.readUtf();
        int order = buf.readVarInt();
        String giver = buf.readUtf();
        String repeat = buf.readUtf();
        boolean board = buf.readBoolean();
        int minStage = buf.readVarInt();
        int ns = buf.readVarInt();
        List<Stage> stages = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            String sid = buf.readUtf();
            String mode = buf.readUtf();
            int xp = buf.readVarInt();
            int no = buf.readVarInt();
            List<Objective> objectives = new ArrayList<>(no);
            for (int j = 0; j < no; j++) {
                String oid = buf.readUtf();
                String type = buf.readUtf();
                int count = buf.readVarInt();
                int nt = buf.readVarInt();
                List<String> targets = new ArrayList<>(nt);
                for (int k = 0; k < nt; k++) {
                    targets.add(buf.readUtf());
                }
                String token = buf.readUtf();
                boolean party = buf.readBoolean();
                objectives.add(new Objective(oid, type, List.copyOf(targets), null, count, 0, Condition.ALWAYS, party,
                        token.isEmpty() ? null : token));
            }
            stages.add(new Stage(sid, mode, List.copyOf(objectives), null, xp, List.of()));
        }
        int xp = buf.readVarInt();
        int ni = buf.readVarInt();
        List<ItemReward> items = new ArrayList<>(ni);
        for (int i = 0; i < ni; i++) {
            String item = buf.readUtf();
            int count = buf.readVarInt();
            String name = buf.readUtf();
            items.add(new ItemReward(item, count, null, name.isEmpty() ? null : name, List.of()));
        }
        return new QuestDef(id, category, order, giver.isEmpty() ? null : giver, null, false, List.copyOf(stages),
                new Reward(xp, List.copyOf(items), Map.of(), List.of()), repeat, board, minStage, Condition.ALWAYS);
    }
}
