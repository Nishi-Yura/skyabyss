package jp.revati.skyabyss.story.ch1;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneSentinel;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.lore.LorePageItem;
import jp.revati.skyabyss.lore.LoreTrigger;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteBuilds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.free.Board;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.Foreshadow;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 序章・一章 (P4a) の自己テスト。段の名前は "ch1 ..." (SKYABYSS_SELFTEST_ONLY=ch1 でこれだけ流せる)。結果は SELFTEST ch1 PASSED / FAILED
//   ch1 data    伝承 12 項目が全部、段階 0 のまま記せる (頁の出どころ・manual の口・文)、経験値の枠、クエスト 3 本 (初日のお使いが日 1 から板に出る)、会話の足し書き、
//               部品、登録した要素、歌石のブロック
//   ch1 logic   歌石の順番、見回りと足跡、小さな異変 (日に一つ・使い切るまで繰り返さない・3 つ目で頁)、歩哨は夜だけ、岩鯨の影の出来事は一度だけ、予兆 3 拍
//   ch1 sites   本物の世界に序章・一章の小要素を置く: 全部建つ・拠点から 150 以内・箱と頁・歌石の番号・筒・積み石の高さ、歌石を叩いて鳴らしきる
public final class Ch1SelfTest {
    private static final List<String> F = new ArrayList<>();
    private static final String[] LANGS = {"ja_jp", "en_us"};
    private static int waited;
    private static FakePlayer base;
    private static BlockPos baseAt;

    private Ch1SelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("ch1 data", Ch1SelfTest::data));
        steps.add(Step.once("ch1 logic", Ch1SelfTest::logic));
        steps.add(Step.until("ch1 sites (wait)", s -> {
            waited++;
            return (!SiteBuilds.busy() && !SiteBuilds.planning()) || waited > 12000;
        }));
        steps.add(Step.once("ch1 sites (start)", Ch1SelfTest::start));
        steps.add(Step.until("ch1 sites (plan)", Ch1SelfTest::settled));
        steps.add(Step.once("ch1 sites", Ch1SelfTest::checkSites));
        steps.add(Step.once("ch1 p6 (load)", s -> Ch1SitesSelfTest.load(s, base)));
        steps.add(Step.until("ch1 p6 (wait)", Ch1SitesSelfTest::loaded));
        steps.add(Step.once("ch1 p6", s -> Ch1SitesSelfTest.run(s, base)));
        steps.add(Step.once("ch1 end", Ch1SelfTest::end));
        return steps;
    }

    static BlockPos baseAt() {
        return baseAt;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    private static FakePlayer fake(MinecraftServer server, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_ch1_test_" + suffix).getBytes());
        return FakePlayerFactory.get(server.overworld(), new GameProfile(id, "skyabyss_ch1_" + suffix));
    }

    private static void end(MinecraftServer server) {
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch1 PASSED (12 lore entries reachable at stage 0, xp within the chapter cap, day-1 errands on the board from day 1, "
                    + "song stone order, rounds and trail, small oddities once a day, sentry only at night, whale hush once, 3 foreshadowing beats, sites built)");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch1 FAILED: {}", m));
        }
    }

    private static JsonObject classpathJson(String path) {
        try (InputStream in = Ch1SelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                F.add("missing resource " + path);
                return new JsonObject();
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            F.add("could not read " + path + ": " + e);
            return new JsonObject();
        }
    }

    private static boolean has(JsonObject lang, String key) {
        return lang.has(key) && !lang.get(key).getAsString().isBlank();
    }

    // ---------------------------------------------------------------- ch1 data
    static void data(MinecraftServer server) {
        try {
            lore(server);
            quests(server);
            dialogues();
            parts();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch1 data crashed", e);
            F.add("data crashed: " + e);
        }
    }

    private static void lore(MinecraftServer server) {
        var catalog = LoreManager.catalog();
        Set<String> pageSources = Set.of(Ch1.PAGE_TRACE, Ch1.PAGE_DIARY);
        List<String> tracePool = new ArrayList<>();
        List<String> diaryPool = new ArrayList<>();
        check(Ch1.ALL_LORE.size() == 12, "there should be 12 lore entries (" + Ch1.ALL_LORE.size() + ")");
        for (String id : Ch1.ALL_LORE) {
            LoreEntry entry = catalog.entry(id);
            check(entry != null && entry.unlockable(), id + " is missing or cannot be unlocked");
            if (entry == null) {
                continue;
            }
            check(entry.chapter().equals("prologue") || entry.chapter().equals(Chapters.WHALE.id()), id + ": chapter " + entry.chapter());
            boolean reachable = false;
            for (LoreTrigger t : entry.unlock()) {
                switch (t.type()) {
                    case "page" -> {
                        check(pageSources.contains(t.target()), id + ": unknown page source " + t.target());
                        (t.target().equals(Ch1.PAGE_TRACE) ? tracePool : diaryPool).add(id);
                        reachable = true;
                    }
                    case "manual" -> {
                        check(Ch1.MANUAL_LORE.contains(id), id + ": a manual entry nobody unlocks");
                        reachable = true;
                    }
                    default -> check(false, id + ": trigger " + t.type() + " would need something beyond stage 0");
                }
            }
            check(reachable, id + " has no way to be reached");
            for (String lang : LANGS) {
                JsonObject l = classpathJson("/assets/skyabyss_ch1/lang/" + lang + ".json");
                check(has(l, entry.titleKey()) && has(l, entry.bodyKey()), lang + ": text of " + id);
            }
        }
        check(new HashSet<>(tracePool).equals(new HashSet<>(Ch1.TRACE_LORE)) && tracePool.size() == 5, "trace page pool is " + tracePool);
        check(diaryPool.equals(List.of(Ch1.DIARY)), "diary page pool is " + diaryPool);
        // 頁を置く場所の数と、頁の出どころごとの項目の数が合う (読み切れる)
        long traceSites = Sites.PROLOGUE.size() + Sites.WHALE.stream().filter(Sites::isTrace).count();
        check(traceSites == tracePool.size(), "trace sites (" + traceSites + ") and trace pages (" + tracePool.size() + ") differ");
        for (String source : pageSources) {
            for (String lang : LANGS) {
                check(has(classpathJson("/assets/skyabyss_ch1/lang/" + lang + ".json"), "item.skyabyss.lore_page.desc." + source), lang + ": page desc " + source);
            }
        }
        // 経験値の表と、章の枠 (小要素は 1 章あたりその時の 1 レベル分の 1〜1.5 倍まで。L→L+1 = 50 + 25k + 2k²)
        int loreXp = 0;
        for (String id : Ch1.ALL_LORE) {
            check(GrowthRewards.LORE_XP.containsKey(id), id + " is not in the lore xp table");
            loreXp += Ch1.LORE_XP.getOrDefault(id, 0);
        }
        int prologueLore = 0;
        for (String id : Ch1.TRACE_LORE.subList(0, 3)) {
            prologueLore += Ch1.LORE_XP.get(id);
        }
        int questXp = 0;
        int prologueQuestXp = 0;
        for (String q : List.of(Ch1.Q_CAIRNS, Ch1.Q_FIREWOOD, Ch1.Q_TRAVELLER)) {
            QuestDef def = QuestManager.quest(q);
            if (def == null) {
                continue;
            }
            int xp = def.reward().xp() + def.stages().stream().mapToInt(QuestDef.Stage::xp).sum();
            questXp += xp;
            if (!q.equals(Ch1.Q_TRAVELLER)) {
                prologueQuestXp += xp;
            }
        }
        int oneLevelWhale = 50 + 25 * 8 + 2 * 8 * 8;
        int oneLevelPrologue = 50 + 25 * 1 + 2;
        check(loreXp + questXp <= oneLevelWhale * 1.5, "small-element xp " + (loreXp + questXp) + " is over 1.5 levels (" + oneLevelWhale + " each)");
        check(prologueLore + prologueQuestXp <= oneLevelPrologue * 1.5, "day-1 xp " + (prologueLore + prologueQuestXp) + " is over 1.5 levels (" + oneLevelPrologue + " each)");
    }

    private static void quests(MinecraftServer server) {
        for (String id : List.of(Ch1.Q_CAIRNS, Ch1.Q_FIREWOOD, Ch1.Q_TRAVELLER)) {
            QuestDef def = QuestManager.quest(id);
            check(def != null, "quest " + id + " is missing");
            if (def == null) {
                continue;
            }
            check(def.minStage() == 0 && def.unlock().type().equals("true"), id + " must open at stage 0 with no other lock");
            check(!def.chapter().isEmpty() && Chapters.byId(def.chapter()) != null, id + ": chapter " + def.chapter());
            for (String lang : LANGS) {
                JsonObject l = classpathJson("/assets/skyabyss_ch1/lang/" + lang + ".json");
                String base = QuestDef.keyBase(id);
                check(has(l, base + ".title") && has(l, base + ".summary") && has(l, base + ".done"), lang + ": quest texts of " + id);
                for (QuestDef.Stage st : def.stages()) {
                    check(has(l, def.journalKey(st)), lang + ": journal of " + id + "." + st.id());
                    for (QuestDef.Objective ob : st.objectives()) {
                        check(has(l, def.objectiveKey(st, ob)), lang + ": objective text of " + id + "." + st.id() + "." + ob.id());
                    }
                }
            }
        }
        QuestDef cairns = QuestManager.quest(Ch1.Q_CAIRNS);
        QuestDef wood = QuestManager.quest(Ch1.Q_FIREWOOD);
        if (cairns != null && wood != null) {
            check(cairns.board() && wood.board() && cairns.repeats() && wood.repeats(), "the day-1 errands belong on the board as repeating errands");
            check(cairns.chapter().equals("prologue") && wood.chapter().equals("prologue"), "day-1 errands belong to the prologue");
            // 日 1 から板に 2 本ある (週替わりの欄は抽選に関わらず常に並ぶ)
            List<QuestDef> today = Board.todays(server);
            check(today.contains(cairns) && today.contains(wood), "both day-1 errands should be on the board today");
            FakePlayer p = fake(server, "board");
            check(Board.canAccept(p, cairns) && Board.canAccept(p, wood), "a new player (stage 0) should be able to take both errands");
        }
        QuestDef lost = QuestManager.quest(Ch1.Q_TRAVELLER);
        if (lost != null) {
            QuestDef.Objective diary = lost.stages().get(0).objectives().get(0);
            check(diary.type().equals("lore") && diary.targets().equals(List.of(Ch1.DIARY)), "the lost traveller asks for the diary entry");
            QuestDef.Objective tell = lost.stages().get(1).objectives().get(0);
            check(tell.type().equals("talk") && Ch1.TOPIC_DIARY_TELL.equals(tell.topic()), "the lost traveller ends with the storyteller topic");
        }
    }

    private static void dialogues() {
        for (String file : List.of("storyteller_ch1", "whale_chaser_ch1")) {
            JsonObject d = classpathJson("/data/skyabyss/dialogue/ch1/" + file + ".json");
            String role = d.get("npc").getAsString();
            String ns = "dialogue.skyabyss." + role.substring(role.indexOf(':') + 1) + ".";
            JsonObject nodes = d.getAsJsonObject("nodes");
            for (Map.Entry<String, JsonElement> n : nodes.entrySet()) {
                check(n.getKey().startsWith("ch1_"), file + ": node " + n.getKey() + " must be prefixed ch1_");
                for (String lang : LANGS) {
                    JsonObject l = classpathJson("/assets/skyabyss_ch1/lang/" + lang + ".json");
                    check(has(l, ns + n.getKey()), lang + ": text of node " + n.getKey());
                    JsonObject node = n.getValue().getAsJsonObject();
                    if (node.has("options")) {
                        for (JsonElement o : node.getAsJsonArray("options")) {
                            String id = o.getAsJsonObject().get("id").getAsString();
                            check(has(l, ns + n.getKey() + "." + id), lang + ": option " + n.getKey() + "." + id);
                        }
                    }
                }
            }
            if (d.has("add_options")) {
                for (Map.Entry<String, JsonElement> menu : d.getAsJsonObject("add_options").entrySet()) {
                    for (JsonElement o : menu.getValue().getAsJsonObject().getAsJsonArray("options")) {
                        String id = o.getAsJsonObject().get("id").getAsString();
                        for (String lang : LANGS) {
                            check(has(classpathJson("/assets/skyabyss_ch1/lang/" + lang + ".json"), ns + menu.getKey() + "." + id), lang + ": menu option " + menu.getKey() + "." + id);
                        }
                    }
                }
            }
        }
        // 本物の会話に混ざっている
        var st = DialogueManager.dialogue("skyabyss:storyteller");
        check(st != null && st.nodes().containsKey("ch1_errand_offer") && st.nodes().containsKey("ch1_diary_tell"), "storyteller extension not merged");
        if (st != null) {
            var nodes = st.entries().stream().map(e -> e.node()).toList();
            check(nodes.contains("ch1_diary_tell") && nodes.indexOf("ch1_diary_tell") < nodes.indexOf("menu"), "the diary entry should come before the catch-all entry: " + nodes);
            check(st.nodes().get("shadow0_b").options().stream().anyMatch(o -> o.id().equals("ch1_help")), "the first talk offers the day-1 errands");
            check(st.entries().get(st.entries().size() - 1).node().equals("menu"), "the catch-all entry must stay last");
        }
        var wc = DialogueManager.dialogue("skyabyss:whale_chaser");
        check(wc != null && wc.nodes().containsKey("ch1_c_why1") && wc.nodes().containsKey("ch1_c_after2")
                && wc.entries().stream().anyMatch(e -> e.node().equals("ch1_c_why1")) && wc.entries().get(wc.entries().size() - 1).node().equals("c_menu"),
                "whale chaser extension not merged");
    }

    private static void parts() {
        List<String> names = new ArrayList<>(Sites.PROLOGUE);
        names.addAll(Sites.WHALE);
        for (String name : names) {
            Blueprint bp = Blueprint.get(name);
            check(!bp.cells.isEmpty() && bp.marks.containsKey("stand"), name + ": empty blueprint or no stand mark");
            if (Sites.isTrace(name) || name.equals(Sites.LOST_CART) || name.equals(Sites.DIARY_SPOT)) {
                check(bp.marks.containsKey("chest"), name + ": no chest mark");
            }
            if (Sites.isTrace(name)) {
                check(bp.marks.containsKey("cairn"), name + ": no cairn mark (the rounds look for it)");
            }
        }
        check(ChapterSites.elements(Chapters.PROLOGUE).stream().map(ChapterSites.Element::id).sorted().toList().equals(Sites.PROLOGUE.stream().sorted().toList()),
                "prologue elements are not registered as expected");
        List<String> whaleIds = new ArrayList<>(Sites.WHALE);
        whaleIds.addAll(Sites.WHALE_P6);
        check(ChapterSites.elements(Chapters.WHALE).stream().map(ChapterSites.Element::id).sorted().toList().equals(whaleIds.stream().sorted().toList()),
                "chapter 1 elements are not registered as expected");
        for (String id : Sites.WHALE_P6) {
            var el = ChapterSites.elements(Chapters.WHALE).stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
            Blueprint bp = el == null ? null : Blueprint.get(el.blueprint());
            check(bp != null && !bp.cells.isEmpty() && bp.marks.containsKey("stand"), id + ": empty blueprint or no stand mark");
        }
        check(ChapterSites.elements(Chapters.PROLOGUE).size() >= 3, "the prologue needs at least 3 traces");
        // 歌石の積み石は 1〜5 段
        for (int n = 1; n <= 5; n++) {
            Blueprint bp = Blueprint.get(Sites.songStoneId(n));
            int[] cairn = bp.marks.get("cairn");
            check(cairn != null && cairn[1] == n, "song stone " + n + ": the cairn should be " + n + " high (mark " + (cairn == null ? null : cairn[1]) + ")");
        }
        var block = Ch1.songStone();
        check(block != Blocks.AIR && BuiltInRegistries.BLOCK.getKey(block).getPath().equals("song_stone") && block.defaultBlockState().hasProperty(SongStoneBlock.LIT),
                "the song stone block is not registered");
    }

    // ---------------------------------------------------------------- ch1 logic
    static void logic(MinecraftServer server) {
        ServerLevel level = server.overworld();
        try {
            stonesPure();
            oddityPure();
            lookoutPure();
            errands(server, level);
            stonesFake(server, level);
            oddities(server, level);
            sentries(server, level);
            hush(server, level);
            beats(server, level);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch1 logic crashed", e);
            F.add("logic crashed: " + e);
        } finally {
            Tasks.flush();
        }
    }

    private static void stonesPure() {
        int p = 0;
        for (int n = 1; n <= 5; n++) {
            SongStones.Step s = SongStones.step(p, n, false);
            check(s.result() == SongStones.Result.ADVANCE && s.progress() == n, "in order, stone " + n + " should advance");
            p = s.progress();
        }
        check(SongStones.step(0, 3, false).result() == SongStones.Result.WRONG && SongStones.step(0, 3, false).progress() == 0, "starting at 3 is wrong");
        check(SongStones.step(2, 4, false).result() == SongStones.Result.WRONG && SongStones.step(2, 4, false).progress() == 0, "skipping a stone resets");
        check(SongStones.step(3, 1, false).result() == SongStones.Result.RESTART && SongStones.step(3, 1, false).progress() == 1, "stone 1 restarts the song");
        check(SongStones.step(1, 1, false).result() == SongStones.Result.RESTART, "stone 1 twice restarts at 1");
        check(SongStones.step(5, 2, true).result() == SongStones.Result.SILENT, "a finished song is silent");
    }

    private static void oddityPure() {
        // 使い切るまで同じ種類を選ばない
        int mask = 0;
        Set<Oddities.Kind> seen = new HashSet<>();
        for (int day = 0; day < Oddities.Kind.values().length; day++) {
            Oddities.Kind k = Oddities.pick(day, mask);
            check(seen.add(k), "oddity " + k + " repeated before all four were used");
            mask |= 1 << k.ordinal();
        }
        check(seen.size() == 4, "all four oddity kinds should be used");
        for (int day = 0; day < 40; day++) {
            int due = Oddities.dueTick(UUID.nameUUIDFromBytes(("o" + day).getBytes()), day);
            check(due >= Oddities.WINDOW_FROM && due < Oddities.WINDOW_FROM + Oddities.WINDOW_SPAN && due <= 11500, "due tick " + due + " is out of the daytime window");
        }
        check(Oddities.daytime(6000) && !Oddities.daytime(2000) && !Oddities.daytime(14000) && Oddities.daytime(30000), "oddities are for the daytime only");
    }

    private static void lookoutPure() {
        check(Lookout.dusk(11000) && Lookout.dusk(12500) && !Lookout.dusk(6000) && !Lookout.dusk(18000) && Lookout.dusk(24000 + 11000), "the lookout shows its edge at dusk only");
    }

    private static void errands(MinecraftServer server, ServerLevel level) {
        FakePlayer p = fake(server, "errands");
        Quests.forgetAll(p);
        Ch1Data data = Ch1Data.get(server.overworld());
        Ch1Data.Player st = data.player(p.getUUID());
        st.rounds.clear();
        st.signalled.clear();
        // 頼みを受ける前に見て回った分も、受けたあとの最初の見回りで数える
        st.rounds.add("c:10,10");
        st.rounds.add("s:ch1_trace_camp#0");
        check(Errands.syncSignals(p, st, data) == 0, "no signal should be sent while the errand is not taken");
        check(Quests.start(p, Ch1.Q_CAIRNS), "could not take the cairn rounds");
        check(Errands.syncSignals(p, st, data) == 2, "the two visits made before should count");
        QuestLog.Entry e = QuestLog.of(p).entry(Ch1.Q_CAIRNS);
        check(e != null && e.done, "the cairn rounds should be done after two visits");
        check(Lore.state(p).has(Ch1.ROUNDS), "finishing the rounds should write the cairn_rounds page");
        check(Errands.syncSignals(p, st, data) == 0 && st.signalled.isEmpty(), "after the errand is done the sent marks are forgotten");
        check(!Quests.start(p, Ch1.Q_CAIRNS), "a weekly errand must not be taken twice in the same week");
        // 薪: 原木 8 本を板で納める
        check(Quests.start(p, Ch1.Q_FIREWOOD), "could not take the firewood errand");
        p.getInventory().add(new ItemStack(Items.OAK_LOG, 8));
        List<String> handed = Quests.turnIn(p, null);
        check(handed.contains(Ch1.Q_FIREWOOD), "the board should take the eight logs: " + handed);
        QuestLog.Entry w = QuestLog.of(p).entry(Ch1.Q_FIREWOOD);
        check(w != null && w.done, "the firewood errand should be done");
        // 足跡のくぼみ
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        check(Errands.draw(p, level, at.offset(-20, 0, 0), at.offset(30, 0, 5)) >= 4, "the trail should draw hollows near the player");
        check(Errands.draw(p, level, at.offset(500, 0, 0), at.offset(540, 0, 0)) == 0, "no hollows far from the player");
        st.rounds.clear();
        Quests.forgetAll(p);
    }

    private static void stonesFake(MinecraftServer server, ServerLevel level) {
        FakePlayer p = fake(server, "stones");
        Ch1Data data = Ch1Data.get(server.overworld());
        Ch1Data.Player st = data.player(p.getUUID());
        st.song = 0;
        st.songDone = false;
        Lore.lock(p, Ch1.SONG);
        BlockPos at = level.getSharedSpawnPos();
        int[] order = {2, 1, 3, 1, 2, 3, 4, 5};
        // 2 (誤り)、1、3 (誤り)、1、2、3、4、5 → 最後で鳴りきる
        for (int i = 0; i < order.length; i++) {
            SongStones.tap(p, level, at, order[i]);
            if (i < order.length - 1) {
                check(!st.songDone, "the song must not finish before the fifth stone in order (step " + i + ")");
            }
        }
        check(st.songDone && st.song == 5, "tapping 1-2-3-4-5 in order should finish the song (" + st.song + ")");
        Tasks.flush();
        check(Lore.state(p).has(Ch1.SONG), "the song_stones page should be written when the bell answers");
        SongStones.tap(p, level, at, 3);
        check(st.songDone, "a finished song stays finished");
        st.song = 0;
        st.songDone = false;
        Lore.lock(p, Ch1.SONG);
    }

    private static void oddities(MinecraftServer server, ServerLevel level) {
        FakePlayer p = fake(server, "oddities");
        Ch1Data data = Ch1Data.get(server.overworld());
        Ch1Data.Player st = data.player(p.getUUID());
        st.oddityDay = -1;
        st.oddityMask = 0;
        st.oddityCount = 0;
        Lore.lock(p, Ch1.WRONGS);
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        p.setPos(at.getX() + 0.5, at.getY() + 150, at.getZ() + 0.5);
        long saveTime = level.getDayTime();
        long saveFirst = Oddities.firstAfter;
        Oddities.firstAfter = 0;
        try {
            // 日 N の昼に一つだけ。同じ日にもう一度は起きない
            long base = Math.floorDiv(level.getDayTime(), 24000L) * 24000L;
            Set<Oddities.Kind> kinds = new HashSet<>();
            for (int d = 0; d < 3; d++) {
                level.setDayTime(base + 24000L * (d + 1) + 11400);
                long day = jp.revati.skyabyss.quest.Repeats.day(level.getServer());
                boolean first = Oddities.tryOdd(p, level);
                check(first, "an oddity should come in the daytime (day " + d + ")");
                check(!Oddities.tryOdd(p, level), "only one oddity a day (day " + d + ")");
                check(st.oddityDay == day, "the oddity day should be recorded");
                check(Integer.bitCount(st.oddityMask) == d + 1, "three days should use three different kinds (" + Integer.toBinaryString(st.oddityMask) + ")");
                Tasks.flush();
            }
            check(st.oddityCount == 3 && Lore.state(p).has(Ch1.WRONGS), "the third oddity writes the small_wrongs page");
            // 夜には起きない
            st.oddityDay = -1;
            level.setDayTime(base + 24000L * 5 + 16000);
            check(!Oddities.tryOdd(p, level), "no oddity at night");
            // 世界の日数で数える (昼夜を止めた世界で日が替わらない件の保険は Repeats.day が受け持つ)
            kinds.clear();
        } finally {
            Oddities.firstAfter = saveFirst;
            level.setDayTime(saveTime);
            Lore.lock(p, Ch1.WRONGS);
            st.oddityDay = -1;
            st.oddityMask = 0;
            st.oddityCount = 0;
        }
    }

    private static void sentries(MinecraftServer server, ServerLevel level) {
        check(SentryPatrol.night(13000) && SentryPatrol.night(22999) && !SentryPatrol.night(23000) && !SentryPatrol.night(6000) && SentryPatrol.night(24000 + 15000),
                "the patrol is for the night only");
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        BlockPos center = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        try {
            int count = SentryPatrol.sync(level, center, true, true);
            check(count == 1 && SentryPatrol.patrol(level, center).size() == 1, "at night with someone near, exactly one sentry walks (" + count + ")");
            check(SentryPatrol.sync(level, center, true, true) == 1 && SentryPatrol.patrol(level, center).size() == 1, "syncing again must not add a second sentry");
            BoneSentinel s = SentryPatrol.patrol(level, center).get(0);
            check(s.getTags().contains(SentryPatrol.TAG) && s.isPersistenceRequired(), "the patrol sentry is tagged and kept");
            check(SentryPatrol.sync(level, center, true, false) == 0 && SentryPatrol.patrol(level, center).isEmpty(), "with nobody near the sentry sleeps");
            SentryPatrol.sync(level, center, true, true);
            check(SentryPatrol.sync(level, center, false, true) == 0 && SentryPatrol.patrol(level, center).isEmpty(), "by day the sentry sleeps");
            FakePlayer p = fake(server, "sentry");
            Lore.lock(p, Ch1.SENTRY);
            SentryPatrol.reward(p);
            check(Lore.state(p).has(Ch1.SENTRY), "beating the sentry writes its page");
            Lore.lock(p, Ch1.SENTRY);
        } finally {
            SentryPatrol.sync(level, center, false, false);
        }
    }

    private static void hush(MinecraftServer server, ServerLevel level) {
        Ch1Data data = Ch1Data.get(server.overworld());
        int saveState = data.hushState;
        long saveStart = data.hushStart;
        long saveEnd = data.hushEnd;
        WhaleHush.SkyStarter saveSky = WhaleHush.skyStarter;
        data.hushState = 0;
        int[] started = {0};
        WhaleHush.skyStarter = (lv, witness) -> {
            started[0]++;
            return new SkyPass(lv.getGameTime(), 0, 0, 1, 0, 0.35f, 1257);
        };
        try {
            StoneWhaleEntity whale = ModEntities.STONE_WHALE.get().create(level);
            FakePlayer p = fake(server, "hush");
            BlockPos at = level.getSharedSpawnPos();
            p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            whale.moveTo(at.getX() + 10.5, at.getY() + 40, at.getZ() + 0.5);
            check(WhaleHush.begin(level, whale, List.of()) == 0 && data.hushState == 0, "nobody near: the event waits for the next descent");
            int held = WhaleHush.begin(level, whale, List.of(p));
            check(held >= 1000 && held <= 1400 && data.hushState == 1 && started[0] == 1, "the first descent starts the event with the pilgrim's shadow (hold " + held + ")");
            check(WhaleHush.begin(level, whale, List.of(p)) == 0 && started[0] == 1, "the event must start only once while it runs");
            // 影が去ったら終わる
            WhaleHush.finish(level, data);
            check(data.hushState == 2, "the event ends after the shadow");
            check(WhaleHush.begin(level, whale, List.of(p)) == 0 && started[0] == 1 && data.hushState == 2, "the event never happens twice");
            check(!whale.isHolding(), "the whale is not holding after the event");
            // 保存して読み戻しても済んだまま
            net.minecraft.nbt.CompoundTag tag = data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            check(tag.getInt("hushState") == 2, "the done state should be saved");
            whale.discard();
            Tasks.flush();
        } finally {
            WhaleHush.skyStarter = saveSky;
            data.hushState = saveState;
            data.hushStart = saveStart;
            data.hushEnd = saveEnd;
            data.setDirty();
        }
    }

    private static void beats(MinecraftServer server, ServerLevel level) {
        List<Beat> beats = Foreshadow.beats(Chapters.WHALE);
        check(beats.size() == 3 && beats.stream().map(Beat::id).toList().equals(Beats.IDS), "three foreshadowing beats should be registered for chapter 1: " + beats.stream().map(Beat::id).toList());
        check(beats.stream().map(Beat::order).toList().equals(List.of(1, 2, 3)), "beats should be ordered 1-3");
        check(beats.get(0).time() == Beat.Time.DAY && beats.get(1).time() == Beat.Time.DAY && beats.get(2).time() == Beat.Time.DUSK, "beat times: day, day, dusk");
        FakePlayer p = fake(server, "beats");
        ArcData arc = ArcData.of(p);
        arc.reset();
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        boolean free = !WhaleKeeper.everSpawned(server.overworld());
        check(Beats.ready(p) == (free && jp.revati.skyabyss.opening.OpeningDirector.quiet(level, p)), "beats are ready only before the whale appears and while calm");
        if (!free || !jp.revati.skyabyss.opening.OpeningDirector.quiet(level, p)) {
            SkyAbyss.LOGGER.warn("SELFTEST ch1: the whale has already appeared or the fake player is not calm; the firing path of the beats is skipped");
            return;
        }
        long t = 10000;
        Beat b1 = Foreshadow.tryFire(p, Chapters.WHALE, t, 6000);
        check(b1 != null && b1.name().equals(Beats.DUST), "the first beat (stone dust) should fire by day: " + (b1 == null ? null : b1.id()));
        check(Foreshadow.tryFire(p, Chapters.WHALE, t + 100, 6000) == null, "the cooldown holds the next beat back");
        Beat b2 = Foreshadow.tryFire(p, Chapters.WHALE, t + 6000, 6000);
        check(b2 != null && b2.name().equals(Beats.SHADOW), "the second beat (ground shadow) fires after the cooldown: " + (b2 == null ? null : b2.id()));
        check(Foreshadow.tryFire(p, Chapters.WHALE, t + 12000, 6000) == null, "the dusk beat waits for dusk");
        Beat b3 = Foreshadow.tryFire(p, Chapters.WHALE, t + 12000, 12000);
        check(b3 != null && b3.name().equals(Beats.CHAIN), "the third beat (chain link) fires at dusk: " + (b3 == null ? null : b3.id()));
        check(Foreshadow.count(p, Chapters.WHALE) == 3 && Foreshadow.tryFire(p, Chapters.WHALE, t + 99999, 12000) == null, "each beat fires once");
        Tasks.flush();
        List<ItemEntity> links = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16, 20, 16), i -> i.getItem().is(Items.CHAIN));
        check(!links.isEmpty() && links.get(0).getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME), "the chain link should fall near the player with its name");
        links.forEach(ItemEntity::discard);
        arc.reset();
    }

    // ---------------------------------------------------------------- ch1 sites (本物の世界)
    static void start(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            baseAt = OpeningSites.camp(server).orElse(level.getSharedSpawnPos());
            base = fake(server, "base");
            base.setPos(baseAt.getX() + 0.5, baseAt.getY(), baseAt.getZ() + 0.5);
            waited = 0;
            ChapterSites.open(base, Chapters.PROLOGUE);
            ChapterSites.open(base, Chapters.WHALE);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch1 sites (start) crashed", e);
            F.add("sites start crashed: " + e);
        }
    }

    private static boolean settled(MinecraftServer server) {
        waited++;
        if (waited > 40000) {
            F.add("timed out waiting for the chapter sites");
            return true;
        }
        if (SiteBuilds.busy() || SiteBuilds.planning() || base == null) {
            return false;
        }
        SiteData data = SiteData.get(server.overworld());
        boolean prologue = data.plans().stream().anyMatch(p -> p.chapter.equals(Chapters.PROLOGUE.id()) && p.players.contains(base.getUUID()) && !p.ahead && !p.sites.isEmpty());
        boolean whale = data.plans().stream().anyMatch(p -> p.chapter.equals(Chapters.WHALE.id()) && p.players.contains(base.getUUID()) && !p.ahead && !p.sites.isEmpty());
        return prologue && whale;
    }

    static void checkSites(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            // 序章: 旅の跡 3 つ、拠点から 150 ブロック以内
            for (String id : Sites.PROLOGUE) {
                var sites = ChapterSites.sitesFor(server, base.getUUID(), id);
                check(sites.size() == 1 && sites.get(0).built(), id + ": should be placed and built exactly once (" + sites.size() + ")");
                for (var s : sites) {
                    double d = Math.hypot(s.pos().getX() - baseAt.getX(), s.pos().getZ() - baseAt.getZ());
                    check(d <= 152, id + " is " + (int) d + " from the base");
                }
            }
            int pages = 0;
            int diaries = 0;
            for (String id : Sites.WHALE) {
                var sites = ChapterSites.sitesFor(server, base.getUUID(), id);
                check(sites.size() == 1 && sites.get(0).built(), id + ": should be placed and built exactly once (" + sites.size() + ")");
                for (var s : sites) {
                    double d = Math.hypot(s.pos().getX() - baseAt.getX(), s.pos().getZ() - baseAt.getZ());
                    check(d <= 152, id + " is " + (int) d + " from the base");
                }
            }
            for (String id : Sites.PROLOGUE.stream().toList()) {
                var s = ChapterSites.sitesFor(server, base.getUUID(), id);
                if (!s.isEmpty()) {
                    pages += pagesNear(level, s.get(0).pos(), Ch1.PAGE_TRACE);
                }
            }
            for (String id : Sites.WHALE) {
                var s = ChapterSites.sitesFor(server, base.getUUID(), id);
                if (s.isEmpty()) {
                    continue;
                }
                if (Sites.isTrace(id)) {
                    pages += pagesNear(level, s.get(0).pos(), Ch1.PAGE_TRACE);
                }
                if (id.equals(Sites.DIARY_SPOT)) {
                    diaries += pagesNear(level, s.get(0).pos(), Ch1.PAGE_DIARY);
                }
                if (id.equals(Sites.LOST_CART)) {
                    check(containerItemsNear(level, s.get(0).pos()) >= 2, "the lost cart's pack should hold food and torches");
                }
                if (id.equals(Sites.LOOKOUT)) {
                    BlockPos rod = find(level, s.get(0).pos(), 5, Blocks.LIGHTNING_ROD);
                    check(rod != null && Lookout.atLookout(level, rod), "the lookout has its brass tube");
                }
                if (id.startsWith("ch1_song_stone_")) {
                    int n = Integer.parseInt(id.substring(id.lastIndexOf('_') + 1));
                    BlockPos stone = find(level, s.get(0).pos(), 4, Ch1.songStone());
                    check(stone != null && SongStones.numberAt(server, stone) == n, id + ": the stone should be found and numbered " + n);
                    check(cairnHeight(level, s.get(0).pos()) == n, id + ": the cairn beside it should be " + n + " high (found " + cairnHeight(level, s.get(0).pos()) + ")");
                }
            }
            check(pages == 5, "five trace pages should be placed (found " + pages + ")");
            check(diaries == 1, "the diary page should be placed once (found " + diaries + ")");
            // 歌石を番号順に叩くと鳴りきる (本物の石の場所から番号を引く)
            Ch1Data data = Ch1Data.get(server.overworld());
            Ch1Data.Player st = data.player(base.getUUID());
            st.song = 0;
            st.songDone = false;
            for (int n = 1; n <= 5; n++) {
                var s = ChapterSites.sitesFor(server, base.getUUID(), Sites.songStoneId(n));
                BlockPos stone = s.isEmpty() ? null : find(level, s.get(0).pos(), 4, Ch1.songStone());
                if (stone != null) {
                    SongStones.tap(base, level, stone);
                }
            }
            check(st.songDone, "tapping the five placed stones in order should finish the song");
            Tasks.flush();
            // 見回りの目印: 旅の跡の積み石は、積み石の見回りの数えに入る
            check(Ch1.TRACE_LORE.size() == 5, "five trace pages");
            st.songDone = false;
            st.song = 0;
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch1 sites crashed", e);
            F.add("sites crashed: " + e);
        }
    }

    private static int containerItemsNear(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -3, -6), pos.offset(6, 4, 6))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    if (!c.getItem(i).isEmpty()) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static int pagesNear(ServerLevel level, BlockPos pos, String source) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -3, -6), pos.offset(6, 4, 6))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack stack = c.getItem(i);
                    if (stack.is(jp.revati.skyabyss.lore.LoreSetup.LORE_PAGE.get()) && LorePageItem.source(stack).equals(source)) {
                        n += stack.getCount();
                    }
                }
            }
        }
        return n;
    }

    private static BlockPos find(ServerLevel level, BlockPos pos, int r, net.minecraft.world.level.block.Block block) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -3, -r), pos.offset(r, 4, r))) {
            if (level.getBlockState(p).is(block)) {
                return p.immutable();
            }
        }
        return null;
    }

    // 部品の積み石 (丸石と苔むした丸石の柱) の高さ: 近くの柱の、丸石の段数の最大
    private static int cairnHeight(ServerLevel level, BlockPos pos) {
        int best = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-5, -2, -5), pos.offset(5, 8, 5))) {
            if (level.getBlockState(p).is(Blocks.COBBLESTONE_SLAB)) {
                int h = 0;
                BlockPos q = p.below();
                while (level.getBlockState(q).is(Blocks.COBBLESTONE) || level.getBlockState(q).is(Blocks.MOSSY_COBBLESTONE)) {
                    h++;
                    q = q.below();
                }
                best = Math.max(best, h);
            }
        }
        return best;
    }
}
