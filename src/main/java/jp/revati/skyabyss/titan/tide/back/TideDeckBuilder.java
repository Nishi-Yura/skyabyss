package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.*;

import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout.Area;
import jp.revati.skyabyss.trial.RingArenaCell;
import jp.revati.skyabyss.trial.tide.T02Crossing;
import jp.revati.skyabyss.trial.tide.T06Choir;
import jp.revati.skyabyss.trial.tide.T07Seas;
import jp.revati.skyabyss.trial.tide.T10Ring;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

// 試練の階 (TideDeck。docs/design/29) を建てる。TideIslandBuilder.buildRest の終わり (固定の部屋のあと、休み場の焚き火を置く前) から呼ぶ。
// 部屋を掘る (体の中は詰まった石なので、掘るだけでよい) → 戸口 → セルごとの仕掛け (入江・円環・貝・水槽) → 石と戻りの光る床 → 灯り・碑・板。
// 戸 (封印の扉) は TrialRuntime.installGates が置く (戸口は空けておく)。
public final class TideDeckBuilder {
    private TideDeckBuilder() {
    }

    private static final BlockState AIR = TideKit.AIR;
    private static final BlockState LAMP = TideKit.LAMP;
    private static final BlockState GATE = ModBlocks.SEALED_GATE.get().defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    // 開発・自己テスト: 試練の階だけを置き直す (休み場の焚き火も)
    public static void buildDeck(TideKit kit) {
        build(kit);
        for (TideBackLayout.Rest r : TideDeck.rests()) {
            kit.campfire(r.campfire());
        }
    }

    static void build(TideKit kit) {
        if (!placed()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        hall(kit);
        t02(kit);
        t10(kit);
        t06(kit);
        t07(kit);
        // 戸口の脇の碑 (どの試練の戸か。入口の一言)・広間の案内
        for (TideBackLayout.Inscription i : SIGNS) {
            kit.wallSign(i.pos(), i.facing(), i.key());
        }
        kit.set(BOARD_H, ModBlocks.TRIAL_BOARD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        jp.revati.skyabyss.SkyAbyss.LOGGER.info("Built the tide trial deck in {} ms", System.currentTimeMillis() - t0);
    }

    // ---------------------------------------------------------------- 共通
    // 内側を掘り、床・天井の石を敷く (まわりの壁には触れない。細い通路が隣の部屋の壁を挟むため)
    private static void carve(TideKit kit, Area a) {
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                kit.set(x, a.floor(), z, kit.floorAt(x, z));
                for (int y = a.floor() + 1; y <= a.floor() + a.height(); y++) {
                    kit.set(x, y, z, AIR);
                }
                kit.set(x, a.floor() + a.height() + 1, z, kit.wall(x, a.floor() + a.height() + 1, z));
            }
        }
    }

    // 部屋 (外周の壁つき)。床の内側も敷く
    private static void room(TideKit kit, Area a) {
        kit.room(a, kit.floorAt(a.x1(), a.z1()));
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                kit.set(x, a.floor(), z, kit.floorAt(x, z));
            }
        }
    }

    private static void open(TideKit kit, List<BlockPos> door) {
        for (BlockPos p : door) {
            kit.set(p, AIR);
        }
    }

    // 灯り: 壁のランタン (固い所だけ)
    private static void lampsAlong(TideKit kit, Area a, int y, int step) {
        for (int z = a.z1() + 1; z <= a.z2(); z += step) {
            kit.lampIn(a.x1() - 1, y, z);
            kit.lampIn(a.x2() + 1, y, z);
        }
    }

    private static void floorLamp(TideKit kit, int x, int z) {
        if (!kit.get(x, F, z).isAir() && !kit.get(x, F, z).is(ModBlocks.SEALED_GATE.get())) {
            kit.set(x, F, z, LAMP);
        }
    }

    // ---------------------------------------------------------------- 通路と試練の広間
    private static void hall(TideKit kit) {
        kit.zone(TideZone.VAULT);
        room(kit, H);
        carve(kit, TC);
        // 潮の広間の南の段の壁を抜く戸口 (TC の最初の行)。南の段の灯りの脇に、通路の灯り
        for (int z = 30; z <= 36; z += 3) {
            kit.lampIn(TC.x1() - 1, F + 3, z);
            kit.lampIn(TC.x2() + 1, F + 3, z);
        }
        // 広間: 床の灯り (二列)・天井から吊るした灯り
        for (int x = -6; x <= 88; x += 6) {
            floorLamp(kit, x, 40);
            floorLamp(kit, x + 3, 44);
        }
        for (int x = 0; x <= 84; x += 12) {
            kit.lampIn(x, F + 11, 42);
        }
        // 四つの戸口 (H の南の壁 2 枚。戸は installGates が置く)
        for (List<BlockPos> g : List.of(T02_GATE, T10_GATE, T06_GATE, T07_GATE)) {
            open(kit, g);
        }
    }

    // ---------------------------------------------------------------- T02 潮の背渡り: 入江
    private static void t02(TideKit kit) {
        kit.zone(TideZone.BEACH);
        room(kit, T02);
        int x1 = T02.x1();
        int x2 = T02.x2();
        // 入江: 内側 (床の行から下) を掘り、底は砂、水は 71〜74 (深さ 4。水面は岸の床の面とほぼ同じ)。水の上には何もない (砂州・浮き石の戸が上に載る)
        for (int x = x1; x <= x2; x++) {
            for (int z = T02_SHORE_Z2 + 1; z < T02_FAR_Z1; z++) {
                for (int y = TideDeck.T02_BED + 1; y <= F; y++) {
                    kit.set(x, y, z, AIR);
                }
                kit.set(x, T02_BED, z, TideKit.SAND);
                for (int y = T02_BED + 1; y <= F; y++) {
                    kit.set(x, y, z, WATER);
                }
            }
        }
        // 水の底の灯り (水の中も暗くならないよう、四マスおきに海のランタンを底へ埋める)
        for (int x = x1 + 1; x <= x2; x += 4) {
            for (int z = T02_SHORE_Z2 + 2; z < T02_FAR_Z1; z += 4) {
                kit.set(x, T02_BED, z, LAMP);
            }
        }
        // 中島
        Area is = T02_ISLET;
        for (int x = is.x1(); x <= is.x2(); x++) {
            for (int z = is.z1(); z <= is.z2(); z++) {
                for (int y = T02_BED; y < F; y++) {
                    kit.set(x, y, z, TideKit.SANDSTONE);
                }
                kit.set(x, F, z, kit.floorAt(x, z));
            }
        }
        // 砂州と浮き石 (三つの配置すべての分。封印の扉のブロック。グループごとに立つ・消える)
        for (BlockPos p : T02Crossing.union()) {
            kit.set(p, GATE);
        }
        // 灯り: 壁 (岸と入江の両わき)・入江の真ん中の天井・岸の床
        lampsAlong(kit, T02, F + 2, 4);
        for (int z = 50; z <= 91; z += 4) {
            kit.lampIn(12, F + 10, z);
        }
        for (int[] at : new int[][] {{4, 51}, {20, 51}, {4, 53}, {20, 53}, {4, 89}, {20, 89}, {4, 91}, {20, 91}, {8, 69}, {16, 70}}) {
            floorLamp(kit, at[0], at[1]);
        }
        kit.set(T02_STONE, TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, false));
        kit.set(T02_PAD, LAMP);
    }

    // ---------------------------------------------------------------- T10 満ち潮の円環
    private static void t10(TideKit kit) {
        kit.zone(TideZone.HATCH);
        room(kit, T10_FOYER_N);
        room(kit, T10_FOYER_S);
        int cx = T10_CX;
        int cz = T10_CZ;
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                double r = Math.hypot(dx, dz);
                int x = cx + dx;
                int z = cz + dz;
                if (r <= RingArenaCell.R_HALL) {
                    for (int y = F + 1; y <= F + 9; y++) {
                        kit.set(x, y, z, AIR);
                    }
                    if (r > RingArenaCell.R_CORE) {
                        // 穴 (深さ 7)。水は床と同じ高さまで満ちる (水面は床の面とほぼ同じ。泳いで足場へ上がれる)
                        for (int y = F - 6; y <= F; y++) {
                            kit.set(x, y, z, WATER);
                        }
                    } else {
                        kit.set(x, F, z, r <= 1.6 ? TideKit.NACRE : kit.floorAt(x, z));
                    }
                } else if (r <= RingArenaCell.R_HALL + 1.2) {
                    int k = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 15.0);
                    if (Math.floorMod(k, 2) == 0) {
                        kit.lampIn(x, F + (Math.floorMod(k / 2, 2) == 0 ? 2 : 6), z);
                    }
                }
            }
        }
        T10Ring ring = new T10Ring();
        for (BlockPos p : ring.discCells()) {
            kit.set(p, GATE);
        }
        open(kit, T10Ring.BRIDGE_IN.stream().flatMap(p -> java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(p::above)).toList());
        open(kit, T10Ring.BRIDGE_OUT.stream().flatMap(p -> java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(p::above)).toList());
        for (BlockPos p : T10Ring.BRIDGE_IN) {
            kit.set(p, GATE);
        }
        for (BlockPos p : T10Ring.BRIDGE_OUT) {
            kit.set(p, GATE);
        }
        // 灯り: 芯の床の輪・広間の天井・穴の底・控え
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            floorLamp(kit, cx + (int) Math.round(Math.cos(a) * 3.2), cz + (int) Math.round(Math.sin(a) * 3.2));
            floorLamp(kit, cx + (int) Math.round(Math.cos(a + Math.PI / 6) * 4.8), cz + (int) Math.round(Math.sin(a + Math.PI / 6) * 4.8));
            kit.set(cx + (int) Math.round(Math.cos(a) * 6), F + 10, cz + (int) Math.round(Math.sin(a) * 6), LAMP);
        }
        kit.set(cx, F + 10, cz, LAMP);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            kit.set(cx + (int) Math.round(Math.cos(a) * 8), F - 7, cz + (int) Math.round(Math.sin(a) * 8), LAMP);
        }
        lampsAlong(kit, T10_FOYER_N, F + 2, 4);
        lampsAlong(kit, T10_FOYER_S, F + 2, 4);
        kit.set(T10_STONE, TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, false));
        kit.set(T10_PAD, LAMP);
    }

    // ---------------------------------------------------------------- T06 貝の合唱
    private static void t06(TideKit kit) {
        kit.zone(TideZone.FIN);
        room(kit, T06);
        room(kit, T06_STONE_ROOM);
        open(kit, T06_EXIT);
        for (int i = 0; i < T06Choir.PEDS; i++) {
            BlockPos p = T06Choir.pedestal(i);
            kit.set(p, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        }
        kit.set(T06Choir.LECTERN, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.WEST));
        for (int x : new int[] {58, 62, 66}) {
            for (int z : new int[] {51, 55, 59, 63}) {
                floorLamp(kit, x, z);
            }
        }
        lampsAlong(kit, T06, F + 3, 4);
        lampsAlong(kit, T06_STONE_ROOM, F + 2, 3);
        for (int z = 50; z <= 63; z += 4) {
            kit.lampIn(62, F + 11, z);
        }
        floorLamp(kit, 62, 69);
        kit.set(T06_STONE, TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, false));
        kit.set(T06_PAD, LAMP);
    }

    // ---------------------------------------------------------------- T07 二つの海
    private static void t07(TideKit kit) {
        kit.zone(TideZone.GAUGE);
        room(kit, T07);
        room(kit, T07_STONE_ROOM);
        open(kit, T07_EXIT);
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int tank = 0; tank < 2; tank++) {
            int cx = T07Seas.TANK_X[tank];
            int cz = T07Seas.TANK_Z;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean ring = x == cx - 2 || x == cx + 2 || z == cz - 2 || z == cz + 2;
                    for (int y = F + 1; y <= F + T07Seas.MAX + 1; y++) {
                        boolean cap = y == F + T07Seas.MAX + 1;
                        kit.set(x, y, z, ring || cap ? glass : AIR);
                    }
                    kit.set(x, F, z, Blocks.PRISMARINE_BRICKS.defaultBlockState());
                }
            }
            for (int k = 1; k <= T07Seas.MAX; k++) {
                kit.set(T07Seas.scale(tank, k), Blocks.DARK_PRISMARINE.defaultBlockState());
            }
        }
        BlockState lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.SOUTH);
        for (BlockPos p : T07Seas.PLUGS) {
            kit.set(p, lever);
        }
        kit.set(T07Seas.RESET, lever);
        for (int x : new int[] {74, 80, 86}) {
            for (int z : new int[] {51, 53, 61}) {
                floorLamp(kit, x, z);
            }
        }
        floorLamp(kit, 73, 50);
        floorLamp(kit, 87, 50);
        floorLamp(kit, 73, 61);
        floorLamp(kit, 87, 61);
        lampsAlong(kit, T07, F + 3, 4);
        lampsAlong(kit, T07_STONE_ROOM, F + 2, 3);
        for (int x = 74; x <= 86; x += 6) {
            for (int z : new int[] {52, 61}) {
                kit.lampIn(x, F + 13, z);
            }
        }
        floorLamp(kit, 80, 67);
        kit.set(T07_STONE, TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, false));
        kit.set(T07_PAD, LAMP);
    }
}
