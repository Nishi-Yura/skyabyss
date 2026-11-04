package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.tree.AbilityNode;
import jp.revati.skyabyss.rpg.skill.tree.AbilityTree;
import jp.revati.skyabyss.rpg.skill.tree.AbilityTrees;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// 技の木 v3 の変化 (TreeVariants・SkillActions・JobActions) と魔法使いの変異の自己テスト。段の名前は "tree variants"。
//   六職の木の変化の節をそれぞれ一つだけ取った人に、その技を一度ずつ使わせる: 変化として選ばれている (runeOf がその番号)・
//   技が例外を出さない (結果は成功か、足場・的のない知らせ)。雷雲・凍て海の変異も一度ずつ
public final class TreeVariantsSelfTest {
    private TreeVariantsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("tree variants", TreeVariantsSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
        double px = spawn.getX() + 12.5;
        double py = 300;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "tree_variant_t"));
        p.moveTo(px, py, pz, 0f, 0f);
        net.minecraft.world.entity.monster.Zombie z = new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        z.moveTo(px, py, pz + 3, 180f, 0f);
        z.setNoAi(true);
        z.setNoGravity(true);
        level.addFreshEntity(z);
        int tried = 0;
        try {
            Growth.get(p).level = 50;
            for (Job job : Job.values()) {
                AbilityTree t = AbilityTrees.of(job);
                if (t == null) {
                    continue;
                }
                for (AbilityNode n : t.nodes()) {
                    if (n.kind != AbilityNode.Kind.RUNE && n.kind != AbilityNode.Kind.MUTATION) {
                        continue;
                    }
                    SkillManager.forgetAll(p);
                    SkillData d = SkillManager.get(p);
                    d.useJob(job);
                    d.bonusPoints = 300;
                    for (AbilityNode q : t.pathTo(n.id)) {
                        d.nodesOf(job).add(q.id);
                    }
                    // 変異は技を極めてから
                    if (n.kind == AbilityNode.Kind.MUTATION) {
                        for (AbilityNode r : t.rankNodes(n.skill)) {
                            for (AbilityNode q : t.pathTo(r.id)) {
                                d.nodesOf(job).add(q.id);
                            }
                        }
                        d.mutations.put(n.skill.id, n.mutation.id);
                    }
                    d.applyNodes();
                    if (n.kind == AbilityNode.Kind.RUNE && SkillManager.runeOf(p, n.skill) != n.rune) {
                        f.add(job + "/" + n.id + ": the variant is not in effect (rune " + SkillManager.runeOf(p, n.skill) + ")");
                    }
                    if (n.kind == AbilityNode.Kind.MUTATION && SkillExtOps.active(p, n.skill) != n.mutation) {
                        f.add(job + "/" + n.id + ": the mutation is not in effect");
                    }
                    p.moveTo(px, py, pz, 0f, 0f);
                    z.moveTo(px, py, pz + 3, 180f, 0f);
                    z.setHealth(z.getMaxHealth());
                    try {
                        SkillActions.perform(p, n.skill, d.rank(n.skill));
                        tried++;
                    } catch (RuntimeException ex) {
                        f.add(job + "/" + n.id + ": " + ex);
                        SkyAbyss.LOGGER.error("tree variant {} threw", n.id, ex);
                    }
                }
            }
        } finally {
            SkillManager.forgetAll(p);
            z.discard();
            p.discard();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tree variants PASSED ({} variants and mutations cast once each)", tried);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST tree variants FAILED: {}", s));
        }
    }
}
