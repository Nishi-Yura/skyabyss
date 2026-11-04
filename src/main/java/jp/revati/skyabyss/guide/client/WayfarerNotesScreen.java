package jp.revati.skyabyss.guide.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.guide.WayfarerNotes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 旅の覚書の画面 (簡素。手帳の中身は文のキー wayfarer_notes.ch<n>.line.<i>。絵の入る頁は、中身を足す担当が作り足す)
// 紙の手帳のまま、大きさは黄金比の中の板 (288x178。client/ui/Golden。docs/design/56)。行 13、段落のあいだ 5、長い中身は目盛りつきで送る
public class WayfarerNotesScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int W = jp.revati.skyabyss.client.ui.Golden.M_W;
    private static final int H = jp.revati.skyabyss.client.ui.Golden.M_H;
    private static final int LINE_H = jp.revati.skyabyss.client.ui.Golden.ROW;
    private static final int GAP = jp.revati.skyabyss.client.ui.Golden.S5;
    private static final int TITLE_H = jp.revati.skyabyss.client.ui.Golden.S8 + jp.revati.skyabyss.client.ui.Golden.HEAD + jp.revati.skyabyss.client.ui.Golden.GAP;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final List<Component> lines = new ArrayList<>();
    private final ScrollPanel list = new ScrollPanel(LINE_H * 3);
    private int left;
    private int top;
    private int w;
    private int h;

    public WayfarerNotesScreen(int chapter) {
        super(Component.translatable("item.skyabyss.wayfarer_notes.named", WayfarerNotes.chapterName(chapter)));
        for (int i = 1; Language.getInstance().has(WayfarerNotes.lineKey(chapter, i)); i++) {
            lines.add(Component.translatable(WayfarerNotes.lineKey(chapter, i)));
        }
        if (lines.isEmpty()) {
            lines.add(Component.translatable("wayfarer_notes.empty"));
        }
    }

    private void layout() {
        fit.fit(width, height, W, H, 3f);
        w = W;
        h = H;
        left = 0;
        top = 0;
        list.setViewport(left + jp.revati.skyabyss.client.ui.Golden.S13, top + TITLE_H, w - 2 * jp.revati.skyabyss.client.ui.Golden.S13 + 4, h - TITLE_H - jp.revati.skyabyss.client.ui.Golden.S13);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        layout();
        fit.begin(g);
        g.fill(left, top, left + w, top + h, 0xFFD8C7A0);
        g.fill(left + 2, top + 2, left + w - 2, top + h - 2, 0xFFEADFBE);
        g.drawCenteredString(font, title, w / 2, top + jp.revati.skyabyss.client.ui.Golden.textY(jp.revati.skyabyss.client.ui.Golden.S8, jp.revati.skyabyss.client.ui.Golden.HEAD), 0xFF4A3A22);
        g.fill(left + jp.revati.skyabyss.client.ui.Golden.S13, top + TITLE_H - 3, left + w - jp.revati.skyabyss.client.ui.Golden.S13, top + TITLE_H - 2, 0x608A6E3E);
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        int content = 0;
        for (Component line : lines) {
            List<FormattedCharSequence> wl = jp.revati.skyabyss.client.ui.JpWrap.split(font, line, list.width());
            wrapped.add(wl);
            content += wl.size() * LINE_H + GAP;
        }
        list.setContentHeight(content);
        list.begin(g);
        int y = list.top();
        for (List<FormattedCharSequence> wl : wrapped) {
            for (FormattedCharSequence seq : wl) {
                if (y + LINE_H >= list.y() && y <= list.y() + list.height()) {
                    g.drawString(font, seq, list.x(), jp.revati.skyabyss.client.ui.Golden.textY(y, LINE_H), 0xFF3A2E1A, false);
                }
                y += LINE_H;
            }
            y += GAP;
        }
        list.end(g);
        list.drawBar(g, 0xFFD8C7A0, 0xAA8A6E3E);
        fit.end(g);
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, w, h);
    }
}
