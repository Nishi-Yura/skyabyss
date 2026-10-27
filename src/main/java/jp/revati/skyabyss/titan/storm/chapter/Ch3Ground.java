package jp.revati.skyabyss.titan.storm.chapter;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.ArtisanSpawner;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 三章の地上。西の峰の跡地 に、世界で一つずつ置く。部品とデータは tools/gen/ch3/gen_ch3_ground.py。
//
// ---------------------------------------------------------------- 場所 (StormSites の 4 種に足す)
//   ch3_wind_flag      風の道の旗 5   稜線 (高台) に沿って。着くと合図 ch3_flag。五つ目で峰じゅうの旗が一斉に逆を向く (人ごとに一度)
//   ch3_scorched_watch 焼けた見張り塔 小さなダンジョン (ScorchedWatch)。旅の職人の三章の立つ所 (印 artisan)
//   ch3_gleaner_field  雷拾いの原     焼けた荷と、硝子のように固まった砂。拾い残しの箱 (人ごとに一度、粗銅 8・銅線 3・焦げ羽 2)。雷拾い (P16) が 2 羽まで荷をついばむ (Ch3Mobs)
//   ch3_wind_cliff     風読みの崖     岩の舞台と碑 (sign.skyabyss.ch3_cliff)、樽に遠眼鏡 (世界に一つ)
//   避雷の塔の跡 (StormSites.RUIN) は部品を ch3_rod_tower に替え、台 4・銅の戸・避雷の見本の柱を足した (LightningPillars)
//
// ---------------------------------------------------------------- 支度の段 (メインの段。順不同。PrepQuests の三章の 3 本)
//   ch3/lightning_pillars (語り部)  台に避雷針 4 本 (合図 ch3_pillar ×4) → 戸の奥の風の民の書 (伝承 ch3/stayer_returns)
//   ch3/scorched_watch    (職人)    焼けた見張り塔を抜ける (伝承 ch3/scorched_watch) → 職人に頁を見せる
//   ch3/sky_stone         (イサナ)  ダイヤ 5、またはダイヤの胸当てか剣
//   三本そろうと合図 ch3_ready (進捗 storm_trail)。竜骨の間の入口の遅れた影は MirrorLag、イサナの決意と語り部の数え歌は会話の足し書き (dialogue/ch3/*_ground.json)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch3Ground {
    public static final String FLAG = "ch3_wind_flag";
    public static final int FLAGS = 5;
    public static final String WATCH = ScorchedWatch.SITE;
    public static final String FIELD = "ch3_gleaner_field";
    public static final String CLIFF = "ch3_wind_cliff";
    public static final List<String> ELEMENTS = List.of(FLAG, WATCH, FIELD, CLIFF);

    public static final String Q_PILLARS = "ch3/lightning_pillars";
    public static final String Q_WATCH = "ch3/scorched_watch";
    public static final String Q_STONE = "ch3/sky_stone";
    public static final List<String> TRAILS = List.of(Q_PILLARS, Q_WATCH, Q_STONE);

    public static final String PAGE_PILLARS = "ch3/pillars";
    public static final String PAGE_STAYER = "ch3/stayer_returns";
    public static final String PAGE_MIRROR = "ch3/mirror_lag";
    public static final String PAGE_WATCH = "ch3/scorched_watch";
    public static final List<String> LORE = List.of(PAGE_PILLARS, PAGE_STAYER, PAGE_MIRROR, PAGE_WATCH);

    public static final String SIGNAL_FLAG = "ch3_flag";
    public static final String SIGNAL_PILLAR = "ch3_pillar";
    public static final String FIELD_CACHE = "ch3_gleaner_cache";
    public static final String CLIFF_SIGN = "sign.skyabyss.ch3_cliff";

    private static boolean registered;

    private Ch3Ground() {
    }

    // StormSites.register から (同じ時機に章の要素をそろえる)
    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ChapterSites.element(FLAG, Chapters.STORM, FLAG).atGround().dist(30, 140).count(FLAGS).prefer(Prefer.HIGH).arriveRadius(6)
                .post(Ch3Ground::finishFlag)
                .onArrive(Ch3Ground::flagArrive).register();
        ScorchedWatch.register();
        ChapterSites.element(FIELD, Chapters.STORM, FIELD).atGround().dist(40, 140).prefer(Prefer.HIGH).arriveRadius(10)
                .post(job -> keep(job, "cache"))
                .onArrive((p, s) -> Notice.narrate(p, "message.skyabyss.ch3g.arrive." + FIELD)).register();
        ChapterSites.element(CLIFF, Chapters.STORM, CLIFF).atGround().dist(60, 150).prefer(Prefer.HIGH).arriveRadius(8)
                .post(Ch3Ground::finishCliff)
                .onArrive((p, s) -> Notice.narrate(p, "message.skyabyss.ch3g.arrive." + CLIFF)).register();
        SiteTreasure.register(FIELD_CACHE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:copper_lump", 8),
                new SiteTreasure.Entry(Ch3Mobs.COPPER_WIRE, 3), new SiteTreasure.Entry(Ch3Mobs.SCORCHED_FEATHER, 2))));
        LightningPillars.register();
        PrepQuests.onReady(3, p -> Notice.narrate(p, "message.skyabyss.ch3g.ready"));
        // 旅の職人の三章の立つ所: 焼けた見張り塔の南西の野営
        ArtisanSpawner.register(new ArtisanSpawner.Spot(3, WATCH, "artisan", p -> Chapters.of(p) == Chapters.STORM));
    }

    static void keep(SiteJob job, String name) {
        BlockPos at = job.mark(name);
        if (at != null) {
            job.entry.extra(name, at);
        }
    }

    // ---------------------------------------------------------------- 風の道の旗
    // 旗は建てた日の風下を指す (StormVane.windAngle。x が東、z が南)。旗の向きの値は 0 = 南、4 = 西、8 = 北、12 = 東 (22.5 度ずつ)
    static int downwind(long day) {
        double a = StormVane.windAngle(day);
        double deg = Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
        return Math.floorMod(Math.round((float) (deg / 22.5)), 16);
    }

    static void finishFlag(SiteJob job) {
        BlockPos at = job.mark("banner");
        if (at == null) {
            return;
        }
        job.entry.extra("banner", at);
        BlockState s = job.level.getBlockState(at);
        if (s.getBlock() instanceof BannerBlock && s.hasProperty(BannerBlock.ROTATION)) {
            job.set(at, s.setValue(BannerBlock.ROTATION, downwind(job.level.getDayTime() / 24000L)));
        }
    }

    static void flagArrive(ServerPlayer p, ChapterSites.Site site) {
        Notice.narrate(p, "message.skyabyss.ch3g.arrive." + FLAG);
        Quests.signal(p, SIGNAL_FLAG);
        if (flagsVisited(p.server, p, site.pos()) >= FLAGS && Growth.discover(p, "ch3_flags_turned")) {
            int n = turnFlags(p.serverLevel());
            StormChapter.later(p.server, 30, () -> Notice.narrate(p, "message.skyabyss.ch3g.flags.turn"));
            if (n > 0) {
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.6f, 1.6f);
            }
        }
    }

    // その人が着いた旗の数 (今着いた旗を含む)
    static int flagsVisited(MinecraftServer server, ServerPlayer p, BlockPos now) {
        int n = 0;
        for (SiteData.Entry e : SiteData.get(server.overworld()).allEntries()) {
            if (e.siteId.equals(FLAG) && e.built && (e.arrived.contains(p.getUUID()) || e.pos.equals(now))) {
                n++;
            }
        }
        return n;
    }

    // 旗を一斉に逆へ (向きの値 + 8)。回した旗の数
    public static int turnFlags(ServerLevel level) {
        int n = 0;
        for (SiteData.Entry e : SiteData.get(level.getServer().overworld()).allEntries()) {
            BlockPos at = e.siteId.equals(FLAG) && e.built ? e.extra("banner") : null;
            if (at == null || !level.isLoaded(at)) {
                continue;
            }
            BlockState s = level.getBlockState(at);
            if (s.getBlock() instanceof BannerBlock && s.hasProperty(BannerBlock.ROTATION)) {
                level.setBlock(at, s.setValue(BannerBlock.ROTATION, (s.getValue(BannerBlock.ROTATION) + 8) & 15), 2);
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 風読みの崖
    static void finishCliff(SiteJob job) {
        keep(job, "spyglass");
        keep(job, "tablet");
        BlockPos barrel = job.mark("spyglass");
        if (barrel != null && job.level.getBlockEntity(barrel) instanceof BarrelBlockEntity be) {
            be.setItem(4, new ItemStack(Items.SPYGLASS));
        }
        BlockPos tablet = job.mark("tablet");
        if (tablet != null) {
            job.set(tablet, Tablets.standingState().setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.STORM));
            Tablets.write(job.level, tablet, CLIFF_SIGN);
        }
    }

    // ---------------------------------------------------------------- 雷拾いの原の箱・塔の跡の石の箱
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || !level.getBlockState(event.getPos()).is(Blocks.BARREL)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (LightningPillars.vaultAt(level.getServer(), pos) != null) {
            event.setCanceled(true);
            LightningPillars.openVault(player, level, pos);
            return;
        }
        for (SiteData.Entry e : SiteData.get(level.getServer().overworld()).allEntries()) {
            if (e.siteId.equals(FIELD) && pos.equals(e.extra("cache"))) {
                event.setCanceled(true);
                openCache(player, level, pos);
                return;
            }
        }
    }

    // 拾い残しの箱: 人ごとに一度、粗銅 8。初めてなら true
    public static boolean openCache(ServerPlayer player, ServerLevel level, BlockPos at) {
        boolean first = !SiteTreasure.claimed(player, FIELD_CACHE);
        if (first) {
            SiteTreasure.claim(player, FIELD_CACHE);
            Notice.narrate(player, "message.skyabyss.ch3g.field.cache");
        } else {
            Notice.narrate(player, "message.skyabyss.ch3g.field.cache_empty");
        }
        if (at != null) {
            level.playSound(null, at, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8f, 0.7f);
        }
        return first;
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!registered || server.overworld() == null) {
            return;
        }
        try {
            if (server.getTickCount() % LightningPillars.EVERY == 3) {
                LightningPillars.tick(server);
            }
            ScorchedWatch.tick(server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("[ch3 ground] tick failed", e);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        LightningPillars.forget();
        ScorchedWatch.forget();
        MirrorLag.forget();
    }
}
