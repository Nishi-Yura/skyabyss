package jp.revati.skyabyss.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.npc.DialogueService;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.ChapterCards;
import jp.revati.skyabyss.story.arc.Foreshadow;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.QuestSetup;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

// 1 章を 4 体分で通す自己テスト (SELFTEST story)。docs/design/14_beta.md の 7 章。
// 巨神 4 体の順番 (世界の進み)・乗る資格 (個人の進み、後の巨神の背中の口)・段階ごとのレベル上限・天の鍵の組み立て・
// メインクエストを「雲の下の歌」のあとから天の鍵まで・1 章の続きのサブクエスト 2 本を確かめる。記録は写して試し、最後に戻す
public final class StorySelfTest {
    // 1 章の続きとして足したクエスト (自由なモードの自己テストは、これを数えない)
    public static final Set<String> STORY_QUESTS = Set.of("main_tide", "main_thunder", "main_ember", "main_key", "sub_old_map", "sub_warm_coal",
            "daily_tide_rounds", "weekly_tide_heart");
    private static final String ST = NpcSetup.STORYTELLER_ID;
    private static final String WC = NpcSetup.WHALE_CHASER_ID;

    private StorySelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ProgressData progress = ProgressData.get(server);
        CompoundTag progressSaved = progress.save(new CompoundTag(), server.registryAccess());
        StoryData story = StoryData.get(server);
        CompoundTag storySaved = story.snapshot(server.registryAccess());
        GameRules rules = server.getGameRules();
        boolean gateBefore = rules.getBoolean(Progression.STORY_GATE);
        ServerLevel level = server.overworld();
        boolean rain = level.getLevelData().isRaining();
        boolean thunder = level.getLevelData().isThundering();
        List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
        try {
            rules.getRule(Progression.STORY_GATE).set(true, server);
            order(server, failures, notes);
            mainQuests(server, level, spawned, failures, notes);
            isanaTopics(server, level, spawned, failures, notes);
            migration(server, level, failures, notes);
        } catch (RuntimeException e) {
            failures.add("exception: " + e);
            SkyAbyss.LOGGER.error("SELFTEST story exception", e);
        } finally {
            spawned.forEach(net.minecraft.world.entity.Entity::discard);
            progress.restore(progressSaved, server.registryAccess());
            story.restore(storySaved, server.registryAccess());
            rules.getRule(Progression.STORY_GATE).set(gateBefore, server);
            level.setWeatherParameters(rain ? 0 : 6000, rain ? 6000 : 0, rain, thunder);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST story: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST story PASSED (4 titans in order, boarding, level caps, sky key, main quests to the key, {} checks)", notes.size());
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST story FAILED: {}", f));
        }
    }


    // ---------------------------------------------------------------- 「イサナに〜を尋ねよう」の連鎖 (聞いたのに目的が残る不具合)
    // 聞いた時点で済む (最後の頁まで読まなくても)、先に聞いても済む、ログイン (保存して読み直し) をまたいでも済む、
    // 古いセーブ (記録なし) でも、先の段の証拠があれば自己修復する
    private static void isanaTopics(MinecraftServer server, ServerLevel level, List<net.minecraft.world.entity.Entity> spawned, List<String> f,
                                    List<String> notes) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos at = new BlockPos(spawn.getX() + 40, 300, spawn.getZ() + 40);
        NpcEntity wc = NpcSetup.WHALE_CHASER.get().create(level);
        if (wc == null) {
            f.add("isana topics: npc could not be created");
            return;
        }
        wc.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        wc.setNoGravity(true);
        level.addFreshEntity(wc);
        spawned.add(wc);
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a1e0b3c-0000-4000-8000-00000000b002"), "[isana_topics]"));
        Lore.reset(p);
        p.getInventory().clearContent();
        Quests.forgetAll(p);
        p.setData(NpcSetup.BONDS, new NpcBonds());
        NpcBonds.of(p).bond(WC).flags().add("met");
        p.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5);
        StoryData.get(server).clear(p.getUUID());
        ProgressRules.TITAN_ORDER.forEach(t -> ProgressData.get(server).clearPersonal(p.getUUID(), t));
        jp.revati.skyabyss.quest.Quests.forget(p, "ch4/fragments");

        // 潮殻: 話題の頁 (ms_c_tide2) に着いたとたんに済む
        Quests.setStage(p, "main_tide", "rumor");
        check(stage(p, "main_tide").equals("rumor"), "isana topics: main_tide did not start at rumor", f);
        talk(p, wc, "ms_c_tide", f);
        check(stage(p, "main_tide").equals("rumor"), "isana topics: the rumor finished before the topic page", f);
        DialogueService.choose(p, "ms_c_tide", DialogueService.CONTINUE);
        check(stage(p, "main_tide").equals("see"), "isana topics: hearing Isana's topic page did not finish main_tide's rumor: " + stage(p, "main_tide")
                + " (node " + node(p) + ", heard " + QuestLog.of(p).heard("main_tide", WC, "ms_c_tide2") + ", npc alive " + wc.isAlive() + ")", f);
        DialogueService.close(p);

        // 余燼: 同じ。保存して読み直しても (ログイン) 聞いた記録は残る
        Quests.setStage(p, "main_ember", "rumor");
        check(stage(p, "main_ember").equals("rumor"), "isana topics: main_ember did not start at rumor", f);
        talk(p, wc, "ms_c_ember", f);
        DialogueService.choose(p, "ms_c_ember", DialogueService.CONTINUE);
        check(stage(p, "main_ember").equals("see"), "isana topics: hearing Isana's topic page did not finish main_ember's rumor: " + stage(p, "main_ember"), f);
        DialogueService.close(p);
        QuestLog before = QuestLog.of(p);
        QuestLog after = QuestLog.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, QuestLog.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, before)
                .getOrThrow()).getOrThrow();
        check(after.heard("main_ember", WC, "ms_c_ember2") && after.heard("main_tide", WC, "ms_c_tide2"), "isana topics: heard topics were lost across save and load", f);
        p.setData(QuestSetup.LOG, after);
        check(stage(p, "main_ember").equals("see") && stage(p, "main_tide").equals("see"), "isana topics: stages changed across relog", f);

        // 記録のあるのに段が残った状態 (古いセーブ・先に聞いていた) は、見回りが一度走れば済む
        Quests.setStage(p, "main_ember", "rumor");
        Quests.poll(p, true);
        check(stage(p, "main_ember").equals("see"), "isana topics: a heard rumor stage did not self-heal on poll: " + stage(p, "main_ember"), f);
        // 先の段の証拠 (心臓を止めた) があれば、聞いた記録がなくても飛ばす
        p.setData(QuestSetup.LOG, new QuestLog());
        Quests.setStage(p, "main_tide", "rumor");
        ProgressData.get(server).markPersonal(p.getUUID(), Titans.TIDE_SHELL);
        Quests.poll(p, true);
        check(!stage(p, "main_tide").equals("rumor"), "isana topics: a rumor stage stayed after the tide heart was stilled: " + stage(p, "main_tide"), f);
        ProgressData.get(server).clearPersonal(p.getUUID(), Titans.TIDE_SHELL);

        // 四章: イサナの話を先に聞いたあとで語り部の段が済めば、イサナの段は着いたとたんに済む
        p.setData(QuestSetup.LOG, new QuestLog());
        Quests.setStage(p, "ch4/fragments", "teller");
        Quests.onTalk(p, WC, "ch4_i_one");
        Quests.onTalk(p, WC, "ch4_i_three");
        check(stage(p, "ch4/fragments").equals("teller"), "isana topics: Isana's tale finished the storyteller's stage", f);
        Quests.onTalk(p, ST, "ch4_s_three");
        check(stage(p, "ch4/fragments").equals("done"), "isana topics: the fragments quest stayed open after both were heard: " + stage(p, "ch4/fragments"), f);
        notes.add("Isana topics: heard on the topic page, remembered across relog, healed on poll, heard-first order");
    }

    // ---------------------------------------------------------------- 既存のワールドの移行 (docs/review/chapter_per_titan_plan.md 2.3)
    // 段階 2 まで進んだ古いセーブ (記録は段階だけ。章の記録・拍子の記録は空) を読み込んだ人:
    //   章が三章、すでに遊んでいた人として題字の札は出ず、前の章の予兆は出ず、今の章 (三章) の拍だけが起きうる
    private static void migration(MinecraftServer server, ServerLevel level, List<String> f, List<String> notes) {
        ProgressData data = ProgressData.get(server);
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("skyabyss_story_migration".getBytes()), "[migration]"));
        ArcData arc = ArcData.of(p);
        arc.reset();
        Lore.reset(p);
        OpeningDirector.resetAsLoadedSave(p);
        data.markPersonal(p.getUUID(), Titans.STONE_WHALE);
        data.markPersonal(p.getUUID(), Titans.TIDE_SHELL);
        try {
            check(Chapters.of(p) == Chapters.STORM, "migration: a save at stage 2 should be chapter 3: " + Chapters.of(p), f);
            // 見回りが一度走ると「すでに遊んでいた人」になる (記録が伝承・クエストになくても、段階が先に進んでいる)
            OpeningDirector.run(level, p);
            check(OpeningDirector.state(p).legacy(), "migration: a stage-2 save should be treated as a player who already played", f);
            check(Chapters.of(p) == Chapters.STORM, "migration: the chapter changed after the opening check: " + Chapters.of(p), f);
            // 題字: すでに遊んでいた人には札を出さず、今の章までを出した扱いにする
            check(!ChapterCards.tick(p, Chapters.STORM, true, 1000), "migration: a legacy player was shown a chapter card", f);
            for (Chapters c : Chapters.values()) {
                check(arc.titleShown(c) == (c.number() <= Chapters.STORM.number()), "migration: legacy title flag wrong for " + c + ": " + arc.titleShown(c), f);
            }
            // 予兆: 今の章の拍だけ。何度か時刻を変えて呼んでも、前の章 (一・二章) の拍は起きず記録もされない
            level.setDayTime(6000);
            long t = 500_000;
            for (int i = 0; i < 12; i++) {
                long day = (i % 3 == 0) ? 6000 : (i % 3 == 1) ? 12000 : 18000;
                Beat b = Foreshadow.fireForCurrentChapter(p, t, day);
                check(b == null || b.chapter() == Chapters.STORM, "migration: a stage-2 player got a beat of " + (b == null ? null : b.chapter()), f);
                t += 20_000;
            }
            for (Chapters past : List.of(Chapters.WHALE, Chapters.TIDE)) {
                check(Foreshadow.count(p, past) == 0, "migration: past chapter " + past + " beats were recorded", f);
            }
            check(Foreshadow.count(p, Chapters.EMBER) == 0, "migration: a later chapter's beats were recorded", f);
            // 題字 (すでに遊んでいた人ではない、先の章で始めた人): 前の章は出さず、今の章だけを一度出す
            arc.reset();
            check(!ChapterCards.tick(p, Chapters.STORM, false, 2000), "migration: the title card came out without the delay", f);
            check(arc.titleShown(Chapters.WHALE) && arc.titleShown(Chapters.TIDE) && !arc.titleShown(Chapters.STORM),
                    "migration: past chapter titles should be skipped and the current one waits", f);
            check(ChapterCards.tick(p, Chapters.STORM, false, 2000 + ChapterCards.TITLE_DELAY), "migration: the current chapter's title card was not shown", f);
            check(!ChapterCards.tick(p, Chapters.STORM, false, 3000 + ChapterCards.TITLE_DELAY), "migration: the title card was shown twice", f);
            notes.add("migration: a stage-2 save is chapter 3, counts as an old player (no title card), gets no beats of chapters 1-2, and a non-legacy late starter gets only chapter 3's title");
        } finally {
            data.clearPersonal(p.getUUID(), Titans.TIDE_SHELL);
            data.clearPersonal(p.getUUID(), Titans.STONE_WHALE);
            arc.reset();
            Lore.reset(p);
            OpeningDirector.reset(p);
        }
    }

    private static int readyTicks;

    // 新しいワールドの直後はスポーン地点の塊がまだエンティティの動く状態でなく、足した NPC が見つからない。強制読み込みして待つ (最長 600 tick)
    static boolean chunkReady(MinecraftServer server, int offset) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos at = new BlockPos(spawn.getX() + offset, 300, spawn.getZ() + offset);
        level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
        if (level.isPositionEntityTicking(at) || ++readyTicks > 600) {
            readyTicks = 0;
            return true;
        }
        return false;
    }

    public static boolean ready(MinecraftServer server) {
        return chunkReady(server, 40);
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) {
            f.add(what);
        }
    }

    // ---------------------------------------------------------------- 4 体の順番・乗る資格・上限
    private static void order(MinecraftServer server, List<String> f, List<String> notes) {
        ProgressData data = ProgressData.get(server);
        ProgressRules.TITAN_ORDER.forEach(data::clearWorld);
        check(Titans.all().equals(List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)), "titan order is wrong", f);
        int[] caps = {15, 21, 27, 33, 40};
        for (int i = 0; i <= 4; i++) {
            check(ProgressRules.levelCap(i) == caps[i], "level cap at stage " + i + " is " + ProgressRules.levelCap(i), f);
        }
        check(Titans.recommendedLevel(Titans.THUNDER_WING) == 24 && Titans.recommendedLevel(Titans.EMBER) == 30, "recommended levels of 3 and 4", f);
        // 世界の進み: 前の心臓を誰かが止めると次が現れる
        ServerLevel ow = server.overworld();
        FakePlayer a = new FakePlayer(ow, new GameProfile(UUID.randomUUID(), "story_test_a"));
        FakePlayer b = new FakePlayer(ow, new GameProfile(UUID.randomUUID(), "story_test_b"));
        for (int i = 0; i < 4; i++) {
            String t = ProgressRules.TITAN_ORDER.get(i);
            check(Titans.mayAppear(server, t), t + " should be allowed to appear after the previous heart", f);
            if (i + 1 < 4) {
                check(!Titans.mayAppear(server, ProgressRules.TITAN_ORDER.get(i + 1)), ProgressRules.TITAN_ORDER.get(i + 1) + " appeared too early", f);
            }
            check(Progression.canBoard(a, t), "a could not board " + t + " after stopping the previous hearts", f);
            if (i > 0) {
                check(!Progression.canBoard(b, t), "b boarded " + t + " without stopping the previous heart", f);
            }
            Progression.onTitanStopped(server, List.of(a.getUUID()), t);
            check(Progression.stage(a) == i + 1 && Progression.worldStage(server) == i + 1, "stage after " + t + " is " + Progression.stage(a), f);
            check(ProgressRules.levelCap(Progression.stage(a)) == caps[i + 1], "cap after " + t, f);
        }
        // b は岩鯨だけ。仲間 (a) がいても雷翼には乗れない
        Progression.onTitanStopped(server, List.of(b.getUUID()), Titans.STONE_WHALE);
        check(Progression.canBoard(b, Titans.TIDE_SHELL) && !Progression.canBoard(b, Titans.THUNDER_WING), "b's boarding after the whale", f);
        // 後の巨神の背中の口: 四体とも背中が登録されていて (Titans.bindBack)、雷翼の背へのディメンション移動は前の心臓を止めた人だけ通す
        for (String t : Titans.all()) {
            check(Titans.hasBack(t), t + " has no back bound", f);
        }
        ResourceKey<Level> stormBack = jp.revati.skyabyss.titan.storm.StormTravel.STORM_BACK;
        EntityTravelToDimensionEvent denied = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(b, stormBack));
        EntityTravelToDimensionEvent allowed = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(a, stormBack));
        check(denied.isCanceled() && !allowed.isCanceled(), "travel to the thunder back was not gated", f);
        check(Titans.STONE_WHALE.equals(Progression.titanOfBack(jp.revati.skyabyss.titan.TitanTravel.TITAN_BACK))
                && Titans.TIDE_SHELL.equals(Progression.titanOfBack(jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK))
                && Titans.THUNDER_WING.equals(Progression.titanOfBack(jp.revati.skyabyss.titan.storm.StormTravel.STORM_BACK)), "built-in backs are missing", f);
        notes.add("four titans wake in order; only those who stopped the previous heart board; caps 15/21/27/33/40; later backs plug in");
    }

    // ---------------------------------------------------------------- メインクエスト (雲の下の歌のあと → 天の鍵)
    private static void mainQuests(MinecraftServer server, ServerLevel level, List<net.minecraft.world.entity.Entity> spawned, List<String> f,
                                   List<String> notes) {
        DialogueManager.loadErrors().forEach(e -> f.add("dialogue load: " + e));
        QuestManager.loadErrors().forEach(e -> f.add("quest load: " + e));
        for (String id : STORY_QUESTS) {
            check(QuestManager.quest(id) != null, "missing quest " + id, f);
        }
        for (String lore : List.of("chapter1/thunder_wing", "chapter1/ember", SkyKey.LORE, "chapter1/fifth_verse", "chapter1/storm_path")) {
            check(jp.revati.skyabyss.lore.LoreManager.catalog().entry(lore) != null, "missing lore " + lore, f);
        }
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos at = new BlockPos(spawn.getX() + 40, 300, spawn.getZ() + 40);
        NpcEntity st = NpcSetup.STORYTELLER.get().create(level);
        NpcEntity wc = NpcSetup.WHALE_CHASER.get().create(level);
        if (st == null || wc == null) {
            f.add("npcs could not be created");
            return;
        }
        st.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        wc.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        st.setNoGravity(true);
        wc.setNoGravity(true);
        level.addFreshEntity(st);
        level.addFreshEntity(wc);
        check(level.getEntity(st.getId()) == st && level.getEntity(wc.getId()) == wc, "the npcs are not in the entity lookup (is the chunk entity-ticking?)", f);
        spawned.add(st);
        spawned.add(wc);
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a1e0b3c-0000-4000-8000-00000000b001"), "[story_test]"));
        Lore.reset(p);
        p.getInventory().clearContent();
        Quests.forgetAll(p);
        p.setData(NpcSetup.BONDS, new NpcBonds());
        NpcBonds bonds = NpcBonds.of(p);
        bonds.bond(ST).flags().add("met");
        bonds.bond(WC).flags().add("met");
        p.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5);
        StoryData.get(server).clear(p.getUUID());
        ProgressData.get(server).clearPersonal(p.getUUID(), Titans.STONE_WHALE);
        ProgressRules.TITAN_ORDER.forEach(t -> ProgressData.get(server).clearPersonal(p.getUUID(), t));

        // 雲の下の歌を終えても、岩鯨の心臓を自分で止めるまでは潮殻の章は始まらない
        Quests.start(p, "main_song");
        for (int i = 0; i < 10 && !stage(p, "main_song").equals("done"); i++) {
            Quests.forceStage(p, "main_song");
        }
        Quests.poll(p, true);
        check(stage(p, "main_song").equals("done") && stage(p, "main_tide").equals("none"), "main_tide started before the whale's heart", f);
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.STONE_WHALE);
        Quests.poll(p, true);
        check(stage(p, "main_tide").equals("rumor"), "main_tide did not start after the whale: " + stage(p, "main_tide"), f);
        check(Titans.STONE_WHALE.equals(ProgressRules.TITAN_ORDER.get(0)) && "main_tide".equals(QuestLog.of(p).tracked()), "the new main quest is not tracked", f);

        // 潮殻: イサナの噂 → 見る → 渡る → 碑文 2 つ → 心臓 → 報告 (語り部)
        talk(p, wc, "ms_c_tide", f);
        next(p);
        check(stage(p, "main_tide").equals("see"), "Isana's rumor did not advance main_tide: " + stage(p, "main_tide"), f);
        check(shown(p, WC, "count"), "the low-tide offer should show while sub_tide_watch is not taken", f);
        pick(p, WC, "go", f);
        Lore.unlock(p, "chapter1/tide_shell", false);
        Quests.poll(p, true);
        Lore.unlock(p, "chapter1/tide_ruins", false);
        Quests.poll(p, true);
        Lore.unlock(p, "chapter1/inscription_tide_beach", false);
        Quests.poll(p, true);
        check(stage(p, "main_tide").equals("inscriptions") && QuestLog.of(p).entry("main_tide").progress(0) == 1, "inscriptions should be 1/2", f);
        Lore.unlock(p, "chapter1/inscription_tide_hub", false);
        Quests.poll(p, true);
        check(stage(p, "main_tide").equals("heart"), "two inscriptions did not advance to the heart: " + stage(p, "main_tide"), f);
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.TIDE_SHELL);
        Quests.poll(p, true);
        check(stage(p, "main_tide").equals("report"), "stilling the tide heart did not advance to report", f);
        talk(p, st, "ms_tide_back", f);
        next(p);
        check(stage(p, "main_tide").equals("done"), "main_tide did not finish at the storyteller", f);
        // 報告の続き: 村の記憶 → 数え歌のふたつめ (dialogue/ch2/storyteller_trail.json)
        next(p);
        next(p);
        check(node(p).equals("ms_tide_verse"), "the tide report should end at the second verse: " + node(p), f);
        pick(p, ST, "ok", f);

        // 雷翼: 語り部の言い伝え → 見る (担当が伝承を記す) → 心臓 → 報告 (イサナ)
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("rumor"), "main_thunder did not start: " + stage(p, "main_thunder"), f);
        talk(p, st, "ms_thunder", f);
        next(p);
        check(stage(p, "main_thunder").equals("see"), "the storyteller's tale did not advance main_thunder", f);
        pick(p, ST, "why", f);
        check(node(p).equals("ms_thunder_why"), "the 'why' branch did not open", f);
        DialogueService.close(p);
        Lore.unlock(p, "chapter1/thunder_wing", false);
        Quests.poll(p, true);
        // 見たあとは「背へ上がる」。背中・碑文・風鳴り石の段は titan/storm/lore/StormLoreSelfTest が通す。心臓を先に止めれば飛ばす
        check(stage(p, "main_thunder").equals("board"), "seeing the thunder wing did not advance: " + stage(p, "main_thunder"), f);
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("board"), "the thunder board stage passed without boarding", f);
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.THUNDER_WING);
        Quests.poll(p, true);
        talk(p, wc, "ms_c_thunder_back", f);
        check(stage(p, "main_thunder").equals("done"), "main_thunder did not finish at Isana: " + stage(p, "main_thunder"), f);
        pick(p, WC, "ok", f);

        // 余燼: イサナの話 → 見る → 心臓 → 欠片がそろって鍵 → 天の鍵 (語り部)
        Quests.poll(p, true);
        check(stage(p, "main_ember").equals("rumor"), "main_ember did not start: " + stage(p, "main_ember"), f);
        talk(p, wc, "ms_c_ember", f);
        next(p);
        pick(p, WC, "go", f);
        Lore.unlock(p, "chapter1/ember", false);
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.EMBER);
        Quests.poll(p, true);
        check(stage(p, "main_ember").equals("key"), "main_ember should wait for the key: " + stage(p, "main_ember"), f);

        // 天の鍵: 段階 4 でも欠片が 3 つでは組まない。4 つで組み、欠片は消え、鍵は一つ
        p.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 3));
        check(Progression.stage(p) == 4 && !SkyKey.ready(p), "the key formed with three fragments", f);
        p.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 1));
        check(SkyKey.ready(p), "four fragments at stage 4 should be ready", f);
        check(SkyKey.assemble(p, false), "assemble failed", f);
        check(SkyKey.fragmentsHeld(p) == 0 && p.getInventory().countItem(StorySetup.SKY_KEY.get()) + jp.revati.skyabyss.pouch.Pouch.count(p, StorySetup.SKY_KEY.get()) == 1,
                "fragments were not turned into one key", f);
        check(Lore.state(p).has(SkyKey.LORE) && SkyKey.assembled(p), "the key was not recorded", f);
        check(!SkyKey.assemble(p, false) && p.getInventory().countItem(StorySetup.SKY_KEY.get()) + jp.revati.skyabyss.pouch.Pouch.count(p, StorySetup.SKY_KEY.get()) == 1,
                "the key formed twice", f);
        check(!Keepsakes.fragmentMissing(p), "after the key, the whale heart would give a lost fragment again", f);
        check(Keepsakes.untossable(new ItemStack(StorySetup.SKY_KEY.get())), "the key can be tossed", f);
        // 段階が足りない人は、欠片を 4 つ持っていても組まない (コマンドで配った欠片など)
        FakePlayer q = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "story_test_q"));
        q.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 4));
        check(!SkyKey.ready(q), "a player at stage 0 with four fragments was ready", f);
        // 欠片は持ち物だけでなく、手もと (エンダーチェスト・箱の中) も数え、そろうときは同じ範囲から取る (M11)
        q.getInventory().clearContent();
        q.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 2));
        q.getEnderChestInventory().setItem(0, new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 1));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 1))));
        q.getInventory().add(shulker);
        check(SkyKey.fragmentsHeld(q) == 4, "fragments in the ender chest and a shulker box were not counted: " + SkyKey.fragmentsHeld(q), f);
        check(Keepsakes.take(q, ModItems.SKY_KEY_FRAGMENT.get(), jp.revati.skyabyss.record.KeepsakeBonds.mine(q), 4) == 4 && SkyKey.fragmentsHeld(q) == 0,
                "taking fragments from every hand-held place left some behind: " + SkyKey.fragmentsHeld(q), f);
        q.getInventory().clearContent();
        q.getEnderChestInventory().clearContent();
        check(!SkyKey.busy(q), "an idle player was busy", f);

        Quests.poll(p, true);
        check(stage(p, "main_ember").equals("done") && stage(p, "main_key").equals("show"), "the key did not open main_key: " + stage(p, "main_key"), f);
        talk(p, st, "ms_key", f);
        next(p);
        check(node(p).equals("ms_key2"), "expected ms_key2, got " + node(p), f);
        next(p);
        check(node(p).equals("ms_key_four"), "the fourth verse (ms_key_four) did not follow: " + node(p), f);
        next(p);
        check(Lore.state(p).has("chapter1/fifth_verse"), "the fifth verse was not recorded", f);
        next(p);
        check(node(p).equals("ms_key_return"), "the storyteller's answer (ms_key_return) did not follow: " + node(p), f);
        next(p);
        check(node(p).equals("ms_key4") && stage(p, "main_key").equals("show"), "main_key should wait for Isana's answer: " + node(p) + " / " + stage(p, "main_key"), f);
        pick(p, ST, "ok", f);
        // イサナは一度だけ鍵の話をする。答え (ms_c_key3) でメイン「天の鍵」が終わる (34 の 4.5: 二人の別々の答え)
        talk(p, wc, "ms_c_key", f);
        next(p);
        next(p);
        check(node(p).equals("ms_c_key3") && stage(p, "main_key").equals("done"), "main_key did not finish at Isana's answer: " + node(p) + " / " + stage(p, "main_key"), f);
        pick(p, WC, "ok", f);
        DialogueService.open(p, wc);
        check(!node(p).equals("ms_c_key"), "Isana talked about the key twice", f);
        DialogueService.close(p);
        long mains = QuestManager.all().stream().filter(QuestDef::main).filter(d -> QuestLog.of(p).entry(d.id()) != null && QuestLog.of(p).entry(d.id()).done)
                .count();
        check(mains == 5, "expected five finished main quests, got " + mains, f);
        notes.add("main quests from the tide shell to the sky key: Isana → storyteller → Isana → storyteller, key at four fragments");

        subQuests(server, level, p, st, wc, f, notes);
    }

    private static void subQuests(MinecraftServer server, ServerLevel level, FakePlayer p, NpcEntity st, NpcEntity wc, List<String> f, List<String> notes) {
        // 鯨追いの古い地図: 雷雨の高い所 → 報告 → 伝承「嵐の通り道」
        DialogueService.open(p, wc);
        pick(p, WC, "errands", f);
        check(shown(p, WC, "map"), "the old map offer should show at stage 2+", f);
        pick(p, WC, "map", f);
        next(p);
        pick(p, WC, "accept", f);
        check(stage(p, "sub_old_map").equals("watch"), "sub_old_map did not start", f);
        level.setWeatherParameters(0, 0, false, false);
        Quests.poll(p, true);
        check(stage(p, "sub_old_map").equals("watch"), "clear weather counted as a storm", f);
        level.setWeatherParameters(0, 6000, true, true);
        Quests.poll(p, true);
        check(stage(p, "sub_old_map").equals("tell"), "a thunderstorm at height 300 did not count", f);
        talk(p, wc, "ms_c_map_done", f);
        pick(p, WC, "wing", f);
        check(stage(p, "sub_old_map").equals("done") && Lore.state(p).has("chapter1/storm_path"), "sub_old_map did not finish", f);
        // 切れ端のあと、風見鶏の話が二つ (12 の 2.6 節)
        next(p);
        next(p);
        pick(p, WC, "ok", f);
        level.setWeatherParameters(6000, 0, false, false);

        // 四章の語り部の話 (ch4/fragments) は、話しかけると先に始まる入口。ここは頼みごとの流れを見るので、あらかじめ済ませておく (13 の 17.5)
        jp.revati.skyabyss.quest.Quests.forget(p, "ch4/fragments");
        // 冷めない炭: ネザーへ行き、マグマクリーム 4 つ → 語り部
        DialogueService.open(p, st);
        pick(p, ST, "errands", f);
        pick(p, ST, "coal", f);
        next(p);
        pick(p, ST, "accept", f);
        check(stage(p, "sub_warm_coal").equals("fetch"), "sub_warm_coal did not start", f);
        p.getInventory().add(new ItemStack(Items.MAGMA_CREAM, 4));
        Quests.poll(p, true);
        check(stage(p, "sub_warm_coal").equals("fetch"), "the coal quest passed without the Nether", f);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            p.setServerLevel(nether);
            Quests.poll(p, true);
            p.setServerLevel(level);
        }
        check(stage(p, "sub_warm_coal").equals("tell"), "visiting the Nether with magma cream did not advance: " + stage(p, "sub_warm_coal"), f);
        talk(p, st, "ms_coal_done", f);
        next(p);
        check(stage(p, "sub_warm_coal").equals("done") && p.getInventory().countItem(Items.MAGMA_CREAM) == 0
                && p.getInventory().countItem(Items.CHARCOAL) == 1, "sub_warm_coal did not take the cream and give the coal", f);
        DialogueService.close(p);
        // 段階で開く: 板の日替わり・週替わりは段階 2 から
        check(QuestManager.quest("daily_tide_rounds").minStage() == 2 && QuestManager.quest("weekly_tide_heart").minStage() == 2,
                "tide board quests should open at stage 2", f);
        notes.add("sub quests: the old map (thunderstorm, height) and the warm coal (Nether, magma cream)");
    }

    // ---------------------------------------------------------------- 会話の手伝い (NpcQuestSelfTest と同じ形)
    private static void talk(FakePlayer p, NpcEntity npc, String expected, List<String> f) {
        quietTrails(p);
        DialogueService.open(p, npc);
        check(node(p).equals(expected), "expected node " + expected + ", got " + node(p), f);
    }

    // 章の地上の頼み (ch<n>/…。支度の段・サブ) は、章が開くと自動で出て、会話の入口を先に取る。この試験はメインの筋だけを見るので、
    // 話す前に、まだ済んでいない章の頼みを外し、章の終わりの一言 (イサナの決意・数え歌のみっつ) は聞いたことにする
    private static void quietTrails(FakePlayer p) {
        QuestLog log = QuestLog.of(p);
        for (QuestDef def : QuestManager.all()) {
            String id = def.id();
            QuestLog.Entry e = log.entry(id);
            if (e != null && !e.done && id.matches("ch[1-4]/.*")) {
                Quests.forget(p, id);
            }
        }
        NpcBonds bonds = NpcBonds.of(p);
        bonds.bond(WC).flags().add("ch3g_stay_told");
        bonds.bond(ST).flags().add("ch3g_verse_told");
    }

    private static String node(FakePlayer p) {
        String n = DialogueService.currentNode(p);
        return n == null ? "(closed)" : n;
    }

    private static void next(FakePlayer p) {
        DialogueService.choose(p, node(p), DialogueService.CONTINUE);
    }

    private static boolean shown(FakePlayer p, String role, String optionId) {
        Dialogue d = DialogueManager.dialogue(role);
        String nodeId = DialogueService.currentNode(p);
        if (d == null || nodeId == null) {
            return false;
        }
        Dialogue.Node n = d.nodes().get(nodeId);
        for (int i = 0; i < n.options().size(); i++) {
            if (n.options().get(i).id().equals(optionId)) {
                return DialogueService.shownOptions(p).contains(i);
            }
        }
        return false;
    }

    private static void pick(FakePlayer p, String role, String optionId, List<String> f) {
        Dialogue d = DialogueManager.dialogue(role);
        String nodeId = DialogueService.currentNode(p);
        if (d == null || nodeId == null) {
            f.add("no open dialogue to pick " + optionId + " from");
            return;
        }
        Dialogue.Node n = d.nodes().get(nodeId);
        for (int i = 0; i < n.options().size(); i++) {
            if (n.options().get(i).id().equals(optionId)) {
                if (!DialogueService.shownOptions(p).contains(i)) {
                    f.add("option " + optionId + " is hidden at " + nodeId);
                    return;
                }
                DialogueService.choose(p, nodeId, i);
                return;
            }
        }
        f.add("option " + optionId + " does not exist at " + nodeId);
    }

    private static String stage(FakePlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || def == null) {
            return "none";
        }
        return e.done ? "done" : def.stages().get(e.stage).id();
    }
}
