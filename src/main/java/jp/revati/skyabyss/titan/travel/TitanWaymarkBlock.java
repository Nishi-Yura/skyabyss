package jp.revati.skyabyss.titan.travel;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import jp.revati.skyabyss.ModText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 巨神の碑。空に向けて立てた石の札で、巨神が上から見つけられる印。
// 触れる (右クリック) と、その人の「巨神での移動」の行き先に加わる。だれの碑でも、触れた人は覚えられる。
// 金床で名付けた碑はその名で、名のない碑は土地の名 (〇〇の碑) で呼ばれる。壊すと、覚えていた人の行き先からも失われる
public class TitanWaymarkBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TitanWaymarkBlock> CODEC = simpleCodec(TitanWaymarkBlock::new);
    // 台座 (14x3x14)、碑 (10x11x4)、骨の笠 (12x2x6)。モデルは models/block/titan_waymark.json
    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 6, 13, 14, 10), Block.box(2, 14, 5, 14, 16, 11));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(6, 3, 3, 10, 14, 13), Block.box(5, 14, 2, 11, 16, 14));

    public TitanWaymarkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && level.dimension() == Level.OVERWORLD) {
            String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "";
            remember(server, pos, name);
            if (placer instanceof ServerPlayer player) {
                register(player, server, pos);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            register(sp, server, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // 世界の碑の一覧に載せる (名前と土地)。/setblock で置かれた碑も、触れたときにここで載る
    static TravelWorldData.Waymark remember(ServerLevel level, BlockPos pos, String name) {
        TravelWorldData data = TravelWorldData.get(level.getServer());
        TravelWorldData.Waymark mark = data.waymark(pos);
        if (mark == null || (!name.isEmpty() && !name.equals(mark.name()))) {
            String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
            mark = new TravelWorldData.Waymark(name.isEmpty() && mark != null ? mark.name() : name, biome);
            data.put(pos, mark);
        }
        return mark;
    }

    static void register(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            player.displayClientMessage(ModText.deny("message.skyabyss.travel.waymark.wrong_dimension"), true);
            return;
        }
        if (!level.canSeeSky(pos.above())) {
            player.displayClientMessage(ModText.deny("message.skyabyss.travel.waymark.no_sky"), true);
            return;
        }
        TravelWorldData.Waymark mark = remember(level, pos, "");
        TravelState state = Voyages.state(player);
        switch (state.addWaymark(pos)) {
            case ADDED -> {
                level.playSound(null, pos, TravelSetup.WAYMARK_SOUND.get(), SoundSource.BLOCKS, 1f, 1f);
                level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.02);
                player.sendSystemMessage(ModText.progress("message.skyabyss.travel.waymark.added",
                        mark.name().isEmpty() ? Voyages.waymarkName(mark.biome()) : net.minecraft.network.chat.Component.literal(mark.name()),
                        state.waymarks.size(), TravelState.MAX_WAYMARKS));
            }
            case ALREADY -> player.displayClientMessage(ModText.hint("message.skyabyss.travel.waymark.already"), true);
            case FULL -> player.displayClientMessage(ModText.deny("message.skyabyss.travel.waymark.full", TravelState.MAX_WAYMARKS), true);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.dimension() == Level.OVERWORLD) {
            TravelWorldData.get(server.getServer()).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // 碑文がときどき淡く光る
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
    }
}
