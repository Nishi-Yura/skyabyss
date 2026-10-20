package jp.revati.skyabyss.weapon;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー -> クライアント: 武器の一撃が「当たった」合図。持ち主と、その人を見ている人へ送る。
// クライアントは振りを hitstop tick 止め、持ち主の画面は shake (0..100) の強さで揺らす (設定で弱められる)
public record StrikePayload(int player, int step, int hitstop, int shake) implements CustomPacketPayload {
    public static final Type<StrikePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "weapon_strike"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrikePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.player());
                buf.writeByte(p.step());
                buf.writeByte(p.hitstop());
                buf.writeByte(p.shake());
            },
            buf -> new StrikePayload(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
