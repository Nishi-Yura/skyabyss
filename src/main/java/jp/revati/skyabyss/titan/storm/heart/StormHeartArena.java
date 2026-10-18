package jp.revati.skyabyss.titan.storm.heart;

import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormBlocks;
import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.back.StormBackBuilder;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 竜骨の間 (心臓の間、12 の 3.6節)。雷翼の胸の奥、竜骨の内側。座標は StormBackLayout (背中の担当の配置) から取る。
// 部屋の穴と前室・入口は背中の担当が鳥の中に彫る (StormBirdBuilder.keel)。ここは穴の中の形 (柱・風穴・竜骨の筋・壁と天井の飾り) を
// 建て直すだけで、背中の担当が彫った範囲 (中心から半径 HEART_ROOM_RADIUS + 1.6、床のブロック HEART_ROOM.y 〜 天井のブロック) の外は触らない。
// 前室・螺旋の階段・天井の上の低い道 (竜骨の門から) は背中の担当のもの。
//  - 丸い部屋 (内側の半径 15、立つ高さ 122、天井のブロック 134)。床は竜骨の稜線に沿って中央の幅 3 が 1 段低い (浅い舟の底)。
//    壁は肋と飛ぶための筋の束: 灰白の石 (磨いた閃緑岩) に、方解石の縦の肋と、白い羽毛の層の帯。赤みは使わない
//  - 避雷の柱 4 本 (北東・北西・南東・南西、中心から (±6, ±6))。銅の柱、高さ 6。中の 3 段は銅の電球で、満ちた段だけ灯る。先は光る
//  - 風穴 4 つ (北・南・東・西、中心から 5)。床の銅の格子、下に光。段階 2 で昇り風が立つ (高さは心臓の下の端まで)
//  - 北の壁に入口 (幅 3、高さ 3。封印の扉のブロックがいつも置いてあり、戦っているグループにだけ閉じる)。その外に前室 (休み場 heart。担当 1 の螺旋の階段から西の道で着く)
// 明るさ (13.2節の「暗い部屋で予告の粒が見えない」の直し): 柱の先・風穴・壁の灯に加え、見えない光のブロックを頭の高さに散らし、
// 床が明るさ 11 以上になるようにする。心臓は自分の光で描く (StormHeartRenderer)
// 背中の鳥は担当 1 が建てる。鳥が建て直されると部屋は石で埋まるので、5 秒ごとに確かめて建て直す (潮殻と同じ)
public final class StormHeartArena {
    public static final int CX = StormBackLayout.HEART_ROOM.getX();
    public static final int CZ = StormBackLayout.HEART_ROOM.getZ();
    // 立つ高さ (竜骨の筋の外)。背中の HEART_ROOM.y は筋の床のブロック、その外は 1 段高い床のブロック (HEART_ROOM.y + 1) の上に立つ
    public static final int FLOOR = StormBackLayout.HEART_ROOM.getY() + 2;
    public static final int RADIUS = StormBackLayout.HEART_ROOM_RADIUS;
    // 天井のブロックの y (空気はその下まで)。その上は竜骨の門からの低い道の床なので、ここより上は触らない
    public static final int CEILING = StormBackLayout.HEART_ROOM_CEILING + 1;
    // 建て直す範囲 (背中の担当が彫った壁の外の端)
    static final double OUTER = RADIUS + 1.6;
    // 竜骨の稜線 (1 段低い中央の筋): |x - CX| <= KEEL_HALF
    static final int KEEL_HALF = 1;
    // 避雷の柱 (中心から ±ROD_OFFSET)。柱は FLOOR から ROD_HEIGHT 段、いちばん上が光る先
    public static final int ROD_OFFSET = 6;
    public static final int ROD_HEIGHT = 6;
    public static final int ROD_STAGES = 3;
    // 風穴 (中心から HOLE_OFFSET。3x3 の格子)。昇り風の半径と上の端
    public static final int HOLE_OFFSET = 5;
    public static final double UPDRAFT_RADIUS = 1.6;
    public static final double UPDRAFT_TOP = FLOOR + 5.5;
    // 入口 (北の壁。前室 KEEL_ANTE の北の口 = 背中の HEART_DOOR) と、その内側の壁の厚み (z GATE_Z + 1)
    static final int GATE_Z = StormBackLayout.KEEL_ANTE.z2() + 1;

    // 柱の満ちた段 (部屋は一つ。竜骨の間は一度に一つのグループだけが入れる部屋なので、その持ち主の戦いの段)
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

    // 前室 (入口の外。背中の担当の KEEL_ANTE)
    public static AABB ante() {
        return StormBackLayout.KEEL_ANTE.aabb();
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

    // 前室に降りたときの位置 (休み場 heart の目を覚ます所。南の入口を向く)
    public static Vec3 anteSpawn() {
        return Vec3.atBottomCenterOf(StormBackLayout.rest("heart"));
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
            if (st.is(StormBackSetup.ROD_BULB.get())) {
                level.setBlock(p, st.setValue(StormBlocks.RodBulb.LIT, s < CHARGE[i]), 2);
            }
        }
    }

    // ---------------------------------------------------------------- 建てる
    // 建っているか (床の中心の印、中の空気、柱の先、入口の内側の空気で見る)
    public static boolean isBuilt(ServerLevel back) {
        return back.getBlockState(new BlockPos(CX, FLOOR - 2, CZ)).is(Blocks.CHISELED_TUFF_BRICKS)
                && back.getBlockState(new BlockPos(CX + 3, FLOOR + 6, CZ + 3)).isAir()
                && back.getBlockState(rod(0).above(ROD_HEIGHT - 1)).is(Blocks.END_ROD)
                && !back.getBlockState(new BlockPos(CX + 1, FLOOR, GATE_Z + 1)).isSolid();
    }

    public static void ensureBuilt(ServerLevel back) {
        if (!isBuilt(back)) {
            build(back);
        } else {
            placeGate(back);
        }
    }

    public static void build(ServerLevel back) {
        // 部屋は鳥の中にある。鳥がまだ建っていなければ先に建てる (前室・入口の床・天井の上の道は背中の担当のもの)
        StormBackBuilder.ensureBuilt(back);
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
        int r = (int) Math.ceil(OUTER);
        for (int y = CEILING; y >= FLOOR - 3; y--) {
            for (int x = CX - r; x <= CX + r; x++) {
                for (int z = CZ - r; z <= CZ + r; z++) {
                    double d = dist(x, z);
                    if (d > OUTER) {
                        continue;
                    }
                    p.set(x, y, z);
                    BlockState s;
                    if (y >= CEILING) {
                        // 天井: 方解石と半径 10 の灯の輪。上が空いていて床のブロックがある所 (竜骨の門と低い道の床) は背中の担当の床のまま
                        if (!back.getBlockState(p).isAir() && back.getBlockState(p.above()).isAir()) {
                            continue;
                        }
                        if (Math.abs(d - 10) < 0.5 && Math.floorMod(x + z, 4) == 0) {
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
        // 避雷の柱: 銅の台、銅の電球 3 段 (満ちた段だけ灯る)、銅、光る先
        for (int i = 0; i < 4; i++) {
            BlockPos b = rod(i);
            back.setBlock(b, Blocks.WAXED_CUT_COPPER.defaultBlockState(), flags);
            for (int s = 0; s < ROD_STAGES; s++) {
                back.setBlock(b.above(1 + s), StormBackSetup.ROD_BULB.get().defaultBlockState().setValue(StormBlocks.RodBulb.LIT, false), flags);
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
        // 入口 (北の壁の口と、その内側の壁の厚み)。前室は背中の担当が彫ったまま
        for (int z = GATE_Z; z <= GATE_Z + 1; z++) {
            for (int x = CX - 1; x <= CX + 1; x++) {
                for (int y = FLOOR; y <= FLOOR + 2; y++) {
                    back.setBlock(new BlockPos(x, y, z), air, flags);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            showCharge(back, i);
        }
        placeGate(back);
        SkyAbyss.LOGGER.info("Built the keel chamber (thunder wing heart)");
    }

    // ---------------------------------------------------------------- 入口 (グループごとの扉。既定は開いている)
    // 扉のブロックを置く (建てたあと・確かめるたび。何度呼んでも同じ)
    public static void placeGate(ServerLevel level) {
        GroupGates.place(level, gate(), true);
    }

    // 戦っているグループにだけ閉じる
    static void seal(ServerLevel level, UUID group) {
        GroupGates.seal(level, group, gate());
    }

    static void open(ServerLevel level, UUID group) {
        if (GroupGates.release(level, group, gate())) {
            for (ServerPlayer player : Groups.online(level, group)) {
                for (BlockPos p : gate()) {
                    level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, false, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
    }

    // そのグループにとって入口が閉じているか
    public static boolean sealed(ServerLevel level, UUID group) {
        return !GroupGates.isOpen(level, group, gate().get(0));
    }

    static void resetState() {
        java.util.Arrays.fill(CHARGE, 0);
    }
}
