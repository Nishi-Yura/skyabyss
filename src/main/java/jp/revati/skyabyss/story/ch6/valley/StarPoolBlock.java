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

// 星の溜まり (谷と崖の野営。docs/design/46 の 4.3・4.4。崖の中なら月の溜まり = 軽身を覚える CliffPuzzles.pool)。右クリックで星術「星灯り」を覚える = 星の灯をもらう (失くしてもまたもらえる)
public class StarPoolBlock extends Block {
    public static final MapCodec<StarPoolBlock> CODEC = simpleCodec(StarPoolBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 6, 15);

    public StarPoolBlock(Properties properties) {
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
                jp.revati.skyabyss.story.ch6.cliff.CliffPuzzles.pool(sp, pos);
            } else if (jp.revati.skyabyss.story.ch6.field.MoonField.inside(pos)) {
                jp.revati.skyabyss.story.ch6.field.FieldPuzzles.pool(sp, pos);
            } else {
                ValleyPuzzles.pool(sp, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
