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

    private static final StreamCodec<net.minecraft.network.FriendlyByteBuf, net.minecraft.world.phys.Vec3> VEC3 = StreamCodec.of(
            (buf, v) -> buf.writeDouble(v.x).writeDouble(v.y).writeDouble(v.z),
            buf -> new net.minecraft.world.phys.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));

    // route = 目的 (次の灯の台・苗床・出口) までの迷路の道のり (マスの真ん中の列。水の入口前は水への入口)。クライアントは床に一本の金の線として引く
    // (client/guide/GuideTrailRenderer)。空なら線なし。目的が変われば引き直して送る
    public record Payload(Component text, java.util.List<net.minecraft.world.phys.Vec3> route) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "lamp_band"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(ComponentSerialization.STREAM_CODEC, Payload::text,
                VEC3.apply(net.minecraft.network.codec.ByteBufCodecs.list(400)), Payload::route, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 試験用: 最後に送った文
    private static Component lastSent = Component.empty();

    public static void send(ServerPlayer player, Component text) {
        send(player, text, null);
    }

    public static void send(ServerPlayer player, Component text, java.util.List<net.minecraft.world.phys.Vec3> route) {
        lastSent = text;
        if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            PacketDistributor.sendToPlayer(player, new Payload(text, route == null ? java.util.List.of() : route));
        }
    }

    public static Component lastSent() {
        return lastSent;
    }
}
