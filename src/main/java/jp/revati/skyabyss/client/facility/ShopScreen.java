package jp.revati.skyabyss.client.facility;

import java.util.List;

import jp.revati.skyabyss.facility.FacilityNetwork;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.ShopStage;
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
// 組み方は店の類の「帳面と台」(client/ui/ShopStage。docs/design/56 の 2.1): 左の生成りの帳面に品の行 (名前 …… 値段。値段は鐘銭と素材、足りない数は赤)、
// 上の辺につまみ (買う / 売る / 買い戻す)。右の黒い紙に、乗せた品 (乗せていなければ最後に乗せた品) を大きく台に立てて、名前・値段・説明。手持ちは山の上。
//   買う: 行を押すと 1 回ぶん、シフトを押しながらで 8 回ぶん。章が届かない品は伏せ、日に限りのある品は「残り」を出す。日替わりの品には印
//   売る: 行を押すと一口、シフトで手持ちの全部。封切り師の買い取りでは、行の下の段に持ち物のどの枠か
//   買い戻す: 売った品の控え (facility/SoldLog。最近の 10 品)。押すと売った値で戻る (2026-11-07 の声「間違って売ってしまったとき用」)
// 台の品に指を置くと品の詳しい札。決めるのはサーバー (ShopService)。押すたびに、新しい並びが届いて描き直す。
public class ShopScreen extends Screen {
    private static final int ROWS = ShopStage.ROWS;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private FacilityNetwork.ShopView view;
    // 0 = 買う、1 = 売る、2 = 買い戻す
    private static final int BUY = 0, SELL = 1, BACK = 2;
    private int mode;
    private int scroll;
    // 台に立てる行 (最後に乗せた行。一覧の中の番号)
    private int focus;
    private boolean closedByServer;

    public ShopScreen(FacilityNetwork.ShopView view) {
        super(Component.translatable(view.title()));
        this.view = view;
        this.mode = sellOnly() ? SELL : BUY;
    }

    // 出すつまみ (左から)
    private java.util.List<Integer> tabs() {
        java.util.List<Integer> t = new java.util.ArrayList<>();
        if (sellOnly()) {
            t.add(SELL);
        } else {
            t.add(BUY);
            if (!view.sells().isEmpty()) {
                t.add(SELL);
            }
        }
        if (!view.sold().isEmpty()) {
            t.add(BACK);
        }
        return t;
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
        if (!tabs().contains(mode)) {
            mode = tabs().get(0);
        }
        clampScroll();
    }

    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override
    protected void init() {
        fit.fit(width, height, ShopStage.W, ShopStage.H, 3f);
        clampScroll();
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
        return mode == SELL ? view.sells().size() : mode == BACK ? view.sold().size() : view.goods().size();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, rowCount() - ROWS));
        focus = Math.max(0, Math.min(focus, rowCount() - 1));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    private static ItemStack coin() {
        return new ItemStack(FacilitySetup.BELL_COIN.get());
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, ShopStage.W, ShopStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Font font = Minecraft.getInstance().font;
        ShopStage.backdrop(g, fit, width, height);
        // つまみ (帳面の上の辺)
        java.util.List<Integer> tabs = tabs();
        for (int t = 0; t < tabs.size(); t++) {
            int m = tabs.get(t);
            String key = m == BUY ? "gui.skyabyss.shop.buy" : m == BACK ? "gui.skyabyss.shop.back" : sellOnly() ? "gui.skyabyss.buyback.tab" : "gui.skyabyss.shop.sell";
            ShopStage.tab(g, font, t, Component.translatable(key), mode == m, mouseX, mouseY);
        }
        // 一覧
        int n = rowCount();
        int hoverRow = ShopStage.rowAt(mouseX, mouseY);
        if (hoverRow >= 0 && hoverRow + scroll < n) {
            focus = hoverRow + scroll;
        } else {
            hoverRow = -1;
        }
        if (n == 0) {
            ShopStage.empty(g, font, Component.translatable(mode == BACK ? "gui.skyabyss.shop.back_empty" : sellOnly() ? "gui.skyabyss.buyback.empty"
                    : mode == SELL ? "gui.skyabyss.shop.nothing_bought" : "gui.skyabyss.shop.empty"));
        }
        for (int i = 0; i < ROWS && i + scroll < n; i++) {
            boolean hot = i + scroll == focus;
            if (hot) {
                ShopStage.hot(g, i);
            }
            int y = ShopStage.rowY(i);
            if (mode == SELL) {
                sellRow(g, font, view.sells().get(i + scroll), y, hot);
            } else if (mode == BACK) {
                backRow(g, font, view.sold().get(i + scroll), y, hot);
            } else {
                goodRow(g, font, view.goods().get(i + scroll), y, hot);
            }
        }
        ShopStage.scroll(g, scroll, n);
        ShopStage.hint(g, font, Component.translatable(mode == BACK ? "gui.skyabyss.shop.hint_back" : sellOnly() ? "gui.skyabyss.buyback.hint"
                : mode == SELL ? "gui.skyabyss.shop.hint_sell" : "gui.skyabyss.shop.hint_buy"));
        // 右: 店の名前・台の品・値段・説明
        ShopStage.title(g, font, title);
        ItemStack shown = ItemStack.EMPTY;
        if (n > 0) {
            if (mode == SELL) {
                FacilityNetwork.SellView v = view.sells().get(focus);
                shown = v.item();
                coinPrice(g, font, v.coins(), ShopStage.RIGHT_X, 126, true, true);
            } else if (mode == BACK) {
                FacilityNetwork.SellView v = view.sold().get(focus);
                shown = v.item();
                coinPrice(g, font, v.coins(), ShopStage.RIGHT_X, 126, view.coins() >= v.coins(), true);
            } else {
                FacilityNetwork.GoodView v = view.goods().get(focus);
                shown = v.locked() ? ItemStack.EMPTY : v.stack();
                if (!v.locked()) {
                    price(g, font, v, ShopStage.RIGHT_X, 126, Paper.ON_BLACK_HI, ShopStage.LACK_ON_BLACK);
                }
            }
        }
        boolean overItem = ShopStage.plinth(g, shown, mouseX, mouseY);
        if (!shown.isEmpty()) {
            ShopStage.describe(g, font, shown, shown.getHoverName(), 146, 190);
        }
        ShopStage.purse(g, font, coin(), view.coins());
        fit.end(g);
        // 行か台の品に指を置くと品の詳しい札 (同じ名前の装備が並んでも見分けられるように。行のどこでも)
        if ((overItem || hoverRow >= 0) && !shown.isEmpty()) {
            g.renderTooltip(font, shown, screenX, screenY);
        }
    }

    // 値段 (左上 x, y): 銭 n、素材 n (足りなければ赤)
    private void price(GuiGraphics g, Font font, FacilityNetwork.GoodView v, int x, int y, int color, int lack) {
        if (v.coins() > 0) {
            g.renderItem(coin(), x, y);
            String s = Integer.toString(v.coins());
            g.drawString(font, s, x + 17, y + 5, view.coins() >= v.coins() ? color : lack, false);
            x += 17 + font.width(s) + 4;
        }
        for (int i = 0; i < v.materials().size(); i++) {
            ItemStack m = v.materials().get(i);
            g.renderItem(new ItemStack(m.getItem()), x, y);
            String s = Integer.toString(m.getCount());
            g.drawString(font, s, x + 17, y + 5, v.have().get(i) >= m.getCount() ? color : lack, false);
            x += 17 + font.width(s) + 4;
        }
    }

    private static int priceW(Font font, FacilityNetwork.GoodView v) {
        int w = 0;
        if (v.coins() > 0) {
            w += 17 + font.width(Integer.toString(v.coins())) + 4;
        }
        for (ItemStack m : v.materials()) {
            w += 17 + font.width(Integer.toString(m.getCount())) + 4;
        }
        return Math.max(0, w - 4);
    }

    // 鐘銭だけの値段。onBlack なら黒い紙の上の色
    private void coinPrice(GuiGraphics g, Font font, int coins, int x, int y, boolean enough, boolean onBlack) {
        g.renderItem(coin(), x, y);
        int c = onBlack ? (enough ? Paper.ON_BLACK_HI : ShopStage.LACK_ON_BLACK) : (enough ? Paper.INK : ShopStage.LACK);
        g.drawString(font, Integer.toString(coins), x + 17, y + 5, c, false);
    }

    // 行の名前と下の段 (名前は品の色を外して墨で)。nameRight = 名前を書ける右の端
    private void rowText(GuiGraphics g, Font font, ItemStack icon, Component name, Component sub, int subColor, int y, int nameRight, boolean hot, boolean dim) {
        int x = ShopStage.LIST_X;
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + 2, y + 3);
            g.renderItemDecorations(font, icon, x + 2, y + 3);
        }
        int tx = x + 22;
        int w = nameRight - tx - 6;
        Component plain = Component.literal(name.getString());
        FitText.draw(g, font, plain, tx, y + 2, w, hot ? ShopStage.ON_PINK : dim ? Paper.INK_SOFT : Paper.INK, false);
        if (sub != null) {
            FitText.draw(g, font, sub, tx, y + 13, w, hot ? ShopStage.ON_PINK : subColor, false);
        }
        int nameW = Math.min(font.width(plain), w);
        ShopStage.leader(g, tx + nameW + 4, nameRight - 2, y + 8, hot);
    }

    private void goodRow(GuiGraphics g, Font font, FacilityNetwork.GoodView v, int y, boolean hot) {
        Component name = v.locked() ? Component.translatable("gui.skyabyss.shop.locked", v.chapter()) : v.stack().getHoverName();
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
        int px = v.locked() ? ShopStage.LIST_X + ShopStage.LIST_W - 4 : ShopStage.LIST_X + ShopStage.LIST_W - 4 - priceW(font, v);
        rowText(g, font, v.stack(), name, sub.isEmpty() ? null : Component.literal(sub.toString()), v.left() == 0 ? ShopStage.LACK : Paper.INK_HEAD, y, px, hot, v.locked());
        if (!v.locked()) {
            price(g, font, v, px, y + 3, hot ? ShopStage.ON_PINK : Paper.INK, hot ? 0x5A0820 : ShopStage.LACK);
        }
    }

    private void sellRow(GuiGraphics g, Font font, FacilityNetwork.SellView v, int y, boolean hot) {
        int pw = 17 + font.width(Integer.toString(v.coins()));
        int px = ShopStage.LIST_X + ShopStage.LIST_W - 4 - pw;
        // 下の段: 封切り師の買い取り (装備) は持ち物のどの枠か (同じ品が並んでも見分けられる)、ほかの店は手持ちの数
        Component sub = sellOnly() ? slotName(v.index()) : Component.translatable("gui.skyabyss.shop.have", v.have());
        rowText(g, font, v.item(), v.item().getHoverName(), sub, sellOnly() || v.have() >= v.item().getCount() ? Paper.INK_HEAD : ShopStage.LACK, y, px, hot, false);
        g.renderItem(coin(), px, y + 3);
        g.drawString(font, Integer.toString(v.coins()), px + 17, y + 8, hot ? ShopStage.ON_PINK : Paper.INK, false);
    }

    // 持ち物の枠の名前 (0〜8 = ホットバー、9〜35 = 持ち物の段と列)
    static Component slotName(int slot) {
        if (slot < 9) {
            return Component.translatable("gui.skyabyss.shop.slot_hotbar", slot + 1);
        }
        int r = (slot - 9) / 9 + 1;
        int c = (slot - 9) % 9 + 1;
        return Component.translatable("gui.skyabyss.shop.slot_bag", r, c);
    }

    // 買い戻す行: 品・「売った品」・買い戻す値 (足りなければ赤)
    private void backRow(GuiGraphics g, Font font, FacilityNetwork.SellView v, int y, boolean hot) {
        int pw = 17 + font.width(Integer.toString(v.coins()));
        int px = ShopStage.LIST_X + ShopStage.LIST_W - 4 - pw;
        rowText(g, font, v.item(), v.item().getHoverName(), Component.translatable("gui.skyabyss.shop.sold_line", v.item().getCount()), Paper.INK_HEAD, y, px, hot, false);
        g.renderItem(coin(), px, y + 3);
        boolean enough = view.coins() >= v.coins();
        g.drawString(font, Integer.toString(v.coins()), px + 17, y + 8, hot ? (enough ? ShopStage.ON_PINK : 0x5A0820) : enough ? Paper.INK : ShopStage.LACK, false);
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (button == 0) {
            java.util.List<Integer> tabs = tabs();
            int t = ShopStage.tabAt(mx, my, tabs.size());
            if (t >= 0) {
                setTab(tabs.get(t));
                return true;
            }
            int r = ShopStage.rowAt(mx, my);
            if (r >= 0) {
                int i = r + scroll;
                if (i < rowCount()) {
                    boolean shift = hasShiftDown();
                    if (mode == BACK) {
                        PacketDistributor.sendToServer(new FacilityNetwork.ShopBuyBack(view.shop(), view.sold().get(i).index()));
                    } else if (mode == SELL) {
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

    private void setTab(int m) {
        if (mode != m) {
            mode = m;
            scroll = 0;
            focus = 0;
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
        mode = sell && !view.sells().isEmpty() ? SELL : sellOnly() ? SELL : BUY;
        focus = 0;
    }

    // 撮影用: 買い戻すつまみを開く
    public void showBack() {
        if (!view.sold().isEmpty()) {
            mode = BACK;
            scroll = 0;
            focus = 0;
        }
    }
}
