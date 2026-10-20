package jp.revati.skyabyss.trial;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// 試練済みの封印 (docs/design/29 の 4.6)。越えた試練の入口に、そのグループの画面だけに立つ (TrialHub.sealOverlay の見かけの上書き)。
// ワールドには置かない (置いてあるのは封印の扉。そのグループの記録で閉じる)。見かけの当たり判定は一つの完全なブロック。壊せない。
// 触れると「この試練は越えた」と一行出る
public class TrialSealBlock extends Block {
    public TrialSealBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial_hub.sealed_block"));
        }
        return InteractionResult.SUCCESS;
    }
}
