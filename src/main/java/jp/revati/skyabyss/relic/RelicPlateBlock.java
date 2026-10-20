package jp.revati.skyabyss.relic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;

// 遺物の感圧板。踏んだ間だけ出力する (磨いた黒石の板と同じ)。見た目だけ巨神ごと
public class RelicPlateBlock extends PressurePlateBlock {
    public RelicPlateBlock(Properties properties) {
        super(BlockSetType.POLISHED_BLACKSTONE, properties);
        registerDefaultState(defaultBlockState().setValue(RelicStyle.PROP, RelicStyle.WHALE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RelicStyle.PROP);
    }
}
