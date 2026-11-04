package jp.revati.skyabyss.story;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import jp.revati.skyabyss.story.finale.FinaleEnding;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// 五章からの物語の厚み (2026-11-05 の決めごと) の会話の入口が、決めた条件で選ばれるか。SKYABYSS_SELFTEST_ONLY="story late"
//   五章: 地下の門のイサナ (会う → 層の主のあと → 心臓のあとの答え)
//   六章: 月の四人の問い (約束の話の途中の節が、地上の人に印を付ける) → 地上の人の一度きりの答え
//   終章: イサナの見送り、結末ごとのイサナと語り部の話、雲の島のこだま (鐘守・灯台守)
public final class StoryLateSelfTest {
    private StoryLateSelfTest() {
    }

    static String entry(FakePlayer p, String role) {
        Dialogue d = DialogueManager.dialogue(role);
        if (d == null) {
            return "no dialogue";
        }
        Dialogue.Node n = d.entryNode(new StoryContext(p, role, null));
        return n == null ? "none" : n.id();
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "late_t"));
        String isana = IsanaPost.ROLE;
        String teller = "skyabyss:storyteller";
        try {
            NpcBonds.of(p).bond(isana).flags().add("met");
            NpcBonds.of(p).bond(teller).flags().add("met");
            // ---- 五章
            Quests.start(p, "main_abyss");
            Quests.setStage(p, "main_abyss", "shaft");
            check(f, "ch5g_c_meet".equals(entry(p, isana)), "at the gate Isana should first meet you: " + entry(p, isana));
            NpcBonds.of(p).bond(isana).flags().add("ch5g_met");
            Quests.setStage(p, "main_abyss", "salt_camp");
            check(f, "ch5g_c_bone".equals(entry(p, isana)), "after the bone lord Isana should talk about the bones: " + entry(p, isana));
            Quests.setStage(p, "main_abyss", "storm_camp");
            check(f, "ch5g_c_salt".equals(entry(p, isana)), "after the salt lord Isana should talk about the salt (the bone line is past): " + entry(p, isana));
            Quests.setStage(p, "main_abyss", "report");
            check(f, "ch5g_c_answer".equals(entry(p, isana)), "after the heart Isana should answer her question: " + entry(p, isana));
            NpcBonds.of(p).bond(isana).flags().add(IsanaPost.FLAG_GATE_DONE);
            check(f, !entry(p, isana).startsWith("ch5g_"), "after the answer no chapter-5 line is left: " + entry(p, isana));
            Quests.forget(p, "main_abyss");

            // ---- 六章: 月の四人の問いの節
            String[][] asks = {{"1", "valley"}, {"2", "cliff"}, {"3", "field"}, {"4", "rift"}};
            for (String[] a : asks) {
                Dialogue d = DialogueManager.dialogue("skyabyss:guest_moon_past" + a[0]);
                Dialogue.Node two = d == null ? null : d.nodes().get("ch6_" + a[1] + "_promise2");
                Dialogue.Node ask = d == null ? null : d.nodes().get("ch6_" + a[1] + "_ask");
                check(f, two != null && ask != null && ("ch6_" + a[1] + "_ask").equals(two.next()) && ("ch6_" + a[1] + "_promise3").equals(ask.next()) && !ask.actions().isEmpty(),
                        "the " + a[1] + " promise talk should pass through the question node");
            }
            for (String role : new String[] {isana, teller, "skyabyss:attendant_survivor_bone"}) {
                NpcBonds.of(p).bond(role).flags().add("ch6_asked");
                String want = "ch6e_" + role.split(":")[1];
                check(f, want.equals(entry(p, role)), role + " should answer the moon's question once: " + entry(p, role));
                NpcBonds.of(p).bond(role).flags().add("ch6_answered");
                check(f, !want.equals(entry(p, role)), role + " answered the moon's question twice");
            }

            // ---- 終章: 見送り
            Quests.start(p, "main_finale_ascend");
            Quests.setStage(p, "main_finale_ascend", "ascend");
            check(f, "fin_c_see".equals(entry(p, isana)), "Isana should see you off at the bell tower: " + entry(p, isana));
            NpcBonds.of(p).bond(isana).flags().add("fin_see_told");
            Quests.forget(p, "main_finale_ascend");
            // 結末ごと
            for (String e : FinaleEnding.ENDINGS) {
                p.getPersistentData().putString(FinaleEnding.KEY, e);
                check(f, ("fin_c_" + e).equals(entry(p, isana)), "Isana's line for the " + e + " ending: " + entry(p, isana));
                check(f, ("fin_s_" + e).equals(entry(p, teller)), "the storyteller's talk for the " + e + " ending: " + entry(p, teller));
                NpcBonds.of(p).bond(isana).flags().add("fin_" + e + "_told");
                NpcBonds.of(p).bond(teller).flags().add("fin_s_" + e + "_told");
                check(f, !entry(p, isana).startsWith("fin_c_") && !entry(p, teller).startsWith("fin_s_"), "the " + e + " ending lines repeat");
            }
            p.getPersistentData().remove(FinaleEnding.KEY);
            // 雲の島のこだま
            String[][] echoes = {{"skyabyss:guest_bellkeeper", "finale/bell_echo_1"}, {"skyabyss:guest_tide_lightkeeper", "finale/lamp_echo_2"}};
            for (String[] e : echoes) {
                String want = "fin_e_" + e[0].split(":")[1];
                check(f, !want.equals(entry(p, e[0])), e[0] + " echoed before the memory was read");
                Lore.unlock(p, e[1], false);
                check(f, want.equals(entry(p, e[0])), e[0] + " should echo the isle's memory: " + entry(p, e[0]));
            }
            // 主との再戦の暦 (2026-11-05 の ct_rematch): 挑める主がいるときだけ門に [試し]
            FakePlayer r = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "rematch_t"));
            check(f, !jp.revati.skyabyss.story.finale.Rematch.available(r), "a fresh player could open the rematch calendar");
            Quests.start(r, jp.revati.skyabyss.story.ch6.core.CorePuzzles.QUEST);
            for (int i = 0; i < 8 && !jp.revati.skyabyss.story.ch6.MoonTrial.open(r); i++) {
                Quests.forceStage(r, jp.revati.skyabyss.story.ch6.core.CorePuzzles.QUEST);
            }
            check(f, jp.revati.skyabyss.story.finale.Rematch.available(r), "stopping the moon core should open the rematch calendar");
            check(f, jp.revati.skyabyss.story.finale.Rematch.gateOption("skyabyss_gate").getStyle().getClickEvent() != null
                    && jp.revati.skyabyss.story.finale.Rematch.gateOption("skyabyss_gate").getStyle().getClickEvent().getValue().equals("/skyabyss_gate trial"),
                    "the [trial] option should run /skyabyss_gate trial");
            Quests.forget(r, jp.revati.skyabyss.story.ch6.core.CorePuzzles.QUEST);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST story late crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (String q : new String[] {"main_abyss", "main_finale_ascend"}) {
                Quests.forget(p, q);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST story late PASSED (Isana at the gate: meet, layer lines, answer; moon questions and ground answers once; see-off, ending lines for Isana and the storyteller, isle echoes, rematch calendar opens after the moon core)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST story late FAILED: {}", f);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
