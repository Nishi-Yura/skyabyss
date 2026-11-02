package jp.revati.skyabyss.story.ch6.rift;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 残像の板 (docs/design/46 の 4.6)。床に埋めた丸い銀の板。人か自分の残像が上に立つと PRESSED (RiftTicker が決め直す)。
// 当たりは持たない (下の床の上に立つ)。踏まれると光る
public class EchoPlateBlock extends Block {
    public static final MapCodec<EchoPlateBlock> CODEC = simpleCodec(EchoPlateBlock::new);
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    private static final VoxelShape UP = Block.box(1, 0, 1, 15, 1, 15);
    private static final VoxelShape DOWN = Block.box(1, 0, 1, 15, 0.5, 15);

    public EchoPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PRESSED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRESSED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PRESSED) ? DOWN : UP;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
