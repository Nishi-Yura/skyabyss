package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.mobs.AbyssScaling;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Gimmick;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Kind;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Platform;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Rare;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Section;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

// SELFTEST abyss depths (docs/design/36 の 9)。区画の決まり・道の届き・型ごとの形・仕掛けの組み合わせ・深さの数字・節目・上乗せ・建てる。
//   純粋な計算 (決まり・道・節目・上乗せ) と、型ごとの区画の並び (block の置く一覧)、最後に区画 1 をサーバーに建てて確かめる。
public final class DepthSelfTest {
    static final long SEED = 20261022L;

    private DepthSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            pure(f);
            layouts(f);
            serverPart(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss depths crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss depths PASSED (determinism, 8 section kinds reachable top to bottom over 200 sections, 8 gimmicks and 4 modifiers, combos grow with depth, depth numbers, milestones, deep scaling capped, 8 section layouts with route and rift, rare rooms with chests, section 1 built on the server)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss depths FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 純粋な計算
    static void pure(List<String> f) {
        check(f, Kind.values().length >= 6, "fewer than 6 section kinds");
        check(f, Kind.values().length == 8, "the section kinds are not the 8 decided ones");
        check(f, Gimmick.values().length >= 8, "fewer than 8 gimmicks");
        check(f, DepthPlan.Modifier.values().length >= 4, "fewer than 4 modifiers");

        // 同じ種と番号は同じ区画。違う番号は (多くの場合) 違う
        check(f, DepthPlan.plan(SEED, 3).equals(DepthPlan.plan(SEED, 3)), "the same seed and index give different sections");
        check(f, !DepthPlan.plan(SEED, 3).equals(DepthPlan.plan(SEED + 1, 3)) || !DepthPlan.plan(SEED, 4).equals(DepthPlan.plan(SEED + 1, 4)),
                "another world seed gives the same sections");
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        Set<List<Gimmick>> gimmickSets = new HashSet<>();
        int unreachable = 0;
        int firstBad = -1;
        for (int i = 1; i <= 200; i++) {
            Section s = DepthPlan.plan(SEED, i);
            kinds.add(s.kind());
            gimmickSets.add(s.gimmicks());
            int bad = DepthPlan.firstUnreachable(DepthPlan.route(s));
            if (bad >= 0) {
                unreachable++;
                if (firstBad < 0) {
                    firstBad = i;
                }
            }
        }
        check(f, kinds.size() == Kind.values().length, "not every section kind appears in 200 sections: " + kinds.size());
        check(f, unreachable == 0, "a route is cut in section " + firstBad + " (" + unreachable + " sections)");
        check(f, gimmickSets.size() >= 20, "the gimmick combinations do not vary enough: " + gimmickSets.size());
        check(f, DepthPlan.routeCount() >= 60, "the route is too short");

        // 深いほど仕掛けと修飾が多い
        check(f, DepthPlan.plan(SEED, 1).gimmicks().size() == 1, "section 1 does not have one gimmick");
        check(f, DepthPlan.plan(SEED, 3).gimmicks().size() == 2, "section 3 does not have two gimmicks");
        check(f, DepthPlan.plan(SEED, 5).gimmicks().size() == 3, "section 5 does not have three gimmicks");
        check(f, DepthPlan.plan(SEED, 9).gimmicks().size() == 3 && DepthPlan.plan(SEED, 9).modifiers().size() == 2,
                "the deepest combos are not 3 gimmicks and 2 modifiers");
        check(f, DepthPlan.plan(SEED, 1).modifiers().isEmpty(), "section 1 has a modifier");
        int rare = 0;
        for (int i = 2; i <= 200; i++) {
            if (DepthPlan.plan(SEED, i).rare() != Rare.NONE) {
                rare++;
            }
        }
        check(f, rare >= 10 && rare <= 80, "rare sections are not occasional: " + rare + " of 199");

        // 深さの数字
        check(f, DepthPlan.beyond(1, DepthPlan.TOP_Y) == 0, "the top of section 1 is not depth 0");
        check(f, DepthPlan.beyond(1, DepthPlan.TOP_Y - 100) == 100, "depth 100 in section 1 is wrong");
        check(f, DepthPlan.beyond(2, DepthPlan.TOP_Y) == DepthPlan.HEIGHT, "the top of section 2 does not continue the depth");
        check(f, DepthPlan.totalDepth(0) == DepthPlan.HEART_DEPTH, "the heart floor is not the total depth 540");
        check(f, DepthPlan.totalDepth(DepthPlan.beyond(2, DepthPlan.TOP_Y - 50)) > DepthPlan.totalDepth(DepthPlan.beyond(1, DepthPlan.TOP_Y - 199)),
                "the total depth does not keep counting across sections");
        check(f, DepthRun.sectionOf(new BlockPos(DepthPlan.SLOT, DepthPlan.TOP_Y - 20, 0)) == 1, "a block in section 1 is not section 1");
        check(f, DepthRun.sectionOf(new BlockPos(0, 60, 0)) == 0, "the shaft is taken for a section");
        check(f, DepthRun.sectionOf(new BlockPos(DepthPlan.SLOT / 2 + 40, DepthPlan.TOP_Y - 20, 0)) == 0, "the gap between sections is taken for a section");

        // 節目 (心臓の下の 100・250・500…)
        check(f, DepthRecords.milestoneCount(0, 99) == 0, "milestone 100 is given too early");
        check(f, DepthRecords.milestoneCount(0, 100) == 1, "milestone 100 is not given");
        check(f, DepthRecords.milestoneCount(0, 600) == 3, "milestones 100, 250, 500 are not all counted at 600");
        check(f, DepthRecords.milestoneCount(3, 600) == 3, "a passed milestone is counted again");
        check(f, DepthRecords.milestoneCount(0, 100000) == DepthRecords.MILESTONES.length, "milestones do not stop at the last one");

        // 深さの上乗せ (心臓の下・上限あり)
        check(f, AbyssScaling.deepSteps(0) == 0, "deep steps are not 0 at the heart floor");
        check(f, AbyssScaling.deepSteps(10000) == AbyssScaling.DEEP_CAP, "deep steps do not stop at the cap");
        double base = AbyssScaling.hp(20, -1300);
        check(f, AbyssScaling.hpDeep(20, -1300, 0) == base, "the deep hp is not the base at 0 steps");
        check(f, AbyssScaling.hpDeep(20, -1300, 8) > base && AbyssScaling.hpDeep(20, -1300, 8) <= base * 2.2 + 1e-9, "the deep hp is not capped at 2.2 times");
        check(f, AbyssScaling.dmgDeep(5, -1300, 8) <= AbyssScaling.dmg(5, -1300) * 1.64 + 1e-9, "the deep damage is not capped");
    }

    // ---------------------------------------------------------------- 型ごとの区画の並び
    static void layouts(List<String> f) {
        Map<Kind, Section> first = new HashMap<>();
        for (int i = 1; i <= 200 && first.size() < Kind.values().length; i++) {
            Section s = DepthPlan.plan(SEED, i);
            first.putIfAbsent(s.kind(), s);
        }
        for (Map.Entry<Kind, Section> e : first.entrySet()) {
            DepthLayout.Result g = DepthLayout.build(e.getValue());
            Map<BlockPos, BlockState> grid = new HashMap<>();
            for (DepthLayout.Put p : g.puts()) {
                grid.put(new BlockPos(p.x(), p.y(), p.z()), p.state());
            }
            String k = e.getKey().name();
            int cx = e.getValue().centerX();
            for (Platform p : g.route()) {
                BlockPos c = new BlockPos(cx + (int) Math.round(p.dx()), DepthPlan.TOP_Y - p.ly(), (int) Math.round(p.dz()));
                BlockState st = grid.get(c);
                check(f, st != null && !st.isAir(), k + ": the route platform " + p.k() + " has no block");
            }
            check(f, grid.get(g.entryFeet().below()) != null && !grid.get(g.entryFeet().below()).isAir(), k + ": the entry has no floor");
            check(f, grid.get(g.entryFeet()) == null || grid.get(g.entryFeet()).isAir(), k + ": the entry is blocked");
            check(f, grid.get(g.hole()) == null || grid.get(g.hole()).isAir(), k + ": the rift hole is not open");
            check(f, grid.get(g.lantern()) != null && grid.get(g.lantern()).getBlock() == Blocks.CANDLE, k + ": the lantern is missing");
            check(f, grid.get(g.exit()) != null && grid.get(g.exit()).getBlock() == Blocks.CHAIN, k + ": the return chain is missing");
            check(f, g.lamps().size() >= 8, k + ": too few lamps: " + g.lamps().size());
            int walls = 0;
            boolean inside = true;
            for (DepthLayout.Put p : g.puts()) {
                if (p.y() > DepthPlan.TOP_Y + 0 || p.y() < DepthPlan.TOP_Y - DepthPlan.HEIGHT + 1) {
                    inside = false;
                }
                if (Math.abs(p.x() - cx) > DepthPlan.ROOM_R || Math.abs(p.z()) > DepthPlan.ROOM_R) {
                    inside = false;
                }
                if (p.state().equals(g.palette().wall())) {
                    walls++;
                }
            }
            check(f, inside, k + ": a block is placed outside the room");
            check(f, walls > 1000, k + ": the walls are missing");
        }
        // 珍しい部屋には箱がある (中身の表つき)
        int loot = 0;
        int lootTables = 0;
        for (int i = 2; i <= 200 && loot < 3; i++) {
            Section s = DepthPlan.plan(SEED, i);
            if (s.rare() == Rare.NONE) {
                continue;
            }
            DepthLayout.Result g = DepthLayout.build(s);
            for (DepthLayout.Put p : g.puts()) {
                if (p.loot() != null) {
                    loot++;
                    if (p.loot().equals(BuiltInLootTables.ANCIENT_CITY) || p.loot().equals(BuiltInLootTables.SIMPLE_DUNGEON)
                            || p.loot().equals(BuiltInLootTables.END_CITY_TREASURE)) {
                        lootTables++;
                    }
                }
            }
        }
        check(f, loot >= 2 && lootTables == loot, "the rare rooms do not have chests with loot tables: " + loot);
    }

    // ---------------------------------------------------------------- サーバー (建てる・門)
    static void serverPart(MinecraftServer server, List<String> f) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        check(f, level != null, "the abyss dimension is missing");
        if (level == null) {
            return;
        }
        long t0 = System.nanoTime();
        DepthBuilder.finishNow(server, 1);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        check(f, DepthBuilder.built(server, 1), "section 1 is not built");
        DepthLayout.Result g = DepthBuilder.geometry(server, 1);
        BlockState floor = level.getBlockState(g.entryFeet().below());
        check(f, !floor.isAir(), "section 1 has no floor at the entry");
        check(f, level.getBlockState(g.hole()).isAir(), "section 1 has a block in the rift hole");
        check(f, level.getBlockState(g.lantern()).getBlock() == Blocks.CANDLE, "section 1 has no lantern");
        check(f, DepthRun.sectionOf(g.entryFeet()) == 1, "the entry is not in section 1");
        // 門: 番人を倒していないときは裂け目は開かない (番人の倒れた印が先)
        if (!jp.revati.skyabyss.story.ch5.warden.WardenArena.cleared(server)) {
            check(f, !DepthRun.riftOpen(server), "the rift is open before the warden is cleared");
        }
        SkyAbyss.LOGGER.info("SELFTEST abyss depths: section 1 built in {} ms", ms);
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
