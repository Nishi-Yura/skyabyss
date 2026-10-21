package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 光海 (闇の水路。docs/design/23。AnemoneLights・MazePlan) の自己テスト。段は "titan back (lights)" (SKYABYSS_SELFTEST_ONLY="titan back" で流れる)。
// 数の決まり (台 3・灯 3・要る数 3)・置き場・苗床・ともす・上書き (台・天井・掲示板・光の道)・なだめる・近道と戸・開く場面・グループごと・リセット・
// 手持ちをなくしても詰まない・持ち歩きの光・灯の戸 (初回も周回も同じ)。計画 (息継ぎのくぼみの間隔・台・近道) は、たくさんの種で確かめる
public final class AnemoneSelfTest {
    private AnemoneSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add("lights: " + what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    private static int lamps(FakePlayer p) {
        return p.getInventory().countItem(ModItems.GLOW_ANEMONE.get());
    }

    private static ItemStack lampStack(FakePlayer p) {
        return p.getInventory().getItem(p.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get())));
    }

    // 計画の不変条件を、たくさんの種で確かめる (ワールドは要らない)
    static void sweepPlans(List<String> f) {
        int noCut = 0;
        int worstReach = 0;
        String worstAt = "";
        int total = 0;
        int minPockets = Integer.MAX_VALUE;
        for (long seed = 1; seed <= 300; seed++) {
            FloodedMaze maze = MazeBuilder.maze(seed);
            MazePlan plan = MazePlan.compute(maze);
            int n = plan.path.size();
            total++;
            minPockets = Math.min(minPockets, plan.pockets.size());
            // 台: 道の上・重ならない・くぼみでない・出口の手前を避ける・順に並ぶ
            int prev = 0;
            for (int k : plan.standK) {
                if (k <= prev || k > n - 4 || plan.isPocket(plan.path.get(k))) {
                    check(f, false, "seed " + seed + ": stand at path index " + k + " is out of order / in a pocket / too near the exit (n=" + n + ")");
                }
                prev = k;
            }
            // くぼみ: 入口・出口の手前・部屋の下・行き止まりにない
            for (int[] c : plan.pockets) {
                int k = plan.pathIndex(c);
                if (k == 0 || k >= n - 3 || maze.links(c[0], c[1]) < 2) {
                    check(f, false, "seed " + seed + ": pocket at cell " + c[0] + "," + c[1] + " is at the start / near the exit / in a dead end");
                }
                BlockPos p = mazeCell(c[0], c[1]);
                for (TitanBackLayout.Box room : List.of(MAZE_ENTRY_ROOM, MAZE_ENTRY_ROOM2, MAZE_EXIT_ROOM)) {
                    boolean x = p.getX() + MAZE_CELL + 1 >= room.x1() - 1 && p.getX() - 1 <= room.x2() + 1;
                    boolean z = p.getZ() + MAZE_CELL + 1 >= room.z1() - 1 && p.getZ() - 1 <= room.z2() + 1;
                    check(f, !(x && z), "seed " + seed + ": pocket at " + p.toShortString() + " is under a room (its floor would be dug out)");
                }
            }
            // 正しい道のくぼみの間隔
            int last = 0;
            for (int k = 1; k < n - 3; k++) {
                if (plan.isPocket(plan.path.get(k))) {
                    last = k;
                }
                if (k - last > MazePlan.PATH_POCKET_EVERY + 3) {
                    check(f, false, "seed " + seed + ": " + (k - last) + " cells of the correct path without a breathing pocket at index " + k);
                    break;
                }
            }
            // どのマスも、くぼみ (か入口) から近い。入口・出口の小部屋の下のマスはくぼみを掘れないので、その 3 マス以内の枝は数えない
            List<int[]> sources = new ArrayList<>(plan.pockets);
            sources.add(plan.path.get(0));
            int[][] pocketDist = plan.distanceMap(sources);
            List<int[]> roomCells = new ArrayList<>();
            for (int i = 0; i < MAZE_W; i++) {
                for (int j = 0; j < MAZE_H; j++) {
                    if (MazePlan.underRoom(i, j)) {
                        roomCells.add(new int[]{i, j});
                    }
                }
            }
            int[][] roomDist = plan.distanceMap(roomCells);
            for (int i = 0; i < MAZE_W; i++) {
                for (int j = 0; j < MAZE_H; j++) {
                    if (roomDist[i][j] <= 3 || pocketDist[i][j] <= worstReach) {
                        continue;
                    }
                    worstReach = pocketDist[i][j];
                    worstAt = "seed " + seed + " cell " + i + "," + j + " (path " + n + " cells, " + plan.pockets.size() + " pockets)";
                }
            }
            // 近道: 隣り合うが通路でないマス・くぼみでない・出口の手前に掛からない・道のりが範囲内・灯 1〜3 本に一つずつ
            if (plan.cuts.size() < AnemoneLights.STANDS) {
                noCut++;
            }
            int lit = 0;
            for (MazePlan.Cut cut : plan.cuts) {
                check(f, cut.gain() >= MazePlan.CUT_MIN_DISTANCE && cut.gain() <= MazePlan.CUT_MAX_DISTANCE, "seed " + seed + ": a shortcut joins cells " + cut.gain() + " apart");
                check(f, Math.abs(cut.a()[0] - cut.b()[0]) + Math.abs(cut.a()[1] - cut.b()[1]) == 1, "seed " + seed + ": a shortcut joins cells that are not neighbours");
                check(f, !maze.linked(cut.a()[0], cut.a()[1], cut.b()[0] - cut.a()[0], cut.b()[1] - cut.a()[1]), "seed " + seed + ": a shortcut is already a passage");
                check(f, !plan.isPocket(cut.a()) && !plan.isPocket(cut.b()), "seed " + seed + ": a shortcut touches a pocket");
                check(f, plan.pathIndex(cut.a()) < n - 3 && plan.pathIndex(cut.b()) < n - 3 && !(cut.b()[0] == MAZE_EXIT_I && cut.b()[1] == MAZE_EXIT_J)
                        && !(cut.a()[0] == MAZE_EXIT_I && cut.a()[1] == MAZE_EXIT_J), "seed " + seed + ": a shortcut touches the exit passage");
                check(f, cut.lit() > lit && cut.lit() <= AnemoneLights.STANDS, "seed " + seed + ": shortcuts are not in order of lamps (" + cut.lit() + ")");
                lit = cut.lit();
            }
        }
        check(f, noCut <= total / 10, "too many seeds have fewer than 3 shortcuts (" + noCut + " / " + total + ")");
        check(f, worstReach <= MazePlan.POCKET_REACH, "some cell is " + worstReach + " cells from a breathing pocket (want about " + MazePlan.POCKET_REACH + "): " + worstAt);
        check(f, minPockets >= 6, "some maze has only " + minPockets + " breathing pockets");
        jp.revati.skyabyss.SkyAbyss.LOGGER.info("SELFTEST lights sweep: {} seeds, worst reach {}, no-shortcut seeds {}, fewest pockets {}", total, worstReach, noCut, minPockets);
    }

    public static List<String> run(ServerLevel back) {
        List<String> f = new ArrayList<>();
        sweepPlans(f);
        long seed = TitanBackData.get(back).mazeSeed();
        FloodedMaze maze = MazeBuilder.maze(seed);
        MazePlan plan = AnemoneLights.plan(seed);
        List<BlockPos> stands = AnemoneLights.stands(seed);
        List<BlockPos> gate = AnemoneLights.exitGate(seed);
        List<List<BlockPos>> cuts = AnemoneLights.cutGates(seed);
        FakePlayer a = player(back, "lamp_a");
        FakePlayer b = player(back, "lamp_b");
        FakePlayer c = player(back, "lamp_c");
        UUID ga = a.getUUID();
        UUID gb = b.getUUID();
        UUID gc = c.getUUID();
        AnemoneLights.extraMembers = List.of(a, b, c);
        BlockPos probeLight = null;
        try {
            // ---- 数の決まり: 台・灯・戸が開くのに要る数が同じ (3)。「いくつ置けて、いくつで開くか」が一つの数で言える
            check(f, AnemoneLights.STANDS == 3 && AnemoneLights.LIGHTS == 3 && AnemoneLights.NEED == 3, "the counts are not 3 stands / 3 lamps / 3 needed");
            check(f, AnemoneLights.STANDS == AnemoneLights.LIGHTS && AnemoneLights.LIGHTS == AnemoneLights.NEED, "stands, lamps and needed are not all equal");
            check(f, new ItemStack(ModItems.GLOW_ANEMONE.get()).getMaxStackSize() == AnemoneLights.LIGHTS, "the lamp stack size is not the lamp count");
            check(f, LAYOUT_VERSION >= 16, "LAYOUT_VERSION was not bumped for the underside rework (" + LAYOUT_VERSION + ")");

            // ---- 置き場: 台は正しい道の上の、くぼみでないマスの真ん中。戸は出口の手前の通路。苗床は入口 (3 つ) と中ほど
            check(f, stands.size() == AnemoneLights.STANDS && stands.stream().distinct().count() == stands.size(), "stands are not 3 distinct places");
            List<int[]> path = maze.solution();
            for (int i = 0; i < stands.size(); i++) {
                BlockPos s = stands.get(i);
                BlockState st = back.getBlockState(s);
                check(f, st.is(ModBlocks.ANEMONE_PEDESTAL.get()) && !st.getValue(AnemonePedestalBlock.LIT) && st.getValue(AnemonePedestalBlock.WATERLOGGED),
                        "stand " + i + " is not an unlit waterlogged pedestal at " + s + " (" + st + ")");
                boolean onPath = false;
                for (int k = 1; k < path.size() - 2; k++) {
                    if (!plan.isPocket(path.get(k)) && mazeCell(path.get(k)[0], path.get(k)[1]).offset(MAZE_CELL / 2, 0, MAZE_CELL / 2).equals(s)) {
                        onPath = true;
                    }
                }
                check(f, onPath, "stand " + i + " is not on the correct path (or sits in a breathing pocket) at " + s);
                check(f, back.getBlockState(s.above()).getFluidState().isSource() && back.getBlockState(s.below()).isSolid(), "stand " + i + " floats or is walled in at " + s);
            }
            check(f, gate.size() == MAZE_CELL * 3, "the exit gate is not a full passage (" + gate.size() + ")");
            for (BlockPos g : gate) {
                BlockState st = back.getBlockState(g);
                check(f, st.is(ModBlocks.SEALED_GATE.get()) && !st.getValue(SealedGateBlock.OPEN), "exit gate block at " + g + " is not a closed sealed gate (" + st + ")");
            }
            BlockPos exitCell = mazeCell(MAZE_EXIT_I, MAZE_EXIT_J);
            BlockPos beforeCell = mazeCell(path.get(path.size() - 2)[0], path.get(path.size() - 2)[1]);
            check(f, gate.stream().allMatch(g -> g.distSqr(exitCell) <= 60 && g.distSqr(beforeCell) <= 60), "the exit gate is not in the passage before the exit");
            check(f, back.getBlockState(AnemoneLights.TALLY_POS).is(ModBlocks.ANEMONE_TALLY.get()), "tally stone missing in the entry room");
            check(f, AnemoneLights.ENTRY_BEDS.size() == 3, "the entry room should have 3 anemone beds");
            for (BlockPos bed : AnemoneLights.ENTRY_BEDS) {
                check(f, back.getBlockState(bed).is(ModBlocks.ANEMONE_BED.get()), "anemone bed missing in the entry room at " + bed);
                check(f, MAZE_ENTRY_ROOM.contains(bed), "bed " + bed + " is outside the entry room");
            }
            check(f, MAZE_ENTRY_ROOM.contains(AnemoneLights.TALLY_POS), "tally is outside the entry room");
            check(f, AnemoneLights.BOARD.size() == AnemoneLights.STANDS, "the lamp board does not have one lamp per stand");
            for (BlockPos p : AnemoneLights.BOARD) {
                BlockState st = back.getBlockState(p);
                check(f, st.is(Blocks.REDSTONE_LAMP) && !st.getValue(RedstoneLampBlock.LIT), "board lamp at " + p + " is not an unlit redstone lamp (" + st + ")");
                check(f, back.getBlockState(p.west()).isAir(), "board lamp at " + p + " has no free air in front of it");
            }
            // 入口の小部屋は、パーティが集まれる広さ
            int width = MAZE_ENTRY_ROOM.x2() - MAZE_ENTRY_ROOM.x1() + 1;
            int depth = MAZE_ENTRY_ROOM.z2() - MAZE_ENTRY_ROOM.z1() + 1;
            check(f, width >= 11 && depth >= 7 && MAZE_ENTRY_ROOM.height() >= 4, "the first rest room is cramped (" + width + "x" + depth + "x" + MAZE_ENTRY_ROOM.height() + ")");
            for (int x = MAZE_ENTRY_ROOM.x1(); x <= MAZE_ENTRY_ROOM.x2(); x++) {
                for (int z = MAZE_ENTRY_ROOM.z1(); z <= MAZE_ENTRY_ROOM.z2(); z++) {
                    BlockPos floor = new BlockPos(x, MAZE_ENTRY_ROOM.floorY(), z);
                    boolean hatch = x == mazeCell(MAZE_START_I, MAZE_START_J).getX() + MAZE_CELL / 2 && z == mazeCell(MAZE_START_I, MAZE_START_J).getZ();
                    check(f, hatch || back.getBlockState(floor).isSolid(), "the entry room floor has a hole at " + floor);
                    check(f, back.getFluidState(floor.above()).isEmpty(), "water in the entry room at " + floor.above());
                }
            }
            BlockPos midBed = MazeBuilder.midPocket(maze).offset(MAZE_CELL - 1, 2, 0);
            check(f, back.getBlockState(midBed).is(ModBlocks.ANEMONE_BED.get()), "anemone bed missing at the mid rest ledge " + midBed);
            check(f, back.getBlockState(MazeBuilder.restSpot(MazeBuilder.midPocket(maze))).isAir(), "the mid rest spot is blocked");

            // ---- 息継ぎのくぼみ: 床の真ん中は魂の砂と泡の柱、床の四隅と天井は水域の色の光
            for (int[] cell : plan.pockets) {
                BlockPos p = mazeCell(cell[0], cell[1]);
                check(f, back.getBlockState(p.offset(MAZE_CELL / 2, -1, MAZE_CELL / 2)).is(Blocks.SOUL_SAND) && back.getBlockState(p.offset(MAZE_CELL / 2, 0, MAZE_CELL / 2)).is(Blocks.BUBBLE_COLUMN)
                        && !back.getBlockState(p.offset(MAZE_CELL / 2, 0, MAZE_CELL / 2)).getValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN),
                        "pocket at " + p.toShortString() + " has no rising bubble column");
                BlockState light = MazeBuilder.ZONE_LIGHT[plan.zoneOf(cell[0], cell[1])];
                check(f, back.getBlockState(p.offset(0, -1, 0)).is(light.getBlock()), "pocket at " + p.toShortString() + " has no zone light on its floor");
            }
            // 水域の色: 床が水域ごとに違う
            Set<net.minecraft.world.level.block.Block> floors = new HashSet<>();
            for (int i = 0; i < MAZE_W; i++) {
                for (int j = 0; j < MAZE_H; j++) {
                    int z = plan.zoneOf(i, j);
                    BlockPos p = mazeCell(i, j).offset(0, -1, 0);
                    if (!plan.isPocket(new int[]{i, j})) {
                        floors.add(back.getBlockState(p.offset(0, 0, 1)).getBlock());
                    }
                    check(f, z >= 0 && z < MazePlan.ZONES, "zone out of range");
                }
            }
            check(f, floors.size() >= 3, "the maze floors do not show distinct zone colours (" + floors.size() + ")");

            // ---- 近道: 戸が壁を抜いた位置に閉じて置かれ、灯の数で順に開く
            check(f, cuts.size() == plan.cuts.size(), "cut gates do not match the plan");
            for (int i = 0; i < cuts.size(); i++) {
                for (BlockPos g : cuts.get(i)) {
                    BlockState st = back.getBlockState(g);
                    check(f, st.is(ModBlocks.SEALED_GATE.get()) && !st.getValue(SealedGateBlock.OPEN), "shortcut gate " + i + " at " + g + " is not a closed sealed gate (" + st + ")");
                }
                check(f, !GroupGates.isOpen(back, ga, cuts.get(i)), "shortcut " + i + " is open for a fresh group");
            }

            // ---- 苗床: 灯は 3 つまで摘める。4 つめは出ない
            check(f, AnemoneLights.growable(back, ga) == 3, "a fresh group can grow " + AnemoneLights.growable(back, ga) + " lamps (want 3)");
            for (int i = 0; i < 6; i++) {
                AnemoneLights.takeFromBed(back, a, AnemoneLights.ENTRY_BED_POS);
            }
            check(f, lamps(a) == 3, "the bed gave " + lamps(a) + " lamps (want exactly 3)");
            check(f, AnemoneLights.growable(back, ga) == 0, "the bed can still grow lamps for a full-handed group");
            check(f, AnemoneLights.growable(back, gb) == 3, "one group's lamps used up another group's bed");

            // ---- ともす: 一つ減る・記録・上書き (その人のグループだけ)・数え石・掲示板・天井・光の道
            AnemoneLights.tryLight(back, a, stands.get(0), lampStack(a));
            BackGroupData.Record ra = BackGroupData.get(back).of(ga);
            check(f, AnemoneLights.isLit(ra, 0) && AnemoneLights.litCount(ra) == 1 && lamps(a) == 2, "lighting stand 0 did not record / consume (lit=" + AnemoneLights.litCount(ra) + ", held=" + lamps(a) + ")");
            check(f, !back.getBlockState(stands.get(0)).getValue(AnemonePedestalBlock.LIT), "lighting changed the shared world block");
            var overlayA = GroupOverlays.compute(back, ga);
            var overlayB = GroupOverlays.compute(back, gb);
            BlockState shown = overlayA.get(stands.get(0));
            check(f, shown != null && shown.getValue(AnemonePedestalBlock.LIT), "the lit stand is not in the group's overlay");
            check(f, shown != null && shown.getLightEmission() == 15, "the lit stand does not give light 15");
            BlockState tally = overlayA.get(AnemoneLights.TALLY_POS);
            check(f, tally != null && tally.getValue(AnemoneTallyBlock.COUNT) == 1, "the tally stone does not show 1 lit stand");
            BlockState board0 = overlayA.get(AnemoneLights.BOARD.get(0));
            check(f, board0 != null && board0.is(Blocks.REDSTONE_LAMP) && board0.getValue(RedstoneLampBlock.LIT), "the first board lamp is not lit in the overlay");
            check(f, !overlayA.containsKey(AnemoneLights.BOARD.get(1)), "the second board lamp is lit with one lamp");
            BlockPos ceiling = new BlockPos(stands.get(0).getX(), MAZE_FLOOR + 4, stands.get(0).getZ());
            check(f, overlayA.get(ceiling) != null && overlayA.get(ceiling).is(Blocks.SEA_LANTERN), "the lit stand has no lantern on the ceiling above it");
            check(f, back.getBlockState(ceiling).isSolid(), "the ceiling above a stand is not solid (" + ceiling + ")");
            int ahead = 0;
            for (BlockPos t : AnemoneLights.trail(back)) {
                BlockState st = overlayA.get(t);
                if (st != null && st.is(Blocks.SEA_LANTERN)) {
                    ahead++;
                    check(f, back.getBlockState(t).isSolid(), "a trail floor block is not solid at " + t);
                }
            }
            check(f, ahead >= 3, "a lit stand does not beam a light path toward the goal (" + ahead + " floor blocks)");
            check(f, !overlayB.containsKey(stands.get(0)) && !overlayB.containsKey(AnemoneLights.TALLY_POS) && !overlayB.containsKey(AnemoneLights.BOARD.get(0)),
                    "a lit stand leaked into another group's overlay");
            check(f, !AnemoneLights.calmedAt(back, gb, Vec3.atCenterOf(stands.get(0))), "the lamp calms another group");
            // もう一度: 減らない
            AnemoneLights.tryLight(back, a, stands.get(0), lampStack(a));
            check(f, lamps(a) == 2 && AnemoneLights.litCount(ra) == 1, "lighting a lit stand again used a lamp");
            // 苗床: 2 持って 1 つともした → 3 を超えない
            AnemoneLights.takeFromBed(back, a, AnemoneLights.ENTRY_BED_POS);
            check(f, lamps(a) == 2, "the bed gave a lamp beyond lit + carried = 3");

            // ---- 近道: 灯がそのしきい値になるまで閉じていて、そろうとそのグループにだけ開く
            AnemoneLights.sync(back, ga);
            for (int i = 0; i < cuts.size(); i++) {
                boolean want = plan.cuts.get(i).lit() <= 1;
                check(f, GroupGates.isOpen(back, ga, cuts.get(i)) == want, "shortcut " + i + " is " + (want ? "closed" : "open") + " with 1 lamp (needs " + plan.cuts.get(i).lit() + ")");
                check(f, !GroupGates.isOpen(back, gb, cuts.get(i)), "shortcut " + i + " opened for another group");
            }

            // ---- 魚をなだめる: 灯のまわり (6.5) の内側だけ。ともした灯の分だけ
            Vec3 near = Vec3.atCenterOf(stands.get(0)).add(0, 1, 0);
            Vec3 far = Vec3.atCenterOf(stands.get(0)).add(AnemoneLights.CALM_RADIUS + 3, 0, 0);
            check(f, AnemoneLights.calmedAt(back, ga, near) && !AnemoneLights.calmedAt(back, ga, far), "lamp calming radius is wrong near/far");
            Vec3 unlitSpot = Vec3.atCenterOf(stands.get(1)).add(0, 1, 0);
            if (unlitSpot.distanceToSqr(Vec3.atCenterOf(stands.get(0))) > AnemoneLights.CALM_RADIUS * AnemoneLights.CALM_RADIUS) {
                check(f, !AnemoneLights.calmedAt(back, ga, unlitSpot), "an unlit stand calms fish");
            }
            a.setPos(near.x, near.y, near.z);
            b.setPos(near.x, near.y, near.z);
            check(f, AnemoneLights.calms(back, a) && !AnemoneLights.calms(back, b), "calms() is not per group");
            check(f, !AnemoneLights.calms(back.getServer().overworld(), a), "calms() applied outside the whale's back");

            // ---- 戸: 灯が 3 本そろうまで閉じている (そのグループにだけ開く)。開くと場面が走り、光の道が出口まで通る
            check(f, !GroupGates.isOpen(back, ga, gate), "the exit gate was open before the lamps");
            AnemoneLights.tryLight(back, a, stands.get(2), lampStack(a));
            check(f, !GroupGates.isOpen(back, ga, gate), "the exit gate opened with only 2 lamps");
            check(f, AnemoneLights.openingsRunning() == 0, "the opening sequence started before the gate opened");
            AnemoneLights.tryLight(back, a, stands.get(1), lampStack(a));
            check(f, AnemoneLights.litCount(ra) == 3 && GroupGates.isOpen(back, ga, gate), "the exit gate did not open at 3 lamps");
            check(f, lamps(a) == 0, "lamps left over after lighting all 3 stands (" + lamps(a) + ")");
            check(f, AnemoneLights.openingsRunning() == 1, "the gate-opening sequence did not start");
            check(f, !GroupGates.isOpen(back, gb, gate), "the exit gate opened for another group");
            for (int i = 0; i < cuts.size(); i++) {
                check(f, GroupGates.isOpen(back, ga, cuts.get(i)), "shortcut " + i + " is still closed with all lamps");
            }
            var open3 = GroupOverlays.compute(back, ga);
            check(f, open3.get(AnemoneLights.TALLY_POS).getValue(AnemoneTallyBlock.COUNT) == 3, "the tally stone does not show 3");
            for (BlockPos p : AnemoneLights.BOARD) {
                check(f, open3.get(p) != null && open3.get(p).getValue(RedstoneLampBlock.LIT), "board lamp at " + p + " is not lit with all lamps");
            }
            int full = 0;
            for (BlockPos t : AnemoneLights.trail(back)) {
                if (open3.get(t) != null && open3.get(t).is(Blocks.SEA_LANTERN)) {
                    full++;
                }
            }
            // 光の道は息継ぎのくぼみのマスと流れの通路を飛ばす (AnemoneLights.computeTrail) ので、道のマスの数ではなく、くぼみでないマスの数以上を見る
            int floorCells = 0;
            for (int[] cell : plan.path) {
                if (!plan.isPocket(cell)) {
                    floorCells++;
                }
            }
            check(f, full == AnemoneLights.trail(back).size() && full >= floorCells, "the light path does not run the whole way to the exit once the gate is open (" + full + " / " + AnemoneLights.trail(back).size() + ")");
            BlockPos ringAt = MazeBuilder.exitBubble().offset(1, -1, 1);
            check(f, open3.get(ringAt) != null && open3.get(ringAt).is(Blocks.SEA_LANTERN), "the exit cell is not ringed with light once the gate is open");
            check(f, !GroupOverlays.compute(back, gb).containsKey(AnemoneLights.trail(back).get(0)), "the light path leaked into another group's overlay");
            // 目的の帯の矢印: 体の向きに対する方角
            check(f, "↑".equals(AnemoneLights.arrow(a, 0, 5)) && "→".equals(AnemoneLights.arrow(a, -5, 0)) && "↓".equals(AnemoneLights.arrow(a, 0, -5))
                    && "←".equals(AnemoneLights.arrow(a, 5, 0)), "the band arrow does not follow the facing");
            // 目的の帯の文は、どの状態でも作れる (水の中・入口の小部屋で)
            a.setPos(stands.get(0).getX() + 0.5, stands.get(0).getY() + 1, stands.get(0).getZ() + 0.5);
            check(f, AnemoneLights.bandText(back, a, ga).getString().length() > 0, "the objective band text is empty in the water");
            a.setPos(AnemoneLights.TALLY_POS.getX() + 0.5, AnemoneLights.TALLY_POS.getY(), AnemoneLights.TALLY_POS.getZ() + 1.5);
            check(f, AnemoneLights.bandText(back, a, ga).getString().length() > 0, "the objective band text is empty in the entry room");
            check(f, AnemoneLights.bandText(back, b, gb).getString().length() > 0, "the objective band text is empty for a fresh group");
            // 4 つめは出ない (台は 3 つで、全部ともった)
            ItemStack spare = new ItemStack(ModItems.GLOW_ANEMONE.get());
            a.getInventory().add(spare);
            AnemoneLights.tryLight(back, a, stands.get(1), spare);
            check(f, AnemoneLights.litCount(ra) == 3 && lamps(a) == 1, "a lit stand used a lamp again");
            a.getInventory().clearContent();

            // ---- 詰まない: なくしても苗床で戻る
            check(f, GroupGates.isOpen(back, ga, gate), "the gate closed again after all lamps");
            AnemoneLights.takeFromBed(back, c, AnemoneLights.ENTRY_BED_POS);
            AnemoneLights.takeFromBed(back, c, AnemoneLights.ENTRY_BED_POS);
            check(f, lamps(c) == 2, "group c did not get its lamps");
            // c がともすのは 1 つ。持ち物 1 を落として失った: 手持ち 0・灯 1 → 2 つ摘める
            AnemoneLights.tryLight(back, c, stands.get(0), lampStack(c));
            c.getInventory().clearContent();
            check(f, AnemoneLights.growable(back, gc) == 2, "after losing its lamps a group cannot regrow them (growable=" + AnemoneLights.growable(back, gc) + ")");

            // ---- リセット: そのグループの灯・戸・近道が元に戻る。ほかのグループは変わらない
            AnemoneLights.takeFromBed(back, b, AnemoneLights.ENTRY_BED_POS);
            AnemoneLights.tryLight(back, b, stands.get(1), lampStack(b));
            check(f, AnemoneLights.isLit(BackGroupData.get(back).of(gb), 1) && !AnemoneLights.isLit(BackGroupData.get(back).of(gb), 0), "group b's lamp is not independent of a");
            GroupProgress.reset(back, ga);
            BackGroupData.Record ra2 = BackGroupData.get(back).of(ga);
            check(f, AnemoneLights.litCount(ra2) == 0, "the reset did not clear the lamps");
            check(f, !GroupGates.isOpen(back, ga, gate), "the reset did not close the exit gate");
            for (int i = 0; i < cuts.size(); i++) {
                check(f, !GroupGates.isOpen(back, ga, cuts.get(i)), "the reset did not close shortcut " + i);
            }
            var afterReset = GroupOverlays.compute(back, ga);
            check(f, !afterReset.containsKey(stands.get(0)) && !afterReset.containsKey(AnemoneLights.TALLY_POS) && !afterReset.containsKey(AnemoneLights.BOARD.get(0))
                    && !afterReset.containsKey(AnemoneLights.trail(back).get(0)), "the reset left lit stands / the board / the light path in the overlay");
            check(f, AnemoneLights.isLit(BackGroupData.get(back).of(gb), 1), "resetting group a cleared group b's lamp");
            check(f, AnemoneLights.growable(back, ga) == 3, "after the reset a can regrow only " + AnemoneLights.growable(back, ga));

            // ---- 迷路が作り直されるとき: 全グループの灯と戸の上書きを消す
            AnemoneLights.takeFromBed(back, a, AnemoneLights.ENTRY_BED_POS);
            AnemoneLights.tryLight(back, a, stands.get(0), lampStack(a));
            AnemoneLights.clearGroups(back, seed);
            check(f, AnemoneLights.litCount(BackGroupData.get(back).of(ga)) == 0 && AnemoneLights.litCount(BackGroupData.get(back).of(gb)) == 0,
                    "clearGroups left lamps in a record");
            check(f, AnemoneLights.openingsRunning() == 0, "clearGroups left a gate-opening sequence running");

            // ---- 持ち歩きの光: 持っている間だけ目の位置の水が見えない光になり、離れる・しまうと水に戻る
            BlockPos eye = stands.get(0).offset(0, 2, 0);
            check(f, back.getBlockState(eye).is(Blocks.WATER), "test setup: no water at " + eye);
            a.setPos(eye.getX() + 0.5, eye.getY() - 1.62 + 0.5, eye.getZ() + 0.5);
            AnemoneLights.updateCarryLight(back, a, true);
            probeLight = AnemoneLights.carryLightAt(ga);
            check(f, probeLight != null && back.getBlockState(probeLight).is(Blocks.LIGHT) && back.getBlockState(probeLight).getValue(LightBlock.WATERLOGGED),
                    "the carried light was not placed in the water (" + probeLight + ")");
            AnemoneLights.updateCarryLight(back, a, false);
            check(f, probeLight != null && back.getBlockState(probeLight).is(Blocks.WATER) && AnemoneLights.carryLightAt(ga) == null,
                    "the carried light did not return the water");
            probeLight = null;

            // ---- 初回を終えたグループも、戸は灯で開く (周回は初回と同じ。既知の道はない)
            check(f, !GroupGates.isOpen(back, gc, gate), "test setup: gate open for a fresh group");
            FirstRun.onFirstCleared(back, List.of(gc));
            check(f, !GroupGates.isOpen(back, gc, gate), "the exit gate opened for a group that finished its first run without lamps");
            check(f, !GroupGates.isOpen(back, gb, gate), "the exit gate opened for a first-run group");
        } finally {
            AnemoneLights.extraMembers = List.of();
            if (probeLight != null) {
                AnemoneLights.updateCarryLight(back, a, false);
            }
            for (UUID id : List.of(ga, gb, gc)) {
                GroupGates.reset(back, id);
                BackGroupData.get(back).remove(id);
            }
        }
        return f;
    }
}
