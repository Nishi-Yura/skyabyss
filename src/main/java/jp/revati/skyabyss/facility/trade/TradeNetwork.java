package jp.revati.skyabyss.facility.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.facility.FacilityClient;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 取引所の画面のパケット (版 trade-1。ほかの担当とぶつからないよう別に登録する)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TradeNetwork {
    private static final String PROTOCOL_VERSION = "trade-1";

    private TradeNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name));
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(View.TYPE, View.STREAM_CODEC, (p, c) -> FacilityClient.trade(p))
                .playToServer(Action.TYPE, Action.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        TradeService.act(player, p);
                    }
                });
    }

    // 品の並び (数は 99 を越えることがあるので別に書く)
    static void writeStacks(RegistryFriendlyByteBuf buf, List<ItemStack> list) {
        buf.writeVarInt(list.size());
        for (ItemStack s : list) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
            buf.writeVarInt(s.getCount());
        }
    }

    static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        List<ItemStack> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ItemStack s = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int c = buf.readVarInt();
            if (!s.isEmpty()) {
                s.setCount(Math.max(1, c));
                out.add(s);
            }
        }
        return out;
    }

    // 出品の行。left = 期限までの ms、apps = 申請の数、applied = 自分が申請している、canPay = ほしいものが自分の持ち物にそろっている
    // (申請の一覧では want = 自分の申し出)
    public record Row(int id, String seller, List<ItemStack> give, List<ItemStack> want, long left, int apps, boolean applied, boolean canPay) {
        static void write(RegistryFriendlyByteBuf buf, Row r) {
            buf.writeVarInt(r.id);
            buf.writeUtf(r.seller);
            writeStacks(buf, r.give);
            writeStacks(buf, r.want);
            buf.writeVarLong(Math.max(0, r.left));
            buf.writeVarInt(r.apps);
            buf.writeBoolean(r.applied);
            buf.writeBoolean(r.canPay);
        }

        static Row read(RegistryFriendlyByteBuf buf) {
            return new Row(buf.readVarInt(), buf.readUtf(), readStacks(buf), readStacks(buf), buf.readVarLong(), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean());
        }
    }

    // 申請の行 (自分の出品へ)。online = いる、ok = いて、申し出の品がそろっている
    public record AppRow(UUID who, String name, boolean online, List<ItemStack> offer, boolean ok) {
    }

    public record MineRow(Row row, List<AppRow> apps) {
    }

    // tab = 開くつまみ (-1 = 今のまま)。atKeeper = 取引所の番の前にいる (出す・申請はここでだけ)
    public record View(int coins, boolean atKeeper, List<Row> market, List<MineRow> mine, List<Row> applied, List<ItemStack> mail, int maxListings,
                       int tab) implements CustomPacketPayload {
        public static final Type<View> TYPE = typeOf("trade_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.coins);
                    buf.writeBoolean(p.atKeeper);
                    buf.writeVarInt(p.market.size());
                    p.market.forEach(r -> Row.write(buf, r));
                    buf.writeVarInt(p.mine.size());
                    for (MineRow m : p.mine) {
                        Row.write(buf, m.row());
                        buf.writeVarInt(m.apps().size());
                        for (AppRow a : m.apps()) {
                            UUIDUtil.STREAM_CODEC.encode(buf, a.who());
                            buf.writeUtf(a.name());
                            buf.writeBoolean(a.online());
                            writeStacks(buf, a.offer());
                            buf.writeBoolean(a.ok());
                        }
                    }
                    buf.writeVarInt(p.applied.size());
                    p.applied.forEach(r -> Row.write(buf, r));
                    writeStacks(buf, p.mail);
                    buf.writeVarInt(p.maxListings);
                    buf.writeVarInt(p.tab + 1);
                },
                buf -> {
                    int coins = buf.readVarInt();
                    boolean at = buf.readBoolean();
                    int n = buf.readVarInt();
                    List<Row> market = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        market.add(Row.read(buf));
                    }
                    int m = buf.readVarInt();
                    List<MineRow> mine = new ArrayList<>(m);
                    for (int i = 0; i < m; i++) {
                        Row r = Row.read(buf);
                        int an = buf.readVarInt();
                        List<AppRow> apps = new ArrayList<>(an);
                        for (int k = 0; k < an; k++) {
                            apps.add(new AppRow(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readBoolean(), readStacks(buf), buf.readBoolean()));
                        }
                        mine.add(new MineRow(r, apps));
                    }
                    int a = buf.readVarInt();
                    List<Row> applied = new ArrayList<>(a);
                    for (int i = 0; i < a; i++) {
                        applied.add(Row.read(buf));
                    }
                    List<ItemStack> mail = readStacks(buf);
                    return new View(coins, at, market, mine, applied, mail, buf.readVarInt(), buf.readVarInt() - 1);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 参加者 → サーバー: op = list / apply / offer / withdraw / cancel / accept / reject / claim / refresh / close
    //   id = 出品の番号、target = 申請した人の UUID、coins = 鐘銭 (ほしい・申し出る)、slots / counts = 持ち物の枠と数 (出す品・申し出の品)、
    //   items / itemCounts = ほしいものの品の ID と数 (探して選んだ型)
    public record Action(String op, int id, String target, int coins, List<Integer> slots, List<Integer> counts, List<String> items,
                         List<Integer> itemCounts) implements CustomPacketPayload {
        public static final Type<Action> TYPE = typeOf("trade_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.op, 16);
                    buf.writeVarInt(p.id);
                    buf.writeUtf(p.target, 64);
                    buf.writeVarInt(p.coins);
                    writeInts(buf, p.slots);
                    writeInts(buf, p.counts);
                    buf.writeVarInt(p.items.size());
                    p.items.forEach(s -> buf.writeUtf(s, 128));
                    writeInts(buf, p.itemCounts);
                },
                buf -> {
                    String op = buf.readUtf(16);
                    int id = buf.readVarInt();
                    String target = buf.readUtf(64);
                    int coins = buf.readVarInt();
                    List<Integer> slots = readInts(buf);
                    List<Integer> counts = readInts(buf);
                    int n = Math.min(buf.readVarInt(), 16);
                    List<String> items = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        items.add(buf.readUtf(128));
                    }
                    return new Action(op, id, target, coins, slots, counts, items, readInts(buf));
                });

        public static Action simple(String op, int id, String target) {
            return new Action(op, id, target, 0, List.of(), List.of(), List.of(), List.of());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeInts(RegistryFriendlyByteBuf buf, List<Integer> list) {
        buf.writeVarInt(list.size());
        list.forEach(buf::writeVarInt);
    }

    private static List<Integer> readInts(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(buf.readVarInt());
        }
        return out;
    }
}
