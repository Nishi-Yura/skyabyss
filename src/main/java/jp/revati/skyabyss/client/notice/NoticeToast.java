package jp.revati.skyabyss.client.notice;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 獲得・解放の札 (右上、4.5 秒)。クエストの札 (QuestToast) と同じ暗い石板に、細い縦の線と小さな見出し。
// 伝承・クエストの札と同じ列 (ToastComponent) に並ぶので、重ならない。本文は枠の幅で折り返し、3 行まで
public class NoticeToast implements Toast {
    private static final long DURATION = 4500L;
    private static final int WIDTH = 160;
    private static final int MAX_LINES = 3;
    private final Component head;
    private final List<FormattedCharSequence> lines;
    private final int accent;
    private final int height;

    NoticeToast(Font font, Component head, Component body, int accent) {
        this.head = head;
        this.accent = accent;
        List<FormattedCharSequence> split = font.split(body, WIDTH - 16);
        this.lines = split.size() > MAX_LINES ? split.subList(0, MAX_LINES) : split;
        this.height = 20 + 10 * Math.max(1, lines.size()) + 3;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        jp.revati.skyabyss.client.hud.HudManager.noteToast(g, height());
        Font font = toasts.getMinecraft().font;
        g.fill(0, 0, WIDTH, height, 0xF0121619);
        g.renderOutline(0, 0, WIDTH, height, 0xFF2E5552);
        g.fill(2, 2, 4, height - 2, 0xFF000000 | accent);
        g.drawString(font, head, 10, 7, 0xFF000000 | accent, false);
        int y = 19;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, 10, y, 0xFFE9E3D4, false);
            y += 10;
        }
        return timeSinceLastVisible >= DURATION * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
