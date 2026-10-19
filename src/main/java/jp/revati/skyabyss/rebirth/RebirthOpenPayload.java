package jp.revati.skyabyss.rebirth;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 台座を右クリックした。転生の画面を開く
public record RebirthOpenPayload() implements CustomPacketPayload {
    public static final Type<RebirthOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "rebirth_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RebirthOpenPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
    }, buf -> new RebirthOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
