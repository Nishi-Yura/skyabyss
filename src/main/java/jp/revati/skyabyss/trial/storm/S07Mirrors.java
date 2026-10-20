package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck2.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormBlocks;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
import jp.revati.skyabyss.titan.storm.back.StormFx;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// S07 二枚の鏡の間 (智・合わせる・★★。docs/design/29 の 3.3)。広間は仕切りで二つの部屋に分かれ、仕切りの真ん中に閉じた窓がある。西の壁の光が、東へまっすぐ伸びる。
//   決まり (教える): 鏡を右クリックで裏返す ('/' と '\')。光は鏡で曲がる (床に光の筋が見える)。部屋 1 の鏡を正しく向けて、光を窓へ当てると窓が開く。
//   ひねり: 窓が開くと部屋 2 がある。部屋 2 の鏡のうち、同じ色の台の鏡は、部屋 1 の鏡と「つながって」いて、片方を裏返すともう片方も裏返る。
//     部屋 1 を直すと、つながった鏡が引きずられる。つながっていない鏡 (灰色の台) を直して、光を東の壁の受け羽へ届ける。
//   受け羽に光が当たり続けると (数秒) 済み。雷蛾が光をさえぎることがある。
// 解の保証: 解ける状態から、「つながった組」と「つながっていない鏡」を m 回裏返した状態から始める。裏返しは互いに独立なので、最短は m 回で解ける
// 人数: m が人数で増える (二人ごとに +1、最大 6)。広間は広く、部屋ごとに手分けできる
// 修飾: 配置 3 (つながりの組)・数字 2 (最短の手数 3/3-4/4-5・光を保つ 2/3/4 秒)・障害 2 (雷蛾・空の雷)・任意の目標 (裏返しを少なく)
public final class S07Mirrors extends StormCell {
    // 光の向き: 0 北 (-z)・1 東 (+x)・2 南 (+z)・3 西 (-x)
    static final int[] DX = {0, 1, 0, -1};
    static final int[] DZ = {-1, 0, 1, 0};
    // 鏡の向き: 0 = '/' (東→北・北→東・西→南・南→西)、1 = '\' (東→南・南→東・西→北・北→西)
    // 鏡 10 枚の、光が来る向き (ふつうの道で)。部屋 1: L0 東・L1 北・L2 東・L3 南。部屋 2: R0 東・R1n 北・R2n 東・R3 南・R1s 南・R2s 東
    static final int[] NOMINAL = {1, 0, 1, 2, 1, 0, 1, 2, 2, 1};
    // 解く状態の部屋 1 (L0 '/'・L1 '/'・L2 '\'・L3 '\')
    static final int[] SOLVED_L = {0, 0, 1, 1};
    // 配置ごとの、つながった組 {部屋 1 の鏡, 部屋 2 の鏡}。部屋 2 の鏡は 4 (R0)・5 (R1n)・6 (R2n)・7 (R3)・8 (R1s)・9 (R2s)
    public static final int[][][] LINKS = {
            {{0, 7}, {3, 4}, {1, 5}, {2, 6}},
            {{0, 4}, {3, 7}, {1, 8}, {2, 9}},
            {{2, 4}, {0, 7}, {1, 6}, {3, 5}}};
    // 配置ごとの、つながっていない部屋 2 の鏡と、解くときの向き (北の道なら R1n=0・R2n=1、南の道なら R1s=1・R2s=0)
    public static final int[][] FREE = {{8, 9}, {5, 6}, {8, 9}};
    public static final int[][] FREE_SOLVED = {{1, 0}, {0, 1}, {1, 0}};
    // 最短の裏返しの数 (数字 0・1・2)
    static final int[][] STEPS = {{3, 3}, {3, 4}, {4, 5}};
    static final int[] HOLD = {40, 60, 80};
    static final int SKY_EVERY = 140;
    static final int SKY_WARN = 24;
    // 光のたどる最大のマス数 (輪になって終わらないのを防ぐ)
    static final int MAX_STEPS = 300;

    private static final BlockState[] PAIR_COLORS = {Blocks.ORANGE_CONCRETE.defaultBlockState(), Blocks.CYAN_CONCRETE.defaultBlockState(),
            Blocks.MAGENTA_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState()};
    private static final BlockState FREE_COLOR = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();

    public S07Mirrors() {
        super("S07", new Ports(StormDeck2.S07_GATE, false, List.of(), StormDeck2.S07_STONE, null, "", StormDeck2.S07_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 計算 (ゲームなしで調べられる)
    public record Trace(List<int[]> cells, int[] inDirs, boolean hitWindow, boolean hitTarget) {
    }

    // 光をたどる。state[i] = 鏡 i の向き。windowOpen = 窓が開いているか。blocked = 雷蛾などでさえぎられるマス (gx * 100 + gz)
    public static Trace trace(int[] state, boolean windowOpen, Set<Integer> blocked) {
        List<int[]> cells = new ArrayList<>();
        int[] inDirs = new int[StormDeck2.S07_MIRRORS.length];
        java.util.Arrays.fill(inDirs, -1);
        int gx = 0;
        int gz = StormDeck2.S07_SRC_ROW;
        int d = 1;
        boolean hitWindow = false;
        boolean hitTarget = false;
        for (int step = 0; step < MAX_STEPS; step++) {
            if (gx < 0 || gx >= StormDeck2.S07_COLS || gz < 0 || gz >= StormDeck2.S07_ROWS) {
                break;
            }
            // 仕切りの柱
            if (gx == StormDeck2.S07_WINDOW_COL && gz != StormDeck2.S07_SRC_ROW) {
                break;
            }
            cells.add(new int[] {gx, gz});
            if (blocked.contains(gx * 100 + gz)) {
                break;
            }
            if (gx == StormDeck2.S07_WINDOW_COL && !windowOpen) {
                hitWindow = true;
                break;
            }
            int m = mirrorAt(gx, gz);
            if (m >= 0) {
                inDirs[m] = d;
                d = state[m] == 0 ? d ^ 1 : 3 - d;
            }
            if (gx == StormDeck2.S07_COLS - 1 && gz == StormDeck2.S07_SRC_ROW && d == 1) {
                hitTarget = true;
                break;
            }
            gx += DX[d];
            gz += DZ[d];
        }
        return new Trace(cells, inDirs, hitWindow, hitTarget);
    }

    static int mirrorAt(int gx, int gz) {
        for (int i = 0; i < StormDeck2.S07_MIRRORS.length; i++) {
            if (StormDeck2.S07_MIRRORS[i][0] == gx && StormDeck2.S07_MIRRORS[i][1] == gz) {
                return i;
            }
        }
        return -1;
    }

    // 配置 l の解く状態 (鏡 10 枚)
    public static int[] solved(int l) {
        int[] s = new int[10];
        for (int i = 0; i < 4; i++) {
            s[i] = SOLVED_L[i];
        }
        for (int[] link : LINKS[l]) {
            s[link[1]] = s[link[0]];
        }
        for (int k = 0; k < 2; k++) {
            s[FREE[l][k]] = FREE_SOLVED[l][k];
        }
        return s;
    }

    // 配置 l の「裏返し」: 0〜3 = つながった組 (部屋 1 の鏡 i と、つながった部屋 2 の鏡)、4・5 = つながっていない鏡
    public static List<Integer> flipSet(int l, int gen) {
        if (gen < 4) {
            return List.of(LINKS[l][gen][0], LINKS[l][gen][1]);
        }
        return List.of(FREE[l][gen - 4]);
    }

    // 鏡 i を裏返したときに動く鏡 (つながった相手を含む)
    public static List<Integer> flipOf(int l, int mirror) {
        for (int g = 0; g < 6; g++) {
            if (flipSet(l, g).contains(mirror)) {
                return flipSet(l, g);
            }
        }
        return List.of(mirror);
    }

    // 最初の状態: 解く状態から m 個の裏返しをしたもの。つながった組を二つ以上、つながっていない鏡を一つ以上含む (最初の部屋で迷い、次の部屋でも直す)
    public static int[] start(int l, int m, long seed) {
        Random rng = new Random(seed);
        int[] s = solved(l);
        List<Integer> pairs = new ArrayList<>(List.of(0, 1, 2, 3));
        List<Integer> frees = new ArrayList<>(List.of(4, 5));
        java.util.Collections.shuffle(pairs, rng);
        java.util.Collections.shuffle(frees, rng);
        int free = Math.max(1, Math.min(2, (m - 2) / 2));
        int pair = Math.min(4, m - free);
        for (int i = 0; i < pair; i++) {
            for (int idx : flipSet(l, pairs.get(i))) {
                s[idx] ^= 1;
            }
        }
        for (int i = 0; i < free; i++) {
            for (int idx : flipSet(l, frees.get(i))) {
                s[idx] ^= 1;
            }
        }
        return s;
    }

    public static int moves(Mods m, int players) {
        int lo = STEPS[m.number(0)][0];
        int hi = STEPS[m.number(0)][1];
        int base = hi > lo && players >= 2 ? hi : lo;
        return Math.min(6, base + (players >= 4 ? 1 : 0) + (m.abyss() ? 1 : 0));
    }

    static int hold(Mods m) {
        int h = HOLD[m.number(1)];
        return m.abyss() ? (int) Math.round(h * Abyss.NUMBER_STEP) : h;
    }

    // 鏡 i の見かけの向き (羽鏡のブロックの ROT。光が来る向き in で、光を当てる面を向ける)
    public static int rotFor(int state, int in) {
        if (state == 0) {
            return in == 1 || in == 2 ? 3 : 1;
        }
        return in == 1 || in == 0 ? 2 : 0;
    }

    // ---------------------------------------------------------------- 状態
    private static int[] st(Run run) {
        return run.get("st", () -> new int[10]);
    }

    private static List<UUID> moths(Run run) {
        return run.get("moths", ArrayList::new);
    }

    static boolean windowOpened(Run run) {
        return run.cleared || windowOpen(run);
    }

    private static boolean windowOpen(Run run) {
        return (Boolean) run.data.getOrDefault("window", false);
    }

    // 雷蛾のいるマス
    private Set<Integer> mothCells(ServerLevel back, Run run) {
        Set<Integer> out = new HashSet<>();
        for (UUID id : new ArrayList<>(moths(run))) {
            Entity e = back.getEntity(id);
            if (e == null || !e.isAlive()) {
                moths(run).remove(id);
                continue;
            }
            int gx = (int) Math.floor((e.getX() - StormDeck2.S07_X0) / 3.0);
            int gz = (int) Math.floor((e.getZ() - StormDeck2.S07_Z0) / 3.0);
            if (e.getY() >= Y && e.getY() <= Y + 6) {
                out.add(gx * 100 + gz);
            }
        }
        return out;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        int l = run.mods.layout();
        int[] s = start(l, moves(run.mods, 1), run.seed);
        System.arraycopy(s, 0, st(run), 0, 10);
        run.put("window", false);
        run.put("flips", 0);
        run.put("hold", 0L);
        run.put("sig", 0);
        run.put("players", 1);
        run.put("nextSky", 0L);
        run.put("mothDone", false);
        GroupGates.release(back, run.group, StormDeck2.s07Window());
        if (run.cleared) {
            System.arraycopy(solved(l), 0, st(run), 0, 10);
            run.put("window", true);
            GroupGates.open(back, run.group, StormDeck2.s07Window());
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, moths(run));
        if (!run.cleared) {
            GroupGates.release(back, run.group, StormDeck2.s07Window());
        }
    }

    // ---------------------------------------------------------------- 鏡
    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>();
        for (int i = 0; i < StormDeck2.S07_MIRRORS.length; i++) {
            set.add(mirrorPos(i));
        }
        return set;
    }

    static BlockPos mirrorPos(int i) {
        BlockPos c = StormDeck2.s07Cell(StormDeck2.S07_MIRRORS[i][0], StormDeck2.S07_MIRRORS[i][1]);
        return new BlockPos(c.getX(), Y + 1, c.getZ());
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        int idx = -1;
        for (int i = 0; i < StormDeck2.S07_MIRRORS.length; i++) {
            if (pos.equals(mirrorPos(i))) {
                idx = i;
            }
        }
        if (idx < 0) {
            return false;
        }
        if (run.cleared) {
            return true;
        }
        int l = run.mods.layout();
        int[] s = st(run);
        List<Integer> set = flipOf(l, idx);
        for (int i : set) {
            s[i] ^= 1;
        }
        run.put("flips", run.getInt("flips") + 1);
        run.put("hold", 0L);
        if (run.mods.goal() == 0 && run.getInt("flips") > allowedFlips(run)) {
            run.goalOk = false;
        }
        TrialFx.sound(back, run.group, pos, SoundEvents.COPPER_PLACE, 1.0f, 1.5f);
        if (set.size() > 1) {
            // つながった鏡: 相手にも音と粒
            for (int i : set) {
                BlockPos q = mirrorPos(i);
                TrialFx.particles(back, run.group, ParticleTypes.ELECTRIC_SPARK, q.getX() + 0.5, q.getY() + 0.8, q.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.05);
            }
        }
        run.put("sig", 0);
        TrialRuntime.refresh(back, run);
        return true;
    }

    static int allowedFlips(Run run) {
        return moves(run.mods, Math.max(1, run.getInt("players"))) + 2;
    }

    // ---------------------------------------------------------------- 動かす
    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = count(inside);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s07.start"));
        }
        for (ServerPlayer p : inside) {
            if (p.getY() < Y - 3) {
                toRest(back, run, p, "s07a", "message.skyabyss.trial.s07.fell");
            }
        }
        if (run.cleared) {
            return;
        }
        // 人数が増えたら、まだ触っていない配置 (裏返し 0) を引き直す
        if (run.getInt("flips") == 0 && n != run.getInt("players") && !windowOpen(run)) {
            run.put("players", n);
            System.arraycopy(start(run.mods.layout(), moves(run.mods, n), run.seed), 0, st(run), 0, 10);
            run.put("sig", 0);
        } else {
            run.put("players", n);
        }
        // 障害
        if (run.mods.hasHazard(0) && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && !(Boolean) run.data.getOrDefault("mothDone", false)) {
            run.put("mothDone", true);
            int want = n >= 3 ? 2 : 1;
            for (int i = 0; i < want; i++) {
                StaticMoth moth = spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, -50 + i * 20, Y + 4, StormDeck2.S07_Z0 + 13 + i * 6);
                if (moth != null) {
                    moths(run).add(moth.getUUID());
                }
            }
        }
        if (run.mods.hasHazard(1) && now >= run.getLong("nextSky") && !inside.isEmpty() && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            ServerPlayer target = inside.get(back.random.nextInt(inside.size()));
            run.put("nextSky", now + SKY_EVERY);
            StormStrike.call(back, target.position(), SKY_WARN, 8f, null, run.group);
        }
        // 光
        Trace tr = trace(st(run), windowOpen(run), mothCells(back, run));
        int sig = 17;
        for (int[] c : tr.cells()) {
            sig = sig * 31 + c[0] * 100 + c[1];
        }
        for (int v : st(run)) {
            sig = sig * 31 + v;
        }
        sig = sig * 31 + (tr.hitTarget() ? 1 : 0) + (windowOpen(run) ? 2 : 0);
        if (sig != run.getInt("sig")) {
            run.put("sig", sig);
            TrialRuntime.refresh(back, run);
        }
        if (now % 5 == 0) {
            drawBeam(back, run, tr);
        }
        if (tr.hitWindow() && !windowOpen(run)) {
            run.put("window", true);
            GroupGates.open(back, run.group, StormDeck2.s07Window());
            TrialFx.sound(back, run.group, StormDeck2.s07Cell(StormDeck2.S07_WINDOW_COL, StormDeck2.S07_SRC_ROW), SoundEvents.BELL_RESONATE, 1.2f, 1.2f);
            bar(inside, ModText.progress("message.skyabyss.trial.s07.window"));
            run.put("sig", 0);
            TrialRuntime.refresh(back, run);
            return;
        }
        if (tr.hitTarget()) {
            long since = run.getLong("hold");
            if (since == 0) {
                run.put("hold", now);
                bar(inside, ModText.progress("message.skyabyss.trial.s07.hold", hold(run.mods) / 20));
            } else if (now - since >= hold(run.mods)) {
                run.put("sig", 0);
                finish(back, run, inside, now, "message.skyabyss.trial.s07.cleared");
            }
        } else if (run.getLong("hold") != 0) {
            run.put("hold", 0L);
        }
    }

    private void drawBeam(ServerLevel back, Run run, Trace tr) {
        List<int[]> cells = tr.cells();
        for (int i = 0; i + 1 < cells.size(); i++) {
            BlockPos a = StormDeck2.s07Cell(cells.get(i)[0], cells.get(i)[1]);
            BlockPos b = StormDeck2.s07Cell(cells.get(i + 1)[0], cells.get(i + 1)[1]);
            Vec3 va = new Vec3(a.getX() + 0.5, Y + 1.6, a.getZ() + 0.5);
            Vec3 vb = new Vec3(b.getX() + 0.5, Y + 1.6, b.getZ() + 0.5);
            StormFx.to(run.group, () -> StormFx.line(back, ParticleTypes.END_ROD, va, vb, 1.0));
        }
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int l = run.mods.layout();
        int[] s = st(run);
        Trace tr = trace(s, windowOpen(run) || run.cleared, run.cleared ? Set.of() : mothCells(back, run));
        // 光の筋 (床)。つながった組の台の色が上書きする
        BlockState beam = Blocks.GLOWSTONE.defaultBlockState();
        for (int i = 0; i + 1 < tr.cells().size(); i++) {
            BlockPos a = StormDeck2.s07Cell(tr.cells().get(i)[0], tr.cells().get(i)[1]);
            BlockPos b = StormDeck2.s07Cell(tr.cells().get(i + 1)[0], tr.cells().get(i + 1)[1]);
            int dx = Integer.signum(b.getX() - a.getX());
            int dz = Integer.signum(b.getZ() - a.getZ());
            BlockPos p = a;
            while (!p.equals(b)) {
                out.accept(new BlockPos(p.getX(), Y, p.getZ()), beam);
                p = p.offset(dx, 0, dz);
            }
        }
        if (!tr.cells().isEmpty()) {
            int[] last = tr.cells().get(tr.cells().size() - 1);
            BlockPos q = StormDeck2.s07Cell(last[0], last[1]);
            out.accept(new BlockPos(q.getX(), Y, q.getZ()), beam);
        }
        // 鏡と台
        for (int i = 0; i < StormDeck2.S07_MIRRORS.length; i++) {
            BlockPos m = mirrorPos(i);
            int in = tr.inDirs()[i] >= 0 ? tr.inDirs()[i] : NOMINAL[i];
            out.accept(m, StormBackSetup.MIRROR.get().defaultBlockState().setValue(StormBlocks.Mirror.ROT, rotFor(s[i], in)));
            out.accept(new BlockPos(m.getX(), Y, m.getZ()), colorOf(l, i));
        }
        // 窓 (閉じているあいだは格子)
        if (!windowOpen(run) && !run.cleared) {
            for (BlockPos p : StormDeck2.s07Window()) {
                out.accept(p, Blocks.WAXED_COPPER_GRATE.defaultBlockState());
            }
        }
        // 受け羽
        out.accept(StormDeck2.s07Catcher(), StormBackSetup.RECEIVER.get().defaultBlockState().setValue(StormBlocks.Receiver.LIT, tr.hitTarget() || run.cleared));
    }

    static BlockState colorOf(int l, int mirror) {
        for (int g = 0; g < 4; g++) {
            if (LINKS[l][g][0] == mirror || LINKS[l][g][1] == mirror) {
                return PAIR_COLORS[g];
            }
        }
        return FREE_COLOR;
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < StormDeck2.S07_MIRRORS.length; i++) {
            if (!back.getBlockState(mirrorPos(i)).is(StormBackSetup.MIRROR.get())) {
                bad.add("S07 mirror " + i + " is missing");
            }
        }
        for (BlockPos p : StormDeck2.s07Window()) {
            if (!GroupGates.isGate(back, p)) {
                bad.add("S07 window gate missing at " + p);
                break;
            }
        }
        if (!back.getBlockState(StormDeck2.s07Catcher()).is(StormBackSetup.RECEIVER.get())) {
            bad.add("S07 catcher missing");
        }
        if (!back.getBlockState(StormDeck2.S07_STONE).is(StormBackSetup.CHIME_STONE.get())) {
            bad.add("S07 stone missing");
        }
        // 解く状態が実際に光を窓へ、そして受け羽へ届ける。どの最初の状態も、そのままでは届かない
        for (int l = 0; l < 3; l++) {
            int[] sol = solved(l);
            Trace t1 = trace(sol, false, Set.of());
            Trace t2 = trace(sol, true, Set.of());
            if (!t1.hitWindow() || !t2.hitTarget()) {
                bad.add("S07 layout " + l + " is not solved by its solution state");
            }
            for (int m = 3; m <= 6; m++) {
                int[] s = start(l, m, 99L + m);
                int diff = 0;
                for (int g = 0; g < 6; g++) {
                    List<Integer> set = flipSet(l, g);
                    diff += s[set.get(0)] != sol[set.get(0)] ? 1 : 0;
                }
                if (diff != m) {
                    bad.add("S07 layout " + l + " start with " + m + " flips differs by " + diff);
                }
                if (trace(s, false, Set.of()).hitWindow()) {
                    bad.add("S07 layout " + l + " start with " + m + " flips already reaches the window");
                }
            }
        }
        return bad;
    }
}
