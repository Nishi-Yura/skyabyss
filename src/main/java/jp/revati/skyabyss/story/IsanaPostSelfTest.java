package jp.revati.skyabyss.story;

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

// イサナの章ごとの居場所 (IsanaPost): 岩鯨のあと噂を聞くまでは鯨見、噂のあと港、潮殻を止めて港で話したら風待の火のそば、
// 雷翼を止めて「俺は残る」と話したら鯨見。見える鯨追いは一人だけ。SKYABYSS_SELFTEST_ONLY="isana post"
public final class IsanaPostSelfTest {
    private IsanaPostSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "isana_t"));
        ProgressData pd = ProgressData.get(server);
        Set<String> flags = NpcBonds.of(p).bond(IsanaPost.ROLE).flags();
        NpcEntity harbor = NpcSetup.WHALE_CHASER.get().create(level);
        NpcEntity hut = NpcSetup.WHALE_CHASER.get().create(level);
        NpcEntity town = NpcSetup.WHALE_CHASER.get().create(level);
        NpcEntity teller = NpcSetup.STORYTELLER.get().create(level);
        try {
            if (harbor == null || hut == null || town == null || teller == null) {
                f.add("could not create the NPCs");
                return;
            }
            harbor.addTag(IsanaPost.Post.HARBOR.tag);
            hut.addTag(IsanaPost.Post.HUT.tag);
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "before the whale's heart, Isana stays in the whale town");
            pd.markPersonal(p.getUUID(), "stone_whale");
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "chapter 2 before the tide quest: still in the whale town");
            Quests.start(p, "main_tide");
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "during the rumor stage she is still in the whale town");
            flags.add(IsanaPost.FLAG_TOLD);
            check(f, IsanaPost.post(p) == IsanaPost.Post.HARBOR, "after she says she will go ahead, she is at the harbor");
            flags.remove(IsanaPost.FLAG_TOLD);
            Quests.setStage(p, "main_tide", "prepare");
            check(f, IsanaPost.post(p) == IsanaPost.Post.HARBOR, "past the rumor stage (or after skipping it) she is at the harbor");
            check(f, IsanaPost.visibleTo(harbor, p) && !IsanaPost.visibleTo(hut, p) && !IsanaPost.visibleTo(town, p), "at the harbor stage only the harbor Isana is shown");
            pd.markPersonal(p.getUUID(), "tide_shell");
            check(f, IsanaPost.post(p) == IsanaPost.Post.HARBOR, "after the tide shell's heart she waits at the harbor for the talk");
            flags.add(IsanaPost.FLAG_DONE);
            check(f, IsanaPost.post(p) == IsanaPost.Post.HUT, "after the harbor talk she waits by the fire in Kazamachi");
            check(f, !IsanaPost.visibleTo(harbor, p) && IsanaPost.visibleTo(hut, p) && !IsanaPost.visibleTo(town, p), "in chapter 3 only the Isana by the fire is shown");
            pd.markPersonal(p.getUUID(), "thunder_wing");
            check(f, IsanaPost.post(p) == IsanaPost.Post.HUT, "after the thunder wing's heart she stays by the fire until she tells she stays");
            flags.add(IsanaPost.FLAG_STAY);
            check(f, IsanaPost.post(p) == IsanaPost.Post.TOWN, "after 'I stay' she goes back to the whale town");
            check(f, !IsanaPost.visibleTo(harbor, p) && !IsanaPost.visibleTo(hut, p) && IsanaPost.visibleTo(town, p), "back in the whale town only the town Isana is shown");
            check(f, IsanaPost.visibleTo(teller, p), "the storyteller is always shown");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST isana post crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (String t : new String[] {"stone_whale", "tide_shell", "thunder_wing"}) {
                pd.clearPersonal(p.getUUID(), t);
            }
            Quests.forget(p, "main_tide");
            for (NpcEntity n : new NpcEntity[] {harbor, hut, town, teller}) {
                if (n != null) {
                    n.discard();
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST isana post PASSED (whale town until the rumor, harbor in chapter 2, the Kazamachi fire in chapter 3, back after 'I stay', one Isana shown)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST isana post FAILED: {}", f);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
