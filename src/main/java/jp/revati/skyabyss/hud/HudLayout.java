package jp.revati.skyabyss.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

// HUD の置き場の計算 (docs/design/18_notices.md の 9 章)。Minecraft の型を一つも使わない「長方形の計算だけ」の部品で、
// 自己テスト (HudLayoutSelfTest) が GUI の倍率 1〜4 と代表的な窓の大きさをすべて当てて、重ならないことを確かめる。
//
// 考え方
//  - バニラの部品 (ホットバー・体力・満腹・空気・経験値・アクションバー・効き目の絵・ボスバー・照準・札 (トースト)) は動かせない「障害物」。
//  - mod の部品は「要求 (Request)」を出す。要求は名前 (Id)・置き場の区分・寸法の決め方 (Fit) だけ。座標は持たない。
//  - 計算は一回で全部の要求を受け取り、区分ごとに積む。入らなければ寸法を縮め (Fit に幅と高さの上限を渡す)、それでも入らなければ
//    優先の低いほうを「出さない」(dropped)。重ねて描くことは決してしない。
//  - 区分: 左上の帯 (上から下へ積む)、右上の板 (効き目の絵と札の下から積む)、上中央 (ボスバーの下へ積む)、下中央 (アクションバーの上から
//    上へ積む。手がかり → 字幕)、ホットバーの左右 (外側へ並べる。入らなければ左上へ逃がす)、中央 (固定)。
//
// 座標はすべて GUI の拡大後の座標 (guiWidth × guiHeight)。倍率が上がると GUI は小さくなるので、同じ計算で倍率にも窓の大きさにも合う。
public final class HudLayout {
    public static final int M = 6;
    public static final int GAP = 4;
    // 左上の帯の左端 (背景の板が 3 から始まる。QuestHud / ObjectiveStrip と同じ)
    public static final int LEFT_X = 3;
    // アクションバー (バニラ: 下から 68) の上に空ける線。手がかりの下端
    public static final int HINT_BOTTOM = 78;

    private HudLayout() {
    }

    // ------------------------------------------------------------ 基本の型
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean intersects(Rect o) {
            return w > 0 && h > 0 && o.w > 0 && o.h > 0 && x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }

        public Rect inflate(int d) {
            return new Rect(x - d, y - d, w + 2 * d, h + 2 * d);
        }

        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public record Size(int w, int h) {
    }

    // 寸法の決め方: 幅の上限 maxW・高さの上限 maxH に収まる寸法を返す。収まらなければ null (出さない)。
    // 文なら幅に合わせて折り返し、行数を減らして高さに合わせる。呼ぶ側 (描画) も、置かれた長方形の幅と高さを上限にして
    // 同じ呼び方をすれば同じ中身になる (決まった結果なので、枠は動かない)
    @FunctionalInterface
    public interface Fit {
        Size fit(int maxW, int maxH);

        static Fit fixed(int w, int h) {
            return (maxW, maxH) -> w <= maxW && h <= maxH ? new Size(w, h) : null;
        }
    }

    public enum Region {
        TOP_LEFT, TOP_RIGHT, TOP_CENTER, BOTTOM_CENTER, CAPTION, SIDE_LEFT, SIDE_RIGHT, CENTER
    }

    // 名前。mod の部品は並びが「区分の中の優先と上下の順」になる (先のほうが優先で、上や内側)
    public enum Id {
        // ---- バニラ (障害物。動かさない)
        V_HOTBAR, V_OFFHAND, V_ATTACK, V_XP, V_HEALTH, V_ARMOR, V_FOOD, V_AIR, V_ITEM_NAME, V_ACTION_BAR, V_EFFECTS, V_BOSS_BARS, V_CROSSHAIR,
        V_TOASTS, V_CHAT,
        // ---- 中央 (固定)
        AIM_RING,
        // ---- ホットバーの左右 (外側へ)
        QUICK_CARD, GROWTH_BAR, SKILL_SLOTS,
        // ---- 左上の帯 (上から下へ)
        STATUS_TL, SLOTS_TL, POPUPS_TL, OBJECTIVE, QUEST,
        // ---- 右上の板 (上から下へ)
        VOW, BACK_GAUGE, BACK_GOAL, PARTY_LIST,
        // ---- 下中央 (下から上へ)
        DOWNED_SELF, HINTS,
        // ---- 上中央 (ボスバーの下へ)
        BOSS_CUE, BANNER, LEVEL_UP, CENTER_LINE,
        // ---- 字幕 (上中央と手がかりのあいだ)
        CAPTION;

        public boolean vanilla() {
            return ordinal() <= V_CHAT.ordinal();
        }
    }

    public enum Side {
        LEFT, RIGHT
    }

    // 要求。minTop = 上端の下限 (0 = なし)。baseline = ホットバーの左右の板の下端の、画面の下からの高さ。
    // side = ホットバーの左右 (QUICK_CARD は持ち手で決まる)。fallback = ホットバーの横に入らないときに左上へ逃がす要求
    public record Request(Id id, Region region, Fit fit, int minTop, int baseline, Side side, List<Request> fallback, Rect fixed, int minH) {
        public static Request of(Id id, Region region, Fit fit) {
            return new Request(id, region, fit, 0, 0, null, List.of(), null, 0);
        }

        public static Request of(Id id, Region region, int w, int h) {
            return of(id, region, Fit.fixed(w, h));
        }

        public Request minTop(int top) {
            return new Request(id, region, fit, top, baseline, side, fallback, fixed, minH);
        }

        // 他の部品と場所を分け合うときに、最低でも確保しておく高さ (字幕は一行ぶん)
        public Request minH(int min) {
            return new Request(id, region, fit, minTop, baseline, side, fallback, fixed, min);
        }

        public static Request side(Id id, Side side, int w, int h, int baseline, Request... fallback) {
            return new Request(id, side == Side.LEFT ? Region.SIDE_LEFT : Region.SIDE_RIGHT, Fit.fixed(w, h), 0, baseline, side, List.of(fallback), null, 0);
        }

        // 中央に固定の長方形 (画面の中心からの相対: dx, dy は中心から左上まで)
        public static Request center(Id id, int dx, int dy, int w, int h) {
            return new Request(id, Region.CENTER, Fit.fixed(w, h), 0, 0, null, List.of(), new Rect(dx, dy, w, h), 0);
        }
    }

    // 画面とバニラの状態
    public record Scene(int w, int h, boolean rightHanded, boolean offhand, boolean attackOnHotbar, int healthRows, int effectCols, int effectRows,
            int bossBottom, int toastH, int chatW, int chatH) {
        public static Scene plain(int w, int h) {
            return new Scene(w, h, true, false, false, 1, 0, 0, 0, 0, 0, 0);
        }

        public int cx() {
            return w / 2;
        }
    }

    // 結果。placed = 置けた mod の部品、dropped = 出さないことにした要求、obstacles = バニラの障害物
    public static final class Result {
        public final Scene scene;
        public final Map<Id, Rect> placed = new EnumMap<>(Id.class);
        public final Map<Id, Rect> vanilla = new EnumMap<>(Id.class);
        public final EnumSet<Id> dropped = EnumSet.noneOf(Id.class);

        Result(Scene scene) {
            this.scene = scene;
        }

        public Rect rect(Id id) {
            return placed.get(id);
        }

        public boolean has(Id id) {
            return placed.containsKey(id);
        }

        // 下中央の字幕・手がかりの上限などを調べるための「いま積まれている全部」
        public List<Rect> solids(boolean withChat) {
            List<Rect> all = new ArrayList<>(placed.values());
            for (Map.Entry<Id, Rect> e : vanilla.entrySet()) {
                if (e.getKey() == Id.V_CHAT && !withChat) {
                    continue;
                }
                all.add(e.getValue());
            }
            return all;
        }
    }

    // ------------------------------------------------------------ 計算
    public static Result compute(Scene s, List<Request> requests) {
        Result r = new Result(s);
        vanilla(s, r);
        List<Request> reqs = new ArrayList<>(requests);
        reqs.sort((a, b) -> a.id.compareTo(b.id));
        // 1. 中央の固定 (照準の輪)
        for (Request q : reqs) {
            if (q.region == Region.CENTER) {
                placeCenterFixed(s, r, q);
            }
        }
        // 2. ホットバーの左右 (外側へ。入らなければ左上へ逃がす)
        List<Request> left = new ArrayList<>();
        for (Request q : reqs) {
            if (q.region == Region.TOP_LEFT) {
                left.add(q);
            }
        }
        for (Side side : Side.values()) {
            int edge = sideEdge(s, side);
            for (Request q : reqs) {
                if (q.side != side) {
                    continue;
                }
                Size sz = q.fit.fit(s.w, s.h);
                if (sz == null) {
                    r.dropped.add(q.id);
                    continue;
                }
                int x = side == Side.LEFT ? edge - GAP + 1 - sz.w : edge + GAP - 1;
                boolean fits = side == Side.LEFT ? x >= 2 : x + sz.w <= s.w - 2;
                Rect cand = new Rect(x, s.h - q.baseline - sz.h, sz.w, sz.h);
                if (fits && firstBlocker(r, cand, false) == null) {
                    r.placed.put(q.id, cand);
                    edge = side == Side.LEFT ? x : x + sz.w;
                } else if (!q.fallback.isEmpty()) {
                    left.addAll(q.fallback);
                } else {
                    r.dropped.add(q.id);
                }
            }
        }
        left.sort((a, b) -> a.id.compareTo(b.id));
        // 3. 左上・右上の帯
        stackColumn(s, r, left, false);
        List<Request> right = new ArrayList<>();
        for (Request q : reqs) {
            if (q.region == Region.TOP_RIGHT) {
                right.add(q);
            }
        }
        stackColumn(s, r, right, true);
        // 照準 (と起こす輪) のまわりは空ける: 下中央の部品はその下へ、上中央の部品はその上へ収める
        Rect cross = r.vanilla.get(Id.V_CROSSHAIR);
        int centerTop = cross.y;
        int centerBottom = cross.bottom();
        Rect ring = r.placed.get(Id.AIM_RING);
        if (ring != null) {
            centerTop = Math.min(centerTop, ring.y);
            centerBottom = Math.max(centerBottom, ring.bottom());
        }
        // 4. 下中央 (アクションバーの上から上へ。倒れた表示 → 手がかり)
        int anchor = s.h - HINT_BOTTOM;
        // 字幕があるなら、その最低の高さ (と間) を手がかりが食わない
        int reserve = 0;
        for (Request q : reqs) {
            if (q.region == Region.CAPTION) {
                reserve = q.minH + 6;
            }
        }
        for (Request q : reqs) {
            if (q.region != Region.BOTTOM_CENTER) {
                continue;
            }
            int bcLimit = Math.max(M + (s.bossBottom > 0 ? s.bossBottom : 0), centerBottom + 6);
            if (q.id == Id.HINTS) {
                bcLimit += reserve;
            }
            Rect got = placeCentered(s, r, q, anchor, true, bcLimit, true);
            if (got == null) {
                r.dropped.add(q.id);
            } else {
                r.placed.put(q.id, got);
                anchor = got.y - 2;
            }
        }
        int hintsTop = anchor;
        // 5. 上中央 (ボスバーの下へ。技の名前 → 上の帯 → レベルアップ → 中央の一行)
        int cursor = s.bossBottom > 0 ? s.bossBottom - 2 : M;
        int topBottom = s.bossBottom > 0 ? s.bossBottom - 2 : 0;
        for (Request q : reqs) {
            if (q.region != Region.TOP_CENTER) {
                continue;
            }
            int top = Math.max(cursor, q.minTop);
            Rect got = placeCentered(s, r, q, top, false, Math.min(hintsTop - 6, centerTop - 6), false);
            if (got == null) {
                r.dropped.add(q.id);
            } else {
                r.placed.put(q.id, got);
                cursor = got.bottom() + 8;
                topBottom = got.bottom();
            }
        }
        // 6. 字幕 (上中央と手がかりのあいだに、行を減らしながら入れる)
        for (Request q : reqs) {
            if (q.region != Region.CAPTION) {
                continue;
            }
            int ceiling = Math.max(topBottom > 0 ? topBottom + 8 : M, centerBottom + 6);
            Rect got = placeCentered(s, r, q, hintsTop - 6, true, ceiling, true);
            if (got == null) {
                r.dropped.add(q.id);
            } else {
                r.placed.put(q.id, got);
            }
        }
        return r;
    }

    // ------------------------------------------------------------ バニラ
    private static void vanilla(Scene s, Result r) {
        int cx = s.cx();
        int w = s.w;
        int h = s.h;
        r.vanilla.put(Id.V_HOTBAR, new Rect(cx - 91, h - 22, 182, 22));
        if (s.offhand) {
            r.vanilla.put(Id.V_OFFHAND, s.rightHanded ? new Rect(cx - 91 - 29, h - 23, 29, 24) : new Rect(cx + 91, h - 23, 29, 24));
        }
        if (s.attackOnHotbar) {
            r.vanilla.put(Id.V_ATTACK, s.rightHanded ? new Rect(cx + 91 + 6, h - 20, 18, 18) : new Rect(cx - 91 - 22, h - 20, 18, 18));
        }
        r.vanilla.put(Id.V_XP, new Rect(cx - 91, h - 29, 182, 5));
        int extra = (Math.max(1, s.healthRows) - 1) * 10;
        r.vanilla.put(Id.V_HEALTH, new Rect(cx - 91, h - 39 - extra, 81, 9 + extra));
        r.vanilla.put(Id.V_ARMOR, new Rect(cx - 91, h - 49 - extra, 81, 9));
        r.vanilla.put(Id.V_FOOD, new Rect(cx + 10, h - 39, 81, 9));
        r.vanilla.put(Id.V_AIR, new Rect(cx + 10, h - 49, 81, 9));
        r.vanilla.put(Id.V_ITEM_NAME, new Rect(cx - 70, h - 59, 140, 9));
        r.vanilla.put(Id.V_ACTION_BAR, new Rect(cx - 120, h - 76, 240, 14));
        r.vanilla.put(Id.V_CROSSHAIR, new Rect(cx - 8, h / 2 - 8, 16, 16));
        if (s.effectCols > 0) {
            int cols = s.effectCols;
            int rows = Math.max(1, s.effectRows);
            r.vanilla.put(Id.V_EFFECTS, new Rect(w - 25 * cols, 1, 25 * cols, rows >= 2 ? 51 : 25));
        }
        if (s.bossBottom > 0) {
            r.vanilla.put(Id.V_BOSS_BARS, new Rect(cx - 91, 3, 182, Math.max(1, s.bossBottom - 3 - 8)));
        }
        if (s.toastH > 0) {
            r.vanilla.put(Id.V_TOASTS, new Rect(w - 160, 0, 160, s.toastH));
        }
        if (s.chatW > 0 && s.chatH > 0) {
            r.vanilla.put(Id.V_CHAT, new Rect(0, h - 40 - s.chatH, s.chatW, s.chatH));
        }
    }

    // ホットバーの横の「外側の端」: バニラの枠 (オフハンド・攻撃の目盛り) の先
    private static int sideEdge(Scene s, Side side) {
        int cx = s.cx();
        return switch (side) {
            case LEFT -> {
                int e = cx - 91;
                if (s.offhand && s.rightHanded) {
                    e -= 29;
                }
                if (s.attackOnHotbar && !s.rightHanded) {
                    e -= 29;
                }
                yield e;
            }
            case RIGHT -> {
                int e = cx + 91;
                if (s.offhand && !s.rightHanded) {
                    e += 29;
                }
                if (s.attackOnHotbar && s.rightHanded) {
                    e += 26;
                }
                yield e;
            }
        };
    }

    private static void placeCenterFixed(Scene s, Result r, Request q) {
        Rect f = q.fixed;
        r.placed.put(q.id, new Rect(s.cx() + f.x, s.h / 2 + f.y, f.w, f.h));
    }

    // ------------------------------------------------------------ 帯 (左上・右上)
    private static void stackColumn(Scene s, Result r, List<Request> items, boolean right) {
        int y = M;
        for (Request q : items) {
            Size sz = q.fit.fit(Math.max(1, s.w - 2 * M), Math.max(1, s.h));
            if (sz == null) {
                r.dropped.add(q.id);
                continue;
            }
            int x = right ? s.w - M - sz.w : LEFT_X;
            int top = Math.max(y, q.minTop);
            Rect cand = null;
            for (int i = 0; i < 10; i++) {
                cand = new Rect(x, top, sz.w, sz.h);
                if (cand.bottom() > s.h - 2) {
                    cand = null;
                    break;
                }
                Rect b = firstBlocker(r, cand, false);
                if (b == null) {
                    break;
                }
                if (pushable(r, b)) {
                    top = b.bottom() + GAP;
                    cand = null;
                } else {
                    cand = null;
                    break;
                }
            }
            if (cand == null) {
                r.dropped.add(q.id);
                continue;
            }
            r.placed.put(q.id, cand);
            y = cand.bottom() + GAP;
        }
    }

    // 帯の部品が下へ避けてよい障害物 (効き目の絵・札・ボスバー)
    private static boolean pushable(Result r, Rect b) {
        return b.equals(r.vanilla.get(Id.V_EFFECTS)) || b.equals(r.vanilla.get(Id.V_TOASTS)) || b.equals(r.vanilla.get(Id.V_BOSS_BARS));
    }

    // 候補と重なる、いま積まれているもの (なければ null)。チャットは withChat のときだけ数える
    static Rect firstBlocker(Result r, Rect cand, boolean withChat) {
        for (Rect o : r.solids(withChat)) {
            if (o.intersects(cand)) {
                return o;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 中央に寄せる部品 (下中央・上中央・字幕)
    // anchor を基準に、up なら anchor を下端として上へ、そうでなければ anchor を上端として下へ置く。
    // limit は反対側の限界 (up なら上端の下限、下へなら下端の上限)。左右は障害物のあいだの幅に縮めて入れる。入らなければ null
    private static Rect placeCentered(Scene s, Result r, Request q, int anchor, boolean up, int limit, boolean withChat) {
        int maxW = Math.max(1, s.w - 2 * 8);
        int maxH = up ? anchor - limit : limit - anchor;
        if (maxH <= 0) {
            return null;
        }
        int cx = s.cx();
        int lo = 2;
        int hi = s.w - 2;
        Integer forcedX = null;
        for (int iter = 0; iter < 10; iter++) {
            Size sz = q.fit.fit(maxW, maxH);
            if (sz == null) {
                return null;
            }
            int y = up ? anchor - sz.h : anchor;
            int x = forcedX != null ? forcedX : Mth.clamp(cx - sz.w / 2, lo, hi - sz.w);
            if (x + sz.w > hi || x < lo) {
                x = Mth.clamp(cx - sz.w / 2, lo, hi - sz.w);
            }
            Rect cand = new Rect(x, y, sz.w, sz.h);
            boolean any = false;
            for (Rect o : r.solids(withChat)) {
                if (!o.intersects(cand)) {
                    continue;
                }
                any = true;
                // 中心をまたぐ障害物 (照準など) があれば入らない
                if (o.x <= cx && o.right() >= cx) {
                    return null;
                }
                if (o.right() <= cx) {
                    lo = Math.max(lo, o.right() + 4);
                } else {
                    hi = Math.min(hi, o.x - 4);
                }
            }
            if (!any) {
                return cand;
            }
            int room = hi - lo;
            if (room <= 0) {
                return null;
            }
            if (sz.w > room) {
                if (room >= maxW) {
                    return null;
                }
                maxW = room;
            }
            forcedX = null;
        }
        return null;
    }

    // ------------------------------------------------------------ 画面の外の矢印 (仲間・墓の標)
    // 画面の中心から向き (vx, vy) へ伸ばした線が、縁の長方形と交わる点。そこが障害物 (置かれた板・バニラ) の上なら、縁を伝って空いた所まで動かす。
    // 札の大きさ (半径 reach) の分、障害物を広げて避ける。縁の長方形は余白 14、下は 56 (ホットバー)
    public static int[] edgePoint(Result r, double vx, double vy) {
        Scene s = r.scene;
        double len = Math.sqrt(vx * vx + vy * vy);
        if (len < 1e-6) {
            vx = 0;
            vy = 1;
            len = 1;
        }
        vx /= len;
        vy /= len;
        double x0 = 14;
        double y0 = 14;
        double x1 = s.w - 14;
        double y1 = s.h - 56;
        double cx = s.w / 2.0;
        double cy = s.h / 2.0;
        double hw = Math.max(1, vx > 0 ? x1 - cx : cx - x0);
        double hh = Math.max(1, vy > 0 ? y1 - cy : cy - y0);
        double t = Math.min(Math.abs(vx) < 1e-6 ? Double.MAX_VALUE : hw / Math.abs(vx), Math.abs(vy) < 1e-6 ? Double.MAX_VALUE : hh / Math.abs(vy));
        // 周の長さに沿った位置 (左上から時計回り) にして、両側へ動かす
        double[] p0 = {cx + vx * t, cy + vy * t};
        double perimeter = 2 * ((x1 - x0) + (y1 - y0));
        double at = perimeterPos(p0[0], p0[1], x0, y0, x1, y1);
        int[] best = {(int) Math.round(p0[0]), (int) Math.round(p0[1])};
        if (!blocked(r, best[0], best[1])) {
            return best;
        }
        for (int step = 4; step < perimeter / 2; step += 4) {
            for (int dir = -1; dir <= 1; dir += 2) {
                double pos = at + dir * step;
                pos = ((pos % perimeter) + perimeter) % perimeter;
                int[] q = perimeterPoint(pos, x0, y0, x1, y1);
                if (!blocked(r, q[0], q[1])) {
                    return q;
                }
            }
        }
        return best;
    }

    // 矢印 (十数 px) が重なるか: 障害物を 10 広げて調べる
    private static boolean blocked(Result r, int x, int y) {
        for (Rect o : r.solids(false)) {
            if (o.inflate(10).contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private static double perimeterPos(double x, double y, double x0, double y0, double x1, double y1) {
        double wid = x1 - x0;
        double hei = y1 - y0;
        double eps = 1e-6;
        if (Math.abs(y - y0) < eps) {
            return x - x0;
        }
        if (Math.abs(x - x1) < eps) {
            return wid + (y - y0);
        }
        if (Math.abs(y - y1) < eps) {
            return wid + hei + (x1 - x);
        }
        return 2 * wid + hei + (y1 - y);
    }

    private static int[] perimeterPoint(double pos, double x0, double y0, double x1, double y1) {
        double wid = x1 - x0;
        double hei = y1 - y0;
        if (pos < wid) {
            return new int[] {(int) Math.round(x0 + pos), (int) Math.round(y0)};
        }
        pos -= wid;
        if (pos < hei) {
            return new int[] {(int) Math.round(x1), (int) Math.round(y0 + pos)};
        }
        pos -= hei;
        if (pos < wid) {
            return new int[] {(int) Math.round(x1 - pos), (int) Math.round(y1)};
        }
        pos -= wid;
        return new int[] {(int) Math.round(x0), (int) Math.round(y1 - pos)};
    }

    // ------------------------------------------------------------ 補助
    private static final class Mth {
        private Mth() {
        }

        static int clamp(int v, int lo, int hi) {
            return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
        }
    }

    // GUI の大きさ (窓のピクセルと倍率から。バニラの Window.setGuiScale と同じ計算)
    public static int guiSize(int pixels, int scale) {
        return (int) Math.ceil(pixels / (double) scale);
    }

    // この窓で選べる倍率の上限 (バニラ: 幅 320・高さ 240 を割らない最大の整数)
    public static int maxScale(int pixelW, int pixelH) {
        int scale = 1;
        while (scale < Integer.MAX_VALUE && pixelW / (scale + 1) >= 320 && pixelH / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }
}
