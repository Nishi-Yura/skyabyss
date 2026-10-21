package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupEvents;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 開発用の自己テスト。環境変数 SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く。
// 岩鯨の背を建て、遊べる形になっているか (通れる・届く・近道がない・空に穴が開いていない・元に戻せる) を確かめて、サーバーを止める。
// 自己テストの入口と順番は SelfTestRunner
public final class TitanBackSelfTest {
    private TitanBackSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき)。1 tick に 1 段ずつ:
    // 建てる → 部屋・扉・アスレ・水路などを調べる → リセットを調べる (ここまでは最初に流す)。
    // 番兵を湧かせて 5 秒動かす → ピースフルで眠るか → 難易度を戻して封印が外れるか → まとめを書いてサーバーを止める (最後に流す)
    public static List<SelfTestRunner.Step> buildSteps() {
        return List.of(
                SelfTestRunner.Step.once("titan back (build)", server -> guarded(() -> {
                    PENDING.clear();
                    SPAWNED.clear();
                    stage = 0;
                    originalDifficulty = null;
                    ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
                    if (back == null) {
                        PENDING.add("titan_back dimension is missing");
                        return;
                    }
                    TitanBackData built = TitanBackData.get(back);
                    TitanBackBuilder.build(back, built);
                    TitanBackTrials.installGates(back);
                    if (built.needsBuild() || built.layoutVersion() != LAYOUT_VERSION) {
                        PENDING.add("layout version was not recorded after building (" + built.layoutVersion() + ")");
                    }
                })),
                SelfTestRunner.Step.once("titan back (layout)", server -> withBack(server, back -> {
                    checkSurface(back, PENDING);
                    checkWhale(back, PENDING);
                    checkRooms(back, PENDING);
                    checkDoors(back, PENDING);
                    checkCourses(back, PENDING);
                    checkHalls(back, PENDING);
                    checkMaze(back, PENDING);
                    checkTreasury(back, PENDING);
                    checkHeart(back, PENDING);
                    checkRestPoints(back, PENDING);
                    checkSigns(back, PENDING);
                    forceMazeChunks(back, true);
                })),
                // 詰み監査 (背中の到達・戻れるか・はしご・床の穴)。調べる部品は titan/audit/BackReach
                SelfTestRunner.Step.once("titan back (reach)", server -> withBack(server, back -> {
                    // アスレチックと迷路の形は、試練が戻るたびに変わる。何通りかで調べる (最初の形 + 4 回のリセット)
                    java.util.Map<String, Integer> seen = new java.util.LinkedHashMap<>();
                    for (int round = 0; round < 5; round++) {
                        if (round > 0) {
                            TitanBackTrials.resetTrials(back);
                        }
                        jp.revati.skyabyss.titan.audit.BackReach.Report r = TitanBackAudit.run(back);
                        if (round == 0) {
                            r.notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST {}", n));
                        }
                        r.failures.forEach(f -> seen.merge(f, 1, Integer::sum));
                    }
                    seen.forEach((f, n) -> {
                        String line = f + (n < 5 ? " (in " + n + " of 5 shapes)" : "");
                        SkyAbyss.LOGGER.warn("REACH {}", line);
                        PENDING.add(line);
                    });
                })),
                SelfTestRunner.Step.once("titan back (groups)", server -> withBack(server, back -> checkGroups(server, back))),
                // 闇の水路の灯 (docs/design/23): 置き場・摘む・ともす・なだめる・戸・グループごと・リセット・詰まない・持ち歩きの光・既知の道
                SelfTestRunner.Step.once("titan back (lights)", server -> withBack(server, back -> PENDING.addAll(AnemoneSelfTest.run(back)))),
                // 水が流れ広がるのを待ってから、息継ぎの空気が残っているかを数える (水は 5 tick ごとに一段広がる)
                SelfTestRunner.Step.once("titan back (maze settled)", MAZE_SETTLE_TICKS, server -> withBack(server, back -> checkPockets(back, PENDING))),
                SelfTestRunner.Step.once("titan back (reset)", server -> withBack(server, back -> {
                    checkReset(back, PENDING);
                    PENDING.addAll(jp.revati.skyabyss.loot.TitanLoot.selfTest());
                    String mapDir = System.getenv("SKYABYSS_SELFTEST_MAP");
                    if (mapDir != null) {
                        writeMaps(back, java.nio.file.Path.of(mapDir));
                    }
                })),
                SelfTestRunner.Step.once("titan back (reset maze settled)", MAZE_SETTLE_TICKS, server -> withBack(server, back -> {
                    List<String> after = new ArrayList<>();
                    checkPockets(back, after);
                    after.forEach(f -> PENDING.add("after reset: " + f));
                    forceMazeChunks(back, false);
                })),
                // 休み場の 5×5 と、版を上げる建て直しで前の配置が残らないこと (版 27)
                SelfTestRunner.Step.once("titan back (rebuild)", server -> withBack(server, back -> PENDING.addAll(UndersideRebuildSelfTest.run(back)))));
    }

    public static List<SelfTestRunner.Step> trialSteps() {
        return List.of(
                SelfTestRunner.Step.once("titan back (guards)", server -> withBack(server, TitanBackSelfTest::spawnGuards)),
                // 番兵たちを 5 秒動かしてから
                SelfTestRunner.Step.once("titan back (peaceful)", 100, server -> withBack(server, back -> {
                    checkGuardsAlive();
                    enterPeaceful(server, back);
                })),
                SelfTestRunner.Step.once("titan back (unseal)", 40, server -> withBack(server, back -> leavePeaceful(server, back))),
                SelfTestRunner.Step.once("titan back", 20, TitanBackSelfTest::finish));
    }

    private static void guarded(Runnable check) {
        try {
            check.run();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan back crashed", e);
            PENDING.add("crashed: " + e);
        }
    }

    private static void withBack(MinecraftServer server, java.util.function.Consumer<ServerLevel> check) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            guarded(() -> check.accept(back));
        }
    }

    private static final List<String> PENDING = new ArrayList<>();
    private static final List<net.minecraft.world.entity.Mob> SPAWNED = new ArrayList<>();

    // 2 段目: ピースフルにすると、試練が眠るか (祠の封印・番兵と心臓が消える・閉じた扉が開く)。
    // 3 段目: 難易度を戻すと、祠の封印が外れるか
    private static int stage;
    private static net.minecraft.world.Difficulty originalDifficulty;

    // 番兵たちを実際に湧かせて、しばらく動かしてみる (落ちたり消えたりしないか)。
    // プレイヤーがいないので、湧かせる場所のチャンクを読み込んだままにしておく
    private static void spawnGuards(ServerLevel back) {
        BlockPos at = HUB.center();
        BlockPos waterAt = mazeCell(MAZE_START_I, MAZE_START_J);
        for (BlockPos c : List.of(at, waterAt)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    back.setChunkForced((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz, true);
                }
            }
        }
        for (String kind : List.of("sentinel", "archer", "crawler", "captain")) {
            var mob = TitanBackTrials.spawnGuard(back, kind, at);
            if (mob == null) {
                PENDING.add("could not spawn " + kind);
            } else {
                SPAWNED.add(mob);
                // 試練の番兵は保存しない (保存して終了したあとに二重に湧かないように)
                if (mob.shouldBeSaved()) {
                    PENDING.add(kind + " spawned by the trials would be saved to disk");
                }
            }
        }
        BlockPos water = mazeCell(MAZE_START_I, MAZE_START_J).offset(1, 1, 1);
        var maw = jp.revati.skyabyss.registry.ModEntities.WATERWAY_MAW.get().create(back);
        if (maw != null) {
            maw.moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5);
            back.addFreshEntity(maw);
            SPAWNED.add(maw);
        }
    }

    private static void checkGuardsAlive() {
        for (var mob : SPAWNED) {
            if (!mob.isAlive() || mob.isRemoved()) {
                PENDING.add(mob.getType() + " did not survive 5 seconds (" + mob.getRemovalReason() + ", hp " + mob.getHealth() + ")");
            }
        }
    }

    private static void enterPeaceful(MinecraftServer server, ServerLevel back) {
        stage = 1;
        SPAWNED.forEach(net.minecraft.world.entity.Entity::discard);
        SPAWNED.clear();
        // 祠の封印の場所を読み込んでおく (プレイヤーがいないので)
        BlockPos seal = shrineSeal().get(0);
        back.setChunkForced(seal.getX() >> 4, seal.getZ() >> 4, true);
        // 戦いの途中を作る: 第一の広間の入口と心臓の間の入口を閉じ、試練の番兵と心臓を置く
        TitanBackTrials.seal(back, TEST_GROUP, E1_ENTRANCE);
        TitanBackTrials.seal(back, TEST_GROUP, HEART_GATE);
        var guard = TitanBackTrials.spawnGuard(back, "sentinel", HUB.center(), TEST_GROUP);
        if (guard != null) {
            SPAWNED.add(guard);
        }
        var heart = jp.revati.skyabyss.registry.ModEntities.STONE_WHALE_HEART.get().create(back);
        if (heart != null) {
            BlockPos at = HUB.center();
            heart.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0f, 0f);
            heart.addTag(TitanBackTrials.HEART_TAG);
            heart.bindArena(TitanBackTrials.heartArena(), 1);
            back.addFreshEntity(heart);
            SPAWNED.add(heart);
            if (heart.shouldBeSaved()) {
                PENDING.add("the trial heart would be saved to disk");
            }
        }
        originalDifficulty = server.getWorldData().getDifficulty();
        if (originalDifficulty == net.minecraft.world.Difficulty.PEACEFUL) {
            originalDifficulty = net.minecraft.world.Difficulty.NORMAL;
        }
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
    }

    private static void leavePeaceful(MinecraftServer server, ServerLevel back) {
        if (stage != 1) {
            return;
        }
        stage = 2;
        for (var mob : SPAWNED) {
            if (mob.isAlive() && !mob.isRemoved()) {
                PENDING.add(mob.getType() + " (trial) was not put to sleep in peaceful");
            }
        }
        for (BlockPos p : shrineSeal()) {
            BlockState st = back.getBlockState(p);
            if (!st.is(ModBlocks.SEALED_GATE.get()) || st.getValue(SealedGateBlock.OPEN)) {
                PENDING.add("peaceful did not seal the shrine stairs at " + p);
                break;
            }
        }
        for (List<BlockPos> door : List.of(E1_ENTRANCE, HEART_GATE)) {
            if (!GroupGates.isOpen(back, TEST_GROUP, door)) {
                PENDING.add("peaceful did not reopen the door at " + door.get(0));
            }
        }
        server.setDifficulty(originalDifficulty, true);
    }

    private static void finish(MinecraftServer server) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null && stage == 2) {
            for (BlockPos p : shrineSeal()) {
                BlockState st = back.getBlockState(p);
                if (st.is(ModBlocks.SEALED_GATE.get()) && !st.getValue(SealedGateBlock.OPEN)) {
                    PENDING.add("leaving peaceful did not unseal the shrine stairs at " + p);
                    break;
                }
            }
        }
        if (PENDING.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST PASSED (guards ran for 5 seconds; peaceful sealed the shrine and put the trials to sleep)");
        } else {
            PENDING.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST FAILED: {}", f));
            SkyAbyss.LOGGER.error("SELFTEST FAILED: {} problems", PENDING.size());
        }
        if (originalDifficulty != null) {
            server.setDifficulty(originalDifficulty, true);
        }
        server.halt(false);
    }

    private static void checkSurface(ServerLevel level, List<String> failures) {
        BlockPos arrival = arrival();
        air(level, arrival, "arrival", failures);
        air(level, arrival.above(), "arrival head", failures);
        solid(level, arrival.below(), "arrival floor", failures);
        if (!level.getBlockState(anchor()).is(Blocks.LODESTONE)) {
            failures.add("anchor lodestone missing at " + anchor());
        }
        // 祠から体内へ、階段が途切れずにつながっていること
        for (int z = SHRINE_Z - 1; z >= ENTRY.z2(); z--) {
            boolean walkable = false;
            for (int y = FLOOR; y <= shrineY() + SHRINE_STAIR_HEIGHT; y++) {
                BlockPos p = new BlockPos(0, y, z);
                if (!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()) {
                    walkable = true;
                    break;
                }
            }
            if (!walkable) {
                failures.add("stairwell blocked at z=" + z);
            }
        }
    }

    // ---------------------------------------------------------------- 鯨の形 (02 の「巨神の形の背中（作り直し）」)
    // 頭・左右の胸びれ・左右の尾びれ・背の遺跡・錨石の鎖がある。背 (体のいちばん上) に掘った穴がない (祠と広場のほかで、背の面が空)。
    // 体の中の部屋は外とつながっていない (鼓動の間から空気を辿って、祠の封印の面より先で外へ出ない。肋骨の窓はガラス)。
    // 尾の付け根の広場から、背を歩いて祠へ、祠の階段で鼓動の間へ行ける
    private static void checkWhale(ServerLevel level, List<String> failures) {
        record Part(String name, int x, int z, int y1, int y2) {
        }
        for (Part part : List.of(new Part("head", -3, -230, 70, 140), new Part("left pectoral fin", -100, -60, 70, 90),
                new Part("right pectoral fin", 94, -60, 70, 90), new Part("left fluke", -30, 365, 95, 112),
                new Part("right fluke", 30, 365, 95, 112),
                new Part("anchor chain", ANCHOR_CHAIN_X, ANCHOR_CHAIN_Z, BASE_Y - 40, ANCHOR_CHAIN_Y))) {
            boolean found = false;
            for (int y = part.y1(); y <= part.y2() && !found; y++) {
                found = !level.getBlockState(new BlockPos(part.x(), y, part.z())).isAir();
            }
            if (!found) {
                failures.add("whale " + part.name() + " is missing around (" + part.x() + ", " + part.z() + ")");
            }
        }
        // 背の穴: 体のいちばん上のブロックが空いていて、その下も空 (掘った部屋が背を突き抜けた)
        int holes = 0;
        BlockPos firstHole = null;
        for (int x = WhaleShape.minX(); x <= WhaleShape.maxX(); x++) {
            for (int z = WhaleShape.minZ(); z <= WhaleShape.maxZ(); z++) {
                int t = WhaleShape.bodyTop(x, z);
                if (t == Integer.MIN_VALUE || (Math.abs(x) <= 9 && z >= SHRINE_Z - 17 && z <= SHRINE_Z + 17)
                        || (Math.abs(x) <= 13 && Math.abs(z - PLATFORM_Z) <= 16)
                        // 登り口の階段は、背に近づくと殻を割って背へ出る (開いた階段)
                        || (x >= STAIR_X0 && x <= 0 && z >= STAIR_Z0 - 1 && z <= STAIR_Z1 + 1)) {
                    continue;
                }
                if (level.getBlockState(new BlockPos(x, t, z)).isAir() && level.getBlockState(new BlockPos(x, t - 1, z)).isAir()) {
                    holes++;
                    if (firstHole == null) {
                        firstHole = new BlockPos(x, t, z);
                    }
                }
            }
        }
        if (holes > 0) {
            failures.add("whale back has " + holes + " holes (first at " + firstHole + ")");
        }
        // 体の中の空気が外へ抜けていないか。祠の封印の面 (shrineSeal) は壁とみなす
        Set<BlockPos> seal = new HashSet<>(shrineSeal());
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos start = HUB.center();
        queue.add(start);
        seen.add(start);
        BlockPos leak = null;
        java.util.Map<BlockPos, BlockPos> parent = new java.util.HashMap<>();
        while (!queue.isEmpty() && leak == null && seen.size() < 600_000) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.contains(q) || seal.contains(q)) {
                    continue;
                }
                BlockState st = level.getBlockState(q);
                if (st.blocksMotion() && st.getFluidState().isEmpty()) {
                    continue;
                }
                seen.add(q);
                parent.put(q, p);
                // 部屋はどれも鯨の体の中を掘ったもの。鯨のブロックが無かった所へ出たら、外とつながっている
                // (胸びれの道のトンネルだけは、わざと体の外へ抜ける)
                if (WhaleShape.at(q.getX(), q.getY(), q.getZ()) == null) {
                    leak = q;
                    break;
                }
                queue.add(q);
            }
        }
        if (leak != null) {
            // 抜け道の足あと (空気の道を、外へ出る所からさかのぼる。見つけやすいよう 12 マスおきに 8 か所)
            StringBuilder trail = new StringBuilder();
            BlockPos cur = leak;
            for (int i = 0; i < 400 && cur != null; i++) {
                if (i % 12 == 0 && trail.length() < 400) {
                    trail.append(cur.toShortString()).append(" < ");
                }
                cur = parent.get(cur);
            }
            failures.add("the inside of the whale opens to the outside at " + leak + " (not through the shrine stairs) via " + trail);
        }
        SkyAbyss.LOGGER.info("Titan back whale: {} air cells inside the body, back holes {}", seen.size(), holes);
        // 歩ける道: 到着から祠、祠の階段で鼓動の間
        // 到着 (左のわき腹の台) から、錨石・登り口の階段・背の広場へ歩けること
        int toAnchor = walkSteps(level, arrival(), anchor().west(), 1);
        int toPlaza = walkSteps(level, arrival(), plaza(), 1);
        if (toAnchor < 0) {
            failures.add("cannot walk from the arrival to the anchor stone on the flank platform");
        }
        if (toPlaza < 0) {
            failures.add("cannot walk from the arrival on the flank platform up the stair to the plaza on the back");
        } else if (toPlaza > 80) {
            failures.add("the climb from the flank platform to the plaza is too long (" + toPlaza + " steps)");
        }
        SkyAbyss.LOGGER.info("Titan back flank: arrival -> anchor {} steps, -> plaza {} steps", toAnchor, toPlaza);
        int toShrine = walkSteps(level, arrival(), new BlockPos(0, shrineY() + 1, SHRINE_Z), 2);
        int toHub = walkSteps(level, arrival(), HUB.center(), 2);
        if (toShrine < 0) {
            failures.add("cannot walk from the arrival on the tail to the shrine");
        }
        if (toHub < 0) {
            failures.add("cannot walk from the arrival to the heartbeat hall");
        } else if (toHub > 400) {
            failures.add("the walk from the arrival to the hub is too long (" + toHub + " steps)");
        }
        SkyAbyss.LOGGER.info("Titan back walk: arrival -> shrine {} steps, -> hub {} steps", toShrine, toHub);
    }

    // 歩いて何歩で着くか (着かなければ -1)。足元が固く、足と頭が空いている所を、上り 1・下り 3 まで。目標から r 以内で着いたとみなす
    static int walkSteps(ServerLevel level, BlockPos from, BlockPos to, int r) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        java.util.Map<BlockPos, Integer> dist = new java.util.HashMap<>();
        queue.add(from);
        dist.put(from, 0);
        while (!queue.isEmpty() && dist.size() < 400_000) {
            BlockPos p = queue.poll();
            int d = dist.get(p);
            if (Math.abs(p.getX() - to.getX()) <= r && Math.abs(p.getZ() - to.getZ()) <= r && Math.abs(p.getY() - to.getY()) <= 1) {
                return d;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos q = p.relative(dir).above(dy);
                    if (dy == 1 && !passable(level, p.above(2))) {
                        continue;
                    }
                    if (passable(level, q) && passable(level, q.above()) && !passable(level, q.below())) {
                        if (!dist.containsKey(q)) {
                            dist.put(q, d + 1);
                            queue.add(q);
                        }
                        break;
                    }
                    if (!passable(level, q)) {
                        break;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean passable(ServerLevel level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return !st.blocksMotion() && !st.is(Blocks.LAVA) && !st.is(Blocks.WATER);
    }

    // どの部屋も、天井と床 (谷は溶岩の底) がそろっていて、空や奈落に穴が開いていないこと
    private static void checkRooms(ServerLevel level, List<String> failures) {
        for (Box room : TitanBackAudit.ROOMS) {
            // 通路の天井は、大門 (BossGates) の高い空間で開く。迷路の入口の小部屋の天井ははしごの縦穴
            if (room == E3_CORRIDOR || room == HEART_CORRIDOR || room == MAZE_ENTRY_ROOM || room == MAZE_ENTRY_ROOM2 || room == BELL) {
                continue;
            }
            int holes = 0;
            BlockPos firstCeilingHole = null;
            for (int x = room.x1(); x <= room.x2(); x++) {
                for (int z = room.z1(); z <= room.z2(); z++) {
                    if (level.getBlockState(new BlockPos(x, room.ceilingY(), z)).isAir()) {
                        // 天井の穴は、はしごや階段の縦穴だけ許す
                        if (!level.getBlockState(new BlockPos(x, room.ceilingY() + 1, z)).isAir()) {
                            continue;
                        }
                        holes++;
                        if (firstCeilingHole == null) {
                            firstCeilingHole = new BlockPos(x, room.ceilingY(), z);
                        }
                    }
                }
            }
            if (holes > 4) {
                failures.add("room " + room + " has " + holes + " holes in its ceiling (first at " + firstCeilingHole + ")");
            }
        }
        for (Box pit : PITS) {
            for (int x = pit.x1(); x <= pit.x2(); x++) {
                for (int z = pit.z1(); z <= pit.z2(); z++) {
                    if (!level.getBlockState(new BlockPos(x, PIT_LAVA_Y, z)).is(Blocks.LAVA)) {
                        failures.add("pit has no lava at " + x + "," + z);
                        return;
                    }
                }
            }
        }
    }

    private static void checkDoors(ServerLevel level, List<String> failures) {
        // 通れるはずの戸口 (足元の高さと頭の高さ)
        int[][] open = {
                {0, FLOOR + 1, -13}, {0, FLOOR + 1, 10}, {-22, FLOOR + 1, -36}, {22, FLOOR + 1, -36},
                {WA.x2() + 1, FLOOR + 1, -36}, {-50, W1.floorY() + 1, WA.z1() - 1}, {-47, W1.floorY() + 1, WB.z2() + 1},
                {-44, W2.floorY() + 1, WB.z1() - 1}, {-42, W2.floorY() + 1, BELL.z2() + 3}, {-33, W2.floorY() + 1, -97},
                {E1.x1() - 1, FLOOR + 1, -36}, {36, FLOOR + 1, -103}, {E3.x2() + 1, FLOOR + 1, -103}, {47, FLOOR + 1, -69}, {47, FLOOR + 1, -100}, {10, FLOOR + 1, -5}, {21, FLOOR + 1, -5},
                // 心臓の回廊と心臓の間の入口 (戦いの前は開いている)
                {0, FLOOR + 1, -76}, {0, FLOOR + 1, HEART_GATE_Z + 1}, {0, FLOOR + 1, HEART_GATE_Z}, {0, FLOOR + 1, HEART_GATE_Z - 1}};
        for (int[] d : open) {
            BlockPos p = new BlockPos(d[0], d[1], d[2]);
            air(level, p, "doorway", failures);
            air(level, p.above(), "doorway head", failures);
        }
        for (List<BlockPos> sealed : List.of(GATE, E1_EXIT, E3_LOCK, BELL_GATE)) {
            for (BlockPos p : sealed) {
                if (!level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                    failures.add("should start sealed: " + p);
                }
            }
        }
        for (BlockPos p : GOAL_SPOTS) {
            if (!jp.revati.skyabyss.trial.TrialGoal.isMark(level.getBlockState(p))) {
                failures.add("resonance stone missing at " + p);
            }
            air(level, p.above(), "above resonance stone", failures);
        }
        for (BlockPos start : List.of(mazeCell(MAZE_START_I, MAZE_START_J), mazeCell(MAZE_START2_I, MAZE_START2_J))) {
            if (!level.getBlockState(new BlockPos(start.getX() + MAZE_CELL / 2, FLOOR, start.getZ())).is(Blocks.LADDER)) {
                failures.add("maze entrance ladder missing at the hub floor " + start);
            }
        }
        // 祠の階段の封印の場所は、ピースフルでないときは通れること
        for (BlockPos p : shrineSeal()) {
            air(level, p, "shrine stair (seal spot)", failures);
        }
        if (level.getBlockState(shrineSeal().get(0).below()).isAir()) {
            failures.add("shrine seal is not on the stairs at " + shrineSeal().get(0));
        }
        // 物語の碑文と試練の入口の看板: 看板があり、掛かっている壁が固いこと
        for (Inscription lore : allSigns()) {
            BlockState sign = level.getBlockState(lore.pos());
            BlockPos wall = lore.pos().relative(lore.facing().getOpposite());
            if (!(sign.getBlock() instanceof jp.revati.skyabyss.tablet.TabletBlock)) {
                failures.add("lore tablet " + lore.key() + " missing at " + lore.pos() + " (" + sign + ")");
            } else if (!level.getBlockState(wall).isFaceSturdy(level, wall, lore.facing())) {
                failures.add("lore tablet " + lore.key() + " has no wall behind it at " + wall);
            }
        }
    }

    // 看板: 背中に置かれた看板は、碑文と試練の入口の一言だけ (allSigns) で、どの行も看板の幅に収まること (日本語・英語)。
    // 囁き (看板の代わりの案内) の文が lang にあること
    // 版 13 で足した碑 (鐘楼) の残り。任意の翼 (書庫・胸びれ・尾の展望・宿) の碑は版 30 で消した
    private static final Set<String> NEW_TABLETS = Set.of("sign.skyabyss.bell_hint");

    private static void checkSigns(ServerLevel level, List<String> failures) {
        Set<BlockPos> expected = new HashSet<>();
        allSigns().forEach(i -> expected.add(i.pos()));
        Set<String> keys = new java.util.TreeSet<>();
        int count = 0;
        for (int cx = (-RX - 8) >> 4; cx <= (RX + 8) >> 4; cx++) {
            for (int cz = (-RZ - 8) >> 4; cz <= (RZ + 8) >> 4; cz++) {
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof jp.revati.skyabyss.tablet.TabletBlockEntity tablet)) {
                        continue;
                    }
                    if (expected.contains(be.getBlockPos())) {
                        count++;
                    } else if (!NEW_TABLETS.contains(tablet.key())) {
                        failures.add("unexpected tablet at " + be.getBlockPos() + " (only inscriptions, trial entrances and the new wings keep tablets)");
                    }
                    keys.add(tablet.key());
                }
            }
        }
        if (count != expected.size()) {
            failures.add("found " + count + " tablets on the back, expected " + expected.size());
        }
        failures.addAll(jp.revati.skyabyss.SignFit.check(keys));
        var ja = jp.revati.skyabyss.SignFit.lang("ja_jp");
        var en = jp.revati.skyabyss.SignFit.lang("en_us");
        for (String k : BackWhispers.keys()) {
            if (!ja.containsKey(k) || !en.containsKey(k)) {
                failures.add("whisper text missing: " + k);
            }
        }
        SkyAbyss.LOGGER.info("Titan back signs: {} placed ({}), every line measured against {} px", count, keys,
                jp.revati.skyabyss.SignFit.MAX_LINE_WIDTH);
    }

    // アスレチック (版 18): 谷ごとに筋の通った一本の道。入口の岩棚から出口の岩棚まで、足場と島の石を伝って行けること (隣り合うか、跳んで届く)。
    // 近道 (足場を大きく飛ばす) がないこと。足場・島の石が世界にあり、頭上が空いていること。島が休み場で、谷に床が残っていないこと
    private static void checkCourses(ServerLevel level, List<String> failures) {
        List<List<int[]>> courses = ParkourCourses.current(level);
        for (int c = 0; c < courses.size(); c++) {
            for (int[] s : ParkourCourses.allBlocks(c)) {
                BlockPos p = new BlockPos(s[0], s[1], s[2]);
                if (level.getBlockState(p).isAir()) {
                    failures.add("course " + c + " step missing at " + p);
                }
                // 島の四隅には灯りの柱が立つ (中央は空いている)。足場と島の中央の頭上が空いていること
                boolean corner = false;
                for (ParkourCourses.Island isl : ParkourCourses.ISLANDS.get(c)) {
                    corner |= isl.has(s[0], s[2]) && s[1] == isl.y() && (s[0] != isl.cx() && s[2] != isl.cz());
                }
                for (int h = 1; h <= 3 && !corner; h++) {
                    air(level, p.above(h), "room above course step", failures);
                }
            }
            validateCourse(ParkourCourses.ALL.get(c), ParkourCourses.allBlocks(c), "course " + c, failures);
            for (ParkourCourses.Island isl : ParkourCourses.ISLANDS.get(c)) {
                // 版 25: 島はコースの途中の休み場ではない (落ちたら谷の入口の休み場へ戻る)
                if (TitanBackTrials.checkpointSpawns().containsKey(isl.id())) {
                    failures.add("island " + isl.id() + " must not be a rest point inside a parkour course");
                }
            }
        }
        for (Box pit : PITS) {
            for (int x = pit.x1(); x <= pit.x2(); x++) {
                for (int z = pit.z1(); z <= pit.z2(); z++) {
                    BlockState floor = level.getBlockState(new BlockPos(x, FLOOR, z));
                    boolean ledge = (x == WA.x2() && z >= -38 && z <= -34) || isStep(courses, x, FLOOR, z) || isIslandColumn(x, z) || isLedge(x, FLOOR, z);
                    if (!floor.isAir() && !ledge) {
                        failures.add("pit floor remains at " + x + "," + z + " (" + floor + ")");
                        return;
                    }
                }
            }
        }
    }

    // 谷の入口・出口の岩棚の石 (前の谷の岩棚は床と同じ高さ)
    private static boolean isLedge(int x, int y, int z) {
        for (CourseGenerator.Spec spec : ParkourCourses.ALL) {
            for (List<int[]> cells : List.of(spec.start(), spec.end())) {
                for (int[] c : cells) {
                    if (c[0] == x && c[1] == y && c[2] == z) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isIslandColumn(int x, int z) {
        for (int pit = 0; pit < ParkourCourses.ISLANDS.size(); pit++) {
            for (ParkourCourses.Island i : ParkourCourses.ISLANDS.get(pit)) {
                if (i.has(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIsland(int x, int y, int z) {
        for (int pit = 0; pit < ParkourCourses.ISLANDS.size(); pit++) {
            for (ParkourCourses.Island i : ParkourCourses.ISLANDS.get(pit)) {
                if (i.y() == y && i.has(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 入口の岩棚から、足場・島の石をたどって出口の岩棚に着くこと。着くまでの最短の手数が、足場の数に近い (大きな近道がない) こと
    private static void validateCourse(CourseGenerator.Spec spec, List<int[]> blocks, String name, List<String> failures) {
        List<int[]> nodes = new ArrayList<>(blocks);
        nodes.addAll(spec.start());
        nodes.addAll(spec.end());
        int n = nodes.size();
        int[] dist = new int[n];
        java.util.Arrays.fill(dist, -1);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            for (int[] st : spec.start()) {
                if (nodes.get(i) == st) {
                    dist[i] = 0;
                    queue.add(i);
                }
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            for (int j = 0; j < n; j++) {
                if (dist[j] < 0 && CourseGenerator.connected(nodes.get(i), nodes.get(j))) {
                    dist[j] = dist[i] + 1;
                    queue.add(j);
                }
            }
        }
        int best = Integer.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            for (int[] en : spec.end()) {
                if (nodes.get(j) == en && dist[j] >= 0) {
                    best = Math.min(best, dist[j]);
                }
            }
        }
        if (best == Integer.MAX_VALUE) {
            failures.add(name + " does not reach its end ledge");
        } else if (best < 6) {
            failures.add(name + " has a shortcut: " + best + " hops for " + blocks.size() + " blocks");
        }
        for (int j = 0; j < blocks.size(); j++) {
            if (dist[j] < 0) {
                int[] b = blocks.get(j);
                failures.add(name + " block cannot be reached from the start at " + b[0] + "," + b[1] + "," + b[2]);
            }
        }
        for (int[] b : blocks) {
            if (b[0] < spec.x1() || b[0] > spec.x2() || b[2] < spec.z1() || b[2] > spec.z2() || b[1] < spec.yMin() || b[1] > spec.yMax()) {
                failures.add(name + " block is outside its pit at " + b[0] + "," + b[1] + "," + b[2]);
            }
        }
    }

    private static boolean isStep(List<List<int[]>> courses, int x, int y, int z) {
        for (List<int[]> course : courses) {
            for (int[] s : course) {
                if (s[0] == x && s[1] == y && s[2] == z) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void checkHalls(ServerLevel level, List<String> failures) {
        for (Box hall : List.of(E1, E3, CK)) {
            for (BlockPos p : EastWingBuilder.spawnPoints(hall)) {
                air(level, p, "hall spawn point", failures);
                air(level, p.above(), "hall spawn point head", failures);
            }
        }
    }

    // 闇の水路: 入口のはしごの下から、水の中を通って出口の泡の柱までたどり着けること。休みの泡がいくつかあること
    private static void checkMaze(ServerLevel level, List<String> failures) {
        BlockPos start = mazeCell(MAZE_START_I, MAZE_START_J).offset(MAZE_CELL / 2, 2, 0);
        BlockPos goal = mazeCell(MAZE_EXIT_I, MAZE_EXIT_J).offset(MAZE_CELL / 2, 1, MAZE_CELL / 2);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = cur.relative(d);
                if (n.getX() < MAZE_X0 || n.getX() > MAZE_X0 + MAZE_W * (MAZE_CELL + 1) || n.getZ() < MAZE_Z0 || n.getZ() > MAZE_Z0 + MAZE_H * (MAZE_CELL + 1)
                        || n.getY() <= MAZE_FLOOR || n.getY() > MAZE_FLOOR + 6 || seen.contains(n)) {
                    continue;
                }
                BlockState state = level.getBlockState(n);
                if (state.getFluidState().isSource() || state.isAir() || state.is(ModBlocks.SEALED_GATE.get())) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        if (!seen.contains(goal)) {
            failures.add("maze exit is unreachable from the ladder");
        }
        if (MazeBuilder.maze(TitanBackData.get(level).mazeSeed()).links(MAZE_START_I, MAZE_START_J) < 2) {
            failures.add("maze entrance is not a junction");
        }
        checkEntryRest(level, failures);
        checkMaw(level, failures);
        // 水路の顎のスポナーは、同時に生かす数が MAW_MAX_NEARBY (2) に絞ってあること
        var deadEnds = new ArrayList<>(MazeBuilder.maze(TitanBackData.get(level).mazeSeed()).deadEnds());
        deadEnds.removeIf(d -> d[0] == MAZE_START2_I && d[1] == MAZE_START2_J);
        for (int n = 1; n < deadEnds.size(); n += 3) {
            BlockPos sp = mazeCell(deadEnds.get(n)[0], deadEnds.get(n)[1]).offset(MAZE_CELL / 2, 0, MAZE_CELL / 2);
            if (!(level.getBlockEntity(sp) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity be)) {
                failures.add("waterway maw spawner missing at " + sp);
                continue;
            }
            int cap = be.saveWithoutMetadata(level.registryAccess()).getShort("MaxNearbyEntities");
            if (cap != MazeBuilder.MAW_MAX_NEARBY) {
                failures.add("waterway maw spawner cap is " + cap + " (want " + MazeBuilder.MAW_MAX_NEARBY + ") at " + sp);
            }
        }
        BlockPos exit = mazeCell(MAZE_EXIT_I, MAZE_EXIT_J).offset(MAZE_CELL / 2, 0, MAZE_CELL / 2);
        if (!level.getBlockState(new BlockPos(exit.getX(), MAZE_EXIT_ROOM.floorY(), exit.getZ())).is(Blocks.BUBBLE_COLUMN)) {
            failures.add("maze exit has no rising bubble column");
        }
        if (!level.getBlockState(new BlockPos(MAZE_EXIT_ROOM.x1(), FLOOR, EXIT_LADDER_Z)).is(Blocks.LADDER)) {
            failures.add("maze return ladder missing");
        }
    }

    // 息継ぎのくぼみを置いてから水を流す時間
    private static final int MAZE_SETTLE_TICKS = 100;

    // 迷路のチャンクを読み込んだままにする (プレイヤーがいないと、水が流れる tick が来ないため)
    private static void forceMazeChunks(ServerLevel level, boolean forced) {
        int x2 = MAZE_X0 + MAZE_W * (MAZE_CELL + 1);
        int z2 = MAZE_Z0 + MAZE_H * (MAZE_CELL + 1);
        for (int cx = MAZE_X0 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = MAZE_Z0 >> 4; cz <= z2 >> 4; cz++) {
                level.setChunkForced(cx, cz, forced);
            }
        }
    }

    // 水を流したあとでも、正しい道の息継ぎのくぼみごとに 3×3×2 の空気が残っていること (流れる水で埋まっていないこと)。
    // くぼみの下の段は水源のままで、泳いで入れること
    // 入口の休み場 (mz0): 水の外・乾いた小部屋・天井あり・戦いの外。はしごの下りた先から歩いて着く
    private static void checkEntryRest(ServerLevel level, List<String> failures) {
        BlockPos sp = TitanBackTrials.checkpointSpawns(level).get(TitanBackTrials.MAZE_ENTRY);
        if (sp == null) {
            failures.add("the waterway entrance has no rest point (mz0)");
            return;
        }
        if (!level.getFluidState(sp).isEmpty() || !level.getFluidState(sp.above()).isEmpty() || !level.getFluidState(sp.below()).isEmpty()) {
            failures.add("the waterway entrance rest point touches water at " + sp);
        }
        if (!level.getBlockState(sp).getCollisionShape(level, sp).isEmpty() || !level.getBlockState(sp.above()).getCollisionShape(level, sp.above()).isEmpty()) {
            failures.add("the waterway entrance rest point is blocked at " + sp);
        }
        if (level.getBlockState(sp.below()).getCollisionShape(level, sp.below()).isEmpty()) {
            failures.add("the waterway entrance rest point has no floor at " + sp);
        }
        if (!MAZE_ENTRY_ROOM.contains(sp) || sp.getY() <= MAZE_ENTRY_ROOM.floorY()) {
            failures.add("the waterway entrance rest point is outside its room at " + sp);
        }
        // 入口のはしごが部屋の中を通り、水面へ続いていること
        BlockPos ladder = mazeCell(MAZE_START_I, MAZE_START_J).offset(MAZE_CELL / 2, 0, 0);
        for (int y = MAZE_FLOOR + 4; y <= FLOOR; y++) {
            if (!level.getBlockState(new BlockPos(ladder.getX(), y, ladder.getZ())).is(Blocks.LADDER)) {
                failures.add("the entrance ladder is broken at y " + y + " (entry room)");
                break;
            }
        }
        // 記録された休み場として復活の行き先にできること
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        TitanBackTrials.writeCheckpoint(tag, TitanBackTrials.MAZE_ENTRY, 7);
        if (!TitanBackTrials.MAZE_ENTRY.equals(TitanBackTrials.validCheckpoint(tag, 7))) {
            failures.add("mz0 is not accepted as a recorded rest point");
        }
        if (!TitanBackTrials.MAZE_ENTRY.equals(TitanBackTrials.respawnCheckpoint(level, UUID.randomUUID(), TitanBackTrials.MAZE_ENTRY))) {
            failures.add("mz0 respawn was moved");
        }
    }

    // 水路の顎: 弱い噛みつき・弾き・予兆・諦めの定数と、実際に噛んだときの傷と弾き
    private static void checkMaw(ServerLevel level, List<String> failures) {
        if (jp.revati.skyabyss.entity.WaterwayMaw.BITE_DAMAGE > 2.0f || jp.revati.skyabyss.entity.WaterwayMaw.ARRIVAL_GRACE < 200
                || jp.revati.skyabyss.entity.WaterwayMaw.WINDUP_TICKS < 12 || jp.revati.skyabyss.entity.WaterwayMaw.GIVE_UP_TICKS > 30
                || jp.revati.skyabyss.entity.WaterwayMaw.MAX_NEAR > 2 || MazeBuilder.MAW_MAX_NEARBY > 2) {
            failures.add("waterway maw is tuned too harshly (damage/grace/windup/give-up/cap)");
        }
        var maw = jp.revati.skyabyss.registry.ModEntities.WATERWAY_MAW.get().create(level);
        if (maw == null) {
            failures.add("could not create a waterway maw");
            return;
        }
        BlockPos at = mazeCell(MAZE_START_I, MAZE_START_J).offset(1, 1, 1);
        level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
        // 本物のプレイヤーは作れない (FakePlayer は無敵) ので、羊で傷と弾きを見る
        var victim = net.minecraft.world.entity.EntityType.SHEEP.create(level);
        victim.moveTo(at.getX() + 0.5 - 1.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        maw.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.addFreshEntity(victim);
        level.addFreshEntity(maw);
        victim.setHealth(8f);
        victim.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        maw.doHurtTarget(victim);
        float lost = 8f - victim.getHealth();
        if (lost < 1.5f || lost > 2.01f) {
            failures.add("waterway maw bite did " + lost + " damage (want about 2)");
        }
        if (victim.getDeltaMovement().horizontalDistanceSqr() < 0.04) {
            failures.add("waterway maw bite did not knock the player back");
        }
        maw.discard();
        victim.discard();
        level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, false);
    }

    private static void checkPockets(ServerLevel level, List<String> failures) {
        // 枝道のくぼみも含めた、すべてのくぼみ (正しい道は 3 マスごと、どのマスも 2 マス以内: MazePlan)
        List<BlockPos> pockets = MazeBuilder.allPockets(MazeBuilder.maze(TitanBackData.get(level).mazeSeed()));
        if (pockets.size() < 6) {
            failures.add("maze has too few breathing pockets (" + pockets.size() + ")");
        }
        int flooded = 0;
        BlockPos mid = MazeBuilder.pockets(MazeBuilder.maze(TitanBackData.get(level).mazeSeed())).isEmpty() ? null
                : MazeBuilder.midPocket(MazeBuilder.maze(TitanBackData.get(level).mazeSeed()));
        boolean[][] midLane = mid == null ? null : MazeBuilder.midLanes(MazeBuilder.maze(TitanBackData.get(level).mazeSeed()), MazePlan.compute(MazeBuilder.maze(TitanBackData.get(level).mazeSeed())));
        for (BlockPos p : pockets) {
            String bad = null;
            for (int dx = 0; dx < MAZE_CELL && bad == null; dx++) {
                for (int dz = 0; dz < MAZE_CELL && bad == null; dz++) {
                    // 中ほどの休み場は、真ん中の一マスの水の穴を除く全面が乾いた足場 (版 27) (水の二段目が石、その上は紫水晶があってもよい)
                    boolean ledge = p.equals(mid) && (dx != MAZE_CELL / 2 || dz != MAZE_CELL / 2) && !midLane[dx][dz];
                    if (ledge) {
                        if (!level.getBlockState(p.offset(dx, 1, dz)).is(Blocks.POLISHED_DEEPSLATE)) {
                            bad = "the rest ledge corner is missing at " + p.offset(dx, 1, dz).toShortString();
                        }
                        continue;
                    }
                    for (int dy = 2; dy <= 3; dy++) {
                        if (!level.getBlockState(p.offset(dx, dy, dz)).isAir()) {
                            bad = level.getBlockState(p.offset(dx, dy, dz)) + " at " + p.offset(dx, dy, dz).toShortString();
                        }
                    }
                    if (!level.getFluidState(p.offset(dx, 1, dz)).isSource()) {
                        bad = "no water source under the air at " + p.offset(dx, 1, dz).toShortString();
                    }
                }
            }
            if (bad != null) {
                flooded++;
                failures.add("breathing pocket at " + p.toShortString() + " is flooded or blocked after " + MAZE_SETTLE_TICKS + " ticks: " + bad);
            }
        }
        SkyAbyss.LOGGER.info("SELFTEST maze pockets: {} in all, {} flooded", pockets.size(), flooded);
    }

    // 封印の回廊 (版 18: 宝物庫の部屋は無くした): 鼓動の間の北の封印のすぐ先が心臓の回廊で、宝箱も台座もないこと (欠片と装備は心臓を倒したときに渡す)
    private static void checkTreasury(ServerLevel level, List<String> failures) {
        for (int z = HEART_CORRIDOR.z1(); z <= HEART_CORRIDOR.z2(); z++) {
            for (int x = HEART_CORRIDOR.x1(); x <= HEART_CORRIDOR.x2(); x++) {
                if (level.getBlockEntity(new BlockPos(x, FLOOR + 1, z)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                    failures.add("treasure chest should be gone at " + x + "," + z);
                }
                if (level.getBlockState(new BlockPos(x, FLOOR + 1, z)).is(Blocks.LODESTONE)) {
                    failures.add("the old pedestal should be gone at " + x + "," + z);
                }
            }
        }
        for (BlockPos p : GATE) {
            BlockPos beyond = p.north();
            if (!level.getBlockState(beyond).isAir()) {
                failures.add("the seal opens onto a wall at " + beyond);
            }
        }
    }

    // 心臓の回廊と心臓の間: 中は空いていて、天井・壁が体の中で閉じていること (抜け道がないこと)。休み場の焚き火があること
    private static void checkHeart(ServerLevel level, List<String> failures) {
        int cx = HEART_CX;
        int cz = HEART_CZ;
        if (!level.getBlockState(new BlockPos(cx, FLOOR + 1, cz)).isAir()) {
            failures.add("heart arena floor-level center is blocked");
        }
        if (!level.getBlockState(new BlockPos(cx, HEART.ceilingY() - 1, cz)).isAir()) {
            failures.add("heart arena top-level center is blocked");
        }
        if (!level.getBlockState(new BlockPos(cx, FLOOR, cz)).isSolid() || !level.getBlockState(new BlockPos(cx, HEART.ceilingY(), cz)).isSolid()) {
            failures.add("heart arena floor or ceiling is not solid");
        }
        int holes = 0;
        for (int dx = -HeartArenaBuilder.EXTENT; dx <= HeartArenaBuilder.EXTENT; dx++) {
            for (int dz = -HeartArenaBuilder.EXTENT; dz <= HeartArenaBuilder.EXTENT; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > HeartArenaBuilder.OUTER_R2) {
                    continue;
                }
                BlockPos ceil = new BlockPos(cx + dx, HEART.ceilingY(), cz + dz);
                if (d2 <= HeartArenaBuilder.INNER_R2) {
                    // 内側の天井は、すべて固体であること
                    if (!level.getBlockState(ceil).isSolid()) {
                        holes++;
                    }
                } else if (!level.getBlockState(new BlockPos(cx + dx, FLOOR + 7, cz + dz)).isSolid()) {
                    // 壁の輪は、どこも空いていないこと
                    holes++;
                }
            }
        }
        if (holes > 0) {
            failures.add("heart arena shell has " + holes + " holes");
        }
        if (!level.getBlockState(HEART_REST.center()).is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())) {
            failures.add("heart rest campfire missing");
        }
    }

    // 休み場の復活位置: 足元と頭が空いていて、床が固いこと (鼓動の間の台座や腰掛けに埋まらない)
    private static void checkRestPoints(ServerLevel level, List<String> failures) {
        for (var e : TitanBackTrials.checkpointSpawns(level).entrySet()) {
            if (!TitanBackTrials.isSafeStand(level, e.getValue())) {
                failures.add("rest point " + e.getKey() + " spawn is not a safe standing spot at " + e.getValue()
                        + " (feet " + level.getBlockState(e.getValue()) + ", floor " + level.getBlockState(e.getValue().below()) + ")");
            }
        }
        checkRespawnSafety(level, failures);
    }

    // 倒れて目覚める場所は、戦いの部屋の中ではなく、戦いの扉から離れていること (目の前が戦場にならない)。
    // 休み場がいまの戦いの前室なら、鼓動の間で目覚めること。闇の水路の中ほどの休み場は水路の中の乾いた足場であること
    private static void checkRespawnSafety(ServerLevel level, List<String> failures) {
        List<TitanBackLayout.Box> fights = List.of(E1, CK, E3, HEART);
        List<List<BlockPos>> doors = List.of(E1_ENTRANCE, E1_EXIT, CK_ENTRANCE, E3_LOCK, E3_ENTRANCE, HEART_GATE);
        for (var e : TitanBackTrials.checkpointSpawns(level).entrySet()) {
            BlockPos spawn = e.getValue();
            for (TitanBackLayout.Box room : fights) {
                if (room.aabb().inflate(0.5).contains(spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5)) {
                    failures.add("rest point " + e.getKey() + " respawns inside a fight room at " + spawn);
                }
            }
            for (List<BlockPos> door : doors) {
                BlockPos d = door.get(door.size() / 2);
                double dist = Math.sqrt((double) (spawn.getX() - d.getX()) * (spawn.getX() - d.getX())
                        + (double) (spawn.getZ() - d.getZ()) * (spawn.getZ() - d.getZ()));
                // 近すぎるのは、扉の高さが近い (同じ階) 休み場のとき
                if (Math.abs(spawn.getY() - d.getY()) < 6 && dist < 4.5) {
                    failures.add("rest point " + e.getKey() + " respawns " + String.format("%.1f", dist) + " blocks from a fight door at " + d);
                }
            }
        }
        // 水路の中ほど: 乾いた足場 (足元と頭が空気で、液体が無い)。迷路の中で、息継ぎのくぼみの上
        BlockPos mz = TitanBackTrials.checkpointSpawns(level).get(TitanBackTrials.MAZE_REST);
        if (mz == null) {
            failures.add("the dark waterway has no mid-way rest point");
        } else {
            if (mz.getX() < MAZE_X0 || mz.getX() > MAZE_X0 + MAZE_W * (MAZE_CELL + 1) || mz.getZ() < MAZE_Z0
                    || mz.getZ() > MAZE_Z0 + MAZE_H * (MAZE_CELL + 1) || mz.getY() <= MAZE_FLOOR) {
                failures.add("the waterway rest point is outside the maze at " + mz);
            }
            if (!level.getFluidState(mz).isEmpty() || !level.getFluidState(mz.above()).isEmpty()) {
                failures.add("the waterway rest point is under water at " + mz);
            }
            if (level.getBlockState(mz.above(2)).getCollisionShape(level, mz.above(2)).isEmpty()) {
                failures.add("the waterway rest point has no ceiling at " + mz);
            }
        }
        // 戦いの前室で休み場を取っていて、その戦いの最中なら、鼓動の間で目覚める (グループごと)
        UUID group = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        for (String[] pair : new String[][] {{"e0", "e1"}, {"ckr", "ck"}}) {
            if (!pair[0].equals(TitanBackTrials.respawnCheckpoint(level, group, pair[0]))) {
                failures.add("rest point " + pair[0] + " moved without a fight");
            }
            TitanBackTrials.testBeginFight(group, pair[1], Set.of());
            if (!"hub".equals(TitanBackTrials.respawnCheckpoint(level, group, pair[0]))) {
                failures.add("a player who fell in " + pair[1] + " would wake up next to the fight (rest point " + pair[0] + ")");
            }
            if (!pair[0].equals(TitanBackTrials.respawnCheckpoint(level, other, pair[0]))) {
                failures.add("another group's fight moved this group's rest point " + pair[0]);
            }
            TitanBackTrials.testEndFight(level, group, pair[1]);
            if (!pair[0].equals(TitanBackTrials.respawnCheckpoint(level, group, pair[0]))) {
                failures.add("rest point " + pair[0] + " stayed moved after the fight ended");
            }
        }
    }

    // 倒れた参加者の再入場 (広間): 20 秒の待ち・回数 (limit 2 のとき)・戦いの終わりで消える。自己テスト heart reentry から
    public static void checkHallReentry(ServerLevel level, List<String> failures) {
        UUID group = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID mate = UUID.randomUUID();
        net.neoforged.neoforge.common.util.FakePlayer dead = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(member, "fallen_member"));
        long now = level.getGameTime();
        TitanBackTrials.testBeginFight(group, "e1", Set.of(member, mate));
        TitanBackTrials.noteFallen(dead);
        jp.revati.skyabyss.titan.group.Reentry r = TitanBackTrials.reentryOf(group, "e1");
        if (r == null || !r.isFallen(member) || r.isFallen(mate)) {
            failures.add("hall: a participant who died in the hall was not remembered as fallen (or the survivor was)");
            TitanBackTrials.testEndFight(level, group, "e1");
            return;
        }
        Set<UUID> participants = Set.of(member, mate);
        r.observeAwake(member, now);
        long[] at = {now, now + jp.revati.skyabyss.titan.group.Reentry.WAIT_TICKS - 1, now + jp.revati.skyabyss.titan.group.Reentry.WAIT_TICKS};
        boolean[] expect = {false, false, true};
        for (int i = 0; i < at.length; i++) {
            if (TitanBackTrials.mayRejoin(member, participants, r, at[i], 2) != expect[i]) {
                failures.add("hall: the door decision at +" + (at[i] - now) + " ticks after waking is wrong");
            }
        }
        if (!TitanBackTrials.mayRejoin(mate, participants, r, now, 2)) {
            failures.add("hall: the door blocks one who did not fall");
        }
        // 2 回戻って、また倒れる: 回数を使い切ったので通らない。limit 0 なら通る
        for (int i = 0; i < 2; i++) {
            if (i > 0) {
                r.fell(member);
                r.observeAwake(member, now - 5000);
            }
            if (!r.entered(member)) {
                failures.add("hall: return " + (i + 1) + " was not counted");
            }
        }
        r.fell(member);
        r.observeAwake(member, now - 5000);
        if (TitanBackTrials.mayRejoin(member, participants, r, now, 2)) {
            failures.add("hall: the door opened after the returns were used up");
        }
        if (!TitanBackTrials.mayRejoin(member, participants, r, now, 0)) {
            failures.add("hall: limit 0 still blocked a returning participant");
        }
        TitanBackTrials.testEndFight(level, group, "e1");
        if (TitanBackTrials.reentryOf(group, "e1") != null) {
            failures.add("hall: the return counts survived the end of the fight");
        }
    }

    // 試練を進めた状態から、リセットで元に戻ること (グループごと)
    private static void checkReset(ServerLevel level, List<String> failures) {
        BackGroupData.Record record = GroupProgress.of(level, TEST_GROUP);
        for (int i = 0; i < GOAL_SPOTS.size(); i++) {
            record.setFlag(TitanBackTrials.STONE_FLAG + i, true);
        }
        GroupGates.open(level, TEST_GROUP, GATE);
        GroupGates.open(level, TEST_GROUP, E1_EXIT);
        record.markGateOpened();
        record.markHallCleared("e1");
        record.markHeartDefeated();
        TitanBackData data = TitanBackData.get(level);
        long courseSeed = data.courseSeed();
        long mazeSeed = data.mazeSeed();
        int epoch = record.epoch();
        // 休み場の記録は、同じ番号の間だけ有効。リセットのあとは無効 (尾の台) になること
        net.minecraft.nbt.CompoundTag rest = new net.minecraft.nbt.CompoundTag();
        TitanBackTrials.writeCheckpoint(rest, "heart", epoch);
        if (!"heart".equals(TitanBackTrials.validCheckpoint(rest, epoch))) {
            failures.add("a fresh rest point was not accepted");
        }
        net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("skyabyss_checkpoint", "heart");
        if (!TitanBackTrials.validCheckpoint(legacy, epoch).isEmpty()) {
            failures.add("a rest point without an epoch (older than this fix) was accepted");
        }
        TitanBackTrials.resetTrials(level);
        if (record.epoch() == epoch) {
            failures.add("reset did not advance the epoch");
        }
        if (!TitanBackTrials.validCheckpoint(rest, record.epoch()).isEmpty()) {
            failures.add("a rest point from before the reset was still accepted");
        }
        if (data.courseSeed() == courseSeed || data.mazeSeed() == mazeSeed) {
            failures.add("reset did not reshape the parkour and maze");
        }
        List<String> after = new ArrayList<>();
        checkCourses(level, after);
        checkMaze(level, after);
        after.forEach(f -> failures.add("after reset: " + f));
        for (int i = 0; i < GOAL_SPOTS.size(); i++) {
            if (record.flag(TitanBackTrials.STONE_FLAG + i)) {
                failures.add("reset left a stone singing for the group: " + i);
            }
        }
        for (List<BlockPos> sealed : List.of(GATE, E1_EXIT)) {
            if (GroupGates.isOpen(level, TEST_GROUP, sealed)) {
                failures.add("reset did not reseal " + sealed.get(0));
            }
        }
        if (!GroupGates.isOpen(level, TEST_GROUP, HEART_GATE)) {
            failures.add("reset did not open the heart gate");
        }
        if (record.hasProgress()) {
            failures.add("reset left progress in the saved data");
        }
    }

    // 同じ背中の二つのグループ: 進み・扉・敵・心臓・パーティの引き継ぎが、グループごとに分かれていること
    private static void checkGroups(MinecraftServer server, ServerLevel back) {
        List<String> f = PENDING;
        FakePlayer pa = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "groupa"));
        FakePlayer pb = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "groupb"));
        UUID a = Groups.keyOf(pa);
        UUID b = Groups.keyOf(pb);
        if (a.equals(b)) {
            f.add("two solo players got the same group key");
            return;
        }
        // 扉: A が宝物庫の封印を開けても、B には閉じたまま
        GroupGates.open(back, a, GATE);
        if (!GroupGates.isOpen(back, a, GATE) || GroupGates.isOpen(back, b, GATE)) {
            f.add("group A opened the treasury seal but group B's view changed (A open " + GroupGates.isOpen(back, a, GATE)
                    + ", B open " + GroupGates.isOpen(back, b, GATE) + ")");
        }
        BlockPos gatePos = GATE.get(0);
        BlockState gateState = back.getBlockState(gatePos);
        if (gateState.getValue(SealedGateBlock.OPEN)) {
            f.add("the world gate block changed instead of the group's override");
        }
        if (!gateState.getCollisionShape(back, gatePos, CollisionContext.of(pa)).isEmpty()) {
            f.add("the opened gate still blocks group A's player on the server");
        }
        if (gateState.getCollisionShape(back, gatePos, CollisionContext.of(pb)).isEmpty()) {
            f.add("the sealed gate lets group B's player through on the server");
        }
        // 見かけ (クライアントへ送る上書き): A には開いた扉、B には何もない
        BlockState shownToA = GroupOverlays.compute(back, a).get(gatePos);
        if (shownToA == null || !shownToA.getValue(SealedGateBlock.OPEN) || GroupOverlays.compute(back, b).containsKey(gatePos)) {
            f.add("the per-group overlay for the opened gate is wrong");
        }
        // 倒れて戻る (復活・次元の移動・ログイン): 新しい体はクライアントの状態を何も覚えていないので、
        // 上書きを送り直す。送る内容が、倒れる前と同じで、開けた扉・閉じた広間を含むこと
        GroupGates.seal(back, a, E1_ENTRANCE);
        GroupOverlays.send(pa);
        var before = GroupOverlays.lastSent(pa.getUUID());
        GroupOverlays.forget(pa.getUUID());
        if (GroupOverlays.lastSent(pa.getUUID()) != null) {
            f.add("the overlay record survived forgetting the old body");
        }
        GroupEvents.resync(pa);
        var after = GroupOverlays.lastSent(pa.getUUID());
        if (before == null || after == null || !before.equals(after) || !after.containsKey(gatePos) || !after.containsKey(E1_ENTRANCE.get(0))
                || after.get(E1_ENTRANCE.get(0)).getValue(SealedGateBlock.OPEN)) {
            f.add("the group overlay was not re-sent after a respawn (before " + before + ", after " + after + ")");
        }
        GroupGates.release(back, a, E1_ENTRANCE);
        // 敵: A の番兵は B に見えず・狙われず・傷を受けない
        var guard = TitanBackTrials.spawnGuard(back, "sentinel", HUB.center(), a);
        if (guard == null) {
            f.add("could not spawn a group guard");
        } else {
            if (!a.equals(Groups.keyOf(guard))) {
                f.add("the guard does not belong to group A");
            }
            if (!GroupOwned.visibleTo(guard, pa) || GroupOwned.visibleTo(guard, pb)) {
                f.add("a group A guard is visible to the wrong players");
            }
            if (!guard.canAttack(pa) || guard.canAttack(pb)) {
                f.add("a group A guard targets the wrong players (A " + guard.canAttack(pa) + ", B " + guard.canAttack(pb) + ")");
            }
            float hp = guard.getHealth();
            guard.hurt(back.damageSources().playerAttack(pb), 4f);
            if (guard.getHealth() < hp) {
                f.add("group B's attack hurt a group A guard");
            }
            guard.hurt(back.damageSources().playerAttack(pa), 4f);
            if (guard.getHealth() >= hp) {
                f.add("group A's own attack did not hurt its guard");
            }
            float php = pb.getHealth();
            pb.hurt(back.damageSources().mobAttack(guard), 4f);
            if (pb.getHealth() < php) {
                f.add("a group A guard hurt a group B player");
            }
            guard.discard();
        }
        // 心臓・広間: A が倒しても B の記録は変わらない
        GroupProgress.of(back, a).markHeartDefeated();
        GroupProgress.of(back, a).markHallCleared("e1");
        if (GroupProgress.of(back, b).isHeartDefeated() || GroupProgress.of(back, b).isHallCleared("e1")) {
            f.add("group A's progress leaked into group B");
        }
        // 心臓の戦いは、グループごとに別の部屋
        if (TitanBackTrials.heartRooms().of(a) == TitanBackTrials.heartRooms().of(b)) {
            f.add("two groups share one heart room");
        }
        // パーティ: 六人まで。組むとリーダーの進みの写しから始まり、抜けると進みを持っていく
        if (jp.revati.skyabyss.party.Parties.MAX_MEMBERS != 6) {
            f.add("the party size is not 6");
        }
        if (StoneWhaleHeart.scaleFor(6) < 3.99f || StoneWhaleHeart.scaleFor(6) > 4.01f) {
            f.add("the heart scaling for 6 players is " + StoneWhaleHeart.scaleFor(6));
        }
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        try {
            parties.clearAll();
            PartyData.Party party = parties.create(pa.getUUID(), "groupa");
            parties.add(party, pb.getUUID(), "groupb");
            for (int i = 0; i < 4; i++) {
                parties.add(party, UUID.randomUUID(), "mate" + i);
            }
            if (party.members.size() != 6) {
                f.add("a party of six was not formed");
            }
            // 同じパーティの二人には、同じ上書き (開けた扉・閉じた広間) が送られる。一人が倒れて戻っても揃ったまま
            UUID partyKey = Groups.keyOf(pa);
            GroupGates.open(back, partyKey, GATE);
            GroupGates.seal(back, partyKey, E3_ENTRANCE);
            GroupEvents.resync(pa);
            GroupEvents.resync(pb);
            GroupOverlays.forget(pb.getUUID());
            GroupEvents.resync(pb);
            if (!partyKey.equals(Groups.keyOf(pb)) || GroupOverlays.lastSent(pa.getUUID()) == null
                    || !GroupOverlays.lastSent(pa.getUUID()).equals(GroupOverlays.lastSent(pb.getUUID()))
                    || !GroupOverlays.lastSent(pb.getUUID()).containsKey(E3_ENTRANCE.get(0))) {
                f.add("two members of one party were sent different overlays after one of them respawned");
            }
            GroupGates.release(back, partyKey, E3_ENTRANCE);
            if (!Groups.keyOf(pa).equals(party.id) || !Groups.keyOf(pb).equals(party.id)) {
                f.add("party members do not share the party's group key");
            }
            BackGroupData.Record partyRecord = GroupProgress.of(back, party.id);
            if (!partyRecord.isHeartDefeated() || !partyRecord.isHallCleared("e1")) {
                f.add("the new party did not start from the leader's progress");
            }
            if (!GroupGates.isOpen(back, party.id, GATE)) {
                f.add("the new party did not inherit the leader's opened gate");
            }
            // B が抜けると、B はそのときのパーティの進みを持つ
            parties.remove(pb.getUUID());
            if (!GroupProgress.of(back, b).isHeartDefeated() || !GroupGates.isOpen(back, b, GATE)) {
                f.add("a member who left did not keep the party's progress");
            }
        } finally {
            parties.restore(snapshot, server.registryAccess());
        }
        // 後始末
        for (UUID id : List.of(a, b)) {
            GroupProgress.reset(back, id);
        }
    }

    private static final UUID TEST_GROUP = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    // 見取り図を画像で書き出す (地表を真上から、体内の歩く高さ、アスレの高さ、水路の高さ)
    private static void writeMaps(ServerLevel level, java.nio.file.Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            int[][] slices = {{-1}, {FLOOR + 1}, {WEST_LEDGE_Y + 1}, {MAZE_FLOOR + 2}};
            String[] names = {"surface", "floor", "west_ledge", "maze"};
            for (int k = 0; k < slices.length; k++) {
                int w = RX * 2 + 9;
                int h = RZ * 2 + 9;
                int scale = 4;
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * scale, h * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int x = -RX - 4; x <= RX + 4; x++) {
                    for (int z = -RZ - 4; z <= RZ + 4; z++) {
                        int color = 0x1A2233;
                        if (slices[k][0] < 0) {
                            for (int y = BASE_Y + 70; y > BASE_Y - 50; y--) {
                                BlockState st = level.getBlockState(new BlockPos(x, y, z));
                                if (!st.isAir()) {
                                    color = shade(st.getMapColor(level, new BlockPos(x, y, z)).col, (y - BASE_Y) * 4);
                                    break;
                                }
                            }
                        } else {
                            BlockPos p = new BlockPos(x, slices[k][0], z);
                            BlockState st = level.getBlockState(p);
                            if (st.isAir()) {
                                BlockState below = level.getBlockState(p.below());
                                color = below.isAir() ? 0x05060A : shade(below.getMapColor(level, p.below()).col, 40);
                            } else if (!st.getFluidState().isEmpty()) {
                                color = 0x2E5BD6;
                            } else {
                                color = shade(st.getMapColor(level, p).col, -60);
                            }
                        }
                        for (int dx = 0; dx < scale; dx++) {
                            for (int dz = 0; dz < scale; dz++) {
                                img.setRGB((x + RX + 4) * scale + dx, (z + RZ + 4) * scale + dz, color);
                            }
                        }
                    }
                }
                javax.imageio.ImageIO.write(img, "png", dir.resolve(names[k] + ".png").toFile());
            }
        } catch (java.io.IOException e) {
            SkyAbyss.LOGGER.error("Failed to write maps", e);
        }
    }

    private static int shade(int rgb, int amount) {
        int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + amount));
        int g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + amount));
        int b = Math.max(0, Math.min(255, (rgb & 255) + amount));
        return (r << 16) | (g << 8) | b;
    }

    private static void air(ServerLevel level, BlockPos pos, String what, List<String> failures) {
        BlockState st = level.getBlockState(pos);
        // 開いた状態の扉のブロックは、空気と同じに通れる
        if (!st.isAir() && !(st.is(ModBlocks.SEALED_GATE.get()) && st.getValue(SealedGateBlock.OPEN))) {
            failures.add(what + " is blocked at " + pos + " by " + level.getBlockState(pos));
        }
    }

    private static void solid(ServerLevel level, BlockPos pos, String what, List<String> failures) {
        if (level.getBlockState(pos).isAir()) {
            failures.add(what + " is missing at " + pos);
        }
    }
}
