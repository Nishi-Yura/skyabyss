package jp.revati.skyabyss.story;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LoreCatalog;
import jp.revati.skyabyss.lore.LoreChapter;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.progress.ProgressRules;
import net.minecraft.server.MinecraftServer;

// 章の写像 (Chapters) の自己テスト: 全 5 段階 × 序章を終えた/終えていない × 鍵、レベルの表、巨神の対応、伝承の章との一致
public final class ChaptersSelfTest {
    private ChaptersSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            run(failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("chapters selftest crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST chapters PASSED (stage x prologue mapping, levels, titans, lore chapters)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST chapters FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(List<String> failures) {
        // 段階 × 序章を終えたか。序章は段階 0 で序章を終えていないときだけ
        Chapters[][] expect = {
                {Chapters.PROLOGUE, Chapters.WHALE},
                {Chapters.TIDE, Chapters.TIDE},
                {Chapters.STORM, Chapters.STORM},
                {Chapters.EMBER, Chapters.EMBER},
                {Chapters.EMBER, Chapters.EMBER}};
        for (int stage = 0; stage < expect.length; stage++) {
            for (int done = 0; done < 2; done++) {
                Chapters got = Chapters.of(stage, done == 1);
                check(got == expect[stage][done], "stage " + stage + " prologueDone=" + (done == 1) + " -> " + got + ", expected " + expect[stage][done], failures);
                // 鍵がそろっていなければ 2 引数と同じ
                check(Chapters.of(stage, done == 1, false) == got, "3-arg without key differs at stage " + stage, failures);
            }
        }
        // 鍵がそろうと五章 (段階 4 だけ)
        check(Chapters.of(4, true, true) == Chapters.ABYSS && Chapters.of(4, false, true) == Chapters.ABYSS, "stage 4 + key should be the abyss chapter", failures);
        check(Chapters.of(3, true, true) == Chapters.EMBER, "the key flag alone must not skip a chapter before stage 4", failures);
        check(Chapters.of(-1, false) == Chapters.PROLOGUE && Chapters.of(99, true) == Chapters.EMBER, "out-of-range stages should clamp", failures);
        // 段階は一章ずつ進み、戻らない
        for (int stage = 0; stage < 4; stage++) {
            check(Chapters.of(stage, true).ordinal() <= Chapters.of(stage + 1, true).ordinal(), "chapter went backwards at stage " + stage, failures);
        }

        // 番号と順番
        Chapters[] all = Chapters.values();
        check(all.length == 8, "8 chapters expected (prologue, 1-4, 5, 6, finale), found " + all.length, failures);
        for (int i = 0; i < all.length; i++) {
            check(all[i].number() == i, all[i] + " number", failures);
            check(Chapters.byId(all[i].id()) == all[i], all[i] + " byId", failures);
            check((i + 1 < all.length ? all[i + 1] : null) == all[i].next(), all[i] + " next", failures);
            check(all[i].reserved() == (i >= 7), all[i] + " reserved (終章だけ)", failures);
        }
        check(Chapters.PROLOGUE.id().equals("prologue") && Chapters.ABYSS.id().equals("chapter2") && Chapters.MOON.id().equals("chapter3")
                && Chapters.FINALE.id().equals("finale"), "ids of the prologue and the later chapters", failures);
        check(Chapters.written().size() == 5 && Chapters.written().stream().noneMatch(Chapters::reserved), "written chapters", failures);

        // 巨神と段階とレベル (ProgressRules の表と一致)
        for (int i = 0; i < ProgressRules.TITAN_ORDER.size(); i++) {
            Chapters c = all[i + 1];
            check(ProgressRules.TITAN_ORDER.get(i).equals(c.titan()), c + " titan " + c.titan(), failures);
            check(c.stage() == i, c + " stage " + c.stage(), failures);
            check(c.levelTo() == ProgressRules.TITAN_LEVEL[i], c + " levelTo " + c.levelTo(), failures);
            check(c.levelCap() == ProgressRules.LEVEL_CAP[i], c + " levelCap " + c.levelCap(), failures);
            check(c.levelFrom() < c.levelTo() && c.levelTo() < c.levelCap(), c + " level range order", failures);
            check(c.titanChapter(), c + " titanChapter", failures);
        }
        check(Chapters.PROLOGUE.titan() == null && Chapters.PROLOGUE.levelCap() == ProgressRules.LEVEL_CAP[0] && !Chapters.PROLOGUE.titanChapter(), "prologue", failures);
        check(Chapters.WHALE.levelCap() == Chapters.PROLOGUE.levelCap(), "prologue and chapter 1 share the stage-0 cap", failures);
        // 五章の始まりは四章の帯の終わり (levelTo) から上限 (levelCap) のあいだ = 余燼を止める前に届く。番人の前の上限 (40) より下
        check(Chapters.ABYSS.stage() == 4 && Chapters.ABYSS.levelFrom() >= Chapters.EMBER.levelTo() && Chapters.ABYSS.levelFrom() <= Chapters.EMBER.levelCap()
                && Chapters.ABYSS.levelFrom() < ProgressRules.LEVEL_CAP[4] && Chapters.ABYSS.levelTo() == ProgressRules.ABYSS_CAP,
                "abyss chapter should start where chapter 4 ends (" + Chapters.ABYSS.levelFrom() + ") and end at the warden cap", failures);
        // 五章の一度きりの経験値 (番人の前): 章のクエスト (番人と報告の段・メインの報酬を除く) + 印 12 + 層の主 4 が、
        // 帯の始まりから番人の前の上限 (Lv 32 → 40) の 4〜8 割。残りを敵と繰り返しで (docs/design/36 の 7.1)
        long band = jp.revati.skyabyss.rpg.GrowthRules.totalXpFor(ProgressRules.LEVEL_CAP[4]) - jp.revati.skyabyss.rpg.GrowthRules.totalXpFor(Chapters.ABYSS.levelFrom());
        long once = 12L * jp.revati.skyabyss.story.ch5.layers.AbyssMarks.MARK_XP;
        for (int xp : jp.revati.skyabyss.story.ch5.layers.AbyssLords.LORD_XP) {
            once += xp;
        }
        for (jp.revati.skyabyss.quest.QuestDef q : jp.revati.skyabyss.quest.QuestManager.all()) {
            if (!Chapters.ABYSS.id().equals(q.chapter()) || q.repeats()) {
                continue;
            }
            boolean main = q.id().equals("main_abyss");
            once += main ? 0 : q.reward().xp();
            for (jp.revati.skyabyss.quest.QuestDef.Stage st : q.stages()) {
                if (main && (st.id().equals("warden") || st.id().equals("heart") || st.id().equals("report"))) {
                    continue;
                }
                once += st.xp();
            }
        }
        check(band > 0 && once >= band * 4 / 10 && once <= band * 8 / 10, "chapter 5 one-time xp before the warden (" + once + ") should be 40-80% of the "
                + Chapters.ABYSS.levelFrom() + "-" + ProgressRules.LEVEL_CAP[4] + " band (" + band + ")", failures);

        // 伝承の章との一致: 章の ID ごとに伝承の章があり、項目の "chapter" 欄はすべてどれかの章を指す
        LoreCatalog catalog = LoreManager.catalog();
        for (Chapters c : all) {
            LoreChapter lc = catalog.chapters().stream().filter(x -> x.id().equals(c.id())).findFirst().orElse(null);
            check(lc != null, "no lore chapter for " + c.id(), failures);
            if (lc != null) {
                check(lc.order() == c.number(), "lore chapter order of " + c.id() + " is " + lc.order(), failures);
                check(lc.tabKey().equals(c.tabKey()) && lc.nameKey().equals(c.nameKey()), c.id() + " lang keys", failures);
            }
            long n = catalog.inChapter(c.id()).stream().filter(LoreEntry::unlockable).count();
            check(c.reserved() ? n == 0 : n > 0, c.id() + " unlockable entries: " + n, failures);
        }
        check(catalog.chapters().size() == all.length, "lore chapters should be exactly the 8 chapters (" + catalog.chapters().size() + ")", failures);
        check(Chapters.byId("chapter1") == null, "the old chapter1 id must be gone", failures);
        for (LoreEntry e : catalog.entries()) {
            check(Chapters.byId(e.chapter()) != null, e.id() + " points at unknown chapter " + e.chapter(), failures);
        }
        // 旧 chapter1/ の ID はそのまま残り、巨神ごとの章にいる
        String[][] pinned = {
                {"chapter1/stone_whale", "titan_whale"}, {"chapter1/whale_chaser", "titan_whale"}, {"chapter1/heart_stilled", "titan_whale"},
                {"chapter1/tide_shell", "titan_tide"}, {"chapter1/tide_hours", "titan_tide"},
                {"chapter1/thunder_wing", "titan_storm"}, {"chapter1/storm_path", "titan_storm"},
                {"chapter1/ember", "titan_ember"}, {"chapter1/fifth_verse", "titan_ember"}, {"chapter1/sky_key_whole", "titan_ember"},
                {"prologue/camp", "prologue"}, {"chapter2/unknown_1", "chapter2"}, {"finale/unknown_1", "finale"}};
        for (String[] p : pinned) {
            LoreEntry e = catalog.entry(p[0]);
            check(e != null && e.chapter().equals(p[1]), p[0] + " should sit in " + p[1] + " (" + (e == null ? "missing" : e.chapter()) + ")", failures);
        }
    }
}
