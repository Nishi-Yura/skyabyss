package jp.revati.skyabyss.rpg.net;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: ステータス画面の「決定」。4 つのステータスへの追加量 (サーバーで検める)
public record GrowthAllocatePayload(int[] add) implements CustomPacketPayload {
    public static final Type<GrowthAllocatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "growth_allocate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrowthAllocatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                for (int i = 0; i < GrowthRules.Stat.values().length; i++) {
                    buf.writeVarInt(i < p.add().length ? p.add()[i] : 0);
                }
            },
            buf -> {
                int[] add = new int[GrowthRules.Stat.values().length];
                for (int i = 0; i < add.length; i++) {
                    add[i] = buf.readVarInt();
                }
                return new GrowthAllocatePayload(add);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
