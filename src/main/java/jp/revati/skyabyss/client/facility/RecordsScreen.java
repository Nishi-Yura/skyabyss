package jp.revati.skyabyss.client.facility;

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

// 空見の砦の老兵の「試練の記録」(docs/design/35 の 3.2。中身は facility.KeepRecords)。施設の画面と同じ石板と骨の枠。
//   上に巨神のつまみ 4 つ (出会っていない巨神は「？」)、その巨神の順路の記録 (越えた回数・最速)、試練の一覧 (見ていない試練は「？？？」、済んだ回数、修飾の帯、任意の目標の印)。
//   下に称号の数と済んだ試練の合計
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 見出しの帯の右に巨神のつまみ、一行目に順路の記録、その下に試練の一覧 (行 13・送れる)、下の段に合計
public class RecordsScreen extends Screen {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box LIST = LAYOUT.body().afterTop(Golden.ROW);
    private static final int ROW_H = Golden.ROW;
    private static final int TAB_W = Golden.S34;
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
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        w = Golden.L_W;
        h = Golden.L_H;
        x0 = 0;
        y0 = 0;
        rows.setViewport(LIST.x() + 2, LIST.y() + 2, LIST.w() - 3, LIST.h() - 4);
    }

    // つまみ i の幅と左端 (見出しの帯の右寄せ)
    private int tabW(int i) {
        KeepRecords.TitanPage p = view.pages().get(i);
        Component c = p.met() ? Component.translatable("progress.skyabyss.titan." + p.titan()) : Component.literal("？");
        return Math.max(TAB_W, font.width(c) + 10);
    }

    private int tabX(int i) {
        int x = LAYOUT.head().right();
        for (int k = view.pages().size() - 1; k >= i; k--) {
            x -= tabW(k) + (k < view.pages().size() - 1 ? Golden.S3 : 0);
        }
        return x;
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
        // つまみ (見出しの帯の右寄せ)
        int ty = head.y() + 4;
        for (int i = 0; i < view.pages().size(); i++) {
            KeepRecords.TitanPage p = view.pages().get(i);
            Component c = p.met() ? Component.translatable("progress.skyabyss.titan." + p.titan()) : Component.literal("？");
            int tx = tabX(i);
            int tw = tabW(i);
            boolean on = page == i;
            boolean hover = mx >= tx && mx < tx + tw && my >= ty && my < ty + Golden.BTN;
            Golden.tab(g, font, new Golden.Box(tx, ty, tw, Golden.BTN), p.stopped() ? c.copy().withStyle(st -> st.withColor(Golden.GOLD)) : c, on, hover);
        }
        Golden.Box foot = LAYOUT.foot();
        g.drawString(font, Component.translatable("gui.skyabyss.records.foot", view.totalCleared(), view.titles()), foot.x() + Golden.S5,
                Golden.textY(foot.y(), foot.h()), FacilityUi.DIM, false);
        if (view.pages().isEmpty()) {
            fit.end(g);
            return;
        }
        KeepRecords.TitanPage p = view.pages().get(page);
        int x = LIST.x();
        int y = Golden.textY(LAYOUT.body().y(), Golden.ROW);
        long best = p.routeBest() / 20;
        Component route = p.routeClears() <= 0 ? Component.translatable("gui.skyabyss.records.route_none")
                : Component.translatable("gui.skyabyss.records.route", p.routeClears(), String.format("%d:%02d", best / 60, best % 60));
        g.drawString(font, route, x, y, FacilityUi.TEXT, false);
        int ly = LIST.y();
        int lw = LIST.w();
        Golden.recess(g, LIST);
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
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(name.getString()), x + Golden.S8, Golden.textY(ry, ROW_H), 130, r.seen() ? FacilityUi.TEXT : FacilityUi.DIM, false);
                if (!r.seen()) {
                    continue;
                }
                Component n = Component.translatable("gui.skyabyss.trial.codex.cleared", r.cleared());
                g.drawString(font, n, x + 144, Golden.textY(ry, ROW_H), r.cleared() > 0 ? FacilityUi.TEAL : FacilityUi.DIM, false);
                if (r.modsTotal() > 0) {
                    int bx = x + 212;
                    int bw = Golden.S55;
                    g.fill(bx, ry + 5, bx + bw, ry + 8, 0xFF0A0C12);
                    g.fill(bx, ry + 5, bx + Math.min(bw, bw * r.modsSeen() / r.modsTotal()), ry + 8, FacilityUi.TEAL);
                    g.drawString(font, r.modsSeen() + "/" + r.modsTotal(), bx + bw + 4, Golden.textY(ry, ROW_H), FacilityUi.DIM, false);
                }
                if (r.goals() > 0) {
                    g.drawString(font, "◆", x + lw - 18, Golden.textY(ry, ROW_H), FacilityUi.GOLD, false);
                }
            }
            rows.end(g);
            rows.drawBar(g, 0xFF0A0C12, FacilityUi.TEAL);
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
            int ty = LAYOUT.head().y() + 4;
            for (int i = 0; i < view.pages().size(); i++) {
                int tx = tabX(i);
                int tw = tabW(i);
                if (mx >= tx && mx < tx + tw && my >= ty && my < ty + Golden.BTN) {
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
