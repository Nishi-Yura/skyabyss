package jp.revati.skyabyss.client.trial;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.trial.RouteBoons;
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// 挑戦の加護を選ぶ画面 (trial/RouteBoons)。試練の板と同じ石板の枠に、二枚の札。
// 押す (または 1 / 2) と選んで閉じる。Esc は閉じるだけ (残りの秒がたつとサーバーが一つ目に決める)。
// 板は黄金比の中の板 (288x178。client/ui/Golden。docs/design/56): 見出しの帯に題と残りの秒、中身に同じ幅の札二枚 (説明は行 13)、下の段に手引き
public class RouteBoonScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.medium(Golden.FOOT);
    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;

    private static final int W = Golden.M_W;
    private static final int H = Golden.M_H;
    private static final int CARD_W = (LAYOUT.body().w() - Golden.GAP) / 2;
    private static final int CARD_H = LAYOUT.body().h();
    private static final int CARD_Y = LAYOUT.body().y();

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
        return LAYOUT.body().x() + i * (CARD_W + Golden.GAP);
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
        fit.fit(width, height, W, H, 3f, 0.5f);
        hover = cardAt(fit.x(screenX), fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        g.drawString(font, Component.translatable("gui.skyabyss.boon.title"), head.x() + Golden.S5, Golden.textY(head.y(), head.h()), argb(1f, GOLD), false);
        int left = (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
        Component sub = Component.translatable("gui.skyabyss.boon.choose", left);
        g.drawString(font, sub, head.right() - Golden.S5 - font.width(sub), Golden.textY(head.y(), head.h()), argb(1f, TEAL), false);
        for (int i = 0; i < 2; i++) {
            RouteBoons.Boon b = boons[i];
            int x = cardX(i);
            boolean on = hover == i;
            Golden.recess(g, new Golden.Box(x, CARD_Y, CARD_W, CARD_H));
            if (on) {
                Golden.outline(g, x, CARD_Y, x + CARD_W, CARD_Y + CARD_H, argb(1f, GOLD));
                g.fill(x + 1, CARD_Y + 1, x + CARD_W - 1, CARD_Y + CARD_H - 1, argb(0.08f, GOLD));
            }
            // 押す数字の札
            int nx = x + Golden.S5;
            int ny = CARD_Y + Golden.S5;
            Golden.outline(g, nx, ny, nx + Golden.S13, ny + Golden.S13, argb(1f, on ? GOLD : 0x4A5A68));
            String n = String.valueOf(i + 1);
            g.drawString(font, n, nx + 7 - font.width(n) / 2, Golden.textY(ny, Golden.S13), argb(1f, on ? GOLD : TEXT), false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(b.key()), nx + Golden.S13 + Golden.S5, Golden.textY(ny, Golden.S13),
                    CARD_W - 2 * Golden.S5 - Golden.S13 - Golden.S5, argb(1f, GOLD), true);
            int ly = ny + Golden.S13 + Golden.S5;
            List<FormattedCharSequence> lines = jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable(b.key() + ".desc"), CARD_W - 2 * Golden.S8);
            for (int l = 0; l < lines.size() && ly + Golden.ROW <= CARD_Y + CARD_H - 2; l++) {
                g.drawString(font, lines.get(l), x + Golden.S8, Golden.textY(ly, Golden.ROW), argb(1f, TEXT), false);
                ly += Golden.ROW;
            }
        }
        Golden.Box foot = LAYOUT.foot();
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.boon.help"), foot.x() + Golden.S5, Golden.textY(foot.y(), foot.h()),
                foot.w() - Golden.S13, argb(1f, Golden.DIM), false);
        fit.end(g);
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 3f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
