package jp.revati.skyabyss.prologue;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// クライアントからサーバーへ「見る者が消えた (どう消えたか)」を知らせるパケット
public record WatcherEndPayload(int id, int outcome) implements CustomPacketPayload {
    public static final Type<WatcherEndPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "watcher_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WatcherEndPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id());
                buf.writeVarInt(p.outcome());
            },
            buf -> new WatcherEndPayload(buf.readVarInt(), buf.readVarInt()));

    public WatcherEndPayload(int id, WatcherOutcome outcome) {
        this(id, outcome.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
