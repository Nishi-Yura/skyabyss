package jp.revati.skyabyss.grave;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.grave.GraveScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 語り部の「墓を選ぶ」画面のパケット (版 grave-1。ほかの担当とぶつからないよう別に登録する)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GraveNetwork {
    private static final String PROTOCOL_VERSION = "grave-1";

    private GraveNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Open.TYPE, Open.STREAM_CODEC, (payload, context) -> GraveScreen.receive(payload))
                .playToServer(Pick.TYPE, Pick.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        GraveChooser.pick(player, payload.id());
                    }
                });
    }

    // 画面の一行 (一つの墓)。age は墓ができてからの tick
    public record Entry(UUID id, String dimension, int x, int y, int z, int items, int xp, long age) {
    }

    // サーバー → 参加者: 救える墓の一覧 (古い順)。percent は救いで戻る割合。空なら画面を閉じる
    public record Open(int percent, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "grave_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.percent);
                    buf.writeVarInt(p.entries.size());
                    for (Entry e : p.entries) {
                        buf.writeUUID(e.id());
                        buf.writeUtf(e.dimension());
                        buf.writeInt(e.x());
                        buf.writeInt(e.y());
                        buf.writeInt(e.z());
                        buf.writeVarInt(e.items());
                        buf.writeVarInt(e.xp());
                        buf.writeVarLong(e.age());
                    }
                },
                buf -> {
                    int percent = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<Entry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
                                buf.readVarLong()));
                    }
                    return new Open(percent, list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 参加者 → サーバー: この墓を救ってほしい
    public record Pick(UUID id) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "grave_pick"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pick> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.id), buf -> new Pick(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
