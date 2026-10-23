package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 潮殻のレイドの部屋の飾り (docs/design/39 の 4.2「潮殻」: 珊瑚・貝殻・真珠。水の仕掛けはなし)。潮殻の部屋 (TR1・TR3〜TR7・中ボスの間 TRM) だけが使う。
//
//   RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, TideBuild.PALETTE));
//   ... 部屋の仕掛けを建てる ...
//   TideBuild d = new TideBuild(ctx);
//   d.keep(...);            // 仕掛け・通り道・予告の床・湧く所の箱 (原点からの相対。飾りはここへ入らない)
//   d.shell(2);             // 殻の濃淡 (下 = 深い海の青緑 → 潮の線の水色 → 上 = 真珠層の白)・扇の畝の壁・天井・床・戸の枠・入口の間と出口の間
//   d.hiddenLights(11, 4, false); // 見えない灯り (天井の格子の灯りの代わり)
//   ... 目印 (fan / spiral / coralArch など) と小道具 ...
//   d.finish();             // 費用を記録する (TideBuild.last(id))
//
// 決まり:
//   - 殻 (部屋の外へ面する壁・天井・床) は、満ちたブロック (不透明の立方体) を別の満ちたブロックへ塗り替えるだけ。穴は開けない (外の虚空は見えない)
//   - 飾りは広間の「空気」の所にだけ置く (仕掛けのブロックを上書きしない)。keep の箱の中には置かない
//   - 形は部屋の id から作った種で決まる (挑戦ごとに変えない。ctx.rng() は使わないので、部屋の配置の乱数の順も変わらない)
//   - 生きた珊瑚・水・昆布・落ちるブロック・支えの要るブロック (珊瑚の扇・海の漬物・絨毯。片付けで拾える物を落とす)・額縁などのエンティティは使わない。珊瑚の色はテラコッタで、干上がった礁は死んだ珊瑚で
//   - 赤は危険の色なので飾りに使わない (桃のテラコッタも赤く見えるので使わない)。金・黄は使わない。青白く光る物を飾りに置かない
//   - 費用の上限 (docs/design/39 の 6 章): 飾り 8 千ブロック・明るさ 12 以上の飾り 40・ブロックエンティティ 24・エンティティ 16
public final class TideBuild {
    // ---------------------------------------------------------------- 色 (docs/design/39 の 4.2 の表)
    static final BlockState FLOOR = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    static final BlockState FLOOR_MIX = Blocks.SANDSTONE.defaultBlockState();
    static final BlockState FLOOR_CUT = Blocks.CUT_SANDSTONE.defaultBlockState();
    static final BlockState FLOOR_MARK = Blocks.CHISELED_SANDSTONE.defaultBlockState();
    static final BlockState DEEP = Blocks.DARK_PRISMARINE.defaultBlockState();
    static final BlockState DEEP_MIX = Blocks.PRISMARINE.defaultBlockState();
    static final BlockState MID = Blocks.PRISMARINE.defaultBlockState();
    static final BlockState MID_MIX = Blocks.PRISMARINE_BRICKS.defaultBlockState();
    static final BlockState TIDE = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
    static final BlockState TIDE_MIX = Blocks.CYAN_TERRACOTTA.defaultBlockState();
    static final BlockState NACRE = Blocks.CALCITE.defaultBlockState();
    static final BlockState NACRE_MIX = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    static final BlockState NACRE_TINT = Blocks.CHERRY_PLANKS.defaultBlockState();
    static final BlockState CEIL = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    static final BlockState CEIL_MIX = Blocks.CALCITE.defaultBlockState();
    static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    static final BlockState QUARTZ_MARK = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    static final BlockState QUARTZ_BRICK = Blocks.QUARTZ_BRICKS.defaultBlockState();
    static final BlockState PEARL = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    // 珊瑚の色 (桃のテラコッタは赤に見えるので使わない。赤は危険の色): 桜の板 (淡い桃)・紅のテラコッタ・紫珀・桃の羊毛 (枝の先の鮮やかな桃)
    static final BlockState[] CORAL = {Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.MAGENTA_TERRACOTTA.defaultBlockState(),
            Blocks.PINK_WOOL.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState()};
    static final BlockState[] DEAD = {Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState()};
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // 潮殻の部屋の色づけ (RaidKit.buildCell に渡す)。床 = 滑らかな砂岩、縁 = 切った砂岩、殻 = 暗い海晶、天井 = 滑らかな石英、灯り = 真珠の蛙の光、戸 = 鉄格子
    public static final RaidKit.Palette PALETTE = new RaidKit.Palette(FLOOR, FLOOR_CUT, DEEP, CEIL, PEARL, Blocks.IRON_BARS.defaultBlockState());

    // 費用の上限 (docs/design/39 の 6 章)
    public static final int MAX_BLOCKS = 8000;
    public static final int MAX_BRIGHT = 40;
    public static final int MAX_BLOCK_ENTITIES = 24;
    public static final int MAX_ENTITIES = 16;

    // 部屋ごとの最後の費用 (自己テストが読む)
    public record Cost(int blocks, int bright, int blockEntities) {
    }

    private static final Map<String, Cost> LAST = new HashMap<>();

    public static Cost last(String roomId) {
        return LAST.get(roomId);
    }

    private final ServerLevel level;
    private final RaidKit.Cell cell;
    private final BlockPos o;
    private final String id;
    private final int hx;
    private final int hz;
    private final int h;
    private final ImprovedNoise noise;
    private final Random rnd;
    private final List<int[]> keep = new ArrayList<>();
    private int blocks;
    private int bright;
    private int blockEntities;

    public TideBuild(RaidCtx ctx) {
        this.level = ctx.level();
        this.cell = ctx.cell();
        this.o = cell.origin();
        this.id = ctx.roomId();
        this.hx = cell.hx();
        this.hz = cell.hz();
        this.h = cell.height();
        long seed = 0x7ADE5EEDL ^ ((long) id.hashCode() * 0x9E3779B97F4A7C15L);
        this.noise = new ImprovedNoise(RandomSource.create(seed));
        this.rnd = new Random(seed);
    }

    // ---------------------------------------------------------------- 基本
    // 飾りを入れない箱 (原点からの相対。角を含む)
    public TideBuild keep(int x0, int y0, int z0, int x1, int y1, int z1) {
        keep.add(new int[] {Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)});
        return this;
    }

    public boolean kept(int x, int y, int z) {
        for (int[] b : keep) {
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                return true;
            }
        }
        return false;
    }

    boolean inHall(int x, int y, int z) {
        return Math.abs(x) <= hx && Math.abs(z) <= hz && y >= 1 && y <= h;
    }

    BlockState at(int x, int y, int z) {
        return level.getBlockState(o.offset(x, y, z));
    }

    // 置く (同じなら何もしない)。費用に数える
    void put(int x, int y, int z, BlockState st) {
        BlockPos p = o.offset(x, y, z);
        if (level.getBlockState(p) == st) {
            return;
        }
        level.setBlock(p, st, Block.UPDATE_CLIENTS);
        blocks++;
        if (st.getLightEmission() >= 12) {
            bright++;
        }
        if (st.hasBlockEntity()) {
            blockEntities++;
        }
    }

    // 飾り: 広間の中の空気で、keep の外にだけ置く
    boolean deco(int x, int y, int z, BlockState st) {
        if (!inHall(x, y, z) || kept(x, y, z) || !at(x, y, z).isAir()) {
            return false;
        }
        put(x, y, z, st);
        return true;
    }

    // 目印: 広間の中の空気にだけ置く (keep の箱は見ない。目的の物の枠など、仕掛けのすぐ脇に置く物)
    boolean mark(int x, int y, int z, BlockState st) {
        if (!inHall(x, y, z) || !at(x, y, z).isAir()) {
            return false;
        }
        put(x, y, z, st);
        return true;
    }

    // 殻の塗り替え: 満ちた不透明のブロックを、満ちた不透明のブロックへだけ (戸・碑・空気は触らない)
    boolean paint(int x, int y, int z, BlockState st) {
        BlockState cur = at(x, y, z);
        if (cur.isAir() || cur.hasBlockEntity() || cur.is(Blocks.IRON_BARS) || !cur.isSolidRender(level, o.offset(x, y, z))) {
            return false;
        }
        put(x, y, z, st);
        return true;
    }

    // 床の模様: 床の材 (砂岩の仲間) の所だけ塗る
    boolean floorPut(int x, int z, BlockState st) {
        BlockState cur = at(x, 0, z);
        if (!(cur.is(Blocks.SMOOTH_SANDSTONE) || cur.is(Blocks.SANDSTONE) || cur.is(Blocks.CUT_SANDSTONE) || cur.is(Blocks.CALCITE))) {
            return false;
        }
        put(x, 0, z, st);
        return true;
    }

    double n3(double x, double y, double z) {
        return noise.noise(x, y, z);
    }

    // 二種を 7:3 で混ぜる (塊になるよう、細かい波で決める)
    boolean mix(int x, int y, int z) {
        return n3(x * 0.47 + 11.3, y * 0.47, z * 0.47 - 5.1) > 0.16;
    }

    // 潮の線の高さ (壁 3 の帯の下の段)
    int tideLine() {
        return Math.max(3, (int) Math.round(h * 0.55));
    }

    // 高さの濃淡: 下 = 深い海 (暗い海晶) → 海晶 → 潮の線 (水色) → 真珠層 (方解石・白・桃)
    BlockState band(int x, int y, int z, int tl) {
        double t = y + n3(x * 0.16, y * 0.2, z * 0.16) * 1.1;
        boolean m = mix(x, y, z);
        if (t < 2.5) {
            return m ? DEEP_MIX : DEEP;
        }
        if (t < tl - 0.3) {
            return m ? MID_MIX : MID;
        }
        if (t < tl + 1.7) {
            return m ? TIDE_MIX : TIDE;
        }
        if (m) {
            return n3(x * 0.9, y * 0.9 + 40, z * 0.9) > 0.25 ? NACRE_TINT : NACRE_MIX;
        }
        return NACRE;
    }

    // ---------------------------------------------------------------- 殻
    // 壁の四面 (内向きの向き)。u = 壁に沿う座標
    private enum Side {
        NORTH, SOUTH, WEST, EAST
    }

    private int span(Side s) {
        return s == Side.NORTH || s == Side.SOUTH ? hx : hz;
    }

    // 壁の面 (殻) の位置。d = 0 が殻、d >= 1 が広間の中へ d 段
    private int[] wallCell(Side s, int u, int y, int d) {
        return switch (s) {
            case NORTH -> new int[] {u, y, -hz - 1 + d};
            case SOUTH -> new int[] {u, y, hz + 1 - d};
            case WEST -> new int[] {-hx - 1 + d, y, u};
            case EAST -> new int[] {hx + 1 - d, y, u};
        };
    }

    // 戸の枠のまわり (北と南の壁の真ん中、幅 7・高さ 5) は肉付けしない
    private boolean nearGate(Side s, int u, int y) {
        return (s == Side.NORTH || s == Side.SOUTH) && Math.abs(u) <= 3 && y <= 5;
    }

    // 殻を建てる: 天井の格子の灯りを消し、壁を濃淡で塗り、扇の畝の肉付け (深さ 0〜reliefMax)、天井、床、戸の枠、入口の間・出口の間
    public TideBuild shell(int reliefMax) {
        int tl = tideLine();
        // 天井の格子の灯り (buildCell) は天井の材へ戻す。灯りは hiddenLights と目印に集める
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                BlockState c = at(x, h + 1, z);
                if (c.is(PALETTE.light().getBlock()) || c.is(Blocks.SEA_LANTERN)) {
                    put(x, h + 1, z, CEIL);
                }
            }
        }
        // 壁の面の濃淡と肉付け
        for (Side s : Side.values()) {
            int L = span(s);
            for (int u = -L - 1; u <= L + 1; u++) {
                for (int y = 0; y <= h; y++) {
                    int[] w = wallCell(s, u, y, 0);
                    paint(w[0], w[1], w[2], y == 0 ? DEEP : band(w[0], y, w[2], tl));
                }
            }
            if (reliefMax <= 0) {
                continue;
            }
            for (int u = -L; u <= L; u++) {
                for (int y = 1; y <= h; y++) {
                    if (nearGate(s, u, y)) {
                        continue;
                    }
                    double v = n3(u * 0.19, y * 0.27, s.ordinal() * 17.3);
                    // 帆立貝の扇の畝: 壁の床の真ん中から放射状に九本ほど
                    double ang = Math.atan2(u * 9.0 / Math.max(6, L), y + 3.0);
                    double rib = Math.sin(ang * 9.0);
                    int depth = (int) Math.round(0.45 + v * 1.3 + (rib > 0.5 ? 1.0 : 0.0));
                    if (y <= 1) {
                        depth = Math.max(depth, 1);
                    }
                    if (y >= h - 1) {
                        depth += 1;
                    }
                    depth = Math.max(0, Math.min(reliefMax, depth));
                    for (int d = 1; d <= depth; d++) {
                        int[] c = wallCell(s, u, y, d);
                        BlockState st = band(c[0], y, c[2], tl);
                        // 畝の背は一段明るく (方解石・石英)。潮の線より上だけ
                        if (d == depth && rib > 0.8 && y > tl + 1) {
                            st = (u + y) % 3 == 0 ? QUARTZ : NACRE;
                        }
                        if (!deco(c[0], c[1], c[2], st)) {
                            break;
                        }
                    }
                }
            }
        }
        // 天井: 滑らかな石英に方解石を混ぜ、ところどころ真珠の桃
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                BlockState c = at(x, h + 1, z);
                if (c.is(Blocks.SMOOTH_QUARTZ) || c.is(Blocks.DARK_PRISMARINE)) {
                    double v = n3(x * 0.21, 50, z * 0.21);
                    put(x, h + 1, z, v > 0.42 ? NACRE_TINT : mix(x, h + 1, z) ? CEIL_MIX : CEIL);
                }
            }
        }
        // 床: 滑らかな砂岩に砂岩を混ぜ、縁は切った砂岩、殻の欠片 (方解石) を少し
        paintFloor(-hx, hx, -hz, hz, true);
        // 戸の枠 (北 = 出口は灯りが入る。南 = 入口)
        gateFrame(true);
        gateFrame(false);
        // 入口の間 (低く暗い前室) と出口の間
        int ez0 = hz + 2;
        paintRoom(-3, 3, ez0, ez0 + 4, 4);
        paintFloor(-3, 3, ez0, ez0 + 4, false);
        entryScene(ez0);
        int xz1 = -hz - 2;
        paintRoom(-2, 2, xz1 - 2, xz1, 4);
        paintFloor(-2, 2, xz1 - 2, xz1, false);
        // 出口の間: 次の部屋の色 (珊瑚の枝) を一つだけ先に見せる
        put(-2, 1, xz1 - 2, CORAL[1]);
        put(-2, 2, xz1 - 2, CORAL[0]);
        put(2, 1, xz1 - 2, CORAL[3]);
        put(2, 2, xz1 - 2, rod(Direction.UP));
        return this;
    }

    private void paintFloor(int x0, int x1, int z0, int z1, boolean border) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockState st;
                if (border && (Math.abs(x) == hx || Math.abs(z) == hz)) {
                    st = FLOOR_CUT;
                } else if (hash(x, z) < 0.012) {
                    st = NACRE;
                } else {
                    st = n3(x * 0.23, -20, z * 0.23) > 0.2 ? FLOOR_MIX : FLOOR;
                }
                floorPut(x, z, st);
            }
        }
    }

    // 小部屋 (入口・出口の間) の壁を濃淡で塗る。天井は低く暗い海晶の煉瓦
    private void paintRoom(int x0, int x1, int z0, int z1, int top) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = x < x0 || x > x1 || z < z0 || z > z1;
                for (int y = 1; y <= top; y++) {
                    // 広間の北と南の殻の面 (戸の枠) は塗らない
                    if (edge && Math.abs(z) != hz + 1) {
                        paint(x, y, z, y == 1 ? DEEP : y == 2 ? (mix(x, y, z) ? MID_MIX : MID) : y == 3 ? TIDE : NACRE);
                    }
                }
                if (!edge) {
                    paint(x, top + 1, z, MID_MIX);
                }
            }
        }
    }

    // 入口の間の場面 (docs/design/39 の 3.8): 真珠採りの籠 (樽を二つ、上に殻の欠片)・吊った鎖の網・古い網 (蜘蛛の巣)。東の壁ぎわ (碑は西の壁)
    // 支えの要るブロック (珊瑚の扇・海の漬物・絨毯など) は使わない: 部屋を片付けるとき (下から空気に戻す) に支えを失って、拾える物を落とすため
    private void entryScene(int ez0) {
        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP);
        put(3, 1, ez0 + 1, barrel);
        put(3, 2, ez0 + 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        put(3, 1, ez0 + 2, barrel.setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.WEST));
        put(3, 1, ez0 + 3, Blocks.CALCITE.defaultBlockState());
        put(2, 1, ez0 + 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        put(3, 3, ez0 + 3, Blocks.CHAIN.defaultBlockState());
        put(3, 4, ez0 + 3, Blocks.CHAIN.defaultBlockState());
        put(3, 4, ez0 + 2, Blocks.COBWEB.defaultBlockState());
        put(-3, 4, ez0, Blocks.COBWEB.defaultBlockState());
        put(-3, 1, ez0, DEAD[2]);
    }

    // 戸の枠: 殻の面の 戸の両脇 (x = ±2、高さ 1〜4) と上 (高さ 4) を石英に、その外に桃の珊瑚の輪。北 (出口) は三つの灯りの目が入り、戸が開くと灯る
    private void gateFrame(boolean north) {
        int z = north ? -hz - 1 : hz + 1;
        for (int y = 1; y <= 4; y++) {
            paint(-2, y, z, QUARTZ_BRICK);
            paint(2, y, z, QUARTZ_BRICK);
        }
        paint(-1, 4, z, QUARTZ);
        paint(0, 4, z, QUARTZ_MARK);
        paint(1, 4, z, QUARTZ);
        for (int y = 1; y <= 5; y++) {
            paint(-3, y, z, y <= 2 ? MID_MIX : NACRE_TINT);
            paint(3, y, z, y <= 2 ? MID_MIX : NACRE_TINT);
        }
        for (int x = -2; x <= 2; x++) {
            paint(x, 5, z, x == 0 ? NACRE : NACRE_TINT);
        }
        if (north) {
            List<BlockPos> lamps = new ArrayList<>();
            for (int[] l : new int[][] {{-2, 3}, {2, 3}, {0, 4}}) {
                if (paint(l[0], l[1], z, LAMP_OFF)) {
                    lamps.add(o.offset(l[0], l[1], z));
                }
            }
            List<BlockPos> gate = cell.exitGate();
            if (!lamps.isEmpty() && !gate.isEmpty()) {
                watch(level, gate.get(gate.size() / 2), lamps);
            }
        }
    }

    // ---------------------------------------------------------------- 灯り
    // 見えない灯り (minecraft:light) の格子 (天井の格子の灯りの代わり。明るさ 12 未満なので「明るい塊」に数えない)。
    // 床を照らす層 = 高さ 3 (頭の上) に step ブロックおき (列ごとに半分ずらす)、明るさ lv。壁と天井を照らす層 = 天井の下に 2×step おき、明るさ lv - 2。
    // respectKeep = keep の箱の中には置かない (仕掛けが空気と入れ替えるブロックの所。苗・伸びる柱)
    public TideBuild hiddenLights(int lv, int step, boolean respectKeep) {
        int row = 0;
        for (int z = -hz + step / 2; z <= hz; z += step, row++) {
            for (int x = -hx + step / 2 + (row % 2) * (step / 2); x <= hx; x += step) {
                if (!(respectKeep && kept(x, 3, z))) {
                    light(x, 3, z, lv);
                }
            }
        }
        int top = h - 1;
        if (top > 6) {
            for (int x = -hx + step; x <= hx; x += 2 * step) {
                for (int z = -hz + step; z <= hz; z += 2 * step) {
                    if (!(respectKeep && kept(x, top, z))) {
                        light(x, top, z, Math.max(1, lv - 2));
                    }
                }
            }
        }
        return this;
    }

    // 見えない灯りを一つ (空気の所だけ。高さ 3 以上: 床の上 1〜2 段の空気を確かめる仕掛けに触れない)
    public boolean light(int x, int y, int z, int lv) {
        if (y < 3 || !inHall(x, y, z) || !at(x, y, z).isAir()) {
            return false;
        }
        put(x, y, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lv));
        return true;
    }

    static BlockState rod(Direction d) {
        return Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, d);
    }

    // ---------------------------------------------------------------- 珊瑚の枝・垂れ
    // 天井から垂れる珊瑚の房 (長さ 1〜maxLen)。根は天井に貼りつく白い殻 (十字)、下へ珊瑚、先の一部はエンドロッド (tips 本まで)。
    // 壁から 2〜5 の所に寄せる (広間の真ん中の上は空ける)。高さ minY より下へは垂らさない
    public TideBuild ceilingCoral(int count, int maxLen, int minY, int tips) {
        int placed = 0;
        for (int k = 0; k < count * 8 && placed < count; k++) {
            int x;
            int z;
            if (rnd.nextBoolean()) {
                x = (rnd.nextBoolean() ? 1 : -1) * (hx - 2 - rnd.nextInt(4));
                z = rnd.nextInt(2 * hz - 3) - hz + 2;
            } else {
                x = rnd.nextInt(2 * hx - 3) - hx + 2;
                z = (rnd.nextBoolean() ? 1 : -1) * (hz - 2 - rnd.nextInt(4));
            }
            int len = 1 + rnd.nextInt(maxLen);
            if (!at(x, h, z).isAir() || kept(x, h, z)) {
                continue;
            }
            // 根
            deco(x, h, z, NACRE_MIX);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (rnd.nextInt(3) > 0) {
                    deco(x + d.getStepX(), h, z + d.getStepZ(), rnd.nextBoolean() ? NACRE : NACRE_MIX);
                }
            }
            int y = h - 1;
            for (int i = 0; i < len && y >= minY; i++, y--) {
                if (!deco(x, y, z, CORAL[rnd.nextInt(CORAL.length)])) {
                    break;
                }
            }
            if (tips > 0 && y >= minY && deco(x, y, z, rod(Direction.DOWN))) {
                tips--;
            }
            placed++;
        }
        return this;
    }

    // 壁から伸びる珊瑚の枝 (斜めに一ブロックずつずらして、上と内へ)。高さ yMin より上から始める
    public TideBuild wallBranches(int count, int yMin, int tips) {
        Side[] sides = Side.values();
        int placed = 0;
        for (int k = 0; k < count * 8 && placed < count; k++) {
            Side s = sides[rnd.nextInt(4)];
            int L = span(s);
            int u = rnd.nextInt(2 * L - 3) - L + 2;
            int y = yMin + rnd.nextInt(Math.max(1, h - 1 - yMin));
            if (nearGate(s, u, y) || Math.abs(u) <= 4 && (s == Side.NORTH || s == Side.SOUTH)) {
                continue;
            }
            // 肉付けの先の、最初の空気
            int d = 1;
            while (d <= 4 && !at(wallCell(s, u, y, d)[0], y, wallCell(s, u, y, d)[2]).isAir()) {
                d++;
            }
            if (d > 4) {
                continue;
            }
            int len = 2 + rnd.nextInt(3);
            int side = rnd.nextBoolean() ? 1 : -1;
            int cu = u;
            int cy = y;
            int cd = d;
            boolean any = false;
            for (int i = 0; i < len; i++) {
                int[] c = wallCell(s, cu, cy, cd);
                if (!deco(c[0], c[1], c[2], CORAL[rnd.nextInt(CORAL.length)])) {
                    break;
                }
                any = true;
                if (i % 2 == 0) {
                    cy++;
                } else {
                    cu += side;
                }
                if (i == 1 || i == 3) {
                    cd++;
                }
                if (cy > h - 1) {
                    break;
                }
            }
            if (any && tips > 0) {
                int[] c = wallCell(s, cu, cy, cd);
                if (deco(c[0], c[1], c[2], rod(Direction.UP))) {
                    tips--;
                }
            }
            if (any) {
                placed++;
            }
        }
        return this;
    }

    // 干上がった潮だまり: 床の浅い窪み (半ブロック。海晶の板の底) と、縁に死んだ珊瑚の塊。壁から 2〜4 の所、keep の外
    public TideBuild pools(int count) {
        int placed = 0;
        for (int k = 0; k < count * 10 && placed < count; k++) {
            boolean xSide = rnd.nextBoolean();
            int sgn = rnd.nextBoolean() ? 1 : -1;
            int x = xSide ? sgn * (hx - 2 - rnd.nextInt(2)) : rnd.nextInt(2 * hx - 9) - hx + 4;
            int z = xSide ? rnd.nextInt(2 * hz - 9) - hz + 4 : sgn * (hz - 2 - rnd.nextInt(2));
            if (Math.abs(x) <= 4 && Math.abs(z) >= hz - 6) {
                continue;
            }
            boolean ok = true;
            for (int dx = -2; dx <= 2 && ok; dx++) {
                for (int dz = -2; dz <= 2 && ok; dz++) {
                    for (int y = 0; y <= 3; y++) {
                        if (kept(x + dx, Math.max(1, y), z + dz)) {
                            ok = false;
                        }
                    }
                    if (!at(x + dx, 1, z + dz).isAir() || !at(x + dx, 2, z + dz).isAir()) {
                        ok = false;
                    }
                }
            }
            if (!ok) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) == 2) {
                        floorPut(x + dx, z + dz, FLOOR_CUT);
                        continue;
                    }
                    put(x + dx, 0, z + dz, dx == 0 && dz == 0 ? Blocks.DARK_PRISMARINE_SLAB.defaultBlockState() : Blocks.PRISMARINE_SLAB.defaultBlockState());
                }
            }
            // 縁の干上がった礁 (壁の側)
            int wx = Math.abs(x) >= hx - 3 ? Integer.signum(x) * 2 : 0;
            int wz = wx == 0 ? Integer.signum(z) * 2 : 0;
            deco(x + wx, 1, z + wz, DEAD[rnd.nextInt(DEAD.length)]);
            placed++;
        }
        return this;
    }

    // 壁ぎわの殻の欠片の小山 (方解石・石英の半ブロック・死んだ珊瑚)。隅に寄せる
    public TideBuild shards(int count) {
        int placed = 0;
        for (int k = 0; k < count * 10 && placed < count; k++) {
            int cx = (rnd.nextBoolean() ? 1 : -1) * (hx - 1 - rnd.nextInt(3));
            int cz = (rnd.nextBoolean() ? 1 : -1) * (hz - 1 - rnd.nextInt(Math.max(1, hz - 5)));
            if (Math.abs(cx) <= 4 && Math.abs(cz) >= hz - 6 || kept(cx, 1, cz) || !at(cx, 1, cz).isAir() || at(cx, 0, cz).isAir()) {
                continue;
            }
            deco(cx, 1, cz, rnd.nextBoolean() ? NACRE : DEAD[rnd.nextInt(DEAD.length)]);
            Direction d = Direction.from2DDataValue(rnd.nextInt(4));
            deco(cx + d.getStepX(), 1, cz + d.getStepZ(), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            Direction e = d.getClockWise();
            deco(cx + e.getStepX(), 1, cz + e.getStepZ(), Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, d));
            placed++;
        }
        return this;
    }

    // ---------------------------------------------------------------- 目印
    // 帆立貝の扇 (北か南の殻の面。中心 (cx, cy) から上の半円、半径 r、rays 本の畝)。戸の枠のまわりは避ける。
    // 畝の背は広間へ一段出す。返り値: 畝ごとの中心の線の位置 (灯す物に使う。0 = 西の端の畝)
    public List<List<BlockPos>> fan(boolean north, int cx, int cy, int r, int rays) {
        Side s = north ? Side.NORTH : Side.SOUTH;
        List<List<BlockPos>> lines = new ArrayList<>();
        for (int i = 0; i < rays; i++) {
            lines.add(new ArrayList<>());
        }
        for (int u = cx - r; u <= cx + r; u++) {
            for (int y = Math.max(1, cy); y <= Math.min(h, cy + r); y++) {
                if (nearGate(s, u, y)) {
                    continue;
                }
                double dx = u - cx;
                double dy = y - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > r + 0.3) {
                    continue;
                }
                // 見る人 (広間の中) から見て左から右へ 0..rays-1。北の壁は西が左、南の壁は東が左
                double ang = Math.atan2(dy, north ? dx : -dx);
                int k = Math.max(0, Math.min(rays - 1, (int) ((Math.PI - ang) / (Math.PI / rays))));
                double within = (Math.PI - ang) / (Math.PI / rays) - k;
                BlockState st;
                if (dist > r - 0.7) {
                    st = QUARTZ;
                } else if (dist < 1.6) {
                    st = QUARTZ_MARK;
                } else {
                    st = k % 2 == 0 ? NACRE : NACRE_TINT;
                }
                int[] w = wallCell(s, u, y, 0);
                paint(w[0], w[1], w[2], st);
                boolean centre = Math.abs(within - 0.5) < 0.26;
                if (centre && dist >= 2 && dist <= r - 1) {
                    lines.get(k).add(o.offset(w[0], w[1], w[2]));
                }
                // 畝の背と縁を一段出す
                if ((k % 2 == 0 && centre && dist >= 2) || dist > r - 0.7) {
                    int[] f = wallCell(s, u, y, 1);
                    mark(f[0], f[1], f[2], dist > r - 0.7 ? Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP) : NACRE);
                }
            }
        }
        return lines;
    }

    // 巻貝の断面 (北か南の殻の面。中心 (cx, cy)、半径 r)。渦の隔壁は方解石で一段出し、室は桃と白を交互に、芯は真珠の光
    public TideBuild spiral(boolean north, int cx, int cy, int r, double turns) {
        Side s = north ? Side.NORTH : Side.SOUTH;
        double pitch = r / turns;
        for (int u = cx - r; u <= cx + r; u++) {
            for (int y = Math.max(1, cy - r); y <= Math.min(h, cy + r); y++) {
                if (nearGate(s, u, y)) {
                    continue;
                }
                double dx = (north ? 1 : -1) * (u - cx);
                double dy = y - cy;
                double rho = Math.sqrt(dx * dx + dy * dy);
                if (rho > r + 0.3) {
                    continue;
                }
                double th = Math.atan2(dy, dx);
                if (th < 0) {
                    th += Math.PI * 2;
                }
                double w = rho / pitch - th / (Math.PI * 2);
                double frac = w - Math.floor(w);
                int[] c = wallCell(s, u, y, 0);
                BlockState st;
                boolean septum = frac < 0.3;
                if (rho < 0.8) {
                    st = PEARL;
                } else if (rho < 1.6) {
                    st = QUARTZ_MARK;
                } else if (rho > r - 0.7) {
                    st = QUARTZ;
                } else if (septum) {
                    st = NACRE;
                } else {
                    st = ((int) Math.floor(w)) % 2 == 0 ? NACRE_TINT : NACRE_MIX;
                }
                paint(c[0], c[1], c[2], st);
                if ((septum && rho >= 1.6) || rho > r - 0.7) {
                    int[] f = wallCell(s, u, y, 1);
                    mark(f[0], f[1], f[2], rho > r - 0.7 ? QUARTZ : NACRE);
                }
            }
        }
        return this;
    }

    // 光の輪 (北か南の殻の面。中心 (cx, cy)、半径 r の輪)。返り値: 輪の上の「灯る目」(角度の順、count 個)
    public List<BlockPos> halo(boolean north, int cx, double cy, double r, int count) {
        Side s = north ? Side.NORTH : Side.SOUTH;
        int R = (int) Math.ceil(r + 1);
        for (int u = cx - R; u <= cx + R; u++) {
            for (int y = Math.max(1, (int) Math.floor(cy - R)); y <= Math.min(h, (int) Math.ceil(cy + R)); y++) {
                if (nearGate(s, u, y)) {
                    continue;
                }
                double dist = Math.hypot(u - cx, y - cy);
                if (Math.abs(dist - r) <= 0.6) {
                    int[] c = wallCell(s, u, y, 0);
                    paint(c[0], c[1], c[2], QUARTZ);
                } else if (Math.abs(dist - r - 1) <= 0.5) {
                    int[] c = wallCell(s, u, y, 0);
                    paint(c[0], c[1], c[2], NACRE_TINT);
                }
            }
        }
        List<BlockPos> eyes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // 下から時計回り (見る人から見て)
            double a = -Math.PI / 2 - (north ? 1 : -1) * Math.PI * 2 * i / count;
            int u = (int) Math.round(cx + Math.cos(a) * r);
            int y = (int) Math.round(cy + Math.sin(a) * r);
            if (y < 1 || y > h || nearGate(s, u, y)) {
                continue;
            }
            int[] c = wallCell(s, u, y, 0);
            if (paint(c[0], c[1], c[2], LAMP_OFF)) {
                eyes.add(o.offset(c[0], c[1], c[2]));
            }
        }
        return eyes;
    }

    // 珊瑚の門: 北の壁の前の列 (z = -hz) に、両脇 (x = ±foot) の床から立ち上がり、戸の上 (高さ top) で絡む枝のアーチ。戸の前は空ける
    public TideBuild coralArch(int foot, int top, int tips) {
        int z = -hz;
        for (int side = -1; side <= 1; side += 2) {
            int steps = 24;
            int lastX = Integer.MIN_VALUE;
            int lastY = Integer.MIN_VALUE;
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                int x = (int) Math.round(side * foot * Math.cos(t * Math.PI / 2));
                int y = (int) Math.round(1 + (top - 1) * Math.sin(t * Math.PI / 2));
                if (x == lastX && y == lastY) {
                    continue;
                }
                lastX = x;
                lastY = y;
                if (Math.abs(x) <= 2 && y <= 5) {
                    continue;
                }
                BlockState st = y <= 2 ? DEAD[Math.floorMod(x * 3 + y, DEAD.length)] : CORAL[Math.floorMod(x * 7 + y * 3, CORAL.length)];
                mark(x, y, z, st);
                mark(x + side, y, z, y <= 2 ? DEAD[Math.floorMod(x + y, DEAD.length)] : CORAL[Math.floorMod(x + y * 5, CORAL.length)]);
                // 枝分かれ: 三段ごとに外と手前へ一つ
                if (y % 3 == 0 && y > 2) {
                    mark(x + 2 * side, y + 1, z, CORAL[Math.floorMod(y, CORAL.length)]);
                    mark(x + side, y, z + 1, CORAL[Math.floorMod(y + 2, CORAL.length)]);
                    if (tips > 0 && mark(x + 2 * side, y + 2, z, rod(Direction.UP))) {
                        tips--;
                    }
                }
            }
        }
        // 頂の真珠 (門の要)
        mark(0, top, z, PEARL);
        mark(-1, top, z, NACRE);
        mark(1, top, z, NACRE);
        return this;
    }

    // 床の模様 ---------------------------------------------------------------
    // 南北の一本の筋 (入口から目的の物へ)
    public TideBuild floorLine(int x, int z0, int z1, BlockState st) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            floorPut(x, z, st);
        }
        return this;
    }

    // 床の輪 (中心 (cx, cz)、半径 r)
    public TideBuild floorRing(int cx, int cz, double r, BlockState st) {
        int R = (int) Math.ceil(r) + 1;
        for (int x = cx - R; x <= cx + R; x++) {
            for (int z = cz - R; z <= cz + R; z++) {
                if (Math.abs(Math.hypot(x - cx, z - cz) - r) < 0.5) {
                    floorPut(x, z, st);
                }
            }
        }
        return this;
    }

    // 床の渦の線 (巻貝の断面の模様。中心 (cx, cz)、半径 r、turns 巻き)
    public TideBuild floorSpiral(int cx, int cz, int r, double turns, BlockState st) {
        double pitch = r / turns;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double rho = Math.hypot(x - cx, z - cz);
                if (rho > r || rho < 1) {
                    continue;
                }
                double th = Math.atan2(z - cz, x - cx);
                if (th < 0) {
                    th += Math.PI * 2;
                }
                double w = rho / pitch - th / (Math.PI * 2);
                if (w - Math.floor(w) < 0.5 / pitch * 1.6) {
                    floorPut(x, z, st);
                }
            }
        }
        return this;
    }

    // 箱庭の窪み (東か西の壁。docs/design/39 の 3.4 の 1): 壁から三段せり出した殻の中に、奥へ小さくなる巻貝の塔の遠景と、手前の格子
    public TideBuild vista(boolean east, int cz, int y0) {
        int s = east ? 1 : -1;
        int wx = s * hx;
        // 外の殻 (幅 7・高さ 7・せり出し 3)
        for (int dz = -3; dz <= 3; dz++) {
            for (int y = 1; y <= y0 + 5; y++) {
                for (int d = 0; d < 3; d++) {
                    boolean inner = Math.abs(dz) <= 2 && y >= y0 && y <= y0 + 3;
                    int x = wx - s * d;
                    if (!inHall(x, y, cz + dz)) {
                        continue;
                    }
                    if (inner) {
                        // 窪みの中 (先に建った壁の肉付けもどける)
                        if (!at(x, y, cz + dz).isAir()) {
                            put(x, y, cz + dz, AIR);
                        }
                        continue;
                    }
                    BlockState st = (Math.abs(dz) == 3 || y == y0 + 5 || y == y0 - 1) ? QUARTZ : band(x, y, cz + dz, tideLine());
                    put(x, y, cz + dz, st);
                }
            }
        }
        // 奥: 巻貝の塔 (真ん中が高く、脇が低い = 遠近)
        int back = wx;
        for (int y = y0; y <= y0 + 3; y++) {
            put(back, y, cz, y == y0 + 3 ? NACRE_TINT : NACRE);
        }
        put(back, y0, cz - 1, NACRE_MIX);
        put(back, y0 + 1, cz - 1, NACRE_TINT);
        put(back, y0, cz + 1, NACRE_MIX);
        put(back, y0, cz - 2, CORAL[1]);
        put(back, y0, cz + 2, CORAL[2]);
        // 奥の空気に見えない灯り (暗め)
        int mid = wx - s;
        light(mid, y0 + 3, cz, 6);
        // 手前の格子 (鉄格子。南北につないだ姿で置く)
        int front = wx - s * 2;
        for (int dz = -2; dz <= 2; dz++) {
            for (int y = y0; y <= y0 + 3; y++) {
                BlockState bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, dz > -2).setValue(IronBarsBlock.SOUTH, dz < 2);
                mark(front, y, cz + dz, bars);
            }
        }
        return this;
    }

    // 珊瑚の頭 (柱の頭の飾り): 頭の高さ y の 2×2 (x, z〜x+1, z+1) を珊瑚に、上に小枝
    public TideBuild coralHead(int x, int y, int z) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockState cur = at(x + dx, y, z + dz);
                if (!cur.isAir()) {
                    put(x + dx, y, z + dz, CORAL[Math.floorMod((x + dx) * 5 + (z + dz) * 3 + y, CORAL.length)]);
                }
            }
        }
        int dx = Math.floorMod(x * 3 + z, 2);
        int dz = Math.floorMod(x + z * 7, 2);
        mark(x + dx, y + 1, z + dz, CORAL[Math.floorMod(x + z, CORAL.length)]);
        if (y + 2 < h) {
            mark(x + dx + (dx == 0 ? -1 : 1), y + 2, z + dz, CORAL[Math.floorMod(x * 2 + z, CORAL.length)]);
        }
        return this;
    }

    // 記録を終える (ログと自己テスト用の費用)
    public Cost finish() {
        Cost c = new Cost(blocks, bright, blockEntities);
        LAST.put(id, c);
        SkyAbyss.LOGGER.debug("Tide raid build {}: deco {} blocks, {} bright, {} block entities", id, blocks, bright, blockEntities);
        return c;
    }

    int hash(int x, int z) {
        long v = (x * 73856093L) ^ (z * 19349663L) ^ id.hashCode();
        v = (v ^ (v >>> 13)) * 0x5bd1e995L;
        return (int) ((v ^ (v >>> 15)) & 0xffff);
    }

    double hashD(int x, int z) {
        return hash(x, z) / 65536.0;
    }

    // ---------------------------------------------------------------- 灯る目 (出口の枠・光の輪など)
    static final BlockState LAMP_OFF = Blocks.CALCITE.defaultBlockState();
    static final BlockState LAMP_ON = PEARL;

    // 灯る目を n 個まで灯す (残りは消す)
    public static void lamps(ServerLevel level, List<BlockPos> eyes, int n) {
        for (int i = 0; i < eyes.size(); i++) {
            BlockPos p = eyes.get(i);
            BlockState want = i < n ? LAMP_ON : LAMP_OFF;
            BlockState cur = level.getBlockState(p);
            if (cur != want && (cur.is(LAMP_OFF.getBlock()) || cur.is(LAMP_ON.getBlock()))) {
                level.setBlock(p, want, Block.UPDATE_CLIENTS);
            }
        }
    }

    // 出口の枠の灯りの見張り: 出口の戸が開いている (戸の真ん中が空気) あいだ、枠の三つの目が灯る。部屋が片付いたら (目が別の物になったら) 見張りをやめる
    private record Watch(ServerLevel level, BlockPos probe, List<BlockPos> eyes) {
    }

    private static final List<Watch> WATCH = new ArrayList<>();

    private static void watch(ServerLevel level, BlockPos probe, List<BlockPos> eyes) {
        WATCH.removeIf(w -> w.level() == level && w.probe().equals(probe));
        WATCH.add(new Watch(level, probe, eyes));
    }

    // 試験用: 見張っている出口の数
    public static int watched() {
        return WATCH.size();
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        private Events() {
        }

        @SubscribeEvent
        static void onTick(ServerTickEvent.Post event) {
            if (WATCH.isEmpty() || event.getServer().getTickCount() % 5 != 0) {
                return;
            }
            pollWatch();
        }

        @SubscribeEvent
        static void onStopped(ServerStoppedEvent event) {
            WATCH.clear();
            LAST.clear();
        }
    }

    // 見張りを一度回す (5 tick おき。自己テストからも)
    public static void pollWatch() {
        for (Iterator<Watch> it = WATCH.iterator(); it.hasNext();) {
            Watch w = it.next();
            if (!w.level().isLoaded(w.probe())) {
                it.remove();
                continue;
            }
            BlockState e0 = w.level().getBlockState(w.eyes().get(0));
            if (!(e0.is(LAMP_OFF.getBlock()) || e0.is(LAMP_ON.getBlock()))) {
                it.remove();
                continue;
            }
            boolean open = w.level().getBlockState(w.probe()).isAir();
            lamps(w.level(), w.eyes(), open ? w.eyes().size() : 0);
        }
    }

    // ---------------------------------------------------------------- 監査 (自己テスト)
    // 建てた部屋を調べる: 明るい塊・ブロックエンティティ・飾りのエンティティの数、殻の外側の層が全部不透明か、入口の休み場から出口と targets まで歩けるか
    public record Audit(int bright, int blockEntities, int entities, String leak, List<BlockPos> unreachable) {
    }

    public static Audit audit(ServerLevel level, RaidKit.Cell cell, List<BlockPos> targets) {
        AABB b = cell.bounds();
        int x0 = (int) b.minX;
        int y0 = (int) b.minY;
        int z0 = (int) b.minZ;
        int x1 = (int) b.maxX - 1;
        int y1 = (int) b.maxY - 1;
        int z1 = (int) b.maxZ - 1;
        int bright = 0;
        int bes = 0;
        String leak = null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    BlockState st = level.getBlockState(m);
                    if (st.isAir()) {
                        if (leak == null && (x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1)) {
                            leak = m.toShortString();
                        }
                        continue;
                    }
                    if (st.getLightEmission() >= 12) {
                        bright++;
                    }
                    if (st.hasBlockEntity()) {
                        bes++;
                    }
                    boolean outer = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    if (outer && leak == null && !st.isSolidRender(level, m)) {
                        leak = m.toShortString() + " " + st;
                    }
                }
            }
        }
        int ents = 0;
        // 飾りのエンティティ (額縁・絵・防具立て・表示のエンティティ)
        for (Entity e : level.getEntities((Entity) null, b, e -> e instanceof net.minecraft.world.entity.decoration.HangingEntity
                || e instanceof net.minecraft.world.entity.decoration.ArmorStand || e instanceof net.minecraft.world.entity.Display)) {
            ents++;
        }
        // 歩けるか: 休み場から、戸を開けた姿で
        Set<BlockPos> gates = new HashSet<>(cell.entryGate());
        gates.addAll(cell.exitGate());
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(cell.rest());
        seen.add(cell.rest().asLong());
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos t = p.offset(d.getStepX(), dy, d.getStepZ());
                    if (!b.contains(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5) || seen.contains(t.asLong())) {
                        continue;
                    }
                    if (!open(level, t, gates) || !open(level, t.above(), gates) || !stand(level, t, gates)) {
                        continue;
                    }
                    if (dy == 1 && !open(level, p.above(2), gates)) {
                        continue;
                    }
                    if (dy < 0) {
                        boolean clear = true;
                        for (int k = 0; k >= dy + 1 && clear; k--) {
                            BlockPos c = p.offset(d.getStepX(), k, d.getStepZ());
                            clear = open(level, c, gates) && open(level, c.above(), gates);
                        }
                        if (!clear) {
                            continue;
                        }
                    }
                    seen.add(t.asLong());
                    q.add(t);
                    break;
                }
            }
        }
        List<BlockPos> miss = new ArrayList<>();
        List<BlockPos> all = new ArrayList<>(targets);
        all.add(0, cell.exitSpot());
        for (BlockPos t : all) {
            boolean near = false;
            for (int dx = -1; dx <= 1 && !near; dx++) {
                for (int dz = -1; dz <= 1 && !near; dz++) {
                    for (int dy = -1; dy <= 2 && !near; dy++) {
                        near = seen.contains(t.offset(dx, dy, dz).asLong());
                    }
                }
            }
            if (!near) {
                miss.add(t);
            }
        }
        return new Audit(bright, bes, ents, leak, miss);
    }

    private static boolean open(ServerLevel level, BlockPos p, Set<BlockPos> gates) {
        if (gates.contains(p)) {
            return true;
        }
        VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
        return s.isEmpty() || s.max(Direction.Axis.Y) <= 0.5;
    }

    private static boolean stand(ServerLevel level, BlockPos feet, Set<BlockPos> gates) {
        BlockPos below = feet.below();
        if (gates.contains(below)) {
            return false;
        }
        VoxelShape here = level.getBlockState(feet).getCollisionShape(level, feet);
        if (!here.isEmpty()) {
            return true;
        }
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}
