package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 今のマナ (変わった分が 1 以上のときなどだけ送る)
public record ManaPayload(float mana) implements CustomPacketPayload {
    public static final Type<ManaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_mana"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManaPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.mana());
            },
            buf -> new ManaPayload(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
