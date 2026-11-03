package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 早駆けの道 (RaceCourses): 道筋の形 (陸・関所の間・標の位置・印の時間) と、走りきる・印の報い・自己最高・外れるとやめる
public final class RaceSelfTest {
    private RaceSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<RaceCourses.Course> all = RaceCourses.all();
        check(f, all.size() == 5, "expected 5 courses, got " + all.size());
        ContinentMap map = ContinentMap.get();
        for (RaceCourses.Course c : all) {
            check(f, c.gates().size() >= 5, c.id() + ": only " + c.gates().size() + " gates");
            for (int i = 1; i < c.gates().size(); i++) {
                int[] a = c.gates().get(i - 1);
                int[] b = c.gates().get(i);
                double d = Math.hypot(b[0] - a[0], b[1] - a[1]);
                check(f, d > 10 && d <= 60, c.id() + ": gates " + (i - 1) + "-" + i + " are " + Math.round(d) + " apart");
            }
            // 関所は陸の上 (川の橋の上の一つまでは許す)
            int wet = 0;
            for (int[] g : c.gates()) {
                if (map.surface(g[0], g[1]) < ContinentChunkGenerator.SEA_LEVEL - 1) {
                    wet++;
                }
            }
            check(f, wet <= 1, c.id() + ": " + wet + " gates are over water");
            int[] g0 = c.gates().get(0);
            double post = Math.hypot(c.postX() - g0[0], c.postZ() - g0[1]);
            check(f, post >= 4 && post <= 8, c.id() + ": the post is " + Math.round(post) + " from the start");
            check(f, c.medalTicks(0) < c.medalTicks(1) && c.medalTicks(1) < c.medalTicks(2) && c.limitTicks() > c.medalTicks(2), c.id() + ": medal times out of order");
            // 金は最上の乗り物でないと届かない (最上の速さの 9 割で走っても金の時間より遅くはならない)
            check(f, c.length() / (RaceCourses.TOP_SPEED * 0.9) < c.medalTicks(0), c.id() + ": gold is out of reach even at top speed");
        }
        roadSteps(f, map);
        if (!all.isEmpty()) {
            run(f, server.overworld(), all.get(all.size() - 1));
        }
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST race courses FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST race courses PASSED ({} courses on land, gates <= 60 apart, posts beside the start, medal times; a run to the end with medal coins, best time kept, leaving the road stops it)",
                    all.size());
        }
    }

    // すべての道の線の上に、乗り物が越えられない段 (2 ブロック以上) がない (1 ブロックずつ。水の上の橋は見ない)
    private static void roadSteps(List<String> f, ContinentMap map) {
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

    // 偽の人を関所から関所へ置いて走らせる (乗り物なし)
    private static void run(List<String> f, ServerLevel level, RaceCourses.Course c) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000ace5"), "race_test"));
        RaceCourses.forgetForTest(p);
        try {
            long now = level.getGameTime();
            int coins = Coins.count(p);
            int gold = c.medalTicks(0);
            RaceCourses.begin(p, c, true);
            RaceCourses.Run r = RaceCourses.runOf(p);
            r.t0 = now;
            // 合図: 数えるあいだは走らない
            RaceCourses.step(p, now + RaceCourses.COUNT_TICKS - 1);
            check(f, r.phase == 0, c.id() + ": the race started before the count ended");
            RaceCourses.step(p, now + RaceCourses.COUNT_TICKS);
            check(f, r.phase == 1, c.id() + ": the race did not start after the count");
            long go = now + RaceCourses.COUNT_TICKS;
            // 金の時間の半分で走りきる
            int n = c.gates().size();
            for (int i = 1; i < n; i++) {
                int[] g = c.gates().get(i);
                p.setPos(g[0] + 0.5, 100, g[1] + 0.5);
                RaceCourses.step(p, go + (long) gold / 2 * i / (n - 1));
            }
            check(f, !RaceCourses.racing(p), c.id() + ": the race did not finish at the last gate");
            check(f, RaceCourses.medal(p, c) == 0, c.id() + ": a fast run got " + RaceCourses.medal(p, c) + ", not gold");
            int best = RaceCourses.best(p, c);
            check(f, best > 0 && best <= gold / 2 + 1, c.id() + ": best time " + best + " not kept");
            int paid = RaceCourses.COINS[0] + RaceCourses.COINS[1] + RaceCourses.COINS[2];
            check(f, Coins.count(p) == coins + paid, c.id() + ": the first gold paid " + (Coins.count(p) - coins) + ", expected " + paid);
            // 遅い二度目: 印も鐘銭も自己最高も変わらない
            int coins2 = Coins.count(p);
            RaceCourses.begin(p, c, true);
            r = RaceCourses.runOf(p);
            r.phase = 1;
            r.t0 = now;
            for (int i = 1; i < n; i++) {
                int[] g = c.gates().get(i);
                p.setPos(g[0] + 0.5, 100, g[1] + 0.5);
                RaceCourses.step(p, now + (long) c.medalTicks(1) * i / (n - 1));
            }
            check(f, RaceCourses.best(p, c) == best && RaceCourses.medal(p, c) == 0 && Coins.count(p) == coins2, c.id() + ": a slower run changed the record or paid again");
            // 道を外れるとやめる
            RaceCourses.begin(p, c, true);
            r = RaceCourses.runOf(p);
            r.phase = 1;
            r.t0 = now;
            p.setPos(c.gates().get(0)[0] + 400, 100, c.gates().get(0)[1] + 400);
            RaceCourses.step(p, now + 5);
            check(f, !RaceCourses.racing(p), c.id() + ": leaving the road did not stop the race");
        } finally {
            RaceCourses.forgetForTest(p);
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
