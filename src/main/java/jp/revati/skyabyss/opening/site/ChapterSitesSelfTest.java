package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntBinaryOperator;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.ChapterPlanner.Obstacle;
import jp.revati.skyabyss.opening.site.ChapterPlanner.Placed;
import jp.revati.skyabyss.opening.site.ChapterPlanner.Req;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteFinder.Ground;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 章の小要素の共通基盤 (ChapterSites / ChapterPlanner) の自己テスト。段の名前は "chapter sites ..."。
//  (synthetic) 合成の地形 6 種 (海辺・山・雪原・森・砂漠・小島) × 拠点 3 つで、2 つ以上が拠点から 150 ブロック以内・間隔・陸の上・扇の広がり、
//              300 ブロック離れたときの判定と進行方向の先の 1 つ
//  (world)     本物のオーバーワールド: 置く・建つ・拠点から 150 以内・二度頼んでも増えない・同じ拠点は共有・遠い人は自分の拠点・
//              離れた人に先へ 1 つ・着いたときの呼び出しが人ごとに一度・人の建物の上には建てない
// 結果は SELFTEST chapter sites PASSED / FAILED
public final class ChapterSitesSelfTest {
    private static final int SEA = 62;
    private static final Chapters CH = Chapters.MOON;
    private static final String MARKER = "ch_test_marker";
    private static final String HUT = "ch_test_hut";
    private static final List<String> F = new ArrayList<>();
    private static int waited;
    private static BlockPos baseA;
    private static BlockPos baseC;
    private static BlockPos farPos;
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static int sitesA;
    private static int arrivals;

    private ChapterSitesSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("chapter sites (synthetic)", ChapterSitesSelfTest::synthetic));
        steps.add(Step.until("chapter sites (wait)", s -> {
            waited++;
            return (!SiteBuilds.busy() && !SiteBuilds.planning()) || waited > 12000;
        }));
        steps.add(Step.once("chapter sites (start)", ChapterSitesSelfTest::start));
        steps.add(Step.until("chapter sites (plan a)", s -> settled(s, "a")));
        steps.add(Step.once("chapter sites (check a)", ChapterSitesSelfTest::checkA));
        steps.add(Step.until("chapter sites (plan c)", s -> settled(s, "c")));
        steps.add(Step.once("chapter sites (check c)", ChapterSitesSelfTest::checkC));
        steps.add(Step.until("chapter sites (plan ahead)", s -> settled(s, "ahead")));
        steps.add(Step.once("chapter sites (check ahead)", ChapterSitesSelfTest::checkAhead));
        steps.add(Step.once("chapter sites (conflict)", ChapterSitesSelfTest::conflict));
        steps.add(Step.once("chapter sites (duplicates)", ChapterSitesSelfTest::duplicates));
        steps.add(Step.once("chapter sites (end)", ChapterSitesSelfTest::end));
        return steps;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    // ---------------------------------------------------------------- 合成の地形
    private record Terrain(String name, IntBinaryOperator height, java.util.function.BiPredicate<Integer, Integer> biome, int[][] bases) implements Ground {
        @Override
        public int top(int x, int z) {
            return height.applyAsInt(x, z);
        }

        @Override
        public int topCoarse(int x, int z) {
            return top(Math.floorDiv(x + 4, 8) * 8, Math.floorDiv(z + 4, 8) * 8);
        }

        @Override
        public boolean wet(int x, int z) {
            return top(x, z) < SEA;
        }

        @Override
        public boolean wetCoarse(int x, int z) {
            return topCoarse(x, z) < SEA;
        }

        @Override
        public boolean biomeOk(int x, int z) {
            return biome.test(x, z);
        }

        @Override
        public int sea() {
            return SEA;
        }
    }

    private static List<Terrain> terrains() {
        List<Terrain> t = new ArrayList<>();
        // 海辺: x < 0 は海。拠点は海岸のすぐ近く
        t.add(new Terrain("coast", (x, z) -> x >= 0 ? 70 + (int) (2 * Math.sin(x / 17.0) * Math.cos(z / 23.0)) : 55, (x, z) -> true,
                new int[][] {{12, 0}, {500, -300}, {15, 777}}));
        // 山: 谷と尾根が大きくうねる (低い谷は池になる)
        t.add(new Terrain("mountain", (x, z) -> (int) (95 + 38 * Math.sin(x / 37.0) * Math.cos(z / 41.0) + 18 * Math.sin(x / 11.0 + z / 13.0)), (x, z) -> true,
                new int[][] {{0, 0}, {500, -300}, {-1234, 777}}));
        t.add(new Terrain("snow", (x, z) -> 130 + (int) (2 * Math.sin(x / 9.0)), (x, z) -> true, new int[][] {{0, 0}, {500, -300}, {-1234, 777}}));
        // 森: ゆるい丘。暗い森 (使えない群系) が市松に混じる
        t.add(new Terrain("forest", (x, z) -> 70 + (int) (7 * Math.sin(x / 29.0) * Math.sin(z / 31.0)), (x, z) -> ((Math.floorDiv(x, 48) + Math.floorDiv(z, 48)) & 1) == 1,
                new int[][] {{0, 0}, {500, -300}, {-1234, 777}}));
        t.add(new Terrain("desert", (x, z) -> 66 + (int) (Math.sin(x / 50.0) * 2), (x, z) -> true, new int[][] {{0, 0}, {500, -300}, {-1234, 777}}));
        // 小島: 半径 70 の島だけが陸で、群系は全部「浜」(群系の条件をゆるめないと置けない)。島は 3000 おき
        t.add(new Terrain("island", (x, z) -> {
            int dx = Math.floorMod(x + 1500, 3000) - 1500;
            int dz = Math.floorMod(z + 1500, 3000) - 1500;
            return (long) dx * dx + (long) dz * dz < 70L * 70L ? 66 : 40;
        }, (x, z) -> false, new int[][] {{0, 0}, {3000, 0}, {-3000, 6000}}));
        return t;
    }

    private static List<Req> reqs() {
        Blueprint marker = Blueprint.get("chapter_test_marker");
        Blueprint hut = Blueprint.get("chapter_test_hut");
        int mh = Math.max(2, Math.max(marker.rx1 - marker.rx0, marker.rz1 - marker.rz0) / 2 + 1);
        int hh = Math.max(2, Math.max(hut.rx1 - hut.rx0, hut.rz1 - hut.rz0) / 2 + 1);
        List<Req> r = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            r.add(new Req(MARKER, i, mh, mh + marker.ring, 40, 140, Prefer.ANY));
        }
        r.add(new Req(HUT, 0, hh, hh + hut.ring, 40, 140, Prefer.HIGH));
        r.add(new Req(HUT, 1, hh, hh + hut.ring, 40, 140, Prefer.SHORE));
        return r;
    }

    static void synthetic(MinecraftServer server) {
        try {
            List<Req> reqs = reqs();
            for (Terrain t : terrains()) {
                for (int[] b0 : t.bases()) {
                    // 谷の池に当たったら乾いた所まで東へずらす (拠点は陸にある)
                    int[] b = {b0[0], b0[1]};
                    while (t.wet(b[0], b[1]) && b[0] < b0[0] + 400) {
                        b[0]++;
                    }
                    BlockPos base = new BlockPos(b[0], t.top(b[0], b[1]) + 1, b[1]);
                    String tag = t.name() + " base " + b[0] + "," + b[1];
                    check(!t.wet(b[0], b[1]), tag + ": the test base is in water (bad test)");
                    List<Placed> out = ChapterPlanner.plan(t, base, reqs, List.of(), Double.NaN, 3);
                    check(out.size() >= 2, tag + ": only " + out.size() + " sites placed");
                    int within = 0;
                    for (Placed p : out) {
                        double d = ChapterSites.horizontal(p.pos(), base);
                        if (d <= 150) {
                            within++;
                        }
                        check(d >= ChapterPlanner.MIN_DIST && d <= ChapterPlanner.MAX_DIST, tag + ": " + p.id() + " is " + (int) d + " from the base");
                        check(!t.wet(p.pos().getX(), p.pos().getZ()), tag + ": " + p.id() + " is in water");
                    }
                    check(within >= 2, tag + ": only " + within + " sites within 150 blocks");
                    for (int i = 0; i < out.size(); i++) {
                        for (int j = i + 1; j < out.size(); j++) {
                            double d = ChapterSites.horizontal(out.get(i).pos(), out.get(j).pos());
                            check(d >= out.get(i).radius() + out.get(j).radius(), tag + ": sites " + i + " and " + j + " overlap (" + (int) d + ")");
                        }
                    }
                    if (t.name().equals("snow") || t.name().equals("desert") || (t.name().equals("forest") && b[0] == 0)) {
                        check(out.size() == reqs.size(), tag + ": " + out.size() + " of " + reqs.size() + " placed on easy ground");
                    }
                    if (t.name().equals("snow") && out.size() >= 3) {
                        // 扇: 最初の 3 つは別々の方向 (どの二つも 90 度以上離れる)
                        for (int i = 0; i < 3; i++) {
                            for (int j = i + 1; j < 3; j++) {
                                double a1 = Math.atan2(out.get(i).pos().getZ() - base.getZ(), out.get(i).pos().getX() - base.getX());
                                double a2 = Math.atan2(out.get(j).pos().getZ() - base.getZ(), out.get(j).pos().getX() - base.getX());
                                double diff = Math.abs(Math.toDegrees(a1 - a2)) % 360;
                                diff = diff > 180 ? 360 - diff : diff;
                                check(diff >= 90, tag + ": fan directions " + i + "," + j + " are only " + (int) diff + " degrees apart");
                            }
                        }
                    }
                    // 進行方向の先に 1 つ: 60〜120 先 (海のほうを向いていても、陸があれば別の向きに置く)
                    List<Placed> ahead = ChapterPlanner.plan(t, base, reqs.subList(0, 1), List.of(), Math.PI, 3);
                    check(ahead.size() == 1, tag + ": no site ahead");
                    for (Placed p : ahead) {
                        double d = ChapterSites.horizontal(p.pos(), base);
                        check(d >= ChapterPlanner.AHEAD_MIN - 1 && d <= ChapterPlanner.AHEAD_MAX + 1, tag + ": the ahead site is " + (int) d + " away");
                    }
                    if (t.name().equals("snow") || t.name().equals("desert")) {
                        Placed p = ahead.get(0);
                        double a = Math.toDegrees(Math.atan2(p.pos().getZ() - base.getZ(), p.pos().getX() - base.getX()));
                        double diff = Math.abs(a - 180) % 360;
                        diff = diff > 180 ? 360 - diff : diff;
                        check(diff <= 25, tag + ": the ahead site is " + (int) diff + " degrees off the travel direction");
                    }
                    SkyAbyss.LOGGER.info("[chapter sites] {}: {} of {} placed", tag, out.size(), reqs.size());
                }
            }
            // 300 ブロック離れた判定
            List<BlockPos> pts = List.of(new BlockPos(0, 70, 0), new BlockPos(100, 70, 50), new BlockPos(-80, 70, 90));
            check(ChapterSites.farFromAll(pts, new BlockPos(500, 70, 0)), "500 blocks east of the sites is not far");
            check(!ChapterSites.farFromAll(pts, new BlockPos(350, 70, 0)), "350 blocks east (250 from the nearest site) counts as far");
            check(!ChapterSites.farFromAll(pts, new BlockPos(0, 70, 299)), "the near side of 300 counts as far");
            check(ChapterSites.farFromAll(pts, new BlockPos(0, 70, 500)), "500 blocks south is not far");
            check(!ChapterSites.farFromAll(List.of(), new BlockPos(9999, 70, 0)), "with no sites yet, nobody is far (the chapter has not opened)");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sites (synthetic) crashed", e);
            F.add("synthetic crashed: " + e);
        }
    }

    // ---------------------------------------------------------------- 本物の世界
    private static boolean settled(MinecraftServer server, String which) {
        waited++;
        if (waited > 40000) {
            F.add("timed out waiting for " + which);
            return true;
        }
        SiteData data = SiteData.get(server.overworld());
        if (ChapterSites.pending() || SiteBuilds.busy()) {
            return false;
        }
        return switch (which) {
            case "a" -> data.plans().stream().anyMatch(p -> p.chapter.equals(CH.id()) && !p.ahead && p.players.contains(A));
            case "c" -> data.plans().stream().anyMatch(p -> p.chapter.equals(CH.id()) && !p.ahead && p.players.contains(C));
            default -> data.plans().stream().anyMatch(p -> p.chapter.equals(CH.id()) && p.ahead);
        };
    }

    private static BlockPos dry(SiteFinder.Probe probe, double angle, int dFrom, int dTo, BlockPos from) {
        return dry(probe, angle, dFrom, dTo, from, null);
    }

    // avoid: その点から AWAY_DIST + 150 (遠い人の拠点の小要素の広がり) より近い所は選ばない。西が海の世界で、向きを回して探すうちに
    // 東の遠い人 (C) の拠点のそばを選び、「どの場所からも遠い」が世界の種しだいで落ちていた
    private static BlockPos dry(SiteFinder.Probe probe, double angle, int dFrom, int dTo, BlockPos from, BlockPos avoid) {
        for (int d = dFrom; d <= dTo; d += 25) {
            for (int k = 0; k < 24; k++) {
                double a = angle + (k % 2 == 0 ? 1 : -1) * Math.toRadians(15 * ((k + 1) / 2));
                int x = from.getX() + (int) Math.round(Math.cos(a) * d);
                int z = from.getZ() + (int) Math.round(Math.sin(a) * d);
                if (avoid != null && Math.hypot(x - avoid.getX(), z - avoid.getZ()) < ChapterSites.AWAY_DIST + 150) {
                    continue;
                }
                if (SiteFinder.area(probe, x, z, 3, 3, 3).ok()) {
                    return new BlockPos(x, probe.top(x, z) + 1, z);
                }
            }
        }
        return null;
    }

    static void start(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            ChapterSites.element(MARKER, CH, "chapter_test_marker").count(3).dist(40, 140).arriveRadius(16).register();
            ChapterSites.element(HUT, CH, "chapter_test_hut").count(2).dist(40, 140).prefer(Prefer.HIGH).arriveRadius(16).onArrive((p, s) -> arrivals++).register();
            // 天と淵の大陸では野営地は海の向こうの島なので、遠い所の試しは鐘の都から
            baseA = jp.revati.skyabyss.world.continent.ContinentInfo.isContinent(server) ? jp.revati.skyabyss.world.continent.ContinentLayout.get().capital()
                    : OpeningSites.camp(server).orElse(level.getSharedSpawnPos());
            SiteFinder.Probe probe = new SiteFinder.Probe(level);
            baseC = dry(probe, 0, 700, 900, baseA);
            farPos = dry(probe, Math.PI, 500, 650, baseA, baseC);
            check(baseC != null && farPos != null, "no dry land for the distant tests");
            waited = 0;
            check(ChapterSites.request(server, A, CH, baseA), "the first request did not schedule anything");
            // 同じ人がすぐ頼み直しても、探している最中は二つ目を作らない
            check(!ChapterSites.request(server, A, CH, baseA), "a second request while searching scheduled another search");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sites (start) crashed", e);
            F.add("start crashed: " + e);
        }
    }

    private static List<SiteData.ChapterPlan> plans(MinecraftServer server) {
        return SiteData.get(server.overworld()).plans().stream().filter(p -> p.chapter.equals(CH.id())).toList();
    }

    static void checkA(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            SiteData data = SiteData.get(level);
            List<SiteData.ChapterPlan> ps = plans(server);
            check(ps.size() == 1, "expected one plan, got " + ps.size());
            SiteData.ChapterPlan plan = ps.get(0);
            sitesA = plan.sites.size();
            int within = 0;
            for (SiteData.Entry e : plan.sites) {
                check(e.built, "site " + e.siteId + "#" + e.index + " is not built");
                if (ChapterSites.horizontal(e.pos, plan.anchor) <= 150) {
                    within++;
                }
                // 部品の中心の下の段が置かれている
                var st = level.getBlockState(e.pos);
                boolean ok = e.siteId.equals(MARKER) ? st.is(Blocks.STONE_BRICKS) : st.is(Blocks.MOSSY_COBBLESTONE);
                check(ok, "site " + e.siteId + "#" + e.index + " at " + e.pos + " has " + st + " instead of its blueprint");
            }
            check(sitesA >= 2 && within >= 2, "only " + within + " of " + sitesA + " sites within 150 blocks of the base " + plan.anchor);
            if (sitesA != 5) {
                SkyAbyss.LOGGER.warn("[chapter sites] only {} of 5 sites placed on the real terrain near {}", sitesA, plan.anchor);
            }
            SkyAbyss.LOGGER.info("[chapter sites] world: {} sites around {}", sitesA, plan.anchor);
            // 冪等: もう一度 (再ログイン・再起動のつもり) 頼んでも増えない
            check(!ChapterSites.request(server, A, CH, baseA), "a repeated request scheduled work");
            check(!ChapterSites.request(server, A, CH, baseA.offset(30, 0, -20)), "a repeated request from a nearby base scheduled work");
            check(plans(server).size() == 1 && plans(server).get(0).sites.size() == sitesA, "a repeated request changed the plan");
            // 共有: 近くに家を持つ人は同じ拠点を使う
            check(!ChapterSites.request(server, B, CH, baseA.offset(60, 0, 40)), "a player with a nearby base scheduled a new search");
            check(plans(server).size() == 1 && plans(server).get(0).players.contains(B), "the nearby player did not share the base");
            check(ChapterSites.sitesFor(server, B, HUT).size() == 2 && ChapterSites.sitesFor(server, C, HUT).isEmpty(), "sitesFor does not follow the shared base");
            // 着いたとき: 人ごとに一度
            BlockPos hut = ChapterSites.sitesFor(server, A, HUT).get(0).pos();
            int[] n = {0};
            for (int i = 0; i < 3; i++) {
                ChapterSites.arrive(data, A, hut, CH, (el, s) -> n[0]++);
            }
            check(n[0] >= 1 && n[0] <= 2, "arrive fired " + n[0] + " times for one player (expected once per nearby site)");
            int before = n[0];
            ChapterSites.arrive(data, B, hut, CH, (el, s) -> n[0]++);
            check(n[0] > before, "arrive did not fire for the second player");
            ChapterSites.arrive(data, C, hut, Chapters.WHALE, (el, s) -> n[0]++);
            int after = n[0];
            ChapterSites.arrive(data, C, hut.offset(400, 0, 0), CH, (el, s) -> n[0]++);
            check(n[0] == after, "arrive fired for a player who was far away or in another chapter");
            // 遠い人は自分の拠点
            waited = 0;
            check(baseC != null && ChapterSites.request(server, C, CH, baseC), "a distant player did not get a new search");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sites (check a) crashed", e);
            F.add("check a crashed: " + e);
        }
    }

    static void checkC(MinecraftServer server) {
        try {
            List<SiteData.ChapterPlan> ps = plans(server);
            check(ps.size() == 2, "expected two plans after the distant player, got " + ps.size());
            SiteData.ChapterPlan pc = ps.stream().filter(p -> p.players.contains(C)).findFirst().orElse(null);
            check(pc != null && !pc.players.contains(A) && !pc.players.contains(B), "the distant player shares the first base");
            if (pc != null) {
                check(pc.sites.size() >= 1, "the distant player's base has no sites");
                check(pc.sites.stream().allMatch(e -> e.built), "the distant player's sites are not all built");
                check(pc.sites.stream().allMatch(e -> ChapterSites.horizontal(e.pos, pc.anchor) <= 150), "a distant site is more than 150 from its base");
            }
            // 拠点から離れた人 A (farPos = 西へ 500 以上): 進行方向 (西) の先に 1 つ
            SiteData data = SiteData.get(server.overworld());
            check(farPos != null && ChapterSites.farFromAll(ChapterSites.pointsOf(data, CH.id()), farPos), "the far position is not far from every site");
            waited = 0;
            check(farPos != null && ChapterSites.requestAhead(server, A, CH, farPos, Math.PI), "the far player did not get a site ahead");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sites (check c) crashed", e);
            F.add("check c crashed: " + e);
        }
    }

    static void checkAhead(MinecraftServer server) {
        try {
            List<SiteData.ChapterPlan> ps = plans(server);
            SiteData.ChapterPlan ahead = ps.stream().filter(p -> p.ahead).findFirst().orElse(null);
            check(ps.size() == 3 && ahead != null, "expected an ahead plan, plans: " + ps.size());
            if (ahead != null) {
                check(ahead.sites.size() == 1, "the ahead plan has " + ahead.sites.size() + " sites (expected exactly one)");
                for (SiteData.Entry e : ahead.sites) {
                    double d = ChapterSites.horizontal(e.pos, farPos);
                    check(e.built, "the ahead site is not built");
                    check(d >= 40 && d <= 150, "the ahead site is " + (int) d + " from the player");
                }
                check(ahead.players.contains(A), "the ahead plan does not list the far player");
            }
            // 頼み直しても増えない
            check(!ChapterSites.request(server, A, CH, baseA), "a repeat request after the ahead plan scheduled work");
            int total = ps.stream().mapToInt(p -> p.sites.size()).sum();
            check(ChapterSites.sites(server, CH).size() == total, "sites(server, chapter) does not list every site");
            check(ChapterSites.sites(server, MARKER).size() + ChapterSites.sites(server, HUT).size() == total, "sites(server, id) does not list every site");
            check(ChapterSites.nearest(server, A, HUT, baseA) != null, "nearest found no site for the first player");
            // 保存と読み直し (再起動): 計画・人・置こうとした要素・場所・着いた人が同じに戻り、読み直したあとの頼みでも増えない
            SiteData data = SiteData.get(server.overworld());
            SiteData back = SiteData.load(data.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()), server.registryAccess());
            check(back.plans().size() == data.plans().size(), "saved plans differ in number");
            for (SiteData.ChapterPlan p : data.plans()) {
                SiteData.ChapterPlan q = back.plan(p.chapter, p.anchor);
                check(q != null && q.ahead == p.ahead && q.players.equals(p.players) && q.attempted.equals(p.attempted) && q.sites.size() == p.sites.size(),
                        "a saved plan did not come back the same: " + p.key());
                if (q != null) {
                    for (int i = 0; i < p.sites.size(); i++) {
                        SiteData.Entry x = p.sites.get(i);
                        SiteData.Entry y = q.sites.get(i);
                        check(x.pos.equals(y.pos) && x.siteId.equals(y.siteId) && x.index == y.index && x.built == y.built && x.yaw == y.yaw && x.arrived.equals(y.arrived)
                                && x.blueprint.equals(y.blueprint) && y.anchor != null && y.anchor.equals(q.anchor), "a saved site did not come back the same: " + x.siteId);
                    }
                }
            }
            // 序盤の 4 種の数には入らない
            check(SiteData.get(server.overworld()).entries().stream().noneMatch(e -> e.kind.startsWith(ChapterSites.KIND_PREFIX)),
                    "a chapter site leaked into the opening entries");
            check(OpeningSites.all(server).stream().noneMatch(s -> ps.stream().anyMatch(p -> p.sites.stream().anyMatch(e -> e.pos.equals(s.pos())))),
                    "OpeningSites lists a chapter site");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sites (check ahead) crashed", e);
            F.add("check ahead crashed: " + e);
        }
    }

    // 人の建物の上には建てない: 板の床と箱のある所に小要素を頼むと、壊さずにずれる (またはあきらめる)
    static void conflict(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            SiteData data = SiteData.get(level);
            SiteFinder.Probe probe = new SiteFinder.Probe(level);
            BlockPos at = dry(probe, 1.0, 200, 400, baseA);
            if (at == null) {
                SkyAbyss.LOGGER.info("SELFTEST chapter sites (conflict) skipped: no land");
                return;
            }
            level.getChunk(at.getX() >> 4, at.getZ() >> 4);
            SiteData.Entry entry = new SiteData.Entry(ChapterSites.KIND_PREFIX + HUT, 9, new BlockPos(at.getX(), at.getY(), at.getZ()), 0);
            entry.siteId = HUT;
            entry.blueprint = "chapter_test_hut";
            entry.anchor = entry.pos;
            entry.maxDist = 150;
            entry.radius = 6;
            SiteJob job = ChapterSites.jobFor(level, data, entry);
            check(job != null, "no job for the conflict test");
            if (job == null) {
                return;
            }
            int g = job.ground(at.getX(), at.getZ());
            BlockPos floor = new BlockPos(at.getX(), g, at.getZ());
            List<BlockPos> made = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = floor.offset(dx, 0, dz);
                    level.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
                    made.add(p);
                }
            }
            BlockPos chest = floor.above();
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2 | 16);
            BlockPos orig = entry.pos;
            job.blocking = true;
            int guard = 0;
            while (!job.step(System.nanoTime() + 200_000_000L) && guard++ < 200) {
                // 進める
            }
            check(level.getBlockState(chest).is(Blocks.CHEST), "the chest was destroyed by a chapter site");
            for (BlockPos p : made) {
                check(level.getBlockState(p).is(Blocks.OAK_PLANKS), "the plank floor at " + p + " was destroyed by a chapter site");
            }
            check(!entry.pos.equals(orig) || !entry.built, "a chapter site was built on top of a player's building");
            check(!entry.built || ChapterSites.horizontal(entry.pos, orig) <= 150, "the moved chapter site left the 150-block limit");
            level.setBlock(chest, Blocks.AIR.defaultBlockState(), 2 | 16);
            for (BlockPos p : made) {
                level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2 | 16);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sites (conflict) crashed", e);
            F.add("conflict crashed: " + e);
        }
    }

    // 同じ要素が近くに二つ (別々の拠点の計画が同時に走った、などの古い世界) あっても、読み込み時の掃除で一つになる。人が置いたブロックは触らない
    static void duplicates(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            SiteData data = SiteData.get(level);
            SiteData.ChapterPlan first = plans(server).stream().filter(p -> p.sites.stream().anyMatch(e -> e.siteId.equals(HUT) && e.index == 0)).findFirst().orElse(null);
            check(first != null, "no hut to duplicate");
            if (first == null) {
                return;
            }
            SiteData.Entry orig = first.sites.stream().filter(e -> e.siteId.equals(HUT) && e.index == 0).findFirst().get();
            SiteData.ChapterPlan other = data.addPlan(CH.id(), orig.pos.offset(30, 0, 30), false);
            other.players.add(C);
            SiteData.Entry dup = new SiteData.Entry(ChapterSites.KIND_PREFIX + HUT, 0, orig.pos.offset(18, 0, 0), 0);
            dup.siteId = HUT;
            dup.blueprint = "chapter_test_hut";
            dup.anchor = other.anchor;
            dup.maxDist = 150;
            dup.radius = 6;
            other.sites.add(dup);
            SiteJob job = ChapterSites.jobFor(level, data, dup);
            check(job != null, "no job for the duplicate");
            if (job == null) {
                return;
            }
            job.blocking = true;
            int guard = 0;
            while (!job.step(System.nanoTime() + 200_000_000L) && guard++ < 200) {
                // 進める
            }
            BlockPos at = dup.pos;
            check(dup.built && level.getBlockState(at).is(Blocks.MOSSY_COBBLESTONE), "the duplicate hut was not built for the test");
            BlockPos marker = at.offset(0, 3, 0);
            BlockPos mine = orig.pos.offset(-12, 0, 12);
            BlockState keep = level.getBlockState(mine);
            level.setBlock(mine, Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
            int removed = ChapterSites.dedupe(server);
            check(removed == 1, "dedupe removed " + removed + " (expected 1)");
            check(!other.sites.contains(dup) && first.sites.contains(orig), "dedupe kept the wrong site");
            check(first.players.contains(C), "the removed site's player was not moved to the kept base");
            check(level.getBlockState(orig.pos).is(Blocks.MOSSY_COBBLESTONE), "the kept hut was damaged");
            check(ChapterSites.horizontal(at, orig.pos) <= 2 || !level.getBlockState(at).is(Blocks.MOSSY_COBBLESTONE), "the duplicate hut's blocks were left standing");
            check(level.getBlockState(mine).is(Blocks.OAK_PLANKS), "dedupe touched a player's block");
            check(ChapterSites.dedupe(server) == 0, "a second dedupe removed more");
            level.setBlock(mine, keep, 2 | 16);
            data.plans().remove(other);
            data.setDirty();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sites (duplicates) crashed", e);
            F.add("duplicates crashed: " + e);
        }
    }

    static void end(MinecraftServer server) {
        try {
            SiteData data = SiteData.get(server.overworld());
            data.plans().removeIf(p -> p.chapter.equals(CH.id()));
            data.setDirty();
        } finally {
            ChapterSites.unregister(MARKER);
            ChapterSites.unregister(HUT);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST chapter sites PASSED (6 synthetic terrains: >=2 within 150 blocks, spacing, dry, fan, ahead; real world: placed, built, idempotent, shared, distant base, ahead site, arrive once, player blocks kept)");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST chapter sites FAILED: {}", m));
        }
    }
}
