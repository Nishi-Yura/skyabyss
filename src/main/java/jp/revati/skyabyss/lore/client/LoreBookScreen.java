package jp.revati.skyabyss.lore.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.BookPlate;
import jp.revati.skyabyss.lore.LoreCatalog;
import jp.revati.skyabyss.lore.LoreChapter;
import jp.revati.skyabyss.lore.LoreEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

// 伝承の書の画面。見開きの古い帳面で、上に章のしおり、左の頁に項目の一覧、右の頁に本文。
// まだ記されていない項目は題を「？？？」、本文をにじんだ墨の帯で伏せ、手がかりを一行だけ添える。
// 絵は鐘の銅の板 (client/ui/BookPlate。docs/design/56 の 2.1): 石と銅の表紙に生成りの頁、しおりは升
public class LoreBookScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    // 墨の色
    static final int INK = BookPlate.INK;
    static final int FADED = BookPlate.FADED;
    static final int TEAL = BookPlate.RUST;
    static final int TITLE = BookPlate.TITLE;

    // 見開きの大きさと、頁の内側 (書の左上からの位置)
    private static final int BW = 304;
    private static final int BH = 192;
    // 頁 (左右とも幅 143、上下は表紙の縁の内側)。のどは 150〜154
    private static final int PAGE_Y = 7;
    private static final int PAGE_H = 178;
    private static final int LPAGE_X = 7;
    private static final int RPAGE_X = 154;
    private static final int PAGE_W = 143;
    // 章は 8 枚 (序章・一〜六章・終章)。しおりは書の上に、頁の左の端から重ねずに並べる (幅 32、あいだ 2)
    private static final int TAB_W = 32;
    private static final int TAB_H = 13;
    private static final int TAB_STEP = 34;
    private static final int TAB_X0 = LPAGE_X;
    private static final int LEFT_X = 18;
    private static final int LEFT_W = 126;
    private static final int RIGHT_X = 160;
    private static final int RIGHT_W = 126;
    private static final int HEADER_Y = 16;
    // 見出しの下の線
    private static final int RULE_Y = 27;
    private static final int LIST_Y = 33;
    // 行は 13 (docs/design/56。紙の本の形はそのまま)
    private static final int ROW_H = 13;
    private static final int LIST_ROWS = 10;
    private static final int BODY_Y = 33;
    private static final int BODY_BOTTOM = 180;
    private static final int COUNT_Y = 174;
    private static final int LINE_H = 13;
    private static final int GAP_H = 5;

    private int left;
    private int top;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
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
        // 書は論理の (0, 24) に置き、窓に合わせた変換 (fit) で大きく描く。上の段 (しおり) の分を上に残す
        left = 0;
        top = 24;
        fit.fit(width, height, BW, top + BH + 8, 2.5f);
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

    // 漢字や仮名の行は 13、英字だけの行は 12 (docs/design/56 の行の高さ)
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
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        // 書は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)。釦などの部品は書の上に
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        List<LoreChapter> chapters = LoreClient.catalog().chapters();
        drawBook(g);
        // しおりは重ならないので、どれも同じ順で描く
        for (int i = 0; i < chapters.size(); i++) {
            drawTab(g, i, chapters.get(i), i == chapterIndex, mouseX, mouseY);
        }
        LoreChapter chapter = chapter();
        if (chapter != null) {
            drawLeftPage(g, chapter, mouseX, mouseY);
        } else {
            g.drawString(font, Component.translatable("gui.skyabyss.lore.empty"), left + LEFT_X, top + HEADER_Y, FADED, false);
        }
        drawRightPage(g, mouseX, mouseY);
        fit.end(g);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // ぼかしは掛けず、暗くするだけ (書の字を読みやすく)
        renderTransparentBackground(g);
    }

    // 表紙・見開きの頁・のど・見出しの線・しおり紐
    private void drawBook(GuiGraphics g) {
        BookPlate.cover(g, left, top, BW, BH);
        BookPlate.spine(g, left + LPAGE_X + PAGE_W, top + PAGE_Y, RPAGE_X - LPAGE_X - PAGE_W, PAGE_H);
        BookPlate.page(g, left + LPAGE_X, top + PAGE_Y, PAGE_W, PAGE_H, 1);
        BookPlate.page(g, left + RPAGE_X, top + PAGE_Y, PAGE_W, PAGE_H, -1);
        BookPlate.rule(g, left + LEFT_X, top + RULE_Y, LEFT_W);
        BookPlate.rule(g, left + RIGHT_X, top + RULE_Y, RIGHT_W);
        BookPlate.ribbon(g, left + RPAGE_X + 82, top + PAGE_Y + PAGE_H, top + BH + 5);
    }

    // しおり (章の名の升) と NEW の印。書の上の端に載る
    private void drawTab(GuiGraphics g, int index, LoreChapter chapter, boolean on, int mouseX, int mouseY) {
        int x = left + TAB_X0 + index * TAB_STEP;
        int y = top - TAB_H;
        Component label = Component.translatable(chapter.tabKey());
        boolean hover = !on && hoverTab(index, mouseX, mouseY);
        BookPlate.tab(g, font, x, y, TAB_W, TAB_H, label, on, hover, !chapterKnown(chapter));
        if (chapterHasNew(chapter)) {
            BookPlate.mark(g, x + TAB_W - 8, y - 4, BookPlate.MARK_NEW);
        }
    }

    private boolean hoverTab(int index, double mouseX, double mouseY) {
        int x = left + TAB_X0 + index * TAB_STEP;
        return mouseX >= x && mouseX < x + TAB_W && mouseY >= top - TAB_H - 1 && mouseY < top;
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
                BookPlate.row(g, x - 2, y, LEFT_W + 2, ROW_H - 1, true);
            }
            BookPlate.mark(g, x, y + 1, unlocked ? BookPlate.MARK_KNOWN : BookPlate.MARK_LOCKED);
            Component title = unlocked ? Component.translatable(entry.titleKey()) : Component.literal("？？？");
            FormattedText clipped = font.substrByWidth(title, LEFT_W - 22);
            boolean isSelected = entry.id().equals(selected);
            int color = !unlocked ? FADED : isSelected ? TITLE : hover ? TEAL : INK;
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(clipped), x + 11, y + 3, color, false);
            if (LoreClient.isNew(entry.id())) {
                BookPlate.mark(g, x + LEFT_W - 9, y + 1, BookPlate.MARK_NEW);
            }
        }
        if (listScroll > 0) {
            // 上の矢印も一覧の下に並べる (一覧の上は見出しの二重線で、重なってしまう)
            BookPlate.arrow(g, x + LEFT_W - 18, top + LIST_Y + LIST_ROWS * ROW_H, true, INK);
        }
        if (listScroll + LIST_ROWS < entries.size()) {
            BookPlate.arrow(g, x + LEFT_W - 8, top + LIST_Y + LIST_ROWS * ROW_H, false, INK);
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
                drawInkBar(g, x, y + 4, line.color());
            } else {
                if (line.bar()) {
                    g.fill(x + 1, y - 1, x + 2, y + line.height() - 1, 0x8083502A);
                }
                int color = line.color();
                if (line.link() != null && mouseX >= x && mouseX < x + RIGHT_W && mouseY >= y && mouseY < y + line.height()) {
                    color = TITLE;
                }
                g.drawString(font, line.text(), x + line.indent(), y + 2, color, false);
            }
            y += line.height();
        }
        // 続きがあれば、文の右の余白に矢印
        if (bodyScroll > 0) {
            BookPlate.arrow(g, x + RIGHT_W + 1, top + BODY_Y, true, INK);
        }
        if (bodyScroll < maxBodyScroll()) {
            BookPlate.arrow(g, x + RIGHT_W + 1, top + BODY_BOTTOM - 8, false, INK);
        }
        // 送りの目盛り (細い帯。矢印の間に、いまの見える所を示す)
        int max = maxBodyScroll();
        if (max > 0) {
            int track = BODY_BOTTOM - BODY_Y;
            int shown = Math.max(1, lines.size() - max);
            int thumb = Math.max(8, track * shown / Math.max(1, lines.size()));
            int ty = top + BODY_Y + (track - thumb) * bodyScroll / max;
            g.fill(x + RIGHT_W + 4, top + BODY_Y, x + RIGHT_W + 6, top + BODY_BOTTOM, 0x40000000);
            g.fill(x + RIGHT_W + 4, ty, x + RIGHT_W + 6, ty + thumb, 0x90 << 24 | 0x83502A);
        }
    }

    private void drawInkBar(GuiGraphics g, int x, int y, int width) {
        BookPlate.inkBar(g, x, y, Math.min(width, RIGHT_W));
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
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = fit.x(screenX);
        double mouseY = fit.y(screenY);
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
    public boolean mouseScrolled(double screenX, double screenY, double scrollX, double scrollY) {
        double mouseX = fit.x(screenX);
        double mouseY = fit.y(screenY);
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

    @Override
    public int[] textBounds() {
        // 上のしおり (章の名) も文字の範囲に入れる (top - 4 で切っていて、章の名が読めなかった)
        return fit.screenRect(left - 40, top - 24, BW + 80, BH + 28);
    }
}
