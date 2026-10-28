package jp.revati.skyabyss.rpg.skill.tree;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillBuild;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;

// 技の木 v2 (docs/design/48) の自己テスト。SKYABYSS_SELFTEST_ONLY="tree"。
//   木の形 (全部の節が幹からつながる・同じ升に二つ置かない・型が使われている・職の木の技がみな節にある・文がある)
//   取る決まり (前提・排他・刻印は一つ・型の点・使う技は 4 本・点)・基礎は節から・職を替えると基礎も替わる・振り直し・検め・保存と同期
public final class AbilityTreeSelfTest {
    private AbilityTreeSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        AbilityTrees.load();
        JsonObject ja = lang("ja_jp");
        JsonObject en = lang("en_us");
        int trees = 0;
        for (Job job : Job.values()) {
            AbilityTree t = AbilityTrees.of(job);
            if (t == null) {
                continue;
            }
            trees++;
            shape(f, job, t, ja, en);
        }
        check(f, trees >= 1, "no ability tree loaded");
        check(f, SkillRules.pointsForLevel(1) == 1 && SkillRules.pointsForLevel(25) == 25 && SkillRules.pointsForLevel(50) == 50, "one point per level is wrong");
        if (AbilityTrees.of(Job.WARRIOR) != null) {
            play(server, f);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tree PASSED ({} trees: shape, lang, take rules, four actives, stats from nodes, job switch, reset, sanitize, save, sync)", trees);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST tree FAILED: {}", s));
        }
    }

    private static JsonObject lang(String code) {
        try (var in = AbilityTreeSelfTest.class.getResourceAsStream("/assets/" + SkyAbyss.MODID + "/lang/" + code + ".json")) {
            return in == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    // ---- 木の形
    private static void shape(List<String> f, Job job, AbilityTree t, JsonObject ja, JsonObject en) {
        String tag = job.id + ": ";
        // 幹からつながる
        Set<String> seen = new HashSet<>();
        ArrayDeque<AbilityNode> queue = new ArrayDeque<>();
        for (AbilityNode n : t.nodes()) {
            if (n.requires.isEmpty()) {
                queue.add(n);
                seen.add(n.id);
            }
        }
        check(f, !queue.isEmpty(), tag + "no root node");
        while (!queue.isEmpty()) {
            AbilityNode cur = queue.poll();
            for (AbilityNode n : t.nodes()) {
                if (!seen.contains(n.id) && n.requires.contains(cur.id)) {
                    seen.add(n.id);
                    queue.add(n);
                }
            }
        }
        for (AbilityNode n : t.nodes()) {
            check(f, seen.contains(n.id), tag + n.id + " is not connected to the root");
        }
        // 同じ升に二つ置かない。前提は上か横 (下から上へ戻らない)
        Set<Long> cells = new HashSet<>();
        for (AbilityNode n : t.nodes()) {
            check(f, cells.add(((long) n.x << 32) | (n.y & 0xffffffffL)), tag + n.id + " shares a cell with another node");
            for (String r : n.requires) {
                AbilityNode rn = t.node(r);
                check(f, rn != null && rn.y <= n.y, tag + n.id + " requires a node below it (" + r + ")");
            }
            check(f, n.cost >= 1 && n.cost <= 3, tag + n.id + " cost out of range");
            if (n.kind == AbilityNode.Kind.KEYSTONE || n.kind == AbilityNode.Kind.TRAIT) {
                check(f, n.arch != null && n.archPoints >= 10, tag + n.id + " keystone needs an archetype gate");
            }
        }
        // 型: 3 つ以上、どれも使われている
        check(f, t.archetypes.size() >= 3, tag + "fewer than 3 archetypes");
        for (String arch : t.archetypes) {
            int pts = 0;
            for (AbilityNode n : t.nodes()) {
                if (arch.equals(n.arch)) {
                    pts += n.cost;
                }
            }
            check(f, pts >= 20, tag + "archetype " + arch + " has only " + pts + " points");
            check(f, ja.has(t.archKey(arch)) && en.has(t.archKey(arch)), tag + "no lang for archetype " + arch);
        }
        // 職の木の技はみな節にある (使う技は ACTIVE、常に効く技は PASSIVE、要は KEYSTONE)。使う技は 4 本より多い (選べるように)
        for (Skill s : Skill.inTree(job.tree)) {
            AbilityNode base = t.skillNode(s);
            check(f, base != null, tag + "skill " + s.id + " has no node");
            if (base != null) {
                AbilityNode.Kind want = s.keystone() ? AbilityNode.Kind.KEYSTONE : s.active ? AbilityNode.Kind.ACTIVE : AbilityNode.Kind.PASSIVE;
                check(f, base.kind == want, tag + "skill node " + s.id + " is " + base.kind + ", expected " + want);
                check(f, t.rankNodes(s).size() == s.maxRank - 1, tag + s.id + " should have " + (s.maxRank - 1) + " rank nodes, has " + t.rankNodes(s).size());
                check(f, t.runesOf(s).size() <= 3, tag + s.id + " has more than 3 rune nodes");
            }
        }
        check(f, t.actives().size() > SkillRules.BASE_SLOTS, tag + "only " + t.actives().size() + " active skills (need more than " + SkillRules.BASE_SLOTS + " to choose)");
        check(f, t.totalCost >= 60, tag + "tree is small: " + t.totalCost + " points");
        // 文: 技でない節は node.skyabyss.<職>.<id> と .effect
        for (AbilityNode n : t.nodes()) {
            if (n.kind == AbilityNode.Kind.EFFECT || n.kind == AbilityNode.Kind.THRESHOLD) {
                check(f, ja.has(n.nameKey(job.id)) && en.has(n.nameKey(job.id)), tag + "no name for node " + n.id);
                check(f, ja.has(n.effectKey(job.id)) && en.has(n.effectKey(job.id)), tag + "no effect text for node " + n.id);
            }
        }
    }

    private static boolean take(FakePlayer p, String id, List<String> f) {
        SkillData.TakeResult r = SkillManager.takeNode(p, id);
        check(f, r == SkillData.TakeResult.OK, "taking " + id + " failed: " + r);
        return r == SkillData.TakeResult.OK;
    }

    private static void takePath(FakePlayer p, AbilityTree t, String id, List<String> f) {
        SkillData d = SkillManager.get(p);
        for (AbilityNode n : t.pathTo(id)) {
            if (!d.hasNode(t.job, n.id)) {
                take(p, n.id, f);
            }
        }
    }

    // ---- 取る決まり (戦士の木)
    private static void play(MinecraftServer server, List<String> f) {
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "tree_t"));
        AbilityTree t = AbilityTrees.of(Job.WARRIOR);
        try {
            SkillManager.forgetAll(p);
            SkillData d = SkillManager.get(p);
            d.useJob(Job.WARRIOR);
            Growth.get(p).level = 10;
            check(f, d.totalPoints(10) == 10 && d.unspentFor(Job.WARRIOR.tree, 10) == 10, "a level 10 warrior should have 10 points");
            check(f, SkillManager.takeNode(p, "str_a") == SkillData.TakeResult.REQUIRES, "a branch node was taken before the root");
            check(f, SkillManager.takeNode(p, "no_such_node") == SkillData.TakeResult.UNKNOWN, "an unknown node was taken");
            take(p, "titan_cleave", f);
            check(f, d.rank(Skill.TITAN_CLEAVE) == 1 && d.nodeSpent(Job.WARRIOR) == 1 && Skill.TITAN_CLEAVE.id.equals(d.slots[0]),
                    "the root skill node did not give rank 1 and a slot: rank " + d.rank(Skill.TITAN_CLEAVE) + " slots " + String.join(",", d.slots));
            check(f, SkillManager.takeNode(p, "titan_cleave") == SkillData.TakeResult.TAKEN, "a node was taken twice");
            check(f, d.canLearn(Skill.TITAN_CLEAVE, 10) == SkillData.LearnResult.SEALED, "a job-tree skill can still be learned outside the tree");
            // 基礎は節から
            take(p, "str_a", f);
            check(f, d.statsFromNodes(Job.WARRIOR)[Stat.STRENGTH.ordinal()] == 4 && Growth.get(p).stat(Stat.STRENGTH) == 4,
                    "a stat node did not raise strength: " + Growth.get(p).stat(Stat.STRENGTH));
            take(p, "vit_a", f);
            take(p, "riposte", f);
            take(p, "stone_stance", f);
            take(p, "shield_rush", f);
            check(f, d.activeCount(Job.WARRIOR) == 2 && d.rank(Skill.RIPOSTE) == 1 && d.rank(Skill.STONE_STANCE) == 1, "passive / second active nodes are wrong");
            check(f, d.nodeSpent(Job.WARRIOR) == 9 && d.unspentFor(Job.WARRIOR.tree, 10) == 1, "spent points are wrong: " + d.nodeSpent(Job.WARRIOR));
            take(p, "str_b", f);
            check(f, SkillManager.takeNode(p, "agi_a") == SkillData.TakeResult.NO_POINTS, "a node was taken without points");
            // ほかの職の点は別
            check(f, d.unspentFor(Job.ARCHER.tree, 10) == 10, "warrior points were taken from the archer");
            // 段の節
            Growth.get(p).level = 50;
            take(p, "titan_cleave_2", f);
            check(f, d.rank(Skill.TITAN_CLEAVE) == 2, "a rank node did not raise the rank");
            // 刻印の節は同じ技で一つ
            takePath(p, t, "heavy_voice", f);
            check(f, d.rune(Skill.ROCK_CALL) == t.node("heavy_voice").rune, "a rune node did not set the rune");
            check(f, SkillManager.takeNode(p, "long_voice") == SkillData.TakeResult.EXCLUDED, "two runes of one skill were taken");
            // 排他
            takePath(p, t, "dull_iron", f);
            check(f, SkillManager.takeNode(p, "quick_iron") == SkillData.TakeResult.EXCLUDED, "an excluded node was taken");
            check(f, Math.abs(NodeEffects.sum(p, "attack") - 0.12) < 1e-9 && Math.abs(NodeEffects.sum(p, "attack_speed") + 0.10) < 1e-9,
                    "effect nodes do not sum: attack " + NodeEffects.sum(p, "attack"));
            check(f, SkillBuild.generalDamage(p, false) >= 0.12 - 1e-9, "general damage does not include effect nodes: " + SkillBuild.generalDamage(p, false));
            // 閾値: 耐久 24 で岩の肌 (まだ 12)、筋力 24 で重い一撃
            check(f, d.hasNode(Job.WARRIOR, "rock_skin") && Growth.get(p).stat(Stat.VITALITY) < 24 && NodeEffects.sum(p, "armor") == 0,
                    "a threshold node fired below its stat: vit " + Growth.get(p).stat(Stat.VITALITY));
            for (String id : List.of("wide_blade", "big_foe", "str_c", "str_d", "heavy_blow")) {
                take(p, id, f);
            }
            check(f, Growth.get(p).stat(Stat.STRENGTH) < 24 && Math.abs(NodeEffects.sum(p, "attack_speed") + 0.10) < 1e-9,
                    "heavy blow fired below strength 24: str " + Growth.get(p).stat(Stat.STRENGTH));
            for (String id : List.of("titan_cleave_3", "sword_spirit", "deep_wound", "str_e", "shield_rush_2", "str_f")) {
                take(p, id, f);
            }
            check(f, Growth.get(p).stat(Stat.STRENGTH) >= 24 && Math.abs(NodeEffects.sum(p, "attack_speed") + 0.02) < 1e-9,
                    "a threshold node did not fire at its stat: str " + Growth.get(p).stat(Stat.STRENGTH) + " aspeed " + NodeEffects.sum(p, "attack_speed"));
            // 型の点: 守り石の約束は盾の型に 20 点 (前提の耐久は取っておく)
            takePath(p, t, "vit_e", f);
            check(f, d.archPoints(Job.WARRIOR, "shield") < 20 && SkillManager.takeNode(p, "stone_oath") == SkillData.TakeResult.ARCH_POINTS,
                    "a keystone opened without archetype points: " + d.archPoints(Job.WARRIOR, "shield"));
            // 使う技は 4 本まで
            int before = d.activeCount(Job.WARRIOR);
            List<AbilityNode> actives = new ArrayList<>(t.actives());
            AbilityNode fifth = null;
            for (AbilityNode a : actives) {
                if (d.hasNode(Job.WARRIOR, a.id)) {
                    continue;
                }
                if (d.activeCount(Job.WARRIOR) >= SkillRules.BASE_SLOTS) {
                    fifth = a;
                    break;
                }
                for (AbilityNode n : t.pathTo(a.id)) {
                    if (!d.hasNode(Job.WARRIOR, n.id)) {
                        d.nodesOf(Job.WARRIOR).add(n.id);
                    }
                }
                d.applyNodes();
            }
            check(f, d.activeCount(Job.WARRIOR) == SkillRules.BASE_SLOTS, "could not reach four actives from " + before);
            if (fifth != null) {
                for (AbilityNode n : t.pathTo(fifth.id)) {
                    if (n != fifth && !d.hasNode(Job.WARRIOR, n.id)) {
                        d.nodesOf(Job.WARRIOR).add(n.id);
                    }
                }
                d.applyNodes();
                check(f, d.canTake(Job.WARRIOR, fifth, 50) == SkillData.TakeResult.ACTIVES_FULL, "a fifth active skill was allowed: " + fifth.id);
            }
            // 職を替えると基礎も枠もその職のもの
            SkillManager.refreshStats(p);
            int str = Growth.get(p).stat(Stat.STRENGTH);
            d.useJob(Job.ARCHER);
            SkillManager.refreshStats(p);
            check(f, Growth.get(p).stat(Stat.STRENGTH) == 0 && d.nodeSpent(Job.WARRIOR) > 0, "switching job did not swap stats or lost the warrior tree");
            d.useJob(Job.WARRIOR);
            SkillManager.refreshStats(p);
            check(f, Growth.get(p).stat(Stat.STRENGTH) == str && Skill.TITAN_CLEAVE.id.equals(d.slots[0]), "switching back did not restore stats and the bar");
            // 保存と同期
            net.minecraft.nbt.CompoundTag nbt = d.serializeNBT(server.registryAccess());
            SkillData back = new SkillData();
            back.deserializeNBT(server.registryAccess(), nbt);
            check(f, back.nodesOf(Job.WARRIOR).equals(d.nodesOf(Job.WARRIOR)) && back.rank(Skill.TITAN_CLEAVE) == d.rank(Skill.TITAN_CLEAVE), "nodes did not survive the save: " + back.nodesOf(Job.WARRIOR) + " vs " + d.nodesOf(Job.WARRIOR) + " rank " + back.rank(Skill.TITAN_CLEAVE) + "/" + d.rank(Skill.TITAN_CLEAVE));
            net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
            SkillData.STREAM_CODEC.encode(buf, d);
            SkillData synced = SkillData.STREAM_CODEC.decode(buf);
            check(f, synced.nodesOf(Job.WARRIOR).equals(d.nodesOf(Job.WARRIOR)) && synced.curJob.equals(d.curJob), "nodes did not survive the sync codec");
            check(f, d.copyOf().nodesOf(Job.WARRIOR).equals(d.nodesOf(Job.WARRIOR)), "copyOf lost the nodes");
            // 検め: 知らない節・前提の崩れた節は外れる
            SkillData broken = d.copyOf();
            broken.nodesOf(Job.WARRIOR).add("no_such");
            broken.nodesOf(Job.WARRIOR).remove("titan_cleave");
            broken.sanitize(50);
            check(f, broken.nodesOf(Job.WARRIOR).isEmpty() && broken.rank(Skill.TITAN_CLEAVE) == 0, "sanitize kept nodes without their root: " + broken.nodesOf(Job.WARRIOR));
            // 振り直し: 今の職の木だけ戻り、基礎も戻る
            d.nodesOf(Job.ARCHER).add("anything");
            d.clearJob(Job.WARRIOR);
            SkillManager.refreshStats(p);
            check(f, d.nodeSpent(Job.WARRIOR) == 0 && d.rank(Skill.TITAN_CLEAVE) == 0 && Growth.get(p).stat(Stat.STRENGTH) == 0 && d.nodesOf(Job.ARCHER).contains("anything"),
                    "clearing one job's tree touched the wrong things");
            d.nodesOf(Job.ARCHER).clear();
        } finally {
            SkillManager.forgetAll(p);
            p.discard();
        }
    }
}
