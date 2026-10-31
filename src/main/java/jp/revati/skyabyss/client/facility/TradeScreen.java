package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.facility.trade.TradeNetwork;
import jp.revati.skyabyss.facility.trade.Trades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 取引所の画面 (docs/design/35 の 6.11)。店の画面と同じ絵柄 (暗い石板・骨の枠・角の青緑のひし形)。つまみ 5 つ:
//   市     : ほかの人の出品 (出す品 → ほしいもの、出す人、残りの日)。「申請」= ほしいもののまま、「別に」= 自分で選んだ品で申し出る
//   出す   : 左に出す品 (持ち物の枠から選ぶ。取引所が預かる)、右にほしいもの (鐘銭と、探して選んだ品)。「別に」から来たときは申し出の品を選ぶ
//   自分の : 自分の出品と、その申請 (いる人・品がそろっている人は色で分かる)。受ける (✔)・断る (✘)・取り下げる (二度押し)
//   申請   : 自分の出した申請 (申し出の品がそろっているか)。取り下げる
//   受け取り: 戻った品・届いた品。「受け取る」で持ち物へ
// 決めるのはサーバー (TradeService → Trades)。押すたびに新しい中身が届いて描き直す。出す・申請は取引所の番の前でだけ (/trade からは見る・受ける・断るだけ)
public class TradeScreen extends Screen {
    private static final int W = 340;
    private static final int H = 230;
    private static final int ROW_H = 24;
    private static final int ROWS = 6;
    private static final String[] TABS = {"market", "sell", "mine", "applied", "mail"};

    private TradeNetwork.View view;
    private int tab;
    private int x0, y0;
    private final int[] scroll = new int[5];
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();
    private final List<Runnable> hitRight = new ArrayList<>();
    private ItemStack hover = ItemStack.EMPTY;
    // 出す / 申し出の編集
    private final List<int[]> picked = new ArrayList<>();          // {枠, 数}
    private int offerFor = -1;
    private EditBox coinsBox;
    private String coinsText = "";
    // 自分の出品で選んだもの
    private int selected = -1;
    private String confirm = "";
    private long confirmUntil;

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

    @Override
    protected void init() {
        fit.fit(width, height, W + 16, H + 16, 2.5f);
        x0 = 8;
        y0 = 8;
        coinsBox = null;
        int by = y0 + H - 26;
        switch (tab) {
            case 1 -> {
                int rx = x0 + 186;
                coinsBox = new EditBox(font, rx + 18, y0 + 54, 50, 14, Component.translatable("gui.skyabyss.trade.coins"));
                coinsBox.setMaxLength(9);
                coinsBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
                coinsBox.setValue(coinsText);
                coinsBox.setResponder(s -> coinsText = s);
                addRenderableWidget(coinsBox);
                Button go = Button.builder(Component.translatable(offerFor >= 0 ? "gui.skyabyss.trade.do_offer" : "gui.skyabyss.trade.do_list"),
                        b -> submit()).bounds(x0 + W - 96, by, 84, 18).build();
                go.active = view.atKeeper();
                addRenderableWidget(go);
                if (offerFor >= 0) {
                    addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.trade.back"), b -> {
                        offerFor = -1;
                        picked.clear();
                        tab = 0;
                        rebuildWidgets();
                    }).bounds(x0 + W - 150, by, 50, 18).build());
                }
            }
            case 2 -> {
                TradeNetwork.MineRow m = mineSelected();
                if (m != null) {
                    addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.trade.cancel"), b -> {
                        if (confirmed("cancel", m.row().id())) {
                            send(TradeNetwork.Action.simple("cancel", m.row().id(), ""));
                        }
                    }).bounds(x0 + 12, by, 90, 18).build());
                }
            }
            case 4 -> {
                Button claim = Button.builder(Component.translatable("gui.skyabyss.trade.claim"), b -> send(TradeNetwork.Action.simple("claim", 0, "")))
                        .bounds(x0 + W - 96, by, 84, 18).build();
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

    // 板は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)。論理の (8, 8) に置く
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
        FacilityUi.panel(g, x0, y0, W, H);
        hits.clear();
        hitActions.clear();
        hitRight.clear();
        hover = ItemStack.EMPTY;
        FacilityUi.gem(g, x0 + 12, y0 + 13);
        g.drawString(font, title, x0 + 18, y0 + 9, FacilityUi.TEXT, false);
        String coins = Integer.toString(view.coins());
        int cx = x0 + W - 14 - font.width(coins);
        g.drawString(font, coins, cx, y0 + 9, FacilityUi.GOLD, false);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), cx - 18, y0 + 5);
        // つまみ
        int tx = x0 + 12;
        for (int i = 0; i < TABS.length; i++) {
            Component label = switch (i) {
                case 2 -> Component.translatable("gui.skyabyss.trade.tab.mine", view.mine().size(), view.maxListings());
                case 4 -> view.mail().isEmpty() ? Component.translatable("gui.skyabyss.trade.tab.mail")
                        : Component.translatable("gui.skyabyss.trade.tab.mail_n", view.mail().size());
                default -> Component.translatable("gui.skyabyss.trade.tab." + TABS[i]);
            };
            int tw = font.width(label) + 12;
            boolean on = tab == i;
            boolean hv = mx >= tx && mx < tx + tw && my >= y0 + 22 && my < y0 + 35;
            g.fill(tx, y0 + 22, tx + tw, y0 + 35, on ? 0xFF2A5E6A : hv ? FacilityUi.ROW_HOVER : 0xFF161B22);
            g.renderOutline(tx, y0 + 22, tw, 13, on ? FacilityUi.TEAL : FacilityUi.LINE);
            g.drawString(font, label, tx + 6, y0 + 25, on ? 0xFFFFFFFF : i == 4 && !view.mail().isEmpty() ? FacilityUi.GOLD : FacilityUi.DIM, false);
            int id = i;
            hit(tx, y0 + 22, tw, 13, () -> setTab(id), null);
            tx += tw + 3;
        }
        switch (tab) {
            case 1 -> renderSell(g, mx, my);
            case 2 -> renderMine(g, mx, my);
            case 3 -> renderApplied(g, mx, my);
            case 4 -> renderMail(g, mx, my);
            default -> renderMarket(g, mx, my);
        }
        // 下の段の説明
        Component foot = !view.atKeeper() && (tab == 0 || tab == 1) ? Component.translatable("gui.skyabyss.trade.not_at_keeper")
                : Component.translatable("gui.skyabyss.trade.hint." + TABS[tab] + (tab == 1 && offerFor >= 0 ? "_offer" : ""));
        int footW = tab == 2 ? W - 120 : tab == 1 || tab == 4 ? W - 130 : W - 24;
        int fx = tab == 2 ? x0 + 108 : x0 + 12;
        List<net.minecraft.util.FormattedCharSequence> lines = jp.revati.skyabyss.client.ui.JpWrap.split(font, foot, footW);
        for (int i = 0; i < lines.size() && i < 2; i++) {
            g.drawString(font, lines.get(i), fx, y0 + H - 25 + i * 9, !view.atKeeper() && (tab == 0 || tab == 1) ? FacilityUi.LACK : FacilityUi.DIM, false);
        }
        for (var r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        if (!hover.isEmpty()) {
            g.renderTooltip(font, hover, mx, my);
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
                g.drawString(font, t, px + 18, y + 4, FacilityUi.GOLD, false);
                px += 18 + font.width(t) + 2;
                continue;
            }
            stack(g, s, px, y, mx, my);
            px += step;
        }
        if (list.size() > max) {
            g.drawString(font, "+", px, y + 4, FacilityUi.DIM, false);
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

    private void button(GuiGraphics g, int x, int y, int w, Component label, int bg, int fg, int mx, int my, Runnable run) {
        boolean hv = mx >= x && mx < x + w && my >= y && my < y + 14;
        g.fill(x, y, x + w, y + 14, hv ? 0xFF2A5E6A : bg);
        g.renderOutline(x, y, w, 14, FacilityUi.LINE);
        g.drawCenteredString(font, label, x + w / 2, y + 3, fg);
        hit(x, y, w, 14, run, null);
    }

    // ---------------------------------------------------------------- 市
    private void renderMarket(GuiGraphics g, int mx, int my) {
        int lx = x0 + 10;
        int ly = y0 + 40;
        int lw = W - 20;
        FacilityUi.well(g, lx, ly, lw, ROWS * ROW_H);
        // 列の見出し (渡すもの・もらえる品) はつまみの列に重なっていたので出さない (2026-11-01)。並びは「値 → もらえる品」
        List<TradeNetwork.Row> rows = view.market();
        clamp(0, rows.size());
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.market_empty"), lx + lw / 2, ly + ROWS * ROW_H / 2 - 4, FacilityUi.DIM);
        }
        for (int i = 0; i < ROWS && i + scroll[0] < rows.size(); i++) {
            TradeNetwork.Row r = rows.get(i + scroll[0]);
            int ry = ly + i * ROW_H;
            if ((i + scroll[0]) % 2 == 1) {
                g.fill(lx, ry, lx + lw, ry + ROW_H, FacilityUi.ROW);
            }
            // 買う側から読む: 左 = 渡すもの (出品者のほしいもの)、右 = もらえる品 (出品の品)。2026-10-22 の試遊: 逆に読めた
            FacilityUi.tag(g, lx + 2, ry + 2, stacksWidth(r.want(), 18, 4) + 4, ROW_H - 4);
            int ex = stacks(g, r.want(), lx + 4, ry + 4, 18, 4, mx, my);
            g.drawString(font, "→", Math.max(ex, lx + 78), ry + 8, FacilityUi.TEAL, false);
            int wx = Math.max(ex, lx + 78) + 10;
            stacks(g, r.give(), wx, ry + 4, 18, 5, mx, my);
            int nx = lx + 190;
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(r.seller()), nx, ry + 3, 64, FacilityUi.TEXT, false);
            g.drawString(font, left(r.left()), nx, ry + 13, FacilityUi.DIM, false);
            int bx = lx + lw - 86;
            if (r.applied()) {
                g.drawString(font, Component.translatable("gui.skyabyss.trade.applied"), bx + 4, ry + 8, FacilityUi.GOLD, false);
            } else {
                button(g, bx, ry + 5, 34, Component.translatable("gui.skyabyss.trade.apply"), 0xFF1E3A40, r.canPay() ? FacilityUi.TEXT : FacilityUi.LACK, mx, my,
                        () -> send(TradeNetwork.Action.simple("apply", r.id(), "")));
                button(g, bx + 36, ry + 5, 48, Component.translatable("gui.skyabyss.trade.counter"), 0xFF22262E, FacilityUi.DIM, mx, my, () -> {
                    offerFor = r.id();
                    picked.clear();
                    coinsText = "";
                    tab = 1;
                    rebuildWidgets();
                });
            }
        }
        scrollBar(g, lx, ly, lw, rows.size(), scroll[0]);
    }

    private void clamp(int t, int n) {
        scroll[t] = Math.max(0, Math.min(scroll[t], n - ROWS));
    }

    private void scrollBar(GuiGraphics g, int lx, int ly, int lw, int n, int s) {
        if (n > ROWS) {
            int barH = Math.max(12, ROWS * ROW_H * ROWS / n);
            int barY = ly + (ROWS * ROW_H - barH) * s / Math.max(1, n - ROWS);
            g.fill(lx + lw - 3, barY, lx + lw - 1, barY + barH, FacilityUi.TEAL);
        }
    }

    // ---------------------------------------------------------------- 出す・申し出
    private void renderSell(GuiGraphics g, int mx, int my) {
        int lx = x0 + 12;
        TradeNetwork.Row target = offerFor >= 0 ? marketRow(offerFor) : null;
        if (offerFor >= 0 && target == null) {
            offerFor = -1;
        }
        g.drawString(font, Component.translatable(offerFor >= 0 ? "gui.skyabyss.trade.offer_head" : "gui.skyabyss.trade.give_head"), lx, y0 + 42,
                FacilityUi.TEAL, false);
        if (target != null) {
            // 申し出は鐘銭の額だけ (持ち物の品は出せない)
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.trade.coins_only"), lx, y0 + 58, 166, FacilityUi.DIM, false);
        }
        // 選んだ品 (4 枠。出品のときだけ)
        int py = y0 + 54;
        List<ItemStack> inv = Minecraft.getInstance().player == null ? List.of() : Minecraft.getInstance().player.getInventory().items;
        for (int i = 0; i < (target == null ? Trades.MAX_GIVE : 0); i++) {
            int sx = lx + i * 20;
            g.fill(sx, py, sx + 18, py + 18, 0xFF0C0F14);
            g.renderOutline(sx, py, 18, 18, FacilityUi.LINE);
            if (i < picked.size()) {
                int[] p = picked.get(i);
                ItemStack s = p[0] < inv.size() ? inv.get(p[0]) : ItemStack.EMPTY;
                stack(g, s.copyWithCount(p[1]), sx + 1, py + 1, mx, my);
                int idx = i;
                hit(sx, py, 18, 18, () -> picked.remove(idx), () -> {
                    p[1]--;
                    if (p[1] <= 0) {
                        picked.remove(idx);
                    }
                });
            }
        }
        // 持ち物 (上 3 段 = 9..35、下の段 = ホットバー 0..8)
        if (target == null) {
            g.drawString(font, Component.translatable("gui.skyabyss.trade.inventory"), lx, y0 + 78, FacilityUi.DIM, false);
        }
        int gy = y0 + 88;
        for (int k = 0; k < (target == null ? 36 : 0); k++) {
            int slot = k < 27 ? k + 9 : k - 27;
            int sx = lx + (k % 9) * 18;
            int sy = gy + (k / 9) * 18 + (k >= 27 ? 4 : 0);
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
        int rx = x0 + 186;
        int rw = x0 + W - 12 - rx;
        if (target != null) {
            // 申し出: 相手の出品 (出す品・ほしいもの) と、足す鐘銭
            g.drawString(font, Component.translatable("gui.skyabyss.trade.coins_offer"), rx, y0 + 42, FacilityUi.TEAL, false);
            g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), rx, y0 + 53);
            g.drawString(font, Component.translatable("gui.skyabyss.trade.for", target.seller()), rx, y0 + 80, FacilityUi.DIM, false);
            FacilityUi.well(g, rx, y0 + 92, rw, 48);
            g.drawString(font, Component.translatable("gui.skyabyss.trade.they_give"), rx + 3, y0 + 95, FacilityUi.DIM, false);
            stacks(g, target.give(), rx + 3, y0 + 104, 18, 4, mx, my);
            g.drawString(font, Component.translatable("gui.skyabyss.trade.they_want"), rx + 3, y0 + 124, FacilityUi.DIM, false);
            stacks(g, target.want(), rx + 60, y0 + 120, 18, 4, mx, my);
            return;
        }
        // ほしいもの: 鐘銭だけ (値段)
        g.drawString(font, Component.translatable("gui.skyabyss.trade.want_head"), rx, y0 + 42, FacilityUi.TEAL, false);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), rx, y0 + 53);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.trade.coins_only_list"), rx, y0 + 76, rw, FacilityUi.DIM, false);
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
        int lx = x0 + 10;
        int ly = y0 + 40;
        int lw = 166;
        FacilityUi.well(g, lx, ly, lw, ROWS * ROW_H);
        List<TradeNetwork.MineRow> rows = view.mine();
        clamp(2, rows.size());
        TradeNetwork.MineRow sel = mineSelected();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.mine_empty"), lx + lw / 2, ly + ROWS * ROW_H / 2 - 4, FacilityUi.DIM);
        }
        for (int i = 0; i < ROWS && i + scroll[2] < rows.size(); i++) {
            TradeNetwork.MineRow m = rows.get(i + scroll[2]);
            int ry = ly + i * ROW_H;
            boolean on = sel != null && sel.row().id() == m.row().id();
            g.fill(lx, ry, lx + lw, ry + ROW_H, on ? 0xFF1E3A40 : (i + scroll[2]) % 2 == 1 ? FacilityUi.ROW : 0x00000000);
            int ex = stacks(g, m.row().give(), lx + 3, ry + 4, 16, 3, mx, my);
            g.drawString(font, "→", ex + 1, ry + 8, FacilityUi.TEAL, false);
            stacks(g, m.row().want(), ex + 9, ry + 4, 16, 3, mx, my);
            String n = Integer.toString(m.apps().size());
            int nx = lx + lw - 6 - font.width(n);
            if (!m.apps().isEmpty()) {
                g.fill(nx - 3, ry + 6, nx + font.width(n) + 2, ry + 17, 0xFF5A4A20);
            }
            g.drawString(font, n, nx, ry + 8, m.apps().isEmpty() ? FacilityUi.DIM : FacilityUi.GOLD, false);
            int id = m.row().id();
            hit(lx, ry, lw - 20, ROW_H, () -> {
                if (selected != id) {
                    selected = id;
                    rebuildWidgets();
                }
            }, null);
        }
        scrollBar(g, lx, ly, lw, rows.size(), scroll[2]);
        // 右: 選んだ出品の申請
        int ax = x0 + 182;
        int aw = x0 + W - 10 - ax;
        if (sel == null) {
            return;
        }
        g.drawString(font, Component.translatable("gui.skyabyss.trade.apps_head", sel.apps().size(), left(sel.row().left())), ax, y0 + 40,
                FacilityUi.TEAL, false);
        int ay = y0 + 51;
        int rh = 26;
        FacilityUi.well(g, ax, ay, aw, 5 * rh);
        if (sel.apps().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.trade.no_apps"), ax + 4, ay + 4, FacilityUi.DIM, false);
        }
        for (int i = 0; i < sel.apps().size() && i < 5; i++) {
            TradeNetwork.AppRow a = sel.apps().get(i);
            int ry = ay + i * rh;
            g.fill(ax + 3, ry + 5, ax + 6, ry + 8, a.online() ? FacilityUi.TEAL : 0xFF3A3F48);
            int col = !a.online() ? FacilityUi.DIM : a.ok() ? FacilityUi.TEXT : FacilityUi.LACK;
            String state = Component.translatable(!a.online() ? "gui.skyabyss.trade.away" : a.ok() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short")
                    .getString();
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(a.name()), ax + 9, ry + 2, aw - 80, col, false);
            stacks(g, a.offer(), ax + 9, ry + 10, 17, 5, mx, my);
            int okx = ax + aw - 34;
            g.fill(okx, ry + 2, okx + 15, ry + 14, a.online() ? 0xFF1E3A40 : 0xFF1A1E24);
            g.drawString(font, "✔", okx + 4, ry + 4, a.online() ? FacilityUi.TEAL : FacilityUi.DIM, false);
            hit(okx, ry + 2, 15, 12, () -> send(TradeNetwork.Action.simple("accept", sel.row().id(), a.who().toString())), null);
            int nox = ax + aw - 17;
            g.fill(nox, ry + 2, nox + 15, ry + 14, 0xFF2A1C22);
            g.drawString(font, "✘", nox + 4, ry + 4, FacilityUi.LACK, false);
            hit(nox, ry + 2, 15, 12, () -> send(TradeNetwork.Action.simple("reject", sel.row().id(), a.who().toString())), null);
        }
        // 取り下げの二度押し
        for (var w : renderables) {
            if (w instanceof Button b && b.getY() == y0 + H - 26) {
                b.setMessage(Component.translatable(confirming("cancel", sel.row().id()) ? "gui.skyabyss.trade.cancel_sure" : "gui.skyabyss.trade.cancel"));
            }
        }
    }

    // ---------------------------------------------------------------- 自分の申請
    private void renderApplied(GuiGraphics g, int mx, int my) {
        int lx = x0 + 10;
        int ly = y0 + 40;
        int lw = W - 20;
        FacilityUi.well(g, lx, ly, lw, ROWS * ROW_H);
        List<TradeNetwork.Row> rows = view.applied();
        clamp(3, rows.size());
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.applied_empty"), lx + lw / 2, ly + ROWS * ROW_H / 2 - 4, FacilityUi.DIM);
        }
        for (int i = 0; i < ROWS && i + scroll[3] < rows.size(); i++) {
            TradeNetwork.Row r = rows.get(i + scroll[3]);
            int ry = ly + i * ROW_H;
            if ((i + scroll[3]) % 2 == 1) {
                g.fill(lx, ry, lx + lw, ry + ROW_H, FacilityUi.ROW);
            }
            // 自分の申請: 左 = 渡すもの (申し出の品)、右 = もらえる品 (出品の品)
            int ex = stacks(g, r.want(), lx + 4, ry + 4, 18, 4, mx, my);
            g.drawString(font, "→", Math.max(ex, lx + 78), ry + 8, FacilityUi.TEAL, false);
            int wx = Math.max(ex, lx + 78) + 10;
            stacks(g, r.give(), wx, ry + 4, 18, 5, mx, my);
            int nx = lx + 190;
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(r.seller()), nx, ry + 3, 64, FacilityUi.TEXT, false);
            g.drawString(font, Component.translatable(r.canPay() ? "gui.skyabyss.trade.ready" : "gui.skyabyss.trade.short"), nx, ry + 13,
                    r.canPay() ? FacilityUi.TEAL : FacilityUi.LACK, false);
            button(g, lx + lw - 52, ry + 5, 48, Component.translatable("gui.skyabyss.trade.withdraw"), 0xFF2A1C22, FacilityUi.LACK, mx, my,
                    () -> send(TradeNetwork.Action.simple("withdraw", r.id(), "")));
        }
        scrollBar(g, lx, ly, lw, rows.size(), scroll[3]);
    }

    // ---------------------------------------------------------------- 受け取り
    private void renderMail(GuiGraphics g, int mx, int my) {
        int lx = x0 + 12;
        int ly = y0 + 44;
        int cols = 17;
        FacilityUi.well(g, lx, ly, cols * 18, 6 * 18);
        if (view.mail().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.trade.mail_empty"), lx + cols * 9, ly + 50, FacilityUi.DIM);
        }
        for (int i = 0; i < view.mail().size() && i < cols * 6; i++) {
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
