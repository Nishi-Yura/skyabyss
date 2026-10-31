package jp.revati.skyabyss.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.Rebirths;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 職の熟練 (JobMastery) の自己テスト。SKYABYSS_SELFTEST_ONLY="mastery"
//   段の境と進み / Lv 50 未満の経験値は熟練にならない / Lv 50 の経験値はいまの職の熟練へ / 保存と読み戻し / 段 10 の称号 / 光が落ちない
public final class JobMasterySelfTest {
    private JobMasterySelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000a5701"), "mastery_test"));
        GrowthData d = Growth.get(p);
        String job0 = SkillManager.get(p).curJob;
        int level0 = d.level;
        try {
            d.mastery.clear();
            check(f, JobMastery.tier(0) == 0 && JobMastery.tier(JobMastery.need(1) - 1) == 0 && JobMastery.tier(JobMastery.need(1)) == 1,
                    "the first mastery step is wrong");
            long all = 0;
            for (int t = 1; t <= JobMastery.TIERS; t++) {
                all += JobMastery.need(t);
            }
            check(f, JobMastery.tier(all) == JobMastery.TIERS && JobMastery.tier(all - 1) == JobMastery.TIERS - 1 && JobMastery.progress(all) == 1f,
                    "the last mastery step is wrong: " + all);
            check(f, Math.abs(JobMastery.progress(JobMastery.need(1) + JobMastery.need(2) / 2) - 0.5f) < 0.01f, "mastery progress is wrong");

            SkillManager.get(p).curJob = Job.WARRIOR.id;
            d.level = 30;
            Growth.addXp(p, 500, "");
            check(f, d.mastery.isEmpty(), "experience below the level cap went into mastery");
            d.level = GrowthRules.MAX_LEVEL;
            d.xp = 0;
            Growth.addXp(p, 7000, "");
            check(f, JobMastery.tier(p, Job.WARRIOR) == 1 && JobMastery.tier(p, Job.MAGE) == 0, "experience at the cap did not go into the current job's mastery");

            GrowthData back = new GrowthData();
            back.deserializeNBT(server.registryAccess(), d.serializeNBT(server.registryAccess()));
            check(f, back.mastery.getOrDefault(Job.WARRIOR.id, 0L).longValue() == d.mastery.getOrDefault(Job.WARRIOR.id, -1L).longValue(), "mastery was not saved");

            d.mastery.put(Job.WARRIOR.id, all);
            check(f, RebirthTitles.has(RebirthTitles.unlockedMask(Rebirths.facts(p)), "mastery_warrior")
                    && !RebirthTitles.has(RebirthTitles.unlockedMask(Rebirths.facts(p)), "mastery_mage"), "the mastery title is wrong");
            for (int t : new int[] {1, 5, 8, 10}) {
                long xp = 0;
                for (int i = 1; i <= t; i++) {
                    xp += JobMastery.need(i);
                }
                d.mastery.put(Job.WARRIOR.id, xp);
                JobMastery.aura(p);
            }
        } catch (RuntimeException ex) {
            f.add("mastery test crashed: " + ex);
            SkyAbyss.LOGGER.error("mastery test", ex);
        } finally {
            d.mastery.clear();
            d.level = level0;
            d.xp = 0;
            SkillManager.get(p).curJob = job0;
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST mastery PASSED (steps, progress, only after the cap, current job, save, title, aura)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST mastery FAILED: {}", m));
        }
    }
}
