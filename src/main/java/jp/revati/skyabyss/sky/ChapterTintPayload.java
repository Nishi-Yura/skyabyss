package jp.revati.skyabyss.sky;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → クライアント: 本人の章の空の色味 (ChapterSky.Stage の tint)。強さ 0 なら何もしない
public record ChapterTintPayload(int rgb, float strength) implements CustomPacketPayload {
    public static final Type<ChapterTintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "chapter_tint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChapterTintPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.rgb());
                buf.writeFloat(p.strength());
            },
            buf -> new ChapterTintPayload(buf.readInt(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
