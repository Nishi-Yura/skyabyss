package jp.revati.skyabyss.story.finale;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 鐘雲 (docs/design/50 の 4.2.1「鐘で雲が固まる」)。ふだんは雲 (当たりなし・落ちが遅い)。近くの鐘が鳴ると 20 秒だけ固まって白く光る。
//   SOLID  固まっている (足場)。FADE  固まりの終わりの 4 秒 (絵が薄くなる)
// 状態は BellIsle が鐘の鳴った時刻から決め直す (ブロックの状態で同期するので、見る人と当たりが一致する)
public class BellCloudBlock extends Block {
    public static final MapCodec<BellCloudBlock> CODEC = simpleCodec(BellCloudBlock::new);
    public static final BooleanProperty SOLID = BooleanProperty.create("solid");
    public static final BooleanProperty FADE = BooleanProperty.create("fade");

    public BellCloudBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SOLID, false).setValue(FADE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOLID, FADE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SOLID) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SOLID) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!state.getValue(SOLID)) {
            entity.makeStuckInBlock(state, new Vec3(0.9, 0.35, 0.9));
            entity.resetFallDistance();
        }
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) && adjacent.getValue(SOLID) == state.getValue(SOLID) || super.skipRendering(state, adjacent, side);
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
