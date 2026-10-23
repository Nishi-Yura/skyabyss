package jp.revati.skyabyss.trial.raid.whale;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

// 岩鯨のレイドの部屋の飾り (docs/design/39 の 4.1「岩鯨（石・骨・歌）」)。RaidKit.buildCell が建てた箱の内側に、
// 地層の有機の壁・肋骨のアーチ・背骨の棟・歌の脈・遠景の窪み・小道具・見えない灯りを足す。部屋のクラスは build の最後で
// Kit を作り、守る場所 (keep = 仕掛け・道・予告の床・運ぶ道。決して書かない) を宣言してから、各部屋の decorate を呼ぶ。
//
// 決まり:
//  - put は広間の空気のマスにだけ置く (仕掛けのブロックを上書きしない)。keep の中には何も置かない。reserve (戦う床など) は
//    壁の肉付け・小道具が避ける柔らかい守りで、目印 (putR) と見えない灯りだけが入れる
//  - paint は部屋の殻・床・天井の「パレットのまま」のブロックだけを、別の不透明な満ちたブロックに塗り替える (殻は必ず一枚残る。外は見えない)
//  - 乱数の種は部屋の id で固定 (挑戦ごとに形は変わらない。ctx.rng() は使わない = 仕掛けの配置の乱数を変えない)
//  - 落ちる・変わる・燃えるブロック (39 の 6.3 節) は使わない。灯りは目的の物と戸に集め、ほかは見えない light で補う
//  - 費用の上限 (39 の 6 章): 飾り 8 千ブロック・明るさ 12 以上 40 個・ブロックエンティティ 24・エンティティ 16。finish が数えて残す
public final class WhaleDecor {
    private WhaleDecor() {
    }

    // 自己テスト用: 飾りを切る (飾りなしと飾りありを建て比べる)
    public static boolean enabled = true;

    public record Stats(int decor, int bright, int blockEntities, int entities, long ms) {
        @Override
        public String toString() {
            return "decor " + decor + ", bright " + bright + ", block entities " + blockEntities + ", entities " + entities + ", " + ms + " ms";
        }
    }

    private static final Map<String, Stats> LAST = new ConcurrentHashMap<>();

    public static Stats stats(String id) {
        return LAST.get(id);
    }

    public static String lastStats(String id) {
        Stats s = LAST.get(id);
        return s == null ? "" : s.toString();
    }

    // ---- 材 (39 の 4.1 の表)
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState[][] BANDS = {
            {Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()},
            {Blocks.TUFF.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState()},
            {Blocks.ANDESITE.defaultBlockState(), Blocks.STONE.defaultBlockState()},
            {Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState()}};
    static final BlockState FLOOR_A = Blocks.POLISHED_TUFF.defaultBlockState();
    static final BlockState FLOOR_B = Blocks.TUFF.defaultBlockState();
    static final BlockState CEIL_A = Blocks.SMOOTH_STONE.defaultBlockState();
    static final BlockState CEIL_B = Blocks.CALCITE.defaultBlockState();
    static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState();
    static final BlockState DARK = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState TUFF_BRICKS = Blocks.TUFF_BRICKS.defaultBlockState();

    static BlockState bone(Direction.Axis a) {
        return Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, a);
    }

    static BlockState light(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Mth.clamp(level, 0, 15));
    }

    static BlockState candle(int n, boolean lit) {
        return Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Mth.clamp(n, 1, 4)).setValue(CandleBlock.LIT, lit);
    }

    static BlockState cluster(Direction facing, int size) {
        Block b = size >= 3 ? Blocks.AMETHYST_CLUSTER : size == 2 ? Blocks.LARGE_AMETHYST_BUD : Blocks.MEDIUM_AMETHYST_BUD;
        return b.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
    }

    static BlockState stairTop(Direction facing) {
        return Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
    }

    static BlockState slab() {
        return Blocks.POLISHED_DIORITE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // ---- 動く飾り (仕掛けの進みに合わせて灯る燭台)
    // 位置にろうそくがあれば灯す/消す (無ければ何もしない)
    public static void setCandle(RaidCtx ctx, BlockPos p, boolean lit) {
        BlockState st = ctx.level().getBlockState(p);
        if (st.getBlock() instanceof CandleBlock && st.getValue(CandleBlock.LIT) != lit) {
            RaidKit.set(ctx.level(), p, st.setValue(CandleBlock.LIT, lit));
        }
    }

    // 出口の戸の脇の燭台 (越えると灯る。39 の 3.5 節「次はここ」)
    public static List<BlockPos> exitCandles(RaidCtx ctx) {
        RaidKit.Cell c = ctx.cell();
        return List.of(c.origin().offset(-3, 1, -c.hz() + 1), c.origin().offset(3, 1, -c.hz() + 1));
    }

    public static void exitLamps(RaidCtx ctx, boolean lit) {
        if (ctx.cell() == null) {
            return;
        }
        for (BlockPos p : exitCandles(ctx)) {
            setCandle(ctx, p, lit);
        }
    }

    // ================================================================
    // 部屋ごとに一つ作る道具。座標はすべて床の中心からの相対 (x, y, z)。y = 0 が床、1..h が広間の空気、h + 1 が天井
    public static final class Kit {
        final RaidCtx ctx;
        final ServerLevel level;
        final RaidKit.Cell cell;
        final BlockPos o;
        final int hx;
        final int hz;
        final int h;
        final long seed;
        final ImprovedNoise noise;
        final RandomSource rnd;
        private final List<int[]> keep = new ArrayList<>();
        private final List<int[]> reserve = new ArrayList<>();
        private final Set<Long> mine = new HashSet<>();
        int decor;
        private final long t0 = System.nanoTime();

        public Kit(RaidCtx ctx) {
            this.ctx = ctx;
            this.level = ctx.level();
            this.cell = ctx.cell();
            this.o = cell.origin();
            this.hx = cell.hx();
            this.hz = cell.hz();
            this.h = cell.height();
            this.seed = 0x5EEDL * 31 + ctx.roomId().hashCode();
            this.noise = new ImprovedNoise(RandomSource.create(seed));
            this.rnd = RandomSource.create(seed ^ 0x9E3779B97F4A7C15L);
            // 入口・出口の戸の前 (幅 5・高さ 5) は、いつも空けておく
            keep(-2, 1, hz - 2, 2, 5, hz);
            keep(-2, 1, -hz, 2, 3, -hz + 2);
        }

        // 決して書かない箱 (角を含む)
        public Kit keep(int x0, int y0, int z0, int x1, int y1, int z1) {
            keep.add(new int[] {Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)});
            return this;
        }

        // 壁の肉付け・小道具が避ける箱 (目印と見えない灯りは入れる)
        public Kit reserve(int x0, int y0, int z0, int x1, int y1, int z1) {
            reserve.add(new int[] {Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)});
            return this;
        }

        private static boolean in(List<int[]> l, int x, int y, int z) {
            for (int[] b : l) {
                if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                    return true;
                }
            }
            return false;
        }

        public boolean kept(int x, int y, int z) {
            return in(keep, x, y, z);
        }

        public boolean reserved(int x, int y, int z) {
            return in(reserve, x, y, z) || y >= roundY0 && y <= roundY1 && x * x + z * z <= roundR * roundR;
        }

        private double roundR = -1;
        private int roundY0;
        private int roundY1 = -1;

        // 円い reserve (中心から半径 r、高さ y0..y1。闘技場の床)
        public Kit reserveRound(double r, int y0, int y1) {
            roundR = r;
            roundY0 = y0;
            roundY1 = y1;
            return this;
        }

        BlockPos at(int x, int y, int z) {
            return o.offset(x, y, z);
        }

        BlockState get(int x, int y, int z) {
            return level.getBlockState(at(x, y, z));
        }

        boolean inHall(int x, int y, int z) {
            return Math.abs(x) <= hx && Math.abs(z) <= hz && y >= 1 && y <= h;
        }

        private static long key(int x, int y, int z) {
            return BlockPos.asLong(x, y, z);
        }

        // 広間の空気のマスに置く (keep・reserve は避ける)
        public boolean put(int x, int y, int z, BlockState st) {
            return !reserved(x, y, z) && putR(x, y, z, st);
        }

        // 目印用: reserve の中にも置く (keep は避ける)
        public boolean putR(int x, int y, int z, BlockState st) {
            if (!inHall(x, y, z) || kept(x, y, z) || !get(x, y, z).isAir()) {
                return false;
            }
            RaidKit.set(level, at(x, y, z), st);
            mine.add(key(x, y, z));
            decor++;
            return true;
        }

        // 自分で置いた飾りを別の飾りに替える (歌の脈など)
        boolean recolor(int x, int y, int z, BlockState st) {
            if (mine.contains(key(x, y, z))) {
                RaidKit.set(level, at(x, y, z), st);
                return true;
            }
            return paint(x, y, z, st);
        }

        // 部屋の殻・床・天井 (パレットのまま残っているブロック) を塗り替える。殻は不透明な満ちたブロックのまま
        public boolean paint(int x, int y, int z, BlockState st) {
            if (kept(x, y, z) || st.isAir()) {
                return false;
            }
            BlockPos p = at(x, y, z);
            AABBi b = bounds();
            if (x < b.x0 || x > b.x1 || y < -1 || y > h + 1 || z < b.z0 || z > b.z1) {
                return false;
            }
            BlockState cur = level.getBlockState(p);
            RaidKit.Palette pal = cell.palette();
            boolean hallXZ = Math.abs(x) <= hx && Math.abs(z) <= hz;
            boolean ok;
            if (hallXZ && y == 0) {
                // 床: 床の石と、buildCell の帯の位置 (縁と十字) の骨だけ
                ok = cur == pal.floor() || cur == pal.trim() && (Math.abs(x) == hx || Math.abs(z) == hz || x == 0 || z == 0);
            } else if (hallXZ && y == h + 1) {
                ok = cur == pal.ceiling() || cur == pal.light();
            } else if (hallXZ) {
                ok = false;
            } else {
                // 殻の壁・入口と出口の間
                ok = cur == pal.wall() || cur == pal.floor() || cur == pal.ceiling() || cur == pal.trim() && y == 0;
            }
            if (!ok) {
                return false;
            }
            if (!st.isSolidRender(level, p)) {
                return false;
            }
            RaidKit.set(level, p, st);
            decor++;
            return true;
        }

        // 入口・出口の間の、殻の中で浮いている灯りを替える (空気の中なので solid でなくてよい)
        boolean replaceLight(int x, int y, int z, BlockState st) {
            if (get(x, y, z) != cell.palette().light()) {
                return false;
            }
            RaidKit.set(level, at(x, y, z), st);
            decor++;
            return true;
        }

        record AABBi(int x0, int z0, int x1, int z1) {
        }

        AABBi bounds() {
            return new AABBi((int) cell.bounds().minX - o.getX(), (int) cell.bounds().minZ - o.getZ(), (int) cell.bounds().maxX - 1 - o.getX(),
                    (int) cell.bounds().maxZ - 1 - o.getZ());
        }

        double n(double a, double b, double c) {
            return noise.noise(a, b, c);
        }

        boolean mix(int x, int y, int z, int pct) {
            return Math.floorMod(Mth.getSeed(x, y, z) ^ seed, 100) < pct;
        }

        // 高さの濃淡 (39 の 3.3 節)。下が重く暗い (深層岩)、上が軽く明るい (方解石)。境目はノイズでぼかす
        BlockState band(int x, int y, int z, double v) {
            double f = (y + v * 1.5) / Math.max(5.0, h);
            int b = f < 0.22 ? 0 : f < 0.45 ? 1 : f < 0.7 ? 2 : 3;
            return BANDS[b][mix(x, y, z, 30) ? 1 : 0];
        }

        // ---- 壁の面の座標。side 0 = 西 (x = -hx)、1 = 東、2 = 北 (z = -hz)、3 = 南。k = 壁から内への深さ (-1 = 殻)
        static int len(Kit k, int side) {
            return side < 2 ? k.hz : k.hx;
        }

        int[] wall(int side, int u, int y, int k) {
            return switch (side) {
                case 0 -> new int[] {-hx + k, y, u};
                case 1 -> new int[] {hx - k, y, u};
                case 2 -> new int[] {u, y, -hz + k};
                default -> new int[] {u, y, hz - k};
            };
        }

        static Direction inward(int side) {
            return switch (side) {
                case 0 -> Direction.EAST;
                case 1 -> Direction.WEST;
                case 2 -> Direction.SOUTH;
                default -> Direction.NORTH;
            };
        }

        double wallNoise(int side, int u, int y) {
            // 横に長く・縦に短い周期 = 横向きの地層 (39 の 4.1 の形の 3)
            return n(u * 0.11 + side * 37.3, y * 0.33, 0.5) + 0.5 * n(u * 0.45 + side * 11.1, y * 0.7, 7.5);
        }

        // ================================================================
        // 壁の肉付け (39 の 3.2 節)。cap(side, u, y) = その場所の深さの上限 (0 なら肉を付けない)。殻そのものも濃淡で塗る
        public interface Cap {
            int at(int side, int u, int y);
        }

        public void organicWall(Cap cap) {
            for (int side = 0; side < 4; side++) {
                int l = len(this, side);
                for (int u = -l; u <= l; u++) {
                    for (int y = 1; y <= h; y++) {
                        double v = wallNoise(side, u, y);
                        int max = Math.max(0, cap.at(side, u, y));
                        int d = Mth.clamp((int) Math.round(1.2 + v * 1.6), 0, max);
                        if (y <= 1) {
                            d = Math.max(d, Math.min(1, max));
                        }
                        if (y >= h - 1) {
                            d = Math.min(max + 1, d + 1);
                        }
                        if (side >= 2 && Math.abs(u) <= 2 && y <= 5) {
                            d = 0;
                        }
                        int[] s = wall(side, u, y, -1);
                        paint(s[0], s[1], s[2], band(s[0], y, s[2], v));
                        for (int k = 0; k < d; k++) {
                            int[] p = wall(side, u, y, k);
                            put(p[0], p[1], p[2], band(p[0], y, p[2], v));
                        }
                    }
                }
            }
        }

        // 床 (39 の 3.3 節の床 + 3.5 節の導線): 凝灰岩の床、縁は暗く、入口から目的の物へ一本の背骨の筋 (骨を南北に寝かせる)
        public void floor(int lineFromZ, int lineToZ) {
            for (int x = -hx; x <= hx; x++) {
                for (int z = -hz; z <= hz; z++) {
                    BlockState st;
                    if (x == 0 && z <= Math.max(lineFromZ, lineToZ) && z >= Math.min(lineFromZ, lineToZ)) {
                        st = bone(Direction.Axis.Z);
                    } else if (Math.abs(x) == hx || Math.abs(z) == hz) {
                        st = mix(x, 0, z, 40) ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                    } else {
                        double v = n(x * 0.21, 3.3, z * 0.21);
                        st = v + (mix(x, 0, z, 50) ? 0.15 : -0.15) > 0.12 ? FLOOR_B : FLOOR_A;
                    }
                    paint(x, 0, z, st);
                }
            }
            // 筋の両脇に一つおきの肋骨の印 (方解石)
            for (int z = Math.min(lineFromZ, lineToZ) + 1; z < Math.max(lineFromZ, lineToZ); z += 3) {
                paint(-1, 0, z, CALCITE);
                paint(1, 0, z, CALCITE);
            }
        }

        // 床に埋めた化石の魚 (骨の背骨と三対の肋骨。床の高さのまま = 段差なし)
        public void fossil(int x, int z, boolean alongX) {
            for (int i = -3; i <= 3; i++) {
                int px = alongX ? x + i : x;
                int pz = alongX ? z : z + i;
                paint(px, 0, pz, bone(alongX ? Direction.Axis.X : Direction.Axis.Z));
                if (i >= -2 && i <= 1 && (i & 1) == 0) {
                    for (int s = -1; s <= 1; s += 2) {
                        int w = i == -2 ? 2 : 1;
                        for (int j = 1; j <= w; j++) {
                            paint(alongX ? px : px + s * j, 0, alongX ? pz + s * j : pz, CALCITE);
                        }
                    }
                }
            }
            paint(alongX ? x + 4 : x, 0, alongX ? z : z + 4, Blocks.POLISHED_DIORITE.defaultBlockState());
        }

        // 天井: 平らな石と方解石。buildCell の格子の灯り (見える塊) は消して、真下に見えない灯りを置く (39 の 3.7 節)
        public void ceiling(int hiddenLevel) {
            BlockState lightBlock = cell.palette().light();
            for (int x = -hx; x <= hx; x++) {
                for (int z = -hz; z <= hz; z++) {
                    boolean wasLight = get(x, h + 1, z) == lightBlock;
                    double v = n(x * 0.17, 9.1, z * 0.17);
                    paint(x, h + 1, z, v > 0.15 || mix(x, h + 1, z, 20) ? CEIL_B : CEIL_A);
                    if (wasLight && hiddenLevel > 0) {
                        putR(x, h, z, light(hiddenLevel));
                    }
                }
            }
        }

        // 見えない灯りの格子 (y の高さ、間隔 step)。壁ぎわ (縁から 3 以内) は edgeLevel
        public void lightGrid(int y, int step, int level, int edgeLevel) {
            for (int x = -hx + step / 2; x <= hx; x += step) {
                for (int z = -hz + step / 2; z <= hz; z += step) {
                    boolean edge = hx - Math.abs(x) <= 3 || hz - Math.abs(z) <= 3;
                    putR(x, y, z, light(edge ? edgeLevel : level));
                }
            }
        }

        // ---- 肋骨のアーチ (39 の 4.1 の形の 1)。z の断面に、壁から立ち上がって天井で閉じる骨の帯。
        // spring = 足が垂直に立つ高さ、top = 頂の高さ (北へ行くほど低くすると喉の奥へ)、legDepth = 足の太さ (壁から)
        public void ribArch(int z, int spring, int top, int legDepth) {
            double a = hx + 0.5;
            double b = Math.max(1.5, top - spring + 0.5);
            double t = 1.6;
            for (int x = -hx; x <= hx; x++) {
                for (int y = 1; y <= Math.min(top, h); y++) {
                    boolean rib;
                    Direction.Axis axis;
                    if (y <= spring) {
                        rib = hx - Math.abs(x) < legDepth;
                        axis = Direction.Axis.Y;
                    } else {
                        double dy = y - spring;
                        double eo = (x / a) * (x / a) + (dy / b) * (dy / b);
                        double ai = a - t;
                        double bi = b - t;
                        double ei = (x / ai) * (x / ai) + (dy / bi) * (dy / bi);
                        rib = eo <= 1.0 && ei > 1.0;
                        axis = Math.abs(x) / a > dy / b ? Direction.Axis.Y : Direction.Axis.X;
                    }
                    if (rib) {
                        putR(x, y, z, bone(axis));
                    }
                }
            }
            // 肩: アーチの内側の下に、上下逆の階段 (壁の側が厚い)。足もとに見えない灯り (骨が浮いて見える)
            for (int x = -hx; x <= hx; x++) {
                for (int y = spring + 1; y <= Math.min(top, h); y++) {
                    if (get(x, y, z).is(Blocks.BONE_BLOCK) && mine.contains(key(x, y, z)) && y - 1 >= spring && get(x, y - 1, z).isAir()
                            && Math.abs(x) >= hx * 0.45 && Math.abs(x) <= hx * 0.8) {
                        putR(x, y - 1, z, stairTop(x < 0 ? Direction.WEST : Direction.EAST));
                    }
                }
            }
            put(-hx + legDepth, 3, z, light(7));
            put(hx - legDepth, 3, z, light(7));
        }

        // 背骨の棟 (39 の 4.1 の形の 2): 天井の真ん中を南北に、三つおきに椎骨 (骨の十字)
        public void spine(int z0, int z1) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                putR(0, h, z, bone(Direction.Axis.Z));
                if (Math.floorMod(z, 3) == 0) {
                    putR(-1, h, z, bone(Direction.Axis.X));
                    putR(1, h, z, bone(Direction.Axis.X));
                    putR(0, h - 1, z, bone(Direction.Axis.Y));
                }
            }
        }

        // 平面 z の弧の額 (目的の物の後ろの門。39 の 3.5 節): 中心 x = 0、足の高さ spring、横の半径 a、縦の半径 b
        public void frameArch(int z, int spring, double a, double b, BlockState legs, BlockState arc) {
            double t = 1.5;
            for (int x = (int) -Math.ceil(a); x <= Math.ceil(a); x++) {
                for (int y = 1; y <= Math.min(h, spring + (int) Math.ceil(b)); y++) {
                    if (y <= spring) {
                        if (Math.abs(x) >= a - t && Math.abs(x) <= a) {
                            putR(x, y, z, legs);
                        }
                    } else {
                        double dy = y - spring;
                        double eo = (x / a) * (x / a) + (dy / b) * (dy / b);
                        double ei = (x / (a - t)) * (x / (a - t)) + (dy / (b - t)) * (dy / (b - t));
                        if (eo <= 1.0 && ei > 1.0) {
                            putR(x, y, z, arc);
                        }
                    }
                }
            }
        }

        // 歌の脈 (39 の 4.1 の形の 4): 壁の面を斜めに走るアメジストの筋。面 (肉付けの一番内側か殻) を塗る
        public void vein(int side, int u0, int y0, int du, int len) {
            for (int i = 0; i < len; i++) {
                int u = u0 + i * du;
                int y = y0 + i;
                if (y < 1 || y > h || Math.abs(u) > len(this, side)) {
                    break;
                }
                int surf = -1;
                for (int k = 3; k >= 0; k--) {
                    int[] p = wall(side, u, y, k);
                    if (mine.contains(key(p[0], p[1], p[2]))) {
                        surf = k;
                        break;
                    }
                }
                int[] p = wall(side, u, y, surf);
                recolor(p[0], p[1], p[2], i % 4 == 2 ? Blocks.BUDDING_AMETHYST.defaultBlockState() : AMETHYST);
                if (i % 3 == 1) {
                    int[] q = wall(side, u, y, surf + 1);
                    put(q[0], q[1], q[2], cluster(inward(side), 1 + (i % 2)));
                }
            }
        }

        // 遠景の窪み (39 の 3.4 節の 1): 壁の肉を深さ 3 で額にして、奥に縮尺を落とした肋骨の籠。手前に鎖の簾、奥は暗め。
        // 肉付けより先に呼ぶ (窪みの中は reserve になる)
        public void niche(int side, int u, int y0, int w, int hh) {
            int half = w / 2;
            // 額 (骨): 窪みのまわり一周、深さ 0..2
            for (int du = -half - 1; du <= half + 1; du++) {
                for (int dy = -1; dy <= hh; dy++) {
                    boolean ring = Math.abs(du) == half + 1 || dy == -1 || dy == hh;
                    for (int k = 0; k <= 2; k++) {
                        int[] p = wall(side, u + du, y0 + dy, k);
                        if (ring) {
                            Direction.Axis ax = Math.abs(du) == half + 1 ? Direction.Axis.Y : (side < 2 ? Direction.Axis.Z : Direction.Axis.X);
                            putR(p[0], p[1], p[2], k == 2 && dy == -1 ? CALCITE : bone(ax));
                        }
                    }
                }
            }
            // 中: 奥の殻を暗く、奥の面 (深さ 0) に遠い肋骨 (高さが 3 → 2 → 1 と小さくなる)、真ん中に見えない灯り、手前 (深さ 2) に鎖
            for (int du = -half; du <= half; du++) {
                for (int dy = 0; dy < hh; dy++) {
                    int[] s = wall(side, u + du, y0 + dy, -1);
                    paint(s[0], s[1], s[2], DARK);
                }
                int ribH = Math.max(1, Math.min(hh - 1, 3 - (du + half) / 2));
                if (Math.floorMod(du + half, 2) == 0) {
                    for (int dy = 0; dy < ribH; dy++) {
                        int[] p = wall(side, u + du, y0 + dy, 0);
                        putR(p[0], p[1], p[2], dy == ribH - 1 ? Blocks.DIORITE_WALL.defaultBlockState() : bone(Direction.Axis.Y));
                    }
                } else {
                    int[] p = wall(side, u + du, y0, 0);
                    putR(p[0], p[1], p[2], slab());
                }
                if (Math.floorMod(du, 2) == 1) {
                    int[] p = wall(side, u + du, y0 + hh - 1, 2);
                    putR(p[0], p[1], p[2], Blocks.CHAIN.defaultBlockState());
                }
            }
            int[] lp = wall(side, u, y0 + hh / 2, 1);
            putR(lp[0], lp[1], lp[2], light(6));
            // 窪みの中は肉付けしない
            int[] a = wall(side, u - half, y0, 0);
            int[] b = wall(side, u + half, y0 + hh - 1, 2);
            reserve(a[0], a[1], a[2], b[0], b[1], b[2]);
        }

        // ---- 小道具 (39 の 3.8 節・4.1 の小道具)
        // 骨の欠片の山: 骨 1 と方解石の半ブロック。頭を載せることも
        public void bonePile(int x, int z, boolean skull) {
            Direction.Axis ax = rnd.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
            if (!put(x, 1, z, bone(ax))) {
                return;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (rnd.nextInt(3) > 0) {
                    put(x + d.getStepX(), 1, z + d.getStepZ(), slab());
                }
            }
            if (skull) {
                put(x, 2, z, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rnd.nextInt(16)));
            } else if (rnd.nextBoolean()) {
                put(x, 2, z, candle(1 + rnd.nextInt(2), true));
            }
        }

        // 古い祈りのろうそく (床に 2〜3 束)
        public void prayer(int x, int z) {
            put(x, 1, z, candle(3, true));
            put(x + 1, 1, z, candle(1, true));
            put(x, 1, z + 1, candle(2, true));
        }

        // 歌い手の座: 音符のブロックを方解石の段で囲む (向いている側が開く)
        public void singerSeat(int x, int z, Direction facing) {
            put(x, 1, z, Blocks.NOTE_BLOCK.defaultBlockState());
            Direction l = facing.getClockWise();
            put(x + l.getStepX(), 1, z + l.getStepZ(), Blocks.CALCITE.defaultBlockState());
            put(x - l.getStepX(), 1, z - l.getStepZ(), Blocks.CALCITE.defaultBlockState());
            put(x - facing.getStepX(), 1, z - facing.getStepZ(), CALCITE);
            put(x - facing.getStepX(), 2, z - facing.getStepZ(), Blocks.POLISHED_DIORITE_SLAB.defaultBlockState());
            put(x, 2, z, candle(1, true));
        }

        // 石化した小魚: 壁の肉の面に頭を掛ける
        public void wallSkull(int side, int u, int y) {
            for (int k = 3; k >= 0; k--) {
                int[] p = wall(side, u, y, k);
                if (!get(p[0], p[1], p[2]).isAir()) {
                    int[] q = wall(side, u, y, k + 1);
                    put(q[0], q[1], q[2], Blocks.SKELETON_WALL_SKULL.defaultBlockState().setValue(WallSkullBlock.FACING, inward(side)));
                    return;
                }
            }
        }

        // 鯨ひげの簾 (39 の 4.1 の形の 5): 出口の戸の上から垂れる鎖と骨。脇の柱と、越えると灯る燭台 (exitCandles)
        public void exitFrame(int lintelY, boolean posts) {
            int z = -hz;
            if (posts) {
                for (int y = 1; y < lintelY; y++) {
                    putR(-3, y, z, bone(Direction.Axis.Y));
                    putR(3, y, z, bone(Direction.Axis.Y));
                }
            }
            if (lintelY <= h) {
                for (int x = -3; x <= 3; x++) {
                    putR(x, lintelY, z, x == 0 ? AMETHYST : bone(Direction.Axis.X));
                }
                for (int x = -2; x <= 2; x++) {
                    for (int y = lintelY - 1; y >= 4; y--) {
                        putR(x, y, z, Math.floorMod(x, 2) == 0 && y == 4 ? bone(Direction.Axis.Y) : Blocks.CHAIN.defaultBlockState());
                    }
                }
            }
            putR(-3, 1, z + 1, candle(3, false));
            putR(3, 1, z + 1, candle(3, false));
        }

        // 入口の間 (前室。39 の 5 章): 低く暗い、人が後から足した煉瓦の間。碑の脇に「ここで何があったか」(倒れた番人) の場面。
        // 出口の間: 次の部屋を先に見せる差し色 (アメジスト)
        public void antechambers() {
            int ez0 = hz + 2;
            for (int x = -4; x <= 4; x++) {
                for (int z = ez0; z <= ez0 + 5; z++) {
                    for (int y = 0; y <= 5; y++) {
                        BlockState st = y == 0 ? (Math.abs(x) <= 1 ? FLOOR_A : FLOOR_B) : y == 5 ? DARK : (y == 1 ? Blocks.POLISHED_TUFF.defaultBlockState() : TUFF_BRICKS);
                        if (y == 0 && x == 0 && z >= ez0 + 1 && z <= ez0 + 3) {
                            st = bone(Direction.Axis.Z);
                        }
                        paint(x, y, z, st);
                    }
                }
            }
            // 天井の浮いた灯り → 見えない灯り (前室は暗め。床の二つのランタンだけ見える)
            replaceLight(0, 4, ez0 + 2, light(7));
            // 碑の下の台と供えのろうそく (碑は休み場の西 = x -3, y 2)
            setIfAir(-3, 1, ez0 + 2, Blocks.CHISELED_TUFF.defaultBlockState());
            setIfAir(-3, 1, ez0 + 1, candle(2, true));
            setIfAir(-3, 1, ez0 + 3, candle(1, true));
            // 倒れた番人: 寝かせた骨と頭と折れた槍 (鉄の格子と鎖)
            setIfAir(3, 1, ez0, bone(Direction.Axis.Z));
            setIfAir(3, 1, ez0 + 1, bone(Direction.Axis.Z));
            setIfAir(2, 1, ez0, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 5));
            setIfAir(3, 2, ez0 + 1, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            setIfAir(3, 1, ez0 + 2, Blocks.POLISHED_DIORITE_SLAB.defaultBlockState());
            // 出口の間
            int xz1 = -hz - 2;
            for (int x = -3; x <= 3; x++) {
                for (int z = xz1 - 3; z <= xz1; z++) {
                    for (int y = 0; y <= 5; y++) {
                        boolean back = z == xz1 - 3;
                        BlockState st = y == 0 ? FLOOR_A : y == 5 ? CEIL_B : back && x == 0 && y >= 2 && y <= 3 ? AMETHYST : back ? CALCITE : band(x, y, z, 0);
                        paint(x, y, z, st);
                    }
                }
            }
            replaceLight(0, 4, xz1 - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        private void setIfAir(int x, int y, int z, BlockState st) {
            if (!kept(x, y, z) && get(x, y, z).isAir()) {
                RaidKit.set(level, at(x, y, z), st);
                decor++;
            }
        }

        // 終わり: 費用を数える (部屋全体の、明るさ 12 以上の塊・ブロックエンティティ・人でないエンティティ)
        public Stats finish() {
            int bright = 0;
            int be = 0;
            net.minecraft.world.phys.AABB b = cell.bounds();
            for (BlockPos p : BlockPos.betweenClosed((int) b.minX, (int) b.minY, (int) b.minZ, (int) b.maxX - 1, (int) b.maxY - 1, (int) b.maxZ - 1)) {
                BlockState st = level.getBlockState(p);
                if (st.isAir()) {
                    continue;
                }
                if (st.getLightEmission(level, p) >= 12) {
                    bright++;
                }
                if (st.hasBlockEntity()) {
                    be++;
                }
            }
            int ents = 0;
            for (Entity e : level.getEntities((Entity) null, b, e -> !(e instanceof Player))) {
                ents++;
            }
            Stats s = new Stats(decor, bright, be, ents, (System.nanoTime() - t0) / 1_000_000);
            LAST.put(ctx.roomId(), s);
            return s;
        }
    }

    // ================================================================
    // 部屋ごとの飾り。各部屋の build の終わりに呼ぶ (WR6 は床を覚える前)

    // 低い所 (足もと〜 lowTop) は low、上は high の深さまで
    static Kit.Cap caps(int low, int high, int lowTop) {
        return (side, u, y) -> y <= lowTop ? low : high;
    }

    // 歩 WR1 骨玉運び: 目印は北の「耳の骨の椀」(台座の後ろの骨の門と、届けた骨玉ごとに灯る三つの燭台)
    public static final int[][] WR1_CANDLES = {{-5, 5, -14}, {5, 5, -14}, {0, 9, -16}};

    public static void wr1(RaidCtx ctx, List<BlockPos> homes, List<net.minecraft.world.phys.AABB> pillars) {
        if (!enabled) {
            return;
        }
        Kit k = new Kit(ctx);
        int hx = k.hx;
        int hz = k.hz;
        int az = WhaleCarryRoom.ALTAR_Z;
        // 守る: 台座と近づく所・出口への道・骨玉の戻る所・遮りの柱のまわり
        k.keep(-6, 1, az - 1, 6, 4, az + 4);
        k.keep(-2, 1, -hz, 2, 3, az - 1);
        for (BlockPos hp : homes) {
            if (hp != null) {
                int x = hp.getX() - k.o.getX();
                int z = hp.getZ() - k.o.getZ();
                k.keep(x - 1, 1, z - 1, x + 1, 3, z + 1);
            }
        }
        for (net.minecraft.world.phys.AABB p : pillars) {
            k.keep((int) p.minX - 1, 1, (int) p.minZ - 1, (int) p.maxX, 5, (int) p.maxZ);
        }
        // 運ぶ床 (壁ぎわ 2 マスの外) は空ける
        k.reserve(-hx + 2, 1, -hz + 3, hx - 2, 3, hz - 3);
        k.antechambers();
        k.ceiling(9);
        k.floor(hz, az + 2);
        k.fossil(-9, 5, true);
        k.fossil(10, -5, false);
        // 目印: 台座の後ろの門 (骨の足・骨の弧・頂にアメジスト)
        k.frameArch(-hz, 4, 6.5, 4.5, bone(Direction.Axis.Y), bone(Direction.Axis.X));
        k.frameArch(-hz + 1, 4, 6.5, 4.5, CALCITE, Blocks.POLISHED_DIORITE.defaultBlockState());
        k.recolor(0, 8, -hz, AMETHYST);
        k.putR(0, 9, -hz, candle(3, false));
        k.putR(-5, 5, az - 1, candle(3, false));
        k.putR(5, 5, az - 1, candle(3, false));
        // 届け先の灯り (目的の物 = 一番明るい): 門の奥の天井に海のランタン
        k.paint(0, k.h + 1, az, Blocks.SEA_LANTERN.defaultBlockState());
        k.putR(0, 6, az, light(12));
        // 肋骨のアーチ (北へ行くほど低い) と背骨
        k.ribArch(9, 3, 10, 2);
        k.ribArch(3, 3, 10, 2);
        k.ribArch(-3, 3, 9, 2);
        k.ribArch(-9, 3, 9, 2);
        k.spine(-hz + 3, hz - 1);
        // 遠景の窪み (東西の壁)
        k.niche(0, 6, 4, 5, 3);
        k.niche(1, -6, 4, 5, 3);
        k.exitFrame(7, false);
        k.organicWall(caps(2, 3, 3));
        k.vein(0, -14, 1, 1, 9);
        k.vein(1, 12, 2, -1, 8);
        k.vein(2, -12, 1, 1, 7);
        k.vein(2, 12, 1, -1, 7);
        // 小道具: 隅に骨の山・祈りのろうそく・歌い手の座。壁に石化した小魚
        k.bonePile(-13, 14, true);
        k.bonePile(14, 13, false);
        k.prayer(12, 14);
        k.singerSeat(-13, -14, Direction.EAST);
        k.bonePile(13, -14, true);
        k.wallSkull(0, -10, 6);
        k.wallSkull(1, 9, 5);
        k.lightGrid(4, 6, 11, 8);
        k.finish();
    }

    public static BlockPos wr1Candle(RaidCtx ctx, int i) {
        int[] c = WR1_CANDLES[i];
        return ctx.origin().offset(c[0], c[1], c[2]);
    }

    // 智 WR2 共鳴の骨: 四隅の骨をパイプオルガンに (骨の管の束、一番高い管の上に鐘、根元にアメジスト)。床に歌の輪
    public static void wr2(RaidCtx ctx, int[][] corners) {
        if (!enabled) {
            return;
        }
        Kit k = new Kit(ctx);
        int hx = k.hx;
        int hz = k.hz;
        for (int[] c : corners) {
            k.keep(c[0] - 1, 1, c[1] - 1, c[0] + 2, 6, c[1] + 2);
        }
        k.keep(-1, 0, -1, 1, 0, 1);
        k.reserve(-10, 1, -10, 10, 3, 10);
        k.antechambers();
        k.ceiling(9);
        // 床の歌の輪 (方解石の輪、骨へ向かう四つのアメジスト)。中の台 (骨) はそのまま
        k.floor(hz, 2);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double d = Math.hypot(x + 0.5, z + 0.5) - 0.7;
                if (d > 5.2 && d <= 6.2) {
                    k.paint(x, 0, z, CALCITE);
                }
                if (Math.abs(Math.abs(x + 0.5) - Math.abs(z + 0.5)) < 0.6 && d > 2.2 && d < 5.2) {
                    k.paint(x, 0, z, AMETHYST);
                }
            }
        }
        // オルガンの管 (隅の側に 4 本、高さを変える)
        for (int[] c : corners) {
            int sx = c[0] < 0 ? -1 : 1;
            int sz = c[1] < 0 ? -1 : 1;
            int bx = sx < 0 ? c[0] : c[0] + 1;
            int bz = sz < 0 ? c[1] : c[1] + 1;
            int[][] pipes = {{bx + 2 * sx, bz, 6}, {bx, bz + 2 * sz, 6}, {bx + 2 * sx, bz + 2 * sz, 8}, {bx + 3 * sx, bz + sz, 7}, {bx + sx, bz + 3 * sz, 7}};
            for (int[] pp : pipes) {
                k.paint(pp[0], 0, pp[1], AMETHYST);
                for (int y = 1; y <= pp[2]; y++) {
                    k.putR(pp[0], y, pp[1], y == pp[2] ? CALCITE : bone(Direction.Axis.Y));
                }
                if (pp[2] == 8) {
                    k.putR(pp[0], 9, pp[1], Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR)
                            .setValue(BellBlock.FACING, sx < 0 ? Direction.EAST : Direction.WEST));
                }
            }
            int[] g = organBase(c);
            k.putR(g[0], 1, g[1], cluster(Direction.UP, 3));
            k.putR(g[0], 2, g[1], light(0));
        }
        k.ribArch(4, 3, 9, 2);
        k.ribArch(-4, 3, 9, 2);
        k.spine(-hz + 3, hz - 1);
        k.niche(0, 0, 4, 5, 3);
        k.niche(1, 0, 4, 5, 3);
        k.exitFrame(6, false);
        k.organicWall(caps(2, 3, 3));
        k.vein(0, -12, 1, 1, 8);
        k.vein(1, 12, 1, -1, 8);
        k.vein(2, 4, 1, 1, 7);
        k.singerSeat(-5, 12, Direction.NORTH);
        k.prayer(5, 12);
        k.wallSkull(2, -7, 5);
        k.wallSkull(3, 8, 5);
        k.lightGrid(4, 6, 11, 8);
        k.finish();
    }

    // WR2: 鳴っている骨の管の根元 (アメジストの塊の上の見えない灯り) が光る
    static int[] organBase(int[] c) {
        int sx = c[0] < 0 ? -1 : 1;
        int sz = c[1] < 0 ? -1 : 1;
        int bx = sx < 0 ? c[0] : c[0] + 1;
        int bz = sz < 0 ? c[1] : c[1] + 1;
        return new int[] {bx + 2 * sx, bz + sz};
    }

    public static void organGlow(RaidCtx ctx, int[] corner, boolean lit) {
        int[] g = organBase(corner);
        BlockPos p = ctx.origin().offset(g[0], 2, g[1]);
        if (ctx.level().getBlockState(p).is(Blocks.LIGHT)) {
            RaidKit.set(ctx.level(), p, light(lit ? 13 : 0));
        }
    }

    // 闘 WR3 鯨の核を守る: 核を肋骨の籠に入れた晶洞 (籠の柱 6 本、頂にアメジスト、核の上の天井に海のランタン)
    public static void wr3(RaidCtx ctx) {
        if (!enabled) {
            return;
        }
        Kit k = new Kit(ctx);
        int hx = k.hx;
        int hz = k.hz;
        // 守る: 核のまわり半径 3 (空ける)、番兵長の影の出る所
        k.keep(-3, 1, -3, 3, 6, 3);
        k.keep(-1, 1, -hz + 2, 1, 4, -hz + 4);
        k.reserve(-hx + 2, 1, -hz + 2, hx - 2, 3, hz - 2);
        k.antechambers();
        k.ceiling(9);
        k.floor(hz, 4);
        // 床の輪 (半径 9.5、方解石)
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                double d = Math.hypot(x, z);
                if (d > 9.2 && d <= 10.1) {
                    k.paint(x, 0, z, CALCITE);
                }
            }
        }
        // 籠の肋骨 6 本: 半径 5 で立ち、上で内へ曲がって核の上で閉じる
        double[] rr = {5, 5, 5, 5, 4.6, 4.0, 3.1, 2.0, 1.0};
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i + Math.PI / 6;
            for (int y = 1; y <= 9; y++) {
                double r = rr[y - 1];
                int x = (int) Math.round(Math.cos(a) * r);
                int z = (int) Math.round(Math.sin(a) * r);
                k.putR(x, y, z, bone(y >= 6 ? (Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z) : Direction.Axis.Y));
                if (y == 5) {
                    k.putR((int) Math.round(Math.cos(a) * 5.6), y, (int) Math.round(Math.sin(a) * 5.6), cluster(Direction.UP, 2));
                }
            }
        }
        k.putR(0, 10, 0, AMETHYST);
        k.putR(0, 9, 0, AMETHYST);
        k.putR(0, 8, 0, cluster(Direction.DOWN, 3));
        k.putR(0, 6, 0, cluster(Direction.UP, 3));
        for (int y = 9; y <= 10; y++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                k.putR(d.getStepX(), y, d.getStepZ(), y == 10 ? AMETHYST : cluster(d, 2));
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                k.paint(x, k.h + 1, z, x == 0 && z == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : AMETHYST);
            }
        }
        // 外の低い骨の柱 (r = 7) の上に祈りのろうそく
        for (int i = 0; i < 8; i += 2) {
            double a = Math.PI / 4 * i;
            k.putR((int) Math.round(Math.cos(a) * 7), 3, (int) Math.round(Math.sin(a) * 7), candle(2, true));
        }
        k.ribArch(12, 3, 11, 2);
        k.ribArch(6, 3, 11, 2);
        k.ribArch(-6, 3, 10, 2);
        k.ribArch(-12, 3, 10, 2);
        k.spine(3, hz - 1);
        k.spine(-hz + 3, -3);
        k.niche(0, 0, 4, 5, 4);
        k.niche(1, 0, 4, 5, 4);
        k.exitFrame(7, false);
        k.organicWall(caps(2, 3, 3));
        k.vein(0, -14, 1, 1, 10);
        k.vein(1, 14, 1, -1, 10);
        k.vein(2, -10, 1, 1, 8);
        k.vein(3, 10, 2, -1, 8);
        k.bonePile(-14, 14, true);
        k.bonePile(14, 14, false);
        k.bonePile(-14, -14, false);
        k.prayer(13, -14);
        k.wallSkull(0, 9, 6);
        k.wallSkull(1, -9, 6);
        k.lightGrid(4, 6, 11, 8);
        k.finish();
    }

    // 歩 WR4 胃の縦穴: 縦穴の殻を高さで濃淡 (底が深層岩、頂が方解石)、踊り場の高さに肋骨の輪の帯、頂に「喉の光」。
    // 中の空気には登る足がかりになる物を置かない (見えない灯りと、頂より上に垂れる骨だけ)
    public static void wr4(RaidCtx ctx, WhaleShaftRoom room) {
        if (!enabled) {
            return;
        }
        Kit k = new Kit(ctx);
        int hx = k.hx;
        int h = k.h;
        // 縦穴の中 (頂の上の 3 段まで) はすべて守る
        k.keep(-hx, 1, -k.hz, hx, WhaleShaftRoom.TOP + 3, k.hz);
        k.antechambers();
        k.ceiling(0);
        k.floor(k.hz, 4);
        // 殻: 濃淡 + 肋骨の輪 (踊り場の高さ) + 縦の肋骨 (天井の肋骨の続き) + 歌の脈
        for (int side = 0; side < 4; side++) {
            int l = Kit.len(k, side);
            for (int u = -l; u <= l; u++) {
                for (int y = 1; y <= h; y++) {
                    int[] s = k.wall(side, u, y, -1);
                    double v = k.wallNoise(side, u, y);
                    BlockState st = k.band(s[0], y, s[2], v);
                    // 輪は踊り場の高さで、壁に沿って一段ゆれる。縦の肋骨は高さとともに左右へ曲がる (格子に見せない)
                    int wob = (int) Math.round(k.n(u * 0.3 + side * 5.1, y / WhaleShaftRoom.RISE * 3.7, 1.5) * 1.4);
                    boolean ring = Math.floorMod(y - wob, WhaleShaftRoom.RISE) == 0;
                    int bend = (int) Math.round(Math.sin(y * 0.22 + side) * 1.3);
                    boolean rib = side < 2 && (u - bend == -4 || u - bend == 0 || u - bend == 4) || side >= 2 && (u - bend == -4 || u - bend == 4);
                    if (ring) {
                        st = bone(side < 2 ? Direction.Axis.Z : Direction.Axis.X);
                    } else if (rib) {
                        st = bone(Direction.Axis.Y);
                    } else if (Math.floorMod(u * 2 + y, 17) == 0 && (u + y) % 2 == 0) {
                        st = AMETHYST;
                    }
                    k.paint(s[0], s[1], s[2], st);
                }
            }
        }
        // 頂の喉の光 (天井の真ん中に海のランタン、まわりにアメジスト)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                k.paint(x, h + 1, z, x == 0 && z == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : AMETHYST);
            }
        }
        // 頂より上に垂れる骨 (肋骨の間の z = ±2)
        for (int x : new int[] {-3, 0, 3}) {
            for (int z : new int[] {-2, 2}) {
                int len = 2 + Math.floorMod(x + z * 3, 2);
                for (int i = 0; i < len; i++) {
                    k.putR(x, h - i, z, bone(Direction.Axis.Y));
                }
                k.putR(x, h - len, z, cluster(Direction.DOWN, 2));
            }
        }
        k.putR(0, h - 1, 0, light(12));
        // 出口の燭台 (床。登る足場にならない低さ)
        for (BlockPos p : exitCandles(ctx)) {
            RaidKit.set(k.level, p, candle(3, false));
            k.decor++;
        }
        // 底の灯りの見えないマス (縦穴の真ん中の柱。登る足場にならない)
        for (int y = 3; y <= WhaleShaftRoom.TOP; y += WhaleShaftRoom.RISE) {
            setLight(k, 0, y, 0, 11);
            setLight(k, 4, y + 2, -1, 8);
            setLight(k, -4, y + 2, 1, 8);
        }
        k.finish();
    }

    // 守る箱の中だが、見えない灯り (当たりなし・踏めない) だけは仕掛けのない空気に置いてよい。段・踊り場のマスは避ける
    private static void setLight(Kit k, int x, int y, int z, int level) {
        if (k.inHall(x, y, z) && k.get(x, y, z).isAir() && !k.get(x, y - 1, z).isSolid()) {
            RaidKit.set(k.level, k.at(x, y, z), light(level));
            k.decor++;
        }
    }

    // 智 WR5 床合わせ: 北の壁の見本を、壁に埋まった化石の鯨の額に (骨の柱と弧)。東西の壁は肋骨の列。見張り台の下に骨の持ち送り
    public static void wr5(RaidCtx ctx) {
        if (!enabled) {
            return;
        }
        Kit k = new Kit(ctx);
        int hx = k.hx;
        int hz = k.hz;
        int h = k.h;
        int bal = WhaleTilesRoom.BALCONY_Y;
        // 守る: 床の石板・見本とその前・見張り台と階段と柱
        k.keep(-hx + 1, 0, -7, hx - 1, 0, 7);
        k.keep(-hx + 1, 5, -hz, hx - 1, WhaleTilesRoom.MURAL_TOP + 1, -hz);
        k.keep(-hx + 1, bal, 8, hx - 1, bal + 3, hz);
        k.keep(hx - 5, 1, 7, hx - 1, bal + 2, 8);
        // 見張り台から見本と床が見える (真ん中の空気は空ける)
        k.reserve(-hx + 2, 1, -hz + 1, hx - 2, h - 1, 7);
        k.antechambers();
        k.ceiling(10);
        k.floor(hz, 8);
        // 化石の額: 見本の左右に骨の柱、上に骨の弧
        for (int y = 1; y <= h - 1; y++) {
            k.putR(-hx, y, -hz, bone(Direction.Axis.Y));
            k.putR(hx, y, -hz, bone(Direction.Axis.Y));
        }
        for (int x = -hx; x <= hx; x++) {
            k.putR(x, h, -hz, x == 0 ? AMETHYST : bone(Direction.Axis.X));
            if (Math.floorMod(x, 3) == 0) {
                k.putR(x, h - 1, -hz + 1, bone(Direction.Axis.Y));
            }
        }
        // 東西の壁の肋骨 (天井の肋骨 z = -8, -2, 4 の続きを床まで)
        for (int z : new int[] {-8, -2, 4}) {
            for (int y = 1; y <= h; y++) {
                k.putR(-hx, y, z, bone(Direction.Axis.Y));
                k.putR(hx, y, z, bone(Direction.Axis.Y));
                if (y >= 6) {
                    k.putR(-hx + 1, y, z, y % 4 == 0 ? CALCITE : bone(Direction.Axis.Y));
                    k.putR(hx - 1, y, z, y % 4 == 0 ? CALCITE : bone(Direction.Axis.Y));
                }
            }
        }
        // 見張り台の下の骨の持ち送り (上下逆の階段。入口の道の上は空ける)
        for (int x = -hx + 6; x <= hx - 6; x++) {
            if (Math.abs(x) > 2 && Math.abs(x) <= hx - 6) {
                k.putR(x, bal - 1, 9, stairTop(Direction.SOUTH));
            }
        }
        k.spine(-hz + 2, hz - 1);
        // 見本を照らす見えない灯り (見本の前)
        for (int y : new int[] {8, 13, 18}) {
            for (int x : new int[] {-6, 0, 6}) {
                k.putR(x, y, -hz + 2, light(13));
            }
        }
        k.exitFrame(4, false);
        k.organicWall((side, u, y) -> {
            if (side >= 2) {
                return side == 2 ? (y <= 4 ? 2 : 0) : (y >= bal + 4 ? 2 : 0);
            }
            return y <= 5 || u <= -hz + 2 ? 1 : 2;
        });
        k.vein(0, -6, 6, 1, 12);
        k.vein(1, 6, 6, -1, 12);
        k.vein(0, 5, 8, 1, 6);
        k.bonePile(-9, -10, true);
        k.prayer(8, -10);
        k.wallSkull(0, 1, 9);
        k.wallSkull(1, -5, 9);
        k.lightGrid(14, 7, 10, 9);
        k.lightGrid(4, 6, 11, 9);
        for (int x : new int[] {-8, 0, 8}) {
            k.putR(x, 3, -9, light(9));
        }
        k.finish();
    }

    // 闘 WR6 縮む輪: 輪の外の隅 (いつも赤い床の外) に骨の山と祈り、床に輪の段の印。外周の椎骨の環はそのまま
    public static void wr6(RaidCtx ctx) {
        if (!enabled) {
            return;
        }
        Kit k = new Kit(ctx);
        int hx = k.hx;
        int hz = k.hz;
        k.keep(-3, 0, -3, 3, 0, 3);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI / 6 * i;
            int x = (int) Math.round(Math.cos(a) * 13.5);
            int z = (int) Math.round(Math.sin(a) * 13.5);
            k.keep(x, 1, z, x, 4, z);
        }
        // 輪の中の床の上 (戦う所) は空ける
        k.reserveRound(14.8, 1, 3);
        k.antechambers();
        k.ceiling(9);
        k.floor(hz, 4);
        // 輪の段の印 (半径 9・6 の方解石の細い輪)。予告の赤はこの上に塗られる
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                double d = Math.hypot(x, z);
                if (Math.abs(d - 9) < 0.45 || Math.abs(d - 6) < 0.45) {
                    k.paint(x, 0, z, CALCITE);
                }
            }
        }
        k.ribArch(9, 3, 11, 2);
        k.ribArch(3, 3, 11, 2);
        k.ribArch(-3, 3, 10, 2);
        k.ribArch(-9, 3, 10, 2);
        k.spine(-hz + 3, hz - 1);
        k.niche(0, 7, 4, 5, 3);
        k.niche(1, -7, 4, 5, 3);
        k.exitFrame(7, false);
        k.organicWall(caps(2, 3, 3));
        k.vein(0, -13, 1, 1, 9);
        k.vein(1, 13, 1, -1, 9);
        k.vein(3, -9, 2, 1, 7);
        k.bonePile(-14, 13, true);
        k.bonePile(13, 14, false);
        k.bonePile(14, -13, true);
        k.prayer(-14, -13);
        k.wallSkull(0, -6, 6);
        k.wallSkull(1, 4, 6);
        k.lightGrid(4, 6, 11, 8);
        k.finish();
    }

    // 中ボス WRM 番兵長の間: 狭い喉の回廊。四つおきの肋骨のアーチ (北へ低く)、奥の壁に番兵長の額 (骨の門・頭・アメジスト)
    public static void wrm(RaidCtx ctx) {
        if (!enabled) {
            return;
        }
        Kit k = new Kit(ctx);
        int hx = k.hx;
        int hz = k.hz;
        // 守る: 休み場・番兵長の居場所・前触れの波の出る所
        k.keep(-1, 1, hz - 3, 1, 4, hz - 1);
        k.keep(-2, 1, -hz + 3, 2, 5, -hz + 7);
        k.reserve(-hx + 1, 1, -hz + 1, hx - 1, 3, hz - 1);
        k.antechambers();
        k.ceiling(9);
        k.floor(hz - 2, -hz + 3);
        int[] zs = {14, 10, 6, 2, -2, -6, -10, -14};
        int[] tops = {11, 11, 10, 10, 10, 9, 9, 9};
        for (int i = 0; i < zs.length; i++) {
            k.ribArch(zs[i], 4, tops[i], 1);
        }
        k.spine(-hz + 1, hz - 1);
        // 奥の額: 出口の戸の上に骨の弧、頂に頭、両脇にアメジスト
        k.frameArch(-hz, 4, 5.5, 4.5, bone(Direction.Axis.Y), bone(Direction.Axis.X));
        k.putR(0, 7, -hz, Blocks.SKELETON_WALL_SKULL.defaultBlockState().setValue(WallSkullBlock.FACING, Direction.SOUTH));
        k.putR(-1, 7, -hz, AMETHYST);
        k.putR(1, 7, -hz, AMETHYST);
        k.paint(0, k.h + 1, -hz + 5, Blocks.SEA_LANTERN.defaultBlockState());
        k.exitFrame(6, false);
        k.organicWall((side, u, y) -> side >= 2 ? (y <= 3 ? 1 : 2) : (y <= 3 ? 1 : 2));
        k.vein(0, -12, 2, 1, 8);
        k.vein(1, 12, 2, -1, 8);
        k.vein(0, 4, 3, 1, 6);
        for (int z : new int[] {-12, -4, 4, 12}) {
            k.wallSkull(z < 0 ? 0 : 1, z, 6);
        }
        k.prayer(-5, hz - 1);
        k.prayer(4, hz - 1);
        for (int z = -hz + 2; z <= hz - 2; z += 5) {
            k.putR(0, 5, z, light(10));
            k.putR(-hx + 2, 4, z, light(7));
            k.putR(hx - 2, 4, z, light(7));
        }
        k.finish();
    }
}
