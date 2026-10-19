package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.F;

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
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.tide.back.TideBackState;
import jp.revati.skyabyss.titan.tide.back.TideClock;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialBacks;
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

// T02 潮の背渡り (歩・拍・★★。docs/design/29 の 3.2)。入江 (中ほどに中島) を渡って向こうの岸へ。道は潮で変わる:
//   引き潮のあいだは砂州 (幅 3 の砂の道)、満ち潮のあいだは浮き石 (2×2 の飛び石)。潮の変わりめには、二つの道が少しのあいだ重なる (砂州が沈む 1.5 秒前に砂が白く瞬く)。
//   どちらの道も渡れる (満ちていても、道が違うだけ)。落ちても入江は浅い水で、泳いで岸へ上がれる (傷はない)。
//   決まりを教える: 手前の入江は、まっすぐな砂州と、離れた浮き石。 ひねる: 中島のあとの入江は、砂州が曲がりくねり、浮き石は三つ間をおく。
//   浮き石は、乗りつづけると沈む (3.5 秒。沈む前に灯りが暗くなる) ので、止まらずに渡る。二人以上は一つの石に重なると速く沈む。
//   一人: 沈むまでが長く、道が重なる時間も長い。 四人から: 沈むのが速い。 「渦」の障害は、道にいる人を横へ押す (予告つき)。
// 潮は島の潮の時計 (TideClock) を使う (潮の速さの数字で、この入江の潮だけ速くなる)。
// 修飾: 配置 3 (砂州と浮き石の並び)・数字 2 (道の重なり・潮の速さ)・障害 (渦)・任意の目標 (一度も水に落ちずに)
public final class T02Crossing implements TrialCell {
    public static final int Y = F;
    // 配置ごとの砂州・浮き石の座標 (x, z)。0 = 西が砂州・東が浮き石 / 1 = その鏡 / 2 = 砂州は真ん中、浮き石は両側
    // 砂州 = {中心線の折れ線 (x, z, x, z, ...), 幅 3}。浮き石 = 2×2 の北西の隅
    static final int[][][] BAR_LINES = {
            // 配置 0: 入江の手前・奥
            {{6, 55, 6, 67}, {9, 66, 5, 66}, {12, 72, 12, 76}, {12, 76, 6, 76}, {6, 76, 6, 81}, {6, 81, 12, 81}, {12, 81, 12, 86}},
            // 配置 1 (鏡) は配置 0 から作る
            {},
            // 配置 2
            {{12, 55, 12, 67}, {12, 72, 12, 77}, {12, 77, 16, 77}, {16, 77, 16, 82}, {16, 82, 10, 82}, {10, 82, 10, 86}}};
    static final int[][][] STONES = {
            {{16, 56}, {18, 59}, {16, 62}, {14, 65}, {16, 73}, {18, 76}, {16, 79}, {18, 82}, {16, 85}},
            {},
            {{5, 56}, {7, 59}, {5, 62}, {7, 65}, {17, 56}, {19, 59}, {17, 62}, {15, 65}, {6, 73}, {4, 76}, {6, 79}, {4, 82}, {6, 85}, {18, 73}, {20, 76}, {18, 79},
                    {20, 82}, {18, 85}}};
    // 重なり (tick)・潮の速さ (やさしい / 標準 / きびしい)
    static final int[] OVERLAP = {130, 90, 50};
    static final double[] SPEED = {1.0, 1.5, 2.0};
    static final int STONE_APPEAR = 1020;
    static final int BAR_APPEAR = 2300;
    static final int WARN = 30;
    static final int SINK_BASE = 70;
    static final int SUNK_FOR = 100;
    static final int EDDY_EVERY = 150;
    static final int EDDY_WARN = 24;

    private final Def def = TrialCatalog.get("T02");
    private final Ports ports = new Ports(TideDeck.T02_GATE, false, List.of(), TideDeck.T02_STONE, TideDeck.T02_PAD, TideDeck.REST_H, TideDeck.T02.aabb(),
            TideDeckRests.of("t02"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 形
    // 配置 layout の砂州のブロック (y = 74)
    public static List<BlockPos> bars(int layout) {
        Set<BlockPos> set = new LinkedHashSet<>();
        int[][] lines = layout == 1 ? BAR_LINES[0] : BAR_LINES[layout];
        for (int[] l : lines) {
            int x1 = Math.min(l[0], l[2]);
            int x2 = Math.max(l[0], l[2]);
            int z1 = Math.min(l[1], l[3]);
            int z2 = Math.max(l[1], l[3]);
            boolean vertical = l[0] == l[2];
            for (int x = x1 - (vertical ? 1 : 0); x <= x2 + (vertical ? 1 : 0); x++) {
                for (int z = z1 - (vertical ? 0 : 1); z <= z2 + (vertical ? 0 : 1); z++) {
                    set.add(new BlockPos(layout == 1 ? 24 - x : x, Y, z));
                }
            }
        }
        return List.copyOf(set);
    }

    // 配置 layout の浮き石 i の四つのブロック
    public static List<BlockPos> stone(int layout, int i) {
        int[][] st = layout == 1 ? STONES[0] : STONES[layout];
        int x0 = layout == 1 ? 23 - st[i][0] : st[i][0];
        int z0 = st[i][1];
        return List.of(new BlockPos(x0, Y, z0), new BlockPos(x0 + 1, Y, z0), new BlockPos(x0, Y, z0 + 1), new BlockPos(x0 + 1, Y, z0 + 1));
    }

    public static int stones(int layout) {
        return (layout == 1 ? STONES[0] : STONES[layout]).length;
    }

    static List<BlockPos> allStones(int layout) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < stones(layout); i++) {
            out.addAll(stone(layout, i));
        }
        return out;
    }

    public static List<BlockPos> union() {
        Set<BlockPos> set = new LinkedHashSet<>();
        for (int l = 0; l < 3; l++) {
            set.addAll(bars(l));
            set.addAll(allStones(l));
        }
        return List.copyOf(set);
    }

    // ---------------------------------------------------------------- 潮 (入江の周期の中の位置 0〜2399)
    public static int cycle(ServerLevel level, Mods m) {
        TideClock.Phase forced = TideClock.forced();
        double speed = SPEED[m.number(1)];
        if (m.abyss()) {
            speed *= Abyss.NUMBER_STEP;
        }
        if (forced != null) {
            return switch (forced) {
                case LOW -> 500;
                case RISING -> 1100;
                case HIGH -> 1700;
                case FALLING -> 2300;
            };
        }
        return Math.floorMod((int) (TideBackState.get(level).clock() * speed), TideClock.CYCLE);
    }

    public static int overlap(Mods m) {
        int o = OVERLAP[m.number(0)];
        return m.abyss() ? Math.max(40, (int) Math.round(o / Abyss.NUMBER_STEP)) : o;
    }

    // 周期の中の位置 c が、start から len tick の窓 (周期をまたいでよい) の中か
    static boolean inWindow(int c, int start, int len) {
        return Math.floorMod(c - start, TideClock.CYCLE) < len;
    }

    // 砂州が立っているか (c = 周期の中の位置)。満ちてゆく途中の少しまで立ち、引いてゆく途中から立つ
    public static boolean barSolid(int c, int overlap) {
        return inWindow(c, BAR_APPEAR, TideClock.CYCLE - BAR_APPEAR + STONE_APPEAR + overlap);
    }

    // 浮き石が立っているか。満ちはじめて少しで現れ、引いてゆく途中の少しまで立つ
    public static boolean stoneSolid(int c, int overlap) {
        return inWindow(c, STONE_APPEAR, BAR_APPEAR + overlap - STONE_APPEAR);
    }

    // 砂州が消える 30 tick 前 / 浮き石が消える 30 tick 前
    public static boolean barWarn(int c, int overlap) {
        return inWindow(c, STONE_APPEAR + overlap - WARN, WARN);
    }

    public static boolean stoneWarn(int c, int overlap) {
        return inWindow(c, Math.floorMod(BAR_APPEAR + overlap - WARN, TideClock.CYCLE), WARN);
    }

    public static int sinkAfter(Mods m, int players) {
        double s = SINK_BASE * (players <= 1 ? 1.3 : players >= 4 ? 0.75 : 1.0);
        return Abyss.shorter(m, (int) Math.round(s));
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        int layout = run.mods.layout();
        Set<BlockPos> mine = new LinkedHashSet<>(bars(layout));
        mine.addAll(allStones(layout));
        List<BlockPos> idle = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!mine.contains(p)) {
                idle.add(p);
            }
        }
        GroupGates.open(back, run.group, idle);
        int n = stones(layout);
        run.put("stand", new int[n]);
        run.put("sunkUntil", new long[n]);
        run.put("view", -1L);
        run.put("barOpen", -1);
        run.put("nextEddy", back.getGameTime() + 160L);
        syncGates(back, run, back.getGameTime());
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        GroupGates.release(back, run.group, union());
    }

    // 砂州・浮き石の戸を、いまの潮に合わせる (変わったものだけ書く)
    private void syncGates(ServerLevel back, Run run, long now) {
        int layout = run.mods.layout();
        int c = cycle(back, run.mods);
        int o = overlap(run.mods);
        boolean bar = barSolid(c, o);
        if (run.getInt("barOpen") != (bar ? 0 : 1)) {
            run.put("barOpen", bar ? 0 : 1);
            GroupGates.set(back, run.group, bars(layout), !bar);
            TrialFx.sound(back, run.group, new BlockPos(12, Y + 1, 70), bar ? SoundEvents.SAND_PLACE : SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2f, 0.7f);
        }
        boolean stonesUp = stoneSolid(c, o);
        long[] sunk = (long[]) run.data.get("sunkUntil");
        Long known = (Long) run.data.get("stoneOpen");
        long open = known == null ? 0L : known;
        for (int i = 0; i < sunk.length; i++) {
            boolean wantOpen = !(stonesUp && now >= sunk[i]);
            boolean isOpen = (open & (1L << i)) != 0;
            if (known == null || wantOpen != isOpen) {
                GroupGates.set(back, run.group, stone(layout, i), wantOpen);
                open = wantOpen ? open | (1L << i) : open & ~(1L << i);
            }
        }
        run.put("stoneOpen", open);
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int layout = m.layout();
        int n = Math.max(1, inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t02.start"));
        }
        int c = cycle(back, m);
        int o = overlap(m);
        // 浮き石: 乗りつづけると沈む
        int[] stand = (int[]) run.data.get("stand");
        long[] sunk = (long[]) run.data.get("sunkUntil");
        int after = sinkAfter(m, n);
        long mask = 0;
        for (int i = 0; i < stand.length; i++) {
            int on = 0;
            List<BlockPos> cells = stone(layout, i);
            double cx = cells.get(0).getX() + 1.0;
            double cz = cells.get(0).getZ() + 1.0;
            for (ServerPlayer p : inside) {
                if (Math.abs(p.getX() - cx) < 1.5 && Math.abs(p.getZ() - cz) < 1.5 && p.getY() >= Y + 0.9 && p.getY() < Y + 2.2) {
                    on++;
                }
            }
            if (now < sunk[i]) {
                stand[i] = 0;
                mask |= 1L << (i + 8);
                continue;
            }
            if (on > 0 && stoneSolid(c, o)) {
                stand[i] += 5 * on;
                if (stand[i] >= after) {
                    sunk[i] = now + SUNK_FOR;
                    stand[i] = 0;
                    TrialFx.sound(back, run.group, cells.get(0), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.3f, 0.6f);
                    TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_POP, cx, Y + 1.2, cz, 10, 0.6, 0.2, 0.6, 0.05);
                } else if (stand[i] >= after - 30) {
                    mask |= 1L << (i + 28);
                }
            } else {
                stand[i] = Math.max(0, stand[i] - 5);
            }
        }
        syncGates(back, run, now);
        mask |= barWarn(c, o) && barSolid(c, o) ? 1L : 0L;
        mask |= stoneWarn(c, o) && stoneSolid(c, o) ? 2L : 0L;
        mask |= barSolid(c, o) ? 4L : 0L;
        mask |= stoneSolid(c, o) ? 16L : 0L;
        mask |= run.cleared ? 1L << 50 : 0L;
        if (mask != run.getLong("view")) {
            run.put("view", mask);
            TrialRuntime.refresh(back, run);
        }
        // 渦 (障害): 道にいる人のそばに予告の泡 → 横へ押す
        if (m.hasHazard(0) && !run.cleared) {
            long warnAt = run.getLong("eddyAt");
            if (warnAt > 0) {
                BlockPos at = (BlockPos) run.data.get("eddyPos");
                if (now < warnAt) {
                    TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_COLUMN_UP, at.getX() + 0.5, Y + 0.6, at.getZ() + 0.5, 10, 0.6, 0.3, 0.6, 0.1);
                } else {
                    TrialFx.particles(back, run.group, ParticleTypes.SPLASH, at.getX() + 0.5, Y + 1.0, at.getZ() + 0.5, 30, 1.0, 0.3, 1.0, 0.3);
                    TrialFx.sound(back, run.group, at, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.6f);
                    int dir = new Random(run.seed ^ now).nextBoolean() ? 1 : -1;
                    for (ServerPlayer p : inside) {
                        if (Math.hypot(p.getX() - (at.getX() + 0.5), p.getZ() - (at.getZ() + 0.5)) < 2.2 && p.getY() >= Y + 0.5) {
                            p.setDeltaMovement(p.getDeltaMovement().add(0.55 * dir, 0.25, 0));
                            p.hurtMarked = true;
                        }
                    }
                    run.put("eddyAt", 0L);
                }
            } else if (now >= run.getLong("nextEddy")) {
                List<ServerPlayer> onRoute = new ArrayList<>();
                for (ServerPlayer p : inside) {
                    if (p.getZ() >= 55 && p.getZ() < 87 && p.getY() >= Y + 0.9) {
                        onRoute.add(p);
                    }
                }
                if (!onRoute.isEmpty()) {
                    ServerPlayer target = onRoute.get(new Random(run.seed ^ now).nextInt(onRoute.size()));
                    run.put("eddyPos", target.blockPosition());
                    run.put("eddyAt", now + EDDY_WARN);
                    run.put("nextEddy", now + (n >= 3 ? EDDY_EVERY * 3 / 4 : EDDY_EVERY));
                } else {
                    run.put("nextEddy", now + 40L);
                }
            }
        }
        // 水に落ちた (任意の目標)
        Set<UUID> wet = wetSet(run);
        for (ServerPlayer p : inside) {
            boolean swimming = p.getZ() >= 55 && p.getZ() < 87 && p.getY() < Y + 0.4 && p.isInWater();
            if (swimming && wet.add(p.getUUID())) {
                run.put("falls", run.getInt("falls") + 1);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
                Notice.bar(p, ModText.hint("message.skyabyss.trial.t02.splash"));
            } else if (!swimming) {
                wet.remove(p.getUUID());
            }
        }
        // 向こうの岸に着いた
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= TideDeck.T02_FAR_Z1 && p.getY() >= Y + 0.9 && p.onGround()) {
                    run.cleared = true;
                    run.clearedAt = now;
                    TrialFx.sound(back, run.group, TideDeck.T02_STONE, SoundEvents.BELL_RESONATE, 1.5f, 1.1f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.t02.crossed"));
                    TrialRuntime.refresh(back, run);
                    break;
                }
            }
        }
    }

    private static Set<UUID> wetSet(Run run) {
        return run.get("wet", () -> new java.util.HashSet<UUID>());
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int layout = run.mods.layout();
        long view = Math.max(0, run.getLong("view"));
        boolean barWarn = (view & 1L) != 0;
        boolean stoneWarn = (view & 2L) != 0;
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState warn = Blocks.CALCITE.defaultBlockState();
        BlockState stoneState = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState sinking = Blocks.DARK_PRISMARINE.defaultBlockState();
        // 立っていない道のブロックは、水の面 (入江の水は床の面と同じ高さまで。扉のブロックが消えた所を水で埋めて見せる)
        BlockState water = Blocks.WATER.defaultBlockState();
        for (BlockPos p : union()) {
            out.accept(p, water);
        }
        if ((view & 4L) != 0) {
            for (BlockPos p : bars(layout)) {
                out.accept(p, barWarn ? warn : sand);
            }
        }
        if ((view & 16L) != 0) {
            for (int i = 0; i < stones(layout); i++) {
                if ((view & (1L << (i + 8))) != 0) {
                    continue;
                }
                BlockState s = stoneWarn ? warn : (view & (1L << (i + 28))) != 0 ? sinking : stoneState;
                for (BlockPos p : stone(layout, i)) {
                    out.accept(p, s);
                }
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!back.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                bad.add("T02 route block missing at " + p);
                break;
            }
        }
        for (int l = 0; l < 3; l++) {
            Set<BlockPos> bs = new LinkedHashSet<>(bars(l));
            for (BlockPos p : allStones(l)) {
                if (bs.contains(p)) {
                    bad.add("T02 layout " + l + ": a floating stone overlaps the sandbar at " + p);
                    break;
                }
            }
            if (!barsConnect(l)) {
                bad.add("T02 layout " + l + ": the sandbar does not run from the near shore through the islet to the far shore");
            }
            if (!stonesConnect(l)) {
                bad.add("T02 layout " + l + ": the floating stones are too far apart to cross");
            }
        }
        // 潮の時計: 砂州と浮き石のどちらかは、いつでも立っている。重なりは警告より長い
        for (int ov : OVERLAP) {
            for (int c = 0; c < TideClock.CYCLE; c++) {
                if (!barSolid(c, ov) && !stoneSolid(c, ov)) {
                    bad.add("T02: no route is up at tide position " + c + " (overlap " + ov + ")");
                    break;
                }
            }
            if (ov < WARN + 10) {
                bad.add("T02: the overlap " + ov + " is not longer than the warning");
            }
        }
        if (!back.getBlockState(TideDeck.T02_STONE).is(jp.revati.skyabyss.titan.tide.back.TideBackSetup.CHIME_STONE.get())) {
            bad.add("T02 stone missing");
        }
        return bad;
    }

    // 砂州が、手前の岸 (z 54) から中島 (z 68〜71) を通って向こうの岸 (z 87) まで、つながっているか (四方の隣で)
    static boolean barsConnect(int layout) {
        Set<BlockPos> cells = new LinkedHashSet<>(bars(layout));
        // 岸・中島を足す
        for (int x = 2; x <= 22; x++) {
            cells.add(new BlockPos(x, Y, 54));
            cells.add(new BlockPos(x, Y, 87));
        }
        for (int x = 8; x <= 16; x++) {
            for (int z = 68; z <= 71; z++) {
                cells.add(new BlockPos(x, Y, z));
            }
        }
        return flood(cells, new BlockPos(12, Y, 54), new BlockPos(12, Y, 87), 1);
    }

    // 浮き石: 岸・中島・ほかの石へ、間の空きが 1 マスまで (斜めは 1 と 1) で跳べる
    static boolean stonesConnect(int layout) {
        Set<BlockPos> cells = new LinkedHashSet<>(allStones(layout));
        for (int x = 2; x <= 22; x++) {
            cells.add(new BlockPos(x, Y, 54));
            cells.add(new BlockPos(x, Y, 87));
        }
        for (int x = 8; x <= 16; x++) {
            for (int z = 68; z <= 71; z++) {
                cells.add(new BlockPos(x, Y, z));
            }
        }
        return flood(cells, new BlockPos(12, Y, 54), new BlockPos(12, Y, 87), 2);
    }

    // reach = 1: 四方の隣。reach = 2: 間に空きが一つまで (dx と dz がともに 2 以内で、一方は 0 か 1 か 2)
    static boolean flood(Set<BlockPos> cells, BlockPos from, BlockPos to, int reach) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (p.getZ() >= to.getZ() && cells.contains(p)) {
                return true;
            }
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx == 0 && dz == 0 || reach == 1 && Math.abs(dx) + Math.abs(dz) != 1 || reach == 2 && Math.abs(dx) + Math.abs(dz) > 3) {
                        continue;
                    }
                    BlockPos q = p.offset(dx, 0, dz);
                    if (cells.contains(q) && seen.add(q)) {
                        queue.add(q);
                    }
                }
            }
        }
        return false;
    }
}
