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
    static final int X = 6;
    static final int Y = 70;
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
        Font font = mc.font;
        List<Line> lines = lines(font, def, entry);
        int h = 12 + lines.size() * 10 + 3;
        int bg = flash > 0 ? Mth.clamp(flash * 6, 0, 0x60) : 0;
        g.fill(X - 3, Y - 3, X + WIDTH + 3, Y + h - 2, (0x50 + bg) << 24 | 0x0A0C10);
        g.fill(X - 3, Y - 3, X - 2, Y + h - 2, def.main() ? MAIN_TITLE : TITLE);
        g.drawString(font, Component.translatable(def.titleKey()), X + 1, Y, def.main() ? MAIN_TITLE : TITLE, true);
        int y = Y + 12;
        for (Line l : lines) {
            g.drawString(font, l.text, X + l.indent, y, l.color, true);
            y += 10;
        }
    }

    record Line(String text, int indent, int color) {
    }

    static List<Line> lines(Font font, QuestDef def, QuestLog.Entry entry) {
        QuestDef.Stage stage = def.stages().get(entry.stage);
        List<Line> out = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < stage.objectives().size(); i++) {
            QuestDef.Objective ob = stage.objectives().get(i);
            int have = entry.progress(i);
            if (have >= ob.count() && !stage.any()) {
                continue;
            }
            if (stage.any() && !first) {
                out.add(new Line(Component.translatable("gui.skyabyss.quest.or").getString(), 8, SOFT));
            }
            first = false;
            String text = Component.translatable(def.objectiveKey(stage, ob)).getString();
            if (ob.count() > 1) {
                text += "（" + Math.min(have, ob.count()) + "/" + ob.count() + "）";
            }
            List<String> wrapped = LoreWrap.wrap(text, WIDTH - 12, font::width);
            for (int k = 0; k < wrapped.size(); k++) {
                out.add(new Line((k == 0 ? "・" : "") + wrapped.get(k), k == 0 ? 0 : 9, TEXT));
            }
        }
        return out;
    }
}
