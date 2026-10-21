package jp.revati.skyabyss.story.ch4;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.ChapterXpBudget;
import jp.revati.skyabyss.guide.HeartbeatWard;
import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteProgressData;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.ArtisanSpawner;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestItems;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.SkyKey;
import jp.revati.skyabyss.story.StoryData;
import jp.revati.skyabyss.story.ch4.mob.AshDragger;
import jp.revati.skyabyss.story.ch4.mob.AshHusk;
import jp.revati.skyabyss.story.ch4.mob.CinderMite;
import jp.revati.skyabyss.story.ch4.mob.ElderThrall;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import jp.revati.skyabyss.titan.ember.EmberAftermath;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 四章の地上 (docs/design/34 の C4) の自己テスト。SKYABYSS_SELFTEST_ONLY="ch4"
//   ch4 ground          データ (支度の段 2 本とサブ 2・日替わり・会話・伝承・文・経験値の枠・湧き台・職人の立つ所・部品の印・場所の置き方・メインの「乗る」の段と天の鍵の二人の答え・
//                       鼓動守の編み方)、二本の流れ (耐火の薬 2 = 延長版も数える・頁)、合図 ch4_ready、サブの合図、前の私の荷・窯の箱 (人ごとに一度)、温石の台、砥石と冷やし水
//   ch4 mobs            敵 4 種: 登録・体力・殻 (屑 15・古い灰被り 40、殻のあいだは傷が半分、煤の砥石は殻へ 1.5 倍)・燃え上がりと燃え尽き (屑は燃え尽き、古い灰被りは燃え尽きない)・
//                       熱の波・熾り蟲の群れと温石の台・灰ひきずりは敵意なし・丸まる・落とし物
//   ch4 flow            四章を通す: 段階 3 で支度の段が自動で出る → 噂 → 見る → 乗る → 二本 → 心臓 → 欠片 4 で天の鍵 → 語り部とイサナの答えで「天の鍵」が終わる →
//                       余韻の札・語り部の一言・終わりの合図。行き止まりがない (四章のメインが全部終わる)
//   ch4 ground (sites)  本物の世界で灰の地の跡地に建ったもの: 坑の碑・荷・温石の台・炉の碑・湧き台の立ち位置・クリアの宝、標 5、窯の箱、岩の台、高台の碑。頁は少しあと (later)
public final class Ch4GroundSelfTest {
    private static final UUID SITE_ID = UUID.nameUUIDFromBytes("skyabyss_ch4_ground_sites".getBytes());
    private static int siteWait;
    private static int loadWait;
    private static int laterWait;
    private static int flowWait;
    private static FakePlayer pitPlayer;
    private static FakePlayer flowPlayer;
    private static final List<String> FLOW_FAILS = new ArrayList<>();
    private static final java.util.Set<Long> FORCED = new java.util.HashSet<>();

    private Ch4GroundSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("ch4 ground", Ch4GroundSelfTest::run));
        steps.add(Step.once("ch4 mobs", Ch4GroundSelfTest::mobs));
        steps.add(Step.once("ch4 flow (start)", Ch4GroundSelfTest::flowStart));
        steps.add(Step.until("ch4 flow", Ch4GroundSelfTest::flowEnd));
        steps.add(Step.once("ch4 ground (sites start)", Ch4GroundSelfTest::sitesStart));
        steps.add(Step.until("ch4 ground (sites wait)", Ch4GroundSelfTest::sitesBuilt));
        steps.add(Step.until("ch4 ground (sites load)", Ch4GroundSelfTest::sitesLoaded));
        steps.add(Step.once("ch4 ground (sites)", Ch4GroundSelfTest::sitesCheck));
        steps.add(Step.until("ch4 ground (sites later)", Ch4GroundSelfTest::later));
        return steps;
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(MinecraftServer server, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_ch4g_" + suffix).getBytes());
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(id, "skyabyss_ch4g_" + suffix));
        Growth.get(p).level = 30;
        return p;
    }

    private static void clean(FakePlayer p) {
        Quests.forgetAll(p);
        Growth.get(p).discoveries.clear();
        p.getInventory().clearContent();
        p.removeAllEffects();
        for (String id : Ch4Ground.LORE) {
            Lore.lock(p, id);
        }
        SiteProgressData.get(p.server).forgetTreasure(p.getUUID(), DeadForgePit.PACK);
        SiteProgressData.get(p.server).forgetTreasure(p.getUUID(), Ch4Ground.KILN_CACHE);
        p.getPersistentData().remove(Ch4Mobs.SOOT_WHETTED_KEY);
    }

    private static void report(String name, List<String> f, String passed) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST {} PASSED ({})", name, passed);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST {} FAILED: {}", name, m));
        }
    }

    static ItemStack firePotion(boolean strong) {
        return PotionContents.createItemStack(Items.POTION, strong ? Potions.LONG_FIRE_RESISTANCE : Potions.FIRE_RESISTANCE);
    }

    // ---------------------------------------------------------------- データと流れ
    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer p = fake(server, "trail");
        try {
            data(f);
            trails(server, p, f);
            things(server, p, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch4 ground crashed", e);
            f.add("crashed: " + e);
        } finally {
            clean(p);
        }
        report("ch4 ground", f, "2 trail steps + 2 sub quests + daily, dialogue, lore, ja/en text, xp budget, main_ember board, main_key answers, ward weave, "
                + "trail flow and ch4_ready, pack and kiln cache once, warm stand, whetstone, cooler");
    }

    private static void data(List<String> f) {
        check(f, PrepQuests.defined(4) && PrepQuests.ids(4).equals(Ch4Ground.TRAILS) && PrepQuests.isTrail(4),
                "the two chapter-4 trail steps are not all defined (" + QuestManager.loadErrors() + ")");
        for (String id : Ch4Ground.TRAILS) {
            QuestDef def = QuestManager.quest(id);
            check(f, def != null && def.main() && def.startWhen() != null && !def.repeats() && !def.board() && def.chapter().equals(Chapters.EMBER.id()),
                    id + ": needs to be a main quest with start_when, chapter titan_ember, no repeat or board");
        }
        for (String id : List.of(Ch4Ground.Q_ROAD, Ch4Ground.Q_DRAGGER)) {
            QuestDef def = QuestManager.quest(id);
            check(f, def != null && !def.main() && def.chapter().equals(Chapters.EMBER.id()), id + " should be a chapter-4 side quest");
        }
        QuestDef daily = QuestManager.quest("daily_mite_clear");
        check(f, daily != null && daily.repeats() && daily.board(), "daily_mite_clear should be a daily board quest");
        check(f, QuestManager.loadErrors().stream().noneMatch(s -> s.contains("ch4/") || s.contains("main_ember") || s.contains("main_key") || s.contains("mite")),
                "chapter-4 quests were flagged: " + QuestManager.loadErrors());
        check(f, DialogueManager.loadErrors().isEmpty() || DialogueManager.loadErrors().stream().noneMatch(s -> s.contains("ch4") || s.contains("ms_key") || s.contains("ms_c_key")),
                "chapter-4 dialogue was flagged: " + DialogueManager.loadErrors());
        for (String[] n : new String[][] {{"skyabyss:storyteller", "ch4g_forge_tale"}, {"skyabyss:storyteller", "ch4g_forge_read2"}, {"skyabyss:storyteller", "ch4g_road_tale"},
                {"skyabyss:storyteller", "ch4g_s_kiln"}, {"skyabyss:storyteller", "ms_key_four"}, {"skyabyss:storyteller", "ms_key_return"},
                {"skyabyss:whale_chaser", "ch4g_c_board"}, {"skyabyss:whale_chaser", "ch4g_c_dragger"}, {"skyabyss:whale_chaser", "ch4g_c_watch"},
                {"skyabyss:whale_chaser", "ms_c_key3"}, {"skyabyss:artisan", "ch4a_first"}, {"skyabyss:artisan", "ch4g_draught_tell"},
                {"skyabyss:artisan", "ch4g_draught_show"}, {"skyabyss:artisan", "ch4t_ward_weave"}, {"skyabyss:artisan", "ch4p_teach"}}) {
            var d = DialogueManager.dialogue(n[0]);
            check(f, d != null && d.nodes().containsKey(n[1]), n[0] + " has no node " + n[1]);
        }
        var st = DialogueManager.dialogue("skyabyss:storyteller");
        check(f, st != null && "ms_key_four".equals(st.nodes().get("ms_key2").next()) && "ms_key_return".equals(st.nodes().get("ms_key3").next())
                && "ms_key4".equals(st.nodes().get("ms_key_return").next()), "the storyteller's key talk should run ms_key2 → ms_key_four → ms_key3 → ms_key_return → ms_key4");
        // メインの段: 灰の昇る山に「乗る」(心臓を先に止めれば飛ばす)、天の鍵に二人の答え
        QuestDef ember = QuestManager.quest("main_ember");
        List<String> stages = ember == null ? List.of() : ember.stages().stream().map(QuestDef.Stage::id).toList();
        check(f, stages.equals(List.of("rumor", "see", "board", "heart", "key")), "main_ember should be rumor → see → board → heart → key (" + stages + ")");
        QuestDef key = QuestManager.quest("main_key");
        check(f, key != null && key.stages().get(0).objectives().size() == 2, "main_key should ask for both answers (storyteller and Isana)");
        // 文 (日英)
        List<String> keys = new ArrayList<>(List.of("quest.skyabyss.ch4.fire_draught.brew.potion", "quest.skyabyss.ch4.fire_draught.show.talk",
                "quest.skyabyss.ch4.forge_ruins.pit.page", "quest.skyabyss.ch4.forge_ruins.tell.talk", "quest.skyabyss.ch4.ash_road.trace.marks",
                "quest.skyabyss.ch4.dragger_trail.follow.kiln", "quest.skyabyss.daily_mite_clear.sweep.kill", "quest.skyabyss.main_ember.board.board",
                "quest.skyabyss.main_key.show.isana", "message.skyabyss.ch4g.smith.return", "message.skyabyss.ch4g.pit.crack", "message.skyabyss.ch4g.ready",
                "entity.skyabyss.elder_thrall", "entity.skyabyss.cinder_mite", "entity.skyabyss.ash_husk", "entity.skyabyss.ash_dragger",
                "item.skyabyss.soot_iron", "item.skyabyss.ash_ward_thread.desc", "item.skyabyss.ash_cooler.desc", "wayfarer_notes.ch4.line.1",
                "dialogue.skyabyss.storyteller.ms_key_return", "dialogue.skyabyss.whale_chaser.ms_c_key3", "dialogue.skyabyss.artisan.a_menu.ward4",
                "advancement.skyabyss.dead_forge_pit.title", "advancement.skyabyss.ember_trail.description", "subtitles.skyabyss_ch4.elder_thrall_crack"));
        for (String id : Ch4Ground.LORE) {
            keys.add("lore.skyabyss." + id.replace('/', '.') + ".title");
            keys.add("lore.skyabyss." + id.replace('/', '.') + ".body");
        }
        for (String sign : List.of(DeadForgePit.SIGN, DeadForgePit.FORGE_SIGN, Ch4Ground.KILN_SIGN, Ch4Ground.WATCH_SIGN)) {
            for (int i = 1; i <= 4; i++) {
                keys.add(sign + "." + i);
            }
        }
        for (String k : keys) {
            check(f, SignFit.lang("ja_jp").containsKey(k) && SignFit.lang("en_us").containsKey(k), "missing ja/en text " + k);
        }
        // 経験値の枠 (四章の新しい分 1,200・合計 2,650)
        int fresh = ChapterXpBudget.newXp(QuestManager.all(), 4);
        check(f, fresh >= 760 && fresh <= ChapterXpBudget.NEW_CAP[4], "chapter 4 new xp " + fresh + " should count the trail steps, the subs and the pit, within " + ChapterXpBudget.NEW_CAP[4]);
        check(f, ChapterXpBudget.overruns(QuestManager.all(), 4).isEmpty(), "chapter 4 is over its xp budget: " + ChapterXpBudget.overruns(QuestManager.all(), 4));
        // 湧き台・職人の立つ所・部品の印・置き方・鼓動守の編み方
        SiteSpawner.Def def = SiteSpawner.def(DeadForgePit.SITE);
        check(f, def != null && def.xp() == DeadForgePit.XP && def.chapter() == Chapters.EMBER && def.lastRoom().equals(DeadForgePit.ROOM_DEEP) && def.pads().size() == 7,
                "the dead forge pit spawner should be chapter 4, 80 xp, seven pads with the deep room last");
        check(f, ArtisanSpawner.spots().stream().anyMatch(s -> s.chapter() == 4 && s.siteId().equals(Ch4Ground.PIT) && s.mark().equals("artisan")),
                "the artisan has no chapter-4 spot at the dead forge pit");
        for (String[] bm : new String[][] {{DeadForgePit.SITE, "tablet"}, {DeadForgePit.SITE, "forge_tablet"}, {DeadForgePit.SITE, "pack"}, {DeadForgePit.SITE, "warm_stand"},
                {DeadForgePit.SITE, "artisan"}, {DeadForgePit.SITE, "exit"}, {Ch4Ground.MARK, "top"}, {Ch4Ground.ROCKS, "stone"}, {Ch4Ground.KILN, "cache"},
                {Ch4Ground.KILN, "tablet"}, {Ch4Ground.WATCH, "tablet"}}) {
            check(f, Blueprint.get(bm[0]).marks.containsKey(bm[1]), bm[0] + " needs mark " + bm[1]);
        }
        for (String e : Ch4Ground.ELEMENTS) {
            ChapterSites.Element el = ChapterSites.element(e);
            check(f, el != null && el.atGround() && el.chapter() == Chapters.EMBER && el.rules().maxDist() <= 150, e + " should be placed on the chapter-4 ground within 150");
        }
        HeartbeatWard.Weave w = HeartbeatWard.weave(4);
        check(f, w != null && w.titan().equals("ember") && w.thread().equals("skyabyss:ash_ward_thread") && w.refill().equals("skyabyss:soot_iron"),
                "chapter 4 should weave the ember heartbeat ward from the ash ward thread");
        check(f, QuestItems.known(Ch4GroundSelfTest.FIRE) && !QuestItems.known("minecraft:potion@minecraft:nope"), "the potion@effect target is not understood");
    }

    static final String FIRE = "minecraft:potion@minecraft:fire_resistance";

    private static void trails(MinecraftServer server, FakePlayer p, List<String> f) {
        clean(p);
        for (String id : Ch4Ground.TRAILS) {
            Quests.start(p, id);
            check(f, QuestLog.of(p).entry(id) != null, id + " did not start");
        }
        // 火除けの薬: 耐火 1 では済まない、2 (延長版も数える) で済む。ただの水の瓶・別の薬は数えない
        p.getInventory().add(firePotion(false));
        p.getInventory().add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        p.getInventory().add(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        Quests.poll(p, true);
        check(f, "brew".equals(DeadForgePit.stageOf(p, Ch4Ground.Q_DRAUGHT)) && QuestItems.count(p, FIRE, null) == 1, "one fire potion should read 1/2 ("
                + QuestItems.count(p, FIRE, null) + ")");
        p.getInventory().add(firePotion(true));
        Quests.poll(p, true);
        check(f, "show".equals(DeadForgePit.stageOf(p, Ch4Ground.Q_DRAUGHT)), "two fire potions (one long) did not move the draught to the show stage");
        var artisan = DialogueManager.dialogue("skyabyss:artisan");
        if (artisan != null && artisan.nodes().containsKey("ch4g_draught_show")) {
            StoryContext ctx = StoryContext.of(p);
            artisan.nodes().get("ch4g_draught_show").actions().forEach(a -> a.run(ctx));
        }
        Quests.onTalk(p, "skyabyss:artisan", "ch4g_draught_show");
        check(f, QuestLog.of(p).entry(Ch4Ground.Q_DRAUGHT).done && Lore.state(p).has(Ch4Ground.PAGE_DRAUGHT),
                "showing the draughts did not finish the draught quest with the ash people's draught page");
        check(f, !PrepQuests.ready(p, 4), "one trail step made chapter 4 ready");
        // 炉跡: 頁 → 語り部に見せる
        check(f, "pit".equals(DeadForgePit.stageOf(p, Ch4Ground.Q_FORGE)), "the forge quest should start at the pit");
        Lore.unlock(p, Ch4Ground.PAGE_FORGE, false);
        Quests.poll(p, true);
        check(f, "tell".equals(DeadForgePit.stageOf(p, Ch4Ground.Q_FORGE)), "the forge page did not move the forge quest to tell");
        Quests.onTalk(p, "skyabyss:storyteller", "ch4g_forge_read2");
        check(f, QuestLog.of(p).entry(Ch4Ground.Q_FORGE).done, "showing the page did not finish the forge quest");
        check(f, PrepQuests.ready(p, 4) && Growth.get(p).discoveries.contains("prep_ready:4"), "the two chapter-4 steps did not raise ch4_ready");
        // サブ: 灰の道の標 5、灰ひきずり (窯の跡)
        Quests.start(p, Ch4Ground.Q_ROAD);
        Quests.start(p, Ch4Ground.Q_DRAGGER);
        for (int i = 0; i < 4; i++) {
            Quests.signal(p, Ch4Ground.SIGNAL_MARK);
        }
        check(f, !QuestLog.of(p).entry(Ch4Ground.Q_ROAD).done && QuestLog.of(p).entry(Ch4Ground.Q_ROAD).progress(0) == 4, "four marks should read 4/5");
        Quests.signal(p, Ch4Ground.SIGNAL_MARK);
        Quests.signal(p, Ch4Ground.SIGNAL_KILN);
        check(f, QuestLog.of(p).entry(Ch4Ground.Q_ROAD).done && QuestLog.of(p).entry(Ch4Ground.Q_DRAGGER).done, "the ash road / dragger signals did not finish the subs");
    }

    private static void things(MinecraftServer server, FakePlayer p, List<String> f) {
        ServerLevel level = server.overworld();
        // 前の私の荷 (人ごとに一度、ブレイズパウダー 1)・窯の箱 (煤鉄 4・灰麻 3)
        p.getInventory().clearContent();
        check(f, DeadForgePit.openPack(p, level, null) && !DeadForgePit.openPack(p, level, null) && QuestItems.count(p, "minecraft:blaze_powder", null) == 1,
                "the pack should hand over one blaze powder, once");
        check(f, Ch4Ground.openCache(p, level, null) && !Ch4Ground.openCache(p, level, null) && QuestItems.count(p, "skyabyss:soot_iron", null) == 4
                && QuestItems.count(p, "skyabyss:ash_hemp", null) == 3, "the kiln cache should hand over four soot iron and three ash hemp, once");
        // 温石の台: 温石なしでは温まらない。温石を持てば温まり、熾り蟲の寄る先になる
        BlockPos stand = level.getSharedSpawnPos().offset(500, 0, 500);
        check(f, !DeadForgePit.touchStand(p, level, stand) && DeadForgePit.warmStandNear(level, stand, 4) == null, "the stand warmed without a warm stone");
        p.getInventory().add(new ItemStack(jp.revati.skyabyss.titan.ember.EmberTitanSetup.WARM_STONE.get()));
        check(f, DeadForgePit.touchStand(p, level, stand) && stand.equals(DeadForgePit.warmStandNear(level, stand.offset(3, 0, 2), 16))
                && DeadForgePit.warmStandNear(level, stand.offset(40, 0, 0), 16) == null, "a warm stone should warm the stand for mites within 16");
        check(f, p.getInventory().countItem(jp.revati.skyabyss.titan.ember.EmberTitanSetup.WARM_STONE.get()) == 1, "warming the stand used up the warm stone");
        DeadForgePit.forget();
        // 煤の砥石: 研いだ刃 + 灰の殻へ 1.5 倍。冷やし水: 耐火
        check(f, Ch4Mobs.shellBonus(level.damageSources().playerAttack(p)) == 1f, "an unwhetted blade got the soot bonus");
        Ch4Setup.SOOT_WHETSTONE.get().use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
        p.getPersistentData().putLong(Ch4Mobs.SOOT_WHETTED_KEY, level.getGameTime() + 100);
        check(f, Ch4Mobs.sootWhetted(p) && Ch4Mobs.shellBonus(level.damageSources().playerAttack(p)) == Ch4Mobs.SOOT_SHELL_MULT, "the soot whetstone did not give the shell bonus");
        AshCoolerItem.drink(p);
        check(f, p.hasEffect(MobEffects.FIRE_RESISTANCE), "the ash cooler did not give fire resistance");
        // レシピ (旅の覚書の四章)
        check(f, jp.revati.skyabyss.guide.WayfarerNotes.recipes(4).size() == Ch4Setup.RECIPES.size()
                && server.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "soot_whetstone")).isPresent()
                && server.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ash_cooler")).isPresent(),
                "the chapter-4 recipes are missing or not on the notes");
    }

    // ---------------------------------------------------------------- 敵
    static void mobs(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        BlockPos at = level.getSharedSpawnPos().offset(0, 0, 60);
        at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        List<Entity> made = new ArrayList<>();
        FakePlayer p = fake(server, "mobs");
        try {
            // 灰被りの屑: 体力 28・殻 15。殻のあいだは傷が半分、殻が割れると燃えて、やがて燃え尽きる
            AshHusk husk = put(level, Ch4Setup.ASH_HUSK.get().create(level), at, made);
            check(f, husk != null && Math.abs(husk.getMaxHealth() - AshHusk.HEALTH) < 0.1f && Math.abs(husk.shell() - AshHusk.SHELL) < 0.1f,
                    "the ash husk should have 28 health and a shell of 15");
            if (husk != null) {
                float h0 = husk.getHealth();
                husk.invulnerableTime = 0;
                husk.hurt(level.damageSources().playerAttack(p), 8f);
                check(f, Math.abs(husk.getHealth() - (h0 - 4f)) < 0.6f && husk.shell() < AshHusk.SHELL, "the husk's shell did not halve the blow (" + h0 + " -> " + husk.getHealth() + ")");
                husk.ignite();
                check(f, husk.isFlared() && husk.burnLeft() == AshHusk.BURNOUT, "a broken husk shell should flare and burn out in 10 s (" + husk.burnLeft() + ")");
            }
            // 古い灰被り: 体力 210・殻 40。煤の砥石は殻へ 1.5 倍。殻が割れると熱の波、燃え尽きない
            ElderThrall elder = put(level, Ch4Setup.ELDER_THRALL.get().create(level), at.offset(6, 0, 0), made);
            check(f, elder != null && Math.abs(elder.getMaxHealth() - ElderThrall.HEALTH) < 0.1f && Math.abs(elder.shell() - ElderThrall.SHELL) < 0.1f,
                    "the elder thrall should have 210 health and a shell of 40");
            if (elder != null) {
                p.getPersistentData().putLong(Ch4Mobs.SOOT_WHETTED_KEY, level.getGameTime() + 100);
                elder.invulnerableTime = 0;
                elder.hurt(level.damageSources().playerAttack(p), 10f);
                check(f, Math.abs(elder.shell() - (ElderThrall.SHELL - 7.5f)) < 0.6f, "a soot-whetted blow should take 1.5x from the elder's shell (" + elder.shell() + ")");
                p.getPersistentData().remove(Ch4Mobs.SOOT_WHETTED_KEY);
                elder.ignite();
                check(f, elder.isFlared() && elder.waves == 1 && elder.burnLeft() == 0, "breaking the elder's shell should send one heat wave and never burn out");
                check(f, ElderThrall.CRACK_WARN >= 20 && ElderThrall.RAKE_WARN >= 20 && ElderThrall.WAVE_WARN >= 20, "a named foe's telegraphs must be at least 1 s");
                check(f, elder.startCracks() == 0 && elder.cracks == 1, "cracks with nobody in the room should be cast but hit no one");
            }
            // 熾り蟲: 体力 6・ひとり歩きは群れを連れる。温まった台へ寄る
            CinderMite mite = put(level, Ch4Setup.CINDER_MITE.get().create(level), at.offset(-6, 0, 0), made);
            check(f, mite != null && Math.abs(mite.getMaxHealth() - CinderMite.HEALTH) < 0.1f, "the cinder mite should have 6 health");
            if (mite != null) {
                int flock = mite.flock();
                check(f, flock >= 2 && flock <= 4, "a wandering mite should bring 2-4 more (" + flock + ")");
                for (CinderMite m : level.getEntitiesOfClass(CinderMite.class, mite.getBoundingBox().inflate(4))) {
                    if (m != mite) {
                        made.add(m);
                        check(f, m.getTags().contains(CinderMite.FLOCK_TAG) && !m.shouldBeSaved(), "flock mites should not be saved");
                    }
                }
                DeadForgePit.warm(level, mite.blockPosition().offset(3, 0, 0));
                check(f, DeadForgePit.warmStandNear(level, mite.blockPosition(), CinderMite.STONE_RANGE) != null, "the mite does not see the warm stand");
                DeadForgePit.forget();
                check(f, mite.hottestPlayer() == null, "a fake player should not be hunted");
            }
            // 灰ひきずり: 敵意なし。丸まる
            AshDragger dragger = put(level, Ch4Setup.ASH_DRAGGER.get().create(level), at.offset(0, 0, 6), made);
            check(f, dragger != null && !dragger.canAttack(p) && Math.abs(dragger.getMaxHealth() - AshDragger.HEALTH) < 0.1f, "the ash dragger should be harmless with 20 health");
            if (dragger != null) {
                dragger.curl(true);
                check(f, dragger.isBall(), "the dragger did not curl up");
                dragger.curl(false);
            }
            check(f, jp.revati.skyabyss.rpg.GrowthRewards.MOD_ENEMIES.containsKey(Ch4Mobs.ELDER_THRALL) && jp.revati.skyabyss.rpg.GrowthRewards.MOD_ENEMIES.containsKey(Ch4Mobs.CINDER_MITE),
                    "the chapter-4 enemies give no growth xp");
            check(f, jp.revati.skyabyss.guide.ChapterMobSpawner.registered().stream().filter(w -> w.chapter() == Chapters.EMBER).count() >= 3,
                    "the chapter-4 wanderers are not registered");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch4 mobs crashed", e);
            f.add("crashed: " + e);
        } finally {
            made.forEach(Entity::discard);
            p.getPersistentData().remove(Ch4Mobs.SOOT_WHETTED_KEY);
        }
        report("ch4 mobs", f, "husk shell/flare/burnout, elder shell 40 with soot bonus, heat wave, no burnout, 1 s telegraphs, cracks, mite flock and warm stand, harmless dragger curls, xp, wanderers");
    }

    static <T extends Mob> T put(ServerLevel level, T mob, BlockPos at, List<Entity> made) {
        if (mob == null) {
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mob.addTag(jp.revati.skyabyss.entity.GuardMob.TEMP_TAG);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        made.add(mob);
        return mob;
    }

    // ---------------------------------------------------------------- 四章を通す
    static void flowStart(MinecraftServer server) {
        FLOW_FAILS.clear();
        flowWait = 0;
        List<String> f = FLOW_FAILS;
        FakePlayer p = fake(server, "flow");
        flowPlayer = p;
        try {
            clean(p);
            Lore.reset(p);
            StoryData.get(server).clear(p.getUUID());
            EmberAftermath.reset(p);
            ProgressRules.TITAN_ORDER.forEach(t -> ProgressData.get(server).clearPersonal(p.getUUID(), t));
            for (String t : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING)) {
                Progression.onTitanStopped(server, List.of(p.getUUID()), t);
            }
            check(f, Progression.stage(p) == 3 && Chapters.of(p) == Chapters.EMBER, "three stilled hearts should open chapter 4 (stage " + Progression.stage(p) + ")");
            Quests.start(p, "main_ember");
            Quests.poll(p, true);
            for (String id : Ch4Ground.TRAILS) {
                check(f, QuestLog.of(p).entry(id) != null, id + " did not appear on its own in chapter 4");
            }
            check(f, QuestLog.of(p).entry(Ch4Ground.Q_ROAD) != null && QuestLog.of(p).entry(Ch4Ground.Q_DRAGGER) != null, "the chapter-4 side quests did not appear");
            // 噂 → 見る → 乗る
            Quests.onTalk(p, "skyabyss:whale_chaser", "ms_c_ember2");
            Quests.poll(p, true);
            check(f, "see".equals(stage(p, "main_ember")), "the rumor did not move main_ember to see: " + stage(p, "main_ember"));
            Lore.unlock(p, "chapter1/ember", false);
            Quests.poll(p, true);
            check(f, "board".equals(stage(p, "main_ember")), "seeing the ember did not move to board: " + stage(p, "main_ember"));
            Quests.poll(p, true);
            check(f, "board".equals(stage(p, "main_ember")), "the board stage passed without boarding");
            Lore.unlock(p, "chapter1/ember_ruins", false);
            Quests.poll(p, true);
            check(f, "heart".equals(stage(p, "main_ember")), "boarding did not move to the heart: " + stage(p, "main_ember"));
            // 支度の段 (どちらも勧め。心臓の前に済ませる)
            p.getInventory().add(firePotion(false));
            p.getInventory().add(firePotion(false));
            Quests.poll(p, true);
            Quests.onTalk(p, "skyabyss:artisan", "ch4g_draught_show");
            Lore.unlock(p, Ch4Ground.PAGE_FORGE, false);
            Quests.poll(p, true);
            Quests.onTalk(p, "skyabyss:storyteller", "ch4g_forge_read2");
            check(f, PrepQuests.ready(p, 4), "the chapter-4 trail steps did not finish in the flow");
            // 心臓 → 欠片 4 で天の鍵
            Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.EMBER);
            Quests.poll(p, true);
            check(f, "key".equals(stage(p, "main_ember")), "stilling the ember heart did not move to key: " + stage(p, "main_ember"));
            int have = SkyKey.fragmentsHeld(p);
            if (have < SkyKey.FRAGMENTS) {
                p.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), SkyKey.FRAGMENTS - have));
            }
            check(f, SkyKey.ready(p) && SkyKey.assemble(p, false), "the key did not form at stage 4 with four fragments");
            Quests.poll(p, true);
            check(f, "done".equals(stage(p, "main_ember")) && "show".equals(stage(p, "main_key")), "the key did not finish main_ember and open main_key");
            // 天の鍵: 語り部の答え (返せ) だけでは終わらず、イサナの答え (眠らせてよいのか) で終わる
            Quests.onTalk(p, "skyabyss:storyteller", "ms_key4");
            Quests.poll(p, true);
            check(f, "show".equals(stage(p, "main_key")), "main_key finished without Isana's answer");
            Quests.onTalk(p, "skyabyss:whale_chaser", "ms_c_key3");
            Quests.poll(p, true);
            check(f, "done".equals(stage(p, "main_key")), "Isana's answer did not finish main_key");
            for (QuestDef d : QuestManager.all()) {
                if (d.main() && Chapters.EMBER.id().equals(d.chapter()) && !d.id().equals("ch4/fragments")) {
                    check(f, "done".equals(stage(p, d.id())), "chapter-4 main quest " + d.id() + " is not done at the end: " + stage(p, d.id()));
                }
            }
            // 余韻: 場面の長さを待たずに進める (key_at・since を昔へ)
            CompoundTag st = p.getPersistentData().getCompound(EmberAftermath.KEY);
            long now = server.overworld().getGameTime();
            st.putLong("key_at", now - EmberAftermath.SCENE_TICKS - 5);
            st.putLong("since", now - EmberAftermath.CALM_TICKS - 5);
            p.getPersistentData().put(EmberAftermath.KEY, st);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch4 flow crashed", e);
            f.add("crashed: " + e);
            flowPlayer = null;
        }
    }

    static String stage(FakePlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        if (e == null) {
            return "none";
        }
        if (e.done) {
            return "done";
        }
        QuestDef def = QuestManager.quest(quest);
        return def == null || e.stage >= def.stages().size() ? "?" : def.stages().get(e.stage).id();
    }

    // 余韻の札 → 語り部の一言 → 終わりの合図 (天の鍵があるので出る)。札と一言の間 6 秒、一言と合図の間 10 秒
    static boolean flowEnd(MinecraftServer server) {
        FakePlayer p = flowPlayer;
        if (p != null) {
            flowWait++;
            EmberAftermath.poll(p);
            if (!EmberAftermath.endDone(p) && flowWait < 900) {
                return false;
            }
            check(FLOW_FAILS, EmberAftermath.cardDone(p) && EmberAftermath.tellerDone(p) && EmberAftermath.endDone(p),
                    "the aftermath did not run card → storyteller → end (" + EmberAftermath.cardDone(p) + "/" + EmberAftermath.tellerDone(p) + "/" + EmberAftermath.endDone(p) + ")");
            clean(p);
            Lore.reset(p);
            EmberAftermath.reset(p);
            StoryData.get(server).clear(p.getUUID());
            ProgressRules.TITAN_ORDER.forEach(t -> ProgressData.get(server).clearPersonal(p.getUUID(), t));
            flowPlayer = null;
        }
        report("ch4 flow", FLOW_FAILS, "stage 3 opens the trail steps and subs, rumor → see → board → heart → key → main_key with both answers, all chapter-4 mains done, aftermath card/teller/end");
        return true;
    }

    // ---------------------------------------------------------------- 本物の世界
    private static FakePlayer siteFake(MinecraftServer server) {
        return FakePlayerFactory.get(server.overworld(), new GameProfile(SITE_ID, "skyabyss_ch4g_sites"));
    }

    private static void sitesStart(MinecraftServer server) {
        siteWait = 0;
        FakePlayer p = siteFake(server);
        BlockPos base = server.overworld().getSharedSpawnPos();
        p.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        ChapterSites.open(p, Chapters.EMBER);
    }

    private static List<ChapterSites.Site> mine(MinecraftServer server) {
        List<ChapterSites.Site> out = new ArrayList<>();
        for (String id : Ch4Ground.ELEMENTS) {
            out.addAll(ChapterSites.sitesFor(server, SITE_ID, id));
        }
        return out;
    }

    private static boolean sitesBuilt(MinecraftServer server) {
        siteWait++;
        List<ChapterSites.Site> sites = mine(server);
        return siteWait > 9000 || (siteWait > 100 && !sites.isEmpty() && sites.stream().allMatch(ChapterSites.Site::built));
    }

    private static boolean sitesLoaded(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (FORCED.isEmpty()) {
            for (SiteData.Entry e : SiteData.get(level).allEntries()) {
                if (!e.built || !Ch4Ground.ELEMENTS.contains(e.siteId)) {
                    continue;
                }
                int r = e.siteId.equals(DeadForgePit.SITE) ? 2 : 1;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
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
            check(f, sites.stream().allMatch(ChapterSites.Site::built) && !sites.isEmpty(), "not every chapter-4 ground element was built (" + sites.size() + ")");
            pit(server, level, f);
            other(server, level, p, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch4 ground (sites) crashed", e);
            f.add("crashed: " + e);
        } finally {
            clean(p);
        }
        report("ch4 ground (sites)", f, "pit tablets, pack, warm stand, pads, first clear; five ash marks; kiln cache and tablet; rock stand; watch tablet");
    }

    private static void pit(MinecraftServer server, ServerLevel level, List<String> f) {
        List<SiteData.Entry> pits = DeadForgePit.entries(server);
        check(f, pits.size() == 1, "the dead forge pit should be built once (" + pits.size() + ")");
        if (pits.isEmpty()) {
            pitPlayer = null;
            return;
        }
        SiteData.Entry e = pits.get(0);
        check(f, tabletSays(level, e.extra("tablet"), DeadForgePit.SIGN), "the pit-mouth tablet does not carry its text");
        check(f, tabletSays(level, e.extra("forge_tablet"), DeadForgePit.FORGE_SIGN), "the forge tablet does not carry its text");
        check(f, e.extra("pack") != null && level.getBlockState(e.extra("pack")).is(Blocks.BARREL), "the pack barrel is missing");
        check(f, e.extra("warm_stand") != null && level.getBlockState(e.extra("warm_stand")).is(Blocks.SMOOTH_BASALT), "the warm stand is missing");
        check(f, e.extra("artisan") != null, "the artisan's camp mark was not kept");
        SiteSpawner.Def def = SiteSpawner.def(DeadForgePit.SITE);
        DeadForgePit.origins(server);
        for (SiteSpawner.Pad pad : def.pads()) {
            BlockPos at = def.padAt(pad, e.pos);
            check(f, level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && !level.getBlockState(at.below()).isAir(),
                    "pit pad " + pad.room() + " at " + at + " is not standing room on a floor (" + level.getBlockState(at) + " / below " + level.getBlockState(at.below()) + ")");
        }
        // クリア: 一人のグループの分だけ湧き、倒しきるとクリア (経験値・煤鉄)。頁は少しあと (later)
        FakePlayer a = fake(server, "pit");
        pitPlayer = a;
        SiteProgressData progress = SiteProgressData.get(server);
        progress.forget(a.getUUID(), DeadForgePit.SITE);
        progress.forgetTreasure(a.getUUID(), DeadForgePit.TREASURE);
        Lore.lock(a, Ch4Ground.PAGE_FORGE);
        a.getInventory().clearContent();
        a.setPos(e.pos.getX() + 0.5, e.pos.getY(), e.pos.getZ() + 0.5);
        long now = level.getGameTime();
        int total = def.pads().stream().mapToInt(x -> x.spawns().stream().mapToInt(SiteSpawner.Spawn::count).sum()).sum();
        SiteSpawner.tick(level, List.of(a), now);
        UUID ga = Groups.keyOf(a);
        int live = SiteSpawner.liveCount(DeadForgePit.SITE, e.pos, ga) + SiteSpawner.pendingCount(DeadForgePit.SITE, e.pos, ga);
        check(f, live == total, "the pit should hold " + total + " enemies for the group (" + live + ")");
        boolean sawElder = false;
        for (UUID id : SiteSpawner.liveIds(DeadForgePit.SITE, e.pos, ga)) {
            Entity x = level.getEntity(id);
            sawElder |= x instanceof ElderThrall;
            if (x != null) {
                x.discard();
            }
        }
        check(f, sawElder || SiteSpawner.pendingCount(DeadForgePit.SITE, e.pos, ga) > 0, "the elder thrall was not among the pit's enemies");
        SiteSpawner.tick(level, List.of(a), now + 40);
        check(f, progress.cleared(a.getUUID(), DeadForgePit.SITE), "clearing the pit was not recorded");
        check(f, QuestItems.count(a, "skyabyss:soot_iron", null) >= 4, "the first pit clear should hand over soot iron");
        laterWait = 0;
    }

    private static void other(MinecraftServer server, ServerLevel level, FakePlayer p, List<String> f) {
        int marks = Ch4Ground.entries(server, Ch4Ground.MARK).size();
        check(f, marks == Ch4Ground.MARKS, "five ash marks should stand (" + marks + ")");
        for (SiteData.Entry e : Ch4Ground.entries(server, Ch4Ground.KILN)) {
            BlockPos cache = e.extra("cache");
            check(f, cache != null && level.getBlockState(cache).is(Blocks.BARREL), "the kiln has no cache barrel");
            check(f, tabletSays(level, e.extra("tablet"), Ch4Ground.KILN_SIGN), "the kiln tablet does not carry its text");
            check(f, e.pos.equals(Ch4Ground.kilnNear(level, e.pos.offset(20, 0, 0), 64)), "the draggers cannot find the kiln");
        }
        check(f, Ch4Ground.entries(server, Ch4Ground.KILN).size() == 1, "the soot kiln should be built once");
        int rocks = 0;
        for (SiteData.Entry e : Ch4Ground.entries(server, Ch4Ground.ROCKS)) {
            rocks++;
            check(f, e.extra("stone") != null && level.getBlockState(e.extra("stone")).is(Blocks.SMOOTH_BASALT), "a warm rock field has no stone stand");
        }
        check(f, rocks == 2, "two warm rock fields should stand (" + rocks + ")");
        for (SiteData.Entry e : Ch4Ground.entries(server, Ch4Ground.WATCH)) {
            check(f, tabletSays(level, e.extra("tablet"), Ch4Ground.WATCH_SIGN), "the ash watch tablet does not carry its text");
        }
        check(f, Ch4Ground.first(server, Ch4Ground.PIT, null) != null && Ch4Ground.first(server, Ch4Ground.KILN, null) != null, "the band targets have no pit or kiln");
    }

    private static boolean later(MinecraftServer server) {
        laterWait++;
        if (pitPlayer == null) {
            unforce(server.overworld());
            return true;
        }
        if (laterWait < 60) {
            return false;
        }
        List<String> f = new ArrayList<>();
        check(f, Lore.state(pitPlayer).has(Ch4Ground.PAGE_FORGE), "clearing the pit did not record the forge page");
        SiteProgressData.get(server).forget(pitPlayer.getUUID(), DeadForgePit.SITE);
        SiteProgressData.get(server).forgetTreasure(pitPlayer.getUUID(), DeadForgePit.TREASURE);
        Lore.lock(pitPlayer, Ch4Ground.PAGE_FORGE);
        Growth.get(pitPlayer).discoveries.clear();
        pitPlayer.getInventory().clearContent();
        SiteSpawner.clearAll(server.overworld());
        pitPlayer = null;
        unforce(server.overworld());
        report("ch4 ground (sites later)", f, "the forge page after the clear");
        return true;
    }
}
