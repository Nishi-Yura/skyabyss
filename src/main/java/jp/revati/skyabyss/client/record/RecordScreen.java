package jp.revati.skyabyss.client.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.record.RecordNetwork;
import jp.revati.skyabyss.record.RecordSnapshot;
import jp.revati.skyabyss.record.RecordSnapshot.Kind;
import jp.revati.skyabyss.record.RecordSnapshot.Row;
import jp.revati.skyabyss.record.RecordSnapshot.Tab;
import jp.revati.skyabyss.rpg.client.skill.TextWrap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// 控え帳の画面 (B キー、/skyrecord、管理者は /skyabyss inspect)。
// 鯨骨の枠に一枚の羊皮紙。上につまみ 4 つ、左に行の一覧、右に選んだ行の詳しい頁 (在りか・守り・取り戻し方)。
// 中身はサーバーが組んだもの (RecordSnapshot) をそのまま描く。開いているあいだ 1 秒ごとに最新を頼み、
// 値が変わった行には金のきらめきを 10 秒付ける。テクスチャは tools/gen/story/gen_record_screen.py (32px 密度) の b 案
public class RecordScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation LEDGER = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/record/ledger.png");
    static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/record/widgets.png");
    static final int LEDGER_W = 608;
    static final int LEDGER_H = 384;
    static final int WIDGETS_W = 512;
    static final int WIDGETS_H = 256;

    // widgets.png の中の絵 (画素。画面には半分で描く)
    record Sprite(int u, int v, int w, int h) {
        void draw(GuiGraphics g, int x, int y) {
            draw(g, x, y, w / 2, h / 2);
        }

        void draw(GuiGraphics g, int x, int y, int gw, int gh) {
            // 半透明の絵 (選んだ行の帯など) があるので、重ねて描く
            RenderSystem.enableBlend();
            g.blit(WIDGETS, x, y, gw, gh, u, v, w, h, WIDGETS_W, WIDGETS_H);
            RenderSystem.disableBlend();
        }
    }

    static final Sprite TAB_OFF = new Sprite(0, 0, 128, 36);
    static final Sprite TAB_ON = new Sprite(128, 0, 128, 48);
    static final Sprite MARK_SAFE = new Sprite(256, 0, 16, 16);
    static final Sprite MARK_ATTENTION = new Sprite(272, 0, 16, 16);
    static final Sprite MARK_LOST = new Sprite(288, 0, 16, 16);
    static final Sprite MARK_LOCKED = new Sprite(304, 0, 16, 16);
    static final Sprite MARK_CHANGED = new Sprite(320, 0, 16, 16);
    static final Sprite ARROW_UP = new Sprite(336, 0, 16, 16);
    static final Sprite ARROW_DOWN = new Sprite(352, 0, 16, 16);
    static final Sprite ROW_SELECTED = new Sprite(0, 56, 256, 24);
    static final Sprite BAR_FRAME = new Sprite(256, 56, 200, 10);
    static final Sprite BAR_FILL = new Sprite(256, 70, 196, 4);
    static final Sprite[] BUTTON = {new Sprite(0, 88, 160, 28), new Sprite(0, 120, 160, 28), new Sprite(0, 152, 160, 28)};
    static final Sprite RIBBON = new Sprite(0, 184, 256, 22);

    // 墨の色 (伝承の書と同じ)
    static final int INK = 0xFF3B2A1C;
    static final int FADED = 0xFF8E7B5E;
    static final int TEAL = 0xFF1D6E69;
    static final int TITLE = 0xFF184F55;
    static final int AMBER = 0xFFA06A0E;
    static final int LOST = 0xFF8A2A3C;
    static final int PAPER = 0xFFF2E6C8;

    static final int BW = 304;
    static final int BH = 192;
    static final int TAB_W = 64;
    // つまみ 5 つ (大事な品・巨神・成長・縁と頁・記し) が書の幅に収まる間隔 (絵は 64 幅、少し重ねる)
    static final int TAB_STEP = 54;
    static final int TAB_X0 = 14;
    static final int LX = 18;
    static final int LW = 128;
    static final int RX = 162;
    static final int RW = 124;
    static final int LIST_TOP = 31;
    static final int LIST_BOTTOM = 182;
    static final int H_HEADER = 13;
    static final int H_ITEM = 19;
    static final int H_VALUE = 11;
    static final int BODY_TOP = 33;
    static final int LINE_H = 10;
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

    @Nullable
    static Sprite markSprite(int mark) {
        return switch (mark) {
            case RecordSnapshot.MARK_SAFE -> MARK_SAFE;
            case RecordSnapshot.MARK_ATTENTION -> MARK_ATTENTION;
            case RecordSnapshot.MARK_LOST -> MARK_LOST;
            case RecordSnapshot.MARK_LOCKED -> MARK_LOCKED;
            default -> null;
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

    private void renderBook(GuiGraphics g) {
        // 選んでいないつまみは書の後ろに
        for (int i = 0; i < RecordSnapshot.TAB_IDS.size(); i++) {
            if (!RecordSnapshot.TAB_IDS.get(i).equals(tab)) {
                TAB_OFF.draw(g, left + TAB_X0 + i * TAB_STEP, top - 15);
            }
        }
        g.blit(LEDGER, left, top, BW, BH, 0, 0, LEDGER_W, LEDGER_H, LEDGER_W, LEDGER_H);
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
            if (on) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 50);
                TAB_ON.draw(g, x, top - 21);
                g.pose().popPose();
            }
            boolean hover = !on && mouseX >= x && mouseX < x + TAB_W && mouseY >= top - 15 && mouseY < top;
            Component name = Component.translatable("skyabyss_record.tab." + id);
            int y = on ? top - 14 : top - 10;
            g.pose().pushPose();
            g.pose().translate(0, 0, on ? 60 : 0);
            g.drawString(font, name, x + (TAB_W - font.width(name)) / 2, y, on ? TITLE : hover ? INK : 0xFF5E5040, false);
            // 変わった値があるつまみには、きらめきを
            if (!on && hasChangedIn(id)) {
                MARK_CHANGED.draw(g, x + TAB_W - 11, y - 1);
            }
            g.pose().popPose();
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
        FormattedCharSequence o = ellipsize(owner, LW - 8);
        int ow = font.width(o);
        if (rebirths > 0) {
            jp.revati.skyabyss.client.rebirth.RebirthClient.drawFrame(g, cx - ow / 2 - 4, top + 17, ow + 8, 11, rebirths);
        }
        if (!snapshot.self()) {
            // 管理者がほかの人を見ているときは、えんじの帯で
            RIBBON.draw(g, cx - ow / 2 - 6, top + 18, ow + 16, 10);
            g.drawString(font, o, cx - ow / 2, top + 19, PAPER, false);
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
            g.fill(x0, y, x1, y + CHIP_H, on ? 0x501D6E69 : hover ? 0x30000000 : 0x18000000);
            if (on) {
                g.fill(x0, y + CHIP_H - 1, x1, y + CHIP_H, TEAL);
            }
            int color = on ? TITLE : c.mark() == RecordSnapshot.MARK_LOCKED ? FADED : hover ? INK : 0xFF5E5040;
            FormattedCharSequence label = ellipsize(c.title(), x1 - x0 - 3);
            g.drawString(font, label, x0 + (x1 - x0 - font.width(label)) / 2 + 1, y + 2, color, false);
            if (!on && hasChangedIn(c.id())) {
                MARK_CHANGED.draw(g, x1 - 7, y - 3);
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
            ARROW_UP.draw(g, left + 140, top + listTop - 9);
        }
        if (scroll() < maxListScroll()) {
            ARROW_DOWN.draw(g, left + 140, top + LIST_BOTTOM - 2);
        }
        return hover;
    }

    @Nullable
    private ItemHover renderRow(GuiGraphics g, Row r, int y, boolean selected, int mouseX, int mouseY, @Nullable ItemHover hover) {
        int x = left + LX;
        boolean inside = mouseY >= Math.max(y, top + listTop()) && mouseY < Math.min(y + rowHeight(r), top + LIST_BOTTOM);
        if (r.kind() == Kind.HEADER) {
            g.drawString(font, r.label(), x, y + 3, TEAL, false);
            g.fill(x + font.width(r.label()) + 4, y + 7, left + LX + LW - 2, y + 8, 0x401D6E69);
            return hover;
        }
        boolean rowHover = inside && mouseX >= x - 2 && mouseX < x + LW;
        if (selected || rowHover) {
            g.pose().pushPose();
            if (!selected) {
                g.setColor(1f, 1f, 1f, 0.45f);
            }
            ROW_SELECTED.draw(g, x - 4, y, LW + 4, rowHeight(r));
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }
        boolean isChanged = changed.containsKey(page() + "/" + r.key());
        if (isChanged) {
            MARK_CHANGED.draw(g, left + 7, y + (rowHeight(r) - 8) / 2);
        }
        int valueColor = isChanged ? AMBER : markColor(r.mark());
        if (r.kind() == Kind.ITEM) {
            if (!r.icon().isEmpty()) {
                g.renderItem(r.icon(), x, y + 1);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 1 && mouseY < y + 17 && inside) {
                    hover = new ItemHover(r.icon());
                }
            } else {
                // 正体を伏せた品
                g.drawString(font, "？", x + 4, y + 5, FADED, false);
            }
            int tx = x + 19;
            int room = LW - 19 - 12;
            g.drawString(font, ellipsize(r.label(), room), tx, y + 1, INK, false);
            g.drawString(font, ellipsize(r.value(), room), tx, y + 10, valueColor, false);
            Sprite m = markSprite(r.mark());
            if (m != null) {
                m.draw(g, x + LW - 11, y + 5);
            }
        } else {
            int bar = r.bar() >= 0 ? 1 : 0;
            FormattedCharSequence value = ellipsize(r.value(), LW / 2);
            int vw = font.width(value);
            g.drawString(font, ellipsize(r.label(), LW - vw - 8), x, y + 1, INK, false);
            g.drawString(font, value, x + LW - 4 - vw, y + 1, valueColor, false);
            if (bar > 0) {
                g.fill(x, y + 10, x + LW - 4, y + 11, 0x30000000);
                g.fill(x, y + 10, x + Math.round((LW - 4) * Mth.clamp(r.bar(), 0f, 1f)), y + 11, TEAL);
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
        g.drawString(font, ellipsize(row.label(), RW - (tx - x)), tx, top + 11, TITLE, false);
        boolean isChanged = changed.containsKey(page() + "/" + row.key());
        g.drawString(font, ellipsize(row.value(), RW - 10), x, top + BODY_TOP, isChanged ? AMBER : markColor(row.mark()), false);
        if (isChanged) {
            MARK_CHANGED.draw(g, x + RW - 8, top + BODY_TOP);
        }
        if (row.bar() >= 0) {
            BAR_FRAME.draw(g, x, top + BODY_TOP + 11, RW - 4, 5);
            int fill = Math.round((RW - 8) * Mth.clamp(row.bar(), 0f, 1f));
            if (fill > 0) {
                BAR_FILL.draw(g, x + 2, top + BODY_TOP + 13, fill, 2);
            }
        }
        int bt = top + bodyTop(row);
        int bb = top + bodyBottom(row);
        bodyScroll = Mth.clamp(bodyScroll, 0, maxBodyScroll(row));
        jp.revati.skyabyss.client.ui.TextClip.clipPosed(g, x - 2, bt, RW + 6, bb - bt);
        int y = bt - bodyScroll;
        for (BodyLine l : body) {
            if (y + l.height() > bt && y < bb) {
                g.drawString(font, l.text(), x, y, l.color(), false);
            }
            y += l.height();
        }
        g.disableScissor();
        if (bodyScroll > 0) {
            ARROW_UP.draw(g, x + RW - 6, bt - 8);
        }
        if (bodyScroll < maxBodyScroll(row)) {
            ARROW_DOWN.draw(g, x + RW - 6, bb - 4);
        }
        if (hasButton(row)) {
            int bx = x + (RW - 80) / 2;
            int by = top + BUTTON_Y;
            boolean hover = mouseX >= bx && mouseX < bx + 80 && mouseY >= by && mouseY < by + 14;
            BUTTON[hover ? 1 : 0].draw(g, bx, by);
            Component label = Component.translatable(switch (row.action()) {
                case RecordSnapshot.ACTION_CLAIM -> "skyabyss_record.gui.claim";
                case RecordSnapshot.ACTION_RECALL_FRAGMENT, RecordSnapshot.ACTION_RECALL_KEY -> "skyabyss_record.gui.recall";
                case RecordSnapshot.ACTION_REPLAY -> "skyabyss_record.gui.replay";
                case RecordSnapshot.ACTION_RECORDS -> "skyabyss_record.gui.open";
                default -> "skyabyss_record.gui.choose";
            });
            g.drawString(font, label, bx + (80 - font.width(label)) / 2, by + 3, TITLE, false);
        }
    }

    private FormattedCharSequence ellipsize(Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
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
                if (mouseX >= x && mouseX < x + TAB_W && mouseY >= top - 21 && mouseY < top + 1) {
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
        } else if (row.action() == RecordSnapshot.ACTION_CHOOSE && minecraft.player != null) {
            // 遺物の選択画面 (サーバーが開かせる。docs/design/30)
            jp.revati.skyabyss.client.reward.HeartRewardClient.request();
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
