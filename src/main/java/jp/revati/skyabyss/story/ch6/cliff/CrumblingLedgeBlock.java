package jp.revati.skyabyss.story.ch6.cliff;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 崩れる棚 (上弦の崖の探す場所 2。docs/design/46 の 4.4)。人が乗ると 1 秒震えて (粒と音が予告) 落ち、5 秒で戻る。
//   止まらずに渡り切ること。落ちた棚は当たりも絵もない (FALLEN)。戻るとき、そこに人がいれば少し待つ
public class CrumblingLedgeBlock extends Block {
    public static final MapCodec<CrumblingLedgeBlock> CODEC = simpleCodec(CrumblingLedgeBlock::new);
    public static final BooleanProperty FALLEN = BooleanProperty.create("fallen");
    public static final BooleanProperty SHAKING = BooleanProperty.create("shaking");
    public static final int SHAKE_TICKS = 20;
    public static final int BACK_TICKS = 100;

    public CrumblingLedgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FALLEN, false).setValue(SHAKING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FALLEN, SHAKING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FALLEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FALLEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FALLEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (!level.isClientSide && entity instanceof Player && !state.getValue(FALLEN) && !state.getValue(SHAKING)) {
            shake((ServerLevel) level, pos, state);
        }
    }

    public static void shake(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(SHAKING, true), 3);
        level.scheduleTick(pos, state.getBlock(), SHAKE_TICKS);
        level.playSound(null, pos, CliffBlocks.LEDGE_SHAKE.get(), SoundSource.BLOCKS, 0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.1,
                pos.getZ() + 0.5, 8, 0.3, 0.05, 0.3, 0.02);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SHAKING)) {
            // 落ちる
            level.setBlock(pos, state.setValue(SHAKING, false).setValue(FALLEN, true), 3);
            level.scheduleTick(pos, this, BACK_TICKS);
            level.playSound(null, pos, CliffBlocks.LEDGE_FALL.get(), SoundSource.BLOCKS, 0.9f, 0.8f + random.nextFloat() * 0.2f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 30, 0.35, 0.3, 0.35, 0.05);
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.01);
            return;
        }
        if (state.getValue(FALLEN)) {
            // 戻る (そこに人がいれば 1 秒待つ)
            if (!level.getEntitiesOfClass(Entity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
                level.scheduleTick(pos, this, 20);
                return;
            }
            level.setBlock(pos, state.setValue(FALLEN, false), 3);
            level.playSound(null, pos, CliffBlocks.LEDGE_BACK.get(), SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    // 自己テスト・片付け: すぐ元に戻す
    public static void restore(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof CrumblingLedgeBlock) {
            level.setBlock(pos, st.setValue(FALLEN, false).setValue(SHAKING, false), 3);
        }
    }
}
