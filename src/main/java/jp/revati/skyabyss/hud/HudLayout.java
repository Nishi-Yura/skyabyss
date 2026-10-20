package jp.revati.skyabyss.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

// HUD の置き場の計算 (docs/design/18_notices.md の 14 章)。Minecraft の型を一つも使わない「長方形の計算だけ」の部品で、
// 自己テスト (HudLayoutSelfTest) が GUI の倍率 1〜4 と代表的な窓の大きさをすべて当てて、重ならないことを確かめる。
//
// 考え方
//  - バニラの部品のうち、ホットバー・体力・満腹・空気・経験値・照準は動かせない「障害物」。
//  - 動かせるバニラ (ボスバー・効き目の絵・札 (トースト)・アクションバーと持ち物の名前・チャット・字幕・スコアボードの横板) は障害物にしない。
//    mod の部品を先に置き、そのあとで重なるバニラを空きへずらす (Result.shiftY。クライアントが描画層ごとに平行移動する)。mod 側は動かさない。
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
        V_SUBTITLES, V_SIDEBAR, V_TOASTS, V_CHAT,
        // ---- 中央 (固定)
        AIM_RING,
        // ---- 領域の手順の板 (右上の一番上に固定。ボスの技の名前に押されない。上中央の札が避ける『帯』には数えない)
        BOSS_DOMAIN,
        // ---- ホットバーの左右 (外側へ)
        QUICK_CARD, GROWTH_BAR, SKILL_SLOTS,
        // ---- 左上の帯 (上から下へ)
        STATUS_TL, SLOTS_TL, CHAPTER, OBJECTIVE, LAMP, QUEST, POPUPS_TL,
        // ---- 右上の板 (上から下へ)
        VOW, BACK_GAUGE, PARTY_LIST,
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
            int bossBottom, int toastH, int chatW, int chatH, int sidebarW, int sidebarH, boolean subtitles, int actionBarW, int toastTop) {
        public Scene(int w, int h, boolean rightHanded, boolean offhand, boolean attackOnHotbar, int healthRows, int effectCols, int effectRows,
                int bossBottom, int toastH, int chatW, int chatH) {
            this(w, h, rightHanded, offhand, attackOnHotbar, healthRows, effectCols, effectRows, bossBottom, toastH, chatW, chatH, 0, 0, false, 0, 0);
        }

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
        // ずらしたバニラの縦の量 (下が正)。ずらさないものは入らない。vanilla にはずらしたあとの長方形が入る
        public final Map<Id, Integer> shiftY = new EnumMap<>(Id.class);
        // 横へずらした量 (右が正)。空きが縦にないとき、横へも動かす (横板・札・チャットなど)
        public final Map<Id, Integer> shiftX = new EnumMap<>(Id.class);
        // チャットの見せる行数の上限 (-1 = そのまま)。0 = いまは出さない (入る空きがない。チャットの窓を開いているときは除く)
        public int chatLines = -1;
        // チャットの幅の上限 (-1 = そのまま)。細くすると、チャットの文はその幅で折り返し直される
        public int chatWidth = -1;
        // 札 (トースト) を同時に出せる数の上限 (5 = そのまま)。札の列が入るだけの空きしかないとき、あとの札は待たせる
        public int toastCap = 5;
        // どこへも動かせなかった動かせるバニラ (計算の途中で使う。効き目・札・横板は、このとき mod の板のほうが避ける組み直しをする)
        final EnumSet<Id> stuck = EnumSet.noneOf(Id.class);

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
        EnumSet<Id> yielding = EnumSet.noneOf(Id.class);
        Result r = compute(s, requests, yielding);
        // 組み直しで別のものが動かせなくなることがあるので、動かせないものが増えなくなるまで (最大 4 回) 繰り返す
        for (int i = 0; i < 4; i++) {
            EnumSet<Id> more = EnumSet.copyOf(yielding);
            for (Id id : r.stuck) {
                if (id != Id.V_BOSS_BARS && id != Id.V_CHAT) {
                    more.add(id);
                }
            }
            if (more.equals(yielding)) {
                break;
            }
            yielding = more;
            r = compute(s, requests, yielding);
        }
        return r;
    }

    private static Result compute(Scene s, List<Request> requests, EnumSet<Id> first) {
        Result r = new Result(s);
        vanilla(s, r);
        // 動かせるバニラは、mod を置くあいだは障害物にしない (置いたあとで空きへずらす)
        Map<Id, Rect> movable = new EnumMap<>(Id.class);
        for (Id id : MOVABLE) {
            Rect m = r.vanilla.remove(id);
            if (m != null) {
                movable.put(id, m);
            }
        }
        // 譲ってもらう組み直し: 効き目 → 札 → 横板の順に、バニラの場所 (重なるものは縦に避ける) へ先に置く
        for (Id id : MOVABLE) {
            if (!first.contains(id) || (movable.get(id) == null && id != Id.V_TOASTS)) {
                continue;
            }
            Rect n = movable.remove(id);
            if (id == Id.V_TOASTS) {
                int rows = Math.max(1, n == null ? 1 : (n.bottom() + 31) / 32);
                Rect col = new Rect(s.w - 160, 0, 160, 32 * rows);
                int[] p = findFreeVertical(r, col, 1, s.h);
                record(r, id, col, p == null ? new int[] {0, 0} : p);
                r.toastCap = 1;
            } else {
                boolean down = id == Id.V_EFFECTS || id == Id.V_SIDEBAR;
                int[] p = findFreeVertical(r, n, down ? 1 : -1, down ? s.h : 140);
                record(r, id, n, p == null ? new int[] {0, 0} : p);
            }
        }
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
        stackLeft(s, r, left);
        List<Request> right = new ArrayList<>();
        for (Request q : reqs) {
            if (q.region == Region.TOP_RIGHT) {
                right.add(q);
            }
        }
        stackColumn(s, r, right, true);
        // ボスバー: 帯 (左上・右上) と重なるなら、帯の下へ下げる。上中央の部品は、下げたボスバーの下に付く
        int bossBottom = s.bossBottom;
        Rect boss = movable.get(Id.V_BOSS_BARS);
        if (boss != null) {
            int dy = shiftAway(r, boss, 1, Math.max(0, s.h / 2 - 12 - boss.bottom()));
            if (dy != 0) {
                r.shiftY.put(Id.V_BOSS_BARS, dy);
            }
            bossBottom += dy;
            r.vanilla.put(Id.V_BOSS_BARS, new Rect(boss.x, boss.y + dy, boss.w, boss.h));
        }
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
            int bcLimit = Math.max(M + (bossBottom > 0 ? bossBottom : 0), centerBottom + 6);
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
        int cursor = bossBottom > 0 ? bossBottom - 2 : M;
        int topBottom = bossBottom > 0 ? bossBottom - 2 : 0;
        for (Request q : reqs) {
            if (q.region != Region.TOP_CENTER) {
                continue;
            }
            int top = Math.max(cursor, q.minTop);
            int limit = Math.min(hintsTop - 6, centerTop - 6);
            Rect got = null;
            if (q.id == Id.BANNER) {
                // 大きな札は、左上・右上の帯 (目的の帯など) の下へ避ける。避けて入らなければ、従来どおり幅を縮めて入れる
                int pushed = pushBelowColumns(s, r, q, top);
                if (pushed > top) {
                    got = placeCentered(s, r, q, pushed, false, limit, false);
                }
            }
            if (got == null) {
                got = placeCentered(s, r, q, top, false, limit, false);
            }
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
        // 7. 動かせるバニラ (効き目・横板は下へ、アクションバー・名前・字幕は上へ。札とチャットは行数・列数を減らしながら)。
        //    mod の部品はそのまま、重なるバニラを空きへずらす。縦で入らなければ横へも動かす
        for (Id id : MOVABLE) {
            Rect n = movable.get(id);
            if (id == Id.V_BOSS_BARS || first.contains(id)) {
                continue;
            }
            if (id == Id.V_TOASTS) {
                placeToasts(s, r, n);
                continue;
            }
            if (n == null) {
                continue;
            }
            if (id == Id.V_CHAT) {
                placeChat(s, r, n);
                continue;
            }
            boolean down = id == Id.V_EFFECTS || id == Id.V_SIDEBAR;
            int[] p = findFree(r, n, down ? 1 : -1, down ? s.h : 140);
            if (p == null) {
                r.stuck.add(id);
            }
            record(r, id, n, p == null ? new int[] {0, 0} : p);
        }
        // 先に置いたバニラが、あとから置いた mod の部品 (中央の照準の輪など) と重なったら、もう一度空きを探す
        for (Id id : first) {
            Rect cur = r.vanilla.get(id);
            if (cur == null || id == Id.V_TOASTS && cur.h == 0) {
                continue;
            }
            boolean hit = false;
            for (Rect m : r.placed.values()) {
                if (m.intersects(cur)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            int sx = r.shiftX.getOrDefault(id, 0);
            int sy = r.shiftY.getOrDefault(id, 0);
            Rect base = new Rect(cur.x - sx, cur.y - sy, cur.w, cur.h);
            r.vanilla.remove(id);
            boolean down = id == Id.V_EFFECTS || id == Id.V_SIDEBAR || id == Id.V_TOASTS;
            int[] p = findFree(r, base, down ? 1 : -1, down ? s.h : 140);
            if (p == null) {
                r.vanilla.put(id, cur);
            } else {
                r.shiftX.remove(id);
                r.shiftY.remove(id);
                record(r, id, base, p);
            }
        }
        return r;
    }

    private static void record(Result r, Id id, Rect n, int[] p) {
        if (p[1] != 0) {
            r.shiftY.put(id, p[1]);
        }
        if (p[0] != 0) {
            r.shiftX.put(id, p[0]);
        }
        r.vanilla.put(id, new Rect(n.x + p[0], n.y + p[1], n.w, n.h));
    }

    // 札 (トースト): 右上の 160 幅の列。何段 (32 ずつ) の列がどこなら空いているかを探し、入る最大の段数を同時に出せる数 (toastCap) にする。
    // いま出ている札があれば、その列 (空いた段も含む) を障害物として置く。札はあとの札を待たせるだけで消さない
    private static void placeToasts(Scene s, Result r, Rect visible) {
        int rows = visible == null ? 0 : (visible.bottom() + 31) / 32;
        int cap = 0;
        int[] capAt = null;
        for (int k = 5; k >= 1; k--) {
            int[] p = findFree(r, new Rect(s.w - 160, 0, 160, 32 * k), 1, s.h);
            if (p != null) {
                cap = k;
                capAt = p;
                break;
            }
        }
        if (cap == 0) {
            if (visible != null) {
                r.stuck.add(Id.V_TOASTS);
            }
            // どこにも入らない (狭い画面に mod の板が詰まっている): 一段だけ、いつもの場所へ。重なるが札を見せることを優先する
            cap = 1;
            capAt = new int[] {0, 0};
        }
        r.toastCap = cap;
        if (visible == null) {
            return;
        }
        int use = Math.max(cap, rows);
        int[] at = capAt;
        if (rows > cap) {
            int[] p = findFree(r, new Rect(s.w - 160, 0, 160, 32 * rows), 1, s.h);
            if (p != null) {
                at = p;
            } else {
                r.stuck.add(Id.V_TOASTS);
            }
        }
        record(r, Id.V_TOASTS, new Rect(s.w - 160, 0, 160, 32 * use), at);
    }

    // チャット: まず幅はそのままで、少しの縦移動で入る行数まで行を減らし、次に縦に大きく動かし、それでも入らなければ横へも動かす。
    // それでも入らなければ幅を細くして (3/4・約半分・約 4 割。最小 110) 同じ順で探す。どれも入らなければ出さない (chatLines = 0)。
    // 見せる行数の上限は必ず出す (chatLines)。幅を細くすると同じ文が折り返して行が増えるので、行数は幅の比で見積もる
    private static void placeChat(Scene s, Result r, Rect n) {
        int want = Math.max(1, (n.h - 2) / 9);
        int[][] passes = {{30, 0}, {s.h, 0}, {s.h, 1}};
        int[] percents = {100, 75, 55, 40};
        for (int[] pass : passes) {
            for (int percent : percents) {
                int w = n.w * percent / 100;
                if (w < 110 && percent < 100) {
                    break;
                }
                int wantNow = percent == 100 ? want : Math.min(20, (int) Math.ceil(want * 100.0 / percent));
                for (int k = wantNow; k >= 1; k--) {
                    // 横へも動かす探しは重いので、行数は (欲しい数・3・2・1) だけ、幅は (全部・約半分・約 4 割) だけ試す
                    if (pass[1] == 1 && (k > 3 && k != wantNow || percent == 75)) {
                        continue;
                    }
                    int h = k * 9 + 2;
                    Rect c = new Rect(n.x, n.bottom() - h, w, h);
                    int[] p = pass[1] == 0 ? findFreeVertical(r, c, -1, pass[0]) : findFree(r, c, -1, pass[0]);
                    if (p != null) {
                        r.chatLines = k;
                        r.chatWidth = percent == 100 ? -1 : w;
                        record(r, Id.V_CHAT, c, p);
                        return;
                    }
                }
            }
        }
        r.chatLines = 0;
    }

    // ずらせるバニラ。この順に、ずらしたものが次のものの障害物になる
    private static final List<Id> MOVABLE = List.of(Id.V_BOSS_BARS, Id.V_ACTION_BAR, Id.V_ITEM_NAME, Id.V_CHAT, Id.V_EFFECTS, Id.V_TOASTS, Id.V_SIDEBAR, Id.V_SUBTITLES);

    // 長方形 n を、いま置かれている mod の部品とバニラの障害物に重ならない所へ動かす縦の量。まず dir (1 = 下、-1 = 上) の向きに、重なっている相手の端まで飛んで探し、
    // 画面からはみ出す・限度 (limit) を超えるなら反対の向きを探す。どちらも入らなければ 0 (動かさない)
    static int shiftAway(Result r, Rect n, int dir, int limit) {
        // まず隙間 GAP を空けて探し、入らなければ隙間 1 で探す
        for (int gap : new int[] {GAP, 1}) {
            for (int d : new int[] {dir, -dir}) {
                int got = shiftAway(r, n, d, limit, gap);
                if (got != Integer.MIN_VALUE) {
                    return got;
                }
            }
        }
        return 0;
    }

    private static int shiftAway(Result r, Rect n, int d, int limit, int gap) {
        Scene s = r.scene;
        int dy = 0;
        for (int i = 0; i < 40; i++) {
            Rect c = new Rect(n.x, n.y + dy, n.w, n.h);
            Rect hit = firstBlocker(r, c, true);
            if (hit == null) {
                return dy == 0 || (c.y >= 0 && c.bottom() <= s.h - 2 && Math.abs(dy) <= limit) ? dy : Integer.MIN_VALUE;
            }
            int next = d > 0 ? hit.bottom() + gap - n.y : hit.y - gap - n.bottom();
            if (d > 0 ? next <= dy : next >= dy) {
                next = dy + d;
            }
            dy = next;
            if (n.y + dy < 0 || n.bottom() + dy > s.h - 2 || Math.abs(dy) > limit) {
                return Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    // 長方形 n を空きへ動かす量 {dx, dy}。dir = 縦の動かしたい向き (1 = 下、-1 = 上)。逆向きは 3 倍の重み、横は 2 倍の重みで、近い空きを選ぶ。
    // 縦の限度 (limit) を超えず、画面の中で、いま置かれた mod の部品とバニラのどれにも重ならない場所。なければ null。動かさずに済めば {0, 0}
    static int[] findFree(Result r, Rect n, int dir, int limit) {
        for (int gap : new int[] {GAP, 1}) {
            int[] got = search(r, n, dir, limit, gap, true);
            if (got != null) {
                return got;
            }
        }
        return null;
    }

    // 縦だけ (横へは動かさない)
    static int[] findFreeVertical(Result r, Rect n, int dir, int limit) {
        for (int gap : new int[] {GAP, 1}) {
            int[] got = search(r, n, dir, limit, gap, false);
            if (got != null) {
                return got;
            }
        }
        return null;
    }

    private static int[] search(Result r, Rect n, int dir, int limit, int gap, boolean sideways) {
        Scene s = r.scene;
        List<Rect> obs = r.solids(true);
        if (!hits(obs, n)) {
            return n.y >= 0 && n.bottom() <= s.h - 2 && n.x >= 0 && n.right() <= s.w ? new int[] {0, 0} : null;
        }
        java.util.TreeSet<Integer> xs = new java.util.TreeSet<>();
        java.util.TreeSet<Integer> ys = new java.util.TreeSet<>();
        xs.add(n.x);
        ys.add(n.y);
        if (sideways) {
            xs.add(2);
            xs.add(s.w - 2 - n.w);
        }
        ys.add(2);
        ys.add(s.h - 2 - n.h);
        for (Rect o : obs) {
            if (sideways) {
                xs.add(o.right() + gap);
                xs.add(o.x - gap - n.w);
            }
            ys.add(o.bottom() + gap);
            ys.add(o.y - gap - n.h);
        }
        // 費用の小さい順に並べて、見つけた最良より高くなったらそこで打ち切る
        List<int[]> yc = new ArrayList<>();
        for (int y : ys) {
            int dy = y - n.y;
            if (y < 0 || y + n.h > s.h - 2 || Math.abs(dy) > limit) {
                continue;
            }
            yc.add(new int[] {y, dy * dir >= 0 ? Math.abs(dy) : 3 * Math.abs(dy)});
        }
        yc.sort((p, q) -> Integer.compare(p[1], q[1]));
        List<int[]> xc = new ArrayList<>();
        for (int x : xs) {
            if (x < 0 || x + n.w > s.w) {
                continue;
            }
            xc.add(new int[] {x, 2 * Math.abs(x - n.x)});
        }
        xc.sort((p, q) -> Integer.compare(p[1], q[1]));
        int bestX = 0;
        int bestY = 0;
        long best = Long.MAX_VALUE;
        for (int[] y : yc) {
            if (y[1] >= best) {
                break;
            }
            for (int[] x : xc) {
                long cost = (long) y[1] + x[1];
                if (cost >= best) {
                    break;
                }
                if (!hits(obs, new Rect(x[0], y[0], n.w, n.h))) {
                    best = cost;
                    bestX = x[0] - n.x;
                    bestY = y[0] - n.y;
                    break;
                }
            }
        }
        return best == Long.MAX_VALUE ? null : new int[] {bestX, bestY};
    }

    private static boolean hits(List<Rect> obs, Rect c) {
        for (Rect o : obs) {
            if (o.intersects(c)) {
                return true;
            }
        }
        return false;
    }

    // 中央に寄せた板が、すでに置いた左上・右上の部品と重なるなら、その下端まで下げた上端を返す
    private static int pushBelowColumns(Scene s, Result r, Request q, int top) {
        Size sz = q.fit.fit(Math.max(1, s.w - 16), Math.max(1, s.h));
        if (sz == null) {
            return top;
        }
        int x = Mth.clamp(s.cx() - sz.w / 2, 2, s.w - 2 - sz.w);
        for (int i = 0; i < 8; i++) {
            Rect cand = new Rect(x, top, sz.w, sz.h);
            Rect hit = null;
            for (Map.Entry<Id, Rect> e : r.placed.entrySet()) {
                if (e.getKey().ordinal() >= Id.STATUS_TL.ordinal() && e.getKey().ordinal() <= Id.PARTY_LIST.ordinal() && e.getValue().intersects(cand)) {
                    hit = e.getValue();
                    break;
                }
            }
            if (hit == null) {
                break;
            }
            top = hit.bottom() + GAP;
        }
        return top;
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
        if (s.actionBarW > 0) {
            r.vanilla.put(Id.V_ACTION_BAR, new Rect(cx - s.actionBarW / 2 - 4, h - 76, s.actionBarW + 8, 14));
        }
        r.vanilla.put(Id.V_CROSSHAIR, new Rect(cx - 8, h / 2 - 8, 16, 16));
        if (s.effectCols > 0) {
            int cols = s.effectCols;
            int rows = Math.max(1, s.effectRows);
            r.vanilla.put(Id.V_EFFECTS, new Rect(w - 25 * cols, 1, 25 * cols, rows >= 2 ? 51 : 25));
        }
        if (s.bossBottom > 0) {
            r.vanilla.put(Id.V_BOSS_BARS, new Rect(cx - 91, 3, 182, Math.max(1, s.bossBottom - 3 - 8)));
        }
        if (s.sidebarW > 0 && s.sidebarH > 0) {
            r.vanilla.put(Id.V_SIDEBAR, new Rect(w - 3 - s.sidebarW, h / 2 + s.sidebarH / 3 - s.sidebarH, s.sidebarW, s.sidebarH));
        }
        if (s.subtitles) {
            r.vanilla.put(Id.V_SUBTITLES, new Rect(w - 150, h - 30 - 33, 148, 33));
        }
        if (s.toastH > 0) {
            int top = Math.min(Math.max(0, s.toastTop), Math.max(0, s.toastH - 1));
            r.vanilla.put(Id.V_TOASTS, new Rect(w - 160, top, 160, s.toastH - top));
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
    // 左上の帯: 得た経験値の列 (POPUPS_TL) は、成長の表示 (STATUS_TL) のすぐ下に置く (目的の帯の下ではない)。ただし優先は最後のまま:
    // まず列なしで積み、列を足しても一つも落ちないときだけ、列を成長の下に入れた積み直しを採る (入らなければ列を出さない)
    private static void stackLeft(Scene s, Result r, List<Request> left) {
        Request popups = null;
        List<Request> rest = new ArrayList<>();
        for (Request q : left) {
            if (q.id == Id.POPUPS_TL) {
                popups = q;
            } else {
                rest.add(q);
            }
        }
        Map<Id, Rect> before = new EnumMap<>(Id.class);
        before.putAll(r.placed);
        EnumSet<Id> droppedBefore = EnumSet.copyOf(r.dropped.isEmpty() ? EnumSet.noneOf(Id.class) : r.dropped);
        stackColumn(s, r, rest, false);
        if (popups == null) {
            return;
        }
        Map<Id, Rect> base = new EnumMap<>(Id.class);
        base.putAll(r.placed);
        EnumSet<Id> baseDropped = EnumSet.copyOf(r.dropped.isEmpty() ? EnumSet.noneOf(Id.class) : r.dropped);
        r.placed.clear();
        r.placed.putAll(before);
        r.dropped.clear();
        r.dropped.addAll(droppedBefore);
        List<Request> withPopups = new ArrayList<>(rest);
        int at = 0;
        for (int i = 0; i < withPopups.size(); i++) {
            if (withPopups.get(i).id == Id.STATUS_TL) {
                at = i + 1;
            }
        }
        withPopups.add(at, popups);
        stackColumn(s, r, withPopups, false);
        boolean keepsAll = r.placed.keySet().containsAll(base.keySet());
        if (!keepsAll || !r.placed.containsKey(Id.POPUPS_TL)) {
            r.placed.clear();
            r.placed.putAll(base);
            r.dropped.clear();
            r.dropped.addAll(baseDropped);
            r.dropped.add(Id.POPUPS_TL);
        }
    }

    private static void stackColumn(Scene s, Result r, List<Request> items, boolean right) {
        int y = M;
        for (Request q : items) {
            // 右上の板はボスバーの右に収まる幅まで (中央の幅 182 の右の端から 4 あけた所。最小 112)。文は Fit が切る
            int maxW = right ? Math.max(112, s.w / 2 - 91 - GAP - M) : Math.max(1, s.w - 2 * M);
            int top = Math.max(y, q.minTop);
            Size sz = null;
            // 左上の帯も同じ: ボスバーの高さにかかるあいだは、ボスバーの左に収まる幅まで (文は折り返す)。その幅で入らない板は、ボスバーの下へ下げる
            int bossLeft = s.cx() - 91 - GAP - LEFT_X;
            int bossLow = s.bossBottom > 0 ? s.bossBottom - 8 + GAP : 0;
            if (!right && top < bossLow && bossLeft >= 40) {
                sz = q.fit.fit(Math.min(maxW, bossLeft), Math.max(1, s.h));
                if (sz == null) {
                    top = Math.max(top, bossLow);
                }
            }
            if (sz == null) {
                sz = q.fit.fit(maxW, Math.max(1, s.h));
            }
            if (sz == null) {
                r.dropped.add(q.id);
                continue;
            }
            int x = right ? s.w - M - sz.w : LEFT_X;
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
        return b.equals(r.vanilla.get(Id.V_EFFECTS)) || b.equals(r.vanilla.get(Id.V_TOASTS)) || b.equals(r.vanilla.get(Id.V_BOSS_BARS))
                || b.equals(r.vanilla.get(Id.V_SIDEBAR));
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
        // 余白 10 で空きを探し、なければ余白なし (板の上でなければよい) で探す
        // 札 (ラベル) は矢印の横へ 100 ほど広がるので、まずは横に広い余白 (64 x 12)、次に 10、最後は余白なしで探す
        // 板の一覧は一度だけ取り、余白ぶん広げた形も余白ごとに一度だけ作る (周を 4 px 刻みで探るので、探るたびに作ると重い)
        List<Rect> solids = r.solids(false);
        for (int[] margin : new int[][] {{64, 12}, {10, 10}, {0, 0}}) {
            Rect[] grown = grow(solids, margin[0], margin[1]);
            if (!blocked(grown, best[0], best[1])) {
                return best;
            }
            for (int step = 4; step < perimeter / 2; step += 4) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    double pos = at + dir * step;
                    pos = ((pos % perimeter) + perimeter) % perimeter;
                    int[] q = perimeterPoint(pos, x0, y0, x1, y1);
                    if (!blocked(grown, q[0], q[1])) {
                        return q;
                    }
                }
            }
        }
        return best;
    }

    private static Rect[] grow(List<Rect> solids, int mx, int my) {
        Rect[] out = new Rect[solids.size()];
        for (int i = 0; i < out.length; i++) {
            Rect o = solids.get(i);
            out[i] = mx == 0 && my == 0 ? o : new Rect(o.x - mx, o.y - my, o.w + 2 * mx, o.h + 2 * my);
        }
        return out;
    }

    // 矢印 (十数 px) が重なるか: 余白ぶん広げた障害物のどれかに入るか
    private static boolean blocked(Rect[] grown, int x, int y) {
        for (Rect o : grown) {
            if (o.contains(x, y)) {
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
