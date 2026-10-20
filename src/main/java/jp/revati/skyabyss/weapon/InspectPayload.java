package jp.revati.skyabyss.weapon;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

// 武器の点検 (かざして回す動き) の合図。クライアント -> サーバー (player は 0 でよい) で送り、サーバーが持ち主の id を入れて、
// 持ち主と見ている人へ返す (サーバー -> クライアント)。持っていない・動きのない品なら、サーバーは返さない
public record InspectPayload(int player) implements CustomPacketPayload {
    public static final Type<InspectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "weapon_inspect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InspectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.player()),
            buf -> new InspectPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void relay(Player player) {
        if (player instanceof ServerPlayer sp) {
            MoveSet ms = WeaponStrikes.setOfAny(sp.getMainHandItem());
            if (ms != null && ms.inspect != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new InspectPayload(sp.getId()));
            }
        }
    }
}
