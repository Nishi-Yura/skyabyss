package jp.revati.skyabyss.titan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.util.Players;

// 巨神の見え方 (docs/design/32 の 3.3)。空・海・嵐・地を泳ぐ成体の巨神は、その章を遊んでいる人 (まだその心臓を止めていない人) と、
// その人のパーティ (同じグループのオンラインの人) にしか見えない。章を終えた人には、本体・鎖・昇り風・口の実体を送らず
// (各実体の broadcastToPlayer)、歌・地鳴りの音も届けない (playSound)。体の当たり (TitanSolids) と乗り込み口も働かない。
// 物語の決まり (ゲームルール skyabyssStoryGate) を切っているときと、観戦者・クリエイティブには、いつも見える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanVisibility {
    private TitanVisibility() {
    }

    // 判定の使い回し (人 + 巨神 → {判定, 時刻})。20 tick ごとに見直す
    private static final int CACHE_TICKS = 20;
    private static final Map<String, long[]> CACHE = new HashMap<>();
    // 自己テスト用: この人たちは心臓を止めたことにする (null なら本物の記録)
    @Nullable
    public static java.util.function.BiPredicate<UUID, String> STOPPED_OVERRIDE;

    private static boolean stopped(MinecraftServer server, UUID player, String titan) {
        return STOPPED_OVERRIDE != null ? STOPPED_OVERRIDE.test(player, titan) : Progression.stopped(server, player, titan);
    }

    // その人に、その巨神が見えるか
    public static boolean sees(Player player, String titan) {
        if (!(player instanceof ServerPlayer sp)) {
            return true;
        }
        if (sp.isSpectator() || sp.isCreative() || !Progression.gateOn(sp.server)) {
            return true;
        }
        long now = sp.server.getTickCount();
        String key = sp.getUUID() + "|" + titan;
        long[] c = CACHE.get(key);
        if (c != null && now - c[1] >= 0 && now - c[1] < CACHE_TICKS) {
            return c[0] != 0;
        }
        boolean v = compute(sp, titan);
        CACHE.put(key, new long[] {v ? 1 : 0, now});
        return v;
    }

    static boolean compute(ServerPlayer sp, String titan) {
        MinecraftServer server = sp.server;
        if (!stopped(server, sp.getUUID(), titan)) {
            return true;
        }
        UUID group = Groups.keyOf(sp);
        for (ServerPlayer o : Players.snapshot(server.getPlayerList().getPlayers())) {
            if (o != sp && Groups.keyOf(o).equals(group) && !stopped(server, o.getUUID(), titan)) {
                return true;
            }
        }
        return false;
    }

    // 巨神の音を、見える人にだけ鳴らす (level.playSound(null, …) の置き換え)。届く距離は vol × 16
    public static void playSound(Level level, String titan, double x, double y, double z, SoundEvent sound, SoundSource source, float vol, float pitch) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        double range = Math.max(16.0, vol * 16.0);
        long seed = server.random.nextLong();
        for (ServerPlayer p : Players.snapshot(server.players())) {
            if (p.distanceToSqr(x, y, z) > range * range || !sees(p, titan)) {
                continue;
            }
            p.connection.send(new ClientboundSoundPacket(Holder.direct(sound), source, x, y, z, vol, pitch, seed));
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CACHE.clear();
        STOPPED_OVERRIDE = null;
    }
}
