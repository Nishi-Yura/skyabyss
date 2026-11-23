package jp.revati.skyabyss.fx;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

// サーバー -> クライアント: 月の主が技を出し始めた (2026-11-15 の光の表現 8「軌道の線」)。
// 月の主は床の予告 (Telegraph) を使わず動作だけで技を見せるので、動作の始まりをここで知らせる。
// クライアント (client/combat/AttackLightFx) は ticks のあいだ足もとの輪を回し、終わりに外へ散らす。塔の主は予告から同じものを描く。
public record OrbitCuePayload(int entityId, int ticks) implements CustomPacketPayload {
    // 技の出だしから放つまでの目安
    public static final int TICKS = 16;

    public static final Type<OrbitCuePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "orbit_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitCuePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVarInt(p.ticks);
            },
            buf -> new OrbitCuePayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(Entity e, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntity(e, new OrbitCuePayload(e.getId(), ticks));
    }
}
