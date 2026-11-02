package jp.revati.skyabyss.story.finale;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 戻り石 (docs/design/50 の 4.2.3「巻き戻り」)。崩れた橋と主の間の外の輪。巻き戻って一瞬だけ石に戻る。
//   PHASE  0 = 崩れている (灰の薄い影。当たりなし)・1 = 戻る前 (残り火の輪郭。当たりなし)・2 = 石 (足場)・3 = 消える前 (薄い石。足場)
// 状態は ForgeIsle が時刻から決め直す (ブロックの状態で同期するので、見る人と当たりが一致する)
public class RewindStoneBlock extends Block {
    public static final MapCodec<RewindStoneBlock> CODEC = simpleCodec(RewindStoneBlock::new);
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);

    public RewindStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    public static boolean solid(BlockState state) {
        return state.getValue(PHASE) >= 2;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid(state) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid(state) ? Shapes.block() : Shapes.empty();
    }
}
