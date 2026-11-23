package jp.revati.skyabyss.client.rebirth;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import jp.revati.skyabyss.rebirth.RebirthConfig;
import jp.revati.skyabyss.rebirth.RebirthData;
import jp.revati.skyabyss.rebirth.RebirthNet;
import jp.revati.skyabyss.rebirth.RebirthText;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.RebirthView;
import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.LedgerStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Paper;

// 称号と飾りの画面 (C04・C23)。称号の一覧 (解けたものを選ぶ。解けていないものは条件が見える) と、見せ方の切り替え
// (名札の効果・称号・背中の飾り・光・プリズムを止める・順位表に載せる)。色の段と記号は固定で切れない。記録画面の「称号」から、または /rebirthfx で開く。
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1):
//   左の黒い紙に題と段の見本、見せ方の釦 7 つと注。右の生成りの帯 (目録) に称号の一覧 (選んでいる称号は桃色の付箋)。Esc で戻る
public final class RebirthScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int ROW = 13;
    private static final int LIST_Y = LedgerStage.LIST_Y;
    private static final int ROWS = LedgerStage.LIST_H / ROW;
    private static final int LX = LedgerStage.INDEX_X;
    private static final int LW = LedgerStage.INDEX_W - 6;
    private static final int BTN_X = LedgerStage.PAGE_X;
    private static final int BTN_W = 160;
    private static final int BTN_Y = 76;

    private final Screen parent;
    private int scroll;
    private final List<Button> toggles = new ArrayList<>();
    private Component tip;

    public RebirthScreen(@Nullable Screen parent) {
        super(Component.translatable("skyabyss_rebirth.gui.title"));
        this.parent = parent;
    }

    private record Toggle(String key, ModConfigSpec.BooleanValue value) {
    }

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle("nametag_effects", RebirthConfig.NAMETAG_EFFECTS),
            new Toggle("nametag_title", RebirthConfig.NAMETAG_TITLE),
            new Toggle("back_self", RebirthConfig.BACK_DECOR_SELF),
            new Toggle("back_others", RebirthConfig.BACK_DECOR_OTHERS),
            new Toggle("burst", RebirthConfig.BURST),
            new Toggle("prism_still", RebirthConfig.PRISM_STILL));

    // 窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    @Override
    protected void init() {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        toggles.clear();
        int y = BTN_Y;
        for (Toggle t : TOGGLES) {
            Button b = addRenderableWidget(Paper.tag(label(t), btn -> {
                t.value.set(!RebirthConfig.on(t.value));
                RebirthConfig.CLIENT_SPEC.save();
                btn.setMessage(label(t));
            }).bounds(BTN_X, y, BTN_W, Golden.BTN).build());
            toggles.add(b);
            y += Golden.BTN + 2;
        }
        addRenderableWidget(Paper.tag(boardLabel(), btn -> {
            boolean now = !hidden();
            PacketDistributor.sendToServer(new RebirthNet.HiddenPayload(now));
            // 返事 (同期) を待たず見た目だけ先に替える
            btn.setMessage(boardLabel(now));
        }).bounds(BTN_X, y, BTN_W, Golden.BTN).build());
    }

    private static Component label(Toggle t) {
        return Component.translatable("skyabyss_rebirth.gui.toggle." + t.key, Component.translatable(RebirthConfig.on(t.value) ? "options.on" : "options.off"));
    }

    private boolean hidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && RebirthView.data(mc.player).hidden();
    }

    private Component boardLabel() {
        return boardLabel(hidden());
    }

    private static Component boardLabel(boolean hidden) {
        return Component.translatable("skyabyss_rebirth.gui.toggle.board", Component.translatable(hidden ? "options.off" : "options.on"));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        LedgerStage.backdrop(g, fit, width, height);
        tip = null;
        renderBody(g, mouseX, mouseY);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        fit.end(g);
        if (tip != null) {
            g.renderTooltip(font, tip, screenX, screenY);
        }
    }

    private void renderBody(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        RebirthData d = RebirthView.data(mc.player);
        int n = RebirthView.count(mc.player);
        long t = RebirthClient.timeMs();
        boolean still = RebirthClient.still();
        // 左の頁: 題・段の見本 (色と記号と縁の形。固定)・見せ方
        LedgerStage.pageTitle(g, font, title, null);
        Component tier = n > 0 ? RebirthText.symbols(n, t, still).append(" ").append(RebirthText.level(50, n, t, still))
                : Component.translatable("skyabyss_rebirth.gui.no_rebirth");
        int tw = font.width(tier);
        int fx = LedgerStage.PAGE_X;
        RebirthClient.drawFrame(g, fx, 44, tw + 10, 14, n);
        g.drawString(font, tier, fx + 5, 47, Golden.TEXT, false);
        g.drawString(font, Component.translatable("skyabyss_rebirth.gui.look"), LedgerStage.PAGE_X, 64, Paper.ON_BLACK_SOFT, false);
        int ny = BTN_Y + (TOGGLES.size() + 1) * (Golden.BTN + 2) + 4;
        for (net.minecraft.util.FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("skyabyss_rebirth.gui.fixed"), LedgerStage.PAGE_W)) {
            if (ny + 9 > LedgerStage.FOOT_Y) {
                break;
            }
            g.drawString(font, line, LedgerStage.PAGE_X, ny, Paper.ON_BLACK_DIM, false);
            ny += 11;
        }

        // 右の帯: 称号の一覧 (最初の行 = 外す)
        LedgerStage.indexHead(g, font, Component.translatable("skyabyss_rebirth.gui.titles", Long.bitCount(d.unlocked()), RebirthTitles.MAX));
        List<RebirthTitles.Title> all = RebirthTitles.all();
        int total = all.size() + 1;
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - ROWS));
        for (int i = 0; i < ROWS && scroll + i < total; i++) {
            int idx = scroll + i;
            int y = LIST_Y + i * ROW;
            boolean hover = mouseX >= LX - 2 && mouseX < LX + LW + 4 && mouseY >= y && mouseY < y + ROW;
            boolean sel;
            boolean has = true;
            String shown;
            if (idx == 0) {
                sel = d.title().isEmpty();
                shown = Component.translatable("skyabyss_rebirth.gui.no_title").getString();
            } else {
                RebirthTitles.Title ti = all.get(idx - 1);
                has = RebirthTitles.has(d.unlocked(), ti);
                sel = ti.id().equals(d.title());
                Component name = Component.translatable("title.skyabyss_rebirth." + ti.id());
                shown = has ? name.getString() : "？ " + "・".repeat(Math.min(6, name.getString().length()));
                if (hover) {
                    tip = has ? Component.translatable("title.skyabyss_rebirth." + ti.id() + ".how")
                            : Component.translatable("skyabyss_rebirth.gui.locked_how", Component.translatable("title.skyabyss_rebirth." + ti.id() + ".how"));
                }
            }
            if (sel) {
                LedgerStage.flag(g, y, ROW);
            } else if (hover) {
                LedgerStage.hover(g, y, ROW);
            }
            FitText.draw(g, font, Component.literal(shown), LX, y + 3, LW, sel ? LedgerStage.ON_PINK : has ? Paper.INK : 0x8A8086, false);
        }
        if (total > ROWS) {
            int barH = ROWS * ROW;
            int knob = Math.max(10, barH * ROWS / total);
            int ky = LIST_Y + (barH - knob) * scroll / Math.max(1, total - ROWS);
            int bx = LedgerStage.INDEX_X + LedgerStage.INDEX_W - 2;
            g.fill(bx, LIST_Y, bx + 1, LIST_Y + barH, Paper.CREAM_LO);
            g.fill(bx - 1, ky, bx + 2, ky + knob, Paper.PINK);
        }
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = fit.x(screenX);
        double mouseY = fit.y(screenY);
        if (button == 0 && mouseX >= LX - 2 && mouseX < LX + LW + 4) {
            int row = (int) ((mouseY - LIST_Y) / ROW);
            if (mouseY >= LIST_Y && row >= 0 && row < ROWS) {
                int idx = scroll + row;
                RebirthData d = RebirthView.data(Minecraft.getInstance().player);
                if (idx == 0) {
                    PacketDistributor.sendToServer(new RebirthNet.SelectTitlePayload(""));
                    return true;
                }
                if (idx - 1 < RebirthTitles.all().size()) {
                    RebirthTitles.Title ti = RebirthTitles.all().get(idx - 1);
                    if (RebirthTitles.has(d.unlocked(), ti)) {
                        PacketDistributor.sendToServer(new RebirthNet.SelectTitlePayload(ti.id()));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double scrollX, double scrollY) {
        double mouseX = fit.x(screenX);
        double mouseY = fit.y(screenY);
        if (mouseX >= LedgerStage.INDEX_X - 12) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, LedgerStage.W, LedgerStage.H);
    }
}
