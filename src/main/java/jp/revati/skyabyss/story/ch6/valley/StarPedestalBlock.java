package jp.revati.skyabyss.story.ch6.valley;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 欠片の台 (新月の谷・上弦の崖の探す場所に一つずつ。docs/design/46 の 4.3・4.4。崖の中なら CliffPuzzles)。右クリックで、その場所の決まりが済んでいれば星の欠片 (ValleyPuzzles.claim)
public class StarPedestalBlock extends Block {
    public static final MapCodec<StarPedestalBlock> CODEC = simpleCodec(StarPedestalBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    public StarPedestalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (jp.revati.skyabyss.story.ch6.cliff.MoonCliff.inside(pos)) {
                jp.revati.skyabyss.story.ch6.cliff.CliffPuzzles.claim(sp, pos);
            } else {
                ValleyPuzzles.claim(sp, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
