package jp.revati.skyabyss.trial;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

// 順路の中ボスの段 (docs/design/32 の 2.1 の 3.5)。四体とも、三つ目の部屋のあと区画の中ボスの間 (trial/raid/mid) で戦う:
//   岩鯨 = 番兵長、潮殻 = 潮招き、雷翼 = 雷獣、余燼 = 灰の鍛え手。倒すと心臓の間へ (TrialRoute が defeated を見張る)
public final class TrialMidBoss {
    private TrialMidBoss() {
    }

    // その周に中ボスの段が要るか (中ボスの間のある巨神)
    public static boolean needed(String titan, TrialDraw.Draw draw) {
        return jp.revati.skyabyss.trial.raid.mid.MidRooms.idOf(titan) != null;
    }

    public static Component name(String titan) {
        return Component.translatable("trial.skyabyss.midboss." + TrialRegions.signalTitan(titan));
    }

    // そのグループが中ボスを倒したか (越えた瞬間に RaidRun が TrialRoute.onMidBossDefeated を呼ぶ。これは念のための見張り)
    public static boolean defeated(ServerLevel back, UUID group, String titan) {
        return jp.revati.skyabyss.trial.raid.RaidRun.midCleared(group);
    }
}
