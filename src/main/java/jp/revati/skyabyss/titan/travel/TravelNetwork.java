package jp.revati.skyabyss.titan.travel;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.travel.client.TravelClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 巨神での移動のパケット。ほかの担当とぶつからないように、別の版で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TravelNetwork {
    private static final String PROTOCOL_VERSION = "travel-3";

    private TravelNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Chart.TYPE, Chart.STREAM_CODEC, (payload, context) -> TravelClient.openChart(payload))
                .playToClient(Veil.TYPE, Veil.STREAM_CODEC, (payload, context) -> TravelClient.veil(payload.on(), payload.style()))
                .playToServer(Choose.TYPE, Choose.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        jp.revati.skyabyss.titan.young.YoungCarriers.choose(player, payload.id());
                    }
                })
                .playToServer(Forget.TYPE, Forget.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        jp.revati.skyabyss.titan.young.YoungCarriers.forgetWaymark(player, payload.id());
                    }
                })
                .playToServer(Disembark.TYPE, Disembark.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        jp.revati.skyabyss.titan.young.YoungCarriers.disembark(player);
                    }
                });
    }

    // 行き先の一つ。kind: 0 = はじまりの地 (世界の初期スポーン)、1 = 碑、2 = 出会った巨神 (name = 巨神の名前)。
    // valid = 選べる。lock = 選べない理由 (gui.skyabyss.young.lock.<lock>。空なら壊された碑)
    public record Dest(String id, String name, String biome, int x, int y, int z, int kind, boolean valid, int seconds, String lock) {
        static void write(RegistryFriendlyByteBuf buf, Dest d) {
            buf.writeUtf(d.id);
            buf.writeUtf(d.name);
            buf.writeUtf(d.biome);
            buf.writeVarInt(d.x);
            buf.writeVarInt(d.y);
            buf.writeVarInt(d.z);
            buf.writeVarInt(d.kind);
            buf.writeBoolean(d.valid);
            buf.writeVarInt(d.seconds);
            buf.writeUtf(d.lock);
        }

        static Dest read(RegistryFriendlyByteBuf buf) {
            return new Dest(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readUtf());
        }
    }

    // サーバー → 乗り場の人: 海図を開く (leader = 行き先を決められる人か)。chosen は決まった行き先 (まだなら空)
    public record Chart(String titan, boolean leader, String leaderName, int originX, int originZ, String chosen, List<Dest> dests)
            implements CustomPacketPayload {
        public static final Type<Chart> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "travel_chart"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chart> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.titan);
                    buf.writeBoolean(p.leader);
                    buf.writeUtf(p.leaderName);
                    buf.writeVarInt(p.originX);
                    buf.writeVarInt(p.originZ);
                    buf.writeUtf(p.chosen);
                    buf.writeVarInt(p.dests.size());
                    p.dests.forEach(d -> Dest.write(buf, d));
                },
                buf -> {
                    String titan = buf.readUtf();
                    boolean leader = buf.readBoolean();
                    String leaderName = buf.readUtf();
                    int ox = buf.readVarInt();
                    int oz = buf.readVarInt();
                    String chosen = buf.readUtf();
                    int n = Math.min(buf.readVarInt(), 64);
                    List<Dest> dests = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        dests.add(Dest.read(buf));
                    }
                    return new Chart(titan, leader, leaderName, ox, oz, chosen, dests);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 乗っている人: 雲の帳 (画面が雲の白に包まれる) の始まりと終わり。style は旅の型 (TitanKind.Style の順。1 = 潮の深み、深い青)
    public record Veil(boolean on, int style) implements CustomPacketPayload {
        public static final Type<Veil> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "travel_veil"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Veil> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.on);
                    buf.writeVarInt(p.style);
                }, buf -> new Veil(buf.readBoolean(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 乗り場の人 → サーバー: この行き先へ
    public record Choose(String id) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "travel_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id), buf -> new Choose(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 乗り場の人 → サーバー: この碑を忘れる
    public record Forget(String id) implements CustomPacketPayload {
        public static final Type<Forget> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "travel_forget"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Forget> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id), buf -> new Forget(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 乗り場の人 → サーバー: 出発の前に降りる (鎖で地上へ降ろしてもらう)
    public record Disembark() implements CustomPacketPayload {
        public static final Type<Disembark> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "travel_disembark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Disembark> STREAM_CODEC = StreamCodec.unit(new Disembark());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
