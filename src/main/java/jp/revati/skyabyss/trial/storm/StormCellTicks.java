package jp.revati.skyabyss.trial.storm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialRuntime;
import jp.revati.skyabyss.util.Players;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 試練の甲板のセルの「毎 tick」の仕事 (昇り風・横風)。TrialRuntime は 5 tick ごとなので、持ち上げのように毎 tick 要る動きはここから。
// 動かしているセル (TrialRuntime.runsOf) のうち PerTick を持つものへ、そのセルの中にいるグループの人を渡す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormCellTicks {
    private StormCellTicks() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != StormTravel.STORM_BACK || level.players().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Map<UUID, List<ServerPlayer>> byGroup = new HashMap<>();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isAlive() && !p.isSpectator()) {
                byGroup.computeIfAbsent(Groups.keyOf(p), k -> new ArrayList<>()).add(p);
            }
        }
        for (Map.Entry<UUID, List<ServerPlayer>> e : byGroup.entrySet()) {
            for (TrialCell.Run run : TrialRuntime.runsOf(e.getKey())) {
                if (!(run.cell instanceof StormCell.PerTick pt)) {
                    continue;
                }
                List<ServerPlayer> inside = new ArrayList<>();
                for (ServerPlayer p : e.getValue()) {
                    if (run.cell.ports().area().contains(p.position())) {
                        inside.add(p);
                    }
                }
                if (!inside.isEmpty()) {
                    pt.perTick(level, run, inside, now);
                }
            }
        }
    }
}
