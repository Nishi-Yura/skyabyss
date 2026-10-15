package jp.revati.skyabyss.prologue;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバーから、見せる相手一人にだけ「ここに見る者を立たせる」と伝えるパケット。
// id はサーバーが振る通し番号で、終わったときの知らせ (WatcherEndPayload) と組み合わせる
public record WatcherPayload(int id, double x, double y, double z, float bodyYaw, int stage) implements CustomPacketPayload {
    public static final Type<WatcherPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "watcher"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WatcherPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id());
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeFloat(p.bodyYaw());
                buf.writeVarInt(p.stage());
            },
            buf -> new WatcherPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
