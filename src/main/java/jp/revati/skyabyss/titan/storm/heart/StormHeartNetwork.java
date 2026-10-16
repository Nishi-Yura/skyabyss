package jp.revati.skyabyss.titan.storm.heart;

import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.heart.client.StormHeartClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 雷翼の心臓のパケット。ほかの担当とぶつからないように、別の版で登録する。
//  Warn  : 技の予告の印 (画面の中央の上に、技の印と名前と残りの帯)。パーティクルの設定に左右されない予告 (12 の 5.3節)
//  Flash : 閃光の予告 (画面が暗くなる) と、当たった人の白い瞬き
//  Shot  : テスト用 (OP のコマンドから)。その人の画面を run/screenshots/<name>.png に写す。キーを送らずに確かめるため
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormHeartNetwork {
    private static final String PROTOCOL_VERSION = "storm-heart-1";

    private StormHeartNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Warn.TYPE, Warn.STREAM_CODEC, (payload, context) -> StormHeartClient.warn(payload.move(), payload.ticks()))
                .playToClient(Flash.TYPE, Flash.STREAM_CODEC, (payload, context) -> StormHeartClient.flash(payload.ticks(), payload.hit()))
                .playToClient(Shot.TYPE, Shot.STREAM_CODEC, (payload, context) -> StormHeartClient.shot(payload.name()));
    }

    // 予告の印 (move が null なら消す)
    public static void warn(Collection<ServerPlayer> players, @Nullable StormHeart.Action move, int ticks) {
        Warn w = new Warn(move == null ? "" : move.key(), ticks);
        for (ServerPlayer p : players) {
            PacketDistributor.sendToPlayer(p, w);
        }
    }

    // 閃光の予告 (ticks のあいだ暗くなる)
    public static void flash(Collection<ServerPlayer> players, int ticks, boolean hit) {
        for (ServerPlayer p : players) {
            PacketDistributor.sendToPlayer(p, new Flash(ticks, hit));
        }
    }

    // 閃光の結果: 当たった人には白い瞬き、ほかの人には弱い瞬き
    public static void flashResult(Collection<ServerPlayer> players, List<ServerPlayer> hit) {
        for (ServerPlayer p : players) {
            PacketDistributor.sendToPlayer(p, new Flash(0, hit.contains(p)));
        }
    }

    public static void shot(ServerPlayer player, String name) {
        PacketDistributor.sendToPlayer(player, new Shot(name));
    }

    public record Warn(String move, int ticks) implements CustomPacketPayload {
        public static final Type<Warn> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_heart_warn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Warn> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Warn::move, ByteBufCodecs.VAR_INT, Warn::ticks, Warn::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Flash(int ticks, boolean hit) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_heart_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flash> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Flash::ticks, ByteBufCodecs.BOOL, Flash::hit, Flash::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Shot(String name) implements CustomPacketPayload {
        public static final Type<Shot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_heart_shot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Shot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Shot::name, Shot::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
