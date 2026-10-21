package jp.revati.skyabyss.quest.free;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestEvents;
import jp.revati.skyabyss.quest.QuestItems;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.QuestSetup;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.Repeats;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 自由なモードのサブクエストの自己テスト (SKYABYSS_SELFTEST=1 の専用サーバー、起動直後に一度)。
// 全クエストを最初から最後まで (会話・板・合図・倒す・納める・連れ戻す)、解放の条件 (段階・伝承)、
// 繰り返し (同じ日に受け直せない・報酬は周期に一度・日が替わると受けられる・受けたままの頼みは残る)、日替わりの枠、
// パーティの共有 (倒す・合図は仲間も、ほかの人は数えない)、二人が同時に進める、印の付いた品を別の品で代えられない、
// 記録の保存と読み込みを確かめる。結果はログに SELFTEST freequest PASSED / FAILED
public final class FreeQuestSelfTest {
    private static final String ST = NpcSetup.STORYTELLER_ID;
    private static final String WC = NpcSetup.WHALE_CHASER_ID;
    private static final Set<String> INTROS = Set.of("first", "c_first", "deja_vu", "deja_vu_song", "c_deja");

    private FreeQuestSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel level = server.overworld();
        ProgressData progress = ProgressData.get(server);
        CompoundTag progressSaved = progress.save(new CompoundTag(), server.registryAccess());
        PartyData parties = PartyData.get(server);
        CompoundTag partiesSaved = parties.snapshot(server.registryAccess());
        long dayBefore = level.getDayTime();
        List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
        try {
            run(server, level, spawned, failures, notes);
        } catch (RuntimeException e) {
            failures.add("exception: " + e);
            SkyAbyss.LOGGER.error("SELFTEST freequest exception", e);
        } finally {
            spawned.forEach(net.minecraft.world.entity.Entity::discard);
            progress.restore(progressSaved, server.registryAccess());
            parties.restore(partiesSaved, server.registryAccess());
            level.setDayTime(dayBefore);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST freequest: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST freequest PASSED ({} free quests, {} checks)", freeQuests().size(), notes.size());
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST freequest FAILED: {}", f));
        }
    }

    static List<QuestDef> freeQuests() {
        // 1 章の続きのクエスト (潮殻〜余燼の段階で開くもの。story/StorySelfTest が確かめる) は数えない
        // 雷翼の背の周回 (段階 3。titan/storm/lore/StormLoreSelfTest が確かめる) も数えない
        return QuestManager.all().stream().filter(q -> !jp.revati.skyabyss.story.StorySelfTest.STORY_QUESTS.contains(q.id()))
                .filter(q -> !jp.revati.skyabyss.titan.storm.lore.StormLore.QUESTS.contains(q.id()))
                // 序章・一章の小要素のお使い (story/ch1。story/ch1/Ch1SelfTest が確かめる)
                .filter(q -> !q.id().startsWith("ch1/"))
                .filter(q -> q.board() || q.id().startsWith("sub_") && !List.of("sub_song", "sub_shards").contains(q.id()))
                .toList();
    }

    private static void check(boolean ok, String what, List<String> failures) {
        if (!ok) {
            failures.add(what);
        }
    }

    // ---------------------------------------------------------------- 道具
    private static FakePlayer player(ServerLevel level, int n, BlockPos at) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(String.format("f1ee0000-0000-4000-8000-0000000000%02d", n)),
                "[free_test_" + n + "]"));
        Lore.reset(p);
        p.getInventory().clearContent();
        p.setData(QuestSetup.LOG, new QuestLog());
        p.setData(NpcSetup.BONDS, new NpcBonds());
        // 17-10: 序盤のサブクエストは、イサナと会うまで出ない。試す人は会ったことにする (閉じていることは OpeningStorySelfTest が確かめる)
        NpcBonds.of(p).bond(WC).flags().add("met");
        Growth.get(p).level = 5;
        p.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5);
        return p;
    }

    private static String node(ServerPlayer p) {
        String n = DialogueService.currentNode(p);
        return n == null ? "(closed)" : n;
    }

    private static void talk(ServerPlayer p, NpcEntity npc) {
        DialogueService.open(p, npc);
        for (int i = 0; i < 3 && INTROS.contains(node(p)); i++) {
            next(p);
        }
    }

    private static void next(ServerPlayer p) {
        DialogueService.choose(p, node(p), DialogueService.CONTINUE);
    }

    private static boolean shown(ServerPlayer p, String role, String optionId) {
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

    private static boolean pick(ServerPlayer p, String role, String optionId, List<String> failures) {
        Dialogue d = DialogueManager.dialogue(role);
        String nodeId = DialogueService.currentNode(p);
        if (d == null || nodeId == null) {
            failures.add("no open dialogue to pick " + optionId);
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

    private static String stage(ServerPlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || def == null) {
            return "none";
        }
        return e.done ? "done" : def.stages().get(e.stage).id();
    }

    private static long totalXp(ServerPlayer p) {
        var g = Growth.get(p);
        long sum = g.xp;
        for (int l = 1; l < g.level; l++) {
            sum += GrowthRules.xpToNext(l);
        }
        return sum;
    }

    private static boolean hasKeepsake(ServerPlayer p, String nameKey) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.has(DataComponents.CUSTOM_NAME) && s.get(DataComponents.CUSTOM_NAME).getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(nameKey)) {
                return true;
            }
        }
        return false;
    }

    private static void stilled(MinecraftServer server, ServerPlayer p) {
        Progression.onTitanStopped(server, List.of(p.getUUID()), "stone_whale");
    }

    // ---------------------------------------------------------------- 本体
    private static void run(MinecraftServer server, ServerLevel level, List<net.minecraft.world.entity.Entity> spawned,
                            List<String> failures, List<String> notes) {
        loading(failures, notes);
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos at = new BlockPos(spawn.getX() + 40, 300, spawn.getZ() + 40);
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
        spawned.add(st);
        spawned.add(wc);
        // 日の始まりにそろえる (夜明けの直後)
        level.setDayTime(Math.floorDiv(level.getDayTime(), Repeats.DAY) * Repeats.DAY + 1000);
        hubMarks(level, st, wc, at, failures, notes);
        onceQuests(server, level, st, wc, at, failures, notes);
        boardQuests(server, level, at, spawned, failures, notes);
        party(server, level, at, failures, notes);
        saving(server, level, at, failures, notes);
    }

    // 「ほかに頼みは」の印: 先で新しい頼みが選べるときだけ金色 (quest)。先が空なら隠す。話の途中で状態が変われば映し直す
    private static String kindOf(ServerPlayer p, String role, String optionId) {
        Dialogue d = DialogueManager.dialogue(role);
        String nodeId = DialogueService.currentNode(p);
        if (d == null || nodeId == null) {
            return "(closed)";
        }
        Dialogue.Node n = d.nodes().get(nodeId);
        List<Integer> shown = DialogueService.shownOptions(p);
        for (int k = 0; k < shown.size(); k++) {
            if (n.options().get(shown.get(k)).id().equals(optionId)) {
                return DialogueService.shownKinds(p).get(k);
            }
        }
        return "(hidden)";
    }

    private static void hubMarks(ServerLevel level, NpcEntity st, NpcEntity wc, BlockPos at, List<String> failures, List<String> notes) {
        FakePlayer p = player(level, 9, at);
        // 鯨追い: 何も開いていないなら「ほかに頼みは」は隠れる
        talk(p, wc);
        check(kindOf(p, WC, "errands").equals("(hidden)"), "empty whale chaser errands hub should be hidden: " + kindOf(p, WC, "errands"), failures);
        DialogueService.close(p);
        // 語り部: 板だけ (その場で終わる) なら金色にしない
        talk(p, st);
        check(kindOf(p, ST, "errands").equals("talk"), "storyteller errands with only the board should be plain: " + kindOf(p, ST, "errands"), failures);
        // 話の途中で新しい頼みが開く (火のそばの話を聞いた) と、映し直されて金色になる
        NpcBonds.of(p).bond(WC).flags().add("c_whale_heard");
        check(DialogueService.refreshForTest(p), "refresh did not pick up the new errand", failures);
        check(kindOf(p, ST, "errands").equals("quest"), "storyteller errands with a new request should be gold: " + kindOf(p, ST, "errands"), failures);
        DialogueService.close(p);
        notes.add("hub marks: empty hub hidden, board-only plain, new request gold, live refresh");
    }

    // ---------------------------------------------------------------- 読み込み
    private static void loading(List<String> failures, List<String> notes) {
        List<QuestDef> free = freeQuests();
        check(free.size() >= 8 && free.size() <= 12, "expected 8-12 free quests, found " + free.size(), failures);
        long daily = free.stream().filter(q -> q.repeat().equals("daily")).count();
        long weekly = free.stream().filter(q -> q.repeat().equals("weekly")).count();
        check(daily >= 3 && weekly >= 1, "need dailies and weeklies (" + daily + "/" + weekly + ")", failures);
        for (QuestDef q : free) {
            check(q.board() == q.repeats(), q.id() + ": board quests should be exactly the repeating ones", failures);
            check(q.minStage() <= 1, q.id() + ": free quests for the prologue and chapter 1 should open by stage 1", failures);
            for (QuestDef.Stage s : q.stages()) {
                for (QuestDef.Objective o : s.objectives()) {
                    check(!(o.type().equals("kill") && o.targets().stream().anyMatch(t -> t.contains("tide"))), q.id()
                            + ": no free quest may ask for the next titan's back", failures);
                }
            }
        }
        // 足し書きが混ざっている: 語り部の話題に「ほかに、頼みごとはある？」が「また来る」の手前に入る
        Dialogue d = DialogueManager.dialogue(ST);
        Dialogue.Node menu = d == null ? null : d.nodes().get("menu");
        if (menu == null) {
            failures.add("storyteller menu missing");
        } else {
            int errands = -1;
            int bye = -1;
            for (int i = 0; i < menu.options().size(); i++) {
                errands = menu.options().get(i).id().equals("errands") ? i : errands;
                bye = menu.options().get(i).id().equals("bye") ? i : bye;
            }
            check(errands >= 0 && errands == bye - 1, "storyteller errands option should sit right before 'bye' (" + errands + "/" + bye + ")", failures);
            check(d.nodes().containsKey("fq_menu") && d.nodes().containsKey("fq_shadow_done2"), "storyteller extension nodes missing", failures);
            check(d.entries().get(d.entries().size() - 1).node().equals("menu"), "the catch-all entry must stay last", failures);
        }
        Dialogue c = DialogueManager.dialogue(WC);
        check(c != null && c.nodes().containsKey("fq_c_menu") && c.nodes().get("c_menu").options().stream().anyMatch(o -> o.id().equals("errands")),
                "whale chaser extension not merged", failures);
        notes.add("loaded " + free.size() + " free quests (" + daily + " daily, " + weekly + " weekly); dialogue extensions merged in place");
    }

    // ---------------------------------------------------------------- 一度きり (語り部・鯨追い)
    private static void onceQuests(MinecraftServer server, ServerLevel level, NpcEntity st, NpcEntity wc, BlockPos at,
                                   List<String> failures, List<String> notes) {
        FakePlayer a = player(level, 1, at);
        FakePlayer b = player(level, 2, at);

        // 空の影の帳面: 影を見て、メインが始まるまでは出ない
        talk(a, st);
        pick(a, ST, "errands", failures);
        check(node(a).equals("fq_menu"), "errands did not open fq_menu: " + node(a), failures);
        check(!shown(a, ST, "shadow") && !shown(a, ST, "parcel") && !shown(a, ST, "gaps"), "offers shown before they unlock", failures);
        DialogueService.close(a);
        Lore.unlock(a, "prologue/shadow", false);
        Quests.start(a, "main_song");
        long xp0 = totalXp(a);
        talk(a, st);
        pick(a, ST, "errands", failures);
        pick(a, ST, "shadow", failures);
        next(a);
        pick(a, ST, "accept", failures);
        check(stage(a, "sub_shadow_log").equals("watch"), "shadow log did not start: " + stage(a, "sub_shadow_log"), failures);
        FreeQuestData data = FreeQuestData.get(level);
        UUID fresh = UUID.randomUUID();
        check(data.firstPass(fresh, 12345) && !data.firstPass(fresh, 12345) && data.firstPass(fresh, 99999),
                "the same shadow pass counted twice or a new pass was ignored", failures);
        data.lastPass.remove(fresh);
        Quests.signal(a, FreeQuestEvents.SHADOW_SEEN);
        check(stage(a, "sub_shadow_log").equals("watch"), "one shadow should not be enough", failures);
        Quests.signal(a, FreeQuestEvents.SHADOW_SEEN);
        check(stage(a, "sub_shadow_log").equals("tell"), "two shadows did not move on", failures);
        talk(a, st);
        check(node(a).equals("fq_shadow_done"), "storyteller did not greet with the shadow report: " + node(a), failures);
        next(a);
        check(stage(a, "sub_shadow_log").equals("done") && Lore.state(a).has("prologue/shadow_heading"), "shadow log did not finish with its lore", failures);
        check(totalXp(a) - xp0 >= 60, "shadow log paid too little xp", failures);
        DialogueService.close(a);

        // 火のそばへ: a と b が同時に。包みは印つきで、ただの革では代わりにならない。なくしても詰まらない
        for (FakePlayer p : List.of(a, b)) {
            NpcBonds.of(p).bond(WC).flags().add("c_whale_heard");
            talk(p, st);
            pick(p, ST, "errands", failures);
            pick(p, ST, "parcel", failures);
            pick(p, ST, "accept", failures);
            check(stage(p, "sub_parcel").equals("deliver") && QuestItems.count(p, "minecraft:leather", "parcel") == 1,
                    "parcel did not start with a marked bundle for " + p.getName().getString(), failures);
            DialogueService.close(p);
        }
        a.getInventory().clearContent();
        a.getInventory().add(new ItemStack(Items.LEATHER, 1));
        talk(a, wc);
        check(!node(a).equals("fq_c_parcel"), "plain leather was taken as the storyteller's bundle", failures);
        DialogueService.close(a);
        talk(a, st);
        pick(a, ST, "errands", failures);
        pick(a, ST, "parcel_lost", failures);
        check(QuestItems.count(a, "minecraft:leather", "parcel") == 1, "a lost bundle was not replaced", failures);
        DialogueService.close(a);
        talk(a, wc);
        check(node(a).equals("fq_c_parcel"), "whale chaser did not take the bundle: " + node(a), failures);
        next(a);
        check(stage(a, "sub_parcel").equals("return") && QuestItems.count(a, "minecraft:bone", "clasp_return") == 1,
                "chaser did not hand back the clasp", failures);
        check(stage(b, "sub_parcel").equals("deliver"), "a's delivery moved b's quest", failures);
        DialogueService.close(a);
        // b は鯨追いに初めて会う: 最初は挨拶と話題、二度目に話しかけると包みを渡す節から
        NpcBonds.of(b).bond(WC).flags().remove("met");
        talk(b, wc);
        DialogueService.close(b);
        talk(b, wc);
        check(node(b).equals("fq_c_parcel"), "b could not deliver: " + node(b), failures);
        next(b);
        DialogueService.close(b);
        for (FakePlayer p : List.of(a, b)) {
            talk(p, st);
            check(node(p).equals("fq_parcel_back"), "storyteller did not greet with the clasp: " + node(p), failures);
            next(p);
            DialogueService.close(p);
            check(stage(p, "sub_parcel").equals("done") && hasKeepsake(p, "item.skyabyss.keepsake.whalebone_clasp"),
                    "parcel did not finish with the clasp keepsake for " + p.getName().getString(), failures);
        }

        // 書の空白を埋める: 数え歌のつづきのあと。序章 7 項目 → 一章 11 項目 → 語り部
        talk(a, st);
        pick(a, ST, "errands", failures);
        check(!shown(a, ST, "gaps"), "lore gaps offered before the counting song", failures);
        DialogueService.close(a);
        Quests.start(a, "sub_song");
        Quests.forceStage(a, "sub_song");
        Quests.forceStage(a, "sub_song");
        talk(a, st);
        pick(a, ST, "errands", failures);
        pick(a, ST, "gaps", failures);
        pick(a, ST, "accept", failures);
        DialogueService.close(a);
        for (String e : List.of("prologue/the_book", "prologue/counting_song", "prologue/seventh_night", "prologue/shaved_peak", "prologue/watcher")) {
            Lore.unlock(a, e, false);
        }
        Quests.poll(a, true);
        check(stage(a, "sub_lore_gaps").equals("chapter1"), "prologue complete did not move on: " + stage(a, "sub_lore_gaps"), failures);
        List<String> ch1 = List.of("chapter1/stone_whale", "chapter1/sky_ruins", "chapter1/inscription_west", "chapter1/inscription_east",
                "chapter1/inscription_below", "chapter1/inscription_heart", "chapter1/inscription_vault", "chapter1/captain",
                "chapter1/heart_stilled", "chapter1/sky_key");
        ch1.forEach(e -> Lore.unlock(a, e, false));
        Quests.poll(a, true);
        check(stage(a, "sub_lore_gaps").equals("chapter1"), "ten chapter-one entries should not be enough", failures);
        Lore.unlock(a, "chapter1/whale_song", false);
        Quests.poll(a, true);
        check(stage(a, "sub_lore_gaps").equals("tell"), "eleven chapter-one entries did not move on", failures);
        // 欠片を記したのでメインは「語り部に見せる」の段にいる。そこではメインの報告が先に開く (頼みの報告はその次)
        talk(a, st);
        check(node(a).equals("fragment"), "the main quest's report should come first: " + node(a), failures);
        for (int i = 0; i < 4 && !stage(a, "main_song").equals("done"); i++) {
            next(a);
        }
        DialogueService.close(a);
        check(stage(a, "main_song").equals("done"), "main quest did not finish in the free quest test", failures);
        // 岩鯨を見たので一章の支度の段も出ている。語り部の入口に割り込む一言 (職人を指す・足音の晩の話) は済ませておく
        jp.revati.skyabyss.npc.NpcBonds.of(a).bond("skyabyss:storyteller").flags().add("ch1p_pointer_told");
        Quests.onTalk(a, "skyabyss:storyteller", "ch1t_shield_tale");
        talk(a, st);
        check(node(a).equals("fq_gaps_done"), "storyteller did not greet with the book: " + node(a), failures);
        next(a);
        DialogueService.close(a);
        check(stage(a, "sub_lore_gaps").equals("done") && hasKeepsake(a, "item.skyabyss.keepsake.indigo_bookmark"), "lore gaps did not finish", failures);

        // 雲間の道標・干潮を数える: 岩鯨の心臓を止めるまで出ない
        talk(a, wc);
        check(!shown(a, WC, "errands"), "an empty errands hub should be hidden at stage 0", failures);
        DialogueService.close(a);
        stilled(server, a);
        talk(a, wc);
        pick(a, WC, "errands", failures);
        pick(a, WC, "way", failures);
        next(a);
        pick(a, WC, "accept", failures);
        DialogueService.close(a);
        Quests.signal(a, "titan_voyage");
        Quests.signal(a, "titan_voyage");
        check(stage(a, "sub_waymarks").equals("tell"), "two voyages did not move on", failures);
        talk(a, wc);
        check(node(a).equals("fq_c_way_done"), "chaser did not ask about the voyage: " + node(a), failures);
        pick(a, WC, "small", failures);
        next(a);
        DialogueService.close(a);
        check(stage(a, "sub_waymarks").equals("done") && hasKeepsake(a, "item.skyabyss.keepsake.notched_stone"), "waymarks did not finish", failures);

        talk(a, wc);
        pick(a, WC, "errands", failures);
        pick(a, WC, "tide", failures);
        next(a);
        pick(a, WC, "accept", failures);
        DialogueService.close(a);
        check(FreeQuestEvents.tideIndex(23500) == FreeQuestEvents.tideIndex(26000) && FreeQuestEvents.tideIndex(12000) != FreeQuestEvents.tideIndex(24500),
                "low tide numbering is off", failures);
        for (int i = 0; i < 3; i++) {
            Quests.signal(a, FreeQuestEvents.TIDE_WATCHED);
        }
        talk(a, wc);
        check(node(a).equals("fq_c_tide_done"), "chaser did not ask about the tide: " + node(a), failures);
        next(a);
        DialogueService.close(a);
        check(stage(a, "sub_tide_watch").equals("done") && Lore.state(a).has("chapter1/tide_hours"), "tide watch did not finish with its lore", failures);
        notes.add("once quests end to end: shadow log, parcel (two players at once, marked items, lost bundle), lore gaps, waymarks, tide watch");
    }

    // ---------------------------------------------------------------- 板 (日替わり・週替わり)
    private static void boardQuests(MinecraftServer server, ServerLevel level, BlockPos at, List<net.minecraft.world.entity.Entity> spawned,
                                    List<String> failures, List<String> notes) {
        FakePlayer p = player(level, 3, at);
        BlockPos board = at.offset(1, 0, 1);
        Board.open(p, board, false);
        List<Board.Line> lines = Board.lines(p);
        check(state(lines, "daily_bone_sentry") == Board.LOCKED || !onBoard(server, "daily_bone_sentry"),
                "a stage-1 daily was not hidden at stage 0", failures);
        check(state(lines, "weekly_back_trials") == Board.CLOSED, "back trials should wait for the whale's back: " + state(lines, "weekly_back_trials"), failures);
        check(!Board.accept(p, "weekly_back_trials"), "accepted a closed weekly", failures);
        // 同じ日は全員が同じ顔ぶれ
        check(Board.todays(server).equals(Board.todays(server)), "the board draw is not stable within a day", failures);

        // 日替わり 3 つ (地上のもの) を最後まで
        long xp0 = totalXp(p);
        check(acceptOrStart(server, p, "daily_lost_flock"), "could not take lost flock", failures);
        check(acceptOrStart(server, p, "daily_night_watch"), "could not take night watch", failures);
        check(acceptOrStart(server, p, "daily_firewood"), "could not take firewood", failures);
        check(Board.usedToday(p) == 3, "quota count is " + Board.usedToday(p), failures);
        // 羊: 2 頭では済まない、3 頭で済む
        List<Sheep> sheep = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Sheep s = EntityType.SHEEP.create(level);
            s.moveTo(board.getX() + 2 + i, board.getY(), board.getZ() + 2);
            s.setNoGravity(true);
            s.setNoAi(true);
            level.addFreshEntity(s);
            spawned.add(s);
            sheep.add(s);
            Quests.poll(p, true);
            if (i == 1) {
                check(stage(p, "daily_lost_flock").equals("herd") && QuestLog.of(p).entry("daily_lost_flock").progress(0) == 2,
                        "two sheep should show 2/3", failures);
            }
        }
        check(stage(p, "daily_lost_flock").equals("done"), "three sheep by the board did not finish the flock", failures);
        for (int i = 0; i < 6; i++) {
            Quests.onKill(p, "minecraft:zombie", true, 3);
        }
        check(stage(p, "daily_night_watch").equals("done"), "six night kills did not finish the watch", failures);
        p.getInventory().add(new ItemStack(Items.OAK_LOG, 15));
        Board.open(p, board, false);
        check(stage(p, "daily_firewood").equals("wood"), "fifteen logs were accepted for sixteen", failures);
        p.getInventory().add(new ItemStack(Items.BIRCH_LOG, 1));
        Board.open(p, board, false);
        check(stage(p, "daily_firewood").equals("done") && QuestItems.count(p, "#minecraft:logs", null) == 0,
                "sixteen mixed logs were not handed in", failures);
        long paid = totalXp(p) - xp0;
        check(paid >= 90, "three dailies paid " + paid, failures);

        // 枠: 4 つ目は受けられない。同じ日に同じものは受け直せない
        stilled(server, p);
        check(!acceptOrStartBoard(server, p, "daily_bone_sentry"), "a fourth daily was accepted the same day", failures);
        check(!Board.accept(p, "daily_night_watch") && stage(p, "daily_night_watch").equals("done"), "a finished daily was taken again the same day", failures);
        // 記録を消して同じ日に受け直しても、報酬は二度出ない
        long before = totalXp(p);
        Quests.forget(p, "daily_night_watch");
        Quests.start(p, "daily_night_watch");
        for (int i = 0; i < 6; i++) {
            Quests.onKill(p, "minecraft:zombie", true, 3);
        }
        check(totalXp(p) == before, "a daily paid twice in one day", failures);

        // 受けたまま日が替わっても残る。日が替わると、また受けられる
        level.setDayTime(level.getDayTime() + Repeats.DAY);
        check(acceptOrStart(server, p, "daily_bone_sentry"), "could not take the sentry rounds on a new day at stage 1", failures);
        level.setDayTime(level.getDayTime() + Repeats.DAY);
        check(stage(p, "daily_bone_sentry").equals("rounds"), "an unfinished daily vanished at dawn", failures);
        check(state(Board.lines(p), "daily_bone_sentry") == Board.ACTIVE, "an unfinished daily is not shown as in progress", failures);
        before = totalXp(p);
        for (int i = 0; i < 8; i++) {
            Quests.onKill(p, "skyabyss:bone_sentinel", true, 3);
        }
        check(stage(p, "daily_bone_sentry").equals("done") && totalXp(p) > before && QuestItems.count(p, "skyabyss:stone_whale_shard", null) >= 2,
                "sentry rounds did not pay", failures);
        before = totalXp(p);
        check(acceptOrStart(server, p, "daily_night_watch"), "a daily could not be taken again on a new day", failures);
        for (int i = 0; i < 6; i++) {
            Quests.onKill(p, "minecraft:zombie", true, 3);
        }
        check(totalXp(p) > before, "a daily on a new day did not pay", failures);

        // 週替わり 2 つ
        Lore.unlock(p, "chapter1/sky_ruins", false);
        check(acceptOrStart(server, p, "weekly_back_trials"), "could not take back trials after the back", failures);
        check(acceptOrStart(server, p, "weekly_heart_again"), "could not take the heart at stage 1", failures);
        for (int i = 0; i < 3; i++) {
            Quests.signal(p, FreeQuestEvents.TRIAL_CLEARED);
        }
        Quests.onKill(p, "skyabyss:stone_whale_heart", true, 5);
        check(stage(p, "weekly_back_trials").equals("done") && stage(p, "weekly_heart_again").equals("done"), "weeklies did not finish", failures);
        check(QuestItems.count(p, "skyabyss:resonance_crystal", null) >= 1, "heart again did not give a crystal", failures);
        check(!Board.accept(p, "weekly_heart_again"), "a weekly was taken twice in a week", failures);
        level.setDayTime(level.getDayTime() + Repeats.DAY * Repeats.WEEK_DAYS);
        check(acceptOrStart(server, p, "weekly_heart_again"), "a weekly could not be taken in the next week", failures);
        // NPC が板の代わりに見せる (語り部の「村の頼みごと」) と、同じ決まりで受けられる
        FakePlayer q = player(level, 6, at);
        Board.open(q, at, true);
        check(Board.sessionPos(q) != null && Board.lines(q).stream().anyMatch(l -> l.state() == Board.OPEN), "the npc board shows nothing to take", failures);
        notes.add("board: stable daily draw, quota " + Board.QUOTA + ", herd/kill/turn-in/trial/heart end to end, no double pay, dailies survive dawn, weeklies reset");
    }

    private static boolean onBoard(MinecraftServer server, String quest) {
        return Board.todays(server).stream().anyMatch(q -> q.id().equals(quest));
    }

    private static byte state(List<Board.Line> lines, String quest) {
        for (Board.Line l : lines) {
            if (l.quest().equals(quest)) {
                return l.state();
            }
        }
        return -1;
    }

    // 今日の抽選に出ていれば板で受ける。出ていなければ (顔ぶれが増えたときのため) 受け口を通さず始める
    private static boolean acceptOrStart(MinecraftServer server, ServerPlayer p, String quest) {
        Board.open(p, p.blockPosition(), false);
        if (onBoard(server, quest)) {
            return Board.accept(p, quest);
        }
        return Quests.start(p, quest, p.blockPosition());
    }

    private static boolean acceptOrStartBoard(MinecraftServer server, ServerPlayer p, String quest) {
        Board.open(p, p.blockPosition(), false);
        return onBoard(server, quest) && Board.accept(p, quest);
    }

    // ---------------------------------------------------------------- パーティ
    private static void party(MinecraftServer server, ServerLevel level, BlockPos at, List<String> failures, List<String> notes) {
        FakePlayer a = player(level, 7, at);
        FakePlayer b = player(level, 8, at);
        FakePlayer c = player(level, 9, at);
        PartyData data = PartyData.get(server);
        data.remove(a.getUUID());
        data.remove(b.getUUID());
        data.remove(c.getUUID());
        PartyData.Party party = data.create(a.getUUID(), "a");
        data.add(party, b.getUUID(), "b");
        Map<UUID, ServerPlayer> online = new HashMap<>();
        for (ServerPlayer p : List.of(a, b, c)) {
            online.put(p.getUUID(), p);
            Quests.start(p, "daily_night_watch", p.blockPosition());
            Quests.start(p, "sub_shadow_log");
        }
        a.moveTo(at.getX(), at.getY(), at.getZ());
        b.moveTo(at.getX() + 50, at.getY(), at.getZ());
        c.moveTo(at.getX() - 50, at.getY(), at.getZ());
        Zombie z = EntityType.ZOMBIE.create(level);
        z.moveTo(at.getX() + 1, at.getY(), at.getZ());
        QuestEvents.credit(level, z, "minecraft:zombie", a, online::get);
        check(QuestLog.of(a).entry("daily_night_watch").progress(0) == 1, "the killer got no credit", failures);
        check(QuestLog.of(b).entry("daily_night_watch").progress(0) == 1, "a party member 50 blocks away got no credit", failures);
        check(QuestLog.of(c).entry("daily_night_watch").progress(0) == 0, "a stranger 50 blocks away got credit", failures);
        // 合図の共有: 試練は仲間も、影の帳面 (人ごとの目的) は本人だけ
        Quests.signalWithParty(level, List.of(a), FreeQuestEvents.SHADOW_SEEN, online::get);
        check(QuestLog.of(a).entry("sub_shadow_log").progress(0) == 1 && QuestLog.of(b).entry("sub_shadow_log").progress(0) == 0,
                "a personal signal was shared with the party", failures);
        Quests.start(a, "weekly_back_trials", a.blockPosition());
        Quests.start(b, "weekly_back_trials", b.blockPosition());
        Quests.signalWithParty(level, List.of(a), FreeQuestEvents.TRIAL_CLEARED, online::get);
        check(QuestLog.of(b).entry("weekly_back_trials").progress(0) == 1, "a party member did not share the trial", failures);
        // 板: 仲間が受けている数 (オンラインの人だけを数えるので、偽のプレイヤーでは 0。数え方だけ確かめる)
        check(Board.lines(a).stream().allMatch(l -> l.mates() >= 0), "mate counts broken", failures);
        data.disband(party);
        notes.add("party: kills shared with mates in range, not with strangers; party signals shared, personal signals not");
    }

    // ---------------------------------------------------------------- 保存
    private static void saving(MinecraftServer server, ServerLevel level, BlockPos at, List<String> failures, List<String> notes) {
        FakePlayer p = player(level, 10, at);
        Quests.start(p, "daily_lost_flock", at);
        Quests.start(p, "weekly_heart_again", at);
        QuestLog log = QuestLog.of(p);
        Tag tag = QuestLog.CODEC.encodeStart(NbtOps.INSTANCE, log).getOrThrow();
        QuestLog back = QuestLog.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        check(back.equals(log), "quest log with cycles and origins did not round-trip", failures);
        check(back.entry("daily_lost_flock").origin == at.asLong() && back.entry("daily_lost_flock").cycle == Repeats.day(server),
                "origin or cycle lost on save", failures);
        // ワールドの記録 (板を立てた村、数えた影と潮)
        FreeQuestData d = FreeQuestData.get(level);
        CompoundTag saved = d.save(new CompoundTag(), server.registryAccess());
        check(saved.contains("boards") && saved.contains("last_pass"), "free quest world data did not save", failures);
        notes.add("saving: quest log (cycle, origin, claimed) and world data round-trip");
    }
}
