package jp.revati.skyabyss.trial.whale;

import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// W08 影絵の間 (智・解く・★★★。docs/design/29 の 3.1)。灯 (奥の台) と骨の柱 (まんなかの台) を動かして、北の壁の「幕」に影を落とし、上に出る目標の紋へ重ねる。
//   決まりを教える (一つ目): 灯が一つ・柱が一つ。柱が灯と壁のあいだにあると、壁に三つ分の影が落ちる。灯を右へ一つ動かすと影は左へ一つ、
//   柱を右へ一つ動かすと影は右へ二つ動く (柱は灯より壁に近いので、影は柱の二倍に伸びる)。光の筋 (粒) が灯から柱の縁を通って壁へ届くので、見て確かめられる。
//   ひねる (二つ目): 灯が二つ。幕は、どちらの灯からも遮られた所だけが影になる (二つの影の重なり)。
//   三つ目 (四人からは四つ目): 灯と柱がそれぞれ複数。紋は、重なりと並びで決まる (配置で、紋の山の数が 1・2・3)。
//   合わせたら 2 秒そのままにすると定まる。台 (北の壁の下) のどれかを右クリックで、灯・柱をその位置へ動かす (灯の台は右の五つの列、柱の台は左の五つの列。段が一つずつの駒)。
//   一人: 灯と柱は一つずつ動かすだけ。 三人から: 最後の幕の柱が増える・四人から一幕多い (灯を動かす人・柱を動かす人・見る人に分かれる)。
//   「隙間風」の障害は、ときどき灯の一つが一つずれる (灯がまたたいて予告)。
// 修飾: 配置 3 (最後の幕の紋の山の数)・数字 2 (最後の幕の灯と柱の数 / 光の筋の見え方: 出しっぱなし・動かして 8 秒・4 秒)・障害 (隙間風)・任意の目標 (手数を少なく)
public final class W08Shadows implements TrialCell {
    // 区画の写しで動かせる (決まりは arm・tick・onUse・disarm・overlay の中だけで座標を使う。docs/design/32 の 5 章)
    @Override
    public boolean relocatable() {
        return true;
    }

    // 幕 (北の壁 z 97。x -47〜-33 の 15 マス)。z 98 が壁の内側の面
    public static final int CELLS = 15;
    public static final int SCREEN_X0 = -47;
    public static final int SCREEN_Z = 97;
    // 灯と柱の台の列 (五つ)。影の計算: 影の始まり = 2 * 柱 - 灯 + OFF (三つぶんの幅)
    public static final int SLOTS = 5;
    public static final int OFF = 4;
    public static final int LAMP_X0 = -41;
    public static final int PILLAR_X0 = -42;
    public static final int LAMP_Z = 110;
    public static final int PILLAR_Z = 104;
    // 幕の行 (y)。紋 (目標) の列・合っているかの列・いまの影
    public static final int ROW_TARGET = Y + 9;
    public static final int ROW_MATCH = Y + 8;
    public static final int ROW_LIVE = Y + 6;
    // 台 (北の壁の下。灯 x -37〜-33・柱 x -47〜-43)。段 y = Y + 1 + 駒の番号
    public static final int LAMP_CONSOLE_X0 = -37;
    public static final int PILLAR_CONSOLE_X0 = -47;
    public static final int MAX_PIECES = 3;
    static final int[] BEAMS = {-1, 160, 80};
    static final int HOLD = 40;
    static final int DRAFT_EVERY = 900;
    static final int DRAFT_WARN = 40;

    private final Def def = TrialCatalog.get("W08");
    private final Ports ports = new Ports(WhaleDeck.W08_GATE, false, WhaleDeck.W08_EXIT, WhaleDeck.W08_STONE, WhaleDeck.W08_PAD, WhaleDeck.REST_S1,
            new net.minecraft.world.phys.AABB(-48, Y + 1, 97, -31, Y + 11, 116), WhaleDeck.rests().stream().filter(r -> r.id().equals(WhaleDeck.REST_W08)).toList());

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    public static BlockPos lampSocket(int piece, int slot) {
        return new BlockPos(LAMP_CONSOLE_X0 + slot, Y + 1 + piece, SCREEN_Z);
    }

    public static BlockPos pillarSocket(int piece, int slot) {
        return new BlockPos(PILLAR_CONSOLE_X0 + slot, Y + 1 + piece, SCREEN_Z);
    }

    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>();
        for (int i = 0; i < MAX_PIECES; i++) {
            for (int k = 0; k < SLOTS; k++) {
                set.add(lampSocket(i, k));
                set.add(pillarSocket(i, k));
            }
        }
        return set;
    }

    // ---------------------------------------------------------------- 影の計算 (純粋。自己テストが見る)
    // 灯 lamp (台の番号) から、柱 pillar (台の番号) が壁に落とす影の最初のマス。三マス (始め・始め+1・始め+2)
    public static int shadowStart(int lamp, int pillar) {
        return 2 * pillar - lamp + OFF;
    }

    // いまの灯・柱の並びの、幕の影 (true = 影)。すべての灯から遮られたマスだけが影
    public static boolean[] shadows(int[] lamps, int[] pillars) {
        boolean[] dark = new boolean[CELLS];
        for (int c = 0; c < CELLS; c++) {
            boolean all = lamps.length > 0;
            for (int l : lamps) {
                boolean blocked = false;
                for (int p : pillars) {
                    int s = shadowStart(l, p);
                    blocked |= c >= s && c <= s + 2;
                }
                all &= blocked;
            }
            dark[c] = all;
        }
        return dark;
    }

    // 影の山 (つながった影) の数
    public static int humps(boolean[] dark) {
        int n = 0;
        for (int c = 0; c < dark.length; c++) {
            if (dark[c] && (c == 0 || !dark[c - 1])) {
                n++;
            }
        }
        return n;
    }

    static int count(boolean[] dark) {
        int n = 0;
        for (boolean b : dark) {
            n += b ? 1 : 0;
        }
        return n;
    }

    // 一つの幕: 灯・柱の数、目標の影、最初の並び (目標とは違う)
    public record Puzzle(int[] targetLamps, int[] targetPillars, boolean[] target, int[] startLamps, int[] startPillars) {
        public int par() {
            int n = 0;
            for (int i = 0; i < startLamps.length; i++) {
                n += startLamps[i] != targetLamps[i] ? 1 : 0;
            }
            for (int i = 0; i < startPillars.length; i++) {
                n += startPillars[i] != targetPillars[i] ? 1 : 0;
            }
            return n;
        }
    }

    // round: 0 一つ目 (灯 1・柱 1) / 1 二つ目 (灯 2・柱 1) / 2 以降 (灯 lamps・柱 pillars)。humps = 最後の幕の紋の山の数 (1〜3。作れなければ近いもの)
    public static Puzzle make(Random rng, int round, int lamps, int pillars, int humps) {
        int nl = round == 0 ? 1 : round == 1 ? 2 : lamps;
        int np = round <= 1 ? 1 : pillars;
        for (int tries = 0; tries < 400; tries++) {
            int[] tl = new int[nl];
            int[] tp = new int[np];
            for (int i = 0; i < nl; i++) {
                tl[i] = rng.nextInt(SLOTS);
            }
            for (int i = 0; i < np; i++) {
                tp[i] = rng.nextInt(SLOTS);
            }
            if (round == 1) {
                // 重なりを見せる: 二つの灯は一つ違いか二つ違い (影は 2 マス か 1 マスに狭まる)
                int d = 1 + rng.nextInt(2);
                tl[1] = tl[0] + (tl[0] + d < SLOTS ? d : -d);
            }
            boolean[] dark = shadows(tl, tp);
            int c = count(dark);
            int h = humps(dark);
            boolean fits;
            if (round == 0) {
                fits = c == 3;
            } else if (round == 1) {
                fits = c >= 1 && c <= 2;
            } else {
                fits = c >= 2 && c <= 9 && (tries > 300 || h == humps);
            }
            if (!fits) {
                continue;
            }
            // 最初の並び: 目標の影とは違い、動かす駒は 3 つ以上 (一つの幕でも)。一つ目・二つ目は 2 つ以上
            for (int t2 = 0; t2 < 200; t2++) {
                int[] sl = new int[nl];
                int[] sp = new int[np];
                for (int i = 0; i < nl; i++) {
                    sl[i] = rng.nextInt(SLOTS);
                }
                for (int i = 0; i < np; i++) {
                    sp[i] = rng.nextInt(SLOTS);
                }
                Puzzle pz = new Puzzle(tl, tp, dark, sl, sp);
                boolean same = java.util.Arrays.equals(shadows(sl, sp), dark);
                if (!same && pz.par() >= Math.min(2, nl + np)) {
                    return pz;
                }
            }
        }
        // 十分に探しても見つからなければ (あり得ない組): 固定の幕
        int[] tl = java.util.Arrays.copyOf(new int[] {2, 3, 1}, nl);
        int[] tp = java.util.Arrays.copyOf(new int[] {2, 3, 1}, np);
        boolean[] dark = shadows(tl, tp);
        int[] sl = new int[nl];
        int[] sp = new int[np];
        java.util.Arrays.fill(sl, 0);
        java.util.Arrays.fill(sp, 4);
        return new Puzzle(tl, tp, dark, sl, sp);
    }

    // 最後の幕の灯と柱の数 (数字と人数)
    public static int finalLamps(Mods m) {
        return m.number(0) == 0 ? 2 : 3;
    }

    public static int finalPillars(Mods m, int players) {
        int p = m.number(0) == 2 ? 3 : 2;
        return Math.min(MAX_PIECES, p + (players >= 3 ? 1 : 0));
    }

    public static int rounds(int players) {
        return players >= 4 ? 4 : 3;
    }

    public static int beamTicks(Mods m) {
        int b = BEAMS[m.number(1)];
        return b < 0 ? -1 : Abyss.shorter(m, b);
    }

    public static int hold(Mods m) {
        return Abyss.longer(m, HOLD);
    }

    // ---------------------------------------------------------------- 状態
    static final class S {
        int round = -1;
        Puzzle pz;
        int[] lamps = new int[0];
        int[] pillars = new int[0];
        int clicks;
        int allowed;
        long holdSince;
        long beamsUntil;
        long nextDraft;
        int draftLamp = -1;
        long view = -1;
        long lastBeams;
        Random rng;
        boolean matched;
    }

    private static S st(Run run) {
        return (S) run.data.get("s");
    }

    // 自己テスト用: いまの幕・組・灯と柱の位置
    public static Puzzle puzzle(Run run) {
        return run.data.get("s") instanceof S s ? s.pz : null;
    }

    public static int round(Run run) {
        return run.data.get("s") instanceof S s ? s.round : -1;
    }

    public static int[] lamps(Run run) {
        return run.data.get("s") instanceof S s ? s.lamps : new int[0];
    }

    public static int[] pillars(Run run) {
        return run.data.get("s") instanceof S s ? s.pillars : new int[0];
    }

    public static int clicks(Run run) {
        return run.data.get("s") instanceof S s ? s.clicks : 0;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        S s = new S();
        s.rng = new Random(run.seed);
        s.nextDraft = back.getGameTime() + DRAFT_EVERY;
        run.put("s", s);
        if (run.cleared) {
            GroupGates.open(back, run.group, WhaleDeck.W08_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        if (!run.cleared) {
            GroupGates.release(back, run.group, WhaleDeck.W08_EXIT);
        }
    }

    private void startRound(ServerLevel back, Run run, S s, int round, long now, int players) {
        Mods m = run.mods;
        s.round = round;
        s.pz = make(s.rng, round, finalLamps(m), finalPillars(m, players), m.layout() + 1);
        s.lamps = s.pz.startLamps().clone();
        s.pillars = s.pz.startPillars().clone();
        s.allowed += s.pz.par() + 3;
        s.holdSince = 0;
        s.matched = false;
        int bt = beamTicks(m);
        s.beamsUntil = bt < 0 ? Long.MAX_VALUE : now + bt;
        s.view = -1;
        TrialRuntime.refresh(back, run);
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        S s = st(run);
        int n = Math.max(1, inside.size());
        if (s.round < 0 && !run.cleared) {
            startRound(back, run, s, 0, now, n);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w08.start"));
            TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.w08.rule0"));
        }
        if (run.cleared) {
            return;
        }
        boolean changed = false;
        // 隙間風 (障害): 灯の一つが一つずれる。予告 (灯がまたたく) のあとで
        if (m.hasHazard(0)) {
            if (s.draftLamp < 0 && now >= s.nextDraft - DRAFT_WARN && now < s.nextDraft) {
                s.draftLamp = new Random(run.seed ^ s.nextDraft).nextInt(s.lamps.length);
                TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.w08.draft_warn"));
                changed = true;
            } else if (s.draftLamp >= 0 && now >= s.nextDraft) {
                int i = s.draftLamp;
                int dir = new Random(run.seed ^ now).nextBoolean() ? 1 : -1;
                int to = s.lamps[i] + dir;
                if (to < 0 || to >= SLOTS) {
                    to = s.lamps[i] - dir;
                }
                s.lamps[i] = Math.max(0, Math.min(SLOTS - 1, to));
                s.draftLamp = -1;
                s.nextDraft = now + (n >= 3 ? DRAFT_EVERY * 3 / 4 : DRAFT_EVERY);
                s.holdSince = 0;
                TrialFx.sound(back, run.group, lampSocket(0, 0), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
                TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.w08.drifted"));
                s.beamsUntil = beamTicks(m) < 0 ? Long.MAX_VALUE : now + beamTicks(m);
                changed = true;
            }
        }
        // 合っているか
        boolean[] dark = shadows(s.lamps, s.pillars);
        boolean match = java.util.Arrays.equals(dark, s.pz.target());
        if (match) {
            if (s.holdSince == 0) {
                s.holdSince = now;
                TrialFx.sound(back, run.group, lampSocket(0, 0), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w08.hold", (int) Math.ceil(hold(m) / 20.0)));
            } else if (now - s.holdSince >= hold(m)) {
                int next = s.round + 1;
                TrialFx.sound(back, run.group, lampSocket(0, 0), SoundEvents.BELL_BLOCK, 1.6f, 0.8f + 0.2f * next);
                if (next >= rounds(n)) {
                    run.cleared = true;
                    run.clearedAt = now;
                    GroupGates.open(back, run.group, WhaleDeck.W08_EXIT);
                    TrialFx.sound(back, run.group, WhaleDeck.W08_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w08.solved"));
                    if (m.goal() == 0 && s.clicks > s.allowed) {
                        run.goalOk = false;
                    }
                    TrialRuntime.refresh(back, run);
                    return;
                }
                startRound(back, run, s, next, now, n);
                TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.w08.rule" + Math.min(next, 3)));
                return;
            }
        } else {
            s.holdSince = 0;
        }
        if (match != s.matched) {
            s.matched = match;
            changed = true;
        }
        // 光の筋 (粒): 出ているあいだ、半秒ごとに描く
        if (now < s.beamsUntil && now - s.lastBeams >= 10) {
            s.lastBeams = now;
            drawBeams(back, run, s);
        }
        long view = (match ? 1L : 0L) | (long) (s.draftLamp + 1) << 4 | (long) s.round << 8 | (now < s.beamsUntil ? 1L << 12 : 0L);
        if (changed || view != s.view) {
            s.view = view;
            TrialRuntime.refresh(back, run);
        }
    }

    private static final ParticleOptions[] BEAM_PARTICLES = {ParticleTypes.END_ROD, ParticleTypes.SMALL_FLAME, ParticleTypes.SOUL_FIRE_FLAME};

    // 灯 → 柱の縁 → 幕の、光の筋 (柱が遮る影の縁。灯ごとの色)。幕の面は z 98
    private void drawBeams(ServerLevel back, Run run, S s) {
        double y = Y + 3.5;
        for (int i = 0; i < s.lamps.length; i++) {
            double lx = LAMP_X0 + s.lamps[i] + 0.5;
            ParticleOptions pt = BEAM_PARTICLES[i % BEAM_PARTICLES.length];
            for (int j = 0; j < s.pillars.length; j++) {
                double[] edges = {PILLAR_X0 + s.pillars[j], PILLAR_X0 + s.pillars[j] + 2.0};
                for (double e : edges) {
                    // 灯 (z 110) から柱 (z 104) の縁まで、そのさき幕 (z 98) まで。幕で x = lx + (e - lx) * 2
                    double sx = lx + (e - lx) * 2;
                    for (int k = 0; k <= 12; k++) {
                        double f = k / 12.0;
                        double z = LAMP_Z - 12.0 * f;
                        double x = f <= 0.5 ? lx + (e - lx) * (f / 0.5) : e + (sx - e) * ((f - 0.5) / 0.5);
                        TrialFx.particles(back, run.group, pt, x, y, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        S s = st(run);
        if (run.cleared) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.w08.done"));
            return true;
        }
        if (s.round < 0) {
            return false;
        }
        for (int i = 0; i < MAX_PIECES; i++) {
            for (int k = 0; k < SLOTS; k++) {
                boolean lampSocket = pos.equals(lampSocket(i, k));
                boolean pillarSocket = pos.equals(pillarSocket(i, k));
                if (!lampSocket && !pillarSocket) {
                    continue;
                }
                int[] arr = lampSocket ? s.lamps : s.pillars;
                if (i >= arr.length) {
                    Notice.bar(player, ModText.hint("message.skyabyss.trial.w08.unused"));
                    return true;
                }
                if (arr[i] == k) {
                    return true;
                }
                arr[i] = k;
                s.clicks++;
                s.holdSince = 0;
                int bt = beamTicks(run.mods);
                s.beamsUntil = bt < 0 ? Long.MAX_VALUE : now + bt;
                TrialFx.sound(back, run.group, pos, lampSocket ? SoundEvents.COPPER_PLACE : SoundEvents.BONE_BLOCK_PLACE, 1.2f, lampSocket ? 1.4f : 0.8f);
                Notice.bar(player, ModText.hint(lampSocket ? "message.skyabyss.trial.w08.lamp_moved" : "message.skyabyss.trial.w08.pillar_moved", i + 1, k + 1));
                s.view = -1;
                TrialRuntime.refresh(back, run);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 見かけ
    static final BlockState[] LAMP_LOOK = {Blocks.GLOWSTONE.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState()};
    static final BlockState[] LAMP_TAG = {Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.ORANGE_CONCRETE.defaultBlockState(),
            Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState()};

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        if (!(run.data.get("s") instanceof S s) || s.pz == null) {
            return;
        }
        BlockState dark = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState lit = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState dim = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState gray = Blocks.GRAY_CONCRETE.defaultBlockState();
        boolean[] live = shadows(s.lamps, s.pillars);
        for (int c = 0; c < CELLS; c++) {
            int x = SCREEN_X0 + c;
            for (int dy = 0; dy < 2; dy++) {
                out.accept(new BlockPos(x, ROW_TARGET + dy, SCREEN_Z), s.pz.target()[c] ? dark : dim);
                out.accept(new BlockPos(x, ROW_LIVE + dy, SCREEN_Z), live[c] ? dark : lit);
            }
            out.accept(new BlockPos(x, ROW_MATCH, SCREEN_Z), live[c] == s.pz.target()[c] ? Blocks.LIME_CONCRETE.defaultBlockState() : gray);
        }
        // 台: 駒の位置が点く
        for (int i = 0; i < MAX_PIECES; i++) {
            for (int k = 0; k < SLOTS; k++) {
                boolean used = i < s.lamps.length;
                out.accept(lampSocket(i, k), !used ? Blocks.BLACKSTONE.defaultBlockState() : s.lamps[i] == k ? LAMP_TAG[i] : gray);
                boolean pused = i < s.pillars.length;
                out.accept(pillarSocket(i, k), !pused ? Blocks.BLACKSTONE.defaultBlockState() : s.pillars[i] == k ? Blocks.BONE_BLOCK.defaultBlockState() : gray);
            }
        }
        // 灯と柱 (奥の台と、まんなかの台の上)
        for (int i = 0; i < s.lamps.length; i++) {
            BlockPos p = new BlockPos(LAMP_X0 + s.lamps[i], Y + 2 + i, LAMP_Z);
            out.accept(p, s.draftLamp == i ? Blocks.CALCITE.defaultBlockState() : LAMP_LOOK[i]);
        }
        for (int j = 0; j < s.pillars.length; j++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    for (int dy = 1; dy <= 4; dy++) {
                        out.accept(new BlockPos(PILLAR_X0 + s.pillars[j] + dx, Y + dy, PILLAR_Z - 1 + dz), Blocks.BONE_BLOCK.defaultBlockState());
                    }
                }
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < MAX_PIECES; i++) {
            for (int k = 0; k < SLOTS; k++) {
                for (BlockPos p : new BlockPos[] {lampSocket(i, k), pillarSocket(i, k)}) {
                    if (back.getBlockState(p).isAir() || !back.getBlockState(p.south()).isAir()) {
                        bad.add("W08 console socket is missing or covered at " + p);
                        return bad;
                    }
                }
            }
        }
        // 幕の壁の面は、全部固い
        for (int c = 0; c < CELLS; c++) {
            for (int y : new int[] {ROW_TARGET, ROW_TARGET + 1, ROW_MATCH, ROW_LIVE, ROW_LIVE + 1}) {
                if (back.getBlockState(new BlockPos(SCREEN_X0 + c, y, SCREEN_Z)).isAir()) {
                    bad.add("W08 screen cell is missing at " + (SCREEN_X0 + c) + "," + y);
                    return bad;
                }
            }
        }
        // 灯と柱が動く範囲 (台の列) は、空いている
        for (int k = 0; k < SLOTS + 1; k++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (!back.getBlockState(new BlockPos(PILLAR_X0 + k, Y + dy, PILLAR_Z)).isAir() || !back.getBlockState(new BlockPos(PILLAR_X0 + k, Y + dy, PILLAR_Z - 1)).isAir()) {
                    bad.add("W08 pillar rail is blocked at slot " + k);
                    return bad;
                }
            }
        }
        for (int k = 0; k < SLOTS; k++) {
            for (int i = 0; i < MAX_PIECES; i++) {
                if (!back.getBlockState(new BlockPos(LAMP_X0 + k, Y + 2 + i, LAMP_Z)).isAir()) {
                    bad.add("W08 lamp rail is blocked at slot " + k);
                    return bad;
                }
            }
        }
        // どの数・山・種でも、幕が作れる (目標の影があり、最初の並びでは合っていない。影の幅・山の数)
        for (int round = 0; round < 4; round++) {
            for (int humps = 1; humps <= 3; humps++) {
                for (long seed = 0; seed < 25; seed++) {
                    for (int[] sz : new int[][] {{2, 2}, {3, 2}, {3, 3}}) {
                        Puzzle pz = make(new Random(seed * 31 + humps), round, sz[0], sz[1], humps);
                        if (count(pz.target()) < 1) {
                            bad.add("W08 round " + round + " has no target shadow");
                            return bad;
                        }
                        if (java.util.Arrays.equals(shadows(pz.startLamps(), pz.startPillars()), pz.target())) {
                            bad.add("W08 round " + round + " starts already solved");
                            return bad;
                        }
                        if (!java.util.Arrays.equals(shadows(pz.targetLamps(), pz.targetPillars()), pz.target())) {
                            bad.add("W08 round " + round + " target is not reachable");
                            return bad;
                        }
                        if (round == 0 && count(pz.target()) != 3) {
                            bad.add("W08 round 0 shadow is not three cells wide");
                            return bad;
                        }
                    }
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(WhaleDeck.W08_STONE))) {
            bad.add("W08 stone missing");
        }
        return bad;
    }
}
