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
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
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
        // 後の巨神の背中の口: 仮の背中を登録すると、ディメンション移動の取り消しが効く
        ResourceKey<Level> fake = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "selftest_thunder_back"));
        List<String> sentDown = new ArrayList<>();
        check(!Titans.hasBack(Titans.THUNDER_WING), "thunder wing already has a back (remove this check when it is made)", f);
        Titans.bindBack(fake, Titans.THUNDER_WING, p -> sentDown.add(p.getGameProfile().getName()));
        try {
            check(Titans.THUNDER_WING.equals(Progression.titanOfBack(fake)), "the bound back is not known", f);
            EntityTravelToDimensionEvent denied = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(b, fake));
            EntityTravelToDimensionEvent allowed = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(a, fake));
            check(denied.isCanceled() && !allowed.isCanceled(), "travel to the thunder back was not gated", f);
            Titans.sendDown(b, Titans.THUNDER_WING);
            check(sentDown.equals(List.of("story_test_b")), "sendDown did not use the bound way down", f);
        } finally {
            Titans.unbindBack(fake);
        }
        check(Progression.titanOfBack(fake) == null && !Titans.hasBack(Titans.THUNDER_WING), "unbind did not remove the back", f);
        check(Titans.STONE_WHALE.equals(Progression.titanOfBack(jp.revati.skyabyss.titan.TitanTravel.TITAN_BACK))
                && Titans.TIDE_SHELL.equals(Progression.titanOfBack(jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK)), "built-in backs are missing", f);
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
        check(stage(p, "main_thunder").equals("heart"), "seeing the thunder wing did not advance", f);
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("heart"), "the thunder heart stage passed without stopping the heart", f);
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
        check(SkyKey.fragmentsHeld(p) == 0 && p.getInventory().countItem(StorySetup.SKY_KEY.get()) == 1, "fragments were not turned into one key", f);
        check(Lore.state(p).has(SkyKey.LORE) && SkyKey.assembled(p), "the key was not recorded", f);
        check(!SkyKey.assemble(p, false) && p.getInventory().countItem(StorySetup.SKY_KEY.get()) == 1, "the key formed twice", f);
        check(!Keepsakes.fragmentMissing(p), "after the key, the whale heart would give a lost fragment again", f);
        check(Keepsakes.untossable(new ItemStack(StorySetup.SKY_KEY.get())), "the key can be tossed", f);
        // 段階が足りない人は、欠片を 4 つ持っていても組まない (コマンドで配った欠片など)
        FakePlayer q = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "story_test_q"));
        q.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 4));
        check(!SkyKey.ready(q), "a player at stage 0 with four fragments was ready", f);

        Quests.poll(p, true);
        check(stage(p, "main_ember").equals("done") && stage(p, "main_key").equals("show"), "the key did not open main_key: " + stage(p, "main_key"), f);
        talk(p, st, "ms_key", f);
        next(p);
        next(p);
        check(Lore.state(p).has("chapter1/fifth_verse"), "the fifth verse was not recorded", f);
        next(p);
        check(node(p).equals("ms_key4") && stage(p, "main_key").equals("done"), "main_key did not finish: " + node(p), f);
        pick(p, ST, "ok", f);
        // イサナは一度だけ鍵の話をする
        talk(p, wc, "ms_c_key", f);
        next(p);
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
        pick(p, WC, "ok", f);
        level.setWeatherParameters(6000, 0, false, false);

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
        DialogueService.open(p, npc);
        check(node(p).equals(expected), "expected node " + expected + ", got " + node(p), f);
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
