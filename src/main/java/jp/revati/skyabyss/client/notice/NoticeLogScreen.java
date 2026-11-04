package jp.revati.skyabyss.client.notice;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 知らせの記録 (N)。流れた語り・獲得・場所の名前を、時刻つきで新しい順に読み返す窓。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 中身は一覧一つ (時刻の列 34・行 13)、下の段に動かし方
public class NoticeLogScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box BODY = LAYOUT.body();
    private static final int ROW_H = Golden.ROW;
    private static final int TIME_W = Golden.S34;
    private static final int EDGE_IN = 0xFF17302F;
    private static final int TEAL = 0xFF000000 | Golden.TEAL;
    private static final int SOFT = 0xFF000000 | Golden.SOFT;
    private final ScrollPanel list = new ScrollPanel(ROW_H * 3);
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

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
            List<FormattedCharSequence> lines = jp.revati.skyabyss.client.ui.JpWrap.split(font, e.text(), width);
            for (int i = 0; i < lines.size(); i++) {
                out.add(new Row(i == 0 ? e.time() : "", colorOf(e.kind()), lines.get(i)));
            }
        }
        return out;
    }

    private void layout() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        list.setViewport(BODY.x() + Golden.S5, BODY.y() + 2, BODY.w() - Golden.S5 - 2, BODY.h() - 4);
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
        g.fill(0, 0, width, height, 0x80000000);
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.recess(g, BODY);
        g.drawString(font, title, LAYOUT.head().x() + Golden.S5, Golden.textY(LAYOUT.head().y(), LAYOUT.head().h()), TEAL, false);
        List<Row> rows = rows(font, list.width() - TIME_W);
        list.setContentHeight(rows.size() * ROW_H);
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.notice.log_empty"), BODY.x() + Golden.S8, Golden.textY(BODY.y() + 2, ROW_H), SOFT, false);
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
        list.drawBar(g, EDGE_IN, 0x887FD6D0);
        Golden.Box foot = LAYOUT.foot();
        g.drawString(font, Component.translatable("gui.skyabyss.notice.log_hint"), foot.x() + Golden.S5, Golden.textY(foot.y(), foot.h()), 0xFF000000 | Golden.DIM, false);
        fit.end(g);
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, Golden.L_W, Golden.L_H);
    }
}
