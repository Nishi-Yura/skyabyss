package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 試練の板の画面のパケット (docs/design/29 の 2.7・4.2)。決めるのは全部サーバー。クライアントは見せるだけ。
//   Open (サーバー → 持ち主): 板の画面を開く。いまの三つ (名前・類・段・稀さ・修飾の札・済んだか)、今週の試練、図鑑の行、称号
public final class TrialBoardNet {
    private static final String PROTOCOL_VERSION = "trial_board-2";

    private TrialBoardNet() {
    }

    // 修飾の札 1 枚。type = layout・number・enemy・hazard・goal・extreme・quiet。code は type ごとの中身 (画面が文にする)。
    // hidden = 板では「？」(入ってから分かる極)。rarity = その札の稀さ (0 常 / 1 稀 / 2 極)
    public record Chip(String type, String code, boolean hidden, int rarity) {
    }

    // 引いた試練 1 つ。state: 0 = まだ (初めて引いた) / 1 = 前に引いた / 2 = 済んだことがある / 3 = 修飾を全部見た
    public record Card(String cellId, int rarity, boolean quiet, List<Chip> chips, boolean clearedNow, int state) {
    }

    // 図鑑の行 1 つ
    public record Row(String cellId, boolean seen, int cleared, int goals, int modsSeen, int modsTotal) {
    }

    // 深淵のつまみ (クライアント → サーバー。docs/design/31 の B04): on = 深淵にする。サーバーが転生 3 回以上かを確かめる
    public record AbyssToggle(String titan, boolean on) implements CustomPacketPayload {
        public static final Type<AbyssToggle> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "trial_board_abyss"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbyssToggle> STREAM_CODEC = StreamCodec.of(
                (buf, t) -> {
                    buf.writeUtf(t.titan, 32);
                    buf.writeBoolean(t.on);
                }, buf -> new AbyssToggle(buf.readUtf(32), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // abyssOpen = この人は深淵を選べる (転生 3 回以上)。abyssOn = このグループは深淵を選んでいる
    public record Open(String titan, int cycle, List<Card> cards, String weeklyId, boolean weeklyDone, boolean weeklyNow, List<Row> codex,
            List<String> titles, boolean abyssOpen, boolean abyssOn) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "trial_board_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(Open::encode, Open::decode);

        private static void encode(RegistryFriendlyByteBuf buf, Open o) {
            buf.writeUtf(o.titan, 32);
            buf.writeVarInt(o.cycle);
            buf.writeVarInt(o.cards.size());
            for (Card c : o.cards) {
                buf.writeUtf(c.cellId, 16);
                buf.writeVarInt(c.rarity);
                buf.writeBoolean(c.quiet);
                buf.writeVarInt(c.chips.size());
                for (Chip ch : c.chips) {
                    buf.writeUtf(ch.type, 16);
                    buf.writeUtf(ch.code, 48);
                    buf.writeBoolean(ch.hidden);
                    buf.writeVarInt(ch.rarity);
                }
                buf.writeBoolean(c.clearedNow);
                buf.writeVarInt(c.state);
            }
            buf.writeUtf(o.weeklyId, 16);
            buf.writeBoolean(o.weeklyDone);
            buf.writeBoolean(o.weeklyNow);
            buf.writeVarInt(o.codex.size());
            for (Row r : o.codex) {
                buf.writeUtf(r.cellId, 16);
                buf.writeBoolean(r.seen);
                buf.writeVarInt(r.cleared);
                buf.writeVarInt(r.goals);
                buf.writeVarInt(r.modsSeen);
                buf.writeVarInt(r.modsTotal);
            }
            buf.writeVarInt(o.titles.size());
            for (String t : o.titles) {
                buf.writeUtf(t, 48);
            }
            buf.writeBoolean(o.abyssOpen);
            buf.writeBoolean(o.abyssOn);
        }

        private static Open decode(RegistryFriendlyByteBuf buf) {
            String titan = buf.readUtf(32);
            int cycle = buf.readVarInt();
            int n = buf.readVarInt();
            List<Card> cards = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String id = buf.readUtf(16);
                int rarity = buf.readVarInt();
                boolean quiet = buf.readBoolean();
                int m = buf.readVarInt();
                List<Chip> chips = new ArrayList<>();
                for (int j = 0; j < m; j++) {
                    chips.add(new Chip(buf.readUtf(16), buf.readUtf(48), buf.readBoolean(), buf.readVarInt()));
                }
                cards.add(new Card(id, rarity, quiet, List.copyOf(chips), buf.readBoolean(), buf.readVarInt()));
            }
            String weeklyId = buf.readUtf(16);
            boolean weeklyDone = buf.readBoolean();
            boolean weeklyNow = buf.readBoolean();
            int k = buf.readVarInt();
            List<Row> codex = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                codex.add(new Row(buf.readUtf(16), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            int t = buf.readVarInt();
            List<String> titles = new ArrayList<>();
            for (int i = 0; i < t; i++) {
                titles.add(buf.readUtf(48));
            }
            return new Open(titan, cycle, List.copyOf(cards), weeklyId, weeklyDone, weeklyNow, List.copyOf(codex), List.copyOf(titles), buf.readBoolean(),
                    buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION).playToClient(Open.TYPE, Open.STREAM_CODEC, (p, ctx) -> jp.revati.skyabyss.client.trial.TrialBoardClient.onOpen(p))
                .playToServer(AbyssToggle.TYPE, AbyssToggle.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        ctx.enqueueWork(() -> TrialBoard.toggleAbyss(sp, p.titan(), p.on()));
                    }
                });
    }
}
