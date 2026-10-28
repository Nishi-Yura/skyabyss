package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// クライアント → サーバー: 技の木 v2 の節を取る (docs/design/48)。職は今の職 (SkillData.curJob)。サーバーが検める
public record SkillNodePayload(String node) implements CustomPacketPayload {
    public static final Type<SkillNodePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_node"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillNodePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), SkillNodePayload::node, SkillNodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
