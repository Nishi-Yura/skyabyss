package jp.revati.skyabyss.trial.whale;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
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

// W03 苔の滑り坂 (歩・渡る・★★。docs/design/29 の 3.1)。背骨に沿って五段に下る苔の坂。苔の床は「流れ」で、乗ると横へ運ばれる (向きは両側の壁の灯りで読む:
// 灯りのついた側へ流れる)。骨の島 (乾いていて流れない) は、流れの床の先の穴の列に三つだけ。流れの向きを読んで、運ばれる分を見込んで次の島へ渡る。
//   決まりを教える: 一本目・二本目の流れは向きが変わらない。 ひねる: 三本目から後の流れは、時が経つと向きが入れ替わる (入れ替わる 1 秒前に灯りが白く瞬く)。
//   島を踏むと光って残る (そのグループの全員に)。落ちたら、最後に立った島 (休み場の広間) へ戻る (穴の底は水。傷は軽い)。
//   一人: 流れが弱い。 三人から: 流れの入れ替わりが速く、「うねり」の障害の間が短い。
// 修飾: 配置 3 (島の並び・流れの向き)・数字 2 (流れの強さ・入れ替わりの間)・障害 (うねり: 一本の流れが予告のあと一時だけ強くなる)・任意の目標 (一度も落ちずに)
public final class W03Moss implements TrialCell {
    public static final int LANES = 5;
    public static final int[] LANE_Z = {132, 137, 142, 148, 153};
    public static final int LANE_LEN = 3;
    public static final int[] LANE_Y = {119, 118, 117, 116, 115};
    // 島の列 (流れの 0・1・3 本目の先)。2 行ぶん。上面のブロックの y
    public static final int[] GAP_Z = {135, 140, 151};
    public static final int[] GAP_Y = {118, 117, 115};
    // 島 (幅 3) の中心 (x = X0 + u)。配置 × 島の列
    public static final int[][] ISLE_U = {{3, 9, 5}, {9, 3, 8}, {6, 10, 2}};
    // 流れの向き (+1 = 東)。配置 × 流れ。三本目から後 (FLIP_FROM) は時で入れ替わる
    public static final int[][] DIRS = {{1, -1, 1, -1, 1}, {-1, 1, -1, 1, -1}, {1, 1, -1, -1, 1}};
    public static final int FLIP_FROM = 2;
    public static final int X0 = WhaleDeck.W03_X1;
    public static final int X1 = WhaleDeck.W03_X2;
    // 休み場の広間・出口の広間の床の y と最後に立つ位置
    static final BlockPos[] STOPS = {new BlockPos(-40, 120, 130), new BlockPos(-40, 117, 146), new BlockPos(-40, 115, 157)};
    // 流れの強さ (tick ごとの足し込み)・入れ替わりの間 (tick)・うねりの間 (tick)
    static final double[] PUSH = {0.30, 0.40, 0.52};
    static final int[] FLIP = {340, 250, 180};
    static final int FLIP_WARN = 24;
    static final int SURGE_EVERY = 190;
    static final int SURGE_WARN = 30;
    static final int SURGE_LEN = 40;

    private final Def def = TrialCatalog.get("W03");
    private final Ports ports = new Ports(WhaleDeck.W03_GATE, false, List.of(), WhaleDeck.W03_STONE, WhaleDeck.W03_PAD, WhaleDeck.REST_S1,
            WhaleDeck.W03.aabb(), WhaleDeck.rests().stream().filter(r -> r.id().startsWith("w03")).toList());

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 形
    public static List<BlockPos> island(int layout, int gap) {
        List<BlockPos> out = new ArrayList<>();
        int cx = X0 + ISLE_U[layout][gap];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                out.add(new BlockPos(cx + dx, GAP_Y[gap], GAP_Z[gap] + dz));
            }
        }
        return out;
    }

    public static List<BlockPos> union() {
        Set<BlockPos> set = new LinkedHashSet<>();
        for (int l = 0; l < ISLE_U.length; l++) {
            for (int g = 0; g < GAP_Z.length; g++) {
                set.addAll(island(l, g));
            }
        }
        return List.copyOf(set);
    }

    public static List<BlockPos> active(int layout) {
        List<BlockPos> out = new ArrayList<>();
        for (int g = 0; g < GAP_Z.length; g++) {
            out.addAll(island(layout, g));
        }
        return out;
    }

    static List<BlockPos> inactive(int layout) {
        Set<BlockPos> act = new LinkedHashSet<>(active(layout));
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!act.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    // z の列の区画の番号 (なければ -1) と、床のブロックの y
    static int segOf(int z) {
        int[][] segs = WhaleDeck.W03_SEGS;
        for (int i = 0; i < segs.length; i++) {
            if (z >= segs[i][0] && z <= segs[i][1]) {
                return i;
            }
        }
        return -1;
    }

    public static int laneOf(double z) {
        for (int l = 0; l < LANES; l++) {
            if (z >= LANE_Z[l] && z < LANE_Z[l] + LANE_LEN) {
                return l;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 数字
    static double push(Run run) {
        double p = PUSH[run.mods.number(0)];
        return run.mods.abyss() ? p * Abyss.NUMBER_STEP : p;
    }

    public static int flipEvery(Run run, int players) {
        int f = Abyss.shorter(run.mods, FLIP[run.mods.number(1)]);
        return players >= 3 ? (int) Math.round(f * 0.85) : f;
    }

    // 流れ l の、時刻 t (tick) での向き。入れ替わる流れは、流れごとに位相をずらす
    public static int dirAt(Run run, int lane, long t, int players) {
        int base = DIRS[run.mods.layout()][lane];
        if (lane < FLIP_FROM) {
            return base;
        }
        int every = flipEvery(run, players);
        long phase = t + (long) (lane - FLIP_FROM) * every / 3;
        return Math.floorMod(phase / every, 2) == 0 ? base : -base;
    }

    // 流れ l が、次の入れ替わりの直前か
    public static boolean warning(Run run, int lane, long t, int players) {
        return lane >= FLIP_FROM && dirAt(run, lane, t + FLIP_WARN, players) != dirAt(run, lane, t, players);
    }

    static int players(Run run) {
        return Math.max(1, run.getInt("players"));
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        GroupGates.open(back, run.group, inactive(run.mods.layout()));
        run.put("lit", 0);
        run.put("view", -1L);
        run.put("stops", new java.util.HashMap<UUID, BlockPos>());
        run.put("nextSurge", back.getGameTime() + 160L);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        GroupGates.release(back, run.group, union());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int layout = m.layout();
        int n = Math.max(1, inside.size());
        run.put("players", n);
        long t = now - run.startTick;
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w03.start"));
        }
        // うねり (障害)
        int surgeLane = -1;
        if (m.hasHazard(0) && !run.cleared) {
            long next = run.getLong("nextSurge");
            Random rng = new Random(run.seed ^ next);
            int lane = rng.nextInt(LANES);
            if (now >= next - SURGE_WARN && now < next) {
                run.put("surgeWarn", lane + 1);
            } else if (now >= next) {
                run.put("surgeLane", lane + 1);
                run.put("surgeUntil", now + SURGE_LEN);
                run.put("surgeWarn", 0);
                run.put("nextSurge", now + (n >= 3 ? SURGE_EVERY * 3 / 4 : SURGE_EVERY));
                TrialFx.sound(back, run.group, new BlockPos(X0 + 6, LANE_Y[lane] + 1, LANE_Z[lane]), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.5f, 0.6f);
            }
            if (now < run.getLong("surgeUntil")) {
                surgeLane = run.getInt("surgeLane") - 1;
            }
        }
        // 見かけ (流れの向きの灯り・島の光) が変わったら送り直す
        long view = 0;
        for (int l = 0; l < LANES; l++) {
            int d = dirAt(run, l, t, n);
            view |= (d > 0 ? 1L : 0L) << l;
            view |= (warning(run, l, t, n) ? 1L : 0L) << (5 + l);
        }
        view |= (long) run.getInt("lit") << 10;
        view |= (long) (run.getInt("surgeWarn") + (surgeLane + 1) * 8) << 14;
        view |= run.cleared ? 1L << 40 : 0L;
        if (view != run.getLong("view")) {
            run.put("view", view);
            TrialRuntime.refresh(back, run);
        }
        // 流れの粒 (向きを見せる)
        for (int l = 0; l < LANES; l++) {
            int d = dirAt(run, l, t, n);
            double x = X0 + 1 + (t / 5 * 7 + l * 3) % (X1 - X0 - 1);
            TrialFx.particles(back, run.group, ParticleTypes.CLOUD, x + 0.5, LANE_Y[l] + 1.15, LANE_Z[l] + 0.5 + (t / 5 + l) % LANE_LEN, 0, d, 0, 0, 0.08);
        }
        double base = push(run) * (n == 1 ? 0.8 : 1.0);
        java.util.Map<UUID, BlockPos> stops = (java.util.Map<UUID, BlockPos>) run.data.get("stops");
        for (ServerPlayer p : inside) {
            int z = (int) Math.floor(p.getZ());
            int seg = segOf(z);
            if (seg < 0) {
                continue;
            }
            int floor = WhaleDeck.W03_SEGS[seg][2];
            // 落ちた
            if (p.getY() < floor + 1 - 1.5) {
                BlockPos at = stops.getOrDefault(p.getUUID(), STOPS[0]);
                p.teleportTo(back, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
                p.setDeltaMovement(0, 0, 0);
                p.hurtMarked = true;
                p.fallDistance = 0;
                if (p.getHealth() > 4f) {
                    p.hurt(p.damageSources().fall(), 1.5f);
                }
                run.put("falls", run.getInt("falls") + 1);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
                Notice.bar(p, ModText.deny("message.skyabyss.trial.w03.fell"));
                continue;
            }
            if (!p.onGround()) {
                continue;
            }
            // 立った所を覚える: 島 (光って残る) と、広間
            int g = islandAt(layout, p);
            if (g >= 0) {
                stops.put(p.getUUID(), new BlockPos(X0 + ISLE_U[layout][g], GAP_Y[g] + 1, GAP_Z[g]));
                int lit = run.getInt("lit");
                if ((lit & (1 << g)) == 0) {
                    run.put("lit", lit | (1 << g));
                    TrialFx.sound(back, run.group, new BlockPos(X0 + ISLE_U[layout][g], GAP_Y[g] + 1, GAP_Z[g]), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.1f);
                }
            } else if (seg == 0 || seg == 6 || seg == 10) {
                stops.put(p.getUUID(), STOPS[seg == 0 ? 0 : seg == 6 ? 1 : 2]);
            }
            // 流れに運ばれる
            int lane = laneOf(p.getZ());
            if (lane >= 0 && Math.abs(p.getY() - (LANE_Y[lane] + 1)) < 0.3) {
                double s = base * (lane == surgeLane ? 2.0 : 1.0);
                p.setDeltaMovement(p.getDeltaMovement().add(dirAt(run, lane, t, n) * s, 0, 0));
                p.hurtMarked = true;
            }
        }
        // 出口の広間に着いた
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= WhaleDeck.W03_SEGS[10][0] && p.getY() >= WhaleDeck.W03_SEGS[10][2] + 0.9 && p.onGround()) {
                    run.cleared = true;
                    run.clearedAt = now;
                    TrialFx.sound(back, run.group, WhaleDeck.W03_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w03.crossed"));
                    TrialRuntime.refresh(back, run);
                    break;
                }
            }
        }
    }

    // p が立っている島の列の番号 (なければ -1)
    private static int islandAt(int layout, ServerPlayer p) {
        for (int g = 0; g < GAP_Z.length; g++) {
            int cx = X0 + ISLE_U[layout][g];
            if (p.getX() >= cx - 1 && p.getX() < cx + 2 && p.getZ() >= GAP_Z[g] && p.getZ() < GAP_Z[g] + 2 && Math.abs(p.getY() - (GAP_Y[g] + 1)) < 0.5) {
                return g;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int layout = run.mods.layout();
        int n = players(run);
        long t = back.getGameTime() - run.startTick;
        int lit = run.getInt("lit");
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState glow = Blocks.SEA_LANTERN.defaultBlockState();
        for (int g = 0; g < GAP_Z.length; g++) {
            for (BlockPos p : island(layout, g)) {
                out.accept(p, (lit & (1 << g)) != 0 ? glow : bone);
            }
        }
        BlockState dark = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState warn = Blocks.CALCITE.defaultBlockState();
        int surgeWarn = run.getInt("surgeWarn") - 1;
        for (int l = 0; l < LANES; l++) {
            int d = dirAt(run, l, t, n);
            boolean w = warning(run, l, t, n) || surgeWarn == l;
            for (int dz = 0; dz < LANE_LEN; dz++) {
                for (int dy = 1; dy <= 2; dy++) {
                    for (int side = 0; side < 2; side++) {
                        boolean eastWall = side == 1;
                        boolean downstream = eastWall == (d > 0);
                        out.accept(new BlockPos(eastWall ? X1 + 1 : X0 - 1, LANE_Y[l] + dy, LANE_Z[l] + dz), w ? warn : downstream ? glow : dark);
                    }
                }
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!back.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                bad.add("W03 island block missing at " + p);
                break;
            }
        }
        for (int l = 0; l < ISLE_U.length; l++) {
            for (int g = 0; g < GAP_Z.length; g++) {
                int u = ISLE_U[l][g];
                if (u < 1 || u > X1 - X0 - 1) {
                    bad.add("W03 layout " + l + " island " + g + " is at the wall");
                }
            }
            // 島から島へは、運ばれる分 (最大 5 マス) を見込んで、歩いて届く (横の離れが 9 以内)
            for (int g = 0; g + 1 < GAP_Z.length; g++) {
                if (Math.abs(ISLE_U[l][g] - ISLE_U[l][g + 1]) > 9) {
                    bad.add("W03 layout " + l + ": islands " + g + " and " + (g + 1) + " are too far apart sideways");
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(WhaleDeck.W03_STONE))) {
            bad.add("W03 stone missing");
        }
        return bad;
    }
}
