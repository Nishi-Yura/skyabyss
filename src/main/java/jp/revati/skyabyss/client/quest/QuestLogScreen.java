package jp.revati.skyabyss.client.quest;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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

// クエストログ (K)。左に一覧 (メイン・サブ・終えたもの)、右に選んだクエストの今の書き付け、目的、報酬。
// 「やること」を書く帳面で、「知ったこと」は伝承の書に分ける。下の釦で、左の小さな表示に出すクエストを選ぶ
public class QuestLogScreen extends Screen {
    private static final int W = 330;
    private static final int H = 206;
    private static final int LIST_W = 112;
    private static final int ROW_H = 12;
    private static final int LINE_H = 10;
    private static final int PANEL = 0xF0101318;
    private static final int EDGE = 0xFF2E5552;
    private static final int EDGE_IN = 0xFF17302F;
    private static final int TEAL = 0xFF7FD6D0;
    private static final int GOLD = 0xFFF2C14E;
    private static final int TEXT = 0xFFE9E3D4;
    private static final int SOFT = 0xFF8C8CA0;
    private static final int DIM = 0xFF5C5C6C;

    private int left;
    private int top;
    @Nullable
    private String selected;
    private int bodyScroll;
    private final List<Row> rows = new ArrayList<>();
    private final List<BodyLine> body = new ArrayList<>();
    private Button trackButton;

    private record Row(@Nullable String quest, Component label, int color) {
    }

    private record BodyLine(String text, int color, int indent) {
    }

    public QuestLogScreen() {
        super(Component.translatable("gui.skyabyss.quest.log"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        trackButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleTrack())
                .bounds(left + W - 98, top + H - 24, 88, 16).build());
        rebuild();
        if (selected == null) {
            QuestLog log = log();
            selected = log != null && !log.tracked().isEmpty() ? log.tracked() : firstQuest();
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
    }

    private void section(String key, List<QuestDef> list, int color) {
        if (list.isEmpty()) {
            return;
        }
        rows.add(new Row(null, Component.translatable(key), DIM));
        for (QuestDef def : list) {
            rows.add(new Row(def.id(), Component.translatable(def.titleKey()), color));
        }
    }

    // 右の頁: 題、依頼主、書き付け、目的、報酬
    private void rebuildBody() {
        body.clear();
        bodyScroll = 0;
        Font font = Minecraft.getInstance().font;
        int w = W - LIST_W - 30;
        QuestLog log = log();
        QuestDef def = selected == null ? null : QuestClient.quest(selected);
        QuestLog.Entry entry = log == null || selected == null ? null : log.entry(selected);
        updateTrackButton(def, entry, log);
        if (def == null || entry == null) {
            return;
        }
        if (def.giver() != null) {
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.giver",
                    Component.translatable("entity." + def.giver().replace(':', '.'))).getString(), SOFT, 0));
        }
        body.add(new BodyLine("", TEXT, 0));
        String journal = entry.done ? Component.translatable(def.doneKey()).getString()
                : Component.translatable(def.journalKey(def.stages().get(Math.min(entry.stage, def.stages().size() - 1)))).getString();
        for (String l : LoreWrap.wrap(journal, w, font::width)) {
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
                String text = Component.translatable(def.objectiveKey(stage, ob)).getString();
                if (ob.count() > 1) {
                    text += "（" + have + "/" + ob.count() + "）";
                }
                List<String> wrapped = LoreWrap.wrap(text, w - 12, font::width);
                for (int k = 0; k < wrapped.size(); k++) {
                    body.add(new BodyLine((k == 0 ? (ok ? "■ " : "□ ") : "") + wrapped.get(k), ok ? SOFT : TEXT, k == 0 ? 0 : 11));
                }
            }
            body.add(new BodyLine("", TEXT, 0));
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.progress", entry.stage + 1, def.stages().size()).getString(), DIM, 0));
        } else {
            body.add(new BodyLine(Component.translatable("gui.skyabyss.quest.finished").getString(), SOFT, 0));
        }
        String reward = rewardText(def);
        if (!reward.isEmpty()) {
            for (String l : LoreWrap.wrap(Component.translatable("gui.skyabyss.quest.reward", reward).getString(), w, font::width)) {
                body.add(new BodyLine(l, GOLD, 0));
            }
        }
    }

    private static String rewardText(QuestDef def) {
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
                parts.add(item.getDescription().getString() + (r.count() > 1 ? " ×" + r.count() : ""));
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
                int scroll = bodyScroll;
                rebuildBody();
                bodyScroll = scroll;
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
        g.fill(0, 0, width, height, 0x80000000);
        drawPanel(g, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        g.fill(left, top, left + W, top + H, PANEL);
        g.renderOutline(left, top, W, H, EDGE);
        g.renderOutline(left + 2, top + 2, W - 4, H - 4, EDGE_IN);
        g.drawString(font, title, left + 10, top + 8, TEAL, false);
        g.fill(left + 8, top + 19, left + W - 8, top + 20, EDGE_IN);
        g.fill(left + LIST_W + 10, top + 24, left + LIST_W + 11, top + H - 8, EDGE_IN);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.quest.empty"), left + W / 2, top + H / 2 - 4, SOFT);
            return;
        }
        // 一覧
        int y = top + 26;
        for (Row r : rows) {
            if (r.quest() == null) {
                g.drawString(font, r.label(), left + 10, y + 2, DIM, false);
            } else {
                boolean sel = r.quest().equals(selected);
                boolean hover = mouseX >= left + 8 && mouseX < left + LIST_W + 6 && mouseY >= y && mouseY < y + ROW_H;
                if (sel || hover) {
                    g.fill(left + 8, y, left + LIST_W + 6, y + ROW_H, sel ? 0x502E8F88 : 0x281E5552);
                }
                String label = font.plainSubstrByWidth(r.label().getString(), LIST_W - 12);
                g.drawString(font, label, left + 16, y + 2, r.color(), false);
                QuestLog log = log();
                if (log != null && r.quest().equals(log.tracked())) {
                    g.drawString(font, "▸", left + 9, y + 2, r.color(), false);
                }
            }
            y += ROW_H;
            if (y > top + H - 14) {
                break;
            }
        }
        // 右の頁
        QuestDef def = selected == null ? null : QuestClient.quest(selected);
        int bx = left + LIST_W + 18;
        if (def != null) {
            g.drawString(font, Component.translatable(def.titleKey()), bx, top + 26, def.main() ? GOLD : TEAL, false);
            int by = top + 40;
            int maxLines = (top + H - 30 - by) / LINE_H;
            bodyScroll = Mth.clamp(bodyScroll, 0, Math.max(0, body.size() - maxLines));
            for (int i = bodyScroll; i < body.size() && i < bodyScroll + maxLines; i++) {
                BodyLine l = body.get(i);
                g.drawString(font, l.text(), bx + l.indent(), by, l.color(), false);
                by += LINE_H;
            }
            if (body.size() > maxLines) {
                g.drawString(font, bodyScroll > 0 ? "▲" : " ", left + W - 16, top + 40, SOFT, false);
                g.drawString(font, bodyScroll + maxLines < body.size() ? "▼" : " ", left + W - 16, top + H - 40, SOFT, false);
            }
        }
        g.drawString(font, Component.translatable("gui.skyabyss.quest.lore_hint"), left + 10, top + H - 19, DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = top + 26;
        for (Row r : rows) {
            if (r.quest() != null && mx >= left + 8 && mx < left + LIST_W + 6 && my >= y && my < y + ROW_H) {
                select(r.quest());
                return true;
            }
            y += ROW_H;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void select(String quest) {
        if (!quest.equals(selected)) {
            selected = quest;
            rebuildBody();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.4f));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx > left + LIST_W + 10) {
            bodyScroll -= (int) Math.signum(dy);
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
        return super.keyPressed(key, scan, mods);
    }
}
