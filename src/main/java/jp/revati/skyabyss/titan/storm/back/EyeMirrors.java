package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.StormSetup;
import net.minecraft.core.BlockPos;
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

// 前の試練 眼の間 (12 の 4.3節)。巨神の左の眼から差す光を、羽鏡で三つの受け羽へ導く。
// - 格子は 5x5 の台 (列 c は西から東、行 r は北から南)。光は西の壁の「入る行」から東へ、床から 2 の高さを走る
// - 羽鏡は片面。ROT k は二つの向きをつなぐ: 0 = 北と東、1 = 東と南、2 = 南と西、3 = 西と北。ほかの向きから来た光は止まる
// - 光が格子の外へ出た所 (壁) に受け羽があれば光り、そのあとは光が外れても消えない。三つとも光ると嘴の小部屋の戸が開く
// - 雷蛾 (static_moth) が光の筋の上にいると、光をさえぎる
// - 配置は手で作った四つ (最短 4・5・6・7 手)。グループが初めて挑むときは、いちばんやさしい 0。自己テストで全配置を解く
// グループ (docs/design/10 の 9 章): 眼の間 (嘴の小部屋まで) は部屋の占有 (RoomLocks。StormBackTrials.EYE_ROOM)。一度に一つのグループだけが入れる。
// 鏡の向き・光った受け羽・配置はグループの記録 (StormProgress) にあり、持ち主が変わると部屋の鏡と受け羽をその記録から置き直す。
// 光のたどりも記録の向きで計算する (世界の鏡のブロックは持ち主の記録を写したもの)。嘴の戸はグループごとの扉 (beakDoor)
public final class EyeMirrors {
    // 向き: 0 北 (-z)、1 東 (+x)、2 南 (+z)、3 西 (-x)
    static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    static final int[][] PAIRS = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
    public static final int RESET_COOLDOWN = 20 * 30;
    // 周回の長い眼の間 (32 の 4 章、R5): 配置を二つ続けて解く。一つ目を解くと眼が向きを変え、次の配置 (ちがう鏡の並び) が現れる
    public static final int STAGES_LONG = 2;

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

    // 戻しの鏡を最後に使った時刻 (グループごと)
    private static final Map<UUID, Long> LAST_RESET = new HashMap<>();
    // 今の光の筋 (粒を出す点。部屋の持ち主の光)。4 tick ごとに作り直す
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
    // 止まった理由: EXIT = 壁へ出た、BACK = 鏡の裏に当たった、BLOCKED = 雷蛾がさえぎった、LOOP = 同じ所を回っている
    public enum Stop {
        EXIT, BACK, BLOCKED, LOOP
    }

    public record Trace(List<int[]> path, @Nullable Wall exit, Stop stop) {
        public Trace(List<int[]> path, @Nullable Wall exit) {
            this(path, exit, exit != null ? Stop.EXIT : Stop.BACK);
        }
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
            boolean blocked = g.blocked(c, r);
            if (!seen.add((c * 5 + r) * 4 + d) || blocked) {
                path.add(new int[] {c, r});
                return new Trace(path, null, blocked ? Stop.BLOCKED : Stop.LOOP);
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
                    return new Trace(path, null, Stop.BACK);
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
        return solveFrom(cfg, cfg.init(), 0);
    }

    // 今の鏡の向き rots・光った受け羽の組 mask から、最短の解き方 (ヒントの「次の一手」用。右回しだけ数える)
    @Nullable
    public static List<Integer> solveFrom(Config cfg, int[] rots0, int mask0) {
        int n = cfg.mirrors().size();
        int full = (1 << cfg.receivers().size()) - 1;
        ArrayDeque<Long> q = new ArrayDeque<>();
        Map<Long, long[]> parent = new HashMap<>();
        int[] start = rots0.clone();
        long s0 = encode(start, lit(cfg, start, mask0));
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

    // 部屋をグループの記録のとおりに置く (持ち主が変わったとき・戻しの鏡)。鏡の向きと光った受け羽は記録から
    public static void apply(ServerLevel level, StormProgress progress) {
        place(level, progress.config(), progress.mirrorRots(), progress.receivers());
        clearBeams(level);
    }

    // だれも持っていない部屋の姿 (配置 0 の最初の向き、受け羽は消えている)
    public static void applyNeutral(ServerLevel level) {
        place(level, CONFIGS.get(0), CONFIGS.get(0).init(), 0);
        clearBeams(level);
    }

    private static void place(ServerLevel level, Config cfg, int[] rots, int receivers) {
        // 台の上を空ける
        for (int c = 0; c < 5; c++) {
            for (int r = 0; r < 5; r++) {
                set(level, gridCell(c, r), Blocks.AIR.defaultBlockState());
            }
        }
        for (int i = 0; i < cfg.mirrors().size(); i++) {
            int[] m = cfg.mirrors().get(i);
            set(level, gridCell(m[0], m[1]), StormBackSetup.MIRROR.get().defaultBlockState().setValue(StormBlocks.Mirror.ROT, rots[i]));
        }
        // 壁: どの配置でも受け羽になりうる所はいったん壁の石に戻す (西の壁は窓のガラス)
        for (Config c : CONFIGS) {
            for (Wall w : c.receivers()) {
                set(level, wallPos(w), w.side() == 3 ? StormKit.LENS : StormKit.CALCITE);
            }
        }
        for (int r = 0; r < 5; r++) {
            set(level, gridCell(0, r).offset(-GRID_STEP + 1, 0, 0).west(), StormKit.LENS);
        }
        // 受け羽の額縁 (25 の 2.2): 白い羽は白い壁に紛れるので、いまの配置の受け羽だけ、まわり 8 マスを銅の枠にして目当てにする。
        // ほかの配置で受け羽になりうる所の枠は、元の壁の石へ戻す
        for (Config c : CONFIGS) {
            for (Wall w : c.receivers()) {
                frame(level, w, false);
            }
        }
        for (Wall w : cfg.receivers()) {
            frame(level, w, true);
        }
        set(level, entryPos(cfg), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
        for (int i = 0; i < cfg.receivers().size(); i++) {
            boolean lit = (receivers & (1 << i)) != 0;
            set(level, wallPos(cfg.receivers().get(i)), StormBackSetup.RECEIVER.get().defaultBlockState().setValue(StormBlocks.Receiver.LIT, lit));
        }
    }

    // 受け羽のまわりの 3x3 (中心を除く)。on なら銅、でなければ元の壁の石
    private static void frame(ServerLevel level, Wall w, boolean on) {
        BlockPos c = wallPos(w);
        boolean alongX = w.side() == 0 || w.side() == 2;
        for (int d = -1; d <= 1; d++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (d == 0 && dy == 0) {
                    continue;
                }
                BlockPos q = c.offset(alongX ? d : 0, dy, alongX ? 0 : d);
                BlockState cur = level.getBlockState(q);
                // 壁の石 (方解石・石英・閃緑岩・銅の枠) だけ。窓・碑・別の受け羽・空気には触れない
                if (!(cur.is(Blocks.CALCITE) || cur.is(Blocks.SMOOTH_QUARTZ) || cur.is(Blocks.POLISHED_DIORITE) || cur.is(StormKit.COPPER.getBlock()))) {
                    continue;
                }
                set(level, q, on ? StormKit.COPPER : StormKit.wall(StormKit.Style.EYE, q.getX(), q.getY(), q.getZ()));
            }
        }
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        if (!level.getBlockState(p).equals(s)) {
            level.setBlock(p, s, 3);
        }
    }

    // 嘴の小部屋の戸 (グループごとの扉。座標)
    public static List<BlockPos> beakDoor() {
        List<BlockPos> out = new ArrayList<>();
        for (int z = BEAK_DOOR_Z1; z <= BEAK_DOOR_Z2; z++) {
            for (int x = -1; x <= 1; x++) {
                for (int y = EYE_HALL.floor() + 1; y <= EYE_HALL.floor() + 3; y++) {
                    out.add(new BlockPos(x, y, z));
                }
            }
        }
        return out;
    }

    // グループの格子: 鏡の向きはグループの記録から、雷蛾はそのグループの (またはグループのない) 雷蛾だけがさえぎる
    static Grid worldGrid(ServerLevel level, StormProgress progress) {
        Config cfg = progress.config();
        Grid base = grid(cfg, progress.mirrorRots());
        List<Entity> moths = level.getEntities((Entity) null, EYE_HALL.aabb(),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("static_moth") && e.isAlive()
                        && (!(e instanceof GroupOwned owned) || owned.groupId() == null || owned.groupId().equals(progress.group)));
        return new Grid() {
            @Override
            public int mirror(int c, int r) {
                return base.mirror(c, r);
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

    // 世界の鏡のブロックの向きでたどる格子 (自己テストが、記録と世界の鏡が合っているかを見る)
    static Grid blockGrid(ServerLevel level) {
        return (c, r) -> {
            BlockState s = level.getBlockState(gridCell(c, r));
            return s.is(StormBackSetup.MIRROR.get()) ? s.getValue(StormBlocks.Mirror.ROT) : -1;
        };
    }

    // 4 tick ごと (眼の間に持ち主がいる間)。光と粒は持ち主のグループのもの
    static void tick(ServerLevel level, StormProgress progress) {
        if (progress.rung(StormBackState.Trial.FRONT) && progress.receivers() == 7) {
            clearBeams(level);
            return;
        }
        StormFx.to(progress.group, () -> traceAndDraw(level, progress));
        tickHint(level, progress);
    }

    // 迷ったときの助け (25 の 3章): 60 秒、鏡を回さず受け羽も増えなければ、次に回す鏡が光の粒で光り、一行が出る。回すか受け羽が光ると、また 60 秒数える
    static final int HINT_IDLE = 20 * 60;
    private static final Map<UUID, Long> LAST_ACT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_MASK = new HashMap<>();
    private static final Map<UUID, Long> HINT_SAID = new HashMap<>();

    private static void tickHint(ServerLevel level, StormProgress progress) {
        UUID g = progress.group;
        long now = level.getGameTime();
        int mask = progress.receivers() & 7;
        Integer lastMask = LAST_MASK.put(g, mask);
        if (lastMask == null || lastMask != mask) {
            LAST_ACT.put(g, now);
            return;
        }
        long idle = now - LAST_ACT.computeIfAbsent(g, k -> now);
        if (idle < HINT_IDLE || mask == 7) {
            return;
        }
        List<Integer> path = solveFrom(progress.config(), progress.mirrorRots(), mask);
        if (path == null || path.isEmpty()) {
            return;
        }
        int[] m = progress.config().mirrors().get(path.get(0));
        BlockPos at = gridCell(m[0], m[1]);
        StormFx.to(g, () -> {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + now * 0.08;
                level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5 + Math.cos(a) * 0.7, at.getY() + 1.4, at.getZ() + 0.5 + Math.sin(a) * 0.7, 1, 0, 0.02, 0, 0.0);
            }
            level.sendParticles(ParticleTypes.GLOW, at.getX() + 0.5, at.getY() + 2.2, at.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.0);
        });
        if (now - HINT_SAID.getOrDefault(g, -10000L) > 600) {
            HINT_SAID.put(g, now);
            for (ServerPlayer p : Groups.online(level, g)) {
                if (StormBackLayout.inFront(p.position())) {
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.mirror_next"));
                }
            }
        }
    }

    // 光の筋の見た目 (25 の 2.2): 粒ではなく、光る細い箱 (block_display) をつなぐ。光の筋が変わったときだけ置き直す
    static final String BEAM_TAG = "skyabyss_storm_beam";
    private static final double BEAM_THICK = 0.14;
    private static String beamSig = "";

    static void clearBeams(ServerLevel level) {
        beam = List.of();
        beamSig = "";
        for (Entity e : level.getEntities((Entity) null, EYE_HALL.aabb().inflate(8, 6, 6), e -> e.getTags().contains(BEAM_TAG))) {
            e.discard();
        }
    }

    private static void box(ServerLevel level, String block, Vec3 c, double sx, double sy, double sz) {
        String snbt = String.format(java.util.Locale.ROOT,
                "{block_state:{Name:\"%s\"},brightness:{sky:15,block:15},Tags:[\"%s\"],view_range:2.0f,"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[%.3ff,%.3ff,%.3ff],scale:[%.3ff,%.3ff,%.3ff]},"
                        + "Pos:[%.3fd,%.3fd,%.3fd]}",
                block, BEAM_TAG, -sx / 2, -sy / 2, -sz / 2, sx, sy, sz, c.x, c.y, c.z);
        try {
            Entity e = net.minecraft.world.entity.EntityType.BLOCK_DISPLAY.create(level);
            if (e == null) {
                return;
            }
            e.load(net.minecraft.nbt.TagParser.parseTag(snbt));
            e.setPos(c.x, c.y, c.z);
            level.addFreshEntity(e);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            jp.revati.skyabyss.SkyAbyss.LOGGER.warn("storm beam display failed: {}", ex.getMessage());
        }
    }

    // 点の並び (入口の窓 → 曲がり角 → 壁) を、光る箱でつなぐ。曲がり角と終わりに小さな玉。止まった所は赤い玉
    private static void drawBeam(ServerLevel level, List<Vec3> pts, boolean day, @Nullable Vec3 stop, boolean hit) {
        StringBuilder sig = new StringBuilder(day ? "d" : "n").append(hit ? "h" : "").append(stop != null ? "s" : "");
        for (Vec3 p : pts) {
            sig.append((int) (p.x * 2)).append(',').append((int) (p.z * 2)).append(';');
        }
        if (sig.toString().equals(beamSig)) {
            return;
        }
        beamSig = sig.toString();
        for (Entity e : level.getEntities((Entity) null, EYE_HALL.aabb().inflate(8, 6, 6), e -> e.getTags().contains(BEAM_TAG))) {
            e.discard();
        }
        String body = hit ? "minecraft:white_concrete" : day ? "minecraft:glowstone" : "minecraft:sea_lantern";
        for (int i = 1; i < pts.size(); i++) {
            Vec3 a = pts.get(i - 1);
            Vec3 b = pts.get(i);
            Vec3 mid = a.add(b).scale(0.5);
            box(level, body, mid, Math.abs(b.x - a.x) + BEAM_THICK, BEAM_THICK, Math.abs(b.z - a.z) + BEAM_THICK);
        }
        for (int i = 1; i < pts.size() - 1; i++) {
            box(level, "minecraft:white_concrete", pts.get(i), 0.3, 0.3, 0.3);
        }
        if (stop != null) {
            box(level, "minecraft:redstone_block", stop, 0.38, 0.38, 0.38);
        }
    }

    // 世界の光の筋 (入口の窓から)
    private static void traceAndDraw(ServerLevel level, StormProgress progress) {
        Config cfg = progress.config();
        Trace t = trace(cfg.entry(), worldGrid(level, progress));
        List<Vec3> pts = new ArrayList<>();
        BlockPos in = entryPos(cfg);
        pts.add(Vec3.atCenterOf(in).add(0.4, 0, 0));
        // 通った格子: 真っ直ぐ通り抜ける所は点を足さない (鏡と終わりの点だけ)
        List<int[]> path = t.path();
        for (int i = 0; i < path.size(); i++) {
            int[] p = path.get(i);
            boolean last = i == path.size() - 1;
            if (last || cfg.mirrors().stream().anyMatch(m -> m[0] == p[0] && m[1] == p[1])) {
                pts.add(Vec3.atCenterOf(gridCell(p[0], p[1])));
            }
        }
        boolean reached = false;
        Vec3 stop = null;
        if (t.exit() != null) {
            Vec3 w = Vec3.atCenterOf(wallPos(t.exit()));
            Vec3 prev = pts.get(pts.size() - 1);
            pts.add(new Vec3(w.x - Math.signum(w.x - prev.x) * 0.45, w.y, w.z - Math.signum(w.z - prev.z) * 0.45));
            int i = cfg.receivers().indexOf(t.exit());
            reached = i >= 0;
            if (i >= 0 && (progress.receivers() & (1 << i)) == 0) {
                light(level, progress, cfg, i);
            }
        } else {
            stop = pts.get(pts.size() - 1);
        }
        beam = pts;
        drawBeam(level, pts, level.isDay(), stop, reached);
        long gt = level.getGameTime();
        // 受け羽: まだ光っていないものは、小さな光の粒を立てて「ここだ」と示す。近くを光が通ると少し揺れる
        for (int i = 0; i < cfg.receivers().size(); i++) {
            Vec3 w = Vec3.atCenterOf(wallPos(cfg.receivers().get(i)));
            if ((progress.receivers() & (1 << i)) != 0) {
                continue;
            }
            if (gt % 10 == 0) {
                StormFx.forced(level, ParticleTypes.END_ROD, w.x, w.y + 0.6, w.z, 1, 0.15, 0.1, 0.15, 0.01);
                StormFx.forced(level, ParticleTypes.END_ROD, w.x, w.y - 0.6, w.z, 1, 0.15, 0.1, 0.15, 0.01);
            }
            for (Vec3 p : pts) {
                if (p.distanceToSqr(w) < 16 && gt % 20 == 0) {
                    StormFx.forced(level, ParticleTypes.WHITE_ASH, w.x, w.y, w.z, 6, 0.3, 0.3, 0.3, 0.01);
                    break;
                }
            }
        }
    }

    static void light(ServerLevel level, StormProgress progress, Config cfg, int i) {
        progress.setReceivers(progress.receivers() | (1 << i));
        BlockPos p = wallPos(cfg.receivers().get(i));
        if (progress.group.equals(RoomLocks.owner(StormBackTrials.EYE_ROOM))) {
            level.setBlock(p, StormBackSetup.RECEIVER.get().defaultBlockState().setValue(StormBlocks.Receiver.LIT, true), 3);
        }
        level.playSound(null, p, StormSetup.RECEIVER_LIT.get(), SoundSource.BLOCKS, 2f, 1f + 0.15f * Integer.bitCount(progress.receivers()));
        StormFx.burst(level, ParticleTypes.END_ROD, Vec3.atCenterOf(p), 20, 0.4);
        for (ServerPlayer pl : Groups.online(level, progress.group)) {
            if (inFront(pl.position())) {
                Notice.bar(pl, ModText.progress("message.skyabyss.storm.feather_lit", Integer.bitCount(progress.receivers())));
            }
        }
        if (progress.receivers() == 7 && progress.mirrorStage() + 1 < progress.mirrorStagesNeeded()) {
            nextStage(level, progress);
            return;
        }
        if (progress.receivers() == 7) {
            GroupGates.open(level, progress.group, beakDoor());
            level.playSound(null, new BlockPos(0, EYE_HALL.floor() + 2, BEAK_DOOR_Z1), net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.7f);
            for (ServerPlayer pl : Groups.online(level, progress.group)) {
                if (inFront(pl.position())) {
                    Notice.bar(pl, ModText.progress("message.skyabyss.storm.beak_opened"));
                }
            }
        }
    }

    // 自己テスト用: 今の配置の受け羽を全部光らせる (light と同じ道)
    public static void testLightAll(ServerLevel level, StormProgress progress) {
        Config cfg = progress.config();
        for (int i = 0; i < cfg.receivers().size(); i++) {
            if ((progress.receivers() & (1 << i)) == 0) {
                light(level, progress, cfg, i);
                cfg = progress.config();
                if (progress.receivers() == 0) {
                    return;
                }
            }
        }
    }

    // 長い周の一つ目の配置を解いた: 受け羽を消し、次の配置 (今と違う 1〜3) に置き直す
    static void nextStage(ServerLevel level, StormProgress progress) {
        int cur = progress.mirrorConfig();
        int next = 1 + Math.floorMod(cur, CONFIGS.size() - 1);
        if (next == cur) {
            next = 1 + Math.floorMod(next, CONFIGS.size() - 1);
        }
        progress.setMirrorStage(progress.mirrorStage() + 1);
        progress.setMirrorConfig(next);
        progress.setReceivers(0);
        if (progress.group.equals(RoomLocks.owner(StormBackTrials.EYE_ROOM))) {
            apply(level, progress);
        }
        level.playSound(null, new BlockPos(0, EYE_HALL.floor() + 2, BEAK_DOOR_Z1), StormSetup.RECEIVER_LIT.get(), SoundSource.BLOCKS, 2f, 0.6f);
        for (ServerPlayer pl : Groups.online(level, progress.group)) {
            if (inFront(pl.position())) {
                Notice.narrate(pl, "message.skyabyss.storm.eye_turns");
            }
        }
    }

    // 羽鏡を回す (右クリック)。眼の間の持ち主のグループの人だけが回せる
    public static void turn(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(StormBackSetup.MIRROR.get())) {
            return;
        }
        UUID group = Groups.keyOf(player);
        if (!group.equals(RoomLocks.owner(StormBackTrials.EYE_ROOM))) {
            Notice.bar(player, ModText.deny("message.skyabyss.room_busy"));
            return;
        }
        StormProgress progress = StormProgress.of(level, group);
        if (turnFor(level, progress, pos, player.isShiftKeyDown() ? 3 : 1)) {
            // 回した直後に光の筋を引き直し、どうなったかを一行で言う (25 の 2.2)
            tick(level, progress);
            report(level, player, progress);
        }
    }

    // 光が今どうなっているか (回した人へ)
    static void report(ServerLevel level, ServerPlayer player, StormProgress progress) {
        Config cfg = progress.config();
        Trace t = trace(cfg.entry(), worldGrid(level, progress));
        String key;
        if (t.exit() != null) {
            int i = cfg.receivers().indexOf(t.exit());
            if (i < 0) {
                key = "message.skyabyss.storm.beam_wall";
            } else if ((progress.receivers() & (1 << i)) != 0) {
                key = "message.skyabyss.storm.beam_lit_already";
            } else {
                return;
            }
        } else {
            key = switch (t.stop()) {
                case BLOCKED -> "message.skyabyss.storm.beam_blocked";
                case LOOP -> "message.skyabyss.storm.beam_loop";
                default -> "message.skyabyss.storm.beam_back";
            };
        }
        Notice.bar(player, ModText.hint(key));
    }

    // 持ち主のグループの記録の鏡を一つ回し、世界の鏡も合わせる (自己テストからも)
    static boolean turnFor(ServerLevel level, StormProgress progress, BlockPos pos) {
        return turnFor(level, progress, pos, 1);
    }

    // step: 1 = 右へ 90 度、3 = 左へ 90 度 (しゃがんで右クリック)
    static boolean turnFor(ServerLevel level, StormProgress progress, BlockPos pos, int step) {
        Config cfg = progress.config();
        int index = -1;
        for (int i = 0; i < cfg.mirrors().size(); i++) {
            int[] m = cfg.mirrors().get(i);
            if (gridCell(m[0], m[1]).equals(pos)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        int rot = (progress.mirrorRots()[index] + step) % 4;
        progress.setMirrorRot(index, rot);
        LAST_ACT.put(progress.group, level.getGameTime());
        BlockState s = level.getBlockState(pos);
        if (s.is(StormBackSetup.MIRROR.get())) {
            level.setBlock(pos, s.setValue(StormBlocks.Mirror.ROT, rot), 3);
        }
        level.playSound(null, pos, StormSetup.MIRROR_TURN.get(), SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    // 戻しの鏡 (前室の取っ手)。グループごとに 30 秒に一度。鏡を最初の向きに戻す (光った受け羽は残る)
    public static boolean resetMirrors(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        UUID group = Groups.keyOf(player);
        long last = LAST_RESET.getOrDefault(group, (long) -RESET_COOLDOWN);
        if (now - last < RESET_COOLDOWN) {
            Notice.bar(player, ModText.deny("message.skyabyss.storm.mirrors_reset_wait", (RESET_COOLDOWN - (now - last) + 19) / 20));
            return false;
        }
        LAST_RESET.put(group, now);
        StormProgress progress = StormProgress.of(level, group);
        progress.resetMirrorRots();
        if (group.equals(RoomLocks.owner(StormBackTrials.EYE_ROOM))) {
            apply(level, progress);
        }
        Notice.bar(player, ModText.hint("message.skyabyss.storm.mirrors_reset"));
        level.playSound(null, player.blockPosition(), StormSetup.MIRROR_TURN.get(), SoundSource.BLOCKS, 1.5f, 0.6f);
        return true;
    }

    static void forget(UUID group) {
        LAST_RESET.remove(group);
        LAST_ACT.remove(group);
        LAST_MASK.remove(group);
        HINT_SAID.remove(group);
    }

    static void clear() {
        LAST_RESET.clear();
        LAST_ACT.clear();
        LAST_MASK.clear();
        HINT_SAID.clear();
        beam = List.of();
        beamSig = "";
    }

    public static List<Vec3> beam() {
        return beam;
    }
}
