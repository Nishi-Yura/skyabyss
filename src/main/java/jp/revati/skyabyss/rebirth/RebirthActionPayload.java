package jp.revati.skyabyss.rebirth;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 転生画面の操作。action 0 = 転生する (arm と boon を添える)、1 = 導きの欠片で節を足す (arm だけ)
public record RebirthActionPayload(int action, String arm, String boon) implements CustomPacketPayload {
    public static final int REBIRTH = 0;
    public static final int SHARD_NODE = 1;

    public static final Type<RebirthActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "rebirth_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RebirthActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action());
                buf.writeUtf(p.arm(), 32);
                buf.writeUtf(p.boon(), 32);
            },
            buf -> new RebirthActionPayload(buf.readVarInt(), buf.readUtf(32), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
