package jp.revati.skyabyss.grave;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.grave.GraveMarkerHud;
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

// 墓の標 (rpg_brushup A8、docs/design/11 の 6 章、18 の HUD の置き場)。自分の墓の場所を、自分にだけ送る。
// 0.5 秒ごとに「その人の墓 (中身が残っているもの)」を見て、前に送ったものと変わったときだけ送る (拾い終えて墓がなくなったら、空を一度だけ送って止める)。
// 他人の墓は決して送らない (パーティの仲間の墓も。開けられないため)。描くのはクライアント (GraveMarkerHud): 画面の中の墓の上の札・画面の外の矢印と距離。
// 墓へ NEAR_BLOCKS 以内に近づくと、クライアントが標を消す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GraveMarkers {
    private static final String PROTOCOL_VERSION = "grave-marker-1";
    public static final int NEAR_BLOCKS = 8;
    // 一度に送る墓の数 (新しい順)
    public static final int MAX = 3;
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private GraveMarkers() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Pos.TYPE, Pos.STREAM_CODEC, (payload, context) -> GraveMarkerHud.receive(payload));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        tick(server, Players.snapshot(server.getPlayerList().getPlayers()), (p, pos) -> {
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

    public static void tick(MinecraftServer server, Iterable<? extends ServerPlayer> receivers, BiConsumer<ServerPlayer, Pos> sink) {
        GraveData data = GraveData.get(server);
        for (ServerPlayer p : receivers) {
            List<Mark> marks = marksFor(data, p.getUUID());
            if (marks.isEmpty()) {
                if (LAST.remove(p.getUUID()) != null) {
                    sink.accept(p, new Pos(List.of()));
                }
                continue;
            }
            Integer hash = marks.hashCode();
            if (!Objects.equals(LAST.put(p.getUUID(), hash), hash)) {
                sink.accept(p, new Pos(marks));
            }
        }
    }

    // owner 本人の、中身が残っている墓 (新しい順、MAX まで)。ほかの人の墓は入らない
    public static List<Mark> marksFor(GraveData data, UUID owner) {
        List<GraveData.Grave> owned = data.owned(owner);
        List<Mark> out = new ArrayList<>();
        for (int i = owned.size() - 1; i >= 0 && out.size() < MAX; i--) {
            GraveData.Grave g = owned.get(i);
            if (!g.isEmpty()) {
                out.add(new Mark(g.dimension.location().toString(), g.pos.getX(), g.pos.getY(), g.pos.getZ()));
            }
        }
        return out;
    }

    // 自己テスト用: 送った控えを消す
    public static void clearForTest() {
        LAST.clear();
    }

    public record Mark(String dimension, int x, int y, int z) {
    }

    public record Pos(List<Mark> marks) implements CustomPacketPayload {
        public static final Type<Pos> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "grave_marker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pos> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.marks.size());
                    for (Mark m : p.marks) {
                        buf.writeUtf(m.dimension, 64);
                        buf.writeInt(m.x);
                        buf.writeInt(m.y);
                        buf.writeInt(m.z);
                    }
                },
                buf -> {
                    int n = Math.min(buf.readVarInt(), MAX);
                    List<Mark> list = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        list.add(new Mark(buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt()));
                    }
                    return new Pos(list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
