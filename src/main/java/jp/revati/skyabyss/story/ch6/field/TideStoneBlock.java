package jp.revati.skyabyss.story.ch6.field;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 満ち引きの石 (docs/design/46 の 4.5「光の満ち引き」)。
//   SHADE  false = 光の石 (白く光る石英の結晶。満ちのあいだ足場) / true = 影の石 (黒い硝子。引きのあいだ足場)
//   STATE  0 = 透けて通れる (薄い輪郭だけ) / 1 = 足場 / 2 = 足場だが瞬く (もうすぐ透ける予告)
// 状態は FieldTide が満ち引きと一瞬止めから決め直す (ブロックの状態で同期するので、見る人と当たりが一致する)
public class TideStoneBlock extends Block {
    public static final MapCodec<TideStoneBlock> CODEC = simpleCodec(TideStoneBlock::new);
    public static final BooleanProperty SHADE = BooleanProperty.create("shade");
    public static final IntegerProperty STATE = IntegerProperty.create("state", 0, 2);

    public TideStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SHADE, false).setValue(STATE, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHADE, STATE);
    }

    public static boolean solid(BlockState state) {
        return state.getValue(STATE) > 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid(state) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid(state) ? Shapes.block() : Shapes.empty();
    }

    // 隣の同じ種類の石どうしの面は描かない (硝子と同じ)
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) && adjacent.getValue(SHADE) == state.getValue(SHADE) && solid(adjacent) == solid(state)
                || super.skipRendering(state, adjacent, side);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
