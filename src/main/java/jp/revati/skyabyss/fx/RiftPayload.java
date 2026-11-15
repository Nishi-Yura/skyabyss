package jp.revati.skyabyss.fx;

import java.util.Collection;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// サーバー -> クライアント: 空間の裂け目で別の場所へ渡る (2026-11-15 の光の表現の選定 1・2)。
// 目の前に裂け目が開き、向こうにその場所の内側の空がのぞき、広がって画面を覆う。覆いきった所で運ぶ (LEAD tick あと)。
// 描くのは client/fx/RiftFx。theme は巨神の名 (Titans) か "abyss" / "moon"。
public record RiftPayload(String theme) implements CustomPacketPayload {
    // 裂け目が開いてから画面を覆いきるまで。運ぶ側はこれだけ待ってから動かす
    public static final int LEAD = 26;

    public static final String ABYSS = "abyss";
    public static final String MOON = "moon";

    public static final Type<RiftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "rift_pass"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.theme),
            buf -> new RiftPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 本物の人 (通信のある人) がいるか。試験の偽の人だけなら、裂け目を待たずにすぐ運ぶ
    public static boolean anyReal(Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (!(p instanceof FakePlayer)) {
                return true;
            }
        }
        return false;
    }

    public static void send(ServerPlayer p, String theme) {
        if (!(p instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(p, new RiftPayload(theme));
        }
    }

    public static void send(Collection<ServerPlayer> players, String theme) {
        for (ServerPlayer p : players) {
            send(p, theme);
        }
    }

    // 巨神の名 (Titans の id、または背の進みの名) をそのまま渡す。知らない名は岩鯨の色
    public static String themeOf(String titan) {
        if (titan == null) {
            return Titans.STONE_WHALE;
        }
        return switch (titan) {
            case Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER, ABYSS, MOON -> titan;
            default -> Titans.STONE_WHALE;
        };
    }
}
