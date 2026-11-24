package jp.revati.skyabyss.client.facility;

import jp.revati.skyabyss.client.ui.LedgerStage;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.facility.KeepRecords;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import jp.revati.skyabyss.client.ui.Paper;

// 空見の砦の老兵の「試練の記録」(docs/design/35 の 3.2。中身は facility.KeepRecords)。施設の画面と同じ石板と骨の枠。
//   上に巨神のつまみ 4 つ (出会っていない巨神は「？」)、その巨神の順路の記録 (越えた回数・最速)、試練の一覧 (見ていない試練は「？？？」、済んだ回数、修飾の帯、任意の目標の印)。
//   下に称号の数と済んだ試練の合計
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1): 順路の記録と試練の一覧は左の黒い紙にじかに、
// 巨神のつまみは右の生成りの帯の目録 (選んだ巨神は桃色の付箋)、合計は目録の下
public class RecordsScreen extends Screen {
    private static final Golden.Box LIST = new Golden.Box(LedgerStage.PAGE_X - 6, LedgerStage.BODY_Y, LedgerStage.PAGE_W + 6, LedgerStage.BODY_H);
    private static final int ROW_H = Golden.ROW;
    private static final int TAB_ROW = 16;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

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
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        rows.setViewport(LIST.x() + 2, LIST.y(), LIST.w() - 2, LIST.h());
    }

    // 目録の i 行目の上の端
    private static int tabY(int i) {
        return LedgerStage.LIST_Y + i * TAB_ROW;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int sx, int sy, float partialTick) {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        renderBackground(g, sx, sy, partialTick);
        int mx = (int) Math.floor(fit.x(sx));
        int my = (int) Math.floor(fit.y(sy));
        fit.begin(g);
        LedgerStage.backdrop(g, fit, width, height);
        Font font = this.font;
        LedgerStage.indexHead(g, font, title);
        // 巨神のつまみ (目録)
        for (int i = 0; i < view.pages().size(); i++) {
            KeepRecords.TitanPage p = view.pages().get(i);
            Component c = p.met() ? Component.translatable("progress.skyabyss.titan." + p.titan()) : Component.literal("？");
            int ty = tabY(i);
            boolean on = page == i;
            if (on) {
                LedgerStage.flag(g, ty, TAB_ROW - 2);
            } else if (LedgerStage.in(mx, my, LedgerStage.INDEX_X - 4, ty, LedgerStage.INDEX_W + 6, TAB_ROW - 2)) {
                LedgerStage.hover(g, ty, TAB_ROW - 2);
            }
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(c.getString()), LedgerStage.INDEX_X + 9, ty + 3, LedgerStage.INDEX_W - 12,
                    on ? LedgerStage.ON_PINK : p.stopped() ? Paper.INK_HEAD : Paper.INK, false);
        }
        // 合計 (目録の下)
        int fy = tabY(Math.max(4, view.pages().size())) + 8;
        for (net.minecraft.util.FormattedCharSequence l : font.split(Component.translatable("gui.skyabyss.records.foot", view.totalCleared(), view.titles()), LedgerStage.INDEX_W - 4)) {
            g.drawString(font, l, LedgerStage.INDEX_X + 2, fy, Paper.INK_SOFT, false);
            fy += ROW_H;
        }
        if (view.pages().isEmpty()) {
            fit.end(g);
            return;
        }
        KeepRecords.TitanPage p = view.pages().get(page);
        int x = LIST.x();
        long best = p.routeBest() / 20;
        Component route = p.routeClears() <= 0 ? Component.translatable("gui.skyabyss.records.route_none")
                : Component.translatable("gui.skyabyss.records.route", p.routeClears(), String.format("%d:%02d", best / 60, best % 60));
        LedgerStage.pageTitle(g, font, p.met() ? Component.translatable("progress.skyabyss.titan." + p.titan()) : Component.literal("？"), route);
        int ly = LIST.y();
        int lw = LIST.w();
        if (!p.met()) {
            g.drawString(font, Component.translatable("gui.skyabyss.records.unmet"), x + Golden.S8, Golden.textY(ly + 2, Golden.ROW), FacilityUi.DIM, false);
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
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(name.getString()), x + Golden.S8, Golden.textY(ry, ROW_H), 100, r.seen() ? FacilityUi.TEXT : FacilityUi.DIM, false);
                if (!r.seen()) {
                    continue;
                }
                Component n = Component.translatable("gui.skyabyss.trial.codex.cleared", r.cleared());
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, n, x + 112, Golden.textY(ry, ROW_H), 50, r.cleared() > 0 ? FacilityUi.TEAL : FacilityUi.DIM, false);
                if (r.modsTotal() > 0) {
                    int bx = x + 166;
                    int bw = Golden.S34;
                    g.fill(bx, ry + 5, bx + bw, ry + 8, 0xFF2A3044);
                    g.fill(bx, ry + 5, bx + Math.min(bw, bw * r.modsSeen() / r.modsTotal()), ry + 8, 0xFFC48A44);
                    g.drawString(font, r.modsSeen() + "/" + r.modsTotal(), bx + bw + 4, Golden.textY(ry, ROW_H), FacilityUi.DIM, false);
                }
                if (r.goals() > 0) {
                    g.drawString(font, "◆", x + lw - 18, Golden.textY(ry, ROW_H), FacilityUi.GOLD, false);
                }
            }
            rows.end(g);
            rows.drawBar(g, 0xFF0F121A, 0xFFC48A44);
        }
        fit.end(g);
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

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (button == 0) {
            if (rows.mouseClicked(mx, my, button)) {
                return true;
            }
            for (int i = 0; i < view.pages().size(); i++) {
                if (LedgerStage.in(mx, my, LedgerStage.INDEX_X - 4, tabY(i), LedgerStage.INDEX_W + 6, TAB_ROW - 2)) {
                    page = i;
                    rows.setScroll(0);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
                    return true;
                }
            }
        }
        return super.mouseClicked(sx, sy, button);
    }
}
