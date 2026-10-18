package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー -> クライアント: 床の印を取り消す (持ち主が倒れた・段階が変わった)。自然に当たり終えたものには送らない
public record TelegraphCancelPayload(int id) implements CustomPacketPayload {
    public static final Type<TelegraphCancelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "telegraph_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelegraphCancelPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.id()),
            buf -> new TelegraphCancelPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
