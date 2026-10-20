package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck2.Y;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.storm.back.StormBackClock;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialCells;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// P3 の雷翼の五つ (S04・S07・S08・S11・S12) の遊びの決まりの自己テスト ("trial pool storm" の一部)。
// 数字の増え方・解の保証・一周の流れ (偽の人で動かす) を調べる。変種の全列挙・建てた形は StormTrialSelfTest。
final class StormP3SelfTest {
    private StormP3SelfTest() {
    }

    private static List<String> fail;

    private static void check(boolean ok, String what) {
        if (!ok) {
            fail.add(what);
        }
    }

    private static Mods mods(int layout, int a, int b) {
        return new Mods(layout, new int[] {a, b}, -1, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
    }

    static void run(ServerLevel back, List<String> failures, List<String> notes) {
        fail = failures;
        boolean peaceful = back.getDifficulty() == Difficulty.PEACEFUL;
        try {
            s04(back);
            s07(back);
            s08(back);
            s11(back, peaceful);
            s12(back, peaceful);
        } finally {
            StormBackClock.force(null);
        }
        if (peaceful) {
            notes.add("P3 enemy checks skipped on peaceful");
        }
    }

    // ---------------------------------------------------------------- S04
    private static void s04(ServerLevel back) {
        S04Bridge cell = (S04Bridge) TrialCells.get("S04");
        Mods plain = mods(0, 1, 1);
        check(S04Bridge.period(plain, 1) > S04Bridge.period(plain, 4), "S04: the gust period does not quicken with the players");
        check(S04Bridge.period(mods(0, 0, 1), 1) > S04Bridge.period(mods(0, 2, 1), 1), "S04: the easy gap is not longer");
        check(S04Bridge.blow(mods(0, 1, 0)) < S04Bridge.blow(mods(0, 1, 2)), "S04: the gust length does not grow with the number");
        check(S04Bridge.warn(false) > S04Bridge.warn(true) && S04Bridge.push(false) < S04Bridge.push(true), "S04: calm is not gentler than storm");
        check(S04Bridge.zoneOf(-60, 141.5, Y + 1) == 0 && S04Bridge.zoneOf(-100, 141.5, Y + 1) == 1 && S04Bridge.zoneOf(-90, 141.5, Y + 1) == -1
                && S04Bridge.zoneOf(-60, 130, Y + 1) == -1, "S04: the bridge zones are wrong");
        // 風向き: 教えの橋はいつも +1、ひねりの橋は配置ごとに両方が出る
        UUID g = new UUID(0x504, 1);
        for (int layout = 0; layout < 3; layout++) {
            TrialCell.Run run = new TrialCell.Run(g, cell, mods(layout, 1, 1), 99L, 0);
            Set<Integer> seen = new HashSet<>();
            for (long c = 0; c < 40; c++) {
                check(S04Bridge.dirOf(run, 0, c) == 1, "S04: the teaching bridge changed direction");
                seen.add(S04Bridge.dirOf(run, 1, c));
            }
            check(seen.size() == 2, "S04 layout " + layout + ": the twisted bridge never changes direction");
        }
        // 陰: どの向きでも、柱のそばに陰がある。風上の側は陰でない。柱から離れたら陰でない
        for (int px : StormDeck2.S04_PILLARS) {
            check(S04Bridge.inLee(px, 144.5, px, 1) && !S04Bridge.inLee(px, 138.5, px, 1), "S04: the lee of pillar " + px + " is wrong for a north wind");
            check(S04Bridge.inLee(px + 1.5, 138.5, px, -1) && !S04Bridge.inLee(px, 144.5, px, -1), "S04: the lee of pillar " + px + " is wrong for a south wind");
            check(!S04Bridge.inLee(px + 4, 144.5, px, 1), "S04: the lee of pillar " + px + " is too long");
        }
        // 吹いているあいだ: 陰の外の人は押され、陰の中の人は押されない。予告のあいだは押されない
        StormBackClock.force(StormBackClock.Phase.CALM);
        StormTrialSelfTest.Hit out = StormTrialSelfTest.person(back, g, "s04out", -60, Y + 1, 139.5);
        StormTrialSelfTest.Hit in = StormTrialSelfTest.person(back, g, "s04in", -54, Y + 1, 144.5);
        TrialCell.Run run = new TrialCell.Run(g, cell, plain, 99L, 0);
        cell.arm(back, run);
        try {
            int period = S04Bridge.period(plain, 1);
            int warn = S04Bridge.warn(false);
            int blow = S04Bridge.blow(plain);
            long tBlow = warn + 2;
            long tWarn = 3;
            out.setDeltaMovement(Vec3.ZERO);
            in.setDeltaMovement(Vec3.ZERO);
            cell.perTick(back, run, List.of(out, in), tWarn);
            check(out.getDeltaMovement().z == 0 && in.getDeltaMovement().z == 0, "S04: the warning pushed someone");
            cell.perTick(back, run, List.of(out, in), tBlow);
            check(out.getDeltaMovement().z > 0.04, "S04: a player outside the lee was not pushed south (" + out.getDeltaMovement().z + ")");
            check(in.getDeltaMovement().z == 0, "S04: a player in the lee was pushed");
            out.setDeltaMovement(Vec3.ZERO);
            cell.perTick(back, run, List.of(out), warn + blow + 4L);
            check(out.getDeltaMovement().z == 0, "S04: the gust kept blowing after its length");
            check(period > warn + blow + 20, "S04: the quiet part of the cycle is too short");
            // 落ちたら休み場へ (傷は軽い)・向こうの台で済み
            StormTrialSelfTest.Hit fallen = StormTrialSelfTest.person(back, g, "s04fall", -60, Y - 6, 141.5);
            cell.tick(back, run, List.of(fallen), 100);
            check(fallen.getY() > Y - 3, "S04: the fallen player was not returned to a rest point");
            StormTrialSelfTest.Hit crosser = StormTrialSelfTest.person(back, g, "s04end", StormDeck2.S04_END.cx(), Y + 1, StormDeck2.S04_END.cz());
            cell.tick(back, run, List.of(crosser), 105);
            check(run.cleared, "S04: reaching the far deck did not finish the trial");
        } finally {
            cell.disarm(back, run);
            GroupGates.reset(back, g);
            BackGroupData.get(back).remove(g);
        }
    }

    // ---------------------------------------------------------------- S07
    private static void s07(ServerLevel back) {
        S07Mirrors cell = (S07Mirrors) TrialCells.get("S07");
        // 鏡の向きの変換は、光の曲がり方と合っている ('/' は東→北・北→東・西→南・南→西、'\' は東→南・南→東・西→北・北→西)
        int[][] slash = {{1, 0}, {0, 1}, {3, 2}, {2, 3}};
        int[][] back2 = {{1, 2}, {2, 1}, {3, 0}, {0, 3}};
        for (int[] s : slash) {
            check((s[0] ^ 1) == s[1], "S07: the / reflection is wrong");
        }
        for (int[] s : back2) {
            check(3 - s[0] == s[1], "S07: the \\ reflection is wrong");
        }
        for (int l = 0; l < 3; l++) {
            int[] sol = S07Mirrors.solved(l);
            check(S07Mirrors.trace(sol, false, Set.of()).hitWindow(), "S07 layout " + l + ": the solved state does not reach the window");
            check(S07Mirrors.trace(sol, true, Set.of()).hitTarget(), "S07 layout " + l + ": the solved state does not reach the catcher");
            // 雷蛾にさえぎられたら届かない
            Set<Integer> block = new HashSet<>();
            block.add(9 * 100 + 4);
            check(!S07Mirrors.trace(sol, true, block).hitTarget(), "S07 layout " + l + ": a moth in the beam did not block it");
            // どの最初の状態も、解く状態から m 回 (m = 3〜6) の裏返しだけで、解がほかにない
            for (int m = 3; m <= 6; m++) {
                for (long seed = 0; seed < 6; seed++) {
                    int[] st = S07Mirrors.start(l, m, seed);
                    check(!S07Mirrors.trace(st, true, Set.of()).hitTarget(), "S07 layout " + l + ": a start state already solves (m " + m + ", seed " + seed + ")");
                }
            }
        }
        // 人数・数字で最短の裏返しが増える
        check(S07Mirrors.moves(mods(0, 1, 1), 1) <= S07Mirrors.moves(mods(0, 1, 1), 2) && S07Mirrors.moves(mods(0, 1, 1), 2) <= S07Mirrors.moves(mods(0, 1, 1), 4)
                && S07Mirrors.moves(mods(0, 0, 1), 1) <= S07Mirrors.moves(mods(0, 2, 1), 1), "S07: the flips do not grow with the players or the number");
        // 遊び: 最短の手で解く (つながった鏡は、相手も裏返る)
        for (int l = 0; l < 3; l++) {
            UUID g = new UUID(0x507, l);
            StormTrialSelfTest.Hit p = StormTrialSelfTest.person(back, g, "s07", StormDeck2.s07Cell(3, 4).getX(), Y + 1, StormDeck2.s07Cell(3, 4).getZ());
            TrialCell.Run run = new TrialCell.Run(g, cell, mods(l, 1, 0), 777L + l, 0);
            cell.arm(back, run);
            try {
                int[] st = new int[10];
                System.arraycopy(S07Mirrors.start(l, S07Mirrors.moves(run.mods, 1), run.seed), 0, st, 0, 10);
                int[] actual = (int[]) run.data.get("st");
                check(java.util.Arrays.equals(st, actual), "S07 layout " + l + ": the armed state is not the start state");
                cell.tick(back, run, List.of(p), 5);
                check(!GroupGates.isOpen(back, g, StormDeck2.s07Window()), "S07 layout " + l + ": the window is open at the start");
                int[] sol = S07Mirrors.solved(l);
                int clicks = 0;
                // 最初の部屋 (部屋 1 のつながった組) を先に
                for (int gen = 0; gen < 4; gen++) {
                    int first = S07Mirrors.flipSet(l, gen).get(0);
                    if (actual[first] != sol[first]) {
                        check(cell.onUse(back, run, p, S07Mirrors.mirrorPos(first), false, 10), "S07: a mirror click was not handled");
                        clicks++;
                        List<Integer> set = S07Mirrors.flipSet(l, gen);
                        check(actual[set.get(0)] == sol[set.get(0)] && actual[set.get(1)] == sol[set.get(1)], "S07 layout " + l + ": the linked twin did not flip with its mirror");
                    }
                }
                cell.tick(back, run, List.of(p), 15);
                check(GroupGates.isOpen(back, g, StormDeck2.s07Window()), "S07 layout " + l + ": the window did not open once room 1 was right");
                for (int gen = 4; gen < 6; gen++) {
                    int first = S07Mirrors.flipSet(l, gen).get(0);
                    if (actual[first] != sol[first]) {
                        cell.onUse(back, run, p, S07Mirrors.mirrorPos(first), false, 20);
                        clicks++;
                    }
                }
                check(java.util.Arrays.equals(actual, sol), "S07 layout " + l + ": after the flips the state is not the solution");
                check(clicks == S07Mirrors.moves(run.mods, 1), "S07 layout " + l + ": solved in " + clicks + " clicks, expected " + S07Mirrors.moves(run.mods, 1));
                cell.tick(back, run, List.of(p), 25);
                check(!run.cleared, "S07: cleared before holding the beam");
                cell.tick(back, run, List.of(p), 25 + 5 + S07Mirrors.hold(run.mods));
                check(run.cleared, "S07 layout " + l + ": holding the beam did not finish the trial");
            } finally {
                cell.disarm(back, run);
                p.discard();
                GroupGates.reset(back, g);
                BackGroupData.get(back).remove(g);
            }
        }
    }

    // ---------------------------------------------------------------- S08
    private static List<int[]> solve(int layout, int stage, int[] from, int[] to) {
        java.util.Map<Integer, int[]> prev = new java.util.HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        prev.put(S08Valves.enc(from), new int[] {-1, -1, -1});
        queue.add(from);
        while (!queue.isEmpty()) {
            int[] s = queue.poll();
            if (java.util.Arrays.equals(s, to)) {
                List<int[]> moves = new ArrayList<>();
                int key = S08Valves.enc(s);
                while (prev.get(key)[0] >= 0) {
                    int[] pv = prev.get(key);
                    moves.add(0, new int[] {pv[1], pv[2]});
                    key = pv[0];
                }
                return moves;
            }
            for (int v = 0; v < S08Valves.valves(stage); v++) {
                for (int sign : new int[] {1, -1}) {
                    int[] n = S08Valves.apply(layout, stage, s, v, sign);
                    if (n != null && !prev.containsKey(S08Valves.enc(n))) {
                        prev.put(S08Valves.enc(n), new int[] {S08Valves.enc(s), v, sign});
                        queue.add(n);
                    }
                }
            }
        }
        return null;
    }

    private static void s08(ServerLevel back) {
        S08Valves cell = (S08Valves) TrialCells.get("S08");
        for (int l = 0; l < 3; l++) {
            for (int steps = 0; steps < 3; steps++) {
                int[] prevD = null;
                for (int extra = 0; extra <= 2; extra++) {
                    int[] t1 = S08Valves.pickTarget(l, 1, S08Valves.START[l], steps, extra, 5L);
                    check(t1 != null && S08Valves.distances(l, 1, S08Valves.START[l])[S08Valves.enc(t1)] >= 2, "S08: no stage 1 target for layout " + l + " steps " + steps);
                    int[] t2 = S08Valves.pickTarget(l, 2, t1, steps, extra, 6L);
                    check(t2 != null && S08Valves.distances(l, 2, t1)[S08Valves.enc(t2)] >= 2, "S08: no stage 2 target for layout " + l + " steps " + steps);
                    int d = S08Valves.distances(l, 2, t1)[S08Valves.enc(t2)];
                    if (prevD != null) {
                        check(d >= prevD[0] - 1, "S08: the targets get nearer with more entrants (layout " + l + ", steps " + steps + ")");
                    }
                    prevD = new int[] {d};
                }
            }
            // 逆に回すと戻る・範囲を出る回しは動かない
            int[] s = S08Valves.START[l].clone();
            int[] up = S08Valves.apply(l, 2, s, 0, 1);
            check(up == null || java.util.Arrays.equals(S08Valves.apply(l, 2, up, 0, -1), s), "S08: turning back does not undo a turn");
            check(S08Valves.apply(l, 2, new int[] {0, 0, 0, 0}, 0, -1) == null || S08Valves.vec(l, 2, 0)[0] <= 0, "S08: a turn below 0 was allowed");
        }
        // 一周: 偽の人で最短の回しをして、二つの段を越える
        for (int l = 0; l < 3; l++) {
            UUID g = new UUID(0x508, l);
            StormTrialSelfTest.Hit p = StormTrialSelfTest.person(back, g, "s08", 42, Y + 1, 146);
            TrialCell.Run run = new TrialCell.Run(g, cell, mods(l, 1, 0), 321L + l, 0);
            cell.arm(back, run);
            try {
                long now = 5;
                cell.tick(back, run, List.of(p), now);
                // 触っていないあいだ人数が決まるので、もう一度
                check(!run.cleared, "S08: cleared at the start");
                // 第三の弁は第一の段では固い
                int before = ((int[]) run.data.get("lv"))[0];
                cell.onUse(back, run, p, StormDeck2.s08Valve(2), false, now);
                check(((int[]) run.data.get("lv"))[0] == before && run.getInt("turns") == 0, "S08: the third valve turned in stage 1");
                for (int stage = 1; stage <= 2; stage++) {
                    int[] cur = ((int[]) run.data.get("lv")).clone();
                    int[] target = ((int[]) run.data.get("target")).clone();
                    List<int[]> moves = solve(l, stage, cur, target);
                    check(moves != null, "S08 layout " + l + " stage " + stage + ": the target is unreachable");
                    if (moves == null) {
                        break;
                    }
                    check(moves.size() >= 2, "S08 layout " + l + " stage " + stage + ": the target is too near");
                    for (int[] mv : moves) {
                        now += 5;
                        check(cell.onUse(back, run, p, StormDeck2.s08Valve(mv[0]), mv[1] < 0, now), "S08: a valve click was not handled");
                    }
                    int[] lv = (int[]) run.data.get("lv");
                    for (int i = 0; i < S08Valves.tanks(stage); i++) {
                        check(lv[i] == target[i], "S08 layout " + l + " stage " + stage + ": tank " + i + " is " + lv[i] + ", expected " + target[i]);
                    }
                    now += 5;
                    cell.tick(back, run, List.of(p), now);
                    if (stage == 1) {
                        check(!run.cleared, "S08: stage 1 finished the whole trial");
                    }
                    now += S08Valves.hold(run.mods) + 5;
                    cell.tick(back, run, List.of(p), now);
                    if (stage == 1) {
                        check(run.getInt("stage") == 2, "S08 layout " + l + ": holding the marks did not wake stage 2");
                    }
                }
                check(run.cleared, "S08 layout " + l + ": holding the second set of marks did not finish the trial");
            } finally {
                cell.disarm(back, run);
                p.discard();
                GroupGates.reset(back, g);
                BackGroupData.get(back).remove(g);
            }
        }
    }

    // ---------------------------------------------------------------- S11
    private static void s11(ServerLevel back, boolean peaceful) {
        S11Cage cell = (S11Cage) TrialCells.get("S11");
        for (int l = 0; l < 3; l++) {
            double len = S11Cage.length(l);
            check(len > 100 && len < 180, "S11 route " + l + " has an odd length " + len);
            double[] s0 = S11Cage.at(l, 0);
            double[] e = S11Cage.at(l, len);
            check(s0[0] == 20 && s0[1] == 161 && e[0] == 62 && e[1] == 204, "S11 route " + l + " has wrong ends");
        }
        Mods plain = mods(0, 1, 1);
        UUID g = new UUID(0x511, 1);
        TrialCell.Run r = new TrialCell.Run(g, cell, plain, 1L, 0);
        check(S11Cage.foes(r, 1, 1) < S11Cage.foes(r, 4, 1) && S11Cage.hpMax(1) < S11Cage.hpMax(4), "S11: the raid and the cage do not scale with the players");
        check(S11Cage.every(plain, false) > S11Cage.every(plain, true), "S11: calm strikes are not slower than storm strikes");
        check(S11Cage.raidAt(r, 0, 1) > S11Cage.length(0) * 0.25 && S11Cage.raidAt(r, 0, 1) < S11Cage.raidAt(r, 0, 2), "S11: the first raid does not leave the first third quiet");
        // 一周: 籠は近くの人がいるあいだ進み、雷は輪の中の人だけに当たる
        StormBackClock.force(StormBackClock.Phase.CALM);
        TrialCell.Run run = new TrialCell.Run(g, cell, plain, 4242L, 0);
        cell.arm(back, run);
        StormTrialSelfTest.Hit near = StormTrialSelfTest.person(back, g, "s11near", 20.5, Y + 1, 163.5);
        StormTrialSelfTest.Hit far = StormTrialSelfTest.person(back, g, "s11far", 20.5, Y + 1, 168.5);
        StormTrialSelfTest.Hit away = StormTrialSelfTest.person(back, g, "s11away", 60.5, Y + 1, 190.5);
        try {
            double s0 = (Double) run.data.get("s");
            cell.tick(back, run, List.of(away), 5);
            check((Double) run.data.get("s") == s0, "S11: the cage moved with nobody near it");
            cell.tick(back, run, List.of(far), 10);
            check((Double) run.data.get("s") > s0, "S11: the cage did not follow a player within 7 blocks");
            // 雷: 予告 → 落ちる
            run.put("s", 0.0);
            run.put("nextStrike", 1000L);
            long t = 1000;
            cell.tick(back, run, List.of(near, far), t);
            check(run.getLong("strikeAt") == t + S11Cage.STRIKE_WARN, "S11: the strike was not announced");
            int hn = near.hits;
            int hf = far.hits;
            cell.tick(back, run, List.of(near, far), t + S11Cage.STRIKE_WARN);
            check(near.hits > hn, "S11: a player inside the ring was not struck");
            check(far.hits == hf, "S11: a player outside the ring was struck");
            // 襲撃: 敵が籠のそばにいると進まない
            if (!peaceful) {
                run.put("s", S11Cage.raidAt(run, 0, 1) + 0.1);
                cell.tick(back, run, List.of(far), t + 100);
                check(run.getInt("trig") == 1, "S11: the first raid did not start");
                Object list = run.data.get("mobs");
                check(list instanceof List<?> l && !l.isEmpty(), "S11: the raid spawned no enemies");
            }
            // 塔に着いたら済み
            run.put("s", S11Cage.length(0) - 0.1);
            run.put("trig", 99);
            cell.tick(back, run, List.of(far), t + 200);
            check(run.cleared, "S11: arriving at the tower did not finish the trial");
        } finally {
            cell.disarm(back, run);
            near.discard();
            far.discard();
            away.discard();
            GroupGates.reset(back, g);
            BackGroupData.get(back).remove(g);
        }
    }

    // ---------------------------------------------------------------- S12
    private static void s12(ServerLevel back, boolean peaceful) {
        S12Summit cell = (S12Summit) TrialCells.get("S12");
        // 扇: 重ならず六つ、芯・筋・輪は含まない、大きさが近い
        Set<BlockPos> all = new HashSet<>();
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int w = 0; w < 6; w++) {
            List<BlockPos> cells = StormDeck2.s12Pads(w);
            min = Math.min(min, cells.size());
            max = Math.max(max, cells.size());
            for (BlockPos p : cells) {
                check(all.add(p), "S12: wedges overlap at " + p);
                double r = Math.hypot(p.getX() - StormDeck2.S12_ARENA.cx(), p.getZ() - StormDeck2.S12_ARENA.cz());
                check(r > StormDeck2.S12_CORE_R && Math.abs(p.getX() - StormDeck2.S12_ARENA.cx()) > 1, "S12: a pad cell sits on the core or a spoke at " + p);
            }
        }
        check(min >= 30 && max <= min * 2, "S12: the wedges are lopsided (" + min + " to " + max + ")");
        check(S12Summit.hold(mods(0, 0, 1)) > S12Summit.hold(mods(0, 2, 1)), "S12: the easy pad swap is not slower");
        check(S12Summit.stormEvery(mods(0, 1, 1), 1) > S12Summit.stormEvery(mods(0, 1, 1), 5), "S12: the feather storm does not quicken with the players");
        UUID g = new UUID(0x512, 1);
        TrialCell.Run r0 = new TrialCell.Run(g, cell, mods(0, 1, 1), 1L, 0);
        check(S12Summit.hpMult(r0, 1) < S12Summit.hpMult(r0, 4) && S12Summit.support(r0, 1) <= S12Summit.support(r0, 5), "S12: the chief and its retinue do not grow with the players");
        // 入れ替えの状態: 4 → 1 → 2 → 3 → 4。瞬く扇はあり、運び去られた扇はない
        check(S12Summit.present(1, 0) && !S12Summit.present(1, 1) && S12Summit.present(3, 1) && !S12Summit.present(3, 0) && S12Summit.present(2, 1) && S12Summit.present(4, 0),
                "S12: the pad states are wrong");
        check(S12Summit.flickering(2, 0) && !S12Summit.flickering(2, 1) && S12Summit.flickering(4, 1) && !S12Summit.flickering(1, 0), "S12: the flicker states are wrong");
        if (peaceful) {
            return;
        }
        StormBackClock.force(StormBackClock.Phase.CALM);
        TrialCell.Run run = new TrialCell.Run(g, cell, mods(0, 1, 1), 4242L, 0);
        cell.arm(back, run);
        int cx = StormDeck2.S12_ARENA.cx();
        int cz = StormDeck2.S12_ARENA.cz();
        StormTrialSelfTest.Hit core = StormTrialSelfTest.person(back, g, "s12core", cx + 0.5, Y + 1, cz + 0.5);
        StormTrialSelfTest.Hit pad = StormTrialSelfTest.person(back, g, "s12pad", cx + 7.5, Y + 1, cz + 0.5);
        int padWedge = S12Summit.wedgeAt(cx + 7.5, cz + 0.5);
        check(padWedge >= 0, "S12: the pad player is not on a wedge");
        try {
            for (BlockPos p : StormDeck2.s12AllPads()) {
                check(GroupGates.isOpen(back, g, p) == false, "S12: a pad is gone before the fight");
                break;
            }
            long now = 100;
            cell.tick(back, run, List.of(core, pad), now);
            check(run.getInt("phase") == 1, "S12: the fight did not start when players came to the middle");
            check(run.data.get("chief") != null, "S12: no chief spawned");
            // 入れ替えを一周: 状態を見ながら
            List<Integer> seen = new ArrayList<>();
            long t = now;
            for (int i = 0; i < 400 && seen.size() < 6; i++) {
                t += 5;
                cell.tick(back, run, List.of(core), t);
                int st = run.getInt("pstate");
                if (seen.isEmpty() || seen.get(seen.size() - 1) != st) {
                    seen.add(st);
                    for (int w = 0; w < 6; w++) {
                        boolean open = GroupGates.isOpen(back, g, StormDeck2.s12Pads(w));
                        check(open != S12Summit.present(st, w), "S12: wedge " + w + " is " + (open ? "gone" : "present") + " in state " + st);
                    }
                }
            }
            check(seen.size() >= 5 && seen.get(0) == 4 && seen.get(1) == 1 && seen.get(2) == 2 && seen.get(3) == 3 && seen.get(4) == 4, "S12: the pad states ran " + seen);
            // 羽の嵐: 扇の上の人は当たり、芯の人は当たらない
            run.put("pstate", 0);
            for (int w = 0; w < 6; w++) {
                GroupGates.seal(back, g, StormDeck2.s12Pads(w));
            }
            run.put("pNext", t + 100000L);
            run.put("nextStorm", t + 5);
            int hp = pad.hits;
            int hc = core.hits;
            t += 10;
            cell.tick(back, run, List.of(core, pad), t);
            check(run.getInt("stormW") == padWedge, "S12: the storm did not choose the wedge with a player (" + run.getInt("stormW") + ")");
            t += S12Summit.STORM_WARN + 5;
            cell.tick(back, run, List.of(core, pad), t);
            check(pad.hits > hp, "S12: a player on the storm wedge was not hit");
            check(core.hits == hc, "S12: a player on the core was hit by the storm");
            // 羽守長を倒したら済み: 足場が戻り、出口の戸が開く
            Entity chief = back.getEntity((UUID) run.data.get("chief"));
            check(chief != null, "S12: the chief entity is missing");
            if (chief != null) {
                chief.discard();
            }
            t += 10;
            cell.tick(back, run, List.of(core), t);
            check(run.cleared, "S12: beating the chief did not finish the trial");
            check(GroupGates.isOpen(back, g, StormDeck2.S12_EXIT_GATE), "S12: the exit gate did not open");
            check(!GroupGates.isOpen(back, g, StormDeck2.s12Pads(0)) && !GroupGates.isOpen(back, g, StormDeck2.s12Pads(1)), "S12: the pads did not all come back");
        } finally {
            cell.disarm(back, run);
            core.discard();
            pad.discard();
            GroupGates.reset(back, g);
            BackGroupData.get(back).remove(g);
        }
    }

}
