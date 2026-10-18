package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.client.PartyMarkers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 仲間の位置 (docs/design/10 の 10.9)。同じパーティの人にだけ、ほかの仲間の位置を送る。
// 0.5 秒ごとに見て、受け取る人ごとに「中身が前に送ったものと変わったとき」だけ送る (位置は 1 ブロック刻み、体力は 10% 刻み)。
// 抜けた・解散した・ログアウトしたときは、空の一覧を一度だけ送って止める。パーティの外の人には何も送らない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PartyLocator {
    private static final String PROTOCOL_VERSION = "party-pos-1";
    // 送った中身の控え (受け取る人 → 中身の hash)。空を送ったら消す
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private PartyLocator() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Pos.TYPE, Pos.STREAM_CODEC, (payload, context) -> PartyMarkers.receive(payload));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        tick(server, server.getPlayerList().getPlayers(), id -> server.getPlayerList().getPlayer(id), (p, pos) -> {
            if (p.connection != null) {
                PacketDistributor.sendToPlayer(p, pos);
            }
        });
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        LAST.clear();
    }

    // receivers: 送り先の候補。lookup: 仲間を UUID から引く。自己テストでは偽のプレイヤーを渡す
    public static void tick(MinecraftServer server, Iterable<? extends ServerPlayer> receivers, Function<UUID, ServerPlayer> lookup,
            BiConsumer<ServerPlayer, Pos> sink) {
        PartyData data = PartyData.get(server);
        for (ServerPlayer p : receivers) {
            PartyData.Party party = data.of(p.getUUID());
            List<Entry> entries = party == null ? List.of() : entriesFor(party, p.getUUID(), lookup);
            if (entries.isEmpty()) {
                // 前に送ったものがあれば、空を一度だけ送って止める
                if (LAST.remove(p.getUUID()) != null) {
                    sink.accept(p, new Pos(List.of()));
                }
                continue;
            }
            Integer hash = entries.hashCode();
            if (!Objects.equals(LAST.put(p.getUUID(), hash), hash)) {
                sink.accept(p, new Pos(entries));
            }
        }
    }

    // 自己テスト用: 送った控えを消す
    public static void clearForTest() {
        LAST.clear();
    }

    // receiver 以外の、同じパーティの今いる仲間。パーティの外の人は入らない
    public static List<Entry> entriesFor(PartyData.Party party, UUID receiver, Function<UUID, ServerPlayer> lookup) {
        List<Entry> out = new ArrayList<>();
        for (UUID id : party.members) {
            if (id.equals(receiver)) {
                continue;
            }
            ServerPlayer m = lookup.apply(id);
            if (m == null || m.isSpectator()) {
                continue;
            }
            int hp = m.getMaxHealth() <= 0 ? 0 : Math.round(Math.min(1f, m.getHealth() / m.getMaxHealth()) * 10f) * 10;
            out.add(new Entry(id.toString(), party.names.getOrDefault(id, m.getGameProfile().getName()), m.level().dimension().location().toString(),
                    (int) Math.floor(m.getX()), (int) Math.floor(m.getY()), (int) Math.floor(m.getZ()), hp));
        }
        return out;
    }

    public record Entry(String id, String name, String dimension, int x, int y, int z, int healthPercent) {
    }

    public record Pos(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Pos> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_pos"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pos> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entries.size());
                    for (Entry e : p.entries) {
                        buf.writeUtf(e.id, 64);
                        buf.writeUtf(e.name, 32);
                        buf.writeUtf(e.dimension, 64);
                        buf.writeInt(e.x);
                        buf.writeInt(e.y);
                        buf.writeInt(e.z);
                        buf.writeByte(e.healthPercent);
                    }
                },
                buf -> {
                    int n = Math.min(buf.readVarInt(), 16);
                    List<Entry> list = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        list.add(new Entry(buf.readUtf(64), buf.readUtf(32), buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readByte()));
                    }
                    return new Pos(list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
