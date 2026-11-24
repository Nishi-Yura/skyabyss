package jp.revati.skyabyss.client.quest;

import java.util.List;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.JpWrap;
import jp.revati.skyabyss.client.ui.Marquee;
import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.ShopStage;
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

// 言伝ての板の画面。今日の頼みごと (日替わり) と、今週の頼みごと (週替わり) を並べる。
// 受けられるものには「受ける」。まだ物語が届かないものは題を伏せる (「？？？」と、何をすれば開くか)。
// 状態はすべてサーバーが決めて送る (BoardNetwork.Open)。押すとサーバーが確かめ直して、新しい中身を送り返す
// 組み方は店の類の「帳面と台」(client/ui/ShopStage。docs/design/56 の 2.1):
//   左の帳面に頼みを一つ一行 (左に週替わり = 桃色・日替わり = 灰の菱、題 …… 状態、二行目に類か開き方)。押して選んだ行は桃色の紙。
//   右の黒い紙に、選んだ頼みの題・類・全文・報酬と「受ける」(主の釦)。右上に板の名前、その下に今日受けた数と次の張り替え
public class BoardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int ROWS = ShopStage.ROWS;
    private static final int BTN_W = 80;
    private static final int BTN_Y = 196;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private BoardNetwork.Open data;
    private int scroll;
    private int focus;
    private Button accept;

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

    private int n() {
        return data.lines().size();
    }

    @Override
    protected void init() {
        fit.fit(width, height, ShopStage.W, ShopStage.H, 3f);
        scroll = Math.max(0, Math.min(scroll, n() - ROWS));
        focus = Math.max(0, Math.min(focus, n() - 1));
        // 受ける (右の列の下。乗せた頼みが受けられるときだけ)
        accept = addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.board.accept"), x -> {
            if (focus < n() && data.lines().get(focus).state() == Board.OPEN) {
                x.active = false;
                PacketDistributor.sendToServer(new BoardNetwork.Accept(data.lines().get(focus).quest()));
            }
        }).bounds(ShopStage.RIGHT_X + ShopStage.RIGHT_W - BTN_W, BTN_Y, BTN_W, Golden.BTN).primary().build());
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
        if (n() > ROWS && scrollY != 0) {
            scroll = Math.max(0, Math.min(n() - ROWS, scroll + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = fit.x(mx);
        double y = fit.y(my);
        int r = ShopStage.rowAt(x, y);
        if (button == 0 && r >= 0 && r + scroll < n()) {
            focus = r + scroll;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    private static boolean weekly(QuestDef def) {
        return def != null && def.repeat().equals("weekly");
    }

    private static String stateKey(int state) {
        return switch (state) {
            case Board.ACTIVE -> "gui.skyabyss.board.state.active";
            case Board.DONE -> "gui.skyabyss.board.state.done";
            case Board.FULL -> "gui.skyabyss.board.state.full";
            case Board.LOCKED, Board.CLOSED -> "gui.skyabyss.board.state.locked";
            default -> null;
        };
    }

    // 二行目: 何をするか (伏せたものは、何をすれば開くか)
    private static String second(Board.Line line, QuestDef def) {
        if (def == null) {
            return "";
        }
        if (line.state() == Board.LOCKED) {
            return Component.translatable("gui.skyabyss.quest.locked." + def.minStage()).getString();
        }
        if (line.state() == Board.CLOSED) {
            return Component.translatable(QuestDef.keyBase(def.id()) + ".unlock_hint").getString();
        }
        return Component.translatable(def.summaryKey()).getString();
    }

    private void drawLine(GuiGraphics g, Font font, Board.Line line, int i, boolean hot) {
        QuestDef def = QuestClient.quest(line.quest());
        boolean locked = line.state() == Board.LOCKED;
        boolean faint = locked || line.state() == Board.CLOSED || line.state() == Board.DONE || line.state() == Board.FULL;
        int y = ShopStage.rowY(i);
        int x = ShopStage.LIST_X + 10;
        int right = ShopStage.LIST_X + ShopStage.LIST_W - 4;
        if (hot) {
            ShopStage.hot(g, i);
        }
        // 週替わり = 桃色の菱、日替わり = 灰の菱
        Paper.pin(g, ShopStage.LIST_X + 4, y + 6, 2, weekly(def));
        String st = stateKey(line.state());
        Component state = st == null ? null : Component.translatable(st);
        int sw = state == null ? 0 : font.width(state);
        Component name = Component.literal((locked || def == null ? Component.translatable("gui.skyabyss.board.hidden") : Component.translatable(def.titleKey())).getString());
        int nameW = Math.min(font.width(name), right - x - sw - 16);
        FitText.draw(g, font, name, x, y + 2, nameW, hot ? ShopStage.ON_PINK : faint ? Paper.INK_SOFT : Paper.INK, false);
        if (state != null) {
            ShopStage.leader(g, x + nameW + 4, right - sw - 3, y + 8, hot);
            g.drawString(font, state, right - sw, y + 2, hot ? ShopStage.ON_PINK : line.state() == Board.ACTIVE ? Paper.INK_HEAD : Paper.INK_SOFT, false);
        }
        Component mates = line.mates() > 0 ? Component.translatable("gui.skyabyss.board.mates", line.mates()) : null;
        int mw = mates == null ? 0 : font.width(mates) + 6;
        // 二行目は類 (日替わり・週替わり)。伏せたものは何をすれば開くか。全文は右の列に
        String sub = def == null ? "" : faint && line.state() != Board.DONE && line.state() != Board.FULL ? second(line, def)
                : Component.translatable("gui.skyabyss.quest.repeat." + def.repeat()).getString();
        Marquee.draw(g, font, sub, x, y + 13, right - x - mw, hot ? ShopStage.ON_PINK : Paper.INK_SOFT, false);
        if (mates != null) {
            g.drawString(font, mates, right - mw + 6, y + 13, hot ? ShopStage.ON_PINK : Paper.INK_HEAD, false);
        }
    }

    // 右の列: 乗せた頼みの題・類・全文・報酬
    private void drawDetail(GuiGraphics g, Font font, Board.Line line) {
        QuestDef def = QuestClient.quest(line.quest());
        boolean locked = line.state() == Board.LOCKED || def == null;
        int x = ShopStage.RIGHT_X;
        int w = ShopStage.RIGHT_W;
        int y = 54;
        Component name = locked ? Component.translatable("gui.skyabyss.board.hidden") : Component.translatable(def.titleKey());
        for (FormattedCharSequence l : JpWrap.split(font, name, w)) {
            g.drawString(font, l, x, y, Paper.ON_BLACK_PINK, false);
            y += 11;
        }
        if (def != null) {
            g.drawString(font, Component.translatable("gui.skyabyss.quest.repeat." + def.repeat()), x, y, Paper.ON_BLACK_SOFT, false);
            y += 13;
        }
        g.fill(x, y - 2, x + w, y - 1, Paper.PINK_LO);
        y += 2;
        int bottom = BTN_Y - 4;
        String reward = locked ? "" : QuestLogScreen.rewardText(def);
        List<FormattedCharSequence> rew = reward.isEmpty() ? List.of() : JpWrap.split(font, Component.translatable("gui.skyabyss.quest.reward", reward), w);
        int rewTop = bottom - rew.size() * 11;
        for (FormattedCharSequence l : JpWrap.split(font, Component.literal(second(line, def)), w)) {
            if (y + 9 > rewTop - 3) {
                break;
            }
            g.drawString(font, l, x, y, Golden.TEXT, false);
            y += 11;
        }
        int ry = rewTop;
        for (FormattedCharSequence l : rew) {
            g.drawString(font, l, x, ry, Paper.ON_BLACK_HI, false);
            ry += 11;
        }
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, ShopStage.W, ShopStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        ShopStage.backdrop(g, fit, width, height);
        Font font = Minecraft.getInstance().font;
        // 選びは押して決める (右の釦へ指を動かすあいだに選びが変わらないように)。乗せた行は薄い桃色
        int hoverRow = ShopStage.rowAt(mouseX, mouseY);
        if (hoverRow >= 0 && hoverRow + scroll < n() && hoverRow + scroll != focus) {
            int hy = ShopStage.rowY(hoverRow);
            g.fill(ShopStage.LIST_X - 1, hy, ShopStage.LIST_X + ShopStage.LIST_W + 1, hy + ShopStage.ROW_H - 1, 0x30C48A44);
        }
        if (n() == 0) {
            ShopStage.empty(g, font, Component.translatable("gui.skyabyss.board.empty"));
        }
        for (int i = 0; i < ROWS && i + scroll < n(); i++) {
            drawLine(g, font, data.lines().get(i + scroll), i, i + scroll == focus);
        }
        ShopStage.scroll(g, scroll, n());
        ShopStage.hint(g, font, Component.translatable("gui.skyabyss.board.footer"));
        // 右: 板の名前・今日受けた数・次の張り替え・乗せた頼み
        ShopStage.title(g, font, title);
        Component quota = Component.translatable("gui.skyabyss.board.quota", data.used(), data.quota());
        FitText.draw(g, font, quota, ShopStage.RIGHT_X, 26, ShopStage.RIGHT_W, data.used() >= data.quota() ? Paper.ON_BLACK_DIM : Paper.ON_BLACK_HI, false);
        long minutes = Math.max(1, Math.round(data.until() / 1200.0));
        FitText.draw(g, font, Component.translatable("gui.skyabyss.board.next", minutes), ShopStage.RIGHT_X, 38, ShopStage.RIGHT_W, Paper.ON_BLACK_SOFT, false);
        boolean open = focus < n() && data.lines().get(focus).state() == Board.OPEN;
        if (focus < n()) {
            drawDetail(g, font, data.lines().get(focus));
        }
        accept.visible = open;
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        fit.end(g);
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, ShopStage.W, ShopStage.H);
    }
}
