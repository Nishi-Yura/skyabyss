package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 天職を選ぶ画面を開く (which = 1 一つ目、2 二つ目)
public record CallingOpenPayload(int which, boolean open) implements CustomPacketPayload {
    public static final Type<CallingOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "calling_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CallingOpenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.which()); buf.writeBoolean(p.open()); },
            buf -> new CallingOpenPayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
