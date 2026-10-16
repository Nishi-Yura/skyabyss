package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 枠に技を置く (skill が空なら枠を空ける)
public record SkillSlotPayload(int slot, String skill) implements CustomPacketPayload {
    public static final Type<SkillSlotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillSlotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.slot());
                buf.writeUtf(p.skill(), 64);
            },
            buf -> new SkillSlotPayload(buf.readVarInt(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
