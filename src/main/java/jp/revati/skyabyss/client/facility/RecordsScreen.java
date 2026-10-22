package jp.revati.skyabyss.client.facility;

import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.facility.KeepRecords;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// 空見の砦の老兵の「試練の記録」(docs/design/35 の 3.2。中身は facility.KeepRecords)。施設の画面と同じ石板と骨の枠。
//   上に巨神のつまみ 4 つ (出会っていない巨神は「？」)、その巨神の順路の記録 (越えた回数・最速)、試練の一覧 (見ていない試練は「？？？」、済んだ回数、修飾の帯、任意の目標の印)。
//   下に称号の数と済んだ試練の合計
// 2026-10-22 の部屋の見直し: 窓は画面に合わせて広がり、試練の一覧は目盛りつきで送る (以前は 12 行で切れていた)
public class RecordsScreen extends Screen {
    private static final int MAX_W = 380;
    private static final int MAX_H = 300;
    private static final int ROW_H = 14;

    private final KeepRecords.View view;
    private int page;
    private int x0, y0;
    private int w;
    private int h;
    private final ScrollPanel rows = new ScrollPanel(ROW_H);

    public RecordsScreen(KeepRecords.View view) {
        super(Component.translatable("gui.skyabyss.records.title"));
        this.view = view;
        // はじめは、心臓を止めたいちばん新しい巨神 (なければ一つ目)
        for (int i = 0; i < view.pages().size(); i++) {
            if (view.pages().get(i).met()) {
                page = i;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.min(MAX_W, width - 16);
        h = Math.min(MAX_H, height - 16);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        int lx = x0 + 16;
        int ly = y0 + 54;
        int listW = w - 32;
        int listH = h - 84;
        rows.setViewport(lx + 2, ly + 2, listW - 4, listH - 4);
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
        // つまみ
        int tx = x0 + 16;
        for (int i = 0; i < view.pages().size(); i++) {
            KeepRecords.TitanPage p = view.pages().get(i);
            Component c = p.met() ? Component.translatable("progress.skyabyss.titan." + p.titan()) : Component.literal("？");
            int tw = Math.max(40, font.width(c) + 12);
            boolean on = page == i;
            boolean hover = mx >= tx && mx < tx + tw && my >= y0 + 22 && my < y0 + 36;
            g.fill(tx, y0 + 22, tx + tw, y0 + 36, on ? 0xFF2A5E6A : hover ? FacilityUi.ROW_HOVER : FacilityUi.ROW);
            g.renderOutline(tx, y0 + 22, tw, 14, on ? FacilityUi.TEAL : FacilityUi.LINE);
            g.drawString(font, c, tx + 6, y0 + 25, p.stopped() ? FacilityUi.GOLD : on ? FacilityUi.TEXT : FacilityUi.DIM, false);
            tx += tw + 3;
        }
        if (view.pages().isEmpty()) {
            return;
        }
        KeepRecords.TitanPage p = view.pages().get(page);
        int x = x0 + 16;
        int y = y0 + 42;
        long best = p.routeBest() / 20;
        Component route = p.routeClears() <= 0 ? Component.translatable("gui.skyabyss.records.route_none")
                : Component.translatable("gui.skyabyss.records.route", p.routeClears(), String.format("%d:%02d", best / 60, best % 60));
        g.drawString(font, route, x, y, FacilityUi.TEXT, false);
        int ly = y + 12;
        int lw = w - 32;
        int lh = h - 84;
        FacilityUi.well(g, x, ly, lw, lh);
        if (!p.met()) {
            g.drawString(font, Component.translatable("gui.skyabyss.records.unmet"), x + 6, ly + 6, FacilityUi.DIM, false);
        } else {
            rows.setContentHeight(p.rows().size() * ROW_H);
            rows.begin(g);
            for (int i = 0; i < p.rows().size(); i++) {
                int ry = rows.top() + i * ROW_H;
                if (ry + ROW_H < rows.y() || ry > rows.y() + rows.height()) {
                    continue;
                }
                KeepRecords.Row r = p.rows().get(i);
                Trial.Def def = TrialCatalog.get(r.cellId());
                Component name = r.seen() && def != null ? Component.translatable(def.nameKey()) : Component.translatable("trial.skyabyss.unknown");
                g.drawString(font, font.plainSubstrByWidth(name.getString(), 130), x + 6, ry + 2, r.seen() ? FacilityUi.TEXT : FacilityUi.DIM, false);
                if (!r.seen()) {
                    continue;
                }
                Component n = Component.translatable("gui.skyabyss.trial.codex.cleared", r.cleared());
                g.drawString(font, n, x + 140, ry + 2, r.cleared() > 0 ? FacilityUi.TEAL : FacilityUi.DIM, false);
                if (r.modsTotal() > 0) {
                    int bx = x + 196;
                    int bw = 48;
                    g.fill(bx, ry + 4, bx + bw, ry + 8, 0xFF0A0C12);
                    g.fill(bx, ry + 4, bx + Math.min(bw, bw * r.modsSeen() / r.modsTotal()), ry + 8, FacilityUi.TEAL);
                    g.drawString(font, r.modsSeen() + "/" + r.modsTotal(), bx + bw + 4, ry + 2, FacilityUi.DIM, false);
                }
                if (r.goals() > 0) {
                    g.drawString(font, "◆", x + lw - 14, ry + 2, FacilityUi.GOLD, false);
                }
            }
            rows.end(g);
            rows.drawBar(g, 0xFF0A0C12, FacilityUi.TEAL);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.records.foot", view.totalCleared(), view.titles()), x, y0 + h - 22, FacilityUi.DIM, false);
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

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (rows.mouseClicked(mx, my, button)) {
                return true;
            }
            int tx = x0 + 16;
            for (int i = 0; i < view.pages().size(); i++) {
                KeepRecords.TitanPage p = view.pages().get(i);
                Component c = p.met() ? Component.translatable("progress.skyabyss.titan." + p.titan()) : Component.literal("？");
                int tw = Math.max(40, font.width(c) + 12);
                if (mx >= tx && mx < tx + tw && my >= y0 + 22 && my < y0 + 36) {
                    page = i;
                    rows.setScroll(0);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
                    return true;
                }
                tx += tw + 3;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
