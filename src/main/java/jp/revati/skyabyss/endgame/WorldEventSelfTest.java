package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.NamedFoes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 大陸の大事件 (WorldEvents) の自己テスト。SKYABYSS_SELFTEST_ONLY="world event"
//   土地の中に来る / 大いなるもの (体力の倍率・名前・大きさ) / 与えた分の少ない人には報いがない / 報い (鐘銭・土地の点) / 時間切れで去る
public final class WorldEventSelfTest {
    private WorldEventSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000e7e01"), "event_test"));
        FakePlayer q = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000e7e02"), "event_test_b"));
        Mob boss = null;
        try {
            WorldEvents.reset(level);
            BlockPos spawn = level.getSharedSpawnPos();
            p.setPos(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
            for (int region = ContinentMap.WHALE; region <= ContinentMap.EMBER; region++) {
                WorldEvents.Event e = WorldEvents.begin(level, region);
                check(f, WorldEvents.current() == e && ContinentMap.get().region(e.spot.getX(), e.spot.getZ()) == region,
                        "a world event did not land in region " + region + ": " + e.spot);
            }
            WorldEvents.leave(level);
            check(f, WorldEvents.current() == null && WorldEvents.nextAt() > level.getGameTime(), "a world event did not leave and schedule the next");

            WorldEvents.Event e = WorldEvents.begin(level, ContinentMap.WHALE);
            boss = WorldEvents.spawnBoss(level, e, p);
            check(f, boss != null, "the great foe did not appear");
            if (boss != null) {
                check(f, WorldEvents.effectiveHealth(e, boss) >= e.baseHealth * WorldEvents.health(1) * 0.99,
                        "the great foe is not tough enough: " + WorldEvents.effectiveHealth(e, boss) + " / " + e.baseHealth);
                check(f, boss.getTags().contains(WorldEvents.TAG) && !NamedFoes.named(boss) && boss.hasCustomName(), "the great foe is not marked and named");
                check(f, boss.getAttribute(Attributes.SCALE).getValue() > 1.5, "the great foe is not bigger");
                WorldEvents.TEST.put(p.getUUID(), p);
                WorldEvents.TEST.put(q.getUUID(), q);
                WorldEvents.dealt(p.getUUID(), 500);
                WorldEvents.dealt(q.getUUID(), 5);
                p.getPersistentData().remove(RegionPoints.FAME);
                p.getPersistentData().remove(RegionPoints.WEEK);
                int beforeP = Coins.count(p);
                int beforeQ = Coins.count(q);
                boss.kill();
                check(f, WorldEvents.current() == null, "defeating the great foe did not end the event");
                int coins = NamedFoes.def(ContinentMap.WHALE).coins() * 4;
                check(f, Coins.count(p) - beforeP == coins, "world event coins are wrong: " + (Coins.count(p) - beforeP));
                check(f, Coins.count(q) == beforeQ, "someone with a tiny share was paid");
                check(f, RegionPoints.fame(p, ContinentMap.WHALE) == 4, "the world event did not add region points");
            }
        } catch (RuntimeException ex) {
            f.add("world event test crashed: " + ex);
            SkyAbyss.LOGGER.error("world event test", ex);
        } finally {
            if (boss != null && boss.isAlive()) {
                boss.discard();
            }
            WorldEvents.reset(level);
            p.getPersistentData().remove(RegionPoints.FAME);
            p.getPersistentData().remove(RegionPoints.WEEK);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST world event PASSED (lands in each region, leaves on time, great foe toughness/name/size, shares, coins, region points)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST world event FAILED: {}", m));
        }
    }
}
