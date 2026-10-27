package jp.revati.skyabyss.titan.storm.chapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

// 焼けた見張り塔 (docs/design/21 の 5.1。三章の小さなダンジョン、支度の段 ch3/scorched_watch の舞台)。部品は ch3_scorched_watch (tools/gen/ch3/gen_ch3_ground.py)。
//
//   場所   跡地 (西の峰) の中心から 60〜150、高台。外 9x9 の三階: 入口の間 (立つ高さ 0)・中の間 (6)・頂の間 (12。まんなかに柱)。北東の隅の梯子で上る。
//          入口の脇に碑 (sign.skyabyss.ch3_watch)、南西の外に旅の職人の野営 (印 artisan。ArtisanSpawner の三章の立つ所)
//   敵     SiteSpawner。三章の地上の敵 (P16。Ch3Mobs):
//            入口の間  峰の雛 2 (梁から急降下。光る線 0.8 秒)        中の間  帯電毛玉 3 (固まると放電が隣へ跳ぶ)
//            頂の間    焼けた羽守 (名づき。体力 190、人数で増える。柱の雷を受けるとしばらく傷がよく通る = ScorchedWarden.struck)
//   柱     頂の間に人がいる間、15 秒に一度、柱に雷が落ちる (2 秒前に柱のまわりが鳴り、電球が光る)。柱から 3.5 以内の敵は最大体力の 1/4 を受ける
//          (羽守を柱へ誘えば雷が斬る)。柱から 2.5 以内の人は 4 を受ける (予告つき)
//   クリア 初めての人に経験値 60・宝 (銅 6・焦げ羽 4・帯電綿 4・銅線 4・雷の護り糸 1)、合図 site_clear:ch3_scorched_watch、焦げた頁 (伝承 ch3/scorched_watch。支度の段の最初の段を済ませる)。
//          再訪は 1 日ごとに頂の間の番が戻り、銅と三章の素材が出る (経験値なし)
public final class ScorchedWatch {
    public static final String SITE = "ch3_scorched_watch";
    public static final String TREASURE = "ch3_scorched_watch";
    public static final String REPEAT_TREASURE = "ch3_scorched_watch_repeat";
    public static final int XP = 60;
    public static final String SIGN = "sign.skyabyss.ch3_watch";
    public static final String ROOM_ENTRY = "entry";
    public static final String ROOM_MIDDLE = "middle";
    public static final String ROOM_TOP = "top";
    public static final String WARDEN = Ch3Mobs.SCORCHED_WARDEN;
    public static final String FLEDGLING = Ch3Mobs.PEAK_FLEDGLING;
    public static final String PUFF = Ch3Mobs.STATIC_PUFF;
    public static final float BOSS_HEALTH = jp.revati.skyabyss.titan.storm.chapter.mob.ScorchedWarden.HEALTH;
    public static final String BOSS_TAG = "skyabyss_scorched_warden";
    public static final int STRIKE_EVERY = 300;
    public static final int WARN_TICKS = 40;
    public static final double STRIKE_MOB = 3.5;
    public static final double STRIKE_PLAYER = 2.5;
    public static final float PLAYER_HIT = 4f;

    private static final Map<Long, Integer> YAW = new HashMap<>();
    // 頂の間に人がいるあいだの拍 (基点 → 次に落ちる時刻)。保存しない
    private static final Map<Long, Long> NEXT = new HashMap<>();

    private ScorchedWatch() {
    }

    static void register() {
        ChapterSites.element(SITE, Chapters.STORM, SITE).atGround().dist(60, 150).prefer(Prefer.HIGH).arriveRadius(14)
                .post(ScorchedWatch::finish)
                .onArrive((p, s) -> Notice.narrate(p, "message.skyabyss.ch3g.arrive." + SITE)).register();
        SiteTreasure.register(TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:copper_lump", 6),
                new SiteTreasure.Entry(Ch3Mobs.SCORCHED_FEATHER, 4), new SiteTreasure.Entry(Ch3Mobs.STATIC_COTTON, 4), new SiteTreasure.Entry(Ch3Mobs.COPPER_WIRE, 4),
                new SiteTreasure.Entry(Ch3Mobs.THUNDER_WARD_THREAD, 1))));
        SiteTreasure.register(REPEAT_TREASURE, SiteTreasure.Table.of(List.of(), 1, 2, List.of(new SiteTreasure.Entry("skyabyss:copper_lump", 2, 4, 3),
                new SiteTreasure.Entry("skyabyss:copper_lump", 2, 5, 3),
                new SiteTreasure.Entry(Ch3Mobs.SCORCHED_FEATHER, 2, 4, 3), new SiteTreasure.Entry(Ch3Mobs.STATIC_COTTON, 2, 4, 3), new SiteTreasure.Entry(Ch3Mobs.COPPER_WIRE, 1, 3, 2))));
        SiteSpawner.register(new SiteSpawner.Def(SITE, Chapters.STORM, pads(), XP, TREASURE, REPEAT_TREASURE, SiteSpawner.MAX_MOBS, ScorchedWatch::origins, ScorchedWatch::yawAt));
        SiteSpawner.onCleared(SITE, (p, first) -> {
            boolean fresh = !Lore.state(p).has(Ch3Ground.PAGE_WATCH);
            if (fresh) {
                Notice.narrate(p, "message.skyabyss.ch3g.watch.page");
                StormChapter.later(p.server, 40, () -> Lore.unlock(p, Ch3Ground.PAGE_WATCH, true));
            }
        });
    }

    // 湧き台 (部品の座標。立つ高さ: 入口の間 0・中の間 6・頂の間 12)。最後の部屋 (頂の間) がクリア後の再訪で戻る
    static List<SiteSpawner.Pad> pads() {
        List<SiteSpawner.Pad> pads = new ArrayList<>();
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, -2, 0, -1, List.of(new SiteSpawner.Spawn(FLEDGLING, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, 2, 0, 0, List.of(new SiteSpawner.Spawn(FLEDGLING, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_MIDDLE, -1, 6, 2, List.of(new SiteSpawner.Spawn(PUFF, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_MIDDLE, 2, 6, 0, List.of(new SiteSpawner.Spawn(PUFF, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_MIDDLE, 0, 6, -2, List.of(new SiteSpawner.Spawn(PUFF, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_TOP, -2, 12, 2, List.of(new SiteSpawner.Spawn(WARDEN, 1))));
        return pads;
    }

    static List<BlockPos> origins(MinecraftServer server) {
        List<BlockPos> list = new ArrayList<>();
        for (SiteData.Entry e : entries(server)) {
            list.add(e.pos);
            YAW.put(e.pos.asLong(), e.yaw);
        }
        return list;
    }

    static int yawAt(BlockPos origin) {
        return YAW.getOrDefault(origin.asLong(), 0);
    }

    public static List<SiteData.Entry> entries(MinecraftServer server) {
        List<SiteData.Entry> list = new ArrayList<>();
        for (SiteData.Entry e : SiteData.get(server.overworld()).allEntries()) {
            if (e.siteId.equals(SITE) && e.built) {
                list.add(e);
            }
        }
        return list;
    }

    static void finish(SiteJob job) {
        for (String name : List.of("pillar_base", "pillar_bulb", "pillar_tip", "tablet", "artisan")) {
            BlockPos at = job.mark(name);
            if (at != null) {
                job.entry.extra(name, at);
            }
        }
        BlockPos tablet = job.mark("tablet");
        if (tablet != null) {
            job.set(tablet, Tablets.standingState().setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.STORM));
            Tablets.write(job.level, tablet, SIGN);
        }
        YAW.put(job.entry.pos.asLong(), job.entry.yaw);
    }

    // 頂の間の箱 (柱の根もとのまわり 4・高さ -1〜+4)
    public static AABB topRoom(BlockPos base) {
        return new AABB(base.getX() - 4, base.getY() - 1, base.getZ() - 4, base.getX() + 5, base.getY() + 5, base.getZ() + 5);
    }

    // ---------------------------------------------------------------- 見回り (StormChapter の tick から毎 tick)
    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (SiteData.Entry e : entries(server)) {
            BlockPos base = e.extra("pillar_base");
            if (base == null || !level.isLoaded(base)) {
                continue;
            }
            AABB room = topRoom(base);
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer p : Players.snapshot(level.players())) {
                if (!p.isSpectator() && p.isAlive() && room.contains(p.position())) {
                    inside.add(p);
                }
            }
            if (now % 20 == 0) {
                crown(level, room);
            }
            long key = e.pos.asLong();
            if (inside.isEmpty()) {
                NEXT.remove(key);
                continue;
            }
            long next = NEXT.computeIfAbsent(key, k -> now + STRIKE_EVERY);
            if (now == next - WARN_TICKS) {
                warn(level, e, base, inside);
            } else if (now >= next) {
                NEXT.put(key, now + STRIKE_EVERY);
                strike(level, e, base);
            }
        }
    }

    // 頂の間の焼けた羽守に、頂の主の印を付ける (湧いた直後の一度)。体力 190 はエンティティの値 (ScorchedWarden.HEALTH。人数で増える)
    static int crown(ServerLevel level, AABB room) {
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, room, x -> x.getTags().contains(SiteSpawner.TAG) && !x.getTags().contains(BOSS_TAG))) {
            if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString().equals(WARDEN)) {
                continue;
            }
            m.addTag(BOSS_TAG);
            n++;
        }
        return n;
    }

    static void warn(ServerLevel level, SiteData.Entry e, BlockPos base, List<ServerPlayer> inside) {
        BlockPos bulb = e.extra("pillar_bulb");
        if (bulb != null) {
            LightningPillars.bulb(level, bulb, true);
        }
        level.playSound(null, base, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.8f);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, base.getX() + 0.5 + Math.cos(a) * STRIKE_MOB, base.getY() + 0.2, base.getZ() + 0.5 + Math.sin(a) * STRIKE_MOB,
                    2, 0.05, 0.1, 0.05, 0.01);
        }
        for (ServerPlayer p : inside) {
            if (Growth.discover(p, "ch3_watch_warn")) {
                Notice.narrate(p, "message.skyabyss.ch3g.watch.warn");
            }
        }
    }

    // 柱に雷。近くの敵に最大体力の 1/4、柱のすぐそばの人に 4。打った敵の数
    public static int strike(ServerLevel level, SiteData.Entry e, BlockPos base) {
        BlockPos tip = e.extra("pillar_tip");
        LightningPillars.strike(level, tip != null ? tip : base.above(3));
        BlockPos bulb = e.extra("pillar_bulb");
        if (bulb != null) {
            LightningPillars.bulb(level, bulb, true);
            StormChapter.later(level.getServer(), 40, () -> LightningPillars.bulb(level, bulb, false));
        }
        int hit = 0;
        double cx = base.getX() + 0.5;
        double cz = base.getZ() + 0.5;
        AABB box = new AABB(cx - STRIKE_MOB, base.getY() - 1, cz - STRIKE_MOB, cx + STRIKE_MOB, base.getY() + 5, cz + STRIKE_MOB);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, x -> x.isAlive() && x.getTags().contains(SiteSpawner.TAG))) {
            double dx = m.getX() - cx;
            double dz = m.getZ() - cz;
            if (dx * dx + dz * dz <= STRIKE_MOB * STRIKE_MOB) {
                // 焼けた羽守は、柱の雷を受けるとしばらく傷がよく通る (先に印を付けてから雷の傷を入れる)
                if (m instanceof jp.revati.skyabyss.titan.storm.chapter.mob.ScorchedWarden sw) {
                    sw.struck();
                }
                // 鎧を通す (羽守の鎧で 1/4 が削れないことがないように。見た目と音は雷)
                m.invulnerableTime = 0;
                m.hurt(level.damageSources().magic(), Math.max(12f, m.getMaxHealth() * 0.25f));
                hit++;
            }
        }
        for (ServerPlayer p : Players.snapshot(level.players())) {
            double dx = p.getX() - cx;
            double dz = p.getZ() - cz;
            if (!p.isSpectator() && !p.isCreative() && dx * dx + dz * dz <= STRIKE_PLAYER * STRIKE_PLAYER && box.contains(p.position())) {
                p.hurt(level.damageSources().lightningBolt(), PLAYER_HIT);
            }
        }
        return hit;
    }

    static void forget() {
        NEXT.clear();
    }
}
