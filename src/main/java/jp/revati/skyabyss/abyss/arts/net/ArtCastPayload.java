package jp.revati.skyabyss.abyss.arts.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 深淵術の鍵を押した (art は AbyssArt の順)。使えるかはサーバーで決める
public record ArtCastPayload(int art) implements CustomPacketPayload {
    public static final Type<ArtCastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_art_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArtCastPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.art()),
            buf -> new ArtCastPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
