package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.GaleFeather;
import jp.revati.skyabyss.titan.storm.back.StormBackBuilder;
import jp.revati.skyabyss.titan.storm.back.StormBackClock;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
import jp.revati.skyabyss.titan.storm.back.StormDeck.Island;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.back.StormTestPlayer;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.Trial.Space;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialCells;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.TrialRegions;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の試練の甲板 (StormDeck。docs/design/29 の P2) の自己テスト。段の名前は "trial pool storm" (SKYABYSS_SELFTEST_ONLY="trial pool" の一部)。
//  - 引き方 (ゲームなし): 雷翼の周回 1 は原型の三つ・周回 2 から歩 {S01 S02 S03}・智 {S05 S06}・闘 {S09 S10} から一つ。三つは必ず西・東・前の枠に重ならずに割り当たる。置かない姿は原型のまま
//  - 世界: 建てた形 (島の床・戸・休み場・看板・石・柱)・四つのセルの audit・島が互いにほかの部屋と重ならない・広間の三つの出口の戸が通路をふさぐ
//  - 戸: 引いた試練の戸だけがそのグループに開く (引いていない原型の戸は閉じる)
//  - 全変種 (配置 × 数字 × 敵 × 障害 × 任意 × 静 + 深淵): arm → overlay (セルの箱の中) → tick → disarm
//  - 遊びの決まり: 気嚢の周期・窓・予告・昇り風・落下・羽根 / 雷の橙の形・金の床・雷の速さ / 柱の三度で放電・波・戸 / 守りの数・風の穴・片付け
//  - 石: 引いた枠の風鳴り石が鳴り、図鑑・済みの記録・順路の運びが付く。引いていない試練は枠を鳴らさない
public final class StormTrialSelfTest {
    private StormTrialSelfTest() {
    }

    // 新作 (P2 の四つと P3 の五つ)
    static final List<String> NEW = List.of("S02", "S03", "S09", "S10", "S04", "S07", "S08", "S11", "S12");
    private static final List<String> FAIL = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAIL.add(what);
        }
    }

    public static List<Step> steps() {
        return List.of(
                Step.once("trial pool storm (build)", server -> {
                    FAIL.clear();
                    NOTES.clear();
                    ServerLevel back = server.getLevel(StormTravel.STORM_BACK);
                    if (back == null) {
                        FAIL.add("storm_back dimension is missing");
                        return;
                    }
                    StormDeck.setPlaced(true);
                    forceChunks(back, true);
                    // 背が建ってあれば (全体の実行では "storm back" が先に建てる) 建て直さない
                    back.getChunk(StormDeck.S02_STONE.getX() >> 4, StormDeck.S02_STONE.getZ() >> 4);
                    if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(StormDeck.S02_STONE))) {
                        long t0 = System.currentTimeMillis();
                        StormBackBuilder.build(back, TitanBackData.get(back));
                        NOTES.add("built in " + (System.currentTimeMillis() - t0) + " ms");
                    }
                }),
                Step.once("trial pool storm", 100, StormTrialSelfTest::run));
    }

    // 試練の甲板のあたりの区画を読み込んだままにする (敵が動く・ブロックを読む)
    private static void forceChunks(ServerLevel back, boolean on) {
        for (int cx = -170 >> 4; cx <= 170 >> 4; cx++) {
            for (int cz = 20 >> 4; cz <= 270 >> 4; cz++) {
                back.setChunkForced(cx, cz, on);
            }
        }
    }

    private static void run(MinecraftServer server) {
        long t0 = System.currentTimeMillis();
        ServerLevel back = server.getLevel(StormTravel.STORM_BACK);
        try {
            pure();
            if (back != null) {
                layout(back);
                world(back);
                variants(back);
                logic(back);
            }
        } catch (RuntimeException e) {
            FAIL.add("exception " + e);
            SkyAbyss.LOGGER.error("SELFTEST trial pool storm exception", e);
        } finally {
            StormDeck.setPlaced(true);
            TrialRuntime.clearAll();
            TrialRoute.clearAll();
            StormBackClock.force(null);
            S09Nest.clearOwner();
            if (back != null) {
                forceChunks(back, false);
            }
        }
        if (FAIL.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial pool storm PASSED ({} ms; {})", System.currentTimeMillis() - t0, String.join("; ", NOTES));
        } else {
            FAIL.forEach(p -> SkyAbyss.LOGGER.error("SELFTEST trial pool storm FAILED: {}", p));
        }
    }

    // ---------------------------------------------------------------- 引き方 (ゲームなし)
    private static void pure() {
        String storm = Titans.THUNDER_WING;
        StormDeck.setPlaced(true);
        check(TrialCatalog.poolActive(storm), "the storm pool is not active with the deck placed");
        check(TrialCatalog.originals(storm).stream().map(Def::id).toList().equals(List.of("S01", "S05", "S06")), "storm originals are not S01 S05 S06");
        for (String id : NEW) {
            TrialCell c = TrialCells.get(id);
            check(c != null && c.runtime(), id + " has no running cell");
            check(!TrialCatalog.get(id).original() && TrialCatalog.get(id).titan().equals(storm), id + " is not a new storm trial");
        }
        check(TrialCatalog.inSlot(storm, Slot.WALK).size() == 4 && TrialCatalog.inSlot(storm, Slot.WIT).size() == 4 && TrialCatalog.inSlot(storm, Slot.FIGHT).size() == 4,
                "the storm slots are not 4 / 4 / 4");
        int[] tiers = new int[4];
        for (Def d : TrialCatalog.of(storm)) {
            tiers[d.tier()]++;
        }
        check(tiers[1] == 4 && tiers[2] == 5 && tiers[3] == 3, "the storm tiers are not 4 / 5 / 3: " + java.util.Arrays.toString(tiers));
        UUID g = new UUID(3, 4);
        TrialDraw.Draw d1 = TrialDraw.draw(storm, 1, 4242L, g, new TrialDraw.Memory());
        check(d1.picks().stream().map(TrialDraw.Pick::cellId).toList().equals(List.of("S01", "S05", "S06")), "storm cycle 1 is not the originals");
        Set<String> seenIds = new HashSet<>();
        for (int trial = 0; trial < 60; trial++) {
            UUID grp = new UUID(trial, 0x57);
            TrialDraw.Memory m = new TrialDraw.Memory();
            TrialDraw.draw(storm, 1, 4242L, grp, m);
            for (int cycle = 2; cycle <= 10; cycle++) {
                TrialDraw.Draw dr = TrialDraw.draw(storm, cycle, 4242L, grp, m);
                check(dr.picks().size() == 3, "storm cycle " + cycle + " did not draw three");
                Set<String> keys = new HashSet<>();
                for (TrialDraw.Pick p : dr.picks()) {
                    seenIds.add(p.cellId());
                    keys.add(StormStones.keyOf(dr, p.def()));
                    check(StormStones.cellOfKey(dr, StormStones.keyOf(dr, p.def())).id().equals(p.cellId()), "key round trip failed for " + p.cellId());
                }
                check(keys.equals(Set.of("west", "east", "front")), "storm draw " + dr.encode() + " does not map to west/east/front: " + keys);
                check(dr.inSlot(Slot.WALK) != null && dr.inSlot(Slot.WIT) != null && dr.inSlot(Slot.FIGHT) != null, "a slot is missing in " + dr.encode());
            }
        }
        check(seenIds.containsAll(NEW), "not every new storm trial was drawn in 60 groups x 9 cycles: " + seenIds);
        // 置かないあいだは雷翼も原型のまま
        StormDeck.setPlaced(false);
        TrialDraw.Draw off = TrialDraw.draw(storm, 5, 4242L, g, new TrialDraw.Memory());
        check(off.picks().stream().map(TrialDraw.Pick::cellId).toList().equals(List.of("S01", "S05", "S06")), "the storm pool drew with the deck not placed");
        StormDeck.setPlaced(true);
        // 修飾の口の名前 (lang の鍵が引ける数だけ)
        for (String id : NEW) {
            Space sp = TrialCatalog.get(id).space();
            check(sp.layouts() == 3 && sp.numbers().size() == 2 && sp.hazards().size() == 2 && sp.goals().size() == 1, id + " does not have 3 layouts, 2 numbers, 2 hazards, 1 goal");
        }
        // 数字の増え方
        Mods plain = new Mods(0, new int[] {1, 1}, 0, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
        check(S02Sacs.window(plain, 1) < S02Sacs.window(plain, 3) && S02Sacs.window(plain, 3) < S02Sacs.window(plain, 5), "the sac window does not grow with the players");
        check(S02Sacs.period(new Mods(0, new int[] {0, 1}, -1, new int[0], -1, Extreme.NONE, false, Rarity.COMMON)) > S02Sacs.period(plain), "easy period is not longer");
        check(S03Pillars.period(plain, 1) > S03Pillars.period(plain, 4), "the pillar tempo does not quicken with the players");
        check(S03Pillars.tier(plain, 1) < S03Pillars.tier(plain, 5), "the pillar shapes do not widen with the players");
        TrialCell.Run r9 = new TrialCell.Run(g, TrialCells.get("S09"), plain, 1L, 0);
        check(S09Nest.callers(r9, 1) < S09Nest.callers(r9, 3) && S09Nest.callers(r9, 3) < S09Nest.callers(r9, 5), "the callers do not grow with the players");
        TrialCell.Run r10 = new TrialCell.Run(g, TrialCells.get("S10"), plain, 1L, 0);
        check(S10Watch.fights(r10, 1) == 2 && S10Watch.fights(r10, 2) == 3 && S10Watch.fights(r10, 4) == 4, "the guarded posts are not 2 / 3 / 4 for 1 / 2 / 4 players");
        check(S10Watch.wardens(r10, 1, 3) < S10Watch.wardens(r10, 5, 3), "the wardens do not grow with the players");
        // 橙の形は金の床を含まない・どの形にも安全な床がある
        for (int id = 0; id < 8; id++) {
            check((S03Pillars.MASKS[id] >> 12 & 1L) == 0 && Long.bitCount(S03Pillars.MASKS[id]) <= 24, "pillar shape " + id + " is wrong");
        }
    }

    // ---------------------------------------------------------------- 配置 (座標だけ)
    private static void layout(ServerLevel back) {
        // 広間 (StormBackLayout) と同じ数字
        check(inBox(StormDeck.WEST_GATE, StormBackLayout.WEST_PASS.aabb()), "the west gate is outside the west pass");
        check(inBox(StormDeck.EAST_GATE, StormBackLayout.EAST_PASS.aabb()), "the east gate is outside the east pass");
        check(inBox(StormDeck.FRONT_GATE, StormBackLayout.NECK.aabb()), "the front gate is outside the neck");
        // 島 (床の箱) は互いに重ならず、一マスの手すりを足しても重ならない (橋・渡り場・戸の道・島)
        List<AABB> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        add(boxes, names, "bridge", new AABB(StormDeck.BRIDGE_X1 - 1, Y, StormDeck.BRIDGE_Z1, StormDeck.BRIDGE_X2 + 2, Y + 1, StormDeck.BRIDGE_Z2 + 1));
        add(boxes, names, "yard", new AABB(-25, Y, 63, 26, Y + 1, 84));
        for (int i = 0; i < StormDeck.S02_ISLANDS.length; i++) {
            add(boxes, names, "s02 " + i, islandBox(StormDeck.S02_ISLANDS[i]));
        }
        for (int i = 0; i < StormDeck.S03_ISLANDS.length; i++) {
            add(boxes, names, "s03 " + i, islandBox(StormDeck.S03_ISLANDS[i]));
        }
        add(boxes, names, "s09 foyer", islandBox(StormDeck.S09_FOYER));
        add(boxes, names, "s09 arena", new AABB(StormDeck.S09_ARENA.cx() - 14, Y, StormDeck.S09_ARENA.cz() - 14, StormDeck.S09_ARENA.cx() + 15, Y + 1, StormDeck.S09_ARENA.cz() + 15));
        add(boxes, names, "s09 exit", islandBox(StormDeck.S09_EXIT));
        for (int i = 0; i < StormDeck.S10_PLATFORMS.length; i++) {
            add(boxes, names, "s10 " + i, islandBox(StormDeck.S10_PLATFORMS[i]));
        }
        // 第二の渡り場 (StormDeck2)
        add(boxes, names, "bridge2", new AABB(StormDeck2.BRIDGE_X1 - 1, Y, StormDeck2.BRIDGE_Z1, StormDeck2.BRIDGE_X2 + 2, Y + 1, StormDeck2.BRIDGE_Z2 + 1));
        add(boxes, names, "yard2", new AABB(-25, Y, 171, 26, Y + 1, 192));
        add(boxes, names, "s04 start", islandBox(StormDeck2.S04_START));
        add(boxes, names, "s04 mid", islandBox(StormDeck2.S04_MID));
        add(boxes, names, "s04 end", islandBox(StormDeck2.S04_END));
        add(boxes, names, "s04 bridge a", new AABB(StormDeck2.S04_A_X1, Y, StormDeck2.S04_BRIDGE_Z1, StormDeck2.S04_A_X2 + 1, Y + 1, StormDeck2.S04_BRIDGE_Z2 + 1));
        add(boxes, names, "s04 bridge b", new AABB(StormDeck2.S04_B_X1, Y, StormDeck2.S04_BRIDGE_Z1, StormDeck2.S04_B_X2 + 1, Y + 1, StormDeck2.S04_BRIDGE_Z2 + 1));
        add(boxes, names, "s08 hall", islandBox(StormDeck2.S08_HALL));
        add(boxes, names, "s07 hall", new AABB(StormDeck2.S07_X1 - 1, Y, StormDeck2.S07_Z1 - 1, StormDeck2.S07_X2 + 2, Y + 1, StormDeck2.S07_Z2 + 2));
        add(boxes, names, "s12 foyer", islandBox(StormDeck2.S12_FOYER));
        add(boxes, names, "s12 arena", new AABB(StormDeck2.S12_ARENA.cx() - 14, Y, StormDeck2.S12_ARENA.cz() - 14, StormDeck2.S12_ARENA.cx() + 15, Y + 1, StormDeck2.S12_ARENA.cz() + 15));
        add(boxes, names, "s12 exit", islandBox(StormDeck2.S12_EXIT));
        add(boxes, names, "s11 deck", islandBox(StormDeck2.S11_DECK));
        add(boxes, names, "s04 approach", new AABB(-32, Y, 178, -25, Y + 1, 185));
        add(boxes, names, "s08 approach", new AABB(25, Y, 178, 32, Y + 1, 185));
        add(boxes, names, "s07 approach", new AABB(-25, Y, 192, -19, Y + 1, 199));
        add(boxes, names, "s12 approach", new AABB(-3, Y, 192, 4, Y + 1, 199));
        add(boxes, names, "s11 approach", new AABB(17, Y, 192, 24, Y + 1, 198));
        add(boxes, names, "s12 bridge", new AABB(-3, Y, 209, 4, Y + 1, 216));
        add(boxes, names, "s12 exit bridge", new AABB(-3, Y, 241, 4, Y + 1, 247));
        // 戸の道 (途中の道は島の間に入る)
        add(boxes, names, "s02 approach", new AABB(-32, Y, 70, -25, Y + 1, 77));
        add(boxes, names, "s03 approach", new AABB(26, Y, 70, 33, Y + 1, 77));
        add(boxes, names, "s10 approach", new AABB(-18, Y, 84, -12, Y + 1, 91));
        add(boxes, names, "s09 approach", new AABB(12, Y, 84, 19, Y + 1, 91));
        add(boxes, names, "s09 bridge", new AABB(21, Y, 98, 25, Y + 1, 103));
        add(boxes, names, "s09 exit bridge", new AABB(50, Y, 101, 55, Y + 1, 108));
        for (int a = 0; a < boxes.size(); a++) {
            for (int b = a + 1; b < boxes.size(); b++) {
                AABB x = boxes.get(a);
                AABB y = boxes.get(b);
                // 高さの違う島 (柱と柱) は水平でだけ見る
                boolean overlap = x.minX < y.maxX && y.minX < x.maxX && x.minZ < y.maxZ && y.minZ < x.maxZ;
                if (overlap && !adjacentOk(names.get(a), names.get(b))) {
                    FAIL.add("deck parts overlap: " + names.get(a) + " and " + names.get(b));
                }
            }
        }
        // 背の広場と螺旋の箱と重ならない
        AABB plaza = new AABB(-25, 150, 11, 26, 190, 36);
        AABB turret = new AABB(-14, 140, 36, -8, 175, 43);
        for (int i = 1; i < boxes.size(); i++) {
            AABB b = boxes.get(i);
            for (AABB z : List.of(plaza, turret)) {
                if (b.minX < z.maxX && z.minX < b.maxX && b.minZ < z.maxZ && z.minZ < b.maxZ && b.minY < z.maxY && z.minY < b.maxY) {
                    FAIL.add("deck part " + names.get(i) + " overlaps the plaza or the turret: " + b);
                }
            }
        }
        // 四つの戸は互いに離れて、渡り場の縁にある
        List<List<BlockPos>> gates = List.of(StormDeck.S02_GATE, StormDeck.S03_GATE, StormDeck.S10_GATE, StormDeck.S09_GATE, StormDeck2.S04_GATE, StormDeck2.S08_GATE,
                StormDeck2.S07_GATE, StormDeck2.S12_GATE, StormDeck2.S11_GATE);
        Set<BlockPos> all = new HashSet<>();
        for (List<BlockPos> gt : gates) {
            check(gt.size() == 25, "a deck gate is not 5x5");
            all.addAll(gt);
        }
        check(all.size() == 225, "the nine deck gates overlap");
        // 滑空の余裕・跳ぶ距離 (凪の昇り風で 0.7 以下)
        double worst = 0;
        for (StormDeck.Hop h : StormDeck.hops()) {
            double m = StormDeck.glideMargin(h.from(), h.to(), StormBackClock.CALM_LIFT);
            worst = Math.max(worst, m);
            check(m <= 0.72, "a glide hop from " + h.from() + " is too long for the calm updraft (margin " + m + ")");
        }
        NOTES.add("worst glide margin " + String.format("%.2f", worst) + ", deck floor " + StormDeck.areaTotal() + " m2");
        // 休み場: 重複しない名前・歩く島の上
        Set<String> restIds = new HashSet<>();
        for (StormBackLayout.Rest r : StormDeck.RESTS) {
            check(restIds.add(r.id()), "rest id " + r.id() + " is duplicated");
            check(r.area().contains(r.spawn()), "rest " + r.id() + ": the spawn is outside its area");
            check(StormBackLayout.REST.containsKey(r.id()), "rest " + r.id() + " is not in StormBackLayout.REST");
        }
    }

    private static boolean adjacentOk(String a, String b) {
        // 橋・戸の道は島や渡り場に接する (縁を一マス共有する) ので、箱の端が重なって見える組を許す
        return a.contains("approach") || b.contains("approach") || a.contains("bridge") || b.contains("bridge") || a.equals("yard") || b.equals("yard") || a.equals("yard2")
                || b.equals("yard2");
    }

    private static void add(List<AABB> boxes, List<String> names, String n, AABB b) {
        boxes.add(b);
        names.add(n);
    }

    private static AABB islandBox(Island is) {
        return new AABB(is.x1() - 1, is.y(), is.z1() - 1, is.x2() + 2, is.y() + 1, is.z2() + 2);
    }

    private static boolean inBox(List<BlockPos> blocks, AABB box) {
        for (BlockPos p : blocks) {
            if (!box.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 世界
    private static void world(ServerLevel back) {
        for (String id : NEW) {
            TrialCell c = TrialCells.get(id);
            List<String> bad = c.audit(back);
            check(bad.isEmpty(), id + " audit: " + bad);
        }
        // 休み場: 目覚める所が空気・足もとが固い・焚き火がある
        for (StormBackLayout.Rest r : StormDeck.RESTS) {
            check(back.getBlockState(r.spawn()).isAir() && back.getBlockState(r.spawn().above()).isAir(), "rest " + r.id() + " is not open air at " + r.spawn());
            check(!back.getBlockState(r.spawn().below()).isAir(), "rest " + r.id() + " has no floor at " + r.spawn());
            check(back.getBlockState(r.campfire()).is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get()), "rest " + r.id() + " has no campfire at " + r.campfire());
        }
        // 看板 (碑): 十四枚、島の床の上
        for (StormBackLayout.Inscription i : StormDeck.SIGNS) {
            check(back.getBlockEntity(i.pos()) instanceof jp.revati.skyabyss.tablet.TabletBlockEntity, "sign " + i.key() + " is missing at " + i.pos());
            check(!back.getBlockState(i.pos().below()).isAir(), "sign " + i.key() + " floats at " + i.pos());
        }
        // 戸: 置いて、通路をふさぐこと
        TrialRuntime.installGates(back);
        for (List<BlockPos> g : List.of(StormDeck.S02_GATE, StormDeck.S03_GATE, StormDeck.S10_GATE, StormDeck.S09_GATE, StormDeck.S09_EXIT_GATE, StormDeck2.S04_GATE, StormDeck2.S08_GATE,
                StormDeck2.S07_GATE, StormDeck2.S12_GATE, StormDeck2.S11_GATE, StormDeck2.S12_EXIT_GATE)) {
            for (BlockPos p : g) {
                check(back.getBlockState(p).is(ModBlocks.SEALED_GATE.get()), "deck gate block missing at " + p);
                break;
            }
        }
        for (List<BlockPos> g : List.of(StormDeck.WEST_GATE, StormDeck.EAST_GATE, StormDeck.FRONT_GATE)) {
            check(back.getBlockState(g.get(0)).is(ModBlocks.SEALED_GATE.get()), "an original-route gate is missing at " + g.get(0));
            check(gateClosesPassage(back, g), "the original-route gate at " + g.get(0) + " leaves a gap in its passage");
        }
        // 四つの戸の道は、戸のところで 5 幅の床が続き、戸の外は空・手すり
        for (List<BlockPos> g : List.of(StormDeck.S02_GATE, StormDeck.S03_GATE, StormDeck.S10_GATE, StormDeck.S09_GATE, StormDeck2.S04_GATE, StormDeck2.S08_GATE,
                StormDeck2.S07_GATE, StormDeck2.S12_GATE, StormDeck2.S11_GATE)) {
            check(!back.getBlockState(g.get(0).below()).isAir(), "a deck gate has no floor under it at " + g.get(0));
        }
        gates(back);
        stones(back);
    }

    // 戸が通路の断面をふさぐか: 戸の面のまわり (±1) で、戸でもなく固くもない所がない
    private static boolean gateClosesPassage(ServerLevel back, List<BlockPos> gate) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos p : gate) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        Set<BlockPos> set = new HashSet<>(gate);
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = minY - 1; y <= maxY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    boolean onPlane = minX == maxX ? x == minX : z == minZ;
                    if (!onPlane || set.contains(p)) {
                        continue;
                    }
                    if (back.getBlockState(p).getCollisionShape(back, p).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // 戸: 順路の今の段 (グループごとに 1〜3 を回す) の試練の戸だけがそのグループに開く
    private static void gates(ServerLevel back) {
        String storm = Titans.THUNDER_WING;
        int fails = 0;
        for (int i = 0; i < 24 && fails < 5; i++) {
            UUID g = new UUID(0x570, i);
            BackGroupData.Record r = GroupProgress.of(back, g);
            r.putInt(TrialData.DONE, 1 + i % 4);
            r.remove(TrialData.DRAW);
            TrialDraw.Draw d = TrialData.current(storm, r, g, TrialData.worldSeed(back));
            int step = 1 + i % 3;
            r.putInt(TrialRoute.STEP, step);
            TrialRuntime.syncGates(back, g, storm, d);
            for (TrialCell c : TrialCells.of(storm)) {
                if (c.ports().entrance().isEmpty()) {
                    continue;
                }
                boolean want = c.def().id().equals(TrialRoute.defOf(d, step).id());
                boolean open = GroupGates.isOpen(back, g, c.ports().entrance());
                if (open != want) {
                    FAIL.add("group " + i + " cycle " + (2 + i % 4) + ": gate of " + c.def().id() + " is " + (open ? "open" : "closed") + " but the draw is " + d.encode());
                    fails++;
                }
            }
            GroupGates.reset(back, g);
            BackGroupData.get(back).remove(g);
        }
    }

    // 石: 引いた枠の風鳴り石が鳴る・記録・引いていない試練は枠を鳴らさない・範囲
    private static void stones(ServerLevel back) {
        String storm = Titans.THUNDER_WING;
        if (back.getDifficulty() == Difficulty.PEACEFUL) {
            NOTES.add("stone checks skipped on peaceful");
            return;
        }
        for (String id : NEW) {
            TrialCell cell = TrialCells.get(id);
            UUID g = new UUID(0x571, id.hashCode());
            StormTestPlayer p = StormTestPlayer.join(back, g, "stone_" + id, Vec3.atBottomCenterOf(cell.ports().stone().west(2)));
            try {
                BackGroupData.Record r = GroupProgress.of(back, g);
                // 引きを決める: 周回 2、その試練を含む三つ
                List<String> ids = new ArrayList<>(List.of("S01", "S05", "S06"));
                Def d = TrialCatalog.get(id);
                ids.set(d.slot() == Slot.WALK ? 0 : 1, id);
                if (d.slot() == Slot.FIGHT) {
                    ids = new ArrayList<>(List.of("S01", "S05", id));
                }
                List<TrialDraw.Pick> picks = new ArrayList<>();
                for (String x : ids) {
                    picks.add(new TrialDraw.Pick(x, Mods.PLAIN));
                }
                r.putInt(TrialData.DONE, 1);
                TrialDraw.Draw draw = new TrialDraw.Draw(2, picks);
                r.putString(TrialData.DRAW, draw.encode());
                String key = StormStones.keyOf(draw, d);
                StormProgress pr = StormProgress.of(back, g);
                TrialCell.Run run = TrialRuntime.armed(back, g, cell, draw, back.getGameTime());
                // 石はセルが済むまで黙っている
                var silent = TrialRuntime.useStone(back, p, cell.ports().stone());
                check(silent != null && !pr.rung(StormBackState.Trial.valueOf(key.toUpperCase(java.util.Locale.ROOT))), id + ": the stone rang before the trial was cleared");
                run.cleared = true;
                TrialRuntime.useStone(back, p, cell.ports().stone());
                check(pr.rung(StormBackState.Trial.valueOf(key.toUpperCase(java.util.Locale.ROOT))), id + ": the " + key + " stone did not ring");
                check(TrialData.isCleared(r, id), id + ": not recorded as cleared");
                check(jp.revati.skyabyss.trial.TrialCodex.get(back.getServer()).peek(p.getUUID(), id).cleared >= 1, id + ": the codex did not count the clear");
                // 試練の範囲: その枠の石は、新作のセルの範囲の中
                List<AABB> regions = TrialRegions.of(back, storm, key, g);
                check(TrialRegions.inside(regions, Vec3.atCenterOf(cell.ports().stone())), id + ": its stone is outside the region of its key");
                // もう一度触れても数えない
                int before = jp.revati.skyabyss.trial.TrialCodex.get(back.getServer()).peek(p.getUUID(), id).cleared;
                TrialRuntime.useStone(back, p, cell.ports().stone());
                check(jp.revati.skyabyss.trial.TrialCodex.get(back.getServer()).peek(p.getUUID(), id).cleared == before, id + ": a second touch counted twice");
                pr.setRung(StormBackState.Trial.valueOf(key.toUpperCase(java.util.Locale.ROOT)), false);
            } finally {
                p.leave();
                TrialRuntime.clearAll();
                TrialRoute.clearAll();
                BackGroupData.get(back).remove(g);
            }
        }
    }

    // ---------------------------------------------------------------- 全変種
    private static List<Mods> variantsOf(Def d) {
        Space sp = d.space();
        List<Mods> out = new ArrayList<>();
        List<int[]> hazardSets = new ArrayList<>();
        hazardSets.add(new int[0]);
        for (int h = 0; h < sp.hazards().size(); h++) {
            hazardSets.add(new int[] {h});
        }
        hazardSets.add(new int[] {0, 1});
        for (int layout = 0; layout < sp.layouts(); layout++) {
            for (int c = 0; c < 9; c++) {
                int[] numbers = {c % 3, c / 3};
                for (int[] hs : hazardSets) {
                    for (int goal = -1; goal < sp.goals().size(); goal++) {
                        for (int enemy = sp.enemySets() > 0 ? 0 : -1; enemy < Math.max(sp.enemySets(), 0) || enemy == -1; enemy++) {
                            for (boolean quiet : new boolean[] {false, true}) {
                                Rarity rar = hs.length == 2 ? Rarity.EXTREME : hs.length == 1 ? Rarity.RARE : Rarity.COMMON;
                                out.add(new Mods(layout, numbers, enemy, hs, goal, hs.length == 2 ? Extreme.DOUBLE_HAZARD : Extreme.NONE, quiet, rar));
                                if (enemy == -1) {
                                    break;
                                }
                            }
                            if (enemy == -1) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        // 深淵: 同じ種から重ねた型
        Random rng = new Random(7);
        List<Mods> base = new ArrayList<>(out);
        for (int i = 0; i < base.size(); i += 5) {
            out.add(TrialDraw.abyssify(d, base.get(i), rng));
        }
        return out;
    }

    private static void variants(ServerLevel back) {
        int total = 0;
        for (String id : NEW) {
            TrialCell cell = TrialCells.get(id);
            AABB box = cell.ports().area().inflate(3);
            int n = 0;
            for (Mods m : variantsOf(cell.def())) {
                n++;
                UUID group = new UUID(0x5E1F, n * 31L + id.hashCode());
                TrialCell.Run run = new TrialCell.Run(group, cell, m, 4242L + n, 0);
                try {
                    cell.arm(back, run);
                    Set<BlockPos> seen = new HashSet<>();
                    cell.overlay(back, run, (pos, st) -> seen.add(pos));
                    for (BlockPos p : seen) {
                        if (!box.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) {
                            FAIL.add(id + " variant " + m.encode() + " overlays a block outside its cell at " + p);
                            break;
                        }
                    }
                    for (long t = 5; t <= 60; t += 5) {
                        cell.tick(back, run, List.of(), t);
                        if (cell instanceof StormCell.PerTick pt) {
                            pt.perTick(back, run, List.of(), t);
                        }
                    }
                    cell.overlay(back, run, (pos, st) -> {
                    });
                } finally {
                    cell.disarm(back, run);
                    GroupGates.reset(back, group);
                    BackGroupData.get(back).remove(group);
                }
                if (FAIL.size() > 30) {
                    return;
                }
            }
            total += n;
        }
        check(total >= 600, "too few storm variants were enumerated: " + total);
        NOTES.add(total + " variants");
    }

    // ---------------------------------------------------------------- 遊びの決まり (偽の人で動かす)
    // 傷を数える偽の人 (FakePlayer は傷を受けないので、hurt の呼びを数える)
    static final class Hit extends net.neoforged.neoforge.common.util.FakePlayer {
        int hits;

        Hit(ServerLevel level, UUID id, String name) {
            super(level, new com.mojang.authlib.GameProfile(id, name));
            setHealth(getMaxHealth());
        }

        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            if (amount > 0) {
                hits++;
            }
            return super.hurt(source, amount);
        }

        @Override
        public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            if (level == level()) {
                moveTo(x, y, z, yaw, pitch);
            } else {
                super.teleportTo(level, x, y, z, yaw, pitch);
            }
        }
    }

    static Hit person(ServerLevel back, UUID group, String name, double x, double y, double z) {
        Hit p = new Hit(back, group, name);
        p.moveTo(x, y, z, 0f, 0f);
        p.setOnGround(true);
        return p;
    }

    private static void logic(ServerLevel back) {
        StormP3SelfTest.run(back, FAIL, NOTES);
        s02(back);
        s03(back);
        s09(back);
        s10(back);
    }

    private static void s02(ServerLevel back) {
        S02Sacs cell = (S02Sacs) TrialCells.get("S02");
        UUID g = new UUID(0x502, 1);
        // 周期・窓: どの配置・数字でも、気嚢 1〜5 は周期のうち膨らむ割合が窓に近く、始めの台はいつも膨らんでいる
        for (int layout = 0; layout < 3; layout++) {
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    Mods m = new Mods(layout, new int[] {a, b}, -1, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
                    int period = S02Sacs.period(m);
                    for (int i = 0; i < S02Sacs.SACS; i++) {
                        int on = 0;
                        int warn = 0;
                        for (int t = 0; t < period; t++) {
                            int s = S02Sacs.stateOf(m, i, t, 1);
                            on += s == 2 ? 1 : 0;
                            warn += s == 1 ? 1 : 0;
                        }
                        if (i == 0) {
                            check(on == period, "S02: the starter sac is not always inflated");
                        } else {
                            check(on > period * 0.2 && on < period * 0.9, "S02 layout " + layout + " sac " + i + " is inflated " + on + "/" + period);
                            check(warn == S02Sacs.WARN, "S02: sac " + i + " warns " + warn + " ticks, not " + S02Sacs.WARN);
                        }
                    }
                }
            }
        }
        // 追い波: 気嚢の膨らみ始めが進む向きに遅くなる。向かい波はその逆
        Mods fwd = new Mods(0, new int[] {1, 1}, -1, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
        Mods bwd = new Mods(1, new int[] {1, 1}, -1, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
        int per = S02Sacs.period(fwd);
        check(S02Sacs.startOf(0, 1, per) < S02Sacs.startOf(0, 5, per) && S02Sacs.startOf(1, 1, per) > S02Sacs.startOf(1, 5, per), "S02: the wave directions are wrong");
        // 昇り風: 膨らんだ気嚢の真ん中に立つと持ち上がる・萎んでいるとき・離れているときは持ち上がらない
        Hit p = person(back, g, "s02", 0, 0, 0);
        TrialCell.Run run = new TrialCell.Run(g, cell, fwd, 4242L, 0);
        cell.arm(back, run);
        try {
            StormBackClock.force(StormBackClock.Phase.CALM);
            BlockPos spot = StormDeck.s02Spot(1);
            p.moveTo(spot.getX() + 0.5, Y + 1, spot.getZ() + 0.5);
            long on = -1;
            long off = -1;
            for (long t = 0; t < per * 2L; t += 5) {
                cell.tick(back, run, List.of(p), t);
                int s = ((int[]) run.data.get("states"))[1];
                if (s == 2 && on < 0) {
                    on = t;
                }
                if (s == 0 && off < 0) {
                    off = t;
                }
            }
            check(on >= 0 && off >= 0, "S02: sac 1 never changed state");
            // 膨らんでいる tick で
            long tOn = per;
            while (S02Sacs.stateOf(fwd, 1, tOn, 1) != 2) {
                tOn += 5;
            }
            p.setDeltaMovement(Vec3.ZERO);
            cell.tick(back, run, List.of(p), tOn);
            cell.perTick(back, run, List.of(p), tOn);
            check(p.getDeltaMovement().y > 0.05, "S02: an inflated sac did not lift the player (" + p.getDeltaMovement() + ")");
            long tOff = per;
            while (S02Sacs.stateOf(fwd, 1, tOff, 1) != 0) {
                tOff += 5;
            }
            cell.tick(back, run, List.of(p), tOff);
            p.setDeltaMovement(Vec3.ZERO);
            cell.perTick(back, run, List.of(p), tOff);
            check(p.getDeltaMovement().y <= 0.0, "S02: a deflated sac lifted the player");
            // 風切りの羽根は中で持たされる
            check(GaleFeather.holds(p), "S02: the player was not given a gale feather");
            // 落ちた: 休み場へ戻る (一度の傷は軽い)
            p.moveTo(-70, Y - 6, 74);
            cell.tick(back, run, List.of(p), tOn + 5);
            check(p.getY() >= Y, "S02: a fallen player was not returned to a rest point (" + p.position() + ")");
            // 向こうの台に着いたら済み
            Island goal = StormDeck.S02_ISLANDS[StormDeck.S02_SACS];
            p.moveTo(goal.cx() + 0.5, Y + 1, goal.cz() + 0.5);
            p.setOnGround(true);
            cell.tick(back, run, List.of(p), tOn + 10);
            check(run.cleared, "S02: standing on the far deck did not clear the trial");
        } finally {
            cell.disarm(back, run);
            StormBackClock.force(null);
            BackGroupData.get(back).remove(g);
        }
    }

    private static void s03(ServerLevel back) {
        S03Pillars cell = (S03Pillars) TrialCells.get("S03");
        UUID g = new UUID(0x503, 1);
        Mods m = new Mods(0, new int[] {1, 1}, -1, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
        TrialCell.Run run = new TrialCell.Run(g, cell, m, 4242L, 0);
        cell.arm(back, run);
        Hit p = person(back, g, "s03", 37.5, Y + 1, 73.5);
        try {
            int period = S03Pillars.period(m, 1);
            int warn = S03Pillars.warn(m);
            check(period > warn + 4 * S03Pillars.D, "S03: the period leaves no quiet time (" + period + " vs " + warn + ")");
            // 柱 0 の橙が出る時刻 (位相 0) と落ちる時刻 (位相 warn の次の 5 の倍数)
            cell.tick(back, run, List.of(p), 0);
            Set<BlockPos> shown = new HashSet<>();
            cell.overlay(back, run, (pos, st) -> shown.add(pos));
            check(!shown.isEmpty(), "S03: no orange tile at the start of the cycle");
            for (BlockPos t : shown) {
                check(!back.getBlockState(t).is(Blocks.GOLD_BLOCK), "S03: the gold tile is shown orange");
            }
            // 橙の床に立つ人は落ちる、橙でない床・金の床の人は落ちない
            Island pil = S03Pillars.pillar(0);
            BlockPos live = null;
            BlockPos safe = null;
            for (int j = 0; j < 5; j++) {
                for (int i = 0; i < 5; i++) {
                    BlockPos t = S03Pillars.tile(0, i, j);
                    if (shown.contains(t) && live == null) {
                        live = t;
                    }
                    if (!shown.contains(t) && !(i == 2 && j == 2) && safe == null) {
                        safe = t;
                    }
                }
            }
            BlockPos gold = S03Pillars.tile(0, 2, 2);
            Hit pLive = person(back, new UUID(0x503, 2), "s03live", live.getX() + 0.5, pil.y() + 1, live.getZ() + 0.5);
            Hit pSafe = person(back, new UUID(0x503, 3), "s03safe", safe.getX() + 0.5, pil.y() + 1, safe.getZ() + 0.5);
            Hit pGold = person(back, new UUID(0x503, 4), "s03gold", gold.getX() + 0.5, pil.y() + 1, gold.getZ() + 0.5);
            for (long t = 5; t <= warn + 5; t += 5) {
                cell.tick(back, run, List.of(pLive, pSafe, pGold), t);
            }
            check(pLive.hits > 0, "S03: a player on an orange tile was not struck");
            check(pSafe.hits == 0 && pGold.hits == 0, "S03: a player on a safe or gold tile was struck");
            // 後半は雷が向こうから走る: 柱 6 (後半の最後) の位相が柱 3 より早い
            check(S03Pillars.offset(6) < S03Pillars.offset(3) && S03Pillars.offset(0) < S03Pillars.offset(2), "S03: the wave directions are wrong");
            // 一人でも渡れる: 金の床のある柱は、橙の形のどれでも金の床だけは外れる
            for (int id = 0; id < 8; id++) {
                check((S03Pillars.MASKS[id] & (1L << 12)) == 0, "S03: shape " + id + " would strike the gold tile");
            }
            // 落ちた: 休み場へ
            p.moveTo(70, Y - 8, 73);
            cell.tick(back, run, List.of(p), warn + 10);
            check(p.getY() >= Y, "S03: a fallen player was not returned");
            // 向こうの台
            Island goal = StormDeck.S03_ISLANDS[StormDeck.S03_ISLANDS.length - 1];
            p.moveTo(goal.cx() + 0.5, goal.y() + 1, goal.cz() + 0.5);
            p.setOnGround(true);
            cell.tick(back, run, List.of(p), warn + 15);
            check(run.cleared, "S03: standing on the far deck did not clear the trial");
        } finally {
            cell.disarm(back, run);
            BackGroupData.get(back).remove(g);
        }
    }

    private static void s09(ServerLevel back) {
        if (back.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        S09Nest.clearOwner();
        S09Nest cell = (S09Nest) TrialCells.get("S09");
        UUID g = new UUID(0x509, 1);
        BackGroupData.Record r = GroupProgress.of(back, g);
        r.putInt(TrialData.DONE, 1);
        TrialDraw.Draw draw = new TrialDraw.Draw(2, List.of(new TrialDraw.Pick("S01", Mods.PLAIN), new TrialDraw.Pick("S05", Mods.PLAIN), new TrialDraw.Pick("S09", Mods.PLAIN)));
        r.putString(TrialData.DRAW, draw.encode());
        long now = back.getGameTime() + 5 - back.getGameTime() % 5;
        TrialCell.Run run = TrialRuntime.armed(back, g, cell, draw, now);
        Hit p = person(back, g, "s09", StormDeck.S09_ARENA.cx() + 0.5, Y + 1, StormDeck.S09_ARENA.cz() + 0.5);
        try {
            List<BlockPos> rods = StormDeck.s09Rods();
            // 柱は登録され、雷の輪は柱へ落ちて数えられる。三度で放電: 柱のそばの人は傷を受ける、離れた人は受けない
            BlockPos rod = rods.get(0);
            check(StormRods.get(back, rod) != null, "S09: the rod is not registered");
            Vec3 at = new Vec3(rod.getX() + 0.5, rod.getY(), rod.getZ() + 0.5);
            Hit near = person(back, g, "s09near", rod.getX() + 1.5, Y + 1, rod.getZ() + 0.5);
            Hit far = person(back, new UUID(0x509, 9), "s09far", StormDeck.S09_ARENA.cx() - 9.5, Y + 1, StormDeck.S09_ARENA.cz() + 0.5);
            for (int i = 0; i < S09Nest.overload(run.mods); i++) {
                StormStrike.call(back, at, 1, 0f, null, g);
                StormStrike.tick(back);
                StormStrike.tick(back);
            }
            Set<BlockPos> shown = new HashSet<>();
            cell.overlay(back, run, (pos, st) -> shown.add(pos));
            check(!shown.isEmpty(), "S09: no bulb overlay");
            long t = now + 5;
            cell.tick(back, run, List.of(near, far), t);
            cell.tick(back, run, List.of(near, far), t + 30);
            check(near.hits > 0, "S09: a full rod did not discharge onto the player beside it");
            check(far.hits == 0, "S09: a discharge hurt a player far from the rod");
            // 波: 中へ入ると始まり、倒すと次の波・最後で戸が開く
            check(!GroupGates.isOpen(back, g, StormDeck.S09_EXIT_GATE), "S09: the exit gate is open before the fight");
            long tt = t + 40;
            int expectWaves = S09Nest.waves(run);
            int wavesSeen = 0;
            for (int guard = 0; guard < 400 && !run.cleared; guard++) {
                tt += 5;
                cell.tick(back, run, List.of(p), tt);
                int wave = run.getInt("wave");
                @SuppressWarnings("unchecked")
                List<UUID> mobs = (List<UUID>) run.data.get("mobs");
                if (mobs != null && !mobs.isEmpty()) {
                    wavesSeen = Math.max(wavesSeen, wave);
                    if (wave == 1) {
                        int callers = 0;
                        for (UUID id : mobs) {
                            Entity e = back.getEntity(id);
                            callers += e instanceof jp.revati.skyabyss.titan.storm.mob.ThunderSupplicant ? 1 : 0;
                        }
                        check(callers == S09Nest.callers(run, 1), "S09: wave 1 has " + callers + " callers, not " + S09Nest.callers(run, 1));
                    }
                    for (UUID id : new ArrayList<>(mobs)) {
                        Entity e = back.getEntity(id);
                        if (e != null) {
                            e.discard();
                        }
                    }
                }
            }
            check(run.cleared && wavesSeen == expectWaves, "S09: the fight did not clear after " + expectWaves + " waves (seen " + wavesSeen + ", cleared " + run.cleared + ")");
            check(GroupGates.isOpen(back, g, StormDeck.S09_EXIT_GATE), "S09: the exit gate stayed closed after the fight");
            // 別のグループが同時に入れない
            UUID other = new UUID(0x509, 2);
            BackGroupData.Record ro = GroupProgress.of(back, other);
            ro.putInt(TrialData.DONE, 1);
            ro.putString(TrialData.DRAW, draw.encode());
            TrialCell.Run runO = TrialRuntime.armed(back, other, cell, draw, tt + 5);
            check((Boolean) runO.data.getOrDefault("blocked", false), "S09: a second group was not blocked while the first holds the nest");
            TrialRuntime.stopGroup(back, other);
            BackGroupData.get(back).remove(other);
        } finally {
            TrialRuntime.stopGroup(back, g);
            S09Nest.clearOwner();
            BackGroupData.get(back).remove(g);
        }
    }

    private static void s10(ServerLevel back) {
        S10Watch cell = (S10Watch) TrialCells.get("S10");
        UUID g = new UUID(0x510, 1);
        Mods m = new Mods(0, new int[] {1, 1}, 0, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
        TrialCell.Run run = new TrialCell.Run(g, cell, m, 4242L, 0);
        cell.arm(back, run);
        Hit p = person(back, g, "s10", -14.5, Y + 1, 96.5);
        try {
            check(S10Watch.ventOpen(run, 0) && !S10Watch.ventOpen(run, 1), "S10: only the first vent should be awake at the start");
            boolean peaceful = back.getDifficulty() == Difficulty.PEACEFUL;
            long t = 0;
            // 台 1・2 は一人では守りがいない: 着いたら風の穴が目覚める
            for (int pl = 1; pl <= 4; pl++) {
                Island is = StormDeck.S10_PLATFORMS[pl];
                p.moveTo(is.cx() + 0.5, Y + 1, is.cz() + 0.5);
                p.setOnGround(true);
                t += 5;
                cell.tick(back, run, List.of(p), t);
                boolean guarded = S10Watch.fight(run, 1, pl);
                if (peaceful) {
                    continue;
                }
                if (!guarded) {
                    check(S10Watch.ventOpen(run, pl) || pl == 4, "S10: platform " + pl + " has no guards but its vent did not wake");
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<UUID> mobs = (List<UUID>) run.data.get("mobs." + pl);
                int expect = S10Watch.wardens(run, 1, pl) + S10Watch.support(run, 1, pl);
                check(mobs != null && mobs.size() == expect, "S10: platform " + pl + " spawned " + (mobs == null ? 0 : mobs.size()) + " guards, expected " + expect);
                if (pl < 4) {
                    check(!S10Watch.ventOpen(run, pl), "S10: platform " + pl + "'s vent woke before its guards were beaten");
                }
                if (mobs != null) {
                    for (UUID id : new ArrayList<>(mobs)) {
                        Entity e = back.getEntity(id);
                        if (e != null) {
                            e.discard();
                        }
                    }
                }
                t += 5;
                cell.tick(back, run, List.of(p), t);
                if (pl < 4) {
                    check(S10Watch.ventOpen(run, pl), "S10: platform " + pl + "'s vent did not wake after its guards were beaten");
                }
            }
            if (!peaceful) {
                check(run.cleared, "S10: beating the last post's guards did not clear the trial");
            }
            // 人数: 守りのいる台と羽守の数
            TrialCell.Run r3 = new TrialCell.Run(g, cell, m, 1L, 0);
            check(!S10Watch.fight(r3, 1, 1) && S10Watch.fight(r3, 2, 2) && S10Watch.fight(r3, 4, 1), "S10: the guarded posts do not follow the players");
        } finally {
            cell.disarm(back, run);
            BackGroupData.get(back).remove(g);
        }
    }
}
