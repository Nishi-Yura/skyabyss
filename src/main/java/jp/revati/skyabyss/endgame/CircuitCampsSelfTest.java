package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.world.continent.ContinentLayout;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 巡り路の野営地 (CircuitCamps): 四つの土地に一つずつ、町から離れた陸。景品の表は野営地と期で決まり、野営地ごと・期ごとに違う。
// 階級がそろい、神器は表に一つ、引きでは 0.1% ほど。引いた品は特性つきの品
public final class CircuitCampsSelfTest {
    private CircuitCampsSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<CircuitCamps.Camp> camps = CircuitCamps.all();
        check(f, camps.size() == 4, "expected 4 camps, got " + camps.size());
        ContinentMap map = ContinentMap.get();
        Set<Integer> regions = new HashSet<>();
        for (CircuitCamps.Camp c : camps) {
            regions.add(c.region());
            check(f, map.region(c.x(), c.z()) == c.region(), c.id() + ": the camp is not in its land");
            check(f, map.surface(c.x(), c.z()) > 64, c.id() + ": the camp is in the water");
            BlockPos town = ContinentLayout.get().town(c.id());
            check(f, town != null && Math.hypot(c.x() - town.getX(), c.z() - town.getZ()) > 100, c.id() + ": the camp is inside its town");
            check(f, CircuitCamps.byRegion(c.region()) == c, c.id() + ": region lookup is wrong");
        }
        check(f, regions.size() == 4, "two camps share a land");
        long period = CircuitCamps.period();
        List<Item> a0 = CircuitCamps.pool(camps.get(0), period);
        check(f, a0.equals(CircuitCamps.pool(camps.get(0), period)), "the reward list is not fixed within a period");
        check(f, !a0.equals(CircuitCamps.pool(camps.get(1), period)), "two camps share a reward list");
        boolean rotates = false;
        for (int k = 1; k <= 3 && !rotates; k++) {
            rotates = !a0.equals(CircuitCamps.pool(camps.get(0), period + k));
        }
        check(f, rotates, "the reward list does not change between periods");
        for (CircuitCamps.Camp c : camps) {
            List<Item> pool = CircuitCamps.pool(c, period);
            for (GearTier t : CircuitCamps.TIERS) {
                check(f, pool.stream().anyMatch(i -> CircuitCamps.tierOf(i) == t), c.id() + ": no " + t.key + " item in the reward list");
            }
            check(f, pool.stream().filter(i -> !CircuitCamps.supply(i) && CircuitCamps.tierOf(i) == GearTier.MYTHIC).count() == 1, c.id() + ": the list should hold one mythic");
            // アンコモンと伝説は入れない。旅の支え (薬・糧・鐘銭) が三つ
            check(f, pool.stream().noneMatch(i -> !CircuitCamps.supply(i) && (CircuitCamps.tierOf(i) == GearTier.UNCOMMON || CircuitCamps.tierOf(i) == GearTier.ARTIFACT)),
                    c.id() + ": the list still holds uncommon or legendary gear");
            check(f, pool.stream().filter(CircuitCamps::supply).count() == 3 && pool.stream().anyMatch(i -> i == jp.revati.skyabyss.facility.FacilitySetup.BELL_COIN.get()),
                    c.id() + ": the list should hold three supplies including bell coins: " + pool.stream().filter(CircuitCamps::supply).toList());
        }
        // 引き: 品は空でなく、神器は 0.1% ほど (2 万回で 60 回より少ない)
        RandomSource r = RandomSource.create(42);
        int artifacts = 0;
        int supplies = 0;
        int empty = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            ItemStack s = CircuitCamps.pull(a0, r);
            if (s.isEmpty()) {
                empty++;
            } else if (CircuitCamps.supply(s.getItem())) {
                supplies++;
                check(f, s.getCount() >= 2, "a supply pull came as a single item: " + s);
            } else if (CircuitCamps.tierOf(s.getItem()) == GearTier.MYTHIC) {
                artifacts++;
            }
        }
        check(f, empty == 0, empty + " empty pulls");
        check(f, artifacts > 0 && artifacts < 60, "mythic pulls " + artifacts + " of " + n + " (expected about 20)");
        check(f, supplies > n * 0.35 && supplies < n * 0.5, "supply pulls " + supplies + " of " + n + " (expected about 42%)");
        check(f, CircuitCamps.ticksUntilRotation() > 0 && CircuitCamps.ticksUntilRotation() <= CircuitCamps.PERIOD_DAYS * 24L * 3600L * 20L,
                "rotation countdown out of range: " + CircuitCamps.ticksUntilRotation());
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST circuit camps FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST circuit camps PASSED (4 camps, one per land outside its town; reward list per camp and per {}-day period with rare to heroic, one mythic and three supplies, no uncommon or legendary; mythic pulls {} / {})",
                    CircuitCamps.PERIOD_DAYS, artifacts, n);
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
