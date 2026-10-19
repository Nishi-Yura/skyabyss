package jp.revati.skyabyss.rebirth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.advancement.Beats;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 転生の見せ方の中心 (サーバー側)。回数の書き込み・記録 (人生の長さ・死・巨神の最速)・称号の解除・名前の飾り (チャット・Tab)・実績。
//   転生の本体 (別の担当) へ: 転生を終えたあと Rebirths.onRebirth(player) を一度呼ぶ。今の仮 (RebirthView.isStub) では回数を 1 増やす所まで受け持つ。
//   本体が API を出したら RebirthView.useSource(...) で元を切り替える (そのときは onRebirth は記録と称号と実績だけ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Rebirths {
    private Rebirths() {
    }

    // ---------------------------------------------------------------- 読む・書く
    public static long playTicks(ServerPlayer p) {
        return p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
    }

    public static RebirthData data(Player p) {
        return RebirthView.data(p);
    }

    private static void put(ServerPlayer p, RebirthData d) {
        if (!d.equals(p.getData(RebirthSetup.DATA.get()))) {
            p.setData(RebirthSetup.DATA.get(), d);
        }
    }

    // 仮の元の回数を書く (開発用コマンド・自己テスト)。順位表の名前と数字も合わせる。記録 (人生の長さ・死) は onRebirth だけが付ける
    public static void setCount(ServerPlayer p, int n) {
        int c = RebirthTier.clamp(n);
        if (!RebirthView.isStub()) {
            // 本体の回数も合わせる (開発用)
            jp.revati.skyabyss.rpg.Growth.get(p).rebirths = c;
            jp.revati.skyabyss.rpg.Growth.sync(p);
            jp.revati.skyabyss.rpg.Growth.applyModifiers(p);
        }
        put(p, data(p).withCount(c));
        RebirthStats.Entry e = RebirthStats.get(p.server).entry(p.getUUID());
        e.name = p.getGameProfile().getName();
        if (c > 0 && e.order == Long.MAX_VALUE) {
            e.order = RebirthStats.get(p.server).takeOrder();
        }
        e.rebirths = c;
        RebirthStats.get(p.server).touch();
        refreshNames(p);
        updateTitles(p);
        fireBeats(p, c, false);
    }

    // 転生を一度終えた。人生の長さと死の数を記録し、最速・最少の死を更新する
    public static void onRebirth(ServerPlayer p) {
        RebirthStats stats = RebirthStats.get(p.server);
        RebirthStats.Entry e = stats.entry(p.getUUID());
        e.name = p.getGameProfile().getName();
        long play = playTicks(p);
        long life = Math.max(1, play - e.lifeStartPlay);
        boolean flawless = e.deathsLife == 0;
        if (e.fastestLife < 0 || life < e.fastestLife) {
            e.fastestLife = life;
        }
        if (e.fewestDeaths < 0 || e.deathsLife < e.fewestDeaths) {
            e.fewestDeaths = e.deathsLife;
        }
        if (flawless) {
            e.flawless++;
            put(p, data(p).withFlawless(e.flawless));
        }
        e.deathsLife = 0;
        e.lifeStartPlay = play;
        int now = RebirthView.count(p);
        if (RebirthView.isStub()) {
            now = RebirthTier.clamp(now + 1);
            put(p, data(p).withCount(now));
        }
        if (e.order == Long.MAX_VALUE) {
            e.order = stats.takeOrder();
        }
        e.rebirths = now;
        stats.touch();
        refreshNames(p);
        updateTitles(p);
        fireBeats(p, now, flawless);
        Notice.unlock(p, "message.skyabyss_rebirth.reborn", RebirthTier.symbols(now), now);
    }

    private static void fireBeats(ServerPlayer p, int count, boolean flawless) {
        for (int m : new int[] {1, 5, 10, 20}) {
            if (count >= m) {
                Beats.fire(p, "rebirth:" + m);
            }
        }
        if (flawless) {
            Beats.fire(p, "rebirth:flawless");
        }
    }

    // 名前の見え方 (チャット・倒れた知らせ・Tab) を作り直す
    public static void refreshNames(ServerPlayer p) {
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    // ---------------------------------------------------------------- 称号
    public static RebirthTitles.Facts facts(ServerPlayer p) {
        RebirthStats.Entry e = RebirthStats.get(p.server).peek(p.getUUID());
        int count = RebirthView.count(p);
        if (e == null) {
            return new RebirthTitles.Facts(count, 0, Progression.stage(p), 0, -1, -1, 0);
        }
        return new RebirthTitles.Facts(count, e.flawless, Progression.stage(p), e.deathsTotal, e.fastestLife, e.fewestDeaths, e.titanBest.size());
    }

    // 称号の解除 (足すだけで消さない)。新しく解けたものがあれば知らせる
    public static void updateTitles(ServerPlayer p) {
        RebirthData d = data(p);
        long mask = d.unlocked() | RebirthTitles.unlockedMask(facts(p));
        long added = mask & ~d.unlocked();
        if (added != 0) {
            put(p, d.withUnlocked(mask));
            for (RebirthTitles.Title t : RebirthTitles.all()) {
                if ((added & (1L << t.index())) != 0) {
                    Notice.unlock(p, "message.skyabyss_rebirth.title_unlocked", Component.translatable("title.skyabyss_rebirth." + t.id()));
                }
            }
        }
    }

    // 称号を選ぶ (解けているものだけ。"" で外す)
    public static boolean selectTitle(ServerPlayer p, String id) {
        RebirthData d = data(p);
        if (id.isEmpty()) {
            put(p, d.withTitle(""));
            return true;
        }
        RebirthTitles.Title t = RebirthTitles.byId(id);
        if (t == null || !RebirthTitles.has(d.unlocked(), t)) {
            return false;
        }
        put(p, d.withTitle(id));
        return true;
    }

    // 順位表に載せるか (断る = false)
    public static void setHidden(ServerPlayer p, boolean hidden) {
        put(p, data(p).withHidden(hidden));
        RebirthStats stats = RebirthStats.get(p.server);
        stats.entry(p.getUUID()).hidden = hidden;
        stats.entry(p.getUUID()).name = p.getGameProfile().getName();
        stats.touch();
    }

    // ---------------------------------------------------------------- 記録のきっかけ
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            RebirthStats.Entry e = RebirthStats.get(p.server).entry(p.getUUID());
            e.deathsLife++;
            e.deathsTotal++;
            RebirthStats.get(p.server).touch();
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            String titan = Progression.titanOfBack(event.getTo());
            if (titan != null) {
                RebirthStats.Entry e = RebirthStats.get(p.server).entry(p.getUUID());
                if (e.entered.putIfAbsent(titan, p.server.overworld().getGameTime()) == null) {
                    RebirthStats.get(p.server).touch();
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            RebirthStats.Entry e = RebirthStats.get(p.server).entry(p.getUUID());
            e.name = p.getGameProfile().getName();
            RebirthData d = data(p);
            if (d.hidden() != e.hidden) {
                e.hidden = d.hidden();
            }
            // 仮の元の回数を順位表へ合わせる (本体の元でも、回数だけは写す)
            int c = RebirthView.count(p);
            if (c != e.rebirths) {
                e.rebirths = c;
                if (c > 0 && e.order == Long.MAX_VALUE) {
                    e.order = RebirthStats.get(p.server).takeOrder();
                }
            }
            RebirthStats.get(p.server).touch();
            updateTitles(p);
            refreshNames(p);
        }
    }

    // 巨神の心臓を止めた (Progression.onTitanStopped から)。入りの時刻から最速を測る
    public static void onTitanStopped(MinecraftServer server, Collection<UUID> participants, String titan) {
        RebirthStats stats = RebirthStats.get(server);
        long now = server.overworld().getGameTime();
        for (UUID id : participants) {
            RebirthStats.Entry e = stats.entry(id);
            Long in = e.entered.remove(titan);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                e.name = p.getGameProfile().getName();
            }
            if (in != null && now > in) {
                long t = now - in;
                Long best = e.titanBest.get(titan);
                if (best == null || t < best) {
                    e.titanBest.put(titan, t);
                }
                if (stats.offerRecord(titan, id, e.name, t, now)) {
                    RebirthBoards.refresh(server, titan);
                    if (p != null) {
                        Notice.unlock(p, "message.skyabyss_rebirth.record", Progression.titanName(titan), RebirthLeaderboard.time(t));
                    }
                }
            }
            stats.touch();
            if (p != null) {
                updateTitles(p);
            }
        }
    }

    // 鳴き方: 称号の解除を数分おきに見直す (進みで解ける称号。心臓を止めた直後は上の hook でも見る)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 1200 == 600) {
            updateTitles(p);
        }
    }

    // 今いない人の回数 (記録から。パーティ一覧用)
    public static int offlineCount(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            return RebirthView.count(p);
        }
        RebirthStats.Entry e = RebirthStats.get(server).peek(id);
        return e == null ? 0 : RebirthTier.clamp(e.rebirths);
    }

    public static List<RebirthLeaderboard.Row> top(MinecraftServer server, RebirthLeaderboard.Metric metric, int limit) {
        return RebirthLeaderboard.top(RebirthStats.get(server).rows(), metric, limit);
    }
}
