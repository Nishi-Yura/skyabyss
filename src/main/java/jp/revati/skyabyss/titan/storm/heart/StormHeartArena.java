package jp.revati.skyabyss.titan.storm.heart;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 竜骨の間 (心臓の間、12 の 3.6節)。雷翼の胸の奥、竜骨の内側。座標は StormBackLayout (背中の担当の配置) から取る。
//  - 丸い部屋 (内側の半径 15、床 121、天井 134)。床は竜骨の稜線に沿って中央の幅 3 が 1 段低い (浅い舟の底)。
//    壁は肋と飛ぶための筋の束: 灰白の石 (磨いた閃緑岩) に、方解石の縦の肋と、白い羽毛の層の帯。赤みは使わない
//  - 避雷の柱 4 本 (北東・北西・南東・南西、中心から (±6, ±6))。銅の柱、高さ 6。中の 3 段は銅の電球で、満ちた段だけ灯る。先は光る
//  - 風穴 4 つ (北・南・東・西、中心から 5)。床の銅の格子、下に光。段階 2 で昇り風が立つ (高さは心臓の下の端まで)
//  - 天井の真ん中に、上の広間へ抜ける細い縦穴 (演出だけ)
//  - 北の壁に入口 (幅 3、高さ 3。戦いの間は封印)。その外に前室 (休み場 heart。担当 1 の螺旋の階段は前室の天井の口から降りてくる)
// 明るさ (13.2節の「暗い部屋で予告の粒が見えない」の直し): 柱の先・風穴・壁の灯に加え、見えない光のブロックを頭の高さに散らし、
// 床が明るさ 11 以上になるようにする。心臓は自分の光で描く (StormHeartRenderer)
// 背中の鳥は担当 1 が建てる。鳥が建て直されると部屋は石で埋まるので、5 秒ごとに確かめて建て直す (潮殻と同じ)
public final class StormHeartArena {
    public static final int CX = StormBackLayout.KEEL_CENTER.getX();
    public static final int CZ = StormBackLayout.KEEL_CENTER.getZ();
    public static final int FLOOR = StormBackLayout.KEEL_FLOOR;
    public static final int RADIUS = StormBackLayout.KEEL_RADIUS;
    public static final int CEILING = StormBackLayout.KEEL_CEILING;
    static final int WALL = 2;
    // 竜骨の稜線 (1 段低い中央の筋): |x - CX| <= KEEL_HALF
    static final int KEEL_HALF = 1;
    // 避雷の柱 (中心から ±ROD_OFFSET)。柱は FLOOR から ROD_HEIGHT 段、いちばん上が光る先
    public static final int ROD_OFFSET = 6;
    public static final int ROD_HEIGHT = 6;
    public static final int ROD_STAGES = 3;
    // 風穴 (中心から HOLE_OFFSET。3x3 の格子)。昇り風の半径と上の端
    public static final int HOLE_OFFSET = 5;
    public static final double UPDRAFT_RADIUS = 1.6;
    public static final double UPDRAFT_TOP = FLOOR + 8.5;
    // 入口 (北の壁、z = CZ - RADIUS - 1 と -2) と前室 (z CZ-22 〜 CZ-18、x -2〜2)
    static final int GATE_Z = CZ - RADIUS - 1;
    static final int ANTE_Z0 = CZ - RADIUS - 3;
    static final int ANTE_Z1 = CZ - RADIUS - 7;
    static final int ANTE_CEILING = FLOOR + 4;

    // 柱の満ちた段 (部屋は一つなので static)
    private static final int[] CHARGE = new int[4];

    private StormHeartArena() {
    }

    // 心臓の居場所の水平の中心
    public static Vec3 center() {
        return new Vec3(CX + 0.5, FLOOR, CZ + 0.5);
    }

    // 戦いの部屋の内側 (入口の封印より奥)。技と狙いもこの中に限る
    public static AABB arena() {
        return new AABB(CX - RADIUS, FLOOR - 2, CZ - RADIUS, CX + RADIUS + 1, CEILING, CZ + RADIUS + 1);
    }

    // 前室 (入口の外の通路)
    public static AABB ante() {
        return new AABB(CX - 2, FLOOR, ANTE_Z1, CX + 3, ANTE_CEILING, GATE_Z);
    }

    public static List<BlockPos> gate() {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 3; y++) {
                list.add(new BlockPos(CX + x, FLOOR + y, GATE_Z));
            }
        }
        return list;
    }

    // 前室に降りたときの位置 (南の入口を向く)
    public static Vec3 anteSpawn() {
        return new Vec3(CX + 0.5, FLOOR, ANTE_Z1 + 2.5);
    }

    static double dist(double x, double z) {
        return Math.sqrt((x - CX) * (x - CX) + (z - CZ) * (z - CZ));
    }

    static boolean inside(int x, int z) {
        return dist(x, z) <= RADIUS;
    }

    static boolean keel(int x) {
        return Math.abs(x - CX) <= KEEL_HALF;
    }

    // その場所の床の上の面 (立つ高さ)。竜骨の筋は 1 段低い
    public static int walkY(double x, double z) {
        return keel((int) Math.floor(x)) ? FLOOR - 1 : FLOOR;
    }

    // ---------------------------------------------------------------- 柱と風穴
    // 柱 i の足もと (0 北東、1 北西、2 南東、3 南西)
    public static BlockPos rod(int i) {
        int sx = (i % 2 == 0) ? 1 : -1;
        int sz = (i < 2) ? -1 : 1;
        return new BlockPos(CX + sx * ROD_OFFSET, FLOOR, CZ + sz * ROD_OFFSET);
    }

    public static Vec3 rodTip(int i) {
        BlockPos r = rod(i);
        return new Vec3(r.getX() + 0.5, r.getY() + ROD_HEIGHT - 0.3, r.getZ() + 0.5);
    }

    // 水平に radius 以内でいちばん近い柱 (なければ -1)
    public static int nearestRod(double x, double z, double radius) {
        int best = -1;
        double bd = radius;
        for (int i = 0; i < 4; i++) {
            BlockPos r = rod(i);
            double d = Math.hypot(x - (r.getX() + 0.5), z - (r.getZ() + 0.5));
            if (d <= bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    // 風穴 k の中心 (0 北、1 南、2 東、3 西)
    public static Vec3 hole(int k) {
        return switch (k) {
            case 0 -> new Vec3(CX + 0.5, FLOOR, CZ - HOLE_OFFSET + 0.5);
            case 1 -> new Vec3(CX + 0.5, FLOOR, CZ + HOLE_OFFSET + 0.5);
            case 2 -> new Vec3(CX + HOLE_OFFSET + 0.5, FLOOR, CZ + 0.5);
            default -> new Vec3(CX - HOLE_OFFSET + 0.5, FLOOR, CZ + 0.5);
        };
    }

    // 昇り風の中か (水平に風穴の半径以内、上の端より下)
    public static int holeAt(double x, double y, double z) {
        for (int k = 0; k < 4; k++) {
            Vec3 h = hole(k);
            if (Math.hypot(x - h.x, z - h.z) <= UPDRAFT_RADIUS && y < UPDRAFT_TOP + 1.5 && y >= FLOOR - 2) {
                return k;
            }
        }
        return -1;
    }

    public static int charge(int i) {
        return CHARGE[i];
    }

    public static boolean allFull() {
        for (int c : CHARGE) {
            if (c < ROD_STAGES) {
                return false;
            }
        }
        return true;
    }

    // 柱を 1 段満たす (満ちていれば変えない)。満ちた段を返す
    public static int addCharge(ServerLevel level, int i) {
        if (CHARGE[i] < ROD_STAGES) {
            CHARGE[i]++;
            showCharge(level, i);
        }
        return CHARGE[i];
    }

    public static void resetRods(ServerLevel level) {
        for (int i = 0; i < 4; i++) {
            CHARGE[i] = 0;
            showCharge(level, i);
        }
    }

    // 満ちた段だけ銅の電球が灯る (下から)
    static void showCharge(ServerLevel level, int i) {
        BlockPos base = rod(i);
        for (int s = 0; s < ROD_STAGES; s++) {
            BlockPos p = base.above(1 + s);
            BlockState st = level.getBlockState(p);
            if (st.is(Blocks.WAXED_COPPER_BULB)) {
                level.setBlock(p, st.setValue(CopperBulbBlock.LIT, s < CHARGE[i]), 2);
            }
        }
    }

    // ---------------------------------------------------------------- 建てる
    // 建っているか (床の中心の印、中の空気、柱の先、前室の床で見る)
    public static boolean isBuilt(ServerLevel back) {
        return back.getBlockState(new BlockPos(CX, FLOOR - 2, CZ)).is(Blocks.CHISELED_TUFF_BRICKS)
                && back.getBlockState(new BlockPos(CX + 3, FLOOR + 6, CZ + 3)).isAir()
                && back.getBlockState(rod(0).above(ROD_HEIGHT - 1)).is(Blocks.END_ROD)
                && !back.getBlockState(new BlockPos(CX, FLOOR, ANTE_Z1 + 2)).isSolid();
    }

    public static void ensureBuilt(ServerLevel back) {
        if (!isBuilt(back)) {
            build(back);
        }
    }

    public static void build(ServerLevel back) {
        int flags = 2 | 16;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState wall = Blocks.POLISHED_DIORITE.defaultBlockState();
        BlockState rib = Blocks.CALCITE.defaultBlockState();
        BlockState down = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState band = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState keelStone = Blocks.TUFF_BRICKS.defaultBlockState();
        BlockState ring = Blocks.POLISHED_TUFF.defaultBlockState();
        BlockState base = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = RADIUS + WALL;
        for (int y = CEILING + 1; y >= FLOOR - 3; y--) {
            for (int x = CX - r; x <= CX + r; x++) {
                for (int z = CZ - r; z <= CZ + r; z++) {
                    double d = dist(x, z);
                    if (d > RADIUS + WALL) {
                        continue;
                    }
                    p.set(x, y, z);
                    BlockState s;
                    if (y >= CEILING) {
                        // 天井: 方解石。中心の縦穴の口 (演出だけ) と、半径 10 の灯の輪
                        if (y == CEILING && d < 1.6) {
                            s = air;
                        } else if (y == CEILING && Math.abs(d - 10) < 0.5 && Math.floorMod(x + z, 4) == 0) {
                            s = lamp;
                        } else {
                            s = (Math.abs(d - 6) < 0.5 || Math.abs(d - 12) < 0.5) ? band : rib;
                        }
                    } else if (d > RADIUS) {
                        // 壁: 灰白の石に、30 度ごとの方解石の縦の肋。高さ 4 と 9 に白い羽の層の帯。肋のあいだに灯
                        double ang = Math.toDegrees(Math.atan2(z - CZ, x - CX));
                        double m = Math.floorMod((int) Math.round(ang), 30);
                        boolean ribLine = m <= 2 || m >= 28;
                        boolean lampSpot = Math.abs(m - 15) <= 1 && (y == FLOOR + 3 || y == FLOOR + 9) && d <= RADIUS + 1;
                        if (y < FLOOR) {
                            s = base;
                        } else if (lampSpot) {
                            s = lamp;
                        } else if (ribLine) {
                            s = rib;
                        } else if (y == FLOOR + 5 || y == FLOOR + 10) {
                            s = down;
                        } else if (y == FLOOR + 6 || y == FLOOR + 11) {
                            s = band;
                        } else {
                            s = wall;
                        }
                    } else if (y == FLOOR - 3) {
                        s = base;
                    } else if (y == FLOOR - 2) {
                        s = (x == CX && z == CZ) ? Blocks.CHISELED_TUFF_BRICKS.defaultBlockState() : keel(x) ? keelStone : base;
                    } else if (y == FLOOR - 1) {
                        if (keel(x)) {
                            s = air;
                        } else {
                            s = (Math.abs(d - 3.5) < 0.5 || Math.abs(d - 11.5) < 0.5) ? ring : floor;
                        }
                    } else {
                        s = air;
                    }
                    back.setBlock(p, s, flags);
                }
            }
        }
        // 天井の縦穴 (2 段だけ。上の広間の床とは重ねない)
        for (int x = CX - 1; x <= CX + 1; x++) {
            for (int z = CZ - 1; z <= CZ + 1; z++) {
                if (dist(x, z) < 1.6) {
                    back.setBlock(new BlockPos(x, CEILING + 1, z), air, flags);
                    back.setBlock(new BlockPos(x, CEILING + 2, z), rib, flags);
                }
            }
        }
        // 避雷の柱: 銅の台、銅の電球 3 段 (満ちた段だけ灯る)、銅、光る先
        for (int i = 0; i < 4; i++) {
            BlockPos b = rod(i);
            back.setBlock(b, Blocks.WAXED_CUT_COPPER.defaultBlockState(), flags);
            for (int s = 0; s < ROD_STAGES; s++) {
                back.setBlock(b.above(1 + s), Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(CopperBulbBlock.LIT, false), flags);
            }
            back.setBlock(b.above(4), Blocks.WAXED_CUT_COPPER.defaultBlockState(), flags);
            back.setBlock(b.above(5), Blocks.END_ROD.defaultBlockState(), flags);
            // 台のまわりの床に銅の縁
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        back.setBlock(new BlockPos(b.getX() + dx, FLOOR - 1, b.getZ() + dz), Blocks.WAXED_CUT_COPPER.defaultBlockState(), flags);
                    }
                }
            }
        }
        // 風穴: 銅の格子と、下の光
        for (int k = 0; k < 4; k++) {
            Vec3 h = hole(k);
            int hx = (int) Math.floor(h.x);
            int hz = (int) Math.floor(h.z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int top = walkY(hx + dx, hz + dz) - 1;
                    back.setBlock(new BlockPos(hx + dx, top, hz + dz), Blocks.WAXED_COPPER_GRATE.defaultBlockState(), flags);
                    back.setBlock(new BlockPos(hx + dx, top - 1, hz + dz), lamp, flags);
                }
            }
        }
        // 見えない光 (床の 1 段上、3 ブロックおき)。床はどこでも明るさ 12 以上になる。
        // 当たり判定がないので、歩くのにも、閃光の陰や技の判定にも効かない
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (int x = CX - RADIUS + 1; x <= CX + RADIUS - 1; x += 3) {
            for (int z = CZ - RADIUS + 1; z <= CZ + RADIUS - 1; z += 3) {
                if (dist(x, z) < RADIUS - 0.5 && nearestRod(x + 0.5, z + 0.5, 0.8) < 0) {
                    back.setBlock(new BlockPos(x, walkY(x, z) + 1, z), light, flags);
                }
            }
        }
        // 入口 (北の壁を抜く) と前室
        for (int z = GATE_Z; z >= ANTE_Z1 - 1; z--) {
            for (int x = CX - 3; x <= CX + 3; x++) {
                for (int y = FLOOR - 2; y <= ANTE_CEILING; y++) {
                    p.set(x, y, z);
                    boolean opening = Math.abs(x - CX) <= 1 && y >= FLOOR && y <= FLOOR + 2;
                    if (z >= GATE_Z - 1) {
                        // 壁の厚み 2 段: 口だけ抜く
                        if (opening) {
                            back.setBlock(p, air, flags);
                        } else if (Math.abs(x - CX) <= 2 && y == FLOOR + 3) {
                            back.setBlock(p, rib, flags);
                        }
                        continue;
                    }
                    boolean edge = Math.abs(x - CX) == 3 || z == ANTE_Z1 - 1 || y <= FLOOR - 1 || y == ANTE_CEILING;
                    BlockState s;
                    if (edge) {
                        if (y == FLOOR - 1) {
                            s = floor;
                        } else if (y < FLOOR - 1) {
                            s = base;
                        } else if (y == ANTE_CEILING) {
                            // 前室の天井の口 (担当 1 の螺旋の階段がここから降りてくる)
                            s = (Math.abs(x - CX) <= 1 && z >= ANTE_Z1 && z <= ANTE_Z1 + 2) ? air : rib;
                        } else {
                            s = (y == FLOOR + 2 && Math.abs(x - CX) == 3 && z == ANTE_Z0 - 1) ? lamp : wall;
                        }
                    } else {
                        s = air;
                    }
                    back.setBlock(p, s, flags);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            showCharge(back, i);
        }
        SkyAbyss.LOGGER.info("Built the keel chamber (thunder wing heart)");
    }

    // ---------------------------------------------------------------- 入口
    static void seal(ServerLevel level) {
        for (BlockPos p : gate()) {
            level.setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
        }
    }

    static void open(ServerLevel level) {
        for (BlockPos p : gate()) {
            if (level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    static boolean sealed(ServerLevel level) {
        return level.getBlockState(gate().get(0)).is(ModBlocks.SEALED_GATE.get());
    }

    static void resetState() {
        java.util.Arrays.fill(CHARGE, 0);
    }
}
