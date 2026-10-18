package jp.revati.skyabyss.quest;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.Affinity;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 会話とクエストで共通に使う結果。JSON の {"type": ..., ...} から作る。
//   quest_start {quest}            : クエストを始める (始まっていれば何もしない)
//   signal      {signal}           : クエストの「合図」の目的を進める
//   affinity    {amount, npc}      : 好感度を足す (-100〜100 に収める)
//   flag / unflag {flag, npc}      : このループの会話の印を立てる / 消す
//   lore        {entry}            : 伝承の書に項目を記す
//   give        {item, count}      : 渡す (入りきらなければ足もとに落とす)
//   take        {item, count}      : 受け取る (足りなければ何もしない。条件 item と組で使う)
//   xp          {amount, once}     : 成長の経験値。once の鍵で一人一回だけ (会話をくり返して稼げないように)
//   give は optional を書ける (まだ登録されていない品なら何もしない)
//   give / take は token・name・lore を書ける (印の付いた品。QuestItems)。item はタグ ("#minecraft:logs") でもよい (take だけ)
//   grave_rescue                   : いちばん古い墓を語り部の救いで戻す (墓は消え、割合の分だけ手もとへ。grave パッケージ)
//   open_board                     : 言伝ての板の画面を開く (板のない所で、NPC が代わりに頼みごとを見せる)
public final class Action {
    public static final Set<String> TYPES = Set.of("quest_start", "signal", "affinity", "flag", "unflag", "lore", "give", "take", "xp",
            "open_board", "grave_rescue");

    private final String type;
    private final JsonObject json;

    private Action(String type, JsonObject json) {
        this.type = type;
        this.json = json;
    }

    public String type() {
        return type;
    }

    public String str(String key) {
        return GsonHelper.getAsString(json, key, "");
    }

    public int integer(String key, int fallback) {
        return GsonHelper.getAsInt(json, key, fallback);
    }

    @Nullable
    public String optStr(String key) {
        return json.has(key) ? GsonHelper.getAsString(json, key) : null;
    }

    public static List<Action> parseList(@Nullable JsonElement element, List<String> errors, String where) {
        List<Action> list = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return list;
        }
        if (element.isJsonObject()) {
            Action a = parse(element.getAsJsonObject(), errors, where);
            if (a != null) {
                list.add(a);
            }
            return list;
        }
        if (!element.isJsonArray()) {
            errors.add(where + ": actions must be an object or an array");
            return list;
        }
        for (JsonElement e : element.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                errors.add(where + ": action must be an object");
                continue;
            }
            Action a = parse(e.getAsJsonObject(), errors, where);
            if (a != null) {
                list.add(a);
            }
        }
        return list;
    }

    @Nullable
    private static Action parse(JsonObject o, List<String> errors, String where) {
        String type = GsonHelper.getAsString(o, "type", "");
        if (!TYPES.contains(type)) {
            errors.add(where + ": unknown action type '" + type + "'");
            return null;
        }
        String need = switch (type) {
            case "quest_start" -> "quest";
            case "signal" -> "signal";
            case "affinity" -> "amount";
            case "flag", "unflag" -> "flag";
            case "lore" -> "entry";
            case "give", "take" -> "item";
            case "xp" -> "once";
            default -> null;
        };
        if (need != null && !o.has(need)) {
            errors.add(where + ": action '" + type + "' needs '" + need + "'");
            return null;
        }
        // give の optional: 別の担当がまだ登録していない品。ないうちは何も渡さない (読み込みは止めない)
        if ((type.equals("give") ? Condition.item(GsonHelper.getAsString(o, "item")) == null && !GsonHelper.getAsBoolean(o, "optional", false)
                : type.equals("take") && !QuestItems.known(GsonHelper.getAsString(o, "item")))) {
            errors.add(where + ": unknown item " + GsonHelper.getAsString(o, "item"));
            return null;
        }
        return new Action(type, o);
    }

    public void run(StoryContext ctx) {
        var player = ctx.player();
        switch (type) {
            case "quest_start" -> Quests.start(player, str("quest"));
            case "signal" -> Quests.signal(player, str("signal"));
            case "affinity" -> {
                String role = ctx.role(optStr("npc"));
                if (role != null) {
                    Affinity.add(player, role, integer("amount", 0));
                }
            }
            case "flag", "unflag" -> {
                String role = ctx.role(optStr("npc"));
                if (role != null) {
                    NpcBonds bonds = NpcBonds.of(player);
                    if (type.equals("flag")) {
                        bonds.bond(role).flags().add(str("flag"));
                    } else {
                        bonds.bond(role).flags().remove(str("flag"));
                    }
                    NpcBonds.sync(player);
                }
            }
            case "lore" -> Lore.unlock(player, str("entry"), true);
            case "give" -> {
                Item item = Condition.item(str("item"));
                if (item != null) {
                    Quests.give(player, QuestItems.make(item, Math.max(1, integer("count", 1)), optStr("token"), optStr("name"), loreKeys()));
                }
            }
            case "take" -> {
                int count = Math.max(1, integer("count", 1));
                ItemStack shown = sample(player, str("item"), optStr("token"));
                if (QuestItems.take(player, str("item"), optStr("token"), count)) {
                    Notice.hint(player, "message.skyabyss.npc.handed", shown.getHoverName(), count);
                }
            }
            case "open_board" -> jp.revati.skyabyss.quest.free.Board.open(player, ctx.npc() != null ? ctx.npc().blockPosition() : player.blockPosition(), true);
            case "grave_rescue" -> jp.revati.skyabyss.grave.GraveRescue.run(player);
            case "xp" -> {
                if (Growth.discover(player, "npc:" + str("once"))) {
                    Growth.addXp(player, integer("amount", 0), GsonHelper.getAsString(json, "reason", "growth.skyabyss.reason.npc"));
                }
            }
            default -> {
            }
        }
    }

    private List<String> loreKeys() {
        List<String> keys = new ArrayList<>();
        if (json.has("lore") && json.get("lore").isJsonArray()) {
            json.getAsJsonArray("lore").forEach(e -> keys.add(e.getAsString()));
        }
        return keys;
    }

    // 渡した品の名前を知らせに出すための見本 (名前の付いた品はその名前)
    private static ItemStack sample(net.minecraft.server.level.ServerPlayer player, String target, @Nullable String token) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (QuestItems.matches(inv.getItem(i), target, token)) {
                return inv.getItem(i).copy();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public String toString() {
        return type + json;
    }
}
