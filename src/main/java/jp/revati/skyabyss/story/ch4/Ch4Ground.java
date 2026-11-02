package jp.revati.skyabyss.story.ch4;

import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.ArtisanSpawner;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.opening.site.SiteNetwork;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.relic.RelicStyle;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.titan.ember.EmberBasin;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.EmberTitanSetup;
import jp.revati.skyabyss.titan.ember.smith.AshSmith;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 四章の地上。都の南の灰の地 (四章の跡地) に、世界で一つずつ置く。部品とデータは直接直す (生成スクリプトはない)。
// 既存の四章の小要素 3 つ (灰の民の炉跡・灰降り観測点・温かい岩棚。EmberChapter) に足す。
//
// ---------------------------------------------------------------- 場所
//   ch4_ash_mark        灰の道の標 5     丘から灰の降る円の縁へ (高台)。着くと合図 ch4_ash_mark (サブ ch4/ash_road)。五つ目で一行 (人ごとに一度)
//   ch4_dead_forge_pit  消えた炉の坑     小さなダンジョン (DeadForgePit)。旅の職人の四章の立つ所 (印 artisan)
//   ch4_warm_rocks      温かい岩の群れ 2 玄武岩の柱の群れと温石の台。温石を持って台に触れると、岩が熱を返す (粒と一行。背中の熱の予習)
//   ch4_soot_kiln       煤の窯の跡       灰の民の窯。拾い残しの箱 (人ごとに一度、煤鉄 4・灰麻 3)、碑 (伝承 ch4/soot_kiln)。着くと合図 ch4_kiln (サブ ch4/dragger_trail)。
//                                        灰ひきずりの巣 (逃げる灰ひきずりはここへ転がる)。語り部に「窯の跡」の一言の印
//   ch4_ash_watch       灰降りの高台     石の舞台と碑。夜に着くと、灰の地の眠る場所の上に赤い粒 (眠る場所の見当)。イサナに「高台」の一言の印
//
// ---------------------------------------------------------------- 支度の段 (メインの段。順不同。PrepQuests の四章の 2 本)
//   ch4/fire_draught  (職人)   耐火のポーション 2 (醸造台。都の薬師の醸造の手ほどき・ネザーのマグマクリーム) → 職人に見せる (伝承 ch4/fire_draught)
//   ch4/forge_ruins   (語り部) 消えた炉の坑を抜ける (伝承 ch4/forge_page) → 語り部に頁を見せる
//   二本そろうと合図 ch4_ready (進捗 ember_trail)。メイン「灰の昇る山」には「乗る」の段 (伝承 chapter1/ember_ruins) を足した。
//   灰の鍛え手 (背中の中ボス) が構える前の一言「返しに来たか」(伝承 ch4/return_the_beat) は、鍛え手が現れたとき近くの人に (onSmithJoin)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch4Ground {
    public static final String MARK = "ch4_ash_mark";
    public static final int MARKS = 5;
    public static final String PIT = DeadForgePit.SITE;
    public static final String ROCKS = "ch4_warm_rocks";
    public static final String KILN = "ch4_soot_kiln";
    public static final String WATCH = "ch4_ash_watch";
    public static final List<String> ELEMENTS = List.of(MARK, PIT, ROCKS, KILN, WATCH);

    public static final String Q_DRAUGHT = "ch4/fire_draught";
    public static final String Q_FORGE = "ch4/forge_ruins";
    public static final String Q_ROAD = "ch4/ash_road";
    public static final String Q_DRAGGER = "ch4/dragger_trail";
    public static final List<String> TRAILS = List.of(Q_DRAUGHT, Q_FORGE);

    public static final String PAGE_DRAUGHT = "ch4/fire_draught";
    public static final String PAGE_FORGE = "ch4/forge_page";
    public static final String PAGE_RETURN = "ch4/return_the_beat";
    public static final String PAGE_KILN = "ch4/soot_kiln";
    public static final List<String> LORE = List.of(PAGE_DRAUGHT, PAGE_FORGE, PAGE_RETURN, PAGE_KILN);

    public static final String SIGNAL_MARK = "ch4_ash_mark";
    public static final String SIGNAL_KILN = "ch4_kiln";
    public static final String KILN_CACHE = "ch4_soot_kiln_cache";
    public static final String KILN_SIGN = "sign.skyabyss.ch4_kiln";
    public static final String WATCH_SIGN = "sign.skyabyss.ch4_watch";
    // NPC の会話の印 (場所に着いた人に付ける。語り部・イサナの短い一言)
    public static final String FLAG_KILN = "ch4g_kiln_seen";
    public static final String FLAG_WATCH = "ch4g_watch_seen";

    private static boolean registered;

    private Ch4Ground() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Ch4Ground::register);
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ChapterSites.element(MARK, Chapters.EMBER, MARK).atGround().dist(30, 140).count(MARKS).prefer(Prefer.HIGH).arriveRadius(6)
                .post(job -> clearFloatingPlants(job, 3))
                .onArrive(Ch4Ground::markArrive).register();
        DeadForgePit.register();
        ChapterSites.element(ROCKS, Chapters.EMBER, ROCKS).atGround().dist(40, 140).count(2).arriveRadius(8)
                .post(job -> {
                    keep(job, "stone");
                    clearFloatingPlants(job, 6);
                })
                .onArrive((p, s) -> Notice.narrate(p, "message.skyabyss.ch4g.arrive." + ROCKS)).register();
        ChapterSites.element(KILN, Chapters.EMBER, KILN).atGround().dist(50, 150).arriveRadius(10)
                .post(Ch4Ground::finishKiln)
                .onArrive(Ch4Ground::kilnArrive).register();
        ChapterSites.element(WATCH, Chapters.EMBER, WATCH).atGround().dist(60, 150).prefer(Prefer.HIGH).arriveRadius(8)
                .post(Ch4Ground::finishWatch)
                .onArrive(Ch4Ground::watchArrive).register();
        SiteTreasure.register(KILN_CACHE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:soot_iron", 4), new SiteTreasure.Entry("skyabyss:ash_hemp", 3))));
        PrepQuests.onReady(4, p -> Notice.narrate(p, "message.skyabyss.ch4g.ready"));
        // 旅の職人の四章の立つ所: 消えた炉の坑の南西の野営
        ArtisanSpawner.register(new ArtisanSpawner.Spot(4, PIT, "artisan", p -> Chapters.of(p) == Chapters.EMBER));
        // 目的の帯の行き先 (SiteTargets の bind は client/Ch4ClientSetup)
        SiteNetwork.EXTRA_TARGETS.put(PIT, server -> first(server, PIT, null));
        SiteNetwork.EXTRA_TARGETS.put(KILN, server -> first(server, KILN, null));
        SiteNetwork.EXTRA_TARGETS.put(MARK, server -> first(server, MARK, null));
    }

    // 建て終わりの仕上げ: 部品のまわり (水平 r・高さ -4〜+6) で、足もとの支えをなくした草花・作物を消す
    // (村の畑などの上に建つと、ならしで畑の土が消え、麦が浮くことがある。build audit の ATTACH_SUPPORT)。消した数
    public static int clearFloatingPlants(SiteJob job, int r) {
        int n = 0;
        BlockPos c = job.origin;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -4, -r), c.offset(r, 6, r))) {
            var st = job.level.getBlockState(p);
            if (!(st.getBlock() instanceof net.minecraft.world.level.block.BushBlock) || !st.getFluidState().isEmpty()) {
                continue;
            }
            var below = job.level.getBlockState(p.below());
            if (below.isAir() || below.getCollisionShape(job.level, p.below()).isEmpty() && below.getBlock() != st.getBlock()) {
                job.set(p.immutable(), Blocks.AIR.defaultBlockState());
                n++;
            }
        }
        return n;
    }

    // 建て終わりの仕上げ: 部品のまわり (水平 r) の地面で、まわり 4 方向が同じ高さなのに、その 1 列だけ 1〜4 低い穴を埋める
    // (灰の地のならしで残るくぼみ。あとで落ちる砂利・灰が 1 低いくぼみになる。build audit の FLOOR_DIMPLE)。埋めた列の数
    public static int fillDimples(SiteJob job, int r) {
        int n = 0;
        BlockPos c = job.origin;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                int top = topSolid(job, x, z, c.getY());
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int around = Integer.MIN_VALUE;
                boolean even = true;
                for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    int t = topSolid(job, x + d.getStepX(), z + d.getStepZ(), c.getY());
                    if (around == Integer.MIN_VALUE) {
                        around = t;
                    }
                    if (t == Integer.MIN_VALUE || t != around) {
                        even = false;
                        break;
                    }
                }
                if (!even || around <= top || around - top > 4) {
                    continue;
                }
                BlockState skin = job.level.getBlockState(new BlockPos(x + 1, around, z));
                for (int y = top + 1; y <= around; y++) {
                    job.set(new BlockPos(x, y, z), y == around ? skin : Blocks.TUFF.defaultBlockState());
                }
                n++;
            }
        }
        return n;
    }

    // 列 (x, z) の y0 の上下 6 で、上が空気 (か草花) の一番上の固いブロックの y。なければ MIN_VALUE
    private static int topSolid(SiteJob job, int x, int z, int y0) {
        for (int y = y0 + 6; y >= y0 - 6; y--) {
            BlockPos p = new BlockPos(x, y, z);
            var st = job.level.getBlockState(p);
            if (st.isAir() || st.getBlock() instanceof net.minecraft.world.level.block.BushBlock || !st.getFluidState().isEmpty()) {
                continue;
            }
            return st.getCollisionShape(job.level, p).isEmpty() ? Integer.MIN_VALUE : y;
        }
        return Integer.MIN_VALUE;
    }

    static void keep(SiteJob job, String name) {
        BlockPos at = job.mark(name);
        if (at != null) {
            job.entry.extra(name, at);
        }
    }

    // その場所の最初の (建った) 記録の位置 (extra を名指すならその印)。なければ null
    @Nullable
    public static BlockPos first(MinecraftServer server, String siteId, @Nullable String extra) {
        if (server == null || server.overworld() == null) {
            return null;
        }
        for (SiteData.Entry e : SiteData.get(server.overworld()).allEntries()) {
            if (e.siteId.equals(siteId) && e.built) {
                return extra == null ? e.pos : e.extra(extra);
            }
        }
        return null;
    }

    public static List<SiteData.Entry> entries(MinecraftServer server, String siteId) {
        return SiteData.get(server.overworld()).allEntries().stream().filter(e -> e.siteId.equals(siteId) && e.built).toList();
    }

    // ---------------------------------------------------------------- 灰の道の標
    static void markArrive(ServerPlayer p, ChapterSites.Site site) {
        Notice.narrate(p, "message.skyabyss.ch4g.arrive." + MARK);
        Quests.signal(p, SIGNAL_MARK);
        if (marksVisited(p.server, p, site.pos()) >= MARKS && Growth.discover(p, "ch4_marks_all")) {
            Tasks.later(40, () -> Notice.narrate(p, "message.skyabyss.ch4g.marks.last"));
        }
    }

    // その人が着いた標の数 (今着いた標を含む)
    public static int marksVisited(MinecraftServer server, ServerPlayer p, @Nullable BlockPos now) {
        int n = 0;
        for (SiteData.Entry e : entries(server, MARK)) {
            if (e.arrived.contains(p.getUUID()) || e.pos.equals(now)) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 煤の窯の跡
    static void finishKiln(SiteJob job) {
        clearFloatingPlants(job, 7);
        fillDimples(job, 9);
        keep(job, "cache");
        keep(job, "tablet");
        BlockPos tablet = job.mark("tablet");
        if (tablet != null) {
            job.set(tablet, Tablets.standingState().setValue(RelicStyle.PROP, RelicStyle.EMBER));
            Tablets.write(job.level, tablet, KILN_SIGN);
        }
    }

    static void kilnArrive(ServerPlayer p, ChapterSites.Site site) {
        Notice.narrate(p, "message.skyabyss.ch4g.arrive." + KILN);
        Quests.signal(p, SIGNAL_KILN);
        npcFlag(p, "skyabyss:storyteller", FLAG_KILN);
        Tasks.later(60, () -> Lore.unlock(p, PAGE_KILN, true));
    }

    // 窯の跡の箱: 人ごとに一度、煤鉄 4・灰麻 3。初めてなら true
    public static boolean openCache(ServerPlayer player, ServerLevel level, @Nullable BlockPos at) {
        boolean first = !SiteTreasure.claimed(player, KILN_CACHE);
        if (first) {
            SiteTreasure.claim(player, KILN_CACHE);
            Notice.narrate(player, "message.skyabyss.ch4g.kiln.cache");
        } else {
            Notice.narrate(player, "message.skyabyss.ch4g.kiln.cache_empty");
        }
        if (at != null) {
            level.playSound(null, at, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
        return first;
    }

    // range の内の窯の跡 (灰ひきずりが転がる先)。なければ null
    @Nullable
    public static BlockPos kilnNear(ServerLevel level, BlockPos from, double range) {
        BlockPos best = null;
        double bestD = range * range;
        for (SiteData.Entry e : entries(level.getServer(), KILN)) {
            double d = e.pos.distSqr(from);
            if (d <= bestD) {
                bestD = d;
                best = e.pos;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 灰降りの高台
    static void finishWatch(SiteJob job) {
        clearFloatingPlants(job, 5);
        keep(job, "tablet");
        BlockPos tablet = job.mark("tablet");
        if (tablet != null) {
            job.set(tablet, Tablets.standingState().setValue(RelicStyle.PROP, RelicStyle.EMBER));
            Tablets.write(job.level, tablet, WATCH_SIGN);
        }
    }

    static void watchArrive(ServerPlayer p, ChapterSites.Site site) {
        Notice.narrate(p, "message.skyabyss.ch4g.arrive." + WATCH);
        npcFlag(p, "skyabyss:whale_chaser", FLAG_WATCH);
        showBasin(p);
    }

    // 夜なら、灰の地の眠る場所の見当に赤い粒 (その人にだけ、遠くから見える)。出したら true
    public static boolean showBasin(ServerPlayer p) {
        long t = p.serverLevel().getDayTime() % 24000L;
        boolean night = t >= 13000 && t <= 23000;
        BlockPos c = EmberBasin.basin(p.server);
        if (!night || c == null) {
            return false;
        }
        int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ());
        p.serverLevel().sendParticles(p, ParticleTypes.LAVA, true, c.getX() + 0.5, y + 1, c.getZ() + 0.5, 30, 6, 1, 6, 0);
        p.serverLevel().sendParticles(p, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, c.getX() + 0.5, y + 2, c.getZ() + 0.5, 12, 4, 2, 4, 0.01);
        Tasks.later(40, () -> Notice.narrate(p, "message.skyabyss.ch4g.watch.night"));
        return true;
    }

    // NPC の会話の印を付ける (語り部・イサナの短い一言の入口)
    static void npcFlag(ServerPlayer p, String npc, String flag) {
        if (NpcBonds.of(p).bond(npc).flags().add(flag)) {
            NpcBonds.sync(p);
        }
    }

    // ---------------------------------------------------------------- 灰の鍛え手の一言「返しに来たか」
    @SubscribeEvent
    static void onSmithJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof AshSmith smith) || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != EmberIds.BACK) {
            return;
        }
        Tasks.later(30, () -> smithSpeaks(level, smith));
    }

    // 鍛え手のまわり (24) の、鍛え手が見える人に、一度だけ一行と伝承。言った人の数
    public static int smithSpeaks(ServerLevel level, AshSmith smith) {
        int n = 0;
        for (ServerPlayer p : jp.revati.skyabyss.util.Players.snapshot(level.players())) {
            if (p.distanceToSqr(smith) < 24 * 24 && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(smith, p) && !Lore.state(p).has(PAGE_RETURN)) {
                Notice.narrate(p, "message.skyabyss.ch4g.smith.return");
                Lore.unlock(p, PAGE_RETURN, true);
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 樽・台・岩
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        BlockPos pos = event.getPos();
        MinecraftServer server = level.getServer();
        var state = level.getBlockState(pos);
        if (state.is(Blocks.BARREL)) {
            if (DeadForgePit.withExtra(server, "pack", pos) != null) {
                event.setCanceled(true);
                DeadForgePit.openPack(player, level, pos);
                return;
            }
            for (SiteData.Entry e : entries(server, KILN)) {
                if (pos.equals(e.extra("cache"))) {
                    event.setCanceled(true);
                    openCache(player, level, pos);
                    return;
                }
            }
        } else if (state.is(Blocks.SMOOTH_BASALT)) {
            if (DeadForgePit.withExtra(server, "warm_stand", pos) != null) {
                event.setCanceled(true);
                DeadForgePit.touchStand(player, level, pos);
                return;
            }
            for (SiteData.Entry e : entries(server, ROCKS)) {
                if (pos.equals(e.extra("stone"))) {
                    event.setCanceled(true);
                    rocksAnswer(player, level, pos);
                    return;
                }
            }
        }
    }

    // 温かい岩の群れの台: 温石を持っていれば、岩が熱を返す (粒・音・一行)。返したら true
    public static boolean rocksAnswer(ServerPlayer player, ServerLevel level, BlockPos at) {
        if (player.getInventory().countItem(EmberTitanSetup.WARM_STONE.get()) <= 0) {
            Notice.narrate(player, "message.skyabyss.ch4g.pit.stand_cold");
            return false;
        }
        level.sendParticles(ParticleTypes.SMALL_FLAME, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 12, 1.5, 0.4, 1.5, 0.01);
        level.sendParticles(ParticleTypes.WHITE_ASH, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 20, 2.0, 0.8, 2.0, 0.01);
        level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4f, 0.8f);
        player.playNotifySound(EmberTitanSetup.FAR_BEAT.get(), SoundSource.AMBIENT, 0.6f, 1.2f);
        Notice.narrate(player, "message.skyabyss.ch4g.rocks.stone");
        return true;
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!registered || server.overworld() == null || server.getTickCount() % 10 != 4) {
            return;
        }
        try {
            DeadForgePit.tick(server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("[ch4 ground] tick failed", e);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        DeadForgePit.forget();
    }
}
