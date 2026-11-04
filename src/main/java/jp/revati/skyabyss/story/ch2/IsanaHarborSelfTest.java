package jp.revati.skyabyss.story.ch2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// 二章のイサナの居場所 (IsanaHarbor): 岩鯨のあと噂を聞くまでは鯨見、噂のあと港、潮殻を止めて港で話したら戻る。見える鯨追いは一人だけ。
// SKYABYSS_SELFTEST_ONLY="isana harbor"
public final class IsanaHarborSelfTest {
    private IsanaHarborSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "isana_t"));
        ProgressData pd = ProgressData.get(server);
        Set<String> flags = NpcBonds.of(p).bond(IsanaHarbor.ROLE).flags();
        try {
            check(f, !IsanaHarbor.atHarbor(p), "before the whale's heart, Isana stays in the whale town");
            pd.markPersonal(p.getUUID(), "stone_whale");
            check(f, !IsanaHarbor.atHarbor(p), "chapter 2 before the tide quest: still in the whale town");
            Quests.start(p, "main_tide");
            check(f, !IsanaHarbor.atHarbor(p), "during the rumor stage she is still in the whale town");
            flags.add(IsanaHarbor.FLAG_TOLD);
            check(f, IsanaHarbor.atHarbor(p), "after she says she will go ahead, she is at the harbor");
            flags.remove(IsanaHarbor.FLAG_TOLD);
            Quests.setStage(p, "main_tide", "prepare");
            check(f, IsanaHarbor.atHarbor(p), "past the rumor stage (or after skipping it) she is at the harbor");
            pd.markPersonal(p.getUUID(), "tide_shell");
            check(f, IsanaHarbor.atHarbor(p), "after the tide shell's heart she waits at the harbor for the talk");
            // 見える鯨追いは一人だけ (港のイサナと鯨見のイサナ)。ほかの NPC はいつも見える
            NpcEntity harbor = NpcSetup.WHALE_CHASER.get().create(level);
            NpcEntity town = NpcSetup.WHALE_CHASER.get().create(level);
            NpcEntity teller = NpcSetup.STORYTELLER.get().create(level);
            if (harbor == null || town == null || teller == null) {
                f.add("could not create the NPCs");
            } else {
                harbor.addTag(IsanaHarbor.TAG);
                check(f, IsanaHarbor.visibleTo(harbor, p) && !IsanaHarbor.visibleTo(town, p), "at the harbor stage only the harbor Isana is shown");
                flags.add(IsanaHarbor.FLAG_DONE);
                check(f, !IsanaHarbor.atHarbor(p), "after the harbor talk she goes back to the whale town");
                check(f, !IsanaHarbor.visibleTo(harbor, p) && IsanaHarbor.visibleTo(town, p), "back in the whale town only the town Isana is shown");
                check(f, IsanaHarbor.visibleTo(teller, p), "the storyteller is always shown");
                harbor.discard();
                town.discard();
                teller.discard();
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST isana harbor crashed", e);
            f.add("crashed: " + e);
        } finally {
            pd.clearPersonal(p.getUUID(), "stone_whale");
            pd.clearPersonal(p.getUUID(), "tide_shell");
            Quests.forget(p, "main_tide");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST isana harbor PASSED (whale town until the rumor, harbor after it, back after the harbor talk, one Isana shown)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST isana harbor FAILED: {}", f);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
