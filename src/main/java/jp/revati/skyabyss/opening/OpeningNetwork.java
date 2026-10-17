package jp.revati.skyabyss.opening;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.opening.OpeningClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 序盤の拍子のパケット (版 opening-1。ほかの担当とぶつからない)
//   Objective : 目的の帯に出す序盤の目的 (ID は objective.skyabyss.<ID>) と、目印の場所。ID が空なら帯はクエストの目的を出す
//   Cue       : 画面と音の演出の始まり (目覚め・足音・歌)。演出そのものはクライアントが、受け取った時刻から自分で進める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class OpeningNetwork {
    private static final String PROTOCOL_VERSION = "opening-1";

    private OpeningNetwork() {
    }

    // 演出の種類 (CuePayload.kind)
    public static final int CUE_AWAKE = 0;
    public static final int CUE_FOOTSTEPS = 1;
    public static final int CUE_SONG = 2;
    // 進行中の演出を止める (コマンドで拍子を戻したとき)
    public static final int CUE_STOP = 3;

    public record ObjectivePayload(String id, boolean hasTarget, long target) implements CustomPacketPayload {
        public static final Type<ObjectivePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "opening_objective"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ObjectivePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.id());
                    buf.writeBoolean(p.hasTarget());
                    buf.writeLong(p.target());
                },
                buf -> new ObjectivePayload(buf.readUtf(), buf.readBoolean(), buf.readLong()));

        public static ObjectivePayload of(String id, BlockPos target) {
            return new ObjectivePayload(id, target != null, target == null ? 0L : target.asLong());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // startTick: 始まったゲーム時間。yaw: 足音の向き (度。プレイヤーの yaw と同じ向きの決め方)
    public record CuePayload(int kind, long startTick, float yaw) implements CustomPacketPayload {
        public static final Type<CuePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "opening_cue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CuePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind());
                    buf.writeLong(p.startTick());
                    buf.writeFloat(p.yaw());
                },
                buf -> new CuePayload(buf.readVarInt(), buf.readLong(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行される
        event.registrar(PROTOCOL_VERSION)
                .playToClient(ObjectivePayload.TYPE, ObjectivePayload.STREAM_CODEC, (payload, context) -> OpeningClient.receive(payload))
                .playToClient(CuePayload.TYPE, CuePayload.STREAM_CODEC, (payload, context) -> OpeningClient.receive(payload));
    }
}
