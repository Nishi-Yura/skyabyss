package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 深みの順位の石の画面へ送る中身 (DepthRecords.show → client/depths/DepthRankScreen)。
//   groups / players: 上位 (名前・表示の深さ・心臓の下の深さ)、ours / mine: 見る人の組と人の表示の深さ (0 = まだ)、next: 次の節目 (心臓の下。0 = 全部越えた)
public record DepthRankPayload(List<Line> groups, List<Line> players, int ours, int mine, int next) implements CustomPacketPayload {
    public static final Type<DepthRankPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "depth_rank"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepthRankPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                write(buf, p.groups);
                write(buf, p.players);
                buf.writeVarInt(p.ours);
                buf.writeVarInt(p.mine);
                buf.writeVarInt(p.next);
            },
            buf -> new DepthRankPayload(read(buf), read(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    public record Line(String name, int depth, int beyond) {
    }

    private static void write(RegistryFriendlyByteBuf buf, List<Line> lines) {
        buf.writeVarInt(lines.size());
        for (Line l : lines) {
            buf.writeUtf(l.name(), 128);
            buf.writeVarInt(l.depth());
            buf.writeVarInt(l.beyond());
        }
    }

    private static List<Line> read(RegistryFriendlyByteBuf buf) {
        int n = Math.min(32, buf.readVarInt());
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new Line(buf.readUtf(128), buf.readVarInt(), buf.readVarInt()));
        }
        return out;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Net {
        private Net() {
        }

        @SubscribeEvent
        static void onPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> jp.revati.skyabyss.client.depths.DepthRankScreen.open(p));
        }
    }
}
