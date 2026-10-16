package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.storm.StormSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 前の試練 眼の間 (12 の 4.3節)。巨神の左の眼から差す光を、羽鏡で三つの受け羽へ導く。
// - 格子は 5x5 の台 (列 c は西から東、行 r は北から南)。光は西の壁の「入る行」から東へ、床から 2 の高さを走る
// - 羽鏡は片面。ROT k は二つの向きをつなぐ: 0 = 北と東、1 = 東と南、2 = 南と西、3 = 西と北。ほかの向きから来た光は止まる
// - 光が格子の外へ出た所 (壁) に受け羽があれば光り、そのあとは光が外れても消えない。三つとも光ると嘴の小部屋の戸が開く
// - 雷蛾 (static_moth) が光の筋の上にいると、光をさえぎる
// - 配置は手で作った四つ (最短 4・5・6・7 手)。ワールドで初めて挑むときは、いちばんやさしい 0。自己テストで全配置を解く
public final class EyeMirrors {
    // 向き: 0 北 (-z)、1 東 (+x)、2 南 (+z)、3 西 (-x)
    static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    static final int[][] PAIRS = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
    public static final int RESET_COOLDOWN = 20 * 30;
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1f, 0.97f, 0.85f), 1.1f);
    private static final DustParticleOptions BEAM_NIGHT = new DustParticleOptions(new Vector3f(0.75f, 0.85f, 1f), 1.1f);

    // 壁の受け羽: side = 光が出ていく向き (0 北の壁・1 東の壁・2 南の壁・3 西の壁)、index = 北・南の壁なら列、東・西の壁なら行
    public record Wall(int side, int index) {
    }

    public record Config(int entry, List<int[]> mirrors, int[] init, List<Wall> receivers) {
    }

    // search で選んだ四つ (scratchpad の mirrors.py で最短の手を数えた。自己テストでも解く)
    public static final List<Config> CONFIGS = List.of(
            new Config(0, cells(0, 0, 2, 2, 0, 1, 2, 1, 2, 3, 0, 3, 1, 1), new int[] {2, 0, 1, 1, 1, 0, 2},
                    List.of(new Wall(0, 0), new Wall(3, 1), new Wall(2, 1))),
            new Config(0, cells(4, 2, 4, 0, 0, 0, 0, 4, 2, 0, 4, 1, 1, 0), new int[] {1, 0, 1, 0, 3, 2, 1},
                    List.of(new Wall(0, 0), new Wall(1, 4), new Wall(3, 4))),
            new Config(2, cells(3, 3, 3, 4, 2, 2, 2, 0, 2, 1, 1, 1, 2, 3, 0, 0), new int[] {2, 0, 1, 3, 3, 1, 3, 2},
                    List.of(new Wall(2, 1), new Wall(1, 4), new Wall(3, 3))),
            new Config(3, cells(0, 2, 3, 1, 3, 4, 1, 3, 1, 2, 4, 3, 0, 3), new int[] {2, 2, 2, 3, 1, 2, 2},
                    List.of(new Wall(3, 2), new Wall(0, 1), new Wall(2, 4))));
    public static final int[] MIN_MOVES = {4, 5, 6, 7};

    private static long lastReset = -RESET_COOLDOWN;
    // 今の光の筋 (粒を出す点)。毎秒作り直す
    private static List<Vec3> beam = List.of();

    private EyeMirrors() {
    }

    private static List<int[]> cells(int... xy) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) {
            out.add(new int[] {xy[i], xy[i + 1]});
        }
        return List.copyOf(out);
    }

    public static Config config(StormBackState state) {
        return CONFIGS.get(Math.floorMod(state.mirrorConfig(), CONFIGS.size()));
    }

    // ---------------------------------------------------------------- 光のたどり方 (格子だけで。自己テストと世界の両方で使う)
    public interface Grid {
        // その格子の鏡の向き (鏡がなければ -1)
        int mirror(int c, int r);

        // その格子を光が通れないか (雷蛾など)
        default boolean blocked(int c, int r) {
            return false;
        }
    }

    // 光の道 (通った格子) と、出ていった壁 (止まったら null)
    public record Trace(List<int[]> path, @Nullable Wall exit) {
    }

    public static Trace trace(int entry, Grid g) {
        List<int[]> path = new ArrayList<>();
        int c = -1;
        int r = entry;
        int d = 1;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        while (true) {
            c += STEP[d][0];
            r += STEP[d][1];
            if (c < 0 || c > 4 || r < 0 || r > 4) {
                return new Trace(path, new Wall(d, d == 1 || d == 3 ? r : c));
            }
            if (!seen.add((c * 5 + r) * 4 + d) || g.blocked(c, r)) {
                path.add(new int[] {c, r});
                return new Trace(path, null);
            }
            path.add(new int[] {c, r});
            int k = g.mirror(c, r);
            if (k >= 0) {
                int s = (d + 2) % 4;
                int[] p = PAIRS[k];
                if (s == p[0]) {
                    d = p[1];
                } else if (s == p[1]) {
                    d = p[0];
                } else {
                    return new Trace(path, null);
                }
            }
        }
    }

    public static Grid grid(Config cfg, int[] rots) {
        Map<Integer, Integer> at = new HashMap<>();
        for (int i = 0; i < cfg.mirrors().size(); i++) {
            int[] m = cfg.mirrors().get(i);
            at.put(m[0] * 5 + m[1], rots[i]);
        }
        return (c, r) -> at.getOrDefault(c * 5 + r, -1);
    }

    // 最短の手 (鏡を一つ 90 度回すのが 1 手)。受け羽は一度光れば消えないので、光った受け羽の組も状態に入れる
    public static int solve(Config cfg) {
        int n = cfg.mirrors().size();
        int full = (1 << cfg.receivers().size()) - 1;
        ArrayDeque<long[]> q = new ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int[] start = cfg.init().clone();
        long s0 = encode(start, lit(cfg, start, 0));
        q.add(new long[] {s0, 0});
        seen.add(s0);
        while (!q.isEmpty()) {
            long[] cur = q.poll();
            int mask = (int) (cur[0] & 0xFF);
            if (mask == full) {
                return (int) cur[1];
            }
            int[] rots = decode(cur[0], n);
            for (int i = 0; i < n; i++) {
                int[] nr = rots.clone();
                nr[i] = (nr[i] + 1) % 4;
                long st = encode(nr, lit(cfg, nr, mask));
                if (seen.add(st)) {
                    q.add(new long[] {st, cur[1] + 1});
                }
            }
        }
        return -1;
    }

    // 最短の解き方 (回す鏡の番号の並び。解けなければ null)。自己テストが世界の鏡で同じ手を打って確かめる
    @Nullable
    public static List<Integer> solvePath(Config cfg) {
        int n = cfg.mirrors().size();
        int full = (1 << cfg.receivers().size()) - 1;
        ArrayDeque<Long> q = new ArrayDeque<>();
        Map<Long, long[]> parent = new HashMap<>();
        int[] start = cfg.init().clone();
        long s0 = encode(start, lit(cfg, start, 0));
        q.add(s0);
        parent.put(s0, null);
        while (!q.isEmpty()) {
            long cur = q.poll();
            if ((int) (cur & 0xFF) == full) {
                List<Integer> moves = new ArrayList<>();
                long c = cur;
                while (parent.get(c) != null) {
                    long[] p = parent.get(c);
                    moves.add(0, (int) p[1]);
                    c = p[0];
                }
                return moves;
            }
            int[] rots = decode(cur, n);
            int mask = (int) (cur & 0xFF);
            for (int i = 0; i < n; i++) {
                int[] nr = rots.clone();
                nr[i] = (nr[i] + 1) % 4;
                long st = encode(nr, lit(cfg, nr, mask));
                if (!parent.containsKey(st)) {
                    parent.put(st, new long[] {cur, i});
                    q.add(st);
                }
            }
        }
        return null;
    }

    static int lit(Config cfg, int[] rots, int mask) {
        Wall w = trace(cfg.entry(), grid(cfg, rots)).exit();
        int i = w == null ? -1 : cfg.receivers().indexOf(w);
        return i >= 0 ? mask | (1 << i) : mask;
    }

    private static long encode(int[] rots, int mask) {
        long v = 0;
        for (int i = rots.length - 1; i >= 0; i--) {
            v = (v << 2) | rots[i];
        }
        return (v << 8) | mask;
    }

    private static int[] decode(long v, int n) {
        int[] r = new int[n];
        v >>= 8;
        for (int i = 0; i < n; i++) {
            r[i] = (int) (v & 3);
            v >>= 2;
        }
        return r;
    }

    // ---------------------------------------------------------------- 世界の中の位置
    public static BlockPos wallPos(Wall w) {
        return switch (w.side()) {
            case 0 -> gridCell(w.index(), 0).offset(0, 0, -GRID_STEP + 1).north();
            case 1 -> gridCell(4, w.index()).offset(GRID_STEP - 1, 0, 0).east();
            case 2 -> gridCell(w.index(), 4).offset(0, 0, GRID_STEP - 1).south();
            default -> gridCell(0, w.index()).offset(-GRID_STEP + 1, 0, 0).west();
        };
    }

    // 入る行の、西の壁の窓 (光が入る所)
    public static BlockPos entryPos(Config cfg) {
        return gridCell(0, cfg.entry()).offset(-GRID_STEP + 1, 0, 0).west();
    }

    // 配置を置き直す (リセット・戻しの鏡)。鏡を最初の向きに、受け羽は消す (keepLit なら光ったものは残す)
    public static void apply(ServerLevel level, StormBackState state, boolean keepLit) {
        Config cfg = config(state);
        // 台の上を空ける
        for (int c = 0; c < 5; c++) {
            for (int r = 0; r < 5; r++) {
                level.setBlock(gridCell(c, r), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int i = 0; i < cfg.mirrors().size(); i++) {
            int[] m = cfg.mirrors().get(i);
            level.setBlock(gridCell(m[0], m[1]), StormBackSetup.MIRROR.get().defaultBlockState().setValue(StormBlocks.Mirror.ROT, cfg.init()[i]), 3);
        }
        // 壁: どの配置でも受け羽になりうる所はいったん壁の石に戻す (西の壁は窓のガラス)
        for (Config c : CONFIGS) {
            for (Wall w : c.receivers()) {
                level.setBlock(wallPos(w), w.side() == 3 ? StormKit.LENS : StormKit.CALCITE, 3);
            }
        }
        for (int r = 0; r < 5; r++) {
            level.setBlock(gridCell(0, r).offset(-GRID_STEP + 1, 0, 0).west(), StormKit.LENS, 3);
        }
        level.setBlock(entryPos(cfg), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), 3);
        if (!keepLit) {
            state.setReceivers(0);
        }
        for (int i = 0; i < cfg.receivers().size(); i++) {
            boolean lit = (state.receivers() & (1 << i)) != 0;
            level.setBlock(wallPos(cfg.receivers().get(i)), StormBackSetup.RECEIVER.get().defaultBlockState().setValue(StormBlocks.Receiver.LIT, lit), 3);
        }
        setDoor(level, state.receivers() == 7 || state.rung(StormBackState.Trial.FRONT));
    }

    public static void setDoor(ServerLevel level, boolean open) {
        for (int z = BEAK_DOOR_Z1; z <= BEAK_DOOR_Z2; z++) {
            for (int x = -1; x <= 1; x++) {
                for (int y = EYE_HALL.floor() + 1; y <= EYE_HALL.floor() + 3; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState want = open ? Blocks.AIR.defaultBlockState() : jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get().defaultBlockState();
                    if (!level.getBlockState(p).equals(want)) {
                        level.setBlock(p, want, 3);
                    }
                }
            }
        }
    }

    // 世界の格子 (鏡のブロックの向きと、雷蛾)
    static Grid worldGrid(ServerLevel level) {
        List<Entity> moths = level.getEntities((Entity) null, EYE_HALL.aabb(),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("static_moth") && e.isAlive());
        return new Grid() {
            @Override
            public int mirror(int c, int r) {
                BlockState s = level.getBlockState(gridCell(c, r));
                return s.is(StormBackSetup.MIRROR.get()) ? s.getValue(StormBlocks.Mirror.ROT) : -1;
            }

            @Override
            public boolean blocked(int c, int r) {
                BlockPos p = gridCell(c, r);
                AABB box = new AABB(p).inflate(1.0, 0.2, 1.0);
                for (Entity m : moths) {
                    if (m.getBoundingBox().intersects(box)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    // 4 tick ごと (眼の間に人がいる間)
    static void tick(ServerLevel level, StormBackState state) {
        if (state.rung(StormBackState.Trial.FRONT) && state.receivers() == 7) {
            beam = List.of();
            return;
        }
        Config cfg = config(state);
        Trace t = trace(cfg.entry(), worldGrid(level));
        // 粒の点: 入口から格子の中心を結ぶ線
        List<Vec3> pts = new ArrayList<>();
        BlockPos in = entryPos(cfg);
        pts.add(Vec3.atCenterOf(in));
        for (int[] p : t.path()) {
            pts.add(Vec3.atCenterOf(gridCell(p[0], p[1])));
        }
        if (t.exit() != null) {
            pts.add(Vec3.atCenterOf(wallPos(t.exit())));
            int i = cfg.receivers().indexOf(t.exit());
            if (i >= 0 && (state.receivers() & (1 << i)) == 0) {
                light(level, state, cfg, i);
            }
        }
        beam = pts;
        boolean day = level.isDay();
        for (int i = 1; i < pts.size(); i++) {
            StormFx.line(level, day ? BEAM : BEAM_NIGHT, pts.get(i - 1), pts.get(i), 0.5);
        }
        // 受け羽の近くを光が通ると、羽が少し揺れる (あと一つで届く、が分かる)
        for (int i = 0; i < cfg.receivers().size(); i++) {
            if ((state.receivers() & (1 << i)) != 0) {
                continue;
            }
            Vec3 w = Vec3.atCenterOf(wallPos(cfg.receivers().get(i)));
            for (Vec3 p : pts) {
                if (p.distanceToSqr(w) < 16 && level.getGameTime() % 20 == 0) {
                    StormFx.forced(level, ParticleTypes.WHITE_ASH, w.x, w.y, w.z, 6, 0.3, 0.3, 0.3, 0.01);
                    break;
                }
            }
        }
    }

    static void light(ServerLevel level, StormBackState state, Config cfg, int i) {
        state.setReceivers(state.receivers() | (1 << i));
        BlockPos p = wallPos(cfg.receivers().get(i));
        level.setBlock(p, StormBackSetup.RECEIVER.get().defaultBlockState().setValue(StormBlocks.Receiver.LIT, true), 3);
        level.playSound(null, p, StormSetup.RECEIVER_LIT.get(), SoundSource.BLOCKS, 2f, 1f + 0.15f * Integer.bitCount(state.receivers()));
        StormFx.burst(level, ParticleTypes.END_ROD, Vec3.atCenterOf(p), 20, 0.4);
        for (ServerPlayer pl : level.players()) {
            if (inFront(pl.position())) {
                pl.displayClientMessage(ModText.progress("message.skyabyss.storm.feather_lit", Integer.bitCount(state.receivers())), true);
            }
        }
        if (state.receivers() == 7) {
            setDoor(level, true);
        }
    }

    // 羽鏡を回す (右クリック)
    public static void turn(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(StormBackSetup.MIRROR.get())) {
            return;
        }
        level.setBlock(pos, s.setValue(StormBlocks.Mirror.ROT, (s.getValue(StormBlocks.Mirror.ROT) + 1) % 4), 3);
        level.playSound(null, pos, StormSetup.MIRROR_TURN.get(), SoundSource.BLOCKS, 1f, 1f);
    }

    // 戻しの鏡 (前室の取っ手)。30 秒に一度
    public static boolean resetMirrors(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        if (now - lastReset < RESET_COOLDOWN) {
            player.displayClientMessage(ModText.deny("message.skyabyss.storm.mirrors_reset_wait", (RESET_COOLDOWN - (now - lastReset) + 19) / 20), true);
            return false;
        }
        lastReset = now;
        apply(level, StormBackState.get(level), true);
        player.displayClientMessage(ModText.hint("message.skyabyss.storm.mirrors_reset"), true);
        level.playSound(null, MIRROR_RESET, StormSetup.MIRROR_TURN.get(), SoundSource.BLOCKS, 1.5f, 0.6f);
        return true;
    }

    static void clear() {
        lastReset = -RESET_COOLDOWN;
        beam = List.of();
    }

    public static List<Vec3> beam() {
        return beam;
    }
}
