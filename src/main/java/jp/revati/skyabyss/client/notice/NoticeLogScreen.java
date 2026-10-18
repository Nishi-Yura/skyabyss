package jp.revati.skyabyss.client.notice;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// 知らせの記録 (N)。流れた語り・獲得・場所の名前を、時刻つきで新しい順に読み返す窓。クエストログと同じ暗い石板の色
public class NoticeLogScreen extends Screen {
    private static final int W = 300;
    private static final int H = 200;
    private static final int PANEL = 0xFA101318;
    private static final int EDGE = 0xFF2E5552;
    private static final int TEAL = 0xFF7FD6D0;
    private static final int SOFT = 0xFF8C8CA0;
    private int scroll;

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

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy) * 3);
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // 背景 (ぼかさない) と枠と中身を描く。クエストログと同じ流儀
        g.fill(0, 0, width, height, 0x80000000);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left, top, left + W, top + H, PANEL);
        g.renderOutline(left, top, W, H, EDGE);
        g.drawString(font, title, left + 10, top + 8, TEAL, false);
        g.fill(left + 8, top + 20, left + W - 8, top + 21, EDGE);
        List<Row> rows = rows(font, W - 64);
        int visible = (H - 27 - 14) / 10;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.notice.log_empty"), left + 10, top + 30, SOFT, false);
        }
        int y = top + 27;
        for (int i = scroll; i < Math.min(rows.size(), scroll + visible); i++) {
            Row r = rows.get(i);
            g.drawString(font, r.time(), left + 10, y, SOFT, false);
            g.drawString(font, r.text(), left + 46, y, r.color(), false);
            y += 10;
        }
        g.drawString(font, Component.translatable("gui.skyabyss.notice.log_hint"), left + 10, top + H - 11, SOFT, false);
    }
}
