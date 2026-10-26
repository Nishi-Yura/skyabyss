package jp.revati.skyabyss.client.quest;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.lore.LoreWrap;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

// クエストログ (U)。左に一覧 (メイン・サブ・終えたもの)、右に選んだクエストの今の書き付け、目的、報酬。
// 「やること」を書く帳面で、「知ったこと」は伝承の書に分ける。下の釦で、左の小さな表示に出すクエストを選ぶ。
// 一覧と右の頁は、どちらも目盛りつきの送り (ホイール・つまみ・キー)。窓は画面に合わせて広がる (2026-10-22 の部屋の見直し)。
// 章の見出し (「· 一章　岩鯨」) を押すと、その下の最初のクエストを開く。
// 終えたクエストの頁: 題・章・依頼主・受けた日と終えた日・終わりの書き付け・やったこと (段ごとの目的を ■ で)・受け取った報酬
public class QuestLogScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_W = 400;
    private static final int MAX_H = 260;
    private static final int LIST_W = 112;
    private static final int ROW_H = 12;
    private static final int LINE_H = 11;
    private static final int PANEL = 0xFA101318;
    private static final int EDGE = 0xFF2E5552;
    private static final int EDGE_IN = 0xFF17302F;
    private static final int TEAL = 0xFF7FD6D0;
    private static final int GOLD = 0xFFF2C14E;
    private static final int TEXT = 0xFFE9E3D4;
    private static final int SOFT = 0xFF8C8CA0;
    private static final int DIM = 0xFF5C5C6C;

    private int w;
    private int h;
    private int left;
    private int top;
    @Nullable
    private String selected;
    // 一覧と右の頁 (どちらも目盛りつきで送る)
    private final ScrollPanel listPanel = new ScrollPanel(ROW_H * 3);
    private final ScrollPanel bodyPanel = new ScrollPanel(LINE_H * 3);
    private final List<Row> rows = new ArrayList<>();
    private final List<BodyLine> body = new ArrayList<>();
    private Button trackButton;

    // jump: 見出しを押したときに開くクエスト (章の見出しだけ。ほかは null)
    private record Row(@Nullable String quest, Component label, int color, @Nullable String jump) {
        Row(@Nullable String quest, Component label, int color) {
            this(quest, label, color, null);
        }
    }

    private record BodyLine(String text, int color, int indent) {
    }

    public QuestLogScreen() {
        super(Component.translatable("gui.skyabyss.quest.log"));
    }

    // 窓の大きさと場所、一覧と頁の見える所 (画面が変わったら、ここで決め直す)
    private void layout() {
        w = Math.min(MAX_W, width - 16);
        h = Math.min(MAX_H, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listPanel.setViewport(left + 8, top + 26, LIST_W - 2, h - 26 - 24);
        int bx = left + LIST_W + 18;
        bodyPanel.setViewport(bx, top + 40, left + w - 12 - bx, h - 40 - 24);
    }

    private int bodyLeft() {
        return left + LIST_W + 18;
    }

    @Override
    protected void init() {
        layout();
        trackButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleTrack())
                .bounds(left + w - 98, top + h - 24, 88, 16).build());
        rebuild();
        if (selected == null) {
            QuestLog log = log();
            selected = log != null && !log.tracked().isEmpty() ? log.tracked() : firstQuest();
        }
        if (selected != null) {
            reveal(selected);
        }
        rebuildBody();
    }

    @Nullable
    private QuestLog log() {
        return Minecraft.getInstance().player == null ? null : QuestLog.of(Minecraft.getInstance().player);
    }

    @Nullable
    private String firstQuest() {
        for (Row r : rows) {
            if (r.quest() != null) {
                return r.quest();
            }
        }
        return null;
    }

    // 一覧: メイン (進行中)、サブ (進行中)、終えたもの
    private void rebuild() {
        rows.clear();
        QuestLog log = log();
        if (log == null) {
            syncList();
            return;
        }
        List<QuestDef> main = new ArrayList<>();
        List<QuestDef> sub = new ArrayList<>();
        List<QuestDef> done = new ArrayList<>();
        for (QuestDef def : QuestClient.all()) {
            QuestLog.Entry e = log.entry(def.id());
            if (e == null) {
                continue;
            }
            (e.done ? done : def.main() ? main : sub).add(def);
        }
        section("gui.skyabyss.quest.section.main", main, GOLD);
        section("gui.skyabyss.quest.section.sub", sub, TEAL);
        section("gui.skyabyss.quest.section.done", done, SOFT);
        // 一つ先の段階で開く頼みごとを、題を伏せて見せる (先があると分かるように。中身は明かさない。二つ先は出さない)
        int stage = jp.revati.skyabyss.party.client.PartyClient.state().stage();
        int ahead = 0;
        for (QuestDef def : QuestClient.all()) {
            if (!def.board() && !def.main() && def.minStage() == stage + 1 && log.entry(def.id()) == null) {
                ahead++;
            }
        }
        if (ahead > 0) {
            rows.add(new Row(null, Component.translatable("gui.skyabyss.quest.locked." + (stage + 1)), DIM));
            for (int i = 0; i < ahead; i++) {
                rows.add(new Row(null, Component.translatable("gui.skyabyss.quest.hidden_row"), DIM));
            }
        }
        syncList();
    }

    private void syncList() {
        listPanel.setContentHeight(rows.size() * ROW_H);
    }

    private void section(String key, List<QuestDef> list, int color) {
        if (list.isEmpty()) {
            return;
        }
        rows.add(new Row(null, Component.translatable(key), DIM));
        // 章の欄があるクエストは章ごとの見出し (「一章　岩鯨」) の下に並べる。章のないものが先、章は番号順。一覧の中の順 (order) は保つ
        jp.revati.skyabyss.story.Chapters current = null;
        boolean grouped = list.stream().anyMatch(d -> d.chapterOrNull() != null);
        List<QuestDef> sorted = new ArrayList<>(list);
        sorted.sort(java.util.Comparator.comparingInt(d -> d.chapterOrNull() == null ? -1 : d.chapterOrNull().number()));
        for (QuestDef def : sorted) {
            jp.revati.skyabyss.story.Chapters ch = def.chapterOrNull();
            if (grouped && ch != null && ch != current) {
                rows.add(new Row(null, Component.literal("· ").append(ch.label()), SOFT, def.id()));
            }
            current = ch;
            // 章の支度の段は、親のメイン (雲の下の歌など) の下の一歩として字下げで見せる (2026-10-22 の試遊: メインが並んで迷った)
            Component title = jp.revati.skyabyss.guide.PrepQuests.chapterOf(def.id()) > 0
                    ? Component.literal(" └ ").append(Component.translatable(def.titleKey())) : Component.translatable(def.titleKey());
            rows.add(new Row(def.id(), title, color));
        }
    }

    // 右の頁: 題、依頼主、書き付け、目的、報酬
    private void rebuildBody() {
        body.clear();
        bodyPanel.setScroll(0);
        Font font = Minecraft.getInstance().font;
        int bw = bodyPanel.width();
        QuestLog log = log();
        QuestDef def = selected == null ? null : QuestClient.quest(selected);
        QuestLog.Entry entry = log == null || selected == null ? null : log.entry(selected);
        updateTrackButton(def, entry, log);
        bodySync();
        if (def == null || entry == null) {
            return;
        }
        if (def.chapterOrNull() != null) {
            body.add(new BodyLine(def.chapterOrNull().label().getString(), SOFT, 0));
        }
        if (def.giver() != null) {
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.giver",
                    Component.translatable("entity." + def.giver().replace(':', '.'))).getString(), SOFT, 0));
        } else if (def.board()) {
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.from_board").getString(), SOFT, 0));
        }
        if (def.repeats()) {
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.repeat_note." + def.repeat()).getString(), TEAL, 0));
        }
        if (entry.done) {
            String days = daysText(entry);
            if (!days.isEmpty()) {
                body.add(new BodyLine(days, SOFT, 0));
            }
        }
        body.add(new BodyLine("", TEXT, 0));
        String journal = entry.done ? doneText(def)
                : Component.translatable(def.journalKey(def.stages().get(Math.min(entry.stage, def.stages().size() - 1)))).getString();
        for (String l : LoreWrap.wrap(journal, bw, font::width)) {
            body.add(new BodyLine(l, TEXT, 0));
        }
        body.add(new BodyLine("", TEXT, 0));
        if (!entry.done && entry.stage < def.stages().size()) {
            QuestDef.Stage stage = def.stages().get(entry.stage);
            body.add(new BodyLine(Component.translatable(stage.any() ? "gui.skyabyss.quest.objectives_any" : "gui.skyabyss.quest.objectives")
                    .getString(), TEAL, 0));
            for (int i = 0; i < stage.objectives().size(); i++) {
                QuestDef.Objective ob = stage.objectives().get(i);
                int have = Math.min(entry.progress(i), ob.count());
                boolean ok = have >= ob.count();
                if (!ok && ob.type().equals("turn_in") && Minecraft.getInstance().player != null) {
                    // 納める品は、手持ちの数を見せる (納めるまでは済みにしない)
                    have = Math.min(jp.revati.skyabyss.quest.QuestItems.count(Minecraft.getInstance().player, ob.targets().get(0), ob.token()), ob.count());
                }
                String text = QuestHud.objectiveText(def, stage, ob);
                List<String> wrapped = QuestHud.wrapWithCount(text, ob.count() > 1 ? have + "/" + ob.count() : "", bw - 12, font);
                for (int k = 0; k < wrapped.size(); k++) {
                    body.add(new BodyLine((k == 0 ? (ok ? "■ " : "□ ") : "") + wrapped.get(k), ok ? SOFT : TEXT, k == 0 ? 0 : 11));
                }
            }
            body.add(new BodyLine("", TEXT, 0));
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.progress", entry.stage + 1, def.stages().size()).getString(), DIM, 0));
        } else {
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.done_steps").getString(), TEAL, 0));
            String or = Component.translatable("gui.skyabyss.quest.or").getString();
            for (jp.revati.skyabyss.quest.QuestDoneView.Line d : jp.revati.skyabyss.quest.QuestDoneView.lines(def)) {
                String text = Component.translatable(d.key()).getString();
                List<String> wrapped = QuestHud.wrapWithCount(d.alternative() ? or + " " + text : text, d.count(), bw - 12, font);
                for (int k = 0; k < wrapped.size(); k++) {
                    String head = k > 0 ? "" : d.alternative() ? "　" : "■ ";
                    body.add(new BodyLine(head + wrapped.get(k), SOFT, k == 0 ? 0 : 11));
                }
            }
            body.add(new BodyLine("", TEXT, 0));
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.finished").getString(), SOFT, 0));
        }
        String reward = rewardText(def);
        if (!reward.isEmpty()) {
            String key = entry.done ? "gui.skyabyss.quest.reward_got" : "gui.skyabyss.quest.reward";
            for (String l : LoreWrap.wrap(Component.translatable(key, reward).getString(), bw, font::width)) {
                body.add(new BodyLine(l, GOLD, 0));
            }
        }
        bodySync();
    }

    private void bodySync() {
        bodyPanel.setContentHeight(body.size() * LINE_H);
    }

    // 終えたクエストの書き付け (終わりの文。無ければ最後の段の書き付け。quest.QuestDoneView)
    static String doneText(QuestDef def) {
        net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
        return Component.translatable(jp.revati.skyabyss.quest.QuestDoneView.journalKey(def, lang::has)).getString();
    }

    // 受けた日と終えた日 (記録が無ければ空)
    static String daysText(QuestLog.Entry entry) {
        long[] d = jp.revati.skyabyss.quest.QuestDoneView.days(entry);
        if (d == null) {
            return "";
        }
        return d[0] < d[1]
                ? Component.translatable("gui.skyabyss.quest.days", d[0], d[1]).getString()
                : Component.translatable("gui.skyabyss.quest.day_done", d[1]).getString();
    }

    static String rewardText(QuestDef def) {
        List<String> parts = new ArrayList<>();
        int xp = def.reward().xp();
        for (QuestDef.Stage s : def.stages()) {
            xp += s.xp();
        }
        if (xp > 0) {
            parts.add(Component.translatable("gui.skyabyss.quest.reward_xp", xp).getString());
        }
        for (QuestDef.ItemReward r : def.reward().items()) {
            ResourceLocation key = ResourceLocation.tryParse(r.item());
            Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            if (item != null) {
                String name = r.name() != null ? Component.translatable(r.name()).getString() : item.getDescription().getString();
                parts.add(name + (r.count() > 1 ? " ×" + r.count() : ""));
            }
        }
        return String.join(Component.translatable("gui.skyabyss.quest.reward_sep").getString(), parts);
    }

    private void updateTrackButton(@Nullable QuestDef def, @Nullable QuestLog.Entry entry, @Nullable QuestLog log) {
        if (trackButton == null) {
            return;
        }
        boolean active = def != null && entry != null && !entry.done;
        trackButton.visible = active;
        if (active && log != null) {
            trackButton.setMessage(Component.translatable(log.tracked().equals(def.id()) ? "gui.skyabyss.quest.untrack" : "gui.skyabyss.quest.track"));
        }
    }

    private void toggleTrack() {
        QuestLog log = log();
        if (log == null || selected == null) {
            return;
        }
        String next = log.tracked().equals(selected) ? "" : selected;
        PacketDistributor.sendToServer(new QuestNetwork.Track(next));
        // 返事 (記録の同期) を待たずに釦の字を替える
        trackButton.setMessage(Component.translatable(next.isEmpty() ? "gui.skyabyss.quest.track" : "gui.skyabyss.quest.untrack"));
    }

    @Override
    public void tick() {
        // 記録が変わったら (目的が進んだ等) 作り直す
        QuestLog log = log();
        if (log != null) {
            int hash = log.hashCode() * 31 + log.tracked().hashCode() + log.quests().values().stream().mapToInt(QuestLog.Entry::hashCode).sum();
            if (hash != lastHash) {
                lastHash = hash;
                rebuild();
                int scroll = bodyPanel.scroll();
                rebuildBody();
                bodyPanel.setScroll(scroll);
            }
        }
    }

    private int lastHash;

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 枠と中身は背景として描き、その上に釦 (super.render の部品) が乗る
        layout();
        g.fill(0, 0, width, height, 0x80000000);
        drawPanel(g, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        g.fill(left, top, left + w, top + h, PANEL);
        g.renderOutline(left, top, w, h, EDGE);
        g.renderOutline(left + 2, top + 2, w - 4, h - 4, EDGE_IN);
        g.drawString(font, title, left + 10, top + 8, TEAL, false);
        g.fill(left + 8, top + 19, left + w - 8, top + 20, EDGE_IN);
        // 縦の仕切りは下の一行 (伝承の書への案内) の上で止める。案内を左の列に押し込むと「伝承の書／に。」と切れるため
        g.fill(left + LIST_W + 10, top + 24, left + LIST_W + 11, top + h - 26, EDGE_IN);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.quest.empty"), left + w / 2, top + h / 2 - 4, SOFT);
            return;
        }
        // 一覧 (目盛りつき。見える行だけ描く)
        listPanel.begin(g);
        for (int ri = 0; ri < rows.size(); ri++) {
            int y = listPanel.top() + ri * ROW_H;
            if (y + ROW_H < listPanel.y() || y > listPanel.y() + listPanel.height()) {
                continue;
            }
            Row r = rows.get(ri);
            if (r.quest() == null) {
                boolean hover = r.jump() != null && inList(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(r.label().getString()), left + 10, y + 2, listTextW(), hover ? TEXT : r.color(), false);
            } else {
                boolean sel = r.quest().equals(selected);
                boolean hover = inList(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
                if (sel || hover) {
                    g.fill(left + 8, y, left + LIST_W + 6, y + ROW_H, sel ? 0x502E8F88 : 0x281E5552);
                }
                String label = font.plainSubstrByWidth(r.label().getString(), listTextW() - 4);
                g.drawString(font, label, left + 16, y + 2, r.color(), false);
                QuestLog log = log();
                if (log != null && r.quest().equals(log.tracked())) {
                    g.drawString(font, "▸", left + 9, y + 2, r.color(), false);
                }
            }
        }
        listPanel.end(g);
        listPanel.drawBar(g, EDGE_IN, 0x887FD6D0);
        // 右の頁
        QuestDef def = selected == null ? null : QuestClient.quest(selected);
        int bx = bodyLeft();
        if (def != null) {
            g.drawString(font, Component.translatable(def.titleKey()), bx, top + 26, def.main() ? GOLD : TEAL, false);
            bodyPanel.begin(g);
            for (int i = 0; i < body.size(); i++) {
                int y = bodyPanel.top() + i * LINE_H;
                if (y + LINE_H < bodyPanel.y() || y > bodyPanel.y() + bodyPanel.height()) {
                    continue;
                }
                BodyLine l = body.get(i);
                g.drawString(font, l.text(), bx + l.indent(), y, l.color(), false);
            }
            bodyPanel.end(g);
            bodyPanel.drawBar(g, EDGE_IN, 0x887FD6D0);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.quest.lore_hint"), left + 10, top + h - 19, DIM, false);
    }

    // 一覧の字の幅 (送りの印が出るときは、その分だけ短く切る)
    private int listTextW() {
        return LIST_W - 12;
    }

    private boolean inList(double mx, double my) {
        return listPanel.contains(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inList(mx, my) && button == 0) {
            int ri = (int) Math.floor((my - listPanel.top()) / ROW_H);
            if (ri >= 0 && ri < rows.size()) {
                Row r = rows.get(ri);
                String target = r.quest() != null ? r.quest() : r.jump();
                if (target != null) {
                    select(target);
                    return true;
                }
            }
        }
        return listPanel.mouseClicked(mx, my, button) || bodyPanel.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return listPanel.mouseDragged(mx, my, button, dx, dy) || bodyPanel.mouseDragged(mx, my, button, dx, dy)
                || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return listPanel.mouseReleased(mx, my, button) || bodyPanel.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    // 選んだクエストの行が一覧の見える所に入るよう送る (真上が章の見出しなら、見出しごと見せる)
    private void reveal(String quest) {
        for (int i = 0; i < rows.size(); i++) {
            if (quest.equals(rows.get(i).quest())) {
                int at = i > 0 && rows.get(i - 1).quest() == null ? i - 1 : i;
                int top = at * ROW_H;
                int view = listPanel.height();
                if (top < listPanel.scroll()) {
                    listPanel.setScroll(top);
                } else if (i * ROW_H + ROW_H > listPanel.scroll() + view) {
                    listPanel.setScroll(i * ROW_H + ROW_H - view);
                }
                return;
            }
        }
    }

    private void select(String quest) {
        reveal(quest);
        if (!quest.equals(selected)) {
            selected = quest;
            rebuildBody();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.4f));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (bodyPanel.mouseScrolled(mx, my, dy) || listPanel.mouseScrolled(mx, my, dy)) {
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (QuestClient.LOG_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            List<String> ids = rows.stream().map(Row::quest).filter(q -> q != null).toList();
            if (!ids.isEmpty()) {
                int i = selected == null ? -1 : ids.indexOf(selected);
                i = Mth.clamp(i + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, ids.size() - 1);
                select(ids.get(i));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            return bodyPanel.keyPressed(key) || super.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public int[] textBounds() {
        layout();
        return new int[] {left, top, w, h};
    }
}
