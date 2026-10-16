package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.heart.client.EmberHeartClient;
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

// 余燼の心臓のパケット。ほかの担当とぶつからないように、別の版で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberHeartNetwork {
    private static final String PROTOCOL_VERSION = "ember-heart-1";

    private EmberHeartNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Nudge.TYPE, Nudge.STREAM_CODEC, (payload, context) -> EmberHeartClient.nudge(payload.x(), payload.y(), payload.z()))
                .playToClient(Shot.TYPE, Shot.STREAM_CODEC, (payload, context) -> EmberHeartClient.shot(payload.name()))
                .playToClient(Veil.TYPE, Veil.STREAM_CODEC, (payload, context) -> EmberHeartClient.veil(payload.ticks()))
                .playToClient(Chill.TYPE, Chill.STREAM_CODEC, (payload, context) -> EmberHeartClient.chill(payload.on()));
    }

    // 人の速さに足す (鞴の嵐の引き寄せ、跳ね上げ)。サーバーは人の本当の速さを知らないので、クライアントで足す
    public static void nudge(ServerPlayer player, double x, double y, double z) {
        PacketDistributor.sendToPlayer(player, new Nudge((float) x, (float) y, (float) z));
    }

    // テスト用 (OP のコマンドから): その人の画面を run/screenshots/<name>.png に写す
    public static void shot(ServerPlayer player, String name) {
        PacketDistributor.sendToPlayer(player, new Shot(name));
    }

    // 灰の帳: 画面を灰でおおう (tick の間)
    public static void veil(ServerPlayer player, int ticks) {
        PacketDistributor.sendToPlayer(player, new Veil(ticks));
    }

    // 凍え: 画面の縁を霜の青灰で縁取る
    public static void chill(ServerPlayer player, boolean on) {
        PacketDistributor.sendToPlayer(player, new Chill(on));
    }

    public record Shot(String name) implements CustomPacketPayload {
        public static final Type<Shot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_heart_shot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Shot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Shot::name, Shot::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Nudge(float x, float y, float z) implements CustomPacketPayload {
        public static final Type<Nudge> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_heart_nudge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Nudge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Nudge::x, ByteBufCodecs.FLOAT, Nudge::y, ByteBufCodecs.FLOAT, Nudge::z, Nudge::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Veil(int ticks) implements CustomPacketPayload {
        public static final Type<Veil> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_heart_veil"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Veil> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Veil::ticks, Veil::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Chill(boolean on) implements CustomPacketPayload {
        public static final Type<Chill> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_heart_chill"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chill> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Chill::on, Chill::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
