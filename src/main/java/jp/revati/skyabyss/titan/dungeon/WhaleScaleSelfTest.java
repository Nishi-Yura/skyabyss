package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.BackMetrics;
import jp.revati.skyabyss.titan.BackScale;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の背の拡大 (版 15) の自己テスト。段の名前は "whale scale" (SKYABYSS_SELFTEST_ONLY="whale scale" でこれだけ)。
//  - BackScale の写し方 (倍率 1.0 は版 12 と同じ。1.6 / 1.3 の値)
//  - 部屋の大きさ・通路の幅と高さ・壁が体の中にある (BackMetrics。倍率の下限を守る)
//  - 幕 (VEIL) が広間の中の面で、人数の少ないグループにだけ張れる / 近い側に湧き場所が残る
//  - 鐘の順 (BellTower.step)・初回だけの部屋の登録 (WhaleFirstRun) と既知の道で戸が開く
//  - 版を上げる建て直しでも、初回を終えた記録 (keep.*) が残る
//  - 休み場の復活位置が安全に立てる
// 建ててある背 (titan back (build)) の後に流す
public final class WhaleScaleSelfTest {
    private WhaleScaleSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    // 部屋の壁の輪 (x1-1〜x2+1 など) に、戸口でも別の部屋の内側でもない空気 (= 意図しない穴) が無いこと
    static List<String> wallHoles(ServerLevel back) {
        List<String> out = new ArrayList<>();
        List<Box> rooms = new ArrayList<>(TitanBackAudit.ROOMS);
        List<BlockPos> doors = new ArrayList<>();
        for (List<BlockPos> l : TitanBackTrials.allDoorLists()) {
            doors.addAll(l);
        }
        java.util.Set<BlockPos> doorSet = new java.util.HashSet<>(doors);
        for (Box b : rooms) {
            int holes = 0;
            String first = null;
            for (int x = b.x1() - 1; x <= b.x2() + 1; x++) {
                for (int z = b.z1() - 1; z <= b.z2() + 1; z++) {
                    boolean ring = x < b.x1() || x > b.x2() || z < b.z1() || z > b.z2();
                    if (!ring) {
                        continue;
                    }
                    for (int y = b.floorY() + 1; y < b.ceilingY(); y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!back.getBlockState(p).isAir() || doorSet.contains(p) || WhaleDump.allowedOpening(p, b)) {
                            continue;
                        }
                        holes++;
                        if (first == null) {
                            first = p.toShortString();
                        }
                    }
                }
            }
            if (holes > 0) {
                out.add("wall holes: room " + b + " has " + holes + " unintended open wall cells (first " + first + ")");
            }
        }
        return out;
    }

    // 版 18: 一本道・ゾーンの材質・胸びれの宝・次の目的・宝物庫の廃止
    private static void checkVersion18(ServerLevel back, List<String> f) {
        // 材質: ゾーンごとの壁が、少なくとも 8 種類はちがう
        java.util.Set<net.minecraft.world.level.block.Block> walls = new java.util.HashSet<>();
        for (Box b : List.of(HUB, W0, WA, E0, E1, CE1, E2, CK_PASS, CK, E3, BELL, LIBRARY, HEART_REST)) {
            walls.add(back.getBlockState(new BlockPos(b.x1() - 1, b.floorY() + 2, (b.z1() + b.z2()) / 2)).getBlock());
        }
        check(f, walls.size() >= 8, "the zones do not read differently: only " + walls.size() + " wall materials " + walls);
        // 宝物庫の部屋はない: 封印のすぐ先が回廊
        check(f, back.getBlockState(new BlockPos(0, FLOOR + 1, GATE.get(0).getZ() - 1)).isAir(), "the seal does not open onto the heart corridor");
        // 胸びれの先の宝箱
        for (int side : new int[]{-1, 1}) {
            check(f, back.getBlockState(WingsBuilder.cachePos(side)).is(net.minecraft.world.level.block.Blocks.CHEST), "fin cache chest missing for side " + side);
        }
        // 東の翼は一本道 (戦いは 3 つ): 戸は北へ順に E1_EXIT → 射手の回廊 (戸なし) → 詰所の入口
        check(f, E1_EXIT.get(0).getZ() > E2_EXIT.get(0).getZ() && E2_EXIT.get(0).getZ() > CK_ENTRANCE.get(0).getZ(), "the east wing doors are not in a forward order");
        // 次の目的: 記録だけから決まる
        BackGroupData.Record r = BackGroupData.get(back).of(UUID.randomUUID());
        var g1 = TrialGuide.next(r, HUB.center());
        check(f, g1 != null && g1.id().equals("wings"), "guide in the hub should say 'wings': " + g1);
        g1 = TrialGuide.next(r, E1.center());
        check(f, g1 != null && g1.id().equals("east_e1"), "guide in the first hall: " + g1);
        r.markHallCleared("e1");
        g1 = TrialGuide.next(r, CE1.center());
        check(f, g1 != null && g1.id().equals("east_ck"), "guide after the first hall: " + g1);
        r.markHallCleared("ck");
        g1 = TrialGuide.next(r, E3.center());
        check(f, g1 != null && g1.id().equals("east_e3"), "guide at the captain: " + g1);
        r.markHallCleared("e3");
        g1 = TrialGuide.next(r, E3.center());
        check(f, g1 != null && g1.id().equals("east_stone"), "guide after the captain: " + g1);
        r.setFlag(TitanBackTrials.STONE_FLAG + 1, true);
        g1 = TrialGuide.next(r, E3.center());
        check(f, g1 == null, "guide after the east stone should be none (the party is returned to the hub automatically): " + g1);
        g1 = TrialGuide.next(BackGroupData.get(back).of(UUID.randomUUID()), W2.center());
        check(f, g1 != null && g1.id().equals("bell"), "guide at the second west rest: " + g1);
        var ja = jp.revati.skyabyss.SignFit.lang("ja_jp");
        var en = jp.revati.skyabyss.SignFit.lang("en_us");
        for (String id : TrialGuide.IDS) {
            check(f, ja.containsKey("objective.skyabyss.trial_" + id) && en.containsKey("objective.skyabyss.trial_" + id), "objective text missing for " + id);
        }
    }

    private static ServerLevel back0(MinecraftServer server) {
        return server.getLevel(TitanTravel.TITAN_BACK);
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        // ---- BackScale
        check(f, BackScale.ONE.s(10) == 10 && BackScale.ONE.dv(10) == 10 && BackScale.ONE.y(93) == 93, "BackScale.ONE is not the identity");
        check(f, SCALE.s(10) == 16 && SCALE.dv(10) == 13 && SCALE.y(95) == 106 && SCALE.y(58) == 58, "BackScale x1.6/x1.3 maps wrongly: " + SCALE.y(58));
        check(f, SCALE.y(93) == FLOOR, "the hub floor does not map to FLOOR: " + SCALE.y(93));
        check(f, LAYOUT_VERSION >= 15, "layout version was not bumped");
        // ---- 大きさ
        BackMetrics.checkWhale().forEach(p -> f.add("metrics: " + p));
        BackMetrics.table("whale").forEach((k, v) -> SkyAbyss.LOGGER.info("SELFTEST whale metrics {}: {}", k, v));
        // ---- 幕
        for (var pair : List.of(java.util.Map.entry(VEIL_E1, E1), java.util.Map.entry(VEIL_E1_SOLO, E1), java.util.Map.entry(VEIL_E3, E3))) {
            for (BlockPos p : pair.getKey()) {
                if (!pair.getValue().contains(p)) {
                    f.add("a veil block is outside its hall at " + p);
                    break;
                }
            }
        }
        // ---- 鐘
        check(f, BellTower.step(0, 1) == BellTower.Result.ADVANCE && BellTower.step(1, 2) == BellTower.Result.ADVANCE
                && BellTower.step(4, 5) == BellTower.Result.DONE && BellTower.step(2, 4) == BellTower.Result.WRONG
                && BellTower.step(3, 1) == BellTower.Result.RESTART, "the bell order logic is wrong");
        for (int n = 1; n <= BellTower.COUNT; n++) {
            check(f, BellTower.numberAt(BellTower.bellPos(n)) == n, "bell number lookup is wrong for " + n);
        }
        // ---- 世界
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null && TitanBackData.get(back).isBuilt()) {
            for (int n = 1; n <= BellTower.COUNT; n++) {
                BlockState st = back.getBlockState(BellTower.bellPos(n));
                check(f, st.is(net.minecraft.world.level.block.Blocks.BELL), "bell " + n + " is missing at " + BellTower.bellPos(n));
            }
            // 初回だけの部屋と、既知の道で開く戸
            TitanBackTrials.installGates(back);
            WhaleFirstRun.register();
            for (String id : List.of(BellTower.FIRST_ROOM, WhaleFirstRun.E2, WhaleFirstRun.CK)) {
                check(f, FirstRun.room(id) != null && FirstRun.room(id).firstRunOnly, "first-run room is not registered: " + id);
            }
            check(f, FirstRun.room(WhaleFirstRun.LIBRARY_ROOM) != null && !FirstRun.room(WhaleFirstRun.LIBRARY_ROOM).firstRunOnly, "the library is not an optional wing");
            UUID group = UUID.randomUUID();
            BackGroupData data = BackGroupData.get(back);
            BackGroupData.Record record = data.of(group);
            check(f, !jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, group, BELL_GATE), "the bell gate was open for a first run");
            check(f, !jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, group, E3_LOCK), "the guard-post door was open for a first run");
            record.markFirstCleared();
            record.setFlag(BellTower.FLAG, true);
            FirstRun.syncDoors(back, group);
            check(f, jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, group, BELL_GATE), "the bell gate did not open on the known path");
            check(f, jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, group, E3_LOCK), "the guard-post door did not open on the known path");
            check(f, jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, group, E1_EXIT), "the way to the second hall did not open on the known path");
            // 版を上げる建て直し: 試練の進みは戻るが、keep.* (初回を終えた印) は残る
            record.setFlag("keep.selftest_seen", true);
            record.markHallCleared("e1");
            record.resetProgress();
            check(f, record.isFirstCleared() && record.flag("keep.selftest_seen"), "a rebuild reset erased the keep.* records");
            check(f, !record.isHallCleared("e1") && !record.flag(BellTower.FLAG), "a rebuild reset did not clear the trial progress");
            record.remove("keep.selftest_seen");
            checkVersion18(back, f);
            // 休み場の復活位置
            for (var e : TitanBackTrials.checkpointSpawns(back).entrySet()) {
                check(f, TitanBackTrials.isSafeStand(back, e.getValue()), "rest point " + e.getKey() + " is not a safe standing spot at " + e.getValue());
            }
        }
        if (back0(server) != null && TitanBackData.get(back0(server)).isBuilt()) {
            f.addAll(wallHoles(back0(server)));
        }
        WhaleDump.write(back0(server));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST whale scale PASSED");
        } else {
            f.forEach(p -> SkyAbyss.LOGGER.error("SELFTEST whale scale FAILED: {}", p));
        }
    }
}
