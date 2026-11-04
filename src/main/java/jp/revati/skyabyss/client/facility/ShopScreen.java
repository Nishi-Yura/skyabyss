package jp.revati.skyabyss.client.facility;

import java.util.List;

import jp.revati.skyabyss.facility.FacilityNetwork;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 都の店の画面 (docs/design/35 の 3.1・3.3、00 の 223)。バニラの取引の画面は使わない。
// 板の上に見出し (店の名前) と手持ちの鐘銭、つまみ (買う / 売る)、品の行。行の右に値段の札 (鐘銭と素材。足りない数は赤)。
//   買う: 行を押すと 1 回ぶん、シフトを押しながらで 8 回ぶん。章が届かない品は伏せ、日に限りのある品は「残り」を出す。日替わりの品には印
//   売る: 行を押すと一口、シフトで手持ちの全部
// 決めるのはサーバー (ShopService)。押すたびに、新しい並びが届いて描き直す。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 見出しの帯に店の名前・つまみ・手持ちの銭、中身は品の一覧 (一行 26 = 2 行ぶん、6 行)、下の端に手引き
public class ShopScreen extends Screen {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    private static final int W = Golden.L_W;
    private static final int ROW_H = 2 * Golden.ROW;
    // 一覧の欄 (中身の上から) と手引きの行 (中身の下の端)
    private static final Golden.Box LIST = LAYOUT.body().top(LAYOUT.body().h() - Golden.ROW - Golden.S5);
    private static final int ROWS = (LIST.h() - 4) / ROW_H;
    private static final int TAB_W = Golden.S55;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private FacilityNetwork.ShopView view;
    private boolean selling;
    private int scroll;
    private int x0, y0, h;
    private boolean closedByServer;

    public ShopScreen(FacilityNetwork.ShopView view) {
        super(Component.translatable(view.title()));
        this.view = view;
        this.selling = sellOnly();
    }

    // 封切り師の買い取り: 売るだけの店 (facility/Buyback)
    private boolean sellOnly() {
        return jp.revati.skyabyss.facility.Buyback.ID.equals(view.shop());
    }

    public String shop() {
        return view.shop();
    }

    public void update(FacilityNetwork.ShopView v) {
        this.view = v;
        if (sellOnly()) {
            selling = true;
        } else if (v.sells().isEmpty()) {
            selling = false;
        }
        clampScroll();
    }

    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        clampScroll();
        h = Golden.L_H;
        x0 = 0;
        y0 = 0;
    }

    // つまみの左端 (見出しの帯の右、銭の左)
    private int tabX(int i) {
        int coinsW = 18 + Minecraft.getInstance().font.width(Integer.toString(view.coins()));
        int n = sellOnly() || view.sells().isEmpty() ? 1 : 2;
        return LAYOUT.head().right() - Golden.S5 - coinsW - Golden.S13 - n * TAB_W - (n - 1) * Golden.S3 + i * (TAB_W + Golden.S3);
    }

    private int tabY() {
        return LAYOUT.head().y() + 4;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!closedByServer && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new FacilityNetwork.ShopClosed());
        }
    }

    private int rowCount() {
        return selling ? view.sells().size() : view.goods().size();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, rowCount() - ROWS));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Font font = Minecraft.getInstance().font;
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        int hy = Golden.textY(head.y(), head.h());
        // 見出しと手持ちの銭
        FacilityUi.gem(g, head.x() + 4, head.y() + head.h() / 2);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, title, head.x() + 10, hy, tabX(0) - head.x() - 10 - Golden.S8, FacilityUi.TEXT, false);
        String coins = Integer.toString(view.coins());
        int cx = head.right() - Golden.S5 - font.width(coins);
        g.drawString(font, coins, cx, hy, FacilityUi.GOLD, false);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), cx - 18, head.y() + (head.h() - 16) / 2);
        // つまみ
        if (sellOnly()) {
            tab(g, font, tabX(0), tabY(), Component.translatable("gui.skyabyss.buyback.tab"), true, mouseX, mouseY);
        } else {
            tab(g, font, tabX(0), tabY(), Component.translatable("gui.skyabyss.shop.buy"), !selling, mouseX, mouseY);
        }
        if (!sellOnly() && !view.sells().isEmpty()) {
            tab(g, font, tabX(1), tabY(), Component.translatable("gui.skyabyss.shop.sell"), selling, mouseX, mouseY);
        }
        // 一覧
        int lx = LIST.x() + 1;
        int ly = LIST.y() + 2;
        int lw = LIST.w() - 2;
        Golden.recess(g, LIST);
        ItemStack hoverStack = ItemStack.EMPTY;
        int n = rowCount();
        if (n == 0) {
            g.drawCenteredString(font, Component.translatable(sellOnly() ? "gui.skyabyss.buyback.empty" : selling ? "gui.skyabyss.shop.nothing_bought" : "gui.skyabyss.shop.empty"),
                    lx + lw / 2, ly + ROWS * ROW_H / 2 - 4, FacilityUi.DIM);
        }
        for (int i = 0; i < ROWS && i + scroll < n; i++) {
            int ry = ly + i * ROW_H;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= ry && mouseY < ry + ROW_H;
            if (hover) {
                g.fill(lx, ry, lx + lw, ry + ROW_H, FacilityUi.ROW_HOVER);
            } else if ((i + scroll) % 2 == 1) {
                g.fill(lx, ry, lx + lw, ry + ROW_H, FacilityUi.ROW);
            }
            ItemStack shown = selling ? sellRow(g, font, view.sells().get(i + scroll), lx, ry, lw) : goodRow(g, font, view.goods().get(i + scroll), lx, ry, lw);
            if (hover && mouseX < lx + 24) {
                hoverStack = shown;
            }
        }
        if (n > ROWS) {
            int barH = Math.max(12, ROWS * ROW_H * ROWS / n);
            int barY = ly + (ROWS * ROW_H - barH) * scroll / Math.max(1, n - ROWS);
            g.fill(lx + lw - 3, barY, lx + lw - 1, barY + barH, FacilityUi.TEAL);
        }
        Golden.Box body = LAYOUT.body();
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(sellOnly() ? "gui.skyabyss.buyback.hint" : selling ? "gui.skyabyss.shop.hint_sell" : "gui.skyabyss.shop.hint_buy"),
                body.x() + Golden.S5, Golden.textY(body.bottom() - Golden.ROW, Golden.ROW), body.w() - Golden.S13, 0xFF000000 | Golden.DIM, false);
        fit.end(g);
        if (!hoverStack.isEmpty()) {
            g.renderTooltip(font, hoverStack, screenX, screenY);
        }
    }

    private void tab(GuiGraphics g, Font font, int x, int y, Component label, boolean on, int mx, int my) {
        boolean hover = mx >= x && mx < x + TAB_W && my >= y && my < y + Golden.BTN;
        Golden.tab(g, font, new Golden.Box(x, y, TAB_W, Golden.BTN), label, on, hover);
    }

    private ItemStack goodRow(GuiGraphics g, Font font, FacilityNetwork.GoodView v, int x, int y, int w) {
        g.renderItem(v.stack(), x + Golden.S5, y + 5);
        g.renderItemDecorations(font, v.stack(), x + Golden.S5, y + 5);
        Component name = v.locked() ? Component.translatable("gui.skyabyss.shop.locked", v.chapter()) : v.stack().getHoverName();
        // 値段の札の幅 (名前はその手前まで。長い名前が札に重ならないように)
        int tw = 6 + (v.coins() > 0 ? 18 + font.width(Integer.toString(v.coins())) + 4 : 0);
        for (ItemStack m : v.materials()) {
            tw += 18 + font.width(Integer.toString(m.getCount())) + 4;
        }
        int textW = (v.locked() ? w - 8 : w - 6 - tw - 4) - 26;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, name, x + 26, Golden.textY(y, Golden.ROW), textW, v.locked() ? FacilityUi.DIM : FacilityUi.TEXT, false);
        // 下の段: 日替わり・残り
        StringBuilder sub = new StringBuilder();
        if (v.rotating()) {
            sub.append(Component.translatable("gui.skyabyss.shop.daily").getString());
        }
        if (v.left() >= 0) {
            if (!sub.isEmpty()) {
                sub.append("  ");
            }
            sub.append(Component.translatable("gui.skyabyss.shop.left", v.left()).getString());
        }
        if (!sub.isEmpty()) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(sub.toString()), x + 26, Golden.textY(y + Golden.ROW, Golden.ROW), textW, v.left() == 0 ? FacilityUi.LACK : FacilityUi.TEAL, false);
        }
        if (v.locked()) {
            return ItemStack.EMPTY;
        }
        // 値段の札 (右寄せ): 銭 n、素材 n (足りなければ赤)
        int tx = x + w - 6 - tw;
        FacilityUi.tag(g, tx, y + 3, tw, ROW_H - 6);
        int px = tx + 3;
        if (v.coins() > 0) {
            g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), px, y + 5);
            String s = Integer.toString(v.coins());
            g.drawString(font, s, px + 17, Golden.textY(y, ROW_H), view.coins() >= v.coins() ? FacilityUi.GOLD : FacilityUi.LACK, false);
            px += 18 + font.width(s) + 4;
        }
        for (int i = 0; i < v.materials().size(); i++) {
            ItemStack m = v.materials().get(i);
            g.renderItem(new ItemStack(m.getItem()), px, y + 5);
            String s = Integer.toString(m.getCount());
            g.drawString(font, s, px + 17, Golden.textY(y, ROW_H), v.have().get(i) >= m.getCount() ? FacilityUi.TEXT : FacilityUi.LACK, false);
            px += 18 + font.width(s) + 4;
        }
        return v.stack();
    }

    private ItemStack sellRow(GuiGraphics g, Font font, FacilityNetwork.SellView v, int x, int y, int w) {
        g.renderItem(v.item(), x + Golden.S5, y + 5);
        g.renderItemDecorations(font, v.item(), x + Golden.S5, y + 5);
        String s = Integer.toString(v.coins());
        int tw = 6 + 18 + font.width(s) + 4;
        int textW = w - 6 - tw - 4 - 26;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, v.item().getHoverName(), x + 26, Golden.textY(y, Golden.ROW), textW, FacilityUi.TEXT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.shop.have", v.have()), x + 26, Golden.textY(y + Golden.ROW, Golden.ROW), textW,
                v.have() >= v.item().getCount() ? FacilityUi.TEAL : FacilityUi.LACK, false);
        int tx = x + w - 6 - tw;
        FacilityUi.tag(g, tx, y + 3, tw, ROW_H - 6);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), tx + 3, y + 5);
        g.drawString(font, s, tx + 20, Golden.textY(y, ROW_H), FacilityUi.GOLD, false);
        return v.item();
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (button == 0) {
            if (!sellOnly() && my >= tabY() && my < tabY() + Golden.BTN) {
                if (mx >= tabX(0) && mx < tabX(0) + TAB_W) {
                    setTab(false);
                    return true;
                }
                if (!view.sells().isEmpty() && mx >= tabX(1) && mx < tabX(1) + TAB_W) {
                    setTab(true);
                    return true;
                }
            }
            int lx = LIST.x() + 1;
            int ly = LIST.y() + 2;
            if (mx >= lx && mx < lx + LIST.w() - 2 && my >= ly && my < ly + ROWS * ROW_H) {
                int i = (int) ((my - ly) / ROW_H) + scroll;
                if (i < rowCount()) {
                    boolean shift = hasShiftDown();
                    if (selling) {
                        FacilityNetwork.SellView v = view.sells().get(i);
                        PacketDistributor.sendToServer(new FacilityNetwork.ShopSell(view.shop(), v.index(), shift ? 999 : 1));
                    } else {
                        FacilityNetwork.GoodView v = view.goods().get(i);
                        if (!v.locked()) {
                            PacketDistributor.sendToServer(new FacilityNetwork.ShopBuy(view.shop(), v.id(), shift ? 8 : 1));
                        }
                    }
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
                    return true;
                }
            }
        }
        return super.mouseClicked(sx, sy, button);
    }

    private void setTab(boolean sell) {
        if (selling != sell) {
            selling = sell;
            scroll = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.5f));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy);
        clampScroll();
        return true;
    }

    // 自己テスト・撮影用
    public List<FacilityNetwork.GoodView> goods() {
        return view.goods();
    }

    public void showSell(boolean sell) {
        selling = sell && !view.sells().isEmpty();
    }
}
