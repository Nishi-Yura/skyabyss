package jp.revati.skyabyss.titan.storm.chapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.ChapterXpBudget;
import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteProgressData;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.ArtisanSpawner;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestItems;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.heart.StormHeartArena;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.raid.InstanceArenas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 三章の地上 (docs/design/34 の C3) の自己テスト。SKYABYSS_SELFTEST_ONLY="ch3 ground"
//   ch3 ground            データ (支度の段 3 本とサブ・会話・伝承・文・経験値の枠・湧き台・職人の立つ所)、三本の流れ (空の石・焼けた見張り塔・避雷の柱の合図)、
//                         合図 ch3_ready、イサナの決意で温石、旗の向き、眼の間の鏡の面 (折り返し・距離・眼の間の外は映さない・偽の人には出さない)
//   ch3 ground (sites)    本物の世界で西の峰の跡地に建ったもの: 塔の跡の台 4・戸 9・石の箱と碑・見本の柱、針を立てると数えて戸が開き書と銅、
//                         焼けた見張り塔の碑・柱・湧き台の立ち位置・柱の雷が敵を削る・焼けた羽守の体力と名前・クリア、旗 5 の向きが一斉に逆、原の箱、崖の遠眼鏡
public final class Ch3GroundSelfTest {
    private static final UUID SITE_ID = UUID.nameUUIDFromBytes("skyabyss_ch3_ground_sites".getBytes());
    private static int siteWait;
    private static int laterWait;
    private static FakePlayer watchPlayer;

    private Ch3GroundSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("ch3 ground", Ch3GroundSelfTest::run));
        steps.add(Step.once("ch3 ground (sites start)", Ch3GroundSelfTest::sitesStart));
        steps.add(Step.until("ch3 ground (sites wait)", Ch3GroundSelfTest::sitesBuilt));
        steps.add(Step.until("ch3 ground (sites load)", Ch3GroundSelfTest::sitesLoaded));
        steps.add(Step.once("ch3 ground (sites)", Ch3GroundSelfTest::sitesCheck));
        steps.add(Step.until("ch3 ground (sites later)", Ch3GroundSelfTest::later));
        return steps;
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(MinecraftServer server, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_ch3g_" + suffix).getBytes());
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(id, "skyabyss_ch3g_" + suffix));
        Growth.get(p).level = 30;
        return p;
    }

    private static void clean(FakePlayer p) {
        Quests.forgetAll(p);
        Growth.get(p).discoveries.clear();
        p.getInventory().clearContent();
        for (String id : Ch3Ground.LORE) {
            Lore.lock(p, id);
        }
        SiteProgressData.get(p.server).forgetTreasure(p.getUUID(), LightningPillars.VAULT_TREASURE);
        SiteProgressData.get(p.server).forgetTreasure(p.getUUID(), Ch3Ground.FIELD_CACHE);
        jp.revati.skyabyss.npc.NpcBonds.of(p).bond("skyabyss:whale_chaser").flags().clear();
    }

    private static void report(String name, List<String> f, String passed) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST {} PASSED ({})", name, passed);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST {} FAILED: {}", name, m));
        }
    }

    // ---------------------------------------------------------------- データと流れ
    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer p = fake(server, "trail");
        try {
            data(f);
            trails(server, p, f);
            mirror(server, f);
            flags(f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch3 ground crashed", e);
            f.add("crashed: " + e);
        } finally {
            clean(p);
        }
        report("ch3 ground", f, "3 trail steps + wind flags quest, dialogue, lore, ja/en text, xp budget, trail flow and ch3_ready, warm stone, mirror plane, flag bearings");
    }

    private static void data(List<String> f) {
        check(f, PrepQuests.defined(3) && PrepQuests.ids(3).equals(Ch3Ground.TRAILS) && PrepQuests.isTrail(3),
                "the three chapter-3 trail steps are not all defined (" + QuestManager.loadErrors() + ")");
        for (String id : Ch3Ground.TRAILS) {
            QuestDef def = QuestManager.quest(id);
            check(f, def != null && def.main() && def.startWhen() != null && !def.repeats() && !def.board() && def.chapter().equals(Chapters.STORM.id()),
                    id + ": needs to be a main quest with start_when, chapter titan_storm, no repeat or board");
        }
        QuestDef flags = QuestManager.quest(Ch3Ground.Q_FLAGS);
        check(f, flags != null && !flags.main() && flags.chapter().equals(Chapters.STORM.id()), "ch3/wind_flags should be a chapter-3 side quest");
        check(f, QuestManager.loadErrors().stream().noneMatch(s -> s.contains("ch3/")), "chapter-3 quests were flagged: " + QuestManager.loadErrors());
        check(f, DialogueManager.loadErrors().stream().noneMatch(s -> s.contains("ch3g")), "chapter-3 ground dialogue was flagged: " + DialogueManager.loadErrors());
        for (String[] n : new String[][] {{"skyabyss:storyteller", "ch3g_pillars_tale"}, {"skyabyss:storyteller", "ch3g_s_verse"},
                {"skyabyss:whale_chaser", "ch3g_stone_offer"}, {"skyabyss:whale_chaser", "ch3g_c_stay"}, {"skyabyss:whale_chaser", "ch3g_c_stay2"},
                {"skyabyss:artisan", "ch3g_watch_tell"}, {"skyabyss:artisan", "ch3g_watch_show"}}) {
            var d = DialogueManager.dialogue(n[0]);
            check(f, d != null && d.nodes().containsKey(n[1]), n[0] + " has no node " + n[1]);
        }
        // 文 (日英)
        List<String> keys = new ArrayList<>(List.of("quest.skyabyss.ch3.lightning_pillars.raise.rods", "quest.skyabyss.ch3.lightning_pillars.vault.page",
                "quest.skyabyss.ch3.scorched_watch.clear.page", "quest.skyabyss.ch3.scorched_watch.show.talk", "quest.skyabyss.ch3.sky_stone.dig.diamonds",
                "quest.skyabyss.ch3.wind_flags.trace.flags", "message.skyabyss.ch3g.ruin.open", "message.skyabyss.ch3g.mirror.2",
                "message.skyabyss.ch3g.flags.turn", "message.skyabyss.ch3g.arrive.ch3_scorched_watch", "entity.skyabyss.scorched_warden",
                "dialogue.skyabyss.whale_chaser.ch3g_c_stay", "dialogue.skyabyss.storyteller.ch3g_s_verse", "dialogue.skyabyss.artisan.ch3g_watch_show",
                "advancement.skyabyss.storm_trail.title", "advancement.skyabyss.scorched_watch.description"));
        for (String id : Ch3Ground.LORE) {
            keys.add("lore.skyabyss." + id.replace('/', '.') + ".title");
            keys.add("lore.skyabyss." + id.replace('/', '.') + ".body");
        }
        for (String sign : List.of(ScorchedWatch.SIGN, LightningPillars.VAULT_SIGN, Ch3Ground.CLIFF_SIGN)) {
            for (int i = 1; i <= 4; i++) {
                keys.add(sign + "." + i);
            }
        }
        for (String key : keys) {
            check(f, SignFit.lang("ja_jp").containsKey(key) && SignFit.lang("en_us").containsKey(key), "missing ja/en text " + key);
        }
        // 経験値の枠 (三章の新しい分 900・合計 1,700)
        int fresh = ChapterXpBudget.newXp(QuestManager.all(), 3);
        check(f, fresh >= 570 && fresh <= ChapterXpBudget.NEW_CAP[3], "chapter 3 new xp " + fresh + " should count the trail steps, the flags and the watch, within " + ChapterXpBudget.NEW_CAP[3]);
        check(f, ChapterXpBudget.overruns(QuestManager.all(), 3).isEmpty(), "chapter 3 is over its xp budget: " + ChapterXpBudget.overruns(QuestManager.all(), 3));
        // 湧き台・職人の立つ所・部品の印
        SiteSpawner.Def def = SiteSpawner.def(ScorchedWatch.SITE);
        check(f, def != null && def.xp() == ScorchedWatch.XP && def.chapter() == Chapters.STORM && def.lastRoom().equals(ScorchedWatch.ROOM_TOP) && def.pads().size() == 6,
                "the scorched watch spawner should be chapter 3, 60 xp, six pads with the top room last");
        check(f, ArtisanSpawner.spots().stream().anyMatch(s -> s.chapter() == 3 && s.siteId().equals(Ch3Ground.WATCH) && s.mark().equals("artisan")),
                "the artisan has no chapter-3 spot at the scorched watch");
        for (String[] bm : new String[][] {{LightningPillars.BLUEPRINT, "pedestal3"}, {LightningPillars.BLUEPRINT, "door8"}, {LightningPillars.BLUEPRINT, "vault_pack"},
                {LightningPillars.BLUEPRINT, "sample_tip"}, {ScorchedWatch.SITE, "pillar_tip"}, {ScorchedWatch.SITE, "artisan"}, {ScorchedWatch.SITE, "tablet"},
                {Ch3Ground.FLAG, "banner"}, {Ch3Ground.FIELD, "cache"}, {Ch3Ground.CLIFF, "spyglass"}}) {
            check(f, Blueprint.get(bm[0]).marks.containsKey(bm[1]), bm[0] + " needs mark " + bm[1]);
        }
        for (String e : Ch3Ground.ELEMENTS) {
            ChapterSites.Element el = ChapterSites.element(e);
            check(f, el != null && el.atGround() && el.rules().maxDist() <= 150, e + " should be placed on the chapter ground within 150");
        }
        ChapterSites.Element ruin = ChapterSites.element(StormSites.RUIN);
        check(f, ruin != null && ruin.blueprint().equals(LightningPillars.BLUEPRINT) && ruin.post() != null, "the ruin should be built from ch3_rod_tower with a finish");
    }

    private static void trails(MinecraftServer server, FakePlayer p, List<String> f) {
        ServerLevel level = server.overworld();
        clean(p);
        for (String id : Ch3Ground.TRAILS) {
            Quests.start(p, id);
            check(f, QuestLog.of(p).entry(id) != null, id + " did not start");
        }
        // 空の石: ダイヤ 4 では済まない、5 で済む (胸当てか剣でも)
        p.getInventory().add(new ItemStack(Items.DIAMOND, 4));
        Quests.poll(p, true);
        check(f, !QuestLog.of(p).entry(Ch3Ground.Q_STONE).done, "four diamonds finished the sky stone");
        p.getInventory().add(new ItemStack(Items.DIAMOND, 1));
        Quests.poll(p, true);
        check(f, QuestLog.of(p).entry(Ch3Ground.Q_STONE).done, "five diamonds did not finish the sky stone");
        Quests.forget(p, Ch3Ground.Q_STONE);
        p.getInventory().clearContent();
        Quests.start(p, Ch3Ground.Q_STONE);
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        Quests.poll(p, true);
        check(f, QuestLog.of(p).entry(Ch3Ground.Q_STONE).done, "a worn diamond chestplate did not finish the sky stone");
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);

        // 焼けた見張り塔: 頂の頁 → 職人に見せる
        Lore.unlock(p, Ch3Ground.PAGE_WATCH, false);
        Quests.poll(p, true);
        check(f, "show".equals(LightningPillars.stageOf(p, Ch3Ground.Q_WATCH)), "the scorched page did not move the watch quest to the show stage");
        Quests.onTalk(p, "skyabyss:artisan", "ch3g_watch_show");
        check(f, QuestLog.of(p).entry(Ch3Ground.Q_WATCH).done, "showing the page to the artisan did not finish the watch quest");

        // 避雷の柱: 台の合図 4 (人ごとに台一つにつき一度) → 書
        check(f, "raise".equals(LightningPillars.stageOf(p, Ch3Ground.Q_PILLARS)), "the pillars quest should start at raise");
        for (int k = 0; k < 3; k++) {
            if (Growth.discover(p, "ch3_pillar:" + k)) {
                Quests.signal(p, Ch3Ground.SIGNAL_PILLAR);
            }
        }
        check(f, !Growth.discover(p, "ch3_pillar:0"), "a stand counted twice");
        check(f, QuestLog.of(p).entry(Ch3Ground.Q_PILLARS).progress(0) == 3 && "raise".equals(LightningPillars.stageOf(p, Ch3Ground.Q_PILLARS)),
                "three rods should read 3/4 (" + QuestLog.of(p).entry(Ch3Ground.Q_PILLARS).progress(0) + ")");
        Growth.discover(p, "ch3_pillar:3");
        Quests.signal(p, Ch3Ground.SIGNAL_PILLAR);
        check(f, "vault".equals(LightningPillars.stageOf(p, Ch3Ground.Q_PILLARS)), "four rods did not open the vault stage");
        check(f, LightningPillars.openVault(p, level, null) && !LightningPillars.openVault(p, level, null), "the vault gave its copper twice or never");
        check(f, QuestItems.count(p, "minecraft:copper_ingot", null) == 4 && Lore.state(p).has(Ch3Ground.PAGE_STAYER), "the vault should hand over four copper and the page");
        Quests.poll(p, true);
        check(f, QuestLog.of(p).entry(Ch3Ground.Q_PILLARS).done, "the page did not finish the pillars quest");
        check(f, PrepQuests.ready(p, 3) && Growth.get(p).discoveries.contains("prep_ready:3"), "the three chapter-3 steps did not raise ch3_ready");

        // イサナの決意: 温石を渡す
        var chaser = DialogueManager.dialogue("skyabyss:whale_chaser");
        if (chaser != null && chaser.nodes().containsKey("ch3g_c_stay2")) {
            StoryContext ctx = StoryContext.of(p);
            chaser.nodes().get("ch3g_c_stay2").actions().forEach(a -> a.run(ctx));
            check(f, QuestItems.count(p, "skyabyss:warm_stone", null) == 1, "Isana's resolve did not hand over the warm stone");
        }
    }

    private static void mirror(MinecraftServer server, List<String> f) {
        // 面の計算 (純粋): 入口の鏡の壁の羽鏡 (中心から北へ 6、東西 3 つずつ) で、着いた所の人を折り返す
        BlockPos cell = StormHeartArena.mirrorWall().get(3);
        double cx = cell.getX() + 0.5;
        double cz = cell.getZ() + 0.5;
        Vec3 landing = Vec3.atBottomCenterOf(InstanceArenas.heartEntry(Titans.THUNDER_WING));
        MirrorLag.Plane north = MirrorLag.planeAt(cell, landing);
        check(f, north != null && north.axis() == 1 && Math.abs(north.reflect(landing).z - (2 * cz - landing.z)) < 1e-6 && StormHeartArena.inRoom(north.reflect(landing)),
                "a player landing at the heart entry should be folded across the mirror's z plane, inside the round room");
        check(f, MirrorLag.planeAt(cell, new Vec3(cx + 1.5, cell.getY(), cz)) == null, "too close to a mirror should show nothing");
        check(f, MirrorLag.planeAt(cell, new Vec3(cx + 12, cell.getY(), cz)) == null, "too far from a mirror should show nothing");
        check(f, MirrorLag.planeAt(cell, new Vec3(cx, cell.getY(), cz + 14)) == null, "a reflection outside the round room should be refused");
        check(f, Math.abs(new MirrorLag.Plane(0, 0).reflectYaw(30f) + 30f) < 1e-4 && Math.abs(new MirrorLag.Plane(1, 0).reflectYaw(30f) - 150f) < 1e-4,
                "reflected facing is wrong");
        FakePlayer me = fake(server, "mirror");
        check(f, !MirrorLag.eligible(me), "a fake player in the overworld should never see the shadow");
        check(f, MirrorLag.DELAY >= 10 && MirrorLag.DELAY <= 40 && MirrorLag.DURATION >= 100, "the lag should be about a second and last several seconds");
        // 建てたあとの部屋: 鏡の壁がある・着く所から面が決まる・順路の人だけ・伝承が記される
        ServerLevel back = server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            SkyAbyss.LOGGER.warn("SELFTEST ch3 ground: no storm back level, mirror wall build checks skipped");
            return;
        }
        InstanceArenas.ensureHeart(back, Titans.THUNDER_WING);
        for (BlockPos m : StormHeartArena.mirrorWall()) {
            check(f, back.getBlockState(m).is(StormBackSetup.MIRROR.get()), "no feather mirror at " + m);
            check(f, back.getBlockState(m.above()).isAir() && !back.getBlockState(m.below()).isAir(), "the mirror at " + m + " should stand on the floor with air above");
        }
        check(f, StormHeartArena.isBuilt(back), "the keel chamber should count as built with its mirror wall");
        check(f, MirrorLag.planeFor(back, landing) != null, "the built arena should give the landing a mirror plane");
        UUID group = Groups.keyOf(me);
        BackGroupData.Record rec = BackGroupData.get(back).of(group);
        try {
            check(f, !MirrorLag.onStormRoute(back, group, me.getUUID()), "a player off the route should not trigger the shadow");
            rec.putInt(TrialRoute.STEP, TrialRoute.LAST_STEP);
            rec.putString(TrialRoute.PARTY, me.getUUID().toString());
            check(f, MirrorLag.onStormRoute(back, group, me.getUUID()), "a route participant at the heart step should trigger the shadow");
            rec.putInt(TrialRoute.STEP, 2);
            check(f, !MirrorLag.onStormRoute(back, group, me.getUUID()), "a participant before the heart step should not");
        } finally {
            TrialRoute.reset(rec);
        }
        check(f, !Lore.state(me).has(Ch3Ground.PAGE_MIRROR), "a fresh player should not have the mirror page yet");
        MirrorLag.record(me);
        check(f, Lore.state(me).has(Ch3Ground.PAGE_MIRROR), "the shadow should record ch3/mirror_lag");
    }

    private static void flags(List<String> f) {
        for (long day = 0; day < 8; day++) {
            double a = StormVane.windAngle(day);
            int r = Ch3Ground.downwind(day);
            double yaw = Math.toRadians(r * 22.5);
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            check(f, fx * Math.cos(a) + fz * Math.sin(a) > 0.9, "day " + day + ": the flag should face downwind (" + r + ")");
        }
    }

    // ---------------------------------------------------------------- 本物の世界
    private static FakePlayer siteFake(MinecraftServer server) {
        return FakePlayerFactory.get(server.overworld(), new GameProfile(SITE_ID, "skyabyss_ch3g_sites"));
    }

    private static void sitesStart(MinecraftServer server) {
        siteWait = 0;
        FakePlayer p = siteFake(server);
        BlockPos base = server.overworld().getSharedSpawnPos();
        p.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        ChapterSites.open(p, Chapters.STORM);
    }

    private static List<ChapterSites.Site> mine(MinecraftServer server) {
        List<ChapterSites.Site> out = new ArrayList<>();
        for (ChapterSites.Element e : ChapterSites.elements(Chapters.STORM)) {
            out.addAll(ChapterSites.sitesFor(server, SITE_ID, e.id()));
        }
        return out;
    }

    private static boolean sitesBuilt(MinecraftServer server) {
        siteWait++;
        List<ChapterSites.Site> sites = mine(server);
        return siteWait > 9000 || (siteWait > 100 && !sites.isEmpty() && sites.stream().allMatch(ChapterSites.Site::built));
    }

    // 跡地は拠点から遠い (偽の人はチャンクを読まない)。三章の場所のまわりを読み込んで待つ
    private static final java.util.Set<Long> FORCED = new java.util.HashSet<>();

    private static boolean sitesLoaded(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (FORCED.isEmpty()) {
            for (SiteData.Entry e : SiteData.get(level).allEntries()) {
                if (!e.built || ChapterSites.element(e.siteId) == null || ChapterSites.element(e.siteId).chapter() != Chapters.STORM) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long key = net.minecraft.world.level.ChunkPos.asLong((e.pos.getX() >> 4) + dx, (e.pos.getZ() >> 4) + dz);
                        if (FORCED.add(key)) {
                            level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), true);
                        }
                    }
                }
            }
            loadWait = 0;
        }
        loadWait++;
        boolean all = true;
        for (long key : FORCED) {
            if (!level.hasChunk(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key))) {
                all = false;
            }
        }
        return (all && loadWait > 40) || loadWait > 1200;
    }

    private static int loadWait;

    private static void unforce(ServerLevel level) {
        for (long key : FORCED) {
            level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), false);
        }
        FORCED.clear();
    }

    private static boolean tabletSays(ServerLevel level, BlockPos at, String key) {
        return at != null && level.getBlockEntity(at) instanceof TabletBlockEntity t && t.key().equals(key);
    }

    static void sitesCheck(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = fake(server, "sites");
        try {
            clean(p);
            List<ChapterSites.Site> sites = mine(server);
            check(f, sites.stream().allMatch(ChapterSites.Site::built), "not every storm element was built (" + sites.size() + ")");
            ruin(level, p, f);
            watch(server, level, f);
            other(level, p, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch3 ground (sites) crashed", e);
            f.add("crashed: " + e);
        } finally {
            clean(p);
        }
        report("ch3 ground (sites)", f, "ruin stands, door, vault and sample pillar; watch tablet, pillar, pads, strike, crown, clear; flags turn; field cache; cliff spyglass");
    }

    private static void ruin(ServerLevel level, FakePlayer p, List<String> f) {
        SiteData.Entry ruin = null;
        for (SiteData.Entry e : LightningPillars.entries(level.getServer())) {
            if (LightningPillars.modern(e)) {
                ruin = e;
            }
        }
        check(f, ruin != null, "no ruin was built from ch3_rod_tower");
        if (ruin == null) {
            return;
        }
        check(f, tabletSays(level, ruin.extra("vault_tablet"), LightningPillars.VAULT_SIGN), "the vault tablet does not carry its text");
        check(f, level.getBlockState(ruin.extra("vault_pack")).is(Blocks.BARREL), "the stone box (barrel) is missing");
        check(f, level.getBlockState(ruin.extra("sample_bulb")).is(jp.revati.skyabyss.titan.storm.back.StormBackSetup.ROD_BULB.get()), "the sample pillar has no bulb");
        for (int k = 0; k < LightningPillars.STANDS; k++) {
            BlockPos at = LightningPillars.stand(ruin, k);
            check(f, at != null && level.getBlockState(at.below()).is(Blocks.WAXED_CHISELED_COPPER), "stand " + k + " has no copper plinth under " + at);
        }
        // 針を外して数える → 3 本では戸は開かず 3/4、4 本で戸が開き書へ
        for (int k = 0; k < LightningPillars.STANDS; k++) {
            level.setBlock(LightningPillars.stand(ruin, k), Blocks.AIR.defaultBlockState(), 3);
        }
        boolean wasOpen = LightningPillars.doorOpen(level, ruin);
        if (!wasOpen) {
            int cells = 0;
            for (int k = 0; k < LightningPillars.DOOR_CELLS; k++) {
                if (level.getBlockState(ruin.extra("door" + k)).is(Blocks.WAXED_OXIDIZED_CUT_COPPER)) {
                    cells++;
                }
            }
            check(f, cells == LightningPillars.DOOR_CELLS, "the copper door should be nine cells (" + cells + ")");
        }
        Quests.start(p, Ch3Ground.Q_PILLARS);
        BlockState rod = Blocks.LIGHTNING_ROD.defaultBlockState();
        for (int k = 0; k < 3; k++) {
            level.setBlock(LightningPillars.stand(ruin, k), rod, 3);
        }
        int m = LightningPillars.mask(level, ruin);
        check(f, m == 7, "three raised rods should read mask 7 (" + m + ")");
        LightningPillars.credit(p, level, ruin, m);
        LightningPillars.credit(p, level, ruin, m);
        check(f, QuestLog.of(p).entry(Ch3Ground.Q_PILLARS).progress(0) == 3, "three stands should count once each (" + QuestLog.of(p).entry(Ch3Ground.Q_PILLARS).progress(0) + ")");
        check(f, wasOpen || !LightningPillars.doorOpen(level, ruin), "the door opened before the fourth rod");
        level.setBlock(LightningPillars.stand(ruin, 3), rod, 3);
        m = LightningPillars.mask(level, ruin);
        check(f, m == 15, "four rods should read mask 15");
        if (!wasOpen) {
            check(f, LightningPillars.openDoor(level, ruin) && LightningPillars.doorOpen(level, ruin) && !LightningPillars.openDoor(level, ruin), "the door did not open once");
        }
        LightningPillars.credit(p, level, ruin, m);
        check(f, "vault".equals(LightningPillars.stageOf(p, Ch3Ground.Q_PILLARS)) && Lore.state(p).has(Ch3Ground.PAGE_PILLARS), "four stands did not move to the vault stage with the pillars page");
        check(f, LightningPillars.openVault(p, level, ruin.extra("vault_pack")), "the vault did not open");
        Quests.poll(p, true);
        check(f, QuestLog.of(p).entry(Ch3Ground.Q_PILLARS).done, "the vault page did not finish the pillars quest");
        // 見本の柱の電球
        BlockPos bulb = ruin.extra("sample_bulb");
        LightningPillars.bulb(level, bulb, true);
        check(f, level.getBlockState(bulb).getValue(jp.revati.skyabyss.titan.storm.back.StormBlocks.RodBulb.LIT), "the sample bulb did not light");
        LightningPillars.bulb(level, bulb, false);
    }

    private static void watch(MinecraftServer server, ServerLevel level, List<String> f) {
        List<SiteData.Entry> watches = ScorchedWatch.entries(server);
        check(f, watches.size() == 1, "the scorched watch should be built once (" + watches.size() + ")");
        if (watches.isEmpty()) {
            return;
        }
        SiteData.Entry w = watches.get(0);
        check(f, tabletSays(level, w.extra("tablet"), ScorchedWatch.SIGN), "the watch tablet does not carry its text");
        BlockPos base = w.extra("pillar_base");
        check(f, base != null && level.getBlockState(base).is(jp.revati.skyabyss.titan.storm.back.StormBackSetup.ROD_BASE.get()), "the top pillar is missing");
        check(f, w.extra("artisan") != null && !ArtisanSpawner.marks(level, ArtisanSpawner.spots().stream().filter(s -> s.chapter() == 3).findFirst().orElseThrow()).isEmpty(),
                "the artisan's camp mark was not kept");
        SiteSpawner.Def def = SiteSpawner.def(ScorchedWatch.SITE);
        ScorchedWatch.origins(server);
        for (SiteSpawner.Pad pad : def.pads()) {
            BlockPos at = def.padAt(pad, w.pos);
            check(f, level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && !level.getBlockState(at.below()).isAir(),
                    "watch pad " + pad.room() + " at " + at + " is not standing room on a floor (" + level.getBlockState(at) + " / below " + level.getBlockState(at.below()) + ")");
        }
        if (base == null) {
            return;
        }
        // 焼けた羽守 (体力 190): 頂の間で頂の主の印。柱の雷が 1/4 より多く削る (雷のあとは傷がよく通る)
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(ScorchedWatch.WARDEN));
        Entity e = type.create(level);
        if (e instanceof Mob mob) {
            mob.moveTo(base.getX() + 1.5, base.getY(), base.getZ() + 1.5, 0, 0);
            mob.addTag(jp.revati.skyabyss.entity.GuardMob.TEMP_TAG);
            level.addFreshEntity(mob);
            // 場所の印は入れたあとに付ける (印つきで入ってくる個体は、保存から戻ったものとして SiteSpawner が消す)
            mob.addTag(SiteSpawner.TAG);
            check(f, ScorchedWatch.crown(level, ScorchedWatch.topRoom(base)) >= 1 && Math.abs(mob.getMaxHealth() - ScorchedWatch.BOSS_HEALTH) < 0.1f
                    && mob.getTags().contains(ScorchedWatch.BOSS_TAG), "the top-room scorched warden was not marked as the top's master");
            float before = mob.getHealth();
            mob.invulnerableTime = 0;
            int hit = ScorchedWatch.strike(level, w, base);
            check(f, hit >= 1 && mob.getHealth() <= before - ScorchedWatch.BOSS_HEALTH * 0.24f, "the pillar strike did not cut the warden (" + before + " -> " + mob.getHealth() + ")");
            mob.discard();
        } else {
            f.add("could not create the warden");
        }
        // クリア: 一人のグループの分だけ湧き、倒しきるとクリア (経験値・銅)。頁は少しあと (later)
        FakePlayer a = fake(server, "watch");
        watchPlayer = a;
        SiteProgressData progress = SiteProgressData.get(server);
        progress.forget(a.getUUID(), ScorchedWatch.SITE);
        progress.forgetTreasure(a.getUUID(), ScorchedWatch.TREASURE);
        Lore.lock(a, Ch3Ground.PAGE_WATCH);
        a.getInventory().clearContent();
        a.setPos(w.pos.getX() + 0.5, w.pos.getY(), w.pos.getZ() + 0.5);
        long now = level.getGameTime();
        int total = def.pads().stream().mapToInt(x -> x.spawns().stream().mapToInt(SiteSpawner.Spawn::count).sum()).sum();
        SiteSpawner.tick(level, List.of(a), now);
        UUID ga = Groups.keyOf(a);
        int live = SiteSpawner.liveCount(ScorchedWatch.SITE, w.pos, ga) + SiteSpawner.pendingCount(ScorchedWatch.SITE, w.pos, ga);
        check(f, live == total, "the watch should hold " + total + " enemies for the group (" + live + ")");
        for (UUID id : SiteSpawner.liveIds(ScorchedWatch.SITE, w.pos, ga)) {
            Entity x = level.getEntity(id);
            if (x != null) {
                x.discard();
            }
        }
        SiteSpawner.tick(level, List.of(a), now + 40);
        check(f, progress.cleared(a.getUUID(), ScorchedWatch.SITE), "clearing the watch was not recorded");
        check(f, QuestItems.count(a, "minecraft:copper_ingot", null) >= 6, "the first watch clear should hand over copper");
        laterWait = 0;
    }

    private static void other(ServerLevel level, FakePlayer p, List<String> f) {
        int flags = 0;
        int turned = 0;
        BlockPos first = null;
        int before = -1;
        for (SiteData.Entry e : SiteData.get(level).allEntries()) {
            if (e.siteId.equals(Ch3Ground.FLAG) && e.built && e.extra("banner") != null) {
                flags++;
                if (first == null) {
                    first = e.extra("banner");
                    BlockState s = level.getBlockState(first);
                    before = s.getBlock() instanceof BannerBlock ? s.getValue(BannerBlock.ROTATION) : -1;
                }
            }
        }
        check(f, flags == Ch3Ground.FLAGS && before >= 0, "five flags with banners should stand (" + flags + ", first banner " + before + ")");
        if (first != null && before >= 0) {
            turned = Ch3Ground.turnFlags(level);
            BlockState s = level.getBlockState(first);
            check(f, turned == Ch3Ground.FLAGS && s.getValue(BannerBlock.ROTATION) == ((before + 8) & 15), "the flags did not all turn to face the other way");
            Ch3Ground.turnFlags(level);
        }
        for (SiteData.Entry e : SiteData.get(level).allEntries()) {
            if (e.siteId.equals(Ch3Ground.FIELD) && e.built) {
                BlockPos cache = e.extra("cache");
                check(f, cache != null && level.getBlockState(cache).is(Blocks.BARREL), "the gleaner field has no cache barrel");
                check(f, Ch3Ground.openCache(p, level, cache) && !Ch3Ground.openCache(p, level, cache) && QuestItems.count(p, "minecraft:raw_copper", null) == 8,
                        "the cache should give eight raw copper once");
            }
            if (e.siteId.equals(Ch3Ground.CLIFF) && e.built) {
                BlockPos glass = e.extra("spyglass");
                check(f, glass != null && level.getBlockEntity(glass) instanceof BarrelBlockEntity b && b.getItem(4).is(Items.SPYGLASS), "the cliff barrel has no spyglass");
                check(f, tabletSays(level, e.extra("tablet"), Ch3Ground.CLIFF_SIGN), "the cliff tablet does not carry its text");
            }
        }
    }

    // クリアの頁は 40 tick あとに記される
    private static boolean later(MinecraftServer server) {
        laterWait++;
        if (watchPlayer == null) {
            unforce(server.overworld());
            return true;
        }
        if (laterWait < 60) {
            return false;
        }
        List<String> f = new ArrayList<>();
        check(f, Lore.state(watchPlayer).has(Ch3Ground.PAGE_WATCH), "clearing the watch did not record the scorched page");
        SiteProgressData.get(server).forget(watchPlayer.getUUID(), ScorchedWatch.SITE);
        SiteProgressData.get(server).forgetTreasure(watchPlayer.getUUID(), ScorchedWatch.TREASURE);
        Lore.lock(watchPlayer, Ch3Ground.PAGE_WATCH);
        Growth.get(watchPlayer).discoveries.clear();
        watchPlayer.getInventory().clearContent();
        SiteSpawner.clearAll(server.overworld());
        watchPlayer = null;
        unforce(server.overworld());
        report("ch3 ground (sites later)", f, "the scorched page after the clear");
        return true;
    }
}
