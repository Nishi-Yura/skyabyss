package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 技を一段覚える (技の画面の「習得」)。サーバーで検める
public record SkillLearnPayload(String skill) implements CustomPacketPayload {
    public static final Type<SkillLearnPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_learn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillLearnPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.skill(), 64);
            },
            buf -> new SkillLearnPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
