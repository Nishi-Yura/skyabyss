package jp.revati.skyabyss.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.hud.HudLayout.Fit;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.hud.HudLayout.Region;
import jp.revati.skyabyss.hud.HudLayout.Request;
import jp.revati.skyabyss.hud.HudLayout.Result;
import jp.revati.skyabyss.hud.HudLayout.Scene;
import jp.revati.skyabyss.hud.HudLayout.Side;
import jp.revati.skyabyss.hud.HudLayout.Size;
import net.minecraft.server.MinecraftServer;

// HUD の置き場の自己テスト (docs/design/18 の 9 章)。長方形の計算だけで、ゲームを開かずに回せる (main でも動く)。
// GUI の倍率 1〜4 × 代表的な窓の大きさ × バニラの状態 (ボスバー・効き目・オフハンド・利き手・体力の段・札・チャット) × mod の部品の
// ありなし (全部 + 乱数の組み合わせ) をすべて当てて、
//   1. 置かれた長方形はどれも画面に収まる
//   2. mod の部品どうし、mod の部品とバニラの部品が重ならない (チャットは下中央の部品だけ)
//   3. ふつうの大きさ (GUI 幅 427 以上・高さ 240 以上) で、字幕と手がかりと左上の目的が出る (全部が同時に出る最悪の場面でも)
// を確かめる。出さないことにした部品は dropped に入り、描かない (重ねて描くことはしない)
public final class HudLayoutSelfTest {
    private HudLayoutSelfTest() {
    }

    // 窓のピクセル (幅, 高さ)
    static final int[][] WINDOWS = {{854, 480}, {1024, 768}, {1280, 720}, {1280, 960}, {1366, 768}, {1600, 900}, {1600, 1200}, {1920, 1080},
            {1920, 1200}, {2560, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}, {800, 600}, {640, 480}, {1152, 864}};

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        StringBuilder stats = new StringBuilder();
        try {
            run(f, stats);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("hud layout selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST hud layout PASSED (no overlap at GUI scales 1-4 x {} windows, random subsets, critical survival)", WINDOWS.length);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST hud layout FAILED: {}", m));
        }
    }

    public static void main(String[] args) {
        List<String> f = new ArrayList<>();
        StringBuilder stats = new StringBuilder();
        run(f, stats);
        System.out.println(stats);
        if (f.isEmpty()) {
            System.out.println("HUD LAYOUT OK");
        } else {
            f.stream().limit(40).forEach(m -> System.out.println("FAIL " + m));
            System.out.println("FAILURES " + f.size());
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ 要求の見本 (各描画層の寸法の決め方と同じ形)
    // 折り返す文: 自然の幅 natW、一行の高さ lineH、行数 lines (幅 maxW に縮むと行数が増える)。maxH に収まるまで行を減らす
    static Fit wrapped(int natW, int lines, int lineH, int pad, int maxLines) {
        return (maxW, maxH) -> {
            int w = Math.min(natW, maxW);
            if (w < 60) {
                return null;
            }
            int n = (int) Math.ceil(lines * (double) natW / w);
            n = Math.min(n, maxLines);
            while (n > 0 && n * lineH + pad > maxH) {
                n--;
            }
            return n <= 0 ? null : new Size(w, n * lineH + pad);
        };
    }

    // 全部が同時に出る最悪の場面の要求
    // (自分が倒れている表示 DOWNED_SELF は、ほかの表示が要らない場面なので別に調べる)
    static List<Request> busy(Scene s, boolean rightHandedCard) {
        List<Request> q = everything(s, rightHandedCard);
        q.removeIf(r -> r.id() == Id.DOWNED_SELF);
        return q;
    }

    // ふつうに遊んでいる場面: 左上の帯・右上の板・字幕と手がかり・ホットバーの横。上の帯・レベルアップ・中央の一行は短く出るだけなので入れない
    static List<Request> typical(Scene s, boolean rightHandedCard) {
        List<Request> q = busy(s, rightHandedCard);
        q.removeIf(r -> r.id() == Id.BANNER || r.id() == Id.LEVEL_UP || r.id() == Id.CENTER_LINE || r.id() == Id.BOSS_CUE || r.id() == Id.AIM_RING);
        return q;
    }

    static List<Request> everything(Scene s, boolean rightHandedCard) {
        List<Request> q = new ArrayList<>();
        int w = s.w();
        int h = s.h();
        // 左上: 成長 (Lv・バー・マナ) と +経験値の列、技の枠、目的の帯、クエストの追跡
        Request status = Request.of(Id.STATUS_TL, Region.TOP_LEFT, 104, 30);
        Request popups = Request.of(Id.POPUPS_TL, Region.TOP_LEFT, 100, 44);
        Request slotsTl = Request.of(Id.SLOTS_TL, Region.TOP_LEFT, 5 * 22, 24);
        q.add(Request.side(Id.GROWTH_BAR, Side.LEFT, 64, 22 + 40, 18, status, popups));
        q.add(Request.side(Id.SKILL_SLOTS, Side.RIGHT, 5 * 22 - 2, 20, 1, slotsTl));
        q.add(Request.side(Id.QUICK_CARD, rightHandedCard ? Side.LEFT : Side.RIGHT, 29, 31, 0));
        q.add(Request.of(Id.OBJECTIVE, Region.TOP_LEFT, 236, 14));
        q.add(Request.of(Id.QUEST, Region.TOP_LEFT, 156, 62));
        // 右上
        q.add(Request.of(Id.VOW, Region.TOP_RIGHT, 150, 22));
        q.add(Request.of(Id.BACK_GAUGE, Region.TOP_RIGHT, 150, 30));
        q.add(Request.of(Id.BACK_GOAL, Region.TOP_RIGHT, 150, 62));
        q.add(Request.of(Id.PARTY_LIST, Region.TOP_RIGHT, 88, 3 * 19 + 10).minTop(h / 3));
        // 下中央
        q.add(Request.of(Id.DOWNED_SELF, Region.BOTTOM_CENTER, 120, 42));
        q.add(Request.of(Id.HINTS, Region.BOTTOM_CENTER, wrapped(300, 3, 13, 0, 3)));
        // 上中央
        q.add(Request.of(Id.BOSS_CUE, Region.TOP_CENTER, wrapped(220, 3, 11, 4, 3)));
        q.add(Request.of(Id.BANNER, Region.TOP_CENTER, wrapped(Math.max(120, w / 2), 4, 14, 8, 4)).minTop((int) (h * 0.16f)));
        q.add(Request.of(Id.LEVEL_UP, Region.TOP_CENTER, 120, 32).minTop((int) (h * 0.27f)));
        q.add(Request.of(Id.CENTER_LINE, Region.TOP_CENTER, wrapped(Math.max(180, w / 2), 2, 11, 10, 2)).minTop((int) (h * 0.36f)));
        // 字幕
        q.add(Request.of(Id.CAPTION, Region.CAPTION, wrapped(Math.min(w - 40, Math.max(160, (int) (w * 0.6f))), 4, 11, 10, 4)).minH(21));
        // 中央: 起こす輪 (半径 14 と一行)
        q.add(Request.center(Id.AIM_RING, -60, -16, 120, 50));
        return q;
    }

    // ------------------------------------------------------------ 検査
    static void run(List<String> f, StringBuilder stats) {
        Random rnd = new Random(20261019L);
        int cases = 0;
        int[] effects = {0, 3, 6};
        for (int[] win : WINDOWS) {
            int max = HudLayout.maxScale(win[0], win[1]);
            for (int scale = 1; scale <= Math.min(4, max); scale++) {
                int gw = HudLayout.guiSize(win[0], scale);
                int gh = HudLayout.guiSize(win[1], scale);
                // 窓ごとの落ちた部品の表 (最悪の場面)
                Scene ws = scene(gw, gh, 1, 3, 1, true, true, false, 3, 0, 0);
                Result worst = HudLayout.compute(ws, busy(ws, true));
                stats.append(String.format("%dx%d @%d (gui %dx%d): dropped %s%n", win[0], win[1], scale, gw, gh, worst.dropped));
                for (int boss = 0; boss <= 3; boss += 1) {
                    for (int eff : effects) {
                        for (int mask = 0; mask < 32; mask++) {
                            boolean offhand = (mask & 1) != 0;
                            boolean right = (mask & 2) == 0;
                            boolean atk = (mask & 4) != 0;
                            int rows = (mask & 8) != 0 ? 3 : 1;
                            int toast = (mask & 16) != 0 ? 32 * 3 : 0;
                            boolean chat = rnd.nextInt(3) == 0;
                            Scene s = scene(gw, gh, boss, eff, eff == 0 ? 0 : eff > 3 ? 2 : 1, right, offhand, atk, rows, toast, chat ? 1 : 0);
                            List<Request> all = everything(s, right);
                            check(f, s, all, "all");
                            // 乱数の組み合わせ (部品のありなし)
                            for (int k = 0; k < 4; k++) {
                                List<Request> some = new ArrayList<>();
                                for (Request r : all) {
                                    if (rnd.nextInt(100) < 55) {
                                        some.add(r);
                                    }
                                }
                                check(f, s, some, "subset");
                            }
                            cases += 5;
                        }
                    }
                }
                // ふつうの場面 (ボスバー 1・効き目 3): GUI 幅 427・高さ 240 以上なら、手がかり・字幕・目的の帯・ホットバーの横が出る
                if (gw >= 427 && gh >= 240) {
                    Scene s = scene(gw, gh, 1, 3, 1, true, true, false, 1, 0, 0);
                    Result r = HudLayout.compute(s, typical(s, true));
                    // 高さ 270 以上: 手がかりも字幕も出る。240〜269 (GUI 倍率が大きい窓): 照準の下に二つは入らないので、どちらかは出る
                    boolean tall = gh >= 270;
                    List<Id> need = new ArrayList<>(List.of(Id.OBJECTIVE, Id.GROWTH_BAR, Id.SKILL_SLOTS, Id.QUICK_CARD));
                    if (tall) {
                        need.addAll(List.of(Id.HINTS, Id.CAPTION, Id.PARTY_LIST));
                    } else if (!r.has(Id.HINTS) && !r.has(Id.CAPTION)) {
                        f.add(String.format("%dx%d@%d gui %dx%d: neither HINTS nor CAPTION in the typical scene (%s)", win[0], win[1], scale, gw, gh, r.dropped));
                    }
                    for (Id n : need) {
                        if (!r.has(n) && !r.has(fallbackOf(n))) {
                            f.add(String.format("%dx%d@%d gui %dx%d: %s was dropped in the typical scene (%s)", win[0], win[1], scale, gw, gh, n, r.dropped));
                        }
                    }
                }
                // 広い画面 (GUI 幅 640・高さ 480 以上) なら、全部が同時に出る場面でも、照準にかかる中央の一行のほかは一つも落ちない
                if (gw >= 640 && gh >= 480) {
                    Scene s = scene(gw, gh, 1, 3, 1, true, true, false, 1, 0, 0);
                    Result r = HudLayout.compute(s, busy(s, true));
                    List<Id> drop = new ArrayList<>(r.dropped);
                    drop.remove(Id.CENTER_LINE);
                    if (!drop.isEmpty()) {
                        f.add(String.format("%dx%d@%d gui %dx%d: dropped %s on a wide screen", win[0], win[1], scale, gw, gh, drop));
                    }
                }
            }
        }
        // 倍率の計算そのもの
        if (HudLayout.guiSize(1920, 3) != 640 || HudLayout.guiSize(1080, 4) != 270 || HudLayout.maxScale(1920, 1080) != 4 || HudLayout.maxScale(854, 480) != 2) {
            f.add("gui size / max scale math is wrong");
        }
        stats.append("cases: ").append(cases).append('\n');
    }

    static Id fallbackOf(Id id) {
        return switch (id) {
            case GROWTH_BAR -> Id.STATUS_TL;
            case SKILL_SLOTS -> Id.SLOTS_TL;
            default -> id;
        };
    }

    static Scene scene(int w, int h, int bossBars, int effectCols, int effectRows, boolean rightHanded, boolean offhand, boolean attack, int healthRows,
            int toastH, int chat) {
        int bossBottom = bossBars <= 0 ? 0 : 12 + 19 * bossBars;
        return new Scene(w, h, rightHanded, offhand, attack, healthRows, effectCols, effectRows, bossBottom, toastH, chat > 0 ? 320 : 0, chat > 0 ? 70 : 0);
    }

    static void check(List<String> f, Scene s, List<Request> reqs, String what) {
        Result r = HudLayout.compute(s, reqs);
        String where = String.format("gui %dx%d boss %d eff %d/%d rh %s off %s atk %s rows %d toast %d chat %s [%s]", s.w(), s.h(), s.bossBottom(),
                s.effectCols(), s.effectRows(), s.rightHanded(), s.offhand(), s.attackOnHotbar(), s.healthRows(), s.toastH(), s.chatW() > 0, what);
        Rect screen = new Rect(0, 0, s.w(), s.h());
        List<Map.Entry<Id, Rect>> mods = new ArrayList<>(r.placed.entrySet());
        for (Map.Entry<Id, Rect> e : mods) {
            Rect a = e.getValue();
            if (a.x() < 0 || a.y() < 0 || a.right() > s.w() || a.bottom() > s.h() || a.w() <= 0 || a.h() <= 0) {
                f.add(where + ": " + e.getKey() + " is outside the screen " + a);
            }
        }
        for (int i = 0; i < mods.size(); i++) {
            for (int j = i + 1; j < mods.size(); j++) {
                if (mods.get(i).getValue().intersects(mods.get(j).getValue())) {
                    f.add(where + ": " + mods.get(i).getKey() + " " + mods.get(i).getValue() + " overlaps " + mods.get(j).getKey() + " " + mods.get(j).getValue());
                }
            }
            for (Map.Entry<Id, Rect> v : r.vanilla.entrySet()) {
                boolean bottomCenter = mods.get(i).getKey() == Id.HINTS || mods.get(i).getKey() == Id.CAPTION || mods.get(i).getKey() == Id.DOWNED_SELF;
                if (v.getKey() == Id.V_CHAT && !bottomCenter) {
                    continue;
                }
                if (mods.get(i).getKey() == Id.AIM_RING && v.getKey() == Id.V_CROSSHAIR) {
                    continue;
                }
                if (mods.get(i).getValue().intersects(v.getValue())) {
                    f.add(where + ": " + mods.get(i).getKey() + " " + mods.get(i).getValue() + " overlaps vanilla " + v.getKey() + " " + v.getValue());
                }
            }
        }
        // 画面の外の矢印: 板の上に落ちない (十分な向きの数で調べる)
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            int[] p = HudLayout.edgePoint(r, Math.cos(rad), Math.sin(rad));
            for (Rect o : r.solids(false)) {
                if (o.contains(p[0], p[1])) {
                    f.add(where + ": marker arrow at angle " + a + " lands on " + o);
                    break;
                }
            }
        }
        // 全要求のうち置けたか落ちたかのどちらか (消えた部品がない)
        EnumSet<Id> asked = EnumSet.noneOf(Id.class);
        for (Request q : reqs) {
            asked.add(q.id());
            asked.addAll(q.fallback().stream().map(Request::id).toList());
        }
        EnumMap<Id, Boolean> seen = new EnumMap<>(Id.class);
        r.placed.keySet().forEach(k -> seen.put(k, true));
        r.dropped.forEach(k -> seen.put(k, true));
        for (Request q : reqs) {
            boolean ok = seen.containsKey(q.id()) || q.fallback().stream().anyMatch(fb -> seen.containsKey(fb.id()));
            if (!ok) {
                f.add(where + ": " + q.id() + " is neither placed nor dropped");
            }
        }
        if (screen.w() <= 0) {
            f.add(where + ": empty screen");
        }
    }
}
