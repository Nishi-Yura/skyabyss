package jp.revati.skyabyss.rebirth;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 飾り板 (C21)。鼓動の間に立ち、その巨神の最速の人と記録を見せる。記録が更新されると (FRESH) 光る。右クリックで巨神ごとの最速の一覧が開く。壊せない
public class RecordBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RecordBoardBlock> CODEC = simpleCodec(RecordBoardBlock::new);
    public static final BooleanProperty FRESH = BooleanProperty.create("fresh");
    private static final VoxelShape SHAPE_NS = Block.box(1, 0, 6, 15, 15, 10);
    private static final VoxelShape SHAPE_EW = Block.box(6, 0, 1, 10, 15, 15);

    public RecordBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FRESH, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FRESH);
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
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            RebirthBoards.openRecords(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
