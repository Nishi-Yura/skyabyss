package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主と近くの人: 技が決まって出た合図 (見た目だけ。当たりや数字はサーバーが決めて済ませている)。
// 見た目の段取り (溜め・放ち・当たり・残り) は各自の手元で組む (client/skill/SkillFx)。1 回の発動で 1 通だけ
public record SkillFxPayload(int caster, String skill, int rank, boolean chain) implements CustomPacketPayload {
    public static final Type<SkillFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.caster());
                buf.writeUtf(p.skill(), 64);
                buf.writeVarInt(p.rank());
                buf.writeBoolean(p.chain());
            },
            buf -> new SkillFxPayload(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
