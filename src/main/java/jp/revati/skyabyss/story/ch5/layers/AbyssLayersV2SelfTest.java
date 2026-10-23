package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssEntrance;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss layers v2: 深淵の層 (docs/design/36 の 8 章。2026-10-23 の作り直し)。SKYABYSS_SELFTEST_ONLY="abyss layers v2"
//   形: 設計図の上で歩いて (戸・封・門は開いたものとして、下りの鎖は次の野営へ)、着く所から各層の三つの印・門の向こう・主の間・下りの鎖・心臓まで届く。
//       近道の格子を閉じたままでも届き、開けると印の部屋から野営へ歩いて帰れる
//   印: 巣を払う前の印は取れない。取るとパーティの二人とも持つ。三つで門を通れ、二つでは通れない
//   段: 二人のパーティで、門 → 骨の野営 (話す) → 印三つ → 骨の主 → 塩の野営 と進む (仲間は離れていても進む)
//   仕掛け: 三つの灯皿に光の術の灯りで骨の戸が開く。灰の迷路の跡が出口まで求まる
public final class AbyssLayersV2SelfTest {
    static final String[] OPENABLE = {"door_bone", "hid_bone", "door_storm_0", "door_storm_1", "door_storm_2", "seal_ash"};

    private AbyssLayersV2SelfTest() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        hold(level, true);
        if (!AbyssBuild.built(server)) {
            AbyssBuild.finishNow(server);
        }
    }

    private static void hold(ServerLevel level, boolean on) {
        for (int cx = -5; cx <= 4; cx++) {
            for (int cz = -5; cz <= 4; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        try {
            layout(f);
            if (level != null) {
                marks(f, server, level);
                puzzles(f, level);
            } else {
                f.add("the abyss dimension is missing");
            }
            sections(f, server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss layers v2 crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (level != null) {
                hold(level, false);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss layers v2 PASSED (layout: every mark, gate, arena, descent and the heart reached from the arrival; "
                    + "shortcuts lead back to camp; marks guarded by nests; party-shared marks; three marks open the gate; shortcut bars gone from the world; the bar gate rises for three marks and falls when left; "
                    + "party of two advances bone camp → marks → lord → salt camp; braziers open the bone door; ash maze trail)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss layers v2 FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 形 (設計図の上で歩く)
    static void layout(List<String> f) {
        Blueprint bp = Blueprint.get(AbyssBuild.BLUEPRINT);
        Map<Long, BlockState> b = new HashMap<>(bp.cells.size() * 2);
        for (Blueprint.Cell c : bp.cells) {
            b.put(BlockPos.asLong(c.x(), c.y(), c.z()), c.state());
        }
        Set<Long> open = new HashSet<>();
        for (String o : OPENABLE) {
            box(open, o);
        }
        for (String layer : Layers.ORDER) {
            BlockPos g = AbyssBuild.mark("gate_" + layer);
            if (g == null) {
                f.add("gate_" + layer + " is missing");
                continue;
            }
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 2; y++) {
                    open.add(BlockPos.asLong(g.getX() + x, g.getY() + y, g.getZ()));
                    open.add(BlockPos.asLong(g.getX() + x, g.getY() + y, g.getZ() - 1));
                }
            }
        }
        Map<Long, BlockPos> links = new HashMap<>();
        for (int i = 0; i < Layers.ORDER.size(); i++) {
            String layer = Layers.ORDER.get(i);
            String next = i + 1 < Layers.ORDER.size() ? "arrive_" + Layers.ORDER.get(i + 1) : "heart_in";
            link(links, "descend_" + layer, next);
        }
        link(links, "well_salt", "well_salt_top");
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival == null) {
            f.add("arrival is missing");
            return;
        }
        Set<Long> seen = walk(b, open, links, arrival);
        List<String> must = new ArrayList<>(List.of("heart", "heart_in", "rest_5"));
        for (String layer : Layers.ORDER) {
            for (int i = 1; i <= 3; i++) {
                must.add("mark_" + layer + "_" + i);
            }
            must.addAll(List.of("gate_" + layer + "_in", "arena_" + layer, "descend_" + layer, "survivor_" + layer, "pool_" + layer));
        }
        for (String m : must) {
            if (!reached(seen, AbyssBuild.mark(m))) {
                f.add(m + " is not reached on foot from the arrival (nearest walked " + nearest(seen, AbyssBuild.mark(m)) + ")");
            }
        }
        // 近道を開けると、印の部屋から野営へ歩いて帰れる
        Set<Long> withScuts = new HashSet<>(open);
        for (String layer : Layers.ORDER) {
            for (int i = 1; i <= 3; i++) {
                box(withScuts, "scut_" + layer + "_" + i);
            }
        }
        for (String layer : Layers.ORDER) {
            for (int i = 1; i <= 3; i++) {
                BlockPos room = AbyssBuild.mark("markroom_" + layer + "_" + i);
                if (room == null) {
                    f.add("markroom_" + layer + "_" + i + " is missing");
                    continue;
                }
                Set<Long> back = walk(b, withScuts, Map.of(), room);
                if (!reached(back, AbyssBuild.mark("camp_" + layer))) {
                    f.add("no shortcut back to camp from " + layer + " mark room " + i);
                }
            }
        }
    }

    static String nearest(Set<Long> seen, BlockPos m) {
        if (m == null) {
            return "-";
        }
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (long k : seen) {
            BlockPos p = BlockPos.of(k);
            double d = p.distSqr(m);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best + " d=" + Math.round(Math.sqrt(bd));
    }

    static void box(Set<Long> out, String name) {
        BlockPos a = AbyssBuild.mark(name + "_a");
        BlockPos c = AbyssBuild.mark(name + "_b");
        if (a == null || c == null) {
            return;
        }
        for (BlockPos q : BlockPos.betweenClosed(a, c)) {
            out.add(q.asLong());
        }
    }

    static void link(Map<Long, BlockPos> links, String from, String to) {
        BlockPos a = AbyssBuild.mark(from);
        BlockPos c = AbyssBuild.mark(to);
        if (a != null && c != null) {
            links.put(a.asLong(), c);
        }
    }

    static boolean reached(Set<Long> seen, BlockPos m) {
        if (m == null) {
            return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (seen.contains(BlockPos.asLong(m.getX() + dx, m.getY() + dy, m.getZ() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean pass(Map<Long, BlockState> b, Set<Long> open, int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        if (open.contains(k)) {
            return true;
        }
        BlockState s = b.get(k);
        return s != null && !s.blocksMotion();
    }

    static boolean step(Map<Long, BlockState> b, int x, int y, int z) {
        BlockState s = b.get(BlockPos.asLong(x, y, z));
        return s != null && (s.getBlock() instanceof StairBlock || s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM);
    }

    static boolean stand(Map<Long, BlockState> b, Set<Long> open, int x, int y, int z) {
        if (step(b, x, y, z) && pass(b, open, x, y + 1, z) && pass(b, open, x, y + 2, z)) {
            return true;
        }
        if (!pass(b, open, x, y, z) || !pass(b, open, x, y + 1, z)) {
            return false;
        }
        BlockState below = b.get(BlockPos.asLong(x, y - 1, z));
        return below != null && below.blocksMotion() && !open.contains(BlockPos.asLong(x, y - 1, z));
    }

    static Set<Long> walk(Map<Long, BlockState> b, Set<Long> open, Map<Long, BlockPos> links, BlockPos start) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start.asLong());
        q.add(start);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            for (Map.Entry<Long, BlockPos> e : links.entrySet()) {
                BlockPos l = BlockPos.of(e.getKey());
                if (Math.abs(l.getX() - c.getX()) <= 2 && Math.abs(l.getZ() - c.getZ()) <= 2 && Math.abs(l.getY() - c.getY()) <= 1
                        && seen.add(e.getValue().asLong())) {
                    q.add(e.getValue());
                }
            }
            for (int[] d : dirs) {
                int nx = c.getX() + d[0], nz = c.getZ() + d[1];
                for (int ny : new int[] {c.getY(), c.getY() + 1, c.getY() - 1, c.getY() - 2, c.getY() - 3}) {
                    // 上りの一段は 0.5 まで (平らな床から段の上へは 1.5 で上れない)
                    if (ny == c.getY() + 1 && step(b, nx, ny, nz) && !step(b, c.getX(), c.getY(), c.getZ())) {
                        continue;
                    }
                    if (!stand(b, open, nx, ny, nz)) {
                        continue;
                    }
                    if (ny > c.getY() && !pass(b, open, c.getX(), c.getY() + 2, c.getZ())) {
                        break;
                    }
                    if (ny < c.getY() - 1) {
                        boolean clear = true;
                        for (int yy = ny; yy <= c.getY() + 1; yy++) {
                            clear &= pass(b, open, nx, yy, nz);
                        }
                        if (!clear) {
                            continue;
                        }
                    }
                    if (seen.add(BlockPos.asLong(nx, ny, nz))) {
                        q.add(new BlockPos(nx, ny, nz));
                    }
                    break;
                }
            }
        }
        return seen;
    }

    static int barsIn(ServerLevel level, String box) {
        BlockPos a = AbyssBuild.mark(box + "_a");
        BlockPos b = AbyssBuild.mark(box + "_b");
        int n = 0;
        if (a != null && b != null) {
            for (BlockPos q : BlockPos.betweenClosed(a, b)) {
                if (level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
                    n++;
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 印と門 (二人のパーティ)
    static void marks(List<String> f, MinecraftServer server, ServerLevel level) {
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v2_a"));
        FakePlayer c = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v2_b"));
        PartyData pd = PartyData.get(server);
        PartyData.Party party = pd.create(a.getUUID(), "a");
        pd.add(party, c.getUUID(), "b");
        LayerData d = LayerData.get(server);
        try {
            Quests.forgetAll(a);
            Quests.forgetAll(c);
            check(f, !AbyssMarks.take(a, "bone", 2), "the nest mark was taken before the nest was cleared");
            d.set(a.getUUID(), "clear:nest_bone");
            check(f, AbyssMarks.take(a, "bone", 1) && AbyssMarks.take(a, "bone", 2), "could not take bone marks 1 and 2");
            check(f, AbyssMarks.count(c, "bone") == 2, "the party mate does not share the marks: " + AbyssMarks.count(c, "bone"));
            BlockPos g = AbyssBuild.mark("gate_bone");
            BlockPos out = AbyssBuild.mark("gate_bone_out");
            a.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 180f, 0f);
            check(f, !AbyssMarks.gate(a, "bone"), "the gate opened with two marks");
            check(f, AbyssMarks.take(c, "bone", 3), "the mate could not take the third mark");
            check(f, AbyssMarks.count(a, "bone") == 3 && AbyssMarks.canPassGate(a, "bone"), "three marks do not open the gate");
            check(f, d.isOpen("scut_bone_3"), "taking a mark did not open its shortcut");
            // 近道の格子が世界から消えている (2026-10-23 の試遊「印をおいても鉄格子のところがあかない」)
            for (int i = 1; i <= 3; i++) {
                check(f, barsIn(level, "scut_bone_" + i) == 0, "the shortcut bars of bone mark " + i + " are still there: " + barsIn(level, "scut_bone_" + i));
            }
            check(f, barsIn(level, "scut_salt_1") > 0 || d.isOpen("scut_salt_1"), "the salt shortcut bars should still be closed");
            // 北の格子の門: 印が二つの人では上がらず、三つの人が近づくと上がり、離れて 3 秒で下りる
            BlockPos gb = AbyssBuild.mark("gate_bone");
            long t0 = level.getGameTime() + 1000;
            AbyssMarks.setGate(level, gb, false);
            FakePlayer two = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v2_two"));
            d.set(two.getUUID(), "mark:bone_1");
            d.set(two.getUUID(), "mark:bone_2");
            two.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 180f, 0f);
            AbyssMarks.gateTick(level, t0, List.of(two));
            check(f, !AbyssMarks.gateRaised(level, "bone"), "the bar gate rose for a player with two marks");
            a.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 180f, 0f);
            AbyssMarks.gateTick(level, t0 + 10, List.of(a, two));
            check(f, AbyssMarks.gateRaised(level, "bone") && level.getBlockState(gb).isAir() && level.getBlockState(gb.offset(1, 2, 0)).isAir(),
                    "the bar gate did not rise for a player with three marks");
            a.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 30.5, 180f, 0f);
            two.moveTo(gb.getX() + 0.5, gb.getY(), gb.getZ() + 0.5, 180f, 0f);
            AbyssMarks.gateTick(level, t0 + 200, List.of(a, two));
            check(f, AbyssMarks.gateRaised(level, "bone"), "the bar gate fell on a player standing in it");
            two.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 30.5, 180f, 0f);
            AbyssMarks.gateTick(level, t0 + 210, List.of(a, two));
            check(f, !AbyssMarks.gateRaised(level, "bone") && level.getBlockState(gb.above()).is(net.minecraft.world.level.block.Blocks.IRON_BARS),
                    "the bar gate did not fall after everyone left");
            d.forget(two.getUUID());
            // 一人 (パーティなし) で印を取っても落ちず、近道の格子が開く (2026-10-23 の試遊「印をおいても鉄格子のところがあかない」の元:
            // 仲間への知らせでパーティが無いと落ち、近道を開ける前で止まっていた)
            FakePlayer solo = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v2_solo"));
            try {
                check(f, AbyssMarks.take(solo, "salt", 1), "a solo player could not take salt mark 1");
                check(f, barsIn(level, "scut_salt_1") == 0, "a solo player's mark did not open its shortcut bars: " + barsIn(level, "scut_salt_1"));
            } catch (RuntimeException e) {
                f.add("taking a mark alone crashed: " + e);
            } finally {
                d.forget(solo.getUUID());
            }
            a.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 180f, 0f);
            check(f, AbyssMarks.gate(a, "bone"), "the gate did not let a player with three marks through");
            BlockPos in = AbyssBuild.mark("gate_bone_in");
            check(f, a.blockPosition().closerThan(in, 2), "passing the gate did not move the player beyond it: " + a.blockPosition() + " gate " + g);
            check(f, !AbyssMarks.descend(a, "bone"), "the descent opened before the lord fell");
            d.set(a.getUUID(), "lord:bone");
            check(f, AbyssMarks.descend(a, "bone") && a.blockPosition().closerThan(AbyssBuild.mark("arrive_salt"), 2), "the descent did not reach the salt camp");
            check(f, Layers.sectionAt(a.blockPosition()) == 1, "the salt camp is not in section 1");
        } finally {
            pd.disband(party);
            d.forget(a.getUUID());
            d.forget(c.getUUID());
        }
    }

    // ---------------------------------------------------------------- 仕掛け
    static void puzzles(List<String> f, ServerLevel level) {
        LayerData d = LayerData.get(level.getServer());
        boolean was = d.isOpen("door_bone");
        UUID owner = UUID.randomUUID();
        for (int k = 0; k < 3; k++) {
            BlockPos b = AbyssBuild.mark("brazier_bone_" + k);
            if (b != null) {
                AbyssLight.add(level, b.above(), 8, level.getServer().overworld().getGameTime() + 400, owner);
            }
        }
        LayerPuzzles.braziers(level);
        check(f, d.isOpen("door_bone"), "three lights on the braziers did not open the bone door");
        BlockPos a = AbyssBuild.mark("door_bone_a");
        check(f, a != null && level.getBlockState(a.above()).isAir(), "the bone door blocks were not cleared");
        if (!was) {
            d.close("door_bone");
        }
        int n = LayerPuzzles.trailLength(level);
        check(f, n > 20, "the ash maze trail was not found (" + n + " cells)");
    }

    // ---------------------------------------------------------------- 段 (二人のパーティ)
    static void sections(List<String> f, MinecraftServer server) {
        QuestDef main = QuestManager.quest(AbyssEntrance.QUEST);
        check(f, main != null, "main_abyss is missing");
        if (main == null) {
            return;
        }
        List<String> ids = main.stages().stream().map(QuestDef.Stage::id).toList();
        check(f, ids.containsAll(Layers.STAGES), "main_abyss misses section stages: " + ids);
        ServerLevel level = server.overworld();
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v2_sa"));
        FakePlayer c = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v2_sb"));
        PartyData pd = PartyData.get(server);
        PartyData.Party party = pd.create(a.getUUID(), "a");
        pd.add(party, c.getUUID(), "b");
        try {
            Map<UUID, ServerPlayer> online = Map.of(a.getUUID(), a, c.getUUID(), c);
            for (FakePlayer q : List.of(a, c)) {
                Quests.forgetAll(q);
                Quests.start(q, AbyssEntrance.QUEST);
                for (int i = 0; i < 8 && !"gate".equals(Layers.stage(q)); i++) {
                    Quests.forceStage(q, AbyssEntrance.QUEST);
                }
            }
            c.moveTo(a.getX() + 60, a.getY(), a.getZ());
            Quests.signalWithParty(level, List.of(a), "ch5_abyss_enter", online::get);
            poll(a, c);
            check(f, "bone_camp".equals(Layers.stage(a)) && "bone_camp".equals(Layers.stage(c)), "the gate did not lead both to bone_camp: " + Layers.stage(a) + "/" + Layers.stage(c));
            for (FakePlayer q : List.of(a, c)) {
                Quests.onTalk(q, "skyabyss:attendant_survivor_bone", "ch5_bone_camp");
            }
            poll(a, c);
            check(f, "bone_marks".equals(Layers.stage(a)), "talking to the lamp keeper did not advance to bone_marks: " + Layers.stage(a));
            for (int i = 1; i <= 3; i++) {
                Quests.signalWithParty(level, List.of(a), AbyssMarks.SIGNAL_MARK + "bone_" + i, online::get);
            }
            poll(a, c);
            check(f, "bone_lord".equals(Layers.stage(a)) && "bone_lord".equals(Layers.stage(c)), "three marks did not advance both to bone_lord: " + Layers.stage(a) + "/" + Layers.stage(c));
            Quests.signalWithParty(level, List.of(a), "abyss_lord_bone", online::get);
            poll(a, c);
            check(f, "salt_camp".equals(Layers.stage(a)) && "salt_camp".equals(Layers.stage(c)), "the bone lord did not advance both to salt_camp: " + Layers.stage(a) + "/" + Layers.stage(c));
            check(f, Layers.pastMarks(a, "bone") && Layers.pastLord(c, "bone") && !Layers.pastMarks(a, "salt"), "stage order helpers are wrong");
        } finally {
            pd.disband(party);
        }
    }

    static void poll(ServerPlayer... ps) {
        for (ServerPlayer p : ps) {
            Quests.poll(p, true);
        }
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
