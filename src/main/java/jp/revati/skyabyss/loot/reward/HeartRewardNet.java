package jp.revati.skyabyss.loot.reward;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 心臓の遺物の選択画面のパケット (docs/design/30_heart_reward_ui.md)。決めるのは全部サーバー。クライアントは見せて、押したものを伝えるだけ。
//   Open    (サーバー → 持ち主): 画面を開く。巨神・選べる品の ID・持っている印 (専用伝説の並びの 1 ビットずつ)・自動で開いたか (場面のあと)
//   State   (サーバー → 持ち主): 「受け取れる遺物がある」の旗。旅嚢の「遺物」ボタンの出し入れ。入ったとき・選んだとき・足されたときに送る
//   Result  (サーバー → 持ち主): 選んだ結果 (0 = 受け取った、ほかは HeartReward.Result の番号)
//   Claim   (持ち主 → サーバー): この品を選ぶ
//   Request (持ち主 → サーバー): 受け取れる遺物の画面を開いて (旅嚢・控え帳・コマンドから)
//   Later   (持ち主 → サーバー): 「あとで」にした (サーバーは何も変えない。案内の一行だけ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class HeartRewardNet {
    private static final String PROTOCOL_VERSION = "heart_reward-1";

    private HeartRewardNet() {
    }

    public record Open(String titan, List<String> options, int ownedMask, boolean auto) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_reward_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Open::titan,
                ByteBufCodecs.stringUtf8(96).apply(ByteBufCodecs.list(8)), Open::options,
                ByteBufCodecs.VAR_INT, Open::ownedMask,
                ByteBufCodecs.BOOL, Open::auto,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(boolean pending) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_reward_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, State::pending, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Result(int code, String titan, String item) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_reward_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Result::code,
                ByteBufCodecs.stringUtf8(32), Result::titan,
                ByteBufCodecs.stringUtf8(96), Result::item,
                Result::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Claim(String titan, String item) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_reward_claim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Claim> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Claim::titan,
                ByteBufCodecs.stringUtf8(96), Claim::item,
                Claim::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_reward_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Later(String titan) implements CustomPacketPayload {
        public static final Type<Later> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_reward_later"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Later> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(32), Later::titan, Later::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Open.TYPE, Open.STREAM_CODEC, (p, ctx) -> jp.revati.skyabyss.client.reward.HeartRewardClient.onOpen(p))
                .playToClient(State.TYPE, State.STREAM_CODEC, (p, ctx) -> jp.revati.skyabyss.client.reward.HeartRewardClient.onState(p))
                .playToClient(Result.TYPE, Result.STREAM_CODEC, (p, ctx) -> jp.revati.skyabyss.client.reward.HeartRewardClient.onResult(p))
                .playToServer(Claim.TYPE, Claim.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        HeartReward.onClaimPacket(player, p.titan(), p.item());
                    }
                })
                .playToServer(Request.TYPE, Request.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        HeartReward.onRequestPacket(player);
                    }
                })
                .playToServer(Later.TYPE, Later.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        HeartReward.onLaterPacket(player, p.titan());
                    }
                });
    }
}
