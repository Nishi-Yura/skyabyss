package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 都の町めぐり (TownTour) の自己テスト。段の名前は "town tour"。都が建っている世界で:
// 都に入ると 1 (鍛冶場へ)、戸に着くたびに次へ進み鐘銭が増える、宿ではパン、四つで済み。済んだ人はもう動かない
public final class TownTourSelfTest {
    private TownTourSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("town tour", TownTourSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        Map<String, BlockPos> doors = TownGuide.doors(server);
        BlockPos center = TownGuide.center(server);
        if (doors.isEmpty() || center == null) {
            SkyAbyss.LOGGER.info("SELFTEST town tour PASSED (no capital in this world; skipped)");
            return;
        }
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0c01"), "tour_a"));
        p.getPersistentData().remove(TownTour.TAG);
        p.getInventory().clearContent();
        Coins.take(p, Coins.count(p));
        p.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        TownTour.tick(p);
        if (TownTour.step(p) != 1) {
            f.add("entering the capital should start the tour at 1, got " + TownTour.step(p));
        }
        int coins = 0;
        for (int i = 0; i < TownTour.STOPS.size(); i++) {
            BlockPos d = doors.get(TownTour.STOPS.get(i));
            if (d == null) {
                f.add("no door for " + TownTour.STOPS.get(i));
                break;
            }
            // 人が実際に立ち止まる所 = 案内の灯りの道の終わり (戸の印は壁の高い看板なので、そこへは立てない)
            TownGuide.Place place = TownGuide.place(TownTour.STOPS.get(i));
            List<BlockPos> nodes = TownGuide.plan(new jp.revati.skyabyss.guide.nav.LevelTerrain(server.overworld(), true), center, place, d).nodes();
            BlockPos end = nodes.isEmpty() ? d : nodes.get(nodes.size() - 1);
            p.moveTo(end.getX() + 0.5, end.getY(), end.getZ() + 0.5);
            TownTour.tick(p);
            coins += TownTour.COINS[i];
            if (TownTour.step(p) != i + 2 || Coins.count(p) != coins) {
                f.add("stop " + TownTour.STOPS.get(i) + ": step " + TownTour.step(p) + " coins " + Coins.count(p) + " (want " + coins + ")");
            }
        }
        if (p.getInventory().countItem(Items.BREAD) + jp.revati.skyabyss.pouch.Pouch.count(p, Items.BREAD) != 3) {
            f.add("the inn did not give 3 bread");
        }
        if (!jp.revati.skyabyss.mount.MountService.hasWhistle(p)) {
            f.add("the stable did not give the mount whistle");
        }
        TownTour.tick(p);
        if (TownTour.step(p) != TownTour.DONE || Coins.count(p) != coins) {
            f.add("a finished tour moved again");
        }
        p.getPersistentData().remove(TownTour.TAG);
        p.getInventory().clearContent();
        Coins.take(p, Coins.count(p));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST town tour PASSED (starts in the capital, five stops reached where the guide path ends, coins, bread at the inn, the whistle at the stable, done stays done)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST town tour FAILED: {}", s);
            }
        }
    }
}
