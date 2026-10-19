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

// 伝承が記されたときの控えめな知らせ。右上に羊皮紙の札が 4 秒出る (チャットには出さない)
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
        LoreBookScreen.blitWidget(g, 0, 0, LoreBookScreen.W_TOAST);
        g.renderFakeItem(new ItemStack(LoreSetup.LORE_BOOK.get()), 8, 8);
        g.drawString(font, Component.translatable("gui.skyabyss.lore.toast"), 30, 7, LoreBookScreen.TEAL, false);
        // 札の絵は一行分の高さ。長い題は「…」で切る (折り返して途中で切れるより、省略だと分かる)
        int room = width() - 36;
        net.minecraft.network.chat.FormattedText shown = font.width(title) <= room ? title
                : net.minecraft.network.chat.FormattedText.composite(font.substrByWidth(title, room - font.width("…")), net.minecraft.network.chat.FormattedText.of("…"));
        g.drawString(font, Language.getInstance().getVisualOrder(shown), 30, 18, LoreBookScreen.INK, false);
        return timeSinceLastVisible >= DURATION * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
