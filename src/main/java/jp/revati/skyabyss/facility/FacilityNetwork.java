package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.facility.FacilityClient;
import jp.revati.skyabyss.facility.guild.GuildService;
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

// 都の施設のパケット (店・ギルド・地図を開く)。ほかの担当とぶつからないよう、別の版 (facility-1) で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FacilityNetwork {
    private static final String PROTOCOL_VERSION = "facility-1";

    private FacilityNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name));
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(ShopView.TYPE, ShopView.STREAM_CODEC, (p, c) -> FacilityClient.shop(p))
                .playToClient(GuildView.TYPE, GuildView.STREAM_CODEC, (p, c) -> FacilityClient.guild(p))
                .playToClient(CloseScreen.TYPE, CloseScreen.STREAM_CODEC, (p, c) -> FacilityClient.close())
                .playToClient(OpenMap.TYPE, OpenMap.STREAM_CODEC, (p, c) -> FacilityClient.openMap())
                .playToServer(ShopBuy.TYPE, ShopBuy.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        ShopService.buy(player, p.shop(), p.good(), p.times());
                    }
                })
                .playToServer(ShopSell.TYPE, ShopSell.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        ShopService.sell(player, p.shop(), p.index(), p.times());
                    }
                })
                .playToServer(ShopClosed.TYPE, ShopClosed.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        ShopService.closed(player);
                    }
                })
                .playToServer(GuildAction.TYPE, GuildAction.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        GuildService.act(player, p);
                    }
                });
    }

    // ---------------------------------------------------------------- 店
    // 並ぶ品。materials = 素材の値段 (個数入り)、have = その素材の手持ち、left = 今日あと何個 (-1 = 限りなし)、
    // locked = まだ章が届かない (chapter の章から)、rotating = 日替わりの品
    public record GoodView(String id, ItemStack stack, int coins, List<ItemStack> materials, List<Integer> have, int left, boolean locked,
                           int chapter, boolean rotating) {
    }

    // 買い取り。item の個数 = 一口の数、coins = 一口の値段、have = 売れる手持ち
    public record SellView(int index, ItemStack item, int coins, int have) {
    }

    public record ShopView(String shop, String title, int coins, List<GoodView> goods, List<SellView> sells) implements CustomPacketPayload {
        public static final Type<ShopView> TYPE = typeOf("shop_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopView> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.shop);
                    buf.writeUtf(p.title);
                    buf.writeVarInt(p.coins);
                    buf.writeVarInt(p.goods.size());
                    for (GoodView g : p.goods) {
                        buf.writeUtf(g.id());
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, g.stack());
                        buf.writeVarInt(g.coins());
                        buf.writeVarInt(g.materials().size());
                        for (int i = 0; i < g.materials().size(); i++) {
                            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, g.materials().get(i));
                            buf.writeVarInt(g.have().get(i));
                        }
                        buf.writeVarInt(g.left() + 1);
                        buf.writeBoolean(g.locked());
                        buf.writeVarInt(g.chapter());
                        buf.writeBoolean(g.rotating());
                    }
                    buf.writeVarInt(p.sells.size());
                    for (SellView s : p.sells) {
                        buf.writeVarInt(s.index());
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.item());
                        buf.writeVarInt(s.coins());
                        buf.writeVarInt(s.have());
                    }
                },
                buf -> {
                    String shop = buf.readUtf();
                    String title = buf.readUtf();
                    int coins = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<GoodView> goods = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        String id = buf.readUtf();
                        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        int price = buf.readVarInt();
                        int m = buf.readVarInt();
                        List<ItemStack> mats = new ArrayList<>(m);
                        List<Integer> have = new ArrayList<>(m);
                        for (int k = 0; k < m; k++) {
                            mats.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                            have.add(buf.readVarInt());
                        }
                        goods.add(new GoodView(id, stack, price, mats, have, buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt(), buf.readBoolean()));
                    }
                    int s = buf.readVarInt();
                    List<SellView> sells = new ArrayList<>(s);
                    for (int i = 0; i < s; i++) {
                        sells.add(new SellView(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));
                    }
                    return new ShopView(shop, title, coins, goods, sells);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShopBuy(String shop, String good, int times) implements CustomPacketPayload {
        public static final Type<ShopBuy> TYPE = typeOf("shop_buy");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopBuy> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.shop);
                    buf.writeUtf(p.good);
                    buf.writeVarInt(p.times);
                },
                buf -> new ShopBuy(buf.readUtf(), buf.readUtf(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShopSell(String shop, int index, int times) implements CustomPacketPayload {
        public static final Type<ShopSell> TYPE = typeOf("shop_sell");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopSell> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.shop);
                    buf.writeVarInt(p.index);
                    buf.writeVarInt(p.times);
                },
                buf -> new ShopSell(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShopClosed() implements CustomPacketPayload {
        public static final Type<ShopClosed> TYPE = typeOf("shop_closed");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopClosed> STREAM_CODEC = StreamCodec.unit(new ShopClosed());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 参加者: 施設の画面を閉じる (店の人から離れた)
    public record CloseScreen() implements CustomPacketPayload {
        public static final Type<CloseScreen> TYPE = typeOf("facility_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, CloseScreen> STREAM_CODEC = StreamCodec.unit(new CloseScreen());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 参加者: 旅の地図 (M) を開く (止まり木の番)
    public record OpenMap() implements CustomPacketPayload {
        public static final Type<OpenMap> TYPE = typeOf("facility_open_map");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMap> STREAM_CODEC = StreamCodec.unit(new OpenMap());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- ギルド
    public record GuildRow(String id, String name, String tag, int color, int accent, int members, boolean open, boolean applied, String leader) {
    }

    public record MemberRow(UUID id, String name, boolean leader, boolean online) {
    }

    // mine = 入っているギルドの ID (空 = 入っていない)、leader = 自分が長か、open = 自分のギルドが誰でも入れるか
    public record GuildView(String mine, boolean leader, boolean open, List<GuildRow> guilds, List<MemberRow> members, List<MemberRow> applicants,
                            int max) implements CustomPacketPayload {
        public static final Type<GuildView> TYPE = typeOf("guild_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, GuildView> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.mine);
                    buf.writeBoolean(p.leader);
                    buf.writeBoolean(p.open);
                    buf.writeVarInt(p.guilds.size());
                    for (GuildRow g : p.guilds) {
                        buf.writeUtf(g.id());
                        buf.writeUtf(g.name());
                        buf.writeUtf(g.tag());
                        buf.writeVarInt(g.color());
                        buf.writeVarInt(g.accent());
                        buf.writeVarInt(g.members());
                        buf.writeBoolean(g.open());
                        buf.writeBoolean(g.applied());
                        buf.writeUtf(g.leader());
                    }
                    writeMembers(buf, p.members);
                    writeMembers(buf, p.applicants);
                    buf.writeVarInt(p.max);
                },
                buf -> {
                    String mine = buf.readUtf();
                    boolean leader = buf.readBoolean();
                    boolean open = buf.readBoolean();
                    int n = buf.readVarInt();
                    List<GuildRow> rows = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        rows.add(new GuildRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                                buf.readBoolean(), buf.readBoolean(), buf.readUtf()));
                    }
                    List<MemberRow> members = readMembers(buf);
                    List<MemberRow> applicants = readMembers(buf);
                    return new GuildView(mine, leader, open, rows, members, applicants, buf.readVarInt());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeMembers(RegistryFriendlyByteBuf buf, List<MemberRow> list) {
        buf.writeVarInt(list.size());
        for (MemberRow m : list) {
            UUIDUtil.STREAM_CODEC.encode(buf, m.id());
            buf.writeUtf(m.name());
            buf.writeBoolean(m.leader());
            buf.writeBoolean(m.online());
        }
    }

    private static List<MemberRow> readMembers(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<MemberRow> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new MemberRow(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
        }
        return out;
    }

    // 参加者 → サーバー: ギルドの画面で押した (op = create / apply / leave / accept / deny / kick / open)。
    // target = ギルドの ID か人の UUID、text = 新しいギルドの名前、tag = 印、color / accent = 旗の色、flag = 誰でも入れる
    public record GuildAction(String op, String target, String text, String tag, int color, int accent, boolean flag) implements CustomPacketPayload {
        public static final Type<GuildAction> TYPE = typeOf("guild_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, GuildAction> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.op, 16);
                    buf.writeUtf(p.target, 64);
                    buf.writeUtf(p.text, 64);
                    buf.writeUtf(p.tag, 16);
                    buf.writeVarInt(p.color);
                    buf.writeVarInt(p.accent);
                    buf.writeBoolean(p.flag);
                },
                buf -> new GuildAction(buf.readUtf(16), buf.readUtf(64), buf.readUtf(64), buf.readUtf(16), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
