package jp.revati.skyabyss.story.ch6.valley;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 灯りの台 (主の間のまわりに六つ。docs/design/46 の 4.3)。星の灯を持っている人が右クリックすると LIT_TICKS のあいだ灯り、
// まわり RADIUS を星灯りにする (StarLamps に足す。影抱きはその中で固まる)。消えるのは時間と、影抱きの影の波
public class LampPostBlock extends Block {
    public static final MapCodec<LampPostBlock> CODEC = simpleCodec(LampPostBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final int LIT_TICKS = 500;
    public static final double RADIUS = 5.0;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public LampPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (!StarLampItem.carries(sp)) {
                jp.revati.skyabyss.notice.Notice.narrate(sp, "message.skyabyss.moon.lamp_post.need_lamp");
                return InteractionResult.SUCCESS;
            }
            light(sl, pos, sp.getUUID());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void light(ServerLevel level, BlockPos pos, java.util.UUID owner) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof LampPostBlock)) {
            return;
        }
        level.setBlock(pos, st.setValue(LIT, true), 3);
        level.scheduleTick(pos, st.getBlock(), LIT_TICKS);
        StarLamps.addPost(level, pos, RADIUS, level.getGameTime() + LIT_TICKS);
        level.playSound(null, pos, ValleyBlocks.STAR_LAMP_PLACE.get(), SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    public static void snuff(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof LampPostBlock && st.getValue(LIT)) {
            level.setBlock(pos, st.setValue(LIT, false), 3);
            StarLamps.removePost(level, pos);
            level.playSound(null, pos, ValleyBlocks.STAR_LAMP_FADE.get(), SoundSource.BLOCKS, 1.0f, 0.8f);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && !StarLamps.postLit(level, pos)) {
            snuff(level, pos);
        }
    }
}
