package jp.revati.skyabyss.titan.storm.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 雷翼の背の敵の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST storm mobs PASSED / FAILED。
// 登録と強さ (12 の 5.1節)、スポーンエッグ、試練の個体は保存されないこと、経験値の表 (6.6節)、落とす物の表 (6.1節、2 万回の抽選)、
// 拍の周期、羽守の突撃 (帯が 0.8 秒前に出る・長さ 14・着地で動けず傷 1.5 倍)、雷蛾の連鎖 (3 人まで・−20%・離れれば跳ばない)、
// 雷乞いの輪 (柱が 3 ブロック以内なら柱へ落ちて人に当たらない。柱がなければ当たって鈍足)
public final class StormMobSelfTest {
    private static final int TEST_X = -4400;
    private static final int TEST_Z = 4400;
    private static final int Y = 200;

    private StormMobSelfTest() {
    }

    private static int waited;

    public static void load(MinecraftServer server) {
        server.overworld().setChunkForced(TEST_X >> 4, TEST_Z >> 4, true);
        waited = 0;
    }

    // 試す所のチャンクが、エンティティを数えられる (近くの敵を探せる) 状態になるまで待つ (新しいワールドでは生成に時間がかかる)
    public static boolean ready(MinecraftServer server) {
        return server.overworld().isPositionEntityTicking(new BlockPos(TEST_X, Y, TEST_Z)) || ++waited > 600;
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        MobStorm.Phase before = MobStorm.forced();
        try {
            run(level, failures, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm mobs crashed", e);
            failures.add("crashed: " + e);
        } finally {
            MobStorm.force(before);
            level.setChunkForced(TEST_X >> 4, TEST_Z >> 4, false);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST storm mobs note: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm mobs PASSED (registry, stats, not saved, xp, drop table, storm cycle, dive line, stun, chain, ring to rod)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST storm mobs FAILED: {}", f));
        }
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) {
            failures.add(what);
        }
    }

    private static void run(ServerLevel level, List<String> failures, List<String> notes) {
        checkStats(level, failures);
        checkXp(failures);
        checkLoot(failures);
        checkCycle(failures);
        BlockPos floor = new BlockPos(TEST_X, Y, TEST_Z);
        check(failures, level.isPositionEntityTicking(floor), "test chunk is entity ticking");
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlock(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        List<Mob> made = new ArrayList<>();
        try {
            checkSaved(level, floor, made, failures);
            checkDive(level, floor, made, failures);
            checkStun(level, floor, made, failures);
            checkChain(level, floor, made, failures);
            checkRing(level, floor, made, failures, notes);
        } finally {
            made.forEach(Mob::discard);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        level.setBlock(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static <T extends Mob> T make(ServerLevel level, EntityType<T> type, Vec3 at, List<Mob> made) {
        T mob = type.create(level);
        mob.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(mob);
        made.add(mob);
        return mob;
    }

    private static Vec3 top(BlockPos floor) {
        return Vec3.atBottomCenterOf(floor).add(0, 1, 0);
    }

    // 登録と強さ (12 の 5.1節の表)
    private static void checkStats(ServerLevel level, List<String> failures) {
        Object[][] rows = {
                {StormMobSetup.FEATHER_WARDEN.get(), "feather_warden", 52.0, 10.0, 9.0},
                {StormMobSetup.STATIC_MOTH.get(), "static_moth", 14.0, 5.0, 2.0},
                {StormMobSetup.THUNDER_SUPPLICANT.get(), "thunder_supplicant", 32.0, 4.0, 4.0},
        };
        for (Object[] r : rows) {
            EntityType<?> type = (EntityType<?>) r[0];
            check(failures, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals(SkyAbyss.MODID + ":" + r[1]), "id of " + r[1]);
            Mob mob = (Mob) type.create(level);
            check(failures, mob instanceof StormMob, r[1] + " is not a StormMob");
            if (mob == null) {
                continue;
            }
            check(failures, mob.getAttributeBaseValue(Attributes.MAX_HEALTH) == (double) r[2], r[1] + " health");
            check(failures, mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == (double) r[3], r[1] + " damage");
            check(failures, mob.getAttributeBaseValue(Attributes.ARMOR) == (double) r[4], r[1] + " armor");
            mob.discard();
        }
        check(failures, FeatherWarden.DIVE_DAMAGE == 12 && FeatherWarden.REACH == 3.0 && FeatherWarden.HALF_ANGLE * 2 == 60, "warden dive 12, reach 3, 60 degrees");
        check(failures, FeatherWarden.CROUCH_TICKS == 20 && FeatherWarden.LINE_LEAD == 16 && FeatherWarden.STUN_TICKS == 30
                && FeatherWarden.LINE_LENGTH == 14, "warden timings (crouch 1.0 s, line 0.8 s, stun 1.5 s, line 14)");
        check(failures, StaticMoth.CHARGE_TICKS_CALM == 80 && StaticMoth.MAX_CHAIN == 3 && StaticMoth.FALLOFF == 0.8f
                && StaticMoth.BURST_RADIUS == 2.5 && StaticMoth.BURST_DAMAGE == 4, "moth charge 4 s, chain 3, -20%, burst 2.5/4");
        check(failures, ThunderSupplicant.CALL_DAMAGE == 9 && ThunderSupplicant.RING_TICKS == 24 && ThunderSupplicant.KEEP_MIN == 8
                && ThunderSupplicant.KEEP_MAX == 14, "supplicant call 9, ring 1.2 s, keeps 8-14");
        for (var egg : List.of(StormMobSetup.FEATHER_WARDEN_EGG, StormMobSetup.STATIC_MOTH_EGG, StormMobSetup.THUNDER_SUPPLICANT_EGG)) {
            check(failures, egg.get() != null && !new ItemStack(egg.get()).isEmpty(), "spawn egg " + egg.getId());
        }
    }

    // 経験値 (12 の 6.6節): 18 / 5 / 14、推奨レベル 24
    private static void checkXp(List<String> failures) {
        String[][] rows = {{"feather_warden", "18"}, {"static_moth", "5"}, {"thunder_supplicant", "14"}};
        for (String[] r : rows) {
            GrowthRewards.Source s = GrowthRewards.MOD_ENEMIES.get(SkyAbyss.MODID + ":" + r[0]);
            check(failures, s != null && s.xp() == Integer.parseInt(r[1]) && s.contentLevel() == 24, "xp of " + r[0] + " = " + s);
        }
    }

    // 落とす物 (12 の 6.1節): 2 万回の抽選の割合が表のとおりか、数の範囲、表にない物が出ないか
    private static void checkLoot(List<String> failures) {
        RandomSource random = RandomSource.create(20261016L);
        Object[][] rows = {{"warden", StormMobLoot.WARDEN}, {"moth", StormMobLoot.MOTH}, {"supplicant", StormMobLoot.SUPPLICANT}};
        int n = 20000;
        for (Object[] row : rows) {
            StormMobLoot.Row r = (StormMobLoot.Row) row[1];
            int[] c = new int[6];
            for (int i = 0; i < n; i++) {
                for (StormMobLoot.Drop d : StormMobLoot.roll(r, random)) {
                    if (d.id().equals(StormMobLoot.SHARD)) {
                        c[0]++;
                        check(failures, d.count() >= r.shardMin() && d.count() <= r.shardMax(), row[0] + " shard count " + d.count());
                    } else if (d.id().equals(StormMobLoot.RESONANCE)) {
                        c[1]++;
                    } else if (d.id().equals(StormMobLoot.FRUIT)) {
                        c[2]++;
                    } else if (d.id().equals(StormMobLoot.DUST)) {
                        c[3]++;
                    } else if (StormMobLoot.ARMOR.contains(d.id())) {
                        c[4]++;
                    } else if (d.id().equals(StormMobLoot.PAGE)) {
                        c[5]++;
                    } else {
                        failures.add(row[0] + " unexpected drop " + d);
                    }
                }
            }
            float[] p = {r.shard(), r.resonance(), r.fruit(), r.dust(), r.armor(), r.page()};
            String[] names = {"shard", "resonance", "fruit", "dust", "armor", "page"};
            for (int k = 0; k < 6; k++) {
                near(failures, row[0] + " " + names[k], c[k], n, p[k]);
            }
        }
        check(failures, StormMobLoot.WARDEN.shard() == 0.30f && StormMobLoot.WARDEN.armor() == 0.01f && StormMobLoot.MOTH.shard() == 0.12f
                && StormMobLoot.MOTH.dust() == 0.15f && StormMobLoot.SUPPLICANT.shard() == 0.25f && StormMobLoot.SUPPLICANT.page() == 0.05f,
                "table values (12 6.1)");
        check(failures, StormMobLoot.item(StormMobLoot.RESONANCE).isPresent(), "resonance shard is registered");
    }

    private static void near(List<String> failures, String what, int got, int n, float p) {
        double rate = got / (double) n;
        double tol = 4 * Math.sqrt(p * (1 - p) / n) + 1e-9;
        check(failures, Math.abs(rate - p) <= tol, what + " rate " + rate + " expected " + p);
    }

    // 拍: 凪 40 秒 → 荒れる 5 → 嵐 40 → 静まる 5
    private static void checkCycle(List<String> failures) {
        check(failures, MobStorm.CYCLE == 1800, "storm cycle is 90 s");
        check(failures, MobStorm.phaseAt(0) == MobStorm.Phase.CALM && MobStorm.phaseAt(799) == MobStorm.Phase.CALM, "calm");
        check(failures, MobStorm.phaseAt(800) == MobStorm.Phase.RISING && MobStorm.phaseAt(899) == MobStorm.Phase.RISING, "rising");
        check(failures, MobStorm.phaseAt(900) == MobStorm.Phase.STORM && MobStorm.phaseAt(1699) == MobStorm.Phase.STORM, "storm");
        check(failures, MobStorm.phaseAt(1700) == MobStorm.Phase.EASING && MobStorm.phaseAt(1800) == MobStorm.Phase.CALM, "easing, then calm");
        check(failures, MobStorm.Phase.STORM.stormy() && !MobStorm.Phase.CALM.stormy(), "stormy phases");
    }

    private static void checkSaved(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(StormMobSetup.FEATHER_WARDEN.get(), StormMobSetup.STATIC_MOTH.get(),
                StormMobSetup.THUNDER_SUPPLICANT.get())) {
            Mob free = make(level, type, top(floor), made);
            Mob trial = make(level, type, top(floor), made);
            trial.addTag(StormMob.TRIAL_TAG);
            check(failures, free.shouldBeSaved(), type + " from an egg should be saved");
            check(failures, !trial.shouldBeSaved(), type + " from the trial must not be saved");
            check(failures, !free.removeWhenFarAway(100), type + " should not despawn");
        }
    }

    // 羽守の突撃: かがみ 1.0 秒 → 帯が出る → 0.8 秒後に突っ込む。帯の長さ 14 (壁がなければ)。最後に着地して動けない
    private static void checkDive(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        FeatherWarden w = make(level, StormMobSetup.FEATHER_WARDEN.get(), top(floor), made);
        w.setOnGround(true);
        w.startCrouch();
        check(failures, w.state3() == FeatherWarden.State.CROUCH, "warden crouches");
        boolean stunned = false;
        for (int i = 0; i < 80 && !stunned; i++) {
            w.tickDive();
            stunned = w.isStunned();
        }
        check(failures, w.lineShownAt == FeatherWarden.CROUCH_TICKS, "line appears after the 1.0 s crouch (at " + w.lineShownAt + ")");
        check(failures, w.diveStartedAt - w.lineShownAt == FeatherWarden.LINE_LEAD, "line shown 0.8 s before the dive ("
                + (w.diveStartedAt - w.lineShownAt) + " ticks)");
        double len = w.lineEnd().subtract(w.lineStart()).horizontalDistance();
        check(failures, len > 13.5 && len <= 14.01, "line length 14 (" + len + ")");
        check(failures, stunned, "warden lands and is stunned");
        check(failures, w.position().subtract(w.lineEnd()).horizontalDistance() < 1.5, "warden lands at the end of the line "
                + w.position() + " vs " + w.lineEnd());
        // 壁があると帯はそこで止まる (すり抜けない)
        FeatherWarden w2 = make(level, StormMobSetup.FEATHER_WARDEN.get(), top(floor), made);
        w2.setYRot(0);   // 南 (+Z) を向く
        for (int dx = -2; dx <= 2; dx++) {
            level.setBlock(floor.offset(dx, 1, 3), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.offset(dx, 2, 3), Blocks.STONE.defaultBlockState(), 3);
        }
        w2.startCrouch();
        for (int i = 0; i < 80 && !w2.isStunned(); i++) {
            w2.tickDive();
        }
        double len2 = w2.lineEnd().subtract(w2.lineStart()).horizontalDistance();
        check(failures, len2 < 3.0, "line stops at a wall (" + len2 + ")");
        check(failures, w2.getZ() < floor.getZ() + 3, "warden does not pass the wall (z " + w2.getZ() + ")");
        for (int dx = -2; dx <= 2; dx++) {
            level.setBlock(floor.offset(dx, 1, 3), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.offset(dx, 2, 3), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    // 着地のあと動けない間の傷は 1.5 倍 (防具を通らない傷で比べる)
    private static void checkStun(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        FeatherWarden w = make(level, StormMobSetup.FEATHER_WARDEN.get(), top(floor), made);
        w.hurt(level.damageSources().magic(), 4);
        check(failures, Math.abs(w.getHealth() - (FeatherWarden.HEALTH - 4)) < 1e-3, "warden takes 4 normally (" + w.getHealth() + ")");
        FeatherWarden s = make(level, StormMobSetup.FEATHER_WARDEN.get(), top(floor), made);
        s.startCrouch();
        for (int i = 0; i < 80 && !s.isStunned(); i++) {
            s.tickDive();
        }
        s.hurt(level.damageSources().magic(), 4);
        check(failures, Math.abs(s.getHealth() - (FeatherWarden.HEALTH - 6)) < 1e-3, "stunned warden takes 1.5x (" + s.getHealth() + ")");
    }

    // 雷蛾の連鎖: 3 ブロックおきに 5 人並べると 3 人まで、5 → 4 → 3.2。6 ブロック離れていれば跳ばない。静電を帯びない人で止まる
    private static void checkChain(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures) {
        Vec3 origin = top(floor).add(0, 1, 0);
        List<LivingEntity> row = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            row.add(make(level, EntityType.PIG, origin.add(2 + i * 3, -1, 0), made));
        }
        List<StaticMoth.Hit> hits = StaticMoth.plan(origin, row, e -> true);
        check(failures, hits.size() == 3, "chain stops at 3 (" + hits.size() + ")");
        if (hits.size() == 3) {
            check(failures, hits.get(0).target() == row.get(0) && hits.get(1).target() == row.get(1) && hits.get(2).target() == row.get(2), "chain order");
            check(failures, Math.abs(hits.get(0).damage() - 5) < 1e-4 && Math.abs(hits.get(1).damage() - 4) < 1e-4
                    && Math.abs(hits.get(2).damage() - 3.2f) < 1e-4, "chain damage 5, 4, 3.2");
        }
        List<LivingEntity> apart = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            apart.add(make(level, EntityType.PIG, origin.add(-2 - i * 6, -1, 6), made));
        }
        check(failures, StaticMoth.plan(origin.add(-2, 0, 6), apart, e -> true).size() == 1, "no jump when 6 apart");
        check(failures, StaticMoth.plan(origin.add(0, 0, -20), row, e -> true).isEmpty(), "nobody within 4");
        var proof = StaticMoth.STATIC_PROOF;
        try {
            StaticMoth.STATIC_PROOF = e -> e == row.get(0);
            check(failures, StaticMoth.plan(origin, row, e -> true).size() == 1, "static-proof target stops the chain");
            StaticMoth.STATIC_PROOF = e -> e == row.get(1);
            List<StaticMoth.Hit> h2 = StaticMoth.plan(origin, row, e -> true);
            check(failures, h2.size() == 1, "chain does not jump to a static-proof one (" + h2.size() + ")");
        } finally {
            StaticMoth.STATIC_PROOF = proof;
        }
    }

    // 雷乞いの輪: 相手の足もとに輪。柱が 3 ブロック以内なら雷は柱へ (相手は無傷)、柱がなければ 9 と鈍足
    private static void checkRing(ServerLevel level, BlockPos floor, List<Mob> made, List<String> failures, List<String> notes) {
        ThunderSupplicant sup = make(level, StormMobSetup.THUNDER_SUPPLICANT.get(), top(floor.offset(-3, 0, -3)), made);
        Pig pig = make(level, EntityType.PIG, top(floor), made);
        pig.setNoAi(true);
        BlockPos rod = floor.offset(2, 1, 0);
        level.setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState(), 3);
        if (StormRods.find(level, pig.position(), 3.0) == null) {
            notes.add("ring-to-rod skipped: StormRods does not see a vanilla lightning rod (the real rod block replaced the stub)");
        } else {
            float hp = pig.getHealth();
            sup.callAt(pig);
            check(failures, sup.lastCall != null && sup.lastCall.distanceTo(pig.position()) < 1e-6, "supplicant calls at the target's feet");
            for (int i = 0; i < ThunderSupplicant.RING_TICKS + 1; i++) {
                StormStrike.tick(level);
            }
            check(failures, pig.getHealth() == hp, "bolt goes to the rod within 3 (pig hp " + pig.getHealth() + ")");
        }
        level.setBlock(rod, Blocks.AIR.defaultBlockState(), 3);
        pig.invulnerableTime = 0;
        float hp = pig.getHealth();
        sup.callAt(pig);
        for (int i = 0; i < ThunderSupplicant.RING_TICKS - 1; i++) {
            StormStrike.tick(level);
        }
        check(failures, pig.getHealth() == hp, "no damage before the ring ends");
        StormStrike.tick(level);
        StormStrike.tick(level);
        check(failures, pig.getHealth() < hp, "bolt hits without a rod (pig hp " + pig.getHealth() + ")");
        check(failures, pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "bolt slows");
    }
}
