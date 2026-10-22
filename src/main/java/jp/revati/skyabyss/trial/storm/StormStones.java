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
            // レイドの部屋 (trial/raid/storm): 歩 = 西・智 = 東・闘 = 前 (順路の段ごとに別の枠にする)
            case "SR1" -> "west";
            case "SR2" -> "east";
            case "SR3" -> "front";
            case "S01", "S02", "S03", "S04" -> "west";
            case "S05", "S07", "S08" -> "east";
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
}
