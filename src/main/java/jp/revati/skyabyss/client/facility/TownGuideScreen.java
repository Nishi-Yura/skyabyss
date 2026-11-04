package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.guide.TownGuideClient;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.facility.TownGuide;
import jp.revati.skyabyss.facility.TownGuideNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// 都の案内人の「行き先を選ぶ」画面 (facility/TownGuide。docs/design/18 の 8.5)。施設の画面と同じ石板と骨の枠 (FacilityUi)。
//   種類ごとの見出し (金 = 買う・預ける / 青 = 休む・旅 / 赤 = 鍛える・戦う / 紫 = 物語・祈り / 灰 = 裏通り。色の札つき) の下に、
//   施設の名前・ひとこと・いまの場所からの距離。案内中の施設には「案内中」。行を押すと、その戸までの灯りがともり画面が閉じる。
//   下に「案内をやめる」(案内中だけ) と「閉じる」。板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 一行目に手引き、その下に一覧 (行 13)、下の段に釦
public class TownGuideScreen extends Screen {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box LIST = LAYOUT.body().afterTop(Golden.ROW);
    private static final int ROW_H = Golden.ROW;
    private static final int HEAD_H = Golden.ROW;
    private static final int BTN_W = Golden.S89;
    private static final int BTN_H = Golden.BTN;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    // 一覧の行 (見出し or 施設)
    private record Line(boolean head, TownGuide.Group group, TownGuideNetwork.Entry entry) {
    }

    private final List<Line> lines = new ArrayList<>();
    private final ScrollPanel rows = new ScrollPanel(ROW_H);
    private int x0, y0, w, h;
    private int listX, listY, listW, listH;

    public TownGuideScreen(TownGuideNetwork.Menu menu) {
        super(Component.translatable("gui.skyabyss.town_guide.title"));
        for (TownGuide.Group g : TownGuide.Group.values()) {
            boolean any = false;
            for (TownGuideNetwork.Entry e : menu.entries()) {
                if (e.group() == g.ordinal()) {
                    if (!any) {
                        lines.add(new Line(true, g, null));
                        any = true;
                    }
                    lines.add(new Line(false, g, e));
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int contentHeight() {
        int y = 0;
        for (Line l : lines) {
            y += l.head ? HEAD_H : ROW_H;
        }
        return y;
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        w = Golden.L_W;
        h = Golden.L_H;
        x0 = 0;
        y0 = 0;
        listX = LIST.x();
        listY = LIST.y();
        listW = LIST.w();
        listH = LIST.h();
        rows.setViewport(listX + 2, listY + 2, listW - 3, listH - 4);
        rows.setContentHeight(contentHeight());
    }

    private int buttonY() {
        return LAYOUT.foot().y() + 4;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
    }

    @Override
    public void render(GuiGraphics g, int sx, int sy, float partialTick) {
        renderBackground(g, sx, sy, partialTick);
        int mx = (int) Math.floor(fit.x(sx));
        int my = (int) Math.floor(fit.y(sy));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Font font = this.font;
        Golden.Box head = LAYOUT.head();
        FacilityUi.gem(g, head.x() + 4, head.y() + head.h() / 2);
        g.drawString(font, title, head.x() + 10, Golden.textY(head.y(), head.h()), FacilityUi.TEXT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(Component.translatable("gui.skyabyss.town_guide.hint").getString()),
                LAYOUT.body().x() + Golden.S5, Golden.textY(LAYOUT.body().y(), Golden.ROW), LAYOUT.body().w() - Golden.S13, FacilityUi.DIM, false);
        Golden.recess(g, LIST);
        String current = TownGuideClient.currentId();
        Minecraft mc = Minecraft.getInstance();
        rows.begin(g);
        int y = rows.top();
        int right = rows.x() + rows.width() - (rows.scrollable() ? ScrollPanel.GUTTER : 0);
        for (Line l : lines) {
            int lh = l.head ? HEAD_H : ROW_H;
            if (y + lh >= rows.y() && y <= rows.y() + rows.height()) {
                int c = 0xFF000000 | l.group.rgb;
                if (l.head) {
                    // 見出し: 色の札と種類の名前、下に細い線
                    g.fill(rows.x() + 2, y + 4, rows.x() + 8, y + 10, c);
                    g.renderOutline(rows.x() + 1, y + 3, 8, 8, 0xFF0A0C12);
                    g.drawString(font, Component.translatable("gui.skyabyss.town_guide.group." + l.group.id), rows.x() + 13, Golden.textY(y, lh), c, false);
                    g.fill(rows.x() + 2, y + lh - 1, right - 2, y + lh, (c & 0x00FFFFFF) | 0x50000000);
                } else {
                    TownGuideNetwork.Entry e = l.entry;
                    boolean hover = mx >= rows.x() && mx < right && my >= Math.max(y, rows.y()) && my < Math.min(y + lh, rows.y() + rows.height());
                    boolean on = e.id().equals(current);
                    if (hover || on) {
                        g.fill(rows.x() + 1, y, right - 1, y + lh - 1, on ? 0xFF243440 : FacilityUi.ROW_HOVER);
                    }
                    // 灯りの印 (小さな菱形の灯)
                    int lx = rows.x() + 6;
                    g.fill(lx - 1, y + 3, lx + 2, y + 10, 0xFF0A0C12);
                    g.fill(lx, y + 4, lx + 1, y + 9, c);
                    g.fill(lx - 2, y + 5, lx + 3, y + 8, c);
                    g.fill(lx - 3, y + 6, lx + 4, y + 7, (c & 0x00FFFFFF) | 0x80000000);
                    Component name = Component.translatable("gui.skyabyss.town_guide.place." + e.id());
                    int nx = rows.x() + 14;
                    jp.revati.skyabyss.client.ui.FitText.draw(g, font, name, nx, Golden.textY(y, lh), right - 10 - nx - 40, on ? FacilityUi.GOLD : FacilityUi.TEXT, false);
                    // 距離 (右寄せ) と、案内中の印
                    String far = "";
                    if (mc.player != null) {
                        double d = Math.sqrt(mc.player.distanceToSqr(e.door().getX() + 0.5, mc.player.getY(), e.door().getZ() + 0.5));
                        far = Component.translatable("gui.skyabyss.town_guide.far", (int) (Math.round(d / 5.0) * 5)).getString();
                    }
                    if (on) {
                        far = Component.translatable("gui.skyabyss.town_guide.current").getString() + "  " + far;
                    }
                    int fw = font.width(far);
                    g.drawString(font, far, right - 4 - fw, Golden.textY(y, lh), on ? FacilityUi.TEAL : FacilityUi.DIM, false);
                    int dx = nx + Math.max(78, font.width(name) + 8);
                    int room = right - 10 - fw - dx;
                    if (room > 30) {
                        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.town_guide.place." + e.id() + ".desc"), dx, Golden.textY(y, lh), room,
                                FacilityUi.DIM, false);
                    }
                }
            }
            y += lh;
        }
        rows.end(g);
        rows.drawBar(g, 0xFF0A0C12, FacilityUi.TEAL);
        // 釦 (下の段)
        int by = buttonY();
        Golden.Box foot = LAYOUT.foot();
        if (current != null) {
            button(g, foot.x(), by, Component.translatable("gui.skyabyss.town_guide.stop"), mx, my);
        }
        button(g, foot.right() - BTN_W, by, Component.translatable("gui.skyabyss.town_guide.close"), mx, my);
        fit.end(g);
    }

    private void button(GuiGraphics g, int x, int y, Component label, int mx, int my) {
        boolean hover = mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
        g.fill(x, y, x + BTN_W, y + BTN_H, hover ? FacilityUi.ROW_HOVER : FacilityUi.ROW);
        g.renderOutline(x, y, BTN_W, BTN_H, hover ? FacilityUi.TEAL : FacilityUi.LINE);
        g.drawCenteredString(font, label, x + BTN_W / 2, Golden.textY(y, BTN_H), hover ? FacilityUi.TEXT : FacilityUi.DIM);
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        Golden.Box foot = LAYOUT.foot();
        if (button == 0) {
            if (rows.mouseClicked(mx, my, button)) {
                return true;
            }
            int by = buttonY();
            if (my >= by && my < by + BTN_H) {
                if (TownGuideClient.currentId() != null && mx >= foot.x() && mx < foot.x() + BTN_W) {
                    TownGuideClient.pick("");
                    click();
                    onClose();
                    return true;
                }
                if (mx >= foot.right() - BTN_W && mx < foot.right()) {
                    click();
                    onClose();
                    return true;
                }
            }
            if (rows.contains(mx, my)) {
                int y = rows.top();
                int right = rows.x() + rows.width() - (rows.scrollable() ? ScrollPanel.GUTTER : 0);
                for (Line l : lines) {
                    int lh = l.head ? HEAD_H : ROW_H;
                    if (!l.head && mx < right && my >= y && my < y + lh) {
                        TownGuideClient.pick(l.entry.id());
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.8f));
                        onClose();
                        return true;
                    }
                    y += lh;
                }
            }
        }
        return super.mouseClicked(sx, sy, button);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return rows.mouseScrolled(fit.x(mx), fit.y(my), dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return rows.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale()) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return rows.mouseReleased(fit.x(mx), fit.y(my), button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return rows.keyPressed(key) || super.keyPressed(key, scan, mods);
    }
}
