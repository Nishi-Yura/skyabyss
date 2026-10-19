package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 技の刻印を選ぶ (はじめては 1 点、付け替えは結晶 1)。サーバーで検める
public record SkillRunePayload(String skill, int index) implements CustomPacketPayload {
    public static final Type<SkillRunePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_rune"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillRunePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.skill(), 64);
                buf.writeVarInt(p.index());
            },
            buf -> new SkillRunePayload(buf.readUtf(64), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
