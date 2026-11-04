package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
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

// 取引所の画面 (docs/design/35 の 6.11)。黄金比の大きい板 (docs/design/56): 見出しの帯に題と手持ちの鐘銭、中身の一行目につまみ 5 つ、
// その下に一覧 (行 26)、下の段 21 の左に手引き (一行。重ねると全文)、右に釦。
//   市     : ほかの人の出品 (値 → もらえる品、出す人、残りの日)。「申請」= その値のまま、「値を出す」= 自分で決めた鐘銭で申し出る
//   出す   : 主の列 (左) に出す品と持ち物、脇の列 (右) に値 (鐘銭)。「値を出す」から来たときは申し出る額と相手の出品
//   自分の : 脇の列 (左) に自分の出品、主の列 (右) にその申請 (いる人・そろっている人は色で分かる)。受ける (✔)・断る (✘)・取り下げる (二度押し)
//   申請   : 自分の出した申請 (払えるか)。取り下げる
//   受け取り: 戻った品・届いた品。「受け取る」で持ち物へ
// 決めるのはサーバー (TradeService → Trades)。押すたびに新しい中身が届いて描き直す。出す・申請は取引所の番の前でだけ (/trade からは見る・受ける・断るだけ)
public class TradeScreen extends Screen {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box HEAD = LAYOUT.head();
    private static final Golden.Box BODY = LAYOUT.body();
    private static final Golden.Box FOOT = LAYOUT.foot();
    // 中身の一行目はつまみ。その下が一覧
    private static final Golden.Box BAR = BODY.top(Golden.ROW);
    private static final Golden.Box CONTENT = BODY.afterTop(Golden.ROW);
    private static final Golden.Box MAJOR_L = CONTENT.major(true);
    private static final Golden.Box MINOR_R = CONTENT.minor(false);
    private static final Golden.Box MINOR_L = CONTENT.minor(true);
    private static final Golden.Box MAJOR_R = CONTENT.major(false);
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
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
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
                Button go = Button.builder(Component.translatable(offerFor >= 0 ? "gui.skyabyss.trade.do_offer" : "gui.skyabyss.trade.do_list"),
                        b -> submit()).bounds(gb.x(), gb.y(), gb.w(), gb.h()).build();
                go.active = view.atKeeper();
                addRenderableWidget(go);
                if (offerFor >= 0) {
                    Golden.Box bb = footButton(Golden.S89 + Golden.S5, Golden.S55);
                    addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.trade.back"), b -> {
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
                    cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.trade.cancel"), b -> {
                        if (confirmed("cancel", m.row().id())) {
                            send(TradeNetwork.Action.simple("cancel", m.row().id(), ""));
                        }
                    }).bounds(cb.x(), cb.y(), cb.w(), cb.h()).build());
                }
            }
            case 4 -> {
                Golden.Box cb = footButton(0, Golden.S89);
                Button claim = Button.builder(Component.translatable("gui.skyabyss.trade.claim"), b -> send(TradeNetwork.Action.simple("claim", 0, "")))
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
        g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
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
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        hits.clear();
        hitActions.clear();
        hitRight.clear();
        hover = ItemStack.EMPTY;
        hoverText = null;
        int ty = Golden.textY(HEAD.y(), HEAD.h());
        g.drawString(font, title, HEAD.x() + Golden.S5, ty, Golden.GOLD, false);
        // 手持ちの鐘銭 (見出しの帯の右)
        String coins = coinText(view.coins());
        int cx = HEAD.right() - Golden.S5 - font.width(coins);
        g.drawString(font, coins, cx, ty, Golden.GOLD, false);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), cx - 18, HEAD.y() + 2);
        // つまみ (中身の一行目)
        int tx = BAR.x();
        for (int i = 0; i < TABS.length; i++) {
            Component label = switch (i) {
                case 2 -> Component.translatable("gui.skyabyss.trade.tab.mine", view.mine().size(), view.maxListings());
                case 4 -> view.mail().isEmpty() ? Component.translatable("gui.skyabyss.trade.tab.mail")
                        : Component.translatable("gui.skyabyss.trade.tab.mail_n", view.mail().size());
                default -> Component.translatable("gui.skyabyss.trade.tab." + TABS[i]);
            };
            int tw = Math.max(Golden.S34, font.width(label) + 12);
            Golden.Box b = new Golden.Box(tx, BAR.y(), tw, BAR.h());
            Golden.tab(g, font, b, label, tab == i, b.contains(mx, my));
            int id = i;
            hit(b.x(), b.y(), b.w(), b.h(), () -> setTab(id), null);
            tx += tw + Golden.S3;
        }
        switch (tab) {
            case 1 -> renderSell(g, mx, my);
            case 2 -> renderMine(g, mx, my);
            case 3 -> renderApplied(g, mx, my);
            case 4 -> renderMail(g, mx, my);
            default -> renderMarket(g, mx, my);
        }
        // 下の段の左: 手引き (一行。入りきらなければ「…」で、重ねると全文)
        boolean away = !view.atKeeper() && (tab == 0 || tab == 1);
        Component foot = away ? Component.translatable("gui.skyabyss.trade.not_at_keeper")
                : Component.translatable("gui.skyabyss.trade.hint." + TABS[tab] + (tab == 1 && offerFor >= 0 ? "_offer" : ""));
        int buttonsW = tab == 1 ? (offerFor >= 0 ? Golden.S89 + Golden.S5 + Golden.S55 : Golden.S89) : tab == 2 && cancelButton != null || tab == 4 ? Golden.S89 : 0;
        int fw = FOOT.w() - Golden.S5 - (buttonsW > 0 ? buttonsW + Golden.S8 : Golden.S5);
        int fy = Golden.textY(FOOT.y(), FOOT.h());
        FitText.draw(g, font, foot, FOOT.x() + Golden.S5, fy, fw, away ? FacilityUi.LACK : Golden.DIM, false);
        if (font.width(foot) > fw && mx >= FOOT.x() && mx < FOOT.x() + Golden.S5 + fw && my >= FOOT.y() && my < FOOT.bottom()) {
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
                g.drawString(font, t, px + 18, y + 4, Golden.GOLD, false);
                px += 18 + font.width(t) + 2;
                continue;
            }
            stack(g, s, px, y, mx, my);
            px += step;
        }
        if (list.size() > max) {
            g.drawString(font, "+", px, y + 4, Golden.DIM, false);
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
        g.fill(x, y, x + w, y + BTN, hv ? 0xFF2A5E6A : bg);
        Golden.outline(g, x, y, x + w, y + BTN, FacilityUi.LINE);
        g.drawString(font, label, x + (w - font.width(label)) / 2, Golden.textY(y, BTN), fg, false);
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

    private void renderMarket(GuiGraphics g, int mx, int my) {
        Golden.recess(g, CONTENT);
        List<TradeNetwork.Row> rows = view.market();
        clamp(0, rows.size());
        int lx = CONTENT.x() + Golden.S5;
        int right = CONTENT.right() - Golden.S8;
        int bx = right - BUY_W - Golden.S3 - BID_W;
        int nx = bx - Golden.S8 - Golden.S55;
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.market_empty"), CONTENT.cx(), CONTENT.y() + CONTENT.h() / 2 - 4, Golden.DIM);
        }
        for (int i = 0; i < ROWS && i + scroll[0] < rows.size(); i++) {
            TradeNetwork.Row r = rows.get(i + scroll[0]);
            int ry = rowY(CONTENT, i);
            if ((i + scroll[0]) % 2 == 1) {
                Golden.stripe(g, new Golden.Box(CONTENT.x() + 1, ry, CONTENT.w() - 2, ROW_H));
            }
            // 買う側から読む: 左 = 渡すもの (出品者のほしいもの)、右 = もらえる品 (出品の品)。2026-10-22 の試遊: 逆に読めた
            int iy = itemY(ry);
            FacilityUi.tag(g, lx - 2, iy - 2, stacksWidth(r.want(), 18, 4) + 4, 20);
            int ex = stacks(g, r.want(), lx, iy, 18, 4, mx, my);
            int ax = Math.max(ex, lx + Golden.S89 - 13);
            g.drawString(font, "→", ax, iy + 4, Golden.TEAL, false);
            stacks(g, r.give(), ax + 10, iy, 18, Math.max(1, (nx - Golden.S5 - ax - 10) / 18), mx, my);
            FitText.draw(g, font, Component.literal(r.seller()), nx, Golden.textY(ry, ROW), Golden.S55, Golden.TEXT, false);
            g.drawString(font, left(r.left()), nx, Golden.textY(ry + ROW, ROW), Golden.DIM, false);
            int by = ry + (ROW_H - BTN) / 2;
            if (r.applied()) {
                g.drawString(font, Component.translatable("gui.skyabyss.trade.applied"), bx, Golden.textY(by, BTN), Golden.GOLD, false);
            } else {
                button(g, bx, by, BUY_W, Component.translatable("gui.skyabyss.trade.apply"), 0xFF1E3A40, r.canPay() ? Golden.TEXT : FacilityUi.LACK, mx, my,
                        () -> send(TradeNetwork.Action.simple("apply", r.id(), "")));
                button(g, bx + BUY_W + Golden.S3, by, BID_W, Component.translatable("gui.skyabyss.trade.counter"), 0xFF22262E, Golden.SOFT, mx, my, () -> {
                    offerFor = r.id();
                    picked.clear();
                    coinsText = "";
                    tab = 1;
                    rebuildWidgets();
                });
            }
        }
        scrollBar(g, CONTENT, rows.size(), scroll[0]);
    }

    private void clamp(int t, int n) {
        scroll[t] = Math.max(0, Math.min(scroll[t], n - ROWS));
    }

    private void scrollBar(GuiGraphics g, Golden.Box box, int n, int s) {
        if (n > ROWS) {
            int full = ROWS * ROW_H;
            int barH = Math.max(12, full * ROWS / n);
            int barY = box.y() + 2 + (full - barH) * s / Math.max(1, n - ROWS);
            g.fill(box.right() - 4, barY, box.right() - 2, barY + barH, Golden.TEAL | 0xFF000000);
        }
    }

    // ---------------------------------------------------------------- 出す・申し出
    private void renderSell(GuiGraphics g, int mx, int my) {
        TradeNetwork.Row target = offerFor >= 0 ? marketRow(offerFor) : null;
        if (offerFor >= 0 && target == null) {
            offerFor = -1;
        }
        Golden.recess(g, MAJOR_L);
        Golden.recess(g, MINOR_R);
        int lx = MAJOR_L.x() + Golden.S5;
        int lw = MAJOR_L.w() - 2 * Golden.S5;
        int y = MAJOR_L.y() + 2;
        g.drawString(font, Component.translatable(offerFor >= 0 ? "gui.skyabyss.trade.offer_head" : "gui.skyabyss.trade.give_head"), lx, Golden.textY(y, ROW),
                Golden.PALE, false);
        y += ROW + Golden.S3;
        List<ItemStack> inv = Minecraft.getInstance().player == null ? List.of() : Minecraft.getInstance().player.getInventory().items;
        if (target != null) {
            // 申し出は鐘銭の額だけ (持ち物の品は出せない)。左は相手の出品
            for (net.minecraft.util.FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.trade.coins_only"), lw)) {
                g.drawString(font, line, lx, Golden.textY(y, ROW), Golden.DIM, false);
                y += ROW;
            }
            y += Golden.S5;
            FitText.draw(g, font, Component.translatable("gui.skyabyss.trade.for", target.seller()), lx, Golden.textY(y, ROW), lw, Golden.SOFT, false);
            y += ROW + Golden.S3;
            g.drawString(font, Component.translatable("gui.skyabyss.trade.they_give"), lx, Golden.textY(y, ROW), Golden.DIM, false);
            y += ROW;
            stacks(g, target.give(), lx, y, 18, 8, mx, my);
            y += 18 + Golden.S5;
            g.drawString(font, Component.translatable("gui.skyabyss.trade.they_want"), lx, Golden.textY(y, ROW), Golden.DIM, false);
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
            g.drawString(font, Component.translatable("gui.skyabyss.trade.inventory"), lx, Golden.textY(y, ROW), Golden.DIM, false);
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
                        g.fill(sx, sy, sx + 17, sy + 17, 0xA0101418);
                    }
                    if (!ok) {
                        g.fill(sx, sy, sx + 17, sy + 17, 0x90401020);
                    } else {
                        hit(sx, sy, 17, 17, () -> addPick(slot, s.getCount()), () -> addPick(slot, Math.min(s.getCount(), used + 1)));
                    }
                }
            }
        }
        // 脇の列: 値 (鐘銭)。欄は init の coinsBox
        int nx = MINOR_R.x() + Golden.S5;
        int nw = MINOR_R.w() - 2 * Golden.S5;
        int ny = MINOR_R.y() + 2;
        g.drawString(font, Component.translatable(target != null ? "gui.skyabyss.trade.coins_offer" : "gui.skyabyss.trade.want_head"), nx, Golden.textY(ny, ROW),
                Golden.PALE, false);
        ny += ROW + Golden.S3;
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), nx, ny);
        ny += 18 + Golden.S8;
        if (target == null) {
            for (net.minecraft.util.FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.trade.coins_only_list"), nw)) {
                if (ny + ROW > MINOR_R.bottom() - 2) {
                    break;
                }
                g.drawString(font, line, nx, Golden.textY(ny, ROW), Golden.DIM, false);
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
        Golden.recess(g, MINOR_L);
        Golden.recess(g, MAJOR_R);
        List<TradeNetwork.MineRow> rows = view.mine();
        clamp(2, rows.size());
        TradeNetwork.MineRow sel = mineSelected();
        int lx = MINOR_L.x() + Golden.S3;
        int lw = MINOR_L.w() - Golden.S3 - 6;
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.mine_empty"), MINOR_L.cx(), MINOR_L.y() + MINOR_L.h() / 2 - 4, Golden.DIM);
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
            g.drawString(font, "→", ex + Golden.S3, iy + 4, Golden.TEAL, false);
            stacks(g, m.row().want(), ex + Golden.S13, iy, 17, 2, mx, my);
            if (!m.apps().isEmpty()) {
                g.fill(nx - 3, iy + 2, nx + font.width(n) + 2, iy + 14, 0xFF5A4A20);
            }
            g.drawString(font, n, nx, iy + 4, m.apps().isEmpty() ? Golden.DIM : Golden.GOLD, false);
            int id = m.row().id();
            hit(rb.x(), ry, rb.w(), ROW_H, () -> {
                if (selected != id) {
                    selected = id;
                    rebuildWidgets();
                }
            }, null);
        }
        scrollBar(g, MINOR_L, rows.size(), scroll[2]);
        // 主の列 (右): 選んだ出品の申請
        if (sel == null) {
            return;
        }
        int ax = MAJOR_R.x() + Golden.S5;
        int aw = MAJOR_R.w() - 2 * Golden.S5;
        FitText.draw(g, font, Component.translatable("gui.skyabyss.trade.apps_head", sel.apps().size(), left(sel.row().left())), ax,
                Golden.textY(MAJOR_R.y() + 2, ROW), aw, Golden.PALE, false);
        int ay = MAJOR_R.y() + 2 + ROW + Golden.S3;
        int appRows = (MAJOR_R.bottom() - 2 - ay) / ROW_H;
        if (sel.apps().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.trade.no_apps"), ax, Golden.textY(ay, ROW), Golden.DIM, false);
        }
        for (int i = 0; i < sel.apps().size() && i < appRows; i++) {
            TradeNetwork.AppRow a = sel.apps().get(i);
            int ry = ay + i * ROW_H;
            g.fill(ax, ry + 5, ax + 3, ry + 8, a.online() ? FacilityUi.TEAL : 0xFF3A3F48);
            int col = !a.online() ? Golden.DIM : a.ok() ? Golden.TEXT : FacilityUi.LACK;
            String state = Component.translatable(!a.online() ? "gui.skyabyss.trade.away" : a.ok() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short")
                    .getString();
            int okx = ax + aw - 32;
            FitText.draw(g, font, Component.literal(a.name() + "  " + state), ax + 7, Golden.textY(ry, ROW), okx - Golden.S5 - ax - 7, col, false);
            stacks(g, a.offer(), ax + 7, ry + ROW - 2, 17, 5, mx, my);
            g.fill(okx, ry + 1, okx + 15, ry + ROW - 1, a.online() ? 0xFF1E3A40 : 0xFF1A1E24);
            g.drawString(font, "✔", okx + 4, Golden.textY(ry, ROW), a.online() ? Golden.TEAL : Golden.DIM, false);
            hit(okx, ry + 1, 15, ROW - 2, () -> send(TradeNetwork.Action.simple("accept", sel.row().id(), a.who().toString())), null);
            int nox = okx + 17;
            g.fill(nox, ry + 1, nox + 15, ry + ROW - 1, 0xFF2A1C22);
            g.drawString(font, "✘", nox + 4, Golden.textY(ry, ROW), FacilityUi.LACK, false);
            hit(nox, ry + 1, 15, ROW - 2, () -> send(TradeNetwork.Action.simple("reject", sel.row().id(), a.who().toString())), null);
        }
    }

    // ---------------------------------------------------------------- 自分の申請
    private void renderApplied(GuiGraphics g, int mx, int my) {
        Golden.recess(g, CONTENT);
        List<TradeNetwork.Row> rows = view.applied();
        clamp(3, rows.size());
        int lx = CONTENT.x() + Golden.S5;
        int right = CONTENT.right() - Golden.S8;
        int bx = right - Golden.S55;
        int nx = bx - Golden.S8 - Golden.S55;
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.applied_empty"), CONTENT.cx(), CONTENT.y() + CONTENT.h() / 2 - 4, Golden.DIM);
        }
        for (int i = 0; i < ROWS && i + scroll[3] < rows.size(); i++) {
            TradeNetwork.Row r = rows.get(i + scroll[3]);
            int ry = rowY(CONTENT, i);
            if ((i + scroll[3]) % 2 == 1) {
                Golden.stripe(g, new Golden.Box(CONTENT.x() + 1, ry, CONTENT.w() - 2, ROW_H));
            }
            // 自分の申請: 左 = 渡すもの (申し出の額)、右 = もらえる品 (出品の品)
            int iy = itemY(ry);
            int ex = stacks(g, r.want(), lx, iy, 18, 4, mx, my);
            int ax = Math.max(ex, lx + Golden.S89 - 13);
            g.drawString(font, "→", ax, iy + 4, Golden.TEAL, false);
            stacks(g, r.give(), ax + 10, iy, 18, Math.max(1, (nx - Golden.S5 - ax - 10) / 18), mx, my);
            FitText.draw(g, font, Component.literal(r.seller()), nx, Golden.textY(ry, ROW), Golden.S55, Golden.TEXT, false);
            g.drawString(font, Component.translatable(r.canPay() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short"), nx, Golden.textY(ry + ROW, ROW),
                    r.canPay() ? Golden.TEAL : FacilityUi.LACK, false);
            button(g, bx, ry + (ROW_H - BTN) / 2, Golden.S55, Component.translatable("gui.skyabyss.trade.withdraw"), 0xFF2A1C22, FacilityUi.LACK, mx, my,
                    () -> send(TradeNetwork.Action.simple("withdraw", r.id(), "")));
        }
        scrollBar(g, CONTENT, rows.size(), scroll[3]);
    }

    // ---------------------------------------------------------------- 受け取り
    private void renderMail(GuiGraphics g, int mx, int my) {
        Golden.recess(g, CONTENT);
        int cols = (CONTENT.w() - 2 * Golden.S5) / 18;
        int rows = (CONTENT.h() - 2 * Golden.S5) / 18;
        int lx = CONTENT.x() + (CONTENT.w() - cols * 18) / 2;
        int ly = CONTENT.y() + Golden.S5;
        if (view.mail().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.mail_empty"), CONTENT.cx(), CONTENT.y() + CONTENT.h() / 2 - 4, Golden.DIM);
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

    // 撮影・試し用
    public void showTab(int t) {
        setTab(Math.max(0, Math.min(4, t)));
    }
}
