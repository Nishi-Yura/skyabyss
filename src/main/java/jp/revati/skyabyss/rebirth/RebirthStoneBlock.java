package jp.revati.skyabyss.rebirth;

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

// 順位の石 (C11)。鼓動の間に立つ低い石の柱。右クリックで順位表 (転生数・最速・最少の死) が開く。壊せない
public class RebirthStoneBlock extends Block {
    public static final MapCodec<RebirthStoneBlock> CODEC = simpleCodec(RebirthStoneBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public RebirthStoneBlock(Properties properties) {
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
            RebirthBoards.openLeaderboard(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
