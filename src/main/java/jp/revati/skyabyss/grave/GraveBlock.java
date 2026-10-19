package jp.revati.skyabyss.grave;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// 墓 (苔むした石の積み石。かすかに光る)。倒れた人の持ち物の入口。
// 中身は GraveData が持つ (ブロックは目印)。壊せない (クリエイティブで壊すと中身は足もとへ落ちる)。
// 右クリックで、開けてよい人 (持ち主、またはゲームルールで許された人) の手もとへ中身が移る。
// 控えがなくなったブロック (語り部の救いのあと、チャンクが読み込まれていなかった墓) は、次の見回りで消える
public class GraveBlock extends Block {
    public static final MapCodec<GraveBlock> CODEC = simpleCodec(GraveBlock::new);
    // 積み石の形 (models/block/grave.json)
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 9, 14);
    static final int CHECK_TICKS = 200;

    public GraveBlock(Properties properties) {
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Graves.patrol(level, pos, this);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            Graves.interact(sp, sl, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // どんな形でも (クリエイティブ・コマンド・ピストン以外) 消えたら、中身を足もとへ落とす (失わない)
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            Graves.spill(sl, pos);
            jp.revati.skyabyss.rebirth.RebirthFx.graveRemoved(sl, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(16) != 0) {
            return;
        }
        double x = pos.getX() + 0.35 + random.nextDouble() * 0.3;
        double y = pos.getY() + 0.6 + random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.35 + random.nextDouble() * 0.3;
        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.012, 0);
    }
}
