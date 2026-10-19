package jp.revati.skyabyss.titan.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

// 灯の数え石 (docs/design/23)。水路の入口の小部屋にある、五つの丸い窪みのある石。
// そのグループがともした灯の数だけ丸が灯る (COUNT は 0〜5。背中の世界のブロックは 0 のまま、数はグループの記録から GroupOverlays で当てる)。
// 右クリックで、目的 (灯を何本ともせば出口の扉が開くか) と、いまの数を伝える
public class AnemoneTallyBlock extends Block {
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 5);

    public AnemoneTallyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COUNT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COUNT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            AnemoneLights.readTally(server, sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
