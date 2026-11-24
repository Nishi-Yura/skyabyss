package jp.revati.skyabyss.lore.client;

import java.util.List;

import jp.revati.skyabyss.lore.LoreSetup;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

// 伝承が記されたときの控えめな知らせ。右上に HUD の札が 4 秒出る (チャットには出さない)
public class LoreToast implements Toast {
    private static final long DURATION = 4000L;
    private final Component title;

    LoreToast(Component title) {
        this.title = title;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        jp.revati.skyabyss.client.hud.HudManager.noteToast(g, height());
        Font font = toasts.getMinecraft().font;
        // 札の地は HUD の札と同じ (鐘の銅。筋は鐘の金)
        jp.revati.skyabyss.client.ui.Plate.hudCard(g, 0, 0, width(), height(), jp.revati.skyabyss.client.ui.Plate.hudHead(1f), 1f);
        g.renderFakeItem(new ItemStack(LoreSetup.LORE_BOOK.get()), 8, 8);
        g.drawString(font, Component.translatable("gui.skyabyss.lore.toast"), 30, 7, jp.revati.skyabyss.client.ui.Plate.hudHead(1f), false);
        // 長い題は省かず、縮めず、等倍で折り返す (札の高さが行に合わせて伸びる。docs/design/56 の 1「字」)
        List<FormattedCharSequence> rows = rows(font);
        for (int i = 0; i < rows.size(); i++) {
            g.drawString(font, rows.get(i), 30, 18 + i * 10, jp.revati.skyabyss.client.ui.Plate.hudText(1f), false);
        }
        return timeSinceLastVisible >= DURATION * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    private List<FormattedCharSequence> rows(Font font) {
        List<FormattedCharSequence> rows = font.split(title, width() - 36);
        return rows.isEmpty() ? List.of(Language.getInstance().getVisualOrder(title)) : rows;
    }

    @Override
    public int height() {
        return Math.max(32, 18 + rows(net.minecraft.client.Minecraft.getInstance().font).size() * 10 + 4);
    }
}
