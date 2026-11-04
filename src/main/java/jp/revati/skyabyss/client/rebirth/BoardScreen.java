package jp.revati.skyabyss.client.rebirth;

import java.util.List;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.rebirth.RebirthLeaderboard;
import jp.revati.skyabyss.rebirth.RebirthNet;
import jp.revati.skyabyss.rebirth.RebirthText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// 順位の石 (C11) と飾り板 (C21) の画面。順位の石: 転生数・最速・最少の死の 3 つの表。上位 3 人だけ大きく、4 位からは小さく (8 位まで)。載せない設定の切り替えもここ。
// 飾り板: 巨神ごとの世界の最速 (その巨神の背にいるときは強調。最近更新されたものは光る縁)
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 順位の石は三つの表を同じ幅の三列に (上位 3 人は二行で順位の色、7 位まで)、
// 飾り板は一行 34 の札を送る。下の段に「載せない」の切り替え (順位の石) か注
public final class BoardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box BODY = LAYOUT.body();
    private static final int ROW = Golden.ROW;
    private static final int REC_H = Golden.S34;
    private static final int TEXT = 0xFFE9EDF0;
    private static final int SOFT = 0xFF000000 | Golden.SOFT;
    private static final int GOLD = 0xFF000000 | Golden.GOLD;
    private static final int[] PODIUM = {0xFFFFD45A, 0xFFD6DDE2, 0xFFD39A64};

    private final RebirthNet.BoardPayload data;
    private boolean hidden;
    private final int w = Golden.L_W;
    private final int left = 0;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final ScrollPanel recs = new ScrollPanel(REC_H);

    BoardScreen(RebirthNet.BoardPayload data) {
        super(Component.translatable(data.kind() == 0 ? "skyabyss_rebirth.board.title" : "skyabyss_rebirth.records.title"));
        this.data = data;
        this.hidden = data.hidden();
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        recs.setViewport(BODY.x(), BODY.y(), BODY.w(), BODY.h());
        recs.setContentHeight(data.records().size() * (REC_H + Golden.S3));
        if (data.kind() == 0) {
            Golden.Box foot = LAYOUT.foot();
            addRenderableWidget(Button.builder(label(), b -> {
                hidden = !hidden;
                PacketDistributor.sendToServer(new RebirthNet.HiddenPayload(hidden));
                b.setMessage(label());
            }).bounds(foot.x(), foot.y() + 4, Golden.S89 + Golden.S55, Golden.BTN).build());
        }
    }

    private Component label() {
        return Component.translatable("skyabyss_rebirth.gui.toggle.board", Component.translatable(hidden ? "options.off" : "options.on"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), GOLD, false);
        if (data.kind() == 0) {
            renderLeaderboard(g);
        } else {
            renderRecords(g);
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        fit.end(g);
    }

    private void renderLeaderboard(GuiGraphics g) {
        int colW = (BODY.w() - 2 * Golden.GAP) / 3;
        for (int c = 0; c < Math.min(3, data.lists().size()); c++) {
            Golden.Box box = new Golden.Box(BODY.x() + c * (colW + Golden.GAP), BODY.y(), colW, BODY.h());
            Golden.recess(g, box);
            int x = box.x() + Golden.S5;
            int tw = box.w() - 2 * Golden.S5;
            int y = box.y() + 2;
            g.drawString(font, Component.translatable("skyabyss_rebirth.board.metric." + c), x, Golden.textY(y, ROW), SOFT, false);
            y += ROW + Golden.S3;
            List<RebirthNet.Line> lines = data.lists().get(c);
            if (lines.isEmpty()) {
                g.drawString(font, Component.translatable("skyabyss_rebirth.board.empty"), x, Golden.textY(y, ROW), 0xFF000000 | Golden.DIM, false);
                continue;
            }
            for (int i = 0; i < lines.size() && y + ROW <= box.bottom() - 2; i++) {
                RebirthNet.Line l = lines.get(i);
                Component value = value(c, l);
                if (i < 3) {
                    // 上位 3 人は二行、順位の色 (名前は段の色の記号つき)
                    jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal((i + 1) + "  " + l.name()), x, Golden.textY(y, ROW), tw, PODIUM[i], true);
                    jp.revati.skyabyss.client.ui.FitText.draw(g, font, RebirthText.symbols(l.rebirths(), RebirthClient.timeMs(), RebirthClient.still()).append(" ").append(value),
                            x + Golden.S8, Golden.textY(y + ROW, ROW), tw - Golden.S8, TEXT, false);
                    y += 2 * ROW;
                } else {
                    jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal((i + 1) + ". " + l.name()), x, Golden.textY(y, ROW),
                            tw - font.width(value) - Golden.S5, SOFT, false);
                    g.drawString(font, value, x + tw - font.width(value), Golden.textY(y, ROW), SOFT, false);
                    y += ROW;
                }
            }
        }
    }

    private static Component value(int metric, RebirthNet.Line l) {
        return switch (metric) {
            case 0 -> Component.translatable("skyabyss_rebirth.board.times", l.value());
            case 1 -> Component.literal(RebirthLeaderboard.time(l.value()));
            default -> Component.translatable("skyabyss_rebirth.board.deaths", l.value());
        };
    }

    private void renderRecords(GuiGraphics g) {
        recs.setContentHeight(data.records().size() * (REC_H + Golden.S3));
        recs.begin(g);
        int y = recs.top();
        int x0 = BODY.x();
        int x1 = BODY.right() - Golden.S8;
        for (RebirthNet.RecordLine r : data.records()) {
            int rh = REC_H;
            if (y + rh >= recs.y() && y <= recs.y() + recs.height()) {
                Golden.recess(g, new Golden.Box(x0, y, x1 - x0, rh));
                if (r.fresh()) {
                    // 最近更新: 光る縁 (呼吸のような明るさ。動きを減らす設定では一定)
                    double wave = RebirthClient.still() ? 1.0 : 0.6 + 0.4 * Math.sin(RebirthClient.timeMs() / 350.0);
                    int a = (int) (200 * wave);
                    Golden.outline(g, x0, y, x1, y + rh, (a << 24) | 0xE6B84A);
                }
                int tx = x0 + Golden.S8;
                g.drawString(font, r.titan(), tx, Golden.textY(y + 4, ROW), r.here() ? GOLD : TEXT, false);
                if (r.ticks() < 0) {
                    g.drawString(font, Component.translatable("skyabyss_rebirth.records.none"), tx, Golden.textY(y + 4 + ROW, ROW), 0xFF000000 | Golden.DIM, false);
                } else {
                    g.drawString(font, Component.literal(RebirthLeaderboard.time(r.ticks())), tx, Golden.textY(y + 4 + ROW, ROW), TEXT, false);
                    g.drawString(font, Component.translatable("skyabyss_rebirth.records.by", r.who()), tx + Golden.S89, Golden.textY(y + 4 + ROW, ROW), SOFT, false);
                    if (r.fresh()) {
                        Component nw = Component.translatable("skyabyss_rebirth.records.new");
                        g.drawString(font, nw, x1 - Golden.S8 - font.width(nw), Golden.textY(y + 4, ROW), GOLD, false);
                    }
                }
            }
            y += rh + Golden.S3;
        }
        recs.end(g);
        recs.drawBar(g, 0xFF101418, 0xA0E6B84A);
        Golden.Box foot = LAYOUT.foot();
        g.drawString(font, Component.translatable("skyabyss_rebirth.records.note"), foot.x() + Golden.S5, Golden.textY(foot.y(), foot.h()), 0xFF000000 | Golden.DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return recs.mouseScrolled(fit.x(mx), fit.y(my), scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return recs.mouseClicked(fit.x(mx), fit.y(my), button) || super.mouseClicked(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return recs.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale())
                || super.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale());
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return recs.mouseReleased(fit.x(mx), fit.y(my), button) || super.mouseReleased(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return recs.keyPressed(key) || super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, Golden.L_W, Golden.L_H);
    }
}
