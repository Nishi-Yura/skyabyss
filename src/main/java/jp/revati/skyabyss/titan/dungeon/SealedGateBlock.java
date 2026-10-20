package jp.revati.skyabyss.titan.dungeon;

import java.util.UUID;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 封印の扉。壊せない。触れると、開け方の手がかりをささやく。
//
// グループごとの進み (docs/design/10 の 10 章): 扉はいつもブロックとして置いてあり、開いているか閉じているかは見る人のグループで決まる。
//  - OPEN は、そのグループの記録に上書きがないときの状態 (既定)。建てたときの形で、ワールド全体の決まり (潮で開く扉など) もここ
//  - サーバーの当たり判定は、動く者 (人・グループ付きの敵・その飛び道具) のグループの上書きを見て、開いていれば通す
//  - クライアントは、サーバーから届く上書き (GroupOverlays) を自分の画面のブロックに当てる (開いたグループには消えて、ほかのグループには見える)。
//    クライアント側の当たり判定はそのブロックの状態そのものなので、サーバーの判定と合う
public class SealedGateBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public SealedGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    // この動く者にとって開いているか。サーバーでは動く者のグループの上書き、それ以外 (クライアント・所属なし) は状態そのもの
    public static boolean openFor(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        boolean open = state.getValue(OPEN);
        if (!(getter instanceof ServerLevel level) || !(context instanceof EntityCollisionContext ec)) {
            return open;
        }
        Entity entity = ec.getEntity();
        UUID group = Groups.keyOf(entity);
        return group == null ? open : GroupGates.effective(level, group, pos, open);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return openFor(state, level, pos, context) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            // 越えた試練の入口 (そのグループの画面では試練済みの封印): 一行を替える
            Notice.bar(player, jp.revati.skyabyss.trial.TrialHub.isClearedEntrance(server, Groups.keyOf(player), pos)
                    ? ModText.hint("message.skyabyss.trial_hub.sealed_block") : ModText.deny("message.skyabyss.sealed_gate"));
        } else if (!level.isClientSide) {
            Notice.bar(player, ModText.deny("message.skyabyss.sealed_gate"));
        }
        return InteractionResult.SUCCESS;
    }
}
