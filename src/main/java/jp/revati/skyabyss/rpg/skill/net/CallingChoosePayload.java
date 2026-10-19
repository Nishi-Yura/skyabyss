package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 天職を継ぐ (木の id。一つ目か二つ目かはサーバーが記録から決める)
public record CallingChoosePayload(String tree) implements CustomPacketPayload {
    public static final Type<CallingChoosePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "calling_choose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CallingChoosePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.tree(), 32),
            buf -> new CallingChoosePayload(buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
