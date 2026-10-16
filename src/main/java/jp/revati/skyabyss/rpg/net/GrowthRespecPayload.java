package jp.revati.skyabyss.rpg.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: ステータス画面の「振り直す」(二度押しのあと)。画面に見えていた費用を添える (サーバーの費用と違えば断る)
public record GrowthRespecPayload(int cost) implements CustomPacketPayload {
    public static final Type<GrowthRespecPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "growth_respec"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrowthRespecPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.cost()),
            buf -> new GrowthRespecPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
