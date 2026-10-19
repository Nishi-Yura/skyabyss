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
}
