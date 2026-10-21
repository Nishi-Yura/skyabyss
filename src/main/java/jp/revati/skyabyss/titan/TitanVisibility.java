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
// 物語の決まり (ゲームルール skyabyssStoryGate) を切っているときと、観戦者・クリエイティブには、いつも見える。
// 例外: 心臓を止めて運び出された人 (と運ばれた仲間) は、その眠り (TitanDormancy) が終わるまで眠る姿と去る姿を見る (眠りの場面のため)。
// 送るかどうかは、巨神が動いて区画をまたいだときだけでなく 1 秒ごとに見直す (resync。止まって待つ巨神でも、止めた人から消える)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanVisibility {
    private TitanVisibility() {
    }

    // 見え方を受ける実体 (本体・鎖・昇り風・口)。broadcastToPlayer で sees を見るもの
    public interface Hidden {
        String visibilityTitan();
    }

    // 眠りを見届ける人 (巨神 → 人)。心臓を止めた戦いで運ばれた人。眠りが終われば消す (保存しない)
    private static final Map<String, java.util.Set<UUID>> REST_WITNESS = new HashMap<>();

    // 心臓が止まった (TitanEnd.onHeartStopped)。ids の人は、眠りが終わるまで巨神を見る
    public static void witnessRest(String titan, java.util.Collection<UUID> ids) {
        REST_WITNESS.computeIfAbsent(titan, k -> new java.util.HashSet<>()).addAll(ids);
        CACHE.clear();
    }

    private static boolean restWitness(MinecraftServer server, UUID player, String titan) {
        java.util.Set<UUID> w = REST_WITNESS.get(titan);
        if (w == null) {
            return false;
        }
        if (!jp.revati.skyabyss.titan.end.TitanDormancy.isResting(server, titan)) {
            REST_WITNESS.remove(titan);
            return false;
        }
        return w.contains(player);
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
        if (!stopped(server, sp.getUUID(), titan) || restWitness(server, sp.getUUID(), titan)) {
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

    // ---------------------------------------------------------------- 送り直し
    // バニラは、実体か人が区画をまたいだときにしか broadcastToPlayer を見直さない。止まって待つ巨神 (鎖を下ろした岩鯨・潮門を開けた潮殻など) は
    // 区画をまたがないので、心臓を止めた・パーティを抜けた人にも送られたままになる。1 秒ごとに、見え方を受ける実体を全員について見直す
    private static java.lang.reflect.Field entityMapField;
    private static java.lang.reflect.Method updatePlayerMethod;
    private static boolean reflectFailed;
    // サーバーにいる、見え方を受ける実体 (巨神は種類ごとに一体なので、数は少ない)
    private static final java.util.Set<net.minecraft.world.entity.Entity> HIDDEN = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @SubscribeEvent
    static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Hidden) {
            HIDDEN.add(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            HIDDEN.remove(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD && level.getGameTime() % CACHE_TICKS == 7) {
            resync(level);
        }
    }

    // 見え方を受ける実体の送り先を、今の判定に合わせる。見直した実体の数 (自己テスト)
    public static int resync(ServerLevel level) {
        if (reflectFailed || level.players().isEmpty()) {
            return 0;
        }
        try {
            if (entityMapField == null) {
                entityMapField = net.minecraft.server.level.ChunkMap.class.getDeclaredField("entityMap");
                entityMapField.setAccessible(true);
            }
            Map<?, ?> map = (Map<?, ?>) entityMapField.get(level.getChunkSource().chunkMap);
            int n = 0;
            for (net.minecraft.world.entity.Entity e : java.util.List.copyOf(HIDDEN)) {
                Object tracked = e.level() == level && !e.isRemoved() ? map.get(e.getId()) : null;
                if (tracked == null) {
                    continue;
                }
                if (updatePlayerMethod == null) {
                    updatePlayerMethod = tracked.getClass().getDeclaredMethod("updatePlayer", ServerPlayer.class);
                    updatePlayerMethod.setAccessible(true);
                }
                for (ServerPlayer p : Players.snapshot(level.players())) {
                    updatePlayerMethod.invoke(tracked, p);
                }
                n++;
            }
            return n;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reflectFailed = true;
            SkyAbyss.LOGGER.warn("Titan visibility resync is off (could not reach the entity tracker): {}", ex.toString());
            return 0;
        }
    }

    // 自己テスト: その人に、その実体が送られているか
    public static boolean sentTo(ServerLevel level, net.minecraft.world.entity.Entity e, ServerPlayer p) {
        try {
            if (entityMapField == null) {
                entityMapField = net.minecraft.server.level.ChunkMap.class.getDeclaredField("entityMap");
                entityMapField.setAccessible(true);
            }
            Object tracked = ((Map<?, ?>) entityMapField.get(level.getChunkSource().chunkMap)).get(e.getId());
            if (tracked == null) {
                return false;
            }
            java.lang.reflect.Field seen = tracked.getClass().getDeclaredField("seenBy");
            seen.setAccessible(true);
            return ((java.util.Set<?>) seen.get(tracked)).contains(p.connection);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CACHE.clear();
        REST_WITNESS.clear();
        HIDDEN.clear();
        STOPPED_OVERRIDE = null;
    }
}
