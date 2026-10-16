package jp.revati.skyabyss.titan.ember.back.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

// 灰の壺の台 (入口の岸)。触れると灰の壺 (灰 5 回分) を受け取る。すでに持っていれば満ちる
public class AshUrnStandBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public AshUrnStandBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            AshPath.giveUrn(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
