package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

// 職 (docs/design/43) の検査。SKYABYSS_SELFTEST_ONLY="jobs"。
//   武器 → 職 (この作品の武器とバニラの品。鐘銭・お守りは職にならない)・職ごとの点 (ほかの職の木の点は数えない)・
//   持ち替えで枠が入れ替わる・職の武器を持たないと技は使えない・旅人と橋の木は閉じた・天職と刻み目は削った
public final class JobSelfTest {
    private JobSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("jobs", JobSelfTest::run));
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        // ---- 武器 → 職
        Object[][] table = {
                {"skyabyss:whalebone_greatsword", Job.WARRIOR}, {"skyabyss:captain_pike", Job.WARRIOR}, {"skyabyss:coral_harpoon", Job.WARRIOR},
                {"skyabyss:warden_glaive", Job.WARRIOR}, {"skyabyss:coral_shield", Job.WARRIOR}, {"skyabyss:abyss_lamp_sword", Job.WARRIOR},
                {"skyabyss:smith_hammer", Job.SMITH}, {"skyabyss:ember_war_axe", Job.SMITH}, {"skyabyss:thrall_poker", Job.SMITH},
                {"skyabyss:waterway_fang", Job.ASSASSIN}, {"skyabyss:tidecaller_claw", Job.ASSASSIN}, {"skyabyss:raiju_claw", Job.ASSASSIN},
                {"skyabyss:abyss_pulse_dagger", Job.ASSASSIN}, {"skyabyss:thunder_wing_bow", Job.ARCHER}, {"skyabyss:abyss_storm_bow", Job.ARCHER},
                {"skyabyss:abyss_bell_staff", Job.SHAMAN},
                {"minecraft:iron_sword", Job.WARRIOR}, {"minecraft:trident", Job.WARRIOR}, {"minecraft:iron_axe", Job.SMITH},
                {"minecraft:bow", Job.ARCHER}, {"minecraft:crossbow", Job.ARCHER},
                {"skyabyss:bell_coin", null}, {"skyabyss:storm_charm", null}, {"minecraft:lightning_rod", null}, {"minecraft:blaze_rod", null},
                {"minecraft:bread", null}};
        for (Object[] row : table) {
            Item it = item((String) row[0]);
            if (it == Items.AIR) {
                f.add("missing item " + row[0]);
                continue;
            }
            check(f, Job.of(it) == row[1], row[0] + " is " + Job.of(it) + ", expected " + row[1]);
        }
        for (Job j : Job.values()) {
            check(f, Job.ofTree(j.tree) == j, "tree of " + j + " does not map back");
        }
        // ---- 職ごとの点・枠・使う
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "jobs_t"));
        SkillData d = SkillManager.get(p);
        SkillManager.forgetAll(p);
        Growth.get(p).level = 10;
        int total = d.totalPoints(10);
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
        SkillManager.forceRank(p, Skill.RIPOSTE, 2);
        check(f, d.unspentFor(SkillTree.ROCK, 10) == total - 5, "warrior points are wrong: " + d.unspentFor(SkillTree.ROCK, 10));
        check(f, d.unspentFor(SkillTree.BONE, 10) == total, "the warrior's points were taken from the archer: " + d.unspentFor(SkillTree.BONE, 10));
        SkillManager.forceRank(p, Skill.CLOUD_PIERCE, 1);
        check(f, d.unspentFor(SkillTree.BONE, 10) == total - 1 && d.unspentFor(SkillTree.ROCK, 10) == total - 5, "job points mixed: total " + total
                + " bone " + d.unspentFor(SkillTree.BONE, 10) + " rock " + d.unspentFor(SkillTree.ROCK, 10) + " ranks " + d.ranks + " runes " + d.runes);
        check(f, d.canLearn(Skill.SIGNAL, 50) == SkillData.LearnResult.SEALED && d.canLearn(Skill.DUST_CHARGE, 50) == SkillData.LearnResult.SEALED,
                "the traveler / bridge trees are still open");
        check(f, !SkillRules.CALLINGS && !Breakpoints.reached(30, 15), "callings or breakpoints are still on");
        // 持ち替え: 剣 → 戦士の枠、弓 → 弓使いの枠
        d.useJob(null);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        check(f, Job.held(p) == Job.WARRIOR, "holding a sword is not a warrior");
        d.useJob(Job.held(p));
        check(f, Skill.TITAN_CLEAVE.id.equals(d.slots[0]), "the warrior bar did not fill: " + String.join(",", d.slots));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        d.useJob(Job.held(p));
        check(f, Skill.CLOUD_PIERCE.id.equals(d.slots[0]) && !java.util.Arrays.asList(d.slots).contains(Skill.TITAN_CLEAVE.id),
                "the archer bar is wrong: " + String.join(",", d.slots));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        d.useJob(Job.held(p));
        check(f, Skill.TITAN_CLEAVE.id.equals(d.slots[0]), "the warrior bar was not restored");
        // 職の武器を持たないと使えない
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        d.mana = 100;
        d.cooldowns.clear();
        d.globalCooldown = 0;
        check(f, SkillManager.cast(p, 0) == SkillManager.CastResult.EMPTY, "a skill was cast without a job weapon");
        // 保存と読み直しで職の枠が残る
        net.minecraft.nbt.CompoundTag nbt = d.serializeNBT(server.registryAccess());
        SkillData back = new SkillData();
        back.deserializeNBT(server.registryAccess(), nbt);
        check(f, back.jobBars.containsKey("warrior") && back.jobBars.containsKey("archer"), "job bars were not saved: " + back.jobBars.keySet());
        SkillManager.forgetAll(p);
        p.discard();
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST jobs PASSED (weapon to job for {} items, per-job points, bar swap on weapon change, no skills without a job weapon, closed trees, save)", table.length);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST jobs FAILED: {}", s));
        }
    }
}
