package jp.revati.skyabyss.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.quest.Action;
import jp.revati.skyabyss.quest.Condition;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 章の導きの基盤 (docs/design/21 の P3) の自己テスト。段: "prep chain" (備えの頼みの規則・合図・覚書の受け渡し)、"wayfarer notes" (レシピの解放)、
// "heartbeat ward" (鼓動守)、"site cave" (湧き台・クリアの記録・宝・周回・グループごと・章のひとり歩き)、"xp budget" (経験値の枠)。
// 場所とクエストは、自己テストの中でだけ作る仮のもの (dev 専用。本番のデータには入らない)
public final class GuideSelfTest {
    private static final String SITE = "selftest_site_cave";
    private static final String CAP_SITE = "selftest_site_cap";
    private static final String TREASURE = "selftest_site_cave";
    private static final String REPEAT = "selftest_site_cave_repeat";

    private GuideSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("prep chain", GuideSelfTest::prepChain),
                SelfTestRunner.Step.once("wayfarer notes", GuideSelfTest::wayfarerNotes),
                SelfTestRunner.Step.once("heartbeat ward", GuideSelfTest::heartbeatWard),
                SelfTestRunner.Step.once("site cave", GuideSelfTest::siteCave),
                SelfTestRunner.Step.once("xp budget", GuideSelfTest::xpBudget));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        Growth.get(p).level = 30;
        return p;
    }

    private static void report(String name, List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST {} PASSED", name);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST {} FAILED: {}", name, m));
        }
    }

    private static QuestDef quest(String id, String json, List<String> errors) {
        return QuestDef.parse(id, JsonParser.parseString(json).getAsJsonObject(), errors);
    }

    // 備えの頼みの仮の定義 (一章)。signal の目的が一つだけの 1 段
    private static String prepJson(String signal, int xp) {
        return "{\"category\":\"sub\",\"order\":" + (50 + xp % 7) + ",\"giver\":\"skyabyss:storyteller\",\"chapter\":\"titan_whale\","
                + "\"start_when\":{\"type\":\"true\"},\"stages\":[{\"id\":\"go\",\"objectives\":[{\"id\":\"do\",\"type\":\"signal\",\"target\":\""
                + signal + "\",\"party\":false}]}],\"reward\":{\"xp\":" + xp + "}}";
    }

    private static List<QuestDef> prepDefs(List<String> errors, String chapterId, int n) {
        List<QuestDef> defs = new ArrayList<>();
        defs.add(quest("ch" + n + "/prep_gear", prepJson("t_gear" + n, 80).replace("titan_whale", chapterId), errors));
        defs.add(quest("ch" + n + "/prep_blade", prepJson("t_blade" + n, 160).replace("titan_whale", chapterId), errors));
        defs.add(quest("ch" + n + "/prep_pulse", prepJson("t_pulse" + n, 60).replace("titan_whale", chapterId), errors));
        return defs;
    }

    // ================================================================ 備えの頼み
    static void prepChain(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = player(level, "prep_a");
        FakePlayer b = player(level, "prep_b");
        Runnable restore = null;
        // 二章・四章は支度の段の章 (CHAPTER_IDS) だが、既定の形 (ch<n>/prep_*) の決まりを試すために、この段のあいだだけ既定の形に戻す
        Runnable form2 = PrepQuests.testDefaultForm(2);
        Runnable form4 = PrepQuests.testDefaultForm(4);
        try {
            // 規則 (ID・種類・検査)
            // 一章は支度の段 (ch1/trail_*)。二〜四章は既定の形 (ch<n>/prep_*)。一章の ch1/prep_* はもう 3 本に数えない
            check(f, PrepQuests.chapterOf("ch1/trail_iron") == 1 && PrepQuests.chapterOf("ch1/trail_shield") == 1 && PrepQuests.chapterOf("ch1/prep_gear") == 0
                    && PrepQuests.chapterOf("ch2/prep_gear") == 2 && PrepQuests.chapterOf("ch4/prep_pulse") == 4 && PrepQuests.chapterOf("ch5/prep_gear") == 0
                    && PrepQuests.chapterOf("ch1/day1_cairns") == 0 && PrepQuests.chapterOf("main_song") == 0, "chapterOf does not read ch1/trail_* and ch2〜ch4/prep_*");
            check(f, PrepQuests.kindOf("ch2/prep_blade").equals("blade") && PrepQuests.kindOf("ch1/trail_shield").equals("shield") && PrepQuests.readySignal(3).equals("ch3_ready")
                    && PrepQuests.id(2, "pulse").equals("ch2/prep_pulse") && PrepQuests.ids(1).equals(List.of("ch1/trail_iron", "ch1/trail_blade", "ch1/trail_shield"))
                    && PrepQuests.isTrail(1) && !PrepQuests.isTrail(2), "kind / signal / id helpers are wrong");
            List<String> errors = new ArrayList<>();
            List<QuestDef> good = prepDefs(errors, "titan_tide", 2);
            check(f, errors.isEmpty(), "the test prep quests did not parse: " + errors);
            java.util.Map<String, QuestDef> map = new java.util.LinkedHashMap<>();
            good.forEach(d -> map.put(d.id(), d));
            List<String> ve = new ArrayList<>();
            PrepQuests.validate(map, ve);
            check(f, ve.isEmpty(), "valid prep quests were flagged: " + ve);
            // 規則違反: main、章違い、start_when なし、繰り返し、板、名前違い
            List<String> junk = new ArrayList<>();
            java.util.Map<String, QuestDef> bad = new java.util.LinkedHashMap<>();
            bad.put("ch4/prep_gear", quest("ch4/prep_gear", prepJson("x", 1).replace("\"sub\"", "\"main\"").replace("titan_whale", "titan_ember"), junk));
            bad.put("ch4/prep_blade", quest("ch4/prep_blade", prepJson("x", 1).replace("titan_whale", "titan_tide"), junk));
            bad.put("ch4/prep_pulse", quest("ch4/prep_pulse", prepJson("x", 1).replace("\"start_when\":{\"type\":\"true\"},", "").replace("titan_whale", "titan_ember"), junk));
            bad.put("ch1/prep_gear", quest("ch1/prep_gear", prepJson("x", 1), junk));
            bad.put("ch1/trail_iron", quest("ch1/trail_iron", prepJson("x", 1), junk));
            bad.put("ch2/prep_gear", quest("ch2/prep_gear", prepJson("x", 0).replace("\"reward\":{\"xp\":0}", "\"reward\":{\"xp\":0},\"repeat\":\"daily\"")
                    .replace("\"start_when\":{\"type\":\"true\"},", "").replace("titan_whale", "titan_tide"), junk));
            bad.put("ch2/prep_odd", quest("ch2/prep_odd", prepJson("x", 1).replace("titan_whale", "titan_tide"), junk));
            // 三章は支度の段の章 (ch3/prep_* は使わない) なので、板の検査は二章の既定の形で
            bad.put("ch2/prep_pulse", quest("ch2/prep_pulse", prepJson("x", 1).replace("titan_whale", "titan_tide").replace("\"sub\"", "\"sub\",\"board\":true"), junk));
            List<String> be = new ArrayList<>();
            PrepQuests.validate(bad, be);
            check(f, be.stream().anyMatch(s -> s.contains("ch4/prep_gear") && s.contains("sub quest")), "a main prep quest was not flagged");
            check(f, be.stream().anyMatch(s -> s.contains("ch4/prep_blade") && s.contains("chapter must be")), "a prep quest in the wrong chapter was not flagged");
            check(f, be.stream().anyMatch(s -> s.contains("ch4/prep_pulse") && s.contains("start_when")), "a prep quest without start_when was not flagged");
            check(f, be.stream().anyMatch(s -> s.contains("ch1/prep_gear") && s.contains("chapter 1 uses")), "an old chapter-1 prep quest was not flagged");
            check(f, be.stream().anyMatch(s -> s.contains("ch1/trail_iron") && s.contains("main quest")), "a chapter-1 trail step as a sub quest was not flagged");
            check(f, be.stream().anyMatch(s -> s.contains("ch2/prep_gear") && s.contains("repeat")), "a repeating prep quest was not flagged");
            check(f, be.stream().anyMatch(s -> s.contains("ch2/prep_odd") && s.contains("kind")), "an unknown prep kind was not flagged");
            check(f, be.stream().anyMatch(s -> s.contains("ch2/prep_pulse") && s.contains("board")), "a prep quest on the board was not flagged");

            // 三本が定義されていない章は、準備ができたことにならない
            check(f, PrepQuests.defined(1) == (QuestManager.quest("ch1/trail_iron") != null && QuestManager.quest("ch1/trail_blade") != null
                    && QuestManager.quest("ch1/trail_shield") != null), "defined() disagrees with the loaded data");
            check(f, !PrepQuests.defined(2), "chapter 2 should not have prep quests in the shipped data yet");
            // 流れ: 仮の三本 (二章) + 合図を受ける見張り
            List<QuestDef> temp = new ArrayList<>(good);
            temp.add(quest("selftest/ready_probe", "{\"category\":\"sub\",\"order\":99,\"stages\":[{\"id\":\"w\",\"objectives\":[{\"id\":\"r\",\"type\":\"signal\","
                    + "\"target\":\"ch2_ready\",\"party\":false}]}]}", errors));
            restore = QuestManager.addTemporary(temp);
            Runnable restoreNow = restore;
            check(f, PrepQuests.defined(2) && !PrepQuests.defined(4), "defined() is wrong for the injected chapter");
            check(f, !PrepQuests.ready(a, 2) && PrepQuests.done(a, 2) == 0, "a fresh player was ready");
            Quests.start(a, "selftest/ready_probe");
            // 章の頭に自動で出る (start_when)
            Quests.poll(a, false);
            QuestLog log = QuestLog.of(a);
            check(f, log.entry("ch2/prep_gear") != null && log.entry("ch2/prep_blade") != null && log.entry("ch2/prep_pulse") != null,
                    "the prep quests did not appear on their own (start_when)");
            // 覚書: 3 本のどれかを受けたとき、人ごとに一度
            check(f, WayfarerNotes.has(a, 2) && countNotes(a, 2) == 1, "the notes were not handed over when the prep quests started");
            Quests.forget(a, "ch2/prep_gear");
            Quests.start(a, "ch2/prep_gear");
            check(f, countNotes(a, 2) == 1, "the notes were handed over twice");
            check(f, !WayfarerNotes.has(b, 2), "another player got the notes");

            // 順番はふさがない: 刃 → 糧 のあと身ごしらえ。二本のとき ready ではないが atLeast(2)
            Condition two = Condition.parse(JsonParser.parseString("{\"type\":\"prep\",\"chapter\":2,\"min\":2}"), errors, "t");
            Condition all = Condition.parse(JsonParser.parseString("{\"type\":\"prep\",\"chapter\":2}"), errors, "t");
            StoryContext ctx = StoryContext.of(a);
            check(f, !two.test(ctx) && !all.test(ctx), "the prep condition was true with nothing done");
            Quests.signal(a, "t_blade2");
            check(f, PrepQuests.done(a, 2) == 1 && !two.test(ctx), "one prep quest counted wrong");
            Quests.signal(a, "t_pulse2");
            check(f, PrepQuests.done(a, 2) == 2 && two.test(ctx) && !all.test(ctx) && PrepQuests.atLeast(a, 2, 2) && !PrepQuests.ready(a, 2),
                    "two prep quests did not satisfy min 2 / did not stay short of ready");
            check(f, QuestLog.of(a).entry("selftest/ready_probe") != null && !QuestLog.of(a).entry("selftest/ready_probe").done,
                    "ch1_ready fired before the third prep quest");
            Quests.signal(a, "t_gear2");
            check(f, PrepQuests.done(a, 2) == 3 && PrepQuests.ready(a, 2) && all.test(ctx) && PrepQuests.groupAtLeast(a, 2, 3),
                    "three prep quests did not make the player ready");
            check(f, QuestLog.of(a).entry("selftest/ready_probe").done, "the ch1_ready signal was not sent when the third quest finished");
            // 合図は一度だけ (記録を戻して終え直しても、もう一度は鳴らない)
            Quests.forget(a, "selftest/ready_probe");
            Quests.start(a, "selftest/ready_probe");
            Quests.forget(a, "ch2/prep_pulse");
            Quests.start(a, "ch2/prep_pulse");
            Quests.signal(a, "t_pulse2");
            check(f, PrepQuests.ready(a, 2) && !QuestLog.of(a).entry("selftest/ready_probe").done, "the ready signal fired twice for one player");

            // 別の順番 (人ごと): 糧 → 身ごしらえ → 刃
            Quests.poll(b, false);
            Quests.signal(b, "t_pulse2");
            Quests.signal(b, "t_gear2");
            check(f, !PrepQuests.ready(b, 2) && PrepQuests.done(b, 2) == 2 && !PrepQuests.ready(a, 3), "progress leaked between players or chapters");
            Quests.signal(b, "t_blade2");
            check(f, PrepQuests.ready(b, 2), "a different order did not finish the chain");

            // 結果 give_notes: なくした人に渡し直す。持っていれば渡さない
            List<String> ae = new ArrayList<>();
            List<Action> give = Action.parseList(JsonParser.parseString("{\"type\":\"give_notes\",\"chapter\":2}"), ae, "t");
            check(f, ae.isEmpty() && give.size() == 1, "give_notes did not parse: " + ae);
            give.get(0).run(StoryContext.of(a));
            check(f, countNotes(a, 2) == 1, "give_notes gave a second copy to someone who has one");
            a.getInventory().clearContent();
            give.get(0).run(StoryContext.of(a));
            check(f, countNotes(a, 2) == 1, "give_notes did not replace lost notes");
            // 条件の検査: 章が 1〜4 でなければ警告
            List<String> ce = new ArrayList<>();
            Condition.parse(JsonParser.parseString("{\"type\":\"prep\",\"chapter\":9}"), ce, "t");
            check(f, !ce.isEmpty(), "a prep condition with chapter 9 was accepted");
            restoreNow.run();
            restore = null;
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST prep chain crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (restore != null) {
                restore.run();
            }
            form2.run();
            form4.run();
            for (FakePlayer p : List.of(a, b)) {
                Quests.forgetAll(p);
                Growth.get(p).discoveries.clear();
            }
        }
        report("prep chain", f);
    }

    private static int countNotes(ServerPlayer p, int chapter) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(GuideSetup.WAYFARER_NOTES.get()) && WayfarerNotes.chapterOf(s) == chapter) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ================================================================ 旅の覚書
    static void wayfarerNotes(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = player(level, "notes_a");
        ResourceLocation recipe = ResourceLocation.withDefaultNamespace("stone_pickaxe");
        try {
            ItemStack notes = WayfarerNotes.make(2);
            check(f, WayfarerNotes.chapterOf(notes) == 2 && notes.is(GuideSetup.WAYFARER_NOTES.get()) && WayfarerNotes.chapterOf(WayfarerNotes.make(9)) == 4
                    && WayfarerNotes.chapterOf(new ItemStack(Items.PAPER)) == 0, "the notes did not keep their chapter");
            var holder = server.getRecipeManager().byKey(recipe);
            check(f, holder.isPresent(), "the vanilla recipe used by the test is missing");
            WayfarerNotes.registerRecipes(2, recipe, ResourceLocation.fromNamespaceAndPath("skyabyss", "no_such_recipe"));
            check(f, WayfarerNotes.recipes(2).size() == 2 && WayfarerNotes.recipes(3).isEmpty(), "registerRecipes did not keep the ids per chapter");
            if (holder.isPresent()) {
                a.getRecipeBook().remove(holder.get());
                check(f, !a.getRecipeBook().contains(holder.get()), "the test recipe was already unlocked");
                check(f, WayfarerNotes.unlockRecipes(a, 3) == 0 && !a.getRecipeBook().contains(holder.get()), "another chapter's notes unlocked the recipe");
                WayfarerNotes.onOpen(a, 2);
                check(f, a.getRecipeBook().contains(holder.get()), "opening the notes did not unlock the recipe");
                check(f, WayfarerNotes.unlockRecipes(a, 2) == 0, "an unlocked recipe was counted again or a missing recipe was counted");
                a.getRecipeBook().remove(holder.get());
            }
            // 渡す: 人ごとに一度。渡し直しは持っていないときだけ
            check(f, WayfarerNotes.give(a, 2) && !WayfarerNotes.give(a, 2) && countNotes(a, 2) == 1, "give did not hand over once");
            check(f, !WayfarerNotes.give(a, 2, true) && countNotes(a, 2) == 1, "a reissue duplicated the notes");
            a.getInventory().clearContent();
            check(f, WayfarerNotes.give(a, 2, true) && countNotes(a, 2) == 1, "a reissue did not replace lost notes");
            // 文: 日英 (ja_jp.json / en_us.json に、ここで使うキーがある)
            for (String lang : List.of("ja_jp", "en_us")) {
                java.util.Map<?, ?> texts = langFile(lang);
                for (String key : List.of("item.skyabyss.wayfarer_notes", "item.skyabyss.wayfarer_notes.named", "item.skyabyss.wayfarer_notes.desc",
                    "message.skyabyss.notes.given", "message.skyabyss.notes.recipes", "message.skyabyss.prep.ready", "wayfarer_notes.empty",
                    "item.skyabyss.heartbeat_ward_stone_whale", "item.skyabyss.heartbeat_ward_tide_shell", "item.skyabyss.heartbeat_ward_thunder_wing",
                    "item.skyabyss.heartbeat_ward_ember", "item.skyabyss.heartbeat_ward.desc", "message.skyabyss.heartbeat_ward.used",
                        "growth.skyabyss.reason.site")) {
                    check(f, texts != null && texts.containsKey(key), lang + " is missing " + key);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST wayfarer notes crashed", e);
            f.add("crashed: " + e);
        } finally {
            WayfarerNotes.clearRecipes(2);
            Growth.get(a).discoveries.clear();
        }
        report("wayfarer notes", f);
    }

    private static java.util.Map<?, ?> langFile(String lang) {
        try (var in = GuideSelfTest.class.getResourceAsStream("/assets/skyabyss/lang/" + lang + ".json")) {
            return in == null ? null : new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), java.util.Map.class);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    // ================================================================ 鼓動守
    private static final class BossHost implements GroupBossRoom.Host {
        final net.minecraft.world.entity.EntityType<? extends Mob> bossType;

        BossHost(net.minecraft.world.entity.EntityType<? extends Mob> bossType) {
            this.bossType = bossType;
        }

        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return List.of();
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
        }

        @Override
        public void open(ServerLevel level, UUID group) {
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
        }

        @Override
        public void letBack(ServerLevel level, UUID group, java.util.Set<UUID> participants) {
        }

        @Override
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            Mob boss = bossType.create(level);
            boss.moveTo(0.5, 300, 0.5, 0f, 0f);
            if (boss instanceof GroupOwned owned) {
                owned.setGroupId(group);
            }
            level.addFreshEntity(boss);
            return boss;
        }

        @Override
        public Vec3 home() {
            return new Vec3(0.5, 300, 0.5);
        }

        @Override
        public double leash() {
            return 1000;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return false;
        }
    }

    private static void wear(ServerPlayer p, int slot, net.minecraft.world.item.Item item) {
        Accessories.get(p).items.set(slot, new ItemStack(item));
    }

    static void heartbeatWard(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = player(level, "ward_a");
        FakePlayer b = player(level, "ward_b");
        GroupBossRoom heart = null;
        GroupBossRoom other = null;
        BossHost heartHost = new BossHost(ModEntities.STONE_WHALE_HEART.get());
        BossHost midHost = new BossHost(ModEntities.MOSS_CRAWLER.get());
        try {
            // 四つの巨神の鼓動守
            for (String titan : List.of("stone_whale", "tide_shell", "thunder_wing", "ember")) {
                check(f, GuideSetup.wardOf(titan) instanceof HeartbeatWardItem w && w.titan().equals(titan) && w.dungeon().equals(titan),
                        "the " + titan + " heartbeat ward is missing or points at another titan");
            }
            check(f, GuideSetup.wardOf("nobody") == null, "wardOf accepted an unknown titan");
            // 式 (純粋)
            check(f, HeartbeatWard.survivable(10f, 6f, 0f) == 5f, "a lethal 10 against 6 HP should be cut to 5 (1 HP left)");
            check(f, HeartbeatWard.survivable(3f, 6f, 0f) == 3f, "a non-lethal hit was changed");
            check(f, HeartbeatWard.survivable(6f, 6f, 0f) == 5f, "a hit equal to health is lethal and should leave 1 HP");
            check(f, HeartbeatWard.survivable(9f, 6f, 4f) == 9f && HeartbeatWard.survivable(10f, 6f, 4f) == 9f, "absorption is not counted");
            check(f, HeartbeatWard.survivable(5f, 1f, 0f) == 0f, "a lethal hit at 1 HP should be cut to 0");
            DamageContainer generic = new DamageContainer(level.damageSources().generic(), 6f);
            check(f, HeartbeatWard.lethal(6f, 6f, 0f, generic.getSource()) && !HeartbeatWard.lethal(5.99f, 6f, 0f, generic.getSource())
                    && !HeartbeatWard.lethal(9.9f, 6f, 4f, null), "the lethal threshold is wrong");
            check(f, !HeartbeatWard.lethal(100f, 6f, 0f, level.damageSources().fellOutOfWorld()), "a hit that bypasses invulnerability was survived");

            // 備えの糧の灯に +0.2 (枠に付けているとき)
            double without = Readiness.of(a, "stone_whale").provisions();
            wear(a, 1, GuideSetup.HEARTBEAT_WARD_STONE_WHALE.get());
            double with = Readiness.of(a, "stone_whale").provisions();
            check(f, Math.abs((with - without) - 0.2) < 1e-9, "the ward did not add 0.2 to the provisions lamp (" + without + " -> " + with + ")");
            a.getInventory().setItem(0, new ItemStack(GuideSetup.HEARTBEAT_WARD_TIDE_SHELL.get()));
            Accessories.get(a).items.set(1, ItemStack.EMPTY);
            check(f, Math.abs(Readiness.of(a, "stone_whale").provisions() - without) < 1e-9, "a ward in the pack (not worn) counted as a charm");
            a.getInventory().clearContent();

            // 心臓の戦いの外では働かない
            wear(a, 1, GuideSetup.HEARTBEAT_WARD_STONE_WHALE.get());
            a.setHealth(6f);
            check(f, !HeartbeatWard.trigger(a, 20f, level.damageSources().generic(), x -> { }) && !Accessories.stack(a, 1).isEmpty(),
                    "the ward worked outside a heart fight");

            // 心臓の戦い
            heart = new GroupBossRoom(Groups.keyOf(a));
            check(f, heart.start(level, heartHost, List.of(a)), "the test heart fight did not start");
            check(f, GroupBossRoom.heartFightOf(level, a.getUUID()) == heart && GroupBossRoom.heartFightOf(level, b.getUUID()) == null,
                    "heartFightOf does not find exactly the participant");
            // 本物のイベントの経路
            LivingDamageEvent.Pre pre = new LivingDamageEvent.Pre(a, new DamageContainer(level.damageSources().generic(), 20f));
            a.setHealth(6f);
            a.setAbsorptionAmount(0f);
            HeartbeatWard.onDamagePre(pre);
            check(f, pre.getNewDamage() == 5f, "the damage event was not cut to leave 1 HP (" + pre.getNewDamage() + ")");
            check(f, Accessories.stack(a, 1).isEmpty(), "the ward did not vanish after saving the player");
            // 一度の戦いに一度 (別の守りを付けても)
            wear(a, 1, GuideSetup.HEARTBEAT_WARD_STONE_WHALE.get());
            LivingDamageEvent.Pre again = new LivingDamageEvent.Pre(a, new DamageContainer(level.damageSources().generic(), 20f));
            HeartbeatWard.onDamagePre(again);
            check(f, again.getNewDamage() == 20f && !Accessories.stack(a, 1).isEmpty(), "the ward worked twice in one fight");
            // 軽い傷では使わない
            heart.reset(level, heartHost, false);
            heart = new GroupBossRoom(Groups.keyOf(a));
            heart.start(level, heartHost, List.of(a));
            LivingDamageEvent.Pre light = new LivingDamageEvent.Pre(a, new DamageContainer(level.damageSources().generic(), 2f));
            HeartbeatWard.onDamagePre(light);
            check(f, light.getNewDamage() == 2f && !Accessories.stack(a, 1).isEmpty(), "a survivable hit spent the ward");
            // 戦いが新しくなれば、また一度 (やり直し)
            LivingDamageEvent.Pre fresh = new LivingDamageEvent.Pre(a, new DamageContainer(level.damageSources().generic(), 20f));
            HeartbeatWard.onDamagePre(fresh);
            check(f, fresh.getNewDamage() == 5f, "the ward did not work again in a new fight");
            // 参加していない人 (同じ部屋にいない) は守られない
            wear(b, 1, GuideSetup.HEARTBEAT_WARD_EMBER.get());
            b.setHealth(6f);
            LivingDamageEvent.Pre bystander = new LivingDamageEvent.Pre(b, new DamageContainer(level.damageSources().generic(), 20f));
            HeartbeatWard.onDamagePre(bystander);
            check(f, bystander.getNewDamage() == 20f, "a non-participant was saved by the ward");
            // 虚空のような傷は耐えない
            wear(a, 1, GuideSetup.HEARTBEAT_WARD_STONE_WHALE.get());
            a.setHealth(6f);
            heart.reset(level, heartHost, false);
            heart = new GroupBossRoom(Groups.keyOf(a));
            heart.start(level, heartHost, List.of(a));
            LivingDamageEvent.Pre voidHit = new LivingDamageEvent.Pre(a, new DamageContainer(level.damageSources().fellOutOfWorld(), 1000f));
            HeartbeatWard.onDamagePre(voidHit);
            check(f, voidHit.getNewDamage() == 1000f && !Accessories.stack(a, 1).isEmpty(), "a hit that bypasses invulnerability was cut");
            // 心臓以外の戦い (中ボスの広間など) では働かない
            heart.reset(level, heartHost, false);
            heart = null;
            other = new GroupBossRoom(Groups.keyOf(a));
            other.start(level, midHost, List.of(a));
            check(f, GroupBossRoom.heartFightOf(level, a.getUUID()) == null, "a mid-boss fight counted as a heart fight");
            LivingDamageEvent.Pre mid = new LivingDamageEvent.Pre(a, new DamageContainer(level.damageSources().generic(), 20f));
            HeartbeatWard.onDamagePre(mid);
            check(f, mid.getNewDamage() == 20f && !Accessories.stack(a, 1).isEmpty(), "the ward worked in a fight that is not a heart");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST heartbeat ward crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (heart != null) {
                heart.reset(level, heartHost, false);
            }
            if (other != null) {
                other.reset(level, midHost, false);
            }
            for (FakePlayer p : List.of(a, b)) {
                for (int i = 0; i < jp.revati.skyabyss.item.accessory.AccessoryData.SIZE; i++) {
                    Accessories.get(p).items.set(i, ItemStack.EMPTY);
                }
                p.getInventory().clearContent();
            }
        }
        report("heartbeat ward", f);
    }

    // ================================================================ 場所の湧き台・宝・章のひとり歩き
    static void siteCave(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = player(level, "site_a");
        FakePlayer b = player(level, "site_b");
        FakePlayer c = player(level, "site_c");
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos origin = new BlockPos(spawn.getX(), 0, spawn.getZ());
        origin = new BlockPos(origin.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ()), origin.getZ());
        BlockPos capOrigin = origin.offset(24, 0, 24);
        level.getChunk(origin);
        level.getChunk(capOrigin);
        capOrigin = new BlockPos(capOrigin.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, capOrigin.getX(), capOrigin.getZ()), capOrigin.getZ());
        final BlockPos at = origin;
        final BlockPos capAt = capOrigin;
        Runnable restore = null;
        SiteProgressData data = SiteProgressData.get(server);
        try {
            // 宝: 登録・一度きり・人ごと・装備は渡さない
            SiteTreasure.register(TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("minecraft:bread", 2),
                    new SiteTreasure.Entry("minecraft:skyabyss_no_such_item", 1)), 1, 1, List.of(new SiteTreasure.Entry("minecraft:iron_nugget", 3, 3, 1))));
            SiteTreasure.register(REPEAT, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("minecraft:flint", 2))));
            SiteTreasure.register("selftest_gear_treasure", SiteTreasure.Table.of(List.of(
                    new SiteTreasure.Entry("skyabyss:whalebone_greatsword", 1), new SiteTreasure.Entry("minecraft:stick", 1))));
            List<ItemStack> gearRoll = SiteTreasure.roll(SiteTreasure.table("selftest_gear_treasure"), level.random);
            check(f, gearRoll.size() == 1 && gearRoll.get(0).is(Items.STICK), "a site treasure handed out titan gear or dropped a plain item: " + gearRoll);
            SiteTreasure.unregister("selftest_gear_treasure");
            data.forgetTreasure(a.getUUID(), TREASURE);
            check(f, !SiteTreasure.claimed(a, TREASURE) && SiteTreasure.claim(a, "selftest_unknown").isEmpty(), "an unregistered treasure was claimed");
            List<ItemStack> got = SiteTreasure.claim(a, TREASURE);
            check(f, a.getInventory().countItem(Items.BREAD) == 2 && a.getInventory().countItem(Items.IRON_NUGGET) == 3 && got.size() == 2,
                    "the treasure was not rolled into the pack (a missing item must be skipped): " + got);
            check(f, SiteTreasure.claimed(a, TREASURE) && SiteTreasure.claim(a, TREASURE).isEmpty() && a.getInventory().countItem(Items.BREAD) == 2,
                    "the treasure was claimed twice by one person");
            check(f, !SiteTreasure.claimed(b, TREASURE), "the treasure record is not per person");
            data.forgetTreasure(a.getUUID(), TREASURE);
            a.getInventory().clearContent();

            // 場所: 前の間 (敵 2) と奥の間 (敵 1)。MossCrawler は GuardMob (GroupOwned)
            String mob = "skyabyss:moss_crawler";
            SiteSpawner.register(new SiteSpawner.Def(SITE, Chapters.WHALE, List.of(
                    new SiteSpawner.Pad("front", 0, 0, 0, List.of(new SiteSpawner.Spawn(mob, 2))),
                    new SiteSpawner.Pad("back", 4, 0, 0, List.of(new SiteSpawner.Spawn(mob, 1)))),
                    100, TREASURE, REPEAT, SiteSpawner.MAX_MOBS, server0 -> List.of(at)));
            SiteSpawner.register(new SiteSpawner.Def(CAP_SITE, Chapters.WHALE, List.of(
                    new SiteSpawner.Pad("only", 0, 0, 0, List.of(new SiteSpawner.Spawn(mob, 15)))),
                    0, null, null, SiteSpawner.MAX_MOBS, server0 -> List.of(capAt)));
            // クリアの合図を受ける仮のクエスト
            restore = QuestManager.addTemporary(List.of(quest("selftest/site_probe_a", "{\"category\":\"sub\",\"order\":99,\"stages\":[{\"id\":\"w\",\"objectives\":["
                    + "{\"id\":\"s\",\"type\":\"signal\",\"target\":\"" + SiteSpawner.signal(SITE) + "\",\"party\":false}]}]}", new ArrayList<>())));
            data.forget(a.getUUID(), SITE);
            data.forget(b.getUUID(), SITE);
            data.forget(c.getUUID(), SITE);
            Quests.start(a, "selftest/site_probe_a");
            long now = level.getGameTime();
            double ox = at.getX() + 0.5;
            double oz = at.getZ() + 0.5;
            a.setPos(ox, at.getY(), oz);
            b.setPos(ox + 10, at.getY(), oz);
            c.setPos(ox + 100, at.getY(), oz);
            UUID ga = Groups.keyOf(a);
            UUID gb = Groups.keyOf(b);

            // 人が 48 以内に来たときだけ、その人のグループの分が湧く
            SiteSpawner.tick(level, List.of(c), now);
            check(f, !SiteSpawner.hasInstance(SITE, at, Groups.keyOf(c)), "a player 100 blocks away woke the site");
            SiteSpawner.tick(level, List.of(a, c), now);
            check(f, SiteSpawner.liveCount(SITE, at, ga) == 3 && SiteSpawner.pendingCount(SITE, at, ga) == 0, "the first group's enemies did not spawn (" + SiteSpawner.liveCount(SITE, at, ga) + ")");
            check(f, !SiteSpawner.hasInstance(SITE, at, Groups.keyOf(c)) && !SiteSpawner.hasInstance(SITE, at, gb), "another group's enemies spawned without that group");
            List<Entity> mine = entities(level, SiteSpawner.liveIds(SITE, at, ga));
            check(f, mine.size() == 3 && mine.stream().allMatch(e -> e instanceof GroupOwned o && ga.equals(o.groupId()) && e.getTags().contains(SiteSpawner.TAG)),
                    "the enemies are not marked with the group and the site tag");
            check(f, mine.stream().allMatch(e -> e instanceof GuardMob g && !g.shouldBeSaved()), "a site enemy would be saved to disk");
            // 同時に二つのグループが入れる
            SiteSpawner.tick(level, List.of(a, b), now + 20);
            check(f, SiteSpawner.liveCount(SITE, at, ga) == 3 && SiteSpawner.liveCount(SITE, at, gb) == 3, "two groups could not use the same site at once");
            List<Entity> theirs = entities(level, SiteSpawner.liveIds(SITE, at, gb));
            Entity ma = mine.get(0);
            Entity mb = theirs.get(0);
            check(f, GroupOwned.visibleTo(ma, a) && !GroupOwned.visibleTo(ma, b) && GroupOwned.visibleTo(mb, b) && !GroupOwned.visibleTo(mb, a),
                    "a site enemy is visible to the wrong group");
            check(f, GroupOwned.mayTarget(ma, a) && !GroupOwned.mayTarget(ma, b), "a site enemy may target the wrong group");
            // 背中の外でも、別のグループの傷は取り消す (GroupEvents)
            LivingIncomingDamageEvent foreign = new LivingIncomingDamageEvent((net.minecraft.world.entity.LivingEntity) ma,
                    new DamageContainer(level.damageSources().playerAttack(b), 4f));
            NeoForge.EVENT_BUS.post(foreign);
            LivingIncomingDamageEvent own = new LivingIncomingDamageEvent((net.minecraft.world.entity.LivingEntity) ma,
                    new DamageContainer(level.damageSources().playerAttack(a), 4f));
            NeoForge.EVENT_BUS.post(own);
            check(f, foreign.isCanceled() && !own.isCanceled(), "damage across groups was not cancelled in the overworld (foreign=" + foreign.isCanceled() + " own=" + own.isCanceled() + ")");
            // 保存して戻ってきた個体 (今の実行で湧かせていない) は入れない
            Mob stray = ModEntities.MOSS_CRAWLER.get().create(level);
            stray.moveTo(ox, at.getY(), oz, 0f, 0f);
            stray.addTag(SiteSpawner.TAG);
            check(f, !level.addFreshEntity(stray), "a stale site enemy joined the world");

            // クリア: a のグループが全部倒す → 人ごとの記録・経験値・宝・合図。b のグループは別
            int xpBefore = Growth.get(a).xp;
            kill(level, SiteSpawner.liveIds(SITE, at, ga));
            SiteSpawner.tick(level, List.of(a), now + 40);
            check(f, data.cleared(a.getUUID(), SITE) && !data.cleared(b.getUUID(), SITE), "site_clear was not recorded for exactly the player who cleared it");
            check(f, !SiteSpawner.hasInstance(SITE, at, ga), "a cleared site kept its enemies");
            check(f, Growth.get(a).discoveries.contains("site:" + SITE), "the clear xp was not granted once");
            check(f, a.getInventory().countItem(Items.BREAD) == 2 && SiteTreasure.claimed(a, TREASURE), "the clear treasure was not claimed");
            check(f, QuestLog.of(a).entry("selftest/site_probe_a").done, "the clear signal was not sent");
            check(f, SiteSpawner.liveCount(SITE, at, gb) == 3, "clearing the site changed another group's enemies");
            // クリア直後は敵が戻らない (周回は 1 日あと)
            SiteSpawner.tick(level, List.of(a), now + 60);
            check(f, !SiteSpawner.hasInstance(SITE, at, ga), "the site refilled right after being cleared");
            // 1 日あとは最後の部屋だけ戻る。経験値なし・宝は周回の素材
            int xpAfter = Growth.get(a).xp;
            SiteSpawner.tick(level, List.of(a), now + SiteSpawner.REPEAT_TICKS + 100);
            check(f, SiteSpawner.liveCount(SITE, at, ga) == 1, "only the last room should come back after a day (" + SiteSpawner.liveCount(SITE, at, ga) + ")");
            kill(level, SiteSpawner.liveIds(SITE, at, ga));
            SiteSpawner.tick(level, List.of(a), now + SiteSpawner.REPEAT_TICKS + 120);
            check(f, a.getInventory().countItem(Items.FLINT) == 2 && a.getInventory().countItem(Items.BREAD) == 2, "the repeat did not give only the repeat materials");
            check(f, Growth.get(a).xp == xpAfter && xpAfter >= xpBefore, "a repeat clear gave xp again");
            SiteSpawner.tick(level, List.of(a), now + SiteSpawner.REPEAT_TICKS + 140);
            check(f, !SiteSpawner.hasInstance(SITE, at, ga), "the repeat came back before another day");
            // b のグループもクリアすれば、同じ宝を人ごとに受け取る
            kill(level, SiteSpawner.liveIds(SITE, at, gb));
            SiteSpawner.tick(level, List.of(b), now + 200);
            check(f, data.cleared(b.getUUID(), SITE) && b.getInventory().countItem(Items.BREAD) == 2, "the second group did not get its own treasure");
            // まだクリアしていない人が近くにいるグループは、全部の部屋 (初回)
            c.setPos(ox + 5, at.getY(), oz);
            SiteSpawner.tick(level, List.of(c), now + 300);
            check(f, SiteSpawner.liveCount(SITE, at, Groups.keyOf(c)) == 3, "a first-timer did not get every room");
            // 離れると敵を消す。戻れば湧き直す
            List<Entity> cm = entities(level, SiteSpawner.liveIds(SITE, at, Groups.keyOf(c)));
            c.setPos(ox + 400, at.getY(), oz);
            for (int i = 0; i < SiteSpawner.IDLE_DESPAWN / SiteSpawner.EVERY + 1; i++) {
                SiteSpawner.tick(level, List.of(c), now + 320 + i * 20L);
            }
            check(f, !SiteSpawner.hasInstance(SITE, at, Groups.keyOf(c)) && cm.stream().allMatch(e -> !e.isAlive()), "the enemies stayed after everyone left");
            c.setPos(ox + 5, at.getY(), oz);
            SiteSpawner.tick(level, List.of(c), now + 2000);
            check(f, SiteSpawner.liveCount(SITE, at, Groups.keyOf(c)) == 3, "the site did not respawn for a returning player");
            SiteSpawner.clearAll(level);

            // 上限: 15 体のうち同時に 12 体まで。倒れたぶんが順に湧く
            data.forget(a.getUUID(), CAP_SITE);
            a.setPos(capAt.getX() + 0.5, capAt.getY(), capAt.getZ() + 0.5);
            SiteSpawner.tick(level, List.of(a), now + 3000);
            check(f, SiteSpawner.liveCount(CAP_SITE, capAt, ga) == 12 && SiteSpawner.pendingCount(CAP_SITE, capAt, ga) == 3,
                    "the cap of 12 at once did not hold (" + SiteSpawner.liveCount(CAP_SITE, capAt, ga) + "/" + SiteSpawner.pendingCount(CAP_SITE, capAt, ga) + ")");
            List<UUID> ids = SiteSpawner.liveIds(CAP_SITE, capAt, ga);
            kill(level, ids.subList(0, 5));
            SiteSpawner.tick(level, List.of(a), now + 3020);
            check(f, SiteSpawner.liveCount(CAP_SITE, capAt, ga) == 10 && SiteSpawner.pendingCount(CAP_SITE, capAt, ga) == 0,
                    "pending enemies did not fill the freed places (" + SiteSpawner.liveCount(CAP_SITE, capAt, ga) + ")");
            SiteSpawner.clearAll(level);

            // 章のひとり歩き (ChapterMobSpawner)
            chapterWanderers(level, a, b, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST site cave crashed", e);
            f.add("crashed: " + e);
        } finally {
            SiteSpawner.clearAll(level);
            SiteSpawner.unregister(SITE);
            SiteSpawner.unregister(CAP_SITE);
            ChapterMobSpawner.clearAll(level);
            SiteTreasure.unregister(TREASURE);
            SiteTreasure.unregister(REPEAT);
            if (restore != null) {
                restore.run();
            }
            for (FakePlayer p : List.of(a, b, c)) {
                data.forget(p.getUUID(), SITE);
                data.forget(p.getUUID(), CAP_SITE);
                data.forgetTreasure(p.getUUID(), TREASURE);
                Quests.forgetAll(p);
                Growth.get(p).discoveries.clear();
                p.getInventory().clearContent();
            }
        }
        report("site cave", f);
    }

    private static List<Entity> entities(ServerLevel level, List<UUID> ids) {
        List<Entity> list = new ArrayList<>();
        for (UUID id : ids) {
            Entity e = level.getEntity(id);
            if (e != null) {
                list.add(e);
            }
        }
        return list;
    }

    private static void kill(ServerLevel level, List<UUID> ids) {
        for (Entity e : entities(level, ids)) {
            e.discard();
        }
    }

    private static void chapterWanderers(ServerLevel level, FakePlayer a, FakePlayer b, List<String> f) {
        BlockPos spawn = level.getSharedSpawnPos();
        a.setPos(spawn.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5);
        UUID ga = Groups.keyOf(a);
        try {
            ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("selftest_wanderer", Chapters.WHALE, "skyabyss:moss_crawler", 5, ChapterMobSpawner.Time.ANY));
            ChapterMobSpawner.chapterOverride = p -> Chapters.WHALE;
            ChapterMobSpawner.anchorOverride = (p, ch) -> new BlockPos((int) p.getX(), (int) p.getY(), (int) p.getZ());
            check(f, ChapterMobSpawner.pick(Chapters.WHALE, 3, 1000) != null && ChapterMobSpawner.pick(Chapters.TIDE, 3, 1000) == null,
                    "the wanderer was picked for the wrong chapter");
            check(f, ChapterMobSpawner.timeOk(ChapterMobSpawner.Time.NIGHT, 14000) && !ChapterMobSpawner.timeOk(ChapterMobSpawner.Time.NIGHT, 6000)
                    && ChapterMobSpawner.timeOk(ChapterMobSpawner.Time.DAY, 6000) && ChapterMobSpawner.timeOk(ChapterMobSpawner.Time.ANY, 14000),
                    "the time of day rule is wrong");
            // 別の章の人・拠点から遠い人には湧かない
            ChapterMobSpawner.chapterOverride = p -> Chapters.TIDE;
            for (int i = 0; i < 60; i++) {
                ChapterMobSpawner.tick(level, List.of(a), level.getGameTime() + i * 40L);
            }
            check(f, ChapterMobSpawner.liveIds().isEmpty(), "a wanderer spawned for a player in another chapter");
            ChapterMobSpawner.chapterOverride = p -> Chapters.WHALE;
            ChapterMobSpawner.anchorOverride = (p, ch) -> new BlockPos((int) p.getX() + 400, (int) p.getY(), (int) p.getZ());
            for (int i = 0; i < 60; i++) {
                ChapterMobSpawner.tick(level, List.of(a), level.getGameTime() + i * 40L);
            }
            check(f, ChapterMobSpawner.liveIds().isEmpty(), "a wanderer spawned more than 300 blocks from the chapter's base");
            // 章の地面にだけ: 薄く湧く (1 チャンクに 1 体、グループあたり 8 体まで)
            ChapterMobSpawner.anchorOverride = (p, ch) -> new BlockPos((int) p.getX(), (int) p.getY(), (int) p.getZ());
            for (int i = 0; i < 400 && ChapterMobSpawner.liveIds().size() < ChapterMobSpawner.PER_GROUP; i++) {
                ChapterMobSpawner.tick(level, List.of(a), level.getGameTime() + i * 40L);
            }
            List<UUID> live = new ArrayList<>(ChapterMobSpawner.liveIds());
            check(f, !live.isEmpty(), "no wanderer spawned near a player in the chapter's own ground");
            check(f, live.size() <= ChapterMobSpawner.PER_GROUP, "more wanderers than the per-group cap (" + live.size() + ")");
            java.util.Map<Long, Integer> perChunk = new java.util.HashMap<>();
            for (Entity e : entities(level, live)) {
                perChunk.merge(e.chunkPosition().toLong(), 1, Integer::sum);
                check(f, e instanceof GroupOwned o && ga.equals(o.groupId()) && e.getTags().contains(SiteSpawner.TAG), "a wanderer is not owned by the group that woke it");
                check(f, e.distanceToSqr(a) >= (ChapterMobSpawner.MIN_DIST - 2) * (ChapterMobSpawner.MIN_DIST - 2), "a wanderer spawned right on the player");
            }
            check(f, perChunk.values().stream().allMatch(n -> n <= ChapterMobSpawner.PER_CHUNK), "two wanderers shared a chunk");
            // 人が離れたら消える
            a.setPos(a.getX() + 600, a.getY(), a.getZ());
            ChapterMobSpawner.chapterOverride = p -> Chapters.TIDE;
            ChapterMobSpawner.tick(level, List.of(a), level.getGameTime() + 100000);
            check(f, ChapterMobSpawner.liveIds().isEmpty() && entities(level, live).stream().noneMatch(Entity::isAlive), "wanderers stayed after the group left");
        } finally {
            ChapterMobSpawner.unregister("selftest_wanderer");
            ChapterMobSpawner.chapterOverride = null;
            ChapterMobSpawner.anchorOverride = null;
            ChapterMobSpawner.clearAll(level);
        }
    }

    // ================================================================ 経験値の枠
    static void xpBudget(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        Runnable restore = null;
        try {
            // 表の整合 (21 の 1.5): 既存 + 新しい分の上限 = 合計の上限、合計は 1 レベル分の 1.6 倍まで
            for (int n = 1; n <= 4; n++) {
                check(f, ChapterXpBudget.EXISTING[n] + ChapterXpBudget.NEW_CAP[n] == ChapterXpBudget.TOTAL_CAP[n], "chapter " + n + ": the budget table does not add up");
                check(f, ChapterXpBudget.TOTAL_CAP[n] <= ChapterXpBudget.LEVEL_XP[n] * ChapterXpBudget.MAX_RATIO, "chapter " + n + ": the total is above the per-level ratio");
            }
            check(f, ChapterXpBudget.chapterOf("ch2/ebb_road") == 2 && ChapterXpBudget.chapterOf("main_song") == 0 && ChapterXpBudget.chapterOf("ch5/x") == 0,
                    "chapterOf is wrong");
            // 実際のデータは枠の中 (もう読み込まれているクエスト)
            check(f, ChapterXpBudget.overruns(QuestManager.all()).isEmpty(), "the shipped quests are over the xp budget: " + ChapterXpBudget.overruns(QuestManager.all()));
            check(f, QuestManager.loadErrors().stream().noneMatch(e -> e.contains("xp") || e.contains("prep quest")), "the quest load reported a budget / prep error: " + QuestManager.loadErrors());
            // 既存の仕分けが古くなっていないか: 既存の一覧のクエストは実在する
            for (String id : ChapterXpBudget.LEGACY) {
                check(f, QuestManager.quest(id) != null, "LEGACY lists a quest that does not exist: " + id);
            }
            // 新しい分の数え方: 一章の新しいクエスト (10 + 10 + 10。本物の支度の段と重ならない ID) を足すと数える。既存・繰り返し・ほかの章は数えない
            // (一章の本物の支度の段 250 と洞 40・骨の坑 40 が入っているので、足せるのは 70 まで)
            List<String> errors = new ArrayList<>();
            List<QuestDef> defs = new ArrayList<>();
            for (String probe : List.of("a", "b", "c")) {
                defs.add(quest("ch1/xp_probe_" + probe, prepJson("t_xp_" + probe, 10), errors));
            }
            int before = ChapterXpBudget.newQuestXp(QuestManager.all(), 1);
            int before2 = ChapterXpBudget.newQuestXp(QuestManager.all(), 2);
            restore = QuestManager.addTemporary(defs);
            int after = ChapterXpBudget.newQuestXp(QuestManager.all(), 1);
            check(f, after - before == 30 && ChapterXpBudget.newQuestXp(QuestManager.all(), 2) == before2,
                    "the new quests were not counted (" + before + " -> " + after + ") or leaked into another chapter");
            check(f, ChapterXpBudget.overruns(QuestManager.all()).isEmpty(), "30 more new xp in chapter 1 was flagged though the budget has room");
            restore.run();
            // 超えたら見つける (読み込みの検査と自己テストの両方)
            defs.add(quest("ch1/prep_extra", prepJson("t_extra", 700), errors));
            restore = QuestManager.addTemporary(defs);
            List<String> over = ChapterXpBudget.overruns(QuestManager.all());
            check(f, over.size() >= 1 && over.get(0).contains("chapter 1"), "an overrun was not found: " + over);
            List<String> loadErrors = new ArrayList<>();
            ChapterXpBudget.validate(QuestManager.all(), loadErrors);
            check(f, !loadErrors.isEmpty(), "the load-time check did not report an overrun");
            check(f, ChapterXpBudget.allowSite(Chapters.WHALE, 50) == 0, "a site in an over-budget chapter still gave xp");
            check(f, ChapterXpBudget.allowSite(Chapters.TIDE, 50) == 50, "an in-budget chapter's site lost its xp");
            restore.run();
            restore = null;
            check(f, ChapterXpBudget.allowSite(Chapters.WHALE, 50) == 50, "a site lost its xp after the budget was restored");
            // 場所の経験値も数える
            int site4 = ChapterXpBudget.siteXp(4);
            SiteSpawner.register(new SiteSpawner.Def("selftest_budget_site", Chapters.EMBER, List.of(), 1300, null, null, 12, s -> List.of()));
            check(f, ChapterXpBudget.siteXp(4) == site4 + 1300 && !ChapterXpBudget.overruns(QuestManager.all()).isEmpty(), "a site's xp was not counted against its chapter");
            SiteSpawner.unregister("selftest_budget_site");
            check(f, ChapterXpBudget.siteXp(4) == site4 && ChapterXpBudget.overruns(QuestManager.all()).isEmpty(), "the budget did not recover after the site was removed");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST xp budget crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (restore != null) {
                restore.run();
            }
            SiteSpawner.unregister("selftest_budget_site");
        }
        report("xp budget", f);
    }
}
