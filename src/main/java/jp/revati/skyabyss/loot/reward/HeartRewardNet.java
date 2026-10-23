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
//   Open    (サーバー → 持ち主): 画面を開く。巨神・選べる品の ID・持っている印 (専用の逸品の並びの 1 ビットずつ)・自動で開いたか (場面のあと)
//   State   (サーバー → 持ち主): 「受け取れる遺物がある」の旗。旅嚢の「遺物」ボタンの出し入れ。入ったとき・選んだとき・足されたときに送る
//   Result  (サーバー → 持ち主): 選んだ結果 (0 = 受け取った、ほかは HeartReward.Result の番号)
//   Claim   (持ち主 → サーバー): この品を選ぶ
//   Request (持ち主 → サーバー): 受け取れる遺物の画面を開いて (旅嚢・控え帳・コマンドから)
//   Later   (持ち主 → サーバー): 「あとで」にした (サーバーは何も変えない。案内の一行だけ)
//   Hold    (持ち主 → サーバー): 画面が開いている間、10 tick ごと。サーバーは 2 秒ぶん「守り」を延ばす:
//           その間は本人は傷つかず (出血・火・落下・敵のどれも。/kill と虚空は除く)、敵は本人を狙わない。画面を閉じて 2 秒で切れる (旗を持たないので、落ちても残らない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class HeartRewardNet {
    private static final String PROTOCOL_VERSION = "heart_reward-2";

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

    public record Hold() implements CustomPacketPayload {
        public static final Type<Hold> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_reward_hold"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hold> STREAM_CODEC = StreamCodec.unit(new Hold());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- 画面が開いている間の守り (人 → 守りが切れるゲーム時刻)
    private static final int HOLD_TICKS = 40;
    private static final java.util.Map<java.util.UUID, Long> HELD = new java.util.HashMap<>();

    public static void hold(ServerPlayer player, int ticks) {
        HELD.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
    }

    public static boolean isHeld(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof ServerPlayer p)) {
            return false;
        }
        Long until = HELD.get(p.getUUID());
        if (until == null) {
            return false;
        }
        if (p.serverLevel().getGameTime() > until) {
            HELD.remove(p.getUUID());
            return false;
        }
        return true;
    }

    // Open を送るとき (画面が開くまでの間も守る)
    static void holdOnOpen(ServerPlayer player) {
        hold(player, HOLD_TICKS * 4);
    }

    @SubscribeEvent
    static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (isHeld(event.getEntity()) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (isHeld(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    // すでに狙っている敵の狙いも外す (1 秒に一度)
    @SubscribeEvent
    static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0 && isHeld(player)) {
            for (net.minecraft.world.entity.Mob mob : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    player.getBoundingBox().inflate(32), m -> m.getTarget() == player)) {
                mob.setTarget(null);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        HELD.clear();
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
                .playToServer(Hold.TYPE, Hold.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        hold(player, HOLD_TICKS);
                    }
                })
                .playToServer(Later.TYPE, Later.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        HeartReward.onLaterPacket(player, p.titan());
                    }
                });
    }
}
