package jp.revati.skyabyss.trial;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 試練の碑 (docs/design/29 の 4.6)。鼓動の間に、試練の枠 (歩・智・闘。余燼は西・東・南) ごとに一つ立つ石の板。
// サーバーの世界ではいつも消えた姿 (LIT なし)。そのグループが試練を越えたら、そのグループの画面でだけ光る (HubTablets の見かけの上書き)。
// 当たり判定は同じ。壊せない
public class TrialTabletBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TrialTabletBlock> CODEC = simpleCodec(TrialTabletBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE_NS = Block.box(1, 0, 6, 15, 15, 10);
    private static final VoxelShape SHAPE_EW = Block.box(6, 0, 1, 10, 15, 15);

    public TrialTabletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }
}
