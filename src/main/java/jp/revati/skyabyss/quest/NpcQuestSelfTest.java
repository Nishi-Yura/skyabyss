package jp.revati.skyabyss.quest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.lore.LoreWrap;
import jp.revati.skyabyss.npc.Affinity;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.npc.DialogueService;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.npc.WorldLoop;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// NPC とクエストの自己テスト (SKYABYSS_SELFTEST=1 の専用サーバー、起動直後に一度)。
// JSON の読み込みと参照先、文の有無と長さ、会話の条件分岐、ループをまたぐ「覚えているような」入口、
// メインクエストを最初から最後まで、サブ 2 本、二人同時、倒した数の数え方、保存と死んだときの引き継ぎを確かめる。
// 結果はログに SELFTEST npc PASSED / FAILED (サーバーを止めるのは岩鯨の背の自己テスト)
public final class NpcQuestSelfTest {
    private static final String[] LANGS = {"ja_jp", "en_us"};
    // 会話画面の本文の幅 (枠 400 − 顔と余白 82) と、枠の高さの上限 (GUI 拡大率 4 の 1080p で画面の高さ 270)
    private static final int TEXT_W = 318;
    private static final int MAX_PANEL_H = 210;

    private NpcQuestSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int loopBefore = WorldLoop.count(server);
        try {
            run(server, failures, notes);
        } catch (RuntimeException e) {
            failures.add("exception: " + e);
            SkyAbyss.LOGGER.error("SELFTEST npc exception", e);
        } finally {
            WorldLoop.set(server, loopBefore);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST npc: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST npc PASSED ({} dialogues, {} quests, {} checks)", DialogueManager.all().size(),
                    QuestManager.all().size(), notes.size());
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST npc FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String what, List<String> failures) {
        if (!ok) {
            failures.add(what);
        }
    }

    private static void run(MinecraftServer server, List<String> failures, List<String> notes) {
        loading(failures, notes);
        texts(failures, notes);
        doneView(server, failures, notes);
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
        try {
            placement(level, new BlockPos(spawn.getX(), 260, spawn.getZ()), failures, notes);
            entity(st, failures, notes);
            dialogues(server, level, st, wc, at, failures, notes);
            jp.revati.skyabyss.npc.NpcMarkerSelfTest.run(player(level, 41, at), player(level, 42, at), st, wc, failures, notes);
            kills(level, failures, notes);
        } finally {
            st.discard();
            wc.discard();
        }
    }

    // ---------------------------------------------------------------- 読み込みと参照先
    private static void loading(List<String> failures, List<String> notes) {
        QuestManager.loadErrors().forEach(e -> failures.add("quest load: " + e));
        DialogueManager.loadErrors().forEach(e -> failures.add("dialogue load: " + e));
        check(QuestManager.quest("main_song") != null && QuestManager.quest("sub_song") != null && QuestManager.quest("sub_shards") != null,
                "expected quests main_song, sub_song, sub_shards", failures);
        check(DialogueManager.dialogue(NpcSetup.STORYTELLER_ID) != null && DialogueManager.dialogue(NpcSetup.WHALE_CHASER_ID) != null,
                "expected dialogues for both npcs", failures);
        // メインは「雲の下の歌」から一本の鎖 (1 章の続きは前のメインを終えると始まる。docs/design/14_beta.md)
        check(QuestManager.all().stream().filter(QuestDef::main).min(java.util.Comparator.comparingInt(QuestDef::order))
                .map(q -> q.id().equals("main_song")).orElse(false), "main_song should be the first main quest", failures);
        // 条件と結果の指す先
        List<Condition> conditions = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        for (Dialogue d : DialogueManager.all().values()) {
            // 係の人 (attendant_<役>) と町のクエストの限定の人 (guest_<人>) は、エンティティの型一つに役を持つ
            boolean known = BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(d.role()))
                    || jp.revati.skyabyss.facility.AttendantRole.byDialogueRole(d.role()) != null
                    // 深淵の生き残りなど、施設の役でない係の人の形 (AttendantEntity.setRoleId。story/ch5/layers/AbyssSurvivors)
                    || (d.role().startsWith(jp.revati.skyabyss.facility.AttendantRole.DIALOGUE_PREFIX)
                    && jp.revati.skyabyss.facility.AttendantEntity.extraRole(d.role().substring(jp.revati.skyabyss.facility.AttendantRole.DIALOGUE_PREFIX.length())))
                    || (d.role().startsWith(jp.revati.skyabyss.town.TownGuests.ROLE_PREFIX)
                    && (jp.revati.skyabyss.town.TownGuests.byId(d.role().substring(jp.revati.skyabyss.town.TownGuests.ROLE_PREFIX.length())) != null
                    // 土地の町の人 (town/RegionFolk。都の町の人の絵を借りる)
                    || jp.revati.skyabyss.town.RegionFolk.byId(d.role().substring(jp.revati.skyabyss.town.TownGuests.ROLE_PREFIX.length())) != null));
            check(known, "dialogue for unknown entity " + d.role(), failures);
            d.entries().forEach(e -> conditions.add(e.condition()));
            for (Dialogue.Node n : d.nodes().values()) {
                actions.addAll(n.actions());
                for (Dialogue.Option o : n.options()) {
                    conditions.add(o.condition());
                    actions.addAll(o.actions());
                }
            }
            // どの節にも入口から届く (取り残された節がない)
            List<String> reach = new ArrayList<>();
            d.entries().forEach(e -> walk(d, e.node(), reach));
            for (String id : d.nodes().keySet()) {
                check(reach.contains(id), d.role() + ": node " + id + " is unreachable", failures);
            }
        }
        for (QuestDef q : QuestManager.all()) {
            if (q.startWhen() != null) {
                conditions.add(q.startWhen());
            }
            check(q.giver() == null || DialogueManager.dialogue(q.giver()) != null, q.id() + ": giver has no dialogue", failures);
            for (QuestDef.Stage s : q.stages()) {
                if (s.skipIf() != null) {
                    conditions.add(s.skipIf());
                }
                actions.addAll(s.actions());
                for (QuestDef.Objective o : s.objectives()) {
                    objective(q, s, o, failures);
                }
            }
            // クエストを始める道がある (自動で始まるか、どこかの会話が始める)
            // 序章・一章の小要素 (story/ch1) の頼みは、章の小要素に着いたときにコードが始める (ChapterSites の onArrive)
            // 後の章の技の稽古 (lesson/tree_*) は、木・要が開いたときにコードが始める (tutorial.SkillLesson の poll)
            boolean started = q.startWhen() != null || q.board() || q.id().startsWith("ch1/") || q.id().startsWith("lesson/tree_") || rumorStarted(q.id()) || DialogueManager.all().values().stream().flatMap(d -> d.nodes().values().stream())
                    .anyMatch(n -> n.actions().stream().anyMatch(a -> a.type().equals("quest_start") && a.str("quest").equals(q.id()))
                            || n.options().stream().anyMatch(o -> o.actions().stream()
                            .anyMatch(a -> a.type().equals("quest_start") && a.str("quest").equals(q.id()))));
            check(started, q.id() + ": nothing starts this quest", failures);
        }
        for (Condition root : conditions) {
            root.visit(c -> {
                switch (c.type()) {
                    case "quest" -> {
                        QuestDef def = QuestManager.quest(c.str("quest"));
                        check(def != null, "condition points to unknown quest " + c.str("quest"), failures);
                        if (def != null && c.optStr("stage") != null) {
                            check(def.stage(c.str("stage")) != null, "condition points to unknown stage " + c.str("stage"), failures);
                        }
                    }
                    case "lore" -> check(LoreManager.catalog().entry(c.str("entry")) != null, "condition points to unknown lore " + c.str("entry"), failures);
                    default -> {
                    }
                }
            });
        }
        for (Action a : actions) {
            switch (a.type()) {
                case "quest_start" -> check(QuestManager.quest(a.str("quest")) != null, "action starts unknown quest " + a.str("quest"), failures);
                case "lore" -> check(LoreManager.catalog().entry(a.str("entry")) != null && LoreManager.catalog().entry(a.str("entry")).unlockable(),
                        "action unlocks unknown lore " + a.str("entry"), failures);
                default -> {
                }
            }
        }
        notes.add("loaded " + QuestManager.all().size() + " quests, " + DialogueManager.all().size() + " dialogues, "
                + conditions.size() + " conditions, " + actions.size() + " actions, all references resolve");
    }

    private static void walk(Dialogue d, String id, List<String> reach) {
        if (id.equals(Dialogue.END) || reach.contains(id)) {
            return;
        }
        if (id.equals(Dialogue.ENTRY)) {
            d.entries().forEach(e -> walk(d, e.node(), reach));
            return;
        }
        Dialogue.Node n = d.nodes().get(id);
        if (n == null) {
            return;
        }
        reach.add(id);
        if (n.next() != null) {
            walk(d, n.next(), reach);
        }
        n.options().forEach(o -> walk(d, o.target(), reach));
    }

    private static void objective(QuestDef q, QuestDef.Stage s, QuestDef.Objective o, List<String> failures) {
        String w = q.id() + "/" + s.id() + "/" + o.id();
        switch (o.type()) {
            case "talk" -> {
                for (String role : o.targets()) {
                    Dialogue d = DialogueManager.dialogue(role);
                    check(d != null, w + ": talk target has no dialogue " + role, failures);
                    if (d != null && o.topic() != null) {
                        check(d.nodes().containsKey(o.topic()), w + ": talk topic " + o.topic() + " is not a node of " + role, failures);
                    }
                }
            }
            case "lore" -> o.targets().forEach(t -> check(LoreManager.catalog().entry(t) != null, w + ": unknown lore " + t, failures));
            case "collect" -> check(Condition.item(o.targets().get(0)) != null || ((o.targets().get(0).startsWith("#") || o.targets().get(0).contains("@")) && QuestItems.known(o.targets().get(0))),
                    w + ": unknown item", failures);
            // 雷翼の敵と心臓は別の担当が登録する (StormLore.PENDING)。まだないうちは数えるだけで、失敗にしない
            case "kill", "near" -> o.targets().forEach(t -> check(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(t))
                    || jp.revati.skyabyss.titan.storm.lore.StormLore.pending(t), w + ": unknown entity " + t, failures));
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 文
    // 終えたクエストの頁 (クエストログ。QuestDoneView): やったこと・書き付け・日・新しい UI の文。押すと中身が出る (以前は何も出なかった)
    private static void doneView(MinecraftServer server, List<String> failures, List<String> notes) {
        Map<String, String> ja;
        Map<String, String> en;
        try {
            ja = jp.revati.skyabyss.LangSelfTest.table("ja_jp");
            en = jp.revati.skyabyss.LangSelfTest.table("en_us");
        } catch (java.io.IOException e) {
            failures.add("done view: lang tables could not be read: " + e);
            return;
        }
        for (String k : List.of("gui.skyabyss.quest.done_steps", "gui.skyabyss.quest.reward_got", "gui.skyabyss.quest.days", "gui.skyabyss.quest.day_done",
                "gui.skyabyss.quest.finished", "gui.skyabyss.quest.or")) {
            check(ja.containsKey(k) && en.containsKey(k), "done view: missing ja/en text " + k, failures);
        }
        int quests = 0;
        for (QuestDef def : QuestManager.all()) {
            List<QuestDoneView.Line> lines = QuestDoneView.lines(def);
            int objectives = def.stages().stream().mapToInt(st -> st.objectives().size()).sum();
            check(lines.size() == objectives, "done view: " + def.id() + " lists " + lines.size() + " of " + objectives + " objectives", failures);
            check(objectives == 0 || !lines.isEmpty(), "done view: " + def.id() + " shows nothing when finished", failures);
            for (QuestDoneView.Line l : lines) {
                check(ja.containsKey(l.key()) && en.containsKey(l.key()), "done view: " + def.id() + " objective text missing " + l.key(), failures);
            }
            String journal = QuestDoneView.journalKey(def, k -> ja.containsKey(k) && en.containsKey(k));
            check(ja.containsKey(journal) && en.containsKey(journal), "done view: " + def.id() + " has no closing text (" + journal + ")", failures);
            quests++;
        }
        // どれか一つの段: 二つ目から「または」
        QuestDef song = QuestManager.quest("main_song");
        if (song != null) {
            List<QuestDoneView.Line> lines = QuestDoneView.lines(song);
            check(lines.size() >= 2 && lines.get(lines.size() - 1).alternative() && !lines.get(lines.size() - 2).alternative(),
                    "done view: the last stage of main_song (either person) should read 'A / or B'", failures);
        }
        // 日 (1 日目から)
        QuestLog.Entry e = new QuestLog.Entry(0, 30000L);
        check(QuestDoneView.days(e) == null, "done view: an unfinished quest should have no days", failures);
        e.done = true;
        e.finished = 3 * 24000L + 10;
        long[] d = QuestDoneView.days(e);
        check(d != null && d[0] == 2 && d[1] == 4, "done view: days should be taken day 2 / finished day 4", failures);
        // 本当に終えると、終えた時刻が残り、頁に日が出る
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.nameUUIDFromBytes("skyabyss_done_view".getBytes(StandardCharsets.UTF_8)), "skyabyss_done_view"));
        String id = "sub_parcel";
        try {
            Quests.forgetAll(p);
            if (QuestManager.quest(id) != null && Quests.start(p, id)) {
                for (int i = 0; i < 8 && QuestLog.of(p).entry(id) != null && !QuestLog.of(p).entry(id).done; i++) {
                    Quests.forceStage(p, id);
                }
                QuestLog.Entry real = QuestLog.of(p).entry(id);
                check(real != null && real.done, "done view: " + id + " did not finish", failures);
                long[] rd = real == null ? null : QuestDoneView.days(real);
                check(rd != null && rd[1] == server.overworld().getGameTime() / 24000L + 1, "done view: a finished quest should record the day it was finished", failures);
            } else {
                failures.add("done view: " + id + " could not be started");
            }
        } finally {
            Quests.forgetAll(p);
            Growth.get(p).discoveries.clear();
            p.getInventory().clearContent();
        }
        notes.add("done view: " + quests + " quests list their objectives and closing text");
    }

    private static void texts(List<String> failures, List<String> notes) {
        List<String> keys = new ArrayList<>(List.of("entity.skyabyss.storyteller", "entity.skyabyss.whale_chaser",
                "message.skyabyss.storyteller.struck", "message.skyabyss.whale_chaser.struck", "key.skyabyss.quest_log",
                "gui.skyabyss.npc.leave", "message.skyabyss.npc.handed", "growth.skyabyss.reason.npc", "gui.skyabyss.quest.log",
                "gui.skyabyss.quest.empty", "gui.skyabyss.quest.or", "gui.skyabyss.quest.track", "gui.skyabyss.quest.untrack",
                "gui.skyabyss.quest.toast.started", "gui.skyabyss.quest.toast.updated", "gui.skyabyss.quest.toast.finished",
                "message.skyabyss.quest.started", "message.skyabyss.quest.finished", "message.skyabyss.quest.open_hint"));
        for (int t = 0; t <= 4; t++) {
            keys.add("gui.skyabyss.npc.tier." + t);
        }
        Map<String, Integer> optionCount = new LinkedHashMap<>();
        for (Dialogue d : DialogueManager.all().values()) {
            for (Dialogue.Node n : d.nodes().values()) {
                keys.add(n.textKey());
                n.options().forEach(o -> keys.add(o.textKey()));
                optionCount.put(n.textKey(), Math.max(1, n.options().size()));
            }
        }
        for (QuestDef q : QuestManager.all()) {
            keys.add(q.titleKey());
            keys.add(q.summaryKey());
            keys.add(q.doneKey());
            for (QuestDef.Stage s : q.stages()) {
                keys.add(q.journalKey(s));
                s.objectives().forEach(o -> keys.add(q.objectiveKey(s, o)));
            }
        }
        int longest = 0;
        String longestKey = "";
        for (String code : LANGS) {
            JsonObject lang = json("/assets/skyabyss_npc/lang/" + code + ".json", failures);
            if (lang == null) {
                continue;
            }
            // 自由なモードのサブクエストの文は skyabyss_quest に置いた (skyabyss_npc は別の担当が直すため)
            // 1 章の続き (メイン 4 本・サブ 4 本) の文は skyabyss_story に置いた
            // 雷翼の背の周回のクエスト (*storm*) と風見鶏の文は skyabyss_storm にある (12 の 11.2 節、伝承の担当)
            // 語り部の墓の救いの文は skyabyss_grave にある (11 の 6 章)
            // 深淵の層の生き残りの会話の文は skyabyss_abyss_v2 (データを直接直す（生成スクリプトはない）)
            // 章ごとのクエスト・追加の会話の文は skyabyss_ch1〜ch4 にある (三章の職人の覚書・鼓動守の会話は skyabyss_ch3gear)
            for (String ns : List.of("skyabyss_quest", "skyabyss_story", "skyabyss_storm", "skyabyss_grave", "skyabyss_ch1", "skyabyss_ch2", "skyabyss_ch3", "skyabyss_ch3gear", "skyabyss_ch4", "skyabyss_ch5", "skyabyss_abyss_v2", "skyabyss_vow", "skyabyss_fac", "skyabyss_town", "skyabyss_lesson")) {
                JsonObject extra = json("/assets/" + ns + "/lang/" + code + ".json", failures);
                if (extra != null) {
                    extra.entrySet().forEach(e -> {
                        if (!lang.has(e.getKey())) {
                            lang.add(e.getKey(), e.getValue());
                        }
                    });
                }
            }
            for (String key : keys) {
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    failures.add(code + ": missing text " + key);
                }
            }
            // 会話の枠の高さ: 本文の行と、選択肢 (全部出たとき) が画面に収まる
            for (Map.Entry<String, Integer> e : optionCount.entrySet()) {
                if (!lang.has(e.getKey())) {
                    continue;
                }
                List<String> lines = LoreWrap.wrap(lang.get(e.getKey()).getAsString(), TEXT_W, NpcQuestSelfTest::width);
                int h = Math.max(94, 10 + lines.size() * 11 + 8 + e.getValue() * 13 + 10);
                if (h > longest) {
                    longest = h;
                    longestKey = code + " " + e.getKey();
                }
                if (h > MAX_PANEL_H) {
                    failures.add(code + ": dialogue panel too tall (" + h + ") for " + e.getKey());
                }
                for (String l : lines) {
                    check(width(l) <= TEXT_W, code + ": wrapped line too wide in " + e.getKey(), failures);
                    check(l.isEmpty() || "、。」）".indexOf(l.charAt(0)) < 0, code + ": line starts with closing punctuation in " + e.getKey(), failures);
                }
            }
        }
        notes.add(keys.size() + " text keys in ja_jp and en_us; tallest dialogue panel " + longest + " (" + longestKey + ")");
    }

    // 字幅のおおよそ (全角 9、半角 6。伝承の書の見本と同じ)
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) < 0x2000 ? 6 : 9;
        }
        return w;
    }

    private static JsonObject json(String path, List<String> failures) {
        try (InputStream in = NpcQuestSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                failures.add("missing " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            failures.add(path + ": " + e);
            return null;
        }
    }

    // ---------------------------------------------------------------- 立たせる場所
    // 空中に草の足場 (半径 40) を作り、真ん中を鐘とみなして、語り部は鐘のそば、鯨追いは外れの火のそばに立つか
    private static void placement(ServerLevel level, BlockPos bell, List<String> failures, List<String> notes) {
        int r = 40;
        List<BlockPos> placed = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos p = bell.offset(dx, -1, dz);
                level.setBlock(p, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                placed.add(p);
            }
        }
        List<NpcEntity> spawned = new ArrayList<>();
        try {
            NpcEntity st = jp.revati.skyabyss.npc.NpcSpawner.spawnStoryteller(level, bell);
            check(st != null, "the storyteller could not be placed", failures);
            if (st != null) {
                spawned.add(st);
                double d = Math.sqrt(st.blockPosition().distSqr(bell));
                check(d <= 8 && st.getY() == bell.getY(), "the storyteller stands too far from the bell (" + d + ")", failures);
                check(bell.equals(st.home()) && st.hasCustomName(), "the storyteller has no home or name", failures);
            }
            NpcEntity wc = jp.revati.skyabyss.npc.NpcSpawner.spawnChaser(level, bell, net.minecraft.util.RandomSource.create(7));
            check(wc != null, "the whale chaser could not be placed", failures);
            if (wc != null) {
                spawned.add(wc);
                double d = Math.sqrt(wc.blockPosition().distSqr(bell));
                BlockPos fire = wc.home();
                check(d >= 16 && d <= 36, "the whale chaser should camp 18-34 blocks from the bell (" + d + ")", failures);
                check(fire != null && level.getBlockState(fire).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                        && fire.distSqr(wc.blockPosition()) <= 4, "the whale chaser has no campfire beside him", failures);
                if (fire != null) {
                    placed.add(fire);
                }
            }
        } finally {
            spawned.forEach(NpcEntity::discard);
            placed.forEach(p -> level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2));
        }
        notes.add("placement: storyteller beside the bell, whale chaser at the village edge with a campfire");
    }

    // ---------------------------------------------------------------- エンティティ
    private static void entity(NpcEntity st, List<String> failures, List<String> notes) {
        check(st.role().equals(NpcSetup.STORYTELLER_ID), "storyteller role id", failures);
        float before = st.getHealth();
        st.hurt(st.damageSources().generic(), 50f);
        check(st.isAlive() && st.getHealth() == before, "an npc was hurt by ordinary damage", failures);
        check(!st.removeWhenFarAway(1000) && st.isPersistenceRequired(), "an npc would despawn", failures);
        CompoundTag tag = new CompoundTag();
        st.setHome(new BlockPos(10, 64, 10));
        st.addAdditionalSaveData(tag);
        NpcEntity copy = NpcSetup.STORYTELLER.get().create(st.level());
        if (copy != null) {
            copy.readAdditionalSaveData(tag);
            check(new BlockPos(10, 64, 10).equals(copy.home()), "npc home is not saved", failures);
            copy.discard();
        }
        notes.add("npc: invulnerable to ordinary damage, persistent, home saved");
    }

    // ---------------------------------------------------------------- 会話とクエスト
    private static FakePlayer player(ServerLevel level, int n, BlockPos at) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a1e0b3c-0000-4000-8000-0000000000" + String.format("%02d", n)),
                "[npc_test_" + n + "]"));
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

    // 今の節の、見えている選択肢の中から id のものを選ぶ。見えていなければ失敗にして false
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

    private static void next(FakePlayer p) {
        DialogueService.choose(p, node(p), DialogueService.CONTINUE);
    }

    private static String stage(FakePlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || def == null) {
            return "none";
        }
        return e.done ? "done" : def.stages().get(e.stage).id();
    }

    private static long totalXp(FakePlayer p) {
        var g = Growth.get(p);
        long sum = g.xp;
        for (int l = 1; l < g.level; l++) {
            sum += jp.revati.skyabyss.rpg.GrowthRules.xpToNext(l);
        }
        return sum;
    }

    private static void dialogues(MinecraftServer server, ServerLevel level, NpcEntity st, NpcEntity wc, BlockPos at,
                                  List<String> failures, List<String> notes) {
        String ST = NpcSetup.STORYTELLER_ID;
        String WC = NpcSetup.WHALE_CHASER_ID;
        WorldLoop.set(server, 0);
        FakePlayer a = player(level, 1, at);
        FakePlayer b = player(level, 2, at);
        Growth.get(a).discoveries.clear();
        long xp0 = totalXp(a);

        // 初めて: first → menu。空の影をまだ見ていない人には「空のことを」、鯨追いの話題は出ない
        DialogueService.open(a, st);
        check(node(a).equals("first"), "first talk should open 'first', got " + node(a), failures);
        next(a);
        check(node(a).equals("menu"), "first → menu, got " + node(a), failures);
        check(shown(a, ST, "shadow_ask") && !shown(a, ST, "shadow_seen") && !shown(a, ST, "chaser_where") && shown(a, ST, "song"),
                "menu options for a newcomer are wrong", failures);
        // 古い選択 (違う節の名前) は捨てる
        DialogueService.choose(a, "song1", 0);
        check(node(a).equals("menu"), "a stale choice moved the dialogue", failures);
        // 数え歌: 伝承が記され、印が立ち、好感度が上がる。「子どもの歌だろう」で少し下がる
        pick(a, ST, "song", failures);
        check(node(a).equals("song1") && Lore.state(a).has("prologue/counting_song") && NpcBonds.of(a).bond(ST).flags().contains("song_heard"),
                "the song did not record lore and flag", failures);
        next(a);
        int affBefore = Affinity.get(a, ST);
        pick(a, ST, "child", failures);
        check(Affinity.get(a, ST) == affBefore - 2, "affinity did not drop by 2 (" + affBefore + " → " + Affinity.get(a, ST) + ")", failures);
        next(a);
        check(node(a).equals("menu"), "@entry should return to the menu, got " + node(a), failures);
        // 空の影 → メインが始まり、shadow3 に入ると 1 段目が済む
        pick(a, ST, "shadow_ask", failures);
        check(stage(a, "main_song").equals("ask_storyteller"), "main quest did not start at ask_storyteller: " + stage(a, "main_song"), failures);
        check(QuestLog.of(a).tracked().equals("main_song"), "main quest is not tracked", failures);
        next(a);
        next(a);
        check(node(a).equals("shadow3"), "expected shadow3, got " + node(a), failures);
        check(stage(a, "main_song").equals("find_chaser"), "talking to the storyteller did not advance to find_chaser: " + stage(a, "main_song"), failures);
        check(totalXp(a) - xp0 >= 30, "stage xp was not given", failures);
        pick(a, ST, "go", failures);
        check(node(a).equals("(closed)"), "'go' should end the conversation", failures);
        // 二度目: menu から。鯨追いの居場所の話題が出る
        DialogueService.open(a, st);
        check(node(a).equals("menu") && shown(a, ST, "chaser_where") && !shown(a, ST, "shadow_ask"), "second talk menu is wrong (" + node(a) + ")", failures);
        DialogueService.close(a);

        // 空の影を見た人には「雲の上を何かが渡った」。二人の会話は別々に進む
        Lore.unlock(b, "prologue/shadow", false);
        Quests.poll(b, true);
        check(stage(b, "main_song").equals("ask_storyteller"), "seeing the shadow did not start the main quest", failures);
        // 初めて話すなら shadow_first (序盤の語り、OpeningStorySelfTest)。ここは二度目の訪れ (話題を選ぶ節) を見る
        NpcBonds.of(b).bond(ST).flags().add("met");
        DialogueService.open(b, st);
        DialogueService.open(a, st);
        check(node(b).equals("menu") && shown(b, ST, "shadow_seen") && !shown(b, ST, "shadow_ask"), "menu for a shadow witness is wrong", failures);
        pick(b, ST, "shadow_seen", failures);
        check(node(a).equals("menu") && node(b).equals("shadow1"), "two players' conversations interfered (" + node(a) + ", " + node(b) + ")", failures);
        check(stage(a, "main_song").equals("find_chaser"), "player b's choice changed player a's quest", failures);
        DialogueService.close(a);
        DialogueService.close(b);
        // 同じ語り部に二人が同時に話しかけ、片方が閉じても、もう片方は続く。それぞれの記録で話題が決まる
        DialogueService.open(a, st);
        DialogueService.open(b, st);
        check(node(a) != null && node(b) != null && !node(a).equals("(closed)") && !node(b).equals("(closed)"), "simultaneous open failed", failures);
        check(shown(a, ST, "chaser_where") && !shown(a, ST, "shadow_ask") && shown(b, ST, "shadow_seen"),
                "each simultaneous talker should see own topics", failures);
        DialogueService.close(a);
        check(node(b).equals("menu") && !node(b).equals("(closed)"), "closing one talker ended the other's conversation", failures);
        DialogueService.close(b);

        // 離れたら選べない
        DialogueService.open(b, st);
        b.moveTo(at.getX() + 30, at.getY(), at.getZ());
        DialogueService.choose(b, node(b), DialogueService.CONTINUE);
        check(node(b).equals("(closed)"), "a far-away player could still continue the conversation", failures);
        b.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5);

        // 鯨追い: 語り部から聞いた話題で 2 段目が済む
        DialogueService.open(a, wc);
        check(node(a).equals("c_first"), "first talk with the chaser should open c_first, got " + node(a), failures);
        next(a);
        check(shown(a, WC, "whale_q") && !shown(a, WC, "whale"), "chaser menu should offer the quest topic", failures);
        pick(a, WC, "whale_q", failures);
        next(a);
        // 鯨の話のあとは一章の支度 (三本そろうまで岩鯨は来ない)
        check(stage(a, "main_song").equals("prepare"), "talking to the chaser did not advance to prepare: " + stage(a, "main_song"), failures);
        next(a);
        // 鉄を着ていた旅人の一言 (一章の支度の段の理由)
        check(node(a).equals("c_whale_iron"), "the chaser should mention the traveller who wore iron, got " + node(a), failures);
        next(a);
        pick(a, WC, "grab", failures);
        // 鯨の話のあと、一章の支度の段 3 本が出る。語り部の入口に割り込む一言 (職人を指す・足音の晩の話) は、ここで済ませておく
        Quests.poll(a, true);
        // 支度の段は一本ずつ順に (鉄の跡 → 折れた剣 → 盾と糧)。鯨の話のあとに出るのは鉄の跡だけ
        check(QuestLog.of(a).entry("ch1/trail_iron") != null && QuestLog.of(a).entry("ch1/trail_blade") == null && QuestLog.of(a).entry("ch1/trail_shield") == null,
                "only the iron trail should start after the chaser's whale talk", failures);
        NpcBonds.of(a).bond(ST).flags().add("ch1p_pointer_told");
        Quests.onTalk(a, ST, "ch1t_shield_tale");

        // 伝承の記録で段階が進む (見る → 乗る → 碑文 3 つ → 心臓と欠片 → 戻る)
        Lore.unlock(a, "chapter1/stone_whale", false);
        Quests.poll(a, true);
        check(stage(a, "main_song").equals("board"), "seeing the whale did not advance to board: " + stage(a, "main_song"), failures);
        Lore.unlock(a, "chapter1/sky_ruins", false);
        Quests.poll(a, true);
        Lore.unlock(a, "chapter1/inscription_west", false);
        Lore.unlock(a, "chapter1/inscription_east", false);
        Quests.poll(a, true);
        QuestLog.Entry me = QuestLog.of(a).entry("main_song");
        check(stage(a, "main_song").equals("inscriptions") && me != null && me.progress(0) == 2, "inscriptions should be at 2/3", failures);
        Lore.unlock(a, "chapter1/inscription_below", false);
        Quests.poll(a, true);
        check(stage(a, "main_song").equals("heart"), "three inscriptions did not advance to heart: " + stage(a, "main_song"), failures);
        Lore.unlock(a, "chapter1/heart_stilled", false);
        Lore.unlock(a, "chapter1/sky_key", false);
        Quests.poll(a, true);
        check(stage(a, "main_song").equals("return"), "heart and fragment did not advance to return: " + stage(a, "main_song"), failures);
        // 鯨追いの側でも、メインの最後を終えられる (どれか一つ)
        long xpBeforeEnd = totalXp(a);
        DialogueService.open(a, wc);
        check(node(a).equals("c_fragment"), "the chaser should greet the fragment, got " + node(a), failures);
        next(a);
        check(stage(a, "main_song").equals("done"), "the main quest did not finish at the chaser: " + stage(a, "main_song"), failures);
        long gained = totalXp(a) - xpBeforeEnd;
        check(gained >= 200, "the main quest reward xp was not given (" + gained + ")", failures);
        check(Affinity.get(a, WC) >= 5, "the main quest affinity reward was not given", failures);
        pick(a, WC, "bye", failures);
        // やり直しても報酬は二度出ない
        Quests.forget(a, "main_song");
        Quests.start(a, "main_song");
        long xpAgain = totalXp(a);
        Quests.poll(a, true);
        check(stage(a, "main_song").equals("return"), "a restarted main quest should fast-forward through recorded lore: " + stage(a, "main_song"), failures);
        Quests.forceStage(a, "main_song");
        check(stage(a, "main_song").equals("done") && totalXp(a) == xpAgain, "a restarted quest paid its rewards twice", failures);
        notes.add("main quest played from the shadow to the fragment (storyteller → chaser → lore stages → return), rewards once");

        // 先に鯨を見つけていた人: 語り部と鯨追いの段階は飛ばす
        FakePlayer c = player(level, 3, at);
        Lore.unlock(c, "chapter1/stone_whale", false);
        Quests.poll(c, true);
        check(stage(c, "main_song").equals("board"), "a player who found the whale first should skip to board: " + stage(c, "main_song"), failures);

        // レイドの順路だけを通った人 (碑文を一つも記さずに心臓を止めた): 段 inscriptions は飛び、一章が済み、二章が始まる (fun_review P1-1)
        FakePlayer r = player(level, 12, at);
        jp.revati.skyabyss.progress.ProgressData pd = jp.revati.skyabyss.progress.ProgressData.get(level.getServer());
        try {
            Lore.unlock(r, "chapter1/stone_whale", false);
            Lore.unlock(r, "chapter1/sky_ruins", false);
            Quests.poll(r, true);
            check(stage(r, "main_song").equals("inscriptions"), "raid route: should wait at inscriptions before the heart: " + stage(r, "main_song"), failures);
            pd.markPersonal(r.getUUID(), "stone_whale");
            Lore.unlock(r, "chapter1/heart_stilled", false);
            Lore.unlock(r, "chapter1/sky_key", false);
            Quests.poll(r, true);
            check(stage(r, "main_song").equals("return") && !Lore.state(r).has("chapter1/inscription_west"),
                    "raid route: stilling the whale without inscriptions did not skip to return: " + stage(r, "main_song"), failures);
            DialogueService.open(r, wc);
            next(r);
            check(stage(r, "main_song").equals("done"), "raid route: chapter 1 did not finish: " + stage(r, "main_song"), failures);
            DialogueService.close(r);
            Quests.poll(r, true);
            check(QuestLog.of(r).entry("main_tide") != null, "raid route: chapter 2 (main_tide) did not start after chapter 1", failures);
        } finally {
            pd.clearPersonal(r.getUUID(), "stone_whale");
        }

        // サブ「鯨のかけら」: 背中に乗った人に頼む → 3 つ集める → 渡す → 遠眼鏡
        DialogueService.open(a, wc);
        check(shown(a, WC, "help"), "the chaser should ask for help after the back was visited", failures);
        pick(a, WC, "help", failures);
        pick(a, WC, "accept", failures);
        check(stage(a, "sub_shards").equals("gather"), "sub_shards did not start", failures);
        a.getInventory().add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 2));
        Quests.poll(a, true);
        check(stage(a, "sub_shards").equals("gather") && QuestLog.of(a).entry("sub_shards").progress(0) == 2, "two shards should be 2/3", failures);
        a.getInventory().add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 2));
        Quests.poll(a, true);
        check(stage(a, "sub_shards").equals("deliver"), "four shards did not advance to deliver", failures);
        DialogueService.open(a, wc);
        check(shown(a, WC, "shards"), "the give option should appear with 3+ shards", failures);
        pick(a, WC, "shards", failures);
        check(a.getInventory().countItem(ModItems.STONE_WHALE_SHARD.get()) == 1, "three shards were not taken", failures);
        check(stage(a, "sub_shards").equals("done") && a.getInventory().countItem(jp.revati.skyabyss.registry.ModItems.SOCKET_SHARD_STONE.get()) >= 1, "sub_shards did not finish with a socket shard", failures);
        DialogueService.close(a);

        // サブ「数え歌のつづき」: 語り部から始め、村の言い伝え 4 つで報告へ
        DialogueService.open(a, st);
        check(shown(a, ST, "song_more"), "the storyteller should offer the song quest", failures);
        pick(a, ST, "song_more", failures);
        pick(a, ST, "accept", failures);
        check(stage(a, "sub_song").equals("gather"), "sub_song did not start", failures);
        // 見る者の話 (好感度 15 から) と鯨追いの峰の話で、2 つ記される
        Affinity.set(a, ST, 12);
        DialogueService.open(a, st);
        check(shown(a, ST, "watcher_ask"), "the watcher topic should appear at affinity 12", failures);
        pick(a, ST, "watcher_ask", failures);
        check(Lore.state(a).has("prologue/watcher"), "the watcher topic did not record lore", failures);
        DialogueService.close(a);
        DialogueService.open(a, wc);
        pick(a, WC, "peak", failures);
        DialogueService.close(a);
        Quests.poll(a, true);
        check(stage(a, "sub_song").equals("tell"), "four village sayings did not advance sub_song: " + stage(a, "sub_song"), failures);
        DialogueService.open(a, st);
        check(node(a).equals("song_done"), "the storyteller should open song_done, got " + node(a), failures);
        next(a);
        check(stage(a, "sub_song").equals("done") && QuestItems.count(a, "skyabyss:resonance_shard", null) >= 2, "sub_song did not finish with resonance shards", failures);
        DialogueService.close(a);
        notes.add("side quests: shards (collect → give → spyglass) and song (4 sayings → report → clock)");

        // ループ: このループの印が「覚えているような記憶」へ移り、入口が変わる
        WorldLoop.set(server, 0);
        WorldLoop.advance(server);
        check(WorldLoop.catchUp(a) && WorldLoop.catchUp(b), "players did not catch up with the loop", failures);
        check(!WorldLoop.catchUp(a), "catching up twice should do nothing", failures);
        check(NpcBonds.of(a).bond(ST).echoes().contains("song_heard") && NpcBonds.of(a).bond(ST).flags().isEmpty()
                && Affinity.get(a, ST) == 0, "the loop did not move flags into memories", failures);
        DialogueService.open(a, st);
        check(node(a).equals("deja_vu_song"), "after a loop, a song listener should get deja_vu_song, got " + node(a), failures);
        DialogueService.close(a);
        // 職人を指す一言はループごとに一度 (印が記憶へ移ったので、もう一度出る)。ここでは済ませておく
        NpcBonds.of(a).bond(ST).flags().add("ch1p_pointer_told");
        DialogueService.open(a, st);
        check(node(a).equals("menu"), "the deja vu should happen once per loop, got " + node(a), failures);
        DialogueService.close(a);
        DialogueService.open(b, st);
        check(node(b).equals("deja_vu"), "after a loop, someone who only met her should get deja_vu, got " + node(b), failures);
        DialogueService.close(b);
        WorldLoop.set(server, 0);
        notes.add("loop: flags became memories, deja vu lines chosen by what was remembered");

        // 保存: プレイヤーのデータに書かれ、読み戻すと同じ。死んだら引き継ぐ
        CompoundTag saved = a.serializeAttachments(server.registryAccess());
        Tag ql = saved.get(SkyAbyss.MODID + ":quest_log");
        Tag nb = saved.get(SkyAbyss.MODID + ":npc_bonds");
        check(ql != null && nb != null, "quest log / npc bonds are not saved with the player: " + saved.getAllKeys(), failures);
        if (ql != null) {
            QuestLog loaded = QuestLog.CODEC.parse(NbtOps.INSTANCE, ql).getOrThrow();
            check(loaded.equals(QuestLog.of(a)), "quest log does not load back the same: " + loaded + " vs " + QuestLog.of(a), failures);
        }
        if (nb != null) {
            NpcBonds loaded = NpcBonds.CODEC.parse(NbtOps.INSTANCE, nb).getOrThrow();
            check(loaded.equals(NpcBonds.of(a)), "npc bonds do not load back the same", failures);
        }
        check(QuestLog.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow().quests().isEmpty(), "an empty quest log should load empty", failures);
        FakePlayer reborn = player(level, 9, at);
        reborn.copyAttachmentsFrom(a, true);
        check(QuestLog.of(reborn).equals(QuestLog.of(a)) && NpcBonds.of(reborn).equals(NpcBonds.of(a)), "records are not copied on death", failures);
        // 参加者へ送る定義の形: 書いて読むと同じ段階と目的
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        QuestDef main = QuestManager.quest("main_song");
        main.write(buf);
        QuestDef back = QuestDef.read(buf);
        check(back.id().equals(main.id()) && back.stages().size() == main.stages().size() && back.reward().xp() == main.reward().xp()
                && back.stage("inscriptions").objectives().get(0).count() == 3, "quest catalog round trip differs", failures);
        notes.add("save/load, copy on death and the client catalog round trip");
    }

    // 倒した数: 倒した人は距離を問わず、ほかは目的の半径の内だけ
    private static void kills(ServerLevel level, List<String> failures, List<String> notes) {
        Map<String, QuestDef> saved = new LinkedHashMap<>();
        QuestManager.all().forEach(q -> saved.put(q.id(), q));
        List<String> errors = new ArrayList<>();
        JsonObject json = JsonParser.parseString("{\"category\":\"sub\",\"stages\":[{\"id\":\"hunt\",\"objectives\":[{\"id\":\"guards\",\"type\":\"kill\","
                + "\"targets\":[\"skyabyss:bone_sentinel\",\"skyabyss:rib_archer\"],\"count\":2,\"radius\":24}]}]}").getAsJsonObject();
        QuestDef test = QuestDef.parse("selftest_hunt", json, errors);
        errors.forEach(e -> failures.add("test quest: " + e));
        Map<String, QuestDef> with = new LinkedHashMap<>(saved);
        with.put(test.id(), test);
        QuestManager.set(with, List.of());
        try {
            BlockPos at = new BlockPos(0, 300, 0);
            FakePlayer x = player(level, 4, at);
            FakePlayer y = player(level, 5, at);
            Quests.start(x, test.id());
            Quests.start(y, test.id());
            String sentinel = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE).toString();
            Quests.onKill(x, sentinel, true, 1);
            check(QuestLog.of(x).entry(test.id()).progress(0) == 0, "a zombie counted as a guard", failures);
            Quests.onKill(x, "skyabyss:bone_sentinel", true, 200);
            Quests.onKill(y, "skyabyss:bone_sentinel", false, 30);
            check(QuestLog.of(x).entry(test.id()).progress(0) == 1, "the killer far away should count", failures);
            check(QuestLog.of(y).entry(test.id()).progress(0) == 0, "a bystander outside the radius should not count", failures);
            Quests.onKill(y, "skyabyss:rib_archer", false, 10);
            Quests.onKill(y, "skyabyss:rib_archer", false, 10);
            check(stage(y, test.id()).equals("done"), "two kills inside the radius should finish the test quest", failures);
            Quests.forget(x, test.id());
            Quests.forget(y, test.id());
        } finally {
            QuestManager.set(saved, List.of());
        }
        notes.add("kill credit: killer at any range, bystanders within the objective radius, wrong mobs ignored");
    }

    // 町のクエストの限定の人のクエストは、宿の女将の噂 (town.TownQuests.rumor) が始める
    private static boolean rumorStarted(String id) {
        jp.revati.skyabyss.town.TownQuests.TownQuest t = jp.revati.skyabyss.town.TownQuests.byId(id);
        return t != null && t.kind() == jp.revati.skyabyss.town.TownQuests.Kind.GUEST;
    }
}
