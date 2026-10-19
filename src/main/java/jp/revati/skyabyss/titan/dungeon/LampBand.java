package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 光海の「灯 n / 3 …」の帯 (docs/design/23 の 11.1)。サーバーが 1 秒ごとに送り、クライアント (LampBandClient) が左上の帯 (目的の帯の下) に出す。
// 画面の中央の手がかりの積みには出さない (アクションバーは手がかりと重なる)。水路を出ると送らなくなり、約 2 秒で消える
public final class LampBand {
    private LampBand() {
    }

    public record Payload(Component text) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "lamp_band"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(ComponentSerialization.STREAM_CODEC, Payload::text, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 試験用: 最後に送った文
    private static Component lastSent = Component.empty();

    public static void send(ServerPlayer player, Component text) {
        lastSent = text;
        if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            PacketDistributor.sendToPlayer(player, new Payload(text));
        }
    }

    public static Component lastSent() {
        return lastSent;
    }
}
