package jp.revati.skyabyss.trial.raid.ember;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.AABB;

// 余燼のレイドの部屋の飾り (docs/design/39 の 4.4「余燼（玄武岩・炉・熾）」)。RaidKit.buildCell の殻の上に、余燼だけの見た目を足す道具。
// 部屋 (ER1〜ER6・中ボスの間 ERM) の build から次の順で呼ぶ:
//   EmberRaidDecor d = EmberRaidDecor.of(ctx);   buildCell のすぐあと
//   d.shell(true);                                殻の色を塗り直す (床・高さの帯の壁・煤けた天井。天井の格子の灯りは消して見えない灯りへ)
//   ... 部屋の仕掛け (元のまま) ...
//   d.keep(...);                                  動線・湧き所・仕掛けの前など、飾りを置かない箱
//   d.relief(); d.veins(2); d.chains(...); d.entry(...); d.exitRoom(); d.lights(...); d.finish();   仕掛けのあとに、空いている所だけへ
// 決まり: 仕掛けのブロックは塗り替えない (殻の色塗りは buildCell の材だけ、あとの足しは空気の所だけ)。殻の外の一枚は必ず残す。溶岩・水・落ちるブロックは使わない。
// 負荷の上限 (39 の 6 章): 飾り 8 千ブロック・明るさ 12 以上 40 個・ブロックエンティティ 24 個・飾りのエンティティ 16 体 (audit で数える)
public final class EmberRaidDecor {
    // ---- 色 (39 の 4.4 の表。下 → 上)
    static final BlockState FLOOR = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    static final BlockState FLOOR_MIX = Blocks.POLISHED_BASALT.defaultBlockState();
    static final BlockState EDGE = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    static final BlockState GILD = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    static final BlockState CEIL = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState CEIL_MIX = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
    static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    static final BlockState P_BASALT = Blocks.POLISHED_BASALT.defaultBlockState();
    static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    static final BlockState CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    static final BlockState IRON_BARS = Blocks.IRON_BARS.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    // 壁の帯 (主, 混ぜ, 混ぜ 2)。足元 (熱い) → 天井際 (灰)
    private static final BlockState[][] BANDS = {
            {Blocks.BLACKSTONE.defaultBlockState(), GILD, GILD},
            {BASALT, P_BASALT, P_BASALT},
            {Blocks.SMOOTH_BASALT.defaultBlockState(), BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()},
            {Blocks.TUFF.defaultBlockState(), Blocks.POLISHED_TUFF.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()}};

    // 負荷の上限
    public static final int MAX_DECO = 8000;
    public static final int MAX_BRIGHT = 40;
    public static final int MAX_BLOCK_ENTITIES = 24;
    public static final int MAX_ENTITIES = 16;
    // 飾りとして置く表示のエンティティに付ける印 (いまは置いていない)
    public static final String DECOR_TAG = "skyabyss_ember_decor";

    final RaidCtx ctx;
    final ServerLevel lv;
    final RaidKit.Cell c;
    final BlockPos o;
    final int hx;
    final int hz;
    final int h;
    private final ImprovedNoise noise;
    private final RandomSource rnd;
    // 飾りを置かない箱 (原点からの x, dy, z。両端を含む)
    private final List<int[]> keep = new ArrayList<>();
    // この道具が置いた・塗った所 (筋を描いてよい所)
    private final Set<Long> mine = new HashSet<>();
    private final List<BlockPos> bars = new ArrayList<>();
    private int placed;

    private EmberRaidDecor(RaidCtx ctx) {
        this.ctx = ctx;
        this.lv = ctx.level();
        this.c = ctx.cell();
        this.o = c.origin();
        this.hx = c.hx();
        this.hz = c.hz();
        this.h = c.height();
        // 種は部屋の形で固定 (挑戦ごとに変えない。形を覚えられるのは遊びの助け)
        long seed = 0x5EED_E3B3L ^ ((long) ctx.roomId().hashCode() << 16) ^ (hx * 131L + hz * 17L + h);
        this.rnd = RandomSource.create(seed);
        this.noise = new ImprovedNoise(RandomSource.create(seed * 31 + 7));
        // 入口・出口の戸の前は空ける (戸の幅 3 の両脇 1 と、高さ 5)
        keep(-2, 0, hz - 2, 2, 5, hz);
        keep(-2, 0, -hz, 2, 5, -hz + 2);
    }

    public static EmberRaidDecor of(RaidCtx ctx) {
        return new EmberRaidDecor(ctx);
    }

    // ---------------------------------------------------------------- 置き方
    BlockPos at(int x, int dy, int z) {
        return o.offset(x, dy, z);
    }

    public EmberRaidDecor keep(int x0, int dy0, int z0, int x1, int dy1, int z1) {
        keep.add(new int[] {Math.min(x0, x1), Math.min(dy0, dy1), Math.min(z0, z1), Math.max(x0, x1), Math.max(dy0, dy1), Math.max(z0, z1)});
        return this;
    }

    // 円 (半径 r) を囲む箱を空ける
    public EmberRaidDecor keepAround(int x, int z, int r, int dy1) {
        return keep(x - r, 0, z - r, x + r, dy1, z + r);
    }

    boolean kept(int x, int dy, int z) {
        for (int[] k : keep) {
            if (x >= k[0] && x <= k[3] && dy >= k[1] && dy <= k[4] && z >= k[2] && z <= k[5]) {
                return true;
            }
        }
        return false;
    }

    // 空いている (空気か、この道具の見えない灯り) で、空ける箱の外か。部屋の殻の中だけ
    boolean free(int x, int dy, int z) {
        if (kept(x, dy, z) || !inBounds(at(x, dy, z))) {
            return false;
        }
        BlockState st = lv.getBlockState(at(x, dy, z));
        return st.isAir() || st.is(Blocks.LIGHT) && mine.contains(at(x, dy, z).asLong());
    }

    private boolean inBounds(BlockPos p) {
        AABB b = c.bounds();
        // 殻の外の一枚には置かない
        return p.getX() > b.minX && p.getX() < b.maxX - 1 && p.getY() > b.minY && p.getY() < b.maxY - 1 && p.getZ() > b.minZ && p.getZ() < b.maxZ - 1;
    }

    void put(BlockPos p, BlockState st) {
        lv.setBlock(p, st, Block.UPDATE_CLIENTS);
        placed++;
        mine.add(p.asLong());
        if (st.getBlock() instanceof IronBarsBlock) {
            bars.add(p.immutable());
        }
    }

    void put(int x, int dy, int z, BlockState st) {
        put(at(x, dy, z), st);
    }

    // 空いていれば置く。置いたか
    boolean place(int x, int dy, int z, BlockState st) {
        if (!free(x, dy, z)) {
            return false;
        }
        put(x, dy, z, st);
        return true;
    }

    // この道具が塗った所なら塗り替える
    boolean repaint(int x, int dy, int z, BlockState st) {
        BlockPos p = at(x, dy, z);
        if (!mine.contains(p.asLong())) {
            return false;
        }
        put(p, st);
        return true;
    }

    boolean isMine(int x, int dy, int z) {
        return mine.contains(at(x, dy, z).asLong());
    }

    double n2(double a, double b) {
        return noise.noise(a, b, 0.0) + 0.5 * noise.noise(a * 2.7, b * 2.7, 7.0);
    }

    static BlockState axis(BlockState st, Direction.Axis a) {
        return st.hasProperty(RotatedPillarBlock.AXIS) ? st.setValue(RotatedPillarBlock.AXIS, a) : st;
    }

    static BlockState light(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    static BlockState candles(int n) {
        return Blocks.ORANGE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
    }

    static BlockState slab(BlockState slab, boolean top) {
        return slab.setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    static BlockState anvil(Direction facing) {
        return Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, facing);
    }

    // 高さ dy の壁の帯の材 (境はノイズでぼかす。帯の中で 7:3 で混ぜる)
    BlockState band(int dy, double v, int x, int z) {
        double t = (dy - 1 + v * 1.2) / Math.max(1.0, h - 1) * 4.0;
        int b = Math.max(0, Math.min(3, (int) Math.floor(t)));
        int r = Math.floorMod((x * 73428767) ^ (z * 912931) ^ (dy * 19349663), 100);
        BlockState[] row = BANDS[b];
        if (b == 0) {
            // 金の筋は少しだけ (足元の熱い所)
            return r < 7 ? row[1] : row[0];
        }
        if (b == 2) {
            return r < 18 ? row[1] : r < 28 ? row[2] : row[0];
        }
        return r < 30 ? (r < 18 ? row[1] : row[2]) : axis(row[0], Direction.Axis.Y);
    }

    BlockState floorAt(int x, int z) {
        int r = Math.floorMod((x * 31) ^ (z * 1103) ^ (x * z * 7), 100);
        double v = noise.noise(x * 0.21, 3.3, z * 0.21);
        return r < 14 + (int) (v * 14) ? axis(FLOOR_MIX, Direction.Axis.Y) : FLOOR;
    }

    // ---------------------------------------------------------------- 1. 殻の色 (仕掛けより前に呼ぶ)
    // floor = 広間の床も塗る (中ボスの間は床の模様が元の間のものなので false)
    public void shell(boolean floor) {
        AABB b = c.bounds();
        int fy = o.getY();
        RaidKit.Palette pal = c.palette();
        // 入口の間の天井の灯り → 見えない灯り (暗い前室)。出口の間の灯り → 吊るしたランタン (次の部屋の合図)
        BlockPos entryLight = c.rest().above(3);
        if (lv.getBlockState(entryLight).is(pal.light().getBlock())) {
            put(entryLight, light(7));
        }
        BlockPos exitLight = c.exitSpot().above(3);
        if (lv.getBlockState(exitLight).is(pal.light().getBlock())) {
            put(exitLight, lantern(true));
        }
        for (int x = (int) b.minX; x < b.maxX; x++) {
            for (int z = (int) b.minZ; z < b.maxZ; z++) {
                for (int y = (int) b.minY; y < b.maxY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = lv.getBlockState(p);
                    if (st.isAir() || mine.contains(p.asLong())) {
                        continue;
                    }
                    boolean airUp = y + 1 < b.maxY && lv.getBlockState(p.above()).isAir();
                    boolean airDown = y - 1 >= b.minY && lv.getBlockState(p.below()).isAir();
                    boolean exposed = airUp || airDown;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos q = p.relative(d);
                        if (q.getX() >= b.minX && q.getX() < b.maxX && q.getZ() >= b.minZ && q.getZ() < b.maxZ && lv.getBlockState(q).isAir()) {
                            exposed = true;
                        }
                    }
                    if (!exposed) {
                        continue;
                    }
                    int lx = x - o.getX();
                    int lz = z - o.getZ();
                    int dy = y - fy;
                    if (airUp && dy == 0) {
                        if (!floor && c.inArena(x + 0.5, z + 0.5)) {
                            continue;
                        }
                        if (st.is(Blocks.BLACKSTONE) || st.is(Blocks.POLISHED_BLACKSTONE_BRICKS)) {
                            boolean edge = Math.abs(lx) == hx || Math.abs(lz) == hz;
                            put(p, edge && c.inArena(x + 0.5, z + 0.5) ? EDGE : floorAt(lx, lz));
                        }
                        continue;
                    }
                    if (airDown && !airUp) {
                        // 天井 (広間の殻の天井の灯りもここで消える)
                        if (st.is(Blocks.BLACKSTONE) || st.is(Blocks.POLISHED_BLACKSTONE) || dy == h + 1 && st.is(pal.light().getBlock())) {
                            int r = Math.floorMod(lx * 7919 ^ lz * 104729, 100);
                            put(p, r < 28 ? CEIL_MIX : CEIL);
                        }
                        continue;
                    }
                    if (dy >= 1 && (st.is(Blocks.POLISHED_BLACKSTONE) || st.is(Blocks.BLACKSTONE))) {
                        double v = n2((lx + lz) * 0.18, dy * 0.25);
                        put(p, band(dy, v, lx, lz));
                    }
                }
            }
        }
    }

    // 床の金の筋 (入口から目的の物へ。39 の 3.5「導線」・4.4「金めっきの黒石を目的の物へ向かう線に」)。z0 → z1 を x の列で
    public void goldLine(int x, int z0, int z1) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            if (isMine(x, 0, z) && lv.getBlockState(at(x, 1, z)).isAir()) {
                put(x, 0, z, (z & 1) == 0 ? GILD : CHISELED);
            }
        }
    }

    // 床の輪 (円の線を磨いた黒石の煉瓦で)
    public void floorRing(int cx, int cz, double r, BlockState st) {
        int ri = (int) Math.ceil(r) + 1;
        for (int x = cx - ri; x <= cx + ri; x++) {
            for (int z = cz - ri; z <= cz + ri; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (Math.abs(d - r) < 0.5 && isMine(x, 0, z)) {
                    put(x, 0, z, st);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 2. 有機の壁 (玄武岩の柱の列・流れの溝・天井の際)
    public void relief() {
        relief(0);
    }

    // 壁の内側 1〜2 列に、高さの違う玄武岩の柱 (39 の 4.4 の 1) と、鉄格子の下に熾の光る溝 (4.4 の 2)、天井の際の肉付け
    // dyMin = これより下には置かない (柱の根の高さ)
    public void relief(int dyMin) {
        int side = 0;
        for (int[] s : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            side++;
            boolean alongX = s[1] != 0;
            int len = alongX ? hx : hz;
            for (int u = -len; u <= len; u++) {
                int x1 = alongX ? u : s[0] * hx;
                int z1 = alongX ? s[1] * hz : u;
                int x2 = alongX ? u : s[0] * (hx - 1);
                int z2 = alongX ? s[1] * (hz - 1) : u;
                double v = n2(u * 0.19 + side * 11.3, side * 3.1);
                int top = Math.max(0, Math.min(h - 1, (int) Math.round(h * 0.42 + v * h * 0.55)));
                if (v < -0.32) {
                    // 溝: 床の縁を熾 (マグマ) にして鉄格子で蓋をし、上に見えない灯り (床から上へ照らす)
                    if (dyMin <= 1 && free(x1, 1, z1) && isMine(x1, 0, z1) && lv.getBlockState(at(x1, 0, z1)).is(Blocks.POLISHED_BLACKSTONE_BRICKS) && free(x1, 2, z1)) {
                        put(x1, 0, z1, MAGMA);
                        put(x1, 1, z1, IRON_BARS);
                        place(x1, 2, z1, light(8));
                    }
                } else if (top >= 2) {
                    int y = Math.max(1, dyMin);
                    int last = -1;
                    for (; y <= top; y++) {
                        if (!free(x1, y, z1)) {
                            break;
                        }
                        last = y;
                    }
                    if (last >= Math.max(1, dyMin) + 1) {
                        for (int yy = Math.max(1, dyMin); yy <= last; yy++) {
                            BlockState st = yy == last ? axis(P_BASALT, Direction.Axis.Y) : yy == Math.max(1, dyMin) ? BRICKS : axis(BASALT, Direction.Axis.Y);
                            put(x1, yy, z1, st);
                        }
                        // 2 列め: 人の目より低い柱の頭 (六角の目を上から見せる)
                        if (v > 0.18 && last >= 4) {
                            int stub = v > 0.42 ? 2 : 1;
                            int base = Math.max(1, dyMin);
                            boolean ok = true;
                            for (int yy = base; yy < base + stub; yy++) {
                                ok &= free(x2, yy, z2);
                            }
                            if (ok) {
                                for (int yy = base; yy < base + stub; yy++) {
                                    put(x2, yy, z2, yy == base + stub - 1 ? axis(P_BASALT, Direction.Axis.Y) : axis(BASALT, Direction.Axis.Y));
                                }
                            }
                        }
                    }
                }
                // 天井の際 (角を丸める): 上の一段は壁ぞいに一周、二段めはノイズで
                if (h >= 6) {
                    place(x1, h, z1, band(h, v, x1, z1));
                    if (v > -0.05) {
                        place(x1, h - 1, z1, band(h - 1, v, x1, z1));
                    }
                    if (v > 0.35) {
                        place(x2, h, z2, band(h, v, x2, z2));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 3. 冷えた血の筋 (泣く黒曜石と黒曜石が壁を斜めに走る。4.4 の 5)
    public void veins(int count) {
        for (int i = 0; i < count; i++) {
            // 南の壁は入口の間との仕切り (一枚) で、筋が入口の側にも出るので、北・西・東だけ
            int sideIdx = new int[] {0, 2, 3}[rnd.nextInt(3)];
            int[] s = new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}[sideIdx];
            boolean alongX = s[1] != 0;
            int len = alongX ? hx : hz;
            int u = rnd.nextInt(2 * len - 5) - len + 3;
            int dir = rnd.nextBoolean() ? 1 : -1;
            for (int k = 0; k < h; k++) {
                int dy = h - k;
                int uu = u + dir * (k / 2) + (k % 3 == 2 ? dir : 0);
                if (Math.abs(uu) > len) {
                    break;
                }
                // 見えている面: 内側の列から外へ、最初の固い所 (この道具が置いた・塗った所だけ)
                for (int depth = 2; depth >= 0; depth--) {
                    int x = alongX ? uu : s[0] * (hx + 1 - depth);
                    int z = alongX ? s[1] * (hz + 1 - depth) : uu;
                    BlockState st = lv.getBlockState(at(x, dy, z));
                    if (st.isAir() || st.is(Blocks.LIGHT)) {
                        continue;
                    }
                    if (isMine(x, dy, z) && !st.is(Blocks.IRON_BARS) && !st.is(Blocks.MAGMA_BLOCK)) {
                        put(x, dy, z, k % 3 == 1 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
                    }
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 4. 吊り鎖と鉤 (天井から長さの違う鎖、先にランタンか鉤の重り。4.4 の 3。金床は落ちるブロックなので床の上にだけ置く)
    // n 本。先の高さは minDy 以上 (人の頭と跳びの上)。lanterns = そのうちランタンにする数。中心 (cx, cz) から半径 avoidR の中には吊らない
    public int chains(int n, int minDy, int lanterns, int cx, int cz, double avoidR) {
        List<int[]> done = new ArrayList<>();
        int made = 0;
        int lit = 0;
        for (int tries = 0; tries < n * 12 && made < n; tries++) {
            int x = rnd.nextInt(2 * hx - 3) - hx + 2;
            int z = rnd.nextInt(2 * hz - 3) - hz + 2;
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) < avoidR * avoidR) {
                continue;
            }
            boolean near = false;
            for (int[] d : done) {
                near |= Math.abs(d[0] - x) < 3 && Math.abs(d[1] - z) < 3;
            }
            if (near) {
                continue;
            }
            int maxLen = h - minDy;
            if (maxLen < 2) {
                return made;
            }
            int len = 2 + rnd.nextInt(Math.max(1, maxLen - 1));
            int bottom = h - len + 1;
            boolean ok = true;
            for (int y = bottom - 1; y <= h; y++) {
                ok &= free(x, y, z);
            }
            if (!ok || bottom - 1 < minDy) {
                continue;
            }
            for (int y = bottom; y <= h; y++) {
                put(x, y, z, CHAIN);
            }
            boolean lamp = lit < lanterns;
            // 先: ランタンか、鉤の重り (重い芯)・吊った砥石・鉄格子。金床は落ちるので吊らない (39 の 6.3)
            int pick = rnd.nextInt(3);
            put(x, bottom - 1, z, lamp ? lantern(true) : pick == 0 ? IRON_BARS : pick == 1 ? Blocks.HEAVY_CORE.defaultBlockState()
                    : Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE,
                            net.minecraft.world.level.block.state.properties.AttachFace.CEILING).setValue(net.minecraft.world.level.block.GrindstoneBlock.FACING,
                                    rnd.nextBoolean() ? Direction.NORTH : Direction.EAST));
            if (lamp) {
                lit++;
            }
            done.add(new int[] {x, z});
            made++;
        }
        return made;
    }

    // 一本の吊り鎖 (位置を決めて)。先 = end (null なら鎖だけ)。鎖の下端の dy を返す (置けなければ -1)
    public int chainAt(int x, int z, int bottom, BlockState end) {
        for (int y = bottom - (end == null ? 0 : 1); y <= h; y++) {
            if (!free(x, y, z)) {
                return -1;
            }
        }
        for (int y = bottom; y <= h; y++) {
            put(x, y, z, CHAIN);
        }
        if (end != null) {
            put(x, bottom - 1, z, end);
        }
        return bottom;
    }

    // ---------------------------------------------------------------- 5. 炉の口 (目的の物の後ろの壁の大きなアーチ。4.4 の 4)
    // 北の壁ぎわの列 (z = -hz) に立てる。開口の半幅 a (戸の幅 3 より広く)、頭の高さ top。奥 (殻の壁) は熾の色 (マグマ・ところどころシュルームライト)
    public void forgeMouth(int cx, int a, int top, int glow) {
        int z = -hz;
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 1; k <= 2; k++) {
                int x = cx + side * (a + k);
                for (int dy = 1; dy <= top; dy++) {
                    place(x, dy, z, dy == 1 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : dy == top ? CHISELED : k == 2 && dy % 3 == 0 ? CHISELED : BRICKS);
                }
            }
            // 肩 (開口の上の角を一段ずつ狭める)
            place(cx + side * a, top - 1, z, BRICKS);
            place(cx + side * a, top, z, BRICKS);
            place(cx + side * (a - 1), top, z, BRICKS);
        }
        for (int x = cx - a + 2; x <= cx + a - 2; x++) {
            place(x, top, z, x == cx ? GILD : BRICKS);
        }
        // 奥の壁: 熾の色。戸 (x ±1, dy 1〜3) は残す
        int lit = 0;
        for (int x = cx - a; x <= cx + a; x++) {
            for (int dy = 1; dy <= top - 1; dy++) {
                if (Math.abs(x) <= 1 && dy <= 3) {
                    continue;
                }
                if (!isMine(x, dy, z - 1)) {
                    continue;
                }
                int r = Math.floorMod(x * 37 + dy * 101, 7);
                boolean bright = lit < glow && dy == top - 2 && Math.abs(x - cx) == a - 1;
                if (bright) {
                    lit++;
                }
                put(x, dy, z - 1, bright ? Blocks.SHROOMLIGHT.defaultBlockState() : r < 4 ? MAGMA : r < 6 ? Blocks.BLACKSTONE.defaultBlockState() : GILD);
            }
        }
    }

    // 出口の戸の枠 (殻の壁に平らに描く。戸が開くまで灯りは黒、開くと灯る: lamps)。lamps の位置は原点からの (x, dy, z)
    public void exitFrame() {
        int z = -hz - 1;
        for (int dy = 1; dy <= 4; dy++) {
            repaint(-2, dy, z, BRICKS);
            repaint(2, dy, z, BRICKS);
        }
        for (int x = -1; x <= 1; x++) {
            repaint(x, 4, z, x == 0 ? CHISELED : BRICKS);
        }
    }

    // 出口の灯り (戸が開いたら点く)。どの部屋でも同じ置き方
    static void exitLamps(RaidCtx ctx, List<BlockPos> lamps, boolean on) {
        for (BlockPos p : lamps) {
            ctx.level().setBlock(p, on ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // 出口の灯りの既定の位置: 戸の枠の肩 (殻の壁の x ±2, dy 3)
    List<BlockPos> defaultExitLamps() {
        List<BlockPos> out = new ArrayList<>();
        for (int x : new int[] {-2, 2}) {
            if (isMine(x, 3, -hz - 1)) {
                out.add(at(x, 3, -hz - 1));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 6. 遠景の窓 (壁の窪みに縮尺を落とした遠くの炉。手前に鉄格子。39 の 3.4 の 1)
    // 南の壁 (広間の z = hz + 1) の西寄り x0..x1 (5 幅)、dy0..dy0+3。奥行き 3 (奥ほど狭く、暗く)。入口の間 (x ±3) にかからないこと
    public void vistaSouth(int x0, int x1, int dy0) {
        int zf = hz + 1;
        int cx = (x0 + x1) / 2;
        // 窪みの手前の列は空ける (柱で塞がない)
        keep(x0, dy0, hz - 1, x1, dy0 + 3, hz);
        for (int x = x0; x <= x1; x++) {
            for (int dy = dy0; dy <= dy0 + 3; dy++) {
                for (int z = zf; z <= zf + 2; z++) {
                    int depth = z - zf;
                    boolean inside = Math.abs(x - cx) <= 2 - (depth == 2 ? 1 : 0) && dy < dy0 + 3 - (depth == 2 ? 1 : 0);
                    BlockState st;
                    if (!inside) {
                        st = depth == 0 ? BRICKS : BASALT;
                    } else if (depth == 0) {
                        st = IRON_BARS;
                    } else {
                        st = AIR;
                    }
                    put(x, dy, z, st);
                }
                // 奥の壁 (z = zf + 3): 遠くの炉の口
                int zb = zf + 3;
                boolean mouth = Math.abs(x - cx) <= 0 && dy <= dy0 + 1 || Math.abs(x - cx) == 1 && dy == dy0;
                put(x, dy, zb, mouth ? MAGMA : dy == dy0 + 2 && Math.abs(x - cx) <= 1 ? CHISELED : Blocks.BLACKSTONE.defaultBlockState());
            }
            // 窪みの床と天井
            put(x, dy0 - 1, zf + 1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            put(x, dy0 - 1, zf + 2, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            put(x, dy0 + 4, zf + 1, CEIL);
            put(x, dy0 + 4, zf + 2, CEIL);
        }
        // 小さな物: 遠くの金床と吊り鎖 (奥ほど小さく)
        put(cx - 1, dy0, zf + 2, anvil(Direction.EAST));
        put(cx + 1, dy0, zf + 1, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        put(cx + 1, dy0 + 2, zf + 1, CHAIN);
        put(cx + 1, dy0 + 1, zf + 1, lantern(true));
        put(cx, dy0 + 1, zf + 1, light(5));
        // 枠の上の飾り (梁)
        for (int x = x0; x <= x1; x++) {
            if (free(x, dy0 + 4, hz)) {
                put(x, dy0 + 4, hz, axis(P_BASALT, Direction.Axis.X));
            }
        }
    }

    // ---------------------------------------------------------------- 7. 入口の間 (圧縮。低く暗く、碑の脇に「ここで何があったか」の場面) と出口の間
    public enum Scene {
        // ER1 灰をまけば血は冷える: 落とした熾 (石炭) と火ばさみ、灰の山
        ASH,
        // ER2 鍵は四つに割った: 割れた鋳型と金のかけら
        MOLD,
        // ER3 火が細れば巨神は眠る: 消えかけの火鉢
        BRAZIER,
        // ER4 後ろの道は鎖が切る: 切れた鎖と落ちた板
        CHAIN,
        // ER5 炉は七つで息をする: 鞴
        BELLOWS,
        // ER6 鎚を持つ者は皆弟子だった: 槌の掛け台と打ちかけの刃
        HAMMERS
    }

    public void entry(Scene scene) {
        int ez0 = c.rest().getZ() - o.getZ() - 2;
        // 低い天井の梁 (dy 4。入口の間は高さ 4)
        for (int x = -3; x <= 3; x++) {
            place(x, 4, ez0 + 1, axis(P_BASALT, Direction.Axis.X));
            place(x, 4, ez0 + 3, axis(P_BASALT, Direction.Axis.X));
        }
        // 東の壁の遠景の窓 (遠くの炉): x 4..7
        int zc = ez0 + 2;
        if (hx + 1 >= 9) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    put(4, dy, zc + dz, dy == 3 || Math.abs(dz) == 1 && dy == 1 ? BRICKS : IRON_BARS);
                    put(5, dy, zc + dz, AIR);
                    put(6, dy, zc + dz, Math.abs(dz) == 1 && dy == 3 ? BASALT : AIR);
                    put(7, dy, zc + dz, dz == 0 && dy <= 2 ? MAGMA : Blocks.BLACKSTONE.defaultBlockState());
                }
                put(5, 0, zc + dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                put(6, 0, zc + dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
            put(6, 1, zc - 1, anvil(Direction.NORTH));
            put(5, 2, zc + 1, CHAIN);
            put(5, 1, zc + 1, lantern(true));
            put(6, 3, zc, CHAIN);
        }
        // 場面 (碑 = 西の壁の x -3。場面は東の奥の隅と西の奥の隅)
        int zb = ez0 + 4;
        switch (scene) {
            case ASH -> {
                place(2, 1, zb, Blocks.COAL_BLOCK.defaultBlockState());
                place(1, 1, zb, slab(Blocks.TUFF_SLAB.defaultBlockState(), false));
                place(2, 2, zb, IRON_BARS);
                place(-2, 1, zb, slab(Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false));
                place(-1, 1, zb, Blocks.GRAY_CARPET.defaultBlockState());
            }
            case MOLD -> {
                place(2, 1, zb, slab(Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), false));
                place(1, 1, zb, slab(Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), false));
                place(2, 2, zb, Blocks.RAW_GOLD_BLOCK.defaultBlockState());
                place(-2, 1, zb, slab(Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), true));
                place(-2, 2, zb, slab(Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), false));
            }
            case BRAZIER -> {
                place(2, 1, zb, Blocks.CAULDRON.defaultBlockState());
                place(1, 1, zb, Blocks.COAL_BLOCK.defaultBlockState());
                place(-2, 1, zb, slab(Blocks.TUFF_SLAB.defaultBlockState(), false));
            }
            case CHAIN -> {
                place(2, 1, zb, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                place(1, 1, zb, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                place(-2, 1, zb, slab(Blocks.DARK_OAK_SLAB.defaultBlockState(), false));
                place(-1, 1, zb, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
            case BELLOWS -> {
                place(2, 1, zb, Blocks.BROWN_WOOL.defaultBlockState());
                place(2, 2, zb, Blocks.SPRUCE_PLANKS.defaultBlockState());
                place(1, 1, zb, IRON_BARS);
                place(-2, 1, zb, Blocks.BARREL.defaultBlockState());
            }
            case HAMMERS -> {
                place(2, 1, zb, anvil(Direction.EAST));
                place(2, 2, zb, IRON_BARS);
                for (int x = -2; x <= -1; x++) {
                    if (free(x, 2, zb)) {
                        put(x, 2, zb, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.NORTH)
                                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
                    }
                    place(x, 3, zb, CHAIN);
                }
            }
        }
    }

    // 出口の間: 両脇の吊り鎖と、床の金の印 (次の部屋も余燼)
    public void exitRoom() {
        int ze = c.exitSpot().getZ() - o.getZ();
        for (int x : new int[] {-2, 2}) {
            place(x, 4, ze - 1, CHAIN);
            place(x, 3, ze - 1, CHAIN);
            place(x, 1, ze - 1, axis(P_BASALT, Direction.Axis.Y));
            place(x, 2, ze - 1, candles(2));
        }
        if (isMine(0, 0, ze - 1)) {
            put(0, 0, ze - 1, GILD);
        }
    }

    // ---------------------------------------------------------------- 8. 見えない灯り (床の明るさの補い。明るさ 12 未満)
    // dy の高さに、間隔 step で。value = 明るさ
    public void lights(int dy, int step, int value) {
        for (int x = -hx + 2; x <= hx - 2; x += step) {
            for (int z = -hz + 2; z <= hz - 2; z += step) {
                place(x, dy, z, light(value));
            }
        }
    }

    // ---------------------------------------------------------------- 仕上げ: 鉄格子の向き (となりの固い所・格子へつなぐ)
    public int finish() {
        for (BlockPos p : bars) {
            BlockState st = lv.getBlockState(p);
            if (!(st.getBlock() instanceof IronBarsBlock)) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState n = lv.getBlockState(p.relative(d));
                boolean link = n.getBlock() instanceof IronBarsBlock || n.isSolidRender(lv, p.relative(d));
                st = st.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d), link);
            }
            lv.setBlock(p, st, Block.UPDATE_CLIENTS);
        }
        return placed;
    }

    public int placed() {
        return placed;
    }

    // ---------------------------------------------------------------- 中ボスの間 ERM (灰の鍛え手。27 x 27 の円い間、高さ 14)
    // 元の間の中身 (AshSmithArena.build) を建てたあとに呼ぶ。槽・灰溜まり・柱・炉の扉・床の模様は触らない。
    // 四隅の壁を帯の色に、円の縁に玄武岩の付け柱、炉の扉の煉瓦の枠と金の要石、天井から鎖と真ん中の吊るし輪、見えない灯り。出口の灯りの位置を返す
    static Audit lastSmithAudit;

    public static List<BlockPos> decorateSmithHall(RaidCtx ctx) {
        EmberRaidDecor d = of(ctx);
        d.shell(false);
        int r = d.hx;
        // 円の縁の付け柱 (南北の軸 = 戸の正面と、灰溜まりのある斜めは避ける)
        for (double deg : new double[] {0, 22.5, 67.5, 112.5, 157.5, 180, 202.5, 247.5, 292.5, 337.5}) {
            double a = Math.toRadians(deg);
            int x = (int) Math.round(Math.cos(a) * (r - 0.4));
            int z = (int) Math.round(Math.sin(a) * (r - 0.4));
            int top = 6 + Math.floorMod((int) deg * 7, 6);
            int last = 0;
            for (int y = 1; y <= top && d.free(x, y, z); y++) {
                last = y;
            }
            if (last >= 3) {
                for (int y = 1; y <= last; y++) {
                    d.put(x, y, z, y == 1 ? BRICKS : y == last ? axis(P_BASALT, Direction.Axis.Y) : axis(BASALT, Direction.Axis.Y));
                }
            }
        }
        // 炉の扉の枠 (扉 x -9..-7, dy 1..3, z 11)
        for (int x = -10; x <= -6; x++) {
            for (int dy = 1; dy <= 5; dy++) {
                boolean door = x >= -9 && x <= -7 && dy <= 3;
                if (door) {
                    continue;
                }
                BlockState st = dy == 4 && x == -8 ? GILD : dy == 5 ? (x == -8 ? CHISELED : null) : x == -10 || x == -6 ? BRICKS : dy == 4 ? BRICKS : null;
                if (st == null) {
                    continue;
                }
                if (!d.place(x, dy, 11, st)) {
                    d.repaint(x, dy, 11, st);
                }
            }
        }
        // 真ん中の上の吊るし輪 (鉄格子の輪、八本の鎖で天井から)
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double dd = Math.sqrt(x * x + z * z);
                if (Math.abs(dd - 4) < 0.5) {
                    d.place(x, 11, z, IRON_BARS);
                }
            }
        }
        for (int[] k : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            d.place(k[0], 12, k[1], CHAIN);
            d.place(k[0], 13, k[1], CHAIN);
            d.place(k[0], 14, k[1], CHAIN);
        }
        d.place(0, 12, 0, light(10));
        d.chains(10, 9, 3, 0, 0, 7);
        d.exitFrame();
        d.exitRoom();
        d.lights(5, 7, 9);
        List<BlockPos> lamps = d.defaultExitLamps();
        exitLamps(ctx, lamps, false);
        int n = d.finish();
        lastSmithAudit = audit(ctx.level(), ctx.cell(), n);
        log(ctx.roomId(), lastSmithAudit);
        return lamps;
    }

    public static void exitLampsPublic(RaidCtx ctx, List<BlockPos> lamps, boolean on) {
        exitLamps(ctx, lamps, on);
    }

    // ---------------------------------------------------------------- 負荷の数え (39 の 6 章)
    public record Audit(int deco, int bright, int blockEntities, int entities) {
        public boolean ok() {
            return deco <= MAX_DECO && bright <= MAX_BRIGHT && blockEntities <= MAX_BLOCK_ENTITIES && entities <= MAX_ENTITIES;
        }

        @Override
        public String toString() {
            return "deco " + deco + "/" + MAX_DECO + ", bright " + bright + "/" + MAX_BRIGHT + ", block entities " + blockEntities + "/" + MAX_BLOCK_ENTITIES
                    + ", entities " + entities + "/" + MAX_ENTITIES;
        }
    }

    public static Audit audit(ServerLevel lv, RaidKit.Cell cell, int deco) {
        AABB b = cell.bounds();
        int bright = 0;
        int be = 0;
        for (int x = (int) b.minX; x < b.maxX; x++) {
            for (int z = (int) b.minZ; z < b.maxZ; z++) {
                for (int y = (int) b.minY; y < b.maxY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = lv.getBlockState(p);
                    if (st.isAir()) {
                        continue;
                    }
                    if (st.getLightEmission() >= 12) {
                        bright++;
                    }
                    if (st.hasBlockEntity()) {
                        be++;
                    }
                }
            }
        }
        int ents = 0;
        // 飾りのエンティティ (額縁・絵・表示・防具立て)。戦いの予告の表示や落ちた物は数えない (予告は Telegraph が tag を付ける)
        for (Entity e : lv.getEntities((Entity) null, b, e -> e instanceof net.minecraft.world.entity.decoration.HangingEntity
                || e instanceof net.minecraft.world.entity.decoration.ArmorStand
                || e instanceof net.minecraft.world.entity.Display && e.getTags().contains(DECOR_TAG))) {
            ents++;
        }
        return new Audit(deco, bright, be, ents);
    }

    static void log(String id, Audit a) {
        SkyAbyss.LOGGER.info("Ember raid room {} decor: {}", id, a);
    }
}
