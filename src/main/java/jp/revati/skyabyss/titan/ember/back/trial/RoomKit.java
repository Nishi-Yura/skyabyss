package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.List;

import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 試練の部屋を建てる道具 (部屋の箱の約束: 箱の中だけに置く。取っておく場所 reserved は、床の高さまでは置いてよく (溶岩が漏れないように)、
// それより上は空ける。B が最後に休み場・熾り石・碑文を置く)
final class RoomKit {
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    static final BlockState SMOOTH = Blocks.SMOOTH_BASALT.defaultBlockState();
    static final BlockState POLISHED = Blocks.POLISHED_BASALT.defaultBlockState();
    static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    static final BlockState NETHER_BRICKS = Blocks.RED_NETHER_BRICKS.defaultBlockState();

    final ServerLevel level;
    final EmberBackLayout.Box box;
    final int floorY;
    private final List<EmberBackLayout.Box> reserved;

    RoomKit(ServerLevel level, String room) {
        this.level = level;
        this.box = EmberBackLayout.ROOMS.get(room);
        this.floorY = EmberBackLayout.floorOf(room);
        this.reserved = EmberBackLayout.reserved(room);
    }

    boolean blocked(int x, int y, int z) {
        if (!box.contains(x, y, z)) {
            return true;
        }
        if (y > floorY) {
            for (EmberBackLayout.Box r : reserved) {
                if (r.contains(x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean set(int x, int y, int z, BlockState s) {
        if (blocked(x, y, z)) {
            return false;
        }
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
        return true;
    }

    boolean set(BlockPos p, BlockState s) {
        return set(p.getX(), p.getY(), p.getZ(), s);
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, s);
                }
            }
        }
    }

    // 壁の石 (玄武岩と黒石のまだら)
    static BlockState wall(int x, int y, int z) {
        int h = Math.floorMod(x * 7 + y * 13 + z * 5, 11);
        return h < 6 ? BASALT : h < 9 ? BLACKSTONE : POLISHED;
    }

    // 箱の外周 1 枚を、壁・床・天井で囲む (取っておく場所の上は空ける)。箱の内側の空気はそのまま
    void shell(boolean ceiling) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
                    if (y == box.minY()) {
                        set(x, y, z, wall(x, y, z));
                    } else if (edge || y == box.maxY() && ceiling) {
                        set(x, y, z, wall(x, y, z));
                    }
                }
            }
        }
    }

    void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    // ---------------------------------------------------------------- 飾りの部品 (版 20。区ごとの言葉は EmberZones と同じ: 黒い岩・骨・灯り)
    static final BlockState ASH_DRIFT = Blocks.GRAY_CONCRETE.defaultBlockState();
    static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
    static final BlockState SHROOM = Blocks.SHROOMLIGHT.defaultBlockState();
    static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();

    static int hash(int x, int y, int z) {
        return Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 1000);
    }

    // 床 floorY の上の柱 (黒石レンガ。先に灯り)
    void post(int x, int z, int h) {
        for (int y = floorY + 1; y <= floorY + h; y++) {
            set(x, y, z, y == floorY + h ? BRICKS : y == floorY + 1 ? POLISHED : BRICKS);
        }
        set(x, floorY + h + 1, z, LANTERN);
    }

    // 灰の木 (玄武岩の幹と、灰色の凝灰岩の冠。冠の芯に光る茂み)。h = 幹の高さ
    void ashTree(int x, int z, int h) {
        for (int y = floorY + 1; y <= floorY + h; y++) {
            set(x, y, z, Blocks.BASALT.defaultBlockState());
        }
        int top = floorY + h;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1 && dy == 1;
                    if (!corner && !(dx == 0 && dz == 0 && dy == 0)) {
                        set(x + dx, top + dy, z + dz, hash(x + dx, top + dy, z + dz) < 450 ? TUFF : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                    }
                }
            }
        }
        set(x, top + 1, z, SHROOM);
        // 枝 (横へ 1 本)
        int sx = hash(x, 1, z) < 500 ? 1 : -1;
        set(x + sx, top - 1, z, Blocks.BASALT.defaultBlockState());
        set(x + 2 * sx, top - 1, z, TUFF);
    }

    // 灰の吹きだまり (床の上のまだら。灰色のコンクリート。灰溜まり = 灰色の羊毛とは別。熱は冷めない)
    void drifts(int x1, int z1, int x2, int z2, int percent) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                if (hash(x, 9, z) < percent * 10) {
                    set(x, floorY, z, ASH_DRIFT);
                }
            }
        }
    }

    // 灰溜まり (灰色の羊毛。熱が冷える。壺が満ちる) を w × d の長方形に
    void ashPool(int x, int z, int w, int d) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                set(x + dx, floorY, z + dz, Blocks.GRAY_WOOL.defaultBlockState());
            }
        }
    }

    // 掲示の台 (後ろに 2 段の石の台を置き、その上に灯り)。掲示は床の上 2 段の高さ
    void stele(int x, int z, int backDz) {
        set(x, floorY + 1, z + backDz, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        set(x, floorY + 2, z + backDz, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        set(x, floorY + 3, z + backDz, LANTERN);
    }

    // ---------------------------------------------------------------- 洞の建築 (版 20): 丸天井・壁の肋・熔脈・溶岩の中の岩の柱
    // 丸天井: x の範囲の真ん中が高く、端が低い (hEdge → hCenter。床の上の高さ)。z の範囲で、天井の下を壁の石で埋める。
    // holes に当たる所 (背の亀裂の下) は埋めない。8 ブロックおきに肋 (黒石レンガ) が天井の下に 1 段張り出し、肋の真ん中と 1/4 に光る茂み
    void vault(int xMin, int xMax, int zMin, int zMax, int hEdge, int hCenter, java.util.function.BiPredicate<Integer, Integer> hole) {
        double cx = (xMin + xMax) / 2.0;
        double half = (xMax - xMin) / 2.0;
        for (int x = xMin; x <= xMax; x++) {
            double u = (x - cx) / half;
            int limit = floorY + hEdge + (int) Math.round((hCenter - hEdge) * (1 - u * u));
            for (int z = zMin; z <= zMax; z++) {
                if (hole != null && hole.test(x, z)) {
                    continue;
                }
                for (int y = limit + 1; y < box.maxY(); y++) {
                    set(x, y, z, wall(x, y, z));
                }
                if (Math.floorMod(z - zMin, 8) == 0) {
                    boolean key = Math.abs(x - cx) < 1 || Math.abs(Math.abs(x - cx) - half / 2) < 1;
                    set(x, limit, z, key ? SHROOM : BRICKS);
                }
            }
        }
    }

    // 壁の肋: 両側の壁 (box の x の端の内側 2 マス) に、8 ブロックおきに幅 2 の黒い岩の肋を高さ h まで立てる。肋の正面に光る茂みを埋める。
    // lavaRow の z (溶岩の湖の行) は、溶岩が壁に触れないよう、内側 2 マスを床の下 3 から床の上 3 まで必ず壁にする (肋でない所も)
    void relief(int zMin, int zMax, int h, java.util.function.IntPredicate lavaRow) {
        for (int z = zMin; z <= zMax; z++) {
            int k = Math.floorMod(z - zMin, 8);
            boolean lava = lavaRow != null && lavaRow.test(z);
            for (int side = 0; side < 2; side++) {
                int x1 = side == 0 ? box.minX() + 1 : box.maxX() - 1;
                int dir = side == 0 ? 1 : -1;
                for (int d = 0; d < 2; d++) {
                    int x = x1 + dir * d;
                    int top = k <= 1 ? floorY + h - 3 * d : lava ? floorY + 3 : floorY;
                    int bottom = lava ? floorY - 3 : floorY + 1;
                    for (int y = bottom; y <= top; y++) {
                        set(x, y, z, hash(x, y, z) < 500 ? POLISHED : BLACKSTONE);
                    }
                    if (d == 1 && k == 0 && Math.floorMod(z - zMin, 16) == 0) {
                        set(x, floorY + 6, z, SHROOM);
                    }
                }
            }
        }
    }

    // 熔脈: 両側の壁の面 (box の端の石) に、縦に走るマグマの筋 (3〜8 ブロック。ゆるく曲がる)。ところどころ光る茂みが混じる
    void veins(int zMin, int zMax, int spacing) {
        for (int z = zMin; z <= zMax; z += spacing) {
            for (int side = 0; side < 2; side++) {
                int x = side == 0 ? box.minX() : box.maxX();
                int zz = z + hash(x, 5, z) % 4;
                int len = 3 + hash(x, 6, z) % 6;
                int y0 = floorY + 2 + hash(x, 7, z) % 8;
                for (int i = 0; i < len; i++) {
                    int jitter = (hash(x, y0 + i, zz) % 5 == 0) ? 1 : 0;
                    set(x, y0 + i, zz + jitter, hash(x, y0 + i, z) < 120 ? SHROOM : MAGMA);
                }
            }
        }
    }

    // 奥の壁と手前の壁 (box の z の端) にも熔脈を走らせる
    void veinsEnds(int xMin, int xMax, int spacing) {
        for (int x = xMin; x <= xMax; x += spacing) {
            for (int side = 0; side < 2; side++) {
                int z = side == 0 ? box.minZ() : box.maxZ();
                int xx = x + hash(x, 8, z) % 4;
                int len = 3 + hash(x, 9, z) % 7;
                int y0 = floorY + 2 + hash(x, 10, z) % 9;
                for (int i = 0; i < len; i++) {
                    set(xx + (hash(xx, y0 + i, z) % 5 == 0 ? 1 : 0), y0 + i, z, hash(xx, y0 + i, z) < 120 ? SHROOM : MAGMA);
                }
            }
        }
    }

    // 石の門: 熾り石を囲む二本の柱と梁 (取っておく場所の外。柱 x1・x2、梁は柱のあいだ)
    void stoneGate(int x1, int x2, int z, int h) {
        for (int x : new int[] {x1, x2}) {
            for (int y = floorY + 1; y <= floorY + h; y++) {
                set(x, y, z, y % 4 == 0 ? POLISHED : BRICKS);
            }
            set(x, floorY + h + 1, z, SHROOM);
        }
        for (int x = x1; x <= x2; x++) {
            set(x, floorY + h, z, BRICKS);
            if ((x - x1) % 3 == 1) {
                set(x, floorY + h - 1, z, RoomKit.BONE);
            }
        }
    }

    // 溶岩の中の岩の柱 (景色。1 × 1 か 2 × 2、水面より 3〜9 高い)。避けたい範囲 (島とその 4 ブロック) を skip で渡す
    void stacks(int xMin, int xMax, int zMin, int zMax, int stepX, int stepZ, java.util.function.BiPredicate<Integer, Integer> skip) {
        for (int x = xMin; x <= xMax; x += stepX) {
            for (int z = zMin; z <= zMax; z += stepZ) {
                int h = hash(x, 21, z);
                if (h > 450) {
                    continue;
                }
                int px = x + h % 3;
                int pz = z + (h / 3) % 3;
                int w = h % 7 == 0 ? 2 : 1;
                if (skip != null && skip.test(px, pz)) {
                    continue;
                }
                int top = floorY + 3 + h % 7;
                for (int dx = 0; dx < w; dx++) {
                    for (int dz = 0; dz < w; dz++) {
                        for (int y = floorY - 3; y <= top - dx - dz; y++) {
                            set(px + dx, y, pz + dz, hash(px + dx, y, pz + dz) < 600 ? BASALT : BLACKSTONE);
                        }
                    }
                }
            }
        }
    }
}
