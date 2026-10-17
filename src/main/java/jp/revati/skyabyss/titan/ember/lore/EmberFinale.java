package jp.revati.skyabyss.titan.ember.lore;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.ember.lore.client.EmberFinaleClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 1 章の締め (13 の 9.4節) のうち、余燼の担当 G が持つ「地上へ出た最初の夜明け」。
// 余燼の心臓を自分で止めた人が、地上 (オーバーワールド) で次の夜明けを迎えたとき、その人の画面だけに一度:
//  - 遠い地鳴り (A の音 ember_world_turn。まだ登録されていなければバニラの低い音で代える) と、足もとのごく小さな揺れ (画面だけ)
//  - チャットに語りの色で「足の下の、ずっと深いところで、何かが寝返りをうった」(EmberLore.FINALE_DAWN)
// 2 章 (深淵) への導き。演出だけで、世界には何も起きない。
//
// 天の鍵 (四つの欠片が一つになる場面、「一章　天の鍵 / ベータ版はここまでです」の札) は story の SkyKey が持つ (14 の 4章)。
// 13 の 9.4節の「第一章 了」の表示は、その終わりの札で出す (二重に作らない)。世界で最初に止めた人の一行は Progression の
// world_first_last (「四つの鼓動が、すべて静まった」)。ここではそれ以外の、夜明けの一度だけを足す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberFinale {
    private static final String PROTOCOL_VERSION = "ember-lore-1";
    static final String DONE = SkyAbyss.MODID + ":ember_dawn_done";
    // 夜明けの窓 (日の時刻)。日の出の少し前から、日が昇りきるまで
    public static final int DAWN_FROM = 22800;
    public static final int DAWN_TO = 1200;
    // 地上へ出てから夜明けを数えはじめるまで (背中から戻ってすぐ・天の鍵の場面のすぐあとに重ねない)
    public static final int SETTLE_TICKS = 20 * 10;

    // 地上にいつから居るか (人ごと、保存しない)
    private static final Map<UUID, Long> ON_SURFACE_SINCE = new HashMap<>();

    private EmberFinale() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(WorldTurn.TYPE, WorldTurn.STREAM_CODEC, (payload, context) -> EmberFinaleClient.play());
    }

    public static boolean isDawn(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= DAWN_FROM || t < DAWN_TO;
    }

    public static boolean done(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(DONE);
    }

    static void setDone(Player player, boolean done) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (done) {
            persisted.putBoolean(DONE, true);
        } else {
            persisted.remove(DONE);
        }
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    // この人に夜明けの一度を出してよいか (自己テストからも呼ぶ)
    public static boolean due(ServerPlayer player, long gameTime, long dayTime, long surfaceSince) {
        return !done(player) && player.level().dimension() == Level.OVERWORLD
                && Progression.stopped(player.server, player.getUUID(), Titans.EMBER)
                && gameTime - surfaceSince >= SETTLE_TICKS && isDawn(dayTime);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.tickCount % 20 != 13
                || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (player.level().dimension() != Level.OVERWORLD) {
            ON_SURFACE_SINCE.remove(player.getUUID());
            return;
        }
        long now = player.level().getGameTime();
        long since = ON_SURFACE_SINCE.computeIfAbsent(player.getUUID(), id -> now);
        if (due(player, now, player.level().getDayTime(), since)) {
            fire(player);
        }
    }

    // 夜明けの一度: その人だけに音と揺れ、語りの一行。二度は出さない
    public static void fire(ServerPlayer player) {
        setDone(player, true);
        PacketDistributor.sendToPlayer(player, new WorldTurn());
        Notice.narrate(player, EmberLore.FINALE_DAWN);
        SkyAbyss.LOGGER.info("{} felt the world turn over at dawn (ember finale)", player.getGameProfile().getName());
    }

    // テストとやり直し用 (コマンドは持たない。/skyabyss story reset と同じく記録だけ)
    public static void reset(Player player) {
        setDone(player, false);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ON_SURFACE_SINCE.remove(event.getEntity().getUUID());
    }

    // サーバー → その人: 夜明けの地鳴りと揺れ (画面だけ)
    public record WorldTurn() implements CustomPacketPayload {
        public static final Type<WorldTurn> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_world_turn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorldTurn> STREAM_CODEC = StreamCodec.unit(new WorldTurn());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
