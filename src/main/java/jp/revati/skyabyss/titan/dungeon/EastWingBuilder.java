package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 東の翼「番兵の回廊」(版 18: 一本道。26 の 8.6): 広間を順に制圧していく戦いの回廊。戻らずに先へ進む。
//   東の前室 (休み場) → 第一の広間 (練兵場) → 北の戸 → 控えの間 (休み場) → 第二の広間 (射手の回廊) → 北の戸 → 前室 (休み場) →
//   番兵の詰所 (老番兵。割符) → 西の戸 (割符の戸) → 通路 → 番兵長の広間 (共鳴石) → 鼓動の間へ戻る扉
// 戸は手前の広間を鎮めると開く (E1_EXIT・E2_EXIT・E3_LOCK)。広間に入ると入口が閉じ、番兵の波が押し寄せる。倒しきると次の戸が開く (TitanBackTrials)。
// ゾーンごとに材質を替える (ZonePalette: 練兵場 = 石レンガと苔と黒樫、射手の回廊 = 安山岩とトウヒ、詰所 = トウヒの板張り、番兵長の広間 = 黒い磨き石と金)。
// 広間の床には、入口から次の戸まで、床に埋めた灯りの道がある。仕切り (VEIL_*) は少人数のときだけ、そのグループの広間を狭くする
final class EastWingBuilder {
    private EastWingBuilder() {
    }

    // 灯りの道 (入口の戸から次の戸まで。x, z の折れ線)
    static final int[] ROUTE_E1 = {34, -36, 44, -36, 44, -54};
    static final int[] ROUTE_E2 = {47, -70, 47, -92};
    static final int[] ROUTE_CK = {47, -101, 47, -103, 41, -103};
    static final int[] ROUTE_E3 = {32, -103, 26, -103, 26, -57};

    static void build(BuildKit kit) {
        kit.palette(ZonePalette.DRILL);
        kit.room(E0);
        kit.roomSolid(E1);
        kit.palette(ZonePalette.GALLERY);
        kit.roomSolid(CE1);
        kit.roomSolid(E2);
        kit.palette(ZonePalette.BARRACKS);
        kit.roomSolid(CK_PASS);
        kit.roomSolid(CK);
        kit.palette(ZonePalette.CAPTAIN);
        kit.roomSolid(E3_CORRIDOR);
        kit.room(E3);
        List<List<BlockPos>> doors = List.of(E1_ENTRANCE, E1_EXIT, E2_ENTRANCE, E2_EXIT, CK_ENTRANCE, E3_LOCK, E3_ENTRANCE);
        for (List<BlockPos> door : doors) {
            for (BlockPos p : door) {
                kit.set(p, BuildKit.AIR);
            }
        }
        // 戸の枠 (どの戸も、くぐる側の材質で)
        kit.palette(ZonePalette.DRILL);
        kit.doorFrame(E1_ENTRANCE, FLOOR);
        kit.doorFrame(E1_EXIT, FLOOR);
        kit.palette(ZonePalette.GALLERY);
        kit.doorFrame(E2_ENTRANCE, FLOOR);
        kit.doorFrame(E2_EXIT, FLOOR);
        kit.palette(ZonePalette.BARRACKS);
        kit.doorFrame(CK_ENTRANCE, FLOOR);
        kit.doorFrame(E3_LOCK, FLOOR);
        kit.palette(ZonePalette.CAPTAIN);
        kit.doorFrame(E3_ENTRANCE, FLOOR);
        // 出口は制圧するまで閉じている (入口は戦いが始まると閉じる)。E2 は戦わない通路なので戸は閉じない (空いたまま)
        for (List<BlockPos> exit : List.of(E1_EXIT, E3_LOCK)) {
            for (BlockPos p : exit) {
                kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
            }
        }

        kit.palette(ZonePalette.DRILL);
        drillHall(kit);
        kit.palette(ZonePalette.GALLERY);
        galleryHall(kit);
        kit.palette(ZonePalette.BARRACKS);
        barracks(kit);
        kit.palette(ZonePalette.CAPTAIN);
        captainHall(kit);
        throneDais(kit);
        // 番兵長の広間から鼓動の間へ戻る扉 (石が歌うまで封じられている)。広間を掘ったあとに置く
        for (BlockPos p : EAST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        // 休み場: 東の前室 (練兵場の材質)、控えの間 (射手の回廊の材質)、詰所の前室 (詰所の材質)
        kit.palette(ZonePalette.DRILL);
        kit.restPoint(E0);
        kit.wallLamps(E0, 3, FLOOR + 4);
        kit.pilasters(E0, 3);
        kit.moss(E0, 0.05f);
        kit.palette(ZonePalette.GALLERY);
        kit.restPoint(CE1);
        kit.wallLamps(CE1, 3, FLOOR + 4);
        kit.pilasters(CE1, 4);
        kit.palette(ZonePalette.BARRACKS);
        kit.restPoint(CK_PASS);
        kit.wallLamps(CK_PASS, 3, FLOOR + 4);
        kit.pilasters(CK_PASS, 4);
        kit.palette(ZonePalette.CAPTAIN);
        kit.wallLamps(E3_CORRIDOR, 3, FLOOR + 4);
        kit.palette(ZonePalette.DEEP);
    }

    // ---------------------------------------------------------------- 第一の広間: 番兵の練兵場
    // 市松の床・石レンガと苔の壁・黒樫の梁・暖かい灯り。両側の壁に番兵の赤い旗。東西の列に身を隠す柱。入口から北の戸まで、灯りの道
    private static void drillHall(BuildKit kit) {
        hallFloorAndPillars(kit, E1, ROUTE_E1);
        kit.pilasters(E1, 5);
        kit.wallLamps(E1, 5, E1.floorY() + 6);
        kit.ribs(E1, 6, true);
        lanterns(kit, E1, 8, 3);
        banners(kit, E1, Blocks.RED_WALL_BANNER, 7);
        kit.moss(E1, 0.03f);
        clearSpawns(kit, E1);
        // 北の戸の両脇の松明代わりの灯り (戸が見つけやすい)
        for (int x : new int[]{E1_EXIT.get(0).getX() - 2, E1_EXIT.get(E1_EXIT.size() - 1).getX() + 2}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                kit.set(x, y, E1.z1(), y == FLOOR + 3 ? kit.pal().lamp() : kit.pal().pillarAlong(Direction.Axis.Y));
            }
        }
        // 演習の的: 干し草の山と柵 (西の壁ぎわ。湧き場所と入口の前は避ける)
        for (int z : new int[]{-50, -16}) {
            kit.set(E1.x1(), FLOOR + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            kit.set(E1.x1(), FLOOR + 2, z, Blocks.HAY_BLOCK.defaultBlockState());
            kit.set(E1.x1() + 1, FLOOR + 1, z, Blocks.OAK_FENCE.defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- 射手の回廊 (版 22: 戦わない通路。第一の広間と詰所のあいだの息つぎ)
    // 縦の縞の床 (明暗)・磨いた安山岩の壁・トウヒの梁・白い旗。入口から北の戸口まで灯りの道。敵は湧かない (東の翼の戦いは 3 つ)
    private static void galleryHall(BuildKit kit) {
        hallFloorAndPillars(kit, E2, ROUTE_E2);
        for (int x = E2.x1(); x <= E2.x2(); x++) {
            if ((x - E2.x1()) % 4 < 2) {
                continue;
            }
            for (int z = E2.z1(); z <= E2.z2(); z++) {
                if (kit.get(x, FLOOR, z).is(kit.pal().floor().getBlock())) {
                    kit.set(x, FLOOR, z, kit.pal().floorAlt());
                }
            }
        }
        kit.pilasters(E2, 5);
        kit.wallLamps(E2, 4, E2.floorY() + 5);
        kit.ribs(E2, 5, true);
        lanterns(kit, E2, 8, 3);
        banners(kit, E2, Blocks.LIGHT_GRAY_WALL_BANNER, 6);
    }

    // ---------------------------------------------------------------- 番兵の詰所: 兵舎
    // トウヒの板張り。簡易寝台 (板の台に赤い絨毯)・樽・矢作りと砥石の作業台・赤い旗。入口から西の戸 (割符の戸) まで灯りの道
    private static void barracks(BuildKit kit) {
        hallFloorAndPillars(kit, CK, ROUTE_CK);
        kit.pilasters(CK, 5);
        kit.wallLamps(CK, 5, CK.floorY() + 5);
        kit.ribs(CK, 5, false);
        lanterns(kit, CK, 7, 2);
        banners(kit, CK, Blocks.RED_WALL_BANNER, 6);
        // 寝台: 東の壁ぎわの 2 列 (板 + 赤い絨毯)
        for (int[] cot : new int[][]{{-115, -113}, {-108, -106}}) {
            for (int z = cot[0]; z <= cot[1]; z++) {
                for (int x = CK.x2() - 1; x <= CK.x2(); x++) {
                    kit.set(x, FLOOR + 1, z, Blocks.SPRUCE_SLAB.defaultBlockState());
                    kit.carpet(x, FLOOR + 2, z, Blocks.RED_CARPET);
                }
            }
        }
        // 樽の山 (北西の隅)
        for (int x = CK.x1(); x <= CK.x1() + 1; x++) {
            for (int z = CK.z1(); z <= CK.z1() + 1; z++) {
                kit.set(x, FLOOR + 1, z, Blocks.BARREL.defaultBlockState());
                if ((x + z) % 2 == 0) {
                    kit.set(x, FLOOR + 2, z, Blocks.BARREL.defaultBlockState());
                }
            }
        }
        // 作業台 (北の壁ぎわの中ほど)
        int cx = (CK.x1() + CK.x2()) / 2;
        kit.set(cx - 1, FLOOR + 1, CK.z1(), Blocks.FLETCHING_TABLE.defaultBlockState());
        kit.set(cx, FLOOR + 1, CK.z1(), Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.GrindstoneBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        kit.set(cx + 1, FLOOR + 1, CK.z1(), Blocks.SMITHING_TABLE.defaultBlockState());
        clearSpawns(kit, CK);
        kit.lantern(CK.x1() + 1, FLOOR + 1, CK.z1() + 2);
    }

    // ---------------------------------------------------------------- 番兵長の広間
    // 黒い磨き石と金の差し色。入口から奥の台まで赤い絨毯の道、両脇に金の柱 (灯りを載せる)。北の端の戸口に着くと、真っすぐ南の台座を望む
    private static void captainHall(BuildKit kit) {
        // 市松の床は磨いた黒石。絨毯の道は幅 3 (x 25〜27)
        hallFloorAndPillars(kit, E3, ROUTE_E3);
        for (int z = E3.z1() + 2; z <= E3.z2() - 4; z++) {
            for (int x = 25; x <= 27; x++) {
                kit.carpet(x, FLOOR + 1, z, Blocks.RED_CARPET);
            }
        }
        // 金の柱 (絨毯の道の両脇、8 マスおき。灯りを載せる)
        for (int z = E3.z1() + 6; z <= E3.z2() - 6; z += 8) {
            for (int x : new int[]{23, 29}) {
                for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
                    kit.set(x, y, z, y == FLOOR + 5 ? kit.pal().cap() : kit.pal().pillarAlong(Direction.Axis.Y));
                }
                kit.set(x, FLOOR + 6, z, kit.pal().lamp());
            }
        }
        kit.pilasters(E3, 6);
        kit.wallLamps(E3, 5, E3.floorY() + 6);
        kit.ribs(E3, 6, true);
        lanterns(kit, E3, 8, 3);
        banners(kit, E3, Blocks.BLACK_WALL_BANNER, 8);
        clearSpawns(kit, E3);
    }

    // 番兵長の広間の奥 (共鳴石のある南端): 石を囲む彫刻の床と、両脇の灯り。抜け道の前は空けておく
    private static void throneDais(BuildKit kit) {
        BlockPos stone = RESONANCE_STONES.get(1);
        for (int x = stone.getX() - 2; x <= stone.getX() + 2; x++) {
            for (int z = stone.getZ() - 2; z <= stone.getZ() + 1; z++) {
                kit.set(x, FLOOR, z, (x + z) % 2 == 0 ? BuildKit.CHISELED : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
        }
        kit.lantern(stone.getX() - 3, FLOOR + 1, stone.getZ());
        kit.lantern(stone.getX() + 3, FLOOR + 1, stone.getZ());
        // 台座の両脇の燭台 (ろうそく)
        for (int dx : new int[]{-2, 2}) {
            kit.set(stone.getX() + dx, FLOOR + 1, stone.getZ() - 2, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
            kit.set(stone.getX() + dx, FLOOR + 2, stone.getZ() - 2, Blocks.CANDLE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 4).setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        }
    }

    // ---------------------------------------------------------------- 部品
    // 床の縁取りと市松・灯りの道・身を隠す柱 (間隔 8 マス。戸口の前・灯りの道・湧き場所は避ける)
    private static void hallFloorAndPillars(BuildKit kit, Box hall, int[] route) {
        BlockState a = kit.pal().floor();
        BlockState b = kit.pal().floorAlt();
        for (int x = hall.x1(); x <= hall.x2(); x++) {
            for (int z = hall.z1(); z <= hall.z2(); z++) {
                if (!kit.get(x, hall.floorY(), z).isAir()) {
                    kit.set(x, hall.floorY(), z, (x + z) % 2 == 0 ? a : b);
                }
            }
        }
        kit.floorBorder(hall);
        List<int[]> cells = BuildKit.line(route);
        kit.routeStrip(hall.floorY(), cells, kit.pal().glow(), a);
        java.util.Set<Long> onRoute = new java.util.HashSet<>();
        for (int[] c : cells) {
            onRoute.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL));
        }
        for (int x = hall.x1(); x <= hall.x2(); x++) {
            for (int z = hall.z1(); z <= hall.z2(); z++) {
                if ((x - hall.x1()) % 8 == 4 && (z - hall.z1()) % 8 == 4 && !isSpawnPoint(hall, x, z)
                        && x > hall.x1() && x < hall.x2() && z > hall.z1() && z < hall.z2()
                        && !nearRoute(onRoute, x, z)) {
                    for (int y = hall.floorY() + 1; y < hall.ceilingY(); y++) {
                        kit.set(x, y, z, y == hall.ceilingY() - 1 ? kit.pal().cap() : y == hall.floorY() + 1 ? kit.pal().floorAlt()
                                : y == hall.floorY() + 3 ? kit.pal().glow() : kit.pal().pillarAlong(Direction.Axis.Y));
                    }
                }
            }
        }
    }

    private static boolean nearRoute(java.util.Set<Long> route, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (route.contains(((long) (x + dx) << 32) ^ ((z + dz) & 0xffffffffL))) {
                    return true;
                }
            }
        }
        return false;
    }

    // 天井から吊るす灯り (格子)
    private static void lanterns(BuildKit kit, Box hall, int step, int chain) {
        for (int x = hall.x1() + 3; x <= hall.x2() - 3; x += step) {
            for (int z = hall.z1() + 3; z <= hall.z2() - 3; z += step) {
                kit.hangingLantern(x, z, hall.ceilingY(), chain);
            }
        }
    }

    // 長い壁 (東西) の内側の面に旗を、間隔 spacing で。戸口・柱の前は壁が空気のとき自然に除かれる
    private static void banners(BuildKit kit, Box hall, Block banner, int spacing) {
        int y = hall.floorY() + 5;
        for (int z = hall.z1() + 3; z <= hall.z2() - 3; z += spacing) {
            kit.wallBanner(hall.x1(), y, z, Direction.EAST, banner);
            kit.wallBanner(hall.x2(), y, z, Direction.WEST, banner);
        }
    }

    // 番兵の湧く場所には、苔・絨毯・飾りを残さない
    private static void clearSpawns(BuildKit kit, Box hall) {
        for (BlockPos p : spawnPoints(hall)) {
            BlockState s = kit.get(p.getX(), p.getY(), p.getZ());
            if (!s.isAir() && !s.is(ModBlocks.SEALED_GATE.get())) {
                kit.set(p, BuildKit.AIR);
            }
        }
    }

    // 戦いの湧き場所 (TitanBackTrials が使う)。広間の四隅の内側・辺の中ほど・中央 (9 か所)
    public static List<BlockPos> spawnPoints(Box hall) {
        BlockPos c = hall.center();
        int y = hall.floorY() + 1;
        int x1 = hall.x1() + 2;
        int x2 = hall.x2() - 2;
        int z1 = hall.z1() + 2;
        int z2 = hall.z2() - 2;
        List<BlockPos> list = new ArrayList<>();
        list.add(new BlockPos(x1, y, z1));
        list.add(new BlockPos(x2, y, z1));
        list.add(new BlockPos(x1, y, z2));
        list.add(new BlockPos(x2, y, z2));
        list.add(new BlockPos(c.getX(), y, c.getZ()));
        list.add(new BlockPos(c.getX(), y, z1));
        list.add(new BlockPos(c.getX(), y, z2));
        list.add(new BlockPos(x1, y, c.getZ()));
        list.add(new BlockPos(x2, y, c.getZ()));
        return List.copyOf(list);
    }

    private static boolean isSpawnPoint(Box hall, int x, int z) {
        for (BlockPos p : spawnPoints(hall)) {
            if (Math.abs(p.getX() - x) <= 1 && Math.abs(p.getZ() - z) <= 1) {
                return true;
            }
        }
        return false;
    }
}
