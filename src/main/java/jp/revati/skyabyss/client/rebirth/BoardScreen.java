package jp.revati.skyabyss.client.rebirth;

import java.util.List;

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
public final class BoardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int W = 330;
    private static final int H = 196;
    private static final int TEXT = 0xFFE9EDF0;
    private static final int SOFT = 0xFF9AA7AE;
    private static final int GOLD = 0xFFE6B84A;
    private static final int[] PODIUM = {0xFFFFD45A, 0xFFD6DDE2, 0xFFD39A64};

    private final RebirthNet.BoardPayload data;
    private boolean hidden;
    private int left;
    private int top;

    BoardScreen(RebirthNet.BoardPayload data) {
        super(Component.translatable(data.kind() == 0 ? "skyabyss_rebirth.board.title" : "skyabyss_rebirth.records.title"));
        this.data = data;
        this.hidden = data.hidden();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (data.kind() == 0) {
            addRenderableWidget(Button.builder(label(), b -> {
                hidden = !hidden;
                PacketDistributor.sendToServer(new RebirthNet.HiddenPayload(hidden));
                b.setMessage(label());
            }).bounds(left, top + H - 20, 150, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 80, top + H - 20, 80, 16).build());
    }

    private Component label() {
        return Component.translatable("skyabyss_rebirth.gui.toggle.board", Component.translatable(hidden ? "options.off" : "options.on"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 6, top - 6, left + W + 6, top + H + 6, 0xC0101418);
        g.fill(left - 5, top - 5, left + W + 5, top - 4, 0x60FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left, top, GOLD, false);
        if (data.kind() == 0) {
            renderLeaderboard(g);
        } else {
            renderRecords(g);
        }
    }

    private void renderLeaderboard(GuiGraphics g) {
        int colW = W / 3;
        for (int c = 0; c < Math.min(3, data.lists().size()); c++) {
            int x = left + c * colW;
            g.drawString(font, Component.translatable("skyabyss_rebirth.board.metric." + c), x, top + 16, SOFT, false);
            List<RebirthNet.Line> lines = data.lists().get(c);
            if (lines.isEmpty()) {
                g.drawString(font, Component.translatable("skyabyss_rebirth.board.empty"), x, top + 32, 0xFF5F6A70, false);
                continue;
            }
            int y = top + 30;
            for (int i = 0; i < Math.min(8, lines.size()); i++) {
                RebirthNet.Line l = lines.get(i);
                Component value = value(c, l);
                if (i < 3) {
                    // 上位 3 人は大きく (倍率 1.5。名前は段の色の記号つき)
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0);
                    g.pose().scale(1.5f, 1.5f, 1f);
                    g.drawString(font, (i + 1) + " " + font.plainSubstrByWidth(l.name(), 52), 0, 0, PODIUM[i], false);
                    g.pose().popPose();
                    g.drawString(font, RebirthText.symbols(l.rebirths(), RebirthClient.timeMs(), RebirthClient.still()).append(" ").append(value), x + 8, y + 12, TEXT, false);
                    y += 28;
                } else {
                    g.drawString(font, font.plainSubstrByWidth((i + 1) + ". " + l.name(), colW - 50), x, y, SOFT, false);
                    g.drawString(font, value, x + colW - 8 - font.width(value), y, SOFT, false);
                    y += 10;
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
        int y = top + 24;
        for (RebirthNet.RecordLine r : data.records()) {
            int h = 34;
            if (r.fresh()) {
                // 最近更新: 光る縁 (呼吸のような明るさ。動きを減らす設定では一定)
                double wave = RebirthClient.still() ? 1.0 : 0.6 + 0.4 * Math.sin(RebirthClient.timeMs() / 350.0);
                int a = (int) (200 * wave);
                g.fill(left - 3, y - 3, left + W + 3, y + h - 1, (a << 24) | 0xE6B84A);
                g.fill(left - 2, y - 2, left + W + 2, y + h - 2, 0xFF101418);
            }
            g.drawString(font, r.titan(), left + 4, y + 2, r.here() ? GOLD : TEXT, false);
            if (r.ticks() < 0) {
                g.drawString(font, Component.translatable("skyabyss_rebirth.records.none"), left + 4, y + 16, 0xFF5F6A70, false);
            } else {
                g.drawString(font, Component.literal(RebirthLeaderboard.time(r.ticks())), left + 4, y + 16, TEXT, false);
                g.drawString(font, Component.translatable("skyabyss_rebirth.records.by", r.who()), left + 80, y + 16, SOFT, false);
                if (r.fresh()) {
                    g.drawString(font, Component.translatable("skyabyss_rebirth.records.new"), left + W - 8 - font.width(Component.translatable("skyabyss_rebirth.records.new")), y + 2, GOLD, false);
                }
            }
            y += h + 2;
        }
        g.drawString(font, Component.translatable("skyabyss_rebirth.records.note"), left, top + H - 14, 0xFF5F6A70, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        return new int[] {left - 6, top - 6, W + 12, H + 12};
    }
}
