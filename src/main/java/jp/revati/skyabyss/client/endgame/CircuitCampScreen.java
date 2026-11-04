package jp.revati.skyabyss.client.endgame;

import java.util.List;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.endgame.CircuitCamps;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 巡り路の野営地の画面 (endgame/CircuitCamps。報いの箱を押すと開く)。試練の板と同じ石板の枠:
//   題と野営地の名・今の景品 (階級の色の枠。指すと品の説明)・入れ替えまでの時間・階級ごとの出やすさ・今日の残り・「歩き出す」。
//   板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 景品は 34 角の枠 7 x 2、下の段に今日の残りと「歩き出す」(89)
public class CircuitCampScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;

    private static final int W = Golden.L_W;
    private static final int H = Golden.L_H;
    private static final int COLS = 7;
    private static final int SLOT = Golden.S34;
    private static final int SLOT_GAP = Golden.S5;
    private static final int GRID_X = LAYOUT.body().x() + (LAYOUT.body().w() - COLS * SLOT - (COLS - 1) * SLOT_GAP) / 2;
    private static final int GRID_Y = LAYOUT.body().y() + Golden.ROW + Golden.S5;
    private static final int BTN_W = Golden.S89;
    private static final int BTN_H = Golden.BTN;

    private final UiFit fit = new UiFit();
    private final CircuitCamps.View view;
    private ItemStack hovered = ItemStack.EMPTY;

    public CircuitCampScreen(CircuitCamps.View view) {
        super(Component.translatable("gui.skyabyss.camp.title"));
        this.view = view;
    }

    public static void receive(CircuitCamps.View v) {
        Minecraft.getInstance().setScreen(new CircuitCampScreen(v));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int line, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF03050A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(1f, line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, argb(1f, fill));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, argb(0.10f, 0xFFFFFF));
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, argb(0.35f, 0x000000));
    }

    static int tierRgb(ItemStack s) {
        return s.getItem() instanceof ModGear g ? g.tier().rgb() : 0x8C98A4;
    }

    static Component time(int seconds) {
        int h = seconds / 3600;
        return h >= 24 ? Component.translatable("gui.skyabyss.camp.time", h / 24, h % 24) : Component.translatable("gui.skyabyss.camp.time_h", h, seconds / 60 % 60);
    }

    private int[] slot(int i) {
        return new int[] {GRID_X + (i % COLS) * (SLOT + SLOT_GAP), GRID_Y + (i / COLS) * (SLOT + SLOT_GAP)};
    }

    private int btnX() {
        return LAYOUT.foot().right() - BTN_W;
    }

    private int btnY() {
        return LAYOUT.foot().y() + 4;
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (button == 0 && !view.running() && mx >= btnX() && mx < btnX() + BTN_W && my >= btnY() && my < btnY() + BTN_H) {
            PacketDistributor.sendToServer(new CircuitCamps.Start(view.camp()));
            onClose();
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        g.fill(0, 0, width, height, argb(0.80f, BG));
        fit.fit(width, height, W, H, 3f, 0.5f);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        int hy = Golden.textY(head.y(), head.h());
        g.drawString(font, title, head.x() + Golden.S5, hy, argb(1f, GOLD), false);
        Component land = Component.translatable("label.skyabyss.facility.circuit_camp_" + view.camp() + ".sub");
        g.drawString(font, land, head.right() - Golden.S5 - font.width(land), hy, argb(1f, TEAL), false);
        int bx = LAYOUT.body().x() + Golden.S5;
        g.drawString(font, Component.translatable("gui.skyabyss.camp.pool", time(view.secondsToRotate())), bx, Golden.textY(LAYOUT.body().y(), Golden.ROW), argb(1f, TEXT), false);
        // 景品の枠 (階級の色)
        hovered = ItemStack.EMPTY;
        List<ItemStack> pool = view.pool();
        for (int i = 0; i < pool.size(); i++) {
            int[] s = slot(i);
            ItemStack st = pool.get(i);
            int rgb = tierRgb(st);
            boolean on = mx >= s[0] && mx < s[0] + SLOT && my >= s[1] && my < s[1] + SLOT;
            Golden.recess(g, new Golden.Box(s[0], s[1], SLOT, SLOT));
            Golden.outline(g, s[0], s[1], s[0] + SLOT, s[1] + SLOT, argb(on ? 1f : 0.75f, rgb));
            if (on) {
                g.fill(s[0] + 1, s[1] + 1, s[0] + SLOT - 1, s[1] + SLOT - 1, argb(0.12f, GOLD));
            }
            g.pose().pushPose();
            g.pose().translate(s[0] + 1, s[1] + 1, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
            if (on) {
                hovered = st;
            }
        }
        // 階級ごとの出やすさ
        int y = GRID_Y + 2 * SLOT + SLOT_GAP + Golden.S5;
        int total = 0;
        for (int w : CircuitCamps.WEIGHTS) {
            total += w;
        }
        MutableComponent odds = Component.empty();
        for (int t = 0; t < CircuitCamps.TIERS.length; t++) {
            GearTier tier = CircuitCamps.TIERS[t];
            if (t > 0) {
                odds.append(Component.literal("  ").withColor(DIM));
            }
            String pct = String.format(java.util.Locale.ROOT, CircuitCamps.WEIGHTS[t] * 100.0 / total < 1 ? "%.1f%%" : "%.0f%%", CircuitCamps.WEIGHTS[t] * 100.0 / total);
            odds.append(Component.translatable("gear.skyabyss.tier." + tier.key).withColor(tier.rgb())).append(Component.literal(" " + pct).withColor(DIM));
        }
        g.drawString(font, odds, bx, Golden.textY(y, Golden.ROW), -1, false);
        for (var line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.camp.how"), LAYOUT.body().w() - Golden.S13)) {
            y += Golden.ROW;
            if (y + Golden.ROW > LAYOUT.body().bottom()) {
                break;
            }
            g.drawString(font, line, bx, Golden.textY(y, Golden.ROW), argb(1f, Golden.DIM), false);
        }
        Golden.Box foot = LAYOUT.foot();
        g.drawString(font, Component.translatable("gui.skyabyss.camp.left", view.runsLeft()), foot.x() + Golden.S5, Golden.textY(foot.y(), foot.h()),
                argb(1f, view.runsLeft() > 0 ? TEAL : DIM), false);
        // 歩き出す
        boolean on = !view.running() && mx >= btnX() && mx < btnX() + BTN_W && my >= btnY() && my < btnY() + BTN_H;
        g.fill(btnX(), btnY(), btnX() + BTN_W, btnY() + BTN_H, on ? 0xFF2A2618 : 0xFF171B25);
        Golden.outline(g, btnX(), btnY(), btnX() + BTN_W, btnY() + BTN_H, argb(1f, view.running() ? 0x2C3846 : on ? GOLD : 0x8A6E2C));
        Component b = Component.translatable(view.running() ? "gui.skyabyss.camp.running" : "gui.skyabyss.camp.start");
        g.drawString(font, b, btnX() + BTN_W / 2 - font.width(b) / 2, Golden.textY(btnY(), BTN_H), argb(1f, view.running() ? DIM : GOLD), false);
        fit.end(g);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, screenX, screenY);
        }
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 3f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
