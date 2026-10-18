package jp.revati.skyabyss.guide.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.guide.WayfarerNotes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 旅の覚書の画面 (簡素。手帳の中身は文のキー wayfarer_notes.ch<n>.line.<i>。絵の入る頁は、中身を足す担当が作り足す)
public class WayfarerNotesScreen extends Screen {
    private static final int WIDTH = 220;
    private final List<Component> lines = new ArrayList<>();

    public WayfarerNotesScreen(int chapter) {
        super(Component.translatable("item.skyabyss.wayfarer_notes.named", WayfarerNotes.chapterName(chapter)));
        for (int i = 1; Language.getInstance().has(WayfarerNotes.lineKey(chapter, i)); i++) {
            lines.add(Component.translatable(WayfarerNotes.lineKey(chapter, i)));
        }
        if (lines.isEmpty()) {
            lines.add(Component.translatable("wayfarer_notes.empty"));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int left = (width - WIDTH) / 2;
        int top = 30;
        int height = 24;
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        for (Component line : lines) {
            List<FormattedCharSequence> w = font.split(line, WIDTH - 16);
            wrapped.add(w);
            height += w.size() * 10 + 4;
        }
        g.fill(left, top, left + WIDTH, top + height + 8, 0xFFD8C7A0);
        g.fill(left + 2, top + 2, left + WIDTH - 2, top + height + 6, 0xFFEADFBE);
        g.drawCenteredString(font, title, width / 2, top + 8, 0xFF4A3A22);
        int y = top + 26;
        for (List<FormattedCharSequence> w : wrapped) {
            for (FormattedCharSequence seq : w) {
                g.drawString(font, seq, left + 8, y, 0xFF3A2E1A, false);
                y += 10;
            }
            y += 4;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
