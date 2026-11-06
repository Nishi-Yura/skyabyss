package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 外れの集落 (FrontierHamlets) の自己テスト。段の名前は "frontier hamlets" (大陸の世界で):
//  - 図に集落が 8 か所。どの部品も読めて、宿・寝台・道具屋・係の人・口・札・板・目覚める所の印がある
//  - どの道も集落の中を通らない (中心から 41 より遠い。部品の半分 34 + 道の半幅 4 + 余白)
//  - 一か所を建てると: 町と同じ守りの中・訪れた人の起きる所に入る・宿の寝台が数えられる・入ると "hamlet:<番号>"・曲は土地の町の曲・
//    口から道までの小道が敷かれている
public final class FrontierHamletsSelfTest {
    private FrontierHamletsSelfTest() {
    }

    static final String[] NEED = {"rest", "inn", "inn_bed", "shop", "npc_innkeeper", "npc_tool_merchant", "door", "sign", "board", "stand", "folk_a", "folk_b"};

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel over = server.overworld();
        try {
            List<ContinentLayout.Frontier> hs = FrontierHamlets.spots();
            check(f, hs.size() == 8, "the layout has " + hs.size() + " hamlets, expected 8");
            for (int i = 0; i < hs.size(); i++) {
                String name = FrontierHamlets.blueprint(i);
                Blueprint bp;
                try {
                    bp = Blueprint.get(name);
                } catch (RuntimeException e) {
                    f.add(name + " cannot be read: " + e.getMessage());
                    continue;
                }
                for (String m : NEED) {
                    check(f, bp.marks.containsKey(m), name + " lacks mark " + m);
                }
                BlockPos c = hs.get(i).pos();
                double near = Double.MAX_VALUE;
                for (List<BlockPos> road : ContinentLayout.get().roads()) {
                    for (BlockPos p : road) {
                        near = Math.min(near, Math.hypot(p.getX() - c.getX(), p.getZ() - c.getZ()));
                    }
                }
                check(f, near > 41, name + ": a road passes " + (int) near + " blocks from the centre (through the hamlet)");
            }
            if (!(over.getChunkSource().getGenerator() instanceof ContinentChunkGenerator) || hs.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST frontier hamlets: not a continent world, built checks skipped");
                report(f);
                return;
            }
            // 一か所 (岩鯨の北東の砦村) を建てて、町の仕組みとのつながりを見る
            int i = 0;
            ContinentLayout.Frontier h = hs.get(i);
            SiteData data = SiteData.get(over);
            SiteData.Entry e = FrontierHamlets.entry(data, i);
            if (e == null) {
                int y = ContinentMap.get().surface(h.pos().getX(), h.pos().getZ()) + 1;
                e = data.add(FrontierHamlets.KIND, i, new BlockPos(h.pos().getX(), y, h.pos().getZ()), ContinentPois.faceRoad(h.pos().getX(), h.pos().getZ()));
                e.blueprint = FrontierHamlets.blueprint(i);
            }
            if (!e.built) {
                SiteJob job = FrontierHamlets.create(over, data, e);
                job.blocking = true;
                int guard = 0;
                while (!job.step(System.nanoTime() + 200_000_000L) && guard++ < 400) {
                    // 進める
                }
            }
            check(f, e.built, "the hamlet " + FrontierHamlets.blueprint(i) + " was not built");
            if (e.built) {
                BlockPos rest = e.extra("rest");
                check(f, rest != null, "the built hamlet has no rest point");
                if (rest != null) {
                    check(f, jp.revati.skyabyss.opening.site.SiteGuard.inTown(over, rest), "the hamlet is not under the town guard");
                    check(f, FrontierHamlets.rests(over, null).contains(rest), "the hamlet's rest is not a waking place");
                    check(f, ContinentAreas.area(rest.getX(), rest.getZ(), "").equals("hamlet:" + i),
                            "standing in the hamlet reads as " + ContinentAreas.area(rest.getX(), rest.getZ(), ""));
                }
                check(f, ContinentTowns.innBeds(server).contains(e.extra("inn_bed")), "the hamlet's inn bed is not counted");
                String music = jp.revati.skyabyss.music.MusicTracks.forArea("hamlet:" + i);
                check(f, ("town_" + h.region()).equals(music), "the hamlet plays " + music + ", expected town_" + h.region());
                BlockPos door = e.extra("door");
                check(f, door != null, "the built hamlet has no door point");
                if (door != null) {
                    BlockPos road = FrontierHamlets.nearestRoad(door.getX(), door.getZ());
                    int paths = 0;
                    if (road != null) {
                        double dx = road.getX() - door.getX();
                        double dz = road.getZ() - door.getZ();
                        int steps = (int) Math.ceil(Math.hypot(dx, dz));
                        for (int k = 0; k <= steps; k++) {
                            int x = (int) Math.round(door.getX() + dx * k / Math.max(1, steps));
                            int z = (int) Math.round(door.getZ() + dz * k / Math.max(1, steps));
                            int y = over.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                            if (over.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT_PATH) || over.getBlockState(new BlockPos(x, y, z)).is(Blocks.COARSE_DIRT)) {
                                paths++;
                            }
                        }
                        check(f, paths >= steps / 2, "the path from the door to the road is missing (" + paths + " of " + steps + ")");
                    }
                }
            }
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST frontier hamlets crashed", ex);
            f.add("crashed: " + ex);
        }
        report(f);
    }

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST frontier hamlets PASSED (8 hamlets with every mark, no road through them; a built one is guarded, a waking place, has a counted inn bed, reads as its own area with the land's town music, and has a path from its door to the road)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST frontier hamlets FAILED: {}", x));
        }
    }

    static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
