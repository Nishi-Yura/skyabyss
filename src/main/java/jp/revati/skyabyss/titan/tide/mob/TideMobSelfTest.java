package jp.revati.skyabyss.titan.tide.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 潮殻の背の敵の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST tide mobs PASSED / FAILED (サーバーを止めるのは TitanBackSelfTest)。
// 登録と強さ、試練の個体は保存されないこと、経験値の表、落とす物の表 (2 万回の抽選)、潮の周期、像になる・目覚める、
// 裏返りの 2 倍、泡が真上にだけ浮かせること、グループの印 (ほかのグループの人には見えず、狙わない)
public final class TideMobSelfTest {
    private static final int TEST_X = -4200;
    private static final int TEST_Z = 4200;

    private TideMobSelfTest() {
    }

    // 呼ぶのは SelfTestRunner: 読み込み (load) → 決まった tick 待つ → 試す (runSelfTest)
    public static void load(MinecraftServer server) {
        server.overworld().setChunkForced(TEST_X >> 4, TEST_Z >> 4, true);
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        MobTide.Phase before = MobTide.forced();
        try {
            run(level, failures);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide mobs crashed", e);
            failures.add("crashed: " + e);
        } finally {
            MobTide.force(before);
            level.setChunkForced(TEST_X >> 4, TEST_Z >> 4, false);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tide mobs PASSED (registry, stats, not saved, xp, drop table, tide cycle, statue, flip, bubble lift, group marks)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST tide mobs FAILED: {}", f));
        }
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) {
            failures.add(what);
        }
    }

    private static void run(ServerLevel level, List<String> failures) {
        checkStats(level, failures);
        checkXp(failures);
        checkLoot(failures);
        checkTide(failures);
        BlockPos floor = new BlockPos(TEST_X, 200, TEST_Z);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        List<Mob> made = new ArrayList<>();
        try {
            checkSaved(level, floor, made, failures);
            checkStatue(level, floor, made, failures);
            checkFlip(level, floor, made, failures);
            checkBubble(level, floor, made, failures);
            checkGroup(level, floor, made, failures);
        } finally {
            made.forEach(Mob::discard);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    level.setBlock(floor.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static <T extends Mob> T make(ServerLevel level, EntityType<T> type, BlockPos floor, List<Mob> made) {
        T mob = type.create(level);
        mob.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
        level.addFreshEntity(mob);
        made.add(mob);
        return mob;
    }

    // 登録と強さ (08 の 5.1節の表)
    private static void checkStats(ServerLevel level, List<String> failures) {
        Object[][] rows = {
                {TideMobSetup.CORAL_THRALL.get(), "coral_thrall", 40.0, 8.0, 8.0},
                {TideMobSetup.REEF_SCUTTLER.get(), "reef_scuttler", 10.0, 3.0, 4.0},
                {TideMobSetup.BUBBLE_PUFFER.get(), "bubble_puffer", 22.0, 3.0, 2.0},
        };
        for (Object[] r : rows) {
            EntityType<?> type = (EntityType<?>) r[0];
            check(failures, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals(SkyAbyss.MODID + ":" + r[1]), "id of " + r[1]);
            Mob mob = (Mob) type.create(level);
            check(failures, mob instanceof TideMob, r[1] + " is not a TideMob");
            if (mob == null) {
                continue;
            }
            check(failures, mob.getAttributeBaseValue(Attributes.MAX_HEALTH) == (double) r[2], r[1] + " health " + mob.getAttributeBaseValue(Attributes.MAX_HEALTH));
            check(failures, mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == (double) r[3], r[1] + " damage");
            check(failures, mob.getAttributeBaseValue(Attributes.ARMOR) == (double) r[4], r[1] + " armor");
            mob.discard();
        }
        check(failures, Math.abs(BubbleShot.DAMAGE - 4) < 1e-6, "bubble damage 4");
        check(failures, Math.abs(CoralThrall.REACH - 2.5) < 1e-6, "thrall reach 2.5");
        check(failures, Math.abs(ReefScuttler.SPEED - 0.35) < 1e-6, "scuttler speed 0.35");
        check(failures, TideMobSetup.BUBBLE_SHOT.get() != null, "bubble shot registered");
        for (var egg : List.of(TideMobSetup.CORAL_THRALL_EGG, TideMobSetup.REEF_SCUTTLER_EGG, TideMobSetup.BUBBLE_PUFFER_EGG)) {
            check(failures, egg.get() != null && !new ItemStack(egg.get()).isEmpty(), "spawn egg " + egg.getId());
        }
    }

    // 経験値 (08 の 6.6節): 14 / 3 / 10、推奨レベル 18
    private static void checkXp(List<String> failures) {
        String[][] rows = {{"coral_thrall", "14"}, {"reef_scuttler", "3"}, {"bubble_puffer", "10"}};
        for (String[] r : rows) {
            GrowthRewards.Source s = GrowthRewards.MOD_ENEMIES.get(SkyAbyss.MODID + ":" + r[0]);
            check(failures, s != null && s.xp() == Integer.parseInt(r[1]) && s.contentLevel() == 18, "xp of " + r[0] + " = " + s);
        }
    }

    // 落とす物 (08 の 6.1節): 2 万回の抽選の割合が表のとおりか、数の範囲
    private static void checkLoot(List<String> failures) {
        RandomSource random = RandomSource.create(20261016L);
        Object[][] rows = {{"thrall", TideMobLoot.THRALL}, {"scuttler", TideMobLoot.SCUTTLER}, {"puffer", TideMobLoot.PUFFER}};
        int n = 20000;
        for (Object[] row : rows) {
            TideMobLoot.Row r = (TideMobLoot.Row) row[1];
            int shard = 0;
            int res = 0;
            int dried = 0;
            int sac = 0;
            for (int i = 0; i < n; i++) {
                for (ItemStack s : TideMobLoot.roll(r, random)) {
                    if (s.is(TideMobSetup.TIDE_SHELL_SHARD.get())) {
                        shard++;
                        check(failures, s.getCount() >= r.shardMin() && s.getCount() <= r.shardMax(), row[0] + " shard count " + s.getCount());
                    } else if (ModItems.isResonanceShard(s.getItem())) {
                        res++;
                    } else if (s.is(TideMobSetup.TIDE_DRIED_SHELL.get())) {
                        dried++;
                    } else if (s.is(TideMobSetup.BUBBLE_SAC.get())) {
                        sac++;
                    } else {
                        failures.add(row[0] + " unexpected drop " + s);
                    }
                }
            }
            near(failures, row[0] + " shard", shard, n, r.shard());
            near(failures, row[0] + " resonance", res, n, r.resonance());
            near(failures, row[0] + " dried shell", dried, n, r.driedShell());
            near(failures, row[0] + " bubble sac", sac, n, r.bubbleSac());
        }
        check(failures, Math.abs(TideMobLoot.THRALL.shard() - 0.30f) < 1e-6 && Math.abs(TideMobLoot.PUFFER.bubbleSac() - 0.20f) < 1e-6
                && Math.abs(TideMobLoot.SCUTTLER.shard() - 0.08f) < 1e-6, "table values (08 6.1)");
    }

    private static void near(List<String> failures, String what, int got, int n, float p) {
        double rate = got / (double) n;
        double tol = 4 * Math.sqrt(p * (1 - p) / n) + 1e-9;
        check(failures, Math.abs(rate - p) <= tol, what + " rate " + rate + " expected " + p);
    }

    // 潮の周期: 干潮 50 秒 → 満ちる 10 → 満潮 50 → 引く 10
    private static void checkTide(List<String> failures) {
        check(failures, MobTide.CYCLE == 2400, "tide cycle is 2 minutes");
        check(failures, MobTide.phaseAt(0) == MobTide.Phase.LOW && MobTide.phaseAt(999) == MobTide.Phase.LOW, "low at start");
        check(failures, MobTide.phaseAt(1000) == MobTide.Phase.RISING && MobTide.phaseAt(1199) == MobTide.Phase.RISING, "rising");
        check(failures, MobTide.phaseAt(1200) == MobTide.Phase.HIGH && MobTide.phaseAt(2199) == MobTide.Phase.HIGH, "high");
        check(failures, MobTide.phaseAt(2200) == MobTide.Phase.FALLING && MobTide.phaseAt(2400) == MobTide.Phase.LOW, "falling, then low");
        check(failures, !MobTide.Phase.LOW.wet() && MobTide.Phase.RISING.wet() && MobTide.Phase.FALLING.wet(), "wet phases");
    }

    // 試練の個体 (印のある個体) は保存されない。スポーンエッグの個体は保存される
    private static void checkSaved(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(TideMobSetup.CORAL_THRALL.get(), TideMobSetup.REEF_SCUTTLER.get(),
                TideMobSetup.BUBBLE_PUFFER.get())) {
            Mob free = make(level, type, floor, made);
            Mob trial = make(level, type, floor, made);
            trial.addTag(TideMob.TRIAL_TAG);
            check(failures, free.shouldBeSaved(), type + " from an egg should be saved");
            check(failures, !trial.shouldBeSaved(), type + " from the trial must not be saved");
            check(failures, !free.removeWhenFarAway(100), type + " should not despawn");
        }
    }

    // 満潮で像になる (動かず、傷を受けない)。干潮で目覚め、近い人へ向き直る (ここでは人はいない)
    private static void checkStatue(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        CoralThrall t = make(level, TideMobSetup.CORAL_THRALL.get(), floor, made);
        MobTide.force(MobTide.Phase.HIGH);
        t.aiStep();
        check(failures, t.isStatue(), "thrall becomes a statue at high tide");
        float hp = t.getHealth();
        boolean hurt = t.hurt(level.damageSources().generic(), 10);
        check(failures, !hurt && t.getHealth() == hp, "statue takes no damage");
        MobTide.force(MobTide.Phase.LOW);
        t.aiStep();
        check(failures, !t.isStatue(), "thrall wakes at low tide");
        t.invulnerableTime = 0;
        check(failures, t.hurt(level.damageSources().generic(), 1) && t.getHealth() < hp, "awake thrall takes damage");
    }

    // 裏返った磯走りへの傷は 2 倍
    private static void checkFlip(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        ReefScuttler s = make(level, TideMobSetup.REEF_SCUTTLER.get(), floor, made);
        s.flip();
        check(failures, s.isFlipped(), "scuttler flips");
        s.hurt(level.damageSources().generic(), 2);
        check(failures, Math.abs(s.getHealth() - 6) < 1e-3, "flipped scuttler takes double damage (hp " + s.getHealth() + ")");
    }

    // グループの印 (GroupOwned): 印のある敵は、そのグループの人にだけ見え、そのグループの人だけを狙う。印のない敵 (スポーンエッグ) は誰にでも。
    // 泡の弾の持ち主のグループも引ける
    private static void checkGroup(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        java.util.UUID ga = java.util.UUID.fromString("7e1de0c4-0000-4000-8000-0000000000c1");
        java.util.UUID gb = java.util.UUID.fromString("7e1de0c4-0000-4000-8000-0000000000c2");
        net.neoforged.neoforge.common.util.FakePlayer a = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(ga, "[TideMobA]"));
        net.neoforged.neoforge.common.util.FakePlayer b = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(gb, "[TideMobB]"));
        CoralThrall t = make(level, TideMobSetup.CORAL_THRALL.get(), floor, made);
        check(failures, t instanceof jp.revati.skyabyss.titan.group.GroupOwned, "tide mobs are not group owned");
        check(failures, jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(t, b) && t.canAttack(b), "a mob without a group is not for everyone");
        t.setGroupId(ga);
        check(failures, jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(t, a) && !jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(t, b),
                "group A's thrall is visible to group B");
        check(failures, t.canAttack(a) && !t.canAttack(b), "group A's thrall may target group B");
        check(failures, jp.revati.skyabyss.titan.group.Groups.differ(t, b) && !jp.revati.skyabyss.titan.group.Groups.differ(t, a), "group keys of mob and players");
        BubbleShot shot = new BubbleShot(level, t, new net.minecraft.world.phys.Vec3(0, 0, 1));
        check(failures, ga.equals(jp.revati.skyabyss.titan.group.Groups.keyOf(shot)), "a bubble shot does not carry its puffer's group");
        shot.discard();
    }

    // 泡は真上へだけ浮かせる (横の速さは当たる前のまま)
    private static void checkBubble(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        ReefScuttler s = make(level, TideMobSetup.REEF_SCUTTLER.get(), floor, made);
        Vec3 before = new Vec3(0.05, 0, -0.02);
        s.setDeltaMovement(0.6, 0.1, 0.6); // 傷のノックバックで付いた速さのつもり
        BubbleShot.lift(s, before);
        Vec3 v = s.getDeltaMovement();
        check(failures, Math.abs(v.x - 0.05) < 1e-9 && Math.abs(v.z + 0.02) < 1e-9 && Math.abs(v.y - BubbleShot.LIFT) < 1e-9, "bubble lifts straight up " + v);
        check(failures, s.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "bubble gives slow falling");
        // 0.48 で上へ: 重力 0.08、空気 0.98 で、およそ 1.5 ブロック上がる
        double y = 0;
        double vy = BubbleShot.LIFT;
        double top = 0;
        for (int i = 0; i < 40; i++) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
            top = Math.max(top, y);
        }
        check(failures, top > 1.3 && top < 1.8, "lift height " + top);
    }
}
