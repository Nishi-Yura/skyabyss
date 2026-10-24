package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

// サーバー → 持ち主と近くの人: 技が決まって出た合図 (見た目だけ。当たりや数字はサーバーが決めて済ませている)。
// 見た目の段取り (溜め・放ち・当たり・残り) は各自の手元で組む (client/skill/SkillFx)。1 回の発動で 1 通だけ。
// target = サーバーが決めた当たりの点 (天の驟雨の降る中心など。手元の向きからでは同じ点にならない技だけ。無ければ null)
public record SkillFxPayload(int caster, String skill, int rank, boolean chain, @javax.annotation.Nullable Vec3 target) implements CustomPacketPayload {
    public static final Type<SkillFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.caster());
                buf.writeUtf(p.skill(), 64);
                buf.writeVarInt(p.rank());
                buf.writeBoolean(p.chain());
                buf.writeBoolean(p.target() != null);
                if (p.target() != null) {
                    buf.writeDouble(p.target().x);
                    buf.writeDouble(p.target().y);
                    buf.writeDouble(p.target().z);
                }
            },
            buf -> new SkillFxPayload(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
