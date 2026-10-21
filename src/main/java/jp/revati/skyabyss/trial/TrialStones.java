package jp.revati.skyabyss.trial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.TrialSteles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 原型の石は近づくだけで鳴らす (版 25)。原型の石 (岩鯨の共鳴石・潮鳴り石・風鳴り石・熾り石の、引いた枠のセルが原型のもの) は、押さなくても、
// 石に近づけば「押した」ことになる (中身は右クリックと同じ: 条件が足りなければ、石がなぜ黙っているかを言う。同じ人・同じ石には STONE_RETRY tick に一度だけ)。
// 新作のセルの石は、セルが終わった時に自動 (TrialRuntime.autoRing) で鳴る。TrialRoute の tick から呼ぶ
public final class TrialStones {
    private TrialStones() {
    }

    private static final int STONE_RETRY = 160;
    private static final double STONE_REACH = 2.6;
    private static final Map<String, Long> STONE_TRY = new HashMap<>();

    static int autoStones(ServerLevel level, String titan, List<ServerPlayer> players, long now) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return 0;
        }
        int pressed = 0;
        for (ServerPlayer p : players) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            UUID group = Groups.keyOf(p);
            for (String key : TrialRegions.keys(titan)) {
                BlockPos stone = TrialRegions.stoneOf(titan, key);
                if (stone == null || Math.abs(p.getX() - (stone.getX() + 0.5)) > STONE_REACH || Math.abs(p.getZ() - (stone.getZ() + 0.5)) > STONE_REACH
                        || Math.abs(p.getY() - stone.getY()) > 3) {
                    continue;
                }
                if (TrialSteles.rung(level.getServer(), group, TrialSteles.signal(TrialRegions.signalTitan(titan), key))) {
                    continue;
                }
                TrialCell cell = TrialRegions.cellOfKey(titan, key, TrialData.current(level, group));
                if (cell != null && cell.runtime()) {
                    continue;
                }
                String tk = p.getUUID() + "|" + titan + "|" + key;
                Long last = STONE_TRY.get(tk);
                if (last != null && now - last < STONE_RETRY) {
                    continue;
                }
                STONE_TRY.put(tk, now);
                arrive(level, titan, p, stone);
                pressed++;
            }
        }
        return pressed;
    }

    // ゴールに着いた (巨神ごとの決まりに渡す。足りない条件は一行で言う)
    static void arrive(ServerLevel level, String titan, ServerPlayer p, BlockPos goal) {
        switch (titan) {
            case Titans.STONE_WHALE -> jp.revati.skyabyss.titan.dungeon.WhaleGoals.arrive(level, p, goal);
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackTrials.useStone(level, goal, p, net.minecraft.world.item.ItemStack.EMPTY);
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackTrials.useStone(level, goal, p);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackTrials.useStone(level, goal, p);
            default -> {
            }
        }
    }

    public static void clearAll() {
        STONE_TRY.clear();
    }
}
