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

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        // ---- BackScale
        check(f, BackScale.ONE.s(10) == 10 && BackScale.ONE.dv(10) == 10 && BackScale.ONE.y(93) == 93, "BackScale.ONE is not the identity");
        check(f, SCALE.s(10) == 16 && SCALE.dv(10) == 13 && SCALE.y(95) == 106 && SCALE.y(58) == 58, "BackScale x1.6/x1.3 maps wrongly: " + SCALE.y(58));
        check(f, SCALE.y(93) == FLOOR, "the hub floor does not map to FLOOR: " + SCALE.y(93));
        check(f, LAYOUT_VERSION >= 15, "layout version was not bumped");
        // ---- 大きさ
        BackMetrics.checkWhale().forEach(p -> f.add("metrics: " + p));
        // ---- 幕
        for (var pair : List.of(java.util.Map.entry(VEIL_E1, E1), java.util.Map.entry(VEIL_E2, E2), java.util.Map.entry(VEIL_E3, E3))) {
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
            check(f, jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, group, E1_SOUTH), "the way to the second hall did not open on the known path");
            // 版を上げる建て直し: 試練の進みは戻るが、keep.* (初回を終えた印) は残る
            record.setFlag("keep.selftest_seen", true);
            record.markHallCleared("e1");
            record.resetProgress();
            check(f, record.isFirstCleared() && record.flag("keep.selftest_seen"), "a rebuild reset erased the keep.* records");
            check(f, !record.isHallCleared("e1") && !record.flag(BellTower.FLAG), "a rebuild reset did not clear the trial progress");
            record.remove("keep.selftest_seen");
            // 休み場の復活位置
            for (var e : TitanBackTrials.checkpointSpawns(back).entrySet()) {
                check(f, TitanBackTrials.isSafeStand(back, e.getValue()), "rest point " + e.getKey() + " is not a safe standing spot at " + e.getValue());
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST whale scale PASSED");
        } else {
            f.forEach(p -> SkyAbyss.LOGGER.error("SELFTEST whale scale FAILED: {}", p));
        }
    }
}
