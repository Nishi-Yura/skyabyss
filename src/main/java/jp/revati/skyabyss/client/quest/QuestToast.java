package jp.revati.skyabyss.client.quest;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// クエストが始まった・進んだ・終わったときの小さな札 (右上、4 秒)。伝承の書の札 (羊皮紙) と見分けられるよう、暗い石板の色にする
public class QuestToast implements Toast {
    private static final long DURATION = 4000L;
    private final Component head;
    private final Component title;
    private final boolean main;

    QuestToast(Component head, Component title, boolean main) {
        this.head = head;
        this.title = title;
        this.main = main;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        Font font = toasts.getMinecraft().font;
        int w = width();
        int h = height();
        g.fill(0, 0, w, h, 0xF0121619);
        g.renderOutline(0, 0, w, h, 0xFF2E5552);
        g.fill(2, 2, 4, h - 2, main ? 0xFFF2C14E : 0xFF7FD6D0);
        g.drawString(font, head, 10, 7, main ? 0xFFF2C14E : 0xFF7FD6D0, false);
        List<FormattedCharSequence> lines = font.split(title, w - 16);
        if (!lines.isEmpty()) {
            g.drawString(font, lines.get(0), 10, 18, 0xFFE9E3D4, false);
        }
        return timeSinceLastVisible >= DURATION * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
