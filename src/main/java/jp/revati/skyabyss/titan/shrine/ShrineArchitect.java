package jp.revati.skyabyss.titan.shrine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;

import jp.revati.skyabyss.progress.Titans;

// 巨神の祠の建物 (地上。TitanShrine.build から呼ぶ)。台座 9×9 と輪・碑・炉は TitanShrine が作る。ここはそのまわり:
//   段のある台地 (23×23)・柱廊の拝殿 (二重の屋根と明かり取りの胴)・四隅の四阿・南の鳥居・参道 (階段は一段ずつ・街灯)・巨神ごとの飾り。
//   座標は祠の中心 (碑) からの相対。y=0 は台座の床の高さ (立つのは y=1)。参道は南 (+z) へ、拝殿の入口も南
//   屋根は階段の輪を内へ一段ずつ上げる寄棟 (角は建てたあとで StairBlock.updateShape に任せて内角・外角を正しくする)
//   光: 立てる床のどこにも光が届くようにする (湧き止め)。足りない所は見えない光のブロックを頭の上に足す
final class ShrineArchitect {
    private ShrineArchitect() {
    }

    static final int TERRACE = 11;
    static final int CLEAR = 12;
    static final int CLEAR_TOP = 26;
    static final int PATH_START = 12;
    static final int PATH_MIN_END = 31;
    static final int PATH_MAX_END = 40;
    static final int FLAT_UNTIL = 15;

    // ---------------------------------------------------------------- 材質
    record Theme(BlockState floorA, BlockState floorB, BlockState trim, BlockState panel, BlockState pillar, BlockState pillarBase, BlockState cap,
            BlockState beam, BlockState roofFill, Block roofStair, BlockState upFill, Block upStair, BlockState drumWall, BlockState drumWindow,
            Block wall, BlockState slab, BlockState slab2, Block pathStair, BlockState hallLamp, BlockState postTop, BlockState finial, BlockState finialTop,
            BlockState foundation) {
    }

    private static BlockState d(Block b) {
        return b.defaultBlockState();
    }

    private static BlockState axisY(Block b) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    }

    private static BlockState lantern(Block b, boolean hanging) {
        return b.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    static Theme theme(String titan) {
        BlockState deepBricks = d(Blocks.DEEPSLATE_BRICKS);
        return switch (titan) {
            case Titans.TIDE_SHELL -> new Theme(d(Blocks.PRISMARINE_BRICKS), d(Blocks.DARK_PRISMARINE), d(Blocks.PRISMARINE), d(Blocks.DARK_PRISMARINE),
                    d(Blocks.PRISMARINE_BRICKS), d(Blocks.DARK_PRISMARINE), d(Blocks.SEA_LANTERN), d(Blocks.DARK_PRISMARINE), d(Blocks.DARK_PRISMARINE),
                    Blocks.DARK_PRISMARINE_STAIRS, d(Blocks.PRISMARINE_BRICKS), Blocks.PRISMARINE_BRICK_STAIRS, d(Blocks.PRISMARINE_BRICKS), d(Blocks.SEA_LANTERN),
                    Blocks.PRISMARINE_WALL, d(Blocks.PRISMARINE_BRICK_SLAB), d(Blocks.DARK_PRISMARINE_SLAB), Blocks.PRISMARINE_BRICK_STAIRS,
                    lantern(Blocks.SOUL_LANTERN, true), d(Blocks.SEA_LANTERN), d(Blocks.PRISMARINE_WALL), d(Blocks.SEA_LANTERN), deepBricks);
            case Titans.THUNDER_WING -> new Theme(d(Blocks.WAXED_WEATHERED_CUT_COPPER), d(Blocks.WAXED_OXIDIZED_CUT_COPPER), d(Blocks.WAXED_COPPER_BLOCK),
                    d(Blocks.WAXED_CHISELED_COPPER), d(Blocks.WAXED_OXIDIZED_CUT_COPPER), d(Blocks.WAXED_COPPER_BLOCK), d(Blocks.WAXED_CHISELED_COPPER),
                    d(Blocks.WAXED_EXPOSED_CUT_COPPER), d(Blocks.WAXED_OXIDIZED_CUT_COPPER), Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS,
                    d(Blocks.WAXED_WEATHERED_CUT_COPPER), Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, d(Blocks.WAXED_CUT_COPPER),
                    d(Blocks.WAXED_COPPER_BULB).setValue(CopperBulbBlock.LIT, true), Blocks.TUFF_BRICK_WALL, d(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB),
                    d(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB), Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, lantern(Blocks.LANTERN, true),
                    d(Blocks.WAXED_COPPER_BULB).setValue(CopperBulbBlock.LIT, true), d(Blocks.TUFF_BRICK_WALL), d(Blocks.LIGHTNING_ROD), deepBricks);
            case Titans.EMBER -> new Theme(d(Blocks.POLISHED_BLACKSTONE_BRICKS), d(Blocks.BLACKSTONE), d(Blocks.GILDED_BLACKSTONE),
                    d(Blocks.CHISELED_POLISHED_BLACKSTONE), axisY(Blocks.POLISHED_BASALT), d(Blocks.POLISHED_BLACKSTONE), d(Blocks.CHISELED_POLISHED_BLACKSTONE),
                    d(Blocks.POLISHED_BLACKSTONE_BRICKS), d(Blocks.POLISHED_BLACKSTONE_BRICKS), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
                    d(Blocks.RED_NETHER_BRICKS), Blocks.RED_NETHER_BRICK_STAIRS, d(Blocks.POLISHED_BLACKSTONE_BRICKS), d(Blocks.SHROOMLIGHT),
                    Blocks.POLISHED_BLACKSTONE_BRICK_WALL, d(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB), d(Blocks.RED_NETHER_BRICK_SLAB),
                    Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, lantern(Blocks.LANTERN, true), d(Blocks.LAVA_CAULDRON), d(Blocks.RED_NETHER_BRICK_WALL),
                    d(Blocks.LAVA_CAULDRON), d(Blocks.BLACKSTONE));
            default -> new Theme(d(Blocks.POLISHED_DIORITE), d(Blocks.CALCITE), d(Blocks.QUARTZ_BRICKS), d(Blocks.CHISELED_QUARTZ_BLOCK),
                    axisY(Blocks.BONE_BLOCK), d(Blocks.QUARTZ_BRICKS), d(Blocks.CHISELED_QUARTZ_BLOCK), d(Blocks.DARK_OAK_PLANKS),
                    d(Blocks.DEEPSLATE_TILES), Blocks.DEEPSLATE_TILE_STAIRS, d(Blocks.DEEPSLATE_BRICKS), Blocks.DEEPSLATE_BRICK_STAIRS,
                    d(Blocks.QUARTZ_BRICKS), d(Blocks.GLASS_PANE), Blocks.DEEPSLATE_TILE_WALL, d(Blocks.DEEPSLATE_TILE_SLAB), d(Blocks.POLISHED_DIORITE_SLAB),
                    Blocks.POLISHED_DIORITE_STAIRS, lantern(Blocks.SOUL_LANTERN, true), lantern(Blocks.LANTERN, false), d(Blocks.DEEPSLATE_TILE_WALL),
                    lantern(Blocks.SOUL_LANTERN, false), deepBricks);
        };
    }

    // ---------------------------------------------------------------- 参道の高さ
    // h[z - PATH_START] = z の床の高さ (y=0 が台座の床)。最初の数マスは平ら (鳥居のため)。あとは地面へ一段ずつ
    record Plan(int[] h, int end) {
        int at(int z) {
            return h[Math.min(z, end) - PATH_START];
        }
    }

    static Plan plan(ServerLevel level, BlockPos center) {
        int top = center.getY() - 1;
        int[] h = new int[PATH_MAX_END - PATH_START + 1];
        int cur = 0;
        int end = PATH_MAX_END;
        for (int z = PATH_START; z <= PATH_MAX_END; z++) {
            int want = cur;
            if (z > FLAT_UNTIL) {
                int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + 7, center.getZ() + z) - 1 - top;
                want = Math.max(cur - 1, Math.min(cur + 1, g));
            }
            cur = want;
            h[z - PATH_START] = cur;
            if (z >= PATH_MIN_END) {
                int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + 7, center.getZ() + z) - 1 - top;
                if (g == cur) {
                    end = z;
                    break;
                }
            }
        }
        return new Plan(h, end);
    }

    // ---------------------------------------------------------------- 整地 (台座の前に)
    static void prepare(ServerLevel level, BlockPos center, Plan plan) {
        int cx = center.getX();
        int cz = center.getZ();
        int top = center.getY() - 1;
        for (int x = cx - CLEAR - 4; x <= cx + CLEAR + 4; x += 16) {
            for (int z = cz - CLEAR; z <= cz + plan.end + 16; z += 16) {
                level.getChunk(x >> 4, z >> 4);
            }
        }
        level.getChunk((cx + CLEAR + 4) >> 4, (cz + plan.end + 2) >> 4);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -CLEAR; dx <= CLEAR; dx++) {
            for (int dz = -CLEAR; dz <= CLEAR; dz++) {
                clearColumn(level, cx + dx, cz + dz, top + 1, top + CLEAR_TOP, air);
                if (Math.abs(dx) <= TERRACE && Math.abs(dz) <= TERRACE) {
                    foundation(level, cx + dx, cz + dz, top - 1, d(Blocks.DEEPSLATE_BRICKS));
                }
            }
        }
        for (int z = PATH_START; z <= plan.end; z++) {
            int h = plan.at(z);
            for (int dx = -6; dx <= 6; dx++) {
                clearColumn(level, cx + dx, cz + z, top + h + 1, top + h + 11, air);
                if (Math.abs(dx) <= 6) {
                    foundation(level, cx + dx, cz + z, top + h - 1, d(Blocks.DEEPSLATE_BRICKS));
                }
            }
        }
    }

    private static void clearColumn(ServerLevel level, int x, int z, int y0, int y1, BlockState air) {
        for (int y = y0; y <= y1; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).isAir()) {
                level.setBlock(p, air, 2);
            }
        }
    }

    private static void foundation(ServerLevel level, int x, int z, int yTop, BlockState block) {
        for (int y = yTop; y > yTop - 18; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) {
                break;
            }
            level.setBlock(p, block, 2);
        }
    }

    // ---------------------------------------------------------------- 設計図
    static final class Blueprint {
        final BlockPos origin; // 碑の足もと (y は台座の床の高さ top)
        final Theme t;
        final Map<BlockPos, BlockState> map = new HashMap<>();
        // 光るもの (相対 x, y, z と明るさ)
        final List<int[]> lights = new ArrayList<>();
        // 立てる床 (相対 x, 床の y, z)
        final List<int[]> floors = new ArrayList<>();

        Blueprint(BlockPos origin, Theme t) {
            this.origin = origin;
            this.t = t;
        }

        void set(int x, int y, int z, BlockState s) {
            map.put(origin.offset(x, y, z), s);
            int e = s.getLightEmission();
            if (e > 0) {
                lights.add(new int[] {x, y, z, e});
            }
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, s);
                    }
                }
            }
        }

        void stair(int x, int y, int z, Block b, Direction facing, boolean upsideDown) {
            set(x, y, z, b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM));
        }

        boolean has(int x, int y, int z) {
            return map.containsKey(origin.offset(x, y, z));
        }

        // 寄棟の屋根。中心 (cx,cz)・半幅 half・足もと y0。輪 inset 0..stairsTo は階段 (中心へ上る)、その内側は stairsTo 以下の高さまで詰める (solid) か空ける
        void hip(int cx, int cz, int half, int y0, Block stair, BlockState fill, int stairsTo, boolean solidInside) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    int m = Math.max(Math.abs(dx), Math.abs(dz));
                    int inset = half - m;
                    int x = cx + dx;
                    int z = cz + dz;
                    if (inset <= stairsTo) {
                        if (inset > 0) {
                            fill(x, y0, z, x, y0 + inset - 1, z, fill);
                        }
                        Direction f = Math.abs(dz) >= Math.abs(dx) ? (dz > 0 ? Direction.NORTH : Direction.SOUTH) : (dx > 0 ? Direction.WEST : Direction.EAST);
                        if (inset == half) {
                            set(x, y0 + inset, z, fill);
                        } else {
                            stair(x, y0 + inset, z, stair, f, false);
                        }
                    } else if (solidInside) {
                        fill(x, y0, z, x, y0 + inset, z, fill);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 建てる
    // 台座の床・碑・炉を置いたあとに呼ぶ。建てたブロック数を返す
    static int finish(ServerLevel level, BlockPos center, String titan, Plan plan) {
        int top = center.getY() - 1;
        Theme t = theme(titan);
        Blueprint b = new Blueprint(new BlockPos(center.getX(), top, center.getZ()), t);
        terrace(b);
        hall(b);
        pylons(b, titan);
        torii(b);
        path(b, plan, titan);
        motif(b, plan, titan);
        lighting(b, plan);
        return apply(level, b);
    }

    private static boolean inPedestal(int x, int y, int z) {
        return Math.abs(x) <= TitanShrine.PEDESTAL && Math.abs(z) <= TitanShrine.PEDESTAL && y <= 6;
    }

    private static void terrace(Blueprint b) {
        Theme t = b.t;
        for (int x = -TERRACE; x <= TERRACE; x++) {
            for (int z = -TERRACE; z <= TERRACE; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                if (m <= TitanShrine.PEDESTAL) {
                    continue;
                }
                BlockState s;
                if (m >= 10 || m == 7) {
                    s = t.trim();
                } else if (z > 0 && Math.abs(x) <= 1) {
                    s = t.floorA();
                } else if (m <= 6) {
                    s = t.floorA();
                } else {
                    s = ((x + z) & 1) == 0 ? t.floorA() : t.floorB();
                }
                if (m == 5 || m == 6) {
                    s = ((x + z) & 1) == 0 ? t.floorB() : t.floorA();
                }
                if (z > 0 && Math.abs(x) <= 1 && m != 7 && m < 10) {
                    s = t.floorA();
                }
                b.set(x, 0, z, s);
                b.set(x, -1, z, m == TERRACE ? t.trim() : t.foundation());
                b.floors.add(new int[] {x, 0, z});
            }
        }
        // 縁の低い壁 (南の口は空ける)・縁の灯り
        for (int i = -TERRACE; i <= TERRACE; i++) {
            for (int[] e : new int[][] {{i, -TERRACE}, {i, TERRACE}, {-TERRACE, i}, {TERRACE, i}}) {
                int x = e[0];
                int z = e[1];
                if (Math.abs(x) >= 9 && Math.abs(z) >= 9) {
                    continue;
                }
                if (z == TERRACE && Math.abs(x) <= 3) {
                    continue;
                }
                b.set(x, 1, z, d(t.wall()));
                if (Math.floorMod(i, 4) == 0) {
                    b.set(x, 2, z, d(Blocks.LANTERN));
                }
            }
        }
    }

    private static void hall(Blueprint b) {
        Theme t = b.t;
        // 柱廊: 角と一つおきの柱、間は低い羽目板 (南の入口は 5 マス空ける)
        for (int side = -5; side <= 5; side++) {
            for (int[] e : new int[][] {{side, -5}, {side, 5}, {-5, side}, {5, side}}) {
                int x = e[0];
                int z = e[1];
                boolean entrance = z == 5 && Math.abs(x) <= 2;
                boolean pillar = (side & 1) != 0;
                if (!entrance) {
                    if (pillar) {
                        b.set(x, 1, z, t.pillarBase());
                        b.fill(x, 2, z, x, 4, z, t.pillar());
                        b.set(x, 5, z, t.cap());
                    } else {
                        b.fill(x, 1, z, x, 2, z, t.panel());
                    }
                }
                b.set(x, 6, z, t.beam());
            }
        }
        // 下の屋根 (±7、三段) と、その内側の台 (±4) から明かり取りの胴、上の屋根
        b.hip(0, 0, 7, 7, t.roofStair(), t.roofFill(), 2, false);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == 4) {
                    b.fill(x, 7, z, x, 9, z, t.roofFill());
                    int along = Math.abs(x) == 4 ? z : x;
                    boolean window = Math.abs(along) <= 2 && (along & 1) == 0;
                    boolean corner = Math.abs(x) == 4 && Math.abs(z) == 4;
                    b.set(x, 10, z, t.drumWall());
                    b.set(x, 13, z, t.drumWall());
                    for (int y = 11; y <= 12; y++) {
                        b.set(x, y, z, window && !corner ? t.drumWindow() : t.drumWall());
                    }
                }
            }
        }
        b.hip(0, 0, 6, 14, t.upStair(), t.upFill(), 5, true);
        // 頂の飾り
        b.fill(0, 21, 0, 0, 22, 0, t.finial());
        b.set(0, 23, 0, t.finialTop());
        // 吊り灯 (天井の高い広間は鎖で)・軒下の灯り
        for (int sx : new int[] {-2, 2}) {
            for (int sz : new int[] {-2, 2}) {
                b.fill(sx, 9, sz, sx, 13, sz, Blocks.CHAIN.defaultBlockState());
                b.set(sx, 8, sz, t.hallLamp());
            }
        }
        for (int c = -6; c <= 6; c += 3) {
            for (int[] e : new int[][] {{c, -6}, {c, 6}, {-6, c}, {6, c}}) {
                b.set(e[0], 6, e[1], lantern(Blocks.LANTERN, true));
            }
        }
        // 床 (拝殿のなか。台座の上は TitanShrine が作る)
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                b.floors.add(new int[] {x, 0, z});
            }
        }
    }

    private static void pylons(Blueprint b, String titan) {
        Theme t = b.t;
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                int cx = sx * 10;
                int cz = sz * 10;
                for (int px : new int[] {-1, 1}) {
                    for (int pz : new int[] {-1, 1}) {
                        int x = cx + px;
                        int z = cz + pz;
                        b.set(x, 1, z, t.pillarBase());
                        b.fill(x, 2, z, x, 5, z, t.pillar());
                        b.set(x, 6, z, t.cap());
                    }
                }
                b.hip(cx, cz, 2, 7, t.roofStair(), t.roofFill(), 1, true);
                b.set(cx, 10, cz, t.finialTop());
                b.set(cx, 6, cz, lantern(Blocks.LANTERN, true));
            }
        }
    }

    private static void torii(Blueprint b) {
        Theme t = b.t;
        int z = 14;
        for (int sx : new int[] {-3, 3}) {
            b.set(sx, 1, z, t.pillarBase());
            b.fill(sx, 2, z, sx, 6, z, t.pillar());
        }
        b.fill(-2, 5, z, 2, 5, z, t.beam());
        b.fill(-5, 7, z, 5, 7, z, t.beam());
        b.fill(-6, 8, z, 6, 8, z, t.slab());
        b.fill(-4, 9, z, 4, 9, z, t.slab2());
        b.set(-5, 6, z, t.pillarBase());
        b.set(5, 6, z, t.pillarBase());
        b.set(-2, 4, z, lantern(Blocks.LANTERN, true));
        b.set(2, 4, z, lantern(Blocks.LANTERN, true));
    }

    private static void path(Blueprint b, Plan plan, String titan) {
        Theme t = b.t;
        for (int z = PATH_START; z <= plan.end; z++) {
            int h = plan.at(z);
            for (int x = -2; x <= 2; x++) {
                BlockState floor = Math.abs(x) == 2 ? t.floorB() : t.floorA();
                b.set(x, h, z, floor);
                b.set(x, h - 1, z, t.foundation());
                b.floors.add(new int[] {x, h, z});
            }
            for (int sx : new int[] {-3, 3}) {
                b.set(sx, h, z, t.trim());
                b.set(sx, h - 1, z, t.foundation());
                b.floors.add(new int[] {sx, h, z});
            }
        }
        // 段: z-1 の床が z より高いとき、z-1 のマスの床を階段 (北向き) にする
        for (int z = PATH_START; z < plan.end; z++) {
            int h = plan.at(z);
            int hs = plan.at(z + 1);
            if (hs < h) {
                for (int x = -2; x <= 2; x++) {
                    b.stair(x, h, z, t.pathStair(), Direction.NORTH, false);
                }
            }
        }
        // 街灯 (平らなところだけ)
        for (int z = 16; z <= plan.end; z += 4) {
            int h = plan.at(z);
            if (plan.at(z - 1) != h || plan.at(z + 1) != h) {
                continue;
            }
            for (int sx : new int[] {-3, 3}) {
                b.set(sx, h + 1, z, d(t.wall()));
                b.set(sx, h + 2, z, d(t.wall()));
                b.set(sx, h + 3, z, t.postTop());
            }
        }
    }

    private static int rib(int y) {
        return (int) Math.round(4.0 * Math.sqrt(Math.max(0, 1 - (y - 1) * (y - 1) / 81.0)));
    }

    // 巨神ごとの飾り
    private static void motif(Blueprint b, Plan plan, String titan) {
        Theme t = b.t;
        switch (titan) {
            case Titans.TIDE_SHELL -> {
                // 台地の東西に浅い池 (底は海のランタン)
                for (int sx : new int[] {-1, 1}) {
                    for (int x = 8; x <= 10; x++) {
                        for (int z = -3; z <= 3; z++) {
                            int px = sx * x;
                            b.set(px, -1, z, d(Blocks.SEA_LANTERN));
                            b.set(px, 0, z, Blocks.WATER.defaultBlockState());
                            final int fz = z;
                            b.floors.removeIf(f -> f[0] == px && f[2] == fz);
                        }
                    }
                    for (int z = -3; z <= 3; z += 3) {
                        b.set(sx * 7, 1, z, d(Blocks.PRISMARINE_BRICK_SLAB));
                    }
                }
                // 参道の脇の海のランタンの柱
                for (int z = 18; z <= plan.end; z += 8) {
                    int h = plan.at(z);
                    for (int sx : new int[] {-5, 5}) {
                        b.set(sx, h, z, t.trim());
                        b.set(sx, h - 1, z, t.foundation());
                        b.fill(sx, h + 1, z, sx, h + 3, z, t.pillar());
                        b.set(sx, h + 4, z, d(Blocks.SEA_LANTERN));
                    }
                }
            }
            case Titans.THUNDER_WING -> {
                // 避雷針の塔 (台地の東西と北)・参道の脇にも
                int[][] masts = {{-8, 0}, {8, 0}, {0, -9}, {-5, 20}, {5, 20}, {-5, 28}, {5, 28}};
                for (int[] m : masts) {
                    int h = m[1] >= PATH_START ? plan.at(Math.min(m[1], plan.end)) : 0;
                    if (m[1] >= PATH_START && m[1] > plan.end) {
                        continue;
                    }
                    b.set(m[0], h, m[1], t.trim());
                    b.set(m[0], h - 1, m[1], t.foundation());
                    int height = m[1] >= PATH_START ? 7 : 11;
                    b.set(m[0], h + 1, m[1], t.pillarBase());
                    b.fill(m[0], h + 2, m[1], m[0], h + height - 1, m[1], t.pillar());
                    b.set(m[0], h + height, m[1], t.cap());
                    b.set(m[0], h + height + 1, m[1], d(Blocks.WAXED_COPPER_BLOCK));
                    b.set(m[0], h + height + 2, m[1], d(Blocks.LIGHTNING_ROD));
                    b.set(m[0], h + height - 1, m[1], d(Blocks.WAXED_COPPER_BULB).setValue(CopperBulbBlock.LIT, true));
                }
            }
            case Titans.EMBER -> {
                // 火鉢 (玄武岩の台に溶岩の大釜): 台地の四方と参道の脇
                int[][] pans = {{-8, -4}, {8, -4}, {-8, 4}, {8, 4}, {0, -9}};
                for (int[] p : pans) {
                    b.fill(p[0], 1, p[1], p[0], 2, p[1], t.pillar());
                    b.set(p[0], 3, p[1], d(Blocks.LAVA_CAULDRON));
                }
                for (int z = 18; z <= plan.end; z += 8) {
                    int h = plan.at(z);
                    for (int sx : new int[] {-5, 5}) {
                        b.set(sx, h, z, t.trim());
                        b.set(sx, h - 1, z, t.foundation());
                        b.fill(sx, h + 1, z, sx, h + 3, z, t.pillar());
                        b.set(sx, h + 4, z, d(Blocks.LAVA_CAULDRON));
                    }
                }
            }
            default -> {
                // 鯨の肋骨のトンネル (参道に 4 本)。骨ブロックの弧
                for (int z = 18; z <= plan.end; z += 4) {
                    int h = plan.at(z);
                    boolean flat = plan.at(Math.max(z - 1, PATH_START)) == h && plan.at(Math.min(z + 1, plan.end)) == h;
                    if (!flat) {
                        continue;
                    }
                    for (int y = 1; y <= 10; y++) {
                        int lo = rib(y);
                        int hi = y > 1 ? rib(y - 1) : lo;
                        for (int x = lo; x <= hi; x++) {
                            BlockState bone = d(Blocks.BONE_BLOCK).setValue(RotatedPillarBlock.AXIS, lo == hi ? Direction.Axis.Y : Direction.Axis.X);
                            b.set(x, h + y, z, bone);
                            b.set(-x, h + y, z, bone);
                        }
                    }
                    b.set(4, h, z, t.trim());
                    b.set(-4, h, z, t.trim());
                    b.set(4, h - 1, z, t.foundation());
                    b.set(-4, h - 1, z, t.foundation());
                    b.set(0, h + 9, z, lantern(Blocks.SOUL_LANTERN, true));
                }
                // 台地の四隅の四阿の脇に、肋骨の飾り (東西の縁に二本ずつ)
                for (int sx : new int[] {-1, 1}) {
                    for (int z : new int[] {-3, 3}) {
                        b.fill(sx * 9, 1, z, sx * 9, 4, z, d(Blocks.BONE_BLOCK).setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                        b.set(sx * 8, 5, z, d(Blocks.BONE_BLOCK).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                        b.set(sx * 9, 5, z, d(Blocks.BONE_BLOCK).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                        b.set(sx * 8, 6, z, lantern(Blocks.SOUL_LANTERN, false));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 光 (湧き止め)
    private static void lighting(Blueprint b, Plan plan) {
        for (int[] c : new int[][] {{-4, 1, -4}, {4, 1, -4}, {-4, 1, 4}, {4, 1, 4}}) {
            b.lights.add(new int[] {c[0], c[1], c[2], 15});
        }
        List<int[]> spots = new ArrayList<>(b.floors);
        for (int[] f : spots) {
            if (b.has(f[0], f[1] + 1, f[2]) || b.has(f[0], f[1] + 2, f[2])) {
                continue;
            }
            if (!covered(b, f)) {
                b.set(f[0], f[1] + 2, f[2], d(Blocks.LIGHT).setValue(LightBlock.LEVEL, 11));
            }
        }
    }

    static boolean covered(Blueprint b, int[] f) {
        for (int[] l : b.lights) {
            int dist = Math.abs(l[0] - f[0]) + Math.abs(l[1] - f[1] - 1) + Math.abs(l[2] - f[2]);
            if (l[3] - dist >= 5) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 置く
    private static int apply(ServerLevel level, Blueprint b) {
        int count = 0;
        for (Map.Entry<BlockPos, BlockState> e : b.map.entrySet()) {
            BlockPos p = e.getKey();
            int rx = p.getX() - b.origin.getX();
            int rz = p.getZ() - b.origin.getZ();
            int ry = p.getY() - b.origin.getY();
            if (inPedestal(rx, ry, rz) && ry >= 0 && ry <= 1) {
                continue;
            }
            level.setBlock(p, e.getValue(), 2);
            count++;
        }
        // 階段・壁・窓格子の形 (角・つながり) を、置いたあとの隣から出し直す
        for (Map.Entry<BlockPos, BlockState> e : b.map.entrySet()) {
            BlockPos p = e.getKey();
            BlockState s = level.getBlockState(p);
            Block blk = s.getBlock();
            if (blk instanceof StairBlock || blk instanceof WallBlock || blk instanceof IronBarsBlock || blk instanceof FenceBlock) {
                BlockState n = s;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos q = p.relative(dir);
                    n = n.updateShape(dir, level.getBlockState(q), level, p, q);
                }
                if (n != s) {
                    level.setBlock(p, n, 2);
                }
            }
        }
        return count;
    }
}
