package jp.revati.skyabyss.titan.ember.smith;

import java.util.ArrayList;
import java.util.List;


import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 鍛え手の間 (13 の 5.2節、11.2節の担当 E)。部屋の箱は EmberBackLayout.SMITH (27 x 27、床 80、天井まで 14)。箱の中だけに置く。
// 中身: 床の模様、四隅の壁 (円形の部屋にする)、焼き入れの槽 2 つ (水。縁の石 5 x 5)、灰溜まり 4 つ (灰色の羊毛 3 x 3)、柱 4 本、
// 奥の炉の扉 (怒りで開く。灰被りが 2 体出る)。戦いは中ボスの間 (trial/raid/mid/EmberMidRoom) が、この中身を部屋の中心へずらして建てて行う。
// 取っておく場所 (EmberBackLayout.reserved("smith")) には置かない。何度呼ばれても同じ物になる (build は箱の中を全部置き直す)
public final class AshSmithArena {
    public static final Box BOX = EmberBackLayout.SMITH;
    public static final int FLOOR = EmberBackLayout.FLOOR_EAST;
    // 円の中心 (ブロック座標。箱の中心)
    public static final int CX = EmberBackLayout.SMITH_CENTER.getX();
    public static final int CZ = EmberBackLayout.SMITH_CENTER.getZ();
    public static final int RADIUS = EmberBackLayout.SMITH_RADIUS;
    public static final Vec3 HOME = new Vec3(CX + 0.5, FLOOR + 1, CZ + 0.5);

    // 焼き入れの槽 (中心。水の 3 x 3 と縁の 5 x 5)
    public static final List<BlockPos> TROUGHS = List.of(new BlockPos(CX - 8, FLOOR, CZ), new BlockPos(CX + 8, FLOOR, CZ));
    // 灰溜まり (中心。3 x 3)
    public static final List<BlockPos> PUDDLES = List.of(new BlockPos(CX - 8, FLOOR, CZ - 8), new BlockPos(CX + 8, FLOOR, CZ - 8),
            new BlockPos(CX - 8, FLOOR, CZ + 8), new BlockPos(CX + 8, FLOOR, CZ + 8));
    // 柱 (2 x 2 の角。床の上 7 ブロック)
    public static final List<BlockPos> PILLARS = List.of(new BlockPos(CX - 5, FLOOR + 1, CZ - 5), new BlockPos(CX + 4, FLOOR + 1, CZ - 5),
            new BlockPos(CX - 11, FLOOR + 1, CZ + 4), new BlockPos(CX + 10, FLOOR + 1, CZ + 4));
    public static final int PILLAR_HEIGHT = 7;
    // 奥の炉の扉 (3 x 3 の格子。後ろに窪み)。怒りで開く
    public static final Box FURNACE_DOOR = new Box(CX - 9, FLOOR + 1, CZ + 11, CX - 7, FLOOR + 3, CZ + 11);
    public static final Box FURNACE_NICHE = new Box(CX - 9, FLOOR + 1, CZ + 12, CX - 7, FLOOR + 3, CZ + 13);
    // 建てたしるし (床の中心の磨いた黒石の模様)
    public static final BlockPos MARK = new BlockPos(CX, FLOOR, CZ);

    private AshSmithArena() {
    }

    // ---------------------------------------------------------------- 場所の問い
    public static boolean inside(Vec3 p) {
        return BOX.contains(p);
    }

    public static boolean reserved(int x, int y, int z) {
        for (Box b : EmberBackLayout.reserved("smith")) {
            if (b.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBuilt(ServerLevel level) {
        return level.getBlockState(MARK).is(Blocks.CHISELED_POLISHED_BLACKSTONE);
    }

    // いちばん近い槽の中心 (人が立つ側の縁の外ではなく、槽の中心の床)
    public static BlockPos nearestTrough(Vec3 from) {
        BlockPos best = TROUGHS.get(0);
        double bestD = Double.MAX_VALUE;
        for (BlockPos t : TROUGHS) {
            double d = (t.getX() + 0.5 - from.x) * (t.getX() + 0.5 - from.x) + (t.getZ() + 0.5 - from.z) * (t.getZ() + 0.5 - from.z);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    // 槽の縁の外 (ボスが立つ所。部屋の中心側)
    public static Vec3 troughStand(BlockPos trough) {
        double dx = trough.getX() < CX ? 3.4 : -3.4;
        return new Vec3(trough.getX() + 0.5 + dx, FLOOR + 1, trough.getZ() + 0.5);
    }

    // 湯気がおおう半分 (槽の側の半分。-1 = 西側、+1 = 東側)
    public static int steamSide(BlockPos trough) {
        return trough.getX() < CX ? -1 : 1;
    }

    // 灰溜まりの上か (熱を冷やす所。B の熱の判定と同じ「灰色の羊毛」)
    public static boolean onPuddle(Vec3 p) {
        BlockPos b = BlockPos.containing(p.x, p.y - 0.2, p.z);
        return b.getY() == FLOOR && puddleAt(b.getX(), b.getZ());
    }

    private static boolean puddleAt(int x, int z) {
        for (BlockPos c : PUDDLES) {
            if (Math.abs(x - c.getX()) <= 1 && Math.abs(z - c.getZ()) <= 1) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 建てる
    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        if (!BOX.contains(x, y, z) || SHIFT == BlockPos.ZERO && reserved(x, y, z)) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z).offset(SHIFT), state, 2);
    }

    private static boolean outside(int x, int z) {
        double dx = x - CX;
        double dz = z - CZ;
        return dx * dx + dz * dz > (RADIUS + 0.6) * (RADIUS + 0.6);
    }

    // 箱の中を建てる (箱の中は空気で、いちばん下の段にだけ仮の床がある前提。何度呼んでもよい)
    public static void build(ServerLevel level, Box box) {
        buildBody(level, box);
        // 部屋を作り直すと壁の灯り (歩く道の灯り。四隅の壁にある) も戻るので置き直す (置いてある灯りは飛ばす)
        jp.revati.skyabyss.titan.light.RouteLamps.place(level, "ember");
    }

    private static void buildShifted(ServerLevel level) {
        buildBody(level, BOX);
    }

    private static void buildBody(ServerLevel level, Box box) {
        BlockState basalt = Blocks.POLISHED_BASALT.defaultBlockState();
        BlockState tile = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState wall = Blocks.BLACKSTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (outside(x, z)) {
                    // 四隅は壁 (円形の部屋)。床から天井まで
                    for (int y = FLOOR; y <= box.maxY(); y++) {
                        set(level, x, y, z, y == FLOOR ? tile : wall);
                    }
                    continue;
                }
                int d = (int) Math.round(Math.sqrt((x - CX) * (x - CX) + (z - CZ) * (double) (z - CZ)));
                // 床の模様: 外周の輪と中心の輪を磨いた黒石に、あいだを玄武岩の格子に
                BlockState floor = (d == RADIUS || d == RADIUS - 1 || d == 4) ? tile : ((x + z) % 2 == 0 ? basalt : Blocks.SMOOTH_BASALT.defaultBlockState());
                set(level, x, FLOOR, z, floor);
                for (int y = FLOOR + 1; y <= box.maxY(); y++) {
                    set(level, x, y, z, air);
                }
            }
        }
        // 床の中心のしるし
        level.setBlock(MARK.offset(SHIFT), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
        for (BlockPos t : TROUGHS) {
            buildTrough(level, t);
        }
        for (BlockPos p : PUDDLES) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(level, p.getX() + dx, FLOOR, p.getZ() + dz, Blocks.GRAY_WOOL.defaultBlockState());
                }
            }
        }
        for (BlockPos p : PILLARS) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    for (int h = 0; h < PILLAR_HEIGHT; h++) {
                        Block b = h == PILLAR_HEIGHT - 1 ? Blocks.SHROOMLIGHT : (h % 3 == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS);
                        set(level, p.getX() + dx, p.getY() + h, p.getZ() + dz, b.defaultBlockState());
                    }
                }
            }
        }
        buildFurnace(level, false);
    }

    private static void buildTrough(ServerLevel level, BlockPos c) {
        BlockState rim = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                set(level, c.getX() + dx, FLOOR, c.getZ() + dz, ring ? rim : Blocks.SMOOTH_BASALT.defaultBlockState());
                set(level, c.getX() + dx, FLOOR + 1, c.getZ() + dz, ring ? rim : Blocks.WATER.defaultBlockState());
            }
        }
    }

    // 炉の扉: 閉じているときは格子 (後ろの窪みが赤く見える)。開くと空気
    public static void buildFurnace(ServerLevel level, boolean open) {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = FURNACE_DOOR.minX(); x <= FURNACE_DOOR.maxX(); x++) {
            for (int y = FURNACE_DOOR.minY(); y <= FURNACE_DOOR.maxY(); y++) {
                set(level, x, y, FURNACE_DOOR.minZ(), open ? air : bars);
            }
        }
        for (int x = FURNACE_NICHE.minX(); x <= FURNACE_NICHE.maxX(); x++) {
            for (int z = FURNACE_NICHE.minZ(); z <= FURNACE_NICHE.maxZ(); z++) {
                for (int y = FURNACE_NICHE.minY() - 1; y <= FURNACE_NICHE.maxY() + 1; y++) {
                    BlockState s = air;
                    if (y == FURNACE_NICHE.minY() - 1) {
                        s = Blocks.MAGMA_BLOCK.defaultBlockState();
                    } else if (y == FURNACE_NICHE.maxY() + 1) {
                        s = Blocks.SHROOMLIGHT.defaultBlockState();
                    }
                    set(level, x, y, z, s);
                }
            }
        }
    }

    public static boolean furnaceOpen(ServerLevel level) {
        return level.getBlockState(new BlockPos(FURNACE_DOOR.minX() + 1, FURNACE_DOOR.minY() + 1, FURNACE_DOOR.minZ())).isAir();
    }

    // 灰被りが出る所 (炉の扉の前、部屋の内側)
    public static List<Vec3> furnaceSpawns() {
        List<Vec3> out = new ArrayList<>();
        out.add(new Vec3(FURNACE_DOOR.minX() + 0.5, FLOOR + 1, FURNACE_DOOR.minZ() - 1.0));
        out.add(new Vec3(FURNACE_DOOR.maxX() + 0.5, FLOOR + 1, FURNACE_DOOR.minZ() - 1.0));
        return out;
    }


    // ---------------------------------------------------------------- ずらした間 (順路の中ボスの部屋。trial/raid/mid)
    // s = 元の間 (EmberBackLayout.SMITH) からのずれ。ZERO なら元の間と同じ
    public static boolean inside(Vec3 p, BlockPos s) {
        return BOX.contains(p.subtract(s.getX(), s.getY(), s.getZ()));
    }

    public static Vec3 home(BlockPos s) {
        return HOME.add(s.getX(), s.getY(), s.getZ());
    }

    public static BlockPos nearestTrough(Vec3 from, BlockPos s) {
        return nearestTrough(from.subtract(s.getX(), s.getY(), s.getZ())).offset(s);
    }

    public static Vec3 troughStand(BlockPos trough, BlockPos s) {
        return troughStand(trough.subtract(s)).add(s.getX(), s.getY(), s.getZ());
    }

    public static int steamSide(BlockPos trough, BlockPos s) {
        return steamSide(trough.subtract(s));
    }

    public static List<Vec3> furnaceSpawns(BlockPos s) {
        List<Vec3> out = new ArrayList<>();
        for (Vec3 v : furnaceSpawns()) {
            out.add(v.add(s.getX(), s.getY(), s.getZ()));
        }
        return out;
    }

    public static boolean furnaceOpen(ServerLevel level, BlockPos s) {
        return level.getBlockState(new BlockPos(FURNACE_DOOR.minX() + 1, FURNACE_DOOR.minY() + 1, FURNACE_DOOR.minZ()).offset(s)).isAir();
    }

    // ずらした間の中身を建てる (箱の中は空気、または壁の前提。外周の壁は呼ぶ側が建てる)。元の間と同じ形
    private static BlockPos SHIFT = BlockPos.ZERO;

    public static void build(ServerLevel level, BlockPos s) {
        SHIFT = s;
        try {
            buildShifted(level);
        } finally {
            SHIFT = BlockPos.ZERO;
        }
    }

    public static void buildFurnace(ServerLevel level, boolean open, BlockPos s) {
        SHIFT = s;
        try {
            buildFurnace(level, open);
        } finally {
            SHIFT = BlockPos.ZERO;
        }
    }
}
