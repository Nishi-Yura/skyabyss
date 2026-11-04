package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 記録 (2026-11-05 の削る・まとめる頁 ct_records = one)。速さや数の記録を一つの画面に、つまみで分けて:
//   踏破 (巨神ごとの出会い・心臓・順路の回数と最速・試練の数・巨神ごとの最速の人) / 塔 / 深み / 釣り / 早駆け / 転生 / 空見
// 今の場所の板と係の人は残し、押すと同じ画面のその頁が開く (記録の間の老兵・塔守・深みの順位の石・食べ物屋・旅の番・転生の石と記録の板)。
// 旅の記録 (つまみの「旅」) の釦からも開く。中身はサーバーが行の並びにして送る (Request → View)。チャットに書いていた塔・釣り・早駆けは say で集める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RecordsHub {
    public static final int CLEAR = 0;
    public static final int TOWER = 1;
    public static final int DEPTH = 2;
    public static final int FISHING = 3;
    public static final int SPRINT = 4;
    public static final int REBIRTH = 5;
    public static final int FOUNDER = 6;
    public static final String[] TABS = {"clear", "tower", "depth", "fishing", "sprint", "rebirth", "founder"};
    static final int GOLD = 0xF2D78A;

    private RecordsHub() {
    }

    // ---------------------------------------------------------------- チャットに書いていた行を集める
    private static final ThreadLocal<List<Component>> CAPTURE = new ThreadLocal<>();

    // 集めているあいだは並びへ、ほかはチャットへ
    public static void say(ServerPlayer p, Component line) {
        List<Component> to = CAPTURE.get();
        if (to != null) {
            to.add(line);
        } else {
            p.sendSystemMessage(line);
        }
    }

    public static boolean capturing() {
        return CAPTURE.get() != null;
    }

    static List<Component> capture(Runnable r) {
        List<Component> out = new ArrayList<>();
        CAPTURE.set(out);
        try {
            r.run();
        } finally {
            CAPTURE.remove();
        }
        return out;
    }

    static Component head(String key, Object... args) {
        return Component.translatable(key, args).withStyle(s -> s.withColor(GOLD));
    }

    // ---------------------------------------------------------------- 頁ごとの行
    public static List<Component> lines(ServerPlayer p, int tab) {
        return switch (tab) {
            case CLEAR -> clear(p);
            case TOWER -> capture(() -> jp.revati.skyabyss.tower.TowerDesk.records(p));
            case DEPTH -> depth(p);
            case FISHING -> capture(() -> jp.revati.skyabyss.fishing.Fishing.show(p));
            case SPRINT -> capture(() -> jp.revati.skyabyss.mount.MountSprint.show(p));
            case REBIRTH -> rebirth(p);
            default -> founder(p);
        };
    }

    static List<Component> clear(ServerPlayer p) {
        List<Component> out = new ArrayList<>();
        jp.revati.skyabyss.facility.KeepRecords.View v = jp.revati.skyabyss.facility.KeepRecords.build(p);
        out.add(head("gui.skyabyss.records_hub.clear.mine"));
        for (jp.revati.skyabyss.facility.KeepRecords.TitanPage page : v.pages()) {
            Component name = jp.revati.skyabyss.progress.Progression.titanName(page.titan());
            if (!page.met()) {
                out.add(Component.translatable("gui.skyabyss.records_hub.clear.unmet", name));
                continue;
            }
            int seen = 0;
            int cleared = 0;
            for (jp.revati.skyabyss.facility.KeepRecords.Row r : page.rows()) {
                seen += r.seen() ? 1 : 0;
                cleared += r.cleared();
            }
            out.add(Component.translatable("gui.skyabyss.records_hub.clear.titan", name,
                    Component.translatable(page.stopped() ? "gui.skyabyss.records_hub.clear.stopped" : "gui.skyabyss.records_hub.clear.awake"),
                    page.routeClears(), page.routeBest() > 0 ? jp.revati.skyabyss.tower.Towers.time(page.routeBest()) : "―", seen, page.rows().size(), cleared));
        }
        out.add(Component.translatable("gui.skyabyss.records_hub.clear.total", v.totalCleared(), v.titles()));
        out.add(Component.empty());
        out.add(head("gui.skyabyss.records_hub.clear.fastest"));
        for (jp.revati.skyabyss.rebirth.RebirthNet.RecordLine l : jp.revati.skyabyss.rebirth.RebirthBoards.recordLines(p.server, null)) {
            out.add(l.ticks() < 0 ? Component.translatable("gui.skyabyss.records_hub.clear.fastest_none", l.titan())
                    : Component.translatable("gui.skyabyss.records_hub.clear.fastest_line", l.titan(), l.who(), jp.revati.skyabyss.tower.Towers.time(l.ticks())));
        }
        return out;
    }

    static List<Component> depth(ServerPlayer p) {
        return jp.revati.skyabyss.story.ch5.depths.DepthRecords.hubLines(p.server, p);
    }

    static List<Component> rebirth(ServerPlayer p) {
        List<Component> out = new ArrayList<>();
        for (jp.revati.skyabyss.rebirth.RebirthLeaderboard.Metric m : jp.revati.skyabyss.rebirth.RebirthLeaderboard.Metric.values()) {
            if (!out.isEmpty()) {
                out.add(Component.empty());
            }
            out.add(head("skyabyss_rebirth.board.metric." + m.ordinal()));
            List<jp.revati.skyabyss.rebirth.RebirthLeaderboard.Row> rows = jp.revati.skyabyss.rebirth.Rebirths.top(p.server, m, 10);
            if (rows.isEmpty()) {
                out.add(Component.translatable("gui.skyabyss.records_hub.none"));
            }
            for (int i = 0; i < rows.size(); i++) {
                jp.revati.skyabyss.rebirth.RebirthLeaderboard.Row r = rows.get(i);
                String value = switch (m) {
                    case REBIRTHS -> String.valueOf(r.rebirths());
                    case FASTEST -> jp.revati.skyabyss.tower.Towers.time(r.fastestLife());
                    case FEWEST_DEATHS -> String.valueOf(r.fewestDeaths());
                };
                out.add(Component.translatable("gui.skyabyss.records_hub.rank", i + 1, r.name(), value));
            }
        }
        return out;
    }

    static List<Component> founder(ServerPlayer p) {
        List<Component> out = new ArrayList<>();
        out.add(head("gui.skyabyss.records_hub.founder.mine"));
        long best = jp.revati.skyabyss.story.finale.founder.Founder.best(p);
        out.add(best > 0 ? Component.literal(jp.revati.skyabyss.story.finale.founder.Founder.time(best)) : Component.translatable("gui.skyabyss.records_hub.none"));
        return out;
    }

    // ---------------------------------------------------------------- 開く
    public static void open(ServerPlayer p, int tab) {
        if (p.connection == null || p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        int t = Math.max(0, Math.min(TABS.length - 1, tab));
        PacketDistributor.sendToPlayer(p, new View(t, jp.revati.skyabyss.rebirth.RebirthView.data(p).hidden(), lines(p, t)));
    }

    // ---------------------------------------------------------------- 通信 (手元の画面は client/record/RecordsHubScreen)
    public record Request(int tab) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "records_hub_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Request::tab, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // hidden: 転生の順位表に載せない設定 (「転生」の頁の釦。前の順位の石の画面の釦と同じ。RebirthNet.HiddenPayload で切り替える)
    public record View(int tab, boolean hidden, List<Component> lines) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "records_hub_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, View::tab,
                ByteBufCodecs.BOOL, View::hidden, ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()), View::lines, View::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 手元: 届いた頁を開く (画面は client 側。ここでは関数を差し込んでもらう)
    public static volatile java.util.function.Consumer<View> clientOpen = v -> {
    };

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("records-hub-1");
        r.playToServer(Request.TYPE, Request.STREAM_CODEC, (req, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer p) {
                open(p, req.tab());
            }
        }));
        r.playToClient(View.TYPE, View.STREAM_CODEC, (v, c) -> c.enqueueWork(() -> clientOpen.accept(v)));
    }
}
