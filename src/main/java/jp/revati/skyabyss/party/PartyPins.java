package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.util.Players;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 旅の地図のピンをパーティーの仲間に見せる (2026-11-09 の声「マップでピンさしたら、1人称でも見えてほしい。パーティー組んでたらパーティーの人にも見えるように」)。
// ピンは手もとの設定 (client/map/MapPrefs) に置き、変えたときと入ったときに自分のピンをサーバーへ送る (Mine)。
// サーバーは人ごとの今のピンを覚え (保存はしない)、2 秒ごとに、各人へ同じパーティーの仲間のピンをまとめて送る (Mates。中身が変わったときだけ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PartyPins {
    // 一人一本 (client/map/MapPrefs.MAX_PINS と同じ)
    public static final int MAX = 1;
    private static final Map<UUID, List<int[]>> PINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Mates> LAST = new ConcurrentHashMap<>();

    private PartyPins() {
    }

    // 人 → サーバー: 自分のピン (x, z の並び)
    public record Mine(List<int[]> pins) implements CustomPacketPayload {
        public static final Type<Mine> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_pins_mine"));
        public static final StreamCodec<FriendlyByteBuf, Mine> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            buf.writeVarInt(Math.min(MAX, m.pins.size()));
            for (int i = 0; i < Math.min(MAX, m.pins.size()); i++) {
                buf.writeVarInt(m.pins.get(i)[0]);
                buf.writeVarInt(m.pins.get(i)[1]);
            }
        }, buf -> {
            int n = Math.min(MAX, buf.readVarInt());
            List<int[]> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                out.add(new int[] {buf.readVarInt(), buf.readVarInt()});
            }
            return new Mine(out);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 仲間の一本のピン (持ち主の名前と、その人のピンの番号)
    public record Pin(String owner, int number, int x, int z) {
    }

    // サーバー → 人: 同じパーティーの仲間のピン
    public record Mates(List<Pin> pins) implements CustomPacketPayload {
        public static final Type<Mates> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_pins_mates"));
        public static final StreamCodec<FriendlyByteBuf, Mates> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            buf.writeVarInt(m.pins.size());
            for (Pin p : m.pins) {
                buf.writeUtf(p.owner, 32);
                buf.writeVarInt(p.number);
                buf.writeVarInt(p.x);
                buf.writeVarInt(p.z);
            }
        }, buf -> {
            int n = Math.min(64, buf.readVarInt());
            List<Pin> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                out.add(new Pin(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new Mates(out);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("party-pins-1")
                .playToServer(Mine.TYPE, Mine.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer p) {
                        PINS.put(p.getUUID(), List.copyOf(payload.pins()));
                    }
                })
                .playToClient(Mates.TYPE, Mates.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                        () -> jp.revati.skyabyss.client.map.PinMarkers.receive(payload)));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PINS.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }

    // 人のピン (自己テスト向け)
    public static void set(UUID who, List<int[]> pins) {
        PINS.put(who, List.copyOf(pins));
    }

    // その人に見せる仲間のピン
    public static List<Pin> matesOf(ServerPlayer p) {
        List<Pin> out = new ArrayList<>();
        PartyData.Party party = Parties.of(p);
        if (party == null) {
            return out;
        }
        for (ServerPlayer mate : Parties.onlineMembers(p.server, party)) {
            if (mate == p) {
                continue;
            }
            List<int[]> pins = PINS.getOrDefault(mate.getUUID(), List.of());
            for (int i = 0; i < pins.size(); i++) {
                out.add(new Pin(mate.getGameProfile().getName(), i + 1, pins.get(i)[0], pins.get(i)[1]));
            }
        }
        return out;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 21) {
            return;
        }
        for (ServerPlayer p : Players.snapshot(server.getPlayerList().getPlayers())) {
            if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null) {
                continue;
            }
            Mates now = new Mates(matesOf(p));
            Mates before = LAST.get(p.getUUID());
            if (before != null && before.pins().equals(now.pins())) {
                continue;
            }
            LAST.put(p.getUUID(), now);
            PacketDistributor.sendToPlayer(p, now);
        }
    }
}
