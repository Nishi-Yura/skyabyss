package jp.revati.skyabyss.client.quest;

import java.util.List;

import net.minecraft.client.Minecraft;
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
    // 題は枠の幅で折り返し、2 行まで (長い題が途中で切れていた: first_hour_log B-2)。2 行になると札は 10 px 高くなる
    private final List<FormattedCharSequence> lines;

    QuestToast(Component head, Component title, boolean main) {
        this.head = head;
        this.title = title;
        this.main = main;
        List<FormattedCharSequence> split = Minecraft.getInstance().font.split(title, width() - 16);
        this.lines = split.size() > 2 ? split.subList(0, 2) : split;
    }

    @Override
    public int height() {
        return lines.size() > 1 ? 42 : 32;
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
        int y = 18;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, 10, y, 0xFFE9E3D4, false);
            y += 10;
        }
        return timeSinceLastVisible >= DURATION * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
