package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 変異を選ぶ (MUTATE=skill)、紋を盤の穴へ差す (PLACE=a:socket・b:bag・skill)、紋を外す (REMOVE=a:socket)。サーバーで検める
public record SkillExtPayload(int action, String skill, int a, int b) implements CustomPacketPayload {
    public static final int MUTATE = 0;
    public static final int PLACE = 1;
    public static final int REMOVE = 2;

    public static final Type<SkillExtPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_ext"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillExtPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action());
                buf.writeUtf(p.skill(), 64);
                buf.writeVarInt(p.a());
                buf.writeVarInt(p.b());
            },
            buf -> new SkillExtPayload(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
