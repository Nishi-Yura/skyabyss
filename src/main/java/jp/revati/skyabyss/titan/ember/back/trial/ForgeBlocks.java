package jp.revati.skyabyss.titan.ember.back.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 鍛えの間のブロック 4 つ (13 の 4.2節): 大炉の口 (塊の色)、鞴の踏み板、金床、焼き入れの槽。
// どれも見た目と触れたときの入口だけ。熱・色・打つ順の中身は Forge が持つ
public final class ForgeBlocks {
    private ForgeBlocks() {
    }

    // 大炉の口。炉の熱の色で塊が光る。段階 0 黒・1 赤・2 橙・3 黄・4 白・5 塊なし (割れた直後・鍵にした直後)。cracked は一度打ち損ねたひび
    public static class AshForgeBlock extends Block {
        public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 5);
        public static final BooleanProperty CRACKED = BooleanProperty.create("cracked");
        public static final int[] LIGHT = {5, 8, 11, 13, 15, 4};

        public AshForgeBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(STAGE, 0).setValue(CRACKED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(STAGE, CRACKED);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                Forge.inspect(server, sp);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    // 鞴の踏み板。乗る・跳んで乗り直すと炉の熱が +12 (Forge が見回る)。見た目の押された状態だけここ
    public static class BellowsBlock extends Block {
        public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
        private static final VoxelShape UP = Block.box(1, 0, 1, 15, 3, 15);
        private static final VoxelShape DOWN = Block.box(1, 0, 1, 15, 1.5, 15);

        public BellowsBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(PRESSED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(PRESSED);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(PRESSED) ? DOWN : UP;
        }
    }

    // 金床。右クリックで炉の塊を打つ
    public static class AshAnvilBlock extends Block {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(4, 4, 3, 12, 5, 13),
                Block.box(6, 5, 4, 10, 10, 12), Block.box(3, 10, 0, 13, 16, 16));

        public AshAnvilBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                Forge.strike(server, sp);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    // 焼き入れの槽。焼き入れ前の火の鍵を持って右クリックすると、焼き入れた鍵になる
    public static class QuenchTroughBlock extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

        public QuenchTroughBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                if (stack.is(EmberTrialSetup.FIRE_KEY_HOT.get())) {
                    Forge.quench(server, sp, hand, pos);
                } else {
                    EmberTrials.hint(sp, jp.revati.skyabyss.titan.ember.EmberIds.LANG_EAST + "trough_hint");
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
    }
}
