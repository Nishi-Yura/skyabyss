package jp.revati.skyabyss.story.ch6.cliff;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

// 月の錘 (持ち物)。ふつうには置けない。西の皿の石の上で右クリックすると、皿に載せる (CliffBalance.setWeight)
public class MoonWeightItem extends BlockItem {
    public MoonWeightItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
            CliffBalance.setWeight(sp, ctx.getClickedPos(), ctx.getItemInHand());
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
