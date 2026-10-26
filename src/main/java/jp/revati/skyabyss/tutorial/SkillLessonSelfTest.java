package jp.revati.skyabyss.tutorial;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.facility.TrainingDummy;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillStance;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.util.FakePlayer;

// 技の稽古 (SkillLesson) の自己テスト: 偽の人が稽古を頭から歩く (師範と話す → 技を覚えて枠に置く → 木偶に技を二度 (同じ使用は一度) →
// 正面では済まず背後で済む → 構えを変える → 振り直しを聞く → 済む・鐘銭)。もう一度受けても鐘銭は出ない。飛ばすと記録が消える。
// 後の章: 星術師の木が開くと一段の稽古が始まり、その木の技を当てると済む。データ (7 本)・会話の節・日英の文がそろう
public final class SkillLessonSelfTest {
    private SkillLessonSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("skill lesson selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST skill lesson PASSED (meet, learn+slot, two casts on a dummy, behind, respec talk, reward once, replay, skip, tree lesson, data, ja/en)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST skill lesson FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static String stage(FakePlayer p) {
        String s = SkillLesson.stage(p);
        return s == null ? (QuestLog.of(p).entry(SkillLesson.QUEST) != null && QuestLog.of(p).entry(SkillLesson.QUEST).done ? "done" : "none") : s;
    }

    // 鐘銭は旅嚢にも入る (2026-10-25) ので、袋と持ち物の両方を数える
    private static int coins(FakePlayer p) {
        Item coin = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "bell_coin"));
        return jp.revati.skyabyss.pouch.Pouch.countAll(p, coin);
    }

    private static void hit(FakePlayer p, TrainingDummy dummy, Skill skill, long castTick) {
        SkillManager.get(p).lastCastTick = castTick;
        dummy.invulnerableTime = 0;
        SkillActions.hitPlain(p, dummy, 1f, skill);
    }

    private static void run(MinecraftServer server, List<String> f) {
        // データ
        check(f, QuestManager.quest(SkillLesson.QUEST) != null, "lesson/skills is not loaded");
        for (SkillLesson.Unlock u : SkillLesson.Unlock.values()) {
            check(f, QuestManager.quest(u.quest()) != null, u.quest() + " is not loaded");
        }
        QuestManager.loadErrors().stream().filter(e -> e.contains("lesson")).forEach(e -> f.add("load error: " + e));
        jp.revati.skyabyss.npc.DialogueManager.loadErrors().stream().filter(e -> e.contains("lesson")).forEach(e -> f.add("dialogue error: " + e));
        var dialogue = jp.revati.skyabyss.npc.DialogueManager.dialogue(SkillLesson.MASTER);
        for (String node : List.of("lesson_offer", "lesson_new", "lesson_again", "lesson_skip", "lesson_respec")) {
            check(f, dialogue != null && dialogue.nodes().containsKey(node), "the training master has no node " + node);
        }
        for (String code : List.of("ja_jp", "en_us")) {
            JsonObject lang = lang(code);
            check(f, lang != null, code + ": no lesson lang");
            if (lang != null) {
                for (String k : List.of("lesson.skyabyss.hint.learn", "lesson.skyabyss.hint.cast", "lesson.skyabyss.hint.behind", 
                        "lesson.skyabyss.hint.reset", "lesson.skyabyss.screen.learn", "message.skyabyss.lesson.reward", "message.skyabyss.lesson.behind",
                        "message.skyabyss.lesson.say.finish", "message.skyabyss.lesson.skipped")) {
                    check(f, lang.has(k), code + ": missing " + k);
                }
                for (SkillLesson.Unlock u : SkillLesson.Unlock.values()) {
                    check(f, lang.has("message.skyabyss.lesson.say." + u.name), code + ": missing the line for " + u.name);
                }
            }
        }

        ServerLevel level = server.overworld();
        BlockPos at = level.getSharedSpawnPos().above(40);
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[lesson]"));
        p.getInventory().clearContent();
        Quests.forgetAll(p);
        SkillManager.forgetAll(p);
        NpcBonds.of(p).bond(SkillLesson.MASTER).flags().clear();
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        TrainingDummy dummy = FacilitySetup.TRAINING_DUMMY.get().create(level);
        if (dummy == null) {
            f.add("could not make a dummy");
            return;
        }
        dummy.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        dummy.yBodyRot = 0;
        level.addFreshEntity(dummy);
        try {
            // 師範と話す
            check(f, Quests.start(p, SkillLesson.QUEST), "the lesson did not start");
            check(f, stage(p).equals("meet"), "the lesson should open at meet: " + stage(p));
            Quests.onTalk(p, SkillLesson.MASTER, "lesson_offer");
            check(f, stage(p).equals("learn"), "talking to the master should move to learn: " + stage(p));
            // 覚えて枠に置く (覚えると空いた枠に入る)
            SkillLesson.poll(p);
            check(f, stage(p).equals("learn"), "no skill yet, but learn passed");
            check(f, SkillManager.learn(p, Skill.TITAN_CLEAVE) == SkillData.LearnResult.OK, "could not learn titan cleave at Lv 1");
            SkillLesson.poll(p);
            check(f, stage(p).equals("cast"), "a slotted skill should move to cast: " + stage(p));
            // 木偶に技を二度 (同じ使用の二打は一度に数える)
            hit(p, dummy, Skill.TITAN_CLEAVE, 100);
            hit(p, dummy, Skill.TITAN_CLEAVE, 100);
            check(f, stage(p).equals("cast"), "one cast hitting twice should count once: " + stage(p));
            hit(p, dummy, Skill.TITAN_CLEAVE, 200);
            check(f, stage(p).equals("behind"), "two casts on the dummy should move to behind: " + stage(p));
            // 正面 (z+2: 木偶の顔の側) では済まない。背後 (z-2) で済む
            check(f, !SkillLesson.behind(dummy, p), "the front counted as behind");
            hit(p, dummy, Skill.TITAN_CLEAVE, 300);
            check(f, stage(p).equals("behind"), "a hit from the front passed the behind step");
            p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 1.5, 0, 0);
            check(f, SkillLesson.behind(dummy, p), "behind the dummy was not behind");
            dummy.invulnerableTime = 0;
            dummy.hurt(p.damageSources().playerAttack(p), 1f);
            // 構えの段は 43 (6 職) で削った: 背後のあとは振り直し
            check(f, stage(p).equals("reset"), "a plain hit from behind should move to reset: " + stage(p));
            // 振り直しを聞く → 済む・鐘銭
            int before = coins(p);
            Quests.onTalk(p, SkillLesson.MASTER, "lesson_respec");
            check(f, stage(p).equals("done"), "asking about respecs should finish the lesson: " + stage(p));
            check(f, coins(p) == before + SkillLesson.REWARD_COINS, "the reward was not paid: " + (coins(p) - before));
            // もう一度: 頭から。報酬は出ない
            SkillLesson.act(p, "replay", null);
            check(f, stage(p).equals("meet"), "replay should restart at meet: " + stage(p));
            before = coins(p);
            SkillLesson.act(p, "finish", null);
            check(f, coins(p) == before, "the reward was paid twice");
            // 飛ばす
            SkillLesson.act(p, "skip", null);
            check(f, QuestLog.of(p).entry(SkillLesson.QUEST) == null, "skip should clear the lesson");

            // 後の章: 星術師の木が開く → 一段の稽古 → その木の技を当てる
            SkillData d = SkillManager.get(p);
            SkillLesson.poll(p);
            check(f, QuestLog.of(p).entry(SkillLesson.Unlock.STAR.quest()) == null, "the star lesson started before the tree opened");
            d.unlockedTrees.add(SkillTree.STAR.id);
            SkillLesson.poll(p);
            var star = QuestLog.of(p).entry(SkillLesson.Unlock.STAR.quest());
            check(f, star != null && !star.done, "the star lesson did not start when the tree opened");
            SkillLesson.onHit(p, dummy, Skill.TITAN_CLEAVE);
            check(f, star != null && !star.done, "a rock skill finished the star lesson");
            Skill starSkill = Skill.inTree(SkillTree.STAR).isEmpty() ? null : Skill.inTree(SkillTree.STAR).get(0);
            if (starSkill != null) {
                SkillLesson.onHit(p, dummy, starSkill);
                star = QuestLog.of(p).entry(SkillLesson.Unlock.STAR.quest());
                check(f, star != null && star.done, "a star skill did not finish the star lesson");
            }
        } finally {
            dummy.discard();
            Quests.forgetAll(p);
            SkillManager.forgetAll(p);
        }
    }

    private static JsonObject lang(String code) {
        try (InputStream in = SkillLessonSelfTest.class.getResourceAsStream("/assets/skyabyss_lesson/lang/" + code + ".json")) {
            return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
