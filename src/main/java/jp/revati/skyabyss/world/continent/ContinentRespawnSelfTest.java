package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 倒れたあとの休み場 (ContinentRespawn): 行ったことのない土地の町では目覚めない。行った町は休み場に入る
public final class ContinentRespawnSelfTest {
    private ContinentRespawnSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel over = server.overworld();
        FakePlayer p = FakePlayerFactory.get(over, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000e5e5"), "respawn_test"));
        String before = p.getPersistentData().getString(ContinentAreas.TOWNS_TAG);
        try {
            SiteData data = SiteData.get(over);
            List<BlockPos> towns = new ArrayList<>();
            String region = null;
            for (SiteData.Entry e : data.entries(ContinentTowns.KIND)) {
                BlockPos r = e.extra("rest");
                if (e.built && r != null) {
                    towns.add(r);
                    if (region == null) {
                        region = ContinentTowns.region(e.index);
                    }
                }
            }
            if (towns.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST continent respawn PASSED (no built towns in this world; nothing to check)");
                return;
            }
            p.getPersistentData().putString(ContinentAreas.TOWNS_TAG, "");
            List<BlockPos> none = ContinentRespawn.rests(over, p);
            check(f, towns.stream().noneMatch(none::contains), "a town the player never visited is a resting place");
            p.getPersistentData().putString(ContinentAreas.TOWNS_TAG, region);
            List<BlockPos> one = ContinentRespawn.rests(over, p);
            check(f, one.size() == none.size() + 1, "the visited town (" + region + ") did not become a resting place");
            check(f, ContinentRespawn.rests(over).containsAll(towns), "the list without a player should hold every town");
        } finally {
            p.getPersistentData().putString(ContinentAreas.TOWNS_TAG, before);
        }
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST continent respawn FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST continent respawn PASSED (unvisited towns are not resting places; a visited one is)");
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
