package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

// 東の試練 雷の骨の「雷の回路」 (25 の 5章 B)。柱を銅の導線 (床の筋) でつなぎ、レバーで雷の通り道を切り替えて、骨の戸の柱へ雷を送る。
//
// 部品
//  - 受け柱 (INTAKE): 金の床 (誘いの床) のそばの柱。金の床に立つと、その人の足もとに輪が出て、雷はこの柱へ落ちる (人には当たらない)
//  - 中継の柱 (RELAY): 導線の分かれ目。レバー (切り替え) が、雷がどの導線へ進むかを決める (倒す = 戸の側、戻す = 行き止まりの側)
//  - 行き止まりの柱 (DECOY): 雷が逃げる柱。傷はない。しばらく光って、雷は消える
//  - 戸の柱 (DOOR): 雷が届くと満ちて、対の骨の戸が開く (StormEast.openFullDoors)
//  - 接地のレバー (回路 0 の受け柱): 戻した状態は地へ逃げる。倒すとつながる (導線 1 本の入門)
//  - 導線: 床の筋。暗い青緑 = いま通らない、橙 = いま雷が通る道 (レバーの状態から先読み)、黄の光 = 雷が通った (戸が開いた回路は光り続ける)
// 役割: 誘い (金の床に立つ人)・切り替え (レバーを倒す人)。ひとりなら、レバーを先に倒してから金の床へ走る。橙の筋が戸の柱へ届いていれば当たり
// 拍: 凪は輪が遅く (2 秒)、雷が柱から柱へ跳ぶのも遅い (読める)。嵐は輪が速く (1.2 秒)、雷も速く、放電の線 (StormEast) が走る。どちらでも戸は開く
// グループ: 回廊は部屋の占有 (StormBackTrials.EAST_ROOM)。レバーの状態は世界のレバーのブロック、戸が開いた回路は記録 (StormProgress の戸のビット) から置き直す
final class StormCircuit {
    enum Kind {
        INTAKE, RELAY, DECOY, DOOR
    }

    enum Outcome {
        DOOR, DECOY, GROUNDED
    }

    record Node(String id, BlockPos rod, Kind kind) {
    }

    // 導線 (床の筋。両端の柱の根元の床を含む)
    record Edge(String id, String from, String to, List<BlockPos> cells) {
    }

    // 切り替え: node から出る導線を、レバーが決める (倒す = on、戻す = off。off が null なら地へ逃げる)
    record Sw(String node, BlockPos lever, @Nullable String on, @Nullable String off) {
    }

    record Circuit(int index, List<Node> nodes, List<Edge> edges, List<Sw> switches, BlockPos pad, String intake) {
        Node node(String id) {
            for (Node n : nodes) {
                if (n.id().equals(id)) {
                    return n;
                }
            }
            throw new IllegalArgumentException(id);
        }

        Edge edge(String id) {
            for (Edge e : edges) {
                if (e.id().equals(id)) {
                    return e;
                }
            }
            throw new IllegalArgumentException(id);
        }

        @Nullable
        Sw switchOf(String nodeId) {
            for (Sw s : switches) {
                if (s.node().equals(nodeId)) {
                    return s;
                }
            }
            return null;
        }

        // 切り替えのない柱から出る、ただ一本の導線
        @Nullable
        Edge soleOut(String nodeId) {
            for (Edge e : edges) {
                if (e.from().equals(nodeId)) {
                    return e;
                }
            }
            return null;
        }

        BlockPos doorRod() {
            return EAST_RODS.get(index);
        }

        List<BlockPos> levers() {
            List<BlockPos> out = new ArrayList<>();
            for (Sw s : switches) {
                out.add(s.lever());
            }
            return out;
        }
    }

    // 雷の道すじ (通った導線と柱、行き着いた先)
    record Trace(List<Edge> edges, List<Node> nodes, Outcome outcome) {
    }

    // 暗い = 通らない、橙 = 先読み、光 = 通った
    static final int OFF = 0;
    static final int ARMED = 1;
    static final int LIT = 2;
    static final BlockState B_OFF = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
    static final BlockState B_ARMED = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
    static final BlockState B_LIT = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    static final BlockState B_PAD = Blocks.GOLD_BLOCK.defaultBlockState();

    static final List<Circuit> CIRCUITS = buildCircuits();

    // 凪・嵐の輪の長さ (tick)・柱から柱へ跳ぶ間・金の床に立ってから輪が出るまで
    static final int WARN_CALM = 44;
    static final int WARN_STORM = 26;
    static final int HOP_CALM = 18;
    static final int HOP_STORM = 6;
    static final int PAD_HOLD = 10;
    static final int DECOY_FLASH = 50;

    // 飛んでいる招雷 → 回路の番号
    private static final Map<StormStrike.Strike, Integer> FLIGHT = new IdentityHashMap<>();
    // 回路ごとの「招雷が飛んでいる・導線を雷が走っている」のあいだ (これを過ぎるまで次の招雷を出さない)
    private static final long[] BUSY_UNTIL = new long[CIRCUITS.size()];
    // 金の床に立ち続けている tick
    private static final Map<UUID, Integer> HOLD = new HashMap<>();
    private static final List<Anim> ANIMS = new ArrayList<>();
    // 行き止まりで光った導線 (回路の番号 → 戻す時刻)
    private static final Map<Integer, Long> FLASH_UNTIL = new HashMap<>();
    private static final Map<Integer, List<Edge>> FLASH_EDGES = new HashMap<>();
    private static final Map<Integer, BlockPos> FLASH_DECOY = new HashMap<>();
    // レバーの前回の状態 (切り替えを見つける)
    private static final Map<BlockPos, Boolean> LAST_LEVER = new HashMap<>();
    private static final Map<UUID, Long> HINT_AT = new HashMap<>();

    private StormCircuit() {
    }

    static void clear() {
        FLIGHT.clear();
        ANIMS.clear();
        HOLD.clear();
        FLASH_UNTIL.clear();
        FLASH_EDGES.clear();
        FLASH_DECOY.clear();
        LAST_LEVER.clear();
        HINT_AT.clear();
        DEMO_SHOWN.clear();
        java.util.Arrays.fill(BUSY_UNTIL, 0);
    }

    static {
        StormStrike.listen(StormCircuit::onLanded);
    }

    // ---------------------------------------------------------------- 回路の定義 (26 の拡大の座標。回廊の中心は WZ、どの回路も戸の手前の 26 ブロックほどの区画)
    private static BlockPos rod(int x, int z) {
        return new BlockPos(x, BONE_FLOOR + 1, z);
    }

    private static BlockPos lever(int x, int z) {
        return new BlockPos(x, BONE_FLOOR + 1, z);
    }

    // a の根元の床から b の根元の床まで (xFirst なら x 方向を先に)。両端を含む
    static List<BlockPos> path(BlockPos a, BlockPos b, boolean xFirst) {
        List<BlockPos> out = new ArrayList<>();
        int x = a.getX();
        int z = a.getZ();
        out.add(new BlockPos(x, BONE_FLOOR, z));
        for (int leg = 0; leg < 2; leg++) {
            boolean alongX = (leg == 0) == xFirst;
            if (alongX) {
                while (x != b.getX()) {
                    x += Integer.signum(b.getX() - x);
                    out.add(new BlockPos(x, BONE_FLOOR, z));
                }
            } else {
                while (z != b.getZ()) {
                    z += Integer.signum(b.getZ() - z);
                    out.add(new BlockPos(x, BONE_FLOOR, z));
                }
            }
        }
        return out;
    }

    // 四つの回路 (一つの決まりを教えて、ひねる):
    //  0 決まりを教える: 受け柱 → 戸の柱。接地のレバー 1 本 (戻していると雷は地へ逃げる。倒すとつながる)
    //  1 分かれ道: 受け柱 → 中継 → (戸の柱 | 行き止まり)。分岐のレバー 1 本 (橙の筋が戸へ届いていれば当たり)
    //  2 二段の分岐: 中継が二つ。レバー 2 本。順に橙の筋を読む
    //  3 ひねり: 中継が三つ、レバー 3 本。レバーは自分の中継の隣になく、ずれた柱のそば (橙の筋の先読みで、どのレバーがどの分かれ道かを見分ける)
    private static List<Circuit> buildCircuits() {
        List<Circuit> out = new ArrayList<>();
        int z0 = WZ;
        // 回路 0 (第一の戸): 受け柱 → 戸の柱。接地のレバー
        BlockPos i0 = rod(76, z0 - 7);
        BlockPos d0 = EAST_RODS.get(0);
        out.add(new Circuit(0, List.of(new Node("i", i0, Kind.INTAKE), new Node("d", d0, Kind.DOOR)),
                List.of(new Edge("a", "i", "d", path(i0, d0, true))),
                List.of(new Sw("i", lever(84, z0 - 4), "a", null)), new BlockPos(76, BONE_FLOOR, z0 - 3), "i"));
        // 回路 1 (第二の戸): 受け柱 → 中継 → (戸の柱 | 行き止まり)。分岐のレバー (倒す = 戸へ、戻す = 行き止まり)
        BlockPos i1 = rod(103, z0);
        BlockPos j1 = rod(110, z0);
        BlockPos x1 = rod(120, z0 - 8);
        BlockPos d1 = EAST_RODS.get(1);
        out.add(new Circuit(1, List.of(new Node("i", i1, Kind.INTAKE), new Node("j", j1, Kind.RELAY), new Node("x", x1, Kind.DECOY), new Node("d", d1, Kind.DOOR)),
                List.of(new Edge("a", "i", "j", path(i1, j1, true)), new Edge("b", "j", "d", path(j1, d1, false)), new Edge("c", "j", "x", path(j1, x1, false))),
                List.of(new Sw("j", lever(113, z0), "b", "c")), new BlockPos(103, BONE_FLOOR, z0 - 4), "i"));
        // 回路 2 (第三の戸): 二段の分岐。受け柱 → 中継 A → (中継 B | 行き止まり A)、中継 B → (戸の柱 | 行き止まり B)
        BlockPos i2 = rod(128, z0);
        BlockPos ja = rod(135, z0);
        BlockPos xa = rod(146, z0 - 9);
        BlockPos jb = rod(146, z0 + 9);
        BlockPos xb = rod(146, z0 + 5);
        BlockPos d2 = EAST_RODS.get(2);
        out.add(new Circuit(2, List.of(new Node("i", i2, Kind.INTAKE), new Node("ja", ja, Kind.RELAY), new Node("xa", xa, Kind.DECOY), new Node("jb", jb, Kind.RELAY),
                new Node("xb", xb, Kind.DECOY), new Node("d", d2, Kind.DOOR)),
                List.of(new Edge("a", "i", "ja", path(i2, ja, true)), new Edge("b", "ja", "xa", path(ja, xa, false)), new Edge("c", "ja", "jb", path(ja, jb, false)),
                        new Edge("d", "jb", "xb", path(jb, xb, false)), new Edge("e", "jb", "d", path(jb, d2, true))),
                List.of(new Sw("ja", lever(139, z0), "c", "b"), new Sw("jb", lever(143, z0 + 6), "e", "d")), new BlockPos(128, BONE_FLOOR, z0 - 4), "i"));
        // 回路 3 (第四の戸): 三つの中継。レバー a は中継 c のそば、b は中継 a のそば、c は中継 b のそば (ずれている)
        BlockPos i3 = rod(155, z0);
        BlockPos ra = rod(160, z0);
        BlockPos rb = rod(165, z0);
        BlockPos rc = rod(170, z0);
        BlockPos xa3 = rod(160, z0 - 7);
        BlockPos xb3 = rod(165, z0 + 7);
        BlockPos xc3 = rod(170, z0 - 7);
        BlockPos d3 = EAST_RODS.get(3);
        out.add(new Circuit(3, List.of(new Node("i", i3, Kind.INTAKE), new Node("ra", ra, Kind.RELAY), new Node("xa", xa3, Kind.DECOY), new Node("rb", rb, Kind.RELAY),
                new Node("xb", xb3, Kind.DECOY), new Node("rc", rc, Kind.RELAY), new Node("xc", xc3, Kind.DECOY), new Node("d", d3, Kind.DOOR)),
                List.of(new Edge("p", "i", "ra", path(i3, ra, true)), new Edge("q", "ra", "rb", path(ra, rb, true)), new Edge("r", "ra", "xa", path(ra, xa3, false)),
                        new Edge("s", "rb", "rc", path(rb, rc, true)), new Edge("t", "rb", "xb", path(rb, xb3, false)), new Edge("u", "rc", "d", path(rc, d3, true)),
                        new Edge("v", "rc", "xc", path(rc, xc3, false))),
                List.of(new Sw("ra", lever(172, z0 - 4), "q", "r"), new Sw("rb", lever(161, z0 + 4), "s", "t"), new Sw("rc", lever(167, z0 - 4), "u", "v")),
                new BlockPos(155, BONE_FLOOR, z0 - 4), "i"));
        return out;
    }

    // ---------------------------------------------------------------- 雷の道すじ (純粋な論理。自己テストが見る)
    static Trace trace(Circuit c, Predicate<BlockPos> leverOn) {
        List<Edge> edges = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        Node n = c.node(c.intake());
        nodes.add(n);
        for (int guard = 0; guard < 16; guard++) {
            if (n.kind() == Kind.DOOR) {
                return new Trace(edges, nodes, Outcome.DOOR);
            }
            if (n.kind() == Kind.DECOY) {
                return new Trace(edges, nodes, Outcome.DECOY);
            }
            Sw sw = c.switchOf(n.id());
            Edge e;
            if (sw != null) {
                String id = leverOn.test(sw.lever()) ? sw.on() : sw.off();
                e = id == null ? null : c.edge(id);
            } else {
                e = c.soleOut(n.id());
            }
            if (e == null) {
                return new Trace(edges, nodes, Outcome.GROUNDED);
            }
            edges.add(e);
            n = c.node(e.to());
            nodes.add(n);
        }
        return new Trace(edges, nodes, Outcome.GROUNDED);
    }

    // 戸へ届くレバーの組み合わせ (倒すレバーの最小の組。レバーは 4 本以下)
    static List<BlockPos> solution(Circuit c) {
        List<BlockPos> levers = c.levers();
        List<BlockPos> best = null;
        for (int mask = 0; mask < (1 << levers.size()); mask++) {
            List<BlockPos> on = new ArrayList<>();
            for (int i = 0; i < levers.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    on.add(levers.get(i));
                }
            }
            if (trace(c, on::contains).outcome() == Outcome.DOOR && (best == null || on.size() < best.size())) {
                best = on;
            }
        }
        if (best == null) {
            throw new IllegalStateException("circuit " + c.index() + " has no solution");
        }
        return best;
    }

    static Trace solutionTrace(Circuit c) {
        List<BlockPos> on = solution(c);
        return trace(c, on::contains);
    }

    // ---------------------------------------------------------------- 建てる (StormBirdBuilder.east から)
    static void build(StormKit kit) {
        for (Circuit c : CIRCUITS) {
            for (Node n : c.nodes()) {
                StormBirdBuilder.rod(kit, n.rod(), 0);
            }
            for (Edge e : c.edges()) {
                for (BlockPos p : e.cells()) {
                    kit.put(p.getX(), p.getY(), p.getZ(), B_OFF);
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.put(c.pad().getX() + dx, BONE_FLOOR, c.pad().getZ() + dz, B_PAD);
                }
            }
            for (BlockPos l : c.levers()) {
                kit.put(l.getX(), l.getY(), l.getZ(), jp.revati.skyabyss.relic.RelicBlocks.leverBase(jp.revati.skyabyss.relic.RelicStyle.STORM).setValue(LeverBlock.FACE, AttachFace.FLOOR)
                        .setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.POWERED, false));
            }
        }
    }

    // ---------------------------------------------------------------- 置き直し (持ち主が変わったとき)
    // progress が null なら誰も持っていない回廊。戸が開いた回路は、柱が満ち・導線が光り・レバーが解の向き
    static void apply(ServerLevel level, @Nullable StormProgress progress) {
        ANIMS.clear();
        FLIGHT.clear();
        FLASH_UNTIL.clear();
        FLASH_EDGES.clear();
        FLASH_DECOY.clear();
        HOLD.clear();
        java.util.Arrays.fill(BUSY_UNTIL, 0);
        for (Circuit c : CIRCUITS) {
            boolean done = progress != null && done(progress, c);
            List<BlockPos> sol = done ? solution(c) : List.of();
            for (BlockPos l : c.levers()) {
                setLever(level, l, sol.contains(l));
                LAST_LEVER.put(l, sol.contains(l));
            }
            for (Node n : c.nodes()) {
                StormRods.register(level, n.rod());
            }
            if (done) {
                for (Node n : solutionTrace(c).nodes()) {
                    StormRods.charge(level, n.rod(), StormRods.FULL);
                }
            }
        }
        refreshVisuals(level, progress);
    }

    static boolean done(StormProgress progress, Circuit c) {
        return progress.eastDoorPassable(c.index());
    }

    static void setLever(ServerLevel level, BlockPos p, boolean on) {
        BlockState s = level.getBlockState(p);
        if (s.is(jp.revati.skyabyss.registry.ModBlocks.RELIC_LEVER.get()) && s.getValue(LeverBlock.POWERED) != on) {
            level.setBlock(p, s.setValue(LeverBlock.POWERED, on), 3);
        }
    }

    static boolean leverOn(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.is(jp.revati.skyabyss.registry.ModBlocks.RELIC_LEVER.get()) && s.getValue(LeverBlock.POWERED);
    }

    // 導線の床と金の床のあるべき姿 (世界のレバーの状態から)
    static Map<BlockPos, Integer> desired(ServerLevel level, @Nullable StormProgress progress) {
        Map<BlockPos, Integer> cells = new LinkedHashMap<>();
        for (Circuit c : CIRCUITS) {
            for (Edge e : c.edges()) {
                for (BlockPos p : e.cells()) {
                    cells.putIfAbsent(p, OFF);
                }
            }
            List<Edge> lit = new ArrayList<>();
            List<Edge> armed = new ArrayList<>();
            if (progress != null && done(progress, c)) {
                lit.addAll(solutionTrace(c).edges());
            } else {
                armed.addAll(trace(c, p -> leverOn(level, p)).edges());
                List<Edge> flash = FLASH_EDGES.get(c.index());
                if (flash != null && level.getGameTime() < FLASH_UNTIL.getOrDefault(c.index(), 0L)) {
                    lit.addAll(flash);
                }
                for (Anim a : ANIMS) {
                    if (a.c == c) {
                        lit.addAll(a.lit);
                    }
                }
            }
            for (Edge e : armed) {
                for (BlockPos p : e.cells()) {
                    cells.merge(p, ARMED, Math::max);
                }
            }
            for (Edge e : lit) {
                for (BlockPos p : e.cells()) {
                    cells.merge(p, LIT, Math::max);
                }
            }
        }
        return cells;
    }

    static BlockState blockFor(int state) {
        return state == LIT ? B_LIT : state == ARMED ? B_ARMED : B_OFF;
    }

    // 世界の導線・金の床を、あるべき姿に合わせる (変わった所だけ置く)
    static void refreshVisuals(ServerLevel level, @Nullable StormProgress progress) {
        for (Map.Entry<BlockPos, Integer> e : desired(level, progress).entrySet()) {
            BlockState want = blockFor(e.getValue());
            if (!level.getBlockState(e.getKey()).equals(want)) {
                level.setBlock(e.getKey(), want, 3);
            }
        }
        for (Circuit c : CIRCUITS) {
            boolean active = progress != null && !done(progress, c);
            BlockState want = progress == null || active ? B_PAD : B_OFF;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(c.pad().getX() + dx, BONE_FLOOR, c.pad().getZ() + dz);
                    if (!level.getBlockState(p).equals(want)) {
                        level.setBlock(p, want, 3);
                    }
                }
            }
        }
    }

    // 見かけだけのグループごとの道しるべ (周回の既知の道): 解の導線を光る色で
    static List<BlockPos> solutionCells() {
        List<BlockPos> out = new ArrayList<>();
        for (Circuit c : CIRCUITS) {
            for (Edge e : solutionTrace(c).edges()) {
                out.addAll(e.cells());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 毎 tick (回廊の持ち主がいるとき)
    static void tick(ServerLevel level, UUID owner, StormProgress progress, boolean storm, boolean peaceful) {
        long gt = level.getGameTime();
        tickAnims(level, owner);
        if (gt % 5 == 0) {
            watchLevers(level, owner, progress);
            refreshVisuals(level, progress);
            tickHints(level, owner, progress);
        }
        if (peaceful) {
            return;
        }
        // 金の床に立って輪を呼ぶ
        for (ServerPlayer p : Groups.alive(level, owner)) {
            if (p.isCreative() || p.isSpectator()) {
                HOLD.remove(p.getUUID());
                continue;
            }
            Circuit c = padAt(p);
            if (c == null || done(progress, c) || gt < BUSY_UNTIL[c.index()]) {
                HOLD.remove(p.getUUID());
                continue;
            }
            int held = HOLD.merge(p.getUUID(), 1, Integer::sum);
            if (held >= PAD_HOLD) {
                HOLD.put(p.getUUID(), -40);
                lure(level, owner, c, p, storm);
            }
        }
    }

    // いま p が立っている金の床の回路
    @Nullable
    static Circuit padAt(ServerPlayer p) {
        if (!p.onGround()) {
            return null;
        }
        for (Circuit c : CIRCUITS) {
            if (Math.abs(p.getX() - (c.pad().getX() + 0.5)) <= 1.5 && Math.abs(p.getZ() - (c.pad().getZ() + 0.5)) <= 1.5 && Math.abs(p.getY() - (BONE_FLOOR + 1)) < 0.6) {
                return c;
            }
        }
        return null;
    }

    // 輪を呼ぶ。雷はいつも受け柱へ落ちる (人には当たらない)
    static StormStrike.Strike lure(ServerLevel level, UUID owner, Circuit c, ServerPlayer p, boolean storm) {
        int warn = storm ? WARN_STORM : WARN_CALM;
        StormStrike.Strike s = StormStrike.call(level, p.position(), warn, StormEast.STRIKE_DAMAGE, null, owner);
        s.rod = c.node(c.intake()).rod();
        s.noCharge();
        FLIGHT.put(s, c.index());
        BUSY_UNTIL[c.index()] = level.getGameTime() + warn + 12;
        return s;
    }

    private static void onLanded(StormStrike.Strike s) {
        Integer idx = FLIGHT.remove(s);
        if (idx == null) {
            return;
        }
        ServerLevel level = s.level;
        Circuit c = CIRCUITS.get(idx);
        UUID owner = s.group;
        boolean storm = owner != null && StormHelm.phase(level, StormBackState.Trial.EAST, owner) == StormBackClock.Phase.STORM;
        Trace t = trace(c, p -> leverOn(level, p));
        ANIMS.add(new Anim(level, c, t, owner, storm ? HOP_STORM : HOP_CALM));
        BUSY_UNTIL[c.index()] = level.getGameTime() + (long) (t.edges().size() + 1) * (storm ? HOP_STORM : HOP_CALM) + DECOY_FLASH;
    }

    // 雷が導線を走る (柱から柱へ、hop tick ごとに一本)
    private static final class Anim {
        final ServerLevel level;
        final Circuit c;
        final Trace t;
        @Nullable
        final UUID group;
        final int hop;
        int step;
        int wait;
        final List<Edge> lit = new ArrayList<>();

        Anim(ServerLevel level, Circuit c, Trace t, @Nullable UUID group, int hop) {
            this.level = level;
            this.c = c;
            this.t = t;
            this.group = group;
            this.hop = hop;
            this.wait = 2;
        }
    }

    private static void tickAnims(ServerLevel level, UUID owner) {
        for (Iterator<Anim> it = ANIMS.iterator(); it.hasNext();) {
            Anim a = it.next();
            if (a.level != level) {
                continue;
            }
            if (a.wait > 0) {
                a.wait--;
                continue;
            }
            if (a.step < a.t.edges().size()) {
                Edge e = a.t.edges().get(a.step);
                Node from = a.t.nodes().get(a.step);
                Node to = a.t.nodes().get(a.step + 1);
                a.lit.add(e);
                for (BlockPos p : e.cells()) {
                    level.setBlock(p, B_LIT, 3);
                }
                Vec3 ta = new Vec3(from.rod().getX() + 0.5, from.rod().getY() + StormRods.HEIGHT - 1, from.rod().getZ() + 0.5);
                Vec3 tb = new Vec3(to.rod().getX() + 0.5, to.rod().getY() + StormRods.HEIGHT - 1, to.rod().getZ() + 0.5);
                StormFx.to(a.group, () -> arc(level, ta, tb));
                StormRods.charge(level, from.rod(), StormRods.FULL);
                a.step++;
                a.wait = a.hop;
                continue;
            }
            finish(level, a);
            it.remove();
        }
    }

    private static void arc(ServerLevel level, Vec3 a, Vec3 b) {
        StormFx.line(level, ParticleTypes.ELECTRIC_SPARK, a, b, 0.4);
        StormFx.line(level, ParticleTypes.END_ROD, a, b, 1.2);
        StormFx.burst(level, ParticleTypes.ELECTRIC_SPARK, b, 14, 0.4);
        level.playSound(null, b.x, b.y, b.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.8f, 1.9f);
    }

    private static void finish(ServerLevel level, Anim a) {
        Trace t = a.t;
        Node last = t.nodes().get(t.nodes().size() - 1);
        switch (t.outcome()) {
            case DOOR -> {
                for (Node n : t.nodes()) {
                    StormRods.charge(level, n.rod(), StormRods.FULL);
                }
                // 戸が開く (次の StormEast.openFullDoors) までのあいだ、導線を光らせておく
                FLASH_EDGES.put(a.c.index(), List.copyOf(t.edges()));
                FLASH_UNTIL.put(a.c.index(), level.getGameTime() + 30);
                tellGroup(level, a.group, "message.skyabyss.storm.circuit_ok");
            }
            case DECOY -> {
                StormRods.charge(level, last.rod(), StormRods.FULL);
                FLASH_EDGES.put(a.c.index(), List.copyOf(a.lit));
                FLASH_UNTIL.put(a.c.index(), level.getGameTime() + DECOY_FLASH);
                FLASH_DECOY.put(a.c.index(), last.rod());
                level.playSound(null, last.rod(), SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1.4f, 0.6f);
                tellGroup(level, a.group, "message.skyabyss.storm.circuit_decoy");
            }
            case GROUNDED -> {
                Node at = last;
                Vec3 tip = new Vec3(at.rod().getX() + 0.5, at.rod().getY() + 1.2, at.rod().getZ() + 0.5);
                StormFx.to(a.group, () -> StormFx.burst(level, ParticleTypes.SMOKE, tip, 12, 0.3));
                level.playSound(null, at.rod(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.2f);
                tellGroup(level, a.group, "message.skyabyss.storm.circuit_ground");
            }
        }
    }

    // 行き止まりの光を消す (いつも呼ぶ)
    static void tickFlash(ServerLevel level) {
        if (FLASH_UNTIL.isEmpty()) {
            return;
        }
        long gt = level.getGameTime();
        for (Iterator<Map.Entry<Integer, Long>> it = FLASH_UNTIL.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Long> e = it.next();
            if (gt >= e.getValue()) {
                BlockPos decoy = FLASH_DECOY.remove(e.getKey());
                if (decoy != null) {
                    StormRods.empty(level, decoy);
                }
                FLASH_EDGES.remove(e.getKey());
                it.remove();
            }
        }
    }

    private static void tellGroup(ServerLevel level, @Nullable UUID group, String key) {
        if (group == null) {
            return;
        }
        for (ServerPlayer p : Groups.online(level, group)) {
            if (StormEast.inCorridor(p.position())) {
                Notice.bar(p, ModText.hint(key));
            }
        }
    }

    // ---------------------------------------------------------------- レバーの切り替え・床の手がかり
    private static void watchLevers(ServerLevel level, UUID owner, StormProgress progress) {
        for (Circuit c : CIRCUITS) {
            if (done(progress, c)) {
                continue;
            }
            for (BlockPos l : c.levers()) {
                boolean now = leverOn(level, l);
                Boolean was = LAST_LEVER.put(l, now);
                if (was != null && was != now) {
                    ServerPlayer who = nearest(level, owner, l, 8);
                    if (who != null) {
                        boolean reaches = trace(c, p -> leverOn(level, p)).outcome() == Outcome.DOOR;
                        Notice.bar(who, ModText.hint(reaches ? "message.skyabyss.storm.lever_reaches" : "message.skyabyss.storm.lever_not_yet"));
                    }
                }
            }
        }
    }

    @Nullable
    private static ServerPlayer nearest(ServerLevel level, UUID owner, BlockPos at, double range) {
        ServerPlayer best = null;
        double bd = range * range;
        for (ServerPlayer p : Groups.alive(level, owner)) {
            double d = p.distanceToSqr(Vec3.atCenterOf(at));
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // 金の床・レバーに近づくと、役割の一行 (45 秒に一度)
    // 初めての見本: グループが回路 0 (接地のレバー) の 14 ブロック以内に初めて来たら、正しい雷の通り道を一度だけ光らせる (回路 0 は決まりを教える部屋)
    private static final java.util.Set<UUID> DEMO_SHOWN = new java.util.HashSet<>();
    static final int DEMO_TICKS = 160;

    private static void tickHints(ServerLevel level, UUID owner, StormProgress progress) {
        long gt = level.getGameTime();
        Circuit first = CIRCUITS.get(0);
        if (!done(progress, first) && !DEMO_SHOWN.contains(owner)) {
            Vec3 pad = new Vec3(first.pad().getX() + 0.5, BONE_FLOOR + 1, first.pad().getZ() + 0.5);
            for (ServerPlayer p : Groups.alive(level, owner)) {
                if (p.position().distanceToSqr(pad) < 14 * 14) {
                    DEMO_SHOWN.add(owner);
                    FLASH_EDGES.put(first.index(), solutionTrace(first).edges());
                    FLASH_UNTIL.put(first.index(), gt + DEMO_TICKS);
                    tellGroup(level, owner, "message.skyabyss.storm.circuit_demo");
                    level.playSound(null, first.pad(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.6f, 1.2f);
                    break;
                }
            }
        }
        for (ServerPlayer p : Groups.alive(level, owner)) {
            if (p.isSpectator() || gt - HINT_AT.getOrDefault(p.getUUID(), -10000L) < 900) {
                continue;
            }
            for (Circuit c : CIRCUITS) {
                if (done(progress, c)) {
                    continue;
                }
                Vec3 pad = new Vec3(c.pad().getX() + 0.5, BONE_FLOOR + 1, c.pad().getZ() + 0.5);
                if (p.position().distanceToSqr(pad) < 3.2 * 3.2) {
                    HINT_AT.put(p.getUUID(), gt);
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.pad_hint"));
                    break;
                }
                boolean near = false;
                for (BlockPos l : c.levers()) {
                    near |= p.position().distanceToSqr(Vec3.atCenterOf(l)) < 2.6 * 2.6;
                }
                if (near) {
                    HINT_AT.put(p.getUUID(), gt);
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.lever_hint"));
                    break;
                }
            }
        }
    }

    // 自己テスト用: 行き止まりの光を今すぐ消す
    static void clearDemo() {
        DEMO_SHOWN.clear();
    }

    static void clearFlash() {
        FLASH_UNTIL.clear();
        FLASH_EDGES.clear();
        FLASH_DECOY.clear();
    }

    // 自己テスト用: 雷が走っている最中か
    static boolean running() {
        return !ANIMS.isEmpty();
    }
}
