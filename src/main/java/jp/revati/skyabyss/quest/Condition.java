package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.Affinity;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.WorldLoop;
import jp.revati.skyabyss.prologue.PrologueData;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;

// 会話とクエストで共通に使う条件。JSON の {"type": ..., ...} から作る。配列は「すべて」。
//   all / any / not            : 組み合わせ ({"type":"any","of":[...]}、not は "of" に一つ)
//   quest   {quest, is, stage} : クエストの状態。is = none / active / done / started / not_done。stage を書くと「その段階にいる」
//   lore    {entry}            : 伝承の書にその項目が記されている
//   affinity{npc, min, max}    : 好感度 (npc を省くと話している相手)
//   flag    {flag, npc}        : このループで立てた会話の印
//   remembers{flag, npc}       : 前のループまでに立てた印 (留まる者の記憶。NPC が「覚えているような」セリフに使う)
//   loop    {min, max}         : 月のループの回数 (M4 までは 0。コマンドで変えられる)
//   item    {item, count}      : 持ち物に入っている数
//   watcher {min}              : 「見る者」に気づいた回数
//   day     {min}              : ワールドの経過日数 (岩鯨は 3 日目から現れる)
//   level   {min}              : 成長のレベル
//   night                      : オーバーワールドが夜
//   stage   {min, max}         : 個人の物語の段階 (自分で止めた心臓の数。progress/Progression.stage)
//   stilled {titan}            : 自分でその巨神の心臓を止めた (Progression.stopped)
//   world_stage {min}          : 世界の進み (ワールドの誰かが止めた心臓の数)
//   weather {thunder, rain}    : 今いる所が雷雨 / 雨で、真上に空が見える (屋根の下では数えない)
//   height  {min}              : 足もとの高さ (y) が min 以上
//   item は token を書くと、その印の品だけを数える (QuestItems)。optional を書くと、まだ登録されていない品でも読み込める (数は 0)
public final class Condition {
    public static final Set<String> TYPES = Set.of("all", "any", "not", "quest", "lore", "affinity", "flag", "remembers", "loop",
            "item", "watcher", "day", "level", "night", "true", "stage", "stilled", "world_stage", "weather", "height");
    public static final Condition ALWAYS = new Condition("true", new JsonObject(), List.of());

    private final String type;
    private final JsonObject json;
    private final List<Condition> children;

    private Condition(String type, JsonObject json, List<Condition> children) {
        this.type = type;
        this.json = json;
        this.children = children;
    }

    public String type() {
        return type;
    }

    public JsonObject json() {
        return json;
    }

    public List<Condition> children() {
        return children;
    }

    // 省略 (null) なら常に真。配列はすべて、オブジェクトは一つ
    public static Condition parse(@Nullable JsonElement element, List<String> errors, String where) {
        if (element == null || element.isJsonNull()) {
            return ALWAYS;
        }
        if (element.isJsonArray()) {
            return new Condition("all", new JsonObject(), parseList(element.getAsJsonArray(), errors, where));
        }
        if (!element.isJsonObject()) {
            errors.add(where + ": condition must be an object or an array");
            return ALWAYS;
        }
        JsonObject o = element.getAsJsonObject();
        String type = GsonHelper.getAsString(o, "type", "");
        if (!TYPES.contains(type)) {
            errors.add(where + ": unknown condition type '" + type + "'");
            return ALWAYS;
        }
        List<Condition> children = List.of();
        if (type.equals("all") || type.equals("any")) {
            children = o.has("of") && o.get("of").isJsonArray() ? parseList(o.getAsJsonArray("of"), errors, where) : List.of();
            if (children.isEmpty()) {
                errors.add(where + ": '" + type + "' needs a non-empty 'of'");
            }
        } else if (type.equals("not")) {
            children = List.of(parse(o.get("of"), errors, where));
        }
        Condition c = new Condition(type, o, children);
        c.validateFields(errors, where);
        return c;
    }

    private static List<Condition> parseList(JsonArray array, List<String> errors, String where) {
        List<Condition> list = new ArrayList<>();
        for (JsonElement e : array) {
            list.add(parse(e, errors, where));
        }
        return list;
    }

    private void validateFields(List<String> errors, String where) {
        switch (type) {
            case "quest" -> {
                need("quest", errors, where);
                String is = GsonHelper.getAsString(json, "is", "active");
                if (!Set.of("none", "active", "done", "started", "not_done").contains(is)) {
                    errors.add(where + ": quest condition 'is' must be none/active/done/started/not_done, got " + is);
                }
            }
            case "lore" -> need("entry", errors, where);
            case "flag", "remembers" -> need("flag", errors, where);
            case "item" -> {
                need("item", errors, where);
                if (!QuestItems.known(GsonHelper.getAsString(json, "item", "")) && !GsonHelper.getAsBoolean(json, "optional", false)) {
                    errors.add(where + ": unknown item " + GsonHelper.getAsString(json, "item", ""));
                }
            }
            case "stilled" -> {
                need("titan", errors, where);
                if (!jp.revati.skyabyss.progress.ProgressRules.TITAN_ORDER.contains(GsonHelper.getAsString(json, "titan", ""))) {
                    errors.add(where + ": unknown titan " + GsonHelper.getAsString(json, "titan", ""));
                }
            }
            default -> {
            }
        }
    }

    private void need(String key, List<String> errors, String where) {
        if (!json.has(key)) {
            errors.add(where + ": condition '" + type + "' needs '" + key + "'");
        }
    }

    // ---------------------------------------------------------------- 評価
    public boolean test(StoryContext ctx) {
        return switch (type) {
            case "true" -> true;
            case "all" -> children.stream().allMatch(c -> c.test(ctx));
            case "any" -> children.stream().anyMatch(c -> c.test(ctx));
            case "not" -> !children.get(0).test(ctx);
            case "quest" -> testQuest(ctx);
            case "lore" -> Lore.state(ctx.player()).has(str("entry"));
            case "affinity" -> {
                String role = ctx.role(optStr("npc"));
                int a = role == null ? 0 : Affinity.get(ctx.player(), role);
                yield a >= GsonHelper.getAsInt(json, "min", Integer.MIN_VALUE) && a <= GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
            }
            case "flag" -> {
                String role = ctx.role(optStr("npc"));
                yield role != null && NpcBonds.of(ctx.player()).bond(role).flags().contains(str("flag"));
            }
            case "remembers" -> {
                String role = ctx.role(optStr("npc"));
                yield role != null && NpcBonds.of(ctx.player()).bond(role).echoes().contains(str("flag"));
            }
            case "loop" -> {
                int n = WorldLoop.count(ctx.player().server);
                yield n >= GsonHelper.getAsInt(json, "min", 0) && n <= GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
            }
            case "item" -> QuestItems.count(ctx.player(), str("item"), optStr("token")) >= GsonHelper.getAsInt(json, "count", 1);
            case "stage" -> {
                int s = jp.revati.skyabyss.progress.Progression.stage(ctx.player());
                yield s >= GsonHelper.getAsInt(json, "min", 0) && s <= GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
            }
            case "stilled" -> jp.revati.skyabyss.progress.Progression.stopped(ctx.player().server, ctx.player().getUUID(), str("titan"));
            case "world_stage" -> jp.revati.skyabyss.progress.Progression.worldStage(ctx.player().server) >= GsonHelper.getAsInt(json, "min", 0);
            case "weather" -> {
                var level = ctx.player().level();
                boolean sky = level.canSeeSky(ctx.player().blockPosition().above());
                // 空の明るさの移り (isThundering) ではなく、天気の記録で見る (雷が鳴りはじめた時点から数える)
                boolean thunder = !GsonHelper.getAsBoolean(json, "thunder", false) || level.getLevelData().isThundering();
                boolean rain = !GsonHelper.getAsBoolean(json, "rain", false) || level.getLevelData().isRaining();
                yield sky && thunder && rain;
            }
            case "height" -> ctx.player().getY() >= GsonHelper.getAsDouble(json, "min", 0);
            case "watcher" -> PrologueData.get(ctx.player().server.overworld()).entry(ctx.player().getUUID()).sightings
                    >= GsonHelper.getAsInt(json, "min", 1);
            case "day" -> ctx.player().server.overworld().getGameTime() / 24000L >= GsonHelper.getAsInt(json, "min", 0);
            case "level" -> Growth.get(ctx.player()).level >= GsonHelper.getAsInt(json, "min", 1);
            case "night" -> {
                long t = ctx.player().server.overworld().getDayTime() % 24000L;
                yield t >= 13000 && t < 23000;
            }
            default -> false;
        };
    }

    private boolean testQuest(StoryContext ctx) {
        String questId = str("quest");
        QuestLog.Entry entry = QuestLog.of(ctx.player()).entry(questId);
        String is = GsonHelper.getAsString(json, "is", "active");
        boolean ok = switch (is) {
            case "none" -> entry == null;
            case "active" -> entry != null && !entry.done;
            case "done" -> entry != null && entry.done;
            case "started" -> entry != null;
            case "not_done" -> entry == null || !entry.done;
            default -> false;
        };
        if (ok && json.has("stage")) {
            QuestDef def = QuestManager.quest(questId);
            if (entry == null || entry.done || def == null || entry.stage >= def.stages().size()) {
                return false;
            }
            return def.stages().get(entry.stage).id().equals(str("stage"));
        }
        return ok;
    }

    // ---------------------------------------------------------------- 参照 (自己テストで、指している先が実在するか調べる)
    public void visit(Consumer<Condition> visitor) {
        visitor.accept(this);
        children.forEach(c -> c.visit(visitor));
    }

    public String str(String key) {
        return GsonHelper.getAsString(json, key, "");
    }

    @Nullable
    public String optStr(String key) {
        return json.has(key) ? GsonHelper.getAsString(json, key) : null;
    }

    @Nullable
    static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    @Override
    public String toString() {
        return type + (children.isEmpty() ? json.toString() : children.toString());
    }
}
