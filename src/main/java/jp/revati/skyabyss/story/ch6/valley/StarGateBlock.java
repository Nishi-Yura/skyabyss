package jp.revati.skyabyss.story.ch6.valley;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

// 星の門 (新月の谷の台地の北の根元と台地の上の帰り口、上弦の崖の頂の門と皿の縁の帰り口。docs/design/46 の 4.3・4.4)。右クリックで渡る (ValleyPuzzles.gate)。
//   CORE  真ん中の光る石 (明るい)
public class StarGateBlock extends Block {
    public static final MapCodec<StarGateBlock> CODEC = simpleCodec(StarGateBlock::new);
    public static final BooleanProperty CORE = BooleanProperty.create("core");

    public StarGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CORE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (jp.revati.skyabyss.story.ch6.cliff.MoonCliff.inside(pos)) {
                jp.revati.skyabyss.story.ch6.cliff.CliffPuzzles.gate(sp, pos);
            } else {
                ValleyPuzzles.gate(sp, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
