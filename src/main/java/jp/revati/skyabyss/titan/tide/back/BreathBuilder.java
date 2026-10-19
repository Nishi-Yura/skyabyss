package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.BreathLayout.*;
import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 息の間を建てる (docs/design/24 の案 C)。予約の間 1〜3 と月の窓の縦穴を、一つの大空洞にする。
// 順番 (TideIslandBuilder.buildRest から): upper (部屋・廊下) → hub (縦穴は気室の下 y 96 まで) → chamber (気室 A の掘り出し・水たまり・縁・皿) →
//   arena (塔の中の気室 B・舞台・風の柱) → skin (内壁) → tower (塔の屋根のガラス) → observatory (展望の間)。
// 水は水たまりと皿の盆だけ (どれも固い縁に囲まれて漏れない。潮の溜め (TideField) には入れない。潮は流れの強さを変えるだけ)
public final class BreathBuilder {
    private BreathBuilder() {
    }

    static final BlockState WATER = Blocks.WATER.defaultBlockState();
    static final BlockState GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
    static final BlockState AMBER = Blocks.OCHRE_FROGLIGHT.defaultBlockState();

    // 気室 A: 底の水たまりと縁・掘り出し・皿・壁の帯と灯り
    public static void chamber(TideKit kit) {
        int r = (int) Math.ceil(R_A) + 2;
        for (int x = CX - r; x <= CX + r; x++) {
            for (int z = CZ - r; z <= CZ + r; z++) {
                double d = dist(x, z);
                if (d > R_A) {
                    continue;
                }
                for (int y = POOL_FLOOR; y <= A_TOP; y++) {
                    BlockState s;
                    if (y == POOL_FLOOR) {
                        s = d <= 3.4 ? GLASS : TideKit.BRICK;
                    } else if (y < LIP) {
                        s = d <= POOL_R ? WATER : TideKit.BRICK;
                    } else if (y == LIP) {
                        s = d <= POOL_R ? WATER : lipFloor(x, z, d);
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        for (Dish dish : dishes()) {
            dish(kit, dish);
        }
        entryPad(kit);
        wallBands(kit);
    }

    // 縁の床 (潮の民の床石。ところどころ光る石)
    private static BlockState lipFloor(int x, int z, double d) {
        if (d > POOL_R + 0.6 && d <= POOL_R + 1.6 && Math.floorMod((int) Math.round(Math.atan2(z - CZ, x - CX) * 8 / Math.PI), 2) == 0) {
            return TideKit.NACRE;
        }
        return TideKit.FLOOR;
    }

    // 皿: 縁の輪 (人が立つ。上面は floorY + 1)、盆 (底は floorY - 1、水は floorY の 1 段)、鉢の裏 (丸い底)。
    // 近い泡 = 盆のまんなかの光る石 (海のランタン)、遠い泡 = 行き先の向きの琥珀色の石。輪の行き先の側は光る石の縁
    private static void dish(TideKit kit, Dish d) {
        int cx = d.cx();
        int cz = d.cz();
        int fy = d.floorY();
        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int z = cz - 4; z <= cz + 4; z++) {
                double dc = Math.hypot(x - cx, z - cz);
                if (dc > RING_R) {
                    continue;
                }
                boolean basin = dc <= BASIN_R;
                if (basin) {
                    kit.set(x, fy - 1, z, TideKit.BRICK);
                    kit.set(x, fy, z, WATER);
                } else {
                    kit.set(x, fy, z, TideKit.FLOOR);
                    kit.set(x, fy - 1, z, TideKit.BRICK);
                }
                if (dc <= 2.6) {
                    kit.set(x, fy - 2, z, TideKit.NACRE);
                }
                if (dc <= 1.8) {
                    kit.set(x, fy - 3, z, TideKit.DARK);
                }
                // 皿の上の空気 (気室を掘ったあとなので、すでに空気。皿 0 と縁の上の水が残らないように)
                for (int y = fy + 1; y <= fy + 4; y++) {
                    if (!kit.get(x, y, z).isAir() && dc <= RING_R) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 近い泡
        kit.set(cx, fy - 1, cz, TideKit.LAMP);
        // 遠い泡 (行き先がある皿だけ)
        List<Lift> lifts = lifts();
        for (Lift l : lifts) {
            if (l.far() && l.from() == 1 + d.index()) {
                double[] p = l.pad();
                kit.set((int) Math.floor(p[0]), fy - 1, (int) Math.floor(p[1]), AMBER);
            }
        }
        // 輪の縁の行き先の側: 近い泡の向きに海のランタン、遠い泡の向きに琥珀の石 (外側の輪の石に)
        for (Lift l : lifts) {
            if (l.from() != 1 + d.index() || l.to() == 10) {
                continue;
            }
            Node b = l.b();
            double[] p = l.pad();
            double ux = b.landX() - d.wx();
            double uz = b.landZ() - d.wz();
            double len = Math.hypot(ux, uz);
            ux /= len;
            uz /= len;
            for (int k = -1; k <= 1; k++) {
                double ax = ux * Math.cos(k * 0.38) - uz * Math.sin(k * 0.38);
                double az = ux * Math.sin(k * 0.38) + uz * Math.cos(k * 0.38);
                int x = (int) Math.floor(d.wx() + ax * 2.6);
                int z = (int) Math.floor(d.wz() + az * 2.6);
                if (!d.basinContains(x, z) && d.ringContains(x, z)) {
                    kit.set(x, fy, z, l.far() ? AMBER : TideKit.LAMP);
                }
            }
        }
        // 予約の間 2・3 の床と同じ高さの皿 (114・126): 皿の縁から気室の壁まで床で埋め、続く床へそのまま歩いて出られる (地続き。足場の休み場 u1・u2)
        if (fy == UPPER_2.floor() || fy == UPPER_3.floor()) {
            for (int x = cx - 6; x <= cx + 6; x++) {
                for (int z = cz - 6; z <= cz + 6; z++) {
                    double dw = dist(x, z);
                    double dc = Math.hypot(x - cx, z - cz);
                    if (dw > R_A - 2.2 && dw <= R_A + 0.6 && dc > RING_R && dc <= RING_R + 3.2) {
                        kit.set(x, fy, z, TideKit.FLOOR);
                        kit.set(x, fy - 1, z, TideKit.BRICK);
                        for (int y = fy + 1; y <= fy + 3; y++) {
                            if (dw <= R_A && !kit.get(x, y, z).isAir()) {
                                kit.set(x, y, z, TideKit.AIR);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void entryPad(TideKit kit) {
        int x = ENTRY_X;
        int z = ENTRY_Z;
        kit.set(x, LIP, z, TideKit.LAMP);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            kit.set(x + dir.getStepX(), LIP, z + dir.getStepZ(), TideKit.NACRE);
        }
    }

    // 壁の帯 (進みの輪) と壁の灯り (気室の壁の外側の 1 マス。すでに空気の所 = 予約の間の中は触らない)
    private static void wallBands(TideKit kit) {
        int r = (int) Math.ceil(R_A) + 3;
        for (int x = CX - r; x <= CX + r; x++) {
            for (int z = CZ - r; z <= CZ + r; z++) {
                double d = dist(x, z);
                if (d <= R_A || d > R_A + 1.2) {
                    continue;
                }
                for (int band : RING_BAND) {
                    if (!kit.get(x, band, z).isAir()) {
                        kit.set(x, band, z, TideKit.DARK);
                    }
                }
                double ang = Math.atan2(z - CZ, x - CX);
                if (Math.floorMod((int) Math.round(ang * 16 / Math.PI), 4) == 0) {
                    for (int y = LIP + 5; y <= A_TOP - 2; y += 7) {
                        if (!kit.get(x, y, z).isAir()) {
                            kit.set(x, y, z, TideKit.LAMP);
                        }
                    }
                }
            }
        }
    }

    // 気室 B (塔の中): 舞台の床の上を、y ごとの半径で掘る。外の壁が 1 ブロックでも空気に面するときは掘らない
    public static void arena(TideKit kit) {
        for (int y = ARENA_FLOOR + 1; y <= B_TOP; y++) {
            double rad = radiusB(y);
            int r = (int) Math.ceil(rad);
            for (int x = CX - r; x <= CX + r; x++) {
                for (int z = CZ - r; z <= CZ + r; z++) {
                    if (dist(x, z) > rad) {
                        continue;
                    }
                    if (!wallIntact(kit, x, y, z, rad)) {
                        continue;
                    }
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 舞台の床 (半径 ARENA_R の円。真ん中に海のランタンの輪)
        for (int x = CX - 7; x <= CX + 7; x++) {
            for (int z = CZ - 7; z <= CZ + 7; z++) {
                double d = dist(x, z);
                if (d > ARENA_R) {
                    continue;
                }
                BlockState s = TideKit.FLOOR;
                if (d >= 4.7 && d < 5.3 && Math.floorMod((int) Math.round(Math.atan2(z - CZ, x - CX) * 6 / Math.PI), 2) == 0) {
                    s = TideKit.LAMP;
                } else if (d < 1.5) {
                    s = TideKit.LAMP;
                } else if (d >= 2.8 && d < 3.4) {
                    s = TideKit.NACRE;
                }
                kit.set(x, ARENA_FLOOR, z, s);
            }
        }
        // 風の柱 (皿 7 の真上): 床の穴と、まわりの低い壁 (床から 2 段)
        Dish v = vent();
        for (int x = v.cx() - 3; x <= v.cx() + 3; x++) {
            for (int z = v.cz() - 3; z <= v.cz() + 3; z++) {
                double dv = Math.hypot(x - v.cx(), z - v.cz());
                if (dv <= VENT_HOLE_R) {
                    for (int y = v.floorY() + 1; y <= ARENA_FLOOR; y++) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                } else if (dv <= VENT_WALL_R && dist(x, z) <= ARENA_R) {
                    kit.set(x, ARENA_FLOOR + 1, z, TideKit.DARK);
                    kit.set(x, ARENA_FLOOR + 2, z, dv <= VENT_WALL_R - 0.6 || (x + z) % 2 == 0 ? TideKit.BRICK : TideKit.LAMP);
                }
            }
        }
        // 再戦の台 (周回のグループが真珠守りに挑み直す)
        kit.set(REMATCH.below(), TideKit.NACRE);
        kit.set(REMATCH, Blocks.LODESTONE.defaultBlockState());
        // 舞台の灯り (塔の壁。気室 B の半径のすぐ外)
        for (int y = ARENA_FLOOR + 6; y <= B_TOP - 2; y += 7) {
            double rad = radiusB(y);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8 + 0.2;
                int x = CX + (int) Math.round(Math.cos(a) * (rad + 1));
                int z = CZ + (int) Math.round(Math.sin(a) * (rad + 1));
                if (!kit.get(x, y, z).isAir() && dist(x, z) > rad) {
                    kit.set(x, y, z, TideKit.LAMP);
                }
            }
        }
    }

    // 掘る前の検査: 半径 rad のすぐ外 (rad + 1.5 まで) がすべて固いこと (外の壁が残る)
    static boolean wallIntact(TideKit kit, int x, int y, int z, double rad) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int nx = x + dx;
                int nz = z + dz;
                double nd = dist(nx, nz);
                if (nd > rad && nd <= rad + 1.5 && kit.get(nx, y, nz).isAir()) {
                    // 掘ったあとの空気ではなく、元から空気 (塔の外) の所
                    return false;
                }
            }
        }
        return true;
    }

    // 展望の間 (塔の頂の柱廊の中。ここへは舞台からまっすぐ昇る風の柱で来る)
    public static void observatory(TideKit kit) {
        int x1 = OBS_X1;
        int x2 = OBS_X2;
        int z1 = OBS_Z1;
        int z2 = OBS_Z2;
        // 壁 (外周 1 マス) と床・空気・屋根
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean edge = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
                for (int y = DECK_FLOOR; y <= OBS_ROOF; y++) {
                    BlockState s;
                    if (y == DECK_FLOOR) {
                        s = edge ? TideKit.DARK : deckFloor(x, z);
                    } else if (y == OBS_ROOF) {
                        s = Math.floorMod(x * 3 + z * 5, 11) == 0 ? TideKit.LAMP : TideKit.BRICK;
                    } else if (edge) {
                        s = y <= DECK_FLOOR + 1 ? TideKit.DARK : (window(x, z) ? GLASS : kit.wall(x, y, z));
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 屋根の真ん中の光の柱 (日と月の光が舞台まで落ちる。ガラス)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.set(CX + dx, OBS_ROOF, CZ + dz, GLASS);
            }
        }
        // 風の柱の穴 (半径 1.5)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.hypot(dx, dz) <= VENT_HOLE_R) {
                    kit.set(CX + dx, DECK_FLOOR, CZ + dz, TideKit.AIR);
                }
            }
        }
        // 穴のふち (低い輪。床の上の 1 段。海のランタンと石)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > VENT_HOLE_R && d <= 2.3) {
                    kit.set(CX + dx, DECK_FLOOR, CZ + dz, (dx + dz) % 2 == 0 ? TideKit.LAMP : TideKit.NACRE);
                }
            }
        }
        // 下りの近道の台 (縦穴の滑降)
        kit.set(RETURN_PAD, TideKit.LAMP);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            kit.set(RETURN_PAD.relative(dir), TideKit.NACRE);
        }
        // 追憶の石
        kit.set(KEEPSAKE.below(), TideKit.NACRE);
        kit.set(KEEPSAKE, Blocks.LODESTONE.defaultBlockState());
    }

    private static boolean window(int x, int z) {
        boolean ns = z == OBS_Z1 - 1 || z == OBS_Z2 + 1;
        return ns ? x >= -6 && x <= 4 : z >= -9 && z <= 3;
    }

    private static BlockState deckFloor(int x, int z) {
        double d = dist(x, z);
        if (d >= 6.0 && d < 6.6) {
            return TideKit.LAMP;
        }
        if (d >= 2.4 && d < 3.0) {
            return TideKit.NACRE;
        }
        return TideKit.FLOOR;
    }
}
