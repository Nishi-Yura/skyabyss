package jp.revati.skyabyss.titan.tide.back;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

// 潮殻の背を建てる道具箱 (岩鯨の BuildKit の潮殻版)。潮の民の石組み: 海の石のレンガ・暗い海の石・方解石 (真珠層)・平らな砂岩
public final class TideKit {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final BlockState BRICK = Blocks.PRISMARINE_BRICKS.defaultBlockState();
    public static final BlockState DARK = Blocks.DARK_PRISMARINE.defaultBlockState();
    public static final BlockState ROUGH = Blocks.PRISMARINE.defaultBlockState();
    public static final BlockState NACRE = Blocks.CALCITE.defaultBlockState();
    public static final BlockState FLOOR = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    public static final BlockState SAND = Blocks.SAND.defaultBlockState();
    public static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    public static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    public static final BlockState SHELL = Blocks.TUFF.defaultBlockState();
    public static final BlockState SHELL_DARK = Blocks.DEEPSLATE_TILES.defaultBlockState();

    private final ServerLevel level;
    final RandomSource random;
    // いまの場所の材質 (TideZone。場所ごとに壁・床・柱・灯りが替わる)
    private TideZone zone = TideZone.SHELL;

    public TideKit(ServerLevel level, RandomSource random) {
        this.level = level;
        this.random = random;
    }

    public ServerLevel level() {
        return level;
    }

    // 以降の部品 (壁・床・柱・灯り) の材質を場所のものにする
    public TideKit zone(TideZone z) {
        this.zone = z;
        return this;
    }

    public TideZone zone() {
        return zone;
    }

    public void set(int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    public void set(BlockPos p, BlockState s) {
        level.setBlock(p, s, FLAGS);
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, s);
                }
            }
        }
    }

    // 壁の石: 場所の材質 (主の石に、ところどころ副・筋・暗い帯)
    public BlockState wall(int x, int y, int z) {
        return zone.wall(x, y, z);
    }

    // 床の石 (場所の材質)
    public BlockState floorAt(int x, int z) {
        return zone.floorAt(x, z);
    }

    // 灯りのブロック (場所の材質。潮殻は海のランタン)
    public BlockState lamp() {
        return zone.lamp;
    }

    // 灯りを壁・天井に埋める (そこが固い石のときだけ。空気や仕掛けには置かない)
    public void lampIn(int x, int y, int z) {
        BlockState cur = get(x, y, z);
        if (!cur.isAir() && cur.isCollisionShapeFullBlock(level, new BlockPos(x, y, z)) && !cur.is(Blocks.WATER)) {
            set(x, y, z, zone.lamp);
        }
    }

    // 柱 (床 y1 から y2。柱頭は cap)。足もとと頭が固い所に立てる
    public void pillar(int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            set(x, y, z, y == y2 ? zone.cap : y == y1 ? zone.floorAlt : zone.pillar);
        }
    }

    // 折れ線 (x, z, x, z, ...) をたどる床のマスの並び (始点から終点まで、1 マスずつ)
    public static java.util.List<int[]> line(int... xz) {
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int i = 0; i + 3 < xz.length; i += 2) {
            int x = xz[i];
            int z = xz[i + 1];
            int tx = xz[i + 2];
            int tz = xz[i + 3];
            while (x != tx || z != tz) {
                if (cells.isEmpty() || cells.get(cells.size() - 1)[0] != x || cells.get(cells.size() - 1)[1] != z) {
                    cells.add(new int[] {x, z});
                }
                if (x != tx) {
                    x += Integer.signum(tx - x);
                } else {
                    z += Integer.signum(tz - z);
                }
            }
        }
        int[] last = {xz[xz.length - 2], xz[xz.length - 1]};
        if (cells.isEmpty() || cells.get(cells.size() - 1)[0] != last[0] || cells.get(cells.size() - 1)[1] != last[1]) {
            cells.add(last);
        }
        return cells;
    }

    // 床に埋め込んだ灯りの道: 1 つおきに glow (床と同じ高さに平らに)、あいだは base。固い床の上だけ
    public void routeStrip(int floorY, java.util.List<int[]> cells, BlockState glow, BlockState base) {
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            if (get(c[0], floorY, c[1]).isAir()) {
                continue;
            }
            set(c[0], floorY, c[1], i % 2 == 0 ? glow : base);
        }
    }

    // 箱の部屋: 内側を空け、外周 1 マスを壁・床・天井にする (すでに空いている所の外周はふさがない)
    public void room(int x1, int x2, int z1, int z2, int floor, int height, BlockState floorState) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                for (int y = floor; y <= floor + height + 1; y++) {
                    boolean in = x >= x1 && x <= x2 && z >= z1 && z <= z2 && y > floor && y <= floor + height;
                    if (in) {
                        set(x, y, z, AIR);
                    } else if (!get(x, y, z).isAir() || y == floor) {
                        set(x, y, z, y == floor ? floorState : wall(x, y, z));
                    }
                }
            }
        }
    }

    public void room(TideBackLayout.Area a, BlockState floorState) {
        room(a.x1(), a.x2(), a.z1(), a.z2(), a.floor(), a.height(), floorState);
    }

    // 階段 (登る向き facing)
    public void stair(int x, int y, int z, Direction facing, BlockState base) {
        set(x, y, z, base.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
    }

    // 休み場の焚き火 (青い焚き火。しゃがんで触れると、試練をあきらめて浜へ戻る)
    public void campfire(BlockPos p) {
        set(p.below(), Blocks.CHISELED_SANDSTONE.defaultBlockState());
        set(p, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        set(p.above(), AIR);
    }

    // 碑 (看板の代わり)。文のキーは lang の "<key>.1"〜"<key>.4"
    public void wallSign(BlockPos pos, Direction facing, String key) {
        set(pos, Tablets.wallState(facing));
        Tablets.write(level, pos, key);
    }

    // 立てた碑
    public void standingTablet(BlockPos pos, String key) {
        set(pos, Tablets.standingState());
        Tablets.write(level, pos, key);
    }
}
