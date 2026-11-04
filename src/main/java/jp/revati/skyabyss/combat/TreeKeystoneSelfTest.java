package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillBuild;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillKeystone;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 型の要の「動きが変わる効果」(docs/design/48 の 4.1。2026-11-04 の手本 = 鍛冶師の木)。段の名前は "tree keystones"。
//   灰の契り: 満ちた近接の一撃で主の崩しの帯が ×1.5 溜まり、崩れた主へ与える傷 ×1.2
//   鍛え人の目: 満ちた近接の一撃のあと、次に受ける生き物の一撃 ×0.7
//   熾火の肺: 敵を倒すたびに発動スキルの再使用まで −1 秒 (節の鍵 cd_kill が数えられる)
public final class TreeKeystoneSelfTest {
    private TreeKeystoneSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("tree keystones", TreeKeystoneSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        BlockPos at = level.getSharedSpawnPos().atY(250);
        FakePlayer a = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0a77"), "tree_keys"));
        a.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        SkillData d = SkillManager.get(a);
        String job = d.curJob;
        Zombie boss = EntityType.ZOMBIE.create(level);
        Zombie foe = EntityType.ZOMBIE.create(level);
        try {
            d.curJob = Job.SMITH.id;
            d.nodesOf(Job.SMITH).addAll(List.of("ash_pact", "smith_eye", "ember_lung"));
            d.applyNodes();
            if (!SkillBuild.has(d, SkillKeystone.ASH_PACT) || !d.knows(Skill.SMITH_EYE)) {
                f.add("the smith's keystones did not come from the nodes");
            }
            if (Math.abs(jp.revati.skyabyss.rpg.skill.tree.NodeEffects.sum(a, "cd_kill") - 1.0) > 1.0E-6) {
                f.add("ember lung does not count its cd_kill (" + jp.revati.skyabyss.rpg.skill.tree.NodeEffects.sum(a, "cd_kill") + ")");
            }
            if (boss == null || foe == null) {
                f.add("could not make the test zombies");
                return;
            }
            for (Zombie z : new Zombie[] {boss, foe}) {
                z.setNoAi(true);
                z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
                z.setHealth(400);
            }
            boss.setPos(at.getX() + 3.5, at.getY(), at.getZ() + 0.5);
            foe.setPos(at.getX() - 3.5, at.getY(), at.getZ() + 0.5);
            level.addFreshEntity(boss);
            level.addFreshEntity(foe);
            BossOpenings.plantedForTest = e -> e == boss;
            // 灰の契り: 帯の溜まり ×1.5
            boss.invulnerableTime = 0;
            MeleeHits.during(a, 1f, () -> boss.hurt(level.damageSources().playerAttack(a), 4f));
            double withPact = BreakGauge.fill(boss);
            BreakGauge.forget(boss);
            d.keystones.remove(SkillKeystone.ASH_PACT.id);
            boss.invulnerableTime = 0;
            boss.setHealth(400);
            MeleeHits.during(a, 1f, () -> boss.hurt(level.damageSources().playerAttack(a), 4f));
            double without = BreakGauge.fill(boss);
            // 灰の契りの与える傷 +12% (KEY_ASH_DAMAGE) も溜まりに乗る
            double wantFill = SkillRules.KEY_ASH_BREAK_FILL * (1 + SkillRules.KEY_ASH_DAMAGE);
            if (without <= 0 || Math.abs(withPact / without - wantFill) > 0.03) {
                f.add("ash pact should fill the break gauge x" + wantFill + " (" + withPact + " vs " + without + ")");
            }
            // 崩れた主へ ×1.2
            BreakGauge.add(boss, BreakGauge.size(boss, BreakGauge.breaks(boss)));
            boss.invulnerableTime = 0;
            float h0 = boss.getHealth();
            boss.hurt(level.damageSources().playerAttack(a), 10f);
            float plain = h0 - boss.getHealth();
            d.keystones.add(SkillKeystone.ASH_PACT.id);
            boss.invulnerableTime = 0;
            float h1 = boss.getHealth();
            boss.hurt(level.damageSources().playerAttack(a), 10f);
            float pact = h1 - boss.getHealth();
            double wantBroken = SkillRules.KEY_ASH_BROKEN_DAMAGE * (1 + SkillRules.KEY_ASH_DAMAGE);
            if (!BreakGauge.broken(boss) || plain <= 0 || Math.abs(pact / plain - wantBroken) > 0.03) {
                f.add("ash pact should deal x" + wantBroken + " to a broken boss (" + plain + " -> " + pact + ")");
            }
            // 鍛え人の目: 満ちた一撃のあと、次の一撃 ×0.7 (偽の人は傷を受けないので、守りの印と使い切りを見る)
            // 上の灰の契りの満ちた一撃で付いた守りを先に使い切る
            jp.revati.skyabyss.rpg.skill.SkillEvents.smithGuard(a, 1f);
            boolean before = jp.revati.skyabyss.rpg.skill.SkillEvents.smithGuarded(a);
            foe.invulnerableTime = 0;
            MeleeHits.during(a, 1f, () -> foe.hurt(level.damageSources().playerAttack(a), 2f));
            boolean guarded = jp.revati.skyabyss.rpg.skill.SkillEvents.smithGuarded(a);
            float soft = jp.revati.skyabyss.rpg.skill.SkillEvents.smithGuard(a, 10f);
            float next = jp.revati.skyabyss.rpg.skill.SkillEvents.smithGuard(a, 10f);
            if (before || !guarded || Math.abs(soft - 10f * SkillRules.SMITH_GUARD) > 0.01f || next != 10f) {
                f.add("smith's eye should soften only the next hit after a full swing to x" + SkillRules.SMITH_GUARD + " (" + before + "/" + guarded + ", " + soft + ", then " + next + ")");
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tree keystones crashed", e);
            f.add("crashed: " + e);
        } finally {
            BossOpenings.plantedForTest = null;
            if (boss != null) {
                BreakGauge.forget(boss);
                boss.discard();
            }
            if (foe != null) {
                foe.discard();
            }
            d.nodesOf(Job.SMITH).removeAll(List.of("ash_pact", "smith_eye", "ember_lung"));
            d.curJob = job;
            d.applyNodes();
        }
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST tree keystones FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tree keystones PASSED (ash pact fills the break gauge x1.5 and hits broken bosses x1.2; smith's eye softens the next hit after a full swing; ember lung counts cd_kill)");
        }
    }
}
