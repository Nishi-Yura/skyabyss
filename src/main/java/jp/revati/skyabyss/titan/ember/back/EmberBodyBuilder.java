package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanBodyBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 余燼の背の体と広間を建てる (13 の 3 章、18 章)。サーバーの起動時に一度だけ (版が変わったら建て直す)。
// 順番: 体 (body.bin。中は凝灰岩で詰まっている) → 口・見送りの段 → 喉の坂 → 炉心の広間と大きな炉 → 広間から三つの試練の入口への通路 →
//       試練の箱 (中は空気、仮の床) と通り道 → 熾の底の門の下の螺旋・前室・熾の底の箱 → 背の亀裂 → 火の井戸 (壁の螺旋・井戸の螺旋・
//       背の上への出口・火口の縁への段) → 掘った所の内壁を「肉と骨の壁」に (skin) → 各担当の部屋の中身 (EmberBackRooms) →
//       休み場・熾り石・光る床・碑文・入口の看板・灰溜まり (取っておく場所。担当の中身より後)
// 熾の底の門・石・光る床・ピースフルの封印の開け閉めは試練の側 (EmberBackTrials) が置く
public final class EmberBodyBuilder {
    public static final TitanBodyBlocks BODY = new TitanBodyBlocks(EmberIds.BODY_BIN);
    // 熾の底の門の下の螺旋の段の並び (中心の柱のまわり 8 マス。潮殻の SPIRAL_RING と同じ並び)
    public static final int[][] RING = {{-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}};
    // 門の螺旋: 最初の段は南の真ん中 (並びの 7 番)、y 84 から 1 段ずつ y 51 まで
    public static final int GATE_STEP_START = 7;
    public static final int GATE_STEPS = 84 - (FLOOR_HEART + 1) + 1;
    // 壁の螺旋 (広間の床 85 → 105): 最初の段は並びの 2 番 (北西の角)、20 段
    public static final int WELL_STAIR_START = 2;
    public static final int WELL_STAIR_STEPS = WELL_BOTTOM - 85;
    // 井戸の螺旋: 一周で 12 上がる。下の端の角度 (x-z の角度。南東 60 度)
    public static final double HELIX_RISE = 12.0;
    public static final double HELIX_START = Math.toRadians(60);

    private final EmberKit kit;
    private final List<Box> carved = new ArrayList<>();
    private int craterFloor;

    private EmberBodyBuilder(ServerLevel level) {
        this.kit = new EmberKit(level);
    }

    // 建てた版 (配置の版と、各担当の中身の版を合わせた数)
    public static int buildVersion() {
        return LAYOUT_VERSION * 10000 + Math.floorMod(EmberBackRooms.contentVersion(), 10000);
    }

    public static void ensureBuilt(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (!BODY.present()) {
            SkyAbyss.LOGGER.warn("ember back: {} is missing, the body is not built", EmberIds.BODY_BIN);
            return;
        }
        int version = buildVersion();
        if (data.needsBuild(version)) {
            if (data.isBuilt()) {
                SkyAbyss.LOGGER.info("Rebuilding the ember back (build {} -> {})", data.layoutVersion(), version);
                data.resetProgress();
            }
            build(back, data);
        }
    }

    // 建てる (かかった時間 ms を返す)
    public static long build(ServerLevel back, TitanBackData data) {
        long start = System.currentTimeMillis();
        EmberBodyBuilder b = new EmberBodyBuilder(back);
        long t0 = System.currentTimeMillis();
        int placed = BODY.place(back, EmberKit.FLAGS, y -> EmberKit.AIR);
        long tBody = System.currentTimeMillis() - t0;
        t0 = System.currentTimeMillis();
        b.restoreUnderground();
        b.mouth();
        b.throat();
        b.hub();
        b.corridors();
        b.trialBoxes();
        b.gateShaft();
        b.ante();
        b.crack();
        b.fireWell();
        b.skin();
        long tCarve = System.currentTimeMillis() - t0;
        t0 = System.currentTimeMillis();
        EmberBackRooms.buildAll(back);
        long tRooms = System.currentTimeMillis() - t0;
        b.fixtures();
        data.markBuilt(buildVersion());
        EmberBackTrials.afterBuild(back);
        long ms = System.currentTimeMillis() - start;
        lastBuildMs = ms;
        SkyAbyss.LOGGER.info("Built the ember back (build {}): {} body blocks in {} ms, carving {} ms, rooms {} ms, total {} ms",
                buildVersion(), placed, tBody, tCarve, tRooms, ms);
        return ms;
    }

    // 最後に建てたときの時間 (自己テストの報告用。建てていなければ -1)
    public static long lastBuildMs = -1;

    // ---------------------------------------------------------------- 灰の原の層 (dimension/ember_back.json と同じ)
    static BlockState plain(int y) {
        if (y <= 0) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (y <= 20) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (y <= 36) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (y <= 62) {
            return Blocks.TUFF.defaultBlockState();
        }
        return y == PLAIN_TOP ? Blocks.SMOOTH_BASALT.defaultBlockState() : EmberKit.AIR;
    }

    // 建て直しのとき、灰の原より下に掘った所 (熾の底・前室・螺旋の下) を灰の原に戻す。口の先の見送りの石の床も
    private void restoreUnderground() {
        for (int x = HEART.minX() - 2; x <= HEART.maxX() + 2; x++) {
            for (int z = GATE_SHAFT.minZ() - 2; z <= HEART.maxZ() + 2; z++) {
                for (int y = HEART.minY() - 2; y <= PLAIN_TOP; y++) {
                    kit.set(x, y, z, plain(y));
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = RETURN_STONE.minZ() - 2; z <= RETURN_STONE.maxZ() + 2; z++) {
                kit.set(x, PLAIN_TOP, z, plain(PLAIN_TOP));
            }
        }
    }

    private void carve(Box b) {
        kit.fill(b, EmberKit.AIR);
        carved.add(b);
    }

    private void track(int x1, int y1, int z1, int x2, int y2, int z2) {
        carved.add(new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
    }

    // ---------------------------------------------------------------- 口 (舌の上の到着点と、顎の先への段)
    // モデルの口はもともと開いている (床 68 の舌、天井 77 前後)。到着の床をならし、顎の先に段を切る
    private void mouth() {
        for (int z = -166; z <= -134; z++) {
            for (int x = -6; x <= 6; x++) {
                kit.set(x, MOUTH_FLOOR, z, Math.abs(x) <= 3 ? Blocks.RED_TERRACOTTA.defaultBlockState() : EmberKit.ruinFloor(x, z));
                for (int y = MOUTH_FLOOR + 1; y <= MOUTH_FLOOR + (Math.abs(x) <= 4 ? 7 : 5); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(-7, MOUTH_FLOOR, -167, 7, MOUTH_FLOOR + 8, -134);
        // 口の中の灯り (頬の内側に熾の光。夜でも舌の上と喉の口が見える)
        for (int z : new int[] {-160, -149, -138}) {
            for (int sx : new int[] {-7, 7}) {
                kit.set(sx, MOUTH_FLOOR + 2, z, EmberKit.GLOW);
            }
        }
        // 見送りの段: 床 67・66・65・64 と下り、z -171 から先は灰の原 (顎の先の唇を 5 幅に切る)
        for (int z = -178; z <= -167; z++) {
            int floor = z >= -170 ? MOUTH_FLOOR + (z + 166) : PLAIN_TOP;
            for (int x = -2; x <= 2; x++) {
                if (z >= -170) {
                    kit.stair(x, floor, z, Direction.SOUTH);
                    kit.set(x, floor - 1, z, EmberKit.RUIN);
                }
                for (int y = floor + 1; y <= MOUTH_FLOOR + 4; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        // 見送りの石 (灰の原の上の床に、光る石の印)
        for (int x = RETURN_STONE.minX(); x <= RETURN_STONE.maxX(); x++) {
            for (int z = RETURN_STONE.minZ(); z <= RETURN_STONE.maxZ(); z++) {
                kit.set(x, PLAIN_TOP, z, x == 0 ? EmberKit.EMBERLIGHT : EmberKit.RUIN_CHISELED);
            }
        }
    }

    // ---------------------------------------------------------------- 喉の坂 (幅 7。床 68 → 76 (休み場の平ら) → 85)
    public static int throatFloor(int z) {
        if (z <= -136) {
            return MOUTH_FLOOR;
        }
        if (z <= -108) {
            return MOUTH_FLOOR + (int) Math.round((z + 135) * 8 / 27.0);
        }
        if (z <= -97) {
            return THROAT_LANDING;
        }
        return Math.min(85, THROAT_LANDING + (int) Math.round((z + 96) * 9 / 46.0));
    }

    private void throat() {
        for (int z = -135; z <= HUB_DOOR_NORTH.getZ(); z++) {
            int f = throatFloor(z);
            boolean rise = f > throatFloor(z - 1);
            for (int x = -3; x <= 3; x++) {
                if (rise) {
                    kit.stair(x, f, z, Direction.SOUTH);
                } else {
                    kit.set(x, f, z, Math.abs(x) <= 1 ? EmberKit.ruinFloor(x, z) : EmberKit.FLESH);
                }
                kit.set(x, f - 1, z, EmberKit.FLESH);
                int h = Math.abs(x) <= 2 ? 5 : 4;
                for (int y = f + 1; y <= f + h; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(-3, MOUTH_FLOOR, -135, 3, 91, HUB_DOOR_NORTH.getZ());
    }

    // ---------------------------------------------------------------- 炉心の広間 (円 半径 14、床 85、天井は丸く 103〜97)
    public static double hubDist(int x, int z) {
        double dx = x - HUB_CENTER.getX();
        double dz = z - HUB_CENTER.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static int hubCeiling(double d) {
        return 103 - (int) Math.round(6 * (d / HUB_RADIUS) * (d / HUB_RADIUS));
    }

    private void hub() {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        int fy = HUB_CENTER.getY();
        for (int x = cx - 15; x <= cx + 15; x++) {
            for (int z = cz - 15; z <= cz + 15; z++) {
                double d = hubDist(x, z);
                if (d > HUB_RADIUS + 0.5) {
                    continue;
                }
                BlockState floor;
                if (d >= 9 && d < 10) {
                    floor = EmberKit.TUFF_BRICKS;
                } else if (d >= 12.6) {
                    floor = EmberKit.RUIN_DARK;
                } else {
                    floor = EmberKit.ruinFloor(x, z);
                }
                kit.set(x, fy, z, floor);
                kit.set(x, fy - 1, z, EmberKit.RUIN);
                int top = hubCeiling(d);
                for (int y = fy + 1; y <= top; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
                // 天井の中央の口 (火の井戸の底の格子まで)
                if (d <= 3.4) {
                    for (int y = top + 1; y < WELL_BOTTOM - 1; y++) {
                        kit.set(x, y, z, EmberKit.AIR);
                    }
                    kit.set(x, WELL_BOTTOM - 1, z, Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
        track(cx - 15, fy - 1, cz - 15, cx + 15, WELL_BOTTOM, cz + 15);
        // 大きな炉 (5 × 5 の石の囲い、2 段。中の 3 × 3 が熾の底の門。門と火は EmberBackTrials.applyGate が置く)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
                    continue;
                }
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                kit.set(cx + dx, fy + 1, cz + dz, corner ? EmberKit.BASALT : EmberKit.RUIN_CHISELED);
                kit.set(cx + dx, fy + 2, cz + dz, corner ? EmberKit.BASALT : EmberKit.RUIN);
                if (corner) {
                    kit.set(cx + dx, fy + 3, cz + dz, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        // 灰溜まり (2 × 2 を四つ、壁ぎわ)
        for (int[] a : new int[][] {{-9, -8}, {8, -8}, {-9, 7}, {8, 7}}) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    kit.set(cx + a[0] + dx, fy, cz + a[1] + dz, EmberKit.ASH);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 広間から試練の入口への通路 (3 幅、高さ 4)
    private void corridors() {
        // 西: 広間の西の扉から西へ (z -35〜-33、床 85)、x -26〜-24 で南へ下り、灰の道の入口 (床 76)
        corridor(-26, HUB_DOOR_WEST.getX(), -35, -33, z -> 85);
        corridorSouth(-26, -24, -35, WEST_DOOR.minZ(), z -> 85 - clamp((int) Math.round((z + 32) * 9 / 22.0), 0, 9));
        // 東: 東の扉から東へ、x 20〜22 で南へ (床 80)
        corridor(HUB_DOOR_EAST.getX(), 22, -35, -33, z -> 85);
        corridorSouth(20, 22, -35, EAST_DOOR.minZ(), z -> 85 - clamp((int) Math.round((z + 32) * 5 / 22.0), 0, 5));
        // 南: 南の扉から谷の入口 (床 78)
        corridorSouth(-1, 1, HUB_DOOR_SOUTH.getZ() - 1, SOUTH_DOOR.minZ(), z -> 85 - clamp((int) Math.round((z + 19) * 7 / 12.0), 0, 7));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void corridor(int x1, int x2, int z1, int z2, java.util.function.IntUnaryOperator floor) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = z1; z <= z2; z++) {
                int f = floor.applyAsInt(z);
                kit.set(x, f, z, EmberKit.ruinFloor(x, z));
                for (int y = f + 1; y <= f + 4; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(Math.min(x1, x2), 84, z1, Math.max(x1, x2), 90, z2);
    }

    // 南へ下る通路 (床が下がる所は北向きの階段)
    private void corridorSouth(int x1, int x2, int z1, int z2, java.util.function.IntUnaryOperator floor) {
        int lo = Integer.MAX_VALUE;
        for (int z = z1; z <= z2; z++) {
            int f = floor.applyAsInt(z);
            lo = Math.min(lo, f);
            boolean drop = f < floor.applyAsInt(z - 1);
            for (int x = x1; x <= x2; x++) {
                if (drop) {
                    kit.stair(x, f, z, Direction.NORTH);
                    kit.set(x, f - 1, z, EmberKit.RUIN);
                } else {
                    kit.set(x, f, z, EmberKit.ruinFloor(x, z));
                }
                for (int y = f + 1; y <= f + 4; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
                // 下りた段の上の前の床の高さまで空ける (段の頭を打たないように)
                for (int y = f + 5; y <= floor.applyAsInt(z1) + 4 && z > z1 && y <= floor.applyAsInt(z - 1) + 4; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(x1, lo - 1, z1, x2, 90, z2);
    }

    // ---------------------------------------------------------------- 試練の箱 (中は空気、仮の床は FLOOR_* の高さまで) と通り道
    private void trialBoxes() {
        for (String room : TRIAL_ROOMS) {
            Box b = ROOMS.get(room);
            carve(b);
            int f = floorOf(room);
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    for (int y = b.minY(); y <= f; y++) {
                        kit.set(x, y, z, y == f ? EmberKit.TEMP_FLOOR : EmberKit.CORE);
                    }
                }
            }
        }
        for (Box door : List.of(WEST_DOOR, EAST_DOOR, SOUTH_DOOR, SMITH_DOOR, HEART_DOOR)) {
            carve(door);
            for (int x = door.minX(); x <= door.maxX(); x++) {
                for (int z = door.minZ(); z <= door.maxZ(); z++) {
                    kit.set(x, door.minY() - 1, z, EmberKit.RUIN);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 熾の底の門の下の螺旋 (3 × 3、y 84 → 51) と前室への通路
    private void gateShaft() {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = FLOOR_HEART + 1; y <= 84; y++) {
                    boolean pillar = dx == 0 && dz == 0;
                    kit.set(cx + dx, y, cz + dz, pillar ? (y % 5 == 0 ? EmberKit.GLOW : EmberKit.RUIN_DARK) : EmberKit.AIR);
                }
                kit.set(cx + dx, FLOOR_HEART, cz + dz, EmberKit.RUIN);
            }
        }
        for (int i = 0; i < GATE_STEPS; i++) {
            int[] c = RING[(GATE_STEP_START + i) % 8];
            int[] prev = RING[Math.floorMod(GATE_STEP_START + i - 1, 8)];
            Direction up = Direction.fromDelta(prev[0] - c[0], 0, prev[1] - c[1]);
            kit.stair(cx + c[0], 84 - i, cz + c[1], up == null ? Direction.NORTH : up);
        }
        track(cx - 1, FLOOR_HEART, cz - 1, cx + 1, 85, cz + 1);
        // 底から南へ前室 (z -32〜-30)
        for (int x = -1; x <= 1; x++) {
            for (int z = cz + 2; z < HEART_ANTE.minZ(); z++) {
                kit.set(x, FLOOR_HEART, z, EmberKit.RUIN);
                for (int y = FLOOR_HEART + 1; y <= FLOOR_HEART + 3; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(-1, FLOOR_HEART, cz + 2, 1, FLOOR_HEART + 3, HEART_ANTE.minZ() - 1);
    }

    // ---------------------------------------------------------------- 熾の底の前室 (床 50)
    private void ante() {
        carve(new Box(HEART_ANTE.minX(), HEART_ANTE.minY() + 1, HEART_ANTE.minZ(), HEART_ANTE.maxX(), HEART_ANTE.maxY(), HEART_ANTE.maxZ()));
        for (int x = HEART_ANTE.minX(); x <= HEART_ANTE.maxX(); x++) {
            for (int z = HEART_ANTE.minZ(); z <= HEART_ANTE.maxZ(); z++) {
                kit.set(x, FLOOR_HEART, z, EmberKit.ruinFloor(x, z));
            }
        }
        carved.add(HEART_ANTE);
    }

    // ---------------------------------------------------------------- 背の亀裂 (火守りの谷の天井から背の上まで。上の縁に格子の手すり)
    public static int crackHalfWidth(int z) {
        if (z < CRACK.minZ() || z > CRACK.maxZ()) {
            return -1;
        }
        return z >= CRACK.minZ() + 4 && z <= CRACK.maxZ() - 4 ? 2 : 1;
    }

    private void crack() {
        for (int z = CRACK.minZ(); z <= CRACK.maxZ(); z++) {
            int w = crackHalfWidth(z);
            for (int x = -w; x <= w; x++) {
                int top = BODY.top(x, z);
                for (int y = CRACK.minY(); y <= Math.max(top, CRACK.minY()); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(CRACK.minX(), SOUTH.maxY(), CRACK.minZ(), CRACK.maxX(), CRACK.maxY(), CRACK.maxZ());
        // 手すり (亀裂のまわりの 1 マスの背の上に、黒石の塀)
        for (int z = CRACK.minZ() - 1; z <= CRACK.maxZ() + 1; z++) {
            for (int x = -4; x <= 4; x++) {
                if (inCrack(x, z) || !nearCrack(x, z)) {
                    continue;
                }
                int top = BODY.top(x, z);
                if (top != Integer.MIN_VALUE) {
                    kit.set(x, top + 1, z, EmberKit.RAIL);
                }
            }
        }
    }

    public static boolean inCrack(int x, int z) {
        int w = crackHalfWidth(z);
        return w >= 0 && Math.abs(x) <= w;
    }

    private static boolean nearCrack(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (inCrack(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 火の井戸
    public static double wellDist(int x, int z) {
        return hubDist(x, z);
    }

    // 井戸の螺旋の段の高さ (立つ面。0.5 刻み)。その柱に段がいくつ重なるかは loop で選ぶ
    public static double helixSurface(int x, int z, int loop) {
        double a = Math.atan2(z - HUB_CENTER.getZ(), x - HUB_CENTER.getX());
        double phi = (((a - HELIX_START) % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI) + loop * 2 * Math.PI;
        double s = (WELL_BOTTOM + 1) + phi * HELIX_RISE / (2 * Math.PI);
        return Math.floor(s * 2) / 2.0;
    }

    // 火口の床の高さ (井戸の螺旋の上の端。体の表から読む)
    public int craterFloor() {
        return craterFloor;
    }

    public static int craterFloorOf() {
        int t = BODY.top(HUB_CENTER.getX() + 8, HUB_CENTER.getZ());
        return t == Integer.MIN_VALUE ? 160 : t;
    }

    private void fireWell() {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        craterFloor = craterFloorOf();
        int r = (int) Math.ceil(WELL_RADIUS);
        // 縦穴 (半径 6.6) を火口の床まで掘る
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = wellDist(x, z);
                if (d > WELL_RADIUS) {
                    continue;
                }
                for (int y = WELL_BOTTOM; y <= craterFloor + 1; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(cx - r - 1, WELL_BOTTOM - 1, cz - r - 1, cx + r + 1, craterFloor + 2, cz + r + 1);
        // 螺旋の段 (3.5〜6.6。内側の 1 マスは段の上に手すり)
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = wellDist(x, z);
                if (d < 3.5 || d > WELL_RADIUS) {
                    continue;
                }
                for (int loop = 0; loop < 8; loop++) {
                    double s = helixSurface(x, z, loop);
                    if (s > craterFloor + 1) {
                        break;
                    }
                    int y;
                    if (s == Math.floor(s)) {
                        y = (int) s - 1;
                        kit.set(x, y, z, EmberKit.RUIN);
                    } else {
                        y = (int) Math.floor(s);
                        kit.set(x, y, z, EmberKit.SLAB);
                    }
                    if (d < 4.5) {
                        kit.set(x, y + 1, z, EmberKit.RAIL);
                    }
                }
            }
        }
        // 底の口: 井戸の底 (y 104) の格子は広間の天井。螺旋の下の端の外は床
        // 壁の螺旋 (広間の南東の壁の中、3 × 3。床 85 → 105)
        int sx = WELL_STAIR.getX();
        int sz = WELL_STAIR.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 86; y <= WELL_BOTTOM + 3; y++) {
                    boolean pillar = dx == 0 && dz == 0 && y < WELL_BOTTOM;
                    kit.set(sx + dx, y, sz + dz, pillar ? (y % 6 == 0 ? EmberKit.GLOW : EmberKit.BASALT) : EmberKit.AIR);
                }
                kit.set(sx + dx, 85, sz + dz, EmberKit.RUIN);
            }
        }
        kit.set(sx, WELL_BOTTOM, sz, EmberKit.RUIN);
        for (int i = 0; i < WELL_STAIR_STEPS; i++) {
            int[] c = RING[(WELL_STAIR_START + i) % 8];
            int[] next = RING[(WELL_STAIR_START + i + 1) % 8];
            Direction up = Direction.fromDelta(next[0] - c[0], 0, next[1] - c[1]);
            kit.stair(sx + c[0], 86 + i, sz + c[1], up == null ? Direction.NORTH : up);
        }
        // 広間から壁の螺旋の最初の段への口 (最初の段の西の 1 マス)
        int[] first = RING[WELL_STAIR_START];
        for (int y = 86; y <= 89; y++) {
            kit.set(sx + first[0] - 1, y, sz + first[1], EmberKit.AIR);
        }
        kit.set(sx + first[0] - 1, 85, sz + first[1], EmberKit.RUIN);
        track(sx - 2, 84, sz - 2, sx + 2, WELL_BOTTOM + 4, sz + 2);
        // 壁の螺旋の上 (床 105) から井戸の螺旋の下の端 (60 度、半径 5.8) へ: x を 3 まで、z を -29 まで
        int[] last = RING[(WELL_STAIR_START + WELL_STAIR_STEPS - 1) % 8];
        int lx = sx + last[0];
        int lz = sz + last[1];
        int hx = cx + (int) Math.round(5.8 * Math.cos(HELIX_START));
        int hz = cz + (int) Math.round(5.8 * Math.sin(HELIX_START));
        int x = lx;
        int z = lz;
        while (x != hx || z != hz) {
            if (x != hx) {
                x += Integer.signum(hx - x);
            } else {
                z += Integer.signum(hz - z);
            }
            if (wellDist(x, z) <= WELL_RADIUS) {
                break;
            }
            kit.set(x, WELL_BOTTOM, z, EmberKit.ruinFloor(x, z));
            for (int y = WELL_BOTTOM + 1; y <= WELL_BOTTOM + 3; y++) {
                kit.set(x, y, z, EmberKit.AIR);
            }
        }
        track(Math.min(lx, hx) - 1, WELL_BOTTOM - 1, Math.min(lz, hz) - 1, Math.max(lx, hx) + 1, WELL_BOTTOM + 4, Math.max(lz, hz) + 1);
        // 背の上への出口 (井戸の南、x -1〜1、床 118 で z -1 まで。背の上に出たら止める)
        for (int zz = cz + 5; zz <= cz + 45; zz++) {
            boolean out = BODY.top(0, zz) <= BACK_EXIT_FLOOR;
            for (int xx = -1; xx <= 1; xx++) {
                if (wellDist(xx, zz) <= WELL_RADIUS - 0.6) {
                    continue;
                }
                kit.set(xx, BACK_EXIT_FLOOR, zz, EmberKit.ruinFloor(xx, zz));
                for (int y = BACK_EXIT_FLOOR + 1; y <= BACK_EXIT_FLOOR + 3; y++) {
                    kit.set(xx, y, zz, EmberKit.AIR);
                }
            }
            track(-2, BACK_EXIT_FLOOR - 1, zz, 2, BACK_EXIT_FLOOR + 4, zz);
            if (out) {
                break;
            }
        }
        // 火口の床から縁へ (北の内壁に沿って、1 段ずつ上る段。縁を切って外を見られる)
        double a0 = Math.toRadians(-90);
        int prevX = Integer.MIN_VALUE;
        int prevZ = Integer.MIN_VALUE;
        int step = 0;
        for (double t = 0; step < 20 && t < Math.PI; t += 0.02) {
            int ox = cx + (int) Math.round(12.5 * Math.cos(a0 + t));
            int oz = cz + (int) Math.round(12.5 * Math.sin(a0 + t));
            if (step > 2 && craterFloor + step > BODY.top(ox, oz) + 1) {
                break;
            }
            int px = cx + (int) Math.round(9.5 * Math.cos(a0 + t));
            int pz = cz + (int) Math.round(9.5 * Math.sin(a0 + t));
            if (px == prevX && pz == prevZ) {
                continue;
            }
            if (prevX != Integer.MIN_VALUE && Math.abs(px - prevX) + Math.abs(pz - prevZ) > 1) {
                // 斜めに飛ぶときは角を埋める
                int[] mid = {px, prevZ};
                ledgeStep(mid[0], mid[1], craterFloor + step, prevX, prevZ);
                prevX = mid[0];
                prevZ = mid[1];
                step++;
            }
            ledgeStep(px, pz, craterFloor + step, prevX, prevZ);
            prevX = px;
            prevZ = pz;
            step++;
        }
        track(cx - 12, craterFloor - 1, cz - 12, cx + 12, craterFloor + 20, cz + 12);
    }

    private void ledgeStep(int x, int z, int y, int px, int pz) {
        Direction up = px == Integer.MIN_VALUE ? Direction.NORTH : Direction.fromDelta(x - px, 0, z - pz);
        kit.set(x, y - 1, z, EmberKit.RUIN);
        kit.stair(x, y, z, up == null ? Direction.NORTH : up);
        for (int yy = y + 1; yy <= y + 3; yy++) {
            kit.set(x, yy, z, EmberKit.AIR);
        }
    }

    // ---------------------------------------------------------------- 掘った所の内壁を「肉と骨の壁」に (体の芯の凝灰岩・灰の原の石が見えたら)
    static boolean coreMaterial(BlockState s) {
        return s.is(Blocks.TUFF) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.BASALT);
    }

    private void skin() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        ServerLevel level = kit.level();
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (Box b0 : carved) {
            Box b = b0.grow(1);
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    for (int y = b.minY(); y <= b.maxY(); y++) {
                        m.set(x, y, z);
                        if (!done.add(m.asLong())) {
                            continue;
                        }
                        if (!coreMaterial(level.getBlockState(m))) {
                            continue;
                        }
                        for (Direction d : Direction.values()) {
                            n.setWithOffset(m, d);
                            if (level.getBlockState(n).isAir()) {
                                kit.set(x, y, z, EmberKit.flesh(x, y, z));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 休み場・熾り石・光る床・碑文・入口の看板 (担当の中身より後)
    private void fixtures() {
        for (Rest r : RESTS) {
            rest(r);
        }
        // 奥の岸 (西)・鍛え手の間の奥 (東)・谷の奥 (南) の床
        farFloor(new Box(-29, FLOOR_WEST, 49, -21, FLOOR_WEST + 4, 56), WEST.minY());
        farFloor(new Box(22, FLOOR_EAST, 52, 30, FLOOR_EAST + 4, 58), SMITH.minY());
        farFloor(new Box(-4, FLOOR_SOUTH, 56, 4, FLOOR_SOUTH + 4, 62), SOUTH.minY());
        // 入口の通り道 (扉から 2 マス内側まで、床と空気)
        lane(new Box(-26, FLOOR_WEST, -8, -24, FLOOR_WEST + 4, -7), WEST.minY());
        lane(new Box(20, FLOOR_EAST, -8, 22, FLOOR_EAST + 4, -7), EAST.minY());
        lane(new Box(25, FLOOR_EAST, 27, 27, FLOOR_EAST + 4, 28), EAST.minY());
        lane(new Box(25, FLOOR_EAST, 32, 27, FLOOR_EAST + 4, 33), SMITH.minY());
        lane(new Box(-1, FLOOR_SOUTH, -6, 1, FLOOR_SOUTH + 4, -5), SOUTH.minY());
        lane(new Box(-1, FLOOR_HEART, -21, 1, FLOOR_HEART + 4, -20), HEART.minY());
        for (Inscription in : INSCRIPTIONS) {
            kit.wallSign(in.pos(), in.facing(), "sign.skyabyss." + in.id(), DyeColor.ORANGE);
        }
        for (Inscription in : ENTRANCE_SIGNS) {
            kit.wallSign(in.pos(), in.facing(), "sign.skyabyss.ember_" + in.id(), DyeColor.ORANGE);
        }
    }

    // 休み場の 5 × 5 の台 (下は箱の底まで詰める)、南の端の真ん中に青い焚き火、北の端に灰溜まり
    private void rest(Rest r) {
        Box a = r.area();
        BlockPos f = r.floor();
        int bottom = f.getY();
        if (r.name().equals("w1")) {
            bottom = WEST.minY();
        }
        for (int x = a.minX(); x <= a.maxX(); x++) {
            for (int z = a.minZ(); z <= a.maxZ(); z++) {
                for (int y = bottom; y < f.getY(); y++) {
                    kit.set(x, y, z, EmberKit.BASALT);
                }
                boolean edge = x == a.minX() || x == a.maxX() || z == a.minZ() || z == a.maxZ();
                kit.set(x, f.getY(), z, edge ? EmberKit.RUIN_DARK : EmberKit.RUIN);
                for (int y = f.getY() + 1; y <= a.maxY(); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            kit.set(f.getX() + dx, f.getY(), f.getZ() - 2, EmberKit.ASH);
        }
        kit.campfire(r.campfire());
    }

    private void farFloor(Box b, int bottom) {
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = bottom; y < b.minY(); y++) {
                    kit.set(x, y, z, EmberKit.BASALT);
                }
                kit.set(x, b.minY(), z, EmberKit.ruinFloor(x, z));
                for (int y = b.minY() + 1; y <= b.maxY(); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
    }

    private void lane(Box b, int bottom) {
        farFloor(b, bottom);
    }

    // 火口の床の高さ (自己テスト・囁き)
    public static int crater() {
        return craterFloorOf();
    }

    // 体の中の芯か (自己テストの「穴」の検査で、体の外と中を分ける)
    public static boolean bodyAt(int x, int y, int z) {
        return BODY.at(x, y, z) != null;
    }

}
