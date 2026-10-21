package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntBinaryOperator;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.map.MapMarks;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.ChapterGrounds.Found;
import jp.revati.skyabyss.opening.site.ChapterGrounds.Shape;
import jp.revati.skyabyss.opening.site.ChapterGrounds.Spec;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.tide.TideBeach;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 章の跡地 (docs/design/33 の 3 章。W2) の自己テスト。SKYABYSS_SELFTEST_ONLY="chapter grounds"。結果は SELFTEST chapter grounds PASSED / FAILED
//   pure   合成の地形で: 扇の順・合う地形を内側から選ぶ (丘・海辺・峰・灰の地)・合わなければ帯のまんなかに形・構造物を避ける・形を重ねた高さ
//          (丘 +24・峰 +60・盆地の底・入り江の水と体の箱)・道しるべ (40 ごと・水を飛ばす・一章は 7 つ以上)・祠が平ら・地図の噂と見た
//   live   本物の世界で: 四つの跡地が方位の扇と帯の中・祠の置き場・一章の骨が北の丘の中・形を整えた地形 (高さ・水・灰)・
//          三章ぶんの章を開いて跡地の小要素が中心から 150 以内に一つずつ・道しるべの石標・潮殻の浜 (休む場所)
public final class ChapterGroundsSelfTest {
    private static final List<String> F = new ArrayList<>();
    private static final UUID FAKE = UUID.nameUUIDFromBytes("skyabyss_w2_grounds".getBytes());
    private static int waited;

    private ChapterGroundsSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("chapter grounds", s -> pure()));
        steps.add(Step.until("chapter grounds (wait)", s -> {
            waited++;
            return (!SiteBuilds.busy() && !SiteBuilds.planning() && SiteData.get(s.overworld()).groundsPlanned()) || waited > 24000;
        }));
        steps.add(Step.once("chapter grounds (open)", ChapterGroundsSelfTest::open));
        steps.add(Step.until("chapter grounds (build)", s -> {
            waited++;
            return (waited > 40 && !SiteBuilds.busy() && !SiteBuilds.planning()) || waited > 30000;
        }));
        steps.add(Step.once("chapter grounds (live)", ChapterGroundsSelfTest::live));
        return steps;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    // ---------------------------------------------------------------- 合成の地形
    static final class Synth implements SiteFinder.Ground {
        final IntBinaryOperator height;
        boolean dry;
        int structureX = Integer.MIN_VALUE;
        int structureZ;

        Synth(IntBinaryOperator height) {
            this.height = height;
        }

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
            return top(x, z) < 63;
        }

        @Override
        public boolean wetCoarse(int x, int z) {
            return topCoarse(x, z) < 63;
        }

        @Override
        public boolean biomeOk(int x, int z) {
            return true;
        }

        @Override
        public int sea() {
            return 63;
        }

        @Override
        public boolean dryBiome(int x, int z) {
            return dry;
        }

        @Override
        public boolean structureNear(int x, int z, int r) {
            return structureX != Integer.MIN_VALUE && Math.hypot(x - structureX, z - structureZ) <= r;
        }
    }

    // 方位の扇と帯の中か (角度の余白 tol 度、距離の余白 dtol)
    static boolean inSector(BlockPos capital, BlockPos at, Spec s, double tol, int dtol) {
        double dx = at.getX() - capital.getX();
        double dz = at.getZ() - capital.getZ();
        double d = Math.hypot(dx, dz);
        double diff = Math.toDegrees(Math.atan2(dz, dx) - s.angle());
        diff = ((diff % 360) + 540) % 360 - 180;
        return Math.abs(diff) <= ChapterGrounds.SECTOR + tol && d >= s.min() - dtol && d <= s.max() + dtol;
    }

    static void pure() {
        F.clear();
        try {
            BlockPos cap = new BlockPos(0, 70, 0);
            int[] off = ChapterGrounds.offsets();
            check(off.length == 11 && off[0] == 0 && off[1] == 5 && off[2] == -5 && off[10] == -25, "sector offsets should go 0, +5, -5 ... -25");
            // 平らな草地: 丘・峰・灰の地・海辺はない → 形
            Synth flat = new Synth((x, z) -> 70);
            for (Spec s : ChapterGrounds.SPECS) {
                Found f = ChapterGrounds.find(flat, cap, s, List.of());
                check(f.shape() == s.shape(), s.chapter() + ": flat land should get the shape " + s.shape() + " (got " + f.shape() + ")");
                check(inSector(cap, f.shape() == Shape.COVE ? f.shapeCenter() : f.center(), s, 1, 0), s.chapter() + ": the shape is outside the sector or the band: " + f.center());
                check(f.shrine() != null, s.chapter() + ": no shrine spot");
            }
            // 丘 (北 400 に高さ 30 の丘) → 形なしで、その丘
            Synth hill = new Synth((x, z) -> 70 + (int) Math.max(0, 30 - Math.hypot(x, z + 420) * 0.4));
            Found wh = ChapterGrounds.find(hill, cap, ChapterGrounds.spec(Chapters.WHALE), List.of());
            check(wh.shape() == Shape.NONE && Math.hypot(wh.center().getX(), wh.center().getZ() + 420) < 48, "the whale ground should take the hill north of the capital: " + wh.center());
            // 野営地が丘の上にあれば、そこは避ける
            Found wh2 = ChapterGrounds.find(hill, cap, ChapterGrounds.spec(Chapters.WHALE), List.of(new ChapterGrounds.Obstacle(new BlockPos(0, 70, -420), 190)));
            check(Math.hypot(wh2.center().getX(), wh2.center().getZ() + 420) >= 150, "the whale ground should keep away from the camp: " + wh2.center());
            // 構造物 (村) が丘のそばにあれば、ずらす
            hill.structureX = 0;
            hill.structureZ = -440;
            Found wh3 = ChapterGrounds.find(hill, cap, ChapterGrounds.spec(Chapters.WHALE), List.of());
            check(Math.hypot(wh3.center().getX(), wh3.center().getZ() + 440) > ChapterGrounds.STRUCTURE_GAP, "the whale ground should move off a village: " + wh3.center());
            // 峰 (西 1,300 に y 175) → 形なし
            Synth peak = new Synth((x, z) -> 70 + (int) Math.max(0, 105 - Math.hypot(x + 1300, z) * 0.6));
            Found st = ChapterGrounds.find(peak, cap, ChapterGrounds.spec(Chapters.STORM), List.of());
            check(st.shape() == Shape.NONE && st.center().getY() >= ChapterGrounds.PEAK_Y, "the storm ground should take the peak in the west: " + st.center());
            // 灰の地 (乾いた群系) → 帯の内側の端
            Synth dry = new Synth((x, z) -> 72);
            dry.dry = true;
            Found em = ChapterGrounds.find(dry, cap, ChapterGrounds.spec(Chapters.EMBER), List.of());
            check(em.shape() == Shape.NONE && em.dist() == 1200 && em.deg() == 0, "dry lowland: the ember ground should be at the inner edge, due south: " + em.dist() + " " + em.deg());
            // 海辺 (x 1,000 から東が深さ 20 の海) → 形なし、浜は海の手前
            Synth coast = new Synth((x, z) -> x < 1000 ? 66 : 43);
            Found td = ChapterGrounds.find(coast, cap, ChapterGrounds.spec(Chapters.TIDE), List.of());
            check(td.shape() == Shape.NONE && td.center().getX() < 1000 && td.center().getX() >= 1000 - ChapterGrounds.SHORE_REACH - 8 && td.sea() != null && td.sea().getX() > 1000,
                    "the tide ground should take the beach before the sea: " + td.center() + " sea " + td.sea());

            // 形を重ねた高さ
            Found fh = ChapterGrounds.find(flat, cap, ChapterGrounds.spec(Chapters.WHALE), List.of());
            SiteFinder.Ground ov = ChapterGrounds.overlay(flat, List.of(fh));
            check(ov.top(fh.shapeCenter().getX(), fh.shapeCenter().getZ()) == 70 + ChapterGrounds.HILL_RISE, "the hill should rise 24 at its centre");
            check(ov.top(fh.shapeCenter().getX() + 60, fh.shapeCenter().getZ()) == 70, "outside the hill the ground stays");
            Found fp = ChapterGrounds.find(flat, cap, ChapterGrounds.spec(Chapters.STORM), List.of());
            int pt = ChapterGrounds.overlay(flat, List.of(fp)).top(fp.shapeCenter().getX(), fp.shapeCenter().getZ());
            check(pt >= 70 + ChapterGrounds.PEAK_RISE - 8 && pt <= 70 + ChapterGrounds.PEAK_RISE, "the peak should rise about 60: " + pt);
            // 峰の縁は切り立った壁にならない: 元の地面が形の中心より 40 低い列でも、縁 (半径の 98%) の段差は小さい
            SiteData.Ground pg = new SiteData.Ground("t", fp.center());
            pg.shape = Shape.PEAK.name();
            pg.shapeCenter = fp.shapeCenter();
            pg.baseY = 110;
            int edge = ChapterGrounds.shapeTop(pg, fp.shapeCenter().getX() + (int) (ChapterGrounds.PEAK_HALF * 0.98), fp.shapeCenter().getZ(), 70, 63);
            check(edge != Integer.MIN_VALUE && edge - 70 <= 6, "the peak skirt should meet the lower ground without a wall: " + edge);
            Found fb = ChapterGrounds.find(flat, cap, ChapterGrounds.spec(Chapters.EMBER), List.of());
            // 盆地の底は 8 下 (ただし海面 + 1 より下へは掘らない)
            int floor = ChapterGrounds.overlay(flat, List.of(fb)).top(fb.shapeCenter().getX(), fb.shapeCenter().getZ());
            check(floor == Math.max(70 - ChapterGrounds.BASIN_DEPTH, 64), "the basin floor is 8 down, not below the sea + 1: " + floor);
            Synth highDry = new Synth((x, z) -> 90);
            Found fb2 = ChapterGrounds.find(highDry, cap, ChapterGrounds.spec(Chapters.EMBER), List.of());
            check(ChapterGrounds.overlay(highDry, List.of(fb2)).top(fb2.shapeCenter().getX(), fb2.shapeCenter().getZ()) == 90 - ChapterGrounds.BASIN_DEPTH,
                    "on high ground the basin is 8 deep");
            Found fc = ChapterGrounds.find(flat, cap, ChapterGrounds.spec(Chapters.TIDE), List.of());
            SiteFinder.Ground ovc = ChapterGrounds.overlay(flat, List.of(fc));
            check(fc.fixedRest() && fc.sea() != null, "a dug cove has a fixed rest spot");
            check(!ovc.wet(fc.center().getX(), fc.center().getZ()), "the beach centre of the cove is dry");
            // 巨神の体の箱 (横 ±67・前 87・後ろ 59) の縁がみな水 (深さ 6 以上)
            if (fc.sea() != null) {
                double yaw = Math.toRadians(fc.restYaw());
                double fx = -Math.sin(yaw);
                double fz = Math.cos(yaw);
                boolean allDeep = true;
                for (double[] q : new double[][] {{-67, -59}, {67, -59}, {-67, 87}, {67, 87}, {0, -59}, {0, 87}, {-67, 14}, {67, 14}, {-33, -32}, {-60, -32}}) {
                    int x = fc.sea().getX() + (int) Math.round(fx * q[1] + fz * q[0]);
                    int z = fc.sea().getZ() + (int) Math.round(fz * q[1] - fx * q[0]);
                    if (ovc.top(x, z) > 63 - 6) {
                        allDeep = false;
                        F.add("the cove is shallow under the titan at " + x + "," + z + " (" + ovc.top(x, z) + ")");
                    }
                }
                check(allDeep, "the titan's body fits in the cove");
                // 潮門 (左) が浜の側
                double lx = Math.cos(yaw);
                double lz = Math.sin(yaw);
                double bx = fc.center().getX() - fc.sea().getX();
                double bz = fc.center().getZ() - fc.sea().getZ();
                check(lx * bx + lz * bz > 0, "the tide gate should face the beach");
            }

            // 道しるべ: 40 ごと、跡地の手前で止まる。浅い川は石の台で渡し、深い水の点は飛ばす
            List<BlockPos> road = ChapterGrounds.road(flat, cap, new BlockPos(0, 70, -1000), 0);
            check(road.size() >= 19 && road.size() <= 21, "the road to 1000 should have about 20 waymarks: " + road.size());
            for (int i = 1; i < road.size(); i++) {
                double d = Math.hypot(road.get(i).getX() - road.get(i - 1).getX(), road.get(i).getZ() - road.get(i - 1).getZ());
                check(d >= 30 && d <= 50, "waymarks " + (i - 1) + "-" + i + " are " + (int) d + " apart");
            }
            Synth river = new Synth((x, z) -> z <= -400 && z >= -440 ? 55 : 70);
            List<BlockPos> wet = ChapterGrounds.road(river, cap, new BlockPos(0, 70, -1000), 0);
            // 浅い川 (深さ 8) の点は飛ばさず、線の上に石の台で渡す (海面 + 1)。深い湖 (深さ 30) の点だけ飛ばす
            check(wet.size() == road.size(), "the shallow river should be bridged, not skipped: " + wet.size() + " of " + road.size());
            check(wet.stream().filter(p -> p.getZ() <= -400 && p.getZ() >= -440).allMatch(p -> p.getX() == 0 && p.getY() == 64)
                    && wet.stream().anyMatch(p -> p.getZ() <= -400 && p.getZ() >= -440), "the river waymark should stand on the line at the water");
            Synth lake = new Synth((x, z) -> z <= -400 && z >= -440 ? 33 : 70);
            List<BlockPos> deep = ChapterGrounds.road(lake, cap, new BlockPos(0, 70, -1000), 0);
            check(deep.stream().noneMatch(p -> p.getZ() <= -400 && p.getZ() >= -440) && deep.size() < road.size(), "no waymark on the deep lake");
            Synth swamp = new Synth((x, z) -> z <= -400 && z >= -440 ? 63 : 70);
            List<BlockPos> low = ChapterGrounds.road(swamp, cap, new BlockPos(0, 70, -1000), 0);
            check(low.size() == road.size(), "a sea-level flat should still get its waymark: " + low.size());
            List<BlockPos> whaleRoad = ChapterGrounds.road(flat, cap, new BlockPos(0, 70, -300), ChapterGrounds.WHALE_CAIRNS);
            check(whaleRoad.size() >= ChapterGrounds.WHALE_CAIRNS, "the whale road should fit the 7 cairns even on a short band: " + whaleRoad.size());
            // 祠は平らな所 (斜面のそばでも 9×9 が 3 以内)
            Synth slope = new Synth((x, z) -> 70 + Math.max(0, x) / 2);
            BlockPos shrine = ChapterGrounds.shrineNear(slope, new BlockPos(0, 70, 0), 16, List.of());
            SiteFinder.Area a = SiteFinder.area(slope, shrine.getX(), shrine.getZ(), 4, 4, 3, false, true);
            check(a.ok(), "the shrine spot should be flat: " + shrine);

            // 地図: 章が開いた人に噂、近くで見た、まだの章は出ない
            SiteData.Ground g = SiteData.newGround(Chapters.TIDE.id(), new BlockPos(1000, 64, 0));
            UUID me = UUID.randomUUID();
            List<MapMarks.Mark> marks = new ArrayList<>();
            MapMarks.groundMarks(List.of(g), me, Chapters.WHALE, null, BlockPos.ZERO, marks);
            check(marks.isEmpty(), "the tide ground should not show before chapter 2");
            MapMarks.groundMarks(List.of(g), me, Chapters.TIDE, null, BlockPos.ZERO, marks);
            check(marks.size() == 1 && marks.get(0).rumor() && marks.get(0).kind() == MapMarks.Kind.SITE, "chapter 2 should hear a rumour of the east shore: " + marks);
            marks.clear();
            MapMarks.groundMarks(List.of(g), me, Chapters.TIDE, new BlockPos(1010, 64, 20), BlockPos.ZERO, marks);
            check(marks.size() == 1 && !marks.get(0).rumor() && marks.get(0).x() == 1000, "near the shore the mark is seen: " + marks);
            marks.clear();
            g.seen.add(me);
            MapMarks.groundMarks(List.of(g), me, Chapters.TIDE, null, BlockPos.ZERO, marks);
            check(marks.size() == 1 && !marks.get(0).rumor(), "once seen, the mark stays seen");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter grounds crashed", e);
            F.add("pure crashed: " + e);
        }
        SkyAbyss.LOGGER.info("[grounds] pure checks: {} failures so far", F.size());
    }

    // ---------------------------------------------------------------- 本物の世界
    private static FakePlayer fake(MinecraftServer server) {
        return FakePlayerFactory.get(server.overworld(), new GameProfile(FAKE, "skyabyss_w2_grounds"));
    }

    // 二〜四章を開く (その章の跡地を整え、小要素と道しるべを置く)。一章は目覚めの前に整っている
    static void open(MinecraftServer server) {
        waited = 0;
        SiteData data = SiteData.get(server.overworld());
        for (SiteData.Ground g : data.grounds()) {
            SkyAbyss.LOGGER.info("[grounds] {} centre {} shape {} at {} base {} shaped {} shrine {} sea {} road {}", g.chapter, g.center.toShortString(), g.shape,
                    g.shapeCenter.toShortString(), g.baseY, g.shaped, g.shrine == null ? "-" : g.shrine.toShortString(), g.sea == null ? "-" : g.sea.toShortString(),
                    g.road.size());
        }
        UUID id = fake(server).getUUID();
        for (Chapters ch : List.of(Chapters.WHALE, Chapters.TIDE, Chapters.STORM, Chapters.EMBER)) {
            ChapterSites.requestGround(server, id, ch);
        }
    }

    static void live(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            SiteData data = SiteData.get(level);
            BlockPos cap = OpeningSites.shelter(server).orElse(null);
            check(cap != null, "no capital");
            check(data.groundsPlanned() && data.grounds().size() == 4, "four grounds should be planned (" + data.grounds().size() + ")");
            if (cap == null) {
                return;
            }
            // 序盤の場所と重ならない
            BlockPos camp = OpeningSites.camp(server).orElse(cap);
            for (Spec s : ChapterGrounds.SPECS) {
                SiteData.Ground g = data.ground(s.chapter().id());
                check(g != null, s.chapter() + ": no ground");
                if (g == null) {
                    continue;
                }
                Shape shape = ChapterGrounds.shapeOf(g);
                BlockPos where = shape == Shape.COVE ? g.shapeCenter : g.center;
                check(inSector(cap, where, s, 6, 24), s.chapter() + ": the ground " + where + " is outside the sector or band");
                check(g.shaped, s.chapter() + ": the ground was not shaped");
                check(g.shrine != null && ChapterSites.horizontal(g.shrine, g.center) <= 160, s.chapter() + ": shrine spot missing or far: " + g.shrine);
                check(ChapterSites.horizontal(g.center, camp) >= ChapterGrounds.CAMP_GAP - 2 || s.chapter() != Chapters.WHALE, "the whale ground is too near the camp");
                check(ChapterGrounds.shrineSpot(server, s.chapter().titan()).isPresent(), s.chapter() + ": shrineSpot is empty");
                check(g.road.size() >= (s.chapter() == Chapters.WHALE ? ChapterGrounds.WHALE_CAIRNS - 2 : 3), s.chapter() + ": too few waymarks (" + g.road.size() + ")");
                // 整えた地形 (形の中心の列を見る)
                if (g.shaped && shape != Shape.NONE) {
                    BlockPos c = g.shapeCenter;
                    level.getChunk(c.getX() >> 4, c.getZ() >> 4);
                    int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, c.getX(), c.getZ()) - 1;
                    BlockState surface = level.getBlockState(new BlockPos(c.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ()) - 1, c.getZ()));
                    switch (shape) {
                        case HILL -> check(top >= g.baseY + ChapterGrounds.HILL_RISE - 2, "the hill was not raised: " + top + " base " + g.baseY);
                        case PEAK -> check(top >= g.baseY + ChapterGrounds.PEAK_RISE - 10, "the peak was not raised: " + top + " base " + g.baseY);
                        case BASIN -> check(top <= ChapterGrounds.basinFloor(g.baseY, level.getSeaLevel()) + 1, "the basin was not dug: " + top);
                        case COVE -> {
                            check(surface.is(Blocks.WATER) && top <= level.getSeaLevel() - ChapterGrounds.COVE_FLOOR + 1, "the cove has no deep water: " + surface + " floor " + top);
                            // 入り江の水が漏れない (縁の外の 1 列の海面の下は固い)
                            int ex = g.shapeCenter.getX() + ChapterGrounds.COVE_HALF + 1;
                            level.getChunk(ex >> 4, c.getZ() >> 4);
                            BlockState edge = level.getBlockState(new BlockPos(ex, level.getSeaLevel() - 4, c.getZ()));
                            check(!edge.isAir() && edge.getFluidState().isEmpty() || level.getBlockState(new BlockPos(ex, level.getSeaLevel() - 1, c.getZ())).is(Blocks.WATER) && g.baseY < level.getSeaLevel(),
                                    "the cove's outer wall is open: " + edge);
                        }
                        default -> {
                        }
                    }
                }
                // 小要素: 跡地の要素は中心から 150 以内、世界に一つずつ。道の要素は道の上
                for (ChapterSites.Element el : ChapterSites.groundElements(s.chapter())) {
                    int n = 0;
                    for (SiteData.Entry e : data.allEntries()) {
                        if (e.siteId.equals(el.id())) {
                            n++;
                            if (el.placement() == ChapterSites.Placement.GROUND) {
                                check(ChapterSites.horizontal(e.pos, g.center) <= 152, el.id() + " is " + (int) ChapterSites.horizontal(e.pos, g.center) + " from the ground centre");
                            }
                            check(e.built, el.id() + "#" + e.index + " was not built at " + e.pos);
                        }
                    }
                    int want = el.placement() == ChapterSites.Placement.ROAD_FILL ? Math.max(0, g.road.size() - roadNumbered(s.chapter())) : el.rules().count();
                    // 道しるべは水たまりなどで建てられず諦める点がある (8 割あればよい)。番号つき・跡地の要素は一つずつ
                    boolean fill = el.placement() == ChapterSites.Placement.ROAD_FILL;
                    check(fill ? n >= want * 4 / 5 && n <= want : n == want, el.id() + " exists " + n + " times (expected " + want + ")");
                }
            }
            // 一章の骨は北の丘の中
            BlockPos ribs = OpeningSites.ribs(server).orElse(null);
            SiteData.Ground wg = data.ground(Chapters.WHALE.id());
            check(ribs != null && wg != null && ChapterSites.horizontal(ribs, wg.center) <= 152, "the rib bones should lie in the north hills: " + ribs);
            if (ribs != null && wg != null && wg.shrine != null) {
                check(ChapterSites.horizontal(ribs, wg.shrine) >= SiteFinder.RIB_HALF_LONG, "the whale shrine pad should be beside the ribs, not on them");
            }
            // 潮殻の浜: 掘った入り江なら決まった休む場所、自然の海辺なら海の点が水
            SiteData.Ground tg = data.ground(Chapters.TIDE.id());
            if (tg != null && tg.sea != null) {
                level.getChunk(tg.sea.getX() >> 4, tg.sea.getZ() >> 4);
                BlockState s = level.getBlockState(new BlockPos(tg.sea.getX(), level.getSeaLevel() - 2, tg.sea.getZ()));
                check(s.is(Blocks.WATER), "the tide rest point is not water: " + s + " at " + tg.sea);
                check(TideBeach.restricted(server) && TideBeach.beach(server) != null, "the tide landing should be limited to the beach");
                if (tg.fixedRest) {
                    check(TideBeach.restSpot(level) != null, "a dug cove gives a rest spot");
                }
            }
            // 雷翼の止まり木は西の峰、余燼の浮上は南の灰の地だけ (33 の 3.1)。遠い人は近いとみなさない。峰の頂は中心のそばで、乾いた高い所
            FakePlayer far = fake(server);
            far.setPos(cap.getX() + 0.5, 100, cap.getZ() + 0.5);
            SiteData.Ground sg = data.ground(Chapters.STORM.id());
            if (sg != null) {
                check(jp.revati.skyabyss.titan.storm.StormPeak.restricted(server) && !jp.revati.skyabyss.titan.storm.StormPeak.near(far),
                        "the thunder wing should perch only on the west peak");
                for (int dx = -64; dx <= 64; dx += 16) {
                    for (int dz = -64; dz <= 64; dz += 16) {
                        level.getChunk((sg.center.getX() + dx) >> 4, (sg.center.getZ() + dz) >> 4);
                    }
                }
                jp.revati.skyabyss.titan.storm.StormPerch.Peak perch = jp.revati.skyabyss.titan.storm.StormPeak.perch(level);
                check(perch != null && Math.hypot(perch.x() - sg.center.getX(), perch.z() - sg.center.getZ()) <= jp.revati.skyabyss.titan.storm.StormPeak.SEARCH + 1,
                        "the west peak should give a perch near its centre: " + perch);
            }
            SiteData.Ground eg = data.ground(Chapters.EMBER.id());
            if (eg != null) {
                check(jp.revati.skyabyss.titan.ember.EmberBasin.restricted(server) && !jp.revati.skyabyss.titan.ember.EmberBasin.near(far),
                        "the ember should surface only in the south ash land");
            }
            // 地図: 二章の人に東の浜の噂
            FakePlayer p = fake(server);
            List<MapMarks.Mark> marks = new ArrayList<>();
            MapMarks.groundMarks(data.grounds(), UUID.randomUUID(), Chapters.TIDE, null, cap, marks);
            check(marks.size() == 2 && marks.stream().filter(MapMarks.Mark::rumor).count() == 1, "a chapter-2 player sees the north hills and hears of the east shore: " + marks);
            int leaves = 0;
            if (wg != null && ChapterGrounds.shapeOf(wg) == Shape.HILL) {
                // 盛った丘の上に浮いた葉が残らない (中心の 9 列)
                for (int dx = -8; dx <= 8; dx += 8) {
                    for (int dz = -8; dz <= 8; dz += 8) {
                        int x = wg.shapeCenter.getX() + dx;
                        int z = wg.shapeCenter.getZ() + dz;
                        BlockState top = level.getBlockState(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z));
                        if (top.is(BlockTags.LEAVES)) {
                            leaves++;
                        }
                    }
                }
            }
            check(leaves <= 2, "floating leaves on the raised hill (" + leaves + ")");
            p.discard();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter grounds crashed", e);
            F.add("live crashed: " + e);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST chapter grounds PASSED (bearing and band search, four shapes, overlay heights, road waymarks, shrine spots, ribs in the north hills, "
                    + "ground sites once each within 150, the tide beach, map rumours)");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST chapter grounds FAILED: {}", m));
        }
    }

    private static int roadNumbered(Chapters ch) {
        int n = 0;
        for (ChapterSites.Element e : ChapterSites.groundElements(ch)) {
            if (e.placement() == ChapterSites.Placement.ROAD) {
                n += e.rules().count();
            }
        }
        return n;
    }
}
