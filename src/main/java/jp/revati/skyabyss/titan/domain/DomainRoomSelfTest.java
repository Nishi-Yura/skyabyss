package jp.revati.skyabyss.titan.domain;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BuildAuditSelfTest;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.heart.EmberHeart;
import jp.revati.skyabyss.titan.ember.heart.EmberHeartArena;
import jp.revati.skyabyss.titan.ember.heart.EmberHeartSetup;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.heart.StormHeart;
import jp.revati.skyabyss.titan.storm.heart.StormHeartArena;
import jp.revati.skyabyss.titan.storm.heart.StormHeartSetup;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import jp.revati.skyabyss.titan.tide.heart.TideHeartArena;
import jp.revati.skyabyss.titan.tide.heart.TideHeartSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 領域の仕掛け (骨・水門・柱・核) が、本物の心臓の間で「壁の中でなく、歩いて届く所」に立つかの検査 (docs/design/02 の 5.9)。
// 四体それぞれ、本物の背の部屋に心臓を置いて (世界には入れない) 領域を作り、仕掛けの置き場を 40 回引いて確かめる。
//  壁の中でない = 当たりの箱 (幅 1.4・高さ 2.2) が空いていて、足もとが固い。歩いて届く = 背の到達の調べ (BackReach) の歩ける所に近い (横 2・縦 1 以内)
// SKYABYSS_SELFTEST_ONLY="domain room"。背が建ててあること (建築の監査の後ろに置く)
public final class DomainRoomSelfTest {
    private static final int DRAWS = 40;

    private DomainRoomSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("domain room", DomainRoomSelfTest::run));
        return steps;
    }

    private record Room(String name, ResourceKey<Level> key, Mob heart, java.util.function.Supplier<HeartDomain> domain, AABB arena) {
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            for (String name : List.of("whale", "tide", "storm", "ember")) {
                check(server, name, f, notes);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST domain room crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST domain room PASSED ({})", String.join("; ", notes));
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST domain room FAILED: {}", m));
        }
    }

    private static Room room(MinecraftServer server, String name) {
        switch (name) {
            case "whale": {
                ServerLevel level = server.getLevel(TitanTravel.TITAN_BACK);
                StoneWhaleHeart h = ModEntities.STONE_WHALE_HEART.get().create(level);
                h.moveTo(TitanBackLayout.HEART_CX + 0.5, TitanBackLayout.FLOOR + 1, TitanBackLayout.HEART_CZ + 0.5, 0f, 0f);
                h.bindArena(TitanBackTrials.heartArena(), 1);
                return new Room(name, TitanTravel.TITAN_BACK, h, h::domain, TitanBackTrials.heartArena());
            }
            case "tide": {
                ServerLevel level = server.getLevel(TideTravel.TIDE_BACK);
                TideHeart h = TideHeartSetup.TIDE_HEART.get().create(level);
                Vec3 home = TideHeartArena.heartHome();
                h.moveTo(home.x, home.y, home.z, 0f, 0f);
                h.bindArena(TideHeartArena.arena(), 1);
                return new Room(name, TideTravel.TIDE_BACK, h, h::domain, TideHeartArena.arena());
            }
            case "storm": {
                ServerLevel level = server.getLevel(StormTravel.STORM_BACK);
                StormHeart h = StormHeartSetup.STORM_HEART.get().create(level);
                Vec3 c = StormHeartArena.center();
                h.moveTo(c.x, StormHeartArena.FLOOR + 0.5, c.z, 180f, 0f);
                h.bindArena(StormHeartArena.arena(), 1);
                return new Room(name, StormTravel.STORM_BACK, h, h::domain, StormHeartArena.arena());
            }
            default: {
                ServerLevel level = server.getLevel(EmberIds.BACK);
                EmberHeart h = EmberHeartSetup.EMBER_HEART.get().create(level);
                Vec3 home = EmberHeartArena.heartHome();
                h.moveTo(home.x, home.y, home.z, 180f, 0f);
                h.bindArena(EmberHeartArena.arena(), 1);
                return new Room(name, EmberIds.BACK, h, h::domain, EmberHeartArena.arena());
            }
        }
    }

    private static void start(Room r, ServerLevel level) {
        if (r.heart() instanceof StoneWhaleHeart h) {
            h.startDomain(level);
        } else if (r.heart() instanceof TideHeart h) {
            h.startDomain(level);
        } else if (r.heart() instanceof StormHeart h) {
            h.startDomain(level);
        } else if (r.heart() instanceof EmberHeart h) {
            h.startDomain(level);
        }
    }

    private static void check(MinecraftServer server, String name, List<String> f, List<String> notes) {
        Room r = room(server, name);
        ServerLevel level = server.getLevel(r.key());
        if (level == null) {
            f.add(name + ": back dimension is missing");
            return;
        }
        start(r, level);
        HeartDomain d = r.domain().get();
        if (d == null) {
            f.add(name + ": the heart made no domain");
            return;
        }
        BackReach.Report reach = BuildAuditSelfTest.routeOf(name, level);
        LongOpenHashSet walk = reach.reached;
        int bad = 0;
        int far = 0;
        int total = 0;
        double maxFromCenter = 0;
        for (int i = 0; i < DRAWS; i++) {
            List<Vec3> spots = d.previewNodes(level);
            if (spots.size() < HeartDomain.NODES) {
                f.add(name + ": the domain planned only " + spots.size() + " nodes");
                break;
            }
            for (Vec3 p : spots) {
                total++;
                AABB box = new AABB(p.x - 0.7, p.y, p.z - 0.7, p.x + 0.7, p.y + 2.2, p.z + 0.7);
                BlockPos under = BlockPos.containing(p.x, p.y - 0.1, p.z);
                boolean free = level.noCollision(box);
                boolean solid = !level.getBlockState(under).getCollisionShape(level, under).isEmpty();
                boolean inArena = r.arena().contains(p.x, p.y + 1, p.z);
                if (!free || !solid || !inArena) {
                    if (bad++ < 3) {
                        f.add(name + ": a gimmick would stand inside a wall or outside the room at " + p + " (free " + free + ", floor " + solid + ", in room " + inArena + ")");
                    }
                    continue;
                }
                if (walk != null && !near(walk, p)) {
                    if (far++ < 3) {
                        f.add(name + ": a gimmick at " + p + " is not reachable on foot (no BackReach standing cell within 2 across / 1 up)");
                    }
                }
                maxFromCenter = Math.max(maxFromCenter, Math.hypot(p.x - d.centerX(), p.z - d.centerZ()));
            }
        }
        // 巡: 通常 2・深淵 3・4 人以上で 3。巡が進むほど手本は長く、雷は速く、核は熱を取り戻すのが速い
        if (name.equals("whale")) {
            int[] seq = StoneSeaDomain.SEQ_LEN;
            for (int i = 1; i < seq.length; i++) {
                if (seq[i] <= seq[i - 1]) {
                    f.add("whale: the bone sequence does not get longer each round");
                }
            }
            double[] charge = ThunderCageDomain.CHARGE_SCALE;
            int[] reheat = HearthCoreDomain.REHEAT;
            for (int i = 1; i < HeartDomain.roundsFor(true, 1); i++) {
                if (charge[i] >= charge[i - 1]) {
                    f.add("storm: the strike window does not get shorter each round");
                }
                if (reheat[i] < reheat[i - 1] || (i == reheat.length - 1 && reheat[i] <= reheat[0])) {
                    f.add("ember: the cores do not re-heat faster each round");
                }
            }
            if (HeartDomain.roundsFor(false, 1) != 2 || HeartDomain.roundsFor(false, 3) != 2 || HeartDomain.roundsFor(false, 4) != 3
                    || HeartDomain.roundsFor(true, 1) != 3) {
                f.add("the round counts are not 2 (solo) / 3 (4+ players or abyss)");
            }
        }
        d.abort(level);
        notes.add(String.format(java.util.Locale.ROOT, "%s %d draws, %d walled, %d unreachable, farthest %.1f from center", name, total, bad, far, maxFromCenter));
    }

    private static boolean near(LongOpenHashSet walk, Vec3 p) {
        int px = (int) Math.floor(p.x);
        int py = (int) Math.floor(p.y);
        int pz = (int) Math.floor(p.z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (walk.contains(BlockPos.asLong(px + dx, py + dy, pz + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
