package jp.revati.skyabyss.client.quest;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.lore.LoreWrap;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.free.Board;
import jp.revati.skyabyss.quest.free.BoardNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// 言伝ての板の画面。今日の頼みごと (日替わり) と、今週の頼みごと (週替わり) を紙の札のように並べる。
// 受けられるものには「受ける」。まだ物語が届かないものは題を伏せる (「？？？」と、何をすれば開くか)。
// 状態はすべてサーバーが決めて送る (BoardNetwork.Open)。押すとサーバーが確かめ直して、新しい中身を送り返す
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 見出しの帯に題と今日の数、一行目に次の張り替え、その下に札 (一行 34。送れる。受ける釦は札に合わせて動く)、下の段に手引き
public class BoardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box LIST = LAYOUT.body().afterTop(Golden.ROW);
    private static final int ROW_H = Golden.S34;
    private static final int EDGE_IN = 0xFF3A2E1C;
    private static final int PAPER = 0x30F0E2C0;
    private static final int TEAL = 0xFF000000 | Golden.TEAL;
    private static final int GOLD = 0xFF000000 | Golden.GOLD;
    private static final int TEXT = 0xFF000000 | Golden.TEXT;
    private static final int SOFT = 0xFF000000 | Golden.SOFT;
    private static final int DIM = 0xFF000000 | Golden.DIM;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private BoardNetwork.Open data;
    private int w;
    private int left;
    private int top;
    private int height0;
    private final ScrollPanel rows = new ScrollPanel(ROW_H);
    // 受ける釦と、その札の番号 (送りで動かす)
    private final List<Button> accepts = new ArrayList<>();
    private final List<Integer> acceptRow = new ArrayList<>();

    public BoardScreen(BoardNetwork.Open data) {
        super(Component.translatable(data.npc() ? "gui.skyabyss.board.title_npc" : "gui.skyabyss.board.title"));
        this.data = data;
    }

    public static void receive(BoardNetwork.Open payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoardScreen s) {
            s.data = payload;
            s.rebuildWidgets();
        } else if (mc.screen == null || mc.screen instanceof jp.revati.skyabyss.client.npc.DialogueScreen) {
            // NPC が代わりに見せるときは、会話の画面と入れ替える
            mc.setScreen(new BoardScreen(payload));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9f, 0.6f));
        }
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        w = Golden.L_W;
        height0 = Golden.L_H;
        left = 0;
        top = 0;
        rows.setViewport(LIST.x(), LIST.y(), LIST.w(), LIST.h());
        rows.setContentHeight(Math.max(1, data.lines().size()) * ROW_H);
        accepts.clear();
        acceptRow.clear();
        for (int i = 0; i < data.lines().size(); i++) {
            Board.Line line = data.lines().get(i);
            if (line.state() == Board.OPEN) {
                Button b = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.board.accept"), x -> {
                    x.active = false;
                    PacketDistributor.sendToServer(new BoardNetwork.Accept(line.quest()));
                }).bounds(LIST.right() - Golden.S8 - Golden.S55 - 4, LIST.y(), Golden.S55, Golden.BTN).build());
                accepts.add(b);
                acceptRow.add(i);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new BoardNetwork.Closed());
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return rows.mouseScrolled(fit.x(mx), fit.y(my), scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return rows.mouseClicked(fit.x(mx), fit.y(my), button) || super.mouseClicked(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return rows.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale())
                || super.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale());
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return rows.mouseReleased(fit.x(mx), fit.y(my), button) || super.mouseReleased(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return rows.keyPressed(key) || super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
    }

    private void drawBoard(GuiGraphics g, int mouseX, int mouseY) {
        hoverTip = null;
        Font font = Minecraft.getInstance().font;
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        int hy = Golden.textY(head.y(), head.h());
        g.drawString(font, title, head.x() + Golden.S5, hy, GOLD, false);
        // 今日受けた数と、次の張り替え
        Component quota = Component.translatable("gui.skyabyss.board.quota", data.used(), data.quota());
        g.drawString(font, quota, head.right() - Golden.S5 - font.width(quota), hy, data.used() >= data.quota() ? SOFT : TEAL, false);
        long minutes = Math.max(1, Math.round(data.until() / 1200.0));
        g.drawString(font, Component.translatable("gui.skyabyss.board.next", minutes), LAYOUT.body().x() + Golden.S5, Golden.textY(LAYOUT.body().y(), Golden.ROW), SOFT, false);
        if (data.lines().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.board.empty"), LIST.cx(), Golden.textY(LIST.y() + 8, Golden.ROW), SOFT);
        }
        rows.setContentHeight(Math.max(1, data.lines().size()) * ROW_H);
        rows.begin(g);
        for (int i = 0; i < data.lines().size(); i++) {
            int y = rows.top() + i * ROW_H;
            if (y + ROW_H < rows.y() || y > rows.y() + rows.height()) {
                continue;
            }
            drawLine(g, font, data.lines().get(i), y, mouseX, mouseY);
        }
        rows.end(g);
        rows.drawBar(g, EDGE_IN, 0x887FD6D0);
        // 受ける釦は、その札の所へ動かす (枠の外のものは隠す)
        for (int k = 0; k < accepts.size(); k++) {
            Button b = accepts.get(k);
            int by = rows.top() + acceptRow.get(k) * ROW_H + (ROW_H - 3 - Golden.BTN) / 2;
            b.setY(by);
            b.visible = by >= rows.y() && by + Golden.BTN <= rows.y() + rows.height();
        }
        Golden.Box foot = LAYOUT.foot();
        g.drawString(font, Component.translatable("gui.skyabyss.board.footer"), foot.x() + Golden.S5, Golden.textY(foot.y(), foot.h()), DIM, false);
    }

    private List<FormattedCharSequence> hoverTip;

    private void drawLine(GuiGraphics g, Font font, Board.Line line, int y, int mouseX, int mouseY) {
        QuestDef def = QuestClient.quest(line.quest());
        boolean locked = line.state() == Board.LOCKED;
        boolean faint = locked || line.state() == Board.CLOSED || line.state() == Board.DONE || line.state() == Board.FULL;
        // 札 (一段くぼんだ欄。左の縁に週替わりは金・日替わりは青緑の帯)
        int cx0 = LIST.x();
        int cx1 = LIST.right() - Golden.S8;
        Golden.recess(g, new Golden.Box(cx0, y, cx1 - cx0, ROW_H - 3));
        if (!faint) {
            g.fill(cx0 + 1, y + 1, cx1 - 1, y + ROW_H - 4, 0x0CF0E2C0);
        }
        g.fill(cx0 + 1, y + 1, cx0 + 3, y + ROW_H - 4, def != null && def.repeat().equals("weekly") ? GOLD : TEAL);
        int textW = cx1 - cx0 - Golden.S8 - Golden.S55 - Golden.S13;
        Component name = locked || def == null ? Component.translatable("gui.skyabyss.board.hidden") : Component.translatable(def.titleKey());
        String tag = def == null ? "" : Component.translatable("gui.skyabyss.quest.repeat." + def.repeat()).getString();
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(name.getString()), cx0 + Golden.S8, Golden.textY(y + 2, Golden.ROW), textW - font.width(tag) - 8, faint ? SOFT : TEXT, false);
        g.drawString(font, tag, cx0 + Golden.S8 + textW - font.width(tag), Golden.textY(y + 2, Golden.ROW), def != null && def.repeat().equals("weekly") ? GOLD : TEAL, false);
        // 二行目: 何をするか (伏せたものは、何をすれば開くか)
        String second;
        if (locked && def != null) {
            second = Component.translatable("gui.skyabyss.quest.locked." + def.minStage()).getString();
        } else if (line.state() == Board.CLOSED && def != null) {
            second = Component.translatable(QuestDef.keyBase(def.id()) + ".unlock_hint").getString();
        } else {
            second = def == null ? "" : Component.translatable(def.summaryKey()).getString();
        }
        // 右下に「仲間も N 人」を出すときは、そのぶん二行目を短く切る
        int secondW = line.mates() > 0 ? textW - 30 : textW;
        // 入らない文は横へ流す (Marquee。前は「…」で切っていた)
        jp.revati.skyabyss.client.ui.Marquee.draw(g, font, second, cx0 + Golden.S8, Golden.textY(y + 2 + Golden.ROW, Golden.ROW), secondW, faint ? DIM : SOFT, false);
        // 右: 状態 (受けられるものは釦)
        String state = switch (line.state()) {
            case Board.ACTIVE -> "gui.skyabyss.board.state.active";
            case Board.DONE -> "gui.skyabyss.board.state.done";
            case Board.FULL -> "gui.skyabyss.board.state.full";
            case Board.LOCKED, Board.CLOSED -> "gui.skyabyss.board.state.locked";
            default -> null;
        };
        if (state != null) {
            Component s = Component.translatable(state);
            g.drawString(font, s, cx1 - Golden.S8 - font.width(s), Golden.textY(y + 2, Golden.ROW), line.state() == Board.ACTIVE ? TEAL : SOFT, false);
        }
        if (line.mates() > 0) {
            Component m = Component.translatable("gui.skyabyss.board.mates", line.mates());
            g.drawString(font, m, cx1 - Golden.S8 - font.width(m), Golden.textY(y + 2 + Golden.ROW, Golden.ROW), TEAL, false);
        }
        // 札に重ねると、全文と報酬
        if (!locked && def != null && mouseX >= cx0 && mouseX < cx1 - Golden.S55 - Golden.S13 && mouseY >= y && mouseY < y + ROW_H - 3 && rows.contains(mouseX, mouseY)) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(Component.translatable(def.titleKey()).getVisualOrderText());
            for (String l : LoreWrap.wrap(Component.translatable(def.summaryKey()).getString(), 200, font::width)) {
                tip.add(Component.literal(l).withColor(0xC8C0A8).getVisualOrderText());
            }
            String reward = QuestLogScreen.rewardText(def);
            if (!reward.isEmpty()) {
                for (String l : LoreWrap.wrap(Component.translatable("gui.skyabyss.quest.reward", reward).getString(), 200, font::width)) {
                    tip.add(Component.literal(l).withColor(GOLD).getVisualOrderText());
                }
            }
            hoverTip = tip;
        }
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        drawBoard(g, mouseX, mouseY);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        fit.end(g);
        if (hoverTip != null) {
            g.renderTooltip(Minecraft.getInstance().font, hoverTip, screenX, screenY);
        }
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, Golden.L_W, Golden.L_H);
    }
}
