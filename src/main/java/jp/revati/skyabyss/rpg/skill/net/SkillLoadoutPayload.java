package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 型の保存 (save = true なら今の枠と刻印を保存、false ならその型に切り替える)
public record SkillLoadoutPayload(int index, boolean save) implements CustomPacketPayload {
    public static final Type<SkillLoadoutPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_loadout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillLoadoutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.index());
                buf.writeBoolean(p.save());
            },
            buf -> new SkillLoadoutPayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
