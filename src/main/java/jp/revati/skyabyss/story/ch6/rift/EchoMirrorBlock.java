package jp.revati.skyabyss.story.ch6.rift;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 下弦の鏡 (docs/design/46 の 4.6)。立てた丸い鏡の台。触れると 8 秒の自分の動きを覚え (MODE recording)、覚え終わると残像が鏡の中で待つ (waiting)。
// 待っているときに触れると、残像が覚えた動きをなぞる (RiftEcho)。MODE は見た目だけ (誰の残像かは RiftEcho が持つ)
public class EchoMirrorBlock extends Block {
    public static final MapCodec<EchoMirrorBlock> CODEC = simpleCodec(EchoMirrorBlock::new);
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 3, 13), Block.box(1, 3, 6.5, 15, 16, 9.5));

    public enum Mode implements StringRepresentable {
        IDLE, RECORDING, WAITING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public EchoMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, Mode.IDLE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            RiftEcho.touch(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
