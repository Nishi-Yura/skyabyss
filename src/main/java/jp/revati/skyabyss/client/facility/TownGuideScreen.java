package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.guide.TownGuideClient;
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
//   下に「案内をやめる」(案内中だけ) と「閉じる」
public class TownGuideScreen extends Screen {
    private static final int MAX_W = 340;
    private static final int MAX_H = 300;
    private static final int ROW_H = 14;
    private static final int HEAD_H = 15;
    private static final int BTN_W = 86;
    private static final int BTN_H = 16;

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
        w = Math.min(MAX_W, width - 16);
        int want = 44 + contentHeight() + 8 + BTN_H + 14;
        h = Math.min(Math.min(MAX_H, height - 16), want);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        listX = x0 + 14;
        listY = y0 + 38;
        listW = w - 28;
        listH = h - 38 - BTN_H - 18;
        rows.setViewport(listX + 2, listY + 2, listW - 4, listH - 4);
        rows.setContentHeight(contentHeight());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        FacilityUi.panel(g, x0, y0, w, h);
        Font font = this.font;
        FacilityUi.gem(g, x0 + 12, y0 + 13);
        g.drawString(font, title, x0 + 18, y0 + 9, FacilityUi.TEXT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(Component.translatable("gui.skyabyss.town_guide.hint").getString()), x0 + 14, y0 + 23, w - 30, FacilityUi.DIM, false);
        FacilityUi.well(g, listX, listY, listW, listH);
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
                    g.drawString(font, Component.translatable("gui.skyabyss.town_guide.group." + l.group.id), rows.x() + 13, y + 3, c, false);
                    g.fill(rows.x() + 2, y + 13, right - 2, y + 14, (c & 0x00FFFFFF) | 0x50000000);
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
                    jp.revati.skyabyss.client.ui.FitText.draw(g, font, name, nx, y + 3, right - 10 - nx - 40, on ? FacilityUi.GOLD : FacilityUi.TEXT, false);
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
                    g.drawString(font, far, right - 4 - fw, y + 3, on ? FacilityUi.TEAL : FacilityUi.DIM, false);
                    int dx = nx + Math.max(78, font.width(name) + 8);
                    int room = right - 10 - fw - dx;
                    if (room > 30) {
                        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.town_guide.place." + e.id() + ".desc"), dx, y + 3, room,
                                FacilityUi.DIM, false);
                    }
                }
            }
            y += lh;
        }
        rows.end(g);
        rows.drawBar(g, 0xFF0A0C12, FacilityUi.TEAL);
        // ボタン
        int by = y0 + h - BTN_H - 10;
        if (current != null) {
            button(g, x0 + 14, by, Component.translatable("gui.skyabyss.town_guide.stop"), mx, my);
        }
        button(g, x0 + w - 14 - BTN_W, by, Component.translatable("gui.skyabyss.town_guide.close"), mx, my);
    }

    private void button(GuiGraphics g, int x, int y, Component label, int mx, int my) {
        boolean hover = mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
        g.fill(x, y, x + BTN_W, y + BTN_H, hover ? FacilityUi.ROW_HOVER : FacilityUi.ROW);
        g.renderOutline(x, y, BTN_W, BTN_H, hover ? FacilityUi.TEAL : FacilityUi.LINE);
        g.drawCenteredString(font, label, x + BTN_W / 2, y + 4, hover ? FacilityUi.TEXT : FacilityUi.DIM);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (rows.mouseClicked(mx, my, button)) {
                return true;
            }
            int by = y0 + h - BTN_H - 10;
            if (my >= by && my < by + BTN_H) {
                if (TownGuideClient.currentId() != null && mx >= x0 + 14 && mx < x0 + 14 + BTN_W) {
                    TownGuideClient.pick("");
                    click();
                    onClose();
                    return true;
                }
                if (mx >= x0 + w - 14 - BTN_W && mx < x0 + w - 14) {
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
        return super.mouseClicked(mx, my, button);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return rows.mouseScrolled(mx, my, dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return rows.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return rows.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return rows.keyPressed(key) || super.keyPressed(key, scan, mods);
    }
}
