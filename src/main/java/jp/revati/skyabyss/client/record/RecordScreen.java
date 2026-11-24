package jp.revati.skyabyss.client.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.BookPlate;
import jp.revati.skyabyss.record.RecordNetwork;
import jp.revati.skyabyss.record.RecordSnapshot;
import jp.revati.skyabyss.record.RecordSnapshot.Kind;
import jp.revati.skyabyss.record.RecordSnapshot.Row;
import jp.revati.skyabyss.record.RecordSnapshot.Tab;
import jp.revati.skyabyss.rpg.client.skill.TextWrap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// 控え帳の画面 (B キー、/skyrecord、管理者は /skyabyss inspect)。
// 石と銅の表紙に一枚の生成りの頁 (client/ui/BookPlate。docs/design/56 の 2.1)。上につまみ 5 つ、左に行の一覧、右に選んだ行の詳しい頁 (在りか・守り・取り戻し方)。
// 中身はサーバーが組んだもの (RecordSnapshot) をそのまま描く。開いているあいだ 1 秒ごとに最新を頼み、
// 値が変わった行には銅の印を 10 秒付ける
public class RecordScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    // 墨の色 (伝承の書と同じ)
    static final int INK = BookPlate.INK;
    static final int FADED = BookPlate.FADED;
    static final int TEAL = BookPlate.RUST;
    static final int TITLE = BookPlate.TITLE;
    static final int AMBER = 0xFFA06A0E;
    static final int LOST = 0xFF8A2A3C;
    static final int PAPER = 0xFFECE4D6;

    static final int BW = 304;
    static final int BH = 192;
    // 頁 (表紙の縁の内側に一枚。真ん中を縦の線で分ける)
    static final int PAGE_X = 7;
    static final int PAGE_Y = 7;
    static final int PAGE_W = 290;
    static final int PAGE_H = 178;
    static final int DIVIDER_X = 152;
    // つまみ 5 つ (大事な品・巨神・成長・縁と頁・記し)。書の上に、頁の左の端から重ねずに並べる (幅 52、あいだ 2)
    static final int TAB_W = 52;
    static final int TAB_H = 13;
    static final int TAB_STEP = 54;
    static final int TAB_X0 = PAGE_X;
    static final int LX = 18;
    static final int LW = 128;
    static final int RX = 162;
    static final int RW = 124;
    static final int LIST_TOP = 31;
    static final int LIST_BOTTOM = 176;
    // 行は 13 の段 (docs/design/56。紙の本の形はそのまま): 見出し 13、品 26 (名前と値の 2 行)、値 13
    static final int H_HEADER = 13;
    static final int H_ITEM = 26;
    static final int H_VALUE = 13;
    static final int BODY_TOP = 33;
    static final int LINE_H = 13;
    static final int GAP_H = 3;
    static final int BUTTON_Y = 166;
    // 1 秒ごとに最新を頼む
    static final int REFRESH_TICKS = 20;
    // 変わった値の印を出しておく長さ
    static final int CHANGED_TICKS = 200;

    private RecordSnapshot snapshot;
    private String tab;
    // 中のつまみ (つまみ → 選んだ頁の id)。巨神のつまみの「一覧・岩鯨・潮殻…」
    private final Map<String, String> subByTab = new HashMap<>();
    // 中のつまみの帯の高さ
    static final int CHIP_H = 12;
    private final Map<String, String> selectedByTab = new HashMap<>();
    private final Map<String, Integer> listScroll = new HashMap<>();
    private int bodyScroll;
    private int left;
    private int top;
    private int ticks;
    // 変わった行 (つまみ/行 → 前の値と、変わった時刻)
    private record Changed(Component before, int at) {
    }

    private final Map<String, Changed> changed = new HashMap<>();
    private final List<BodyLine> body = new ArrayList<>();

    private record BodyLine(FormattedCharSequence text, int color, int height) {
    }

    public RecordScreen(RecordSnapshot snapshot, String tab) {
        super(Component.translatable("skyabyss_record.title"));
        this.snapshot = snapshot;
        String parent = RecordSnapshot.parentTab(tab);
        this.tab = RecordSnapshot.TAB_IDS.contains(parent) ? parent : RecordSnapshot.KEEPSAKES;
        if (!parent.equals(tab)) {
            subByTab.put(parent, tab);
        }
    }

    public UUID target() {
        return snapshot.target();
    }

    // サーバーから新しい中身が届いた
    void update(RecordSnapshot next, String openTab) {
        for (RecordSnapshot.Change c : RecordSnapshot.diff(snapshot, next)) {
            if (!RecordSnapshot.isVolatile(c.key())) {
                changed.put(c.key(), new Changed(c.before(), ticks));
            }
        }
        snapshot = next;
        String parent = RecordSnapshot.parentTab(openTab);
        if (RecordSnapshot.TAB_IDS.contains(parent)) {
            tab = parent;
            if (!parent.equals(openTab)) {
                subByTab.put(parent, openTab);
            }
        }
        rebuildBody(false);
    }

    @Override
    protected void init() {
        fit.fit(width, height, BW, 26 + BH + 8, 2.5f);
        left = 0;
        top = 26;
        rebuildBody(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (ticks % REFRESH_TICKS == 0) {
            PacketDistributor.sendToServer(new RecordNetwork.Request(snapshot.target()));
        }
        changed.values().removeIf(c -> ticks - c.at() > CHANGED_TICKS);
    }

    // ---------------------------------------------------------------- 選ぶ
    // 中のつまみ (なければ空)
    private List<Tab> chips() {
        return snapshot.subTabs(tab);
    }

    // 行の一覧に出す頁の id (中のつまみがあれば、選んだ頁)
    private String page() {
        List<Tab> chips = chips();
        if (chips.isEmpty()) {
            return tab;
        }
        String sub = subByTab.get(tab);
        for (Tab c : chips) {
            if (c.id().equals(sub)) {
                return sub;
            }
        }
        return chips.get(0).id();
    }

    private int listTop() {
        return chips().isEmpty() ? LIST_TOP : LIST_TOP + CHIP_H + 1;
    }

    private Tab currentTab() {
        String id = page();
        Tab t = snapshot.tab(id);
        return t == null ? new Tab(id, List.of()) : t;
    }

    private void switchPage(String id) {
        if (!id.equals(page())) {
            subByTab.put(tab, id);
            bodyScroll = 0;
            rebuildBody(true);
        }
    }

    @Nullable
    private Row selected() {
        Tab t = currentTab();
        String key = selectedByTab.get(page());
        Row row = key == null ? null : t.row(key);
        if (row == null || row.kind() == Kind.HEADER) {
            for (Row r : t.rows()) {
                if (r.kind() != Kind.HEADER) {
                    return r;
                }
            }
            return null;
        }
        return row;
    }

    private void select(Row row) {
        selectedByTab.put(page(), row.key());
        bodyScroll = 0;
        rebuildBody(true);
    }

    private void switchTab(String next) {
        if (!next.equals(tab)) {
            tab = next;
            bodyScroll = 0;
            rebuildBody(true);
        }
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.6f));
    }

    private static int rowHeight(Row r) {
        return switch (r.kind()) {
            case HEADER -> H_HEADER;
            case ITEM -> H_ITEM;
            case VALUE -> H_VALUE;
        };
    }

    private int listHeight() {
        int h = 0;
        for (Row r : currentTab().rows()) {
            h += rowHeight(r);
        }
        return h;
    }

    private int maxListScroll() {
        return Math.max(0, listHeight() - (LIST_BOTTOM - listTop()));
    }

    private int scroll() {
        return Mth.clamp(listScroll.getOrDefault(page(), 0), 0, maxListScroll());
    }

    static int markColor(int mark) {
        return switch (mark) {
            case RecordSnapshot.MARK_SAFE -> TEAL;
            case RecordSnapshot.MARK_ATTENTION -> AMBER;
            case RecordSnapshot.MARK_LOST -> LOST;
            case RecordSnapshot.MARK_LOCKED -> FADED;
            default -> INK;
        };
    }

    // 行の印 (BookPlate.mark の種類。-1 = 印なし)
    static int markKind(int mark) {
        return switch (mark) {
            case RecordSnapshot.MARK_SAFE -> BookPlate.MARK_KNOWN;
            case RecordSnapshot.MARK_ATTENTION -> BookPlate.MARK_NEW;
            case RecordSnapshot.MARK_LOST -> BookPlate.MARK_LOST;
            case RecordSnapshot.MARK_LOCKED -> BookPlate.MARK_LOCKED;
            default -> -1;
        };
    }

    // ---------------------------------------------------------------- 右の頁
    private boolean hasButton(@Nullable Row row) {
        return row != null && row.action() != RecordSnapshot.ACTION_NONE && snapshot.self();
    }

    private int bodyBottom(@Nullable Row row) {
        return hasButton(row) ? BUTTON_Y - 3 : LIST_BOTTOM;
    }

    private void rebuildBody(boolean resetScroll) {
        body.clear();
        if (font == null) {
            return;
        }
        Row row = selected();
        if (row == null) {
            return;
        }
        if (resetScroll) {
            bodyScroll = 0;
        }
        Changed c = changed.get(page() + "/" + row.key());
        if (c != null) {
            for (FormattedCharSequence l : TextWrap.split(font, Component.translatable("skyabyss_record.gui.before", c.before()), RW)) {
                body.add(new BodyLine(l, AMBER, LINE_H));
            }
            body.add(new BodyLine(FormattedCharSequence.EMPTY, INK, GAP_H));
        }
        boolean first = true;
        for (Component para : row.detail()) {
            // 一つ目の段落は見出し (在りか など) のことが多いので、青緑で
            int color = first && row.kind() == Kind.ITEM ? TITLE : INK;
            for (FormattedCharSequence l : TextWrap.split(font, para, RW)) {
                body.add(new BodyLine(l, color, LINE_H));
            }
            body.add(new BodyLine(FormattedCharSequence.EMPTY, INK, GAP_H));
            first = false;
        }
    }

    private int bodyTop(Row row) {
        return BODY_TOP + 11 + (row.bar() >= 0 ? 8 : 0);
    }

    private int bodyHeight() {
        int h = 0;
        for (BodyLine l : body) {
            h += l.height();
        }
        return h;
    }

    private int maxBodyScroll(Row row) {
        return Math.max(0, bodyHeight() - (bodyBottom(row) - bodyTop(row)));
    }

    // ---------------------------------------------------------------- 描く
    // 書は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)。論理の (0, 26) に置く
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        renderBook(g);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        renderBody(g, mouseX, mouseY, partialTick);
        fit.end(g);
    }

    // 表紙・頁・真ん中の線・右の頁の見出しの線
    private void renderBook(GuiGraphics g) {
        BookPlate.cover(g, left, top, BW, BH);
        BookPlate.page(g, left + PAGE_X, top + PAGE_Y, PAGE_W, PAGE_H, 0);
        BookPlate.divider(g, left + DIVIDER_X, top + 15, top + 177);
        BookPlate.rule(g, left + RX - 2, top + 27, RW + 2);
    }

    private void renderBody(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTabs(g, mouseX, mouseY);
        renderHeader(g);
        renderRebirthLink(g, mouseX, mouseY);
        @Nullable ItemHover hover = renderList(g, mouseX, mouseY);
        renderDetail(g, mouseX, mouseY);
        if (hover != null) {
            g.renderTooltip(font, hover.stack, mouseX, mouseY);
        }
    }

    private record ItemHover(net.minecraft.world.item.ItemStack stack) {
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < RecordSnapshot.TAB_IDS.size(); i++) {
            String id = RecordSnapshot.TAB_IDS.get(i);
            int x = left + TAB_X0 + i * TAB_STEP;
            boolean on = id.equals(tab);
            int y = top - TAB_H;
            boolean hover = !on && mouseX >= x && mouseX < x + TAB_W && mouseY >= y - 1 && mouseY < top;
            Component name = Component.translatable("skyabyss_record.tab." + id);
            BookPlate.tab(g, font, x, y, TAB_W, TAB_H, name, on, hover, false);
            // 変わった値があるつまみには、右上の角に銅の印を
            if (!on && hasChangedIn(id)) {
                BookPlate.mark(g, x + TAB_W - 8, y - 4, BookPlate.MARK_CHANGED);
            }
        }
    }

    private boolean hasChangedIn(String tabId) {
        for (String key : changed.keySet()) {
            if (key.startsWith(tabId + "/") || key.startsWith(tabId + RecordSnapshot.SUB)) {
                return true;
            }
        }
        return false;
    }

    private void renderHeader(GuiGraphics g) {
        Component name = Component.translatable("skyabyss_record.tab." + tab);
        int cx = left + 79;
        g.drawString(font, name, cx - font.width(name) / 2, top + 9, TITLE, false);
        Component owner = snapshot.self() ? Component.translatable("skyabyss_record.gui.owner", snapshot.targetName())
                : Component.translatable("skyabyss_record.gui.inspecting", snapshot.targetName());
        // 転生した人は、名前の横に段の色の記号と枠 (docs/design/31 の C15)
        int rebirths = jp.revati.skyabyss.rebirth.RebirthRecord.countOf(snapshot);
        if (rebirths > 0) {
            owner = jp.revati.skyabyss.rebirth.RebirthText.symbols(rebirths, jp.revati.skyabyss.client.rebirth.RebirthClient.timeMs(),
                    jp.revati.skyabyss.client.rebirth.RebirthClient.still()).append(" ").append(owner);
        }
        FormattedCharSequence o = owner.getVisualOrderText();
        int ow = Math.min(font.width(o), LW - 8);
        if (rebirths > 0) {
            jp.revati.skyabyss.client.rebirth.RebirthClient.drawFrame(g, cx - ow / 2 - 4, top + 17, ow + 8, 11, rebirths);
        }
        if (!snapshot.self()) {
            // 管理者がほかの人を見ているときは、暗い銅の帯で
            BookPlate.band(g, cx - ow / 2 - 6, top + 18, ow + 12, 10);
            jp.revati.skyabyss.client.ui.Marquee.draw(g, font, o, cx - ow / 2, top + 19, ow, PAPER, false);
        } else {
            g.drawString(font, o, cx - ow / 2, top + 19, FADED, false);
        }
    }

    // 称号・飾りの画面へのリンク (自分の記録だけ。見出しの右)
    private int linkX() {
        return left + LX + LW - font.width(Component.translatable("skyabyss_rebirth.gui.open")) - 2;
    }

    private void renderRebirthLink(GuiGraphics g, int mouseX, int mouseY) {
        if (!snapshot.self()) {
            return;
        }
        Component label = Component.translatable("skyabyss_rebirth.gui.open");
        int x = linkX();
        boolean hover = mouseX >= x - 2 && mouseX < x + font.width(label) + 2 && mouseY >= top + 7 && mouseY < top + 18;
        g.drawString(font, label, x, top + 9, hover ? TITLE : FADED, false);
    }

    // 中のつまみの帯: 左の頁の上に、頁の数で等分して並べる (巨神が増えれば細くなる)
    private int chipX(int i, int n) {
        return left + LX - 4 + (LW + 4) * i / n;
    }

    private void renderChips(GuiGraphics g, int mouseX, int mouseY) {
        List<Tab> chips = chips();
        if (chips.isEmpty()) {
            return;
        }
        int n = chips.size();
        int y = top + LIST_TOP - 1;
        String current = page();
        for (int i = 0; i < n; i++) {
            Tab c = chips.get(i);
            int x0 = chipX(i, n);
            int x1 = chipX(i + 1, n) - 1;
            boolean on = c.id().equals(current);
            boolean hover = !on && mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + CHIP_H;
            g.fill(x0, y, x1, y + CHIP_H, on ? 0x50C48A44 : hover ? 0x30000000 : 0x18000000);
            if (on) {
                g.fill(x0, y + CHIP_H - 1, x1, y + CHIP_H, TEAL);
            }
            int color = on ? TITLE : c.mark() == RecordSnapshot.MARK_LOCKED ? FADED : hover ? INK : 0xFF6E6468;
            FormattedCharSequence label = c.title().getVisualOrderText();
            if (font.width(label) > x1 - x0 - 3) {
                jp.revati.skyabyss.client.ui.Marquee.draw(g, font, label, x0 + 2, y + 2, x1 - x0 - 3, color, false);
            } else {
                g.drawString(font, label, x0 + (x1 - x0 - font.width(label)) / 2 + 1, y + 2, color, false);
            }
            if (!on && hasChangedIn(c.id())) {
                BookPlate.mark(g, x1 - 7, y - 3, BookPlate.MARK_CHANGED);
            }
        }
    }

    @Nullable
    private ItemHover renderList(GuiGraphics g, int mouseX, int mouseY) {
        Tab t = currentTab();
        Row sel = selected();
        renderChips(g, mouseX, mouseY);
        int listTop = listTop();
        int y = top + listTop - scroll();
        ItemHover hover = null;
        jp.revati.skyabyss.client.ui.TextClip.clipPosed(g, left + 6, top + listTop, 145, LIST_BOTTOM - listTop);
        for (Row r : t.rows()) {
            int h = rowHeight(r);
            if (y + h >= top + LIST_TOP && y < top + LIST_BOTTOM) {
                hover = renderRow(g, r, y, r == sel, mouseX, mouseY, hover);
            }
            y += h;
        }
        g.disableScissor();
        if (scroll() > 0) {
            BookPlate.arrow(g, left + 140, top + listTop - 9, true, INK);
        }
        if (scroll() < maxListScroll()) {
            BookPlate.arrow(g, left + 140, top + LIST_BOTTOM - 1, false, INK);
        }
        return hover;
    }

    @Nullable
    private ItemHover renderRow(GuiGraphics g, Row r, int y, boolean selected, int mouseX, int mouseY, @Nullable ItemHover hover) {
        int x = left + LX;
        boolean inside = mouseY >= Math.max(y, top + listTop()) && mouseY < Math.min(y + rowHeight(r), top + LIST_BOTTOM);
        if (r.kind() == Kind.HEADER) {
            g.drawString(font, r.label(), x, y + 3, TEAL, false);
            g.fill(x + font.width(r.label()) + 4, y + 7, left + LX + LW - 2, y + 8, 0x40C48A44);
            return hover;
        }
        boolean rowHover = inside && mouseX >= x - 2 && mouseX < x + LW;
        if (selected || rowHover) {
            BookPlate.row(g, x - 4, y, LW + 4, rowHeight(r), selected);
        }
        boolean isChanged = changed.containsKey(page() + "/" + r.key());
        if (isChanged) {
            BookPlate.mark(g, left + 7, y + (rowHeight(r) - 8) / 2, BookPlate.MARK_CHANGED);
        }
        int valueColor = isChanged ? AMBER : markColor(r.mark());
        if (r.kind() == Kind.ITEM) {
            if (!r.icon().isEmpty()) {
                g.renderItem(r.icon(), x, y + 5);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 5 && mouseY < y + 21 && inside) {
                    hover = new ItemHover(r.icon());
                }
            } else {
                // 正体を伏せた品
                g.drawString(font, "？", x + 4, y + 9, FADED, false);
            }
            int tx = x + 19;
            int room = LW - 19 - 12;
            jp.revati.skyabyss.client.ui.Marquee.draw(g, font, r.label(), tx, y + 3, room, INK, false);
            jp.revati.skyabyss.client.ui.Marquee.draw(g, font, r.value(), tx, y + 16, room, valueColor, false);
            int m = markKind(r.mark());
            if (m >= 0) {
                BookPlate.mark(g, x + LW - 11, y + 9, m);
            }
        } else {
            int bar = r.bar() >= 0 ? 1 : 0;
            FormattedCharSequence value = r.value().getVisualOrderText();
            int vw = Math.min(font.width(value), LW / 2);
            jp.revati.skyabyss.client.ui.Marquee.draw(g, font, r.label(), x, y + 2, LW - vw - 8, INK, false);
            jp.revati.skyabyss.client.ui.Marquee.draw(g, font, value, x + LW - 4 - vw, y + 2, vw, valueColor, false);
            if (bar > 0) {
                g.fill(x, y + 11, x + LW - 4, y + 12, 0x30000000);
                g.fill(x, y + 11, x + Math.round((LW - 4) * Mth.clamp(r.bar(), 0f, 1f)), y + 12, TEAL);
            }
        }
        return hover;
    }

    private void renderDetail(GuiGraphics g, int mouseX, int mouseY) {
        Row row = selected();
        int x = left + RX;
        if (row == null) {
            return;
        }
        int tx = x;
        if (!row.icon().isEmpty()) {
            g.renderItem(row.icon(), x, top + 6);
            tx += 19;
        }
        jp.revati.skyabyss.client.ui.Marquee.draw(g, font, row.label(), tx, top + 11, RW - (tx - x), TITLE, false);
        boolean isChanged = changed.containsKey(page() + "/" + row.key());
        jp.revati.skyabyss.client.ui.Marquee.draw(g, font, row.value(), x, top + BODY_TOP, RW - 10, isChanged ? AMBER : markColor(row.mark()), false);
        if (isChanged) {
            BookPlate.mark(g, x + RW - 8, top + BODY_TOP, BookPlate.MARK_CHANGED);
        }
        if (row.bar() >= 0) {
            BookPlate.bar(g, x, top + BODY_TOP + 11, RW - 4, Mth.clamp(row.bar(), 0f, 1f));
        }
        int bt = top + bodyTop(row);
        int bb = top + bodyBottom(row);
        bodyScroll = Mth.clamp(bodyScroll, 0, maxBodyScroll(row));
        jp.revati.skyabyss.client.ui.TextClip.clipPosed(g, x - 2, bt, RW + 6, bb - bt);
        int y = bt - bodyScroll;
        for (BodyLine l : body) {
            if (y + l.height() > bt && y < bb) {
                g.drawString(font, l.text(), x, y + (l.height() >= LINE_H ? 2 : 0), l.color(), false);
            }
            y += l.height();
        }
        g.disableScissor();
        if (bodyScroll > 0) {
            BookPlate.arrow(g, x + RW - 6, bt - 8, true, INK);
        }
        if (bodyScroll < maxBodyScroll(row)) {
            BookPlate.arrow(g, x + RW - 6, bb - 1, false, INK);
        }
        if (hasButton(row)) {
            int bx = x + (RW - 80) / 2;
            int by = top + BUTTON_Y;
            boolean hover = mouseX >= bx && mouseX < bx + 80 && mouseY >= by && mouseY < by + 14;
            Component label = Component.translatable(switch (row.action()) {
                case RecordSnapshot.ACTION_CLAIM -> "skyabyss_record.gui.claim";
                case RecordSnapshot.ACTION_RECALL_FRAGMENT, RecordSnapshot.ACTION_RECALL_KEY -> "skyabyss_record.gui.recall";
                case RecordSnapshot.ACTION_REPLAY -> "skyabyss_record.gui.replay";
                case RecordSnapshot.ACTION_RECORDS -> "skyabyss_record.gui.open";
                default -> "skyabyss_record.gui.choose";
            });
            jp.revati.skyabyss.client.ui.Plate.button(g, font, bx, by, 80, 14, label, true,
                    hover ? jp.revati.skyabyss.client.ui.Plate.HOVER : jp.revati.skyabyss.client.ui.Plate.REST, jp.revati.skyabyss.client.ui.Plate.GOLD);
        }
    }

    // ---------------------------------------------------------------- 操作
    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = fit.x(screenX);
        double mouseY = fit.y(screenY);
        if (button == 0 && snapshot.self() && mouseX >= linkX() - 2 && mouseX < linkX() + font.width(Component.translatable("skyabyss_rebirth.gui.open")) + 2
                && mouseY >= top + 7 && mouseY < top + 18) {
            click();
            minecraft.setScreen(new jp.revati.skyabyss.client.rebirth.RebirthScreen(this));
            return true;
        }
        if (button == 0) {
            // つまみ
            for (int i = 0; i < RecordSnapshot.TAB_IDS.size(); i++) {
                int x = left + TAB_X0 + i * TAB_STEP;
                if (mouseX >= x && mouseX < x + TAB_W && mouseY >= top - TAB_H - 1 && mouseY < top) {
                    if (!RecordSnapshot.TAB_IDS.get(i).equals(tab)) {
                        click();
                        switchTab(RecordSnapshot.TAB_IDS.get(i));
                    }
                    return true;
                }
            }
            // 中のつまみ
            List<Tab> chips = chips();
            int cy = top + LIST_TOP - 1;
            if (!chips.isEmpty() && mouseY >= cy && mouseY < cy + CHIP_H) {
                for (int i = 0; i < chips.size(); i++) {
                    if (mouseX >= chipX(i, chips.size()) && mouseX < chipX(i + 1, chips.size()) - 1) {
                        if (!chips.get(i).id().equals(page())) {
                            click();
                            switchPage(chips.get(i).id());
                        }
                        return true;
                    }
                }
            }
            // 一覧の行
            if (mouseX >= left + LX - 4 && mouseX < left + LX + LW && mouseY >= top + listTop() && mouseY < top + LIST_BOTTOM) {
                int y = top + listTop() - scroll();
                for (Row r : currentTab().rows()) {
                    int h = rowHeight(r);
                    if (mouseY >= y && mouseY < y + h) {
                        if (r.kind() != Kind.HEADER && r != selected()) {
                            click();
                            select(r);
                        }
                        return true;
                    }
                    y += h;
                }
            }
            // ボタン
            Row row = selected();
            if (hasButton(row)) {
                int bx = left + RX + (RW - 80) / 2;
                int by = top + BUTTON_Y;
                if (mouseX >= bx && mouseX < bx + 80 && mouseY >= by && mouseY < by + 14) {
                    press(row);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void press(Row row) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        if (row.action() == RecordSnapshot.ACTION_CLAIM) {
            PacketDistributor.sendToServer(new RecordNetwork.Claim());
        } else if (row.action() == RecordSnapshot.ACTION_RECALL_FRAGMENT || row.action() == RecordSnapshot.ACTION_RECALL_KEY) {
            PacketDistributor.sendToServer(new RecordNetwork.Recall(row.action() == RecordSnapshot.ACTION_RECALL_KEY));
        } else if (row.action() == RecordSnapshot.ACTION_REPLAY && row.key().startsWith(RecordSnapshot.REEL_ROW)) {
            // 語りの画面はサーバーが開かせる (控え帳は閉じる)
            PacketDistributor.sendToServer(new RecordNetwork.Replay(row.key().substring(RecordSnapshot.REEL_ROW.length())));
            onClose();
        } else if (row.action() == RecordSnapshot.ACTION_RECORDS) {
            // 試練の記録の画面はサーバーが開かせる
            PacketDistributor.sendToServer(new RecordNetwork.OpenTrials());
        }
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double scrollX, double scrollY) {
        double mouseX = fit.x(screenX);
        double mouseY = fit.y(screenY);
        int step = (int) Math.signum(scrollY) * -12;
        if (mouseX < left + 154) {
            listScroll.put(page(), Mth.clamp(scroll() + step, 0, maxListScroll()));
        } else {
            Row row = selected();
            if (row != null) {
                bodyScroll = Mth.clamp(bodyScroll + step, 0, maxBodyScroll(row));
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RecordClient.OPEN_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        List<String> ids = RecordSnapshot.TAB_IDS;
        int ti = ids.indexOf(tab);
        if (keyCode == GLFW.GLFW_KEY_RIGHT && ti + 1 < ids.size()) {
            click();
            switchTab(ids.get(ti + 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT && ti > 0) {
            click();
            switchTab(ids.get(ti - 1));
            return true;
        }
        List<Tab> chips = chips();
        if (keyCode == GLFW.GLFW_KEY_TAB && !chips.isEmpty()) {
            int ci = 0;
            for (int i = 0; i < chips.size(); i++) {
                if (chips.get(i).id().equals(page())) {
                    ci = i;
                }
            }
            int next = Math.floorMod(ci + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), chips.size());
            click();
            switchPage(chips.get(next).id());
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + ids.size()) {
            switchTab(ids.get(keyCode - GLFW.GLFW_KEY_1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            List<Row> rows = currentTab().rows().stream().filter(r -> r.kind() != Kind.HEADER).toList();
            Row sel = selected();
            int i = sel == null ? -1 : rows.indexOf(sel);
            int next = Mth.clamp(i + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, rows.size() - 1);
            if (!rows.isEmpty() && next != i) {
                select(rows.get(next));
                ensureVisible(rows.get(next));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureVisible(Row target) {
        int y = 0;
        for (Row r : currentTab().rows()) {
            if (r == target) {
                int view = LIST_BOTTOM - listTop();
                int s = scroll();
                if (y < s) {
                    s = y;
                } else if (y + rowHeight(r) > s + view) {
                    s = y + rowHeight(r) - view;
                }
                listScroll.put(page(), Mth.clamp(s, 0, maxListScroll()));
                return;
            }
            y += rowHeight(r);
        }
    }

    @Override
    public int[] textBounds() {
        // 上のつまみ (top - 21 から) も文字の範囲に入れる (書の上端で切っていて、つまみの名が出なかった)
        return fit.screenRect(left, top - 24, BW, BH + 24);
    }
}
