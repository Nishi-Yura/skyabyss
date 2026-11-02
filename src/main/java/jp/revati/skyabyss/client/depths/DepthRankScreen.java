package jp.revati.skyabyss.client.depths;

import java.util.List;

import jp.revati.skyabyss.story.ch5.depths.DepthRankPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// 深みの順位の石の画面 (docs/design/36 の 9.6)。左に組ごと、右に一人ごとの上位 5 (上位 3 は大きく、順位の色)。
// 下に見る人の組と人の最深と、次の節目まで。転生の順位の石 (client/rebirth/BoardScreen) と同じ作り
public final class DepthRankScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_W = 380;
    private static final int MAX_H = 220;
    private static final int TEXT = 0xFFE9EDF0;
    private static final int SOFT = 0xFF9AA7AE;
    private static final int DIM = 0xFF5F6A70;
    private static final int GOLD = 0xFFE6B84A;
    private static final int DEEP = 0xFF7FD6C8;
    private static final int[] PODIUM = {0xFFFFD45A, 0xFFD6DDE2, 0xFFD39A64};

    private final DepthRankPayload data;
    private int w;
    private int h;
    private int left;
    private int top;

    DepthRankScreen(DepthRankPayload data) {
        super(Component.translatable("message.skyabyss.depths.rank_title"));
        this.data = data;
    }

    public static void open(DepthRankPayload p) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new DepthRankScreen(p)));
    }

    @Override
    protected void init() {
        w = Math.min(MAX_W, width - 16);
        h = Math.min(MAX_H, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 80, top + h - 20, 80, 16).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 6, top - 6, left + w + 6, top + h + 6, 0xC00C1214);
        g.fill(left - 5, top - 5, left + w + 5, top - 4, 0x607FD6C8);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left, top, GOLD, false);
        int colW = w / 2;
        column(g, left, colW, Component.translatable("message.skyabyss.depths.rank_group"), data.groups());
        column(g, left + colW, colW, Component.translatable("message.skyabyss.depths.rank_player"), data.players());
        // 自分の記録と次の節目
        int y = top + h - 44;
        g.fill(left, y - 4, left + w, y - 3, 0x307FD6C8);
        g.drawString(font, Component.translatable("message.skyabyss.depths.rank_mine",
                data.ours() == 0 ? Component.translatable("message.skyabyss.depths.rank_dash") : Component.literal(Integer.toString(data.ours())),
                data.mine() == 0 ? Component.translatable("message.skyabyss.depths.rank_dash") : Component.literal(Integer.toString(data.mine()))), left, y, TEXT, false);
        Component next = data.next() == 0 ? Component.translatable("message.skyabyss.depths.rank_next_all")
                : Component.translatable("message.skyabyss.depths.rank_next", data.next());
        g.drawString(font, next, left, y + 12, SOFT, false);
    }

    private void column(GuiGraphics g, int x, int colW, Component head, List<DepthRankPayload.Line> lines) {
        g.drawString(font, head, x, top + 16, SOFT, false);
        if (lines.isEmpty()) {
            g.drawString(font, Component.translatable("message.skyabyss.depths.rank_none"), x, top + 32, DIM, false);
            return;
        }
        int y = top + 30;
        for (int i = 0; i < lines.size(); i++) {
            DepthRankPayload.Line l = lines.get(i);
            Component depth = Component.translatable("message.skyabyss.depths.rank_depth", l.depth());
            if (i < 3) {
                // 上位 3 は大きく (倍率 1.4)
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(1.4f, 1.4f, 1f);
                g.drawString(font, (i + 1) + " " + font.plainSubstrByWidth(l.name(), (int) ((colW - 12) / 1.4f) - 10), 0, 0, PODIUM[i], false);
                g.pose().popPose();
                g.drawString(font, depth, x + 10, y + 12, DEEP, false);
                y += 26;
            } else {
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal((i + 1) + ". " + l.name()), x, y, colW - 70, SOFT, false);
                g.drawString(font, depth, x + colW - 10 - font.width(depth), y, SOFT, false);
                y += 11;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        return new int[] {left - 6, top - 6, w + 12, h + 12};
    }
}
