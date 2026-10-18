package jp.revati.skyabyss.titan.group;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.client.GroupClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// グループごとの見かけのパケット (サーバー → 背中にいる人)。
//  Overlay: その人のグループにだけ当てるブロックの上書き (座標と、ブロックの状態の id)。いつも全部を送る (空なら全部消す)
//  Hidden : その人から見えなくする人 (別のグループの人) のエンティティ id。いつも全部を送る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GroupNetwork {
    private static final String PROTOCOL_VERSION = "group-1";

    private GroupNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Overlay.TYPE, Overlay.STREAM_CODEC, (payload, context) -> GroupClient.receiveOverlay(payload))
                .playToClient(Hidden.TYPE, Hidden.STREAM_CODEC, (payload, context) -> GroupClient.receiveHidden(payload));
    }

    public record Overlay(long[] pos, int[] states) implements CustomPacketPayload {
        public static final Type<Overlay> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "group_overlay"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Overlay> STREAM_CODEC = StreamCodec.of(
                (buf, o) -> {
                    buf.writeVarInt(o.pos.length);
                    for (int i = 0; i < o.pos.length; i++) {
                        buf.writeLong(o.pos[i]);
                        buf.writeVarInt(o.states[i]);
                    }
                },
                buf -> {
                    int n = buf.readVarInt();
                    long[] pos = new long[n];
                    int[] states = new int[n];
                    for (int i = 0; i < n; i++) {
                        pos[i] = buf.readLong();
                        states[i] = buf.readVarInt();
                    }
                    return new Overlay(pos, states);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Hidden(int[] ids) implements CustomPacketPayload {
        public static final Type<Hidden> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "group_hidden"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hidden> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(
                        list -> list.stream().mapToInt(Integer::intValue).toArray(),
                        arr -> java.util.Arrays.stream(arr).boxed().toList()),
                Hidden::ids, Hidden::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
