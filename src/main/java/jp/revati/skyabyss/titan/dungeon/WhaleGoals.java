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
import net.minecraft.world.phys.AABB;

// 岩鯨の背の原型の試練 (西・東・下) のゴール。石はもう無い: 試練の終わりのゴールの印 (TrialGoal) に着くと、その試練を越えたことになる。
// 三つ越えると宝物庫の封印が解ける
public final class WhaleGoals {
    private WhaleGoals() {
    }

    // ゴールに着いた (TrialStones.autoStones から)。index = 西 0 / 東 1 / 下 2。着けなかった理由は一行で言う
    public static InteractionResult arrive(ServerLevel server, ServerPlayer sp, BlockPos pos) {
        int index = TitanBackLayout.GOAL_SPOTS.indexOf(pos);
        if (server.dimension() != TitanTravel.TITAN_BACK || index < 0) {
            return InteractionResult.PASS;
        }
        Player player = sp;
        // 背中のゴールの「着いたか」はグループの記録にある (世界のブロックは変わらない。金の印に見えるのは、着いたグループの画面だけ)
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record record = GroupProgress.of(server, group);
        if (record.flag(TitanBackTrials.STONE_FLAG + index)) {
            return InteractionResult.SUCCESS;
        }
        // 番兵の回廊のゴールは、そのグループが番兵長を倒すまで着けない
        if (index == 1 && !record.isHallCleared("e3")) {
            Notice.bar(player, ModText.deny("message.skyabyss.goal_not_yet"));
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
        // 着いたら、順路が少しして仲間を次の段の入口へ運ぶ (TrialRoute)
        Notice.bar(player, ModText.hint("message.skyabyss.return_opened"));
        // 背の外にいる仲間にも (クエストはグループの記録から数える)
        jp.revati.skyabyss.titan.group.TrialSteles.onRung(server, group, jp.revati.skyabyss.titan.group.TrialSteles.WHALE, String.valueOf(index),
                Component.translatable("message.skyabyss.goal_name"), sp);
        finishRung(server, sp, group, record);
        return InteractionResult.SUCCESS;
    }

    // ゴールに着いたあとの数え: 何個目かを知らせ、三つそろえば宝物庫の封印を解く (試練の階のセルからも使う)
    public static void finishRung(ServerLevel server, ServerPlayer sp, UUID group, BackGroupData.Record record) {
        int active = countActive(record);
        int total = TitanBackLayout.GOAL_SPOTS.size();
        notifyGroup(server, group, ModText.progress("message.skyabyss.resonance", active, total));
        if (active >= total) {
            openGate(server, group, record);
        }
    }

    private static int countActive(BackGroupData.Record record) {
        int count = 0;
        for (int i = 0; i < TitanBackLayout.GOAL_SPOTS.size(); i++) {
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
        BlockPos center = TitanBackLayout.GATE.get(TitanBackLayout.GATE.size() / 2);
        Groups.sound(level, group, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.6f);
        Groups.sound(level, group, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 4f, 1.1f);
        notifyGroup(level, group, ModText.lore("message.skyabyss.gate_opened"));
    }

    // 近くにいる、そのグループの人へ
    private static void notifyGroup(ServerLevel level, UUID group, Component message) {
        AABB area = new AABB(-90, 40, -200, 90, 210, 30);
        for (ServerPlayer player : Groups.online(level, group)) {
            if (area.contains(player.position())) {
                Notice.send(player, message);
            }
        }
    }
}
