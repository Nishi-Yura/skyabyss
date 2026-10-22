package jp.revati.skyabyss.quest.free;

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

// 言伝ての板。村の人の頼みごと (日替わり・週替わりのサブクエスト) が紙で留めてある。
// 右クリックで今日の頼みごとの画面 (Board) を開く。品を納める頼みごとは、開いたときに手持ちから受け取る。
// 村ごとに一枚、鐘のそばに立つ (FreeQuestEvents.placeBoard)。作って好きな所に置いてもよい。モデルは tools/gen/story/gen_message_board.py
public class MessageBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MessageBoardBlock> CODEC = simpleCodec(MessageBoardBlock::new);
    // 北向きで x 0〜16、y 0〜16、z 5〜11 (脚・板・庇)
    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 5, 16, 16, 11);
    private static final VoxelShape SHAPE_EW = Block.box(5, 0, 0, 11, 16, 16);

    public MessageBoardBlock(Properties properties) {
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
            Board.open(sp, pos, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
