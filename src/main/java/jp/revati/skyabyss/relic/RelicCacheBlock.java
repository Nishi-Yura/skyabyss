package jp.revati.skyabyss.relic;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 遺物の箱 (宝のキャッシュ)。見た目と当たり判定は箱 (14x14x14) と同じ。中身は持たない:
// 右クリックは各キャッシュの処理 (FinCaches など) が横取りし、取った印は人ごとに持つ。
// OPEN = 蓋が上がった見た目 (誰かが開けたとき数秒だけ。pulse)
public class RelicCacheBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RelicCacheBlock> CODEC = simpleCodec(RelicCacheBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public RelicCacheBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(RelicStyle.PROP, RelicStyle.WHALE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, RelicStyle.PROP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, false), 3);
            level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    // 開けた見た目を数秒出す (キャッシュの処理から呼ぶ。箱でなければ何もしない)
    public static void pulse(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof RelicCacheBlock && !s.getValue(OPEN)) {
            level.setBlock(pos, s.setValue(OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 1.1f);
            level.scheduleTick(pos, s.getBlock(), 50);
        }
    }
}
