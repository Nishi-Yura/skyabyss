package jp.revati.skyabyss.sky;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバーから見せる相手一人にだけ「雲の目を開く」と伝えるパケット。
// yaw はマイクラの向き (0 = 南)、elevation は見上げる角度。どちらも度。stage は CloudEyeStage の番号
public record CloudEyePayload(int id, float yaw, float elevation, int stage, int seed) implements CustomPacketPayload {
    public static final Type<CloudEyePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cloud_eye"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloudEyePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id());
                buf.writeFloat(p.yaw());
                buf.writeFloat(p.elevation());
                buf.writeVarInt(p.stage());
                buf.writeInt(p.seed());
            },
            buf -> new CloudEyePayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
