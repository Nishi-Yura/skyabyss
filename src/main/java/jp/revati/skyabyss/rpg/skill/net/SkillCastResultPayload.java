package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 技を使えたか (枠を光らせる・揺らすため)
public record SkillCastResultPayload(int slot, boolean ok) implements CustomPacketPayload {
    public static final Type<SkillCastResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_cast_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillCastResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.slot());
                buf.writeBoolean(p.ok());
            },
            buf -> new SkillCastResultPayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
