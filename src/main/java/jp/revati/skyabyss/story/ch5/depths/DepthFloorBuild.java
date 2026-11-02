package jp.revati.skyabyss.story.ch5.depths;

import java.util.Random;

import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Chest;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Plan;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Room;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.RoomType;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Spot;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Trap;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Kind;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

// 階の間取り (DepthFloor.Plan) をブロックの箱にする (docs/design/36 の 9)。箱は SIZE × BOX_H × SIZE。
//   空気の部分 (床の上 ROOM_H・下り口の穴) のまわりは、必ず壁・床・天井で包む (外の虚空は見えない)。
//   null は「何もない (空気)」。建てるとき、箱の全部を見比べて違う所だけ置くので、前の階の残りは消える。
//   型ごとの景色 (八つ) は、材料と飾りで変える。飾りは通路の線・箱・罠・灯籠のまわりを避けて置く。
public final class DepthFloorBuild {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int N = DepthPlan.SIZE;
    private static final int H = DepthPlan.BOX_H;
    static final int FLOOR_YI = DepthPlan.BELOW;
    static final int CEIL_YI = DepthPlan.BELOW + DepthPlan.ROOM_H + 1;

    // 型ごとの材料。floor 床・wall 壁・roof 天井・accent 飾り・lamp 灯り
    public record Palette(BlockState floor, BlockState wall, BlockState roof, BlockState accent, BlockState lamp, BlockState trim) {
    }

    private final Plan plan;
    private final BlockState[] cells = new BlockState[N * N * H];
    private final Palette pal;
    private final Random r;
    private final boolean[][] spec = new boolean[N][N];
    private final boolean sealed;

    private DepthFloorBuild(Plan plan, boolean sealed) {
        this.plan = plan;
        this.sealed = sealed;
        this.pal = palette(plan.kind());
        this.r = new Random(plan.seed() ^ 0x51A7E);
    }

    public record Volume(BlockState[] cells) {
        public BlockState at(int x, int yi, int z) {
            return cells[(yi * N + z) * N + x];
        }
    }

    // sealed: 王の階の下り口の蓋を載せて建てる (王を倒すまで)
    public static Volume build(Plan plan, boolean sealed) {
        DepthFloorBuild b = new DepthFloorBuild(plan, sealed);
        b.run();
        return new Volume(b.cells);
    }

    public static Palette palette(Kind k) {
        return switch (k) {
            case INVERTED_FOREST -> new Palette(Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.ROOTED_DIRT.defaultBlockState(), Blocks.MANGROVE_LOG.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
                    Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState());
            case FLOATING_GRAVES -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
                    Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            case PULSE_CAVE -> new Palette(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(),
                    Blocks.NETHER_WART_BLOCK.defaultBlockState(), Blocks.NETHER_WART_BLOCK.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
                    Blocks.CRACKED_NETHER_BRICKS.defaultBlockState());
            case MIRROR_DARK -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(),
                    Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
            case CHAIN_NET -> new Palette(Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                    Blocks.DEEPSLATE.defaultBlockState(), Blocks.CHAIN.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
                    Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            case LAMP_HALL -> new Palette(Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.SCULK.defaultBlockState(), Blocks.SCULK.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState());
            case DRY_WELL -> new Palette(Blocks.PACKED_MUD.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(),
                    Blocks.PACKED_MUD.defaultBlockState(), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
                    Blocks.MUD_BRICKS.defaultBlockState());
            case HOLLOW_STARS -> new Palette(Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(),
                    Blocks.PURPUR_PILLAR.defaultBlockState());
        };
    }

    private int idx(int x, int yi, int z) {
        return (yi * N + z) * N + x;
    }

    private BlockState get(int x, int yi, int z) {
        return cells[idx(x, yi, z)];
    }

    private void set(int x, int yi, int z, BlockState st) {
        cells[idx(x, yi, z)] = st;
    }

    private boolean isOpen(int x, int yi, int z) {
        return x >= 0 && z >= 0 && x < N && z < N && yi >= 0 && yi < H && get(x, yi, z) == AIR;
    }

    private void run() {
        carve();
        shell();
        lamps();
        features();
        dress();
        decorate();
        finish();
    }

    // ---------------------------------------------------------------- 仕上げ (形は変えない。2026-10-28 の見直し「箱の部屋が平たい」)
    //   材料の斑: 壁・床・天井を型ごとの数種へ、ゆっくり変わる斑で。壁のいちばん下の段は台座の石、いちばん上の段は縁の石。
    //   天井の蛇腹: 壁ぞいのいちばん上の空気 (床から 6 上。通るのを邪魔しない) に、逆さの階段を壁へ向けて。
    //   罠の床 (ひびの深層岩タイル)・落ちる石・光る石は使わない (罠の見分け・灯りの決まり・床の下は空気)
    record Mix(BlockState[] s, int[] w) {
        static Mix of(Object... kv) {
            BlockState[] s = new BlockState[kv.length / 2];
            int[] w = new int[kv.length / 2];
            for (int i = 0; i < s.length; i++) {
                s[i] = ((net.minecraft.world.level.block.Block) kv[i * 2]).defaultBlockState();
                w[i] = (Integer) kv[i * 2 + 1];
            }
            return new Mix(s, w);
        }

        BlockState pick(double u) {
            int total = 0;
            for (int x : w) {
                total += x;
            }
            double t = Math.max(0, Math.min(0.999, u)) * total;
            for (int i = 0; i < s.length; i++) {
                t -= w[i];
                if (t < 0) {
                    return s[i];
                }
            }
            return s[s.length - 1];
        }
    }

    record Finish(Mix walls, Mix floors, Mix roofs, Mix base, BlockState band, BlockState cornice) {
    }

    static Finish finishOf(Kind k) {
        return switch (k) {
            case INVERTED_FOREST -> new Finish(Mix.of(Blocks.DEEPSLATE_TILES, 5, Blocks.DEEPSLATE_BRICKS, 3, Blocks.COBBLED_DEEPSLATE, 1),
                    Mix.of(Blocks.MOSS_BLOCK, 6, Blocks.ROOTED_DIRT, 2, Blocks.COARSE_DIRT, 1), Mix.of(Blocks.ROOTED_DIRT, 5, Blocks.DIRT, 2, Blocks.MOSS_BLOCK, 1),
                    Mix.of(Blocks.MOSS_BLOCK, 3, Blocks.MOSSY_COBBLESTONE, 2, Blocks.COBBLED_DEEPSLATE, 2), Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState(),
                    Blocks.MANGROVE_STAIRS.defaultBlockState());
            case FLOATING_GRAVES -> new Finish(Mix.of(Blocks.DEEPSLATE_BRICKS, 6, Blocks.CRACKED_DEEPSLATE_BRICKS, 2, Blocks.DEEPSLATE_TILES, 1, Blocks.POLISHED_DEEPSLATE, 1),
                    Mix.of(Blocks.POLISHED_BLACKSTONE_BRICKS, 6, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, 2, Blocks.POLISHED_BLACKSTONE, 1),
                    Mix.of(Blocks.DEEPSLATE_BRICKS, 5, Blocks.DEEPSLATE_TILES, 2), Mix.of(Blocks.POLISHED_BLACKSTONE, 1),
                    Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState());
            case PULSE_CAVE -> new Finish(Mix.of(Blocks.RED_NETHER_BRICKS, 6, Blocks.NETHER_BRICKS, 2, Blocks.NETHERRACK, 1),
                    Mix.of(Blocks.NETHER_BRICKS, 6, Blocks.CRACKED_NETHER_BRICKS, 2, Blocks.RED_NETHER_BRICKS, 1, Blocks.NETHERRACK, 1),
                    Mix.of(Blocks.NETHER_WART_BLOCK, 6, Blocks.NETHERRACK, 1, Blocks.RED_NETHER_BRICKS, 1), Mix.of(Blocks.NETHER_BRICKS, 3, Blocks.NETHERRACK, 1),
                    Blocks.CHISELED_NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState());
            case MIRROR_DARK -> new Finish(Mix.of(Blocks.BLACKSTONE, 6, Blocks.POLISHED_BLACKSTONE, 2, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, 1, Blocks.BASALT, 1),
                    Mix.of(Blocks.POLISHED_BLACKSTONE_BRICKS, 6, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, 2, Blocks.POLISHED_BLACKSTONE, 1),
                    Mix.of(Blocks.BLACKSTONE, 5, Blocks.BASALT, 2), Mix.of(Blocks.POLISHED_BLACKSTONE, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, 1),
                    Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState());
            case CHAIN_NET -> new Finish(Mix.of(Blocks.DEEPSLATE, 5, Blocks.COBBLED_DEEPSLATE, 3, Blocks.POLISHED_DEEPSLATE, 1, Blocks.DEEPSLATE_BRICKS, 1),
                    Mix.of(Blocks.DEEPSLATE_TILES, 6, Blocks.DEEPSLATE_BRICKS, 2), Mix.of(Blocks.DEEPSLATE, 5, Blocks.COBBLED_DEEPSLATE, 2),
                    Mix.of(Blocks.POLISHED_DEEPSLATE, 1), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState());
            case LAMP_HALL -> new Finish(Mix.of(Blocks.DEEPSLATE_BRICKS, 6, Blocks.CRACKED_DEEPSLATE_BRICKS, 2, Blocks.DEEPSLATE_TILES, 1),
                    Mix.of(Blocks.POLISHED_DEEPSLATE, 6, Blocks.DEEPSLATE_TILES, 2), Mix.of(Blocks.SCULK, 5, Blocks.DEEPSLATE, 2),
                    Mix.of(Blocks.POLISHED_DEEPSLATE, 1), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState());
            case DRY_WELL -> new Finish(Mix.of(Blocks.MUD_BRICKS, 6, Blocks.PACKED_MUD, 2, Blocks.BROWN_TERRACOTTA, 1),
                    Mix.of(Blocks.PACKED_MUD, 5, Blocks.COARSE_DIRT, 2, Blocks.BROWN_TERRACOTTA, 1), Mix.of(Blocks.PACKED_MUD, 5, Blocks.DRIPSTONE_BLOCK, 2),
                    Mix.of(Blocks.PACKED_MUD, 2, Blocks.DRIPSTONE_BLOCK, 1), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.MUD_BRICK_STAIRS.defaultBlockState());
            case HOLLOW_STARS -> new Finish(Mix.of(Blocks.POLISHED_BASALT, 6, Blocks.BASALT, 2, Blocks.SMOOTH_BASALT, 2),
                    Mix.of(Blocks.END_STONE_BRICKS, 6, Blocks.END_STONE, 2), Mix.of(Blocks.BLACKSTONE, 5, Blocks.BASALT, 1, Blocks.OBSIDIAN, 1),
                    Mix.of(Blocks.SMOOTH_BASALT, 1), Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.END_STONE_BRICK_STAIRS.defaultBlockState());
        };
    }

    private void finish() {
        Finish f = finishOf(plan.kind());
        long seed = plan.seed();
        int top = FLOOR_YI + DepthPlan.ROOM_H;
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Direction[] face = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        // 蛇腹 (斑より先に: 壁を pal.wall() で見分ける)
        for (int x = 1; x < N - 1; x++) {
            for (int z = 1; z < N - 1; z++) {
                if (get(x, top, z) != AIR) {
                    continue;
                }
                for (int i = 0; i < 4; i++) {
                    if (get(x + d[i][0], top, z + d[i][1]) == pal.wall()) {
                        set(x, top, z, f.cornice().setValue(net.minecraft.world.level.block.StairBlock.FACING, face[i])
                                .setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
                        break;
                    }
                }
            }
        }
        // 材料の斑と壁の帯
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                for (int yi = 0; yi < H; yi++) {
                    BlockState st = get(x, yi, z);
                    if (st == null || st == AIR) {
                        continue;
                    }
                    // 斑の値は真ん中に寄るので広げる (端の材料も出るように)
                    double u = (noise(seed, x, yi, z, 4.0) - 0.5) * 1.8 + 0.5 + (r.nextDouble() - 0.5) * 0.15;
                    // 型によって床・壁・天井が同じ材料のことがあるので、段で見分ける
                    if (yi == CEIL_YI && st == pal.roof()) {
                        set(x, yi, z, f.roofs().pick(u));
                    } else if (yi == FLOOR_YI && st == pal.floor()) {
                        set(x, yi, z, f.floors().pick(u));
                    } else if (st == pal.wall() && yi > FLOOR_YI && yi < CEIL_YI) {
                        set(x, yi, z, yi == FLOOR_YI + 1 ? f.base().pick(u) : yi == top ? f.band() : f.walls().pick(u));
                    } else if (st == pal.wall()) {
                        set(x, yi, z, f.walls().pick(u));
                    }
                }
            }
        }
        // 型の小物: 乾いた井戸の枯れ草 (枯れ草が育つ床の上だけ)
        if (plan.kind() == Kind.DRY_WELL) {
            for (int x = 1; x < N - 1; x++) {
                for (int z = 1; z < N - 1; z++) {
                    BlockState fl = get(x, FLOOR_YI, z);
                    if (freeTile(x, z) && (fl == Blocks.COARSE_DIRT.defaultBlockState() || fl == Blocks.BROWN_TERRACOTTA.defaultBlockState()) && r.nextInt(100) < 35) {
                        set(x, FLOOR_YI + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                }
            }
        }
    }

    // ゆっくり変わる値の斑 (0..1)
    static double noise(long seed, int x, int y, int z, double s) {
        double fx = x / s;
        double fy = y / s;
        double fz = z / s;
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        int iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix);
        double ty = smooth(fy - iy);
        double tz = smooth(fz - iz);
        double v = 0;
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                for (int c = 0; c <= 1; c++) {
                    double w = (a == 0 ? 1 - tx : tx) * (b == 0 ? 1 - ty : ty) * (c == 0 ? 1 - tz : tz);
                    v += w * hash(seed, ix + a, iy + b, iz + c);
                }
            }
        }
        return v;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    // ---------------------------------------------------------------- 空気と包み
    private void carve() {
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                if (plan.open()[x][z]) {
                    for (int yi = FLOOR_YI + 1; yi <= FLOOR_YI + DepthPlan.ROOM_H; yi++) {
                        set(x, yi, z, AIR);
                    }
                }
            }
        }
        // 下り口の穴 (3×3、床の高さから 4 下まで)
        Spot s = plan.stairs();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int yi = 1; yi <= FLOOR_YI; yi++) {
                    set(s.x() + dx, yi, s.z() + dz, AIR);
                }
            }
        }
    }

    private void shell() {
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                for (int yi = 0; yi < H; yi++) {
                    if (get(x, yi, z) != AIR) {
                        continue;
                    }
                    for (int[] v : d) {
                        int nx = x + v[0];
                        int ny = yi + v[1];
                        int nz = z + v[2];
                        if (nx < 0 || nz < 0 || nx >= N || nz >= N || ny < 0 || ny >= H) {
                            throw new IllegalStateException("open cell on the box edge at " + x + "," + yi + "," + z);
                        }
                        if (get(nx, ny, nz) == null) {
                            set(nx, ny, nz, v[1] < 0 ? pal.floor() : v[1] > 0 ? pal.roof() : pal.wall());
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 灯り
    private void lamps() {
        for (Spot l : plan.lights()) {
            set(l.x(), CEIL_YI, l.z(), pal.lamp());
        }
        // 穴の底の光
        Spot s = plan.stairs();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(s.x() + dx, 0, s.z() + dz, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
    }

    // ---------------------------------------------------------------- 仕掛けの設え (下り口・休み場・行商・箱・罠)
    private void features() {
        Spot s = plan.stairs();
        // 下り口: 穴のまわりに輪、四隅に魂の灯り
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = s.x() + dx;
                int z = s.z() + dz;
                spec[x][z] = true;
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    if (get(x, FLOOR_YI, z) != null) {
                        set(x, FLOOR_YI, z, Blocks.SCULK.defaultBlockState());
                    }
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && get(x, FLOOR_YI + 1, z) == AIR) {
                        set(x, FLOOR_YI + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
                    }
                }
            }
        }
        if (sealed) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(s.x() + dx, FLOOR_YI, s.z() + dz, Blocks.CRYING_OBSIDIAN.defaultBlockState());
                }
            }
        }
        mark(plan.entry(), 2);
        if (plan.lantern() != null) {
            Spot l = plan.lantern();
            mark(l, 2);
            set(l.x(), FLOOR_YI + 1, l.z(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(l.x(), FLOOR_YI + 2, l.z(), Blocks.CANDLE.defaultBlockState());
        }
        if (plan.chain() != null) {
            Spot c = plan.chain();
            mark(c, 1);
            for (int yi = FLOOR_YI + 2; yi <= FLOOR_YI + DepthPlan.ROOM_H; yi++) {
                set(c.x(), yi, c.z(), Blocks.CHAIN.defaultBlockState());
            }
            set(c.x(), FLOOR_YI + 1, c.z(), Blocks.LANTERN.defaultBlockState());
        }
        if (plan.merchant() != null) {
            Spot m = plan.merchant();
            mark(m, 2);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (get(m.x() + dx, FLOOR_YI + 1, m.z() + dz) == AIR) {
                        set(m.x() + dx, FLOOR_YI + 1, m.z() + dz, Blocks.RED_CARPET.defaultBlockState());
                    }
                }
            }
            if (get(m.x() - 2, FLOOR_YI + 1, m.z() - 2) == AIR) {
                set(m.x() - 2, FLOOR_YI + 1, m.z() - 2, Blocks.LANTERN.defaultBlockState());
            }
            if (get(m.x() + 2, FLOOR_YI + 1, m.z() + 2) == AIR) {
                set(m.x() + 2, FLOOR_YI + 1, m.z() + 2, Blocks.LANTERN.defaultBlockState());
            }
        }
        for (Chest c : plan.chests()) {
            mark(new Spot(c.x(), c.z()), 1);
            if (c.tier() == 3) {
                continue; // 王の褒美は倒したあとに置く
            }
            set(c.x(), FLOOR_YI + 1, c.z(), chestState(c));
            if (c.tier() == 1) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (get(c.x() + dx, FLOOR_YI, c.z() + dz) != null && get(c.x() + dx, FLOOR_YI + 1, c.z() + dz) != Blocks.CHEST.defaultBlockState()) {
                            set(c.x() + dx, FLOOR_YI, c.z() + dz, Blocks.GILDED_BLACKSTONE.defaultBlockState());
                        }
                    }
                }
            } else if (c.tier() == 2) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (get(c.x() + dx, FLOOR_YI, c.z() + dz) != null) {
                            set(c.x() + dx, FLOOR_YI, c.z() + dz, Blocks.SCULK.defaultBlockState());
                        }
                    }
                }
            }
        }
        for (Trap t : plan.traps()) {
            mark(new Spot(t.x(), t.z()), 1);
            set(t.x(), FLOOR_YI, t.z(), Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
        }
        if (plan.boss()) {
            Spot b = plan.bossSpawn();
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    int rr = dx * dx + dz * dz;
                    int x = b.x() + dx;
                    int z = b.z() + dz;
                    if (rr >= 30 && rr <= 49 && get(x, FLOOR_YI, z) != null && get(x, FLOOR_YI + 1, z) == AIR) {
                        set(x, FLOOR_YI, z, pal.accent().getBlock() == Blocks.CHAIN ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : pal.accent());
                    }
                }
            }
        }
    }

    private BlockState chestState(Chest c) {
        Room room = plan.roomAt(c.x(), c.z());
        Direction f = Direction.NORTH;
        if (room != null) {
            int dx = room.cx() - c.x();
            int dz = room.cz() - c.z();
            f = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        }
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f);
    }

    private void mark(Spot s, int rad) {
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                int x = s.x() + dx;
                int z = s.z() + dz;
                if (x >= 0 && z >= 0 && x < N && z < N) {
                    spec[x][z] = true;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 部屋の作り (柱・梁・足もとの縁取り。通路の線・飾りの場所は避ける)
    private void dress() {
        for (Room room : plan.rooms()) {
            for (int x = room.x0(); x < room.x0() + room.w(); x++) {
                for (int z = room.z0(); z < room.z0() + room.d(); z++) {
                    boolean edgeX = x == room.x0() || x == room.x0() + room.w() - 1;
                    boolean edgeZ = z == room.z0() || z == room.z0() + room.d() - 1;
                    // 天井の梁
                    if ((x - room.x0()) % 5 == 2 && get(x, CEIL_YI, z) == pal.roof()) {
                        set(x, CEIL_YI, z, pal.trim());
                    }
                    if (!(edgeX || edgeZ) || plan.path()[x][z] || spec[x][z]) {
                        continue;
                    }
                    // 足もとの縁取り
                    if (get(x, FLOOR_YI, z) == pal.floor()) {
                        set(x, FLOOR_YI, z, pal.trim());
                    }
                    // 壁の柱 (四つおき。角は除く)
                    int along = edgeX ? z - room.z0() : x - room.x0();
                    boolean corner = edgeX && edgeZ;
                    if (!corner && along % 4 == 2 && room.w() >= 8 && room.d() >= 8 && get(x, FLOOR_YI + 1, z) == AIR) {
                        for (int yi = FLOOR_YI + 1; yi <= FLOOR_YI + DepthPlan.ROOM_H; yi++) {
                            if (get(x, yi, z) == AIR) {
                                set(x, yi, z, pal.trim());
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 型の景色
    private boolean freeTile(int x, int z) {
        return plan.open()[x][z] && !plan.path()[x][z] && !spec[x][z] && get(x, FLOOR_YI + 1, z) == AIR && get(x, CEIL_YI, z) != pal.lamp();
    }

    private void decorate() {
        Kind k = plan.kind();
        for (int x = 1; x < N - 1; x++) {
            for (int z = 1; z < N - 1; z++) {
                if (!plan.open()[x][z]) {
                    continue;
                }
                // 床の材料 (罠・輪・飾りに置き換えられていない床を、型の床にそろえる)
                int roll = r.nextInt(1000);
                switch (k) {
                    case INVERTED_FOREST -> {
                        if (freeTile(x, z) && (plan.roomAt(x, z) == null || plan.roomAt(x, z).type() != RoomType.BOSS)) {
                            if (roll < 90) {
                                hang(x, z, Blocks.MANGROVE_ROOTS.defaultBlockState(), 1 + r.nextInt(2));
                            } else if (roll < 105) {
                                hang(x, z, Blocks.MANGROVE_LOG.defaultBlockState(), 3);
                            } else if (roll < 260) {
                                set(x, FLOOR_YI + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                            }
                        }
                    }
                    case FLOATING_GRAVES -> {
                        if (freeTile(x, z) && roll < 40 && plan.roomAt(x, z) != null) {
                            set(x, FLOOR_YI + 1, z, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                            set(x, FLOOR_YI + 2, z, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
                        }
                    }
                    case PULSE_CAVE -> {
                        if ((x * 7 + z * 13) % 5 == 0) {
                            for (int yi = FLOOR_YI + 1; yi <= FLOOR_YI + DepthPlan.ROOM_H; yi++) {
                                wallVein(x, yi, z);
                            }
                        }
                    }
                    case MIRROR_DARK -> {
                        for (int yi = FLOOR_YI + 3; yi <= FLOOR_YI + 4; yi++) {
                            mirror(x, yi, z);
                        }
                    }
                    case CHAIN_NET -> {
                        if (freeTile(x, z) && plan.roomAt(x, z) != null) {
                            if (z % 4 == 0 && get(x, FLOOR_YI + DepthPlan.ROOM_H, z) == AIR) {
                                set(x, FLOOR_YI + DepthPlan.ROOM_H, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, net.minecraft.core.Direction.Axis.X));
                            } else if (roll < 50) {
                                hang(x, z, Blocks.CHAIN.defaultBlockState(), 2 + r.nextInt(2));
                            }
                        }
                    }
                    case LAMP_HALL -> {
                        if (freeTile(x, z)) {
                            if (roll < 40) {
                                set(x, CEIL_YI, z, Blocks.SEA_LANTERN.defaultBlockState());
                            } else if (roll < 240) {
                                set(x, FLOOR_YI, z, Blocks.SCULK.defaultBlockState());
                            }
                        }
                    }
                    case DRY_WELL -> {
                        if (freeTile(x, z) && roll < 25 && wellFits(x, z)) {
                            well(x, z);
                        }
                    }
                    case HOLLOW_STARS -> {
                        if (freeTile(x, z)) {
                            if (roll < 40) {
                                set(x, FLOOR_YI + 1, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                            } else if (roll < 60) {
                                set(x, CEIL_YI, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }
    }

    private void hang(int x, int z, BlockState st, int len) {
        for (int i = 0; i < len; i++) {
            int yi = CEIL_YI - 1 - i;
            if (get(x, yi, z) == AIR) {
                set(x, yi, z, st);
            }
        }
    }

    // 壁の面 (空気の隣の壁) だけ脈の色にする
    private void wallVein(int x, int yi, int z) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] v : d) {
            int wx = x + v[0];
            int wz = z + v[1];
            if (wx >= 0 && wz >= 0 && wx < N && wz < N && get(wx, yi, wz) == pal.wall()) {
                set(wx, yi, wz, Blocks.NETHER_WART_BLOCK.defaultBlockState());
            }
        }
    }

    // 壁の帯を色ガラスに (その向こうが壁でなければ、裏に壁を足す。向こうが別の空間ならそのまま壁)
    private void mirror(int x, int yi, int z) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] v : d) {
            int wx = x + v[0];
            int wz = z + v[1];
            int bx = wx + v[0];
            int bz = wz + v[1];
            if (wx < 1 || wz < 1 || wx >= N - 1 || wz >= N - 1 || bx < 0 || bz < 0 || bx >= N || bz >= N) {
                continue;
            }
            if (get(wx, yi, wz) == pal.wall() && get(bx, yi, bz) == null) {
                set(bx, yi, bz, pal.wall());
                set(wx, yi, wz, Blocks.TINTED_GLASS.defaultBlockState());
            }
        }
    }

    private boolean wellFits(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!freeTile(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void well(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                set(x + dx, FLOOR_YI + 1, z + dz, center ? Blocks.MUD.defaultBlockState() : Blocks.MUD_BRICK_WALL.defaultBlockState());
                spec[x + dx][z + dz] = true;
            }
        }
    }

    // 建てるときの値 (null は空気)
    public static BlockState desired(Volume v, int x, int yi, int z) {
        BlockState st = v.at(x, yi, z);
        return st == null ? AIR : st;
    }
}
