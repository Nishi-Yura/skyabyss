package jp.revati.skyabyss.titan.ember.back.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

// 冷え殻 (13 の 4.1節): 溶岩に灰をまくとできる黒い殻。上を歩ける。崩れる時計は AshPath が持つ (段階だけここ)。
// 段階 0 黒 (8 秒まで)、1 割れ目が赤く光る (ぴしぴし鳴る)、2 沈みかけ。火の粉蛾は冷え殻に寄る (EmberMoth が名前で探す)
public class CooledCrustBlock extends Block {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    public static final int[] LIGHT = {3, 9, 13};

    public CooledCrustBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    // 時計を持たない冷え殻 (サーバーを再起動した・コマンドで置いた) は、今から数え始める
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        AshPath.adopt(level, pos);
    }
}
