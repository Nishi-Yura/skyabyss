package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 今日・今週 (2026-11-05 の削る・まとめる頁 ct_clock = page)。朝 5 時と月曜の朝に戻るもの (と、時間で巡るもの) を、
// クエストログ (U) の「今日・今週」の頁に一行ずつ: 名前・済んだか / 残り・次に戻るまで・受ける所。時計そのものは変えない。
// 状態はサーバーにしかないので、頁を開いたとき (と開いているあいだ 2 秒ごと) に頼まれて組み立てて送る (Request → List)。手元は CLIENT
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Clocks {
    // 戻り方: 0 = なし、1 = 毎朝 5 時、2 = 月曜の朝 5 時
    public static final int NONE = 0;
    public static final int DAY = 1;
    public static final int WEEK = 2;
    static final String K = "quest.skyabyss.clock.";

    // 一行。status の引数は文字のまま。「#」で始まるものは訳の鍵
    public record Row(String id, String statusKey, List<String> args, int reset, long ticks, String where) {
    }

    // 手元の写し (サーバーの側では空のまま)
    public static volatile List<Row> CLIENT = List.of();
    public static volatile int clientVersion;

    private Clocks() {
    }

    static String n(Object o) {
        return String.valueOf(o);
    }

    static String key(String k) {
        return "#" + k;
    }

    // その人の時計の一覧
    public static List<Row> rows(ServerPlayer p) {
        List<Row> out = new ArrayList<>();
        long day = jp.revati.skyabyss.util.DayClock.ticksUntilNextDay();
        long week = jp.revati.skyabyss.util.DayClock.ticksUntilNextWeek();
        // 言伝ての板 (日替わり・週替わり)
        out.add(new Row("board_day", K + "board_day.status", List.of(n(jp.revati.skyabyss.quest.free.Board.usedToday(p)), n(jp.revati.skyabyss.quest.free.Board.QUOTA)),
                DAY, day, K + "board.where"));
        int weekDone = 0;
        int weekAll = 0;
        for (jp.revati.skyabyss.quest.free.Board.Line l : jp.revati.skyabyss.quest.free.Board.lines(p)) {
            QuestDef def = QuestManager.quest(l.quest());
            if (def == null || !"weekly".equals(def.repeat()) || l.state() == jp.revati.skyabyss.quest.free.Board.LOCKED
                    || l.state() == jp.revati.skyabyss.quest.free.Board.CLOSED) {
                continue;
            }
            weekAll++;
            if (l.state() == jp.revati.skyabyss.quest.free.Board.DONE) {
                weekDone++;
            }
        }
        out.add(weekAll == 0 ? new Row("board_week", K + "board_week.none", List.of(), WEEK, week, K + "board.where")
                : new Row("board_week", K + "board_week.status", List.of(n(weekDone), n(weekAll)), WEEK, week, K + "board.where"));
        // 巡り路
        out.add(new Row("circuit", K + "circuit.status", List.of(n(jp.revati.skyabyss.endgame.Circuits.runsToday(p)), n(jp.revati.skyabyss.endgame.Circuits.DAILY)),
                DAY, day, K + "circuit.where"));
        // あいだの塔 (一日一回・週の構え)
        if (jp.revati.skyabyss.tower.Towers.eligible(p)) {
            out.add(new Row("tower", K + "tower.status", List.of(key(K + (jp.revati.skyabyss.tower.Towers.usedToday(p) ? "tower.used" : "tower.free")),
                    key(jp.revati.skyabyss.tower.TowerStance.thisWeek().nameKey())), DAY, day, K + "tower.where"));
        } else {
            out.add(new Row("tower", K + "tower.closed", List.of(), NONE, 0, K + "tower.where"));
        }
        // ダンジョンの今日の様子
        List<String> moods = new ArrayList<>();
        for (jp.revati.skyabyss.dungeon.Dungeon d : jp.revati.skyabyss.dungeon.Dungeon.values()) {
            moods.add(key(d.nameKey()));
            moods.add(key(jp.revati.skyabyss.dungeon.DungeonMood.today(d).nameKey()));
        }
        out.add(new Row("dungeon", K + "dungeon.status." + jp.revati.skyabyss.dungeon.Dungeon.values().length, moods, DAY, day, K + "dungeon.where"));
        // 魚の注文 (日・週)。土地ごとに一度
        int fishDay = 0;
        int fishWeek = 0;
        String today = String.valueOf(jp.revati.skyabyss.util.DayClock.today().toEpochDay());
        String thisWeek = String.valueOf(jp.revati.skyabyss.tower.TowerWagers.week().toEpochDay());
        for (int land : jp.revati.skyabyss.fishing.FishingSpots.LANDS) {
            if (today.equals(p.getPersistentData().getString(jp.revati.skyabyss.fishing.FishOrders.DAY_KEY + land))) {
                fishDay++;
            }
            if (thisWeek.equals(p.getPersistentData().getString(jp.revati.skyabyss.fishing.FishOrders.WEEK_KEY + land))) {
                fishWeek++;
            }
        }
        int lands = jp.revati.skyabyss.fishing.FishingSpots.LANDS.length;
        out.add(new Row("fish_order", K + "fish_order.status", List.of(n(fishDay), n(lands), n(fishWeek), n(lands)), DAY, day, K + "fish.where"));
        // 釣り大会 (週)
        jp.revati.skyabyss.fishing.Fishing.Entry mine = jp.revati.skyabyss.fishing.Fishing.Week.get(p.server).of(p.getUUID());
        out.add(mine == null
                ? new Row("fish_week", K + "fish_week.none", List.of(), WEEK, week, K + "fish.where")
                : new Row("fish_week", K + "fish_week.status", List.of(fishName(mine.fish()), String.format("%.1f", mine.size() / 10.0)), WEEK, week, K + "fish.where"));
        // 組合の旗取り (週)
        out.add(new Row("guild_week", K + "guild_week.status", List.of(), WEEK, week, K + "guild_week.where"));
        // 大陸の大事件 (45 分ごと)
        jp.revati.skyabyss.endgame.WorldEvents.Event ev = jp.revati.skyabyss.endgame.WorldEvents.current();
        long next = jp.revati.skyabyss.endgame.WorldEvents.ticksUntilNext(p.server);
        out.add(ev != null
                ? new Row("world_event", K + "world_event.now", List.of(), NONE, 0, K + "world_event.where")
                : next >= 0
                ? new Row("world_event", K + "world_event.next", List.of(), NONE, next, K + "world_event.where")
                : new Row("world_event", K + "world_event.idle", List.of(), NONE, 0, K + "world_event.where"));
        // 巡り路の野営地の景品 (3 日ごと)
        out.add(new Row("camp_prizes", K + "camp_prizes.status", List.of(), NONE, jp.revati.skyabyss.endgame.CircuitCamps.ticksUntilRotation(), K + "circuit.where"));
        // 宝箱 (人ごと、開けてから 20 分)
        out.add(new Row("chests", K + "chests.status", List.of(), NONE, 0, K + "chests.where"));
        return out;
    }

    static String fishName(String id) {
        jp.revati.skyabyss.fishing.Fish.Species sp = jp.revati.skyabyss.fishing.Fish.byId(id);
        return sp == null ? id : key("item.skyabyss." + sp.itemId());
    }

    public static void send(ServerPlayer p) {
        if (p.connection == null || p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        PacketDistributor.sendToPlayer(p, new ListPayload(rows(p)));
    }

    // ---------------------------------------------------------------- 手元で文にする
    public static Component status(Row r) {
        Object[] args = new Object[r.args().size() + 1];
        for (int i = 0; i < r.args().size(); i++) {
            String a = r.args().get(i);
            args[i] = a.startsWith("#") ? Component.translatable(a.substring(1)) : a;
        }
        args[r.args().size()] = left(r.ticks());
        return Component.translatable(r.statusKey(), args);
    }

    // 戻るまで (現実の時間。20 tick = 1 秒)
    public static Component left(long ticks) {
        long minutes = Math.max(1, ticks / 1200);
        if (minutes >= 60 * 24) {
            return Component.translatable(K + "left.days", minutes / (60 * 24), (minutes / 60) % 24);
        }
        if (minutes >= 60) {
            return Component.translatable(K + "left.hours", minutes / 60, minutes % 60);
        }
        return Component.translatable(K + "left.minutes", minutes);
    }

    // ---------------------------------------------------------------- 通信
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "clocks_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ListPayload(List<Row> rows) implements CustomPacketPayload {
        public static final Type<ListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "clocks_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ListPayload> STREAM_CODEC = StreamCodec.of((buf, l) -> {
            buf.writeVarInt(l.rows.size());
            for (Row r : l.rows) {
                buf.writeUtf(r.id());
                buf.writeUtf(r.statusKey());
                buf.writeVarInt(r.args().size());
                for (String a : r.args()) {
                    buf.writeUtf(a);
                }
                buf.writeVarInt(r.reset());
                buf.writeVarLong(Math.max(0, r.ticks()));
                buf.writeUtf(r.where());
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String id = buf.readUtf();
                String key = buf.readUtf();
                int na = buf.readVarInt();
                List<String> args = new ArrayList<>();
                for (int j = 0; j < na; j++) {
                    args.add(buf.readUtf());
                }
                rows.add(new Row(id, key, args, buf.readVarInt(), buf.readVarLong(), buf.readUtf()));
            }
            return new ListPayload(rows);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("clocks-1");
        r.playToServer(Request.TYPE, Request.STREAM_CODEC, (req, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer p) {
                send(p);
            }
        }));
        r.playToClient(ListPayload.TYPE, ListPayload.STREAM_CODEC, (l, c) -> c.enqueueWork(() -> {
            CLIENT = List.copyOf(l.rows());
            clientVersion++;
        }));
    }
}
