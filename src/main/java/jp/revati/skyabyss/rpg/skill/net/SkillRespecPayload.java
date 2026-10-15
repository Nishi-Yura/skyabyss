package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 技を振り直す。cost は画面に出ていた費用 (サーバーの費用と違えば断る)
public record SkillRespecPayload(int cost) implements CustomPacketPayload {
    public static final Type<SkillRespecPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_respec"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillRespecPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.cost());
            },
            buf -> new SkillRespecPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
