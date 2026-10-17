package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.TitanTravel;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

// 共鳴石。触れると歌い出し、光り続ける。岩鯨の背の3つがそろうと封印の扉が解ける
public class ResonanceStoneBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ResonanceStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(ACTIVE)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        boolean onBack = server.dimension() == TitanTravel.TITAN_BACK;
        int index = TitanBackLayout.RESONANCE_STONES.indexOf(pos);
        // 番兵の回廊の石は、番兵長を倒すまで黙っている
        if (onBack && index == 1 && !TitanBackTrials.isHallCleared(server, "e3")) {
            Notice.bar(player, ModText.deny("message.skyabyss.stone_silent"));
            return InteractionResult.SUCCESS;
        }
        server.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);
        server.playSound(null, pos, ModSounds.RESONANCE.get(), SoundSource.BLOCKS, 2.5f, 0.7f);
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);

        if (onBack && index >= 0) {
            // 石が歌うと、その試練の奥から鼓動の間 (入口の通路) へ戻る抜け道が開く
            List<BlockPos> shortcut = switch (index) {
                case 0 -> TitanBackLayout.WEST_RETURN;
                case 1 -> TitanBackLayout.EAST_RETURN;
                default -> TitanBackLayout.LOWER_RETURN;
            };
            TitanBackTrials.open(server, shortcut);
            Notice.bar(player, ModText.hint("message.skyabyss.return_opened"));
            int active = countActive(server);
            int total = TitanBackLayout.RESONANCE_STONES.size();
            notifyNearby(server, ModText.progress("message.skyabyss.resonance", active, total));
            if (active >= total) {
                openGate(server);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static int countActive(ServerLevel level) {
        int count = 0;
        for (BlockPos p : TitanBackLayout.RESONANCE_STONES) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ResonanceStoneBlock && s.getValue(ACTIVE)) {
                count++;
            }
        }
        return count;
    }

    // 3つの歌が重なり、封印が息を吐くように崩れる
    private static void openGate(ServerLevel level) {
        TitanBackData data = TitanBackData.get(level);
        if (data.isGateOpened()) {
            return;
        }
        data.markGateOpened();
        TitanBackTrials.open(level, TitanBackLayout.GATE);
        BlockPos center = TitanBackLayout.GATE.get(4);
        level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.6f);
        level.playSound(null, center, ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 4f, 1.1f);
        notifyNearby(level, ModText.lore("message.skyabyss.gate_opened"));
    }

    private static void notifyNearby(ServerLevel level, Component message) {
        AABB area = new AABB(-60, 60, -80, 60, 130, 20);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            Notice.send(player, message);
        }
    }
}
