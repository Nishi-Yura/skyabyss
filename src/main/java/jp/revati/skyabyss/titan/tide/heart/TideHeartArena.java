package jp.revati.skyabyss.titan.tide.heart;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 殻の底 (心臓の間) の仮の形 (08 の 3.6節)。背中の本番の島がまだないので、tide_back の仮の島の真下に置く。
// 本番の配置 (潮の広間の床の底の門の下) は次の担当が決める。そのときは CENTER と入口・出口の pad を動かす。
//  - 丸い部屋 (内側の半径 14、天井まで 10)。床は砂と平らな石、壁は海の石と真珠層 (方解石) の白い筋
//  - 中心から半径約 8.5 に、高さ 2 の「岩の島」が 4 つ (北東・北西・南東・南西。上は 3x3)
//  - 床の外周に水の溜めの溝 (半径 12.5〜13.5、1 段低い)
//  - 南の壁に入口 (幅 3、高さ 3。戦いの間は封印)。その外に前室 (休み場の代わり)。前室の南の光る床で島へ戻る
//  - 島の頂の柱の跡の真ん中に「潮の底へ降りる」床。踏むと前室へ降りる
// 満ち潮: 床の高さの一段 (FLOOR) に水を置き、6 秒で引く。島の上 (FLOOR + 2) は濡れない
public final class TideHeartArena {
    // 部屋の中心 (床の上の空気の段の y)
    public static final int CX = 0;
    public static final int CZ = 0;
    public static final int FLOOR = 44;
    public static final int RADIUS = 14;
    public static final int CEILING = 54;
    // 溝 (床の外周、1 段低い水)
    static final double GROOVE_IN = 12.5;
    static final double GROOVE_OUT = 13.5;
    // 岩の島: 中心 (±6, ±6)、上は 3x3、高さ 2
    public static final int ISLAND_OFFSET = 6;
    public static final int ISLAND_HEIGHT = 2;
    // 入口 (南の壁、z = 15。x -1〜1、y FLOOR〜FLOOR+2) と前室 (z 16〜20)
    static final int GATE_Z = 15;
    static final int ANTE_Z0 = 16;
    static final int ANTE_Z1 = 20;
    // 前室の南の端の、島へ戻る光る床 (z = 20)
    static final int EXIT_Z = 20;

    // 満ち潮の状態 (部屋は一つなので static)。fillTicks: 満ちていく残り、holdTicks: 満ちたままの残り、drainTicks: 引く残り
    private static int fillTicks;
    private static int holdTicks;
    private static int drainTicks;
    private static final int FILL = 20;
    private static final int DRAIN = 12;

    private TideHeartArena() {
    }

    public static Vec3 heartHome() {
        return new Vec3(CX + 0.5, FLOOR + 1.5, CZ + 0.5);
    }

    // 戦いの部屋の内側 (入口の封印より奥)。技と狙いもこの中に限る
    public static AABB arena() {
        return new AABB(CX - RADIUS, FLOOR - 1, CZ - RADIUS, CX + RADIUS + 1, CEILING, CZ + RADIUS + 1);
    }

    // 前室 (入口の外の通路)
    public static AABB ante() {
        return new AABB(CX - 2, FLOOR, ANTE_Z0, CX + 3, FLOOR + 4, ANTE_Z1 + 1);
    }

    public static List<BlockPos> gate() {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 3; y++) {
                list.add(new BlockPos(CX + x, FLOOR + y, CZ + GATE_Z));
            }
        }
        return list;
    }

    // 前室に降りたときの位置 (北の入口を向く)
    public static Vec3 anteSpawn() {
        return new Vec3(CX + 0.5, FLOOR, CZ + 18.5);
    }

    static double dist(int x, int z) {
        return Math.sqrt((double) (x - CX) * (x - CX) + (double) (z - CZ) * (z - CZ));
    }

    static boolean inside(int x, int z) {
        return dist(x, z) <= RADIUS;
    }

    static boolean groove(int x, int z) {
        double d = dist(x, z);
        return d > GROOVE_IN && d <= GROOVE_OUT;
    }

    // 岩の島の上の 3x3 か
    public static boolean island(int x, int z) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (Math.abs(x - (CX + sx * ISLAND_OFFSET)) <= 1 && Math.abs(z - (CZ + sz * ISLAND_OFFSET)) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    // 人が岩の島の上に立っているか (島の上の面より上)
    public static boolean onIsland(double x, double y, double z) {
        return island((int) Math.floor(x), (int) Math.floor(z)) && y >= FLOOR + ISLAND_HEIGHT - 0.2;
    }

    // 島の頂の「降りる床」の中心 (柱の跡の真ん中)。高さは建てたときの地面
    static BlockPos descentPad(ServerLevel back) {
        int top = back.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CX, CZ) - 1;
        return new BlockPos(CX, top, CZ);
    }

    // 建っているか (部屋の床の中心と、前室の出口の床で見る。島が建て直されると、部屋は砂で埋まる)
    public static boolean isBuilt(ServerLevel back) {
        return back.getBlockState(new BlockPos(CX, FLOOR - 1, CZ)).is(Blocks.CHISELED_TUFF_BRICKS)
                && back.getBlockState(new BlockPos(CX, FLOOR - 1, CZ + EXIT_Z)).is(Blocks.SEA_LANTERN)
                && back.getBlockState(new BlockPos(CX, CEILING - 2, CZ)).isAir()
                && back.getBlockState(new BlockPos(CX + ISLAND_OFFSET, FLOOR + ISLAND_HEIGHT - 1, CZ + ISLAND_OFFSET)).is(Blocks.SEA_LANTERN)
                && back.getBlockState(descentPad(back)).is(Blocks.SEA_LANTERN)
                && back.getBlockState(descentPad(back).offset(1, 0, 0)).is(Blocks.DARK_PRISMARINE);
    }

    public static void ensureBuilt(ServerLevel back) {
        if (!isBuilt(back)) {
            build(back);
        }
    }

    public static void build(ServerLevel back) {
        int flags = 2 | 16;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState wall = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState band = Blocks.DARK_PRISMARINE.defaultBlockState();
        BlockState nacre = Blocks.CALCITE.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState stone = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        BlockState rock = Blocks.TUFF.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = RADIUS + 2;
        // 天井から下へ (天井を先に置くので、上の砂は落ちない)
        for (int y = CEILING + 1; y >= FLOOR - 2; y--) {
            for (int x = CX - r; x <= CX + r; x++) {
                for (int z = CZ - r; z <= CZ + r; z++) {
                    double d = dist(x, z);
                    if (d > RADIUS + 2) {
                        continue;
                    }
                    p.set(x, y, z);
                    BlockState s;
                    if (y >= CEILING || d > RADIUS) {
                        // 壁と天井。壁は海の石、高さ 3 ごとに暗い帯、方解石の白い筋を斜めに
                        if (y >= CEILING) {
                            s = (Math.floorMod(x * 3 + z * 5, 11) == 0 && d < RADIUS - 1) ? lamp : wall;
                        } else if (y == FLOOR + 6 && Math.floorMod(x + z, 6) == 0) {
                            s = lamp;
                        } else if ((y - FLOOR) % 4 == 3) {
                            s = band;
                        } else if (Math.floorMod(x + y * 2 - z, 9) == 0) {
                            s = nacre;
                        } else {
                            s = wall;
                        }
                    } else if (y == FLOOR - 2) {
                        s = stone;
                    } else if (y == FLOOR - 1) {
                        if (groove(x, z)) {
                            s = water;
                        } else if (x == CX && z == CZ) {
                            s = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
                        } else if (island(x, z)) {
                            s = rock;
                        } else {
                            double dd = dist(x, z);
                            s = (dd < 3.5 || Math.floorMod((int) Math.round(dd * 2), 7) == 0) ? stone : sand;
                        }
                    } else if (island(x, z) && y < FLOOR + ISLAND_HEIGHT) {
                        s = y == FLOOR + ISLAND_HEIGHT - 1 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock;
                    } else {
                        s = air;
                    }
                    back.setBlock(p, s, flags);
                }
            }
        }
        // 明かり: 天井だけでは床が暗く、予告の粒 (色の粒は周りの明るさで暗くなる) が見えにくいので、
        // 床に埋めた海のランタン (半径 4.5 に 8、半径 10 に 12)、壁の足もと (12 か所)、岩の島の上の真ん中
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            floorLamp(back, (int) Math.round(Math.cos(a) * 4.5), (int) Math.round(Math.sin(a) * 4.5), lamp, flags);
        }
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(15 + 30 * k);
            floorLamp(back, (int) Math.round(Math.cos(a) * 10), (int) Math.round(Math.sin(a) * 10), lamp, flags);
            int wx = (int) Math.round(Math.cos(a) * 15);
            int wz = (int) Math.round(Math.sin(a) * 15);
            if (!inside(CX + wx, CZ + wz) && dist(CX + wx, CZ + wz) <= RADIUS + 2) {
                back.setBlock(new BlockPos(CX + wx, FLOOR + 1, CZ + wz), lamp, flags);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                back.setBlock(new BlockPos(CX + sx * ISLAND_OFFSET, FLOOR + ISLAND_HEIGHT - 1, CZ + sz * ISLAND_OFFSET), lamp, flags);
            }
        }
        // 溝の下は砂岩 (水の底が抜けないように)
        for (int x = CX - RADIUS; x <= CX + RADIUS; x++) {
            for (int z = CZ - RADIUS; z <= CZ + RADIUS; z++) {
                if (groove(x, z)) {
                    back.setBlock(new BlockPos(x, FLOOR - 2, z), stone, flags);
                }
            }
        }
        // 入口 (南の壁を抜く) と前室
        for (int z = GATE_Z; z <= ANTE_Z1 + 1; z++) {
            for (int x = CX - 3; x <= CX + 3; x++) {
                for (int y = FLOOR - 1; y <= FLOOR + 4; y++) {
                    p.set(x, y, CZ + z);
                    boolean edge = Math.abs(x - CX) == 3 || z == ANTE_Z1 + 1 || y == FLOOR - 1 || y == FLOOR + 4;
                    if (z == GATE_Z) {
                        // 入口の段: 口は x -1〜1、y FLOOR〜FLOOR+2。まわりは壁のまま
                        if (Math.abs(x - CX) <= 1 && y >= FLOOR && y <= FLOOR + 2) {
                            back.setBlock(p, air, flags);
                        } else if (Math.abs(x - CX) <= 2 && y == FLOOR + 3) {
                            back.setBlock(p, band, flags);
                        }
                        continue;
                    }
                    BlockState s;
                    if (edge) {
                        if (y == FLOOR - 1) {
                            s = z == EXIT_Z && Math.abs(x - CX) <= 1 ? lamp : stone;
                        } else if (y == FLOOR + 4) {
                            s = band;
                        } else {
                            s = wall;
                        }
                    } else if (Math.abs(x - CX) == 2 && y <= FLOOR + 3) {
                        s = (y == FLOOR + 2 && z == ANTE_Z0 + 1) ? lamp : wall;
                    } else {
                        s = air;
                    }
                    back.setBlock(p, s, flags);
                }
            }
        }
        // 島の頂の降りる床: 柱の跡の真ん中に、暗い海の石の 3x3 と海のランタン
        BlockPos pad = descentPad(back);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                back.setBlock(pad.offset(x, 0, z), x == 0 && z == 0 ? lamp : band, flags);
                for (int y = 1; y <= 2; y++) {
                    back.setBlock(pad.offset(x, y, z), air, flags);
                }
            }
        }
        fillTicks = holdTicks = drainTicks = 0;
        SkyAbyss.LOGGER.info("Built the tide heart chamber (placeholder) under the tide back island");
    }

    private static void floorLamp(ServerLevel back, int dx, int dz, BlockState lamp, int flags) {
        int x = CX + dx;
        int z = CZ + dz;
        if (inside(x, z) && !groove(x, z) && !island(x, z) && !(dx == 0 && dz == 0)) {
            back.setBlock(new BlockPos(x, FLOOR - 1, z), lamp, flags);
        }
    }

    // 島の頂の降りる床の上に立っているか
    static boolean onDescentPad(ServerLevel back, double x, double y, double z) {
        BlockPos pad = descentPad(back);
        return x >= pad.getX() - 1 && x < pad.getX() + 2 && z >= pad.getZ() - 1 && z < pad.getZ() + 2
                && y >= pad.getY() + 0.8 && y < pad.getY() + 2.5;
    }

    // 前室の出口の光る床の上に立っているか
    static boolean onExitPad(double x, double y, double z) {
        return x >= CX - 1 && x < CX + 2 && z >= CZ + EXIT_Z && z < CZ + EXIT_Z + 1 && y >= FLOOR - 0.2 && y < FLOOR + 1.5;
    }

    // ---------------------------------------------------------------- 満ち潮
    public static boolean flooding() {
        return fillTicks > 0 || holdTicks > 0 || drainTicks > 0;
    }

    // 水が床を覆っているか (槍の輪が出る間)
    public static boolean flooded() {
        return holdTicks > 0;
    }

    // 外周の溝から中へ満ちて、hold tick のあいだ満ちたまま、引く
    public static void startFlood(ServerLevel level, int hold) {
        if (flooding()) {
            return;
        }
        fillTicks = FILL;
        holdTicks = hold;
        drainTicks = 0;
        level.playSound(null, CX + 0.5, FLOOR, CZ + 0.5, TideHeartSetup.FLOOD.get(), net.minecraft.sounds.SoundSource.HOSTILE, 2.5f, 1.0f);
    }

    // 毎 tick (TideHeartBattle から)
    static void tickFlood(ServerLevel level) {
        if (fillTicks > 0) {
            fillTicks--;
            // 外から内へ: この tick で水が届く半径
            double reach = RADIUS - (RADIUS + 0.5) * (FILL - fillTicks) / (double) FILL;
            setLayer(level, reach, RADIUS + 0.5, true);
            if (fillTicks == 0) {
                setLayer(level, -1, RADIUS + 0.5, true);
            }
            return;
        }
        if (holdTicks > 0) {
            holdTicks--;
            if (holdTicks % 10 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double d = level.random.nextDouble() * RADIUS;
                    level.sendParticles(ParticleTypes.SPLASH, CX + 0.5 + Math.cos(a) * d, FLOOR + 0.9, CZ + 0.5 + Math.sin(a) * d, 4, 0.4, 0.05, 0.4, 0.1);
                }
            }
            if (holdTicks == 0) {
                drainTicks = DRAIN;
            }
            return;
        }
        if (drainTicks > 0) {
            drainTicks--;
            // 内から外へ引く
            double reach = (RADIUS + 0.5) * (DRAIN - drainTicks) / (double) DRAIN;
            setLayer(level, -1, reach, false);
            if (drainTicks == 0) {
                drainNow(level);
            }
        }
    }

    // 水を置く (fill) か消す。dist が (from, to] の床の段
    private static void setLayer(ServerLevel level, double from, double to, boolean fill) {
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = CX - RADIUS; x <= CX + RADIUS; x++) {
            for (int z = CZ - RADIUS; z <= CZ + RADIUS; z++) {
                double d = dist(x, z);
                if (d <= from || d > to || !inside(x, z) || island(x, z)) {
                    continue;
                }
                p.set(x, FLOOR, z);
                BlockState s = level.getBlockState(p);
                if (fill) {
                    if (s.isAir() || (s.is(Blocks.WATER) && !s.getFluidState().isSource())) {
                        level.setBlock(p, water, 2);
                    }
                } else if (s.is(Blocks.WATER)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // すぐに引く (放棄・やり直し・撃破のとき)。床の段の水と流れを残らず消す
    public static void drainNow(ServerLevel level) {
        fillTicks = holdTicks = drainTicks = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = CX - RADIUS; x <= CX + RADIUS; x++) {
            for (int z = CZ - RADIUS; z <= CZ + RADIUS; z++) {
                for (int y = FLOOR; y <= FLOOR + 1; y++) {
                    p.set(x, y, z);
                    if (inside(x, z) && level.getBlockState(p).is(Blocks.WATER)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        // 入口の口に流れた水も消す
        for (BlockPos g : gate()) {
            if (level.getBlockState(g).is(Blocks.WATER)) {
                level.setBlock(g, Blocks.AIR.defaultBlockState(), 2);
            }
        }
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
                level.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    static boolean sealed(ServerLevel level) {
        return level.getBlockState(gate().get(0)).is(ModBlocks.SEALED_GATE.get());
    }

    static void resetState() {
        fillTicks = holdTicks = drainTicks = 0;
    }
}
