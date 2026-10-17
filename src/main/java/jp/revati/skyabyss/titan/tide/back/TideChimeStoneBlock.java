package jp.revati.skyabyss.titan.tide.back;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

// 潮鳴り石 (08 の 4 章)。三つの試練の終わりに一つずつ。鳴ると光り、潮の広間の床の灯りが一つ点く。
// どうすれば鳴るかは試練ごとに違う (西は真珠を据える、東は岩棚に着いて触れる、前は潮招きを倒してから触れる)。判定は TideBackTrials
public class TideChimeStoneBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public TideChimeStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            TideBackTrials.useStone(server, pos, sp, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            TideBackTrials.useStone(server, pos, sp, stack);
        }
        return ItemInteractionResult.SUCCESS;
    }
}
