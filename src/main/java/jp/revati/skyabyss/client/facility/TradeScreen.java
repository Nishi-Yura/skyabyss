package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ShopStage;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.facility.trade.TradeNetwork;
import jp.revati.skyabyss.facility.trade.Trades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Paper;

// 取引所の画面 (docs/design/35 の 6.11)。黄金比の大きい板 (docs/design/56): 見出しの帯に題と手持ちの鐘銭、中身の一行目につまみ 5 つ、
// その下に一覧 (行 26)、下の段 21 の左に手引き (一行。重ねると全文)、右に釦。
//   市     : ほかの人の出品 (値 → もらえる品、出す人、残りの日)。「申請」= その値のまま、「値を出す」= 自分で決めた鐘銭で申し出る
//   出す   : 主の列 (左) に出す品と持ち物、脇の列 (右) に値 (鐘銭)。「値を出す」から来たときは申し出る額と相手の出品
//   自分の : 脇の列 (左) に自分の出品、主の列 (右) にその申請 (いる人・そろっている人は色で分かる)。受ける (✔)・断る (✘)・取り下げる (二度押し)
//   申請   : 自分の出した申請 (払えるか)。取り下げる
//   受け取り: 戻った品・届いた品。「受け取る」で持ち物へ
// 決めるのはサーバー (TradeService → Trades)。押すたびに新しい中身が届いて描き直す。出す・申請は取引所の番の前でだけ (/trade からは見る・受ける・断るだけ)
// 組み方は店の類の「帳面と台」(client/ui/ShopStage。docs/design/56 の 2.1): 一覧と品を選ぶ所は左の生成りの帳面 (字は墨へ写す)、
// つまみは帳面の上の辺、手引きは帳面の下の端。値の欄・申請の一覧と釦は右の黒い紙、題は右上、手持ちの鐘銭は山の上の黒い札
public class TradeScreen extends Screen {
    // 区画 (舞台の論理 408x228 の中)。前の板の区画の名前のまま、帳面と右の黒い紙へ向け直した
    private static final Golden.Box FOOT = new Golden.Box(ShopStage.RIGHT_X - 4, 184, ShopStage.RIGHT_W + 4, 15);
    // つまみの段 (帳面の上の辺) と帳面の中身
    private static final Golden.Box BAR = new Golden.Box(ShopStage.TAB_X - 6, ShopStage.TAB_Y, 230, 13);
    private static final Golden.Box CONTENT = new Golden.Box(ShopStage.LIST_X - 2, ShopStage.LIST_Y, ShopStage.LIST_W + 2, 170);
    private static final Golden.Box MAJOR_L = CONTENT;
    private static final Golden.Box MINOR_L = CONTENT;
    // 右の黒い紙の欄
    private static final Golden.Box MINOR_R = new Golden.Box(ShopStage.RIGHT_X - 4, 28, ShopStage.RIGHT_W + 4, 154);
    private static final Golden.Box MAJOR_R = MINOR_R;
    private static final int ROW = Golden.ROW;
    private static final int ROW_H = 2 * Golden.ROW;
    private static final int ROWS = (CONTENT.h() - 4) / ROW_H;
    private static final int BTN = Golden.BTN;
    private static final String[] TABS = {"market", "sell", "mine", "applied", "mail"};

    private TradeNetwork.View view;
    private int tab;
    private final int[] scroll = new int[5];
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();
    private final List<Runnable> hitRight = new ArrayList<>();
    private ItemStack hover = ItemStack.EMPTY;
    private Component hoverText;
    // 出す / 申し出の編集
    private final List<int[]> picked = new ArrayList<>();          // {枠, 数}
    private int offerFor = -1;
    private EditBox coinsBox;
    private String coinsText = "";
    // 自分の出品で選んだもの
    private int selected = -1;
    private String confirm = "";
    private long confirmUntil;
    private Button cancelButton;

    public TradeScreen(TradeNetwork.View view) {
        super(Component.translatable("gui.skyabyss.trade.title"));
        this.view = view;
        if (view.tab() >= 0) {
            tab = Math.min(4, view.tab());
        }
    }

    public void update(TradeNetwork.View v) {
        boolean listed = v.mine().size() > view.mine().size();
        boolean offered = v.applied().size() > view.applied().size();
        view = v;
        if (v.tab() >= 0) {
            tab = Math.min(4, v.tab());
        }
        if (listed || offered) {
            // 出せた・申し出た: 編集を空にして、その一覧へ
            picked.clear();
            coinsText = "";
            if (tab == 1) {
                tab = offered && offerFor >= 0 ? 3 : 2;
            }
            offerFor = -1;
        }
        rebuildWidgets();
    }

    public TradeNetwork.View view() {
        return view;
    }

    public int tab() {
        return tab;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(TradeNetwork.Action.simple("close", 0, ""));
        }
    }

    // 下の段の釦 (右から順に。幅 w)
    private static Golden.Box footButton(int fromRight, int w) {
        return new Golden.Box(FOOT.right() - fromRight - w, FOOT.y() + (FOOT.h() - BTN) / 2, w, BTN);
    }

    @Override
    protected void init() {
        fit.fit(width, height, ShopStage.W, ShopStage.H, 3f);
        coinsBox = null;
        cancelButton = null;
        switch (tab) {
            case 1 -> {
                int nx = MINOR_R.x() + Golden.S5;
                coinsBox = new EditBox(font, nx + 18 + Golden.S3, MINOR_R.y() + 2 + ROW + Golden.S3 + 2, Golden.S55, BTN, Component.translatable("gui.skyabyss.trade.coins"));
                coinsBox.setMaxLength(9);
                coinsBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
                coinsBox.setValue(coinsText);
                coinsBox.setResponder(s -> coinsText = s);
                addRenderableWidget(coinsBox);
                Golden.Box gb = footButton(0, Golden.S89);
                Button go = Paper.tag(Component.translatable(offerFor >= 0 ? "gui.skyabyss.trade.do_offer" : "gui.skyabyss.trade.do_list"),
                        b -> submit()).bounds(gb.x(), gb.y(), gb.w(), gb.h()).primary().build();
                go.active = view.atKeeper();
                addRenderableWidget(go);
                if (offerFor >= 0) {
                    Golden.Box bb = footButton(Golden.S89 + Golden.S5, Golden.S55);
                    addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.trade.back"), b -> {
                        offerFor = -1;
                        picked.clear();
                        tab = 0;
                        rebuildWidgets();
                    }).bounds(bb.x(), bb.y(), bb.w(), bb.h()).build());
                }
            }
            case 2 -> {
                TradeNetwork.MineRow m = mineSelected();
                if (m != null) {
                    Golden.Box cb = footButton(0, Golden.S89);
                    cancelButton = addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.trade.cancel"), b -> {
                        if (confirmed("cancel", m.row().id())) {
                            send(TradeNetwork.Action.simple("cancel", m.row().id(), ""));
                        }
                    }).bounds(cb.x(), cb.y(), cb.w(), cb.h()).build());
                }
            }
            case 4 -> {
                Golden.Box cb = footButton(0, Golden.S89);
                Button claim = Paper.tag(Component.translatable("gui.skyabyss.trade.claim"), b -> send(TradeNetwork.Action.simple("claim", 0, "")))
                        .bounds(cb.x(), cb.y(), cb.w(), cb.h()).build();
                claim.active = !view.mail().isEmpty();
                addRenderableWidget(claim);
            }
            default -> {
            }
        }
    }

    private TradeNetwork.MineRow mineSelected() {
        for (TradeNetwork.MineRow m : view.mine()) {
            if (m.row().id() == selected) {
                return m;
            }
        }
        if (!view.mine().isEmpty()) {
            selected = view.mine().get(0).row().id();
            return view.mine().get(0);
        }
        return null;
    }

    private TradeNetwork.Row marketRow(int id) {
        for (TradeNetwork.Row r : view.market()) {
            if (r.id() == id) {
                return r;
            }
        }
        return null;
    }

    private void send(TradeNetwork.Action a) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(a);
        }
    }

    private int coinsValue() {
        try {
            return coinsText.isEmpty() ? 0 : Math.min(Trades.MAX_COINS, Integer.parseInt(coinsText));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void submit() {
        List<Integer> slots = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int[] p : picked) {
            slots.add(p[0]);
            counts.add(p[1]);
        }
        // 品で払う申し出・品をほしいものにする出品は無い (2026-11-01「トレードで使えるのはお金のみ」)。申し出は鐘銭の額だけ
        if (offerFor >= 0) {
            send(new TradeNetwork.Action("offer", offerFor, "", coinsValue(), List.of(), List.of(), List.of(), List.of()));
        } else {
            send(new TradeNetwork.Action("list", 0, "", coinsValue(), slots, counts, List.of(), List.of()));
        }
    }

    // 二度押しの確かめ (4 秒)
    private boolean confirmed(String op, int id) {
        String key = op + ":" + id;
        long now = net.minecraft.Util.getMillis();
        if (key.equals(confirm) && now < confirmUntil) {
            confirm = "";
            return true;
        }
        confirm = key;
        confirmUntil = now + 4000;
        return false;
    }

    private boolean confirming(String op, int id) {
        return (op + ":" + id).equals(confirm) && net.minecraft.Util.getMillis() < confirmUntil;
    }

    // ---------------------------------------------------------------- 描く
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xA00E0D14);
    }

    // 板は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        renderBoard(g, mx, my, partialTick);
        fit.end(g);
    }

    private void renderBoard(GuiGraphics g, int mx, int my, float partialTick) {
        ShopStage.backdrop(g, fit, width, height);
        hits.clear();
        hitActions.clear();
        hitRight.clear();
        hover = ItemStack.EMPTY;
        hoverText = null;
        ShopStage.title(g, font, title);
        // 手持ちの鐘銭 (山の上の黒い札)
        ShopStage.purse(g, font, new ItemStack(FacilitySetup.BELL_COIN.get()), view.coins());
        // つまみ (帳面の上の辺)
        int tx = BAR.x();
        for (int i = 0; i < TABS.length; i++) {
            Component label = switch (i) {
                case 2 -> Component.translatable("gui.skyabyss.trade.tab.mine", view.mine().size(), view.maxListings());
                case 4 -> view.mail().isEmpty() ? Component.translatable("gui.skyabyss.trade.tab.mail")
                        : Component.translatable("gui.skyabyss.trade.tab.mail_n", view.mail().size());
                default -> Component.translatable("gui.skyabyss.trade.tab." + TABS[i]);
            };
            int tw = Math.max(28, font.width(label) + 10);
            Golden.Box b = new Golden.Box(tx, BAR.y(), tw, BAR.h());
            Golden.tab(g, font, b, label, tab == i, b.contains(mx, my));
            int id = i;
            hit(b.x(), b.y(), b.w(), b.h(), () -> setTab(id), null);
            tx += tw + Golden.S3;
        }
        // 帳面の上の字は墨へ写す (右の黒い紙へ移る所で各頁が inkEnd する)
        Paper.inkBegin();
        switch (tab) {
            case 1 -> renderSell(g, mx, my);
            case 2 -> renderMine(g, mx, my);
            case 3 -> renderApplied(g, mx, my);
            case 4 -> renderMail(g, mx, my);
            default -> renderMarket(g, mx, my);
        }
        Paper.inkEnd();
        // 帳面の下の端: 手引き (一行。入りきらなければ「…」で、重ねると全文)
        boolean away = !view.atKeeper() && (tab == 0 || tab == 1);
        Component foot = away ? Component.translatable("gui.skyabyss.trade.not_at_keeper")
                : Component.translatable("gui.skyabyss.trade.hint." + TABS[tab] + (tab == 1 && offerFor >= 0 ? "_offer" : ""));
        int fw = ShopStage.LIST_W - 8;
        int fx = ShopStage.LIST_X + 4;
        int fy = ShopStage.HINT_Y;
        FitText.draw(g, font, foot, fx, fy, fw, away ? ShopStage.LACK : Paper.INK_SOFT, false);
        if (font.width(foot) > fw && mx >= fx && mx < fx + fw && my >= fy - 2 && my < fy + 10) {
            hoverText = foot;
        }
        if (cancelButton != null && mineSelected() != null) {
            cancelButton.setMessage(Component.translatable(confirming("cancel", mineSelected().row().id()) ? "gui.skyabyss.trade.cancel_sure" : "gui.skyabyss.trade.cancel"));
        }
        for (var r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        if (!hover.isEmpty()) {
            g.renderTooltip(font, hover, mx, my);
        } else if (hoverText != null) {
            g.renderTooltip(font, font.split(hoverText, 200), mx, my);
        }
    }

    private void setTab(int t) {
        if (tab != t) {
            tab = t;
            if (t != 1) {
                offerFor = -1;
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.5f));
            rebuildWidgets();
        }
    }

    // 品を一つ描く (数は 99 を越えることもあるので字で)。上にのると説明
    private void stack(GuiGraphics g, ItemStack s, int x, int y, int mx, int my) {
        if (s.isEmpty()) {
            return;
        }
        g.renderItem(s, x, y);
        g.renderItemDecorations(font, s.copyWithCount(1), x, y, s.getCount() > 1 ? Integer.toString(s.getCount()) : null);
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            hover = s;
        }
    }

    // 鐘銭の額 (桁区切り。大きな額も絵の上に数を重ねない。2026-11-01)
    private static String coinText(int n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    // 品を並べたときの幅 (鐘銭は絵と額の文字)
    private int stacksWidth(List<ItemStack> list, int step, int max) {
        int w = 0;
        for (int i = 0; i < list.size() && i < max; i++) {
            ItemStack s = list.get(i);
            w += Trades.isCoin(s) ? 18 + font.width(coinText(s.getCount())) + 2 : step;
        }
        return w;
    }

    // 品を並べて描き、右端の x を返す (step = 間隔)。鐘銭は絵の横に額を書く
    private int stacks(GuiGraphics g, List<ItemStack> list, int x, int y, int step, int max, int mx, int my) {
        int px = x;
        for (int i = 0; i < list.size() && i < max; i++) {
            ItemStack s = list.get(i);
            if (Trades.isCoin(s)) {
                g.renderItem(s.copyWithCount(1), px, y);
                if (mx >= px && mx < px + 16 && my >= y && my < y + 16) {
                    hover = s.copyWithCount(1);
                }
                String t = coinText(s.getCount());
                g.drawString(font, t, px + 18, y + 4, Paper.ON_BLACK_PINK, false);
                px += 18 + font.width(t) + 2;
                continue;
            }
            stack(g, s, px, y, mx, my);
            px += step;
        }
        if (list.size() > max) {
            g.drawString(font, "+", px, y + 4, Paper.ON_BLACK_DIM, false);
            px += 6;
        }
        return px;
    }

    private String left(long ms) {
        long h = ms / 3_600_000L;
        if (h >= 24) {
            return Component.translatable("gui.skyabyss.trade.days_left", h / 24).getString();
        }
        return Component.translatable("gui.skyabyss.trade.hours_left", Math.max(1, h)).getString();
    }

    // 行の中の小さな釦 (高さ 13)
    private void button(GuiGraphics g, int x, int y, int w, Component label, int bg, int fg, int mx, int my, Runnable run) {
        boolean hv = mx >= x && mx < x + w && my >= y && my < y + BTN;
        // bg = 0 で足りないときは灰の紙 (押すとサーバーが理由を返す)。bg は前の地の色の名残で、0 かどうかだけ見る
        Paper.button(g, font, x, y, w, BTN, label, false, Paper.state(!(bg == 0 && fg == FacilityUi.LACK), hv));
        hit(x, y, w, BTN, run, null);
    }

    // 一覧の行 i の上の端 (欄の中、上に 2 あけて)
    private static int rowY(Golden.Box box, int i) {
        return box.y() + 2 + i * ROW_H;
    }

    // 品の絵を行の真ん中に置く y
    private static int itemY(int ry) {
        return ry + (ROW_H - 16) / 2;
    }

    // ---------------------------------------------------------------- 市
    // 列: 値 → もらえる品 (左から)・出す人と残り (二行)・申請 (34) と値を出す (55)
    private static final int BUY_W = Golden.S34;
    private static final int BID_W = Golden.S55;

    // 市で選んだ出品 (一覧の番号。押して選ぶ。右の黒い紙に出品者と釦を出す)
    private int marketFocus;

    private void renderMarket(GuiGraphics g, int mx, int my) {
        List<TradeNetwork.Row> rows = view.market();
        clamp(0, rows.size());
        int lx = CONTENT.x() + Golden.S8;
        int right = CONTENT.right() - Golden.S8;
        if (rows.isEmpty()) {
            FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.trade.market_empty"), CONTENT.cx(), CONTENT.y() + CONTENT.h() / 2 - 4, CONTENT.w() - 8, Paper.ON_BLACK_DIM, false);
        }
        marketFocus = Math.max(0, Math.min(marketFocus, rows.size() - 1));
        for (int i = 0; i < ROWS && i + scroll[0] < rows.size(); i++) {
            int ry = rowY(CONTENT, i);
            // 押して選ぶ (右の釦へ指を動かすあいだに選びが変わらないように)。乗せた行は薄い桃色
            int pick = i + scroll[0];
            hit(CONTENT.x(), ry, CONTENT.w(), ROW_H, () -> marketFocus = pick, null);
            if (pick != marketFocus && mx >= CONTENT.x() && mx < CONTENT.right() && my >= ry && my < ry + ROW_H) {
                g.fill(CONTENT.x(), ry, CONTENT.right(), ry + ROW_H, 0x30E45A9C);
            }
        }
        for (int i = 0; i < ROWS && i + scroll[0] < rows.size(); i++) {
            TradeNetwork.Row r = rows.get(i + scroll[0]);
            int ry = rowY(CONTENT, i);
            boolean hot = i + scroll[0] == marketFocus;
            if (hot) {
                Paper.cut(g, "trade_hot", new float[] {CONTENT.x(), ry, CONTENT.right(), ry - 1, CONTENT.right() + 1, ry + ROW_H - 1, CONTENT.x() + 1, ry + ROW_H}, Paper.PINK, 0, 0, 0);
            } else if (i > 0) {
                for (int d = lx; d < right; d += 3) {
                    g.fill(d, ry, d + 1, ry + 1, Paper.CREAM_LO);
                }
            }
            // 買う側から読む: 左 = 渡すもの (出品者のほしいもの)、右 = もらえる品 (出品の品)。2026-10-22 の試遊: 逆に読めた
            int iy = itemY(ry);
            FacilityUi.tag(g, lx - 2, iy - 2, stacksWidth(r.want(), 18, 4) + 4, 20);
            Paper.inkEnd();
            int ex = stacks(g, r.want(), lx, iy, 18, 4, mx, my);
            Paper.inkBegin();
            int ax = Math.max(ex, lx + Golden.S89 - 13);
            g.drawString(font, "→", ax, iy + 4, hot ? ShopStage.ON_PINK : Paper.ON_BLACK_HI, false);
            int tagW = r.applied() ? font.width(Component.translatable("gui.skyabyss.trade.applied")) + 6 : 0;
            stacks(g, r.give(), ax + 10, iy, 18, Math.max(1, (right - tagW - ax - 10) / 18), mx, my);
            if (r.applied()) {
                Component t = Component.translatable("gui.skyabyss.trade.applied");
                g.drawString(font, t, right - font.width(t), iy + 4, hot ? ShopStage.ON_PINK : Paper.INK_HEAD, false);
            }
        }
        scrollBar(g, CONTENT, rows.size(), scroll[0]);
        Paper.inkEnd();
        // 右の黒い紙: 乗せた出品の出す人・残り・払えるか、申請と値を出す
        if (rows.isEmpty()) {
            return;
        }
        TradeNetwork.Row r = rows.get(marketFocus);
        int x = MINOR_R.x() + 4;
        int w = MINOR_R.w() - 8;
        int y = MINOR_R.y() + 2;
        FitText.draw(g, font, Component.literal(r.seller()), x, y, w, Paper.ON_BLACK_PINK, false);
        y += 12;
        g.drawString(font, left(r.left()), x, y, Paper.ON_BLACK_SOFT, false);
        y += 16;
        g.drawString(font, Component.translatable(r.canPay() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short"), x, y,
                r.canPay() ? Paper.ON_BLACK_HI : FacilityUi.LACK, false);
        int by = FOOT.y() + (FOOT.h() - BTN) / 2;
        if (r.applied()) {
            g.drawString(font, Component.translatable("gui.skyabyss.trade.applied"), x, by + 3, Paper.ON_BLACK_PINK, false);
        } else {
            int bw = BUY_W + 16;
            button(g, x, by, bw, Component.translatable("gui.skyabyss.trade.apply"), 0, r.canPay() ? Golden.TEXT : FacilityUi.LACK, mx, my,
                    () -> send(TradeNetwork.Action.simple("apply", r.id(), "")));
            button(g, x + bw + Golden.S3, by, w - bw - Golden.S3, Component.translatable("gui.skyabyss.trade.counter"), 0, Paper.ON_BLACK_SOFT, mx, my, () -> {
                offerFor = r.id();
                picked.clear();
                coinsText = "";
                tab = 1;
                rebuildWidgets();
            });
        }
    }

    private void clamp(int t, int n) {
        scroll[t] = Math.max(0, Math.min(scroll[t], n - ROWS));
    }

    private void scrollBar(GuiGraphics g, Golden.Box box, int n, int s) {
        if (n > ROWS) {
            int full = ROWS * ROW_H;
            int barH = Math.max(12, full * ROWS / n);
            int barY = box.y() + 2 + (full - barH) * s / Math.max(1, n - ROWS);
            g.fill(box.right() - 4, barY, box.right() - 2, barY + barH, Paper.PINK);
        }
    }

    // ---------------------------------------------------------------- 出す・申し出
    private void renderSell(GuiGraphics g, int mx, int my) {
        TradeNetwork.Row target = offerFor >= 0 ? marketRow(offerFor) : null;
        if (offerFor >= 0 && target == null) {
            offerFor = -1;
        }
        int lx = MAJOR_L.x() + Golden.S5;
        int lw = MAJOR_L.w() - 2 * Golden.S5;
        int y = MAJOR_L.y() + 2;
        g.drawString(font, Component.translatable(offerFor >= 0 ? "gui.skyabyss.trade.offer_head" : "gui.skyabyss.trade.give_head"), lx, Golden.textY(y, ROW),
                Paper.ON_BLACK_HI, false);
        y += ROW + Golden.S3;
        List<ItemStack> inv = Minecraft.getInstance().player == null ? List.of() : Minecraft.getInstance().player.getInventory().items;
        if (target != null) {
            // 申し出は鐘銭の額だけ (持ち物の品は出せない)。左は相手の出品
            for (net.minecraft.util.FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.trade.coins_only"), lw)) {
                g.drawString(font, line, lx, Golden.textY(y, ROW), Paper.ON_BLACK_DIM, false);
                y += ROW;
            }
            y += Golden.S5;
            FitText.draw(g, font, Component.translatable("gui.skyabyss.trade.for", target.seller()), lx, Golden.textY(y, ROW), lw, Paper.ON_BLACK_SOFT, false);
            y += ROW + Golden.S3;
            g.drawString(font, Component.translatable("gui.skyabyss.trade.they_give"), lx, Golden.textY(y, ROW), Paper.ON_BLACK_DIM, false);
            y += ROW;
            stacks(g, target.give(), lx, y, 18, 8, mx, my);
            y += 18 + Golden.S5;
            g.drawString(font, Component.translatable("gui.skyabyss.trade.they_want"), lx, Golden.textY(y, ROW), Paper.ON_BLACK_DIM, false);
            y += ROW;
            stacks(g, target.want(), lx, y, 18, 8, mx, my);
        } else {
            // 選んだ品 (4 枠)
            for (int i = 0; i < Trades.MAX_GIVE; i++) {
                int sx = lx + i * 20;
                g.fill(sx, y, sx + 18, y + 18, 0xFF0C0F14);
                g.renderOutline(sx, y, 18, 18, FacilityUi.LINE);
                if (i < picked.size()) {
                    int[] p = picked.get(i);
                    ItemStack s = p[0] < inv.size() ? inv.get(p[0]) : ItemStack.EMPTY;
                    stack(g, s.copyWithCount(p[1]), sx + 1, y + 1, mx, my);
                    int idx = i;
                    hit(sx, y, 18, 18, () -> picked.remove(idx), () -> {
                        p[1]--;
                        if (p[1] <= 0) {
                            picked.remove(idx);
                        }
                    });
                }
            }
            y += 18 + Golden.S5;
            // 持ち物 (上 3 段 = 9..35、下の段 = ホットバー 0..8)
            g.drawString(font, Component.translatable("gui.skyabyss.trade.inventory"), lx, Golden.textY(y, ROW), Paper.ON_BLACK_DIM, false);
            int gy = y + ROW;
            for (int k = 0; k < 36; k++) {
                int slot = k < 27 ? k + 9 : k - 27;
                int sx = lx + (k % 9) * 18;
                int sy = gy + (k / 9) * 18 + (k >= 27 ? Golden.S3 : 0);
                g.fill(sx, sy, sx + 17, sy + 17, 0xFF0C0F14);
                ItemStack s = slot < inv.size() ? inv.get(slot) : ItemStack.EMPTY;
                int used = usedFrom(slot);
                if (!s.isEmpty()) {
                    boolean ok = allowed(s);
                    int rest = s.getCount() - used;
                    if (rest > 0) {
                        stack(g, s.copyWithCount(rest), sx, sy, mx, my);
                    } else {
                        stack(g, s, sx, sy, mx, my);
                        g.fill(sx, sy, sx + 17, sy + 17, 0xA00E0D14);
                    }
                    if (!ok) {
                        g.fill(sx, sy, sx + 17, sy + 17, 0x90401020);
                    } else {
                        hit(sx, sy, 17, 17, () -> addPick(slot, s.getCount()), () -> addPick(slot, Math.min(s.getCount(), used + 1)));
                    }
                }
            }
        }
        Paper.inkEnd();
        // 右の黒い紙: 値 (鐘銭)。欄は init の coinsBox
        int nx = MINOR_R.x() + Golden.S5;
        int nw = MINOR_R.w() - 2 * Golden.S5;
        int ny = MINOR_R.y() + 2;
        g.drawString(font, Component.translatable(target != null ? "gui.skyabyss.trade.coins_offer" : "gui.skyabyss.trade.want_head"), nx, Golden.textY(ny, ROW),
                Paper.ON_BLACK_HI, false);
        ny += ROW + Golden.S3;
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), nx, ny);
        ny += 18 + Golden.S8;
        if (target == null) {
            for (net.minecraft.util.FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.trade.coins_only_list"), nw)) {
                if (ny + ROW > MINOR_R.bottom() - 2) {
                    break;
                }
                g.drawString(font, line, nx, Golden.textY(ny, ROW), Paper.ON_BLACK_DIM, false);
                ny += ROW;
            }
        }
    }

    private static boolean allowed(ItemStack s) {
        try {
            return Trades.allowed(s);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private int usedFrom(int slot) {
        for (int[] p : picked) {
            if (p[0] == slot) {
                return p[1];
            }
        }
        return 0;
    }

    private void addPick(int slot, int count) {
        for (int[] p : picked) {
            if (p[0] == slot) {
                p[1] = count;
                return;
            }
        }
        if (picked.size() < Trades.MAX_GIVE && count > 0) {
            picked.add(new int[]{slot, count});
        }
    }

    // ---------------------------------------------------------------- 自分の出品
    private void renderMine(GuiGraphics g, int mx, int my) {
        // 脇の列 (左): 自分の出品 (出す品 → 値、申請の数)
        List<TradeNetwork.MineRow> rows = view.mine();
        clamp(2, rows.size());
        TradeNetwork.MineRow sel = mineSelected();
        int lx = MINOR_L.x() + Golden.S3;
        int lw = MINOR_L.w() - Golden.S3 - 6;
        if (rows.isEmpty()) {
            FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.trade.mine_empty"), MINOR_L.cx(), MINOR_L.y() + MINOR_L.h() / 2 - 4, MINOR_L.w() - 8, Paper.ON_BLACK_DIM, false);
        }
        for (int i = 0; i < ROWS && i + scroll[2] < rows.size(); i++) {
            TradeNetwork.MineRow m = rows.get(i + scroll[2]);
            int ry = rowY(MINOR_L, i);
            boolean on = sel != null && sel.row().id() == m.row().id();
            Golden.Box rb = new Golden.Box(MINOR_L.x() + 1, ry, MINOR_L.w() - 2, ROW_H);
            if (on) {
                Golden.selected(g, rb);
            } else if ((i + scroll[2]) % 2 == 1) {
                Golden.stripe(g, rb);
            }
            String n = Integer.toString(m.apps().size());
            int nx = lx + lw - font.width(n);
            int iy = itemY(ry);
            int ex = stacks(g, m.row().give(), lx, iy, 17, 3, mx, my);
            g.drawString(font, "→", ex + Golden.S3, iy + 4, Paper.ON_BLACK_HI, false);
            stacks(g, m.row().want(), ex + Golden.S13, iy, 17, 2, mx, my);
            if (!m.apps().isEmpty()) {
                g.fill(nx - 3, iy + 2, nx + font.width(n) + 2, iy + 14, 0xFF5A4A20);
            }
            g.drawString(font, n, nx, iy + 4, m.apps().isEmpty() ? Paper.ON_BLACK_DIM : Paper.ON_BLACK_PINK, false);
            int id = m.row().id();
            hit(rb.x(), ry, rb.w(), ROW_H, () -> {
                if (selected != id) {
                    selected = id;
                    rebuildWidgets();
                }
            }, null);
        }
        scrollBar(g, MINOR_L, rows.size(), scroll[2]);
        Paper.inkEnd();
        // 主の列 (右): 選んだ出品の申請
        if (sel == null) {
            return;
        }
        int ax = MAJOR_R.x() + Golden.S5;
        int aw = MAJOR_R.w() - 2 * Golden.S5;
        FitText.draw(g, font, Component.translatable("gui.skyabyss.trade.apps_head", sel.apps().size(), left(sel.row().left())), ax,
                Golden.textY(MAJOR_R.y() + 2, ROW), aw, Paper.ON_BLACK_HI, false);
        int ay = MAJOR_R.y() + 2 + ROW + Golden.S3;
        int appRows = (MAJOR_R.bottom() - 2 - ay) / ROW_H;
        if (sel.apps().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.trade.no_apps"), ax, Golden.textY(ay, ROW), Paper.ON_BLACK_DIM, false);
        }
        for (int i = 0; i < sel.apps().size() && i < appRows; i++) {
            TradeNetwork.AppRow a = sel.apps().get(i);
            int ry = ay + i * ROW_H;
            g.fill(ax, ry + 5, ax + 3, ry + 8, a.online() ? FacilityUi.TEAL : 0xFF3A3644);
            int col = !a.online() ? Paper.ON_BLACK_DIM : a.ok() ? Golden.TEXT : FacilityUi.LACK;
            String state = Component.translatable(!a.online() ? "gui.skyabyss.trade.away" : a.ok() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short")
                    .getString();
            int okx = ax + aw - 32;
            FitText.draw(g, font, Component.literal(a.name() + "  " + state), ax + 7, Golden.textY(ry, ROW), okx - Golden.S5 - ax - 7, col, false);
            stacks(g, a.offer(), ax + 7, ry + ROW - 2, 17, 5, mx, my);
            g.fill(okx, ry + 1, okx + 15, ry + ROW - 1, a.online() ? 0xFF2A2232 : 0xFF1A1E24);
            g.drawString(font, "✔", okx + 4, Golden.textY(ry, ROW), a.online() ? Paper.ON_BLACK_HI : Paper.ON_BLACK_DIM, false);
            hit(okx, ry + 1, 15, ROW - 2, () -> send(TradeNetwork.Action.simple("accept", sel.row().id(), a.who().toString())), null);
            int nox = okx + 17;
            g.fill(nox, ry + 1, nox + 15, ry + ROW - 1, 0xFF2A1C22);
            g.drawString(font, "✘", nox + 4, Golden.textY(ry, ROW), FacilityUi.LACK, false);
            hit(nox, ry + 1, 15, ROW - 2, () -> send(TradeNetwork.Action.simple("reject", sel.row().id(), a.who().toString())), null);
        }
    }

    // ---------------------------------------------------------------- 自分の申請
    // 申請で選んだ行 (押して選ぶ。右の黒い紙に出す人・払えるか・取り下げる)
    private int appliedFocus;

    private void renderApplied(GuiGraphics g, int mx, int my) {
        List<TradeNetwork.Row> rows = view.applied();
        clamp(3, rows.size());
        int lx = CONTENT.x() + Golden.S8;
        int right = CONTENT.right() - Golden.S8;
        if (rows.isEmpty()) {
            FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.trade.applied_empty"), CONTENT.cx(), CONTENT.y() + CONTENT.h() / 2 - 4, CONTENT.w() - 8, Paper.ON_BLACK_DIM, false);
        }
        appliedFocus = Math.max(0, Math.min(appliedFocus, rows.size() - 1));
        for (int i = 0; i < ROWS && i + scroll[3] < rows.size(); i++) {
            int ry = rowY(CONTENT, i);
            // 押して選ぶ (右の釦へ指を動かすあいだに選びが変わらないように)。乗せた行は薄い桃色
            int pick = i + scroll[3];
            hit(CONTENT.x(), ry, CONTENT.w(), ROW_H, () -> appliedFocus = pick, null);
            if (pick != appliedFocus && mx >= CONTENT.x() && mx < CONTENT.right() && my >= ry && my < ry + ROW_H) {
                g.fill(CONTENT.x(), ry, CONTENT.right(), ry + ROW_H, 0x30E45A9C);
            }
        }
        for (int i = 0; i < ROWS && i + scroll[3] < rows.size(); i++) {
            TradeNetwork.Row r = rows.get(i + scroll[3]);
            int ry = rowY(CONTENT, i);
            boolean hot = i + scroll[3] == appliedFocus;
            if (hot) {
                Paper.cut(g, "trade_hot", new float[] {CONTENT.x(), ry, CONTENT.right(), ry - 1, CONTENT.right() + 1, ry + ROW_H - 1, CONTENT.x() + 1, ry + ROW_H}, Paper.PINK, 0, 0, 0);
            } else if (i > 0) {
                for (int d = lx; d < right; d += 3) {
                    g.fill(d, ry, d + 1, ry + 1, Paper.CREAM_LO);
                }
            }
            // 自分の申請: 左 = 渡すもの (申し出の額)、右 = もらえる品 (出品の品)。右の端に払えるか
            int iy = itemY(ry);
            int ex = stacks(g, r.want(), lx, iy, 18, 4, mx, my);
            int ax = Math.max(ex, lx + Golden.S89 - 13);
            g.drawString(font, "→", ax, iy + 4, hot ? ShopStage.ON_PINK : Paper.ON_BLACK_HI, false);
            Component st = Component.translatable(r.canPay() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short");
            int sw = font.width(st);
            stacks(g, r.give(), ax + 10, iy, 18, Math.max(1, (right - sw - 6 - ax - 10) / 18), mx, my);
            g.drawString(font, st, right - sw, iy + 4, hot ? ShopStage.ON_PINK : r.canPay() ? Paper.ON_BLACK_HI : FacilityUi.LACK, false);
        }
        scrollBar(g, CONTENT, rows.size(), scroll[3]);
        Paper.inkEnd();
        // 右の黒い紙: 乗せた申請の出す人・残り・取り下げる
        if (rows.isEmpty()) {
            return;
        }
        TradeNetwork.Row r = rows.get(appliedFocus);
        int x = MINOR_R.x() + 4;
        int w = MINOR_R.w() - 8;
        int y = MINOR_R.y() + 2;
        FitText.draw(g, font, Component.literal(r.seller()), x, y, w, Paper.ON_BLACK_PINK, false);
        y += 12;
        g.drawString(font, left(r.left()), x, y, Paper.ON_BLACK_SOFT, false);
        y += 16;
        g.drawString(font, Component.translatable(r.canPay() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short"), x, y,
                r.canPay() ? Paper.ON_BLACK_HI : FacilityUi.LACK, false);
        int by = FOOT.y() + (FOOT.h() - BTN) / 2;
        button(g, x + w - Golden.S89, by, Golden.S89, Component.translatable("gui.skyabyss.trade.withdraw"), 0xFF2A1C22, FacilityUi.LACK, mx, my,
                () -> send(TradeNetwork.Action.simple("withdraw", r.id(), "")));
    }

    // ---------------------------------------------------------------- 受け取り
    private void renderMail(GuiGraphics g, int mx, int my) {
        int cols = (CONTENT.w() - 2 * Golden.S5) / 18;
        int rows = (CONTENT.h() - 2 * Golden.S5) / 18;
        int lx = CONTENT.x() + (CONTENT.w() - cols * 18) / 2;
        int ly = CONTENT.y() + Golden.S5;
        if (view.mail().isEmpty()) {
            FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.trade.mail_empty"), CONTENT.cx(), CONTENT.y() + CONTENT.h() / 2 - 4, CONTENT.w() - 8, Paper.ON_BLACK_DIM, false);
        }
        for (int i = 0; i < view.mail().size() && i < cols * rows; i++) {
            stack(g, view.mail().get(i), lx + 1 + (i % cols) * 18, ly + 1 + (i / cols) * 18, mx, my);
        }
    }

    // ---------------------------------------------------------------- 押す
    private void hit(int x, int y, int w, int h, Runnable left, Runnable right) {
        hits.add(new int[]{x, y, w, h});
        hitActions.add(left);
        hitRight.add(right);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] r = hits.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                Runnable run = button == 1 ? hitRight.get(i) : button == 0 ? hitActions.get(i) : null;
                if (run != null) {
                    run.run();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll[tab] -= (int) Math.signum(dy);
        scroll[tab] = Math.max(0, scroll[tab]);
        return true;
    }

}
