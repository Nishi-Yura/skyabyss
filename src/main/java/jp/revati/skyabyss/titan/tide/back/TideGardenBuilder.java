package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 珊瑚の庭 (西の試練の部屋) を建てる (26 の 9 章)。甲羅の中の洞に、幅 5 の水路 (高さ 9 の礁の壁で囲む) と 6 つの台・祭壇・大貝。
// 場所ごとに色が違う (biome): 入口の「脳珊瑚の林」(桃・赤紫)、南の「昆布のうろ」(青緑・紫)、中ほどの「日向の礁」(橙・黄・白)、北の「真珠の祠」(白・桃・方解石)。
// 水路のふさぎ方・台・祭壇は道の形ごとに置き直す (carveGardenRoute)。壁の窪み (洞穴の小部屋) は道の形によらず置く (grottos)。
final class TideGardenBuilder {
    private TideGardenBuilder() {
    }

    enum Biome {
        GROVE, HOLLOW, SUNLIT, SHRINE
    }

    static Biome biome(int x, int z) {
        if (z <= -40) {
            return Biome.SHRINE;
        }
        if (z >= 26) {
            return Biome.HOLLOW;
        }
        return x >= -63 ? Biome.GROVE : Biome.SUNLIT;
    }

    // 礁の色 (5 つ)・壁の中の石 (中・石組み・苔・白) を場所ごとに
    private static final Block[][] REEF = {
            {Blocks.PINK_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.ORANGE_TERRACOTTA},
            {Blocks.CYAN_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.BLUE_TERRACOTTA},
            {Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.YELLOW_TERRACOTTA},
            {Blocks.WHITE_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA}};
    private static final Block[] MID = {Blocks.TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA};
    private static final Block[] STONE = {Blocks.TUFF_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SMOOTH_SANDSTONE, Blocks.POLISHED_DIORITE};
    private static final Block[] MOSS = {Blocks.MOSSY_STONE_BRICKS, Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS, Blocks.CALCITE};

    static BlockState reef(Biome b, int i) {
        return REEF[b.ordinal()][Math.floorMod(i, 5)].defaultBlockState();
    }

    static BlockState wall(int x, int y, int z) {
        Biome b = biome(x, z);
        int h = Math.floorMod(x * 5 + y * 3 + z * 11 + (y / 3) * 7, 17);
        if (h < 6) {
            return reef(b, h + y / 2);
        }
        if (h < 9) {
            return MID[b.ordinal()].defaultBlockState();
        }
        if (h < 12) {
            return STONE[b.ordinal()].defaultBlockState();
        }
        if (h < 14) {
            return MOSS[b.ordinal()].defaultBlockState();
        }
        return Blocks.WHITE_TERRACOTTA.defaultBlockState();
    }

    // 壁のいちばん上 (苔と珊瑚)
    static BlockState cap(int x, int z) {
        Biome b = biome(x, z);
        double n = TideIslandBuilder.noise(x, z, 4, 6);
        return n < 0.45 ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.7 ? reef(b, (int) (n * 13)) : MOSS[b.ordinal()].defaultBlockState();
    }

    // 庭の天井の石: 真珠層と珊瑚、ところどころ灯り (海のランタンと、珊瑚の色に合う菌の灯り)
    static BlockState roof(int x, int z) {
        int h = Math.floorMod(x * 7 + z * 11, 17);
        if (h == 0) {
            return TideKit.LAMP;
        }
        if (h == 9) {
            return Blocks.SHROOMLIGHT.defaultBlockState();
        }
        double n = TideIslandBuilder.noise(x, z, 4, 13);
        return n < 0.5 ? TideKit.NACRE : reef(biome(x, z), (int) (n * 11));
    }

    static int bump(int x, int z) {
        return (int) Math.floor(TideIslandBuilder.noise(x, z, 5, 5) * 2.99);
    }

    // 庭の天井 (その柱の、いちばん上の空気の y)。甲羅の地表から 4 段下で、高くても GARDEN_CEILING + 2
    static int ceiling(int x, int z) {
        int top = shellTop(x, z);
        if (top == Integer.MIN_VALUE) {
            return GARDEN_FLOOR;
        }
        return Math.min(top - 6, GARDEN_CEILING + (int) Math.floor(TideIslandBuilder.noise(x, z, 7, 11) * 2.99));
    }

    // ---------------------------------------------------------------- 洞
    static void build(TideKit kit) {
        Area g = GARDEN;
        kit.zone(TideZone.SHELL);
        for (int x = g.x1(); x <= g.x2(); x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                int bump = bump(x, z);
                int ceil = ceiling(x, z);
                boolean edge = x == g.x1() || x == g.x2() || z == g.z1() || z == g.z2() || ceil < GARDEN_WALL_TOP + 8;
                Biome b = biome(x, z);
                for (int y = 58; y <= Math.max(ceil, GARDEN_WALL_TOP) + 1; y++) {
                    BlockState s;
                    if (edge) {
                        s = wall(x, y, z);
                    } else if (y <= GARDEN_WALL_TOP + bump) {
                        s = y > GARDEN_WALL_TOP - 1 + bump ? (Math.floorMod(x * 5 + z * 3, 23) == 0 ? TideKit.LAMP : cap(x, z)) : wall(x, y, z);
                    } else if (y <= ceil) {
                        s = TideKit.AIR;
                    } else {
                        s = roof(x, z);
                    }
                    kit.set(x, y, z, s);
                }
                if (edge) {
                    continue;
                }
                double sp = TideIslandBuilder.noise(x, z, 2.2, 7);
                if (sp > 0.88) {
                    // 珊瑚の塔 (背の高い房。上ほど細く、ときどき灯り)
                    int hgt = 2 + (int) ((sp - 0.88) * 90);
                    hgt = Math.min(hgt, ceil - (GARDEN_WALL_TOP + bump) - 3);
                    for (int y = 1; y <= hgt; y++) {
                        kit.set(x, GARDEN_WALL_TOP + bump + y, z, y == hgt && hgt >= 6 && (x + z) % 3 == 0 ? TideKit.LAMP : reef(b, x + z + y / 3));
                    }
                } else if (sp > 0.74) {
                    kit.set(x, GARDEN_WALL_TOP + bump + 1, z, TideIslandBuilder.deadFan(x * 3 + z));
                }
                // 天井から垂れた珊瑚 (壁の上より 4 段以上の高さだけ)
                double st = TideIslandBuilder.noise(x, z, 3.1, 12);
                if (st > 0.8) {
                    int len = 1 + (int) ((st - 0.8) * 40);
                    for (int i = 0; i < len && ceil - i > GARDEN_WALL_TOP + bump + 6; i++) {
                        kit.set(x, ceil - i, z, reef(b, x * 2 + z + i));
                    }
                }
            }
        }
        // 大貝の淵 (半径 5 の窪み。床 64)。大貝そのものは道の形と一緒に置く
        for (int x = CLAM[0] - 6; x <= CLAM[0] + 6; x++) {
            for (int z = CLAM[1] - 6; z <= CLAM[1] + 6; z++) {
                if (Math.hypot(x - CLAM[0], z - CLAM[1]) <= 5.3) {
                    kit.set(x, GARDEN_FLOOR, z, TideKit.SAND);
                    for (int y = GARDEN_FLOOR + 1; y <= GARDEN_WALL_TOP + 8; y++) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        carveRoute(kit, 0, true);
    }

    // ---------------------------------------------------------------- 水路
    // 水路の L 字 (幅 5): 始めは z の向き、次に x の向き
    static List<BlockPos> channelCells(Channel c) {
        List<BlockPos> list = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        int fx = c.from[0];
        int fz = c.from[1];
        int tx = c.to[0];
        int tz = c.to[1];
        for (int z = Math.min(fz, tz); z <= Math.max(fz, tz); z++) {
            for (int d = -CHANNEL_HALF; d <= CHANNEL_HALF; d++) {
                add(list, seen, fx + d, z);
            }
        }
        for (int x = Math.min(fx, tx); x <= Math.max(fx, tx); x++) {
            for (int d = -CHANNEL_HALF; d <= CHANNEL_HALF; d++) {
                add(list, seen, x, tz + d);
            }
        }
        return list;
    }

    private static void add(List<BlockPos> list, Set<Long> seen, int x, int z) {
        if (seen.add(((long) x << 32) ^ (z & 0xffffffffL))) {
            list.add(new BlockPos(x, 0, z));
        }
    }

    // 庭の道の形を置く: すべての水路を壁でふさいでから、選んだ形の水路と、いつも開いている水路 (大貝〜A、g0〜C) を掘る。
    // 台・祭壇・入口の段は最後に置き直す (水路が台を通るため)
    static void carveRoute(TideKit kit, int route, boolean full) {
        kit.zone(TideZone.SHELL);
        for (Channel c : Channel.values()) {
            for (BlockPos p : channelCells(c)) {
                int bump = bump(p.getX(), p.getZ());
                for (int y = GARDEN_FLOOR; y <= GARDEN_WALL_TOP + bump; y++) {
                    kit.set(p.getX(), y, p.getZ(), y > GARDEN_WALL_TOP - 1 + bump ? cap(p.getX(), p.getZ()) : wall(p.getX(), y, p.getZ()));
                }
            }
        }
        List<Channel> open = new ArrayList<>(List.of(GARDEN_ROUTES[Math.floorMod(route, GARDEN_ROUTES.length)]));
        open.add(Channel.CLAM_A);
        open.add(Channel.G0_C);
        for (Channel c : open) {
            List<BlockPos> cells = channelCells(c);
            for (BlockPos p : cells) {
                kit.set(p.getX(), GARDEN_FLOOR, p.getZ(), TideIslandBuilder.noise(p.getX(), p.getZ(), 3, 8) < 0.25 ? Blocks.GRAVEL.defaultBlockState() : TideKit.SAND);
                for (int y = GARDEN_FLOOR + 1; y <= GARDEN_WALL_TOP + 8; y++) {
                    kit.set(p.getX(), y, p.getZ(), TideKit.AIR);
                }
            }
            lampsAlong(kit, c);
        }
        // 台 (15x15 の段。上の 7x7 が床 68)
        for (int[] c : new int[][] {PLAT_A, PLAT_B1, PLAT_B2, PLAT_C, PLAT_D1, PLAT_D2}) {
            ziggurat(kit, c[0], c[1], GARDEN_FLOOR, PLATFORM_TOP, PLAT_CORE, open, c == PLAT_C);
        }
        // 祭壇の台 (床 69。上は 9x9)
        ziggurat(kit, ALTAR[0], ALTAR[1], GARDEN_FLOOR, ALTAR_TOP, PLAT_CORE + 1, open, false);
        // g1 の焚き火と、台の灯りを置き直す (台を建て直したため)
        Rest g1 = RESTS.get(2);
        kit.campfire(g1.campfire());
        TideCaches.place(kit, "fin_w_gate");
        grottos(kit);
        if (!full) {
            return;
        }
        // 入口の段 g0 (床 68) と、水路へ下りる階段 (西へ)
        for (int x = G0.x1(); x <= G0.x2(); x++) {
            for (int z = G0.z1(); z <= G0.z2(); z++) {
                for (int y = GARDEN_FLOOR; y <= PLATFORM_TOP; y++) {
                    kit.set(x, y, z, y == PLATFORM_TOP ? TideKit.FLOOR : TideKit.SANDSTONE);
                }
                for (int y = PLATFORM_TOP + 1; y <= GARDEN_WALL_TOP + 8; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 一段ごとに 1 下りる。階段 (登る向きは東) の背の高い側が、上の床の上面にそろう高さに置く: 1 段目は g0 の床と同じ高さ (PLATFORM_TOP)
        for (int i = 0; i < 4; i++) {
            int x = G0.x1() - 1 - i;
            for (int z = -CHANNEL_HALF; z <= CHANNEL_HALF; z++) {
                for (int y = GARDEN_FLOOR; y < PLATFORM_TOP - i; y++) {
                    kit.set(x, y, z, TideKit.SANDSTONE);
                }
                kit.stair(x, PLATFORM_TOP - i, z, Direction.EAST, Blocks.SANDSTONE_STAIRS.defaultBlockState());
                for (int y = PLATFORM_TOP - i + 1; y <= GARDEN_WALL_TOP + 3; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        clam(kit);
    }

    // 水路の壁に埋めた灯り (9 マスおき、左右互い違い。水路の底まで光が届く)
    private static void lampsAlong(TideKit kit, Channel c) {
        List<BlockPos> cells = channelCells(c);
        int n = 0;
        for (BlockPos p : cells) {
            // 水路の中心線の上だけ (幅 5 の真ん中の列)。向きは L 字の 2 本の腕のどちらか
            boolean zRun = Math.abs(p.getX() - c.from[0]) == 0 && p.getZ() >= Math.min(c.from[1], c.to[1]) && p.getZ() <= Math.max(c.from[1], c.to[1]);
            boolean xRun = p.getZ() == c.to[1];
            if (!zRun && !xRun) {
                continue;
            }
            if (++n % 9 != 0) {
                continue;
            }
            int side = (n / 9) % 2 == 0 ? 1 : -1;
            int lx = p.getX() + (zRun && !xRun ? side * (CHANNEL_HALF + 1) : 0);
            int lz = p.getZ() + (xRun ? side * (CHANNEL_HALF + 1) : 0);
            if (!kit.get(lx, GARDEN_FLOOR + 3, lz).isAir()) {
                kit.set(lx, GARDEN_FLOOR + 3, lz, TideKit.LAMP);
            }
        }
    }

    // 段の台: 外周から内へ一段ずつ上がる。上の床は top (中心の (2*core+1) 四方)
    private static void ziggurat(TideKit kit, int cx, int cz, int floor, int top, int coreR, List<Channel> open, boolean rest) {
        int steps = top - floor;
        BlockState topStone = TideKit.FLOOR;
        for (int dx = -coreR - steps; dx <= coreR + steps; dx++) {
            for (int dz = -coreR - steps; dz <= coreR + steps; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                int h = ring <= coreR ? top : top - (ring - coreR);
                if (h <= floor) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                for (int y = floor; y <= h; y++) {
                    BlockState s;
                    if (y != h) {
                        s = TideKit.SANDSTONE;
                    } else if (ring <= coreR) {
                        // 台の上: 縁は暗い石の枠、中は床石に市松の灯り
                        s = ring == coreR ? Blocks.CUT_SANDSTONE.defaultBlockState() : ((dx + dz) % 4 == 0 && ring <= coreR - 2 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : topStone);
                    } else {
                        s = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    }
                    kit.set(x, y, z, s);
                }
                for (int y = h + 1; y <= GARDEN_WALL_TOP + 8; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 台の四隅に灯りの柱 (潮除けの台の目印)
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int px = cx + sx * coreR;
                int pz = cz + sz * coreR;
                kit.set(px, top + 1, pz, Blocks.PRISMARINE_WALL.defaultBlockState());
                kit.set(px, top + 2, pz, TideKit.LAMP);
            }
        }
    }

    // 大貝 (淵の真ん中の台。真珠は試練の側が置く): 5x5 の白い殻に、桃色の縁
    private static void clam(TideKit kit) {
        int cx = CLAM[0];
        int cz = CLAM[1];
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                kit.set(cx + dx, GARDEN_FLOOR + 1, cz + dz, edge ? Blocks.PINK_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState());
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            kit.set(cx + dx, GARDEN_FLOOR + 2, cz + 1, Blocks.PINK_TERRACOTTA.defaultBlockState());
            kit.set(cx + dx, GARDEN_FLOOR + 3, cz + 1, dx == 0 ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.PINK_TERRACOTTA.defaultBlockState());
        }
        kit.set(cx - 1, GARDEN_FLOOR + 2, cz, Blocks.PINK_TERRACOTTA.defaultBlockState());
        kit.set(cx + 1, GARDEN_FLOOR + 2, cz, Blocks.PINK_TERRACOTTA.defaultBlockState());
        kit.set(cx - 1, GARDEN_FLOOR + 2, cz - 1, Blocks.PINK_TERRACOTTA.defaultBlockState());
        kit.set(cx + 1, GARDEN_FLOOR + 2, cz - 1, Blocks.PINK_TERRACOTTA.defaultBlockState());
        // 淵のまわりの灯り (四方の柱)
        for (int[] d : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            kit.set(cx + d[0], GARDEN_FLOOR + 1, cz + d[1], Blocks.PRISMARINE_WALL.defaultBlockState());
            kit.set(cx + d[0], GARDEN_FLOOR + 2, cz + d[1], TideKit.LAMP);
        }
    }

    // ---------------------------------------------------------------- 壁の窪み (洞穴の小部屋。行き止まりには碑と宝)
    // 道の形によらず建てる (carveRoute のたびに置き直す。水路の壁をふさぐ処理が窪みに触れないよう、窪みは水路の外にある)
    record Grotto(String id, int x1, int x2, int z1, int z2, int openX1, int openX2, int openZ1, int openZ2, Direction face, BlockPos tablet, BlockPos cache) {
    }

    static final List<Grotto> GROTTOS = List.of(
            // 漁り人の窪み: 西の入口の水路 (g0〜C) の南の壁に口。碑と宝箱
            new Grotto("garden_a", -60, -50, -1, 8, -56, -54, -3, -3, Direction.NORTH, new BlockPos(-55, 67, 8), new BlockPos(-52, 65, 6)),
            // 貝守の窪み: 大貝の水路の西の壁に口
            new Grotto("garden_b", -97, -88, 46, 56, -86, -86, 50, 52, Direction.EAST, new BlockPos(-97, 67, 51), new BlockPos(-95, 65, 54)));

    static void grottos(TideKit kit) {
        for (Grotto g : GROTTOS) {
            kit.zone(biome(g.x1(), g.z1()) == Biome.HOLLOW ? TideZone.SHELL : TideZone.BEACH);
            int floor = GARDEN_FLOOR;
            int top = floor + 6;
            for (int x = g.x1() - 1; x <= g.x2() + 1; x++) {
                for (int z = g.z1() - 1; z <= g.z2() + 1; z++) {
                    boolean in = x >= g.x1() && x <= g.x2() && z >= g.z1() && z <= g.z2();
                    for (int y = floor; y <= top + 1; y++) {
                        BlockState s;
                        if (!in) {
                            s = y <= top ? wall(x, y, z) : kit.wall(x, y, z);
                        } else if (y == floor) {
                            s = (x + z) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : TideKit.SAND;
                        } else if (y == top + 1) {
                            s = (x + z) % 5 == 0 ? TideKit.LAMP : TideKit.NACRE;
                        } else {
                            s = TideKit.AIR;
                        }
                        kit.set(x, y, z, s);
                    }
                }
            }
            // 口 (壁 1 枚を抜く。高さ 4)
            for (int x = g.openX1(); x <= g.openX2(); x++) {
                for (int z = g.openZ1(); z <= g.openZ2(); z++) {
                    for (int y = floor; y <= floor + 4; y++) {
                        kit.set(x, y, z, y == floor ? TideKit.SAND : TideKit.AIR);
                    }
                }
            }
            // 碑と宝箱
            TideCaches.place(kit, g.id());
            // 窪みのまん中の灯りの柱と、枯れた珊瑚
            int mx = (g.x1() + g.x2()) / 2;
            int mz = (g.z1() + g.z2()) / 2;
            kit.set(mx, floor + 1, mz, Blocks.PRISMARINE_WALL.defaultBlockState());
            kit.set(mx, floor + 2, mz, TideKit.LAMP);
            for (int[] d : new int[][] {{g.x1(), g.z1()}, {g.x2(), g.z2()}, {g.x1(), g.z2()}, {g.x2(), g.z1()}}) {
                kit.set(d[0], floor + 1, d[1], TideIslandBuilder.deadFan(d[0] * 5 + d[1]));
            }
        }
    }
}
