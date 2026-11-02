package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 型の溜まるもの (Gauges.Kind の序数。-1 = 無し) と今の値 (整数。変わったときだけ送る)
public record GaugePayload(int kind, int value) implements CustomPacketPayload {
    public static final Type<GaugePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_gauge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GaugePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind() + 1);
                buf.writeVarInt(p.value());
            },
            buf -> new GaugePayload(buf.readVarInt() - 1, buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
