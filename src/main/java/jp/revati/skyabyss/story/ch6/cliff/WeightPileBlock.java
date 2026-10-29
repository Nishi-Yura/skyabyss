package jp.revati.skyabyss.story.ch6.cliff;

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

// 錘の山 (天秤の岩のそばの道の脇。右クリックで月の錘を一つ持つ。CliffBalance.pickWeight)
public class WeightPileBlock extends Block {
    public static final MapCodec<WeightPileBlock> CODEC = simpleCodec(WeightPileBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public WeightPileBlock(Properties properties) {
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
            CliffBalance.pickWeight(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
