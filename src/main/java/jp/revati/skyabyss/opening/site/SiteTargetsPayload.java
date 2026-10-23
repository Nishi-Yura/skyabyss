package jp.revati.skyabyss.opening.site;

import java.util.LinkedHashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → クライアント: 序盤の構造物の場所 (目的の帯の方角と距離のため)。キーは "camp" "shelter" "footprint" "ribs" "notebook" "bell"
public record SiteTargetsPayload(Map<String, BlockPos> sites) implements CustomPacketPayload {
    public static final Type<SiteTargetsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "opening_sites"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SiteTargetsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.sites().size());
                p.sites().forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeBlockPos(v);
                });
            },
            buf -> {
                int n = buf.readVarInt();
                Map<String, BlockPos> m = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    m.put(buf.readUtf(), buf.readBlockPos());
                }
                return new SiteTargetsPayload(m);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
