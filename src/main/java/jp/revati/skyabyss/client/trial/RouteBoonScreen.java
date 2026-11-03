package jp.revati.skyabyss.client.trial;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.trial.RouteBoons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// 挑戦の加護を選ぶ画面 (trial/RouteBoons)。試練の板と同じ石板の枠に、二枚の札。
// 押す (または 1 / 2) と選んで閉じる。Esc は閉じるだけ (残りの秒がたつとサーバーが一つ目に決める)
public class RouteBoonScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;

    private static final int W = 300;
    private static final int H = 128;
    private static final int CARD_W = 134;
    private static final int CARD_H = 60;
    private static final int CARD_Y = 40;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final RouteBoons.Boon[] boons;
    private final long until;
    private int hover = -1;

    public RouteBoonScreen(RouteBoons.Offer offer) {
        super(Component.translatable("gui.skyabyss.boon.title"));
        this.boons = new RouteBoons.Boon[] { RouteBoons.Offer.boon(offer.a()), RouteBoons.Offer.boon(offer.b()) };
        this.until = System.currentTimeMillis() + offer.seconds() * 1000L;
    }

    // サーバーから: 見せる・閉じる。幕の最中は割り込まない (サーバーが残りの秒で決める)
    public static void receive(RouteBoons.Offer offer) {
        Minecraft mc = Minecraft.getInstance();
        if (!offer.open()) {
            if (mc.screen instanceof RouteBoonScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (RouteBoons.Offer.boon(offer.a()) == null || RouteBoons.Offer.boon(offer.b()) == null
                || jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            return;
        }
        mc.setScreen(new RouteBoonScreen(offer));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void pick(int index) {
        PacketDistributor.sendToServer(new RouteBoons.Pick(index));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_1 || key == GLFW.GLFW_KEY_KP_1) {
            pick(0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_2 || key == GLFW.GLFW_KEY_KP_2) {
            pick(1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        int i = cardAt(fit.x(screenX), fit.y(screenY));
        if (button == 0 && i >= 0) {
            pick(i);
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    private static int cardX(int i) {
        return 12 + i * (CARD_W + 8);
    }

    private static int cardAt(double mx, double my) {
        for (int i = 0; i < 2; i++) {
            if (mx >= cardX(i) && mx < cardX(i) + CARD_W && my >= CARD_Y && my < CARD_Y + CARD_H) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 絵
    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int line, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF03050A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(1f, line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, argb(1f, fill));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, argb(0.10f, 0xFFFFFF));
        g.fill(x + 2, y + 2, x + 3, y + h - 2, argb(0.06f, 0xFFFFFF));
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, argb(0.35f, 0x000000));
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        g.fill(0, 0, width, height, argb(0.55f, BG));
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        hover = cardAt(fit.x(screenX), fit.y(screenY));
        fit.begin(g);
        frame(g, 0, 0, W, H, 0x3A4A58, PANEL);
        TextStyle.HEADING.drawLeft(g, font, Component.translatable("gui.skyabyss.boon.title"), 12, 9, 1f, GOLD);
        int left = (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
        Component sub = Component.translatable("gui.skyabyss.boon.choose", left);
        g.drawString(font, sub, W - 12 - font.width(sub), 14, argb(1f, TEAL), false);
        for (int i = 0; i < 2; i++) {
            RouteBoons.Boon b = boons[i];
            int x = cardX(i);
            boolean on = hover == i;
            frame(g, x, CARD_Y, CARD_W, CARD_H, on ? GOLD : 0x2C3846, on ? 0x2A2618 : CARD);
            // 押す数字の札
            frame(g, x + 6, CARD_Y + 6, 14, 14, on ? GOLD : 0x4A5A68, 0x0E1118);
            String n = String.valueOf(i + 1);
            g.drawString(font, n, x + 13 - font.width(n) / 2, CARD_Y + 9, argb(1f, on ? GOLD : TEXT), false);
            g.drawString(font, Component.translatable(b.key()), x + 26, CARD_Y + 9, argb(1f, GOLD), true);
            List<FormattedCharSequence> lines = font.split(Component.translatable(b.key() + ".desc"), CARD_W - 16);
            for (int l = 0; l < Math.min(3, lines.size()); l++) {
                g.drawString(font, lines.get(l), x + 8, CARD_Y + 28 + l * 10, argb(1f, TEXT), false);
            }
        }
        g.drawString(font, Component.translatable("gui.skyabyss.boon.help"), 12, H - 18, argb(1f, DIM), false);
        fit.end(g);
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
