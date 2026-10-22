package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssShortcuts;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

// SELFTEST abyss layers storm ash: 雷の層の吊り橋と雷の拍・像、灰の迷路の道と番人の間・近道 (docs/design/36 の 8 章)。
//   印が揃っている、橋を渡れる、雷の拍は決まっていて止まった区間へは落ちない、羽虫は近い区間を引く、
//   迷路の道が入口から番人の間まで一本で届く、番人を倒すと近道が開く（一度だけ）。
//   「arrival から心臓まで歩ける」は tools/abyss/audit_layers.py が見る（ここでは見ない）。
public final class AbyssLayersSelfTest {
    private AbyssLayersSelfTest() {
    }

    // 橋と迷路の塊を先に読み込み、無ければ建てる (強制読み込み)
    public static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        for (BlockPos p : marks()) {
            if (p != null) {
                hold(level, p, true);
            }
        }
        if (!AbyssBuild.built(server)) {
            AbyssBuild.finishNow(server);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        check(f, level != null, "the abyss dimension is missing");
        if (level != null) {
            checkMarks(f);
            checkBridge(f, level);
            checkStrikes(f, server);
            checkMaze(f, level);
            checkGate(f, server);
            // 読み込みで固定した塊を外す (外し忘れると、止めるときの片づけが終わらないことがあった)
            for (BlockPos p : marks()) {
                if (p != null) {
                    hold(level, p, false);
                }
            }
        }
        report(f);
    }

    private static void checkMarks(List<String> f) {
        for (String m : new String[]{"storm_bridge_a", "storm_bridge_b", "idol_0", "idol_1", "idol_2", "ash_maze_in", "ash_gate", "watch_ash_maze"}) {
            check(f, AbyssBuild.mark(m) != null, m + " mark is missing");
        }
    }

    // 橋は、印の二つの間の線の上で、床が続き、頭の上が空いている
    private static void checkBridge(List<String> f, ServerLevel level) {
        BlockPos a = AbyssBuild.mark("storm_bridge_a");
        BlockPos b = AbyssBuild.mark("storm_bridge_b");
        if (a == null || b == null) {
            return;
        }
        int steps = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ())) * 2;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
            int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
            BlockPos at = new BlockPos(x, a.getY(), z);
            check(f, !level.getBlockState(at.below()).isAir(), "bridge has a gap at " + at);
            check(f, level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir(), "bridge is blocked at " + at);
        }
    }

    // 雷の拍: 決まっている・止まった区間へは落ちない・羽虫は近い区間を引く・全部止まれば落ちない
    private static void checkStrikes(List<String> f, MinecraftServer server) {
        int[] all = {StormBridge.STATE_ALIVE, StormBridge.STATE_ALIVE, StormBridge.STATE_ALIVE};
        for (long beat = 0; beat < 12; beat++) {
            int s = StormBridge.sectionAt(beat, all, -1);
            check(f, s == StormBridge.pattern(beat), "beat " + beat + " does not follow the pattern");
            check(f, s == StormBridge.sectionAt(beat, all, -1), "beat " + beat + " is not deterministic");
        }
        int[] one = {StormBridge.STATE_ALIVE, StormBridge.STATE_STOPPED, StormBridge.STATE_ALIVE};
        for (long beat = 0; beat < 60; beat++) {
            check(f, StormBridge.sectionAt(beat, one, -1) != 1, "a stopped section was struck at beat " + beat);
        }
        int[] none = {StormBridge.STATE_STOPPED, StormBridge.STATE_STOPPED, StormBridge.STATE_STOPPED};
        check(f, StormBridge.sectionAt(3, none, -1) == -1, "all stopped still strikes");
        check(f, StormBridge.sectionAt(3, all, 2) == 2, "a charged moth does not draw the strike");
        check(f, StormBridge.sectionAt(3, one, 1) != 1, "a moth draws a stopped section");
        check(f, StormBridge.center() != null, "bridge center is missing");
        // 止める・戻す（像が壊れた状態を作って、戻す）
        StormBridge.reset(server);
        StormBridge.stop(server, 2);
        check(f, StormBridge.states(server)[2] == StormBridge.STATE_STOPPED, "stop did not stop the section");
        StormBridge.reset(server);
        check(f, StormBridge.states(server)[2] == StormBridge.STATE_UNSPAWNED, "reset did not reset the section");
    }

    // 迷路の道: 入口から番人の間まで、一本の道（隣り合う足もとが一マスずつ）
    private static void checkMaze(List<String> f, ServerLevel level) {
        List<BlockPos> route = AshMaze.path(level);
        BlockPos in = AshMaze.entrance();
        BlockPos g = AshMaze.gate();
        check(f, !route.isEmpty(), "the ash maze has no route from the entrance to the gate");
        if (route.isEmpty() || in == null || g == null) {
            return;
        }
        check(f, route.get(0).equals(in), "route does not start at the entrance");
        check(f, route.get(route.size() - 1).distSqr(g) <= 4, "route does not end at the gate");
        check(f, route.size() >= 60, "the route is too short for a 3 to 6 minute maze: " + route.size());
        for (int i = 1; i < route.size(); i++) {
            BlockPos p = route.get(i - 1);
            BlockPos q = route.get(i);
            check(f, Math.abs(p.getX() - q.getX()) + Math.abs(p.getZ() - q.getZ()) == 1 && p.getY() == q.getY(),
                    "the route breaks at step " + i);
        }
    }

    // 番人を倒すと近道が開く（一度だけ）
    private static void checkGate(List<String> f, MinecraftServer server) {
        AshMaze.resetGate(server);
        check(f, !AbyssShortcuts.isOpen(server, AshMaze.KEY), "the ash shortcut is open before the gate");
        AshMaze.clearGate(server);
        check(f, AbyssShortcuts.isOpen(server, AshMaze.KEY), "defeating the ember keeper did not open the ash shortcut");
        check(f, AshMaze.gateState(server) == 2, "the gate is not marked as cleared");
        check(f, !AbyssShortcuts.open(server, AshMaze.KEY), "the shortcut opened twice");
        AshMaze.resetGate(server);
    }

    private static List<BlockPos> marks() {
        List<BlockPos> out = new ArrayList<>();
        out.add(AbyssBuild.mark("storm_bridge_a"));
        out.add(AbyssBuild.mark("storm_bridge_b"));
        out.add(AbyssBuild.mark("ash_maze_in"));
        out.add(AbyssBuild.mark("ash_gate"));
        return out;
    }

    private static void hold(ServerLevel level, BlockPos p, boolean on) {
        int cx = p.getX() >> 4;
        int cz = p.getZ() >> 4;
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                level.setChunkForced(x, z, on);
            }
        }
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss layers storm ash PASSED (marks, bridge, strike beat and idols, moth draw, maze route, gate and shortcut)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss layers storm ash FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
