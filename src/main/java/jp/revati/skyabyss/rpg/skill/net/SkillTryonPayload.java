package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 試着 (仮振り) の確定。仮に振った順の技の id
public record SkillTryonPayload(java.util.List<String> skills) implements CustomPacketPayload {
    public static final Type<SkillTryonPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_tryon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillTryonPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.skills().size());
                for (String id : p.skills()) {
                    buf.writeUtf(id, 64);
                }
            },
            buf -> new SkillTryonPayload(readList(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static java.util.List<String> readList(RegistryFriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        java.util.List<String> list = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf(64));
        }
        return list;
    }
}
