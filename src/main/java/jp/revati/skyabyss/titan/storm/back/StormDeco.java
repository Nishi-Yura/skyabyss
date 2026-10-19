package jp.revati.skyabyss.titan.storm.back;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 雷翼の背の飾りと目当ての部品 (灯の柱・矢じるし・床の色の線・羽軸の柱)。どれも「道を読む」ためのもの:
// 矢じるしは進む向き、色の線は行き先 (西 = 青、東 = 金、前 = 白)、灯の柱は道の縁
final class StormDeco {
    static final BlockState POST = Blocks.WAXED_CUT_COPPER.defaultBlockState();
    static final BlockState POST_TOP = Blocks.WAXED_CHISELED_COPPER.defaultBlockState();
    static final BlockState RAIL = Blocks.DIORITE_WALL.defaultBlockState();
    // 行き先の色: 西 (風 = 水色)、東 (雷 = 金)、前 (眼 = 白)
    static final BlockState WEST_LINE = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
    static final BlockState EAST_LINE = Blocks.YELLOW_CONCRETE.defaultBlockState();
    static final BlockState FRONT_LINE = Blocks.WHITE_CONCRETE.defaultBlockState();
    static final BlockState WEST_PIP = Blocks.SEA_LANTERN.defaultBlockState();
    static final BlockState EAST_PIP = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    static final BlockState FRONT_PIP = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();

    private StormDeco() {
    }

    // 灯の柱: 床のブロック y の 1 上から 3 段の銅の柱、てっぺんは彫り銅の上に灯り。足もとの柱は固い所だけ (掘った所は避ける)
    static void lampPost(StormKit kit, int x, int floorY, int z) {
        for (int y = floorY + 1; y <= floorY + 3; y++) {
            kit.put(x, y, z, POST);
        }
        kit.put(x, floorY + 4, z, StormKit.LAMP);
    }

    // 矢じるし (床に埋め込む。頂点が dir の先): 幅 7 の V の字。床のブロック y の置き換え
    static void chevron(StormKit kit, int cx, int cz, Direction dir, BlockState mark, java.util.function.IntUnaryOperator floorOfZ) {
        int sx = dir.getStepX();
        int sz = dir.getStepZ();
        // dir の後ろ向きに 4 列。頂点が先頭 (k = 0)
        for (int k = 0; k <= 3; k++) {
            for (int s : k == 0 ? new int[] {0} : new int[] {-k, k}) {
                int x = cx - sx * k + (sx == 0 ? s : 0);
                int z = cz - sz * k + (sz == 0 ? s : 0);
                kit.put(x, floorOfZ.applyAsInt(z), z, mark);
            }
        }
    }

    // 床の線 (折れ線 pts を軸方向の線分でつなぐ。床のブロック y の置き換え)。pipEvery おきに光る玉
    static void strip(StormKit kit, int floorY, BlockState line, BlockState pip, int pipEvery, int[]... pts) {
        int n = 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            int x = pts[i][0];
            int z = pts[i][1];
            int dx = Integer.signum(pts[i + 1][0] - x);
            int dz = Integer.signum(pts[i + 1][1] - z);
            while (x != pts[i + 1][0] || z != pts[i + 1][1]) {
                kit.put(x, floorY, z, n % pipEvery == pipEvery - 1 ? pip : line);
                x += dx;
                z += dz;
                n++;
            }
        }
        int[] last = pts[pts.length - 1];
        kit.put(last[0], floorY, last[1], pip);
    }

    // 羽軸の柱 (3x3 の白い羽の柱。四段ごとに銅の帯、てっぺんに灯り)。base は床のブロック y
    static void featherPillar(StormKit kit, int cx, int cz, int floorY, int height) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                for (int y = floorY + 1; y <= floorY + height; y++) {
                    BlockState s = y == floorY + 1 || y == floorY + height ? StormKit.COPPER : (y - floorY) % 5 == 0 ? StormKit.COPPER
                            : corner ? StormKit.CALCITE : StormKit.FEATHER_WHITE;
                    kit.put(cx + dx, y, cz + dz, s);
                }
            }
        }
        kit.put(cx, floorY + height + 1, cz, StormKit.LAMP);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    kit.put(cx + dx, floorY + height + 1, cz + dz, StormKit.COPPER);
                }
            }
        }
    }

    // 腰掛け (半ブロックの階段の並び。向きは facing の先を向く)
    static void bench(StormKit kit, int x1, int x2, int z1, int z2, int y, Direction facing) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                kit.stair(x, y, z, facing.getOpposite(), Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
            }
        }
    }
}
