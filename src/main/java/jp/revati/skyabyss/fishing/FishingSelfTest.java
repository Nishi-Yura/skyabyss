package jp.revati.skyabyss.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.util.FakePlayer;

// 釣り (Fish・Fishing) の自己テスト。SKYABYSS_SELFTEST_ONLY="fishing"
//   表 (31 種・土地ごと 6 種・ごくまれ 1 種) / 品・絵・形 / 土地ごとの釣れ方の割合 / 大きさ / 図鑑と報い / 週の大会 / 店 (竿・買い取り)
public final class FishingSelfTest {
    private FishingSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "fish_test"));
        FakePlayer q = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "fish_test_b"));
        try {
            // 表
            check(f, Fish.ALL.size() == 31 && Fish.ALL.stream().map(Fish.Species::id).distinct().count() == 31, "the fish table is not 31 distinct fish");
            int[] lands = {ContinentMap.MEADOW, ContinentMap.WHALE, ContinentMap.TIDE, ContinentMap.STORM, ContinentMap.EMBER};
            for (int land : lands) {
                check(f, Fish.ALL.stream().filter(s -> s.region() == land).count() == 6, "land " + land + " does not have six fish");
            }
            check(f, Fish.ALL.stream().filter(s -> s.region() == Fish.ANY && s.rarity() == 'L').count() == 1, "no single legendary fish");
            for (Fish.Species s : Fish.ALL) {
                check(f, s.minMm() > 0 && s.maxMm() > s.minMm(), "bad size range " + s.id());
                check(f, FishingSetup.FISH.containsKey(s.id()), "no item for " + s.id());
                check(f, FishingSelfTest.class.getResource("/assets/skyabyss/textures/item/fish_" + s.id() + ".png") != null, "no icon for " + s.id());
                check(f, FishingSelfTest.class.getResource("/assets/skyabyss/models/item/fish_" + s.id() + ".json") != null, "no model for " + s.id());
            }
            check(f, FishingSelfTest.class.getResource("/assets/skyabyss/models/item/fishing_rod_cast.json") != null, "no cast model for the rod");
            // 釣れ方: その土地の魚かごくまれな一匹だけ、ごくまれは 1％ ほど、まれは土地に R があれば 9％ ほど
            RandomSource rnd = RandomSource.create(42);
            for (int land : lands) {
                Map<Character, Integer> by = new HashMap<>();
                int n = 20000;
                for (int i = 0; i < n; i++) {
                    Fish.Species s = Fish.roll(land, rnd);
                    check(f, s.region() == land || s.region() == Fish.ANY, "land " + land + " gave " + s.id());
                    by.merge(s.rarity(), 1, Integer::sum);
                }
                double legend = by.getOrDefault('L', 0) / (double) n;
                check(f, legend > 0.005 && legend < 0.016, "legendary rate in land " + land + ": " + legend);
                double rare = by.getOrDefault('R', 0) / (double) n;
                boolean hasRare = !Fish.of(land, 'R').isEmpty();
                check(f, hasRare ? rare > 0.07 && rare < 0.11 : rare == 0, "rare rate in land " + land + ": " + rare);
                if (f.size() > 20) {
                    break;
                }
            }
            check(f, Fish.roll(ContinentMap.OUTSKIRTS, rnd).region() != ContinentMap.OUTSKIRTS, "the start island has its own fish");
            // 大きさ: 幅の中、大きいものほど出にくい
            Fish.Species carp = Fish.byId("bell_carp");
            int small = 0;
            for (int i = 0; i < 4000; i++) {
                int mm = Fish.size(carp, rnd);
                check(f, mm >= carp.minMm() && mm <= carp.maxMm(), "a fish size left its range: " + mm);
                if (mm < (carp.minMm() + carp.maxMm()) / 2) {
                    small++;
                }
            }
            check(f, small > 2400, "big fish are not rarer: " + small + "/4000 under the middle");
            // 図鑑: 初めて・大きい一匹だけ覚える・そろうと一度だけ報い
            p.getPersistentData().remove(Fishing.BEST);
            p.getPersistentData().remove(Fishing.DONE);
            Fishing.caught(p, carp, 500);
            Fishing.caught(p, carp, 400);
            check(f, Fishing.codexCount(p) == 1 && Fishing.best(p, "bell_carp") == 500, "the codex did not keep the biggest");
            Fishing.caught(p, carp, 650);
            check(f, Fishing.best(p, "bell_carp") == 650, "a bigger fish did not replace the record");
            int coins0 = Coins.count(p);
            for (Fish.Species s : Fish.ALL) {
                Fishing.caught(p, s, s.minMm());
            }
            check(f, Fishing.codexCount(p) == 31 && Coins.count(p) - coins0 == Fishing.CODEX_COINS && Fishing.marks(p).contains("fish:all"),
                    "completing the codex did not pay once: " + (Coins.count(p) - coins0));
            int coins1 = Coins.count(p);
            Fishing.caught(p, carp, 700);
            check(f, Coins.count(p) == coins1, "the codex paid twice");
            // 週の大会: 点の高い順、週が替わると賞、1 位は称号の印
            Fishing.Week w = Fishing.Week.get(server);
            w.week = "test-a";
            w.entries.clear();
            w.prizes.clear();
            w.enter(p, carp, 400);
            w.enter(q, carp, 780);
            w.enter(p, carp, 200);
            check(f, w.top(2).size() == 2 && w.top(2).get(0).player().equals(q.getUUID()) && w.of(p.getUUID()).size() == 400, "the contest order or best is wrong");
            w.week = "test-b";
            w.roll();
            check(f, w.prizes.get(q.getUUID()) == 1 && w.prizes.get(p.getUUID()) == 2 && w.entries.isEmpty(), "the contest did not settle at the week change");
            q.getPersistentData().remove(Fishing.CHAMPION);
            int qc = Coins.count(q);
            w.pay(q);
            check(f, Coins.count(q) - qc == Fishing.PRIZE[0] && Fishing.marks(q).contains("fish:champion"), "the winner was not paid");
            check(f, !w.pay(q), "a prize was paid twice");
            w.prizes.clear();
            w.last.clear();
            w.week = "";
            w.setDirty();
            // 店: 道具屋に竿、食べ物屋が全部の魚を買い取る
            var tool = jp.revati.skyabyss.facility.ShopManager.shop("tool_shop");
            var food = jp.revati.skyabyss.facility.ShopManager.shop("food_shop");
            check(f, tool != null && tool.goods().stream().anyMatch(g -> g.item() == FishingSetup.ROD.get()), "the tool shop does not sell the rod");
            for (Fish.Species s : Fish.ALL) {
                check(f, food != null && food.buys().stream().anyMatch(b -> b.item() == FishingSetup.FISH.get(s.id()).get() && b.coins() == s.sell() && b.per() == 1),
                        "the food vendor does not buy " + s.id());
            }
        } catch (RuntimeException e) {
            f.add("fishing test crashed: " + e);
            SkyAbyss.LOGGER.error("fishing test", e);
        } finally {
            for (FakePlayer x : List.of(p, q)) {
                x.getPersistentData().remove(Fishing.BEST);
                x.getPersistentData().remove(Fishing.DONE);
                x.getPersistentData().remove(Fishing.CHAMPION);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST fishing PASSED (31 fish, six per land, icons and models, catch rates per land, sizes, codex and reward once, weekly contest, rod for sale and fish bought)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST fishing FAILED: {}", m));
        }
    }
}
