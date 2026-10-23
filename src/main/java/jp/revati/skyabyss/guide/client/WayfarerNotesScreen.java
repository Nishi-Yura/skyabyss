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
// 2026-10-22 の部屋の見直し: 手帳は画面に合わせて広げ、長い中身は目盛りつきで送る (以前は下が画面の外へ出た)
public class WayfarerNotesScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_W = 300;
    private static final int MAX_H = 240;
    private static final int LINE_H = 12;
    private static final int GAP = 6;
    private static final int TITLE_H = 26;
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
        w = Math.min(MAX_W, width - 24);
        h = Math.min(MAX_H, height - 24);
        left = (width - w) / 2;
        top = (height - h) / 2;
        list.setViewport(left + 8, top + TITLE_H, w - 16, h - TITLE_H - 8);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        layout();
        g.fill(left, top, left + w, top + h, 0xFFD8C7A0);
        g.fill(left + 2, top + 2, left + w - 2, top + h - 2, 0xFFEADFBE);
        g.drawCenteredString(font, title, width / 2, top + 8, 0xFF4A3A22);
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
                    g.drawString(font, seq, left + 8, y, 0xFF3A2E1A, false);
                }
                y += LINE_H;
            }
            y += GAP;
        }
        list.end(g);
        list.drawBar(g, 0xFFD8C7A0, 0xAA8A6E3E);
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        layout();
        return new int[] {left, top, w, h};
    }
}
