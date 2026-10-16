package jp.revati.skyabyss.sky;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// クライアントからサーバーへ「雲の目がどう閉じたか」(CloudEyeOutcome の番号) を伝えるパケット
public record CloudEyeEndPayload(int id, int outcome) implements CustomPacketPayload {
    public static final Type<CloudEyeEndPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cloud_eye_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloudEyeEndPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id());
                buf.writeVarInt(p.outcome());
            },
            buf -> new CloudEyeEndPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
