package jp.revati.skyabyss.tablet;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 碑 (いしぶみ)。巨神の背や遺跡に刻まれた石の板。看板の代わりに、書いてある文を画面の字幕 (語り) で見せる。
// 目を向けるか右クリックすると読める (TabletEvents / useWithoutItem)。かすかに光り、ときどき光の粒が立つ。
// FACING = 文の面が向く向き。STANDING = 壁に掛けず、床に立てた碑 (台つき)
public class TabletBlock extends BaseEntityBlock {
    public static final MapCodec<TabletBlock> CODEC = simpleCodec(TabletBlock::new);
    public static final BooleanProperty STANDING = BooleanProperty.create("standing");

    // 壁掛け: 14x13x3 の板が、向きの反対側の壁にぴったり付く (models/block/tablet_wall.json)
    private static final VoxelShape WALL_N = Block.box(1, 2, 13, 15, 15, 16);
    private static final VoxelShape WALL_S = Block.box(1, 2, 0, 15, 15, 3);
    private static final VoxelShape WALL_W = Block.box(13, 2, 1, 16, 15, 15);
    private static final VoxelShape WALL_E = Block.box(0, 2, 1, 3, 15, 15);
    // 立碑: 台 (12x2x6) と碑 (10x13x4) (models/block/tablet_standing.json)
    private static final VoxelShape STAND_NS = Shapes.or(Block.box(2, 0, 5, 14, 2, 11), Block.box(3, 2, 6, 13, 15, 10));
    private static final VoxelShape STAND_EW = Shapes.or(Block.box(5, 0, 2, 11, 2, 14), Block.box(6, 2, 3, 10, 15, 13));

    public TabletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(STANDING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, STANDING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        if (state.getValue(STANDING)) {
            return facing.getAxis() == Direction.Axis.Z ? STAND_NS : STAND_EW;
        }
        return switch (facing) {
            case NORTH -> WALL_N;
            case SOUTH -> WALL_S;
            case WEST -> WALL_W;
            default -> WALL_E;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TabletBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof TabletBlockEntity tablet && !tablet.key().isEmpty()) {
            if (player instanceof ServerPlayer sp) {
                Tablets.read(sp, tablet.key());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    // かすかな光の粒 (文の面の前に、ゆっくり昇る)
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(14) != 0) {
            return;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.3 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.3 + (random.nextDouble() - 0.5) * 0.5;
        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.01, 0);
    }
}
