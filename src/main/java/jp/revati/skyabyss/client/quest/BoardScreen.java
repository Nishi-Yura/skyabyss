package jp.revati.skyabyss.client.quest;

import java.util.ArrayList;
import java.util.List;

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
// 2026-10-22 の部屋の見直し: 窓は画面に合わせて広がり、札の行は目盛りつきで送る (受ける釦は札に合わせて動く)
public class BoardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_W = 360;
    private static final int ROW_H = 34;
    private static final int PANEL = 0xFA15120E;
    private static final int EDGE = 0xFF6B5534;
    private static final int EDGE_IN = 0xFF3A2E1C;
    private static final int PAPER = 0x30F0E2C0;
    private static final int TEAL = 0xFF7FD6D0;
    private static final int GOLD = 0xFFF2C14E;
    private static final int TEXT = 0xFFE9E3D4;
    private static final int SOFT = 0xFF9C9480;
    private static final int DIM = 0xFF5F5848;

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
        w = Math.min(MAX_W, width - 16);
        // 札は画面の高さに収まるだけ見せる (残りは送る)
        int maxRows = Math.max(1, (height - 16 - 46 - 22) / ROW_H);
        height0 = 46 + Math.min(Math.max(1, data.lines().size()), maxRows) * ROW_H + 22;
        left = (width - w) / 2;
        top = Math.max(4, (height - height0) / 2);
        rows.setViewport(left + 8, top + 40, w - 16, height0 - 40 - 22);
        rows.setContentHeight(Math.max(1, data.lines().size()) * ROW_H);
        accepts.clear();
        acceptRow.clear();
        for (int i = 0; i < data.lines().size(); i++) {
            Board.Line line = data.lines().get(i);
            if (line.state() == Board.OPEN) {
                Button b = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.board.accept"), x -> {
                    x.active = false;
                    PacketDistributor.sendToServer(new BoardNetwork.Accept(line.quest()));
                }).bounds(left + w - 62, top + 46, 52, 16).build());
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
        return rows.mouseScrolled(mx, my, scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return rows.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
        hoverTip = null;
        Font font = Minecraft.getInstance().font;
        g.fill(left, top, left + w, top + height0, PANEL);
        g.renderOutline(left, top, w, height0, EDGE);
        g.renderOutline(left + 2, top + 2, w - 4, height0 - 4, EDGE_IN);
        g.drawString(font, title, left + 10, top + 8, GOLD, false);
        // 今日受けた数と、次の張り替え
        Component quota = Component.translatable("gui.skyabyss.board.quota", data.used(), data.quota());
        g.drawString(font, quota, left + w - 10 - font.width(quota), top + 8, data.used() >= data.quota() ? SOFT : TEAL, false);
        long minutes = Math.max(1, Math.round(data.until() / 1200.0));
        g.drawString(font, Component.translatable("gui.skyabyss.board.next", minutes), left + 10, top + 22, SOFT, false);
        g.fill(left + 8, top + 36, left + w - 8, top + 37, EDGE_IN);
        if (data.lines().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.board.empty"), left + w / 2, top + 52, SOFT);
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
            int by = rows.top() + acceptRow.get(k) * ROW_H + 6;
            b.setY(by);
            b.visible = by >= rows.y() && by + 16 <= rows.y() + rows.height();
        }
        g.drawString(font, Component.translatable("gui.skyabyss.board.footer"), left + 10, top + height0 - 15, DIM, false);
    }

    private List<FormattedCharSequence> hoverTip;

    private void drawLine(GuiGraphics g, Font font, Board.Line line, int y, int mouseX, int mouseY) {
        QuestDef def = QuestClient.quest(line.quest());
        boolean locked = line.state() == Board.LOCKED;
        boolean faint = locked || line.state() == Board.CLOSED || line.state() == Board.DONE || line.state() == Board.FULL;
        // 紙の札
        g.fill(left + 8, y, left + w - 8, y + ROW_H - 3, faint ? 0x14F0E2C0 : PAPER);
        g.fill(left + 8, y, left + 10, y + ROW_H - 3, def != null && def.repeat().equals("weekly") ? GOLD : TEAL);
        int textW = w - 90;
        Component name = locked || def == null ? Component.translatable("gui.skyabyss.board.hidden") : Component.translatable(def.titleKey());
        String tag = def == null ? "" : Component.translatable("gui.skyabyss.quest.repeat." + def.repeat()).getString();
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(name.getString()), left + 14, y + 4, textW - font.width(tag) - 8, faint ? SOFT : TEXT, false);
        g.drawString(font, tag, left + 14 + textW - font.width(tag), y + 4, def != null && def.repeat().equals("weekly") ? GOLD : TEAL, false);
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
        String cut = font.plainSubstrByWidth(second, secondW);
        if (cut.length() < second.length()) {
            cut = font.plainSubstrByWidth(second, secondW - font.width("…")) + "…";
        }
        g.drawString(font, cut, left + 14, y + 15, faint ? DIM : SOFT, false);
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
            g.drawString(font, s, left + w - 14 - font.width(s), y + 4, line.state() == Board.ACTIVE ? TEAL : SOFT, false);
        }
        if (line.mates() > 0) {
            Component m = Component.translatable("gui.skyabyss.board.mates", line.mates());
            g.drawString(font, m, left + w - 14 - font.width(m), y + 16, TEAL, false);
        }
        // 札に重ねると、全文と報酬
        if (!locked && def != null && mouseX >= left + 8 && mouseX < left + w - 70 && mouseY >= y && mouseY < y + ROW_H - 3 && rows.contains(mouseX, mouseY)) {
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (hoverTip != null) {
            g.renderTooltip(Minecraft.getInstance().font, hoverTip, mouseX, mouseY);
        }
    }

    @Override
    public int[] textBounds() {
        return new int[] {left, top, w, height0};
    }
}
