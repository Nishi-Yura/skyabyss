package jp.revati.skyabyss.story.ch4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.relic.RelicStyle;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.titan.ember.EmberTitanSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// 消えた炉の坑 (四章の小さなダンジョン、支度の段 ch4/forge_ruins の舞台)。部品は ch4_dead_forge_pit (データを直接直す（生成スクリプトはない）)。
//
//   場所   四章の跡地 (都の南の灰の地) の中心から 60〜150、高台 (丘の斜面のつもり)。地表に黒石の枠の坑口と崩れた煙突、坑口の碑 (sign.skyabyss.ch4_forge_pit)、
//          南西の外に旅の職人の野営 (印 artisan。ArtisanSpawner の四章の立つ所)。階段で地下 13 へ。部屋は荒い黒石で包む
//   部屋   入口の間  熾り蟲 4。西の壁ぎわに温石の台 (玄武岩の柱。温石を持って触れると 30 秒温まり、熾り蟲は人より台へ寄る)        予習: 火の粉蛾の「いちばん熱いもの」
//          鍛えの跡  灰被りの屑 2。冷えた金床・水の釜・崩れた道具棚                                                            予習: 灰被りの殻
//          奥の間    古い灰被り (名づき)。北の壇に碑 (sign.skyabyss.ch4_forge)・前の私の荷 (樽。人ごとに一度: ブレイズパウダー 1・松明 8)・冷えた金床。
//                    東の壁の外に地上へ出る梯子の縦穴                                                                          予習: 灰の鍛え手の亀裂
//   クリア 湧かせた敵がすべて倒れる → 初めての人に経験値 80・宝 (煤鉄 4・灰麻 4・石炭 4)・合図 site_clear:ch4_dead_forge_pit。
//          冷えた金床の上の頁 (伝承 ch4/forge_page。支度の段「炉跡」の最初の段)。再訪は 1 日ごとに奥の間の主が戻り、煤鉄と灰麻 (経験値なし)
public final class DeadForgePit {
    public static final String SITE = "ch4_dead_forge_pit";
    public static final String TREASURE = "ch4_dead_forge_pit";
    public static final String REPEAT_TREASURE = "ch4_dead_forge_pit_repeat";
    public static final String PACK = "ch4_dead_forge_pack";
    public static final int XP = 80;
    public static final String SIGN = "sign.skyabyss.ch4_forge_pit";
    public static final String FORGE_SIGN = "sign.skyabyss.ch4_forge";
    public static final String ROOM_ENTRY = "entry";
    public static final String ROOM_FORGE = "forge";
    public static final String ROOM_DEEP = "deep";
    public static final int STAND_Y = -12;
    // 温石を置いた台が温まっている長さ
    public static final int WARM_TICKS = 600;

    private static final Map<Long, Integer> YAW = new HashMap<>();
    // 温まっている台 (台の位置 → 冷める時刻)。保存しない
    private static final Map<Long, Long> WARM = new HashMap<>();

    private DeadForgePit() {
    }

    static void register() {
        ChapterSites.element(SITE, Chapters.EMBER, SITE).atGround().dist(60, 150).prefer(Prefer.HIGH).arriveRadius(14)
                .post(DeadForgePit::finish)
                .onArrive((p, s) -> {
                    Notice.narrate(p, "message.skyabyss.ch4g.arrive." + SITE);
                    if ("pit".equals(stageOf(p, Ch4Ground.Q_FORGE))) {
                        Tasks.later(80, () -> Notice.narrate(p, "message.skyabyss.ch4g.pit.trail"));
                    }
                }).register();
        SiteTreasure.register(PACK, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:ember_drop", 1), new SiteTreasure.Entry("minecraft:torch", 8))));
        SiteTreasure.register(TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:soot_iron", 4), new SiteTreasure.Entry("skyabyss:ash_hemp", 4),
                new SiteTreasure.Entry("skyabyss:charcoal_lump", 4))));
        SiteTreasure.register(REPEAT_TREASURE, SiteTreasure.Table.of(List.of(), 1, 2, List.of(new SiteTreasure.Entry("skyabyss:soot_iron", 1, 3, 3),
                new SiteTreasure.Entry("skyabyss:ash_hemp", 1, 3, 3))));
        SiteSpawner.register(new SiteSpawner.Def(SITE, Chapters.EMBER, pads(), XP, TREASURE, REPEAT_TREASURE, SiteSpawner.MAX_MOBS, DeadForgePit::origins,
                DeadForgePit::yawAt));
        SiteSpawner.onCleared(SITE, (p, first) -> {
            if (!Lore.state(p).has(Ch4Ground.PAGE_FORGE)) {
                Notice.narrate(p, "message.skyabyss.ch4g.pit.page");
                Tasks.later(40, () -> Lore.unlock(p, Ch4Ground.PAGE_FORGE, true));
            }
        });
    }

    @Nullable
    static String stageOf(ServerPlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        if (e == null || e.done) {
            return null;
        }
        var def = jp.revati.skyabyss.quest.QuestManager.quest(quest);
        return def == null || e.stage >= def.stages().size() ? null : def.stages().get(e.stage).id();
    }

    // 湧き台 (部品の座標。立つ高さ -12)。最後の部屋 (奥の間) がクリア後の再訪で戻る
    public static List<SiteSpawner.Pad> pads() {
        List<SiteSpawner.Pad> pads = new ArrayList<>();
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, -2, STAND_Y, 16, List.of(new SiteSpawner.Spawn(Ch4Mobs.CINDER_MITE, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, 2, STAND_Y, 18, List.of(new SiteSpawner.Spawn(Ch4Mobs.CINDER_MITE, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, -1, STAND_Y, 21, List.of(new SiteSpawner.Spawn(Ch4Mobs.CINDER_MITE, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, 3, STAND_Y, 22, List.of(new SiteSpawner.Spawn(Ch4Mobs.CINDER_MITE, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_FORGE, -17, STAND_Y, 15, List.of(new SiteSpawner.Spawn(Ch4Mobs.ASH_HUSK, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_FORGE, -14, STAND_Y, 21, List.of(new SiteSpawner.Spawn(Ch4Mobs.ASH_HUSK, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_DEEP, -14, STAND_Y, -3, List.of(new SiteSpawner.Spawn(Ch4Mobs.ELDER_THRALL, 1))));
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
        Ch4Ground.clearFloatingPlants(job, 9);
        for (String name : List.of("tablet", "forge_tablet", "pack", "anvil", "warm_stand", "artisan", "exit")) {
            BlockPos at = job.mark(name);
            if (at != null) {
                job.entry.extra(name, at);
            }
        }
        for (String[] t : new String[][] {{"tablet", SIGN}, {"forge_tablet", FORGE_SIGN}}) {
            BlockPos at = job.mark(t[0]);
            if (at != null) {
                job.set(at, Tablets.standingState().setValue(RelicStyle.PROP, RelicStyle.EMBER));
                Tablets.write(job.level, at, t[1]);
            }
        }
        YAW.put(job.entry.pos.asLong(), job.entry.yaw);
    }

    @Nullable
    static SiteData.Entry withExtra(MinecraftServer server, String name, BlockPos pos) {
        for (SiteData.Entry e : entries(server)) {
            if (pos.equals(e.extra(name))) {
                return e;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 前の私の荷
    // 人ごとに一度 (ブレイズパウダー 1・松明 8)。初めてなら true
    public static boolean openPack(ServerPlayer player, ServerLevel level, @Nullable BlockPos at) {
        boolean first = !SiteTreasure.claimed(player, PACK);
        if (first) {
            SiteTreasure.claim(player, PACK);
            Notice.narrate(player, "message.skyabyss.ch4g.pit.pack");
        } else {
            Notice.narrate(player, "message.skyabyss.ch4g.pit.pack_empty");
        }
        if (at != null) {
            level.playSound(null, at, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8f, 0.7f);
        }
        return first;
    }

    // ---------------------------------------------------------------- 温石の台
    // 温石を持った人が台に触れると、台が WARM_TICKS 温まる (温石は減らない)。温めたら true
    public static boolean touchStand(ServerPlayer player, ServerLevel level, BlockPos at) {
        if (player.getInventory().countItem(EmberTitanSetup.WARM_STONE.get()) <= 0) {
            Notice.narrate(player, "message.skyabyss.ch4g.pit.stand_cold");
            return false;
        }
        warm(level, at);
        Notice.narrate(player, "message.skyabyss.ch4g.pit.stand_warm");
        return true;
    }

    public static void warm(ServerLevel level, BlockPos at) {
        WARM.put(at.asLong(), level.getGameTime() + WARM_TICKS);
        level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 1.4f);
    }

    public static boolean warmNow(ServerLevel level, BlockPos at) {
        Long until = WARM.get(at.asLong());
        return until != null && until > level.getGameTime();
    }

    // range の内の温まっている台 (なければ null)。熾り蟲が寄る先
    @Nullable
    public static BlockPos warmStandNear(ServerLevel level, BlockPos from, double range) {
        if (WARM.isEmpty()) {
            return null;
        }
        long now = level.getGameTime();
        for (Map.Entry<Long, Long> e : WARM.entrySet()) {
            if (e.getValue() <= now) {
                continue;
            }
            BlockPos at = BlockPos.of(e.getKey());
            if (at.distSqr(from) <= range * range) {
                return at;
            }
        }
        return null;
    }

    // 見回り (Ch4Ground の tick から 10 tick ごと): 温まった台の粒、冷めた台を忘れる
    static void tick(MinecraftServer server) {
        if (WARM.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        WARM.entrySet().removeIf(e -> e.getValue() <= now);
        for (long key : WARM.keySet()) {
            BlockPos at = BlockPos.of(key);
            if (level.isLoaded(at)) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
                level.sendParticles(ParticleTypes.WHITE_ASH, at.getX() + 0.5, at.getY() + 1.4, at.getZ() + 0.5, 3, 0.4, 0.3, 0.4, 0.01);
            }
        }
    }

    static void forget() {
        WARM.clear();
    }
}
