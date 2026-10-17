package jp.revati.skyabyss.quest.free;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.quest.BoardScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 言伝ての板のパケット (版 board-1。ほかの担当とぶつからないよう別に登録する)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BoardNetwork {
    private static final String PROTOCOL_VERSION = "board-1";

    private BoardNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Open.TYPE, Open.STREAM_CODEC, (payload, context) -> BoardScreen.receive(payload))
                .playToServer(Accept.TYPE, Accept.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Board.accept(player, payload.quest());
                    }
                })
                .playToServer(Closed.TYPE, Closed.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Board.closed(player);
                    }
                });
    }

    // サーバー → 参加者: 板の中身。used / quota は今日受けた日替わりの数と枠、until は次の入れ替えまでの tick
    public record Open(boolean npc, int used, int quota, long until, List<Board.Line> lines) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "board_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.npc);
                    buf.writeVarInt(p.used);
                    buf.writeVarInt(p.quota);
                    buf.writeVarLong(p.until);
                    buf.writeVarInt(p.lines.size());
                    for (Board.Line l : p.lines) {
                        buf.writeUtf(l.quest());
                        buf.writeByte(l.state());
                        buf.writeVarInt(l.mates());
                    }
                },
                buf -> {
                    boolean npc = buf.readBoolean();
                    int used = buf.readVarInt();
                    int quota = buf.readVarInt();
                    long until = buf.readVarLong();
                    int n = buf.readVarInt();
                    List<Board.Line> lines = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        lines.add(new Board.Line(buf.readUtf(), buf.readByte(), buf.readVarInt()));
                    }
                    return new Open(npc, used, quota, until, lines);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Accept(String quest) implements CustomPacketPayload {
        public static final Type<Accept> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "board_accept"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Accept> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.quest), buf -> new Accept(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Closed() implements CustomPacketPayload {
        public static final Type<Closed> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "board_closed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Closed> STREAM_CODEC = StreamCodec.unit(new Closed());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
