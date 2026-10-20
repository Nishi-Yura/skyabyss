package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.G;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// T04 渦潮の滑り台 (歩・拍・★★★。docs/design/29 の 3.2)。水の流れる長い溝を、流れに乗って下る。溝は三つの分かれ道 (二本の水路) を通る:
//   決まりを教える: 分かれ道では、二本のうち一本だけが前へ流れ (緑の床)、もう一本は後ろへ押し戻す (赤の床)。前へ流れる水路は、拍で入れ替わる (入れ替わる 1 秒前に黄色く瞬く)。
//   仕切りの窓 (水路のあいだの穴) から、もう一本へ移れる。押し戻されても、分かれ道の手前の溜まりへ戻るだけ (傷も罰もない)。
//   ひねる: 一つの分かれ道だけ、拍が逆 (前の二つと同じ側へ入ると押し戻される)。 見て選ぶ。
//   栓 (溜まりの縁の仕掛け): 引くと、その分かれ道の流れの向きを一定のあいだ固める。一人は長く固まり、人数が増えると短い (手分けして固める)。
//   一人: 周期が長く、栓が長く効く。 三人から: 周期が短く、栓が短い。 「渦」の障害は、道にいる人を横へ押す (予告の泡つき)。
// 修飾: 配置 3 (拍の位相。どの分かれ道が逆か)・数字 2 (流れの速さ・入れ替わりの間)・障害 (渦)・任意の目標 (一度も押し戻されずに)
public final class T04Whirl implements TrialCell {
    // 溝 (水路 x 22〜30。仕切り x 26)・分かれ道 3 つ (z の始まり)
    public static final int X1 = 22;
    public static final int X2 = 30;
    public static final int DIV_X = 26;
    public static final int WATER_Z1 = 79;
    public static final int WATER_Z2 = 111;
    public static final int[] J_START = {81, 92, 103};
    public static final int J_LEN = 9;
    public static final int[] WINDOWS = {2, 6};
    public static final int BED = G - 2;
    // 栓 (三つの分かれ道ぶん。入口の岸・溜まりの縁の張り出し)
    public static final BlockPos[] VALVES = {new BlockPos(31, G + 1, 77), new BlockPos(32, G + 1, 91), new BlockPos(32, G + 1, 102)};
    // 位相 (三つの分かれ道の、最初に前へ流れる水路。0 西 / 1 東) = 配置
    public static final int[][] PHASES = {{0, 0, 1}, {1, 1, 0}, {0, 1, 0}};
    // 流れの速さ (人の速さへの 5 tick ごとの押し。1 tick あたりの距離)・入れ替わりの間 (tick)
    static final double[] CURRENT = {0.070, 0.100, 0.130};
    static final int[] TURN = {200, 150, 110};
    static final int WARN = 20;
    static final int[] LOCK = {320, 240, 200};
    static final int EDDY_EVERY = 170;
    static final int EDDY_WARN = 26;
    static final int WASH_AFTER = 30;

    private final Def def = TrialCatalog.get("T04");
    private final Ports ports = new Ports(TideDeck.T04_GATE, false, TideDeck.T04_EXIT, TideDeck.T04_STONE, TideDeck.T04_PAD, TideDeck.REST_H2,
            new net.minecraft.world.phys.AABB(20, G + 1, 75, 33, G + 11, 122), TideDeckRests.of("t04"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    @Override
    public Set<BlockPos> interactive() {
        return new HashSet<>(List.of(VALVES));
    }

    // ---------------------------------------------------------------- 数字
    public static int turn(Mods m, int players) {
        double t = TURN[m.number(1)] * (players <= 1 ? 1.25 : players >= 3 ? 0.85 : 1.0);
        return Abyss.shorter(m, (int) Math.round(t));
    }

    public static double current(Mods m) {
        double c = CURRENT[m.number(0)];
        return m.abyss() ? c * Abyss.NUMBER_STEP : c;
    }

    public static int lockTicks(Mods m, int players) {
        int t = LOCK[players <= 1 ? 0 : players >= 3 ? 2 : 1];
        return m.abyss() ? (int) Math.round(t / Abyss.NUMBER_STEP) : t;
    }

    // 分かれ道 k の、時刻 t (動き始めからの tick) に前へ流れる水路 (0 西 / 1 東)。固めていればその水路
    public static int forwardLane(int layout, int k, long t, int period) {
        return (PHASES[layout][k] + (int) Math.floorMod(Math.floorDiv(t, (long) period), 2L)) % 2;
    }

    // 入れ替わる WARN tick 前か
    public static boolean flipping(long t, int period) {
        return Math.floorMod(t, (long) period) >= period - WARN;
    }

    // 位置 (x, z) の、流れの向き (前 +1 / 後ろ -1 / 流れ無し 0) と、どの分かれ道か (-1 = 溜まり)。fwd[k] = 分かれ道 k の前へ流れる水路
    public static int flowAt(double x, double z, int[] fwd) {
        if (x < X1 || x >= X2 + 1 || z < WATER_Z1 || z >= WATER_Z2 + 1) {
            return 0;
        }
        for (int k = 0; k < 3; k++) {
            if (z >= J_START[k] && z < J_START[k] + J_LEN) {
                int lane = x < DIV_X + 0.5 ? 0 : 1;
                return lane == fwd[k] ? 1 : -1;
            }
        }
        return 1;
    }

    // 分かれ道 k の水路 lane のマス (床。仕切りの窓も含めない)
    public static List<BlockPos> laneBed(int k, int lane) {
        List<BlockPos> out = new ArrayList<>();
        int x1 = lane == 0 ? X1 : DIV_X + 1;
        int x2 = lane == 0 ? DIV_X - 1 : X2;
        for (int x = x1; x <= x2; x++) {
            for (int z = J_START[k]; z < J_START[k] + J_LEN; z++) {
                out.add(new BlockPos(x, BED, z));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 状態
    static final class S {
        long start;
        final long[] lockUntil = new long[3];
        final int[] lockLane = new int[3];
        final long[] lockReady = new long[3];
        final Map<UUID, Long> backSince = new HashMap<>();
        final Set<UUID> washed = new HashSet<>();
        int washes;
        long view = -1;
        long nextEddy;
        long eddyAt;
        BlockPos eddyPos;
        int eddyDir;
    }

    private static S st(Run run) {
        return (S) run.data.get("s");
    }

    // 自己テスト用: いま各分かれ道で前へ流れる水路・動き始めの tick・押し戻された数
    public static int[] lanesNow(Run run, long now, int players) {
        S s = st(run);
        return forwards(run, s, now, turn(run.mods, players));
    }

    public static long startTick(Run run) {
        return st(run).start;
    }

    public static int washes(Run run) {
        return st(run).washes;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        S s = new S();
        s.start = back.getGameTime();
        s.nextEddy = s.start + 200;
        run.put("s", s);
        if (run.cleared) {
            GroupGates.open(back, run.group, TideDeck.T04_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        if (!run.cleared) {
            GroupGates.release(back, run.group, TideDeck.T04_EXIT);
        }
    }

    // 時刻 now に分かれ道 k で前へ流れる水路 (栓で固めていれば、その水路)
    static int forward(Run run, S s, int k, long now, int period) {
        if (now < s.lockUntil[k]) {
            return s.lockLane[k];
        }
        return forwardLane(run.mods.layout(), k, now - s.start, period);
    }

    static int[] forwards(Run run, S s, long now, int period) {
        return new int[] {forward(run, s, 0, now, period), forward(run, s, 1, now, period), forward(run, s, 2, now, period)};
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        S s = st(run);
        int n = Math.max(1, inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t04.start"));
        }
        int period = turn(m, n);
        int[] fwd = forwards(run, s, now, period);
        double v = current(m);
        // 流れ: 溝の中の人を押す
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getY() < G + 1.7 && p.getY() > G - 3) {
                    int dir = flowAt(p.getX(), p.getZ(), fwd);
                    if (dir != 0) {
                        double base = v * (dir > 0 ? 0.75 : 1.05);
                        p.setDeltaMovement(p.getDeltaMovement().add(0, 0, dir * base));
                        p.hurtMarked = true;
                    }
                    // 押し戻された (任意の目標): 後ろへ流れる水路に 1.5 秒いた
                    boolean inBack = dir < 0;
                    if (inBack) {
                        long since = s.backSince.computeIfAbsent(p.getUUID(), k -> now);
                        if (now - since >= WASH_AFTER && s.washed.add(p.getUUID())) {
                            s.washes++;
                            if (m.goal() == 0) {
                                run.goalOk = false;
                            }
                            Notice.bar(p, ModText.hint("message.skyabyss.trial.t04.washed"));
                        }
                    } else {
                        s.backSince.remove(p.getUUID());
                        s.washed.remove(p.getUUID());
                    }
                }
            }
        }
        // 栓の効き終わりの知らせ (1 秒前に黄色)
        long view = 0;
        for (int k = 0; k < 3; k++) {
            view |= (long) fwd[k] << k;
            if (now < s.lockUntil[k]) {
                view |= 1L << (4 + k);
                if (s.lockUntil[k] - now <= WARN) {
                    view |= 1L << (8 + k);
                }
            }
            if (!(now < s.lockUntil[k]) && flipping(now - s.start, period)) {
                view |= 1L << (12 + k);
            }
        }
        view |= run.cleared ? 1L << 20 : 0L;
        if (view != s.view) {
            s.view = view;
            TrialRuntime.refresh(back, run);
        }
        // 流れの粒 (前へ流れる水路に、先へ向かう泡)
        if (now % 10 == 0 && !run.cleared) {
            for (int k = 0; k < 3; k++) {
                int lane = fwd[k];
                double x = lane == 0 ? X1 + 2 : DIV_X + 3;
                TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_COLUMN_UP, x + 0.5, G + 0.2, J_START[k] + 3.5, 4, 1.2, 0.1, 3.0, 0.1);
            }
        }
        // 渦 (障害): 道にいる人のそばに予告の泡 → 横へ押す
        if (m.hasHazard(0) && !run.cleared) {
            if (s.eddyAt > 0) {
                if (now < s.eddyAt) {
                    TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_COLUMN_UP, s.eddyPos.getX() + 0.5, G + 0.2, s.eddyPos.getZ() + 0.5, 10, 0.6, 0.3, 0.6, 0.1);
                } else {
                    TrialFx.particles(back, run.group, ParticleTypes.SPLASH, s.eddyPos.getX() + 0.5, G + 1.0, s.eddyPos.getZ() + 0.5, 30, 1.0, 0.3, 1.0, 0.3);
                    TrialFx.sound(back, run.group, s.eddyPos, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.6f);
                    for (ServerPlayer p : inside) {
                        if (Math.hypot(p.getX() - (s.eddyPos.getX() + 0.5), p.getZ() - (s.eddyPos.getZ() + 0.5)) < 2.4 && p.getY() < G + 1.7) {
                            p.setDeltaMovement(p.getDeltaMovement().add(0.42 * s.eddyDir, 0.05, 0));
                            p.hurtMarked = true;
                        }
                    }
                    s.eddyAt = 0;
                }
            } else if (now >= s.nextEddy) {
                List<ServerPlayer> onRoute = new ArrayList<>();
                for (ServerPlayer p : inside) {
                    if (p.getZ() >= WATER_Z1 && p.getZ() < WATER_Z2 + 1 && p.getY() < G + 1.7) {
                        onRoute.add(p);
                    }
                }
                if (!onRoute.isEmpty()) {
                    ServerPlayer target = onRoute.get(new Random(run.seed ^ now).nextInt(onRoute.size()));
                    s.eddyPos = target.blockPosition();
                    s.eddyDir = new Random(run.seed ^ (now * 31)).nextBoolean() ? 1 : -1;
                    s.eddyAt = now + EDDY_WARN;
                    s.nextEddy = now + (n >= 3 ? EDDY_EVERY * 3 / 4 : EDDY_EVERY);
                } else {
                    s.nextEddy = now + 40L;
                }
            }
        }
        // 向こうの岸に着いた
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= WATER_Z2 + 1 && p.getY() >= G + 0.9 && p.onGround()) {
                    run.cleared = true;
                    run.clearedAt = now;
                    GroupGates.open(back, run.group, TideDeck.T04_EXIT);
                    TrialFx.sound(back, run.group, TideDeck.T04_STONE, SoundEvents.BELL_RESONATE, 1.5f, 1.1f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.t04.landed"));
                    TrialRuntime.refresh(back, run);
                    break;
                }
            }
        }
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        S s = st(run);
        for (int k = 0; k < 3; k++) {
            if (!pos.equals(VALVES[k])) {
                continue;
            }
            if (run.cleared) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t04.done"));
                return true;
            }
            int players = Math.max(1, jp.revati.skyabyss.titan.group.Groups.inBox(back, run.group, ports.area()).size());
            if (now < s.lockUntil[k]) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t04.locked", (int) Math.ceil((s.lockUntil[k] - now) / 20.0)));
            } else if (now < s.lockReady[k]) {
                Notice.bar(player, ModText.deny("message.skyabyss.trial.t04.valve_cooling", (int) Math.ceil((s.lockReady[k] - now) / 20.0)));
            } else {
                int period = turn(run.mods, players);
                // 固めるのは、いま前へ流れている水路 (次に入れ替わる前に固める)
                int lane = forwardLane(run.mods.layout(), k, now - s.start, period);
                s.lockLane[k] = lane;
                s.lockUntil[k] = now + lockTicks(run.mods, players);
                s.lockReady[k] = s.lockUntil[k] + 80;
                TrialFx.sound(back, run.group, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, 1.4f, 0.7f);
                TrialFx.sound(back, run.group, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2f, 0.8f);
                Notice.bar(player, ModText.progress("message.skyabyss.trial.t04.locking", k + 1, (int) Math.round(lockTicks(run.mods, players) / 20.0)));
                s.view = -1;
                TrialRuntime.refresh(back, run);
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        if (!(run.data.get("s") instanceof S s) || s.view < 0) {
            return;
        }
        long view = s.view;
        BlockState go = Blocks.LIME_CONCRETE.defaultBlockState();
        BlockState stop = Blocks.RED_CONCRETE.defaultBlockState();
        BlockState warnGo = Blocks.YELLOW_CONCRETE.defaultBlockState();
        BlockState warnStop = Blocks.ORANGE_CONCRETE.defaultBlockState();
        BlockState locked = Blocks.CYAN_CONCRETE.defaultBlockState();
        boolean cleared = (view & (1L << 20)) != 0;
        for (int k = 0; k < 3; k++) {
            int lane = (int) ((view >> k) & 1L);
            boolean lock = (view & (1L << (4 + k))) != 0;
            boolean lockEnding = (view & (1L << (8 + k))) != 0;
            boolean flipSoon = (view & (1L << (12 + k))) != 0;
            for (int l = 0; l < 2; l++) {
                boolean fwd = l == lane;
                BlockState st;
                if (cleared) {
                    st = go;
                } else if (lock) {
                    st = lockEnding ? (fwd ? warnGo : warnStop) : fwd ? locked : stop;
                } else if (flipSoon) {
                    st = fwd ? warnGo : warnStop;
                } else {
                    st = fwd ? go : stop;
                }
                for (BlockPos p : laneBed(k, l)) {
                    out.accept(p, st);
                }
            }
            // 栓の灯り (固めているあいだ点く)
            out.accept(VALVES[k], lock ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.COPPER_BLOCK.defaultBlockState());
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            for (int lane = 0; lane < 2; lane++) {
                for (BlockPos p : laneBed(k, lane)) {
                    if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).is(Blocks.WATER) || !back.getBlockState(p.above(2)).is(Blocks.WATER)) {
                        bad.add("T04 lane bed is missing or dry at " + p);
                        return bad;
                    }
                }
            }
            for (int w : WINDOWS) {
                BlockPos win = new BlockPos(DIV_X, BED + 1, J_START[k] + w);
                if (!back.getBlockState(win).is(Blocks.WATER)) {
                    bad.add("T04 divider window missing at " + win);
                }
            }
            // 仕切りは窓のほかは立っている
            BlockPos wall = new BlockPos(DIV_X, BED + 1, J_START[k] + 4);
            if (back.getBlockState(wall).isAir() || back.getBlockState(wall).is(Blocks.WATER)) {
                bad.add("T04 divider missing at " + wall);
            }
            BlockPos v = VALVES[k];
            if (back.getBlockState(v).isAir() || !back.getBlockState(v.above()).isAir()) {
                bad.add("T04 valve missing or covered at " + v);
            }
        }
        // 位相: どの配置でも、前の二つの分かれ道は同じ側、一つだけ逆 (配置 2 は 0・1・0 で、真ん中だけ逆)
        for (int l = 0; l < PHASES.length; l++) {
            int[] ph = PHASES[l];
            int flips = 0;
            for (int k = 1; k < 3; k++) {
                if (ph[k] != ph[0]) {
                    flips++;
                }
            }
            if (flips < 1) {
                bad.add("T04 layout " + l + " has no twisted junction");
            }
        }
        // 前へ流れる水路は、どの周期でも一人で窓まで移れる (窓は 4 ブロックおき以内)
        if (WINDOWS[1] - WINDOWS[0] > 4 || WINDOWS[0] > 3 || J_LEN - 1 - WINDOWS[1] > 3) {
            bad.add("T04 the divider windows are too far apart");
        }
        // 溝の水の床 (水の深さ 2) と、仕切りのない溜まり
        if (!back.getBlockState(new BlockPos(X1, BED + 1, WATER_Z1)).is(Blocks.WATER)) {
            bad.add("T04 entry basin is dry");
        }
        if (!back.getBlockState(TideDeck.T04_STONE).is(jp.revati.skyabyss.titan.tide.back.TideBackSetup.CHIME_STONE.get())) {
            bad.add("T04 stone missing");
        }
        return bad;
    }
}
