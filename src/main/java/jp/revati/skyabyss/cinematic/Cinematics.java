package jp.revati.skyabyss.cinematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.CinematicNetwork.StartPayload;
import jp.revati.skyabyss.cinematic.CinematicNetwork.StopPayload;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 場面 (シネマティック) のサーバー側 (docs/design/19_cinematics.md)。
// 一人にだけ始める (パケットはその人だけ。ほかの人の画面・動きは変わらない)。場面の間、本人は無敵で、その場に留まる (カメラはクライアントが動かす)。
// 終わりはクライアントの End (最後まで / スキップ) か、サーバーの時間切れ・次元の移動・退出。どの終わり方でも、物語の効果 (applyEffects / 登録された聞き手) は一度だけ適用する。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Cinematics {
    // クライアントが応えないときの打ち切り (場面の長さに足す tick)。ほかの画面に隠れて止まっている時間も含めて余裕を見る
    static final int TIMEOUT_EXTRA = 20 * 90;
    // 場面の間に動いてしまったとき、元の場所へ戻す距離
    private static final double HOLD_RADIUS = 0.6;

    private record Active(Scene scene, long startTick, Vec3 origin, float yaw) {
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<String, List<BiConsumer<ServerPlayer, Boolean>>> LISTENERS = new HashMap<>();

    private Cinematics() {
    }

    public static CinematicState state(net.minecraft.world.entity.player.Player player) {
        return player.getData(CinematicSetup.STATE);
    }

    public static boolean active(net.minecraft.world.entity.player.Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    @Nullable
    public static String activeScene(net.minecraft.world.entity.player.Player player) {
        Active a = ACTIVE.get(player.getUUID());
        return a == null ? null : a.scene().id();
    }

    // 場面が終わったとき (最後まで・スキップ・打ち切りのどれでも) に呼ぶ処理を足す。第二引数 = スキップ (または打ち切り) か。
    // ボスの担当などが、場面のあとの物語の効果 (戦いの始まりなど) をここに掛ける
    public static void onEnd(String scene, BiConsumer<ServerPlayer, Boolean> listener) {
        LISTENERS.computeIfAbsent(scene, k -> new ArrayList<>()).add(listener);
    }

    // 一度だけ見せる場面。見たことがあれば何もしない (false)。始めた時点で見たことにする (途中で落ちても繰り返さない)
    public static boolean startOnce(ServerPlayer player, String sceneId, @Nullable Entity target, @Nullable Vec3 faceToward) {
        if (state(player).seen(sceneId)) {
            return false;
        }
        return start(player, sceneId, target, faceToward);
    }

    // 場面を始める。すでに別の場面の最中・存在しない場面・観戦者・死んでいる人には始めない (false)。
    // target: 狙う物 (岩鯨・心臓など。見つからなくても faceToward の位置を見る)、faceToward: 場面の「前」の向きにする点 (null なら本人の向き)
    public static boolean start(ServerPlayer player, String sceneId, @Nullable Entity target, @Nullable Vec3 faceToward) {
        Scene scene = Scenes.get(sceneId);
        if (scene == null || ACTIVE.containsKey(player.getUUID()) || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        Vec3 origin = player.position();
        Vec3 aim = target != null ? target.position() : faceToward;
        float yaw = aim != null && aim.subtract(origin).horizontalDistanceSqr() > 1.0 ? CameraMath.yawOf(aim.subtract(origin)) : player.getYRot();
        Vec3 fallback = aim != null ? aim : origin.add(0, 0, 1);
        long now = player.serverLevel().getGameTime();
        ACTIVE.put(player.getUUID(), new Active(scene, now, origin, yaw));
        state(player).markSeen(sceneId);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        PacketDistributor.sendToPlayer(player, new StartPayload(sceneId, origin.x, origin.y, origin.z, yaw, target == null ? -1 : target.getId(),
                fallback.x, fallback.y, fallback.z));
        return true;
    }

    // クライアントの End を受けた (またはサーバー側の打ち切り)。始めた場面と名前が違うものは無視する
    public static void end(ServerPlayer player, String sceneId, boolean skipped) {
        Active a = ACTIVE.get(player.getUUID());
        if (a == null || !a.scene().id().equals(sceneId)) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        applyEffects(player, a.scene(), skipped);
    }

    // サーバーから打ち切る (次元の移動・時間切れ・コマンド)。クライアントへ Stop を送り、物語の効果は適用する
    public static void stop(ServerPlayer player, boolean notifyClient) {
        Active a = ACTIVE.remove(player.getUUID());
        if (a == null) {
            return;
        }
        if (notifyClient && !(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, new StopPayload(a.scene().id()));
        }
        applyEffects(player, a.scene(), true);
    }

    // 場面の物語の効果。スキップしても必ず適用する (見せ方だけが省かれる)
    static void applyEffects(ServerPlayer player, Scene scene, boolean skipped) {
        switch (scene.id()) {
            // 開幕: 最初の目的を、操作が戻った瞬間に大きく一行で伝える (帯と同じ「煙の立つ方」)
            case Scenes.OPENING -> Notice.alert(player, "cinematic.skyabyss.opening.goal");
            default -> {
            }
        }
        for (BiConsumer<ServerPlayer, Boolean> listener : LISTENERS.getOrDefault(scene.id(), List.of())) {
            listener.accept(player, skipped);
        }
    }

    // ---------------------------------------------------------------- 場面の間の見張り
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Active a = ACTIVE.get(player.getUUID());
        if (a == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now - a.startTick() > a.scene().ticks() + TIMEOUT_EXTRA || !player.isAlive()) {
            stop(player, true);
            return;
        }
        // その場に留める: 地面にいる間の押し出し・歩きを止め、離れたら戻す (落ちている間は触らない)
        if (player.onGround()) {
            player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
            if (player.position().subtract(a.origin()).horizontalDistanceSqr() > HOLD_RADIUS * HOLD_RADIUS) {
                player.teleportTo(a.origin().x, player.getY(), a.origin().z);
            }
        }
        player.setSprinting(false);
        player.stopUsingItem();
        player.setRemainingFireTicks(0);
    }

    // 場面の間は本人は傷つかない (終わったら元どおり。フラグを持たないので、落ちても無敵のまま残らない)
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ACTIVE.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, true);
        }
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
