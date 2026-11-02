package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;

// 巨神の居場所 (ContinentLairs): 四体の居場所が読める・体の下の地面が体より低い・道/町/跡地/見どころにかからない。SKYABYSS_SELFTEST_ONLY="titan lairs"
public final class ContinentLairsSelfTest {
    private ContinentLairsSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        var lairs = ContinentLairs.all();
        for (String t : List.of("stone_whale", "tide_shell", "thunder_wing", "ember")) {
            if (!lairs.containsKey(t)) {
                f.add("no lair for " + t);
            }
        }
        ContinentMap map = ContinentMap.get();
        ContinentLayout lay = ContinentLayout.get();
        int checked = 0;
        for (ContinentLairs.Lair l : lairs.values()) {
            if (!l.grounded()) {
                continue;
            }
            TitanVolume v = TitanVolume.get(l.volume());
            double fx = -Mth.sin(l.yaw() * Mth.DEG_TO_RAD);
            double fz = Mth.cos(l.yaw() * Mth.DEG_TO_RAD);
            int over = 0;
            for (TitanVolume.Column c : v.coreColumns()) {
                int x = Mth.floor(l.x() + fx * c.fwd() + fz * c.side());
                int z = Mth.floor(l.z() + fz * c.fwd() - fx * c.side());
                if (map.surface(x, z) >= l.base() + c.lo()) {
                    over++;
                }
                checked++;
            }
            if (over > 0) {
                f.add(l.titan() + ": the ground touches the body in " + over + " columns");
            }
            // 道・町・跡地・見どころ
            for (List<BlockPos> road : lay.roads()) {
                for (int i = 0; i + 1 < road.size(); i++) {
                    BlockPos a = road.get(i);
                    BlockPos b = road.get(i + 1);
                    for (int k = 0; k <= 8; k++) {
                        int x = a.getX() + (b.getX() - a.getX()) * k / 8;
                        int z = a.getZ() + (b.getZ() - a.getZ()) * k / 8;
                        if (ContinentLairs.inside(x, z)) {
                            f.add(l.titan() + ": a road runs through the lair at " + x + ", " + z);
                            k = 9;
                            i = road.size();
                        }
                    }
                }
            }
            for (String t : List.of("whale", "tide", "storm", "ember")) {
                BlockPos s = lay.site(t);
                BlockPos town = lay.town(t);
                if (s != null && ContinentLairs.inside(s.getX(), s.getZ())) {
                    f.add(l.titan() + ": the " + t + " site is inside the lair");
                }
                if (town != null && ContinentLairs.inside(town.getX(), town.getZ())) {
                    f.add(l.titan() + ": the " + t + " town is inside the lair");
                }
            }
            for (ContinentPois.Poi p : ContinentPois.all()) {
                if (ContinentLairs.inside(p.x(), p.z())) {
                    f.add(l.titan() + ": the " + p.kind() + " point of interest at " + p.x() + ", " + p.z() + " is inside the lair");
                }
            }
        }
        // 祠の広場: 四つあり、半径 6 の中が平ら
        for (String t : List.of("stone_whale", "tide_shell", "thunder_wing", "ember")) {
            int[] c = ContinentLairs.shrines().get(t);
            if (c == null) {
                f.add("no shrine spot for " + t);
                continue;
            }
            int y = map.surface(c[0], c[1]);
            for (int dx = -6; dx <= 6; dx += 3) {
                for (int dz = -6; dz <= 6; dz += 3) {
                    if (dx * dx + dz * dz <= 36 && map.surface(c[0] + dx, c[1] + dz) != y) {
                        f.add(t + ": the shrine pad is not flat at " + (c[0] + dx) + ", " + (c[1] + dz));
                        dx = 99;
                        break;
                    }
                }
            }
            if (y < ContinentChunkGenerator.SEA_LEVEL) {
                f.add(t + ": the shrine pad is under the sea");
            }
        }
        // 生きた巨神: 潮殻を居場所に出すと、居場所で休む (片付けて、ほかの段に残さない)
        var over = server.overworld();
        ContinentLairs.testLive = true;
        try {
            var keeper = jp.revati.skyabyss.titan.tide.TideKeeper.KEEPER;
            ContinentLairs.Lair lair = keeper.lair(over);
            var slot = keeper.slot(over);
            boolean wasSpawned = slot.spawned;
            var tide = lair == null ? null : keeper.appearAtLair(over, true);
            if (tide == null) {
                f.add("the tide shell did not appear at its lair");
            } else {
                if (Math.hypot(tide.getX() - lair.x(), tide.getZ() - lair.z()) > 1 || !tide.settled() || !tide.anchored()) {
                    f.add("the tide shell is not resting at its lair (" + (int) tide.getX() + ", " + (int) tide.getZ() + ", settled " + tide.settled() + ")");
                }
                tide.discard();
                keeper.releaseTicket(over);
                slot.spawned = wasSpawned;
                slot.id = null;
                slot.hasLast = false;
                jp.revati.skyabyss.titan.TitanWorldData.get(over).setDirty();
            }
        } finally {
            ContinentLairs.testLive = false;
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST titan lairs PASSED (four lairs, {} body columns clear of the ground, four flat shrine pads, no road/town/site/poi inside, the tide shell rests at its lair)", checked);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST titan lairs FAILED: {}", s));
        }
    }
}
