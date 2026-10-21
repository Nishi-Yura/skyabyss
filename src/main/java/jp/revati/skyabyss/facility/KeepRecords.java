package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.travel.TitanCalls;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCodex;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

// 空見の砦の老兵の「試練の記録」(docs/design/35 の 3.2)。祠の画面の図鑑と同じ記録 (TrialCodex) を、都で四体ぶん読み返す画面の中身。
//   巨神ごと: 出会ったか・心臓を止めたか、順路を越えた回数と最速、試練ごと (見たか・済んだ回数・任意の目標・出会った修飾の数)
//   称号の数。画面は client/facility/RecordsScreen。読むだけ (見ても「見た」の印は付けない)
public final class KeepRecords {
    private KeepRecords() {
    }

    public record Row(String cellId, boolean seen, int cleared, int goals, int modsSeen, int modsTotal) {
    }

    public record TitanPage(String titan, boolean met, boolean stopped, int routeClears, long routeBest, List<Row> rows) {
    }

    public record View(List<TitanPage> pages, int titles, int totalCleared) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID, "keep_records"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.of(View::encode, View::decode);

        private static void encode(RegistryFriendlyByteBuf buf, View v) {
            buf.writeVarInt(v.pages.size());
            for (TitanPage p : v.pages) {
                buf.writeUtf(p.titan(), 32);
                buf.writeBoolean(p.met());
                buf.writeBoolean(p.stopped());
                buf.writeVarInt(p.routeClears());
                buf.writeVarLong(p.routeBest());
                buf.writeVarInt(p.rows().size());
                for (Row r : p.rows()) {
                    buf.writeUtf(r.cellId(), 16);
                    buf.writeBoolean(r.seen());
                    buf.writeVarInt(r.cleared());
                    buf.writeVarInt(r.goals());
                    buf.writeVarInt(r.modsSeen());
                    buf.writeVarInt(r.modsTotal());
                }
            }
            buf.writeVarInt(v.titles);
            buf.writeVarInt(v.totalCleared);
        }

        private static View decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<TitanPage> pages = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String titan = buf.readUtf(32);
                boolean met = buf.readBoolean();
                boolean stopped = buf.readBoolean();
                int clears = buf.readVarInt();
                long best = buf.readVarLong();
                int m = buf.readVarInt();
                List<Row> rows = new ArrayList<>(m);
                for (int k = 0; k < m; k++) {
                    rows.add(new Row(buf.readUtf(16), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                pages.add(new TitanPage(titan, met, stopped, clears, best, rows));
            }
            return new View(pages, buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static View build(ServerPlayer player) {
        TrialCodex codex = TrialCodex.get(player.server);
        UUID id = player.getUUID();
        List<TitanPage> pages = new ArrayList<>();
        for (String titan : Titans.all()) {
            boolean stopped = Progression.stopped(player.server, id, titan);
            boolean met = stopped || TitanCalls.met(player, titan);
            List<Row> rows = new ArrayList<>();
            for (Trial.Def d : TrialCatalog.of(titan)) {
                TrialCodex.Entry e = codex.peek(id, d.id());
                rows.add(new Row(d.id(), e.seen || e.cleared > 0, e.cleared, e.goals, e.mods.size(), TrialRuntime.modTotal(d)));
            }
            pages.add(new TitanPage(titan, met, stopped, codex.routeClears(id, titan), codex.routeBest(id, titan), rows));
        }
        return new View(pages, codex.titles(id).size(), codex.totalCleared(id));
    }

    public static void open(ServerPlayer player) {
        ShopService.send(player, build(player));
    }
}
