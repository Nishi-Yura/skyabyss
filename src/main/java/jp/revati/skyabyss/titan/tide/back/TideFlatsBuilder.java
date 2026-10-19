package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;

// 北の試練 群れの干潟 と、そこへ続く「首の廊下」(背骨の回廊) を建てる (26 の 9 章)。
//  首の廊下: 干潟の門 (z -24) → 長い廊下 (骨の肋がアーチを作る。両側の壁の窪みに碑と宝) → 前室 f0 → 廊下 → 干潟の口 (z -79)
//  干潟: 甲羅の前の洞 (孵り場)。泥と砂の浅瀬に岩の島 14、巣の塚 (5 つ。候補 10)、天井を渡る大きな骨の肋 (鯨骨の洞)、北の壁の窓から首と頭が見える
final class TideFlatsBuilder {
    private TideFlatsBuilder() {
    }

    // 廊下の壁の窪み (行き止まり。碑と宝)
    record Niche(String id, int x1, int x2, int z1, int z2, int openX1, int openX2, Direction face, BlockPos tablet, BlockPos cache) {
    }

    static final java.util.List<Niche> NICHES = java.util.List.of(
            // 西: 首の廊下の最初の長い所 (z -40〜-35)
            new Niche("neck_a", -10, -5, -40, -35, -4, -3, Direction.EAST, new BlockPos(-10, 68, -38), new BlockPos(-8, 67, -36)),
            // 東: 前室の手前 (z -46〜-41)
            new Niche("neck_b", 5, 10, -46, -41, 3, 4, Direction.WEST, new BlockPos(10, 68, -44), new BlockPos(8, 67, -42)));

    // 窓 (北西・北東の角): 甲羅の前の縁を抜ける細い窓。ガラスの向こうに首と頭と海が見える
    static void neck(TideKit kit) {
        kit.zone(TideZone.NECK);
        // 干潟の門から干潟の口まで (x -2〜2 が床、壁は ±3)
        for (int z = FLATS_GATE_Z; z >= MOUTH_Z; z--) {
            int f = floorAt(z);
            boolean inF0 = z >= F0.z1() && z <= F0.z2();
            int ceil = z >= -27 ? f + 5 : TUNNEL_FLOOR + 7;
            int half = inF0 ? F0.x2() + 1 : 3;
            for (int x = -half; x <= half; x++) {
                boolean wall = Math.abs(x) == half;
                for (int y = f - 1; y <= ceil; y++) {
                    BlockState s;
                    if (wall || (inF0 && y == ceil)) {
                        s = kit.wall(x, y, z);
                        if (!inF0 && y == f + 2 && Math.floorMod(z, 7) == 0) {
                            s = TideKit.LAMP;
                        }
                    } else if (y < f) {
                        s = TideKit.BRICK;
                    } else if (y == f) {
                        if (z <= FLATS_GATE_Z - 2 && z >= FLATS_GATE_Z - 4) {
                            kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                            continue;
                        }
                        s = kit.floorAt(x, z);
                        if (!inF0 && (Math.abs(x) == 2 || (x == 0 && Math.floorMod(z, 3) == 0))) {
                            s = Blocks.POLISHED_ANDESITE.defaultBlockState();
                        }
                    } else if (y == ceil) {
                        // 門の段は口を 3 マスの高さにする (水面の上のすき間から泳いで抜けないように)
                        s = TideZone.NECK.dark;
                    } else if (z == FLATS_GATE_Z && y == f + 4) {
                        s = TideZone.NECK.dark;
                    } else if (z == MOUTH_Z && y == TUNNEL_FLOOR + 4) {
                        s = TideZone.NECK.dark;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 肋のアーチ: 廊下の 6 マスおきに、壁の骨の柱とアーチ (骨の肋が天井へ回る)
        for (int z = -33; z >= MOUTH_Z + 3; z -= 6) {
            if (z >= F0.z1() - 1 && z <= F0.z2() + 1) {
                continue;
            }
            for (int y = TUNNEL_FLOOR + 1; y <= TUNNEL_FLOOR + 5; y++) {
                kit.set(-2, y, z, y == TUNNEL_FLOOR + 5 ? Blocks.BONE_BLOCK.defaultBlockState() : (y == TUNNEL_FLOOR + 3 ? TideKit.LAMP : Blocks.BONE_BLOCK.defaultBlockState()));
                kit.set(2, y, z, y == TUNNEL_FLOOR + 5 ? Blocks.BONE_BLOCK.defaultBlockState() : (y == TUNNEL_FLOOR + 3 ? TideKit.LAMP : Blocks.BONE_BLOCK.defaultBlockState()));
            }
            for (int x = -1; x <= 1; x++) {
                kit.set(x, TUNNEL_FLOOR + 6, z, Blocks.BONE_BLOCK.defaultBlockState());
            }
        }
        f0(kit);
        niches(kit);
    }

    // 干潟の口の階段 (廊下の北の端から干潟へ下りる 2 段。登る向きは南)。干潟を掘ったあとに置く
    static void mouthStairs(TideKit kit) {
        for (int i = 0; i < 2; i++) {
            int z = MOUTH_Z - 1 - i;
            for (int x = -MOUTH_HALF; x <= MOUTH_HALF; x++) {
                for (int y = FLATS_FLOOR; y < TUNNEL_FLOOR - 1 - i; y++) {
                    kit.set(x, y, z, Blocks.ANDESITE.defaultBlockState());
                }
                kit.stair(x, TUNNEL_FLOOR - 1 - i, z, Direction.SOUTH, Blocks.STONE_STAIRS.defaultBlockState());
                for (int y = TUNNEL_FLOOR; y <= TUNNEL_FLOOR + 3; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
    }

    // 廊下の床の高さ (門の段から 66 へ下りる)
    static int floorAt(int z) {
        if (z >= FLATS_GATE_Z - 1) {
            return HUB_FLOOR;
        }
        return switch (FLATS_GATE_Z - z) {
            case 2 -> 69;
            case 3 -> 68;
            case 4 -> 67;
            default -> TUNNEL_FLOOR;
        };
    }

    // 前室 f0 (z -64〜-48): 柱の間と、骨の肋のアーチ。焚き火は休み場の設え (fixtures)
    private static void f0(TideKit kit) {
        kit.zone(TideZone.NECK);
        Area a = F0;
        for (int x = a.x1() - 1; x <= a.x2() + 1; x++) {
            for (int z = a.z1() - 1; z <= a.z2() + 1; z++) {
                boolean in = x >= a.x1() && x <= a.x2() && z >= a.z1() && z <= a.z2();
                boolean door = Math.abs(x) <= 2 && (z == a.z1() - 1 || z == a.z2() + 1);
                for (int y = a.floor(); y <= a.floor() + a.height() + 1; y++) {
                    if (in) {
                        if (y == a.floor()) {
                            kit.set(x, y, z, f0Floor(x, z));
                        } else if (y == a.floor() + a.height() + 1) {
                            kit.set(x, y, z, Math.floorMod(x + z, 5) == 0 ? TideKit.LAMP : kit.wall(x, y, z));
                        } else {
                            kit.set(x, y, z, TideKit.AIR);
                        }
                    } else if (door && y > a.floor() && y <= a.floor() + 5) {
                        kit.set(x, y, z, TideKit.AIR);
                    } else if (y == a.floor()) {
                        kit.set(x, y, z, kit.floorAt(x, z));
                    } else {
                        kit.set(x, y, z, kit.wall(x, y, z));
                    }
                }
            }
        }
        // 四隅の骨の柱と、壁ぎわの灯りの柱
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int px = sx * (a.x2() - 1);
                int pz = (a.z1() + a.z2()) / 2 + sz * 6;
                for (int y = a.floor() + 1; y <= a.floor() + a.height(); y++) {
                    kit.set(px, y, pz, y == a.floor() + a.height() ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                }
                kit.set(px, a.floor() + 4, pz, TideKit.LAMP);
            }
        }
        // 天井を渡る肋 (前室の幅いっぱい、3 本)
        for (int z = a.z1() + 3; z <= a.z2() - 2; z += 5) {
            int top = a.floor() + a.height();
            for (int x = a.x1(); x <= a.x2(); x++) {
                int drop = Math.abs(x) >= a.x2() - 1 ? 2 : Math.abs(x) >= a.x2() - 3 ? 1 : 0;
                kit.set(x, top - drop, z, Blocks.BONE_BLOCK.defaultBlockState());
            }
        }
    }

    private static BlockState f0Floor(int x, int z) {
        if (Math.abs(x) <= 1 && Math.floorMod(z, 3) == 0) {
            return TideKit.LAMP;
        }
        return (Math.abs(x) + z) % 4 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
    }

    // 廊下の壁の窪み: 行き止まり。碑と宝の箱
    private static void niches(TideKit kit) {
        kit.zone(TideZone.NECK);
        for (Niche n : NICHES) {
            int floor = TUNNEL_FLOOR;
            for (int x = n.x1() - 1; x <= n.x2() + 1; x++) {
                for (int z = n.z1() - 1; z <= n.z2() + 1; z++) {
                    boolean in = x >= n.x1() && x <= n.x2() && z >= n.z1() && z <= n.z2();
                    for (int y = floor; y <= floor + 6; y++) {
                        BlockState s;
                        if (!in) {
                            s = kit.wall(x, y, z);
                        } else if (y == floor) {
                            s = Blocks.SMOOTH_BASALT.defaultBlockState();
                        } else if (y == floor + 6) {
                            s = (x + z) % 4 == 0 ? TideKit.LAMP : kit.wall(x, y, z);
                        } else {
                            s = TideKit.AIR;
                        }
                        kit.set(x, y, z, s);
                    }
                }
            }
            // 口 (壁 2 枚を抜く。高さ 4)。廊下の壁は x = ±3
            int mz = (n.z1() + n.z2()) / 2;
            for (int x = n.openX1(); x <= n.openX2(); x++) {
                for (int z = mz - 1; z <= mz + 1; z++) {
                    for (int y = floor; y <= floor + 4; y++) {
                        kit.set(x, y, z, y == floor ? Blocks.SMOOTH_BASALT.defaultBlockState() : TideKit.AIR);
                    }
                }
            }
            // 碑と宝箱
            TideCaches.place(kit, n.id());
            // 窪みの灯りの柱
            int px = n.face() == Direction.EAST ? n.x1() + 1 : n.x2() - 1;
            kit.set(px, floor + 1, n.z1(), Blocks.BONE_BLOCK.defaultBlockState());
            kit.set(px, floor + 2, n.z1(), TideKit.LAMP);
            kit.set(px, floor + 1, n.z2(), Blocks.BONE_BLOCK.defaultBlockState());
            kit.set(px, floor + 2, n.z2(), TideKit.LAMP);
        }
    }

    // ---------------------------------------------------------------- 干潟 (孵り場)
    static void flats(TideKit kit) {
        kit.zone(TideZone.HATCH);
        Area f = FLATS;
        for (int x = f.x1() - 2; x <= f.x2() + 2; x++) {
            for (int z = f.z1() - 2; z <= f.z2() + 2; z++) {
                boolean inside = x >= f.x1() && x <= f.x2() && z >= f.z1() && z <= f.z2();
                int edge = inside ? Math.min(Math.min(x - f.x1(), f.x2() - x), Math.min(z - f.z1(), f.z2() - z)) : -1;
                // 天井は壁ぎわで低く (76)、内へ 6 マスで FLATS_CEILING
                int ceil = FLATS_CEILING - Math.max(0, 6 - edge) * 1;
                for (int y = 58; y <= FLATS_CEILING + 1; y++) {
                    BlockState s;
                    if (!inside) {
                        // 壁の灯り (腰の高さ。天井の灯りは床まで届かないため)
                        s = y == FLATS_FLOOR + 4 && Math.floorMod(x + z, 6) == 0 ? TideKit.LAMP : kit.wall(x, y, z);
                    } else if (y < FLATS_FLOOR) {
                        s = TideKit.SANDSTONE;
                    } else if (y == FLATS_FLOOR) {
                        s = floorBlock(x, z);
                    } else if (y <= ceil) {
                        s = TideKit.AIR;
                    } else {
                        s = Math.floorMod(x * 3 + z * 7, 11) == 0 ? TideKit.LAMP : kit.wall(x, y, z);
                    }
                    kit.set(x, y, z, s);
                }
                // 天井から垂れた石 (鍾乳石)。干潟の真ん中の高い所だけ
                if (inside && edge >= 8 && TideIslandBuilder.noise(x, z, 3, 14) > 0.82) {
                    int len = 1 + (int) ((TideIslandBuilder.noise(x, z, 3, 14) - 0.82) * 30);
                    for (int i = 0; i < len; i++) {
                        kit.set(x, ceil - i, z, i == len - 1 ? Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN) : kit.wall(x, ceil - i, z));
                    }
                }
            }
        }
        // 岩の島 (7x7 の段。上 3x3 が床 66)
        for (int[] r : FLATS_ROCKS) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    int h = ring <= 1 ? ROCK_TOP : ring == 2 ? ROCK_TOP - 1 : ROCK_TOP - 2;
                    for (int y = FLATS_FLOOR; y <= h; y++) {
                        kit.set(r[0] + dx, y, r[1] + dz, y == h ? (ring <= 1 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState())
                                : Blocks.ANDESITE.defaultBlockState());
                    }
                }
            }
        }
        ribs(kit);
        windows(kit);
    }

    // 天井を渡る大きな骨の肋 (鯨骨の洞)。x ±37 の壁から天井の高みまでの半楕円を、北南に 9 本
    private static void ribs(TideKit kit) {
        Area f = FLATS;
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        for (int z = f.z1() + 6; z <= f.z2() - 4; z += 5) {
            if (Math.abs(z - (f.z1() + f.z2()) / 2) < 0) {
                continue;
            }
            for (int x = f.x1() + 1; x <= f.x2() - 1; x++) {
                double u = x / (double) (f.x2() - 1);
                int y = FLATS_FLOOR + 9 + (int) Math.round(Math.sqrt(Math.max(0, 1 - u * u)) * (FLATS_CEILING - FLATS_FLOOR - 11));
                int low = FLATS_FLOOR + 1;
                // 肋は上の半分の弧だけ (壁ぎわは地面から立ち上がる)
                if (Math.abs(u) > 0.93) {
                    for (int yy = low; yy <= y; yy++) {
                        kit.set(x, yy, z, bone);
                    }
                } else if (y < FLATS_CEILING - 1) {
                    kit.set(x, y, z, bone);
                    kit.set(x, y + 1, z, bone);
                } else {
                    kit.set(x, FLATS_CEILING - 1, z, bone);
                }
            }
        }
        // 肋の根もとの灯り
        for (int z = f.z1() + 6; z <= f.z2() - 4; z += 5) {
            kit.set(f.x1() + 1, FLATS_FLOOR + 5, z, TideKit.LAMP);
            kit.set(f.x2() - 1, FLATS_FLOOR + 5, z, TideKit.LAMP);
        }
    }

    // 北の角の窓: 干潟の北の壁から甲羅の前の縁の外まで、幅 3・高さ 2 の穴。外の端にガラス
    private static void windows(TideKit kit) {
        Area f = FLATS;
        for (int[] w : TideIslandBuilder.FLATS_WINDOWS) {
            int z = f.z1() - 1;
            while (z > f.z1() - 16 && !windowOpen(kit, w[0], z)) {
                for (int dx = -1; dx <= 1; dx++) {
                    kit.set(w[0] + dx, FLATS_FLOOR + 2, z, kit.wall(w[0] + dx, FLATS_FLOOR + 2, z));
                    kit.set(w[0] + dx, FLATS_FLOOR + 3, z, TideKit.AIR);
                    kit.set(w[0] + dx, FLATS_FLOOR + 4, z, TideKit.AIR);
                    kit.set(w[0] + dx, FLATS_FLOOR + 5, z, TideZone.HATCH.dark);
                }
                z--;
            }
            for (int dx = -1; dx <= 1; dx++) {
                kit.set(w[0] + dx, FLATS_FLOOR + 3, z + 1, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                kit.set(w[0] + dx, FLATS_FLOOR + 4, z + 1, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            }
        }
    }

    // 窓の穴が外 (甲羅の外の空気) に抜けたか: 穴の高さで、真ん中の 1 列の先が空気
    private static boolean windowOpen(TideKit kit, int x, int z) {
        return kit.get(x, FLATS_FLOOR + 3, z).isAir() && shellTop(x, z) < FLATS_FLOOR + 3;
    }

    private static BlockState floorBlock(int x, int z) {
        double n = TideIslandBuilder.noise(x, z, 5, 9) * 0.75 + TideIslandBuilder.noise(x, z, 2, 10) * 0.25;
        return n < 0.55 ? TideKit.SAND : n < 0.72 ? Blocks.MUD.defaultBlockState() : n < 0.85 ? Blocks.CLAY.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }
}
