package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 潮見の間の水位の謎解き (08 の 4.2節) の決まりだけを持つ (ブロックは TideWatch)。自己テストはここで四つの配置を解く。
// 溜めは三つ: 下 B (0)・中 M (1)・上 T (2)。段の数は配置ごとの上限まで。
// 栓は「どこから・どこへ」を持ち、開いている間は 2 秒に一段ずつ水を移す (元が空か、先が上限なら止まる)。
// 重石の床に誰かが立っている間は、つながった栓の向きが逆になる。
// 人の動き: 段 k の床から浮き石 (B・M) に乗れるのは、その溜めが 1 段以下のとき (戸が開いている)。
//          浮き石から次の段へ上がれるのは、溜めが 5 段以上のとき。上の溜め T が空になると、底の戸から岩棚へ行ける。
public final class TidePuzzle {
    public static final int REACH = 5;
    public static final int FLOW_TICKS = 40;

    public record Plug(int from, int to, int tier) {
    }

    public record Config(int[] caps, int[] init, Plug[] plugs, int plateTier, int platePlug) {
    }

    // 四つの配置。0 は「ワールドで初めて挑むとき」に必ず使う、いちばんやさしいもの。
    // 栓の段 (tier) は、その栓の取っ手がある段 (0 = 前室の段、1 = 中の段、2 = 上の段)
    public static final Config[] CONFIGS = {
            new Config(new int[] {5, 7, 7}, new int[] {1, 4, 6}, new Plug[] {new Plug(2, 1, 1), new Plug(1, 0, 0)}, 0, 0),
            new Config(new int[] {5, 7, 7}, new int[] {1, 3, 6}, new Plug[] {new Plug(2, 1, 1), new Plug(1, 0, 0), new Plug(2, 0, 0)}, 0, 0),
            new Config(new int[] {7, 6, 7}, new int[] {1, 5, 6}, new Plug[] {new Plug(2, 1, 0), new Plug(1, 0, 0)}, 1, 0),
            new Config(new int[] {5, 5, 6}, new int[] {1, 3, 6}, new Plug[] {new Plug(2, 1, 1), new Plug(1, 0, 1), new Plug(2, 0, 0)}, 1, 2)};

    private TidePuzzle() {
    }

    // 一歩 (2 秒) 進める。plate: 重石の床に誰かが立っているか。水の段を書き換え、動いたら true
    public static boolean step(Config c, int[] levels, boolean[] open, boolean plate) {
        boolean moved = false;
        for (int i = 0; i < c.plugs().length; i++) {
            if (!open[i]) {
                continue;
            }
            int from = c.plugs()[i].from();
            int to = c.plugs()[i].to();
            if (plate && c.platePlug() == i) {
                int t = from;
                from = to;
                to = t;
            }
            if (levels[from] > 0 && levels[to] < c.caps()[to]) {
                levels[from]--;
                levels[to]++;
                moved = true;
            }
        }
        return moved;
    }

    // 人の居場所: 段 0・1・2 (P)、浮き石 B・M (F)、重石の床 (PLATE)、岩棚 (GOAL)
    enum Loc {
        P0, FB, P1, FM, P2, PLATE, GOAL
    }

    private record State(int b, int m, int t, int open, Loc loc) {
    }

    // 一人で解ける最短の手数 (栓を回す・乗る・上がるを 1 手、水を待つのは 0 手)。解けなければ -1
    public static int solve(Config c) {
        State start = new State(c.init()[0], c.init()[1], c.init()[2], 0, Loc.P0);
        Map<State, Integer> dist = new HashMap<>();
        ArrayDeque<State> queue = new ArrayDeque<>();
        dist.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            State s = queue.pollFirst();
            int d = dist.get(s);
            if (s.loc() == Loc.GOAL) {
                return d;
            }
            for (Object[] n : next(c, s)) {
                State ns = (State) n[0];
                int w = (int) n[1];
                Integer old = dist.get(ns);
                if (old == null || old > d + w) {
                    dist.put(ns, d + w);
                    if (w == 0) {
                        queue.addFirst(ns);
                    } else {
                        queue.addLast(ns);
                    }
                }
            }
        }
        return -1;
    }

    private static List<Object[]> next(Config c, State s) {
        List<Object[]> out = new ArrayList<>();
        int[] lv = {s.b(), s.m(), s.t()};
        boolean[] open = new boolean[c.plugs().length];
        for (int i = 0; i < open.length; i++) {
            open[i] = (s.open() & (1 << i)) != 0;
        }
        int[] after = lv.clone();
        if (step(c, after, open, s.loc() == Loc.PLATE)) {
            out.add(new Object[] {new State(after[0], after[1], after[2], s.open(), s.loc()), 0});
        }
        int tier = switch (s.loc()) {
            case P0 -> 0;
            case P1 -> 1;
            case P2 -> 2;
            default -> -1;
        };
        if (tier >= 0) {
            for (int i = 0; i < open.length; i++) {
                if (c.plugs()[i].tier() == tier) {
                    out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open() ^ (1 << i), s.loc()), 1});
                }
            }
            if (tier < 2 && lv[tier] <= 1) {
                out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open(), tier == 0 ? Loc.FB : Loc.FM), 1});
            }
            if (tier > 0) {
                out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open(), tier == 1 ? Loc.FB : Loc.FM), 1});
            }
            if (tier == 2 && lv[2] == 0) {
                out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open(), Loc.GOAL), 1});
            }
            if (c.plateTier() == tier) {
                out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open(), Loc.PLATE), 1});
            }
        }
        if (s.loc() == Loc.PLATE) {
            Loc back = c.plateTier() == 0 ? Loc.P0 : c.plateTier() == 1 ? Loc.P1 : Loc.P2;
            out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open(), back), 0});
        }
        if (s.loc() == Loc.FB || s.loc() == Loc.FM) {
            int k = s.loc() == Loc.FB ? 0 : 1;
            if (lv[k] <= 1) {
                out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open(), k == 0 ? Loc.P0 : Loc.P1), 1});
            }
            if (lv[k] >= REACH) {
                out.add(new Object[] {new State(lv[0], lv[1], lv[2], s.open(), k == 0 ? Loc.P1 : Loc.P2), 1});
            }
        }
        return out;
    }
}
