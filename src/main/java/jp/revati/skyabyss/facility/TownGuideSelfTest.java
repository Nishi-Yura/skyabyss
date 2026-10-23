package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.nav.LevelTerrain;
import jp.revati.skyabyss.guide.nav.PathFinder;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.opening.site.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

// 都の案内 (TownGuide・案内人) の自己テスト。段: "town guide"。
//   印: 都の設計図に案内人の印が五つ (四つの門 + 広場) あり、役は案内人。一覧の施設はどれも戸の印がある
//   道: 設計図の上で (建てた都と同じ形)、どの案内人からも、どの施設の戸まで歩ける道がある (着く)。道は大通りを好む (屋根の下を通るのは少し)
//   選ぶ: 選んだときの知らせ (Path) が戸と道を持ち、灯りが 3 ブロックごとに戸まで並ぶ。送る形の読み書きが元に戻る
//   会話: 案内人の会話の「行き先を選ぶ」が施設の働き guide を開き、その働きが登録されている
public final class TownGuideSelfTest {
    private TownGuideSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("town guide", TownGuideSelfTest::run));
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    // 設計図のブロックだけの地形 (設計図に無い所は、地面 y <= -1 が固く、上は空気)
    static final class BlueprintTerrain implements PathFinder.Terrain {
        private final Map<Long, BlockState> cells = new HashMap<>();
        private final ServerLevel level;

        BlueprintTerrain(Blueprint bp, ServerLevel level) {
            this.level = level;
            for (Blueprint.Cell c : bp.cells) {
                cells.put(BlockPos.asLong(c.x(), c.y(), c.z()), c.state());
            }
        }

        @Override
        public PathFinder.Cell cell(int x, int y, int z) {
            BlockState s = cells.get(BlockPos.asLong(x, y, z));
            if (s == null) {
                return y <= -1 ? PathFinder.Cell.SOLID : PathFinder.Cell.AIR;
            }
            return LevelTerrain.classify(s, level, BlockPos.ZERO);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        Blueprint bp = TownGuide.blueprint();
        // ---- 印
        List<String> guides = new ArrayList<>();
        for (String k : bp.marks.keySet()) {
            if (k.startsWith(TownGuide.GUIDE_MARK)) {
                guides.add(k);
                check(f, AttendantRole.byMark(k) == AttendantRole.GUIDE, "guide mark role " + k);
                check(f, Attendants.ours(k), "attendants read " + k);
            }
        }
        for (String need : List.of("n", "s", "e", "w", "plaza")) {
            check(f, bp.marks.containsKey(TownGuide.GUIDE_MARK + need), "guide mark " + need);
        }
        check(f, AttendantRole.byMark("npc_guide") == AttendantRole.GUIDE && AttendantRole.byMark("npc_smith") == AttendantRole.SMITH, "role by mark");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (TownGuide.Place p : TownGuide.PLACES) {
            check(f, ids.add(p.id()), "place id once " + p.id());
            check(f, bp.marks.containsKey(p.mark()), "door mark " + p.mark() + " for " + p.id());
        }
        for (TownGuide.Group g : TownGuide.Group.values()) {
            check(f, TownGuide.PLACES.stream().anyMatch(p -> p.group() == g), "group has places " + g.id);
        }
        // ---- 道 (設計図の上で、案内人から施設の戸まで)
        BlueprintTerrain terrain = new BlueprintTerrain(bp, server.overworld());
        int routes = 0;
        int roofed = 0;
        int steps = 0;
        long worst = 0;
        TownGuideNetwork.Path sample = null;
        for (String g : guides) {
            int[] gm = bp.marks.get(g);
            BlockPos from = new BlockPos(gm[0], gm[1], gm[2]);
            for (TownGuide.Place p : TownGuide.PLACES) {
                int[] dm = bp.marks.get(p.mark());
                if (dm == null) {
                    continue;
                }
                BlockPos door = new BlockPos(dm[0], dm[1], dm[2]);
                long t0 = System.nanoTime();
                PathFinder.Result r = TownGuide.route(terrain, from, door);
                worst = Math.max(worst, System.nanoTime() - t0);
                check(f, r.reached(), "route " + g + " -> " + p.id() + " (best " + (r.nodes().isEmpty() ? "none" : r.nodes().get(r.nodes().size() - 1)) + ")");
                if (!r.reached()) {
                    continue;
                }
                routes++;
                for (BlockPos b : r.nodes()) {
                    steps++;
                    for (int dy = 2; dy <= 7; dy++) {
                        if (terrain.cell(b.getX(), b.getY() + dy, b.getZ()) == PathFinder.Cell.SOLID) {
                            roofed++;
                            break;
                        }
                    }
                }
                if (g.endsWith("plaza") && p.id().equals("tool_shop")) {
                    sample = TownGuide.plan(terrain, from, p, door);
                }
            }
        }
        check(f, routes == guides.size() * TownGuide.PLACES.size(), "all routes reached " + routes + "/" + guides.size() * TownGuide.PLACES.size());
        // 屋根の下 (門の口・アーケード・戸口) を通るのは道の 1 割まで
        check(f, steps == 0 || roofed * 10 <= steps, "routes stay outdoors (" + roofed + "/" + steps + " roofed)");
        // ---- 選ぶ: 知らせと灯り
        if (sample == null) {
            f.add("no sample path plaza -> tool_shop");
        } else {
            List<BlockPos> n = sample.nodes();
            check(f, n.size() >= 2 && sample.reached() && sample.id().equals("tool_shop") && sample.group() == TownGuide.Group.GOLD.ordinal(), "path payload " + sample.id());
            BlockPos end = n.get(n.size() - 1);
            double dx = end.getX() - sample.door().getX();
            double dz = end.getZ() - sample.door().getZ();
            check(f, dx * dx + dz * dz <= 2.5 * 2.5, "path ends at the door " + end + " vs " + sample.door());
            List<TownGuide.Lantern> ls = TownGuide.lanterns(n);
            check(f, ls.size() >= 3, "lanterns " + ls.size());
            boolean even = true;
            for (int i = 1; i < ls.size() - 1; i++) {
                double gap = ls.get(i).s() - ls.get(i - 1).s();
                even &= Math.abs(gap - TownGuide.LANTERN_SPACING) < 1.0e-6;
            }
            check(f, even, "lanterns every " + TownGuide.LANTERN_SPACING);
            TownGuide.Lantern last = ls.get(ls.size() - 1);
            check(f, Math.abs(last.x() - (end.getX() + 0.5)) + Math.abs(last.z() - (end.getZ() + 0.5)) < 3.5, "last lantern near the door");
            // 送る形
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            TownGuideNetwork.Path.STREAM_CODEC.encode(buf, sample);
            TownGuideNetwork.Path back = TownGuideNetwork.Path.STREAM_CODEC.decode(buf);
            check(f, back.equals(sample), "path codec round trip");
            buf.release();
            List<BlockPos> line = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(3, 0, 0), new BlockPos(3, 0, 1));
            List<BlockPos> simple = TownGuide.simplify(line);
            check(f, simple.get(0).equals(line.get(0)) && simple.get(simple.size() - 1).equals(line.get(4)) && simple.contains(new BlockPos(3, 0, 0))
                    && simple.size() < line.size(), "simplify keeps ends and turns " + simple);
        }
        // ---- 会話
        Dialogue d = DialogueManager.dialogue(AttendantRole.GUIDE.dialogueRole());
        check(f, d != null, "guide dialogue");
        check(f, FacilityActions.OPENS.contains("guide"), "facility open guide");
        if (d != null) {
            Dialogue.Node menu = d.nodes().get("menu");
            check(f, menu != null && menu.options().stream().anyMatch(o -> o.id().equals("way")), "guide menu has the way");
        }
        // ---- 建てた都があれば (この世界では建てていないことが多い): 世界の印と案内人の所
        if (jp.revati.skyabyss.opening.OpeningSites.built(server, jp.revati.skyabyss.opening.OpeningSites.Kind.SHELTER)) {
            check(f, TownGuide.guideSpots(server).size() == guides.size(), "world guide spots");
            check(f, TownGuide.doors(server).size() == TownGuide.PLACES.size(), "world doors " + TownGuide.doors(server).size());
        }
        SkyAbyss.LOGGER.info("[SELFTEST] town guide: {} guides x {} places, {} routes, roofed {}/{}, slowest {} ms", guides.size(), TownGuide.PLACES.size(),
                routes, roofed, steps, worst / 1_000_000);
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST town guide PASSED (guide marks at 4 gates + plaza, every facility reachable from every guide, path payload + lanterns, dialogue)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST town guide FAILED: {}", m));
        }
    }
}
