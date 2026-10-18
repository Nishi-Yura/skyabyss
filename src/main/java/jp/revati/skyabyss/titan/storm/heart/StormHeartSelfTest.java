package jp.revati.skyabyss.titan.storm.heart;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.titan.dungeon.SealedGateBlock;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.back.StormBackTrials;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import jp.revati.skyabyss.titan.storm.back.StormTestPlayer;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.phys.Vec3;

// 雷翼の心臓の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST storm_heart PASSED / FAILED。
// 模型・音・バー・予告の印があるか、竜骨の間の形 (柱・風穴・竜骨の筋・入口・前室・明るさ)、柱の段と翼が裂ける、
// 閃光の向きの判定 (向いている / 背を向けている / 柱の陰)、風の刃 (しゃがむと当たらない)、雷の網 (すき間)、
// 戦いの始まり・封印・保存されない・段階と咆哮の無敵・段階 2 の 1.2 倍・翼が裂けて 1.5 倍・人数の倍率・段階の高さ、
// 9 つの技が最後まで回る、部屋の外へ出た心臓を戻す・放棄、報酬 (天井・図鑑・欠片は一人一回・作られていない品は外す・
// 巨神ごとの記録・6.4節の模擬)、撃破で雷翼を止めた記録と入口が開く、立ったままでは勝てない (段階 2 に届かない、受ける傷の数)。
// グループ: 心臓は起こしたグループのもの (ほかのグループには見えず・狙わず・傷を受けない)、入口はそのグループにだけ閉じ、
// 撃破はそのグループの記録だけを変える。6 人の倍率
public final class StormHeartSelfTest {

    private StormHeartSelfTest() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel back = StormHeartBattle.back(server);
        if (back != null) {
            forceChunks(back);
        }
    }

    private static void forceChunks(ServerLevel back) {
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -3; dz <= 0; dz++) {
                back.setChunkForced(dx, dz, true);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            run(server, failures, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm_heart crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm_heart PASSED ({})", String.join("; ", notes));
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST storm_heart FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(MinecraftServer server, List<String> failures, List<String> notes) {
        ServerLevel back = StormHeartBattle.back(server);
        if (back == null) {
            failures.add("storm_back is missing");
            return;
        }
        checkAssets(failures);
        checkDesign(failures);
        checkLang(failures);
        forceChunks(back);
        checkArena(back, failures);
        checkJudgements(back, failures);
        checkBattle(back, failures, notes);
        checkLoot(server, back, failures, notes);
        checkDefeat(server, back, failures);
        checkStandingStill(failures, notes);
    }

    private static void checkAssets(List<String> failures) {
        List<String> paths = new ArrayList<>(List.of("/assets/skyabyss/animations/entity/storm_heart.animation.json",
                "/assets/skyabyss_storm_heart/sounds.json"));
        for (int n = 1; n <= StormHeart.PHASE_COUNT; n++) {
            paths.add("/assets/skyabyss/geo/entity/storm_heart_p" + n + ".geo.json");
            paths.add("/assets/skyabyss/textures/entity/storm_heart_p" + n + ".png");
            paths.add("/assets/skyabyss/textures/entity/storm_heart_p" + n + "_glowmask.png");
            paths.add("/assets/skyabyss/textures/gui/boss/storm_heart_frame_p" + n + ".png");
            paths.add("/assets/skyabyss/textures/gui/boss/storm_heart_icon_p" + n + ".png");
        }
        for (StormHeart.Action a : StormHeart.Action.values()) {
            if (a != StormHeart.Action.NONE) {
                paths.add("/assets/skyabyss/textures/gui/storm_heart/warn_" + a.key() + ".png");
            }
        }
        for (String s : List.of("beat", "roar", "summon", "strike", "blade", "chain", "split", "wingbeat", "charge", "feather", "flash",
                "web", "barrage", "death")) {
            paths.add("/assets/skyabyss_storm_heart/sounds/" + s + ".ogg");
        }
        for (String p : paths) {
            try (InputStream in = StormHeartSelfTest.class.getResourceAsStream(p)) {
                check(in != null, "missing asset " + p, failures);
            } catch (java.io.IOException e) {
                failures.add("could not read " + p);
            }
        }
        check(StormHeart.bagFor(0).length == 3 && StormHeart.bagFor(1).length == 3 && StormHeart.bagFor(2).length == 3, "each phase should have 3 moves", failures);
        GrowthRewards.Source xp = GrowthRewards.MOD_ENEMIES.get(SkyAbyss.MODID + ":thunder_wing_heart");
        check(xp != null && xp.xp() == StormLoot.HEART_XP && xp.contentLevel() == StormLoot.STORM_LEVEL, "growth xp entry for the heart is missing or wrong", failures);
    }

    // 技の予告の長さと傷 (12 の 5.3節の表): 招雷 1.2 秒・風の刃 1.2・雷の鎖 1.0・羽ばたき 1.5・帯電の床 1.5・羽根の雨 1.2・閃光 2.0・
    // 雷の網 1.5・招雷の連打 1.0。咆哮 3 秒、翼が裂けて 6 秒、閃光は視線から 60 度、網は一周 6 秒
    private static void checkDesign(List<String> failures) {
        java.util.Map<StormHeart.Action, Integer> warn = java.util.Map.of(
                StormHeart.Action.SUMMON, 24, StormHeart.Action.BLADE, 24, StormHeart.Action.CHAIN, 20,
                StormHeart.Action.WINGBEAT, 30, StormHeart.Action.CHARGE, 30, StormHeart.Action.FEATHER, 24,
                StormHeart.Action.FLASH, 40, StormHeart.Action.WEB, 30, StormHeart.Action.BARRAGE, 20);
        warn.forEach((a, t) -> check(a.warnTicks == t, a + " warning should be " + t + " ticks (" + a.warnTicks + ")", failures));
        check(StormHeart.ROAR_TICKS == 60 && StormHeart.SPLIT_TICKS == 120 && StormHeart.WEB_TICKS == 120, "roar / split / web timings differ from 5.3", failures);
        check(StormHeart.FLASH_CONE_DEG == 60, "flash cone should be 60 degrees", failures);
        float[] dmg = {StormHeart.SUMMON_DAMAGE, StormHeart.BLADE_DAMAGE, StormHeart.CHAIN_DAMAGE, StormHeart.WINGBEAT_DAMAGE,
                StormHeart.CHARGE_DAMAGE, StormHeart.FEATHER_DAMAGE, StormHeart.FLASH_DAMAGE, StormHeart.WEB_DAMAGE, StormHeart.BARRAGE_DAMAGE};
        float[] want = {13f, 14f, 15.5f, 6f, 3f, 10f, 12f, 14f, 10f};
        check(java.util.Arrays.equals(dmg, want), "move damage differs from 5.3: " + java.util.Arrays.toString(dmg), failures);
        // 9 つの技と咆哮のアニメが模型のアニメにある
        try (InputStream in = StormHeartSelfTest.class.getResourceAsStream("/assets/skyabyss/animations/entity/storm_heart.animation.json")) {
            String json = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (StormHeart.Action a : StormHeart.Action.values()) {
                if (a.anim != null) {
                    check(json.contains("\"animation.storm_heart." + a.anim + "\""), "animation missing: " + a.anim, failures);
                }
            }
            for (int n = 1; n < StormHeart.PHASE_COUNT; n++) {
                check(json.contains("\"animation.storm_heart.roar_" + n + "\""), "animation missing: roar_" + n, failures);
            }
        } catch (java.io.IOException e) {
            failures.add("could not read the heart animations");
        }
    }

    // 文は伝承の担当の skyabyss_storm の lang にまとまっている: 断片 (parts/heart.json) の鍵と、音の字幕の鍵が日英ともにある
    private static void checkLang(List<String> failures) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        try (InputStream parts = StormHeartSelfTest.class.getResourceAsStream("/assets/skyabyss_storm/lang/parts/heart.json");
             InputStream sounds = StormHeartSelfTest.class.getResourceAsStream("/assets/skyabyss_storm_heart/sounds.json")) {
            if (parts == null || sounds == null) {
                failures.add("missing parts/heart.json or the heart sounds.json");
                return;
            }
            keys.addAll(com.google.gson.JsonParser.parseString(new String(parts.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().keySet());
            com.google.gson.JsonObject table = com.google.gson.JsonParser.parseString(
                    new String(sounds.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (String name : table.keySet()) {
                keys.add(table.getAsJsonObject(name).get("subtitle").getAsString());
            }
        } catch (java.io.IOException | RuntimeException e) {
            failures.add("could not read the heart text keys: " + e);
            return;
        }
        for (StormHeart.Action a : StormHeart.Action.values()) {
            if (a != StormHeart.Action.NONE) {
                keys.add("gui.skyabyss.storm_heart.warn." + a.key());
            }
        }
        for (int n = 1; n <= StormHeart.PHASE_COUNT; n++) {
            keys.add("gui.skyabyss.boss.thunder_wing_heart.phase." + n);
        }
        for (String code : new String[] {"ja_jp", "en_us"}) {
            String path = "/assets/skyabyss_storm/lang/" + code + ".json";
            try (InputStream in = StormHeartSelfTest.class.getResourceAsStream(path)) {
                if (in == null) {
                    failures.add("missing " + path);
                    continue;
                }
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(
                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : keys) {
                    check(json.has(k), code + " has no " + k, failures);
                }
            } catch (java.io.IOException | RuntimeException e) {
                failures.add("could not read " + path + ": " + e);
            }
        }
    }

    private static void checkArena(ServerLevel back, List<String> failures) {
        StormHeartArena.build(back);
        check(StormHeartArena.isBuilt(back), "chamber not recognised as built", failures);
        int f = StormHeartArena.FLOOR;
        for (int i = 0; i < 4; i++) {
            BlockPos r = StormHeartArena.rod(i);
            check(back.getBlockState(r.above(StormHeartArena.ROD_HEIGHT - 1)).is(Blocks.END_ROD), "rod " + i + " has no glowing tip", failures);
            check(back.getBlockState(r.above(1)).is(Blocks.WAXED_COPPER_BULB), "rod " + i + " has no charge bulbs", failures);
            check(Math.abs(StormHeartArena.dist(r.getX(), r.getZ()) - 9) < 1.0, "rod " + i + " is not about 9 from the centre", failures);
        }
        for (int k = 0; k < 4; k++) {
            Vec3 h = StormHeartArena.hole(k);
            BlockPos g = BlockPos.containing(h.x, StormHeartArena.walkY(h.x, h.z) - 1, h.z);
            check(back.getBlockState(g).is(Blocks.WAXED_COPPER_GRATE), "wind hole " + k + " has no grate at " + g, failures);
        }
        // 竜骨の筋は 1 段低い
        check(StormHeartArena.walkY(StormHeartArena.CX + 0.5, StormHeartArena.CZ + 5.5) == f - 1
                && StormHeartArena.walkY(StormHeartArena.CX + 4.5, StormHeartArena.CZ + 0.5) == f, "keel line height is wrong", failures);
        check(back.getBlockState(new BlockPos(StormHeartArena.CX, f - 1, StormHeartArena.CZ + 3)).isAir()
                && back.getBlockState(new BlockPos(StormHeartArena.CX, f - 2, StormHeartArena.CZ + 3)).isSolid(), "keel line is not lowered", failures);
        check(back.getBlockState(new BlockPos(StormHeartArena.CX + 3, StormHeartArena.CEILING - 1, StormHeartArena.CZ + 3)).isAir()
                && !back.getBlockState(new BlockPos(StormHeartArena.CX + 3, StormHeartArena.CEILING, StormHeartArena.CZ + 3)).isAir(), "chamber ceiling is wrong", failures);
        for (BlockPos g : StormHeartArena.gate()) {
            check(back.getBlockState(g).is(ModBlocks.SEALED_GATE.get()) && back.getBlockState(g).getValue(SealedGateBlock.OPEN)
                    && GroupGates.isOpen(back, GROUP_A, g), "entrance is not an open gate at " + g, failures);
        }
        check(StormHeartArena.ante().contains(StormHeartArena.anteSpawn()), "antechamber spawn is outside the antechamber", failures);
        check(!StormHeartArena.arena().contains(StormHeartArena.anteSpawn()), "antechamber spawn is inside the fight", failures);
        check(back.getBlockState(BlockPos.containing(StormHeartArena.anteSpawn()).below()).isSolid(), "antechamber has no floor", failures);
        // 柱の段: 満ちた段だけ灯る。4 本満ちると allFull
        StormHeartArena.resetRods(back);
        StormHeartArena.addCharge(back, 0);
        StormHeartArena.addCharge(back, 0);
        check(StormHeartArena.charge(0) == 2 && back.getBlockState(StormHeartArena.rod(0).above(2)).getValue(CopperBulbBlock.LIT)
                && !back.getBlockState(StormHeartArena.rod(0).above(3)).getValue(CopperBulbBlock.LIT), "rod bulbs do not show the charge", failures);
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 4; k++) {
                StormHeartArena.addCharge(back, i);
            }
        }
        check(StormHeartArena.allFull() && StormHeartArena.charge(1) == StormHeartArena.ROD_STAGES, "rods do not cap at 3 or are not all full", failures);
        StormHeartArena.resetRods(back);
        check(!StormHeartArena.allFull() && StormHeartArena.charge(3) == 0, "rods did not reset", failures);
        check(StormHeartArena.nearestRod(StormHeartArena.rod(2).getX() + 2.5, StormHeartArena.rod(2).getZ() + 0.5, 3.0) == 2
                && StormHeartArena.nearestRod(StormHeartArena.CX + 0.5, StormHeartArena.CZ + 0.5, 3.0) < 0, "rod reach test is wrong", failures);
    }

    // 閃光・風の刃・雷の網の判定 (人を置かずに、判定の関数を部屋の中で試す)
    private static void checkJudgements(ServerLevel back, List<String> failures) {
        int f = StormHeartArena.FLOOR;
        Vec3 core = new Vec3(StormHeartArena.CX + 0.5, f + StormHeart.heightFor(2, false) + 2.2, StormHeartArena.CZ + 0.5);
        // 南東の柱の向こう (中心から柱を通る線の先) に立つ人
        BlockPos rod = StormHeartArena.rod(2);
        Vec3 dir = new Vec3(rod.getX() + 0.5 - core.x, 0, rod.getZ() + 0.5 - core.z).normalize();
        Vec3 hiddenEye = new Vec3(rod.getX() + 0.5 + dir.x * 2.5, f + 1.62, rod.getZ() + 0.5 + dir.z * 2.5);
        Vec3 openEye = new Vec3(StormHeartArena.CX + 8.5, f + 1.62, StormHeartArena.CZ + 0.5);
        Vec3 toCore = core.subtract(openEye).normalize();
        check(StormHeart.flashHits(back, openEye, toCore, core, null), "flash should hit someone looking at the heart", failures);
        check(!StormHeart.flashHits(back, openEye, toCore.scale(-1), core, null), "flash hit someone with their back turned", failures);
        Vec3 side = new Vec3(-toCore.z, 0, toCore.x);
        check(!StormHeart.flashHits(back, openEye, side, core, null), "flash hit someone looking 90 degrees away", failures);
        Vec3 sixty = toCore.scale(Math.cos(Math.toRadians(50))).add(side.scale(Math.sin(Math.toRadians(50))));
        check(StormHeart.flashHits(back, openEye, sixty, core, null), "flash should hit someone looking 50 degrees off", failures);
        check(!StormHeart.flashHits(back, hiddenEye, core.subtract(hiddenEye).normalize(), core, null), "flash hit someone behind a rod", failures);
        // 風の刃: 立っている人 (背 1.8) は当たり、しゃがんだ人 (背 1.5) は当たらない。跳んで刃の上へ出ても当たらない
        check(StormHeart.bladeHits(f, f + 1.8, f), "blade missed a standing player", failures);
        check(!StormHeart.bladeHits(f, f + 1.5, f), "blade hit a crouching player", failures);
        check(StormHeart.bladeHits(f - 1, f + 0.8, f - 1) && !StormHeart.bladeHits(f - 1, f + 0.5, f - 1), "blade height in the keel line is wrong", failures);
        check(!StormHeart.bladeHits(f + 2.5, f + 4.3, f), "blade hit a player above it", failures);
        // 雷の網: 腕の上は当たり、すき間と腕の外は当たらない
        check(StormHeart.webHits(8, 0.2, 0, 4), "web missed a player on the arm", failures);
        check(!StormHeart.webHits(5, 0, 0, 4), "web hit a player in the gap", failures);
        check(!StormHeart.webHits(0, 8, 0, 4), "web hit a player away from the arm", failures);
        check(!StormHeart.webHits(-8, 0, 0, 4), "web hit the opposite side of a single arm", failures);
    }

    // 段階 2 の高低の拍 (HIGH_TICKS 高く、PERCH_TICKS 下りる)。時計の式と、心臓が実際に上がり下りすることを確かめる。
    // heart は、段階 2 に入って 80 tick たった (時計が 80 前後の) 心臓
    private static void checkRiseRhythm(ServerLevel back, StormHeart heart, List<String> failures) {
        int cycle = StormHeart.HIGH_TICKS + StormHeart.PERCH_TICKS;
        int perched = 0;
        int longestHigh = 0;
        int run = 0;
        for (int clock = 0; clock < cycle * 3; clock++) {
            if (StormHeart.perchedAt(clock)) {
                perched++;
                run = 0;
            } else {
                longestHigh = Math.max(longestHigh, ++run);
            }
        }
        check(perched == StormHeart.PERCH_TICKS * 3 && longestHigh <= StormHeart.HIGH_TICKS, "rise rhythm is not high " + StormHeart.HIGH_TICKS + " / low "
                + StormHeart.PERCH_TICKS + " (low " + perched + ", longest high " + longestHigh + ")", failures);
        check(StormHeart.PERCH_TICKS >= 100 && StormHeart.HIGH_TICKS <= 240, "phase 2 heart stays out of reach too long", failures);
        double floor = StormHeartArena.FLOOR;
        double low = floor + StormHeart.heightFor(0, false);
        check(low < floor + 1.62 + 3.0, "perched heart is out of melee reach (y " + low + ")", failures);
        // 実際の動き: 時計が 80 前後から 320 tick 進める。160 から下りて床の近くへ、320 を過ぎて次の周の高い側で再び昇り風の上の端へ戻る
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (int i = 0; i < cycle; i++) {
            tickHeart(heart, 1);
            lowest = Math.min(lowest, heart.getY());
            highest = Math.max(highest, heart.getY());
        }
        check(lowest <= low + 0.6, "heart never came down to melee height in phase 2 (lowest y " + lowest + ")", failures);
        check(highest >= StormHeartArena.UPDRAFT_TOP - 0.3, "heart did not go back up (highest y " + highest + ")", failures);
        check(heart.getY() >= StormHeartArena.UPDRAFT_TOP - 0.6, "heart is not back up at the start of the next cycle (y " + heart.getY() + ")", failures);
    }

    // 自己テストのグループ (合成の鍵。一人のグループ = 人の UUID)
    static final UUID GROUP_A = UUID.fromString("5c1e0a2b-0000-4000-8000-0000005ea001");
    static final UUID GROUP_B = UUID.fromString("5c1e0a2b-0000-4000-8000-0000005eb002");

    private static StormHeart startHeart(ServerLevel back, List<String> failures) {
        StormHeartBattle.abandonAll(back);
        RoomLocks.release(StormHeartBattle.ROOM);
        StormBackTrials.resetGroup(back, GROUP_A, false, null, true, false);
        check(StormHeartBattle.startNow(back, GROUP_A), "battle did not start", failures);
        StormHeart heart = StormHeartBattle.current(back, GROUP_A);
        if (heart == null) {
            failures.add("no heart after starting (difficulty " + back.getDifficulty() + ")");
        } else {
            heart.skipIntro();
        }
        return heart;
    }

    // 倒れる演出 (100 tick) を最後まで流す。撃破の報告は演出が終わってから出る
    // グループ: A の心臓は A のもの。B には見えず、B を狙わず、B の傷は届かない。6 人の倍率
    private static void checkGroups(ServerLevel back, StormHeart heart, List<String> failures) {
        StormTestPlayer pa = StormTestPlayer.make(back, GROUP_A, "[heart_a]");
        StormTestPlayer pb = StormTestPlayer.make(back, GROUP_B, "[heart_b]");
        check(GROUP_A.equals(heart.groupId()), "the heart does not belong to the group that woke it (" + heart.groupId() + ")", failures);
        check(GroupOwned.visibleTo(heart, pa) && !GroupOwned.visibleTo(heart, pb), "the heart is shown to another group", failures);
        check(heart.canAttack(pa) && !heart.canAttack(pb), "the heart may target another group", failures);
        check(!damageCancelled(heart, heart.damageSources().playerAttack(pa)) && damageCancelled(heart, heart.damageSources().playerAttack(pb)),
                "damage from another group should not reach the heart", failures);
        check(Math.abs(StormHeart.scaleFor(6) - 4.0f) < 1e-4 && StormHeart.scaleFor(7) > StormHeart.scaleFor(6), "party of 6 scale should be 4.0 (uncapped)", failures);
    }

    static boolean damageCancelled(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        var event = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(victim,
                new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 4f));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static void finishDeath(StormHeart heart) {
        for (int i = 0; i < jp.revati.skyabyss.entity.HeartBoss.DEATH_TICKS + 5 && !heart.isRemoved(); i++) {
            heart.tick();
        }
    }

    private static void tickHeart(StormHeart heart, int ticks) {
        for (int i = 0; i < ticks && heart.isAlive(); i++) {
            heart.tick();
        }
    }

    private static void checkBattle(ServerLevel back, List<String> failures, List<String> notes) {
        StormHeart heart = startHeart(back, failures);
        if (heart == null) {
            return;
        }
        check(StormHeartArena.sealed(back, GROUP_A), "entrance not sealed when the fight starts", failures);
        check(!StormHeartArena.sealed(back, GROUP_B), "entrance sealed for another group", failures);
        checkGroups(back, heart, failures);
        check(heart.getTags().contains(TitanBackTrials.HEART_TAG) && heart.isBound(), "heart not tagged / bound", failures);
        check(!heart.shouldBeSaved(), "heart would be saved to disk", failures);
        check(Math.abs(heart.getMaxHealth() - StormHeart.MAX_HEALTH) < 0.01, "max health is " + heart.getMaxHealth(), failures);
        check(StormHeart.phaseFor(1f) == 0 && StormHeart.phaseFor(0.67f) == 0 && StormHeart.phaseFor(0.65f) == 1
                && StormHeart.phaseFor(0.34f) == 1 && StormHeart.phaseFor(0.32f) == 2, "phase thresholds are wrong", failures);
        check(Math.abs(StormHeart.takenFor(1, false, 10, 1) - 12f) < 1e-4 && Math.abs(StormHeart.takenFor(0, false, 10, 1) - 10f) < 1e-4
                && Math.abs(StormHeart.takenFor(0, true, 10, 1) - 15f) < 1e-4
                && Math.abs(StormHeart.takenFor(2, false, 16, StormHeart.scaleFor(2)) - 10f) < 1e-4, "damage taken multipliers are wrong", failures);
        // 段階の高さ: 段階 2 は 7 上がり、下の端が昇り風の上の端。段階 3 は床の 2 上
        check(Math.abs(StormHeart.heightFor(1, false) - StormHeart.heightFor(0, false) - 7.0) < 0.01
                && Math.abs(StormHeartArena.FLOOR + StormHeart.heightFor(1, false) - StormHeartArena.UPDRAFT_TOP) < 0.01
                && StormHeart.heightFor(2, false) == 2.0, "phase heights are wrong", failures);
        // 翼が裂ける: 落ちて 1.5 倍、6 秒で戻る
        heart.forceSplitForTest(back);
        check(heart.isSplit() && heart.splitTicks() == StormHeart.SPLIT_TICKS && heart.targetY() < StormHeartArena.FLOOR + 1, "wings did not split", failures);
        heart.invulnerableTime = 0;
        float s0 = heart.getHealth();
        heart.hurt(heart.damageSources().magic(), 10f);
        check(Math.abs((s0 - heart.getHealth()) - 15f) < 0.6f, "split heart should take 1.5x (took " + (s0 - heart.getHealth()) + ")", failures);
        tickHeart(heart, StormHeart.SPLIT_TICKS + 2);
        check(!heart.isSplit(), "split did not end", failures);
        // 咆哮: 体力を 60% にすると、咆哮 (無敵) のあと段階 2
        heart.setHealth(heart.getMaxHealth() * 0.6f);
        tickHeart(heart, 3);
        check(heart.isRoaring() && heart.currentAction() == StormHeart.Action.ROAR, "no roar after dropping to 60%", failures);
        float before = heart.getHealth();
        heart.hurt(heart.damageSources().generic(), 50f);
        check(heart.getHealth() == before, "heart took damage while roaring", failures);
        heart.invulnerableTime = 0;
        tickHeart(heart, StormHeart.ROAR_TICKS + 2);
        check(heart.getPhase() == 1 && !heart.isRoaring(), "phase did not advance after the roar (phase " + heart.getPhase() + ")", failures);
        tickHeart(heart, 80);
        check(Math.abs(heart.getY() - StormHeartArena.UPDRAFT_TOP) < 0.3, "heart did not rise in phase 2 (y " + heart.getY() + ")", failures);
        check(Math.abs(heart.bossBarProgress() - heart.getHealth() / heart.getMaxHealth()) < 0.01, "boss bar progress does not follow health", failures);
        // 段階 2 の高低の拍: 高い (昇り風の上の端) → 下りる (床の近く。立ったまま殴れる) → また上がる、を時計どおりにくり返す
        checkRiseRhythm(back, heart, failures);
        heart.invulnerableTime = 0;
        float h0 = heart.getHealth();
        heart.hurt(heart.damageSources().magic(), 10f);
        check(Math.abs((h0 - heart.getHealth()) - 12f) < 0.6f, "open wings should take 1.2x (took " + (h0 - heart.getHealth()) + ")", failures);
        // 9 つの技が最後まで回る (人がいないので当たらない)
        int ran = 0;
        for (int phase = 0; phase < 3; phase++) {
            heart.forcePhase(phase);
            for (StormHeart.Action a : StormHeart.bagFor(phase)) {
                heart.forceAction(a);
                tickHeart(heart, 220);
                if (heart.currentAction() == StormHeart.Action.NONE) {
                    ran++;
                } else {
                    failures.add(a + " did not finish");
                }
            }
        }
        notes.add(ran + "/9 moves ran to the end");
        // 部屋の外へ出た心臓は戻る。誰もいなければ 3 秒で放棄
        heart.moveTo(StormHeartArena.CX + 0.5, StormHeartArena.FLOOR + 3, StormHeartArena.CZ + 20.5);
        StormHeartBattle.tick(back);
        check(heart.distanceToSqr(StormHeartArena.center()) < 100, "heart outside the room was not brought back", failures);
        for (int i = 0; i < 6; i++) {
            StormHeartBattle.tick(back);
        }
        check(!StormHeartBattle.room(GROUP_A).active() && !heart.isAlive() && !StormHeartArena.sealed(back, GROUP_A), "empty room was not abandoned", failures);
    }

    private static void checkLoot(MinecraftServer server, ServerLevel back, List<String> failures, List<String> notes) {
        RandomSource random = RandomSource.create(42);
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        StormLoot.Roll first = StormLoot.rollHeart(r, random, id -> true);
        check(first.draws().stream().anyMatch(d -> d.id().equals(StormLoot.CORE)), "first clear has no core", failures);
        check(first.draws().stream().anyMatch(d -> d.id().equals(StormLoot.SHARD) && d.count() >= 4 && d.count() <= 7), "shards not 4-7", failures);
        check(first.draws().stream().anyMatch(d -> d.id().equals(StormLoot.RESONANCE_SHARD) && d.count() >= 1 && d.count() <= 3), "resonance shards not 1-3", failures);
        check(StormLoot.collection().size() == 10, "collection should have 10 items", failures);
        TitanMemoryData.Record p = new TitanMemoryData.Record();
        p.memory = TitanLoot.HARD_PITY - 1;
        StormLoot.Roll pity = StormLoot.rollHeart(p, random, id -> true);
        check(pity.guaranteed() != null && p.memory == 0, "hard pity did not guarantee a new piece", failures);
        TitanMemoryData.Record all = new TitanMemoryData.Record();
        all.owned.addAll(StormLoot.collection());
        all.memory = TitanLoot.HARD_PITY - 1;
        StormLoot.Roll full = StormLoot.rollHeart(all, random, id -> true);
        check(full.pityShards() && all.memory == 0, "full collection pity should give shards", failures);
        TitanMemoryData.Record none = new TitanMemoryData.Record();
        for (int i = 0; i < 30; i++) {
            StormLoot.rollHeart(none, random, id -> false);
        }
        check(none.owned.isEmpty(), "unmade items were recorded", failures);
        // 6.4節の模擬 (12 の表: 図鑑 10 品で平均 13.9)
        double mean = StormLoot.meanClearsToComplete(4000, 7, id -> true);
        check(mean > 11 && mean < 17, "mean clears to complete the collection is " + mean, failures);
        notes.add(String.format("collection in %.1f clears on average", mean));
        List<String> unmade = StormLoot.allIds().stream().filter(id -> !StormLoot.registered(id)).toList();
        notes.add("not made yet (skipped from the draw): " + unmade.size());
        // 撃破の報酬 (いない人の分は預かる)。欠片は一人一回。巨神ごとの記録
        UUID fake = UUID.randomUUID();
        TitanMemoryData memory = TitanMemoryData.get(server);
        boolean worldBefore = ProgressData.get(server).worldStopped(StormLoot.TITAN);
        StormLoot.onHeartDefeat(back, List.of(), List.of(fake));
        TitanMemoryData.Record rec = memory.peek(fake, StormLoot.TITAN);
        check(rec != null && rec.clears == 1 && rec.fragment && rec.firstCleared && rec.choicePending, "defeat record is wrong", failures);
        check(memory.peek(fake, TitanLoot.STONE_WHALE) == null && memory.peek(fake, "tide_shell") == null, "storm defeat wrote to another titan's record", failures);
        check(memory.hasPending(fake), "rewards for an absent player were not held", failures);
        TitanMemoryData.Pending held = memory.takePending(fake);
        long fragments = held.items.stream().filter(s -> TitanLoot.id(s.getItem()).equals(StormLoot.SKY_KEY_FRAGMENT)).count();
        check(fragments == 1, "first defeat should give one sky key fragment (" + fragments + ")", failures);
        if (StormLoot.registered(StormLoot.SHARD)) {
            check(held.items.stream().anyMatch(s -> TitanLoot.id(s.getItem()).equals(StormLoot.SHARD) && s.getCount() >= 4), "heart reward did not give shards", failures);
        }
        StormLoot.onHeartDefeat(back, List.of(), List.of(fake));
        if (memory.hasPending(fake)) {
            held = memory.takePending(fake);
            check(held.items.stream().noneMatch(s -> TitanLoot.id(s.getItem()).equals(StormLoot.SKY_KEY_FRAGMENT)), "second defeat gave another fragment", failures);
        }
        check(Progression.stopped(server, fake, StormLoot.TITAN), "defeat did not record the thunder wing as stopped", failures);
        check(ProgressData.get(server).worldStopped(StormLoot.TITAN), "world progress did not record the thunder wing", failures);
        ProgressData.get(server).clearPersonal(fake, StormLoot.TITAN);
        if (!worldBefore) {
            ProgressData.get(server).clearWorld(StormLoot.TITAN);
        }
    }

    private static void checkDefeat(MinecraftServer server, ServerLevel back, List<String> failures) {
        boolean worldBefore = ProgressData.get(server).worldStopped(StormLoot.TITAN);
        StormHeart heart = startHeart(back, failures);
        if (heart == null) {
            return;
        }
        heart.forcePhase(2);
        heart.setHealth(1f);
        heart.invulnerableTime = 0;
        heart.hurt(heart.damageSources().genericKill(), 1000f);
        StormProgress a = StormProgress.of(back, GROUP_A);
        StormProgress b = StormProgress.of(back, GROUP_B);
        int epochB = b.epoch();
        check(!heart.isAlive(), "heart did not die", failures);
        check(!a.heartDefeated() && StormHeartBattle.room(GROUP_A).active(), "defeat was reported before the death animation finished", failures);
        finishDeath(heart);
        check(heart.isRemoved(), "heart was not removed after the death animation", failures);
        check(a.heartDefeated() && a.everDefeated(), "defeat was not recorded in the group's record", failures);
        check(!b.heartDefeated() && b.epoch() == epochB, "the heart killed by group A changed group B's record", failures);
        check(!StormHeartBattle.room(GROUP_A).active() && !StormHeartArena.sealed(back, GROUP_A), "entrance did not reopen after the defeat", failures);
        check(GroupGates.isOpen(back, GROUP_A, StormHeartArena.gate().get(0)), "seal left after the defeat", failures);
        int epochA = a.epoch();
        StormHeartBattle.reawaken(back, GROUP_A);
        check(!a.heartDefeated() && a.epoch() != epochA, "reawaken did not reset the group's heart", failures);
        check(b.epoch() == epochB, "reawakening group A's heart changed group B's trial number", failures);
        BackGroupData.get(back).remove(GROUP_A);
        BackGroupData.get(back).remove(GROUP_B);
        if (!worldBefore) {
            ProgressData.get(server).clearWorld(StormLoot.TITAN);
        }
    }

    // 立ったままでは勝てない (03 の 9.8節の目安: 推奨 Lv24、防具 21 / 14 と一式 ×0.8、武器 +0 の 1 秒あたり 15.2)
    // - 段階 2 の心臓は床から届かない (下の端が床から 8.5。立った人の目 1.62 + 届く距離 3 より上)。昇り風に乗らないと削れない
    // - 段階 1 と 3 の、立ったまま (しゃがまず、柱から離れ、心臓を見て殴り続ける) 人が受ける傷の数 (手ごたえ)
    private static void checkStandingStill(List<String> failures, List<String> notes) {
        check(StormHeart.heightFor(1, false) > 1.62 + 3.0 + 0.5, "phase 2 heart can be hit from the floor", failures);
        GearCurve.Stage st = GearCurve.STAGES.get(2);
        double health = GearCurve.maxHealth(st.level());
        double perHit = st.dps0() / GearCurve.SWORD_SPEED;
        double dps = perHit * GearCurve.armorTaken(perHit, 9, 6) * GearCurve.SWORD_SPEED;
        double taken1 = (taken(st, StormHeart.SUMMON_DAMAGE) + taken(st, StormHeart.BLADE_DAMAGE) + taken(st, StormHeart.CHAIN_DAMAGE)) / 3;
        double taken3 = (taken(st, StormHeart.FLASH_DAMAGE) + 2 * taken(st, StormHeart.WEB_DAMAGE) + 3 * taken(st, StormHeart.BARRAGE_DAMAGE)) / 3;
        // 技の間隔 = 間 (平均) + 技の長さ (平均)
        double cycle1 = (58 + (40 + 55 + 32) / 3.0) / 20.0;
        double cycle3 = (38 + (50 + 156 + 66) / 3.0) / 20.0;
        double hp1 = StormHeart.MAX_HEALTH * (1 - StormHeart.PHASE_TWO_AT);
        double hp3 = StormHeart.MAX_HEALTH * StormHeart.PHASE_THREE_AT;
        double damage = hp1 / dps / cycle1 * taken1 + hp3 / dps / cycle3 * taken3;
        double load = damage / health;
        notes.add(String.format("standing still: phase 2 out of reach, phases 1+3 deal %.1f of %.0f health (load %.2f), %.1f hits to fall",
                damage, health, load, health / taken1));
        check(load > 0.8, "standing still takes too little damage in phases 1 and 3 (load " + load + ")", failures);
        check(health / taken1 > 5 && health / taken1 < 10, "hits to fall should be about 7 (" + health / taken1 + ")", failures);
    }

    // 明るさ (光の計算は別の糸で進むので、部屋を建ててから 2 秒待って測る。SelfTestRunner の storm_heart (light) の段)。
    // 床の上 (柱と柱のあいだ、端の近く、竜骨の筋) で 11 以上 (予告の粒が暗くならない)
    public static void checkLight(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        ServerLevel back = StormHeartBattle.back(server);
        if (back == null) {
            failures.add("storm_back is missing");
        } else {
            int f = StormHeartArena.FLOOR;
            int min = 15;
            for (BlockPos p : List.of(new BlockPos(StormHeartArena.CX + 10, f, StormHeartArena.CZ + 2), new BlockPos(StormHeartArena.CX - 3, f, StormHeartArena.CZ - 11),
                    new BlockPos(StormHeartArena.CX + 2, f, StormHeartArena.CZ + 12), new BlockPos(StormHeartArena.CX, f - 1, StormHeartArena.CZ + 8),
                    new BlockPos(StormHeartArena.CX - 13, f, StormHeartArena.CZ - 1))) {
                int light = back.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
                min = Math.min(min, light);
                check(light >= 11, "chamber floor too dark at " + p + " (" + light + ")", failures);
            }
            if (failures.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST storm_heart light PASSED (darkest floor block light {})", min);
            }
        }
        failures.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST storm_heart light FAILED: {}", x));
    }

    private static double taken(GearCurve.Stage st, float damage) {
        return damage * GearCurve.armorTaken(damage, st.armor(), st.toughness()) * st.setMultiplier();
    }
}
