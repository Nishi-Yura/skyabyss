package jp.revati.skyabyss.story.ch6.valley;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 影の橋 (docs/design/46 の 4.2・4.3「影の道」)。暗がりにだけある黒い硝子の足場。
//   SOLID  足場がある (暗い)。星灯りの中・位相の合わない脇の列では消える (当たりも絵も薄い影だけ)
//   WIDE   両脇の列 (新月のときだけ足場。真ん中の列はいつでも暗がりなら足場)
// 状態は ValleyTicker が星灯りと位相から決め直す (ブロックの状態で同期するので、見る人と当たりが一致する)
public class ShadowBridgeBlock extends Block {
    public static final MapCodec<ShadowBridgeBlock> CODEC = simpleCodec(ShadowBridgeBlock::new);
    public static final BooleanProperty SOLID = BooleanProperty.create("solid");
    public static final BooleanProperty WIDE = BooleanProperty.create("wide");

    public ShadowBridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SOLID, true).setValue(WIDE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOLID, WIDE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SOLID) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SOLID) ? Shapes.block() : Shapes.empty();
    }

    // 隣の橋どうしの面は描かない (硝子と同じ)
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) && adjacent.getValue(SOLID) == state.getValue(SOLID) || super.skipRendering(state, adjacent, side);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
