package jp.revati.skyabyss.titan.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// 巨神の背中の右上の目盛り (12 の 10.2節。潮殻の TideGaugeHud の板の描き方を巨神によらない形にしたもの)。
// 板の絵 (左の飾り) と文だけ巨神ごと。幅は文の長さに合わせ (最小 112、ボスバーの右端から 4 あけた所まで)、
// それでも入らない狭い画面では枠の中で切る。右上の効き目の絵の下へずらす。鳴った石は右の三つの点。
// (潮殻は今のところ TideGaugeHud のまま。中身は同じ)
public final class BackGaugeHud {
    public static final int HEIGHT = 30;
    public static final int DECOR = 34;

    public interface Decor {
        // 左の飾り (幅 DECOR - 4、高さ 20) を (x, y) から描く
        void draw(GuiGraphics g, int x, int y);
    }

    private BackGaugeHud() {
    }

    // 板の上端。右上の効き目の絵 (バニラ: 良い効き目は y 1〜、そのほかは y 27〜) があれば、その下へずらす
    public static int top(Minecraft mc) {
        if (mc.player == null || mc.player.getActiveEffects().isEmpty()) {
            return 6;
        }
        boolean other = mc.player.getActiveEffects().stream().anyMatch(e -> e.showIcon() && !e.getEffect().value().isBeneficial());
        boolean good = mc.player.getActiveEffects().stream().anyMatch(e -> e.showIcon() && e.getEffect().value().isBeneficial());
        return other ? 54 : good ? 28 : 6;
    }

    // 板の幅 (文に合わせる)
    public static int width(GuiGraphics g, Minecraft mc, Component name, Component time) {
        int text = Math.max(mc.font.width(name), mc.font.width(time));
        return Mth.clamp(DECOR + text + 10, 112, Math.max(112, g.guiWidth() / 2 - 91 - 4 - 6));
    }

    // 板を描く。border は縁の色、bg は地の色、文の色は name・time の色
    public static void draw(GuiGraphics g, Minecraft mc, Component name, Component time, int stones, int border, int bg, int nameColor,
            int timeColor, int stoneColor, Decor decor) {
        int w = width(g, mc, name, time);
        int h = HEIGHT;
        int x = g.guiWidth() - w - 6;
        int y = top(mc);
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + w, y + h - 2, bg);
        g.fill(x + 2, y, x + w - 2, y + 1, border);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, border);
        g.fill(x, y + 2, x + 1, y + h - 2, border);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, border);
        decor.draw(g, x + 4, y + 5);
        int room = w - DECOR - 10;
        int tx = x + DECOR;
        g.drawString(mc.font, Language.getInstance().getVisualOrder(mc.font.substrByWidth(name, room)), tx, y + 5, nameColor, true);
        g.drawString(mc.font, Language.getInstance().getVisualOrder(mc.font.substrByWidth(time, room)), tx, y + 16, timeColor, true);
        for (int i = 0; i < 3; i++) {
            int c = i < stones ? stoneColor : 0xFF404848;
            g.fill(x + w - 8, y + 6 + i * 7, x + w - 4, y + 10 + i * 7, c);
        }
    }
}
