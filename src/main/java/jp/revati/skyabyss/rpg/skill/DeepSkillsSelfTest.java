package jp.revati.skyabyss.rpg.skill;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rebirth.RebirthData;
import jp.revati.skyabyss.rebirth.RebirthSetup;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 橋 (A09)・変異 (A08)・前世の技 (B08)・淵 (B09)・巨神のアスペクト (58) の自己テスト。BuildsSelfTest.run の最後から呼ぶ。docs/design/31
final class DeepSkillsSelfTest {
    private DeepSkillsSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        model(f);
        migration(f);
        FakePlayer p = player(server, "deep_test");
        rebirth(p, f);
        mutations(server, f);
        casts(server, f);
        balance(f);
        aspects(server, f);
    }

    private static FakePlayer player(MinecraftServer server, String name) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 0.5, 0f, 0f);
        Growth.get(p).level = 50;
        p.setOnGround(true);
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
        p.getAttribute(jp.revati.skyabyss.rpg.Rpg.MAX_MANA).setBaseValue(200);
        SkillManager.get(p).lastCombat = Long.MIN_VALUE / 2;
        return p;
    }

    private static void setRebirth(FakePlayer p, int n) {
        p.setData(RebirthSetup.DATA.get(), RebirthData.EMPTY.withCount(n));
        // サーバーの見せ方は本体の回数 (GrowthData.rebirths) を読む (Rebirth.displayCount)。写しと本体を揃える
        jp.revati.skyabyss.rpg.Growth.get(p).rebirths = n;
        SkillManager.sync(p);
        SkillExtOps.refreshRebirth(p);
    }

    // ---------------------------------------------------------------- 形
    private static void model(List<String> f) {
        int deep = 0;
        int past = 0;
        java.util.Set<Integer> icons = new java.util.HashSet<>();
        for (Skill s : Skill.values()) {
            check(f, icons.add(s.iconIndex()), "duplicate skill icon " + s.id);
            switch (s.tree) {
                case DEEP -> deep++;
                case PAST -> past++;
                default -> {
                }
            }
        }
        check(f, deep == 6, "deep tree size " + deep);
        check(f, past == 6 && past <= 6, "past-life skill count " + past);
        // 淵: 転生 1 で入口 (行 0)、5 で全段
        check(f, Skill.DEEP_PULL.rebirthAt() == 1 && Skill.DEEP_OATH.rebirthAt() == 5, "deep tree rebirth range is wrong");
        for (Skill s : Skill.values()) {
            if (s.tree == SkillTree.DEEP || s.tree == SkillTree.PAST) {
                check(f, s.rebirthAt() >= 1 && s.rebirthAt() <= 5, "reborn skill " + s.id + " has bad rebirth requirement");
            } else {
                check(f, s.rebirthAt() == 0, "ordinary skill " + s.id + " requires rebirth");
            }
            check(f, SkillTag.of(s) != null, "no tag " + s.id);
            if (s.active) {
                check(f, SkillRules.manaCost(s, 1) > 0 && SkillRules.cooldownTicks(s, 1) > 0, "active skill without cost: " + s.id);
            }
        }
        check(f, Mutation.values().length == 10, "mutation count");
        for (Mutation m : Mutation.values()) {
            check(f, m.skill.active && !m.coreItem().equals(net.minecraft.world.item.Items.AIR) && !m.shardItem().equals(net.minecraft.world.item.Items.AIR),
                    "mutation " + m.id + " has no skill or material");
        }
    }

    // ---------------------------------------------------------------- 保存の移し (版 3 → 4)
    private static void migration(List<String> f) {
        SkillData old = new SkillData();
        old.ranks.put("titan_cleave", 2);
        old.ranks.put("lunge", 1);
        old.mastery.put("titan_cleave", 20);
        CompoundTag tag = old.serializeNBT(null);
        // 版 3 の記録: 新しい欄が丸ごと無い
        tag.putInt("v", 3);
        tag.remove("mutations");
        tag.remove("aspects");
        tag.remove("aspectSlots");
        SkillData loaded = new SkillData();
        loaded.deserializeNBT(null, tag);
        check(f, loaded.version == SkillData.VERSION && SkillData.VERSION == 4, "the record was not migrated to version 4: " + loaded.version);
        check(f, loaded.rank(Skill.TITAN_CLEAVE) == 2 && loaded.rank(Skill.LUNGE) == 1 && loaded.masteryXp(Skill.TITAN_CLEAVE) == 20, "version 3 skills were lost");
        check(f, loaded.mutations.isEmpty() && loaded.aspects.isEmpty(), "new fields did not start empty");
        for (String s : loaded.aspectSlots) {
            check(f, s != null && s.isEmpty(), "an aspect slot did not start empty");
        }
        // 新しい欄が往復する。壊れた値は落ちる
        loaded.mutations.put("wind_rend", "rend_blade");
        loaded.mutations.put("lunge", "rend_blade");
        loaded.mutations.put("nothing", "x");
        loaded.aspects.put("warrior_r1", 3);
        loaded.aspects.put("garbage", 2);
        loaded.aspectSlots[1] = "warrior_r1";
        loaded.aspectSlots[2] = "nothing";
        loaded.aspectPity = 5;
        SkillData again = new SkillData();
        again.deserializeNBT(null, loaded.serializeNBT(null));
        check(f, again.mutations.size() == 1 && "rend_blade".equals(again.mutations.get("wind_rend")), "mutations did not survive the round trip");
        again.sanitizeExt();
        check(f, again.aspects.size() == 1 && again.aspects.get("warrior_r1") == 3, "aspects did not survive the round trip: " + again.aspects);
        check(f, "warrior_r1".equals(again.aspectSlots[1]) && again.aspectSlots[2].isEmpty() && again.aspectPity == 5, "aspect slots did not survive the round trip");
    }

    // ---------------------------------------------------------------- 転生 (B08・B09)
    private static void rebirth(FakePlayer p, List<String> f) {
        SkillData d = SkillManager.get(p);
        SkillManager.forgetAll(p);
        d.bonusPoints = 80;
        setRebirth(p, 0);
        // 未転生の人は淵も前世の技も取れない (画面にも出ない)。点は通常の点から引かれない
        check(f, d.canLearn(Skill.DEEP_PULL, 50) == SkillData.LearnResult.REBIRTH, "the deep opened without a rebirth");
        check(f, d.canLearn(Skill.ECHO_STRIKE, 50) == SkillData.LearnResult.REBIRTH, "a past-life skill opened without a rebirth");
        check(f, d.pastPool() == 0 && SkillTree.DEEP.rebornOnly() && SkillTree.PAST.rebornOnly() && !SkillTree.ROCK.rebornOnly(), "reborn-only flags are wrong");
        // 転生 1: 淵は入口 (行 0) だけ。前世の技は 2 点 (遺響の一撃・旧き見張り)
        setRebirth(p, 1);
        check(f, d.rebirth == 1 && d.pastPool() == 2, "the rebirth count did not reach the record");
        check(f, SkillManager.learn(p, Skill.DEEP_PULL) == SkillData.LearnResult.OK, "the deep entry did not open at rebirth 1");
        check(f, SkillManager.learn(p, Skill.DEEP_ECHO) == SkillData.LearnResult.REBIRTH, "a deep row opened too early");
        int normalSpent = d.spentPoints();
        check(f, SkillManager.learn(p, Skill.ECHO_STRIKE) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.OLD_VIGIL) == SkillData.LearnResult.OK,
                "past-life skills were not learned at rebirth 1");
        check(f, d.spentPoints() == normalSpent && d.pastSpent() == 2 && d.pastLeft() == 0, "past-life points are not a separate pool");
        check(f, SkillManager.learn(p, Skill.ECHO_STRIKE) == SkillData.LearnResult.NO_POINTS, "past-life points ran past the pool");
        check(f, SkillManager.learn(p, Skill.GRAVE_BELL) == SkillData.LearnResult.REBIRTH, "a later past-life skill opened at rebirth 1");
        // 転生が増えると深い段・点が開く。5 で全段
        setRebirth(p, 3);
        check(f, d.pastPool() == 6, "past pool at 3 rebirths is " + d.pastPool());
        check(f, SkillManager.learn(p, Skill.GRAVE_BELL) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.MEMORY_EDGE) == SkillData.LearnResult.OK, "rebirth 3 past skills");
        check(f, SkillManager.learn(p, Skill.FINAL_LIGHT) == SkillData.LearnResult.REBIRTH, "final light opened at rebirth 3");
        check(f, SkillManager.learn(p, Skill.DEEP_ECHO) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.DEEP_ECHO) == SkillData.LearnResult.OK, "deep tier 1 at rebirth 3");
        check(f, SkillManager.learn(p, Skill.DEEP_MAW) == SkillData.LearnResult.REBIRTH, "the deep capstone opened at rebirth 3");
        setRebirth(p, 5);
        check(f, SkillManager.learn(p, Skill.DEEP_MIRROR) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.DEEP_TITHE) == SkillData.LearnResult.OK
                && SkillManager.learn(p, Skill.DEEP_MAW) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.DEEP_OATH) == SkillData.LearnResult.OK
                && SkillManager.learn(p, Skill.FINAL_LIGHT) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.SOUL_THREAD) == SkillData.LearnResult.OK,
                "the whole deep tree / past skills did not open at rebirth 5");
        // 上限: 技は 6 本まで・点は 12 まで
        check(f, d.pastPool() == 10 && d.pastSpent() <= d.pastPool(), "past points are over the pool at 5 rebirths");
        // 淵の鏡: タグが 3 種類以上で全技 +3% (段 1)。貢ぎ: いちばん多いタグ以外の多い順へ +1
        SkillManager.forgetAll(p);
        setRebirth(p, 5);
        SkillManager.forceRank(p, Skill.DEEP_MIRROR, 1);
        check(f, SkillExt.distinctTags(p) == 1 && SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) < SkillRules.mirrorPower(1), "the mirror applied too early");
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        SkillManager.forceRank(p, Skill.LUNGE, 1);
        SkillManager.forceRank(p, Skill.WARD_SONG, 1);
        double before = SkillBuild.skillPower(p, Skill.TITAN_CLEAVE);
        check(f, SkillExt.distinctTags(p) >= 3 && before >= SkillRules.mirrorPower(1), "the mirror did not apply with three tags: " + before);
        SkillManager.forceRank(p, Skill.DEEP_TITHE, 2);
        int tideBefore = SkillBuild.tagCount(p, SkillTag.TIDE);
        check(f, SkillExt.tithed(SkillManager.get(p)).size() == 2, "tithe did not pick two tags");
        check(f, SkillBuild.tagCount(p, SkillTag.TIDE) >= tideBefore, "tithe lowered a tag count");
        // 淵の誓い: 5 つのタグがそろうと +8%
        SkillManager.forceRank(p, Skill.DEEP_OATH, 1);
        check(f, SkillExt.distinctTags(p) < 5 || SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) >= SkillRules.OATH_DEEP_POWER, "oath without five tags applied");
        // 回数が下がる (開発用・やり直し) と、足りない淵・前世の技は戻る。通常の技は残る
        SkillManager.forceRank(p, Skill.SOUL_THREAD, 1);
        setRebirth(p, 0);
        check(f, !d.knows(Skill.DEEP_OATH) && !d.knows(Skill.SOUL_THREAD) && !d.knows(Skill.DEEP_MIRROR) && d.knows(Skill.TITAN_CLEAVE),
                "lowering the rebirth count did not drop reborn skills");
        // 記憶の刃: 転生の回数に比例
        setRebirth(p, 4);
        SkillManager.forceRank(p, Skill.MEMORY_EDGE, 2);
        check(f, Math.abs(SkillRules.memoryPower(2, 4) - 0.04) < 1e-9, "memory edge scaling is wrong");
        setRebirth(p, 0);
    }

    // ---------------------------------------------------------------- 変異 (A08)
    private static void give(FakePlayer p, Mutation m, int cores, int shards) {
        p.getInventory().add(new ItemStack(m.coreItem(), cores));
        p.getInventory().add(new ItemStack(m.shardItem(), shards));
    }

    private static void mutations(MinecraftServer server, List<String> f) {
        FakePlayer p = player(server, "deep_test_m");
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 80;
        SkillManager.forceRank(p, Skill.WIND_REND, 1);
        Mutation blade = Mutation.REND_BLADE;
        // 変異は 48 で木の節に (SkillManager.takeNode が極めと素材を見る。木の決まりは自己テスト tree)。ここは素材の数えと払い
        SkillManager.forceRank(p, Skill.WIND_REND, Skill.WIND_REND.maxRank);
        check(f, !SkillExtOps.hasMaterial(p, blade), "materials were counted with nothing held");
        give(p, blade, 1, 2);
        check(f, !SkillExtOps.hasMaterial(p, blade), "too few shards counted as enough");
        give(p, blade, 0, 1);
        check(f, SkillExtOps.hasMaterial(p, blade), "materials were not counted");
        SkillExtOps.payMutation(p, blade);
        check(f, "rend_blade".equals(d.mutations.get("wind_rend")), "the mutation was not recorded");
        check(f, SkillExtOps.count(p, blade.coreItem()) == 0 && SkillExtOps.count(p, blade.shardItem()) == 0, "materials were not taken");
        // 素材は留め袋に入っていても数える・使える (拾った素材は袋へ自動で入る。持ち物だけを見ると「足りない」と出てしまった)
        Mutation surge = Mutation.LUNGE_SURGE;
        SkillManager.forceRank(p, Skill.LUNGE, Skill.LUNGE.maxRank);
        jp.revati.skyabyss.pouch.Pouch.add(p, new ItemStack(surge.coreItem(), surge.CORES));
        jp.revati.skyabyss.pouch.Pouch.add(p, new ItemStack(surge.shardItem(), surge.SHARDS));
        check(f, SkillExtOps.hasMaterial(p, surge) && SkillExtOps.count(p, surge.coreItem()) == surge.CORES, "materials in the pouch were not counted");
        SkillExtOps.payMutation(p, surge);
        check(f, "lunge_surge".equals(d.mutations.get("lunge")) && SkillExtOps.count(p, surge.shardItem()) == 0, "the pouch materials did not pay for a mutation");
        // 刻印とは別: 刻印も選べる
        check(f, SkillManager.chooseRune(p, Skill.WIND_REND, 1) == SkillManager.RuneResult.OK && d.rune(Skill.WIND_REND) == 1 && d.mutations.containsKey("wind_rend"),
                "a rune could not coexist with a mutation");
        // 効くのは極めているあいだだけ
        check(f, SkillExtOps.active(p, Skill.WIND_REND) == Mutation.REND_BLADE, "the mutation is not active");
        d.ranks.put("wind_rend", 1);
        check(f, SkillExtOps.active(p, Skill.WIND_REND) == null, "the mutation worked below max rank");
        d.ranks.put("wind_rend", Skill.WIND_REND.maxRank);
        // 力・待ち・マナは元のまま (変異は数字を変えない)
        check(f, SkillManager.manaCostFor(p, Skill.WIND_REND) == SkillRules.manaCost(Skill.WIND_REND, Skill.WIND_REND.maxRank), "a mutation changed the mana cost");
        // 8 つとも素材が違う巨神・技が違う
        java.util.Set<Skill> skills = new java.util.HashSet<>();
        java.util.Map<String, Integer> perTitan = new java.util.HashMap<>();
        for (Mutation m : Mutation.values()) {
            skills.add(m.skill);
            perTitan.merge(m.titan, 1, Integer::sum);
        }
        check(f, skills.size() == 10 && perTitan.size() == 4 && perTitan.values().stream().allMatch(n -> n >= 2), "mutations are not spread over four titans");
        // 転生しても変異は残る
        SkillReset.forRebirth(p);
        check(f, "rend_blade".equals(d.mutations.get("wind_rend")) && SkillExtOps.active(p, Skill.WIND_REND) == null, "the rebirth reset lost or kept a mutation wrongly");
    }

    // ---------------------------------------------------------------- 技を使う (形と傷)
    private static Zombie zombie(ServerLevel level, double x, double y, double z) {
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.moveTo(x, y, z, 180f, 0f);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        zombie.setHealth(1000);
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static double hurt(Zombie z) {
        double v = 1000 - z.getHealth();
        z.setHealth(1000);
        z.invulnerableTime = 0;
        return v;
    }

    private static void casts(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double px = spawn.getX() + 0.5;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = player(server, "deep_test_c");
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 80;
        setRebirth(p, 5);
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        // 駆け抜ける技は足場のあるところだけ進む: 空に石の床を敷く (終わりに外す)
        java.util.List<BlockPos> floor = new java.util.ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -2; dz <= 12; dz++) {
                BlockPos at = new BlockPos(spawn.getX() + dx, 299, spawn.getZ() + dz);
                level.setBlock(at, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                floor.add(at);
            }
        }
        Zombie near = zombie(level, px, 300, pz + 2.5);
        Zombie side = zombie(level, px + 2.5, 300, pz + 0.5);
        Zombie far = zombie(level, px, 300, pz + 7.0);
        try {
            // ---- 変異を入れない元の技の傷 (比べる基準)
            for (Skill s : new Skill[] {Skill.TITAN_CLEAVE, Skill.WHALE_SWEEP, Skill.WIND_REND, Skill.LUNGE}) {
                SkillManager.forceRank(p, s, s.maxRank);
            }
            double[] base = new double[4];
            Skill[] four = {Skill.TITAN_CLEAVE, Skill.WHALE_SWEEP, Skill.WIND_REND, Skill.LUNGE};
            for (int i = 0; i < four.length; i++) {
                p.moveTo(px, 300, pz, 0f, 0f);
                hurt(near);
                hurt(side);
                hurt(far);
                check(f, SkillActions.perform(p, four[i], four[i].maxRank) == null, "base skill failed: " + four[i].id);
                base[i] = hurt(near);
                hurt(side);
                hurt(far);
            }
            // ---- 変異した形 (すぐ当たる 4 つ): 傷は元の 0.4〜1.6 倍。形が違う (遠い敵・横の敵への当たり)
            Mutation[] muts = {Mutation.CLEAVE_CLIFF, Mutation.SWEEP_TAIL, Mutation.REND_BLADE, Mutation.LUNGE_SURGE};
            for (int i = 0; i < muts.length; i++) {
                Mutation m = muts[i];
                d.mutations.put(m.skill.id, m.id);
                p.moveTo(px, 300, pz, 0f, 0f);
                hurt(near);
                hurt(side);
                hurt(far);
                check(f, SkillActions.perform(p, m.skill, m.skill.maxRank) == null, "mutated skill failed: " + m.id);
                double dn = hurt(near);
                double ds = hurt(side);
                double df = hurt(far);
                check(f, dn > 0, m.id + " did not hit a foe right ahead");
                check(f, dn >= base[i] * 0.4 && dn <= base[i] * 1.6 + 0.5, m.id + " damage is out of line with the base skill: " + dn + " vs " + base[i]);
                if (m == Mutation.CLEAVE_CLIFF || m == Mutation.REND_BLADE) {
                    check(f, df > 0, m.id + " did not reach the far foe (shape did not change)");
                    check(f, ds == 0, m.id + " hit a foe to the side");
                }
                if (m == Mutation.SWEEP_TAIL) {
                    check(f, ds > 0 && df == 0, "tail quake shape is wrong: side " + ds + " far " + df);
                }
                d.mutations.remove(m.skill.id);
            }
            // ---- 遅れて落ちる 3 つ: 呼びが通り、あとで動く予約ができる
            SkillManager.forceRank(p, Skill.EMBER_HAMMER, 3);
            SkillManager.forceRank(p, Skill.BONE_TRAP, 2);
            SkillManager.forceRank(p, Skill.EBB_TIDE, 2);
            for (Mutation m : new Mutation[] {Mutation.HAMMER_METEOR, Mutation.TRAP_MINE, Mutation.EBB_WHIRL}) {
                d.mutations.put(m.skill.id, m.id);
                int later = SkillEffects.pendingLater();
                p.moveTo(px, 300, pz, 0f, 0f);
                check(f, SkillActions.perform(p, m.skill, Math.min(m.skill.maxRank, d.rank(m.skill))) == null || m == Mutation.EBB_WHIRL,
                        "mutated skill failed: " + m.id);
                check(f, SkillEffects.pendingLater() >= later, m.id + " lost its reservations");
                d.mutations.remove(m.skill.id);
            }
            // ---- 淵・前世の能動 6 本
            for (Skill s : new Skill[] {Skill.DEEP_PULL, Skill.DEEP_ECHO, Skill.DEEP_MAW, Skill.ECHO_STRIKE,
                    Skill.GRAVE_BELL, Skill.FINAL_LIGHT}) {
                SkillManager.forceRank(p, s, 2);
                p.moveTo(px, 300, pz, 0f, 0f);
                hurt(near);
                hurt(side);
                hurt(far);
                String r = SkillActions.perform(p, s, 2);
                check(f, r == null, "active skill failed: " + s.id + " " + r);
                double dn = hurt(near);
                double ds = hurt(side);
                if (s == Skill.DEEP_ECHO) {
                    check(f, dn == 0 && d.echoUntil > level.getGameTime(), "deep echo should buff and deal no damage");
                    check(f, SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) >= SkillRules.echoPower(2), "deep echo did not raise skill power");
                } else if (s == Skill.GRAVE_BELL || s == Skill.FINAL_LIGHT) {
                    check(f, dn > 0 && ds > 0, s.id + " did not hit foes all around");
                } else if (s == Skill.ECHO_STRIKE) {
                    check(f, dn > 0, s.id + " did not hit a foe ahead");
                }
                p.moveTo(px, 300, pz, 0f, 0f);
                d.echoUntil = 0;
            }
        } finally {
            near.discard();
            side.discard();
            far.discard();
            for (BlockPos at : floor) {
                level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            setRebirth(p, 0);
        }
    }

    // ---------------------------------------------------------------- 数のつり合い
    private static void balance(List<String> f) {
        // 新しい能動 6 本は、マナあたりの力が既存の最大 (巨神斬り段 3 を大きな相手へ) を超えない
        double ceiling = BuildsSelfTest.perMana(Skill.TITAN_CLEAVE, 3);
        double[][] fresh = {
                {SkillRules.pullMultiplier(2), SkillRules.manaCost(Skill.DEEP_PULL, 2), SkillRules.cooldownTicks(Skill.DEEP_PULL, 2)},
                {SkillRules.mawMultiplier(2) * 1.2, SkillRules.manaCost(Skill.DEEP_MAW, 2), SkillRules.cooldownTicks(Skill.DEEP_MAW, 2)},
                {SkillRules.echoStrikeMultiplier(2), SkillRules.manaCost(Skill.ECHO_STRIKE, 2), SkillRules.cooldownTicks(Skill.ECHO_STRIKE, 2)},
                {SkillRules.bellMultiplier(2), SkillRules.manaCost(Skill.GRAVE_BELL, 2), SkillRules.cooldownTicks(Skill.GRAVE_BELL, 2)},
                {SkillRules.lightMultiplier(2), SkillRules.manaCost(Skill.FINAL_LIGHT, 2), SkillRules.cooldownTicks(Skill.FINAL_LIGHT, 2)}};
        for (double[] row : fresh) {
            check(f, row[0] / row[1] <= ceiling, "a new active is stronger per mana than the strongest old one: " + row[0] / row[1] + " vs " + ceiling);
            check(f, row[0] * 20 / row[2] <= 0.45 * 1.6 * 3, "a new active is too strong per second: " + row[0] * 20 / row[2]);
        }
        // 最強の組み立て (31 の A 案の上限 + 紋・淵・前世の足しを全部): 心臓は 15 秒、番兵長は 5 秒を割らない
        double basic = 1.6;
        double regen = SkillRules.regenPerSecond(20 + GrowthRules.bonusMana(20), false);
        double skillDps = regen * BuildsSelfTest.perMana(Skill.TITAN_CLEAVE, 3);
        double general = SkillRules.KEY_GLASS_DAMAGE + SkillRules.KEY_DANCE_DAMAGE + 0.25;
        // 技の力: 木の節・熟練の刻印 + アスペクト + 淵の残響・鏡・誓い + 記憶の刃 (技の力の合計は +100% で頭打ち)
        double skillPart = 0.15 + SkillRules.MASTERY_RUNE_POWER + SkillRules.ASPECT_POWER_MAX
                + SkillRules.echoPower(2) + SkillRules.mirrorPower(3) + SkillRules.OATH_DEEP_POWER + SkillRules.memoryPower(2, 6);
        skillPart = Math.min(SkillRules.POWER_CAP, skillPart);
        double regenBoost = 1 + SkillRules.OATH_DEEP_REGEN;
        for (double mana : new double[] {1 + SkillRules.KEY_ASH_MANA_COST - SkillRules.ASPECT_MANA_MAX, 1 - SkillRules.ASPECT_MANA_MAX}) {
            double gen = mana < 1 ? general - SkillRules.KEY_ASH_DAMAGE : general;
            double maxDps = basic * (1 + gen) + skillDps * regenBoost / mana * (1 + gen) * (1 + skillPart);
            double heart = 450 / (8 * maxDps);
            double captain = 150 / (8 * maxDps);
            check(f, heart >= 15 && captain >= 5, "the strongest build with aspects / deep / past kills too fast: heart " + heart + " s, captain " + captain + " s");
            SkyAbyss.LOGGER.info("[selftest] deep skills balance (strongest build, mana x{}): heart {} s, captain {} s", mana, Math.round(heart * 10) / 10.0, Math.round(captain * 10) / 10.0);
        }
        // 基準の心臓 26 秒・番兵長 9 秒は変えない (新しい技はふつうの回し方を強くしない)
        double heartBase = 450 / (8 * (basic + skillDps));
        double captainBase = 150 / (8 * (basic + skillDps));
        check(f, heartBase >= 20 && heartBase <= 40 && captainBase >= 6 && captainBase <= 14, "baseline kill times moved: " + heartBase + " / " + captainBase);
    }

    // ---------------------------------------------------------------- アスペクト (docs/design/58)
    private static void aspects(MinecraftServer server, List<String> f) {
        // 表: 6 職 × 16 (レア 4・エピック 4・ミシック 8 = 祠ごとに 2)
        check(f, jp.revati.skyabyss.rpg.aspect.AspectTable.ALL.size() == 96, "aspect count " + jp.revati.skyabyss.rpg.aspect.AspectTable.ALL.size());
        for (Job job : Job.values()) {
            check(f, jp.revati.skyabyss.rpg.aspect.Aspects.ofJob(job).size() == 16, "aspects of " + job);
            for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
                check(f, jp.revati.skyabyss.rpg.aspect.Aspects.pool(job, titan).size() == 10, "pool of " + job + " at " + titan);
            }
        }
        // Lv: 数 1/2/4/7/11 で Lv 1〜5
        int[] copies = {0, 1, 2, 3, 4, 6, 7, 10, 11, 40};
        int[] want = {0, 1, 2, 2, 3, 3, 4, 4, 5, 5};
        for (int i = 0; i < copies.length; i++) {
            check(f, jp.revati.skyabyss.rpg.aspect.Aspects.levelOf(copies[i]) == want[i], "aspect level of " + copies[i] + " copies");
        }
        // 枠: Lv 20/30/40/50 で 1〜4、転生 3 回で 5
        check(f, jp.revati.skyabyss.rpg.aspect.Aspects.slots(19, 0) == 0 && jp.revati.skyabyss.rpg.aspect.Aspects.slots(20, 0) == 1
                && jp.revati.skyabyss.rpg.aspect.Aspects.slots(50, 2) == 4 && jp.revati.skyabyss.rpg.aspect.Aspects.slots(50, 3) == 5
                && jp.revati.skyabyss.rpg.aspect.Aspects.slots(40, 3) == 3, "aspect slots by level/rebirth");
        // 引き: ミシックはその祠だけ。8 回出なければ必ずミシック
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(7);
        for (int i = 0; i < 400; i++) {
            jp.revati.skyabyss.rpg.aspect.Aspect a = jp.revati.skyabyss.rpg.aspect.Aspects.roll(Job.values()[i % Job.values().length], "ember", i % 9, r);
            check(f, a != null, "roll gave nothing");
            if (a == null) {
                return;
            }
            check(f, a.rarity() != jp.revati.skyabyss.rpg.aspect.Aspect.MYTHIC || "ember".equals(a.shrine()), "a mythic from another shrine: " + a.id());
            check(f, i % 9 < jp.revati.skyabyss.rpg.aspect.Aspects.PITY || a.rarity() == jp.revati.skyabyss.rpg.aspect.Aspect.MYTHIC, "pity did not give a mythic");
        }
        // 渡す: 崩れなしで 2 つ。最高の数を越えない
        FakePlayer p = player(server, "aspect_test");
        SkillData d = SkillManager.get(p);
        Job job = Job.values()[0];
        d.curJob = job.id;
        d.aspects.clear();
        d.aspectPity = 0;
        java.util.Arrays.fill(d.aspectSlots, "");
        List<jp.revati.skyabyss.rpg.aspect.AspectNet.Got> got = jp.revati.skyabyss.rpg.aspect.AspectNet.grant(p, "stone_whale", true);
        check(f, got.size() == 2, "a no-wipe route gave " + got.size() + " aspects");
        check(f, jp.revati.skyabyss.rpg.aspect.AspectNet.grant(p, "stone_whale", false).size() == 1, "a route with wipes did not give one aspect");
        jp.revati.skyabyss.rpg.aspect.Aspect first = jp.revati.skyabyss.rpg.aspect.Aspects.ofJob(job).get(0);
        d.aspects.put(first.id(), 11);
        d.aspectPity = 99;
        for (int i = 0; i < 30; i++) {
            jp.revati.skyabyss.rpg.aspect.AspectNet.grant(p, "stone_whale", false);
        }
        for (int v : d.aspects.values()) {
            check(f, v <= 11, "aspect copies went past the max: " + v);
        }
        // 付ける: 枠が開いていなければ断る。持っていなければ断る。付けた技の力が上がる
        Growth.get(p).level = 10;
        check(f, jp.revati.skyabyss.rpg.aspect.Aspects.equip(p, 0, first.id()) == jp.revati.skyabyss.rpg.aspect.Aspects.EquipResult.LOCKED, "a locked slot took an aspect");
        Growth.get(p).level = 50;
        double before = jp.revati.skyabyss.rpg.aspect.Aspects.power(p, first.skill());
        check(f, jp.revati.skyabyss.rpg.aspect.Aspects.equip(p, 0, first.id()) == jp.revati.skyabyss.rpg.aspect.Aspects.EquipResult.OK, "equipping an owned aspect failed");
        check(f, jp.revati.skyabyss.rpg.aspect.Aspects.equip(p, 1, first.id()) == jp.revati.skyabyss.rpg.aspect.Aspects.EquipResult.OK
                && d.aspectSlots[0].isEmpty() && first.id().equals(d.aspectSlots[1]), "the same aspect sat in two slots");
        double after = jp.revati.skyabyss.rpg.aspect.Aspects.power(p, first.skill()) + jp.revati.skyabyss.rpg.aspect.Aspects.cooldown(p, first.skill()) * -1
                + jp.revati.skyabyss.rpg.aspect.Aspects.mana(p, first.skill()) * -1;
        check(f, first.fx().isEmpty() || after != before || jp.revati.skyabyss.rpg.aspect.Aspects.worn(p).size() == 1, "a worn aspect changed nothing");
        check(f, jp.revati.skyabyss.rpg.aspect.Aspects.worn(p).size() == 1 && jp.revati.skyabyss.rpg.aspect.Aspects.worn(p).get(0).level() == 5, "worn aspect level");
        d.aspects.remove("nobody_has_this");
        check(f, jp.revati.skyabyss.rpg.aspect.Aspects.equip(p, 2, "nobody_has_this") == jp.revati.skyabyss.rpg.aspect.Aspects.EquipResult.NOT_OWNED, "an unowned aspect was equipped");
        // 別の職へ替えると効かない
        d.curJob = Job.values()[1].id;
        check(f, jp.revati.skyabyss.rpg.aspect.Aspects.worn(p).isEmpty(), "another job's aspect stayed worn");
        d.curJob = job.id;
        d.aspects.clear();
        java.util.Arrays.fill(d.aspectSlots, "");
    }
}
