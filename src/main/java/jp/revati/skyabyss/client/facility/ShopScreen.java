package jp.revati.skyabyss.client.facility;

import java.util.List;

import jp.revati.skyabyss.facility.FacilityNetwork;
import jp.revati.skyabyss.facility.FacilitySetup;
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
// 決めるのはサーバー (ShopService)。押すたびに、新しい並びが届いて描き直す
public class ShopScreen extends Screen {
    // 窓の幅と見せる行の数は、画面に合わせて init で決める (2026-10-22 の部屋の見直し)
    private int W = 300;
    private static final int ROW_H = 22;
    private int ROWS = 7;

    private FacilityNetwork.ShopView view;
    private boolean selling;
    private int scroll;
    private int x0, y0, h;
    private boolean closedByServer;

    public ShopScreen(FacilityNetwork.ShopView view) {
        super(Component.translatable(view.title()));
        this.view = view;
    }

    public String shop() {
        return view.shop();
    }

    public void update(FacilityNetwork.ShopView v) {
        this.view = v;
        if (v.sells().isEmpty()) {
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
        W = Math.min(380, width - 16);
        ROWS = Math.max(5, Math.min(16, (height - 120) / ROW_H));
        clampScroll();
        h = 46 + ROWS * ROW_H + 22;
        x0 = (width - W) / 2;
        y0 = (height - h) / 2;
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        FacilityUi.panel(g, x0, y0, W, h);
        // 見出しと手持ちの銭
        FacilityUi.gem(g, x0 + 12, y0 + 13);
        g.drawString(font, title, x0 + 18, y0 + 9, FacilityUi.TEXT, false);
        String coins = Integer.toString(view.coins());
        int cx = x0 + W - 14 - font.width(coins);
        g.drawString(font, coins, cx, y0 + 9, FacilityUi.GOLD, false);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), cx - 18, y0 + 5);
        // つまみ
        tab(g, font, x0 + 12, y0 + 24, Component.translatable("gui.skyabyss.shop.buy"), !selling, mouseX, mouseY);
        if (!view.sells().isEmpty()) {
            tab(g, font, x0 + 70, y0 + 24, Component.translatable("gui.skyabyss.shop.sell"), selling, mouseX, mouseY);
        }
        // 一覧
        int lx = x0 + 10;
        int ly = y0 + 40;
        int lw = W - 20;
        FacilityUi.well(g, lx, ly, lw, ROWS * ROW_H);
        ItemStack hoverStack = ItemStack.EMPTY;
        int n = rowCount();
        if (n == 0) {
            g.drawCenteredString(font, Component.translatable(selling ? "gui.skyabyss.shop.nothing_bought" : "gui.skyabyss.shop.empty"),
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
        g.drawString(font, Component.translatable(selling ? "gui.skyabyss.shop.hint_sell" : "gui.skyabyss.shop.hint_buy"),
                x0 + 12, y0 + h - 18, FacilityUi.DIM, false);
        if (!hoverStack.isEmpty()) {
            g.renderTooltip(font, hoverStack, mouseX, mouseY);
        }
    }

    private void tab(GuiGraphics g, Font font, int x, int y, Component label, boolean on, int mx, int my) {
        int w = 54;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 13;
        g.fill(x, y, x + w, y + 13, on ? 0xFF2A5E6A : hover ? 0xFF243440 : 0xFF161B22);
        g.renderOutline(x, y, w, 13, on ? FacilityUi.TEAL : FacilityUi.LINE);
        g.drawCenteredString(font, label, x + w / 2, y + 3, on ? 0xFFFFFFFF : FacilityUi.DIM);
    }

    private ItemStack goodRow(GuiGraphics g, Font font, FacilityNetwork.GoodView v, int x, int y, int w) {
        g.renderItem(v.stack(), x + 4, y + 3);
        g.renderItemDecorations(font, v.stack(), x + 4, y + 3);
        Component name = v.locked() ? Component.translatable("gui.skyabyss.shop.locked", v.chapter()) : v.stack().getHoverName();
        g.drawString(font, name, x + 26, y + 3, v.locked() ? FacilityUi.DIM : FacilityUi.TEXT, false);
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
            g.drawString(font, sub.toString(), x + 26, y + 12, v.left() == 0 ? FacilityUi.LACK : FacilityUi.TEAL, false);
        }
        if (v.locked()) {
            return ItemStack.EMPTY;
        }
        // 値段の札 (右寄せ): 銭 n、素材 n (足りなければ赤)
        int tw = 6 + (v.coins() > 0 ? 18 + font.width(Integer.toString(v.coins())) + 4 : 0);
        for (ItemStack m : v.materials()) {
            tw += 18 + font.width(Integer.toString(m.getCount())) + 4;
        }
        int tx = x + w - 6 - tw;
        FacilityUi.tag(g, tx, y + 2, tw, ROW_H - 4);
        int px = tx + 3;
        if (v.coins() > 0) {
            g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), px, y + 3);
            String s = Integer.toString(v.coins());
            g.drawString(font, s, px + 17, y + 7, view.coins() >= v.coins() ? FacilityUi.GOLD : FacilityUi.LACK, false);
            px += 18 + font.width(s) + 4;
        }
        for (int i = 0; i < v.materials().size(); i++) {
            ItemStack m = v.materials().get(i);
            g.renderItem(new ItemStack(m.getItem()), px, y + 3);
            String s = Integer.toString(m.getCount());
            g.drawString(font, s, px + 17, y + 7, v.have().get(i) >= m.getCount() ? FacilityUi.TEXT : FacilityUi.LACK, false);
            px += 18 + font.width(s) + 4;
        }
        return v.stack();
    }

    private ItemStack sellRow(GuiGraphics g, Font font, FacilityNetwork.SellView v, int x, int y, int w) {
        g.renderItem(v.item(), x + 4, y + 3);
        g.renderItemDecorations(font, v.item(), x + 4, y + 3);
        g.drawString(font, v.item().getHoverName(), x + 26, y + 3, FacilityUi.TEXT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.shop.have", v.have()), x + 26, y + 12,
                v.have() >= v.item().getCount() ? FacilityUi.TEAL : FacilityUi.LACK, false);
        String s = Integer.toString(v.coins());
        int tw = 6 + 18 + font.width(s) + 4;
        int tx = x + w - 6 - tw;
        FacilityUi.tag(g, tx, y + 2, tw, ROW_H - 4);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), tx + 3, y + 3);
        g.drawString(font, s, tx + 20, y + 7, FacilityUi.GOLD, false);
        return v.item();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (my >= y0 + 24 && my < y0 + 37) {
                if (mx >= x0 + 12 && mx < x0 + 66) {
                    setTab(false);
                    return true;
                }
                if (!view.sells().isEmpty() && mx >= x0 + 70 && mx < x0 + 124) {
                    setTab(true);
                    return true;
                }
            }
            int lx = x0 + 10;
            int ly = y0 + 40;
            if (mx >= lx && mx < lx + W - 20 && my >= ly && my < ly + ROWS * ROW_H) {
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
        return super.mouseClicked(mx, my, button);
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
