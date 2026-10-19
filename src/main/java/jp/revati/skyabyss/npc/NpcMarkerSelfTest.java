package jp.revati.skyabyss.npc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.npc.NpcMarkers.Marker;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestUx;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.common.util.FakePlayer;

// NPC の頭の上の印・章の札・更新の音の自己テスト (NpcQuestSelfTest から呼ぶ)。
//   印: 人ごとに決まる (別の人の進みで変わらない)。メインの段・報告・新しい頼み・新しい雑談の違い。ハブの印 (visibleOptions) と食い違わない。
//       話しても何も起きない (定型の入口だけ) なら印なし。入口を開いたら雑談の点は消える
//   文: 「探索しつつ」と言わない、章の札の文と音の資源がある、パケットの詰め替え
public final class NpcMarkerSelfTest {
    private static final String ROLE = "skyabyss:test_marker";

    private NpcMarkerSelfTest() {
    }

    private static void check(boolean ok, String what, List<String> failures) {
        if (!ok) {
            failures.add("[marker] " + what);
        }
    }

    // a, b: 別々の人 (どちらも持ち物とクエストを空にしてある)。st: 語り部、wc: 鯨追い
    public static void run(FakePlayer a, FakePlayer b, NpcEntity st, NpcEntity wc, List<String> failures, List<String> notes) {
        // --- 人ごと: a が「語り部に尋ねる」段、b が「鯨追いに話す」段
        Quests.forgetAll(a);
        Quests.forgetAll(b);
        a.setData(NpcSetup.BONDS, new NpcBonds());
        b.setData(NpcSetup.BONDS, new NpcBonds());
        Quests.setStage(a, "main_song", "ask_storyteller");
        Quests.setStage(b, "main_song", "find_chaser");
        check(NpcMarkers.of(a, st) == Marker.NEW_MAIN, "a (ask_storyteller) should see a gold ! over the storyteller: " + NpcMarkers.of(a, st), failures);
        check(NpcMarkers.of(a, wc) != Marker.NEW_MAIN && NpcMarkers.of(a, wc) != Marker.READY_MAIN,
                "a should have no main marker over the chaser: " + NpcMarkers.of(a, wc), failures);
        check(NpcMarkers.of(b, wc) == Marker.NEW_MAIN, "b (find_chaser) should see a gold ! over the chaser: " + NpcMarkers.of(b, wc), failures);
        check(NpcMarkers.of(b, st) != Marker.NEW_MAIN && NpcMarkers.of(b, st) != Marker.READY_MAIN,
                "b should have no main marker over the storyteller: " + NpcMarkers.of(b, st), failures);
        // 一人のクエストを進めても、もう一人の印は変わらない
        Marker bBefore = NpcMarkers.of(b, wc);
        Quests.setStage(a, "main_song", "find_chaser");
        check(NpcMarkers.of(b, wc) == bBefore, "a's progress changed b's marker", failures);

        // --- 報告の「?」: 最後の段は、二人とも話す相手
        Quests.setStage(a, "main_song", "return");
        check(NpcMarkers.of(a, st) == Marker.READY_MAIN && NpcMarkers.of(a, wc) == Marker.READY_MAIN,
                "the return stage should show a gold ? over both: " + NpcMarkers.of(a, st) + "/" + NpcMarkers.of(a, wc), failures);
        // クエストを終えたら、その印は消える (報告も新しい段もない)
        QuestLog.Entry done = QuestLog.of(a).entry("main_song");
        done.done = true;
        check(NpcMarkers.of(a, st) != Marker.READY_MAIN && NpcMarkers.of(a, st) != Marker.NEW_MAIN, "a finished quest still marks the storyteller", failures);

        // --- ハブの印との一致: 実際の会話の、入口の節で選べる選択肢に新しい頼み・渡す品があれば「!」(段の印がないとき)。なければ「!」にならない
        for (String role : List.of(NpcSetup.STORYTELLER_ID, NpcSetup.WHALE_CHASER_ID)) {
            Dialogue d = DialogueManager.dialogue(role);
            NpcEntity npc = role.equals(NpcSetup.STORYTELLER_ID) ? st : wc;
            for (int scenario = 0; scenario < 3; scenario++) {
                Quests.forgetAll(a);
                a.setData(NpcSetup.BONDS, new NpcBonds());
                if (scenario == 1) {
                    Quests.setStage(a, "main_song", "see_whale");
                } else if (scenario == 2) {
                    Quests.setStage(a, "main_song", "board");
                    QuestLog.of(a).entry("main_song").done = true;
                }
                StoryContext ctx = new StoryContext(a, role, npc);
                Marker m = NpcMarkers.of(d, ctx);
                Dialogue.Node entry = d.entryNode(ctx);
                List<Integer> shown = new ArrayList<>();
                List<String> kinds = new ArrayList<>();
                DialogueService.visibleOptions(d, entry, ctx, shown, kinds);
                boolean request = kinds.contains("quest") || kinds.contains("give");
                boolean step = NpcMarkers.questStep(a, role) != Marker.NONE;
                if (!step) {
                    boolean bang = m == Marker.NEW_MAIN || m == Marker.NEW_SIDE;
                    check(bang == request, role + " scenario " + scenario + ": marker " + m + " disagrees with the hub (kinds " + kinds + ")", failures);
                }
            }
        }

        // --- 話しても何も起きなければ印なし / 新しい雑談の点 / 開いたら消える / 頼みがあれば「!」(見本の会話)
        Quests.forgetAll(a);
        a.setData(NpcSetup.BONDS, new NpcBonds());
        List<String> errors = new ArrayList<>();
        Dialogue plain = Dialogue.parse("test_marker", json("""
                {"npc":"%s","entries":[{"node":"special","if":{"type":"flag","flag":"gossip"}},{"node":"idle"}],
                 "nodes":{"special":{"text":"x","next":"end"},"idle":{"text":"y","next":"end"}}}""".formatted(ROLE)), errors);
        check(errors.isEmpty(), "sample dialogue errors " + errors, failures);
        StoryContext ctx = new StoryContext(a, ROLE, st);
        check(NpcMarkers.of(plain, ctx) == Marker.NONE, "a plain greeting must not carry a marker: " + NpcMarkers.of(plain, ctx), failures);
        NpcBonds.of(a).bond(ROLE).flags.add("gossip");
        check(NpcMarkers.of(plain, ctx) == Marker.CHAT, "an unheard entry should carry the chat dot: " + NpcMarkers.of(plain, ctx), failures);
        NpcBonds.of(a).bond(ROLE).seen.add("special");
        check(NpcMarkers.of(plain, ctx) == Marker.NONE, "a heard entry must lose its dot: " + NpcMarkers.of(plain, ctx), failures);
        // 別の人には、同じ入口の点がまだ出る (人ごと)
        NpcBonds.of(b).bond(ROLE).flags.add("gossip");
        check(NpcMarkers.of(plain, new StoryContext(b, ROLE, st)) == Marker.CHAT, "b should still have the dot (per player)", failures);
        Dialogue request = Dialogue.parse("test_marker", json("""
                {"npc":"%s","entries":[{"node":"hub"}],
                 "nodes":{"hub":{"text":"x","options":[{"id":"ask","kind":"quest","goto":"offer"},{"id":"bye","kind":"leave","goto":"end"}]},
                          "offer":{"text":"y","options":[{"id":"ok","kind":"quest","goto":"thanks","do":[{"type":"open_board"}]},{"id":"no","kind":"leave","goto":"end"}]},
                          "thanks":{"text":"z","next":"end"}}}""".formatted(ROLE)),
                errors);
        check(errors.isEmpty(), "sample hub errors " + errors, failures);
        Marker req = NpcMarkers.of(request, new StoryContext(a, ROLE, st));
        check(req == Marker.NEW_SIDE || req == Marker.NEW_MAIN, "a new request in the hub should show !: " + req, failures);
        // 先に頼みが一つも出ないハブ (「ほかに頼みは」の先が立ち去るだけ) なら「!」にならない
        Dialogue empty = Dialogue.parse("test_marker", json("""
                {"npc":"%s","entries":[{"node":"hub"}],
                 "nodes":{"hub":{"text":"x","options":[{"id":"ask","kind":"quest","goto":"offer"},{"id":"bye","kind":"leave","goto":"end"}]},
                          "offer":{"text":"y","options":[{"id":"no","kind":"leave","goto":"end"}]}}}""".formatted(ROLE)), errors);
        check(NpcMarkers.of(empty, new StoryContext(a, ROLE, st)) == Marker.NONE, "a hub with nothing to offer must carry no marker", failures);
        // 先が「頼み」に見えても実際には何も起きない (クエストを始めない・品を渡さない) なら「!」にならない
        Dialogue talky = Dialogue.parse("test_marker", json("""
                {"npc":"%s","entries":[{"node":"hub"}],
                 "nodes":{"hub":{"text":"x","options":[{"id":"ask","kind":"quest","goto":"offer"},{"id":"bye","kind":"leave","goto":"end"}]},
                          "offer":{"text":"y","options":[{"id":"ok","kind":"quest","goto":"thanks"},{"id":"no","kind":"leave","goto":"end"}]},
                          "thanks":{"text":"z","next":"end"}}}""".formatted(ROLE)), errors);
        check(NpcMarkers.of(talky, new StoryContext(a, ROLE, st)) == Marker.NONE, "a request that offers nothing must carry no marker: " + NpcMarkers.of(talky, new StoryContext(a, ROLE, st)), failures);
        // --- パケットの詰め替え
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.server.registryAccess());
        NpcMarkers.Update.STREAM_CODEC.encode(buf, new NpcMarkers.Update(new int[] {5, 900}, new int[] {1, 5}));
        NpcMarkers.Update back = NpcMarkers.Update.STREAM_CODEC.decode(buf);
        check(back.ids().length == 2 && back.ids()[1] == 900 && back.markers()[1] == 5, "marker packet round trip", failures);
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.server.registryAccess());
        QuestUx.ChapterPayload.STREAM_CODEC.encode(buf, new QuestUx.ChapterPayload(-1));
        check(QuestUx.ChapterPayload.STREAM_CODEC.decode(buf).chapter() == -1, "chapter packet round trip (-1)", failures);
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.server.registryAccess());
        QuestUx.ChapterPayload.STREAM_CODEC.encode(buf, new QuestUx.ChapterPayload(3));
        check(QuestUx.ChapterPayload.STREAM_CODEC.decode(buf).chapter() == 3, "chapter packet round trip (3)", failures);

        // --- 文と音の資源
        for (String lang : List.of("ja_jp", "en_us")) {
            for (String ns : List.of("skyabyss_opening", "skyabyss_npc")) {
                String text = resource("/assets/" + ns + "/lang/" + lang + ".json");
                for (String bad : new String[] {"探索しつつ", "探索しながら", "探索して強く", "探索で強く", "Explore;", "Explore,", "Explore and", "Grow stronger"}) {
                    check(!text.contains(bad), ns + "/" + lang + " still says \"" + bad + "\"", failures);
                }
            }
        }
        check(resource("/assets/skyabyss/sounds.json").contains("\"quest_main\"") && resource("/assets/skyabyss/sounds.json").contains("\"quest_side\""),
                "sounds.json lacks quest_main / quest_side", failures);
        for (String ogg : new String[] {"quest_main", "quest_side"}) {
            check(NpcMarkerSelfTest.class.getResource("/assets/skyabyss/sounds/ui/" + ogg + ".ogg") != null, "missing sounds/ui/" + ogg + ".ogg", failures);
        }
        // 章の札の文 (tab と title) が ja・en の両方にある
        for (jp.revati.skyabyss.story.Chapters c : jp.revati.skyabyss.story.Chapters.values()) {
            for (String lang : List.of("ja_jp", "en_us")) {
                check(anyLang(c.tabKey(), lang) && anyLang(c.titleKey(), lang), "chapter label text missing: " + c.id() + " " + lang, failures);
            }
        }
        notes.add("npc markers: per player, hub-consistent, chat dot clears, packets, texts");
    }

    // すべての名前空間の lang から、キーを探す
    private static boolean anyLang(String key, String lang) {
        for (String ns : List.of("skyabyss", "skyabyss_opening", "skyabyss_npc", "skyabyss_story", "skyabyss_ch1", "skyabyss_ch2", "skyabyss_ch3", "skyabyss_ch4",
                "skyabyss_lore", "skyabyss_ember")) {
            if (resource("/assets/" + ns + "/lang/" + lang + ".json").contains("\"" + key + "\"")) {
                return true;
            }
        }
        return false;
    }

    private static String resource(String path) {
        try (InputStream in = NpcMarkerSelfTest.class.getResourceAsStream(path)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonObject json(String text) {
        return JsonParser.parseReader(new InputStreamReader(new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
