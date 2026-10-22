package jp.revati.skyabyss.abyss.arts.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// サーバー → 持ち主: 深淵術の待ちの終わり (ゲームの tick)。画面の秒の表示に使う
public record ArtCooldownPayload(int art, long until) implements CustomPacketPayload {
    public static final Type<ArtCooldownPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_art_cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArtCooldownPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.art());
                buf.writeVarLong(p.until());
            },
            buf -> new ArtCooldownPayload(buf.readVarInt(), buf.readVarLong()));

    public static void send(ServerPlayer player, int art, long until) {
        if (!player.hasDisconnected()) {
            PacketDistributor.sendToPlayer(player, new ArtCooldownPayload(art, until));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
