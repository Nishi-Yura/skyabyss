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
            check(f, WorldEvents.levelHealth(0) == 1.0 && WorldEvents.levelHealth(38) > 5 && WorldEvents.levelDamage(38) > WorldEvents.levelDamage(0),
                    "the great foe does not grow with the visitors' level");
            // 取り残された大いなるもの (印付きで、いまの大事件のものでない) は世界に入れない
            var strayType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                    net.minecraft.resources.ResourceLocation.parse(NamedFoes.def(ContinentMap.WHALE).entity()));
            var stray = strayType.create(level);
            if (stray != null) {
                stray.moveTo(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
                stray.addTag(WorldEvents.TAG);
                boolean added = level.addFreshEntity(stray);
                check(f, !added, "a stray great foe joined the world");
                if (added) {
                    stray.discard();
                }
            }

            // 前触れ: 先触れ (名前つき) と群れが出て、去るときに消える
            WorldEvents.Event h = WorldEvents.begin(level, ContinentMap.WHALE);
            p.setPos(h.spot.getX() + 0.5, h.spot.getY() + 1, h.spot.getZ() + 0.5);
            int heralds = WorldEventFight.herald(level, h, p);
            check(f, heralds >= 3 && WorldEventFight.heraldLeft(level, h) == heralds, "the herald pack did not come: " + heralds);
            WorldEvents.leave(level);
            check(f, WorldEventFight.heraldLeft(level, h) == 0, "the herald pack stayed after the event left");
            p.setPos(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);

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
                // 封じ: 体力が 2/3 を切ると守りを張り、輪を三つ満たすと割れて膝をつく
                boss.setHealth(boss.getMaxHealth() * 0.6f);
                long t = level.getGameTime();
                WorldEventFight.step(level, e, boss, t);
                check(f, WorldEventFight.sealed(e) && e.seals.size() == 3, "the great foe did not raise its ward under 2/3");
                float before = boss.getHealth();
                boss.hurt(level.damageSources().playerAttack(p), 40);
                check(f, before - boss.getHealth() < 40 * WorldEventFight.SEALED * 1.5f + 0.5f, "the ward did not soften hits: " + (before - boss.getHealth()));
                for (WorldEventFight.Seal s : new ArrayList<>(e.seals)) {
                    p.setPos(s.pos().x, s.pos().y, s.pos().z);
                    for (int i = 0; i < 4 && !s.full(); i++) {
                        WorldEventFight.step(level, e, boss, t);
                    }
                    check(f, s.full(), "standing in a seal ring did not fill it");
                }
                WorldEventFight.step(level, e, boss, t);
                check(f, !WorldEventFight.sealed(e) && e.sealsDone == 1 && jp.revati.skyabyss.combat.BreakGauge.broken(boss),
                        "filling the seal rings did not break the ward and bring the great foe to its knees");
                p.setPos(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
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
