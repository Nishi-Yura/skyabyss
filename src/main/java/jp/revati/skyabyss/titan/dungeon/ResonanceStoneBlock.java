package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.TitanTravel;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;

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
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        boolean onBack = server.dimension() == TitanTravel.TITAN_BACK;
        int index = TitanBackLayout.RESONANCE_STONES.indexOf(pos);
        if (!onBack || index < 0) {
            // 岩鯨の背の外の石 (ふつうのブロックとして置いた石): 光るのはワールド全体
            server.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);
            server.playSound(null, pos, ModSounds.RESONANCE.get(), SoundSource.BLOCKS, 2.5f, 0.7f);
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
            return InteractionResult.SUCCESS;
        }
        // 背中の石の「鳴ったか」はグループの記録にある (世界のブロックは光らない。光って見えるのは、鳴らしたグループの画面だけ)
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record record = GroupProgress.of(server, group);
        if (record.flag(TitanBackTrials.STONE_FLAG + index)) {
            return InteractionResult.PASS;
        }
        // 番兵の回廊の石は、そのグループが番兵長を倒すまで黙っている
        if (index == 1 && !record.isHallCleared("e3")) {
            Notice.bar(player, ModText.deny("message.skyabyss.stone_silent"));
            return InteractionResult.SUCCESS;
        }
        record.setFlag(TitanBackTrials.STONE_FLAG + index, true);
        GroupOverlays.refresh(server, group);
        Groups.sound(server, group, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.RESONANCE.get(), SoundSource.BLOCKS, 2.5f, 0.7f);
        for (ServerPlayer member : Groups.online(server, group)) {
            if (member.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
                server.sendParticles(member, ParticleTypes.END_ROD, false, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
            }
        }
        // 石が歌うと、その試練の奥から鼓動の間 (入口の通路) へ戻る抜け道が開く
        List<BlockPos> shortcut = switch (index) {
            case 0 -> TitanBackLayout.WEST_RETURN;
            case 1 -> TitanBackLayout.EAST_RETURN;
            default -> TitanBackLayout.LOWER_RETURN;
        };
        GroupGates.open(server, group, shortcut);
        Notice.bar(player, ModText.hint("message.skyabyss.return_opened"));
        // 背中の外にいる仲間にも (クエストはグループの記録から数える)
        jp.revati.skyabyss.titan.group.TrialSteles.onRung(server, group, jp.revati.skyabyss.titan.group.TrialSteles.WHALE, String.valueOf(index),
                Component.translatable("block.skyabyss.resonance_stone"));
        int active = countActive(record);
        int total = TitanBackLayout.RESONANCE_STONES.size();
        notifyGroup(server, group, ModText.progress("message.skyabyss.resonance", active, total));
        if (active >= total) {
            openGate(server, group, record);
        }
        return InteractionResult.SUCCESS;
    }

    private static int countActive(BackGroupData.Record record) {
        int count = 0;
        for (int i = 0; i < TitanBackLayout.RESONANCE_STONES.size(); i++) {
            if (record.flag(TitanBackTrials.STONE_FLAG + i)) {
                count++;
            }
        }
        return count;
    }

    // 3つの歌が重なり、封印が息を吐くように崩れる (そのグループにだけ)
    private static void openGate(ServerLevel level, UUID group, BackGroupData.Record record) {
        if (record.isGateOpened()) {
            return;
        }
        record.markGateOpened();
        GroupGates.open(level, group, TitanBackLayout.GATE);
        GroupGates.open(level, group, TitanBackLayout.INNER_GATE);
        BlockPos center = TitanBackLayout.GATE.get(4);
        Groups.sound(level, group, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.6f);
        Groups.sound(level, group, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 4f, 1.1f);
        notifyGroup(level, group, ModText.lore("message.skyabyss.gate_opened"));
    }

    // 近くにいる、そのグループの人へ
    private static void notifyGroup(ServerLevel level, UUID group, Component message) {
        AABB area = new AABB(-60, 60, -80, 60, 130, 20);
        for (ServerPlayer player : Groups.online(level, group)) {
            if (area.contains(player.position())) {
                Notice.send(player, message);
            }
        }
    }
}
