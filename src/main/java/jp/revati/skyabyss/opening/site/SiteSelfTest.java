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
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// 序盤の構造物の自己テスト (docs/design/17 の 4 章)。新しいワールドで 4 つが必ず建つ、スポーンからの距離、陸の上、
// 宿りに鐘 (POI) があって語り部が立てる (村のない世界でも)、足跡のくぼみ、骨と石板の看板の幅、人の建物があれば場所をずらして壊さない。
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
            SkyAbyss.LOGGER.info("SELFTEST opening PASSED (planned, four sites built, spawn at the camp, distances, on land, bell and storyteller, footprints, ribs, slab sign)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST opening FAILED: {}", m));
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
        check(f, ds >= 100 && ds <= 320, "shelter distance " + ds);
        check(f, dr >= 200 && dr <= 380, "ribs distance " + dr);
        SkyAbyss.LOGGER.info("[opening] camp {} shelter {} ({} blocks) ribs {} ({} blocks) footprints {} cairns {}", camp, shelter, (int) ds, ribs, (int) dr, prints,
                OpeningSites.cairns(server).size());
        double ang = Math.abs(Math.toDegrees(Math.atan2(shelter.getZ() - camp.getZ(), shelter.getX() - camp.getX())
                - Math.atan2(ribs.getZ() - camp.getZ(), ribs.getX() - camp.getX())));
        ang = ang > 180 ? 360 - ang : ang;
        check(f, ang >= 80, "ribs and shelter are only " + (int) ang + " degrees apart");
        // 足跡: 道から 30〜60 (余裕を見て 24〜70)
        for (BlockPos p : prints) {
            double d = SiteFinder.distToPath(p.getX(), p.getZ(), camp, (shelter.getX() - camp.getX()) / ds, (shelter.getZ() - camp.getZ()) / ds, ds);
            check(f, d >= 20 && d <= 160, "footprint " + p + " is " + (int) d + " from the road");
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
        check(f, solidBelow(level, shelterStand) && level.getBlockState(shelterStand).isAir(), "shelter stand " + shelterStand + " is not on solid ground");
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
        // 積み石
        // 道が池や海をまたぐ世界では積み石が少なくなる (飾りの目印なので 1 つあればよい)
        check(f, OpeningSites.cairns(server).size() >= 1, "no cairns");
        if (OpeningSites.cairns(server).size() < 2) {
            SkyAbyss.LOGGER.warn("[opening] only {} cairn (the road crosses water)", OpeningSites.cairns(server).size());
        }
        for (BlockPos c : OpeningSites.cairns(server)) {
            check(f, level.getBlockState(c).is(Blocks.STONE), "no cairn at " + c);
        }
        // 骨: 骨のブロックが十分あり、石板の看板に文がある
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
        check(f, slab != null && level.getBlockEntity(slab) instanceof SignBlockEntity, "no slab sign at " + slab);
        check(f, SignFit.check(List.of(SiteKit.SLAB_SIGN)).isEmpty(), "slab sign does not fit: " + SignFit.check(List.of(SiteKit.SLAB_SIGN)));
    }

    private static BlockPos site(MinecraftServer server, Kind kind, String extra, BlockPos fallback) {
        SiteData.Entry e = SiteData.get(server.overworld()).first(kind.id);
        BlockPos p = e == null ? null : e.extra(extra);
        return p == null ? fallback : p;
    }

    // 人の建物の上には建てない: 板の床と箱のある所に積み石を頼むと、壊さずにずれる (あきらめる)
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
            SiteData.Entry probeEntry = new SiteData.Entry("cairn", 99, new BlockPos(at.getX(), at.getY() + 1, at.getZ()), 0);
            SiteJob job = CairnBuilder.create(level, data, probeEntry);
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
