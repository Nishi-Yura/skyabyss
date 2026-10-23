package jp.revati.skyabyss.trial.raid.storm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.AABB;

// 雷翼のレイドの部屋 (SR1〜SR6) と雷獣の間 (SRM) の飾り (docs/design/39 の 4.3「雷翼」。銅・雲・羽・雷)。
// 部屋の build の終わりに dress*(ctx, ...) を一度呼ぶ。RaidKit.buildCell の殻はそのまま閉じたまま、内側へ足す・殻の色を塗り替えるだけ:
//   - 壁: 高さで錆びる蝋引きの銅の帯 (下 = 新しい銅 → 上 = 緑青。蝋引きなので変わらない)。頭より上 (床 +4 から) に白い羽の段 (瓦のように重なる、先が階段)、
//         天井の際は雲 (羊毛) が壁へ垂れて角を丸める。足もとは銅の階段の幅木 (床と壁の角を消す)
//   - 天井: 雲 (白い羊毛・コンクリート・雪) と垂れた雲のかたまり、ジグザグの稲妻の割れ目 (黄の色ガラスと下向きのエンドロッド)
//   - 床: 錆びの斑 (仕掛けの床はそのまま)、銅の格子の下に雲の層 (床の下へ 4 段の閉じた箱)、
//   - 北と南の厚い壁に遠景の窪み (鉄格子の奥に縮んだ雲海と避雷の塔)、出口の戸の枠 (越えると枠の電球が灯る)、入口の間の場面 (焼けた銅・倒れた避雷針・抜けた羽・凧)
//   - 部屋ごとの目印: SR1 羽の座の扇・SR2 電源の上の尖塔と灯の配線と見渡し台・SR3 中央の尖塔と雲の床の輪・SR4 鍵の台の縁と天井の雲の切れ目・
//     SR5 北の壁の拍子の見本 (九つの電球) と雲に浮く板と見渡し台・SR6 丸い広間と天井の嵐の目・SRM 雷獣の間の環と天井の星
// 仕掛け (台・柱・板・鍵・針) は build のあとの姿から読み取り、その列とまわり 1 ブロックには足さない。床の仕掛け (色の違う床) は塗らない。
// 書いた数は LAST (部屋の id ごと) に残す: 飾り 8 千ブロック・明るさ 12 以上の見える灯り 40・ブロックエンティティ 24・飾りのエンティティ 16 まで (自己テスト)
public final class StormRoomDeco {
    private StormRoomDeco() {
    }

    public static final int MAX_BLOCKS = 8000;
    public static final int MAX_BRIGHT = 40;
    public static final int MAX_BLOCK_ENTITIES = 24;
    public static final int MAX_ENTITIES = 16;
    // 床の下の雲の層の深さ (床の下の段の数。いちばん下は白い底)
    static final int UNDER = 4;
    private static final int FLAG = Block.UPDATE_CLIENTS;

    public record Stats(int blocks, int bright, int blockEntities, int entities, int minY) {
    }

    // 見比べの撮影用: 環境変数 SKYABYSS_STORM_DECO=off で飾らない (建築の前の姿)
    static final boolean OFF = "off".equals(System.getenv("SKYABYSS_STORM_DECO"));

    // 部屋の id → 最後に飾ったときの数 (自己テスト)
    public static final Map<String, Stats> LAST = new HashMap<>();

    // ---------------------------------------------------------------- 材
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState[][] BANDS = {
            {Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), Blocks.WAXED_CUT_COPPER.defaultBlockState()},
            {Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState(), Blocks.WAXED_EXPOSED_COPPER.defaultBlockState()},
            {Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(), Blocks.WAXED_WEATHERED_COPPER.defaultBlockState()},
            {Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState(), Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState()}};
    private static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState WHITE = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState WOOL = Blocks.WHITE_WOOL.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState GRATE = Blocks.WAXED_COPPER_GRATE.defaultBlockState();
    private static final BlockState CRACK = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
    private static final BlockState BULB = Blocks.WAXED_COPPER_BULB.defaultBlockState();
    private static final BlockState CHISELED = Blocks.WAXED_CHISELED_COPPER.defaultBlockState();
    private static final BlockState GLEAM = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
    private static final BlockState CUT = Blocks.WAXED_CUT_COPPER.defaultBlockState();
    private static final BlockState PATINA = Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState();

    private static BlockState light(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    private static BlockState rod(Direction d) {
        return Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, d);
    }

    private static BlockState endRod(Direction d) {
        return Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, d);
    }

    // ---------------------------------------------------------------- 一部屋の飾りの道具
    static final class Deco {
        final RaidCtx ctx;
        final ServerLevel level;
        final RaidKit.Cell cell;
        final BlockPos o;
        final int hx;
        final int hz;
        final int h;
        final ImprovedNoise noise;
        final RandomSource rng;
        final long seed;
        // 仕掛けの列: 床の上で一番高い仕掛けのブロックの高さ (0 = なし)。床の仕掛け (色の違う床) は floorLocked
        final int[][] gameTop;
        final boolean[][] floorLocked;
        // 部屋の側が「ここには足さない」とした箱 (x0, z0, x1, z1, 上の高さ)
        final List<int[]> keeps = new ArrayList<>();
        int blocks;
        int bright;
        int blockEntities;
        int minY;
        final BlockState floorBase;
        final BlockState wallBase;

        Deco(RaidCtx ctx) {
            this.ctx = ctx;
            this.level = ctx.level();
            this.cell = ctx.cell();
            this.o = cell.origin();
            this.hx = cell.hx();
            this.hz = cell.hz();
            this.h = cell.height();
            // 形は部屋で決まる (挑戦ごとに変えない)。挑戦ごとに動く仕掛けは読み取ってよける
            this.seed = ctx.roomId().hashCode() * 0x9E3779B97F4A7C15L + 0x5DEECE66DL;
            this.rng = RandomSource.create(seed);
            this.noise = new ImprovedNoise(RandomSource.create(seed ^ 0x2545F4914F6CDD1DL));
            this.floorBase = cell.palette().floor();
            this.wallBase = cell.palette().wall();
            this.minY = (int) cell.bounds().minY;
            gameTop = new int[2 * hx + 1][2 * hz + 1];
            floorLocked = new boolean[2 * hx + 1][2 * hz + 1];
            for (int x = -hx; x <= hx; x++) {
                for (int z = -hz; z <= hz; z++) {
                    BlockState f = level.getBlockState(o.offset(x, 0, z));
                    floorLocked[x + hx][z + hz] = !f.equals(floorBase) && !f.equals(cell.palette().trim());
                    for (int y = h; y >= 1; y--) {
                        BlockState s = level.getBlockState(o.offset(x, y, z));
                        if (!s.isAir() && !s.equals(wallBase)) {
                            gameTop[x + hx][z + hz] = y;
                            break;
                        }
                    }
                }
            }
        }

        boolean inArena(int x, int z) {
            return x >= -hx && x <= hx && z >= -hz && z <= hz;
        }

        // 仕掛けの列か (margin ブロック以内に、床の上の仕掛けがある)
        boolean nearGame(int x, int z, int margin) {
            for (int dx = -margin; dx <= margin; dx++) {
                for (int dz = -margin; dz <= margin; dz++) {
                    if (inArena(x + dx, z + dz) && gameTop[x + dx + hx][z + dz + hz] > 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean locked(int x, int z) {
            return inArena(x, z) && floorLocked[x + hx][z + hz];
        }

        boolean kept(int x, int y, int z) {
            for (int[] k : keeps) {
                if (x >= k[0] && x <= k[2] && z >= k[1] && z <= k[3] && y <= k[4]) {
                    return true;
                }
            }
            return false;
        }

        void keep(int x0, int z0, int x1, int z1, int top) {
            keeps.add(new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), top});
        }

        // 戸の前 (入口・出口の戸の列と、その前 3 ブロック・高さ 5 まで) か
        boolean doorway(int x, int y, int z) {
            return Math.abs(x) <= 3 && y <= 5 && (z >= hz - 2 || z <= -hz + 2);
        }

        BlockState get(int x, int y, int z) {
            return level.getBlockState(o.offset(x, y, z));
        }

        // 置く (数える)。同じなら何もしない
        void put(int x, int y, int z, BlockState st) {
            BlockPos p = o.offset(x, y, z);
            BlockState was = level.getBlockState(p);
            if (was.equals(st)) {
                return;
            }
            level.setBlock(p, st, FLAG);
            blocks++;
            if (st.getLightEmission() >= 12 && !st.is(Blocks.LIGHT) || st.is(Blocks.WAXED_COPPER_BULB)) {
                bright++;
            }
            if (st.hasBlockEntity()) {
                blockEntities++;
            }
            minY = Math.min(minY, p.getY());
        }

        // 空気の所にだけ置く
        boolean putAir(int x, int y, int z, BlockState st) {
            if (!get(x, y, z).isAir()) {
                return false;
            }
            put(x, y, z, st);
            return true;
        }

        double n(double x, double y, double z) {
            return noise.noise(x, y, z);
        }

        int hash(int x, int y, int z) {
            long v = seed ^ (x * 0x9E3779B1L) ^ (y * 0x85EBCA77L) ^ (z * 0xC2B2AE3DL);
            v ^= v >>> 29;
            v *= 0xBF58476D1CE4E5B9L;
            v ^= v >>> 32;
            return (int) (v & 0x7fffffff);
        }

        // 高さ y の壁の色 (下 = 新しい銅 → 上 = 緑青。境目はノイズでぼかし、帯の中は 7:3 で模様違い)
        BlockState band(int x, int y, int z) {
            double v = n(x * 0.21, y * 0.17 + 31, z * 0.21);
            double t = (y - 0.5 + v * 1.6) / Math.max(1.0, (h + 1) / 4.0);
            int b = Math.max(0, Math.min(3, (int) Math.floor(t)));
            return BANDS[b][hash(x, y, z) % 10 < 7 ? 0 : 1];
        }

        BlockState cloud(int x, int y, int z) {
            int r = hash(x, y, z) % 10;
            return r < 7 ? WOOL : r < 9 ? WHITE : SNOW;
        }

        // 北 (-1)・南 (+1) の壁で、部屋の側が使う x (窪みを作らない)
        final List<int[]> wallMarks = new ArrayList<>();

        // 床の下の雲の層の箱を置いた列 (x, z) の集まり
        final Set<Long> under = new HashSet<>();

        static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }

    // ---------------------------------------------------------------- 部屋の飾り (部屋ごとの入口)
    // SR1 避雷の羽運び: 東の壁に三つの「羽の座」(扇)。perchX = 止まり木の x、perchZ = 三つの z。charm = 護符の場所 (西寄り)
    public static void dressSR1(RaidCtx ctx, int perchX, int[] perchZ, List<int[]> charmHomes) {
        if (OFF) {
            return;
        }
        Deco d = new Deco(ctx);
        for (int[] ch : charmHomes) {
            d.keep(ch[0] - 1, ch[1] - 1, ch[0] + 1, ch[1] + 1, 3);
        }
        for (int pz : perchZ) {
            d.keep(perchX + 2, pz - 4, d.hx, pz + 4, 7);
        }
        common(d, true, true);
        for (int pz : perchZ) {
            fan(d, pz, false);
        }
        windows(d, 4, 2, 1);
        exitFrame(d, true);
        vestibule(d, false);
        finish(d);
    }

    // SR2 銅の回路つなぎ: 床いっぱいの盤 (触らない)。電源の上に吊りの尖塔、四つの灯の後ろの壁に配線の電球、南の上に見渡し台
    public static void dressSR2(RaidCtx ctx, int[] srcRel, List<int[]> lampRel) {
        if (OFF) {
            return;
        }
        Deco d = new Deco(ctx);
        for (int[] l : lampRel) {
            BlockPos b = conduitBulb(d.cell, l[0], l[1]);
            int bz = b.getZ() - d.o.getZ();
            if (Math.abs(bz) == d.hz + 1) {
                d.wallMarks.add(new int[] {Integer.signum(bz), b.getX() - d.o.getX()});
            }
        }
        overlook(d);
        common(d, false, false, true);
        spire(d, srcRel[0], srcRel[1], 5, false);
        for (int[] l : lampRel) {
            lampConduit(d, l[0], l[1]);
        }
        exitFrame(d, false);
        vestibule(d, true);
        finish(d);
    }

    // SR3 帯電の連鎖: 中央に吊りの避雷の尖塔、そのまわりに雲の床の輪 (格子の下に雲)
    public static void dressSR3(RaidCtx ctx) {
        if (OFF) {
            return;
        }
        Deco d = new Deco(ctx);
        common(d, true, true);
        spire(d, 0, 0, 6, true);
        ring(d, 0, 0, 4.0, 6.4);
        windows(d, 4, 2, 1);
        exitFrame(d, true);
        vestibule(d, false);
        finish(d);
    }

    // SR4 雷柱から逃げて鍵集め: 鍵の台の縁 (床) と、その上の天井の雲の切れ目 (光)
    public static void dressSR4(RaidCtx ctx, int[][] keys) {
        if (OFF) {
            return;
        }
        Deco d = new Deco(ctx);
        common(d, true, true);
        for (int[] k : keys) {
            keyHalo(d, k[0], k[1]);
        }
        windows(d, 4, 2, 1);
        exitFrame(d, true);
        vestibule(d, false);
        finish(d);
    }

    // SR5 雷の拍子覚え: 板の間の筋を格子にして下に雲 (板が雲に浮く)、北の壁の高い所に拍子の見本 (九つの電球)、南の上に見渡し台
    public static void dressSR5(RaidCtx ctx) {
        if (OFF) {
            return;
        }
        Deco d = new Deco(ctx);
        overlook(d);
        common(d, true, true);
        List<int[]> cols = new ArrayList<>();
        for (int x = -d.hx + 1; x <= d.hx - 1; x++) {
            for (int z = -d.hz + 1; z <= d.hz - 1; z++) {
                if (BeatTiles.tileAt(d.o, d.o.getX() + x + 0.5, d.o.getZ() + z + 0.5) < 0 && !d.locked(x, z) && !d.nearGame(x, z, 0)
                        && !(Math.abs(x) <= 1 && Math.abs(z) >= d.hz - 1)) {
                    cols.add(new int[] {x, z});
                }
            }
        }
        underFloor(d, cols);
        for (int i = 0; i < BeatTiles.N * BeatTiles.N; i++) {
            BlockPos m = mirrorPos(d.cell, i);
            d.put(m.getX() - d.o.getX(), m.getY() - d.o.getY(), m.getZ() - d.o.getZ(), BULB);
        }
        // 見本の枠 (羽の白)
        for (int x = -3; x <= 3; x++) {
            for (int y = d.h - 3; y <= d.h; y++) {
                if (Math.abs(x) == 3 || y == d.h - 3) {
                    d.put(x, y, -d.hz - 1, y == d.h - 3 ? QUARTZ : WHITE);
                }
            }
        }
        exitFrame(d, true);
        vestibule(d, true);
        finish(d);
    }

    // SR6 嵐の目へ: 丸い広間 (四隅を羽の壁で埋める)、天井の嵐の目 (渦の雲と中心の光)。胸の高さ (床 +2) の広間の中には何も足さない (柱の陰の判定)
    public static void dressSR6(RaidCtx ctx) {
        if (OFF) {
            return;
        }
        Deco d = new Deco(ctx);
        double rr = (d.hx + 0.4) * (d.hx + 0.4);
        for (int x = -d.hx; x <= d.hx; x++) {
            for (int z = -d.hz; z <= d.hz; z++) {
                if (x * x + z * z > rr && d.gameTop[x + d.hx][z + d.hz] == 0 && !(Math.abs(x) <= 2 && Math.abs(z) >= d.hz - 1)) {
                    for (int y = 1; y <= d.h; y++) {
                        d.putAir(x, y, z, d.wallBase);
                    }
                }
            }
        }
        common(d, true, false, false);
        eye(d);
        ring(d, 0, 0, 3.0, 4.6);
        exitFrame(d, true);
        vestibule(d, false);
        finish(d);
    }

    // SRM 雷獣の間 (丸い洞、高さ 15): 壁の帯と羽の段、天井の雲と稲妻と中心の星、栓の前の縁
    public static void dressSRM(RaidCtx ctx) {
        if (OFF) {
            return;
        }
        Deco d = new Deco(ctx);
        // 雷獣が跳ぶ空 (柱の上と中央) は、壁の際より内には足さない
        common(d, true, true, false);
        ring(d, 0, 0, 0.0, 3.2);
        spire(d, 0, 0, d.h - 2, true);
        exitFrame(d, true);
        finish(d);
    }

    // ---------------------------------------------------------------- 共通の層
    // 壁・天井・床・灯り (部屋の型によらない)。skirt = 足もとの幅木、patina = 床の錆びの斑
    private static void common(Deco d, boolean skirt, boolean patina) {
        common(d, skirt, patina, true);
    }

    private static void common(Deco d, boolean skirt, boolean patina, boolean niches) {
        clearBelow(d);
        organicWall(d, skirt);
        recolorFaces(d);
        ceiling(d);
        crack(d);
        if (patina) {
            patina(d);
        }
        if (niches) {
            niches(d);
        }
        hiddenLights(d);
        props(d);
    }

    // 壁の肉付け: 天井の際から垂れる二重の羽の房 (壁ぎわ = 長い羽 2〜5、手前 = 短い羽 1〜3。幅 2 の羽が隣どうし長さを変え、先は逆さの階段)。
    // 天井の際の一段は雲 (天井と壁の角を丸める)。高い部屋 (12 以上) は中ほどにもう一つの房。床 +4 より下には足さない (足もとは銅の階段の幅木だけ)。
    // 羽の長さは壁に沿う u の長い周期のノイズ (風の向き) と羽ごとの揺れ。仕掛けの列・戸の前・部屋の側の箱はよける
    private static void organicWall(Deco d, boolean skirt) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<int[]> second = new ArrayList<>();
        for (int y = 1; y <= d.h; y++) {
            for (int x = -d.hx; x <= d.hx; x++) {
                for (int z = -d.hz; z <= d.hz; z++) {
                    if (!d.get(x, y, z).isAir()) {
                        continue;
                    }
                    Direction toWall = null;
                    for (int[] dir : dirs) {
                        if (isWall(d, x + dir[0], y, z + dir[1])) {
                            toWall = Direction.fromDelta(dir[0], 0, dir[1]);
                            break;
                        }
                    }
                    if (toWall == null || d.doorway(x, y, z) || d.kept(x, y, z) || d.nearGame(x, z, 1)) {
                        continue;
                    }
                    if (y <= 3) {
                        // 角 (両側が壁) の段は置かない。段の低い側 (部屋の側) が壁だと、上る向きが食い違う
                        Direction room = toWall.getOpposite();
                        if (!d.get(x + room.getStepX(), y, z + room.getStepZ()).isAir()) {
                            continue;
                        }
                        if (skirt && y == 1 && !d.locked(x, z)) {
                            // 壁ぎわの縁: 一段の高さの幅木 (半ブロック)。上る筋がないので向きを持たせない (向きが壁に当たる段を作らない)
                            d.put(x, y, z, Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                    net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
                        }
                        continue;
                    }
                    int u = toWall.getAxis() == Direction.Axis.X ? z : x;
                    BlockState st = fringe(d, x, y, z, u, 1, toWall);
                    if (st != null) {
                        d.put(x, y, z, st);
                        if (fringe(d, x, y, z, u, 2, toWall) != null) {
                            second.add(new int[] {x, y, z, u, toWall.ordinal()});
                        }
                    }
                }
            }
        }
        // 二段目: 一段目の手前 (広間の側) の空気
        for (int[] s : second) {
            Direction toWall = Direction.values()[s[4]];
            int x = s[0] - toWall.getStepX();
            int z = s[2] - toWall.getStepZ();
            int y = s[1];
            if (!d.inArena(x, z) || d.doorway(x, y, z) || d.kept(x, y, z) || d.nearGame(x, z, 1) || !d.get(x, y, z).isAir()) {
                continue;
            }
            d.put(x, y, z, fringe(d, x, y, z, s[3], 2, toWall));
        }
    }

    // 壁か: 広間の外の殻、または広間の中の部屋の側の壁 (丸い洞の詰め物)。仕掛けの柱 (切った銅など) は壁ではない
    private static boolean isWall(Deco d, int x, int y, int z) {
        return d.get(x, y, z).equals(d.wallBase);
    }

    // 羽の房の一つのブロック (layer 1 = 壁ぎわ、2 = 手前)。null = 置かない
    private static BlockState fringe(Deco d, int x, int y, int z, int u, int layer, Direction toWall) {
        if (y == d.h) {
            return layer == 1 || d.hash(u, 3, 9) % 3 != 0 ? (d.hash(x, y, z) % 4 == 0 ? WHITE : WOOL) : null;
        }
        int[] tops = d.h >= 12 ? new int[] {d.h - 1, d.h - 7} : new int[] {d.h - 1};
        for (int top : tops) {
            // 羽の幅 2。手前の段は一つずらす
            int f = Math.floorDiv(u + (layer == 2 ? 1 : 0), 2);
            double wind = d.n(f * 0.21, top * 0.5 + layer * 13.0, 5.5);
            int len = layer == 1 ? 3 + (int) Math.round(wind * 2.2) + d.hash(f, top, 1) % 2 : 1 + (int) Math.round(wind * 1.4) + d.hash(f, top, 2) % 2;
            len = layer == 1 ? Math.max(2, Math.min(5, len)) : Math.max(1, Math.min(3, len));
            int bottom = Math.max(4, top - len + 1);
            if (y < bottom || y > top) {
                continue;
            }
            if (y == bottom) {
                return Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toWall).setValue(StairBlock.HALF, Half.TOP);
            }
            // 羽ごとに二色 (羽軸の側は白いコンクリート、羽の外は石英)
            return Math.floorMod(u + (layer == 2 ? 1 : 0), 2) == 0 ? WHITE : QUARTZ;
        }
        return null;
    }

    // 壁の面 (空気に面した wallBase) を高さの銅の帯で塗る。部屋全体 (入口・出口の間・見渡し台も)
    private static void recolorFaces(Deco d) {
        AABB b = d.cell.bounds();
        int x0 = (int) b.minX - d.o.getX();
        int x1 = (int) b.maxX - 1 - d.o.getX();
        int z0 = (int) b.minZ - d.o.getZ();
        int z1 = (int) b.maxZ - 1 - d.o.getZ();
        int y1 = d.h;
        List<int[]> todo = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y <= y1; y++) {
                    if (!d.get(x, y, z).equals(d.wallBase)) {
                        continue;
                    }
                    if (d.get(x + 1, y, z).isAir() || d.get(x - 1, y, z).isAir() || d.get(x, y, z + 1).isAir() || d.get(x, y, z - 1).isAir()
                            || d.get(x, y + 1, z).isAir() || d.get(x, y - 1, z).isAir()) {
                        todo.add(new int[] {x, y, z});
                    }
                }
            }
        }
        for (int[] t : todo) {
            d.put(t[0], t[1], t[2], d.band(t[0], Math.max(1, t[1]), t[2]));
        }
    }

    // 天井: 広間の天井の板を雲に、天井の下に垂れた雲のかたまり (二段まで)。天井の灯り (蛙灯) はここで雲に替わる
    private static void ceiling(Deco d) {
        int top = d.h + 1;
        for (int x = -d.hx; x <= d.hx; x++) {
            for (int z = -d.hz; z <= d.hz; z++) {
                BlockState c = d.get(x, top, z);
                if (!c.isAir() && !c.is(Blocks.GLASS)) {
                    d.put(x, top, z, d.cloud(x, top, z));
                }
            }
        }
        for (int x = -d.hx; x <= d.hx; x++) {
            for (int z = -d.hz; z <= d.hz; z++) {
                if (d.gameTop[x + d.hx][z + d.hz] >= d.h - 1 || d.kept(x, d.h, z)) {
                    continue;
                }
                // 大きな周期のノイズで丸いかたまり (三割ほど)。芯だけ二段に垂れる
                double v = d.n(x * 0.085, 50, z * 0.085) + 0.25 * d.n(x * 0.3, 61, z * 0.3);
                if (v > 0.2 && d.get(x, top, z).isSolidRender(d.level, d.o.offset(x, top, z))) {
                    d.putAir(x, d.h, z, v > 0.38 ? WHITE : WOOL);
                    if (v > 0.5 && d.h >= 9 && !d.kept(x, d.h - 1, z) && d.gameTop[x + d.hx][z + d.hz] < d.h - 2) {
                        d.putAir(x, d.h - 1, z, WOOL);
                    }
                }
            }
        }
    }

    // 稲妻の割れ目: 西から東へ折れ曲がる線。天井の下の段に黄の色ガラス、折れ目に下向きのエンドロッド
    private static void crack(Deco d) {
        int x = -d.hx + 2;
        int z = (int) Math.round((d.rng.nextDouble() - 0.5) * d.hz);
        int dir = d.rng.nextBoolean() ? 1 : -1;
        int rods = 0;
        while (x < d.hx - 1) {
            int len = 2 + d.rng.nextInt(3);
            for (int i = 0; i < len && x < d.hx - 1; i++, x++) {
                crackCell(d, x, z);
            }
            int dz = 2 + d.rng.nextInt(2);
            for (int i = 0; i < dz; i++) {
                int nz = z + dir;
                if (Math.abs(nz) > d.hz - 2) {
                    break;
                }
                z = nz;
                crackCell(d, x, z);
            }
            if (rods < 5 && d.get(x, d.h - 1, z).isAir() && d.gameTop[x + d.hx][z + d.hz] < d.h - 2 && !d.kept(x, d.h - 1, z)) {
                d.put(x, d.h - 1, z, endRod(Direction.DOWN));
                rods++;
            }
            dir = -dir;
        }
    }

    private static void crackCell(Deco d, int x, int z) {
        if (!d.inArena(x, z) || d.gameTop[x + d.hx][z + d.hz] >= d.h || d.kept(x, d.h, z)) {
            return;
        }
        BlockState s = d.get(x, d.h, z);
        if (s.isAir() || s.is(Blocks.WHITE_WOOL) || s.is(Blocks.WHITE_CONCRETE) || s.is(Blocks.SNOW_BLOCK)) {
            d.put(x, d.h, z, CRACK);
            d.put(x, d.h + 1, z, WHITE);
        }
    }

    // 床の下 (殻の下の 2〜UNDER 段) を空にする (前の挑戦の部屋の雲の層が残っていても、同じ形から建てる)
    private static void clearBelow(Deco d) {
        for (int x = -d.hx - 1; x <= d.hx + 1; x++) {
            for (int z = -d.hz - 1; z <= d.hz + 1; z++) {
                for (int y = -2; y >= -UNDER; y--) {
                    BlockPos p = d.o.offset(x, y, z);
                    if (!d.level.getBlockState(p).isAir()) {
                        d.level.setBlock(p, AIR, FLAG);
                    }
                }
            }
        }
    }

    // 床の錆びの斑 (元の床 = palette.floor の所だけ)
    private static void patina(Deco d) {
        for (int x = -d.hx; x <= d.hx; x++) {
            for (int z = -d.hz; z <= d.hz; z++) {
                if (d.get(x, 0, z).equals(d.floorBase) && d.n(x * 0.23, 90, z * 0.23) + 0.3 * d.n(x * 0.7, 91, z * 0.7) > 0.22) {
                    d.put(x, 0, z, PATINA);
                }
            }
        }
    }

    // 床の下の雲の層: 列の床を銅の格子にし、下 UNDER 段の閉じた箱に雲のかたまりと見えない灯り。箱の横は壁で閉じる
    static void underFloor(Deco d, List<int[]> cols) {
        Set<Long> set = new HashSet<>();
        for (int[] c : cols) {
            set.add(Deco.key(c[0], c[1]));
        }
        for (int[] c : cols) {
            int x = c[0];
            int z = c[1];
            d.put(x, 0, z, GRATE);
            for (int y = -1; y > -UNDER; y--) {
                d.put(x, y, z, AIR);
            }
            d.put(x, -UNDER, z, WHITE);
            double v = d.n(x * 0.35, 120, z * 0.35);
            d.put(x, -UNDER + 1, z, v > -0.25 ? d.cloud(x, -UNDER + 1, z) : AIR);
            if (v > 0.25) {
                d.put(x, -UNDER + 2, z, d.cloud(x, -UNDER + 2, z));
            }
            if (Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0) {
                d.put(x, -1, z, light(11));
            }
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dir : dirs) {
                int nx = x + dir[0];
                int nz = z + dir[1];
                if (set.contains(Deco.key(nx, nz))) {
                    continue;
                }
                for (int y = -1; y >= -UNDER; y--) {
                    BlockState s = d.get(nx, y, nz);
                    if (s.isAir() || !s.isSolidRender(d.level, d.o.offset(nx, y, nz))) {
                        d.put(nx, y, nz, y == -UNDER ? WHITE : d.band(nx, 1, nz));
                    }
                }
            }
            d.under.add(Deco.key(x, z));
        }
    }

    // 輪 (中心 cx, cz の r0〜r1) の床を雲の層に (元の床・帯の所だけ。仕掛けの床・柱の足もとはよける)
    private static void ring(Deco d, int cx, int cz, double r0, double r1) {
        List<int[]> cols = new ArrayList<>();
        for (int x = -d.hx; x <= d.hx; x++) {
            for (int z = -d.hz; z <= d.hz; z++) {
                double r = Math.hypot(x - cx, z - cz);
                if (r >= r0 && r <= r1 && !d.locked(x, z) && !d.nearGame(x, z, 0) && !d.get(x, 0, z).is(Blocks.WAXED_COPPER_GRATE)) {
                    cols.add(new int[] {x, z});
                }
            }
        }
        underFloor(d, cols);
    }

    // 雲の窓: 床の空いた所に小さな格子の窓 (w × d の箱、count まで)。仕掛け・戸の前・部屋の側の箱から離す
    private static void windows(Deco d, int count, int halfW, int halfD) {
        int made = 0;
        for (int tries = 0; tries < 200 && made < count; tries++) {
            int cx = d.rng.nextInt(2 * d.hx - 7) - d.hx + 4;
            int cz = d.rng.nextInt(2 * d.hz - 7) - d.hz + 4;
            boolean along = d.rng.nextBoolean();
            int ax = along ? halfW : halfD;
            int az = along ? halfD : halfW;
            boolean ok = true;
            for (int x = cx - ax - 1; x <= cx + ax + 1 && ok; x++) {
                for (int z = cz - az - 1; z <= cz + az + 1 && ok; z++) {
                    if (!d.inArena(x, z) || d.locked(x, z) || d.nearGame(x, z, 1) || d.kept(x, 1, z) || d.doorway(x, 1, z) || Math.abs(x) <= 2
                            || d.get(x, 0, z).is(Blocks.WAXED_COPPER_GRATE) || Math.abs(x) >= d.hx - 1 || Math.abs(z) >= d.hz - 1) {
                        ok = false;
                    }
                }
            }
            if (!ok) {
                continue;
            }
            List<int[]> cols = new ArrayList<>();
            for (int x = cx - ax; x <= cx + ax; x++) {
                for (int z = cz - az; z <= cz + az; z++) {
                    cols.add(new int[] {x, z});
                }
            }
            underFloor(d, cols);
            // 窓の縁 (切った銅)
            for (int x = cx - ax - 1; x <= cx + ax + 1; x++) {
                for (int z = cz - az - 1; z <= cz + az + 1; z++) {
                    if (x < cx - ax || x > cx + ax || z < cz - az || z > cz + az) {
                        d.put(x, 0, z, CUT);
                    }
                }
            }
            made++;
        }
    }

    // 北と南の厚い壁の遠景の窪み (幅 5・高さ 4・奥行き 3。手前に鉄格子、奥に雲海と遠くの避雷の塔)。殻の奥の 1 枚以上は残す
    private static void niches(Deco d) {
        List<int[]> spots = new ArrayList<>();
        // 北: 出口の間 (x ±2) の外。z = -hz-1 .. -hz-3 (殻は -hz-5 まで)
        for (int s = -1; s <= 1; s += 2) {
            int c = s * Math.max(6, d.hx / 2 + 1);
            if (Math.abs(c) + 2 <= d.hx) {
                spots.add(new int[] {c, -1});
            }
        }
        // 南: 入口の間 (x ±3、壁 ±4) の外。広い部屋だけ
        if (d.hx >= 10) {
            for (int s = -1; s <= 1; s += 2) {
                int c = s * Math.max(7, d.hx / 2 + 2);
                if (Math.abs(c) + 2 <= d.hx) {
                    spots.add(new int[] {c, 1});
                }
            }
        }
        for (int[] sp : spots) {
            int c = sp[0];
            int side = sp[1];
            boolean marked = false;
            for (int[] m : d.wallMarks) {
                if (m[0] == side && Math.abs(m[1] - c) <= 3) {
                    marked = true;
                }
            }
            if (marked) {
                continue;
            }
            int face = side * (d.hz + 1);
            // 前が仕掛けで埋まっていないか (柱が窪みの前に立つなら作らない)
            boolean blocked = false;
            for (int x = c - 3; x <= c + 3; x++) {
                if (d.nearGame(x, side * d.hz, 0) || d.nearGame(x, side * (d.hz - 1), 0)) {
                    blocked = true;
                }
            }
            if (blocked || d.h < 7) {
                continue;
            }
            int y0 = 2;
            int y1 = 5;
            for (int x = c - 2; x <= c + 2; x++) {
                for (int k = 0; k < 3; k++) {
                    int z = face + side * k;
                    for (int y = y0; y <= y1; y++) {
                        BlockState st;
                        if (k == 0) {
                            st = Blocks.IRON_BARS.defaultBlockState();
                        } else if (k == 2) {
                            st = d.cloud(x, y, z);
                        } else {
                            st = y == y0 ? d.cloud(x, y, z) : AIR;
                        }
                        d.put(x, y, z, st);
                    }
                }
                // 前の上下は白い羽の縁
                d.put(x, y1 + 1, face, QUARTZ);
                d.put(x, y0 - 1, face, QUARTZ);
            }
            // 雲海の上に遠くの避雷の塔 (1 ブロック幅) と、小さな塔
            d.put(c, y0 + 1, face + side, CUT);
            d.put(c, y0 + 2, face + side, rod(Direction.UP));
            d.put(c + (c > 0 ? -2 : 2), y0 + 1, face + side * 2, rod(Direction.UP));
            d.put(c - 1, y1, face + side, light(7));
            // 窪みの前には足さない (肉付けの外)
            d.keep(c - 3, side > 0 ? d.hz - 1 : -d.hz, c + 3, side > 0 ? d.hz : -d.hz + 1, y1 + 1);
        }
    }

    // 見えない灯り: 天井の下 (5 ブロックおき) と壁ぎわ (床 +3)。見える灯りは目印と戸だけに集める
    private static void hiddenLights(Deco d) {
        int ly = d.h - 1;
        for (int x = -d.hx + 2; x <= d.hx - 2; x += 5) {
            for (int z = -d.hz + 2; z <= d.hz - 2; z += 5) {
                if (d.get(x, ly, z).isAir() && !d.kept(x, ly, z)) {
                    d.put(x, ly, z, light(d.h >= 12 ? 13 : 12));
                }
                if (d.h >= 12 && d.get(x, 6, z).isAir() && !d.kept(x, 6, z) && Math.hypot(x, z) > 4) {
                    d.put(x, 6, z, light(11));
                }
            }
        }
        // 壁ぎわ: 広間の縁の内側 1 ブロック、床 +3、6 ブロックおき (EyeAdvance の陰の判定は床 +2 なので当たらない)
        for (int x = -d.hx; x <= d.hx; x += 6) {
            for (int z : new int[] {-d.hz, d.hz}) {
                if (d.get(x, 3, z).isAir()) {
                    d.put(x, 3, z, light(9));
                }
            }
        }
        for (int z = -d.hz + 3; z <= d.hz - 3; z += 6) {
            for (int x : new int[] {-d.hx, d.hx}) {
                if (d.get(x, 3, z).isAir()) {
                    d.put(x, 3, z, light(9));
                }
            }
        }
    }

    // 小道具 (壁ぎわ。近景): 抜けた羽 (白い絨毯)・雷に焼かれた銅 (緑青に黒い点)・配線の巻き (鎖と銅)・風見 (横向きの避雷針)。
    // 同じ物を等間隔に並べない (場所と種類は乱数でずらす)。仕掛け・戸の前・部屋の側の箱はよける
    private static void props(Deco d) {
        int placed = 0;
        for (int tries = 0; tries < 120 && placed < 10; tries++) {
            int side = d.rng.nextInt(4);
            int x;
            int z;
            Direction toWall;
            if (side < 2) {
                x = d.rng.nextInt(2 * d.hx - 1) - d.hx + 1;
                z = side == 0 ? -d.hz : d.hz;
                toWall = side == 0 ? Direction.NORTH : Direction.SOUTH;
            } else {
                z = d.rng.nextInt(2 * d.hz - 1) - d.hz + 1;
                x = side == 2 ? -d.hx : d.hx;
                toWall = side == 2 ? Direction.WEST : Direction.EAST;
            }
            if (d.doorway(x, 1, z) || d.kept(x, 1, z) || d.nearGame(x, z, 1) || d.locked(x, z)) {
                continue;
            }
            BlockState at = d.get(x, 1, z);
            if (!at.isAir() && !at.is(Blocks.WAXED_CUT_COPPER_STAIRS)) {
                continue;
            }
            int kind = d.rng.nextInt(4);
            Direction in = toWall.getOpposite();
            switch (kind) {
                case 0 -> {
                    // 抜けた羽: 壁ぎわと一つ手前に白い絨毯
                    d.put(x, 1, z, Blocks.WHITE_CARPET.defaultBlockState());
                    if (d.get(x + in.getStepX(), 1, z + in.getStepZ()).isAir() && !d.nearGame(x + in.getStepX(), z + in.getStepZ(), 1)) {
                        d.put(x + in.getStepX(), 1, z + in.getStepZ(), Blocks.WHITE_CARPET.defaultBlockState());
                    }
                }
                case 1 -> {
                    // 焼けた銅: 緑青の塊と黒い点
                    d.put(x, 1, z, Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState());
                    d.put(x, 2, z, Blocks.COAL_BLOCK.defaultBlockState());
                }
                case 2 -> {
                    // 配線の巻き: 切った銅の半ブロックと、上へ伸びる鎖
                    d.put(x, 1, z, Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState());
                    for (int y = 2; y <= 3; y++) {
                        d.put(x, y, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                    }
                    if (d.get(x, 4, z).isAir()) {
                        d.put(x, 4, z, CUT);
                    }
                }
                default -> {
                    // 風見: 壁から横へ出る避雷針 (床 +3)
                    if (d.get(x, 3, z).isAir() || d.get(x, 3, z).is(Blocks.LIGHT)) {
                        d.put(x, 3, z, rod(in));
                    }
                    if (at.is(Blocks.WAXED_CUT_COPPER_STAIRS)) {
                        break;
                    }
                    d.put(x, 1, z, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB.defaultBlockState());
                }
            }
            placed++;
        }
        // 凧 (空を渡る民の白い旗): 壁の高い所に二つ
        int kites = 0;
        for (int tries = 0; tries < 40 && kites < 2; tries++) {
            int z = d.rng.nextInt(2 * d.hz - 5) - d.hz + 3;
            int x = d.rng.nextBoolean() ? -d.hx : d.hx;
            int y = Math.min(d.h - 3, 5);
            int wx = x + (x < 0 ? -1 : 1);
            Direction in = x < 0 ? Direction.EAST : Direction.WEST;
            if (d.get(x, y, z).isAir() && d.get(wx, y, z).isSolidRender(d.level, d.o.offset(wx, y, z)) && !d.nearGame(x, z, 1) && !d.kept(x, y, z)) {
                d.put(x, y, z, Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, in));
                kites++;
            }
        }
    }

    // ---------------------------------------------------------------- 目印
    // SR1: 東の壁の羽の座。止まり木 (pz) の後ろの壁ぎわ (x = hx) に半円の扇 (半径 3)。骨は銅、届くと白に。要に金、縁に羽の先
    private static void fan(Deco d, int pz, boolean lit) {
        int x = d.hx;
        d.put(x, 1, pz, Blocks.GOLD_BLOCK.defaultBlockState());
        for (int[] p : fanRibs(pz)) {
            d.put(x, p[0], p[1], lit ? QUARTZ : CUT);
        }
        for (int[] p : fanRim(pz)) {
            BlockState s = d.get(x, p[0], p[1]);
            if (s.isAir() || s.equals(d.wallBase)) {
                d.put(x, p[0], p[1], QUARTZ);
            }
        }
        // 羽の先の下向きの階段 (縁の外の上 = 天井へ向かう羽)
        d.put(x - 1, 5, pz, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
    }

    // 扇の骨 (y, z): 要 (1, pz) から 20〜160 度の 5 本、長さ 1〜3
    private static List<int[]> fanRibs(int pz) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(20 + k * 35);
            for (int r = 1; r <= 3; r++) {
                int y = 1 + (int) Math.round(Math.sin(a) * r);
                int z = pz + (int) Math.round(Math.cos(a) * r);
                if (seen.add(Deco.key(y, z)) && !(y == 1 && z == pz)) {
                    out.add(new int[] {y, z});
                }
            }
        }
        return out;
    }

    private static List<int[]> fanRim(int pz) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int a = 0; a <= 180; a += 10) {
            double t = Math.toRadians(a);
            int y = 1 + (int) Math.round(Math.sin(t) * 4);
            int z = pz + (int) Math.round(Math.cos(t) * 4);
            if (y >= 1 && seen.add(Deco.key(y, z))) {
                out.add(new int[] {y, z});
            }
        }
        return out;
    }

    // SR1: 止まり木 pz に羽が届いた (lit) / 戻った。扇の骨を白/銅に
    public static void fanLit(RaidCtx ctx, int pz, boolean lit) {
        RaidKit.Cell c = ctx.cell();
        if (c == null) {
            return;
        }
        for (int[] p : fanRibs(pz)) {
            BlockPos at = c.origin().offset(c.hx(), p[0], p[1]);
            BlockState s = ctx.level().getBlockState(at);
            if (s.is(Blocks.WAXED_CUT_COPPER) || s.is(Blocks.SMOOTH_QUARTZ)) {
                ctx.level().setBlock(at, lit ? QUARTZ : CUT, FLAG);
            }
        }
    }

    // 吊りの避雷の尖塔: 天井から tipY まで (上は 3×3 の銅と羽の肩、十字の彫りの銅、細い柱、点いた電球、先は下向きの避雷針)。
    // big = 太い (闘技場・雷獣の間)
    private static void spire(Deco d, int cx, int cz, int tipY, boolean big) {
        int top = d.h;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean corner = dx != 0 && dz != 0;
                if (corner) {
                    Direction toward = Math.abs(dx) > 0 ? (dx > 0 ? Direction.WEST : Direction.EAST) : Direction.NORTH;
                    d.put(cx + dx, top, cz + dz, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toward).setValue(StairBlock.HALF, Half.TOP));
                } else {
                    d.put(cx + dx, top, cz + dz, GLEAM);
                }
                d.put(cx + dx, top + 1, cz + dz, GLEAM);
            }
        }
        if (big) {
            for (int[] p : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                d.put(cx + p[0], top, cz + p[1], Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
        }
        int y = top - 1;
        if (y > tipY) {
            d.put(cx, y, cz, CHISELED);
            for (int[] p : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                d.put(cx + p[0], y, cz + p[1], big ? CUT : Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
            y--;
        }
        boolean bulb = false;
        while (y > tipY) {
            d.put(cx, y, cz, !bulb && y <= tipY + 2 ? BULB.setValue(BlockStateProperties.LIT, true) : GLEAM);
            bulb |= y <= tipY + 2;
            y--;
        }
        d.put(cx, tipY, cz, rod(Direction.DOWN));
        d.keep(cx - 1, cz - 1, cx + 1, cz + 1, d.h);
    }

    // SR2: 灯のタイル (盤の相対 x, z) の後ろの壁 (殻) に、床から上る配線と電球。電球は灯がつながると点く
    private static void lampConduit(Deco d, int x, int z) {
        BlockPos b = conduitBulb(d.cell, x, z);
        int bx = b.getX() - d.o.getX();
        int bz = b.getZ() - d.o.getZ();
        int by = b.getY() - d.o.getY();
        for (int y = 1; y < by; y++) {
            if (!d.get(bx, y, bz).is(Blocks.IRON_BARS)) {
                d.put(bx, y, bz, y % 2 == 0 ? CHISELED : GLEAM);
            }
        }
        d.put(bx, by, bz, BULB);
        // 電球のまわりに羽の白の小さな枠
        int sx = bz == -d.hz - 1 || bz == d.hz + 1 ? 1 : 0;
        int sz = 1 - sx;
        d.put(bx + sx, by, bz + sz, QUARTZ);
        d.put(bx - sx, by, bz - sz, QUARTZ);
        d.put(bx, by + 1, bz, QUARTZ);
    }

    // 灯のタイル (x, z) の配線の電球の場所 (殻の中。ワールドの座標)
    static BlockPos conduitBulb(RaidKit.Cell c, int x, int z) {
        int hx = c.hx();
        int hz = c.hz();
        int dxw = hx - Math.abs(x);
        int dzw = hz - Math.abs(z);
        int wx;
        int wz;
        if (dxw <= dzw) {
            wx = x < 0 ? -hx - 1 : hx + 1;
            wz = z;
        } else {
            wx = x;
            wz = z < 0 ? -hz - 1 : hz + 1;
            if (Math.abs(wx) <= 2) {
                wx = wx < 0 ? -3 : 3;
            }
        }
        return c.origin().offset(wx, 5, wz);
    }

    // SR2: 灯 (x, z) がつながった / 切れた
    public static void lampLit(RaidCtx ctx, int x, int z, boolean on) {
        RaidKit.Cell c = ctx.cell();
        if (c == null) {
            return;
        }
        setBulb(ctx.level(), conduitBulb(c, x, z), on);
    }

    // SR4: 鍵の台の縁 (床の 5×5 の枠) と、天井の雲の切れ目 (エンドロッドと黄のガラスの輪)
    private static void keyHalo(Deco d, int kx, int kz) {
        for (int x = kx - 2; x <= kx + 2; x++) {
            for (int z = kz - 2; z <= kz + 2; z++) {
                if ((Math.abs(x - kx) == 2 || Math.abs(z - kz) == 2) && d.inArena(x, z) && !d.locked(x, z)) {
                    d.put(x, 0, z, (x + z & 1) == 0 ? CUT : CHISELED);
                }
            }
        }
        for (int x = kx - 2; x <= kx + 2; x++) {
            for (int z = kz - 2; z <= kz + 2; z++) {
                if (!d.inArena(x, z)) {
                    continue;
                }
                for (int y = d.h - 1; y <= d.h; y++) {
                    BlockState s = d.get(x, y, z);
                    if (s.is(Blocks.WHITE_WOOL) || s.is(Blocks.WHITE_CONCRETE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.YELLOW_STAINED_GLASS)) {
                        d.put(x, y, z, AIR);
                    }
                }
                int r = Math.max(Math.abs(x - kx), Math.abs(z - kz));
                if (r == 2) {
                    d.put(x, d.h, z, d.cloud(x, d.h, z));
                } else if (r == 1) {
                    d.put(x, d.h + 1, z, WHITE);
                }
            }
        }
        d.put(kx, d.h + 1, kz, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
        d.put(kx, d.h, kz, endRod(Direction.DOWN));
        d.keep(kx - 2, kz - 2, kx + 2, kz + 2, d.h);
    }

    // SR6: 天井の嵐の目 (三本の腕の渦の雲。中心は開いて光る)
    private static void eye(Deco d) {
        int y = d.h;
        for (int x = -d.hx; x <= d.hx; x++) {
            for (int z = -d.hz; z <= d.hz; z++) {
                double r = Math.hypot(x, z);
                BlockState s = d.get(x, y, z);
                boolean cloudy = s.is(Blocks.WHITE_WOOL) || s.is(Blocks.WHITE_CONCRETE) || s.is(Blocks.SNOW_BLOCK);
                if (r < 3.5) {
                    if (cloudy || s.is(Blocks.YELLOW_STAINED_GLASS)) {
                        d.put(x, y, z, AIR);
                    }
                    continue;
                }
                double a = Math.atan2(z, x);
                double sw = Math.sin(3 * a + r * 0.55);
                if (d.gameTop[x + d.hx][z + d.hz] >= d.h - 1) {
                    continue;
                }
                if (sw > 0.25 && r < d.hx + 0.5) {
                    if (s.isAir()) {
                        d.put(x, y, z, d.cloud(x, y, z));
                    }
                    if (sw > 0.8 && r > 5 && d.get(x, y - 1, z).isAir()) {
                        d.put(x, y - 1, z, d.cloud(x, y - 1, z));
                    }
                } else if (cloudy && sw < -0.2) {
                    d.put(x, y, z, AIR);
                }
            }
        }
        // 目の中心: 天井の板に真珠の光の十字と白い輪
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double r = Math.hypot(x, z);
                if (r <= 1.01) {
                    d.put(x, d.h + 1, z, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
                } else if (r < 3.3) {
                    d.put(x, d.h + 1, z, r < 2.3 ? WHITE : QUARTZ);
                }
            }
        }
        d.put(0, d.h, 0, endRod(Direction.DOWN));
        d.keep(-3, -3, 3, 3, d.h);
    }

    // 出口の戸の枠: 殻の戸のまわりを銅の枠に、枠の上の二つの電球 (閉じている間は消えている。越えると灯る)。protrude = 広間へ出る柱と梁
    private static void exitFrame(Deco d, boolean protrude) {
        int z = -d.hz - 1;
        for (int y = 1; y <= 5; y++) {
            d.put(-2, y, z, y == 5 ? BULB : y % 2 == 0 ? CHISELED : GLEAM);
            d.put(2, y, z, y == 5 ? BULB : y % 2 == 0 ? CHISELED : GLEAM);
        }
        for (int x = -1; x <= 1; x++) {
            d.put(x, 4, z, x == 0 ? CHISELED : GLEAM);
            d.put(x, 5, z, QUARTZ);
        }
        if (!d.get(0, 6, z).is(Blocks.WAXED_COPPER_BULB) && d.h >= 6) {
            // 壁と壁の間に挟まる段は、どちら向きにも上り下りの筋が通らないので、半ブロックの縁にする (向きを持たない)
            d.put(0, 6, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
        }
        if (protrude && !d.nearGame(2, -d.hz, 1) && !d.nearGame(-2, -d.hz, 1)) {
            for (int s = -1; s <= 1; s += 2) {
                for (int y = 1; y <= 4; y++) {
                    d.putAir(s * 2, y, -d.hz, y == 4 ? Blocks.WAXED_CUT_COPPER_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                            .setValue(StairBlock.HALF, Half.TOP) : y == 1 ? GLEAM : Blocks.WAXED_COPPER_GRATE.defaultBlockState());
                }
            }
            for (int x = -1; x <= 1; x++) {
                d.putAir(x, 4, -d.hz, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
        }
        // 出口の間の奥: 次の部屋の色を一つ (切った銅の床の筋と、消えた電球)
        int xz1 = -d.hz - 2;
        for (int k = 0; k < 3; k++) {
            d.put(0, 0, xz1 - k, CUT);
        }
    }

    // 出口の枠の電球を点ける / 消す (部屋が越えたとき・やり直しのとき)
    public static void exitLit(RaidCtx ctx, boolean on) {
        RaidKit.Cell c = ctx.cell();
        if (c == null) {
            return;
        }
        for (int s = -1; s <= 1; s += 2) {
            setBulb(ctx.level(), c.origin().offset(s * 2, 5, -c.hz() - 1), on);
        }
    }

    private static void setBulb(ServerLevel level, BlockPos at, boolean on) {
        BlockState s = level.getBlockState(at);
        if (s.is(Blocks.WAXED_COPPER_BULB) && s.getValue(BlockStateProperties.LIT) != on) {
            level.setBlock(at, s.setValue(BlockStateProperties.LIT, on), FLAG);
        }
    }

    // SR5: 拍子の見本の電球 (板 i に対応。北の殻の高い所の 3×3)
    static BlockPos mirrorPos(RaidKit.Cell c, int i) {
        int tx = i % BeatTiles.N;
        int tz = i / BeatTiles.N;
        return c.origin().offset((tx - 1) * 2, c.height() - tz, -c.hz() - 1);
    }

    // SR5: 板 i が光った (見せる・踏んだ) / 消えた
    public static void beatMirror(RaidCtx ctx, int i, boolean on) {
        RaidKit.Cell c = ctx.cell();
        if (c != null) {
            setBulb(ctx.level(), mirrorPos(c, i), on);
        }
    }

    // 見渡し台 (智の部屋): 入口の間の上 (南の厚い壁の中) に細長い台。広間の側は腰の高さの格子とガラス。入口の間の東の壁ぎわの梯子で上る
    private static void overlook(Deco d) {
        int fy = 5;
        int zr = d.hz + 1;
        for (int x = -4; x <= 3; x++) {
            for (int z = zr + 1; z <= zr + 2; z++) {
                for (int y = fy + 1; y <= d.h; y++) {
                    d.put(x, y, z, AIR);
                }
                d.put(x, fy, z, (x & 1) == 0 ? CUT : GLEAM);
            }
            d.put(x, fy + 1, zr, GRATE);
            for (int y = fy + 2; y <= d.h; y++) {
                d.put(x, y, zr, Blocks.GLASS.defaultBlockState());
            }
        }
        // 梯子: 入口の間の東 (x = 3) の、休み場より北の列。上の段まで (東の壁 x = 4 に掛ける)
        int lz = d.hz + 3;
        for (int y = 1; y <= fy + 1; y++) {
            d.put(3, y, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        }
        d.put(-4, d.h, zr + 2, light(9));
        d.put(-2, fy + 1, zr + 2, Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState());
        // 台の前の広間の側 (南の壁の上) には肉付け・雲を足さない
        d.keep(-6, d.hz - 2, 5, d.hz, d.h);
    }

    // 入口の間 (前室): 低く暗く。天井の蛙灯をやめ、見えない弱い灯りに。西の碑のそばに場面 (焼けた銅と倒れた避雷針、抜けた羽)、東に凧
    private static void vestibule(Deco d, boolean ladder) {
        int ez0 = d.hz + 2;
        if (d.get(0, 4, ez0 + 2).is(d.cell.palette().light().getBlock())) {
            d.put(0, 4, ez0 + 2, light(8));
        }
        // 場面: 北西の隅 (碑の北) に焼けた銅と黒い点、床に倒れた避雷針、白い絨毯
        d.put(-3, 1, ez0, Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState());
        d.put(-3, 2, ez0, Blocks.COAL_BLOCK.defaultBlockState());
        d.put(-3, 1, ez0 + 1, rod(Direction.SOUTH));
        d.put(-3, 1, ez0 + 3, Blocks.WHITE_CARPET.defaultBlockState());
        d.put(-2, 1, ez0 + 3, Blocks.WHITE_CARPET.defaultBlockState());
        // 東: 凧 (白い旗) と配線の巻き
        d.put(3, 3, ez0 + 3, Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST));
        if (!ladder) {
            d.put(3, 1, ez0, Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState());
            d.put(3, 2, ez0, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            d.put(3, 3, ez0, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            d.put(3, 4, ez0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // 入口の戸の内側の枠 (入口の間の側): 戸の両脇に羽の白
        for (int y = 1; y <= 4; y++) {
            d.put(-2, y, ez0, y == 4 ? QUARTZ : WHITE);
            d.put(2, y, ez0, y == 4 ? QUARTZ : WHITE);
        }
        for (int x = -1; x <= 1; x++) {
            d.put(x, 4, ez0, QUARTZ);
        }
    }

    // ---------------------------------------------------------------- 仕上げ
    private static Method setCell;

    // 部屋の箱を、床の下の雲の層まで広げる (片付け・保護が雲の層も含むように)。数を残す
    private static void finish(Deco d) {
        RaidKit.Cell c = d.ctx.cell();
        AABB b = c.bounds();
        if (d.minY < b.minY) {
            AABB nb = new AABB(b.minX, d.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
            RaidKit.Cell n = new RaidKit.Cell(c.origin(), c.hx(), c.hz(), c.height(), c.rest(), c.exitSpot(), c.inner(), nb, c.entryGate(),
                    c.exitGate(), c.palette());
            try {
                if (setCell == null) {
                    Method m = RaidCtx.class.getDeclaredMethod("setCell", RaidKit.Cell.class);
                    m.setAccessible(true);
                    setCell = m;
                }
                setCell.invoke(d.ctx, n);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("StormRoomDeco: cannot widen the cell bounds", e);
            }
        }
        LAST.put(d.ctx.roomId(), new Stats(d.blocks, d.bright, d.blockEntities, 0, d.minY));
    }
}
