package jp.revati.skyabyss.titan.storm.chapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.ChapterCards;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 三章「風に逆らう嵐」(雷翼。docs/design/12 の 12 章、01 の 4.2) の見回りの入口。章の中身は次の部品に分けてある。
//   StormForeshadow  予兆 3 拍 (焼けた筋 → 伏せる獣と遠雷 → 稲光の翼) と、モッドバスでの登録 (予兆・小要素・空の段)
//   StormSites       小要素 4 種 (風見の丘・避雷の塔の跡・焼けた荷・風の碑 3 枚)
//   StormVane        風見の丘の矢 (巨神の方角を指す。巨神がいなければ風に逆らう向き)
//   StormShowcase    中盤の出来事 (巨神が空の影から隠れる。影と巨神を一緒に見て巡礼者の大きさを知る。一人一度)
// ここでは、章の細かい見回り (人ごとに 1 秒に一度) をまとめる: 峰の足もとへ着いた合図、章の余韻 (雷翼の心臓を止めたあと)、見せ場の見張り。
// 段階 2 (三章) の人にだけ働く。ほかの章の人には何も起こさない。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormChapter {
    public static final String LORE_BURNT_PACK = "chapter1/storm_burnt_pack";
    public static final String LORE_TABLETS = "chapter1/storm_wind_tablets";
    public static final String LORE_SIZE = "chapter1/storm_size";
    // イサナのクエスト (ch3/sub_isana_wait) の合図: 夕立の刻に、止まった雷翼の足もとへ着いた
    public static final String SIGNAL_PERCH_FOOT = "ch3_perch_foot";
    public static final double PERCH_FOOT_RANGE = 28;
    static final long PERCH_FOOT_REPEAT = 400;

    private record Delayed(long at, Runnable action) {
    }

    private static final List<Delayed> LATER = new ArrayList<>();
    private static final Map<UUID, Long> PERCH_SENT = new HashMap<>();

    private StormChapter() {
    }

    // ---------------------------------------------------------------- 少し後で (サーバーの tick で数える)
    static void later(MinecraftServer server, int ticks, Runnable action) {
        synchronized (LATER) {
            LATER.add(new Delayed(server.getTickCount() + Math.max(0, ticks), action));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        List<Runnable> due = new ArrayList<>();
        synchronized (LATER) {
            for (Iterator<Delayed> it = LATER.iterator(); it.hasNext();) {
                Delayed d = it.next();
                if (d.at() <= server.getTickCount()) {
                    due.add(d.action());
                    it.remove();
                }
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("[storm chapter] a delayed action failed", e);
            }
        }
        if (server.getTickCount() % StormVane.UPDATE_TICKS == 0) {
            StormVane.tick(server);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        synchronized (LATER) {
            LATER.clear();
        }
        PERCH_SENT.clear();
        StormShowcase.forgetAll();
    }

    // ---------------------------------------------------------------- 人ごとの見回り (1 秒に一度)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        int phase = (player.getUUID().hashCode() & 0x7fffffff) % 20;
        if (player.tickCount % 20 != phase || !player.isAlive() || player.isSpectator()) {
            return;
        }
        Chapters chapter = Chapters.of(player);
        if (chapter == Chapters.STORM) {
            perchFoot(player);
            StormShowcase.tick(player);
        }
        aftermath(player);
    }

    // 夕立の刻に、止まった雷翼の足もとへ着いた (イサナのクエストの合図)。400 tick に一度まで
    static void perchFoot(ServerPlayer player) {
        StormWingEntity titan = StormKeeper.KEEPER.current((ServerLevel) player.level());
        if (titan == null || !titan.isPerched() || Math.hypot(titan.getX() - player.getX(), titan.getZ() - player.getZ()) > PERCH_FOOT_RANGE) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = PERCH_SENT.get(player.getUUID());
        if (last != null && now - last < PERCH_FOOT_REPEAT) {
            return;
        }
        PERCH_SENT.put(player.getUUID(), now);
        Quests.signal(player, SIGNAL_PERCH_FOOT);
    }

    // 章の余韻: 自分で雷翼の心臓を止めた人に、章の終わりの札と語り部の一言 (一度だけ。すでに遊んでいた人 = legacy には出さない)
    static boolean aftermath(ServerPlayer player) {
        if (player.getServer() == null || !Progression.stopped(player.getServer(), player.getUUID(), Titans.THUNDER_WING)
                || ArcData.of(player).aftermathShown(Chapters.STORM) || OpeningDirector.state(player).legacy()) {
            return false;
        }
        if (!ChapterCards.aftermath(player, Chapters.STORM)) {
            return false;
        }
        Notice.narrate(player, "message.skyabyss.ch3.aftermath");
        return true;
    }
}
