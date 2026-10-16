package jp.revati.skyabyss.titan.tide;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背 (tide_back) の仮の島。第一歩では、到着地点 (潮門の浜) と、地上へ戻る潮門だけを建てる。
// 背中の島の本番 (甲羅の形の楕円、潮の広間、三つの試練。08 の 3章) は次の担当が建てる。そのときは LAYOUT_VERSION を上げる。
// ディメンションの生成は平らな海 (岩盤 1・砂 40・水は y=62 まで)。島はここで、最初に乗り込んだとき (と版が変わったとき) に建てる
public final class TideBackBuilder {
    // 建てたときの版。TitanBackData (ディメンションごとの記録) に残し、違えば建て直す
    public static final int LAYOUT_VERSION = 1;
    // 生成の海面 (水の最上段は y=62)。平らな世界の getSeaLevel は使えない (-63 を返す) ので、ここで決める
    public static final int SEA = 63;
    static final int FLOOR = 41;
    // 島 (中心 0, 0)。半径 22 の低い丘。頂は y=67
    static final int ISLAND_R = 22;
    // 潮門の浜: 島の南 (+Z) の浜。門は z=20〜21 に立ち、口は x -1〜1、y 64〜67
    static final int GATE_Z = 20;
    static final int GATE_HALF = 1;
    static final int GATE_Y0 = 64;
    static final int GATE_Y1 = 67;

    private TideBackBuilder() {
    }

    // 到着地点 (潮門の浜の、門から 5 ブロック島の側)。島の中心を向く
    public static BlockPos arrival() {
        return new BlockPos(0, surfaceY(0, GATE_Z - 5) + 1, GATE_Z - 5);
    }

    // 島の地面の高さ (いちばん上のブロックの y)。島の外は海底
    static int surfaceY(int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        if (r > ISLAND_R) {
            return FLOOR - 1;
        }
        double t = r / ISLAND_R;
        return SEA - 1 + (int) Math.round(5.0 * (1 - t * t));
    }

    // 地上へ戻る潮門の口に入っているか (ブロックの座標で)
    static boolean inReturnGate(double x, double y, double z) {
        return x >= -GATE_HALF && x < GATE_HALF + 1 && z >= GATE_Z && z < GATE_Z + 2 && y >= GATE_Y0 - 0.5 && y < GATE_Y1;
    }

    public static void ensureBuilt(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (data.needsBuild(LAYOUT_VERSION)) {
            build(back, data);
        }
    }

    public static void build(ServerLevel back, TitanBackData data) {
        int flags = 2 | 16;
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        int r = ISLAND_R + 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int top = surfaceY(x, z);
                for (int y = FLOOR; y <= SEA + 12; y++) {
                    p.set(x, y, z);
                    BlockState s;
                    if (y <= top) {
                        s = y >= top - 2 ? sand : sandstone;
                    } else {
                        s = y < SEA ? water : Blocks.AIR.defaultBlockState();
                    }
                    if (!back.getBlockState(p).equals(s)) {
                        back.setBlock(p, s, flags);
                    }
                }
            }
        }
        buildGate(back, flags);
        buildRuin(back, flags);
        data.markBuilt(LAYOUT_VERSION);
        SkyAbyss.LOGGER.info("Built the tide back placeholder island (layout {})", LAYOUT_VERSION);
    }

    // 地上へ戻る潮門: 海の石のレンガの枠、上は暗い海の石、両脇に海のランタン。口の床は砂
    private static void buildGate(ServerLevel back, int flags) {
        BlockState frame = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState lintel = Blocks.DARK_PRISMARINE.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        for (int z = GATE_Z; z <= GATE_Z + 1; z++) {
            for (int y = GATE_Y0 - 1; y <= GATE_Y1 + 1; y++) {
                for (int x = -GATE_HALF - 1; x <= GATE_HALF + 1; x++) {
                    BlockPos at = new BlockPos(x, y, z);
                    boolean side = Math.abs(x) == GATE_HALF + 1;
                    BlockState s;
                    if (y == GATE_Y0 - 1) {
                        s = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    } else if (y > GATE_Y1 - 1) {
                        s = lintel;
                    } else if (side) {
                        s = y == GATE_Y0 + 1 ? lamp : frame;
                    } else {
                        s = Blocks.AIR.defaultBlockState();
                    }
                    back.setBlock(at, s, flags);
                }
            }
        }
        // 門の向こうの水際へ下りる段 (浜から海へ)
        for (int z = GATE_Z + 2; z <= GATE_Z + 3; z++) {
            for (int x = -GATE_HALF; x <= GATE_HALF; x++) {
                back.setBlock(new BlockPos(x, GATE_Y0 - 1, z), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), flags);
            }
        }
    }

    // 島の頂の小さな遺跡 (潮の民の柱の跡。本番の島が建つまでの目印)
    private static void buildRuin(ServerLevel back, int flags) {
        int top = surfaceY(0, 0) + 1;
        BlockState pillar = Blocks.PRISMARINE.defaultBlockState();
        int[][] posts = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] c : posts) {
            int h = (c[0] + c[1]) % 2 == 0 ? 3 : 2;
            for (int y = 0; y < h; y++) {
                back.setBlock(new BlockPos(c[0], top + y, c[1]), pillar, flags);
            }
        }
        back.setBlock(new BlockPos(0, top, 0), Blocks.SEA_LANTERN.defaultBlockState(), flags);
    }
}
