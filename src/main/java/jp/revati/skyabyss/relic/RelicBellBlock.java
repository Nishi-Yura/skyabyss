package jp.revati.skyabyss.relic;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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

// 遺物の鐘。門の枠に吊った鐘で、当たり判定は鐘 (床置き) と同じ。叩くと鳴る。
// 鳴らす処理 (数え歌の鐘楼など) は位置で横取りする。RING = 鳴っている間の音の輪の見た目 (pulse)
public class RelicBellBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RelicBellBlock> CODEC = simpleCodec(RelicBellBlock::new);
    public static final BooleanProperty RING = BooleanProperty.create("ring");
    private static final VoxelShape NS = Block.box(0, 0, 4, 16, 16, 12);
    private static final VoxelShape EW = Block.box(4, 0, 0, 12, 16, 16);

    public RelicBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RING, false).setValue(RelicStyle.PROP, RelicStyle.WHALE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RING, RelicStyle.PROP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
            pulse(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RING)) {
            level.setBlock(pos, state.setValue(RING, false), 3);
        }
    }

    // 音の輪の見た目を 1 秒ほど出す (鳴らす処理から呼ぶ)
    public static void pulse(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof RelicBellBlock) {
            level.setBlock(pos, s.setValue(RING, true), 3);
            level.scheduleTick(pos, s.getBlock(), 24);
        }
    }
}
