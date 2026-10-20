package jp.revati.skyabyss.titan.ember.back;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

// 余燼の背を建てる道具箱 (潮殻の TideKit の余燼版)。部屋の中身の担当 (C1・C2・C3・E・F) も使ってよい。
// 灰の民の石組み: 磨かれた黒石のレンガ・玄武岩・凝灰岩のレンガ。体の中の壁は「肉と骨の壁」(赤いネザーレンガ・骨ブロック・マグマの血管・光る血管)。
// 灰溜まり (熱が冷える目印) は灰色の羊毛だけ (EmberHeat が見る)。体のほかの所には灰色の羊毛を使わない
public final class EmberKit {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
    // 灰の民の床と石組み
    public static final BlockState RUIN = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    public static final BlockState RUIN_CRACKED = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    public static final BlockState RUIN_DARK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    public static final BlockState RUIN_CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    public static final BlockState BASALT = Blocks.POLISHED_BASALT.defaultBlockState();
    public static final BlockState SMOOTH = Blocks.SMOOTH_BASALT.defaultBlockState();
    public static final BlockState TUFF_BRICKS = Blocks.TUFF_BRICKS.defaultBlockState();
    public static final BlockState RAIL = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
    public static final BlockState SLAB = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    public static final BlockState STAIR = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState();
    // 灰溜まり (立つと 1 秒に −15)
    public static final BlockState ASH = Blocks.GRAY_WOOL.defaultBlockState();
    // 肉と骨の壁
    public static final BlockState FLESH = Blocks.RED_NETHER_BRICKS.defaultBlockState();
    public static final BlockState FLESH_SOFT = Blocks.NETHER_WART_BLOCK.defaultBlockState();
    public static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();
    public static final BlockState VEIN = Blocks.MAGMA_BLOCK.defaultBlockState();
    public static final BlockState GLOW = Blocks.SHROOMLIGHT.defaultBlockState();
    public static final BlockState EMBERLIGHT = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    // 体の芯 (gen_ember_back_body.py の DEEP) と、仮の床
    public static final BlockState CORE = Blocks.TUFF.defaultBlockState();
    public static final BlockState TEMP_FLOOR = Blocks.TUFF_BRICKS.defaultBlockState();

    private final ServerLevel level;
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    public EmberKit(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return level;
    }

    public void set(int x, int y, int z, BlockState s) {
        m.set(x, y, z);
        if (level.getBlockState(m) != s) {
            level.setBlock(m, s, FLAGS);
        }
    }

    public void set(BlockPos p, BlockState s) {
        set(p.getX(), p.getY(), p.getZ(), s);
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(m.set(x, y, z));
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

    public void fill(EmberBackLayout.Box b, BlockState s) {
        fill(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), s);
    }

    static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    // 肉と骨の壁: 体を横切る骨の輪 (z が 9 ごと)、マグマの血管の筋、ところどころ光る血管
    public static BlockState flesh(int x, int y, int z) {
        int h = Math.floorMod(hash(x, y, z), 1000);
        if (Math.floorMod(z, 9) == 0 && Math.floorMod(hash(x >> 2, 0, z), 5) != 0) {
            return BONE;
        }
        if (h < 22) {
            return GLOW;
        }
        int vein = Math.floorMod(x * 3 + y * 5 - z * 2 + (hash(x >> 3, y >> 3, z >> 3) & 7), 23);
        if (vein == 0) {
            return VEIN;
        }
        return h < 300 ? FLESH_SOFT : FLESH;
    }

    // 灰の民の床 (ひびの入ったレンガが混じる)
    public static BlockState ruinFloor(int x, int z) {
        int h = Math.floorMod(hash(x, 7, z), 10);
        return h == 0 ? RUIN_CRACKED : h == 1 ? RUIN_DARK : RUIN;
    }

    // 登る向き facing の階段
    public void stair(int x, int y, int z, Direction facing) {
        set(x, y, z, STAIR.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
    }

    // 休み場の焚き火 (青い焚き火 =「冷えた火」。しゃがんで触れると、試練をあきらめて到着点へ戻る)
    public void campfire(BlockPos p) {
        set(p.below(), RUIN_CHISELED);
        set(p, jp.revati.skyabyss.relic.RelicBlocks.hearth(jp.revati.skyabyss.relic.RelicStyle.EMBER, true));
        set(p.above(), AIR);
    }

    // 壁の碑 (facing = 文が向く向き。後ろ (facing の反対) に台のブロックを置く)
    public void wallSign(BlockPos pos, Direction facing, String key, DyeColor color) {
        BlockPos back = pos.relative(facing.getOpposite());
        if (!get(back.getX(), back.getY(), back.getZ()).isSolid()) {
            set(back, RUIN_CHISELED);
        }
        set(pos, Tablets.wallState(facing).setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.EMBER));
        Tablets.write(level, pos, key);
    }

    // 掲示の碑 (壁に付く看板。後ろが壁でなければ、2 段の石の台を立て、その上に灯りを置く。台は床の上)
    public void board(BlockPos pos, Direction facing, String key, DyeColor color) {
        BlockPos back = pos.relative(facing.getOpposite());
        if (!get(back.getX(), back.getY(), back.getZ()).isSolid()) {
            set(back, RUIN_CHISELED);
            BlockPos below = back.below();
            if (!get(below.getX(), below.getY(), below.getZ()).isSolid()) {
                set(below, RUIN_CHISELED);
            }
            BlockPos above = back.above();
            if (get(above.getX(), above.getY(), above.getZ()).isAir()) {
                set(above, Blocks.LANTERN.defaultBlockState());
            }
        }
        set(pos, Tablets.wallState(facing).setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.EMBER));
        Tablets.write(level, pos, key);
    }
}
