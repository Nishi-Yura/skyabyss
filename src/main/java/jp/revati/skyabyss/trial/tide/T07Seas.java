package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.F;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// T07 二つの海 (智・合わせる・★★。docs/design/29 の 3.2)。広間に、ガラスの水槽が二つ (上の海 A・下の海 B)。三つの栓 (レバー) を引くたびに、決まった量だけ
// 水が動く (たとえば栓 1 は「A から 1 を B へ」、栓 2 は「A に 2 を足して B から 1 を減らす」)。水槽の脇の目盛りの金色に、二つの水位を同時に合わせて、しばらく保つ。
//   栓は押すたびに一度だけ働く。水槽から溢れる・空になる引きはできない (働かない)。戻しの栓で、いつでも最初の水位へ戻せる (詰まない)。
//   目盛りの組は、最初の水位から栓の引きで必ずたどり着ける所 (配置・数字・人数ごとに幅優先で調べて選ぶ)。
//   一人: 栓を順に引くだけ。 複数: 栓は離れていて、手分けして引く。人数が増えると目盛りが遠くなる。 「泡吹き」の障害は、広間を泡吹きが漂って邪魔をする。
// 修飾: 配置 3 (栓の働きの組と最初の水位)・数字 2 (目盛りまでの引きの数・保つ長さ)・障害 (泡吹き)・任意の目標 (戻しの栓を使わずに)
public final class T07Seas implements TrialCell {
    public static final int MAX = 8;
    // 水槽 A・B の中心 (x, z)。内側は 3×3、ガラスの囲いは 5×5。目盛りの柱は外側の隣
    public static final int[] TANK_X = {76, 84};
    public static final int TANK_Z = 57;
    public static final int[] SCALE_X = {73, 87};
    // 栓 (レバー) 1〜3 と戻しの栓
    public static final BlockPos[] PLUGS = {new BlockPos(76, F + 1, 61), new BlockPos(80, F + 1, 61), new BlockPos(84, F + 1, 61)};
    public static final BlockPos RESET = new BlockPos(84, F + 1, 53);
    // 配置ごとの、栓の働き {dA, dB} × 3 と、最初の水位 {A, B}
    public static final int[][][] OPS = {{{-1, 1}, {2, -1}, {-2, -1}}, {{-1, 2}, {1, -1}, {1, 1}}, {{-2, 1}, {1, -2}, {2, 2}}};
    public static final int[][] START = {{4, 4}, {2, 6}, {6, 2}};
    static final int[][] STEPS = {{3, 4}, {5, 6}, {7, 9}};
    static final int[] HOLD = {60, 100, 140};

    private final Def def = TrialCatalog.get("T07");
    private final Ports ports = new Ports(TideDeck.T07_GATE, false, TideDeck.T07_EXIT, TideDeck.T07_STONE, TideDeck.T07_PAD, TideDeck.REST_H,
            new net.minecraft.world.phys.AABB(72, F + 1, 49, 89, F + 13, 70), TideDeckRests.of("t07"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // 水槽 t (0 A・1 B) の内側のブロック (y は水位の段 k = 1〜8 のとき F + k)
    public static List<BlockPos> inner(int tank, int level) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = TANK_X[tank] - 1; x <= TANK_X[tank] + 1; x++) {
            for (int z = TANK_Z - 1; z <= TANK_Z + 1; z++) {
                out.add(new BlockPos(x, F + level, z));
            }
        }
        return out;
    }

    public static BlockPos scale(int tank, int level) {
        return new BlockPos(SCALE_X[tank], F + level, TANK_Z);
    }

    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>(List.of(PLUGS));
        set.add(RESET);
        return set;
    }

    // ---------------------------------------------------------------- 水位の計算 (ゲームなしで調べられる)
    // 最初の水位から、栓 ops の引きで届く水位ごとの引きの数 (届かなければ -1)
    public static int[][] distances(int layout) {
        int[][] d = new int[MAX + 1][MAX + 1];
        for (int[] row : d) {
            java.util.Arrays.fill(row, -1);
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        d[START[layout][0]][START[layout][1]] = 0;
        queue.add(START[layout]);
        while (!queue.isEmpty()) {
            int[] s = queue.poll();
            for (int[] op : OPS[layout]) {
                int a = s[0] + op[0];
                int b = s[1] + op[1];
                if (a < 0 || a > MAX || b < 0 || b > MAX || d[a][b] >= 0) {
                    continue;
                }
                d[a][b] = d[s[0]][s[1]] + 1;
                queue.add(new int[] {a, b});
            }
        }
        return d;
    }

    // 目盛りの組 {A, B}。引きの数が [lo, hi] に入る水位から、種で一つ。なければいちばん遠い (3 以上の) もの。入れる水位は 1〜7 (見やすい高さ)
    public static int[] pickTarget(int layout, int steps, int extra, long seed) {
        int[][] d = distances(layout);
        int lo = STEPS[steps][0] + extra;
        int hi = STEPS[steps][1] + extra;
        List<int[]> cand = new ArrayList<>();
        int bestD = 0;
        int[] best = null;
        for (int a = 1; a <= MAX - 1; a++) {
            for (int b = 1; b <= MAX - 1; b++) {
                if (d[a][b] >= lo && d[a][b] <= hi) {
                    cand.add(new int[] {a, b});
                }
                if (d[a][b] > bestD) {
                    bestD = d[a][b];
                    best = new int[] {a, b};
                }
            }
        }
        if (!cand.isEmpty()) {
            return cand.get(new Random(seed).nextInt(cand.size()));
        }
        return best;
    }

    // 最初の水位から目盛り target まで、栓 (0〜2) の引きの最短の並び。届かなければ null
    public static List<Integer> solution(int layout, int[] target) {
        java.util.Map<Integer, int[]> from = new java.util.HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int startKey = START[layout][0] * 16 + START[layout][1];
        from.put(startKey, new int[] {-1, -1});
        queue.add(START[layout]);
        while (!queue.isEmpty()) {
            int[] s = queue.poll();
            if (s[0] == target[0] && s[1] == target[1]) {
                List<Integer> out = new ArrayList<>();
                int key = s[0] * 16 + s[1];
                while (key != startKey) {
                    int[] f = from.get(key);
                    out.add(0, f[1]);
                    key = f[0];
                }
                return out;
            }
            for (int k = 0; k < OPS[layout].length; k++) {
                int a = s[0] + OPS[layout][k][0];
                int b = s[1] + OPS[layout][k][1];
                int key = a * 16 + b;
                if (a < 0 || a > MAX || b < 0 || b > MAX || from.containsKey(key)) {
                    continue;
                }
                from.put(key, new int[] {s[0] * 16 + s[1], k});
                queue.add(new int[] {a, b});
            }
        }
        return null;
    }

    static int extra(int players) {
        return Math.min(2, Math.max(0, players - 1));
    }

    static int hold(Mods m) {
        int h = HOLD[m.number(1)];
        return m.abyss() ? (int) Math.round(h * Abyss.NUMBER_STEP) : h;
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        int layout = run.mods.layout();
        run.put("a", START[layout][0]);
        run.put("b", START[layout][1]);
        run.put("target", null);
        run.put("holdSince", 0L);
        run.put("view", -1L);
        run.put("pressed", 0);
        if (run.cleared) {
            GroupGates.open(back, run.group, TideDeck.T07_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        clearPuffers(back, run);
        if (!run.cleared) {
            GroupGates.release(back, run.group, TideDeck.T07_EXIT);
        }
    }

    private static List<UUID> puffers(Run run) {
        return run.get("puffers", ArrayList::new);
    }

    private static void clearPuffers(ServerLevel back, Run run) {
        for (UUID id : puffers(run)) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        puffers(run).clear();
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int n = Math.max(1, inside.size());
        if (run.data.get("target") == null && !run.cleared) {
            int[] t = pickTarget(m.layout(), m.number(0), extra(n), run.seed);
            run.put("target", t);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t07.start", t[0], t[1]));
            TrialRuntime.refresh(back, run);
        }
        if (run.cleared) {
            return;
        }
        // 泡吹き (障害): 広間の高いところを漂う。全部倒しても、少しして新しく湧く
        if (m.hasHazard(0)) {
            List<UUID> list = puffers(run);
            for (int i = list.size() - 1; i >= 0; i--) {
                Entity e = back.getEntity(list.get(i));
                if (e == null || !e.isAlive()) {
                    list.remove(i);
                }
            }
            if (list.isEmpty()) {
                long at = run.getLong("puffAt");
                if (at == 0) {
                    run.put("puffAt", now + 100L);
                } else if (now >= at) {
                    int count = 1 + n / 3;
                    for (int i = 0; i < count; i++) {
                        Mob puffer = TideSpawns.spawn(back, TideMobSetup.BUBBLE_PUFFER.get(), new BlockPos(78 + 4 * i, F + 5, 52), run.group);
                        if (puffer != null) {
                            list.add(puffer.getUUID());
                        }
                    }
                    run.put("puffAt", 0L);
                }
            }
        }
        int[] t = (int[]) run.data.get("target");
        int a = run.getInt("a");
        int b = run.getInt("b");
        boolean onTarget = a == t[0] && b == t[1];
        if (onTarget) {
            long since = run.getLong("holdSince");
            if (since == 0) {
                run.put("holdSince", now);
                TrialFx.sound(back, run.group, new BlockPos(80, F + 1, 57), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.2f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.t07.hold", hold(m) / 20));
            } else if (now - since >= hold(m)) {
                run.cleared = true;
                run.clearedAt = now;
                clearPuffers(back, run);
                GroupGates.open(back, run.group, TideDeck.T07_EXIT);
                TrialFx.sound(back, run.group, TideDeck.T07_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.t07.solved"));
                TrialRuntime.refresh(back, run);
                return;
            }
        } else {
            run.put("holdSince", 0L);
        }
        long view = a | (long) b << 4 | (onTarget ? 1L << 8 : 0L) | (long) t[0] << 12 | (long) t[1] << 16;
        if (view != run.getLong("view")) {
            run.put("view", view);
            TrialRuntime.refresh(back, run);
        }
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        if (run.cleared || run.data.get("target") == null) {
            return pos.equals(RESET) || java.util.Arrays.asList(PLUGS).contains(pos);
        }
        int layout = run.mods.layout();
        int a = run.getInt("a");
        int b = run.getInt("b");
        if (pos.equals(RESET)) {
            run.put("a", START[layout][0]);
            run.put("b", START[layout][1]);
            run.put("holdSince", 0L);
            if (run.mods.goal() == 0) {
                run.goalOk = false;
            }
            TrialFx.sound(back, run.group, pos, SoundEvents.PISTON_CONTRACT, 1.2f, 0.8f);
            Notice.bar(player, ModText.hint("message.skyabyss.trial.t07.reset", START[layout][0], START[layout][1]));
            TrialRuntime.refresh(back, run);
            return true;
        }
        int k = -1;
        for (int i = 0; i < PLUGS.length; i++) {
            if (pos.equals(PLUGS[i])) {
                k = i;
            }
        }
        if (k < 0) {
            return false;
        }
        int na = a + OPS[layout][k][0];
        int nb = b + OPS[layout][k][1];
        if (na < 0 || na > MAX || nb < 0 || nb > MAX) {
            TrialFx.sound(back, run.group, pos, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
            Notice.bar(player, ModText.deny("message.skyabyss.trial.t07.blocked", a, b));
            return true;
        }
        run.put("a", na);
        run.put("b", nb);
        run.put("holdSince", 0L);
        TrialFx.sound(back, run.group, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5f, 0.8f + 0.1f * k);
        TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.05);
        int[] t = (int[]) run.data.get("target");
        Notice.bar(player, ModText.progress("message.skyabyss.trial.t07.moved", na, nb, t[0], t[1]));
        TrialRuntime.refresh(back, run);
        return true;
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int a = run.data.get("a") instanceof Integer i ? i : START[run.mods.layout()][0];
        int b = run.data.get("b") instanceof Integer i ? i : START[run.mods.layout()][1];
        int[] t = run.data.get("target") instanceof int[] x ? x : null;
        int[] levels = {a, b};
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int tank = 0; tank < 2; tank++) {
            for (int k = 1; k <= MAX; k++) {
                for (BlockPos p : inner(tank, k)) {
                    if (k <= levels[tank]) {
                        out.accept(p, water);
                    }
                }
                BlockState mark = t != null && k == t[tank] ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : k <= levels[tank] ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState();
                out.accept(scale(tank, k), mark);
            }
        }
        BlockState[] floor = {Blocks.RED_CONCRETE.defaultBlockState(), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState()};
        for (int i = 0; i < PLUGS.length; i++) {
            out.accept(PLUGS[i].below(), floor[i]);
        }
        out.accept(RESET.below(), Blocks.WHITE_CONCRETE.defaultBlockState());
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < 3; l++) {
            int[][] d = distances(l);
            for (int steps = 0; steps < 3; steps++) {
                for (int players = 1; players <= 6; players++) {
                    int[] t = pickTarget(l, steps, extra(players), 1234L + players);
                    if (t == null || d[t[0]][t[1]] < 3) {
                        bad.add("T07 layout " + l + " steps " + steps + " players " + players + ": no solvable target of three or more pulls");
                    } else if (t[0] == START[l][0] && t[1] == START[l][1]) {
                        bad.add("T07 layout " + l + ": the target is the starting level");
                    }
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            for (BlockPos p : inner(i, 1)) {
                if (!back.getBlockState(p).isAir()) {
                    bad.add("T07 tank " + i + " is not empty at " + p);
                    break;
                }
            }
            if (back.getBlockState(new BlockPos(TANK_X[i] - 2, F + 3, TANK_Z)).isAir()) {
                bad.add("T07 tank " + i + " has no glass wall");
            }
        }
        for (BlockPos p : PLUGS) {
            if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.RELIC_LEVER.get())) {
                bad.add("T07 plug missing at " + p);
            }
        }
        if (!back.getBlockState(RESET).is(jp.revati.skyabyss.registry.ModBlocks.RELIC_LEVER.get())) {
            bad.add("T07 reset plug missing");
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(TideDeck.T07_STONE))) {
            bad.add("T07 stone missing");
        }
        return bad;
    }
}
