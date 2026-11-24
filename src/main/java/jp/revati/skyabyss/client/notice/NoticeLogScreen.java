package jp.revati.skyabyss.client.notice;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.LedgerStage;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import jp.revati.skyabyss.client.ui.Paper;

// 知らせの記録 (N)。流れた語り・獲得・場所の名前を、時刻つきで新しい順に読み返す窓。
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1): 記録は左の黒い紙にじかに (時刻の列 34・行 13)、
// 右の生成りの帯に題と数と動かし方
public class NoticeLogScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Box BODY = new Golden.Box(LedgerStage.PAGE_X, LedgerStage.PAGE_Y, LedgerStage.PAGE_W, 184);
    private static final int ROW_H = Golden.ROW;
    private static final int TIME_W = Golden.S34;
    private static final int EDGE_IN = 0xFF1C1A26;
    private static final int SOFT = 0xFF000000 | Paper.ON_BLACK_SOFT;
    private final ScrollPanel list = new ScrollPanel(ROW_H * 3);
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private record Row(String time, int color, FormattedCharSequence text) {
    }

    public NoticeLogScreen() {
        super(Component.translatable("gui.skyabyss.notice.log"));
    }

    private static int colorOf(Notice.Kind kind) {
        return switch (kind) {
            case NARRATE -> 0xFFFFF6EA;
            case HINT -> 0xFFB8B8C8;
            case LEVEL_UP, GAIN, PLACE, ALERT -> 0xFFF2C47C;
            default -> 0xFFFFF6EA;
        };
    }

    private List<Row> rows(Font font, int width) {
        List<Row> out = new ArrayList<>();
        for (NoticeLog.Entry e : NoticeLog.entries()) {
            List<FormattedCharSequence> lines = jp.revati.skyabyss.client.ui.JpWrap.split(font, e.text(), width);
            for (int i = 0; i < lines.size(); i++) {
                out.add(new Row(i == 0 ? e.time() : "", colorOf(e.kind()), lines.get(i)));
            }
        }
        return out;
    }

    private void layout() {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        list.setViewport(BODY.x(), BODY.y(), BODY.w(), BODY.h());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return list.mouseScrolled(fit.x(mx), fit.y(my), dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return list.mouseClicked(fit.x(mx), fit.y(my), button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return list.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale()) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return list.mouseReleased(fit.x(mx), fit.y(my), button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return list.keyPressed(key) || super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // 背景 (ぼかさない) と板と中身を描く。板は窓に合わせた変換 (画素ちょうど) の中で
        layout();
        Paper.dim(g, width, height);
        fit.begin(g);
        LedgerStage.backdrop(g, fit, width, height, LedgerStage.HEAD_NONE, false);
        LedgerStage.indexHead(g, font, title);
        List<Row> rows = rows(font, list.width() - TIME_W - ScrollPanel.GUTTER);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(Integer.toString(NoticeLog.entries().size())), LedgerStage.INDEX_X + 2, 32, LedgerStage.INDEX_W - 4,
                Paper.INK_HEAD, false);
        int hy = 48;
        for (FormattedCharSequence l : font.split(Component.translatable("gui.skyabyss.notice.log_hint"), LedgerStage.INDEX_W - 4)) {
            g.drawString(font, l, LedgerStage.INDEX_X + 2, hy, Paper.INK_SOFT, false);
            hy += ROW_H;
        }
        list.setContentHeight(rows.size() * ROW_H);
        if (rows.isEmpty()) {
            Golden.empty(g, font, BODY, Component.translatable("gui.skyabyss.notice.log_empty"));
        }
        list.begin(g);
        for (int i = 0; i < rows.size(); i++) {
            int y = list.top() + i * ROW_H;
            if (y + ROW_H < list.y() || y > list.y() + list.height()) {
                continue;
            }
            Row r = rows.get(i);
            int ty = Golden.textY(y, ROW_H);
            g.drawString(font, r.time(), list.x(), ty, SOFT, false);
            g.drawString(font, r.text(), list.x() + TIME_W, ty, r.color(), false);
        }
        list.end(g);
        list.drawBar(g, EDGE_IN, 0x88C48A44);
        fit.end(g);
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, LedgerStage.W, LedgerStage.H);
    }
}
