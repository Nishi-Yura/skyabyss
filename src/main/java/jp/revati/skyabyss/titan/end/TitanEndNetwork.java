package jp.revati.skyabyss.titan.end;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 巨神に降ろされるときの暗転のパケット (サーバー → その人だけ)。共有の ModNetwork とは別の登録口にして、版を独立させる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanEndNetwork {
    private static final String PROTOCOL_VERSION = "2";

    private TitanEndNetwork() {
    }

    // 画面が黒へ in tick で落ち、hold tick 黒のまま、out tick で明ける (out = 0 なら、黒のまま消える。場面が続きを受け持つとき)
    public record Fade(int in, int hold, int out) implements CustomPacketPayload {
        public static final Type<Fade> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "titan_end_fade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fade> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.in());
                    buf.writeVarInt(p.hold());
                    buf.writeVarInt(p.out());
                },
                buf -> new Fade(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 光の柱と枠 (乗り込み口・戻る場所の目印)。サーバー → その人だけ。key が同じ知らせは置き換え、ttl tick たつと消える。
    // 枠は centre を中心に、yaw の向きに hx × hz (横 × 奥行き)、高さ hy の半分の大きさ。柱は beamFrom〜beamTo (y)
    public record Beam(String key, int rgb, double cx, double cy, double cz, float hx, float hy, float hz, float yaw,
            double beamFrom, double beamTo, float beamRadius, int ttl) implements CustomPacketPayload {
        public static final Type<Beam> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "board_beam"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Beam> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.key(), 32);
                    buf.writeInt(p.rgb());
                    buf.writeDouble(p.cx());
                    buf.writeDouble(p.cy());
                    buf.writeDouble(p.cz());
                    buf.writeFloat(p.hx());
                    buf.writeFloat(p.hy());
                    buf.writeFloat(p.hz());
                    buf.writeFloat(p.yaw());
                    buf.writeDouble(p.beamFrom());
                    buf.writeDouble(p.beamTo());
                    buf.writeFloat(p.beamRadius());
                    buf.writeVarInt(p.ttl());
                },
                buf -> new Beam(buf.readUtf(32), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Fade.TYPE, Fade.STREAM_CODEC, (payload, context) -> jp.revati.skyabyss.client.titan.CarryFadeClient.receive(payload))
                .playToClient(Beam.TYPE, Beam.STREAM_CODEC, (payload, context) -> jp.revati.skyabyss.client.titan.BoardBeamClient.receive(payload));
    }
}
