package jp.revati.skyabyss.story;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.lore.LoreWrap;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.npc.DialogueService;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 序盤の語り (担当 4) の自己テスト (SELFTEST opening story)。
//  - 新しい伝承の項目 6 つ (野営地・帳面の余白・足跡・足音・骨の丘・空の歌): 読み込み・日英の文・折り返し・記される・核心に触れない言葉
//  - 石板の看板の幅、開幕の一行、目的の帯の文 (7 つ。日本語 20 字、英語 52 字まで。「〜しよう」と次の行動を言い、操作のキーは書かない。骨の丘の前触れの字幕 4 つ)
//  - メイン「雲の下の歌」の始まり: 帳面を拾う → 煙の下の宿り (語り部の shadow0) → 空を見る → 語り部に尋ねる → イサナ、の通し。
//    合図の代わりに、伝承を記す (Lore.unlock) と会話の節に入ることで進める (拍子の担当が送るものと同じ)
//  - 影を先に見た人・空のことを先に尋ねた人は、序盤の二段を飛ばす
//  - 道すがらの見聞 (足跡・足音・骨・歌・野営地) を、語り部とイサナに話せる。一度話した話題は消える
//  - 17-10: イサナと会うまで、言伝ての板の頼みと語り部の頼みごとの話題は出ない
public final class OpeningStorySelfTest {
    private static final String ST = NpcSetup.STORYTELLER_ID;
    private static final String WC = NpcSetup.WHALE_CHASER_ID;
    private static final String[] LANGS = {"ja_jp", "en_us"};
    private static final List<String> LORE_IDS = List.of("prologue/camp", "prologue/the_book_margin", "prologue/footprints",
            "prologue/footsteps", "prologue/bone_hill", "prologue/sky_song");
    private static final List<String> OBJECTIVE_IDS = List.of("examine_camp", "follow_smoke", "watch_sky", "ask_storyteller", "find_isana",
            "see_whale", "wait_steps", "wait_whale", "bone_hill", "read_page");
    // 序盤で明かさない言葉 (巨神・見る者・ループ・天の鍵・ほかの巨神の名前)
    private static final List<String> SPOILERS = List.of("巨神", "見る者", "ループ", "天の鍵", "余燼", "潮殻", "雷翼", "titan", "Titan", "Watcher",
            "loop", "Sky Key", "Ember", "Tide Shell", "Thunder Wing");
    private static final int PAGE_W = 126;

    private OpeningStorySelfTest() {
    }

    // 新しいワールドの直後は、スポーン地点の塊がまだ「エンティティが動く」状態になっておらず、足した NPC が見つからない
    // (会話が開かない)。先に塊を強制読み込みにして、動く状態になるまで待つ (実際の遊びでは人の近くの塊は必ず動いている)
    public static boolean ready(MinecraftServer server) {
        return StorySelfTest.chunkReady(server, 0);
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
        try {
            lore(failures, notes);
            texts(failures, notes);
            play(server, spawned, failures, notes);
        } catch (RuntimeException e) {
            failures.add("exception: " + e);
            SkyAbyss.LOGGER.error("SELFTEST opening story exception", e);
        } finally {
            spawned.forEach(net.minecraft.world.entity.Entity::discard);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST opening story: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST opening story PASSED ({} lore entries, {} objective lines, main quest from the notebook, {} checks)",
                    LORE_IDS.size(), OBJECTIVE_IDS.size(), notes.size());
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST opening story FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String what, List<String> failures) {
        if (!ok) {
            failures.add(what);
        }
    }

    // ---------------------------------------------------------------- 伝承
    private static void lore(List<String> failures, List<String> notes) {
        check(LoreManager.catalog().problems().isEmpty(), "lore catalog problems: " + LoreManager.catalog().problems(), failures);
        for (String id : LORE_IDS) {
            LoreEntry e = LoreManager.catalog().entry(id);
            check(e != null && e.unlockable() && e.chapter().equals("prologue"), "lore entry " + id + " is missing or not unlockable", failures);
        }
        for (String lang : LANGS) {
            JsonObject json = json("/assets/skyabyss_lore/lang/" + lang + ".json", failures);
            JsonObject main = json("/assets/skyabyss/lang/" + lang + ".json", failures);
            if (json == null || main == null) {
                continue;
            }
            for (String id : LORE_IDS) {
                LoreEntry e = LoreManager.catalog().entry(id);
                if (e == null) {
                    continue;
                }
                String title = text(json, e.titleKey(), lang, failures);
                String body = text(json, e.bodyKey(), lang, failures);
                check(measure(title) <= PAGE_W, lang + ": the title of " + id + " is wider than the page (" + measure(title) + ")", failures);
                for (String s : List.of(title, body)) {
                    for (String bad : SPOILERS) {
                        check(!s.contains(bad), lang + ": " + id + " says too much (" + bad + ")", failures);
                    }
                }
                List<String> lines = new ArrayList<>();
                for (String paragraph : body.split("\n")) {
                    lines.addAll(LoreWrap.wrap(paragraph, PAGE_W, OpeningStorySelfTest::measure));
                }
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    check(measure(line) <= PAGE_W, lang + ": " + id + " has a line wider than the page: " + line, failures);
                    check(i == 0 || line.isEmpty() || "、。」』）".indexOf(line.charAt(0)) < 0, lang + ": " + id + " starts a line with punctuation: " + line, failures);
                }
                check(lines.size() <= 30, lang + ": " + id + " is too long (" + lines.size() + " lines)", failures);
                // 問いか物で終える
                String last = body.substring(body.lastIndexOf('\n') + 1);
                check(last.endsWith("？") || last.endsWith("?") || last.endsWith("。") || last.endsWith("."), lang + ": " + id + " does not end cleanly", failures);
                if (e.inscription()) {
                    text(json, e.inscriptionKey(), lang, failures);
                }
                if (!e.inscriptionSign().isEmpty()) {
                    for (String key : e.inscriptionKeys()) {
                        check(main.has(key), lang + ": " + id + " copies a sign line that does not exist: " + key, failures);
                    }
                }
            }
        }
        List<String> bad = SignFit.check(List.of("sign.skyabyss.lore_bone_slab"));
        check(bad.isEmpty(), "the bone slab does not fit a sign: " + bad, failures);
        check(SignFit.allSignKeys().contains("sign.skyabyss.lore_bone_slab"), "SignFit does not see the bone slab", failures);
        notes.add(LORE_IDS.size() + " lore entries read, fit the page, no spoilers; the bone slab fits a sign");
    }

    // ---------------------------------------------------------------- 開幕の一行・目的の帯の文
    private static void texts(List<String> failures, List<String> notes) {
        for (String lang : LANGS) {
            JsonObject json = json("/assets/skyabyss_opening/lang/" + lang + ".json", failures);
            if (json == null) {
                continue;
            }
            String line = text(json, "opening.skyabyss.line", lang, failures);
            check(line.codePointCount(0, line.length()) <= 24 && !line.contains("\n"), lang + ": the opening line is too long", failures);
            text(json, "opening.skyabyss.margin_line", lang, failures);
            for (String id : OBJECTIVE_IDS) {
                String s = text(json, "objective.skyabyss." + id, lang, failures);
                int max = lang.equals("ja_jp") ? 20 : 52;
                check(s.codePointCount(0, s.length()) <= max, lang + ": objective " + id + " is too long (" + s + ")", failures);
                for (String bad : SPOILERS) {
                    check(!s.contains(bad), lang + ": objective " + id + " says too much (" + bad + ")", failures);
                }
                for (String key : List.of("WASD", "Space", "スペース", "キー")) {
                    check(!s.contains(key), lang + ": objective " + id + " mentions a raw key (" + key + ")", failures);
                }
                // 名前や場所だけにせず、次の行動を「〜しよう」で言う (日本語)。英語は命令形 (動詞から始まる)
                if (lang.equals("ja_jp")) {
                    check(s.endsWith("う") || s.endsWith("）"), "ja_jp: objective " + id + " does not tell the player what to do (" + s + ")", failures);
                }
            }
            check(!json.has("opening.skyabyss.hint_move"), lang + ": the raw key hint is still there", failures);
            for (String cue : List.of("bone_rumor", "bone_far", "bone_mid", "bone_near")) {
                String s = text(json, "opening.skyabyss." + cue, lang, failures);
                for (String bad : SPOILERS) {
                    check(!s.contains(bad), lang + ": bone cue " + cue + " says too much (" + bad + ")", failures);
                }
            }
            // 歌のあとの「待つ」版の文 (クエストログと、帯が出ないときの目的): 日英ともにあり、日本語は「〜しよう」で終わる
            JsonObject npcText = json("/assets/skyabyss_npc/lang/" + lang + ".json", failures);
            if (npcText != null) {
                String wait = text(npcText, "quest.skyabyss.main_song.see_whale.see.wait", lang, failures);
                check(!lang.equals("ja_jp") || wait.endsWith("う"), lang + ": the waiting objective does not tell the player what to do (" + wait + ")", failures);
                for (String bad : SPOILERS) {
                    check(!wait.contains(bad), lang + ": the waiting objective says too much (" + bad + ")", failures);
                }
            }
            // メインの目的の文 (帯に出る) は、巨神 4 つを通して「〜しよう」で終わる (日本語)
            if (lang.equals("ja_jp")) {
                for (String ns : List.of("skyabyss_npc", "skyabyss_story")) {
                    JsonObject q = json("/assets/" + ns + "/lang/ja_jp.json", failures);
                    if (q == null) {
                        continue;
                    }
                    for (String key : q.keySet()) {
                        if (key.matches("quest\\.skyabyss\\.main_[a-z]+\\.[a-z_]+\\.[a-z_]+") && !key.endsWith(".journal") && !key.endsWith(".title")
                                && !key.endsWith(".summary") && !key.endsWith(".done") && !key.endsWith(".lead")) {
                            String v = q.get(key).getAsString();
                            check(v.endsWith("う"), "ja_jp: main objective " + key + " does not tell the player what to do (" + v + ")", failures);
                        }
                    }
                }
            }
        }
        notes.add("opening line and " + OBJECTIVE_IDS.size() + " objective-strip lines in ja_jp and en_us");
    }

    // ---------------------------------------------------------------- 通し
    private static FakePlayer player(ServerLevel level, int n, BlockPos at) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a1e0b3c-0000-4000-8000-0000000000" + String.format("%02d", n)),
                "[opening_test_" + n + "]"));
        Lore.reset(p);
        p.getInventory().clearContent();
        Quests.forgetAll(p);
        p.setData(NpcSetup.BONDS, new NpcBonds());
        p.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5);
        return p;
    }

    private static String node(FakePlayer p) {
        String n = DialogueService.currentNode(p);
        return n == null ? "(closed)" : n;
    }

    private static String stage(FakePlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || def == null) {
            return "none";
        }
        return e.done ? "done" : def.stages().get(e.stage).id();
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

    private static boolean pick(FakePlayer p, String role, String optionId, List<String> failures) {
        Dialogue d = DialogueManager.dialogue(role);
        String nodeId = DialogueService.currentNode(p);
        if (d == null || nodeId == null) {
            failures.add("no open dialogue to pick " + optionId + " from");
            return false;
        }
        Dialogue.Node n = d.nodes().get(nodeId);
        for (int i = 0; i < n.options().size(); i++) {
            if (n.options().get(i).id().equals(optionId)) {
                if (!DialogueService.shownOptions(p).contains(i)) {
                    failures.add("option " + optionId + " is hidden at " + nodeId);
                    return false;
                }
                DialogueService.choose(p, nodeId, i);
                return true;
            }
        }
        failures.add("option " + optionId + " does not exist at " + nodeId);
        return false;
    }

    // 語り部と話している最中に空の影が過ぎる (影を見る・頁を読むで伝承が記される): 会話の選択肢が古いまま取り残されず、
    // 目的が止まらず、序盤の拍子 (破れた頁) が会話に割り込まない
    private static void shadowDuringTalk(ServerLevel level, NpcEntity st, List<String> failures, List<String> notes) {
        BlockPos at = st.blockPosition();
        FakePlayer p = player(level, 62, at);
        FakePlayer other = player(level, 63, at.offset(40, 0, 0));
        OpeningDirector.reset(p);
        OpeningDirector.force(p, jp.revati.skyabyss.opening.OpeningBeat.CAMP);
        Quests.poll(p, true);
        check(stage(p, "main_song").equals("follow_smoke"), "talk-through: the main quest did not start: " + stage(p, "main_song"), failures);
        DialogueService.open(p, st);
        check(node(p).equals("shadow0") && stage(p, "main_song").equals("watch_sky"), "talk-through: expected shadow0 / watch_sky, got " + node(p) + " / " + stage(p, "main_song"), failures);
        next(p);
        pick(p, ST, "ok", failures);
        check(node(p).equals("menu") && !shown(p, ST, "shadow_seen"), "talk-through: the menu before the shadow shows the shadow topic", failures);
        // 話している人には頁を落とさない。もう一人 (話していない) には落とせる
        check(OpeningDirector.pageBusy(p) && !OpeningDirector.pageBusy(other), "talk-through: the page route is not paused for the talker only", failures);
        // 話している最中に影が済み、頁が読まれて伝承が記された
        Lore.unlock(p, "prologue/shadow", false);
        Quests.poll(p, true);
        check(stage(p, "main_song").equals("ask_storyteller"), "talk-through: the shadow did not advance the quest during the talk: " + stage(p, "main_song"), failures);
        check(node(p).equals("menu") && !shown(p, ST, "shadow_seen"), "talk-through: expected the stale menu before the refresh", failures);
        check(DialogueService.refreshForTest(p) && shown(p, ST, "shadow_seen"), "talk-through: the open menu did not gain the shadow topic after the shadow", failures);
        check(!DialogueService.refreshForTest(p), "talk-through: the menu was re-sent without any change", failures);
        // 古い画面から押された選択も、会話の記録を失わない: 続けて話を進めれば目的が進む
        pick(p, ST, "shadow_seen", failures);
        next(p);
        next(p);
        check(node(p).equals("shadow3") && stage(p, "main_song").equals("find_chaser"), "talk-through: the talk after the shadow did not advance the quest: " + stage(p, "main_song"), failures);
        pick(p, ST, "go", failures);
        check(!OpeningDirector.pageBusy(p), "talk-through: the page route stayed paused after the talk", failures);
        DialogueService.close(p);
        OpeningDirector.reset(p);
        notes.add("talk through the shadow: open menu refreshed, quest advanced, page route paused only while talking");
    }

    private static void next(FakePlayer p) {
        DialogueService.choose(p, node(p), DialogueService.CONTINUE);
    }

    private static void play(MinecraftServer server, List<net.minecraft.world.entity.Entity> spawned, List<String> failures, List<String> notes) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos at = new BlockPos(spawn.getX(), 300, spawn.getZ());
        NpcEntity st = NpcSetup.STORYTELLER.get().create(level);
        NpcEntity wc = NpcSetup.WHALE_CHASER.get().create(level);
        if (st == null || wc == null) {
            failures.add("npc entities could not be created");
            return;
        }
        st.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        wc.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        st.setNoGravity(true);
        wc.setNoGravity(true);
        level.addFreshEntity(st);
        level.addFreshEntity(wc);
        check(level.getEntity(st.getId()) == st && level.getEntity(wc.getId()) == wc, "the npcs are not in the entity lookup (is the chunk entity-ticking?)", failures);
        spawned.add(st);
        spawned.add(wc);

        QuestDef main = QuestManager.quest("main_song");
        check(main != null && main.stages().size() >= 3 && main.stages().get(0).id().equals("follow_smoke")
                && main.stages().get(1).id().equals("watch_sky") && main.stages().get(2).id().equals("ask_storyteller"),
                "main_song should begin follow_smoke, watch_sky, ask_storyteller", failures);

        // ---- A: 帳面を拾う → 宿り (語り部) → 空の影 → 語り部に尋ねる → イサナ
        FakePlayer a = player(level, 61, at);
        check(stage(a, "main_song").equals("none"), "the main quest should not start by itself", failures);
        Lore.unlock(a, Lore.BOOK_ENTRY, false);
        Quests.poll(a, true);
        check(stage(a, "main_song").equals("follow_smoke"), "picking up the notebook should start the main quest at follow_smoke: " + stage(a, "main_song"), failures);
        check(QuestLog.of(a).tracked().equals("main_song"), "the main quest should be tracked", failures);
        // 序盤のサブクエストは、イサナと会うまで出ない (17-10)
        for (QuestDef def : QuestManager.all()) {
            // 初日のお使い (story/ch1) は、日 1 から開く例外
            if (def.board() && !def.id().startsWith("ch1/")) {
                check(!def.unlock().test(StoryContext.of(a)), "board quest " + def.id() + " is open before meeting Isana", failures);
            }
        }
        DialogueService.open(a, st);
        check(node(a).equals("shadow0"), "the first talk after the notebook should open shadow0, got " + node(a), failures);
        check(stage(a, "main_song").equals("watch_sky"), "reaching the storyteller did not advance to watch_sky: " + stage(a, "main_song"), failures);
        next(a);
        check(node(a).equals("shadow0_b") && shown(a, ST, "ok") && shown(a, ST, "why"), "shadow0_b should offer ok and why, got " + node(a), failures);
        pick(a, ST, "why", failures);
        check(node(a).equals("shadow0_why"), "expected shadow0_why, got " + node(a), failures);
        pick(a, ST, "ok", failures);
        check(node(a).equals("menu"), "shadow0_why should lead to the menu, got " + node(a), failures);
        check(!shown(a, ST, "shadow_seen") && !shown(a, ST, "shadow_ask") && !shown(a, ST, "errands") && !shown(a, ST, "song_more")
                && !shown(a, ST, "seen"), "the menu before the shadow shows topics it should not", failures);
        DialogueService.close(a);
        // 影を待つ間に話しかけると、一度だけ声をかける
        DialogueService.open(a, st);
        check(node(a).equals("shadow_wait"), "waiting for the shadow should open shadow_wait, got " + node(a), failures);
        next(a);
        check(node(a).equals("menu"), "shadow_wait should lead to the menu", failures);
        DialogueService.close(a);
        DialogueService.open(a, st);
        check(node(a).equals("menu"), "shadow_wait should be said only once, got " + node(a), failures);
        DialogueService.close(a);
        // 空の影を見る → 語り部に尋ねる
        Lore.unlock(a, "prologue/shadow", false);
        Quests.poll(a, true);
        check(stage(a, "main_song").equals("ask_storyteller"), "seeing the shadow did not advance to ask_storyteller: " + stage(a, "main_song"), failures);
        DialogueService.open(a, st);
        check(node(a).equals("menu") && shown(a, ST, "shadow_seen"), "after the shadow the menu should offer shadow_seen, got " + node(a), failures);
        pick(a, ST, "shadow_seen", failures);
        next(a);
        next(a);
        check(node(a).equals("shadow3") && stage(a, "main_song").equals("find_chaser"), "asking about the shadow did not advance to find_chaser: " + stage(a, "main_song"), failures);
        pick(a, ST, "go", failures);
        // イサナと会うまでは、語り部の頼みごとの話題は出ない
        DialogueService.open(a, st);
        check(node(a).equals("menu") && !shown(a, ST, "errands"), "errands should be hidden before meeting Isana", failures);
        DialogueService.close(a);
        DialogueService.open(a, wc);
        check(node(a).equals("c_first") && Lore.state(a).has("chapter1/whale_chaser"), "first talk with Isana should open c_first and record him, got " + node(a), failures);
        next(a);
        for (QuestDef def : QuestManager.all()) {
            if (def.board() && def.id().equals("daily_firewood")) {
                check(def.unlock().test(StoryContext.of(a)), "board quest " + def.id() + " should open once Isana has been met", failures);
            }
        }
        pick(a, WC, "whale_q", failures);
        next(a);
        // イサナの話のあとは一章の支度 (三本そろうまで岩鯨は来ない)
        check(stage(a, "main_song").equals("prepare"), "talking to Isana did not advance to prepare: " + stage(a, "main_song"), failures);
        DialogueService.close(a);
        DialogueService.open(a, st);
        check(node(a).equals("menu") && shown(a, ST, "errands"), "errands should appear once Isana has been met", failures);
        DialogueService.close(a);
        shadowDuringTalk(level, st, failures, notes);
        notes.add("main quest: notebook → follow_smoke → shadow0 → watch_sky → shadow → ask_storyteller → find_chaser → prepare, errands closed until Isana");

        // ---- 道すがらの見聞: 伝承が記されたものだけ並び、話した話題は消える (語り部とイサナの両方)
        for (String role : List.of(ST, WC)) {
            NpcEntity npc = role.equals(ST) ? st : wc;
            String menu = role.equals(ST) ? "menu" : "c_menu";
            String seenMenu = role.equals(ST) ? "seen_menu" : "c_seen_menu";
            FakePlayer p = player(level, role.equals(ST) ? 62 : 63, at);
            NpcBonds.of(p).bond(role).flags().add("met");
            DialogueService.open(p, npc);
            check(node(p).equals(menu) && !shown(p, role, "seen"), role + ": the 'seen' topic should be hidden before any lore: " + node(p), failures);
            DialogueService.close(p);
            List<String> told = new ArrayList<>();
            List<String[]> topics = new ArrayList<>(List.of(new String[] {"camp", "prologue/camp", "camp_told"},
                    new String[] {"prints", "prologue/footprints", "prints_told"}, new String[] {"steps", "prologue/footsteps", "steps_told"},
                    new String[] {"bones", "prologue/bone_hill", "bones_told"}, new String[] {"song", "prologue/sky_song", "song_told"}));
            if (role.equals(ST)) {
                // 語り部の話題の節 (menu) は枠に収めるため、岩鯨と背中の話もこの「見たもの」の節へ移した
                topics.add(new String[] {"whale", "chapter1/stone_whale", "whale_told"});
                topics.add(new String[] {"ruins", "chapter1/sky_ruins", "back_told"});
            }
            for (String[] topic : topics) {
                Lore.unlock(p, topic[1], false);
                DialogueService.open(p, npc);
                check(node(p).equals(menu) && shown(p, role, "seen"), role + ": 'seen' should appear after " + topic[1] + ", at " + node(p), failures);
                if (!pick(p, role, "seen", failures)) {
                    DialogueService.close(p);
                    continue;
                }
                check(node(p).equals(seenMenu), role + ": expected " + seenMenu + ", got " + node(p), failures);
                check(shown(p, role, topic[0]), role + ": topic " + topic[0] + " should be listed", failures);
                for (String other : told) {
                    check(!shown(p, role, other), role + ": told topic " + other + " should be gone", failures);
                }
                pick(p, role, topic[0], failures);
                // 話の続き (続きの▼と、返事の選択肢は最後のもの) を、メニューへ戻るまで進める
                for (int guard = 0; guard < 6 && !node(p).equals(menu) && !node(p).equals("(closed)"); guard++) {
                    Dialogue.Node n = DialogueManager.dialogue(role).nodes().get(node(p));
                    if (n.options().isEmpty()) {
                        next(p);
                    } else {
                        pick(p, role, n.options().get(n.options().size() - 1).id(), failures);
                    }
                }
                check(node(p).equals(menu), role + ": the " + topic[0] + " topic did not return to the menu: " + node(p), failures);
                check(NpcBonds.of(p).bond(role).flags().contains(topic[2]), role + ": flag " + topic[2] + " was not set", failures);
                told.add(topic[0]);
                DialogueService.close(p);
            }
            DialogueService.open(p, npc);
            check(node(p).equals(menu) && !shown(p, role, "seen"), role + ": after all topics the 'seen' option should be gone: " + node(p), failures);
            DialogueService.close(p);
        }
        notes.add("five sights (camp, footprints, footsteps, bone hill, song) told to the storyteller and to Isana (the storyteller also hears the whale and the ruins); each told topic disappears");

        // ---- B: 影を先に見てから、初めて宿りへ来た人
        FakePlayer b = player(level, 64, at);
        Lore.unlock(b, Lore.BOOK_ENTRY, false);
        Quests.poll(b, true);
        check(stage(b, "main_song").equals("follow_smoke"), "b should start at follow_smoke", failures);
        Lore.unlock(b, "prologue/shadow", false);
        Quests.poll(b, true);
        check(stage(b, "main_song").equals("ask_storyteller"), "seeing the shadow before the lodge should skip both opening stages: " + stage(b, "main_song"), failures);
        DialogueService.open(b, st);
        check(node(b).equals("shadow_first"), "a first meeting after the shadow should open shadow_first, got " + node(b), failures);
        next(b);
        next(b);
        check(node(b).equals("shadow3") && stage(b, "main_song").equals("find_chaser"), "shadow_first should lead on to shadow3 and find_chaser: " + node(b) + " " + stage(b, "main_song"), failures);
        DialogueService.close(b);

        // ---- C: 帳面なしで、空のことを先に尋ねた人 (村の語り部。序盤の二段は飛ばす)
        FakePlayer c = player(level, 65, at);
        DialogueService.open(c, st);
        check(node(c).equals("first"), "a stranger should open first, got " + node(c), failures);
        next(c);
        pick(c, ST, "shadow_ask", failures);
        check(stage(c, "main_song").equals("ask_storyteller"), "asking about the sky first should skip the opening stages: " + stage(c, "main_song"), failures);
        next(c);
        next(c);
        check(stage(c, "main_song").equals("find_chaser"), "asking about the sky did not reach find_chaser: " + stage(c, "main_song"), failures);
        DialogueService.close(c);

        // ---- D: 先に岩鯨を見た人も飛ばす
        FakePlayer d = player(level, 66, at);
        Lore.unlock(d, Lore.BOOK_ENTRY, false);
        Quests.poll(d, true);
        Lore.unlock(d, "chapter1/stone_whale", false);
        Quests.poll(d, true);
        check(stage(d, "main_song").equals("board"), "a player who saw the whale first should reach board: " + stage(d, "main_song"), failures);
        notes.add("skips: shadow before the lodge, the sky asked first (no notebook), the whale seen first");
    }

    // ---------------------------------------------------------------- 道具
    private static String text(JsonObject json, String key, String lang, List<String> failures) {
        if (!json.has(key) || json.get(key).getAsString().isBlank()) {
            failures.add(lang + ": missing text " + key);
            return "";
        }
        return json.get(key).getAsString();
    }

    // 字幅のおおよそ (全角 9、半角 6。LoreSelfTest と同じ)
    private static int measure(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) >= 0x2000 ? 9 : 6;
        }
        return w;
    }

    private static JsonObject json(String path, List<String> failures) {
        try (InputStream in = OpeningStorySelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                failures.add("missing resource " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            failures.add(path + ": " + e);
            return null;
        }
    }
}
