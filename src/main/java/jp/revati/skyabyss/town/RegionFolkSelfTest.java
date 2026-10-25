package jp.revati.skyabyss.town;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 土地の町の人の頼み (RegionFolk の六人。docs/design/42 の 4) の検査。SKYABYSS_SELFTEST_ONLY="region folk"。
// 頼みごとに偽の人で: 依頼主が町の人・見どころの合図 (poi:<種類>) で進む・品を持てば進む・野盗を退ければ進む・渡すと終わって鐘銭が増える
public final class RegionFolkSelfTest {
    private RegionFolkSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("region folk", RegionFolkSelfTest::run));
    }

    // 町の人 → 頼み
    static final String[][] ERRANDS = {
            {"tide_ferryman", "town/tide_ferry"}, {"tide_lightkeeper", "town/tide_light"},
            {"storm_elder", "town/storm_rods"}, {"storm_hutkeeper", "town/storm_hut"},
            {"ember_survivor", "town/ember_forge"}, {"ember_watch", "town/ember_watch"}};

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        for (String[] e : ERRANDS) {
            String folk = e[0];
            String qid = e[1];
            QuestDef def = QuestManager.quest(qid);
            if (def == null) {
                f.add(qid + ": not loaded");
                continue;
            }
            if (!("skyabyss:guest_" + folk).equals(def.giver())) {
                f.add(qid + ": the giver is not " + folk + " (" + def.giver() + ")");
            }
            boolean known = false;
            for (RegionFolk.Folk rf : RegionFolk.ALL) {
                known |= rf.id().equals(folk);
            }
            if (!known) {
                f.add(qid + ": " + folk + " is not a region folk");
            }
            FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "folk_" + folk.substring(0, Math.min(10, folk.length()))));
            Quests.start(p, qid);
            for (int guard = 0; guard < 8; guard++) {
                String stage = TownGuests.stageOf(p, qid);
                if (stage == null) {
                    break;
                }
                QuestLog.Entry entry = QuestLog.of(p).entry(qid);
                QuestDef.Stage st = def.stages().get(entry.stage);
                if (stage.equals("back")) {
                    int coins = Coins.count(p);
                    Quests.signal(p, "town_hand:guest_" + folk);
                    QuestLog.Entry done = QuestLog.of(p).entry(qid);
                    if (done == null || !done.done) {
                        f.add(qid + ": handing over did not finish the errand");
                    } else if (Coins.count(p) <= coins) {
                        f.add(qid + ": no bell coins for the errand");
                    }
                    break;
                }
                // 合図の前は進まない
                Quests.poll(p, true);
                if (!stage.equals(TownGuests.stageOf(p, qid))) {
                    f.add(qid + ": stage " + stage + " finished by itself");
                    break;
                }
                for (QuestDef.Objective ob : st.objectives()) {
                    switch (ob.type()) {
                        case "signal" -> {
                            if (!ob.targets().get(0).startsWith("poi:")) {
                                f.add(qid + ": stage " + stage + " waits for a signal that is not a landmark (" + ob.targets() + ")");
                            }
                            Quests.signal(p, ob.targets().get(0));
                        }
                        case "collect" -> {
                            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ob.targets().get(0)));
                            p.getInventory().add(new ItemStack(item, ob.count()));
                            Quests.poll(p, true);
                        }
                        case "kill" -> {
                            for (int i = 0; i < ob.count(); i++) {
                                Quests.onKill(p, ob.targets().get(0), true, 0);
                            }
                        }
                        default -> f.add(qid + ": unexpected objective " + ob.type());
                    }
                }
                if (stage.equals(TownGuests.stageOf(p, qid))) {
                    f.add(qid + ": stage " + stage + " did not advance");
                    break;
                }
            }
            p.discard();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST region folk PASSED ({} errands: giver, landmark signals, items, bandits, hand-over and bell coins)", ERRANDS.length);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST region folk FAILED: {}", s));
        }
    }
}
