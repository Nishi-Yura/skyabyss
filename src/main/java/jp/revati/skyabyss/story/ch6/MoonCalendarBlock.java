package jp.revati.skyabyss.story.ch6;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 月の暦 (docs/design/46 の 4.8)。着く所の南の台。銀の円盤に四つの位相が刻まれている。右クリックで位相の挑戦の名前をチャットに (MoonTrial.menu)
public class MoonCalendarBlock extends Block {
    public static final MapCodec<MoonCalendarBlock> CODEC = simpleCodec(MoonCalendarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 10, 13), Block.box(0, 10, 0, 16, 13, 16));

    public MoonCalendarBlock(Properties properties) {
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
            MoonTrial.menu(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
