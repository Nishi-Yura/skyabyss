package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.Rebirths;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentPois;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 土地の名声と旅帳 (RegionFame) の自己テスト。SKYABYSS_SELFTEST_ONLY="fame"
//   段の境 / 段が上がると贈り物 / 値引きは段とともに増える / 旅帳 (土地の名所をすべて見つけると褒美と称号) / 名声 5 段の称号
public final class RegionFameSelfTest {
    private RegionFameSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000fa301"), "fame_test"));
        try {
            clear(p);
            check(f, RegionFame.tier(0) == 0 && RegionFame.tier(9) == 0 && RegionFame.tier(10) == 1 && RegionFame.tier(249) == 4 && RegionFame.tier(250) == 5,
                    "fame steps are wrong");
            for (int t = 1; t < RegionFame.DISCOUNT.length; t++) {
                check(f, RegionFame.DISCOUNT[t] >= RegionFame.DISCOUNT[t - 1], "the discount shrinks at fame " + t);
            }
            int before = Coins.count(p);
            RegionPoints.add(p, ContinentMap.WHALE, 30);
            check(f, RegionFame.tier(p, ContinentMap.WHALE) == 2, "30 points did not make fame 2");
            check(f, Coins.count(p) - before == 100 + 200, "fame gifts are wrong: " + (Coins.count(p) - before));

            // 旅帳: 鯨見の土地の名所をすべて見つける
            RegionFame.Journal j0 = RegionFame.journal(p, ContinentMap.WHALE);
            check(f, j0.total() >= 5 && j0.found() == 0, "the whale journal is empty or already filled: " + j0);
            var all = ContinentPois.all();
            ContinentMap map = ContinentMap.get();
            int fame0 = RegionPoints.fame(p, ContinentMap.WHALE);
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).site() && map.region(all.get(i).x(), all.get(i).z()) == ContinentMap.WHALE) {
                    ContinentPois.discoverForTest(p, i);
                }
            }
            RegionFame.Journal j1 = RegionFame.journal(p, ContinentMap.WHALE);
            check(f, j1.done() && RegionFame.journalDone(p, ContinentMap.WHALE), "finding every whale sight did not fill the journal");
            check(f, RegionPoints.fame(p, ContinentMap.WHALE) - fame0 == j1.total() + RegionFame.JOURNAL_FAME, "journal fame is wrong");
            long mask = RebirthTitles.unlockedMask(Rebirths.facts(p));
            check(f, RebirthTitles.has(mask, "walker_whale") && !RebirthTitles.has(mask, "walker_tide"), "the journal title is wrong");

            RegionPoints.add(p, ContinentMap.WHALE, 300);
            mask = RebirthTitles.unlockedMask(Rebirths.facts(p));
            check(f, RebirthTitles.has(mask, "hero_whale") && !RebirthTitles.has(mask, "hero_all"), "the fame 5 title is wrong");
            for (int r = ContinentMap.TIDE; r <= ContinentMap.EMBER; r++) {
                RegionPoints.add(p, r, 300);
            }
            check(f, RebirthTitles.has(RebirthTitles.unlockedMask(Rebirths.facts(p)), "hero_all"), "fame 5 everywhere did not give the continent title");
        } catch (RuntimeException ex) {
            f.add("fame test crashed: " + ex);
            SkyAbyss.LOGGER.error("fame test", ex);
        } finally {
            clear(p);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST fame PASSED (steps, gifts, discounts, journal fill and reward, journal and hero titles)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST fame FAILED: {}", m));
        }
    }

    static void clear(FakePlayer p) {
        p.getPersistentData().remove(RegionPoints.FAME);
        p.getPersistentData().remove(RegionPoints.WEEK);
        p.getPersistentData().remove(RegionFame.JOURNAL_DONE);
        ContinentPois.forgetForTest(p);
    }
}
