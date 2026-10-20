package jp.revati.skyabyss.relic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

// 遺物のレバー。挙動 (入れ切り・出力・当たり判定) は普通のレバーのまま。見た目だけ巨神ごと
public class RelicLeverBlock extends LeverBlock {
    public RelicLeverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RelicStyle.PROP, RelicStyle.WHALE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RelicStyle.PROP);
    }
}
