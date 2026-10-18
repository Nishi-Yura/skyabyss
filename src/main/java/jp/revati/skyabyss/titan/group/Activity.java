package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 人が「動いている」か (放置していないか) の記録 (docs/design/10 の 9 章・6.2)。
// 動いた (位置が 1 ブロック以上、または向きが 20 度以上変わった)・戦った (傷を与えた/受けた)・触れた (ブロック・アイテム) と、最後に動いた時刻を更新する。
// 使い道:
//  - 背中の試練: 動いていない人 (放置・観戦) が一人いるだけで、誰もいない扱いにならずリセットや呼び直しが止まるのを防ぐ
//  - パーティの分け前 (Parties.absentMates): 拠点で立っているだけの仲間に、道中・試練の経験値が入らないようにする
// 記録はメモリだけ (サーバーを止めると、全員「いま動いた」から始まる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Activity {
    // 背中にいるとみなす (試練の進行を数える) 放置の長さ (5 分)
    public static final int PRESENT_TICKS = 20 * 60 * 5;
    // パーティの分け前を受け取るための、直近の動き (60 秒)
    public static final int SHARE_TICKS = 20 * 60;

    private static final class State {
        double x;
        double y;
        double z;
        float yRot;
        float xRot;
        long lastActive;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Activity() {
    }

    // 動いた印を付ける
    public static void touch(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            state(player).lastActive = player.level().getGameTime();
        }
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> {
            State s = new State();
            s.x = player.getX();
            s.y = player.getY();
            s.z = player.getZ();
            s.yRot = player.getYRot();
            s.xRot = player.getXRot();
            s.lastActive = player.level().getGameTime();
            return s;
        });
    }

    // 直近 ticks のあいだに動いたか (記録がなければ、いま来たばかりなので true)
    public static boolean active(ServerPlayer player, int ticks) {
        State s = STATES.get(player.getUUID());
        return s == null || player.level().getGameTime() - s.lastActive <= ticks;
    }

    // その背中に、乗っているとみなせる人 (観戦者・クリエイティブでなく、長く動かないままでもない人) がいるか。
    // 巨神の呼び直し (TitanKeeper) と試練のリセットが「だれかいる」と見るときに使う
    public static boolean anyoneAboard(net.minecraft.server.level.ServerLevel back) {
        return anyoneAboard(back.players());
    }

    public static boolean anyoneAboard(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (!player.isSpectator() && !player.isCreative() && active(player, PRESENT_TICKS)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        State s = state(player);
        double dx = player.getX() - s.x;
        double dy = player.getY() - s.y;
        double dz = player.getZ() - s.z;
        float turn = Math.abs(player.getYRot() - s.yRot) + Math.abs(player.getXRot() - s.xRot);
        if (dx * dx + dy * dy + dz * dz >= 1.0 || turn >= 20f) {
            s.lastActive = player.level().getGameTime();
            s.x = player.getX();
            s.y = player.getY();
            s.z = player.getZ();
            s.yRot = player.getYRot();
            s.xRot = player.getXRot();
        }
    }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        touch(event.getEntity());
        touch(event.getSource().getEntity());
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        touch(event.getEntity());
    }

    @SubscribeEvent
    static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        touch(event.getEntity());
    }

    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        touch(event.getEntity());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    // 自己テスト用: 最後に動いた時刻を決める
    public static void setLastActive(Player player, long gameTime) {
        if (player instanceof ServerPlayer sp) {
            state(sp).lastActive = gameTime;
        }
    }
}
