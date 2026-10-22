package jp.revati.skyabyss.client.notice;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 知らせの記録 (N)。流れた語り・獲得・場所の名前を、時刻つきで新しい順に読み返す窓。クエストログと同じ暗い石板の色
// 2026-10-22 の部屋の見直し: 窓は画面に合わせて広げ、字の行は間をあけ、送りは目盛りつき (ホイール・つまみ・キー)
public class NoticeLogScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_W = 380;
    private static final int MAX_H = 260;
    private static final int ROW_H = 12;
    private static final int TIME_W = 40;
    private static final int PANEL = 0xFA101318;
    private static final int EDGE = 0xFF2E5552;
    private static final int EDGE_IN = 0xFF17302F;
    private static final int TEAL = 0xFF7FD6D0;
    private static final int SOFT = 0xFF8C8CA0;
    private final ScrollPanel list = new ScrollPanel(ROW_H * 3);
    private int left;
    private int top;
    private int w;
    private int h;

    private record Row(String time, int color, FormattedCharSequence text) {
    }

    public NoticeLogScreen() {
        super(Component.translatable("gui.skyabyss.notice.log"));
    }

    private static int colorOf(Notice.Kind kind) {
        return switch (kind) {
            case NARRATE -> 0xFF7FD6D0;
            case HINT -> 0xFFB8B8C8;
            case LEVEL_UP, GAIN, PLACE, ALERT -> 0xFFF2C14E;
            default -> 0xFF9FE8FF;
        };
    }

    private List<Row> rows(Font font, int width) {
        List<Row> out = new ArrayList<>();
        for (NoticeLog.Entry e : NoticeLog.entries()) {
            List<FormattedCharSequence> lines = font.split(e.text(), width);
            for (int i = 0; i < lines.size(); i++) {
                out.add(new Row(i == 0 ? e.time() : "", colorOf(e.kind()), lines.get(i)));
            }
        }
        return out;
    }

    private void layout() {
        w = Math.min(MAX_W, width - 24);
        h = Math.min(MAX_H, height - 24);
        left = (width - w) / 2;
        top = (height - h) / 2;
        list.setViewport(left + 10, top + 27, w - 20, h - 27 - 20);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return list.mouseScrolled(mx, my, dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return list.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return list.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return list.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return list.keyPressed(key) || super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // 背景 (ぼかさない) と枠と中身を描く。クエストログと同じ流儀
        layout();
        g.fill(0, 0, width, height, 0x80000000);
        g.fill(left, top, left + w, top + h, PANEL);
        g.renderOutline(left, top, w, h, EDGE);
        g.drawString(font, title, left + 10, top + 8, TEAL, false);
        g.fill(left + 8, top + 20, left + w - 8, top + 21, EDGE);
        List<Row> rows = rows(font, list.width() - TIME_W);
        list.setContentHeight(rows.size() * ROW_H);
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.notice.log_empty"), left + 10, top + 30, SOFT, false);
        }
        list.begin(g);
        for (int i = 0; i < rows.size(); i++) {
            int y = list.top() + i * ROW_H;
            if (y + ROW_H < list.y() || y > list.y() + list.height()) {
                continue;
            }
            Row r = rows.get(i);
            g.drawString(font, r.time(), left + 10, y + 2, SOFT, false);
            g.drawString(font, r.text(), left + 10 + TIME_W, y + 2, r.color(), false);
        }
        list.end(g);
        list.drawBar(g, EDGE_IN, 0x887FD6D0);
        g.drawString(font, Component.translatable("gui.skyabyss.notice.log_hint"), left + 10, top + h - 11, SOFT, false);
    }

    @Override
    public int[] textBounds() {
        layout();
        return new int[] {left, top, w, h};
    }
}
