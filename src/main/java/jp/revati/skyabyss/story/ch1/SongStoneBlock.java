package jp.revati.skyabyss.story.ch1;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

// 歌石: 苔むした古い石。右クリックで叩くと鳴る (SongStones)。鳴ったあと 1 秒ほど、刻まれた輪がほのかに光る (LIT)。
// 傍らの積み石の高さが、その石の番号 (1〜5)。順番は積み石の低いほうから
public class SongStoneBlock extends Block {
    public static final MapCodec<SongStoneBlock> CODEC = simpleCodec(SongStoneBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final int LIT_TICKS = 24;

    public SongStoneBlock(Properties properties) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            SongStones.tap(sp, server, pos);
            server.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            server.scheduleTick(pos, this, LIT_TICKS);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        }
    }
}
