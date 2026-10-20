package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck2.Y;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// S08 気圧の弁 (智・解く・★★★。docs/design/29 の 3.3)。四つの気嚢 (A 白・B 青・C 黄・D 紫) の目盛りと、三つの弁。弁を右クリックで回すと、
// 弁の壁の板が示すとおりに気嚢の圧が動く (緑 = +1、赤 = −1、灰 = そのまま)。しゃがんで回すと逆回し (いつでも戻せる)。圧は 0〜5。
// 動いて 0 未満・5 超になる回しは「固い」ので動かない。金の印の高さに、動く気嚢の圧を同時に合わせて、しばらく保つ。
//   決まり (教える): 第一の段は二つの気嚢 (A・B) と二つの弁だけ。板の色を読んで、金の印に合わせる。
//   ひねり: 合わせると C・D が目を覚まし、三つ目の弁が回る。今度は弁が C・D にも効く (板の色が変わる)。四つ全部の金の印に合わせて保つ。
// 人数: 目盛りまでの回しの数が人数で増える (二人ごとに +1、最大 +2)。弁は離れていて、手分けして回せる (音の高さで圧が分かる: 高いほど高圧)
// 修飾: 配置 3 (板の組と最初の圧)・数字 2 (回しの数・保つ長さ 3/5/7 秒)・障害 2 (気嚢の漏れ・雷蛾)・任意の目標 (回しを少なく)
public final class S08Valves extends StormCell {
    public static final int MAX = 5;
    public static final int TANKS = 4;
    // 第一の段の弁 {v0, v1} の効き {A, B}、第二の段の弁 {v0, v1, v2} の効き {A, B, C, D} (配置ごと)
    public static final int[][][] STAGE1 = {{{1, -1}, {0, 1}}, {{1, 1}, {-1, 1}}, {{1, -1}, {1, 1}}};
    public static final int[][][] STAGE2 = {
            {{1, -1, 1, 0}, {0, 1, -1, 1}, {1, 0, 1, -1}},
            {{1, 1, 0, -1}, {-1, 1, 1, 0}, {0, -1, 1, 1}},
            {{1, -1, 0, 1}, {-1, 1, -1, 0}, {0, 1, 1, -1}}};
    // 最初の圧 {A, B, C, D}
    public static final int[][] START = {{2, 3, 2, 2}, {1, 3, 3, 2}, {3, 1, 2, 3}};
    // 目盛りまでの回しの数 (最短)。第一の段・第二の段 (数字 0・1・2)
    static final int[][] STEPS1 = {{2, 3}, {3, 4}, {4, 5}};
    static final int[][] STEPS2 = {{3, 4}, {4, 6}, {6, 8}};
    static final int[] HOLD = {60, 100, 140};
    static final int LEAK_EVERY = 600;
    static final int LEAK_WARN = 60;

    private static final BlockState[] FILL = {Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState()};

    public S08Valves() {
        super("S08", new Ports(StormDeck2.S08_GATE, false, List.of(), StormDeck2.S08_STONE, null, "", StormDeck2.S08_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 計算 (ゲームなしで調べられる)
    static int enc(int[] s) {
        return ((s[3] * 6 + s[2]) * 6 + s[1]) * 6 + s[0];
    }

    static int[] dec(int k) {
        return new int[] {k % 6, k / 6 % 6, k / 36 % 6, k / 216};
    }

    // 弁 v の効き (その段の 4 つ組。第一の段は C・D が 0)
    public static int[] vec(int layout, int stage, int v) {
        if (stage == 1) {
            int[] a = STAGE1[layout][v];
            return new int[] {a[0], a[1], 0, 0};
        }
        return STAGE2[layout][v];
    }

    public static int valves(int stage) {
        return stage == 1 ? 2 : 3;
    }

    public static int tanks(int stage) {
        return stage == 1 ? 2 : 4;
    }

    // 弁 v を sign (+1 回す / -1 逆回し) で回した圧。範囲を出るなら null
    public static int[] apply(int layout, int stage, int[] s, int v, int sign) {
        int[] d = vec(layout, stage, v);
        int[] out = new int[TANKS];
        for (int i = 0; i < TANKS; i++) {
            out[i] = s[i] + sign * d[i];
            if (out[i] < 0 || out[i] > MAX) {
                return null;
            }
        }
        return out;
    }

    // 圧 from からの回しの数 (届かなければ -1)。長さ 6^4
    public static int[] distances(int layout, int stage, int[] from) {
        int[] dist = new int[6 * 6 * 6 * 6];
        java.util.Arrays.fill(dist, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        dist[enc(from)] = 0;
        queue.add(from);
        while (!queue.isEmpty()) {
            int[] s = queue.poll();
            for (int v = 0; v < valves(stage); v++) {
                for (int sign : new int[] {1, -1}) {
                    int[] n = apply(layout, stage, s, v, sign);
                    if (n != null && dist[enc(n)] < 0) {
                        dist[enc(n)] = dist[enc(s)] + 1;
                        queue.add(n);
                    }
                }
            }
        }
        return dist;
    }

    // 金の印: from から [lo, hi] 回しで届く圧 (動く気嚢はすべて 1 以上。第一の段の C・D は from のまま) から、種で一つ。なければいちばん遠い (2 以上の) もの
    public static int[] pickTarget(int layout, int stage, int[] from, int steps, int extra, long seed) {
        int[][] range = stage == 1 ? STEPS1 : STEPS2;
        int lo = range[steps][0] + extra;
        int hi = range[steps][1] + extra;
        int[] dist = distances(layout, stage, from);
        List<int[]> cand = new ArrayList<>();
        int[] best = null;
        int bestD = 1;
        for (int k = 0; k < dist.length; k++) {
            if (dist[k] < 2) {
                continue;
            }
            int[] s = dec(k);
            boolean ok = true;
            for (int t = 0; t < tanks(stage); t++) {
                ok &= s[t] >= 1;
            }
            if (!ok) {
                continue;
            }
            if (dist[k] >= lo && dist[k] <= hi) {
                cand.add(s);
            }
            if (dist[k] > bestD) {
                bestD = dist[k];
                best = s;
            }
        }
        if (!cand.isEmpty()) {
            return cand.get(new Random(seed).nextInt(cand.size()));
        }
        return best;
    }

    static int extra(int players) {
        return Math.min(2, Math.max(0, (players - 1) / 2));
    }

    static int hold(Mods m) {
        int h = HOLD[m.number(1)];
        return m.abyss() ? (int) Math.round(h * Abyss.NUMBER_STEP) : h;
    }

    // ---------------------------------------------------------------- 状態
    private static int[] lv(Run run) {
        return run.get("lv", () -> new int[TANKS]);
    }

    private static int[] target(Run run) {
        return run.get("target", () -> new int[TANKS]);
    }

    private static List<UUID> moths(Run run) {
        return run.get("moths", ArrayList::new);
    }

    private static int stage(Run run) {
        return run.data.get("stage") instanceof Integer i ? i : 1;
    }

    private void newStage(ServerLevel back, Run run, int stage, int players) {
        int[] cur = lv(run);
        int[] t = pickTarget(run.mods.layout(), stage, cur, run.mods.number(0), extra(players), run.seed ^ (stage * 7919L));
        if (t == null) {
            t = cur.clone();
        }
        int[] tg = target(run);
        System.arraycopy(t, 0, tg, 0, TANKS);
        run.put("stage", stage);
        run.put("holdSince", 0L);
        run.put("steps" + stage, distances(run.mods.layout(), stage, cur)[enc(t)]);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        int[] s = START[run.mods.layout()];
        System.arraycopy(s, 0, lv(run), 0, TANKS);
        run.put("turns", 0);
        run.put("holdSince", 0L);
        run.put("nextLeak", back.getGameTime() + LEAK_EVERY);
        run.put("leakTank", -1);
        run.put("mothDone", false);
        newStage(back, run, 1, 1);
        if (run.cleared) {
            run.put("stage", 3);
            System.arraycopy(target(run), 0, lv(run), 0, TANKS);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, moths(run));
    }

    // ---------------------------------------------------------------- 弁
    @Override
    public Set<BlockPos> interactive() {
        return Set.of(StormDeck2.s08Valve(0), StormDeck2.s08Valve(1), StormDeck2.s08Valve(2));
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        int v = -1;
        for (int i = 0; i < 3; i++) {
            if (pos.equals(StormDeck2.s08Valve(i))) {
                v = i;
            }
        }
        if (v < 0) {
            return false;
        }
        if (run.cleared || stage(run) > 2) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.s08.done"));
            return true;
        }
        int stage = stage(run);
        if (v >= valves(stage)) {
            Notice.bar(player, ModText.deny("message.skyabyss.trial.s08.locked"));
            TrialFx.sound(back, run.group, pos, SoundEvents.COPPER_BREAK, 0.8f, 0.6f);
            return true;
        }
        int[] next = apply(run.mods.layout(), stage, lv(run), v, sneaking ? -1 : 1);
        if (next == null) {
            Notice.bar(player, ModText.deny("message.skyabyss.trial.s08.stuck"));
            TrialFx.sound(back, run.group, pos, SoundEvents.COPPER_BREAK, 0.8f, 0.6f);
            return true;
        }
        System.arraycopy(next, 0, lv(run), 0, TANKS);
        run.put("turns", run.getInt("turns") + 1);
        run.put("holdSince", 0L);
        // 音の高さ: 動いた気嚢の圧が高いほど高い
        int[] d = vec(run.mods.layout(), stage, v);
        for (int t = 0; t < TANKS; t++) {
            if (d[t] != 0) {
                float pitch = 0.5f + 0.18f * next[t];
                TrialFx.sound(back, run.group, StormDeck2.s08Gauge(t, 1), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, Math.min(2f, pitch));
            }
        }
        TrialFx.sound(back, run.group, pos, SoundEvents.COPPER_PLACE, 1.0f, 1.2f);
        TrialRuntime.refresh(back, run);
        return true;
    }

    // ---------------------------------------------------------------- 動かす
    private static boolean matched(Run run, int stage) {
        int[] a = lv(run);
        int[] t = target(run);
        for (int i = 0; i < tanks(stage); i++) {
            if (a[i] != t[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = count(inside);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s08.start"));
        }
        if (run.cleared) {
            return;
        }
        int stage = stage(run);
        // 人数が増えたら、まだ触っていない第一の段 (回し 0) の目盛りを遠くする
        if (run.getInt("turns") == 0 && n != run.getInt("players") && stage == 1) {
            run.put("players", n);
            newStage(back, run, 1, n);
            TrialRuntime.refresh(back, run);
        } else {
            run.put("players", n);
        }
        // 気嚢の漏れ (障害): 予告 → 一つの気嚢が一段下がる
        if (run.mods.hasHazard(0)) {
            leak(back, run, inside, now, stage);
        }
        // 雷蛾 (障害)
        if (run.mods.hasHazard(1) && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && !(Boolean) run.data.getOrDefault("mothDone", false)) {
            run.put("mothDone", true);
            int want = n >= 3 ? 2 : 1;
            for (int i = 0; i < want; i++) {
                StaticMoth moth = spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, StormDeck2.S08_HALL.cx() - 4 + i * 8, Y + 5, StormDeck2.S08_HALL.cz());
                if (moth != null) {
                    moths(run).add(moth.getUUID());
                }
            }
        }
        // 合っているあいだ、保つ
        if (matched(run, stage)) {
            long since = run.getLong("holdSince");
            int need = hold(run.mods);
            if (since == 0) {
                run.put("holdSince", now);
                TrialRuntime.refresh(back, run);
                bar(inside, ModText.progress("message.skyabyss.trial.s08.hold", need / 20));
            } else if (now - since >= need) {
                done(back, run, inside, now, stage, n);
                return;
            } else if ((now - since) % 20 == 0) {
                bar(inside, ModText.progress("message.skyabyss.trial.s08.hold_left", (int) Math.ceil((need - (now - since)) / 20.0)));
            }
            if (now % 10 == 0) {
                for (int t = 0; t < tanks(stage); t++) {
                    BlockPos g = StormDeck2.s08Gauge(t, Math.max(1, lv(run)[t]));
                    TrialFx.particles(back, run.group, ParticleTypes.END_ROD, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.01);
                }
            }
        } else if (run.getLong("holdSince") != 0) {
            run.put("holdSince", 0L);
            TrialRuntime.refresh(back, run);
        }
        if (run.mods.goal() == 0 && run.getInt("turns") > allowedTurns(run)) {
            run.goalOk = false;
        }
    }

    // 回しの数の上限 (任意の目標「回しを少なく」): 最短 + 3
    static int allowedTurns(Run run) {
        return run.getInt("steps1") + run.getInt("steps2") + 3;
    }

    private void done(ServerLevel back, Run run, List<ServerPlayer> inside, long now, int stage, int players) {
        run.put("holdSince", 0L);
        TrialFx.sound(back, run.group, StormDeck2.s08Gauge(1, 1), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 1.5f);
        if (stage == 1) {
            // 第二の段: C・D が目覚める
            newStage(back, run, 2, players);
            bar(inside, ModText.progress("message.skyabyss.trial.s08.stage2"));
            TrialRuntime.refresh(back, run);
            return;
        }
        run.put("stage", 3);
        finish(back, run, inside, now, "message.skyabyss.trial.s08.cleared");
    }

    private void leak(ServerLevel back, Run run, List<ServerPlayer> inside, long now, int stage) {
        int lt = run.getInt("leakTank");
        if (lt >= 0) {
            BlockPos g = StormDeck2.s08Gauge(lt, Math.max(1, lv(run)[lt]));
            if (now % 5 == 0) {
                TrialFx.particles(back, run.group, ParticleTypes.CLOUD, g.getX() + 0.5, g.getY() + 0.6, g.getZ() + 1.2, 2, 0.2, 0.2, 0.1, 0.02);
            }
            if (now >= run.getLong("leakAt")) {
                int[] a = lv(run);
                if (a[lt] > 0) {
                    a[lt]--;
                }
                run.put("leakTank", -1);
                run.put("holdSince", 0L);
                run.put("nextLeak", now + LEAK_EVERY);
                TrialFx.sound(back, run.group, g, SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.4f);
                TrialRuntime.refresh(back, run);
            }
            return;
        }
        if (now >= run.getLong("nextLeak") && !inside.isEmpty()) {
            int t = back.random.nextInt(tanks(stage));
            if (lv(run)[t] > 1) {
                run.put("leakTank", t);
                run.put("leakAt", now + LEAK_WARN);
                bar(inside, ModText.deny("message.skyabyss.trial.s08.leak", String.valueOf((char) ('A' + t))));
                TrialFx.sound(back, run.group, StormDeck2.s08Gauge(t, 1), SoundEvents.PUFFER_FISH_BLOW_OUT, 1.0f, 1.2f);
            } else {
                run.put("nextLeak", now + 100);
            }
        }
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int stage = stage(run);
        int[] a = lv(run);
        int[] t = target(run);
        boolean done = run.cleared || stage > 2;
        int active = done ? TANKS : tanks(stage);
        boolean holding = run.getLong("holdSince") != 0;
        for (int k = 0; k < TANKS; k++) {
            boolean on = k < active;
            for (int level = 1; level <= StormDeck2.S08_TANK_H; level++) {
                BlockPos g = StormDeck2.s08Gauge(k, level);
                if (!on) {
                    out.accept(g, Blocks.BLACK_STAINED_GLASS.defaultBlockState());
                } else if (level <= a[k]) {
                    out.accept(g, FILL[0]);
                }
            }
            if (on && t[k] >= 1) {
                boolean ok = a[k] == t[k];
                BlockState mark = ok ? (holding ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.EMERALD_BLOCK.defaultBlockState()) : Blocks.GOLD_BLOCK.defaultBlockState();
                out.accept(StormDeck2.s08Mark(k, t[k]), mark);
                out.accept(StormDeck2.s08Mark(k, t[k]).west(2), mark);
            }
        }
        // 弁の板: 緑 = +1、赤 = −1、灰 = そのまま、黒 = まだ動かない
        for (int v = 0; v < 3; v++) {
            boolean vOn = !done && v < valves(stage);
            for (int k = 0; k < TANKS; k++) {
                BlockState s;
                if (!vOn || k >= active) {
                    s = Blocks.BLACK_CONCRETE.defaultBlockState();
                } else {
                    int d = vec(run.mods.layout(), stage, v)[k];
                    s = d > 0 ? Blocks.LIME_CONCRETE.defaultBlockState() : d < 0 ? Blocks.RED_CONCRETE.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                }
                out.accept(StormDeck2.s08Panel(v, k), s);
            }
            out.accept(StormDeck2.s08Valve(v).above(), vOn ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : StormDeck2.S08_HALL.floor()) {
            if (back.getBlockState(p).isAir()) {
                bad.add("S08 hall has a hole at " + p);
                break;
            }
        }
        for (int t = 0; t < TANKS; t++) {
            for (int level = 1; level <= StormDeck2.S08_TANK_H; level++) {
                if (!back.getBlockState(StormDeck2.s08Gauge(t, level)).is(Blocks.WHITE_STAINED_GLASS)) {
                    bad.add("S08 gauge " + t + " has no glass at level " + level);
                    break;
                }
            }
        }
        for (int v = 0; v < 3; v++) {
            if (!back.getBlockState(StormDeck2.s08Valve(v)).is(Blocks.WAXED_CHISELED_COPPER)) {
                bad.add("S08 valve " + v + " is missing");
            }
            if (!back.getBlockState(StormDeck2.s08Panel(v, 0)).is(Blocks.LIGHT_GRAY_CONCRETE)) {
                bad.add("S08 panel " + v + " is missing");
            }
        }
        if (!back.getBlockState(StormDeck2.S08_STONE).is(StormBackSetup.CHIME_STONE.get())) {
            bad.add("S08 stone missing");
        }
        // どの配置・数字・人数でも、目盛りが選べる (第一の段・第二の段とも、最短が 2 以上)
        for (int l = 0; l < 3; l++) {
            for (int s = 0; s < 3; s++) {
                for (int ex = 0; ex <= 2; ex++) {
                    int[] t1 = pickTarget(l, 1, START[l], s, ex, 1L);
                    if (t1 == null || distances(l, 1, START[l])[enc(t1)] < 2) {
                        bad.add("S08 layout " + l + " steps " + s + " has no stage 1 target");
                        continue;
                    }
                    int[] t2 = pickTarget(l, 2, t1, s, ex, 2L);
                    if (t2 == null || distances(l, 2, t1)[enc(t2)] < 2) {
                        bad.add("S08 layout " + l + " steps " + s + " has no stage 2 target");
                    }
                }
            }
        }
        return bad;
    }
}
