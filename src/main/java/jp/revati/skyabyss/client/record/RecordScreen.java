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
// 値が変わった行には金のきらめきを 10 秒付ける。テクスチャは tools/gen_record_screen.py (32px 密度) の b 案
public class RecordScreen extends Screen {
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
    static final int TAB_STEP = 68;
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
        this.tab = RecordSnapshot.TAB_IDS.contains(tab) ? tab : RecordSnapshot.KEEPSAKES;
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
        if (RecordSnapshot.TAB_IDS.contains(openTab)) {
            tab = openTab;
        }
        rebuildBody(false);
    }

    @Override
    protected void init() {
        left = (width - BW) / 2;
        top = (height - BH) / 2 + 10;
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
    private Tab currentTab() {
        Tab t = snapshot.tab(tab);
        return t == null ? new Tab(tab, List.of()) : t;
    }

    @Nullable
    private Row selected() {
        Tab t = currentTab();
        String key = selectedByTab.get(tab);
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
        selectedByTab.put(tab, row.key());
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
        return Math.max(0, listHeight() - (LIST_BOTTOM - LIST_TOP));
    }

    private int scroll() {
        return Mth.clamp(listScroll.getOrDefault(tab, 0), 0, maxListScroll());
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
        Changed c = changed.get(tab + "/" + row.key());
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
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // 選んでいないつまみは書の後ろに
        for (int i = 0; i < RecordSnapshot.TAB_IDS.size(); i++) {
            if (!RecordSnapshot.TAB_IDS.get(i).equals(tab)) {
                TAB_OFF.draw(g, left + TAB_X0 + i * TAB_STEP, top - 15);
            }
        }
        g.blit(LEDGER, left, top, BW, BH, 0, 0, LEDGER_W, LEDGER_H, LEDGER_W, LEDGER_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTabs(g, mouseX, mouseY);
        renderHeader(g);
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
            if (key.startsWith(tabId + "/")) {
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
        FormattedCharSequence o = ellipsize(owner, LW - 8);
        int ow = font.width(o);
        if (!snapshot.self()) {
            // 管理者がほかの人を見ているときは、えんじの帯で
            RIBBON.draw(g, cx - ow / 2 - 6, top + 18, ow + 16, 10);
            g.drawString(font, o, cx - ow / 2, top + 19, PAPER, false);
        } else {
            g.drawString(font, o, cx - ow / 2, top + 19, FADED, false);
        }
    }

    @Nullable
    private ItemHover renderList(GuiGraphics g, int mouseX, int mouseY) {
        Tab t = currentTab();
        Row sel = selected();
        int y = top + LIST_TOP - scroll();
        ItemHover hover = null;
        g.enableScissor(left + 6, top + LIST_TOP, left + 151, top + LIST_BOTTOM);
        for (Row r : t.rows()) {
            int h = rowHeight(r);
            if (y + h >= top + LIST_TOP && y < top + LIST_BOTTOM) {
                hover = renderRow(g, r, y, r == sel, mouseX, mouseY, hover);
            }
            y += h;
        }
        g.disableScissor();
        if (scroll() > 0) {
            ARROW_UP.draw(g, left + 140, top + LIST_TOP - 9);
        }
        if (scroll() < maxListScroll()) {
            ARROW_DOWN.draw(g, left + 140, top + LIST_BOTTOM - 2);
        }
        return hover;
    }

    @Nullable
    private ItemHover renderRow(GuiGraphics g, Row r, int y, boolean selected, int mouseX, int mouseY, @Nullable ItemHover hover) {
        int x = left + LX;
        boolean inside = mouseY >= Math.max(y, top + LIST_TOP) && mouseY < Math.min(y + rowHeight(r), top + LIST_BOTTOM);
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
        boolean isChanged = changed.containsKey(tab + "/" + r.key());
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
        boolean isChanged = changed.containsKey(tab + "/" + row.key());
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
        g.enableScissor(x - 2, bt, x + RW + 4, bb);
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
            Component label = Component.translatable(row.action() == RecordSnapshot.ACTION_CLAIM ? "skyabyss_record.gui.claim" : "skyabyss_record.gui.choose");
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            // 一覧の行
            if (mouseX >= left + LX - 4 && mouseX < left + LX + LW && mouseY >= top + LIST_TOP && mouseY < top + LIST_BOTTOM) {
                int y = top + LIST_TOP - scroll();
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
        } else if (row.action() == RecordSnapshot.ACTION_CHOOSE && minecraft.player != null) {
            // 選ぶ品の名前はチャットに出る (押すと選べる)。チャットが見えるよう画面を閉じる
            minecraft.player.connection.sendCommand("titanmemory");
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY) * -12;
        if (mouseX < left + 154) {
            listScroll.put(tab, Mth.clamp(scroll() + step, 0, maxListScroll()));
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
                int view = LIST_BOTTOM - LIST_TOP;
                int s = scroll();
                if (y < s) {
                    s = y;
                } else if (y + rowHeight(r) > s + view) {
                    s = y + rowHeight(r) - view;
                }
                listScroll.put(tab, Mth.clamp(s, 0, maxListScroll()));
                return;
            }
            y += rowHeight(r);
        }
    }
}
