package jp.revati.skyabyss.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// イサナの章ごとの居場所 (IsanaPost): 岩鯨のあと噂を聞くまでは鯨見、噂のあと港、潮殻を止めて港で話したら風待の火のそば、
// 雷翼を止めて「俺は残る」と話したら鯨見。五章は地下の門の前、終章の門出は大鐘楼の門のそば。見える鯨追いは一人だけ。SKYABYSS_SELFTEST_ONLY="isana post"
public final class IsanaPostSelfTest {
    private IsanaPostSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "isana_t"));
        ProgressData pd = ProgressData.get(server);
        Set<String> flags = NpcBonds.of(p).bond(IsanaPost.ROLE).flags();
        NpcEntity harbor = NpcSetup.WHALE_CHASER.get().create(level);
        NpcEntity hut = NpcSetup.WHALE_CHASER.get().create(level);
        NpcEntity town = NpcSetup.WHALE_CHASER.get().create(level);
        NpcEntity teller = NpcSetup.STORYTELLER.get().create(level);
        try {
            if (harbor == null || hut == null || town == null || teller == null) {
                f.add("could not create the NPCs");
                return;
            }
            harbor.addTag(IsanaPost.Post.HARBOR.tag);
            hut.addTag(IsanaPost.Post.HUT.tag);
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "before the whale's heart, Isana stays in the whale town");
            pd.markPersonal(p.getUUID(), "stone_whale");
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "chapter 2 before the tide quest: still in the whale town");
            Quests.start(p, "main_tide");
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "during the rumor stage she is still in the whale town");
            flags.add(IsanaPost.FLAG_TOLD);
            check(f, IsanaPost.post(p) == IsanaPost.Post.HARBOR, "after she says she will go ahead, she is at the harbor");
            flags.remove(IsanaPost.FLAG_TOLD);
            Quests.setStage(p, "main_tide", "prepare");
            check(f, IsanaPost.post(p) == IsanaPost.Post.HARBOR, "past the rumor stage (or after skipping it) she is at the harbor");
            check(f, IsanaPost.visibleTo(harbor, p) && !IsanaPost.visibleTo(hut, p) && !IsanaPost.visibleTo(town, p), "at the harbor stage only the harbor Isana is shown");
            pd.markPersonal(p.getUUID(), "tide_shell");
            check(f, IsanaPost.post(p) == IsanaPost.Post.HARBOR, "after the tide shell's heart she waits at the harbor for the talk");
            flags.add(IsanaPost.FLAG_DONE);
            check(f, IsanaPost.post(p) == IsanaPost.Post.HUT, "after the harbor talk she waits by the fire in Kazamachi");
            check(f, !IsanaPost.visibleTo(harbor, p) && IsanaPost.visibleTo(hut, p) && !IsanaPost.visibleTo(town, p), "in chapter 3 only the Isana by the fire is shown");
            pd.markPersonal(p.getUUID(), "thunder_wing");
            check(f, IsanaPost.post(p) == IsanaPost.Post.HUT, "after the thunder wing's heart she stays by the fire until she tells she stays");
            flags.add(IsanaPost.FLAG_STAY);
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "after 'I stay' she goes back to the whale town");
            check(f, !IsanaPost.visibleTo(harbor, p) && !IsanaPost.visibleTo(hut, p) && IsanaPost.visibleTo(town, p), "back in the whale town only the town Isana is shown");
            check(f, IsanaPost.visibleTo(teller, p), "the storyteller is always shown");
            // 五章 (2026-11-05 の c5_isana = gate): 酒場の地下の戸を開けてから、心臓のあとの話をするか「近づく月」を終えるまで、地下の門の前
            NpcEntity gate = NpcSetup.WHALE_CHASER.get().create(level);
            gate.addTag(IsanaPost.Post.GATE.tag);
            Quests.start(p, "main_abyss");
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "before the cellar door opens she stays in the whale town");
            Quests.setStage(p, "main_abyss", "shaft");
            check(f, IsanaPost.post(p) == IsanaPost.Post.GATE, "after the cellar door opens she waits at the underground gate");
            check(f, IsanaPost.visibleTo(gate, p) && !IsanaPost.visibleTo(town, p), "in chapter 5 only the Isana at the gate is shown");
            // 層の主のあとの話の条件 (quest の after)
            jp.revati.skyabyss.quest.Condition afterBone = jp.revati.skyabyss.quest.Condition.parse(com.google.gson.JsonParser.parseString(
                    "{\"type\":\"quest\",\"quest\":\"main_abyss\",\"is\":\"active\",\"after\":\"bone_lord\"}"), new ArrayList<>(), "test");
            check(f, !afterBone.test(jp.revati.skyabyss.quest.StoryContext.of(p)), "'after bone_lord' was true at the shaft stage");
            Quests.setStage(p, "main_abyss", "salt_camp");
            check(f, afterBone.test(jp.revati.skyabyss.quest.StoryContext.of(p)), "'after bone_lord' was false at the salt camp stage");
            Quests.setStage(p, "main_abyss", "report");
            check(f, IsanaPost.post(p) == IsanaPost.Post.GATE, "after the heart she waits at the gate to answer");
            flags.add(IsanaPost.FLAG_GATE_DONE);
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "after her answer she goes back to the whale town");
            flags.remove(IsanaPost.FLAG_GATE_DONE);
            Quests.forceStage(p, "main_abyss");
            Quests.start(p, jp.revati.skyabyss.story.ch5.MoonNear.QUEST);
            Quests.forceStage(p, jp.revati.skyabyss.story.ch5.MoonNear.QUEST);
            Quests.forceStage(p, jp.revati.skyabyss.story.ch5.MoonNear.QUEST);
            jp.revati.skyabyss.quest.QuestLog.Entry near = jp.revati.skyabyss.quest.QuestLog.of(p).entry(jp.revati.skyabyss.story.ch5.MoonNear.QUEST);
            check(f, near != null && near.done && IsanaPost.post(p) == IsanaPost.Post.TOWN, "from chapter 6 she is back in the whale town even without the answer");
            gate.discard();
            // 終章 (fin_see): 「雲の上へ」の段 ascend のあいだ、大鐘楼の門のそば
            Quests.start(p, "main_finale_ascend");
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "during the seventh-verse talk she is still in the whale town");
            Quests.setStage(p, "main_finale_ascend", "ascend");
            check(f, IsanaPost.post(p) == IsanaPost.Post.BELL, "before the cloud sea she sees you off at the bell tower gate");
            Quests.setStage(p, "main_finale_ascend", "bell");
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "after reaching the cloud sea she is back in the whale town");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST isana post crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (String t : new String[] {"stone_whale", "tide_shell", "thunder_wing"}) {
                pd.clearPersonal(p.getUUID(), t);
            }
            for (String q : new String[] {"main_tide", "main_abyss", jp.revati.skyabyss.story.ch5.MoonNear.QUEST, "main_finale_ascend"}) {
                Quests.forget(p, q);
            }
            for (NpcEntity n : new NpcEntity[] {harbor, hut, town, teller}) {
                if (n != null) {
                    n.discard();
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST isana post PASSED (whale town until the rumor, harbor in chapter 2, the Kazamachi fire in chapter 3, back after 'I stay', the underground gate in chapter 5 until her answer, the bell tower gate before the cloud sea, one Isana shown)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST isana post FAILED: {}", f);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
