package jp.revati.skyabyss.titan.storm.back;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 雷翼の背の試練のブロック (どれも壊せない。見た目はバニラのテクスチャを使う)
public final class StormBlocks {
    private StormBlocks() {
    }

    // 風鳴り石 (三つの試練の終わり)。鳴ると光る。どうすれば鳴るかは試練ごと (StormBackTrials.useStone)
    public static class ChimeStone extends Block {
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        public ChimeStone(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(ACTIVE);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                StormBackTrials.useStone(server, pos, sp);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                StormBackTrials.useStone(server, pos, sp);
            }
            return ItemInteractionResult.SUCCESS;
        }
    }

    // 羽鏡 (眼の間)。磨いた銅の羽を 45 度に立てた片面の鏡。右クリックで 90 度ずつ回る (ROT 0〜3)。
    // ROT k の鏡は、二つの向きの光をつなぐ: 0 = 北と東、1 = 東と南、2 = 南と西、3 = 西と北 (EyeMirrors)
    public static class Mirror extends Block {
        public static final IntegerProperty ROT = IntegerProperty.create("rot", 0, 3);
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

        public Mirror(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(ROT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(ROT);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                EyeMirrors.turn(server, pos, sp);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                EyeMirrors.turn(server, pos, sp);
            }
            return ItemInteractionResult.SUCCESS;
        }
    }

    // 受け羽 (眼の間の壁の白い羽の飾り)。光が当たると光り続ける
    public static class Receiver extends Block {
        public static final BooleanProperty LIT = BooleanProperty.create("lit");

        public Receiver(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LIT);
        }
    }

    // ---- 避雷の柱 (12 の 120。銅の台・段の電球 3 つ・軸・先の針。StormBirdBuilder.rod が積み、StormRods が満ちかけを調べる) ----

    // 柱の台と軸と針。形だけで、状態を持たない
    public static class RodPost extends Block {
        private final VoxelShape shape;

        public RodPost(Properties p, VoxelShape shape) {
            super(p);
            this.shape = shape;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shape;
        }
    }

    // 段の電球。柱が満ちた段だけ青白く光る (LIT)。光の段は StormEast・RaijuNest・StormHeartArena が切り替える
    public static class RodBulb extends Block {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

        public RodBulb(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LIT);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    // 戻しの鏡 (前室の台の上の取っ手つきの手鏡。12 の 120)。引く動作は StormBackTrials の右クリックが受ける (座標 MIRROR_RESET)
    public static class ReturnMirror extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

        public ReturnMirror(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }
}
