package jp.revati.skyabyss.facility.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 取引所の画面 (client/facility/TradeScreen) のサーバー側。画面は中身を映し、押したことを返すだけ (決めるのは Trades)。
//   取引所の番 (係の人 trade_keeper) から開くと「番の前」= 出す・申請ができる (8 ブロックまで)。
//   /trade はどこからでも開ける (自分の出品の申請を受ける・断る、取り下げ、申請の取り下げ、受け取り。出す・申請は番の前で)
//   /skyabyss facility trade (OP) は番の前と同じに開く (試し・撮影用)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TradeService {
    public static final double REACH = 8.0;
    // npcId: -1 = 番の前と同じ (コマンド)、-2 = どこからでも (/trade)
    private static final int ANYWHERE_AT = -1;
    private static final int REMOTE = -2;

    private static final class Session {
        int npcId;
        boolean lastAt;

        Session(int npcId) {
            this.npcId = npcId;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TradeService() {
    }

    // 番から開く (npc = null ならコマンドの「番の前と同じ」)
    public static void open(ServerPlayer player, @Nullable Entity npc) {
        SESSIONS.put(player.getUUID(), new Session(npc == null ? ANYWHERE_AT : npc.getId()));
        send(player, view(player, -1));
    }

    // どこからでも (/trade)。番の前で開いていれば、そのまま
    public static void openRemote(ServerPlayer player, int tab) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !atKeeper(player)) {
            SESSIONS.put(player.getUUID(), new Session(REMOTE));
        }
        send(player, view(player, tab));
    }

    public static boolean atKeeper(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.npcId == REMOTE) {
            return false;
        }
        if (s.npcId == ANYWHERE_AT) {
            return true;
        }
        Entity e = player.level().getEntity(s.npcId);
        return e != null && e.isAlive() && e.distanceToSqr(player) <= REACH * REACH;
    }

    // ---------------------------------------------------------------- 映すもの
    public static TradeNetwork.View view(ServerPlayer p, int tab) {
        TradeData data = TradeData.get(p.server);
        long now = Trades.now();
        List<TradeNetwork.Row> market = new ArrayList<>();
        List<TradeNetwork.MineRow> mine = new ArrayList<>();
        List<TradeNetwork.Row> applied = new ArrayList<>();
        List<TradeData.Listing> all = data.all();
        java.util.Collections.reverse(all);
        for (TradeData.Listing l : all) {
            boolean own = l.seller.equals(p.getUUID());
            TradeData.Application my = l.apps.get(p.getUUID());
            TradeNetwork.Row row = new TradeNetwork.Row(l.id, l.sellerName, l.give, l.want, l.expires - now, l.apps.size(), my != null,
                    !own && Trades.canPay(p, l.want));
            if (own) {
                List<TradeNetwork.AppRow> apps = new ArrayList<>();
                for (TradeData.Application a : l.apps.values()) {
                    ServerPlayer who = Trades.online(p.server, a.applicant);
                    apps.add(new TradeNetwork.AppRow(a.applicant, a.name, who != null, a.offer, who != null && Trades.canPay(who, a.offer)));
                }
                mine.add(new TradeNetwork.MineRow(row, apps));
            } else if (market.size() < 120) {
                market.add(row);
            }
            if (my != null) {
                applied.add(new TradeNetwork.Row(l.id, l.sellerName, l.give, my.offer, l.expires - now, l.apps.size(), true, Trades.canPay(p, my.offer)));
            }
        }
        return new TradeNetwork.View(Coins.count(p), atKeeper(p), market, mine, applied, data.mail(p.getUUID()), Trades.MAX_LISTINGS, tab);
    }

    // ---------------------------------------------------------------- 押したこと
    public static Trades.Result act(ServerPlayer p, TradeNetwork.Action a) {
        if (a.op().equals("close")) {
            SESSIONS.remove(p.getUUID());
            return null;
        }
        if (a.op().equals("refresh")) {
            send(p, view(p, -1));
            return null;
        }
        Trades.Result r = switch (a.op()) {
            case "list" -> atKeeper(p) ? Trades.list(p, picks(a), items(a.items()), a.itemCounts(), a.coins()) : Trades.Result.NOT_AT_KEEPER;
            case "apply" -> atKeeper(p) ? Trades.apply(p, a.id(), null, 0) : Trades.Result.NOT_AT_KEEPER;
            case "offer" -> atKeeper(p) ? Trades.apply(p, a.id(), picks(a), a.coins()) : Trades.Result.NOT_AT_KEEPER;
            case "withdraw" -> Trades.withdraw(p, a.id());
            case "cancel" -> Trades.cancel(p, a.id());
            case "accept", "reject" -> {
                UUID who;
                try {
                    who = UUID.fromString(a.target());
                } catch (IllegalArgumentException e) {
                    yield Trades.Result.NO_APPLICATION;
                }
                yield a.op().equals("accept") ? Trades.accept(p, a.id(), who) : Trades.reject(p, a.id(), who);
            }
            case "claim" -> Trades.claim(p);
            default -> null;
        };
        if (r == null) {
            return null;
        }
        if (r.ok()) {
            Notice.gain(p, r.key());
        } else {
            Notice.hint(p, r.key());
        }
        send(p, view(p, -1));
        return r;
    }

    private static List<Trades.Pick> picks(TradeNetwork.Action a) {
        List<Trades.Pick> out = new ArrayList<>();
        for (int i = 0; i < a.slots().size() && i < a.counts().size() && i < 8; i++) {
            out.add(new Trades.Pick(a.slots().get(i), a.counts().get(i)));
        }
        return out;
    }

    private static List<Item> items(List<String> ids) {
        List<Item> out = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            out.add(rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null));
        }
        return out;
    }

    // 画面を開いている人の中身を新しくする (ほかの人の申請・受けたなど)
    public static void refresh(ServerPlayer p) {
        if (SESSIONS.containsKey(p.getUUID())) {
            send(p, view(p, -1));
        }
    }

    static void send(ServerPlayer player, TradeNetwork.View v) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, v);
    }

    // 番から離れた / 近づいた: 「番の前」の印を映し直す
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            Session s = SESSIONS.get(player.getUUID());
            if (s != null) {
                boolean at = atKeeper(player);
                if (at != s.lastAt) {
                    s.lastAt = at;
                    send(player, view(player, -1));
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    // /trade [つまみ 0..4]  取引所の画面をどこからでも開く (誰でも)
    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("trade")
                .executes(c -> {
                    openRemote(c.getSource().getPlayerOrException(), -1);
                    return 1;
                })
                .then(Commands.argument("tab", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4)).executes(c -> {
                    openRemote(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tab"));
                    return 1;
                })));
    }

    // 撮影・試し用 (/skyabyss facility trade demo): ほかの旅人の出品 3 つ、自分の出品 1 つとそれへの申請 2 つ (いない人)、受け取り箱の品
    public static int demo(ServerPlayer p) {
        TradeData data = TradeData.get(p.server);
        long now = Trades.now();
        String[] names = {"ミナト", "カイ", "シオン"};
        ItemStack[][] gives = {{new ItemStack(jp.revati.skyabyss.item.outfit.MaterialGear.SKY_STONE.get(), 3)},
                {new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.STRONG_HEALING.get()), new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.SMOKED_MEAT.get(), 16)},
                {new ItemStack(jp.revati.skyabyss.item.outfit.MaterialGear.INDIGO_BOOKMARK.get(), 2)}};
        // 値は鐘銭だけ (2026-11-01「トレードで使えるのはお金のみ」)
        ItemStack[][] wants = {{Trades.coins(60)}, {Trades.coins(1_250)}, {Trades.coins(240_000)}};
        for (int i = 0; i < names.length; i++) {
            TradeData.Listing l = data.create(UUID.nameUUIDFromBytes(("trade_demo_" + i).getBytes()), names[i], now, now + Trades.EXPIRE_MS - i * 30L * 3_600_000L);
            l.give.addAll(java.util.Arrays.asList(gives[i]));
            l.want.addAll(java.util.Arrays.asList(wants[i]));
            if (i == 0) {
                // 自分の申請 (ほしいもののまま)
                TradeData.Application my = new TradeData.Application(p.getUUID());
                my.name = p.getGameProfile().getName();
                my.offer.add(Trades.coins(60));
                l.apps.put(my.applicant, my);
            }
        }
        TradeData.Listing mine = data.create(p.getUUID(), p.getGameProfile().getName(), now, now + Trades.EXPIRE_MS);
        mine.give.add(new ItemStack(jp.revati.skyabyss.item.outfit.MaterialGear.IRON_LUMP.get(), 16));
        mine.want.add(Trades.coins(80));
        TradeData.Application a = new TradeData.Application(UUID.nameUUIDFromBytes("trade_demo_a".getBytes()));
        a.name = "ミナト";
        a.offer.add(Trades.coins(80));
        mine.apps.put(a.applicant, a);
        TradeData.Application b = new TradeData.Application(UUID.nameUUIDFromBytes("trade_demo_b".getBytes()));
        b.name = "シオン";
        b.offer.add(Trades.coins(70));
        mine.apps.put(b.applicant, b);
        data.addMail(p.getUUID(), new ItemStack(jp.revati.skyabyss.item.outfit.MaterialGear.CHARCOAL_LUMP.get(), 8));
        data.addMail(p.getUUID(), new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 32));
        data.dirty();
        refresh(p);
        return 4;
    }
}
