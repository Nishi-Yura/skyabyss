package jp.revati.skyabyss.rpg.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 経験値を得た (画面の小さな「+12」と、レベルが上がったときの文字に使う)。
// reason は理由の翻訳キー (空なら数だけ)。levels は今回上がったレベルの数
public record GrowthGainPayload(int amount, String reason, int levels) implements CustomPacketPayload {
    public static final Type<GrowthGainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "growth_gain"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrowthGainPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.amount());
                buf.writeUtf(p.reason(), 128);
                buf.writeVarInt(p.levels());
            },
            buf -> new GrowthGainPayload(buf.readVarInt(), buf.readUtf(128), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
