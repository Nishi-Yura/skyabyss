package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestSetup;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.back.TideBackState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 「パーティの石」の自己テスト (TrialSteles / docs/design/10 の 10.9): 誰かが石を鳴らせば、パーティの全員 (離れた人・あとから入った人) の
// クエストの石の合図が数えられる。一人のグループは影響を受けない。個人の報酬 (経験値) は記録からは増えない
public final class TrialStelesSelfTest {
    private TrialStelesSelfTest() {
    }

    private static final String QUEST = "main_thunder";
    private static final int STAGE = 3; // trials (read, stone)

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        List<UUID> groups = new ArrayList<>();
        try {
            ServerLevel storm = server.getLevel(StormTravel.STORM_BACK);
            ServerLevel tide = server.getLevel(TideTravel.TIDE_BACK);
            ServerLevel ember = server.getLevel(EmberIds.BACK);
            ServerLevel whale = server.getLevel(TitanTravel.TITAN_BACK);
            if (storm == null || tide == null || ember == null || whale == null) {
                f.add("a titan back dimension is missing");
            } else {
                parties.clearAll();
                FakePlayer a = player(storm, "steles_a");
                FakePlayer b = player(storm, "steles_b");
                FakePlayer c = player(storm, "steles_c");
                FakePlayer solo = player(storm, "steles_solo");
                FakePlayer late = player(storm, "steles_late");
                PartyData.Party party = parties.create(a.getUUID(), "steles_a");
                parties.add(party, b.getUUID(), "steles_b");
                parties.add(party, c.getUUID(), "steles_c");
                UUID group = party.id;
                groups.add(group);
                groups.add(solo.getUUID());
                groups.add(late.getUUID());
                for (FakePlayer p : List.of(a, b, c, solo, late)) {
                    questAt(p);
                }
                int xpB = Growth.get(b).xp;
                int xpLate = Growth.get(late).xp;

                // 何も鳴っていない間は、誰も数えない
                pollAll(List.of(a, b, c, solo, late));
                check(f, stone(a) == 0 && stone(b) == 0 && stone(solo) == 0, "a stone was counted before any rang");

                // A が鳴らす (A の石の近くにいる)。B・C は遠く (別の場所) で、まだ触れていない
                StormProgress.of(storm, group).setRung(StormBackState.Trial.WEST, true);
                Quests.signal(a, "storm_trial_west"); // 石の近くの人は、鳴った瞬間に合図を受ける (TrialSteles はその後に合わせる)
                pollAll(List.of(a, b, c, solo, late));
                check(f, stone(a) == 1, "the one who rang did not count (" + stone(a) + ")");
                check(f, stone(b) == 1 && stone(c) == 1, "party members elsewhere did not count (b " + stone(b) + ", c " + stone(c) + ")");
                check(f, stone(solo) == 0, "a solo player counted another party's stone");
                check(f, stone(late) == 0, "a player outside the party counted the stone");
                check(f, TrialSteles.rung(server, group, "storm_trial_west") && !TrialSteles.rung(server, group, "storm_trial_east"),
                        "the group stone state is wrong");
                check(f, !TrialSteles.rung(server, solo.getUUID(), "storm_trial_west"), "a solo group saw the party's stone");

                // 記録から合わせても、何度合わせても数は増えない・経験値は動かない (二重に受け取らない)
                for (int i = 0; i < 3; i++) {
                    pollAll(List.of(a, b, c));
                }
                check(f, stone(a) == 1 && stone(b) == 1, "repeated polls changed the count");
                check(f, Growth.get(b).xp == xpB, "the stone record paid out xp to a member (" + Growth.get(b).xp + " vs " + xpB + ")");

                // あとから入った人は、パーティの進みを引き継ぐ
                parties.add(party, late.getUUID(), "steles_late");
                pollAll(List.of(late));
                check(f, stone(late) == 1, "a late joiner did not inherit the party's stone");
                check(f, Growth.get(late).xp == xpLate, "a late joiner got xp from the record");

                // 抜けた人は、持っていた数を保つ (戻らない)。グループが変わっても
                parties.remove(c.getUUID());
                pollAll(List.of(c));
                check(f, stone(c) == 1, "a member who left lost what they had");
                // ほかの巨神の石も、同じ記録から引ける
                TideBackState.of(tide, group).ring(TideBackState.Trial.EAST);
                EmberBackState.of(ember, group).ring(EmberBackState.Trial.SOUTH);
                BackGroupData.get(whale).of(group).setFlag(TitanBackTrials.STONE_FLAG + 1, true);
                check(f, TrialSteles.rung(server, group, "tide_trial_east") && !TrialSteles.rung(server, group, "tide_trial_west"), "tide stone state wrong");
                check(f, TrialSteles.rung(server, group, "ember_trial_south") && !TrialSteles.rung(server, group, "ember_trial_west"), "ember stone state wrong");
                check(f, TrialSteles.rung(server, group, "whale_trial_1") && !TrialSteles.rung(server, group, "whale_trial_0"), "whale stone state wrong");
                check(f, !TrialSteles.rung(server, group, "nonsense_trial_west") && !TrialSteles.rung(server, group, "storm_trial_nowhere"),
                        "an unknown signal counted as a stone");
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST party steles crashed", e);
            f.add("crashed: " + e);
        } finally {
            parties.restore(snapshot, server.registryAccess());
            for (UUID g : groups) {
                for (var key : List.of(StormTravel.STORM_BACK, TideTravel.TIDE_BACK, EmberIds.BACK, TitanTravel.TITAN_BACK)) {
                    ServerLevel l = server.getLevel(key);
                    if (l != null) {
                        BackGroupData.get(l).remove(g);
                    }
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST party steles PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST party steles FAILED: {}", m));
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.setData(QuestSetup.LOG, new QuestLog());
        p.setPos(0, 100, 0);
        return p;
    }

    // 風の章の「試練」の段階まで進めた形で始める
    private static void questAt(ServerPlayer p) {
        Quests.start(p, QUEST);
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        e.stage = STAGE;
        e.progress = new int[2];
    }

    private static int stone(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        return e == null ? -1 : e.progress(1);
    }

    private static void pollAll(List<? extends ServerPlayer> players) {
        for (ServerPlayer p : players) {
            Quests.poll(p, false);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
