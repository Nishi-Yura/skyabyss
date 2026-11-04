package jp.revati.skyabyss.client.depths;

import java.util.List;

import jp.revati.skyabyss.story.ch5.depths.DepthRankPayload;
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// 深みの順位の石の画面 (docs/design/36 の 9.6)。脇の列 (左) に組ごと、主の列 (右) に一人ごとの上位 5 (上位 3 は二行で順位の色)。
// 下の段に見る人の組と人の最深と、次の節目まで。板は黄金比の大きい板 (client/ui/Golden。docs/design/56)。転生の順位の石 (client/rebirth/BoardScreen) と同じ作り
public final class DepthRankScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT2);
    private static final Golden.Box GROUPS = LAYOUT.body().minor(true);
    private static final Golden.Box PLAYERS = LAYOUT.body().major(false);
    private static final int ROW = Golden.ROW;
    private static final int TEXT = 0xFFE9EDF0;
    private static final int SOFT = 0xFF000000 | Golden.SOFT;
    private static final int DIM = 0xFF000000 | Golden.DIM;
    private static final int GOLD = 0xFF000000 | Golden.GOLD;
    private static final int DEEP = 0xFF7FD6C8;
    private static final int[] PODIUM = {0xFFFFD45A, 0xFFD6DDE2, 0xFFD39A64};

    private final DepthRankPayload data;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

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
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.recess(g, GROUPS);
        Golden.recess(g, PLAYERS);
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), GOLD, false);
        column(g, GROUPS, Component.translatable("message.skyabyss.depths.rank_group"), data.groups());
        column(g, PLAYERS, Component.translatable("message.skyabyss.depths.rank_player"), data.players());
        // 自分の記録と次の節目 (下の段)
        Golden.Box foot = LAYOUT.foot();
        int fx = foot.x() + Golden.S5;
        g.drawString(font, Component.translatable("message.skyabyss.depths.rank_mine",
                data.ours() == 0 ? Component.translatable("message.skyabyss.depths.rank_dash") : Component.literal(Integer.toString(data.ours())),
                data.mine() == 0 ? Component.translatable("message.skyabyss.depths.rank_dash") : Component.literal(Integer.toString(data.mine()))),
                fx, Golden.textY(foot.y() + 4, ROW), TEXT, false);
        Component next = data.next() == 0 ? Component.translatable("message.skyabyss.depths.rank_next_all")
                : Component.translatable("message.skyabyss.depths.rank_next", data.next());
        g.drawString(font, next, fx, Golden.textY(foot.y() + 4 + ROW, ROW), SOFT, false);
        fit.end(g);
    }

    // 一つの表: 見出し、上位 3 は二行 (順位の色の名前と深さ)、4 位からは一行
    private void column(GuiGraphics g, Golden.Box box, Component head, List<DepthRankPayload.Line> lines) {
        int x = box.x() + Golden.S8;
        int colW = box.w() - 2 * Golden.S8;
        int y = box.y() + 2;
        g.drawString(font, head, x, Golden.textY(y, ROW), SOFT, false);
        y += ROW + Golden.S3;
        if (lines.isEmpty()) {
            g.drawString(font, Component.translatable("message.skyabyss.depths.rank_none"), x, Golden.textY(y, ROW), DIM, false);
            return;
        }
        for (int i = 0; i < lines.size() && y + ROW <= box.bottom() - 2; i++) {
            DepthRankPayload.Line l = lines.get(i);
            Component depth = Component.translatable("message.skyabyss.depths.rank_depth", l.depth());
            if (i < 3) {
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal((i + 1) + "  " + l.name()), x, Golden.textY(y, ROW), colW, PODIUM[i], true);
                g.drawString(font, depth, x + Golden.S13, Golden.textY(y + ROW, ROW), DEEP, false);
                y += 2 * ROW;
            } else {
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal((i + 1) + ". " + l.name()), x, Golden.textY(y, ROW), colW - font.width(depth) - Golden.S5, SOFT, false);
                g.drawString(font, depth, x + colW - font.width(depth), Golden.textY(y, ROW), SOFT, false);
                y += ROW;
            }
        }
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
