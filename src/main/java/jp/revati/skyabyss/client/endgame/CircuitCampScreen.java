package jp.revati.skyabyss.client.endgame;

import java.util.List;

import jp.revati.skyabyss.client.ui.TextStyle;
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
//   題と野営地の名・今の景品 (階級の色の枠。指すと品の説明)・入れ替えまでの時間・階級ごとの出やすさ・今日の残り・「歩き出す」
public class CircuitCampScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;

    private static final int W = 360;
    private static final int H = 236;
    private static final int COLS = 7;
    private static final int SLOT = 40;
    private static final int GRID_X = (W - COLS * SLOT - (COLS - 1) * 6) / 2;
    private static final int GRID_Y = 56;
    private static final int BTN_W = 110;
    private static final int BTN_H = 20;

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
        return new int[] {GRID_X + (i % COLS) * (SLOT + 6), GRID_Y + (i / COLS) * (SLOT + 6)};
    }

    private int btnX() {
        return W - 12 - BTN_W;
    }

    private int btnY() {
        return H - 12 - BTN_H;
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
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        frame(g, 0, 0, W, H, 0x3A4A58, PANEL);
        TextStyle.HEADING.drawLeft(g, font, title, 12, 9, 1f, GOLD);
        Component land = Component.translatable("label.skyabyss.facility.circuit_camp_" + view.camp() + ".sub");
        g.drawString(font, land, W - 12 - font.width(land), 14, argb(1f, TEAL), false);
        g.drawString(font, Component.translatable("gui.skyabyss.camp.pool", time(view.secondsToRotate())), 12, 38, argb(1f, TEXT), false);
        // 景品の枠 (階級の色)
        hovered = ItemStack.EMPTY;
        List<ItemStack> pool = view.pool();
        for (int i = 0; i < pool.size(); i++) {
            int[] s = slot(i);
            ItemStack st = pool.get(i);
            int rgb = tierRgb(st);
            boolean on = mx >= s[0] && mx < s[0] + SLOT && my >= s[1] && my < s[1] + SLOT;
            frame(g, s[0], s[1], SLOT, SLOT, rgb, on ? 0x2A2618 : CARD);
            g.fill(s[0] + 2, s[1] + SLOT - 5, s[0] + SLOT - 2, s[1] + SLOT - 3, argb(0.85f, rgb));
            g.pose().pushPose();
            g.pose().translate(s[0] + SLOT / 2f - 16, s[1] + SLOT / 2f - 17, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
            if (on) {
                hovered = st;
            }
        }
        // 階級ごとの出やすさ
        int y = GRID_Y + 2 * (SLOT + 6) + 4;
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
        g.drawString(font, odds, 12, y, -1, false);
        for (var line : font.split(Component.translatable("gui.skyabyss.camp.how"), W - 24)) {
            y += 12;
            g.drawString(font, line, 12, y, argb(1f, DIM), false);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.camp.left", view.runsLeft()), 12, btnY() + 6, argb(1f, view.runsLeft() > 0 ? TEAL : DIM), false);
        // 歩き出す
        boolean on = !view.running() && mx >= btnX() && mx < btnX() + BTN_W && my >= btnY() && my < btnY() + BTN_H;
        frame(g, btnX(), btnY(), BTN_W, BTN_H, view.running() ? 0x2C3846 : on ? GOLD : 0x8A6E2C, on ? 0x2A2618 : CARD);
        Component b = Component.translatable(view.running() ? "gui.skyabyss.camp.running" : "gui.skyabyss.camp.start");
        g.drawString(font, b, btnX() + BTN_W / 2 - font.width(b) / 2, btnY() + 6, argb(1f, view.running() ? DIM : GOLD), false);
        fit.end(g);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, screenX, screenY);
        }
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
