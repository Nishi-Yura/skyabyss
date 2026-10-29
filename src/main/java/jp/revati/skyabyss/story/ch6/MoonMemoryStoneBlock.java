package jp.revati.skyabyss.story.ch6;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 記憶の石 (docs/design/46 の 4.9)。区画ごとに一つ、道から少し外れた所に立つ銀の小さな碑。右クリックで前の自分の記憶の断片 (MoonMemories)
public class MoonMemoryStoneBlock extends Block {
    public static final MapCodec<MoonMemoryStoneBlock> CODEC = simpleCodec(MoonMemoryStoneBlock::new);
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 3);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 14, 12);

    public MoonMemoryStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SECTION, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SECTION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            MoonMemories.onStone(sp, state.getValue(SECTION));
            sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.2, 0.4, 0.2, 0.02);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.7f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
