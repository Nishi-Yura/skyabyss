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
    // 成長の表示の「+12」の列 (バーの下 y=25 から、マナ・技の段の分だけ下げて、最大 4 行) より下に置く。
    // 列の長さで上下に動かないよう、いつも 4 行分あける
    static final int POPUP_TOP = 25;
    static final int POPUP_ROWS = 4;

    public static int y() {
        // 成長の表示を経験値バーの左に置いたときは、左上の一番上から (左上に寄せた技の枠があればその下)
        Minecraft mc = Minecraft.getInstance();
        if (jp.revati.skyabyss.rpg.client.GrowthHud.atXpBar(mc, mc.getWindow().getGuiScaledWidth())) {
            return 6 + jp.revati.skyabyss.rpg.client.skill.SkillHud.topLeftRowsWhenGrowthAtBottom();
        }
        return POPUP_TOP + jp.revati.skyabyss.rpg.client.skill.SkillHud.rowsBelowXpBar() + POPUP_ROWS * 10 + 6;
    }
    static final int WIDTH = 150;
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

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen instanceof QuestLogScreen) {
            return;
        }
        QuestLog log = QuestLog.of(mc.player);
        if (log.tracked().isEmpty()) {
            return;
        }
        QuestDef def = QuestClient.quest(log.tracked());
        QuestLog.Entry entry = log.entry(log.tracked());
        if (def == null || entry == null || entry.done || entry.stage >= def.stages().size()) {
            return;
        }
        if (def.main() && ObjectiveStrip.active()) {
            return;
        }
        Font font = mc.font;
        List<Line> lines = lines(font, def, entry, WIDTH - 12);
        int h = 12 + lines.size() * 10 + 3;
        int top = y() + ObjectiveStrip.rows();
        int bg = flash > 0 ? Mth.clamp(flash * 6, 0, 0x60) : 0;
        g.fill(X - 3, top - 3, X + WIDTH + 3, top + h - 2, (0x50 + bg) << 24 | 0x0A0C10);
        g.fill(X - 3, top - 3, X - 2, top + h - 2, def.main() ? MAIN_TITLE : TITLE);
        g.drawString(font, Component.translatable(def.titleKey()), X + 1, top, def.main() ? MAIN_TITLE : TITLE, true);
        int y = top + 12;
        for (Line l : lines) {
            g.drawString(font, l.text, X + l.indent, y, l.color, true);
            y += 10;
        }
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
    static boolean chainDown() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof jp.revati.skyabyss.titan.StoneWhaleEntity whale && whale.isChainReaching()) {
                return true;
            }
        }
        return false;
    }

    public static String objectiveText(QuestDef def, QuestDef.Stage stage, QuestDef.Objective ob) {
        String key = def.objectiveKey(stage, ob);
        jp.revati.skyabyss.client.opening.OpeningClient.Objective o = jp.revati.skyabyss.client.opening.OpeningClient.objective();
        // 序盤の帯が出ていない人 (以前から遊んでいた人など) は、歌の頁が記されたかで決める
        boolean waiting = o != null ? o.id().equals("wait_whale") : jp.revati.skyabyss.lore.client.LoreClient.has("prologue/sky_song");
        // 岩鯨の「鎖を上る」段: 待ち版の文は、鎖が降りるまで (備えを進める・探索する。story.ch1.Ch1Descent の条件が整うまで降りてこない)。
        // 歌の頁を持つ人は上の式がいつも真になり、鎖が降りても「待ち」のままだった (first_hour.txt の通しで発見)。この段だけは鎖で決める
        if (def.id().equals("main_song") && stage.id().equals("board")) {
            waiting = !chainDown();
        }
        net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
        if (waiting && def.id().equals("main_song") && stage.id().equals("board")) {
            // 鎖の待ちの帯 (first_hour_log B-4): 備えの頼みの本数と三灯を一行で言う。備えの頼みが 2 本済んでいれば三灯、まだなら本数。
            // 備えの頼みの記録が無い (定義が無い・旧データ) ときは従来の一行
            int done = prepDone();
            if (done >= 0) {
                String variant = jp.revati.skyabyss.quest.ObjectiveRules.waitVariant(done);
                if (lang.has(key + variant)) {
                    return Component.translatable(key + variant, Math.min(done, jp.revati.skyabyss.story.ch1.Ch1Descent.NEEDED_PREP)).getString();
                }
            }
        }
        if (waiting && lang.has(key + ".wait")) {
            key = key + ".wait";
        }
        return Component.translatable(key).getString();
    }

    // 一章の備えの頼みを終えた本数 (クエストの記録に 3 本とも無ければ -1)
    static int prepDone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        QuestLog log = QuestLog.of(mc.player);
        int done = 0;
        int known = 0;
        for (String kind : jp.revati.skyabyss.guide.PrepQuests.KINDS) {
            QuestLog.Entry e = log.entry(jp.revati.skyabyss.guide.PrepQuests.id(1, kind));
            if (e != null) {
                known++;
                if (e.done) {
                    done++;
                }
            }
        }
        return known == 0 ? -1 : done;
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
