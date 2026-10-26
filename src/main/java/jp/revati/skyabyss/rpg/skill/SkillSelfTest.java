package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

// 技の自己テスト。SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く (止めるのは TitanBackSelfTest)。
// 技の点の計算、習得の条件、記録の検め、保存 (NBT・同期・死亡時の引き継ぎ・プレイヤーの保存と読み込み)、
// クールダウン、マナ (回復・払う・足りない)、実際に技を使う (巨神斬り・雲穿ち・天の驟雨)、常時の技の修飾子を確かめる
public final class SkillSelfTest {
    private SkillSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        // 技の規則の検めは、五つの木が全部開いた状態で流す。天職の層は BuildsSelfTest.callings が自分で入れる
        SkillRules.CALLINGS = false;
        try {
            run(server, failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("skill selftest crashed", e);
            failures.add("crashed: " + e);
        } finally {
            SkillRules.CALLINGS = true;
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST skills PASSED (points, learn rules, sanitize, nbt, sync codec, death copy, player save/load, cooldowns, mana, casting, passives)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST skills FAILED: {}", f));
        }
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) {
            failures.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        // ---------------------------------------------------------------- 全技の表の整合 (前提・段・枠・アイコン・翻訳)
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Skill s : Skill.values()) {
            check(f, ids.add(s.id), "duplicate skill id " + s.id);
            check(f, s.maxRank >= 1 && s.iconIndex() >= 0, s.id + " has a bad rank or icon");
            for (Skill r : s.requires()) {
                check(f, r.tree == s.tree && r.tier < s.tier, s.id + " requires a skill that is not in the same tree or an earlier tier: " + r.id);
            }
            // 前提が空の技は 1 段目だけ。前提のある技は、前提を覚えれば覚えられる (橋と前世の技は前提の線なし)
            boolean linked = s.tree != SkillTree.HYBRID && s.tree != SkillTree.PAST;
            check(f, !linked || (s.tier == 0) == s.requires().isEmpty(), s.id + " tier and requirements disagree");
            SkillData d = new SkillData();
            // 淵・前世の技は転生の回数、橋は両隣の木の技 3 つずつ
            d.rebirth = SkillRules.GLYPH_SOCKETS * 3;
            for (SkillTree parent : s.bridgeTrees()) {
                int n = 0;
                for (Skill q : Skill.inTree(parent)) {
                    if (n++ < SkillData.BRIDGE_SKILLS_PER_TREE) {
                        d.ranks.put(q.id, 1);
                    }
                }
            }
            for (Skill r : s.requires()) {
                // 前提の前提も覚えておく (そうでないと検めで全部戻る)
                for (Skill q : Skill.inTree(s.tree)) {
                    if (q.tier < r.tier) {
                        d.ranks.put(q.id, 1);
                    }
                }
                d.ranks.put(r.id, 1);
            }
            d.bonusPoints = 10;
            // 旅人と橋の木は 43 (6 職) で閉じた: 覚えられない
            if (s.tree == SkillTree.TRAVELER || s.tree == SkillTree.HYBRID) {
                check(f, d.canLearn(s, 50) == SkillData.LearnResult.SEALED, s.id + " (a closed tree) can still be learned");
                continue;
            }
            check(f, d.canLearn(s, 50) == SkillData.LearnResult.OK, s.id + " cannot be learned with its requirements");
            d.ranks.put(s.id, 1);
            SkillManager.autoSlot(d, s);
            boolean slotted = java.util.Arrays.asList(d.slots).contains(s.id);
            check(f, slotted == s.active, s.id + " slotting disagrees with active=" + s.active);
            d.sanitize(50);
            check(f, java.util.Arrays.asList(d.slots).contains(s.id) == s.active, s.id + " slot changed by sanitize");
        }
        // ---------------------------------------------------------------- 技の点・木の形
        check(f, SkillRules.pointsForLevel(1) == 1 && SkillRules.pointsForLevel(2) == 1 && SkillRules.pointsForLevel(3) == 2
                && SkillRules.pointsForLevel(9) == 5 && SkillRules.pointsForLevel(10) == 5 && SkillRules.pointsForLevel(12) == 7
                && SkillRules.pointsForLevel(15) == 10 && SkillRules.pointsForLevel(21) == 16 && SkillRules.pointsForLevel(33) == 28
                && SkillRules.pointsForLevel(50) == 45, "skill points per level are wrong");
        // 53 本 + 職の新しい技 10 本 (docs/design/43: 星読みの 6 と、鍛冶師・暗殺者・呪術師 2 の 4 つ目)
        check(f, Skill.values().length == 65 && Rune.values().length == 62, "skill / rune counts are wrong");
        int[] perTree = new int[SkillTree.values().length];
        for (Skill s : Skill.values()) {
            perTree[s.tree.ordinal()]++;
            boolean linked = s.tree != SkillTree.HYBRID && s.tree != SkillTree.PAST;
            check(f, !linked || (s.tier == 0 ? s.requires().isEmpty() : !s.requires().isEmpty()), s.id + " requirement shape is wrong");
            check(f, s.active == (SkillRules.cooldownTicks(s, 1) > 0), s.id + " cooldown does not match its kind");
            check(f, s.keystone() == (s.tier == 3) && (!s.keystone() || s.maxRank == 1), s.id + " keystone shape is wrong");
            check(f, s.ordinal() < 36 ? s.iconIndex() == s.ordinal() : s.iconIndex() >= 100 && s.iconIndex() < 148 && (s.iconIndex() < 117 || s.iconIndex() >= 125),
                    s.id + " icon index");
            if (s.active && s != Skill.SIGNAL) {
                check(f, SkillRules.manaCost(s, 1) > 0, s.id + " has no mana cost");
            }
            for (int r = 1; r <= s.maxRank; r++) {
                check(f, SkillRules.describeArgs(s, r).length > 0, s.id + " has no description args");
            }
        }
        for (SkillTree t : SkillTree.values()) {
            // 3 行目の左右に職の 4 つ目の技: 岩守り・風読み・灰の鍛え人・骨の射手 +1、潮歌い +2
            int want = t == SkillTree.HYBRID ? 5 : t == SkillTree.ROCK || t == SkillTree.WIND || t == SkillTree.ASH || t == SkillTree.BONE ? 7
                    : t == SkillTree.TIDE ? 8 : 6;
            check(f, t.sealed || perTree[t.ordinal()] == want, t.id + " does not have the right number of skills: " + perTree[t.ordinal()]);
        }
        // 1 本の木の総点 (段 + 刻印 1 点ずつ)。Lv 50 の 45 点では 3 本は極められない
        int[] treePoints = new int[SkillTree.values().length];
        for (Skill s : Skill.values()) {
            treePoints[s.tree.ordinal()] += s.maxRank + (s.runes().isEmpty() ? 0 : 1);
        }
        int rockPts = treePoints[SkillTree.ROCK.ordinal()];
        check(f, rockPts >= 12 && rockPts <= 20 && rockPts * 3 > SkillRules.pointsForLevel(50), "tree points out of range: " + rockPts);
        SkyAbyss.LOGGER.info("[selftest] skills: points per tree {}, points at 50: {}", java.util.Arrays.toString(treePoints), SkillRules.pointsForLevel(50));

        // ---------------------------------------------------------------- 習得の条件
        SkillData d = new SkillData();
        check(f, d.canLearn(Skill.TITAN_CLEAVE, 1) == SkillData.LearnResult.OK, "level 1 cannot learn titan cleave");
        check(f, d.canLearn(Skill.LUNGE, 1) == SkillData.LearnResult.OK, "lunge (row 1 of wind) was refused at level 1");
        check(f, d.canLearn(Skill.BACKSTEP, 1) == SkillData.LearnResult.LEVEL, "backstep at level 1 was not blocked by level");
        check(f, d.canLearn(Skill.BACKSTEP, 5) == SkillData.LearnResult.REQUIRES, "backstep without lunge was not blocked");
        d.ranks.put(Skill.TITAN_CLEAVE.id, 1);
        check(f, d.canLearn(Skill.TITAN_CLEAVE, 1) == SkillData.LearnResult.NO_POINTS, "a second point at level 1 was allowed");
        check(f, d.canLearn(Skill.ROCK_CALL, 5) == SkillData.LearnResult.OK, "rock call at level 5 with titan cleave was refused");
        d.ranks.put(Skill.TITAN_CLEAVE.id, 3);
        check(f, d.canLearn(Skill.TITAN_CLEAVE, 50) == SkillData.LearnResult.MAX_RANK, "rank over the max was allowed");
        // 奥義は 2 行目のどちらか一つで足りる
        d.ranks.put(Skill.STONE_STANCE.id, 1);
        d.ranks.put(Skill.RIPOSTE.id, 1);
        check(f, d.canLearn(Skill.WHALE_SWEEP, 12) == SkillData.LearnResult.OK, "whale sweep with stone stance only was refused");
        check(f, d.canLearn(Skill.WHALE_SWEEP, 11) == SkillData.LearnResult.LEVEL, "whale sweep below level 12 was allowed");
        d.ranks.put(Skill.WHALE_SWEEP.id, 1);
        check(f, d.canLearn(Skill.STONE_OATH, 19) == SkillData.LearnResult.LEVEL && d.canLearn(Skill.STONE_OATH, 20) == SkillData.LearnResult.NO_POINTS
                || d.canLearn(Skill.STONE_OATH, 20) == SkillData.LearnResult.OK, "the keystone level gate is wrong");
        // 星読みは魔法使いの木 (docs/design/43) になり、はじめから開いている
        check(f, d.treeOpen(SkillTree.STAR) && !d.treeOpen(SkillTree.ABYSS) && d.treeOpen(SkillTree.ROCK), "sealed trees are open (or the mage tree is closed)");
        d.unlockedTrees.add(SkillTree.ABYSS.id);
        check(f, d.treeOpen(SkillTree.ABYSS), "an unlocked tree stayed sealed");
        d.unlockedTrees.remove(SkillTree.ABYSS.id);

        // ---------------------------------------------------------------- 記録の検め
        d = new SkillData();
        d.ranks.put(Skill.TITAN_CLEAVE.id, 3);
        d.ranks.put(Skill.LUNGE.id, 1);
        d.ranks.put("skyabyss:gone", 2);
        d.slots[0] = Skill.LUNGE.id;
        d.slots[1] = Skill.RIPOSTE.id; // 常時の技は置けない
        d.slots[2] = Skill.LUNGE.id; // 重複
        d.slots[3] = "nonsense";
        d.sanitize(9); // 5 点に 4 段
        check(f, d.ranks.size() == 2 && !d.ranks.containsKey("skyabyss:gone"), "unknown skills were not dropped");
        check(f, d.slots[0].equals(Skill.LUNGE.id) && d.slots[1].isEmpty() && d.slots[2].isEmpty() && d.slots[3].isEmpty(),
                "bad slots were not cleared: " + String.join(",", d.slots));
        d.sanitize(3); // 2 点しかない → 全部戻る
        check(f, d.ranks.isEmpty() && d.slots[0].isEmpty(), "overspent skills were not refunded");
        d = new SkillData();
        d.ranks.put(Skill.BACKSTEP.id, 1); // 前提なし
        d.sanitize(50);
        check(f, d.ranks.isEmpty(), "a skill without its requirement survived sanitize");

        // ---------------------------------------------------------------- 空いた枠へ自動で置く
        d = new SkillData();
        d.ranks.put(Skill.TITAN_CLEAVE.id, 1);
        d.ranks.put(Skill.RIPOSTE.id, 1);
        SkillManager.autoSlot(d, Skill.RIPOSTE); // 常時の技は置かない
        SkillManager.autoSlot(d, Skill.TITAN_CLEAVE);
        SkillManager.autoSlot(d, Skill.TITAN_CLEAVE); // 二度目は重ねない
        SkillManager.autoSlot(d, Skill.ROCK_CALL); // 覚えていない技は置かない
        check(f, d.slots[0].equals(Skill.TITAN_CLEAVE.id) && d.slots[1].isEmpty(), "auto slot is wrong: " + String.join(",", d.slots));

        // ---------------------------------------------------------------- クールダウン
        d = new SkillData();
        d.cooldowns.put(Skill.TITAN_CLEAVE.id, 3);
        d.globalCooldown = 2;
        d.tickCooldowns();
        d.tickCooldowns();
        check(f, d.globalCooldown == 0 && d.cooldown(Skill.TITAN_CLEAVE) == 1, "cooldowns did not count down");
        d.tickCooldowns();
        check(f, d.cooldowns.isEmpty(), "a finished cooldown was not removed");

        // ---------------------------------------------------------------- 数値
        check(f, Math.abs(SkillRules.regenPerSecond(20, false) - 0.8) < 1e-9 && Math.abs(SkillRules.regenPerSecond(20, true) - 2.0) < 1e-9,
                "mana regen is wrong");
        check(f, SkillRules.respecCost(0) == 0 && SkillRules.respecCost(1) == 1 && SkillRules.respecCost(3) == 3
                && SkillRules.respecCost(40) == SkillRules.RESPEC_MAX_COST, "respec cost is wrong");
        check(f, SkillRules.farShotBonus(3, 4) == 0 && Math.abs(SkillRules.farShotBonus(3, 16) - 0.09) < 1e-9
                && Math.abs(SkillRules.farShotBonus(3, 60) - 0.18) < 1e-9, "far shot falloff is wrong");
        check(f, SkillRules.pierceDamage(1) == 11 && SkillRules.pierceDamage(3) == 16, "cloud pierce damage is " + SkillRules.pierceDamage(1)
                + "/" + SkillRules.pierceDamage(3));
        check(f, SkillRules.isLarge(1.4f, 2.7f, 100) && !SkillRules.isLarge(0.6f, 1.95f, 20) && SkillRules.isLarge(0.6f, 1.95f, 450),
                "large target rule is wrong");

        // ---------------------------------------------------------------- 保存 (NBT) と同期のコーデック
        SkillData a = sample();
        SkillData b = new SkillData();
        b.deserializeNBT(server.registryAccess(), a.serializeNBT(server.registryAccess()));
        check(f, same(a, b), "nbt round trip lost data");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        SkillData.STREAM_CODEC.encode(buf, a);
        SkillData c = SkillData.STREAM_CODEC.decode(buf);
        buf.release();
        check(f, same(a, c), "sync codec lost data");

        // ---------------------------------------------------------------- プレイヤー
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double px = spawn.getX() + 0.5;
        double py = 300;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "skill_test_a"));
        p.moveTo(px, py, pz, 0f, 0f); // 南 (+Z) を向く
        Growth.get(p).level = 12;
        SkillData pd = SkillManager.get(p);
        SkillManager.settleMana(p);
        check(f, Math.abs(pd.mana - GrowthRules.BASE_MANA) < 1e-4, "fresh mana is not full: " + pd.mana);
        // 復活のあと (mana = -1) は、装備の特性の最大マナが付いた最初の tick で、その上限まで満たす
        net.minecraft.resources.ResourceLocation gearMana = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                jp.revati.skyabyss.SkyAbyss.MODID, "selftest_gear_mana");
        pd.mana = -1;
        p.getAttribute(jp.revati.skyabyss.rpg.Rpg.MAX_MANA).addTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(gearMana, 15, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        SkillManager.regenTick(p, pd, level.getGameTime());
        check(f, Math.abs(pd.mana - (GrowthRules.BASE_MANA + 15)) < 1e-4, "respawn mana did not fill to the gear max: " + pd.mana);
        p.getAttribute(jp.revati.skyabyss.rpg.Rpg.MAX_MANA).removeModifier(gearMana);
        SkillManager.settleMana(p);

        // 習得 (サーバーの入口)
        check(f, SkillManager.learn(p, Skill.TITAN_CLEAVE) == SkillData.LearnResult.OK, "learning titan cleave failed");
        check(f, Skill.TITAN_CLEAVE.id.equals(pd.slots[0]), "a new active skill was not put in the first free slot");
        check(f, SkillManager.learn(p, Skill.TITAN_CLEAVE) == SkillData.LearnResult.OK && pd.rank(Skill.TITAN_CLEAVE) == 2,
                "learning a second rank failed");
        check(f, SkillManager.learn(p, Skill.SKY_RAIN) != SkillData.LearnResult.OK, "sky rain was learned without its requirements");
        check(f, !SkillManager.setSlot(p, 1, Skill.RIPOSTE) && !SkillManager.setSlot(p, 1, Skill.LUNGE), "an unknown or passive skill was slotted");

        // 常時の技の修飾子 (岩の構え 2 段 = 防具 +2・押し返し耐性 +20%) と、岩の木の点 5 のセット (防具 +1・押し返し耐性 +10%)
        SkillManager.forceRank(p, Skill.STONE_STANCE, 2);
        check(f, pd.knows(Skill.RIPOSTE), "forcing stone stance did not fill its requirement");
        check(f, Math.abs(p.getAttributeValue(Attributes.ARMOR) - 3) < 1e-6
                && Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.3) < 1e-6, "stone stance modifiers are wrong: armor "
                        + p.getAttributeValue(Attributes.ARMOR) + ", kb " + p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        check(f, pd.unspentPoints(12) >= 0, "forcing a rank left negative points");

        // マナの回復: 戦いの最中 20 tick で 0.8、静かなときは 2.0
        pd.mana = 0;
        pd.lastCombat = level.getGameTime();
        for (int i = 0; i < 20; i++) {
            SkillManager.regenTick(p, pd, level.getGameTime());
        }
        check(f, Math.abs(pd.mana - 0.8) < 1e-3, "combat regen for 1 second gave " + pd.mana);
        pd.mana = 0;
        pd.lastCombat = level.getGameTime() - SkillRules.CALM_AFTER_TICKS;
        for (int i = 0; i < 20; i++) {
            SkillManager.regenTick(p, pd, level.getGameTime());
        }
        check(f, Math.abs(pd.mana - 2.0) < 1e-3, "calm regen for 1 second gave " + pd.mana);
        pd.mana = 19.99f;
        SkillManager.regenTick(p, pd, level.getGameTime() + 10_000);
        SkillManager.regenTick(p, pd, level.getGameTime() + 10_000);
        check(f, pd.mana <= 20f + 1e-4, "mana went over the max: " + pd.mana);

        // 実際に使う: 巨神斬り (前の敵に当たる・マナを払う・クールダウン・もう一度は使えない)
        // 偽のプレイヤーは tick しないので、手の剣の修飾子が付かない。ダイヤの剣と同じ攻撃力 7 を素の値で持たせる
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.moveTo(px, py, pz + 2.0, 180f, 0f);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        level.addFreshEntity(zombie);
        IronGolem golem = new IronGolem(EntityType.IRON_GOLEM, level);
        golem.moveTo(px + 30, py, pz, 0f, 0f);
        golem.setNoAi(true);
        golem.setNoGravity(true);
        level.addFreshEntity(golem);
        check(f, SkillActions.isFoe(p, zombie) && !SkillActions.isFoe(p, golem) && !SkillActions.isFoe(p, p), "foe filter is wrong");
        // 怒っていないゾンビピグリンは巻き込まない
        net.minecraft.world.entity.monster.ZombifiedPiglin pigman = new net.minecraft.world.entity.monster.ZombifiedPiglin(EntityType.ZOMBIFIED_PIGLIN, level);
        pigman.moveTo(px - 30, py, pz, 0f, 0f);
        check(f, !SkillActions.isFoe(p, pigman), "a calm zombified piglin counts as a foe");
        pigman.setTarget(p);
        check(f, SkillActions.isFoe(p, pigman), "an angry zombified piglin does not count as a foe");
        check(f, SkillActions.isLarge(golem) && !SkillActions.isLarge(zombie), "large check is wrong for golem / zombie");
        pd.mana = 20;
        pd.cooldowns.clear();
        pd.globalCooldown = 0;
        float before = zombie.getHealth();
        SkillManager.CastResult r = SkillManager.cast(p, 0);
        check(f, r == SkillManager.CastResult.OK, "casting titan cleave returned " + r);
        check(f, zombie.getHealth() < before - 4, "titan cleave did not hurt the zombie (" + before + " -> " + zombie.getHealth() + ")");
        check(f, Math.abs(pd.mana - 10) < 1e-4, "titan cleave did not cost 10 mana: " + pd.mana);
        check(f, pd.cooldown(Skill.TITAN_CLEAVE) == SkillRules.cooldownTicks(Skill.TITAN_CLEAVE, 2) && pd.globalCooldown == SkillRules.GLOBAL_COOLDOWN,
                "titan cleave cooldown did not start");
        check(f, zombie.invulnerableTime == 0, "a skill hit left the zombie invulnerable (it would eat the next normal attack)");
        check(f, SkillManager.cast(p, 0) == SkillManager.CastResult.COOLDOWN, "titan cleave was cast again during its cooldown");
        pd.cooldowns.clear();
        pd.globalCooldown = 0;
        pd.mana = 5;
        check(f, SkillManager.cast(p, 0) == SkillManager.CastResult.NO_MANA && pd.mana == 5, "titan cleave was cast without mana");
        check(f, SkillManager.cast(p, 3) == SkillManager.CastResult.EMPTY, "an empty slot did something");

        // 驟雨の傷: 独自の傷の種類で、ノックバックしない (敵を範囲の外へ押し出さない)
        var rainType = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolder(SkillActions.SKY_RAIN_DAMAGE);
        check(f, rainType.isPresent() && rainType.get().is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK), "the sky rain damage type is missing or knocks back");
        zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        float beforeRain = zombie.getHealth();
        check(f, SkillActions.hit(p, zombie, 2f, Skill.SKY_RAIN) && zombie.getHealth() < beforeRain, "sky rain damage did not hurt");
        check(f, zombie.getDeltaMovement().horizontalDistanceSqr() < 1e-6, "sky rain damage knocked the zombie back");

        // 踏み込み: 宙に浮いている (地に足が着いていない) と使えず、マナも減らない
        SkillManager.forceRank(p, Skill.LUNGE, 1);
        SkillManager.setSlot(p, 3, Skill.LUNGE);
        pd.mana = 20;
        pd.globalCooldown = 0;
        check(f, SkillManager.cast(p, 3) == SkillManager.CastResult.FAILED && pd.mana == 20 && pd.cooldown(Skill.LUNGE) == 0,
                "lunge in mid-air was not refused for free");
        SkillManager.setSlot(p, 3, null);

        // 雲穿ち: 弓と矢が要る。矢を 1 本使い、貫く矢が飛ぶ。弓がなければ何も払わない
        SkillManager.forceRank(p, Skill.CLOUD_PIERCE, 2);
        SkillManager.setSlot(p, 1, Skill.CLOUD_PIERCE);
        pd.mana = 20;
        check(f, SkillManager.cast(p, 1) == SkillManager.CastResult.FAILED && pd.mana == 20, "cloud pierce without a bow was not refused for free");
        pd.globalCooldown = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        p.getInventory().setItem(9, new ItemStack(Items.ARROW, 5));
        r = SkillManager.cast(p, 1);
        check(f, r == SkillManager.CastResult.OK, "casting cloud pierce returned " + r);
        check(f, p.getInventory().getItem(9).getCount() == 4, "cloud pierce did not use one arrow");
        List<AbstractArrow> arrows = level.getEntitiesOfClass(AbstractArrow.class, new AABB(px - 8, py - 8, pz - 8, px + 8, py + 8, pz + 8),
                e -> e.getTags().contains(SkillActions.PIERCE_TAG));
        check(f, arrows.size() == 1 && arrows.get(0).getPierceLevel() == SkillRules.piercePierce(2) + 1 && arrows.get(0).isNoGravity()
                && arrows.get(0).pickup == AbstractArrow.Pickup.CREATIVE_ONLY, "the cloud pierce arrow is wrong: " + arrows.size());
        arrows.forEach(AbstractArrow::discard);

        // 天の驟雨: 弓を持って使うと、降る予定が一つ積まれる
        SkillManager.forceRank(p, Skill.SKY_RAIN, 1);
        SkillManager.setSlot(p, 2, Skill.SKY_RAIN);
        pd.mana = 20;
        pd.globalCooldown = 0;
        int rains = SkillActions.pendingRains();
        r = SkillManager.cast(p, 2);
        check(f, r == SkillManager.CastResult.OK && SkillActions.pendingRains() == rains + 1 && Math.abs(pd.mana - 4) < 1e-4,
                "sky rain was not scheduled (" + r + ", mana " + pd.mana + ")");
        SkillActions.clear();

        // 鷹の目の印と遠矢は数値で (矢の飛ぶ時間を待たない)。返し刃の窓
        check(f, SkillRules.markBonus(3) == 0.12 && SkillRules.riposteBonus(3) > 0.44 && SkillRules.RIPOSTE_WINDOW == 60, "passive numbers changed");

        // 死亡時の引き継ぎと、プレイヤーの保存と読み込み
        pd.cooldowns.put(Skill.TITAN_CLEAVE.id, 77);
        FakePlayer reborn = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "skill_test_b"));
        reborn.copyAttachmentsFrom(p, true);
        check(f, same(pd, SkillManager.get(reborn)), "skill data was not copied on death");
        CompoundTag saved = new CompoundTag();
        p.saveWithoutId(saved);
        FakePlayer loaded = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "skill_test_c"));
        loaded.load(saved);
        check(f, same(pd, SkillManager.get(loaded)), "skill data was not saved with the player");
        check(f, SkillManager.get(loaded).cooldown(Skill.TITAN_CLEAVE) == 77, "cooldowns were not saved");
        check(f, Math.abs(loaded.getAttributeValue(Attributes.ARMOR) - 3) < 1e-6, "stone stance armor was not saved with the player");

        // 忘れると修飾子が外れる
        SkillManager.forgetAll(p);
        check(f, p.getAttribute(Attributes.ARMOR).getModifier(SkillManager.MOD_STANCE_ARMOR) == null && pd.slot(0) == null,
                "forgetting did not clear the modifiers and slots");
        // 振り直しは炉のそばでだけ (サバイバル)
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        Growth.get(p).level = 16;
        check(f, SkillManager.respec(p) == SkillManager.RespecResult.NO_FORGE, "respec worked away from a forge");

        zombie.discard();
        golem.discard();
        BuildsSelfTest.run(server, f);
    }

    private static SkillData sample() {
        SkillData a = new SkillData();
        a.ranks.put(Skill.TITAN_CLEAVE.id, 3);
        a.ranks.put(Skill.LUNGE.id, 1);
        a.ranks.put(Skill.CLOUD_PIERCE.id, 2);
        a.slots[0] = Skill.LUNGE.id;
        a.slots[2] = Skill.TITAN_CLEAVE.id;
        a.bonusPoints = 2;
        a.respecs = 3;
        a.mana = 17.5f;
        a.cooldowns.put(Skill.TITAN_CLEAVE.id, 40);
        a.globalCooldown = 0;
        a.unlockedTrees.add(SkillTree.ABYSS.id);
        return a;
    }

    private static boolean same(SkillData a, SkillData b) {
        return a.ranks.equals(b.ranks) && java.util.Arrays.equals(a.slots, b.slots) && a.bonusPoints == b.bonusPoints
                && a.respecs == b.respecs && Math.abs(a.mana - b.mana) < 1e-4 && a.cooldowns.equals(b.cooldowns)
                && a.unlockedTrees.equals(b.unlockedTrees);
    }
}
