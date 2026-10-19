package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.client.DownedClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 起こす仕組み (Downed) のパケット。
//  サーバー → 人: パーティの倒れた人の一覧 (Sync。自分を含む。空 = 誰も倒れていない)
//  人 → サーバー: 「この人を起こしている (使うを押し続けている)」(Hold)。サーバーが距離・仲間・状態を検める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DownedPackets {
    private static final String PROTOCOL_VERSION = "party-revive-1";

    private DownedPackets() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> DownedClient.receive(payload))
                .playToServer(Hold.TYPE, Hold.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Downed.hold(player, payload.target(), player.level().getGameTime());
                    }
                });
    }

    // ticksLeft: 残りの tick (5 刻み)、progressPercent: 起こしている進み (0..100)
    public record Entry(String id, String name, String dimension, int x, int y, int z, int ticksLeft, int progressPercent) {
    }

    public record Sync(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "downed_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.entries.size());
                    for (Entry e : s.entries) {
                        buf.writeUtf(e.id, 64);
                        buf.writeUtf(e.name, 32);
                        buf.writeUtf(e.dimension, 64);
                        buf.writeInt(e.x);
                        buf.writeInt(e.y);
                        buf.writeInt(e.z);
                        buf.writeVarInt(e.ticksLeft);
                        buf.writeVarInt(e.progressPercent);
                    }
                },
                buf -> {
                    int n = Math.min(buf.readVarInt(), 16);
                    List<Entry> list = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        list.add(new Entry(buf.readUtf(64), buf.readUtf(32), buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readVarInt(), buf.readVarInt()));
                    }
                    return new Sync(list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Hold(UUID target) implements CustomPacketPayload {
        public static final Type<Hold> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "revive_hold"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hold> STREAM_CODEC = StreamCodec.of(
                (buf, h) -> buf.writeUUID(h.target),
                buf -> new Hold(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
