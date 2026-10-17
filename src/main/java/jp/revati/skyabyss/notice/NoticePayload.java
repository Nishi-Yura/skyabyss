package jp.revati.skyabyss.notice;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → クライアント: 画面に出す知らせ一つ。extra は場所の名前の副題 (なければ空)
public record NoticePayload(Notice.Kind kind, Component text, Component extra) implements CustomPacketPayload {
    public static final Type<NoticePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind().ordinal());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.text());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.extra());
            },
            buf -> {
                Notice.Kind[] kinds = Notice.Kind.values();
                int i = buf.readByte();
                Notice.Kind kind = i >= 0 && i < kinds.length ? kinds[i] : Notice.Kind.BAR;
                return new NoticePayload(kind, ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
