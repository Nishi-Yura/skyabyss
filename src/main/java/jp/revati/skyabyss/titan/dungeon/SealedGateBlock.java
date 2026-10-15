package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.ModText;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// 封印の扉。壊せない。触れると、開け方の手がかりをささやく
public class SealedGateBlock extends Block {
    public SealedGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(ModText.deny("message.skyabyss.sealed_gate"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
