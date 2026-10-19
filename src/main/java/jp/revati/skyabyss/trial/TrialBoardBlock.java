package jp.revati.skyabyss.trial;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 試練の板 (docs/design/29 の 2.7)。右クリックで、いまのグループの「引いた三つ」(名前・類・段・修飾の札)、図鑑、称号の画面を開く。
// 鼓動の間 (南の壁ぎわ) と試練の階の広間 (北の壁ぎわ) に立つ。壊せない。モデルは tools/gen_trial_ui.py の絵
public class TrialBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TrialBoardBlock> CODEC = simpleCodec(TrialBoardBlock::new);
    // 北向きで x 1〜15、y 0〜15、z 6〜10
    private static final VoxelShape SHAPE_NS = Block.box(1, 0, 6, 15, 15, 10);
    private static final VoxelShape SHAPE_EW = Block.box(6, 0, 1, 10, 15, 15);

    public TrialBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            TrialBoard.open(sp, null);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
