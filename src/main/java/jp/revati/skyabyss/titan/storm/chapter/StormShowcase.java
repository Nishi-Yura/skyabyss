package jp.revati.skyabyss.titan.storm.chapter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.story.arc.Foreshadow;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// 三章の中盤の出来事「巨神が影から隠れる」(docs/design/12 の 1.2 と 12.4、01 の 4.2)。
// 雷翼は空の影 (巡礼者) が 640 ブロック以内を通るあいだ、近くの峰へ降りて翼で頭を覆う (StormWingEntity の COWER)。
// それを三章の人が影と一緒に見たとき、一度だけ、字幕三行と心音で「巡礼者は、雷翼よりずっと大きい」と気づかせる。伝承「影と翼」を記し、これで済み。
//   見せ場の条件 (witnessing): 三章の人、まだ見ていない (伝承 storm_size がない)、影が通っている最中、雷翼がうずくまって 2 秒以上、
//                              雷翼まで 400 ブロック以内で視線が通る、影の中心が REACH 以内、空が見える、場面の最中でない
//   起こす (begin): 0 秒 字幕 1「峰にうずくまっている」 → 5 秒 字幕 2「影が渡っていく。雷翼が小さい」 → 11 秒 字幕 3 + 心音 + 伝承。操作は奪わない
//                   (場面 (カメラ) は使わない。Scenes は共有の台本なので足さなかった。足すなら Cinematics.onEnd と startOnce で同じ条件につなげる)
//   確実に見せる (directedReady): ワールドの影は 2〜4 日に一度なので、待たせすぎないよう、予兆 3 拍目 (稲光の翼) のあと、昼に雷翼が巡航して 120〜360 ブロックの所にいて、
//                   その人から見える (空が見える) 状態が 10 秒続いたら、その人のための影を一回起こす (SkyEvents.startFor)。影は人の近くを通るので、雷翼がうずくまりに来る。
public final class StormShowcase {
    static final double WITNESS_RANGE = 400;
    static final int COWER_SETTLE_TICKS = 40;
    static final int DIRECTED_DWELL_TICKS = 200;
    static final double DIRECTED_MIN = 120;
    static final double DIRECTED_MAX = 360;
    // 字幕の間 (tick): 2 行目、3 行目 (と伝承)
    static final int LINE2_AT = 100;
    static final int LINE3_AT = 220;

    private static final Map<UUID, Long> DWELL = new HashMap<>();
    private static final Map<UUID, Long> RUNNING = new HashMap<>();

    private StormShowcase() {
    }

    static void forgetAll() {
        DWELL.clear();
        RUNNING.clear();
    }

    public static boolean done(ServerPlayer player) {
        return Lore.state(player).has(StormChapter.LORE_SIZE);
    }

    // ---------------------------------------------------------------- 条件 (純粋。テストできる形)
    public static boolean witnessing(boolean stormChapter, boolean done, boolean running, boolean passActive, boolean cowering, int cowerTicks, double titanDist,
            boolean lineOfSight, double shadowDist, boolean sky, boolean inScene) {
        return stormChapter && !done && !running && passActive && cowering && cowerTicks >= COWER_SETTLE_TICKS && titanDist <= WITNESS_RANGE && lineOfSight
                && shadowDist <= SkyPass.REACH && sky && !inScene;
    }

    public static boolean directedReady(boolean stormChapter, boolean done, boolean flashDone, boolean passActive, boolean day, boolean raining,
            boolean cruising, double titanDist, boolean sky, long dwellTicks) {
        return stormChapter && !done && flashDone && !passActive && day && !raining && cruising && titanDist >= DIRECTED_MIN && titanDist <= DIRECTED_MAX && sky
                && dwellTicks >= DIRECTED_DWELL_TICKS;
    }

    // ---------------------------------------------------------------- 見張り (三章の人に 1 秒に一度)
    static void tick(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        boolean done = done(player);
        UUID id = player.getUUID();
        if (done) {
            DWELL.remove(id);
            return;
        }
        StormWingEntity titan = StormKeeper.KEEPER.current(level);
        SkyPass pass = SkyEvents.activePass();
        boolean passActive = pass != null && pass.isActive(level.getGameTime());
        boolean sky = level.canSeeSky(player.blockPosition().above());
        double dist = titan == null ? Double.MAX_VALUE : Math.hypot(titan.getX() - player.getX(), titan.getZ() - player.getZ());
        if (titan != null && passActive) {
            double age = pass.age(level.getGameTime(), 0f);
            double shadow = Math.hypot(pass.centerX(age) - player.getX(), pass.centerZ(age) - player.getZ());
            if (witnessing(true, false, RUNNING.containsKey(id), true, titan.cowering(), titan.modeTicks(), dist, player.hasLineOfSight(titan), shadow, sky,
                    Cinematics.active(player))) {
                begin(player);
            }
            return;
        }
        // 影が通っていない: 待たせすぎないように、条件が 10 秒続いたら、その人の近くへ影を一回起こす
        boolean ready = directedReady(true, false, Foreshadow.done(player, "titan_storm/" + StormForeshadow.WING_FLASH), false, level.isDay(), level.isRaining(),
                titan != null && titan.mode() == StormWingEntity.Mode.CRUISE, dist, sky, DIRECTED_DWELL_TICKS);
        if (!ready) {
            DWELL.remove(id);
            return;
        }
        long now = level.getGameTime();
        long since = DWELL.computeIfAbsent(id, k -> now);
        if (now - since >= DIRECTED_DWELL_TICKS) {
            DWELL.remove(id);
            SkyEvents.startFor(level, player);
        }
    }

    // ---------------------------------------------------------------- 起こす
    // 字幕 3 行 (0・5・11 秒) と心音 2 つ。最後に伝承「影と翼」を記して済み。戻り値 = 始めたか
    public static boolean begin(ServerPlayer player) {
        if (done(player) || RUNNING.containsKey(player.getUUID())) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        UUID id = player.getUUID();
        RUNNING.put(id, level.getGameTime());
        Notice.narrate(player, "message.skyabyss.ch3.showcase_1");
        heartbeat(player, 0.5f);
        StormChapter.later(level.getServer(), LINE2_AT, () -> {
            ServerPlayer p = player(level, id);
            if (p != null) {
                Notice.narrate(p, "message.skyabyss.ch3.showcase_2");
                heartbeat(p, 0.6f);
            }
        });
        StormChapter.later(level.getServer(), LINE3_AT, () -> {
            ServerPlayer p = player(level, id);
            RUNNING.remove(id);
            if (p != null) {
                finish(p);
            }
        });
        return true;
    }

    // 終わりの一行と伝承 (「見た」ことの記録。二度は起きない)。テストはこれを直接呼ぶ
    static void finish(ServerPlayer player) {
        Notice.narrate(player, "message.skyabyss.ch3.showcase_3");
        heartbeat(player, 0.7f);
        Lore.unlock(player, StormChapter.LORE_SIZE, true);
    }

    @Nullable
    private static ServerPlayer player(ServerLevel level, UUID id) {
        return level.getServer().getPlayerList().getPlayer(id);
    }

    private static void heartbeat(ServerPlayer player, float volume) {
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, volume, 0.6f);
    }

    static boolean running(UUID id) {
        return RUNNING.containsKey(id);
    }
}
