package jp.revati.skyabyss.trial;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

// 順路の中ボスの段 (docs/design/32 の 2.1 の 3.5、決定 201)。いまは四体とも、三つ目の部屋のあと区画の中ボスの間 (trial/raid/mid) で戦う。
// 以下は背の中の元の戦い (原型の試練の中) の決まり:
//   岩鯨 = 番兵長 (W09 東の翼の番兵長の広間)、潮殻 = 潮招き (T09 群れの干潟)、雷翼 = 雷獣 (S05 東の雷の骨の回廊の巣)、余燼 = 灰の鍛え手 (E05 鍛えの間の奥)。
// その原型を引いた周は、試練の中で中ボスと戦う (段 3.5 は要らない)。引いていない周は、闘のあと中ボスの前室へ運び、手前の仕掛けは済んだことにして
// (prime)、中ボスを倒すと心臓の前室へ (TrialRoute が defeated を見張る)
public final class TrialMidBoss {
    private TrialMidBoss() {
    }

    // 中ボスのいる原型の試練
    @Nullable
    public static String hostOf(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> "W09";
            case Titans.TIDE_SHELL -> "T09";
            case Titans.THUNDER_WING -> "S05";
            case Titans.EMBER -> "E05";
            default -> null;
        };
    }

    // 中ボスの前室の休み場
    @Nullable
    public static String restOf(String titan) {
        return switch (titan) {
            // 番兵の詰所の前 (詰所は済んだことにする)。その先が番兵長の広間の前室
            case Titans.STONE_WHALE -> "ckr";
            // 干潟の入口
            case Titans.TIDE_SHELL -> "f0";
            // 雷の骨の回廊の巣の手前
            case Titans.THUNDER_WING -> "e1";
            // 鍛え手の扉の前
            case Titans.EMBER -> "e0";
            default -> null;
        };
    }

    // その周に中ボスの段が要るか (中ボスのいる原型を引いていない)
    public static boolean needed(String titan, TrialDraw.Draw draw) {
        String host = hostOf(titan);
        return host != null && restOf(titan) != null && !draw.has(host);
    }

    public static Component name(String titan) {
        return Component.translatable("trial.skyabyss.midboss." + TrialRegions.signalTitan(titan));
    }

    // 前室へ運ぶ前に: 中ボスの手前の仕掛けを、そのグループには済んだことにする
    public static void prime(ServerLevel back, UUID group, String titan) {
        if (jp.revati.skyabyss.trial.raid.mid.MidRooms.idOf(titan) != null) {
            // 中ボスは区画の中ボスの間で戦う (trial/raid/mid)。背の中の手前の仕掛けは使わない
            return;
        }
        switch (titan) {
            case Titans.STONE_WHALE -> {
                // 番兵の詰所を制圧済みにして、番兵長の広間への割符の戸を開ける
                GroupProgress.of(back, group).markHallCleared("ck");
                GroupGates.open(back, group, jp.revati.skyabyss.titan.dungeon.TitanBackLayout.E3_LOCK);
            }
            case Titans.TIDE_SHELL -> {
                // 干潟の巣を壊したことにする (干潟へ入ると潮招きの集合が始まる)
                var state = jp.revati.skyabyss.titan.tide.back.TideBackState.of(back, group);
                for (int i = 0; i < state.nestSites(); i++) {
                    state.breakNest(i);
                }
            }
            case Titans.EMBER -> GroupGates.open(back, group,
                    jp.revati.skyabyss.titan.ember.back.EmberBackTrials.boxBlocks(jp.revati.skyabyss.titan.ember.back.EmberBackLayout.SMITH_DOOR));
            default -> {
            }
        }
        GroupOverlays.refresh(back, group);
    }

    // そのグループが中ボスを倒したか
    public static boolean defeated(ServerLevel back, UUID group, String titan) {
        if (jp.revati.skyabyss.trial.raid.mid.MidRooms.idOf(titan) != null) {
            // 区画の中ボスの間を越えた (越えた瞬間に RaidRun が TrialRoute.onMidBossDefeated を呼ぶ。これは念のための見張り)
            return jp.revati.skyabyss.trial.raid.RaidRun.midCleared(group);
        }
        return switch (titan) {
            case Titans.STONE_WHALE -> GroupProgress.of(back, group).isHallCleared("e3");
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackState.of(back, group).beckonerDefeated();
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.raiju.RaijuBattle.defeated(back, group);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.smith.AshSmithBattle.done(back, group);
            default -> true;
        };
    }
}
