package jp.revati.skyabyss.relic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;

// 遺物のボタン。石のボタンと同じ (20 刻押される。向き・出力・当たり判定も同じ)。見た目だけ巨神ごと
public class RelicButtonBlock extends ButtonBlock {
    public RelicButtonBlock(Properties properties) {
        super(BlockSetType.STONE, 20, properties);
        registerDefaultState(defaultBlockState().setValue(RelicStyle.PROP, RelicStyle.WHALE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RelicStyle.PROP);
    }
}
