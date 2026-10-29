package jp.revati.skyabyss.music;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 地上でいまいる所 (ContinentAreas の読み) を画面へ知らせる。画面はそれで BGM を選ぶ (client/music/MusicDirector)
public final class MusicArea {
    public record Payload(String area) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "music_area"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Payload::area, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MusicArea() {
    }

    // 曲を流すボスの帯にする (画面は帯の名前で心臓かどうかを見分ける。MusicDirector)。名のある野良の敵・護衛の帯には付けない
    public static net.minecraft.server.level.ServerBossEvent bossBar(net.minecraft.server.level.ServerBossEvent bar) {
        bar.setPlayBossMusic(true);
        return bar;
    }

    public static void send(ServerPlayer p, String area) {
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        PacketDistributor.sendToPlayer(p, new Payload(area));
    }
}
