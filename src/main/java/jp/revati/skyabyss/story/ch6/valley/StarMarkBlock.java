package jp.revati.skyabyss.story.ch6.valley;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 星の印 (星の灯で足もとに置く小さな光の結晶。docs/design/46 の 4.2「星灯り」)。当たりはなく、StarLamps が時間で消す
public class StarMarkBlock extends Block {
    public static final MapCodec<StarMarkBlock> CODEC = simpleCodec(StarMarkBlock::new);
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 7, 11);

    public StarMarkBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // 星の粒が昇る (光の輪は StarLamps がサーバーから出す)
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0, 0.02, 0);
        }
    }
}
