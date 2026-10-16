package jp.revati.skyabyss.titan.ember;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.client.EmberTitanClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 余燼の地上 (担当 A) のパケット。骨組みの EmberNetwork (ember-1) とは別の版 ember-surface-1 で登録する (17.8節の約束)。
// クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberSurfaceNetwork {
    private static final String PROTOCOL_VERSION = "ember-surface-1";

    private EmberSurfaceNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Plunge.TYPE, Plunge.STREAM_CODEC, (p, c) -> EmberTitanClient.plunge(p.ticks()))
                .playToClient(Tremor.TYPE, Tremor.STREAM_CODEC, (p, c) -> EmberTitanClient.tremor(p.ticks(), p.strength()))
                .playToClient(AshSky.TYPE, AshSky.STREAM_CODEC, (p, c) -> EmberTitanClient.ashSky(p.tier()))
                .playToClient(StoneInfo.TYPE, StoneInfo.STREAM_CODEC, (p, c) -> EmberTitanClient.stoneInfo(p));
    }

    // 一人へ送る。偽のプレイヤー (自己テスト・ほかの Mod) には送らない (接続が無く、送ると落ちる)
    public static void send(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload payload) {
        if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer) && player.connection != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    // サーバー → 口に呑まれた人・背中から出た人: 画面を橙に沈める (潮殻の Plunge と同じ形。色だけ違う)
    public record Plunge(int ticks) implements CustomPacketPayload {
        public static final Type<Plunge> TYPE = new Type<>(id("ember_plunge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Plunge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Plunge::ticks, Plunge::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 地鳴りの近くの人: 画面の微振動 (クライアント設定 screenShake で切れる)
    public record Tremor(int ticks, float strength) implements CustomPacketPayload {
        public static final Type<Tremor> TYPE = new Type<>(id("ember_tremor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tremor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tremor::ticks, ByteBufCodecs.FLOAT, Tremor::strength, Tremor::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 眠る巨神から 256 ブロック以内の人 (2 秒ごと): 灰の降る段 (1〜3)。空と霧をうすく赤くする。届かなくなれば薄れる
    public record AshSky(int tier) implements CustomPacketPayload {
        public static final Type<AshSky> TYPE = new Type<>(id("ember_ash_sky"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AshSky> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AshSky::tier, AshSky::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 温石を手に持つ人 (1 秒ごと): 巨神の様子。present が false なら巨神がいない (石は冷たい)。
    // side: 巨神が右なら +1、左なら -1、前後なら 0。distance: 0 遠い・1 近い・2 すぐそこ。sleeping: 眠っている (浮かぶ・沈むを含む)。
    // ticksToSleep: 次の眠りの始まりまで。requester: 今夜、温石で頼んだ人 (いなければ空)
    public record StoneInfo(boolean present, int side, int distance, boolean sleeping, int ticksToSleep, String requester)
            implements CustomPacketPayload {
        public static final Type<StoneInfo> TYPE = new Type<>(id("ember_stone_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StoneInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, StoneInfo::present, ByteBufCodecs.VAR_INT, StoneInfo::side, ByteBufCodecs.VAR_INT, StoneInfo::distance,
                ByteBufCodecs.BOOL, StoneInfo::sleeping, ByteBufCodecs.VAR_INT, StoneInfo::ticksToSleep, ByteBufCodecs.STRING_UTF8,
                StoneInfo::requester, StoneInfo::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
