package jp.revati.skyabyss.sky;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバーからクライアントへ「通過が始まった」ことを伝えるパケット
public record SkyPassPayload(SkyPass pass) implements CustomPacketPayload {
    public static final Type<SkyPassPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "sky_pass"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyPassPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                SkyPass p = payload.pass();
                buf.writeLong(p.startTick());
                buf.writeDouble(p.originX());
                buf.writeDouble(p.originZ());
                buf.writeFloat(p.dirX());
                buf.writeFloat(p.dirZ());
                buf.writeFloat(p.speed());
                buf.writeVarInt(p.duration());
            },
            buf -> new SkyPassPayload(new SkyPass(
                    buf.readLong(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
