package jp.revati.skyabyss.rpg.skill.net;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 覚えた秘伝 (id と段) と枠 (SecretArts。技の画面と帯が今の変化を見せるため)
public record SecretArtsPayload(List<String> ids, List<Integer> tiers, List<String> slots) implements CustomPacketPayload {
    public static final Type<SecretArtsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "secret_arts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SecretArtsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.ids().size());
                for (int i = 0; i < p.ids().size(); i++) {
                    buf.writeUtf(p.ids().get(i));
                    buf.writeVarInt(p.tiers().get(i));
                }
                buf.writeVarInt(p.slots().size());
                p.slots().forEach(buf::writeUtf);
            },
            buf -> {
                int n = buf.readVarInt();
                List<String> ids = new ArrayList<>();
                List<Integer> tiers = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    ids.add(buf.readUtf());
                    tiers.add(buf.readVarInt());
                }
                int m = buf.readVarInt();
                List<String> slots = new ArrayList<>();
                for (int i = 0; i < m; i++) {
                    slots.add(buf.readUtf());
                }
                return new SecretArtsPayload(ids, tiers, slots);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
