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
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;

// 称号と飾りの画面 (C04・C23)。左 = 称号の一覧 (解けたものを選ぶ。解けていないものは条件が見える)、右 = 見せ方の切り替え (名札の効果・称号・背中の飾り・光・プリズムを止める・順位表に載せる)。
// 色の段と記号は固定で切れない。記録画面の「称号」から、または /rebirthfx で開く。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 見出しの帯の右に段の見本、主の列 (左) に称号の一覧 (行 13)、脇の列 (右) に見え方の釦 7 つと注。Esc で戻る
public final class RebirthScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    private static final Golden.Box LIST = LAYOUT.body().major(true);
    private static final Golden.Box LOOK = LAYOUT.body().minor(false);
    private static final int W = Golden.L_W;
    private static final int H = Golden.L_H;
    private static final int ROW = Golden.ROW;
    private static final int LIST_Y = LIST.y() + ROW + Golden.S5;
    private static final int ROWS = (LIST.bottom() - 2 - LIST_Y) / ROW;
    private static final int TEXT = 0xFFE9EDF0;
    private static final int SOFT = 0xFF9AA7AE;
    private static final int LOCKED = 0xFF5F6A70;
    private static final int GOLD = 0xFFE6B84A;

    private final Screen parent;
    private int left;
    private int top;
    private int scroll;
    private final List<Button> toggles = new ArrayList<>();

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

    // 板は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)。論理の (8, 8) に置く
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    @Override
    protected void init() {
        fit.fit(width, height, W, H, 3f);
        left = 0;
        top = 0;
        toggles.clear();
        int bx = LOOK.x() + Golden.S5;
        int bw = LOOK.w() - 2 * Golden.S5;
        int y = LOOK.y() + ROW + Golden.S5;
        for (Toggle t : TOGGLES) {
            Button b = addRenderableWidget(Button.builder(label(t), btn -> {
                t.value.set(!RebirthConfig.on(t.value));
                RebirthConfig.CLIENT_SPEC.save();
                btn.setMessage(label(t));
            }).bounds(bx, y, bw, Golden.BTN).build());
            toggles.add(b);
            y += Golden.BTN + Golden.S5;
        }
        addRenderableWidget(Button.builder(boardLabel(), btn -> {
            boolean now = !hidden();
            PacketDistributor.sendToServer(new RebirthNet.HiddenPayload(now));
            // 返事 (同期) を待たず見た目だけ先に替える
            btn.setMessage(boardLabel(now));
        }).bounds(bx, y, bw, Golden.BTN).build());
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
        Golden.recess(g, LIST);
        Golden.recess(g, LOOK);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        renderBody(g, mouseX, mouseY);
        fit.end(g);
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
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), GOLD, false);
        // 段の見本 (色と記号と縁の形。固定)。見出しの帯の右
        Component tier = n > 0 ? RebirthText.symbols(n, t, still).append(" ").append(RebirthText.level(50, n, t, still))
                : Component.translatable("skyabyss_rebirth.gui.no_rebirth");
        int tw = font.width(tier);
        int fx = head.right() - tw - 10;
        RebirthClient.drawFrame(g, fx, head.y() + 4, tw + 10, 14, n);
        g.drawString(font, tier, fx + 5, Golden.textY(head.y() + 4, 14), TEXT, false);
        g.drawString(font, Component.translatable("skyabyss_rebirth.gui.titles", Long.bitCount(d.unlocked()), RebirthTitles.MAX), LIST.x() + Golden.S5,
                Golden.textY(LIST.y() + 2, ROW), SOFT, false);
        g.drawString(font, Component.translatable("skyabyss_rebirth.gui.look"), LOOK.x() + Golden.S5, Golden.textY(LOOK.y() + 2, ROW), SOFT, false);
        int lx = LIST.x() + Golden.S5;
        int lw = LIST.w() - 2 * Golden.S5 - 4;

        // 称号の一覧 (最初の行 = 外す)
        List<RebirthTitles.Title> all = RebirthTitles.all();
        int total = all.size() + 1;
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - ROWS));
        Component tip = null;
        for (int i = 0; i < ROWS && scroll + i < total; i++) {
            int idx = scroll + i;
            int y = LIST_Y + i * ROW;
            boolean hover = mouseX >= LIST.x() && mouseX < LIST.right() && mouseY >= y && mouseY < y + ROW;
            if (idx == 0) {
                boolean sel = d.title().isEmpty();
                g.drawString(font, (sel ? "▶ " : "  ") + Component.translatable("skyabyss_rebirth.gui.no_title").getString(), lx, Golden.textY(y, ROW), sel ? GOLD : hover ? TEXT : SOFT, false);
                continue;
            }
            RebirthTitles.Title ti = all.get(idx - 1);
            boolean has = RebirthTitles.has(d.unlocked(), ti);
            boolean sel = ti.id().equals(d.title());
            Component name = Component.translatable("title.skyabyss_rebirth." + ti.id());
            if (hover) {
                g.fill(LIST.x() + 1, y, LIST.right() - 5, y + ROW, 0x30FFFFFF);
                tip = has ? Component.translatable("title.skyabyss_rebirth." + ti.id() + ".how") : Component.translatable("skyabyss_rebirth.gui.locked_how", Component.translatable("title.skyabyss_rebirth." + ti.id() + ".how"));
            }
            String shown = has ? name.getString() : "？ " + "・".repeat(Math.min(6, name.getString().length()));
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal((sel ? "▶ " : "  ") + shown), lx, Golden.textY(y, ROW), lw, sel ? GOLD : has ? TEXT : LOCKED, false);
        }
        if (total > ROWS) {
            int barH = ROWS * ROW;
            int knob = Math.max(10, barH * ROWS / total);
            int ky = LIST_Y + (barH - knob) * scroll / Math.max(1, total - ROWS);
            g.fill(LIST.right() - 4, LIST_Y, LIST.right() - 2, LIST_Y + barH, 0x30FFFFFF);
            g.fill(LIST.right() - 4, ky, LIST.right() - 2, ky + knob, 0xA0FFFFFF);
        }
        if (tip != null) {
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
        // 設定の説明 (色と記号は固定)
        int ny = LOOK.y() + ROW + Golden.S5 + (TOGGLES.size() + 1) * (Golden.BTN + Golden.S5);
        for (net.minecraft.util.FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("skyabyss_rebirth.gui.fixed"), LOOK.w() - 2 * Golden.S5)) {
            if (ny + ROW > LOOK.bottom()) {
                break;
            }
            g.drawString(font, line, LOOK.x() + Golden.S5, Golden.textY(ny, ROW), LOCKED, false);
            ny += ROW;
        }
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = fit.x(screenX);
        double mouseY = fit.y(screenY);
        if (button == 0 && mouseX >= LIST.x() && mouseX < LIST.right()) {
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
        if (mouseX < LOOK.x()) {
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
        return fit.screenRect(0, 0, W, H);
    }
}
