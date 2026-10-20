package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.G;

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
import jp.revati.skyabyss.registry.ModBlocks;
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

// T03 くらげの回廊 (歩・渡る・★★。docs/design/29 の 3.2)。海の洞に浮かぶくらげの傘 (3×3) を足場に、向こうの岸へ渡る。
//   決まりを教える: 傘は乗っていると沈みはじめる (色が濃くなる)。沈んだ傘は少しで浮き上がる。止まらずに次の傘へ。手前の六つの傘と、中ほどの休み島まで。
//   ひねる: 休み島のあとは、「触手の線」(洞を横切る 2 ブロックの帯) が拍で払う。桃色に膨らんで 1.5 秒のあとに払う。帯の中にいなければ当たらない
//   (傘は帯をまたぐ間に置いてある。払う直後に跳ぶ)。落ちても水は浅く、泳いで傘や岸へ上がれる (傷はない。ただし帯の中の人は払われる)。
//   一人: 傘が沈むまでが長く、戻りが速く、払う間隔が長い。 二人以上で一つの傘に乗ると、沈むのが遅い (一人が押さえて二人目を渡す)。 四人から: 沈むのが速い。
//   「小魚」の障害は、ときどき一つの傘をつつく (予告の泡。つつかれた傘は倍の速さで沈む)。
// 修飾: 配置 3 (傘の並び)・数字 2 (傘の沈むまで・払う間隔)・障害 (小魚)・任意の目標 (一度も水に落ちずに)
public final class T03Jellies implements TrialCell {
    public static final int Y = G;
    public static final int BED = G - 4;
    public static final int X1 = 0;
    public static final int X2 = 16;
    public static final int WATER_Z1 = 79;
    public static final int WATER_Z2 = 107;
    public static final int FAR_Z1 = 108;
    // 傘の段 (中心の z)。前の三つは手前の水域、あとの三つは休み島のあと
    public static final int[] RANK_Z = {80, 84, 88, 96, 101, 106};
    // 配置ごとの、段ごとの傘の中心 x (二つずつ)。配置 1 は配置 0 の鏡
    static final int[][][] CENTERS = {
            {{3, 12}, {7, 14}, {4, 10}, {5, 11}, {8, 3}, {12, 6}},
            {},
            {{8, 14}, {4, 10}, {8, 13}, {3, 9}, {6, 13}, {9, 4}}};
    // 触手の線 (z の始まり。2 ブロック幅)
    public static final int[] ROW_Z = {98, 103};
    static final int[] SINK = {56, 36, 24};
    static final int[] SWEEP = {300, 220, 160};
    static final int SUNK_FOR = 90;
    static final int ROW_WARN = 30;
    static final int ROW_ZAP = 10;
    static final float ROW_DAMAGE = 4f;
    static final int NIBBLE_EVERY = 160;
    static final int NIBBLE_WARN = 24;
    static final int NIBBLE_LASTS = 140;

    private final Def def = TrialCatalog.get("T03");
    private final Ports ports = new Ports(TideDeck.T03_GATE, false, TideDeck.T03_EXIT, TideDeck.T03_STONE, TideDeck.T03_PAD, TideDeck.REST_H2,
            new net.minecraft.world.phys.AABB(0, G - 4, 75, 17, G + 11, 118), TideDeckRests.of("t03"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 形
    public static int centerX(int layout, int rank, int i) {
        return layout == 1 ? 16 - CENTERS[0][rank][i] : CENTERS[layout][rank][i];
    }

    public static int umbrellas() {
        return RANK_Z.length * 2;
    }

    // 傘 u (= 段 * 2 + i) の九つのブロック
    public static List<BlockPos> umbrella(int layout, int u) {
        int cx = centerX(layout, u / 2, u % 2);
        int cz = RANK_Z[u / 2];
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.add(new BlockPos(cx + dx, Y, cz + dz));
            }
        }
        return out;
    }

    public static BlockPos center(int layout, int u) {
        return new BlockPos(centerX(layout, u / 2, u % 2), Y, RANK_Z[u / 2]);
    }

    public static List<BlockPos> union() {
        Set<BlockPos> set = new java.util.LinkedHashSet<>();
        for (int l = 0; l < 3; l++) {
            for (int u = 0; u < umbrellas(); u++) {
                set.addAll(umbrella(l, u));
            }
        }
        return List.copyOf(set);
    }

    public static List<BlockPos> rowBed(int r) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = X1 + 1; x < X2; x++) {
            for (int z = ROW_Z[r]; z < ROW_Z[r] + 2; z++) {
                out.add(new BlockPos(x, BED, z));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 数字
    // 傘が沈むまで (tick)。一人は長く、四人からは短い
    public static int sinkAfter(Mods m, int players) {
        double s = SINK[m.number(0)] * (players <= 1 ? 1.35 : players >= 4 ? 0.8 : 1.0);
        return Abyss.shorter(m, (int) Math.round(s));
    }

    public static int sunkFor(int players) {
        return players <= 1 ? 60 : SUNK_FOR;
    }

    public static int sweepPeriod(Mods m, int players) {
        double p = SWEEP[m.number(1)] * (players <= 1 ? 1.25 : players >= 3 ? 0.85 : 1.0);
        return Abyss.shorter(m, (int) Math.round(p));
    }

    // 触手の線 r の位相 (0 〜 period - 1)。二本目は半周期ずれる
    public static int rowPhase(int r, long t, int period) {
        return (int) Math.floorMod(t + (long) r * period / 2, (long) period);
    }

    public static boolean rowWarn(int phase, int period) {
        return phase >= period - ROW_WARN - ROW_ZAP && phase < period - ROW_ZAP;
    }

    public static boolean rowZap(int phase, int period) {
        return phase >= period - ROW_ZAP;
    }

    // ---------------------------------------------------------------- 状態
    static final class S {
        long start;
        int[] stand;
        long[] sunkUntil;
        long[] nibbledUntil;
        final Set<UUID> wet = new HashSet<>();
        final long[] zapped = {-1, -1};
        long nextNibble;
        int nibbleU = -1;
        long nibbleAt;
        int falls;
        long view = -1;
    }

    private static S st(Run run) {
        return (S) run.data.get("s");
    }

    // 自己テスト用: いま小魚につつかれている傘の数
    public static int nibbledNow(Run run, long now) {
        if (!(run.data.get("s") instanceof S s)) {
            return 0;
        }
        int n = 0;
        for (long until : s.nibbledUntil) {
            n += now < until ? 1 : 0;
        }
        return n;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        S s = new S();
        s.start = back.getGameTime();
        int n = umbrellas();
        s.stand = new int[n];
        s.sunkUntil = new long[n];
        s.nibbledUntil = new long[n];
        s.nextNibble = s.start + 220;
        run.put("s", s);
        int layout = run.mods.layout();
        Set<BlockPos> mine = new HashSet<>();
        for (int u = 0; u < n; u++) {
            mine.addAll(umbrella(layout, u));
        }
        List<BlockPos> idle = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!mine.contains(p)) {
                idle.add(p);
            }
        }
        GroupGates.open(back, run.group, idle);
        // 傘は全部立っている (最初)
        for (int u = 0; u < n; u++) {
            GroupGates.seal(back, run.group, umbrella(layout, u));
        }
        if (run.cleared) {
            GroupGates.open(back, run.group, TideDeck.T03_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        GroupGates.release(back, run.group, union());
        if (!run.cleared) {
            GroupGates.release(back, run.group, TideDeck.T03_EXIT);
        }
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        S s = st(run);
        int layout = m.layout();
        int n = Math.max(1, inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t03.start"));
        }
        int sink = sinkAfter(m, n);
        long t = now - s.start;
        // 傘: 乗っていると沈む
        long mask = 0;
        boolean changed = false;
        for (int u = 0; u < s.stand.length; u++) {
            BlockPos c = center(layout, u);
            int on = 0;
            for (ServerPlayer p : inside) {
                if (Math.abs(p.getX() - (c.getX() + 0.5)) < 1.9 && Math.abs(p.getZ() - (c.getZ() + 0.5)) < 1.9 && p.getY() >= Y + 0.9 && p.getY() < Y + 2.2) {
                    on++;
                }
            }
            boolean sunk = now < s.sunkUntil[u];
            boolean nibbled = now < s.nibbledUntil[u];
            if (sunk) {
                s.stand[u] = 0;
                mask |= 1L << u;
                continue;
            }
            if (on > 0) {
                // 二人以上で乗ると沈むのが遅い (押さえ合う)
                double rate = (nibbled ? 2.0 : 1.0) / (1.0 + 0.5 * (on - 1));
                s.stand[u] += (int) Math.round(5 * rate);
                if (s.stand[u] >= sink) {
                    s.sunkUntil[u] = now + sunkFor(n);
                    s.stand[u] = 0;
                    GroupGates.open(back, run.group, umbrella(layout, u));
                    changed = true;
                    TrialFx.sound(back, run.group, c, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.3f, 0.6f);
                    TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_POP, c.getX() + 0.5, Y + 1.2, c.getZ() + 0.5, 14, 1.0, 0.2, 1.0, 0.05);
                    mask |= 1L << u;
                    continue;
                } else if (s.stand[u] >= sink * 0.45) {
                    mask |= 1L << (u + 16);
                }
            } else {
                s.stand[u] = Math.max(0, s.stand[u] - 4);
            }
            if (nibbled) {
                mask |= 1L << (u + 32);
            }
        }
        // 沈みから浮き上がる
        for (int u = 0; u < s.sunkUntil.length; u++) {
            if (s.sunkUntil[u] > 0 && now >= s.sunkUntil[u]) {
                s.sunkUntil[u] = 0;
                GroupGates.seal(back, run.group, umbrella(layout, u));
                TrialFx.particles(back, run.group, ParticleTypes.BUBBLE, center(layout, u).getX() + 0.5, Y + 0.6, center(layout, u).getZ() + 0.5, 12, 1.0, 0.2, 1.0, 0.1);
                changed = true;
            }
        }
        // 触手の線
        int period = sweepPeriod(m, n);
        for (int r = 0; r < 2; r++) {
            int ph = rowPhase(r, t, period);
            if (rowWarn(ph, period)) {
                mask |= 1L << (48 + r);
                if (now % 10 == 0) {
                    TrialFx.particles(back, run.group, ParticleTypes.GLOW, 8.5, Y + 1.0, ROW_Z[r] + 1.0, 14, 7.5, 0.5, 0.8, 0.02);
                }
            } else if (rowZap(ph, period)) {
                mask |= 1L << (50 + r);
                long cycle = Math.floorDiv(t + (long) r * period / 2, (long) period);
                if (s.zapped[r] != cycle) {
                    s.zapped[r] = cycle;
                    TrialFx.sound(back, run.group, new BlockPos(8, Y, ROW_Z[r]), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 1.6f);
                    TrialFx.particles(back, run.group, ParticleTypes.ELECTRIC_SPARK, 8.5, Y + 1.0, ROW_Z[r] + 1.0, 40, 7.5, 0.7, 0.8, 0.3);
                    for (ServerPlayer p : inside) {
                        if (p.getZ() >= ROW_Z[r] && p.getZ() < ROW_Z[r] + 2 && p.getY() < Y + 4) {
                            p.hurt(p.damageSources().generic(), ROW_DAMAGE);
                            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.25, p.getZ() < ROW_Z[r] + 1 ? -0.45 : 0.45));
                            p.hurtMarked = true;
                        }
                    }
                }
            }
        }
        // 小魚 (障害): 一つの傘をつつく。予告の泡 → つつかれた傘は倍の速さで沈む
        if (m.hasHazard(0) && !run.cleared) {
            if (s.nibbleU >= 0) {
                BlockPos c = center(layout, s.nibbleU);
                if (now < s.nibbleAt) {
                    TrialFx.particles(back, run.group, ParticleTypes.BUBBLE, c.getX() + 0.5, Y + 0.6, c.getZ() + 0.5, 6, 1.2, 0.2, 1.2, 0.1);
                } else {
                    s.nibbledUntil[s.nibbleU] = now + NIBBLE_LASTS;
                    TrialFx.sound(back, run.group, c, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0f, 1.8f);
                    s.nibbleU = -1;
                }
            } else if (now >= s.nextNibble) {
                List<Integer> options = new ArrayList<>();
                for (int u = 0; u < s.stand.length; u++) {
                    if (now >= s.sunkUntil[u] && now >= s.nibbledUntil[u]) {
                        options.add(u);
                    }
                }
                if (!options.isEmpty()) {
                    s.nibbleU = options.get(new Random(run.seed ^ now).nextInt(options.size()));
                    s.nibbleAt = now + NIBBLE_WARN;
                    s.nextNibble = now + (n >= 3 ? NIBBLE_EVERY * 3 / 4 : NIBBLE_EVERY);
                } else {
                    s.nextNibble = now + 40;
                }
            }
        }
        if (changed || mask != s.view) {
            s.view = mask;
            TrialRuntime.refresh(back, run);
        }
        // 水に落ちた (任意の目標)
        for (ServerPlayer p : inside) {
            boolean swimming = p.getZ() >= WATER_Z1 && p.getZ() < FAR_Z1 && p.getY() < Y + 0.6 && p.getY() > BED && !onIslet(p);
            if (swimming && s.wet.add(p.getUUID())) {
                s.falls++;
                run.put("falls", s.falls);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
                Notice.bar(p, ModText.hint("message.skyabyss.trial.t03.splash"));
            } else if (!swimming) {
                s.wet.remove(p.getUUID());
            }
        }
        // 向こうの岸に着いた
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= FAR_Z1 && p.getY() >= Y + 0.9 && p.onGround()) {
                    run.cleared = true;
                    run.clearedAt = now;
                    GroupGates.open(back, run.group, TideDeck.T03_EXIT);
                    TrialFx.sound(back, run.group, TideDeck.T03_STONE, SoundEvents.BELL_RESONATE, 1.5f, 1.1f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.t03.crossed"));
                    TrialRuntime.refresh(back, run);
                    break;
                }
            }
        }
    }

    private static boolean onIslet(ServerPlayer p) {
        return TideDeck.T03_ISLET.contains(p.position());
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        if (!(run.data.get("s") instanceof S s) || s.view < 0) {
            return;
        }
        long view = s.view;
        int layout = run.mods.layout();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState glass = Blocks.PINK_STAINED_GLASS.defaultBlockState();
        BlockState dark = Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
        BlockState nibbled = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        BlockState core = Blocks.SEA_LANTERN.defaultBlockState();
        for (BlockPos p : union()) {
            out.accept(p, water);
        }
        for (int u = 0; u < umbrellas(); u++) {
            if ((view & (1L << u)) != 0) {
                continue;
            }
            boolean sinking = (view & (1L << (u + 16))) != 0;
            boolean nib = (view & (1L << (u + 32))) != 0;
            BlockState face = sinking ? dark : nib ? nibbled : glass;
            BlockPos c = center(layout, u);
            for (BlockPos p : umbrella(layout, u)) {
                out.accept(p, p.equals(c) && !sinking ? core : face);
            }
        }
        BlockState rowWarn = Blocks.PINK_CONCRETE.defaultBlockState();
        BlockState rowZap = Blocks.RED_CONCRETE.defaultBlockState();
        for (int r = 0; r < 2; r++) {
            if ((view & (1L << (48 + r))) != 0) {
                for (BlockPos p : rowBed(r)) {
                    out.accept(p, rowWarn);
                }
            } else if ((view & (1L << (50 + r))) != 0) {
                for (BlockPos p : rowBed(r)) {
                    out.accept(p, rowZap);
                }
            }
        }
    }

    // 傘から傘へ、岸と休み島へ、泳がずに渡れるか (縁の間の距離が跳べる範囲か)。audit と自己テストが見る
    public static boolean crossable(int layout) {
        // 渡れる傘の組 (縁の最短距離が 2.9 以下) の連結で、手前の岸 (z 78) から休み島 (z 91)・向こうの岸 (z 108) まで届く
        List<int[]> boxes = new ArrayList<>();
        for (int u = 0; u < umbrellas(); u++) {
            BlockPos c = center(layout, u);
            boxes.add(new int[] {c.getX() - 1, c.getX() + 1, c.getZ() - 1, c.getZ() + 1});
        }
        int near = boxes.size();
        boxes.add(new int[] {0, 16, 75, 78});
        int islet = boxes.size();
        boxes.add(new int[] {5, 11, 91, 94});
        int far = boxes.size();
        boxes.add(new int[] {0, 16, 108, 110});
        boolean[] seen = new boolean[boxes.size()];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        seen[near] = true;
        q.add(near);
        while (!q.isEmpty()) {
            int a = q.poll();
            for (int b = 0; b < boxes.size(); b++) {
                if (seen[b]) {
                    continue;
                }
                int[] p = boxes.get(a);
                int[] r = boxes.get(b);
                int gx = Math.max(0, Math.max(p[0] - r[1], r[0] - p[1]) - 1);
                int gz = Math.max(0, Math.max(p[2] - r[3], r[2] - p[3]) - 1);
                // 手前の岸と休み島の列のあいだは、跳ぶ距離 (空きの数) が三つまで
                if (Math.hypot(gx, gz) <= 2.9) {
                    seen[b] = true;
                    q.add(b);
                }
            }
        }
        return seen[islet] && seen[far];
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!back.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                bad.add("T03 umbrella block missing at " + p);
                break;
            }
        }
        for (int l = 0; l < 3; l++) {
            Set<BlockPos> seen = new HashSet<>();
            for (int u = 0; u < umbrellas(); u++) {
                for (BlockPos p : umbrella(l, u)) {
                    if (!seen.add(p)) {
                        bad.add("T03 layout " + l + ": umbrellas overlap at " + p);
                    }
                    if (p.getX() < X1 + 1 || p.getX() > X2 - 1) {
                        bad.add("T03 layout " + l + ": an umbrella touches the wall at " + p);
                    }
                }
            }
            if (!crossable(l)) {
                bad.add("T03 layout " + l + ": the umbrellas are too far apart to cross");
            }
            // 触手の線は、傘と重ならない (傘は線の外)
            for (int r = 0; r < 2; r++) {
                for (int x = X1; x <= X2; x++) {
                    for (int z = ROW_Z[r]; z < ROW_Z[r] + 2; z++) {
                        if (seen.contains(new BlockPos(x, Y, z))) {
                            bad.add("T03 layout " + l + ": an umbrella stands in tentacle row " + r);
                            return bad;
                        }
                    }
                }
            }
        }
        for (int r = 0; r < 2; r++) {
            for (BlockPos p : rowBed(r)) {
                if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).is(Blocks.WATER)) {
                    bad.add("T03 tentacle bed is missing or dry at " + p);
                    return bad;
                }
            }
        }
        // 触手の線は、どの周期でも、一本ごとに 2 秒以上の静かな時間が残る (跳ぶのに 0.6 秒)。いちばん短い周期 = 一番きびしい数字 × 三人から ÷ 深淵
        int shortest = (int) Math.round(SWEEP[2] * 0.85 / Abyss.NUMBER_STEP);
        if (shortest - ROW_WARN - ROW_ZAP < 60) {
            bad.add("T03 the tentacle rows leave too little time (" + (shortest - ROW_WARN - ROW_ZAP) + " ticks)");
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(TideDeck.T03_STONE))) {
            bad.add("T03 stone missing");
        }
        return bad;
    }
}
