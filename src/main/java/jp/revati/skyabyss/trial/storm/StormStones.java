package jp.revati.skyabyss.trial.storm;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.TrialSteles;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormBackTrials;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialCells;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;

// 雷翼の石の枠 (西 west・東 east・前 front) と、引いた試練の対応 (docs/design/29 の 3.3・5.4)。
// 広間の三つの碑と竜骨の門 (三つ鳴れば開く) は、今までどおり三つの風鳴り石 (StormProgress.rung) で数える。引いた三つの試練は、枠に割り当てる:
//   歩 (S01・S02・S03) = west。智は S05 = east・S06 = front (原型の石がある所)。闘 (S09・S10) = 智が使っていない方の枠 (S06 を引いたら east、そうでなければ front)。
// 新作の石 (甲板の島の石) が鳴ると、その枠の風鳴り石が鳴ったことになる (広間の碑が光り、仲間が広間へ戻る)。原型の枠の石は、引かれていないあいだは戸で閉じている
public final class StormStones {
    private StormStones() {
    }

    // 試練の id と、そのグループの引き → 枠の名前
    public static String keyOf(TrialDraw.Draw draw, Def def) {
        return switch (def.id()) {
            case "S01", "S02", "S03" -> "west";
            case "S05" -> "east";
            case "S06" -> "front";
            default -> draw != null && draw.has("S06") ? "east" : "front";
        };
    }

    // その枠にあたる、引いた試練 (なければ null)
    @Nullable
    public static Def cellOfKey(TrialDraw.Draw draw, String key) {
        for (TrialDraw.Pick p : draw.picks()) {
            Def d = p.def();
            if (d != null && keyOf(draw, d).equals(key)) {
                return d;
            }
        }
        return null;
    }

    // 試練の範囲 (TrialRegions)。その枠に新作を引いていれば、そのセルの箱 (なければ null = 原型の範囲)
    @Nullable
    public static AABB regionOf(ServerLevel back, UUID group, String key) {
        Def d = cellOfKey(TrialData.current(back, group), key);
        if (d == null) {
            return null;
        }
        TrialCell cell = TrialCells.get(d.id());
        return cell != null && cell.runtime() ? cell.ports().area().inflate(1, 2, 1) : null;
    }

    // 新作の石を触った (TrialRuntime.useStone から。セルが済んでいることは確かめてある)
    public static InteractionResult use(ServerLevel back, ServerPlayer sp, TrialCell cell, TrialDraw.Draw draw, boolean drawn, TrialCell.Run run) {
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record r = GroupProgress.of(back, group);
        String flag = TrialRuntime.stoneFlag(cell, draw);
        Def def = cell.def();
        String key = keyOf(draw, def);
        if (r.flag(flag)) {
            if (drawn) {
                TrialSteles.tellWho(sp, TrialSteles.STORM, key);
            } else {
                Notice.bar(sp, ModText.hint("message.skyabyss.trial.weekly_stone_rung"));
            }
            return InteractionResult.SUCCESS;
        }
        if (back.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return InteractionResult.SUCCESS;
        }
        r.setFlag(flag, true);
        GroupOverlays.refresh(back, group);
        Mods mods = run.mods;
        boolean goalDone = mods.goal() >= 0 && run.goalOk;
        BlockPos pos = cell.ports().stone();
        if (drawn) {
            StormBackState.Trial trial = StormBackState.Trial.valueOf(key.toUpperCase(java.util.Locale.ROOT));
            StormProgress p = StormProgress.of(back, group);
            // 風鳴り石が鳴る (音・経験値・広間の碑・仲間の戻り)。原型の数えは止める (この試練は下で数える)
            TrialRuntime.withoutLegacy(() -> StormBackTrials.ring(back, p, trial, sp));
        } else if (pos != null) {
            // 今週の試練 (引いていない): 石の音と光だけ
            Groups.sound(back, group, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.RESONANCE.get(), SoundSource.BLOCKS, 2.5f, 0.7f);
            for (ServerPlayer member : Groups.online(back, group)) {
                if (member.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
                    back.sendParticles(member, ParticleTypes.END_ROD, false, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
                }
            }
        }
        TrialRuntime.onCellCleared(back, group, def, mods, goalDone, sp);
        return InteractionResult.SUCCESS;
    }
}
