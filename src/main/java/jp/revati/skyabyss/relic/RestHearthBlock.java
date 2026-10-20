package jp.revati.skyabyss.relic;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 休み火 (休み場所)。焚き火と同じ当たり判定 (16x7x16)。熱ダメージも料理もない。
// 休み場所の判定は block タグ minecraft:campfires に入れてあるので、野営の火と同じに扱われる。LIT = 灯っている
public class RestHearthBlock extends Block {
    public static final MapCodec<RestHearthBlock> CODEC = simpleCodec(RestHearthBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 7, 16);

    public RestHearthBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(RelicStyle.PROP, RelicStyle.WHALE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, RelicStyle.PROP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(10) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.5f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f, false);
        }
        if (random.nextInt(5) == 0) {
            ParticleOptions spark = switch (state.getValue(RelicStyle.PROP)) {
                case WHALE, TIDE -> ParticleTypes.SOUL_FIRE_FLAME;
                case STORM -> ParticleTypes.ELECTRIC_SPARK;
                case EMBER -> ParticleTypes.FLAME;
            };
            level.addParticle(spark, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.6,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0, 0.04, 0);
        }
        if (random.nextInt(14) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    0, 0.07, 0);
        }
    }
}
