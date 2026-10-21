package jp.revati.skyabyss.titan.shrine;

import com.mojang.serialization.MapCodec;

import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.relic.RelicStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 巨神の祠の碑 (docs/design/32 の 3.1)。巨神の碑 (titan_waymark) と同じ形で、笠がその巨神の材質。titan = その巨神。
//   右クリック: 祠の画面 (引いてある三つ・修飾・周回・歌片の数・深淵のつまみ・順路の記録)。しゃがんで右クリック: 誓いの一覧 (チャット)
//   その巨神の歌片を手に持って長押し (1.5 秒): 輪の中のパーティを順路へ運ぶ (TitanShrine.activate)
public class TitanShrineBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TitanShrineBlock> CODEC = simpleCodec(TitanShrineBlock::new);
    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 6, 13, 14, 10), Block.box(2, 14, 5, 14, 16, 11));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(6, 3, 3, 10, 14, 13), Block.box(5, 14, 2, 11, 16, 14));

    public TitanShrineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(RelicStyle.PROP, RelicStyle.WHALE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RelicStyle.PROP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    public static String titanOf(BlockState state) {
        return switch (state.getValue(RelicStyle.PROP)) {
            case TIDE -> jp.revati.skyabyss.progress.Titans.TIDE_SHELL;
            case STORM -> jp.revati.skyabyss.progress.Titans.THUNDER_WING;
            case EMBER -> jp.revati.skyabyss.progress.Titans.EMBER;
            default -> jp.revati.skyabyss.progress.Titans.STONE_WHALE;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        String titan = titanOf(state);
        if (hand == InteractionHand.MAIN_HAND && stack.is(ModItems.songShard(titan))) {
            if (player instanceof ServerPlayer sp) {
                TitanShrine.hold(sp, titan, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            TitanShrine.open(sp, titanOf(state), player.isShiftKeyDown());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
