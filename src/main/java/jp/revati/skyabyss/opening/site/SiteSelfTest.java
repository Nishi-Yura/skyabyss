package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSpawner;
import jp.revati.skyabyss.npc.NpcWorldData;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.OpeningSites.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 序盤の構造物の自己テスト (docs/design/17 の 4 章)。新しいワールドで 4 つが必ず建つ、スポーンからの距離、陸の上、
// 宿りに鐘 (POI) があって語り部が立てる (村のない世界でも)、足跡のくぼみ、骨と石板の碑の幅、人の建物があれば場所をずらして壊さない。
// 結果は SELFTEST opening PASSED / FAILED
public final class SiteSelfTest {
    private static int waited;

    private SiteSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.until("opening (wait)", server -> {
            waited++;
            return (!SiteBuilds.busy() && !SiteBuilds.planning()) || waited > 12000;
        }));
        steps.add(Step.once("opening (load)", SiteSelfTest::load));
        steps.add(Step.once("opening", 30, SiteSelfTest::run));
        steps.add(Step.once("opening (conflict)", SiteSelfTest::conflict));
        // 章の小要素の共通基盤 (ChapterSites)。SKYABYSS_SELFTEST_ONLY="chapter sites" でこれだけ流せる
        steps.addAll(ChapterSitesSelfTest.steps());
        return steps;
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    // 調べる場所のチャンクを読み込む
    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!SiteBuilds.enabled(server)) {
            return;
        }
        for (OpeningSites.Site s : OpeningSites.all(server)) {
            if (s.kind() != Kind.RIBS) {
                level.getChunk(s.pos().getX() >> 4, s.pos().getZ() >> 4);
            }
        }
        OpeningSites.ribs(server).ifPresent(p -> {
            for (int dx = -64; dx <= 64; dx += 16) {
                for (int dz = -64; dz <= 64; dz += 16) {
                    level.getChunk((p.getX() + dx) >> 4, (p.getZ() + dz) >> 4);
                }
            }
        });
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            if (!SiteBuilds.enabled(server)) {
                SkyAbyss.LOGGER.info("SELFTEST opening PASSED (skipped: disabled by the environment)");
                return;
            }
            runChecks(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST opening crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST opening PASSED (planned, four sites built, spawn at the camp, distances, on land, bell and storyteller, footprints, ribs, slab tablet)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST opening FAILED: {}", m));
        }
    }

    // 足跡の場所探しは、どんな地形でも必ず 1 つは決める (first_hour_log B-6)。合成の地形: 平ら・起伏が激しい・斑の池・ほぼ海の小島。
    // 宿りがないとき (仮の宿りの向き) も
    private static void footprintFallback(List<String> f) {
        String[] names = {"flat", "rugged", "ponds", "islets"};
        for (int kind = 0; kind < names.length; kind++) {
            final int k = kind;
            SiteFinder.Ground g = new SiteFinder.Ground() {
                int h(int x, int z) {
                    int cx = Math.floorDiv(x, 8);
                    int cz = Math.floorDiv(z, 8);
                    int hash = (cx * 73856093) ^ (cz * 19349663);
                    hash = (hash ^ (hash >>> 13)) * 1274126177;
                    int r = Math.floorMod(hash ^ (hash >>> 16), 100);
                    return switch (k) {
                        case 0 -> 70;
                        case 1 -> 64 + r * 3;
                        case 2 -> (Math.floorMod(cx / 6 + cz / 6, 3) == 0) ? 55 : 70 + r / 20;
                        default -> (Math.floorMod(cx / 5 + cz / 5, 4) == 0) ? 66 + r / 10 : 40;
                    };
                }

                @Override
                public int top(int x, int z) {
                    return h(x, z);
                }

                @Override
                public int topCoarse(int x, int z) {
                    return top(Math.floorDiv(x + 4, 8) * 8, Math.floorDiv(z + 4, 8) * 8);
                }

                @Override
                public boolean wet(int x, int z) {
                    return top(x, z) < 62;
                }

                @Override
                public boolean wetCoarse(int x, int z) {
                    return topCoarse(x, z) < 62;
                }

                @Override
                public boolean biomeOk(int x, int z) {
                    return Math.floorMod(Math.floorDiv(x, 64) + Math.floorDiv(z, 64), 5) != 0;
                }

                @Override
                public int sea() {
                    return 62;
                }
            };
            int plans = 0;
            for (int i = 0; i < 12; i++) {
                BlockPos camp = new BlockPos(i * 301 - 1500, 80, i * -177 + 400);
                // 宿りが建った世界と、宿りが見つからなかった世界 (仮の宿り)
                for (BlockPos shelter : new BlockPos[] {camp.offset(170, 0, 40), camp.offset(180, 0, 0)}) {
                    List<SiteFinder.Planned> out = new ArrayList<>();
                    SiteFinder.planFootprints(g, out, camp, shelter);
                    long n = out.stream().filter(p -> p.kind().equals(OpeningSites.Kind.FOOTPRINT.id)).count();
                    if (n < 1) {
                        // 起伏・水が全面でも、中心が乾いた所が一つもない地形ではないはず (どの合成にも乾いた所はある)
                        f.add("footprint fallback: " + names[k] + " camp " + camp + " shelter " + shelter + ": no footprint planned");
                    }
                    plans++;
                }
            }
            check(f, plans == 24, "footprint fallback: " + names[k] + ": " + plans + " plans");
        }
    }

    private static boolean solidBelow(ServerLevel level, BlockPos stand) {
        BlockState below = level.getBlockState(stand.below());
        return !below.getCollisionShape(level, stand.below()).isEmpty() && below.getFluidState().isEmpty();
    }

    // 足跡の「中」の判定は、円ではなく見える足の形 (爪先まで含む)。向き 8 通り
    private static void footprintShape(List<String> f) {
        BlockPos origin = new BlockPos(1000, 64, -500);
        for (String name : new String[] {"footprint", "footprint_end"}) {
            Blueprint bp = Blueprint.get(name);
            for (int yaw = 0; yaw < 8; yaw++) {
                int inside = 0;
                int far = 0;
                for (int[] r : bp.relief) {
                    if (r[2] > 1) {
                        continue;
                    }
                    BlockPos w = Blueprint.transform(r[0], 0, r[1], SiteFinder.rot(yaw), SiteFinder.mirror(yaw), origin);
                    if (!FootprintShape.contains(name, origin, yaw, w, 3)) {
                        inside++;
                    }
                    if (OpeningSites.horizontal(w, origin) > 24) {
                        far++;
                    }
                }
                check(f, inside == 0, name + " yaw " + yaw + ": " + inside + " dug columns count as outside the footprint");
                check(f, far > 0, name + ": no dug column is farther than the old 24 radius (the test would prove nothing)");
                // 見える足の形から 3 ブロックより遠い点は外 (どの向きでも)
                for (int[] r : bp.relief) {
                    if (r[2] > 1) {
                        continue;
                    }
                    BlockPos w = Blueprint.transform(r[0], 0, r[1], SiteFinder.rot(yaw), SiteFinder.mirror(yaw), origin);
                    BlockPos out = w.offset(0, 0, 8);
                    boolean reallyOutside = true;
                    for (int[] q : bp.relief) {
                        if (q[2] <= 1 && Math.abs(Blueprint.transform(q[0], 0, q[1], SiteFinder.rot(yaw), SiteFinder.mirror(yaw), origin).getX() - out.getX()) <= 4
                                && Math.abs(Blueprint.transform(q[0], 0, q[1], SiteFinder.rot(yaw), SiteFinder.mirror(yaw), origin).getZ() - out.getZ()) <= 4) {
                            reallyOutside = false;
                            break;
                        }
                    }
                    if (reallyOutside) {
                        check(f, !FootprintShape.contains(name, origin, yaw, out, 3), name + " yaw " + yaw + ": a point 8+ blocks from the footprint counts as inside");
                        break;
                    }
                }
            }
        }
    }

    private static void runChecks(MinecraftServer server, List<String> f) {
        footprintShape(f);
        footprintFallback(f);
        ServerLevel level = server.overworld();
        check(f, OpeningSites.planned(server), "sites are not planned");
        check(f, OpeningSites.allBuilt(server), "not every site is built: " + OpeningSites.all(server));
        BlockPos camp = OpeningSites.camp(server).orElse(null);
        BlockPos shelter = OpeningSites.shelter(server).orElse(null);
        BlockPos ribs = OpeningSites.ribs(server).orElse(null);
        List<BlockPos> prints = OpeningSites.footprints(server);
        check(f, camp != null, "no camp");
        check(f, shelter != null, "no shelter");
        check(f, ribs != null, "no ribs");
        check(f, prints.size() >= 1 && prints.size() <= 4, "footprints: " + prints.size());
        if (prints.size() < 4) {
            SkyAbyss.LOGGER.warn("[opening] only {} footprints (the land beside the road is hard)", prints.size());
        }
        if (camp == null || shelter == null || ribs == null) {
            return;
        }
        // スポーンが野営地 (新しいワールドで計画したときだけ動かす。前から遊んでいるワールドでは動かさない)
        BlockPos spawn = level.getSharedSpawnPos();
        if (SiteData.get(level).plannedAt() < SiteBuilds.NEW_WORLD_TICKS) {
            check(f, OpeningSites.horizontal(spawn, camp) <= 6, "spawn " + spawn + " is not at the camp " + camp);
        }
        // 距離
        double ds = OpeningSites.horizontal(camp, shelter);
        double dr = OpeningSites.horizontal(camp, ribs);
        // 天と淵の大陸: 野営地ははじまりの島、宿り (都) は海の向こう。足跡は島の道 (野営地 → 船着き場) に沿う
        boolean continent = jp.revati.skyabyss.world.continent.ContinentPois.in(level);
        check(f, continent || ds >= 100 && ds <= 480, "shelter distance " + ds);  // 都: 260〜340、見つからなければ 240〜480 (SiteFinder.findShelter)
        // 骨: 一章の跡地 (北の丘。docs/design/33 の 3 章) があればその中、なければ野営地から 250〜350 (宿りと違う方角)
        SiteData.Ground whale = SiteData.get(level).ground(jp.revati.skyabyss.story.Chapters.WHALE.id());
        if (whale != null) {
            check(f, OpeningSites.horizontal(whale.center, ribs) <= 152, "ribs are " + (int) OpeningSites.horizontal(whale.center, ribs) + " from the north hills");
        } else {
            check(f, dr >= 200 && dr <= 380, "ribs distance " + dr);
        }
        SkyAbyss.LOGGER.info("[opening] camp {} shelter {} ({} blocks) ribs {} ({} blocks) footprints {}", camp, shelter, (int) ds, ribs, (int) dr, prints);
        double ang = Math.abs(Math.toDegrees(Math.atan2(shelter.getZ() - camp.getZ(), shelter.getX() - camp.getX())
                - Math.atan2(ribs.getZ() - camp.getZ(), ribs.getX() - camp.getX())));
        ang = ang > 180 ? 360 - ang : ang;
        check(f, whale != null || ang >= 80, "ribs and shelter are only " + (int) ang + " degrees apart");
        // 足跡: 道から 30〜60 (余裕を見て 24〜70)
        BlockPos to = continent ? jp.revati.skyabyss.world.continent.ContinentLayout.get().dock() : shelter;
        double dt = OpeningSites.horizontal(camp, to);
        for (BlockPos p : prints) {
            double d = SiteFinder.distToPath(p.getX(), p.getZ(), camp, (to.getX() - camp.getX()) / dt, (to.getZ() - camp.getZ()) / dt, dt);
            // 大陸の島の道は曲がっているので、まっすぐな線からは近いことも遠いこともある (0〜160)
            check(f, (continent ? d >= 0 : d >= 20) && d <= 160, "footprint " + p + " is " + (int) d + " from the road");
            // くぼみ: 中心の列の、いちばん上の固い地面 (木の葉は数えない) が、場所の高さ (地面の中央値) より 2 以上低い
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 1;
            check(f, surfaceY <= p.getY() - 2, "footprint " + p + " is not a pit (the surface is at " + surfaceY + ")");
        }
        // 一歩の間隔 (96 ブロック前後。間の足跡が建てられず抜けたときは 2 歩分)
        for (int i = 1; i < prints.size(); i++) {
            double step = OpeningSites.horizontal(prints.get(i - 1), prints.get(i));
            check(f, step >= 60 && step <= 240, "footprint step " + i + " is " + (int) step + " blocks");
        }
        // 立つ場所は陸の上
        BlockPos campStand = site(server, Kind.CAMP, "stand", camp);
        check(f, solidBelow(level, campStand) && level.getBlockState(campStand).isAir(), "camp stand " + campStand + " is not on solid ground");
        BlockPos shelterStand = site(server, Kind.SHELTER, "stand", shelter);
        check(f, solidBelow(level, shelterStand) && level.getBlockState(shelterStand).isAir(), "shelter stand " + shelterStand + " is not on solid ground (" + level.getBlockState(shelterStand) + " above " + level.getBlockState(shelterStand.below()) + ")");
        // 帳面と樽
        BlockPos nb = OpeningSites.notebook(server).orElse(null);
        check(f, nb != null && !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(nb).inflate(2),
                e -> e.getTags().contains(CampBuilder.NOTEBOOK_TAG)).isEmpty(), "no notebook item at " + nb);
        // 鐘と語り部
        BlockPos bell = OpeningSites.bell(server).orElse(null);
        check(f, bell != null && level.getBlockState(bell).is(Blocks.BELL), "no bell at " + bell);
        if (bell != null) {
            boolean poi = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), bell, 3, PoiManager.Occupancy.ANY).isPresent();
            check(f, poi, "the bell is not a meeting POI");
            NpcEntity npc = NpcSpawner.spawnStoryteller(level, bell);
            check(f, npc != null, "the storyteller cannot stand near the bell");
            if (npc != null) {
                npc.discard();
            }
            // 目的の帯の目印: 語り部は鐘、鯨追いは鐘から離れた自分の焚き火 (別の場所)。建物ごとに別の目印
            check(f, bell.equals(jp.revati.skyabyss.opening.OpeningDirector.target(level, "ask_storyteller")), "ask_storyteller target is not the bell");
            NpcEntity chaser = NpcSpawner.spawnChaser(level, bell, level.random);
            check(f, chaser != null, "the whale chaser cannot stand near the lodge");
            if (chaser != null) {
                chaser.discard();
            }
            // 鯨追いの焚き火の記録 (立たせたときに残す) が目印になる。鐘の位置にはならない
            BlockPos fire = bell.offset(20, 0, 5);
            NpcWorldData.get(server.overworld()).markChaserFire(bell.asLong(), fire.asLong());
            BlockPos t = jp.revati.skyabyss.opening.OpeningDirector.target(level, "find_isana");
            check(f, fire.equals(t), "find_isana target " + t + " is not the recorded chaser fire " + fire + " (bell " + bell + ")");
            check(f, !bell.equals(t) && !bell.equals(jp.revati.skyabyss.opening.OpeningDirector.target(level, "find_isana")), "find_isana points at the bell");
        }
        check(f, OpeningSites.smoke(server).map(p -> level.getBlockState(p).is(Blocks.CAMPFIRE)).orElse(false), "no smoking campfire");
        // 骨: 骨のブロックが十分あり、石板の碑に文がある
        int bones = 0;
        for (int x = -56; x <= 56; x++) {
            for (int z = -56; z <= 56; z++) {
                for (int y = -14; y <= 46; y++) {
                    if (level.getBlockState(ribs.offset(x, y, z)).is(Blocks.BONE_BLOCK)) {
                        bones++;
                    }
                }
            }
        }
        check(f, bones >= 1500, "only " + bones + " bone blocks at the ribs");
        BlockPos slab = OpeningSites.slab(server).orElse(null);
        check(f, slab != null && level.getBlockEntity(slab) instanceof jp.revati.skyabyss.tablet.TabletBlockEntity tablet && tablet.key().equals(SiteKit.SLAB_SIGN), "no slab tablet at " + slab);
        check(f, slab != null && !jp.revati.skyabyss.tablet.Tablets.text(SiteKit.SLAB_SIGN).getString().isEmpty(), "slab tablet has no text");
        check(f, SignFit.check(List.of(SiteKit.SLAB_SIGN)).isEmpty(), "slab sign does not fit: " + SignFit.check(List.of(SiteKit.SLAB_SIGN)));
    }

    private static BlockPos site(MinecraftServer server, Kind kind, String extra, BlockPos fallback) {
        SiteData.Entry e = SiteData.get(server.overworld()).first(kind.id);
        BlockPos p = e == null ? null : e.extra(extra);
        return p == null ? fallback : p;
    }

    // 人の建物の上には建てない: 板の床と箱のある所に小さな石を頼むと、壊さずにずれる (あきらめる)
    static void conflict(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            if (!SiteBuilds.enabled(server) || OpeningSites.camp(server).isEmpty()) {
                return;
            }
            ServerLevel level = server.overworld();
            BlockPos camp = OpeningSites.camp(server).get();
            SiteFinder.Probe probe = new SiteFinder.Probe(level);
            BlockPos at = null;
            for (int r = 40; r <= 120 && at == null; r += 8) {
                for (int k = 0; k < 8 && at == null; k++) {
                    double a = Math.PI * 2 * k / 8;
                    int x = camp.getX() + (int) Math.round(Math.cos(a) * r);
                    int z = camp.getZ() + (int) Math.round(Math.sin(a) * r);
                    if (SiteFinder.area(probe, x, z, 3, 3, 2).ok()) {
                        at = new BlockPos(x, probe.top(x, z), z);
                    }
                }
            }
            if (at == null) {
                SkyAbyss.LOGGER.info("SELFTEST opening (conflict) skipped: no land near the camp");
                return;
            }
            level.getChunk(at.getX() >> 4, at.getZ() >> 4);
            SiteData data = SiteData.get(level);
            SiteData.Entry probeEntry = new SiteData.Entry("ch1_road_cairn", 99, new BlockPos(at.getX(), at.getY() + 1, at.getZ()), 0);
            SiteJob job = new SiteJob(level, data, probeEntry, Blueprint.get("ch1_road_cairn"), "conflict probe", j -> {
            });
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
            BlockPos orig = probeEntry.pos;
            job.blocking = true;
            int guard = 0;
            while (!job.step(System.nanoTime() + 200_000_000L) && guard++ < 200) {
                // 進める
            }
            check(f, level.getBlockState(chest).is(Blocks.CHEST), "the chest was destroyed");
            for (BlockPos p : made) {
                check(f, level.getBlockState(p).is(Blocks.OAK_PLANKS), "the plank floor at " + p + " was destroyed");
            }
            boolean moved = !probeEntry.pos.equals(orig) || !probeEntry.built;
            check(f, moved, "the site was built on top of a player's building");
            // 片付ける
            level.setBlock(chest, Blocks.AIR.defaultBlockState(), 2 | 16);
            for (BlockPos p : made) {
                level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2 | 16);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST opening (conflict) crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST opening (conflict) PASSED (a player's building is not built over)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST opening (conflict) FAILED: {}", m));
        }
    }
}
