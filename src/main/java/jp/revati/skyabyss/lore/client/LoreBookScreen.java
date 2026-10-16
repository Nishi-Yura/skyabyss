package jp.revati.skyabyss.lore.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LoreCatalog;
import jp.revati.skyabyss.lore.LoreChapter;
import jp.revati.skyabyss.lore.LoreEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

// 伝承の書の画面。見開きの古い帳面で、上に章のしおり、左の頁に項目の一覧、右の頁に本文。
// まだ記されていない項目は題を「？？？」、本文をにじんだ墨の帯で伏せ、手がかりを一行だけ添える。
// テクスチャは 32px 密度 (GUI の 1 単位 = 2 画素) で描いてある (tools/gen_lore_book.py)
public class LoreBookScreen extends Screen {
    static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/lore/book.png");
    static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/lore/widgets.png");
    private static final int BOOK_TEX_W = 608;
    private static final int BOOK_TEX_H = 384;
    private static final int WIDGETS_TEX_W = 512;
    private static final int WIDGETS_TEX_H = 256;

    // widgets.png の中の絵 (画素で u, v, 幅, 高さ。画面には半分の大きさで描く)
    record Sprite(int u, int v, int w, int h) {
    }

    static final Sprite W_TAB_OFF = new Sprite(0, 0, 104, 36);
    static final Sprite W_TAB_ON = new Sprite(104, 0, 104, 48);
    static final Sprite W_MARK_KNOWN = new Sprite(208, 0, 16, 16);
    static final Sprite W_MARK_LOCKED = new Sprite(224, 0, 16, 16);
    static final Sprite W_MARK_NEW = new Sprite(240, 0, 16, 16);
    static final Sprite W_ARROW_UP = new Sprite(256, 0, 16, 16);
    static final Sprite W_ARROW_DOWN = new Sprite(272, 0, 16, 16);
    static final Sprite W_ROW_SELECTED = new Sprite(0, 56, 256, 24);
    static final Sprite W_INK_BAR = new Sprite(0, 88, 256, 12);
    static final Sprite W_TOAST = new Sprite(0, 104, 320, 64);

    // 墨の色
    static final int INK = 0xFF3B2A1C;
    static final int FADED = 0xFF8E7B5E;
    static final int TEAL = 0xFF1D6E69;
    static final int TITLE = 0xFF184F55;
    static final int PAPER = 0xFFF2E6C8;
    // まだ何も記されていない章のしおりの字
    static final int TAB_LOCKED = 0xFF5E5040;

    // 見開きの大きさと、頁の内側 (書の左上からの位置)
    private static final int BW = 304;
    private static final int BH = 192;
    private static final int TAB_W = 52;
    private static final int TAB_STEP = 56;
    private static final int TAB_X0 = 14;
    private static final int LEFT_X = 18;
    private static final int LEFT_W = 126;
    private static final int RIGHT_X = 160;
    private static final int RIGHT_W = 126;
    private static final int HEADER_Y = 16;
    private static final int LIST_Y = 33;
    private static final int ROW_H = 11;
    private static final int LIST_ROWS = 12;
    private static final int BODY_Y = 33;
    private static final int BODY_BOTTOM = 180;
    private static final int COUNT_Y = 176;
    private static final int LINE_H = 10;
    private static final int GAP_H = 5;

    private int left;
    private int top;
    private int chapterIndex;
    @Nullable
    private String selected;
    private int listScroll;
    private int bodyScroll;
    // 右の頁の行 (描くたびに作り直さないよう、選び直したときだけ作る)
    private final List<Line> lines = new ArrayList<>();

    // 右の頁の一行。link があれば、押すとその項目へ移る
    private record Line(FormattedCharSequence text, int color, int indent, boolean bar, int height, @Nullable String link) {
    }

    public LoreBookScreen() {
        super(Component.translatable("item.skyabyss.lore_book"));
    }

    @Override
    protected void init() {
        left = (width - BW) / 2;
        top = (height - BH) / 2 + 8;
        if (selected == null) {
            String start = LoreClient.newestUnread();
            if (start == null) {
                start = LoreClient.lastSelected;
            }
            if (start == null || LoreClient.catalog().entry(start) == null) {
                start = firstKnown();
            }
            select(start, false);
        } else {
            rebuildLines();
        }
    }

    // サーバーから新しい記録が届いたとき
    void refresh() {
        if (selected != null && LoreClient.catalog().entry(selected) == null) {
            selected = null;
        }
        if (selected == null) {
            select(firstKnown(), false);
        } else {
            LoreClient.markSeen(selected);
            rebuildLines();
        }
    }

    @Nullable
    private String firstKnown() {
        for (LoreEntry entry : LoreClient.catalog().entries()) {
            if (LoreClient.unlocked(entry.id())) {
                return entry.id();
            }
        }
        List<LoreEntry> all = LoreClient.catalog().entries();
        return all.isEmpty() ? null : all.get(0).id();
    }

    private void select(@Nullable String id, boolean sound) {
        LoreCatalog catalog = LoreClient.catalog();
        LoreEntry entry = id == null ? null : catalog.entry(id);
        selected = entry == null ? null : entry.id();
        bodyScroll = 0;
        if (entry != null) {
            List<LoreChapter> chapters = catalog.chapters();
            for (int i = 0; i < chapters.size(); i++) {
                if (chapters.get(i).id().equals(entry.chapter())) {
                    chapterIndex = i;
                }
            }
            LoreClient.lastSelected = entry.id();
            LoreClient.markSeen(entry.id());
            // 選んだ行が一覧の外なら、見える所まで送る
            int row = chapterEntries().indexOf(entry);
            if (row >= 0) {
                if (row < listScroll) {
                    listScroll = row;
                } else if (row >= listScroll + LIST_ROWS) {
                    listScroll = row - LIST_ROWS + 1;
                }
            }
            if (sound && minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.7f));
            }
        }
        rebuildLines();
    }

    @Nullable
    private LoreChapter chapter() {
        List<LoreChapter> chapters = LoreClient.catalog().chapters();
        if (chapters.isEmpty()) {
            return null;
        }
        chapterIndex = Math.max(0, Math.min(chapterIndex, chapters.size() - 1));
        return chapters.get(chapterIndex);
    }

    private List<LoreEntry> chapterEntries() {
        LoreChapter chapter = chapter();
        return chapter == null ? List.of() : LoreClient.catalog().inChapter(chapter.id());
    }

    private boolean chapterKnown(LoreChapter chapter) {
        for (LoreEntry entry : LoreClient.catalog().inChapter(chapter.id())) {
            if (LoreClient.unlocked(entry.id())) {
                return true;
            }
        }
        return false;
    }

    private boolean chapterHasNew(LoreChapter chapter) {
        for (LoreEntry entry : LoreClient.catalog().inChapter(chapter.id())) {
            if (LoreClient.isNew(entry.id())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 右の頁の行を組む
    private void rebuildLines() {
        lines.clear();
        if (font == null) {
            return;
        }
        LoreEntry entry = selected == null ? null : LoreClient.catalog().entry(selected);
        if (entry == null) {
            return;
        }
        if (!LoreClient.unlocked(entry.id())) {
            // 伏せ字: にじんだ墨の帯を数本 (項目ごとに長さが決まる)。手がかりを一行
            long seed = entry.id().hashCode() * 0x9E3779B97F4A7C15L;
            int bars = entry.placeholder() ? 3 : 5;
            for (int i = 0; i < bars; i++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int w = 50 + (int) ((seed >>> 33) % 70);
                if (i == bars - 1) {
                    w = Math.min(w, 60);
                }
                lines.add(new Line(FormattedCharSequence.EMPTY, w, 0, true, LINE_H, null));
            }
            lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0, false, GAP_H, null));
            addWrapped(Component.translatable(entry.hintKey()).withStyle(s -> s.withItalic(true)), FADED, 0, false, null);
            return;
        }
        List<String> inscription = entry.inscriptionKeys();
        if (!inscription.isEmpty()) {
            // 碑文の写し: 看板の行はそのまま一行ずつ (空の行は飛ばす)
            StringBuilder text = new StringBuilder();
            for (String key : inscription) {
                String part = Component.translatable(key).getString().strip();
                if (!part.isEmpty()) {
                    text.append(text.length() > 0 ? "\n" : "").append(part);
                }
            }
            addWrapped(Component.literal(text.toString()), TEAL, 6, true, null);
            lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0, false, GAP_H, null));
        }
        addWrapped(Component.translatable(entry.bodyKey()), INK, 0, false, null);
        List<String> related = entry.related();
        if (!related.isEmpty()) {
            lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0, false, GAP_H, null));
            addWrapped(Component.translatable("gui.skyabyss.lore.related"), FADED, 0, false, null);
            for (String id : related) {
                LoreEntry other = LoreClient.catalog().entry(id);
                if (other == null) {
                    continue;
                }
                boolean known = LoreClient.unlocked(id);
                Component name = known ? Component.literal("› ").append(Component.translatable(other.titleKey()))
                        : Component.literal("› ？？？");
                addWrapped(name, known ? TEAL : FADED, 4, false, known ? id : null);
            }
        }
    }

    // 日本語の禁則を守って折り返す (LoreWrap)。字の飾り (斜体など) は文全体で一つ
    private void addWrapped(Component text, int color, int indent, boolean bar, @Nullable String link) {
        net.minecraft.network.chat.Style style = text.getStyle();
        for (String line : jp.revati.skyabyss.lore.LoreWrap.wrap(text.getString(), RIGHT_W - indent, font::width)) {
            boolean empty = line.isEmpty();
            FormattedCharSequence seq = Component.literal(line).withStyle(style).getVisualOrderText();
            lines.add(new Line(seq, color, indent, bar && !empty, empty ? GAP_H : lineHeight(line), link));
        }
    }

    // 漢字や仮名の行は 10、英字だけの行は 9 (バニラの行の高さ)。日本語は字が詰まって見えるので 1 だけ空ける
    private static int lineHeight(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) >= 0x2E80) {
                return LINE_H;
            }
        }
        return LINE_H - 1;
    }

    // ---------------------------------------------------------------- 描画
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<LoreChapter> chapters = LoreClient.catalog().chapters();
        // しおり (選んでいないもの) は書の後ろに
        for (int i = 0; i < chapters.size(); i++) {
            if (i != chapterIndex) {
                drawTab(g, i, chapters.get(i), false, mouseX, mouseY);
            }
        }
        g.blit(BOOK, left, top, BW, BH, 0, 0, BOOK_TEX_W, BOOK_TEX_H, BOOK_TEX_W, BOOK_TEX_H);
        LoreChapter chapter = chapter();
        if (chapter != null) {
            drawTab(g, chapterIndex, chapter, true, mouseX, mouseY);
            drawLeftPage(g, chapter, mouseX, mouseY);
        } else {
            g.drawString(font, Component.translatable("gui.skyabyss.lore.empty"), left + LEFT_X, top + HEADER_Y, FADED, false);
        }
        drawRightPage(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // ぼかしは掛けず、暗くするだけ (書の字を読みやすく)
        renderTransparentBackground(g);
    }

    private void drawTab(GuiGraphics g, int index, LoreChapter chapter, boolean on, int mouseX, int mouseY) {
        int x = left + TAB_X0 + index * TAB_STEP;
        int y = top - (on ? 18 : 15);
        blitWidget(g, x, y, on ? W_TAB_ON : W_TAB_OFF);
        boolean known = chapterKnown(chapter);
        Component label = Component.translatable(chapter.tabKey());
        int color = on ? TITLE : known ? INK : TAB_LOCKED;
        if (!on && hoverTab(index, mouseX, mouseY)) {
            color = TEAL;
        }
        g.drawString(font, label, x + (TAB_W - font.width(label)) / 2, y + 5, color, false);
        if (chapterHasNew(chapter)) {
            blitWidget(g, x + TAB_W - 9, y + 2, W_MARK_NEW);
        }
    }

    private boolean hoverTab(int index, double mouseX, double mouseY) {
        int x = left + TAB_X0 + index * TAB_STEP;
        return mouseX >= x && mouseX < x + TAB_W && mouseY >= top - 18 && mouseY < top + 1;
    }

    private void drawLeftPage(GuiGraphics g, LoreChapter chapter, int mouseX, int mouseY) {
        int x = left + LEFT_X;
        // 章の名は、その章の項目が一つでも記されるまで伏せる
        Component chapterName = chapterKnown(chapter) ? Component.translatable(chapter.nameKey()) : Component.literal("？？？");
        Component name = Component.translatable(chapter.tabKey()).append("　").append(chapterName);
        if (font.width(name) > LEFT_W) {
            name = chapterName;
        }
        g.drawString(font, name, x + (LEFT_W - font.width(name)) / 2, top + HEADER_Y, TITLE, false);
        List<LoreEntry> entries = chapterEntries();
        int known = 0;
        int total = 0;
        for (LoreEntry entry : entries) {
            if (!entry.placeholder()) {
                total++;
                if (LoreClient.unlocked(entry.id())) {
                    known++;
                }
            }
        }
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, entries.size() - LIST_ROWS)));
        for (int row = 0; row < LIST_ROWS && listScroll + row < entries.size(); row++) {
            LoreEntry entry = entries.get(listScroll + row);
            int y = top + LIST_Y + row * ROW_H;
            boolean unlocked = LoreClient.unlocked(entry.id());
            boolean hover = mouseX >= x && mouseX < x + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            if (entry.id().equals(selected)) {
                blitWidget(g, x - 2, y - 1, W_ROW_SELECTED);
            }
            blitWidget(g, x, y + 1, unlocked ? W_MARK_KNOWN : W_MARK_LOCKED);
            Component title = unlocked ? Component.translatable(entry.titleKey()) : Component.literal("？？？");
            FormattedText clipped = font.substrByWidth(title, LEFT_W - 22);
            // 選んだ行は濃い青緑の帯の上なので、羊皮紙の色で書く (墨の色では帯に沈んで読めない)
            boolean isSelected = entry.id().equals(selected);
            int color = !unlocked ? FADED : isSelected ? PAPER : hover ? TEAL : INK;
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(clipped), x + 11, y + 1, color, false);
            if (LoreClient.isNew(entry.id())) {
                blitWidget(g, x + LEFT_W - 9, y + 1, W_MARK_NEW);
            }
        }
        if (listScroll > 0) {
            // 上の矢印も一覧の下に並べる (一覧の上は見出しの二重線で、重なってしまう)
            blitWidget(g, x + LEFT_W - 18, top + LIST_Y + LIST_ROWS * ROW_H, W_ARROW_UP);
        }
        if (listScroll + LIST_ROWS < entries.size()) {
            blitWidget(g, x + LEFT_W - 8, top + LIST_Y + LIST_ROWS * ROW_H, W_ARROW_DOWN);
        }
        if (total > 0) {
            Component count = Component.translatable("gui.skyabyss.lore.count", known, total);
            g.drawString(font, count, x + (LEFT_W - font.width(count)) / 2, top + COUNT_Y, FADED, false);
        }
    }

    private void drawRightPage(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + RIGHT_X;
        LoreEntry entry = selected == null ? null : LoreClient.catalog().entry(selected);
        if (entry == null) {
            return;
        }
        boolean unlocked = LoreClient.unlocked(entry.id());
        Component title = unlocked ? Component.translatable(entry.titleKey()) : Component.literal("？？？");
        FormattedText clipped = font.substrByWidth(title, RIGHT_W);
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(clipped), x, top + HEADER_Y, unlocked ? TITLE : FADED, false);
        bodyScroll = Math.max(0, Math.min(bodyScroll, maxBodyScroll()));
        int y = top + BODY_Y;
        for (int i = bodyScroll; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (y + line.height() > top + BODY_BOTTOM) {
                break;
            }
            if (line.bar() && line.text() == FormattedCharSequence.EMPTY) {
                // 伏せ字の墨の帯 (color に幅を入れてある)
                drawInkBar(g, x, y + 2, line.color());
            } else {
                if (line.bar()) {
                    g.fill(x + 1, y - 1, x + 2, y + line.height() - 1, 0x801D6E69);
                }
                int color = line.color();
                if (line.link() != null && mouseX >= x && mouseX < x + RIGHT_W && mouseY >= y && mouseY < y + line.height()) {
                    color = TITLE;
                }
                g.drawString(font, line.text(), x + line.indent(), y, color, false);
            }
            y += line.height();
        }
        // 続きがあれば、文の右の余白に矢印
        if (bodyScroll > 0) {
            blitWidget(g, x + RIGHT_W + 1, top + BODY_Y, W_ARROW_UP);
        }
        if (bodyScroll < maxBodyScroll()) {
            blitWidget(g, x + RIGHT_W + 1, top + BODY_BOTTOM - 8, W_ARROW_DOWN);
        }
    }

    private void drawInkBar(GuiGraphics g, int x, int y, int width) {
        int w = Math.min(width, W_INK_BAR.w() / 2);
        g.blit(WIDGETS, x, y, w, W_INK_BAR.h() / 2, W_INK_BAR.u(), W_INK_BAR.v(), w * 2, W_INK_BAR.h(), WIDGETS_TEX_W, WIDGETS_TEX_H);
    }

    static void blitWidget(GuiGraphics g, int x, int y, Sprite s) {
        g.blit(WIDGETS, x, y, s.w() / 2, s.h() / 2, s.u(), s.v(), s.w(), s.h(), WIDGETS_TEX_W, WIDGETS_TEX_H);
    }

    // 右の頁を、一番下の行が下端に来るまで送れる
    private int maxBodyScroll() {
        int available = BODY_BOTTOM - BODY_Y;
        int total = 0;
        for (Line line : lines) {
            total += line.height();
        }
        if (total <= available) {
            return 0;
        }
        int skip = 0;
        int hidden = total - available;
        for (Line line : lines) {
            if (hidden <= 0) {
                break;
            }
            hidden -= line.height();
            skip++;
        }
        return skip;
    }

    // ---------------------------------------------------------------- 操作
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<LoreChapter> chapters = LoreClient.catalog().chapters();
            for (int i = 0; i < chapters.size(); i++) {
                if (hoverTab(i, mouseX, mouseY) && i != chapterIndex) {
                    openChapter(i);
                    return true;
                }
            }
            int x = left + LEFT_X;
            List<LoreEntry> entries = chapterEntries();
            for (int row = 0; row < LIST_ROWS && listScroll + row < entries.size(); row++) {
                int y = top + LIST_Y + row * ROW_H;
                if (mouseX >= x && mouseX < x + LEFT_W && mouseY >= y && mouseY < y + ROW_H) {
                    String id = entries.get(listScroll + row).id();
                    if (!id.equals(selected)) {
                        select(id, true);
                    }
                    return true;
                }
            }
            // 関連する項目へ
            int rx = left + RIGHT_X;
            int y = top + BODY_Y;
            for (int i = bodyScroll; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (y + line.height() > top + BODY_BOTTOM) {
                    break;
                }
                if (line.link() != null && mouseX >= rx && mouseX < rx + RIGHT_W && mouseY >= y && mouseY < y + line.height()) {
                    select(line.link(), true);
                    return true;
                }
                y += line.height();
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openChapter(int index) {
        chapterIndex = index;
        listScroll = 0;
        List<LoreEntry> entries = chapterEntries();
        String pick = null;
        for (LoreEntry entry : entries) {
            if (LoreClient.isNew(entry.id())) {
                pick = entry.id();
                break;
            }
        }
        if (pick == null) {
            for (LoreEntry entry : entries) {
                if (LoreClient.unlocked(entry.id())) {
                    pick = entry.id();
                    break;
                }
            }
        }
        if (pick == null && !entries.isEmpty()) {
            pick = entries.get(0).id();
        }
        select(pick, true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (mouseX < left + BW / 2.0) {
            listScroll = Math.max(0, Math.min(listScroll + step, Math.max(0, chapterEntries().size() - LIST_ROWS)));
        } else {
            bodyScroll = Math.max(0, Math.min(bodyScroll + step * 2, maxBodyScroll()));
        }
        return true;
    }

    // ↑↓ で項目、←→ で章を移る
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<LoreEntry> entries = chapterEntries();
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(selected)) {
                index = i;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN && index + 1 < entries.size()) {
            select(entries.get(index + 1).id(), true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP && index > 0) {
            select(entries.get(index - 1).id(), true);
            return true;
        }
        int chapters = LoreClient.catalog().chapters().size();
        if (keyCode == GLFW.GLFW_KEY_RIGHT && chapterIndex + 1 < chapters) {
            openChapter(chapterIndex + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT && chapterIndex > 0) {
            openChapter(chapterIndex - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
