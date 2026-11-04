package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;

// 大陸の道: どの道の線の上にも、歩きでも乗り物でも越えられない段 (2 ブロック以上) がない
public final class ContinentRoadsSelfTest {
    private ContinentRoadsSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        roadSteps(f, ContinentMap.get());
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST continent roads FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST continent roads PASSED ({} roads walked block by block, no 2-block steps)", ContinentLayout.get().roads().size());
        }
    }

    // すべての道の線の上に、乗り物が越えられない段 (2 ブロック以上) がない (1 ブロックずつ。水の上の橋は見ない)
    static void roadSteps(List<String> f, ContinentMap map) {
        List<List<net.minecraft.core.BlockPos>> roads = ContinentLayout.get().roads();
        for (int r = 0; r < roads.size(); r++) {
            List<net.minecraft.core.BlockPos> line = roads.get(r);
            int steps = 0;
            String where = "";
            int lx = Integer.MIN_VALUE;
            int lz = 0;
            for (int i = 1; i < line.size(); i++) {
                net.minecraft.core.BlockPos a = line.get(i - 1);
                net.minecraft.core.BlockPos b = line.get(i);
                int n = Math.max(1, (int) Math.ceil(Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ())));
                for (int k = i == 1 ? 0 : 1; k <= n; k++) {
                    int x = (int) Math.floor(a.getX() + (b.getX() - a.getX()) * (double) k / n);
                    int z = (int) Math.floor(a.getZ() + (b.getZ() - a.getZ()) * (double) k / n);
                    if (lx != Integer.MIN_VALUE && (x != lx || z != lz)) {
                        int step = rise(map, lx, lz, x, z);
                        int h = map.surface(x, z);
                        if (step >= 2 && Math.min(h, map.surface(lx, lz)) >= ContinentChunkGenerator.SEA_LEVEL - 1) {
                            steps++;
                            where = x + "," + z + " (" + map.surface(lx, lz) + "->" + h + ")";
                        }
                    }
                    lx = x;
                    lz = z;
                }
            }
            check(f, steps == 0, "road " + r + ": " + steps + " steps of 2+ blocks, e.g. " + where);
        }
    }

    // 隣のマスへ移る段の高さ。斜めは、横か縦のマスを通る二つの道のうち低い方 (人は斜めの角をそのまま越えない)
    private static int rise(ContinentMap map, int x0, int z0, int x1, int z1) {
        int h0 = map.surface(x0, z0);
        int h1 = map.surface(x1, z1);
        if (x0 == x1 || z0 == z1) {
            return Math.abs(h1 - h0);
        }
        int viaX = map.surface(x1, z0);
        int viaZ = map.surface(x0, z1);
        return Math.min(Math.max(Math.abs(viaX - h0), Math.abs(h1 - viaX)), Math.max(Math.abs(viaZ - h0), Math.abs(h1 - viaZ)));
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
