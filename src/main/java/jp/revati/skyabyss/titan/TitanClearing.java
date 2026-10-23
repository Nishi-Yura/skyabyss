package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// 地面に休む巨神 (潮殻・燃えがらの巨神) が座るとき、体のなかに食い込む地形・木を取り除く (「ブロックにめり込まない」)。
// 体の柱 (TitanVolume.coreColumns) ごとに、体の底から頂までの高さにある地形のブロック (石・土・砂・木・葉・鉱石・植物など。natural) を消す
// (水・流体・岩盤・都や場所の中は触らない。人の建物 (板・ガラス・丸石・箱など、地形でないブロックと、中身を持つブロック) も触らない。
// 2026-10-23: 前は固いブロックを全部消していて、余燼の浮上で人の家と箱が消えた。海の底を削ったところは水で埋める)。
// 体の頂より上に残った葉・雪も払う (宙に浮く葉を残さない)。
public final class TitanClearing {
    // 1 回で壊す上限 (重さの歯止め)
    public static final int MAX_BLOCKS = 120_000;

    private TitanClearing() {
    }

    // 地形のブロックか (ワールド生成が置くもの。人がふつう建物に使う加工したブロックは含めない)。
    // 葉は人が置いたもの (persistent) を除く
    public static boolean natural(BlockState s) {
        if (s.is(BlockTags.LEAVES)) {
            return !s.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT) || !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT);
        }
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.LOGS)
                || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.REPLACEABLE)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CORAL_BLOCKS) || s.is(BlockTags.CORALS)
                || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
                || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.VINE)
                || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.BAMBOO) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)
                || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS) || s.is(Blocks.SEA_PICKLE) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.POINTED_DRIPSTONE) || s.is(Blocks.DRIPSTONE_BLOCK)
                || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.BUDDING_AMETHYST)
                || s.is(Blocks.CALCITE) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.COCOA) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.GLOW_LICHEN) || s.is(Blocks.HANGING_ROOTS) || s.is(Blocks.SPORE_BLOSSOM) || s.is(Blocks.BIG_DRIPLEAF)
                || s.is(Blocks.BIG_DRIPLEAF_STEM) || s.is(Blocks.SMALL_DRIPLEAF) || s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)
                || s.is(Blocks.LILY_PAD) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.PACKED_MUD);
    }

    // base = 体の底 (巨神の位置 y)。壊したブロック数を返す
    public static int carve(ServerLevel level, String volumeName, double x, double base, double z, float yaw) {
        TitanVolume volume = TitanVolume.get(volumeName);
        if (volume == null || !TitanGround.enabled) {
            return 0;
        }
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int removed = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (TitanVolume.Column c : volume.coreColumns()) {
            double wx = x + fx * c.fwd() + fz * c.side();
            double wz = z + fz * c.fwd() - fx * c.side();
            for (int dx = -1; dx <= 0; dx++) {
                for (int dz = -1; dz <= 0; dz++) {
                    int bx = Mth.floor(wx) + dx;
                    int bz = Mth.floor(wz) + dz;
                    if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
                        continue;
                    }
                    if (OpeningSites.siteAt(level.getServer(), new BlockPos(bx, 0, bz), 0).isPresent()) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                    int lo = Mth.floor(base + c.lo());
                    int hi = Math.min(Mth.ceil(base + c.hi()), top);
                    for (int y = hi; y >= lo; y--) {
                        p.set(bx, y, bz);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || !s.getFluidState().isEmpty() || s.is(Blocks.BEDROCK) || s.getDestroySpeed(level, p) < 0
                                || !natural(s) || level.getBlockEntity(p) != null) {
                            continue;
                        }
                        // 海の底 (海面より下で、真上が水) は水で埋める。空気にすると、近所の更新を出さないので海の底に空気の穴が残る
                        boolean wet = y < level.getSeaLevel() && level.getFluidState(p.above()).is(net.minecraft.tags.FluidTags.WATER);
                        level.setBlock(p, wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                        if (++removed >= MAX_BLOCKS) {
                            return removed;
                        }
                    }
                    for (int y = hi + 1; y <= top; y++) {
                        p.set(bx, y, bz);
                        BlockState s = level.getBlockState(p);
                        if ((s.is(BlockTags.LEAVES) || s.is(Blocks.SNOW)) && natural(s)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                        }
                    }
                }
            }
        }
        return removed;
    }
}
