package jp.revati.skyabyss.client.quest;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.lore.LoreWrap;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// 追跡中のクエストの、今の目的を画面の左に小さく出す (成長の表示の下)。
// 済んだ目的は出さない (「どれか一つ」の段階は、選べる目的を「または」でつなぐ)。F1・F3 の画面と、追跡していないときは出さない
public final class QuestHud {
    public static final int X = 6;
    static int w() {
        return Math.min(150, ObjectiveStrip.bandWidth(Minecraft.getInstance()));
    }

    static final int TITLE = 0xFF7FD6D0;
    static final int MAIN_TITLE = 0xFFF2C14E;
    static final int TEXT = 0xFFE0DCD0;
    static final int SOFT = 0xFF9A9AAE;
    private static int flash;

    private QuestHud() {
    }

    static void flash() {
        flash = 30;
    }

    static void tick() {
        if (flash > 0) {
            flash--;
        }
    }

    // 札を出すなら (題 + 済んでいない目的の行)。出さないなら null。要求と描画で同じ判断をする
    // 要求 (HudManager) と描画は同じフレームで同じ答えになるので、一フレームに一度だけ組み立てる
    private static int shownFrame = -1;
    private static Shown shownCache;

    @javax.annotation.Nullable
    private static Shown shown(Minecraft mc) {
        int f = jp.revati.skyabyss.client.hud.HudManager.frame();
        if (f == shownFrame) {
            return shownCache;
        }
        shownCache = build(mc);
        shownFrame = f;
        return shownCache;
    }

    @javax.annotation.Nullable
    private static Shown build(Minecraft mc) {
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen instanceof QuestLogScreen) {
            return null;
        }
        if (demo) {
            List<Line> demoLines = new ArrayList<>();
            for (String t : List.of("渡り鳥の羽を 3 枚集める", "見張り台の鐘を鳴らす")) {
                List<String> wrapped = wrapWithCount(t, t.startsWith("渡り") ? "1/3" : "", w() - 12, mc.font);
                for (int k = 0; k < wrapped.size(); k++) {
                    demoLines.add(new Line((k == 0 ? "・" : "") + wrapped.get(k), k == 0 ? 0 : 9, TEXT));
                }
            }
            return new Shown(null, null, Component.literal("見張り台の頼みごと"), false, demoLines);
        }
        QuestLog log = QuestLog.of(mc.player);
        if (log.tracked().isEmpty()) {
            return null;
        }
        QuestDef def = QuestClient.quest(log.tracked());
        QuestLog.Entry entry = log.entry(log.tracked());
        if (def == null || entry == null || entry.done || entry.stage >= def.stages().size()) {
            return null;
        }
        if (def.main() && ObjectiveStrip.showsQuest(def)) {
            return null;
        }
        List<Line> lines = lines(mc.font, def, entry, w() - 12);
        // 目的の行が一つも無い (済んだ・今は出すものが無い) ときは、空の札を出さない
        if (lines.isEmpty()) {
            return null;
        }
        return new Shown(def, entry, Component.translatable(def.titleKey()), def.main(), lines);
    }

    private record Shown(QuestDef def, QuestLog.Entry entry, Component title, boolean main, List<Line> lines) {
    }

    // HUD の要求 (左上の帯。目的の帯の下)
    public static void requests(Minecraft mc, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        Shown s = shown(mc);
        if (s != null) {
            out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.QUEST, jp.revati.skyabyss.hud.HudLayout.Region.TOP_LEFT,
                    w() + 6, s.lines.size() * 10 + 16 + (s.def != null && guideShown(mc, s) ? 10 : 0)));
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Shown s = shown(mc);
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.QUEST);
        if (s == null || r == null) {
            return;
        }
        int top = r.y() + 3;
        drawBox(g, mc, top, s.title, s.main, s.lines, s.def != null && guideShown(mc, s), () -> {
            if (s.def != null) {
                drawGuide(g, mc, s.def, s.entry, top + 12 + s.lines.size() * 10);
            }
        });
    }

    // 札の箱 (題と、済んでいない目的の行)
    private static void drawBox(GuiGraphics g, Minecraft mc, int top, Component title, boolean main, List<Line> lines, boolean withGuide, Runnable guide) {
        Font font = mc.font;
        int WIDTH = w();
        int h = 12 + lines.size() * 10 + 3 + (withGuide ? 10 : 0);
        int bg = flash > 0 ? Mth.clamp(flash * 6, 0, 0x60) : 0;
        g.fill(X - 3, top - 3, X + WIDTH + 3, top + h - 2, (0x50 + bg) << 24 | 0x0A0C10);
        g.fill(X - 3, top - 3, X - 2, top + h - 2, main ? MAIN_TITLE : TITLE);
        String ts = title.getString();
        if (font.width(ts) > WIDTH - 2) {
            ts = font.plainSubstrByWidth(ts, WIDTH - 2 - font.width("…")) + "…";
        }
        g.drawString(font, ts, X + 1, top, main ? MAIN_TITLE : TITLE, true);
        guide.run();
        int y = top + 12;
        for (Line l : lines) {
            g.drawString(font, l.text, X + l.indent, y, l.color, true);
            y += 10;
        }
    }

    // 開発用 (/skyabyss_hud demo): データなしで追跡の札を出す
    public static volatile boolean demo;

    // 札に導きの行を出すか (行き先が分かるとき)
    private static boolean guideShown(Minecraft mc, Shown s) {
        ObjectiveStrip.Resolved r = ObjectiveStrip.resolveQuest(s.def, s.entry);
        return r.inside() || r.pos() != null;
    }

    // 追跡中のクエストの札の最後の行に、目的までの矢印と距離 (帯と同じ。設定 questArrow が OFF なら方位の文字)。背中の中なら「背中の中」
    private static void drawGuide(GuiGraphics g, Minecraft mc, QuestDef def, QuestLog.Entry entry, int top) {
        ObjectiveStrip.Resolved r = ObjectiveStrip.resolveQuest(def, entry);
        Font font = mc.font;
        if (r.inside()) {
            String s = Component.translatable("gui.skyabyss.guide.inside").getString();
            g.drawString(font, s, X + 1, top, 0xFF9A9AAE, true);
            return;
        }
        if (r.pos() == null) {
            return;
        }
        net.minecraft.core.BlockPos t = r.pos();
        double d = Math.sqrt(mc.player.position().distanceToSqr(t.getX() + 0.5, mc.player.getY(), t.getZ() + 0.5));
        boolean arrow = jp.revati.skyabyss.Config.QUEST_ARROW.get();
        String far = jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(d, arrow ? "" : ObjectiveStrip.direction(mc.player.position(), t));
        if (far.isEmpty()) {
            return;
        }
        int fx = X + 1;
        if (arrow) {
            float deg = jp.revati.skyabyss.guide.nav.NavMath.arrowDegrees(mc.player.getX(), mc.player.getZ(), t.getX() + 0.5, t.getZ() + 0.5, mc.player.getYRot());
            jp.revati.skyabyss.client.guide.GuideArrow.draw(g, fx + jp.revati.skyabyss.client.guide.GuideArrow.SIZE / 2, top + 4, deg, 1f);
            fx += jp.revati.skyabyss.client.guide.GuideArrow.SIZE + 3;
            int v = jp.revati.skyabyss.guide.nav.NavMath.vertical(t.getY() + 0.5 - mc.player.getY());
            if (v != 0) {
                jp.revati.skyabyss.client.guide.GuideArrow.drawVertical(g, fx + 2, top + 4, v > 0, 1f);
                fx += 9;
            }
        }
        g.drawString(font, far, fx, top, 0xFF7FD6D0, true);
    }

    record Line(String text, int indent, int color) {
    }

    // 目的の文を折り返し、数 (「1/3」) は途中で切らずに最後の行の後ろへ付ける。入らなければ次の行へ
    // (ゲーム画面で「（1/」「3）」と切れていたため)
    static List<String> wrapWithCount(String text, String count, int width, Font font) {
        List<String> out = new ArrayList<>(LoreWrap.wrap(text, width, font::width));
        if (count.isEmpty()) {
            return out;
        }
        String tail = "（" + count + "）";
        String last = out.isEmpty() ? "" : out.get(out.size() - 1);
        if (!out.isEmpty() && font.width(last + tail) <= width) {
            out.set(out.size() - 1, last + tail);
        } else {
            out.add(tail);
        }
        return out;
    }

    // 目的の文。序盤の帯が「歌のあと」(wait_whale) の間は、その段の「待つ」版の文 (<鍵>.wait があれば) を出す (クエストログと帯の言うことを合わせる)
    // 近く (クライアントが追っている範囲) の岩鯨が、鎖を地上へ垂らしているか
    // (生き物は tick ごとにしか変わらないので、同じ tick のあいだは前の答え)
    private static long chainTick = Long.MIN_VALUE;
    private static Object chainLevel;
    private static boolean chainCache;

    static boolean chainDown() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        long t = mc.level.getGameTime();
        if (t == chainTick && chainLevel == mc.level) {
            return chainCache;
        }
        boolean down = false;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof jp.revati.skyabyss.titan.StoneWhaleEntity whale && whale.isChainReaching()) {
                down = true;
                break;
            }
        }
        chainTick = t;
        chainLevel = mc.level;
        chainCache = down;
        return down;
    }

    public static String objectiveText(QuestDef def, QuestDef.Stage stage, QuestDef.Objective ob) {
        String key = def.objectiveKey(stage, ob);
        jp.revati.skyabyss.client.opening.OpeningClient.Objective o = jp.revati.skyabyss.client.opening.OpeningClient.objective();
        // 序盤の帯が出ていない人 (以前から遊んでいた人など) は、歌の頁が記されたかで決める
        boolean waiting = o != null ? o.id().equals("wait_whale") : jp.revati.skyabyss.lore.client.LoreClient.has("prologue/sky_song");
        // 岩鯨の「鎖を上る」段: 待ち版の文 (「空の岩鯨を、追いかけよう」) は、鎖が降りるまで (見たあと、岩鯨は人のもとへ降りてくる。story.ch1.Ch1Descent)。
        // 歌の頁を持つ人は上の式がいつも真になり、鎖が降りても「待ち」のままだった (first_hour.txt の通しで発見)。この段だけは鎖で決める
        if (def.id().equals("main_song") && stage.id().equals("board")) {
            waiting = !chainDown();
        }
        net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
        if (waiting && lang.has(key + ".wait")) {
            key = key + ".wait";
        }
        return Component.translatable(key).getString();
    }

    // 帯に出す、今の目的の一行 (折り返さず全文。数は「（1/3）」を後ろに付ける。長ければ帯が切る)
    public static String firstObjective(Font font, QuestDef def, QuestLog.Entry entry, int width) {
        for (Line l : lines(font, def, entry, 100000)) {
            if (l.text.startsWith("・")) {
                String s = l.text.substring(1);
                return s;
            }
        }
        // 全部済んでいて次へ進む前の一瞬: 題名だけにせず、この段の最初のやることを出す
        QuestDef.Stage stage = def.stages().get(entry.stage);
        if (!stage.objectives().isEmpty()) {
            return objectiveText(def, stage, stage.objectives().get(0));
        }
        return Component.translatable(def.titleKey()).getString();
    }

    static List<Line> lines(Font font, QuestDef def, QuestLog.Entry entry, int width) {
        QuestDef.Stage stage = def.stages().get(entry.stage);
        List<Line> out = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < stage.objectives().size(); i++) {
            QuestDef.Objective ob = stage.objectives().get(i);
            int have = entry.progress(i);
            if (have >= ob.count() && !stage.any()) {
                continue;
            }
            if (ob.type().equals("turn_in") && Minecraft.getInstance().player != null) {
                have = Math.min(jp.revati.skyabyss.quest.QuestItems.count(Minecraft.getInstance().player, ob.targets().get(0), ob.token()), ob.count());
            }
            if (stage.any() && !first) {
                out.add(new Line(Component.translatable("gui.skyabyss.quest.or").getString(), 8, SOFT));
            }
            first = false;
            String text = objectiveText(def, stage, ob);
            List<String> wrapped = wrapWithCount(text, ob.count() > 1 ? Math.min(have, ob.count()) + "/" + ob.count() : "", width, font);
            for (int k = 0; k < wrapped.size(); k++) {
                out.add(new Line((k == 0 ? "・" : "") + wrapped.get(k), k == 0 ? 0 : 9, TEXT));
            }
        }
        return out;
    }
}
