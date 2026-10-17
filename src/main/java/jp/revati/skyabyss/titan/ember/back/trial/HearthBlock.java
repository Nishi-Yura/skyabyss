package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 余燼の火床 (13 の 4.3節): 谷の奥の火。炎の高さと色が火の強さ (level 0〜4 = 強さ 0〜24, 25〜49, 50〜74, 75〜99, 100)。
// 熾 (live_coal) を持って右クリックすると +8
public class HearthBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    public static final int[] LIGHT = {4, 8, 11, 13, 15};
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public HearthBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            if (stack.is(EmberTrialSetup.LIVE_COAL.get())) {
                Hearth.feed(server, sp, stack);
            } else {
                EmberTrials.hint(sp, EmberIds.LANG_SOUTH + "hearth_hint");
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
            EmberTrials.hint(sp, EmberIds.LANG_SOUTH + "hearth_hint");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
