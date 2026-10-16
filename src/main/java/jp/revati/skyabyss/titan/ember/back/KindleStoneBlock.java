package jp.revati.skyabyss.titan.ember.back;

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

// 熾り石 (13 の 3.5節・4 章)。三つの試練の奥に一つずつ。右クリックで鳴らす (西はいつでも、東は鍛え手を倒したあと、南は火床が 100 になったあと)。
// 鳴ると光り、そばの床が光って広間へ運ぶ抜け道になる。判定は EmberBackTrials.useStone。見た目はバニラの絵 (磨かれた黒石の模様と熾の光)
public class KindleStoneBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public KindleStoneBlock(Properties properties) {
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
            EmberBackTrials.useStone(server, pos, sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            EmberBackTrials.useStone(server, pos, sp);
        }
        return ItemInteractionResult.SUCCESS;
    }
}
