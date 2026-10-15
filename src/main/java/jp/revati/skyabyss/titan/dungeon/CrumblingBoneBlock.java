package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 崩れる骨の足場。乗るとひびが入り、すぐに崩れ落ちて消える。しばらくすると元に戻る。
// stepOn は止まって立っているときや着地の瞬間に呼ばれないことがあるので、
// 毎tick、プレイヤーの足もとにある骨を直接調べて崩す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public class CrumblingBoneBlock extends Block {
    public static final EnumProperty<Stage> STAGE = EnumProperty.create("stage", Stage.class);
    // 乗ってから崩れるまで / 崩れてから戻るまで (tick)
    private static final int CRACK_TICKS = 12;
    private static final int RESTORE_TICKS = 90;
    // 人が埋まっている間は戻さず、少し待つ
    private static final int RESTORE_RETRY_TICKS = 10;

    public enum Stage implements StringRepresentable {
        SOLID, CRACKING, GONE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public CrumblingBoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, Stage.SOLID));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof Player) {
            crack(level, pos, state);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (entity instanceof Player) {
            crack(level, pos, state);
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    private static void crack(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide && state.getBlock() instanceof CrumblingBoneBlock block && state.getValue(STAGE) == Stage.SOLID) {
            level.setBlock(pos, state.setValue(STAGE, Stage.CRACKING), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, block, CRACK_TICKS);
            level.playSound(null, pos, SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS, 1.2f, 0.6f);
        }
    }

    // 足の裏が触れているすべての骨を崩す (縁に立っていても、二つにまたがっていても)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.isSpectator() || !player.onGround()) {
            return;
        }
        AABB box = player.getBoundingBox();
        int y = Mth.floor(box.minY - 0.05);
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-4); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-4); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = player.level().getBlockState(pos);
                if (state.getBlock() instanceof CrumblingBoneBlock) {
                    crack(player.level(), pos, state);
                }
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        switch (state.getValue(STAGE)) {
            case CRACKING -> {
                level.setBlock(pos, state.setValue(STAGE, Stage.GONE), Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, RESTORE_TICKS);
                level.playSound(null, pos, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.4f, 0.7f);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, 0.3, 0.3, 0.3, 0.1);
            }
            case GONE -> {
                // 落ちてきた人や、下にいた人が骨の中に埋まらないよう、そこに立つ人がいなくなってから戻す
                if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
                    level.scheduleTick(pos, this, RESTORE_RETRY_TICKS);
                    return;
                }
                level.setBlock(pos, state.setValue(STAGE, Stage.SOLID), Block.UPDATE_CLIENTS);
                level.playSound(null, pos, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
            }
            default -> {
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAGE) == Stage.GONE ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAGE) == Stage.GONE ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(STAGE) == Stage.GONE ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
}
