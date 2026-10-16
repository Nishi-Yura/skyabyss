package jp.revati.skyabyss.story;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバーからその人へ「天の鍵がそろった場面を見せる」。days: 序章から数えたワールドの日数 (終わりの札に出す)。
// replay: コマンドで見直す (記録は変わらない)
public record SkyKeyScenePayload(int days, boolean replay) implements CustomPacketPayload {
    public static final Type<SkyKeyScenePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "sky_key_scene"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyKeyScenePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.days());
                buf.writeBoolean(p.replay());
            },
            buf -> new SkyKeyScenePayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
