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

// 技の木 (docs/design/48。v3 = 2026-11-04) の自己テスト。SKYABYSS_SELFTEST_ONLY="tree"。
//   木の形 (全部の節が幹からつながる・同じ升に二つ置かない・線は真上か同じ行の隣だけ・型が使われている・発動は 4 つで型を持たない・
//     奥義は金色で 3 SP・門は型 15・封じる節は両向き・同じ技の変化どうしは封じ合う・職の木は節 70〜74・SP 126〜132・文がある)
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
        for (AbilityTree t : AbilityTrees.all()) {
            trees++;
            shape(f, t, ja, en);
        }
        check(f, AbilityTrees.of(jp.revati.skyabyss.rpg.skill.SkillTree.DEEP) != null && AbilityTrees.of(jp.revati.skyabyss.rpg.skill.SkillTree.PAST) != null, "the deep / past-life trees did not load");
        check(f, trees >= 1, "no ability tree loaded");
        check(f, SkillRules.pointsForLevel(1) == 1 && SkillRules.pointsForLevel(25) == 25 && SkillRules.pointsForLevel(50) == 50, "one point per level is wrong");
        if (AbilityTrees.of(Job.WARRIOR) != null) {
            play(server, f);
        }
        variants(server, f);
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
    private static void shape(List<String> f, AbilityTree t, JsonObject ja, JsonObject en) {
        String tag = t.key + ": ";
        boolean jobTree = t.job != null;
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
        // 同じ升に二つ置かない。前提は上か横 (下から上へ戻らない)。職の木は縦と横の線だけ (真上か同じ行の隣)
        Set<Long> cells = new HashSet<>();
        for (AbilityNode n : t.nodes()) {
            check(f, cells.add(((long) n.x << 32) | (n.y & 0xffffffffL)), tag + n.id + " shares a cell with another node");
            for (String r : n.requires) {
                AbilityNode rn = t.node(r);
                check(f, rn != null && rn.y <= n.y, tag + n.id + " requires a node below it (" + r + ")");
                if (jobTree && rn != null) {
                    boolean up = rn.x == n.x && rn.y == n.y - 1;
                    boolean side = rn.y == n.y && Math.abs(rn.x - n.x) == 1;
                    check(f, up || side, tag + n.id + " links to " + r + " with a slanted or long line");
                }
            }
            check(f, n.cost >= 1 && n.cost <= 3, tag + n.id + " cost out of range");
            if (jobTree && (n.kind == AbilityNode.Kind.KEYSTONE || n.kind == AbilityNode.Kind.TRAIT)) {
                check(f, n.arch != null && n.archPoints == KEYSTONE_GATE && n.cost == 3, tag + n.id + " keystone needs 3 SP and the gate " + KEYSTONE_GATE);
            }
            // 封じる節は両向きで、奥義は封じない
            for (String b : n.blocks) {
                AbilityNode bn = t.node(b);
                check(f, bn != null && bn.blocks.contains(n.id), tag + n.id + " blocks " + b + " one way only");
                check(f, bn != null && n.kind != AbilityNode.Kind.KEYSTONE && n.kind != AbilityNode.Kind.TRAIT
                        && bn.kind != AbilityNode.Kind.KEYSTONE && bn.kind != AbilityNode.Kind.TRAIT, tag + n.id + " blocks a keystone pair " + b);
            }
            // 同じ技の変化どうしは封じ合う
            if (n.kind == AbilityNode.Kind.RUNE) {
                for (AbilityNode o : t.runesOf(n.skill)) {
                    check(f, o == n || n.blocks.contains(o.id), tag + n.id + " does not block " + o.id + " (same skill)");
                }
            }
        }
        // 型: 3 つ以上、どれも使われている
        check(f, !jobTree || t.archetypes.size() >= 3, tag + "fewer than 3 archetypes");
        for (String arch : t.archetypes) {
            int pts = 0;
            for (AbilityNode n : t.nodes()) {
                if (arch.equals(n.arch)) {
                    pts += n.cost;
                }
            }
            check(f, !jobTree || pts >= 20, tag + "archetype " + arch + " has only " + pts + " points");
            check(f, ja.has(t.archKey(arch)) && en.has(t.archKey(arch)), tag + "no lang for archetype " + arch);
        }
        // 節のある技は、段の節が段の上限まである。発動は 4 つ (枠の数) で、どの型からも取れる (型を持たない)。
        // 発動から外した技 (霧隠れ・盾殴りなど) は節を持たず、残った技の変化 (TreeVariants) として動く
        for (Skill s : Skill.inTree(t.tree)) {
            AbilityNode base = t.skillNode(s);
            if (base != null) {
                AbilityNode.Kind want = s.keystone() ? AbilityNode.Kind.KEYSTONE : s.active ? AbilityNode.Kind.ACTIVE : AbilityNode.Kind.PASSIVE;
                check(f, base.kind == want, tag + "skill node " + s.id + " is " + base.kind + ", expected " + want);
                check(f, t.rankNodes(s).size() == s.maxRank - 1, tag + s.id + " should have " + (s.maxRank - 1) + " rank nodes, has " + t.rankNodes(s).size());
                check(f, t.runesOf(s).size() <= 4, tag + s.id + " has more than 4 rune nodes");
            }
        }
        if (jobTree) {
            check(f, t.actives().size() == SkillRules.BASE_SLOTS, tag + t.actives().size() + " active skills (want " + SkillRules.BASE_SLOTS + ")");
            for (AbilityNode a : t.actives()) {
                check(f, a.arch == null, tag + "active " + a.id + " belongs to an archetype");
            }
            check(f, t.nodes().size() >= 70 && t.nodes().size() <= 74, tag + t.nodes().size() + " nodes (want 70-74)");
            check(f, t.totalCost >= 126 && t.totalCost <= 132, tag + t.totalCost + " SP (want 126-132)");
        }
        // 変異の節: 変異のある技にはその節があり、段の節の先にある
        for (jp.revati.skyabyss.rpg.skill.Mutation m : jp.revati.skyabyss.rpg.skill.Mutation.values()) {
            if (m.skill.tree == t.tree) {
                check(f, t.mutationNode(m.skill) != null, tag + "no mutation node for " + m.id);
            }
        }
        // 文: 技でない節は node.skyabyss.<職>.<id> と .effect
        for (AbilityNode n : t.nodes()) {
            if (n.kind == AbilityNode.Kind.EFFECT || n.kind == AbilityNode.Kind.THRESHOLD) {
                check(f, ja.has(n.nameKey(t.key)) && en.has(n.nameKey(t.key)), tag + "no name for node " + n.id);
                check(f, ja.has(n.effectKey(t.key)) && en.has(n.effectKey(t.key)), tag + "no effect text for node " + n.id);
            }
        }
    }

    // 奥義の門 (その型の節に使う SP。全職同じ。docs/design/48 の 4 章)
    static final int KEYSTONE_GATE = 15;

    private static boolean take(FakePlayer p, String id, List<String> f) {
        SkillData.TakeResult r = SkillManager.takeNode(p, id);
        check(f, r == SkillData.TakeResult.OK, "taking " + id + " failed: " + r);
        return r == SkillData.TakeResult.OK;
    }

    // 取った節から、封じられた節と変異 (目当てでなければ) を避けて、id までのいちばん短い道を取る
    private static void takePath(FakePlayer p, AbilityTree t, String id, List<String> f) {
        takePathAvoiding(p, t, id, List.of(), f);
    }

    private static void takePathAvoiding(FakePlayer p, AbilityTree t, String id, java.util.Collection<String> avoid, List<String> f) {
        SkillData d = SkillManager.get(p);
        Set<String> mine = d.nodesOf(t.tree);
        java.util.Map<String, String> parent = new java.util.HashMap<>();
        ArrayDeque<AbilityNode> queue = new ArrayDeque<>();
        for (AbilityNode n : t.nodes()) {
            if (mine.contains(n.id) || n.requires.isEmpty()) {
                parent.put(n.id, null);
                queue.add(n);
            }
        }
        while (!queue.isEmpty() && !parent.containsKey(id)) {
            AbilityNode cur = queue.poll();
            for (AbilityNode n : t.nodes()) {
                // 目当てでない変化・変異は通らない (同じ技の変化は一つだけ)。道の上の節と封じ合う節も通らない
                boolean side = (n.kind == AbilityNode.Kind.MUTATION || n.kind == AbilityNode.Kind.RUNE) && !n.id.equals(id);
                if (parent.containsKey(n.id) || !n.requires.contains(cur.id) || side || avoid.contains(n.id)) {
                    continue;
                }
                java.util.Set<String> chain = new java.util.HashSet<>(mine);
                for (String at = cur.id; at != null; at = parent.get(at)) {
                    chain.add(at);
                }
                if (n.blocks.stream().anyMatch(chain::contains) || t.nodes().stream().anyMatch(o -> chain.contains(o.id) && o.blocks.contains(n.id))) {
                    continue;
                }
                parent.put(n.id, cur.id);
                queue.add(n);
            }
        }
        List<String> path = new ArrayList<>();
        for (String at = id; at != null && parent.containsKey(at); at = parent.get(at)) {
            path.add(at);
        }
        check(f, parent.containsKey(id), "no open path to " + id);
        java.util.Collections.reverse(path);
        for (String n : path) {
            if (!d.hasNode(t.job, n)) {
                take(p, n, f);
            }
        }
    }

    // ---- 変化の節: どの職の木でも、変化の節を取ると、その技の変化として効く (2026-11-08 の声「スキル変化とってるのに、スキル変化していない」)
    // 職を替えて、Lv 50 で節までの道を取り、SkillManager.runeOf がその番号を返すか。取ったあと読み直しても (保存・型の切り替え) 残るか
    private static void variants(MinecraftServer server, List<String> f) {
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "tree_v"));
        int checked = 0;
        for (Job job : Job.values()) {
            AbilityTree t = AbilityTrees.of(job);
            if (t == null) {
                continue;
            }
            for (AbilityNode n : t.nodes()) {
                if (n.kind != AbilityNode.Kind.RUNE) {
                    continue;
                }
                SkillManager.forgetAll(p);
                SkillData d = SkillManager.get(p);
                d.useJob(job);
                Growth.get(p).level = 50;
                // 先にその技そのものを覚え、変化と封じ合う節は通らずに変化の節へ
                for (AbilityNode a : t.nodes()) {
                    if (a.kind == AbilityNode.Kind.ACTIVE && a.skill == n.skill) {
                        takePath(p, t, a.id, f);
                    }
                }
                takePathAvoiding(p, t, n.id, n.blocks, f);
                int got = SkillManager.runeOf(p, n.skill);
                check(f, got == n.rune, job.id + ": the variant node " + n.id + " (" + n.skill.id + " #" + n.rune + ") did not change the skill (runeOf " + got
                        + ", knows " + d.knows(n.skill) + ", rune " + d.rune(n.skill) + ")");
                // 型に保存して読み直しても変わらない
                SkillManager.saveLoadout(p, 0);
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                SkillManager.loadLoadout(p, 0);
                check(f, SkillManager.runeOf(p, n.skill) == n.rune, job.id + ": the variant " + n.id + " was lost after reloading the loadout");
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                checked++;
            }
        }
        check(f, checked >= 30, "too few variant nodes were checked: " + checked);
        SkyAbyss.LOGGER.info("SELFTEST tree variants: {} variant nodes checked", checked);
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
            check(f, SkillManager.takeNode(p, "str_1") == SkillData.TakeResult.REQUIRES, "a branch node was taken before the root");
            check(f, SkillManager.takeNode(p, "no_such_node") == SkillData.TakeResult.UNKNOWN, "an unknown node was taken");
            take(p, "titan_cleave", f);
            check(f, d.rank(Skill.TITAN_CLEAVE) == 1 && d.nodeSpent(Job.WARRIOR) == 1 && Skill.TITAN_CLEAVE.id.equals(d.slots[0]),
                    "the root skill node did not give rank 1 and a slot: rank " + d.rank(Skill.TITAN_CLEAVE) + " slots " + String.join(",", d.slots));
            check(f, SkillManager.takeNode(p, "titan_cleave") == SkillData.TakeResult.TAKEN, "a node was taken twice");
            check(f, d.canLearn(Skill.TITAN_CLEAVE, 10) == SkillData.LearnResult.SEALED, "a job-tree skill can still be learned outside the tree");
            // 基礎は節から
            take(p, "str_1", f);
            check(f, d.statsFromNodes(Job.WARRIOR)[Stat.STRENGTH.ordinal()] == 4 && Growth.get(p).stat(Stat.STRENGTH) == 4,
                    "a stat node did not raise strength: " + Growth.get(p).stat(Stat.STRENGTH));
            // 常時スキルは発動②の横 (線は縦と横だけ。発動②を取る前は取れない)
            check(f, SkillManager.takeNode(p, "riposte") == SkillData.TakeResult.REQUIRES, "a side node was taken before its neighbour");
            take(p, "shield_rush", f);
            take(p, "riposte", f);
            take(p, "stone_stance", f);
            check(f, d.activeCount() == 2 && d.rank(Skill.RIPOSTE) == 1 && d.rank(Skill.STONE_STANCE) == 1, "passive / second active nodes are wrong");
            check(f, d.nodeSpent(Job.WARRIOR) == 8 && d.unspentFor(Job.WARRIOR.tree, 10) == 2, "spent points are wrong: " + d.nodeSpent(Job.WARRIOR));
            take(p, "vit_2", f);
            take(p, "cheap_shield_rush", f);
            check(f, SkillManager.takeNode(p, "str_4") == SkillData.TakeResult.NO_POINTS, "a node was taken without points");
            // ほかの職の点は別
            check(f, d.unspentFor(Job.ARCHER.tree, 10) == 10, "warrior points were taken from the archer");
            // 段の節
            Growth.get(p).level = 50;
            takePath(p, t, "titan_cleave_2", f);
            check(f, d.rank(Skill.TITAN_CLEAVE) == 2, "a rank node did not raise the rank");
            // 変化の節は同じ技で一つ
            takePath(p, t, "heavy_voice", f);
            check(f, d.rune(Skill.ROCK_CALL) == t.node("heavy_voice").rune, "a rune node did not set the rune");
            check(f, SkillManager.takeNode(p, "long_voice") == SkillData.TakeResult.EXCLUDED, "two runes of one skill were taken");
            // 封じる節 (型どうし): 盾の壁を取ると捨て身は取れない (逆も)
            takePath(p, t, "shield_wall", f);
            for (String r : t.node("reckless").requires) {
                if (t.node(r).kind != AbilityNode.Kind.RUNE && !d.hasNode(Job.WARRIOR, r)) {
                    takePath(p, t, r, f);
                    break;
                }
            }
            check(f, d.canTake(Job.WARRIOR, t.node("reckless"), 50) == SkillData.TakeResult.EXCLUDED, "a blocked node was taken: " + d.canTake(Job.WARRIOR, t.node("reckless"), 50));
            // 数は木の節から読む
            double wallTaken = t.node("shield_wall").effect.get("damage_taken");
            check(f, Math.abs(NodeEffects.sum(p, "damage_taken") - wallTaken) < 1e-9, "effect nodes do not sum: damage taken " + NodeEffects.sum(p, "damage_taken"));
            // 閾値: 重い一撃は筋力が at に届いたときだけ効く
            double heavySpeed = t.node("heavy_blow").effect.get("attack_speed");
            int heavyAt = t.node("heavy_blow").at;
            takePath(p, t, "heavy_blow", f);
            double speedBase = NodeEffects.sum(p, "attack_speed") - (Growth.get(p).stat(Stat.STRENGTH) >= heavyAt ? heavySpeed : 0);
            check(f, Math.abs(speedBase) < 1e-9, "unexpected attack speed from other nodes: " + speedBase);
            boolean reached = Growth.get(p).stat(Stat.STRENGTH) >= heavyAt;
            check(f, Math.abs(NodeEffects.sum(p, "attack_speed") - (reached ? heavySpeed : 0)) < 1e-9,
                    "heavy blow did not follow strength " + heavyAt + ": str " + Growth.get(p).stat(Stat.STRENGTH) + " aspeed " + NodeEffects.sum(p, "attack_speed"));
            // 幹と剣士の型の筋力の節を、浅い (奥義より上の) 順に取る
            List<AbilityNode> strNodes = new ArrayList<>();
            for (AbilityNode n : t.nodes()) {
                if (n.kind == AbilityNode.Kind.STAT && n.stat == Stat.STRENGTH && (n.arch == null || "cleaver".equals(n.arch)) && n.y <= 8) {
                    strNodes.add(n);
                }
            }
            strNodes.sort(java.util.Comparator.comparingInt(n -> n.y));
            for (AbilityNode n : strNodes) {
                if (!d.hasNode(Job.WARRIOR, n.id) && Growth.get(p).stat(Stat.STRENGTH) < heavyAt) {
                    takePath(p, t, n.id, f);
                }
            }
            check(f, Growth.get(p).stat(Stat.STRENGTH) >= heavyAt && Math.abs(NodeEffects.sum(p, "attack_speed") - heavySpeed) < 1e-9,
                    "a threshold node did not fire at its stat: str " + Growth.get(p).stat(Stat.STRENGTH) + " aspeed " + NodeEffects.sum(p, "attack_speed"));
            // 型の点: 守り石の約束は盾の型に 15 SP
            check(f, d.archPoints(Job.WARRIOR, "shield") < KEYSTONE_GATE && SkillManager.takeNode(p, "stone_oath") != SkillData.TakeResult.OK,
                    "a keystone opened without archetype points: " + d.archPoints(Job.WARRIOR, "shield"));
            // 使う技は 4 本まで
            int before = d.activeCount();
            List<AbilityNode> actives = new ArrayList<>(t.actives());
            AbilityNode fifth = null;
            for (AbilityNode a : actives) {
                if (d.hasNode(Job.WARRIOR, a.id)) {
                    continue;
                }
                if (d.activeCount() >= SkillRules.BASE_SLOTS) {
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
            check(f, d.activeCount() == SkillRules.BASE_SLOTS && fifth == null, "could not reach four actives from " + before);
            // 変異の節: 極めてから、素材を払って。払えば振り直しのあとも ただ
            // ここから先は取った節の点が Lv 50 の点を越えるので (手本の形で節の点が上がった)、外されないよう点を足しておく (保存にも乗る)
            d.bonusPoints = 200;
            takePath(p, t, "titan_cleave_3", f);
            AbilityNode mut = t.mutationNode(Skill.TITAN_CLEAVE);
            check(f, mut != null && d.rank(Skill.TITAN_CLEAVE) >= Skill.TITAN_CLEAVE.maxRank, "no titan cleave mutation node or not mastered");
            if (mut != null) {
                for (AbilityNode n : t.pathTo(mut.id)) {
                    if (n != mut && !d.hasNode(Job.WARRIOR, n.id)) {
                        d.nodesOf(Job.WARRIOR).add(n.id);
                    }
                }
                d.applyNodes();
                check(f, SkillManager.takeNode(p, mut.id) == SkillData.TakeResult.NO_MATERIAL, "a mutation was taken without materials");
                p.getInventory().add(new net.minecraft.world.item.ItemStack(mut.mutation.coreItem(), 1));
                p.getInventory().add(new net.minecraft.world.item.ItemStack(mut.mutation.shardItem(), 3));
                check(f, SkillManager.takeNode(p, mut.id) == SkillData.TakeResult.OK && mut.mutation.id.equals(d.mutations.get(Skill.TITAN_CLEAVE.id))
                        && jp.revati.skyabyss.rpg.skill.SkillExtOps.active(p, Skill.TITAN_CLEAVE) == mut.mutation
                        && jp.revati.skyabyss.rpg.skill.SkillExtOps.count(p, mut.mutation.coreItem()) == 0, "taking the mutation node did not pay and mutate");
                d.nodesOf(Job.WARRIOR).remove(mut.id);
                d.applyNodes();
                check(f, jp.revati.skyabyss.rpg.skill.SkillExtOps.active(p, Skill.TITAN_CLEAVE) == null, "a mutation stayed active without its node");
                check(f, SkillManager.takeNode(p, mut.id) == SkillData.TakeResult.OK, "retaking a paid mutation was not free");
            }
            // 淵・前世の木 (職をまたぐ): 転生の回数で開き、前世は別の点。使う技の 4 本は職の木と合わせて数える
            AbilityTree deep = AbilityTrees.of(jp.revati.skyabyss.rpg.skill.SkillTree.DEEP);
            AbilityTree past = AbilityTrees.of(jp.revati.skyabyss.rpg.skill.SkillTree.PAST);
            if (deep != null && past != null) {
                // 転生の回数は記録へ写されるので、見せかけの出どころを差し込む (終わったら本物へ戻す)
                jp.revati.skyabyss.rebirth.RebirthView.useSource(pl -> 0);
                d.rebirth = 0;
                check(f, d.canTake(jp.revati.skyabyss.rpg.skill.SkillTree.DEEP, deep.skillNode(Skill.DEEP_PULL), 50) == SkillData.TakeResult.REBIRTH, "the deep opened without a rebirth");
                jp.revati.skyabyss.rebirth.RebirthView.useSource(pl -> 1);
                d.rebirth = 1;
                check(f, d.canTake(jp.revati.skyabyss.rpg.skill.SkillTree.DEEP, deep.skillNode(Skill.DEEP_PULL), 50) == SkillData.TakeResult.ACTIVES_FULL, "a deep active did not count against the four");
                check(f, d.canTake(jp.revati.skyabyss.rpg.skill.SkillTree.DEEP, deep.skillNode(Skill.DEEP_MIRROR), 50) == SkillData.TakeResult.OK, "the deep passive did not open at rebirth 1");
                check(f, SkillManager.takeNode(p, "deep", "deep_mirror") == SkillData.TakeResult.OK && d.rank(Skill.DEEP_MIRROR) == 1 && d.sharedSpent() >= 1, "taking a deep node failed");
                check(f, d.canTake(jp.revati.skyabyss.rpg.skill.SkillTree.PAST, past.skillNode(Skill.OLD_VIGIL), 50) == SkillData.TakeResult.OK, "a past-life passive did not open at rebirth 1");
                check(f, SkillManager.takeNode(p, "past", "old_vigil") == SkillData.TakeResult.OK && d.pastSpent() == 1 && d.pastLeft() == d.pastPool() - 1, "taking a past node failed");
                check(f, d.canTake(jp.revati.skyabyss.rpg.skill.SkillTree.PAST, past.skillNode(Skill.GRAVE_BELL), 50) == SkillData.TakeResult.REBIRTH, "grave bell opened at rebirth 1");
                check(f, SkillManager.takeNode(p, "warrior", "deep_mirror") == SkillData.TakeResult.UNKNOWN, "a deep node was taken through the job tree");
                d.rebirth = 0;
                d.enforceRebirth();
                check(f, d.nodesOf(jp.revati.skyabyss.rpg.skill.SkillTree.DEEP).isEmpty() && d.rank(Skill.DEEP_MIRROR) == 0 && d.nodesOf(jp.revati.skyabyss.rpg.skill.SkillTree.PAST).isEmpty(),
                        "lowering rebirth did not return deep / past nodes");
                jp.revati.skyabyss.rebirth.RebirthView.useSource(jp.revati.skyabyss.rebirth.Rebirth::displayCount);
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
