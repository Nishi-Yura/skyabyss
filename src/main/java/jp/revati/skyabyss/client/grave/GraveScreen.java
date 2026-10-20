package jp.revati.skyabyss.client.grave;

import java.util.List;

import jp.revati.skyabyss.grave.GraveNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

// 語り部に頼む墓の一覧の画面。古い順に、場所・品の数・経験値・経った時間を一行ずつ並べ、「呼び戻す」で一つ選ぶ。
// 中身はサーバーが決めて送る (GraveNetwork.Open)。選ぶとサーバーが確かめて救い、残りの一覧を送り返す (空なら閉じる)。
// 行が多いときはホイールで送る
public class GraveScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int W = 316;
    private static final int ROW_H = 28;
    private static final int MAX_ROWS = 6;
    private static final int PANEL = 0xFA15120E;
    private static final int EDGE = 0xFF6B5534;
    private static final int EDGE_IN = 0xFF3A2E1C;
    private static final int PAPER = 0x30F0E2C0;
    private static final int PAPER_HOVER = 0x50F0E2C0;
    private static final int TEAL = 0xFF7FD6D0;
    private static final int GOLD = 0xFFF2C14E;
    private static final int TEXT = 0xFFE9E3D4;
    private static final int SOFT = 0xFF9C9480;
    private static final int DIM = 0xFF5F5848;

    private GraveNetwork.Open data;
    private int scroll;
    private int left;
    private int top;
    private int panelH;

    public GraveScreen(GraveNetwork.Open data) {
        super(Component.translatable("gui.skyabyss.grave.title"));
        this.data = data;
    }

    public static void receive(GraveNetwork.Open payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GraveScreen s) {
            if (payload.entries().isEmpty()) {
                s.onClose();
                return;
            }
            s.data = payload;
            s.scroll = Math.min(s.scroll, Math.max(0, payload.entries().size() - MAX_ROWS));
            s.rebuildWidgets();
        } else if (!payload.entries().isEmpty() && (mc.screen == null || mc.screen instanceof jp.revati.skyabyss.client.npc.DialogueScreen)) {
            // 語り部の会話の画面と入れ替える
            mc.setScreen(new GraveScreen(payload));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9f, 0.6f));
        }
    }

    private int rows() {
        return Math.min(MAX_ROWS, Math.max(1, data.entries().size() - scroll));
    }

    @Override
    protected void init() {
        panelH = 54 + rows() * ROW_H + 20;
        left = (width - W) / 2;
        top = Math.max(4, (height - panelH) / 2);
        for (int i = 0; i < rows() && scroll + i < data.entries().size(); i++) {
            GraveNetwork.Entry e = data.entries().get(scroll + i);
            int y = top + 46 + i * ROW_H;
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.grave.pick"), b -> {
                b.active = false;
                PacketDistributor.sendToServer(new GraveNetwork.Pick(e.id()));
            }).bounds(left + W - 66, y + 5, 56, 16).build());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, data.entries().size() - MAX_ROWS);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
        draw(g, Minecraft.getInstance().font, mouseX, mouseY);
    }

    private void draw(GuiGraphics g, Font font, int mouseX, int mouseY) {
        g.fill(left, top, left + W, top + panelH, PANEL);
        g.renderOutline(left, top, W, panelH, EDGE);
        g.renderOutline(left + 2, top + 2, W - 4, panelH - 4, EDGE_IN);
        g.drawString(font, title, left + 10, top + 8, GOLD, false);
        Component count = Component.translatable("gui.skyabyss.grave.count", data.entries().size());
        g.drawString(font, count, left + W - 10 - font.width(count), top + 8, TEAL, false);
        g.drawString(font, Component.translatable("gui.skyabyss.grave.share", data.percent()), left + 10, top + 22, SOFT, false);
        g.fill(left + 8, top + 36, left + W - 8, top + 37, EDGE_IN);
        for (int i = 0; i < rows() && scroll + i < data.entries().size(); i++) {
            drawRow(g, font, data.entries().get(scroll + i), top + 46 + i * ROW_H, mouseX, mouseY);
        }
        if (data.entries().size() > MAX_ROWS) {
            String page = (scroll + 1) + "-" + Math.min(data.entries().size(), scroll + MAX_ROWS) + " / " + data.entries().size();
            g.drawString(font, page, left + W - 10 - font.width(page), top + panelH - 15, DIM, false);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.grave.footer"), left + 10, top + panelH - 15, DIM, false);
    }

    private void drawRow(GuiGraphics g, Font font, GraveNetwork.Entry e, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= left + 8 && mouseX < left + W - 8 && mouseY >= y && mouseY < y + ROW_H - 3;
        g.fill(left + 8, y, left + W - 8, y + ROW_H - 3, hover ? PAPER_HOVER : PAPER);
        g.fill(left + 8, y, left + 10, y + ROW_H - 3, TEAL);
        // 一行目: 場所。二行目: 品の数・経験値・経った時間
        String place = Component.translatable("gui.skyabyss.grave.place", dimensionName(e.dimension()), e.x(), e.y(), e.z()).getString();
        g.drawString(font, font.plainSubstrByWidth(place, W - 96), left + 14, y + 4, TEXT, false);
        String contents = Component.translatable("gui.skyabyss.grave.contents", e.items(), e.xp()).getString();
        g.drawString(font, contents, left + 14, y + 15, SOFT, false);
        String age = age(e.age());
        g.drawString(font, age, left + W - 74 - font.width(age), y + 15, SOFT, false);
    }

    // 経った時間 (墓のできた tick からの差。20 tick = 1 秒)
    static String age(long ticks) {
        long minutes = ticks / 1200L;
        if (minutes < 1) {
            return Component.translatable("gui.skyabyss.grave.age.now").getString();
        }
        if (minutes < 60) {
            return Component.translatable("gui.skyabyss.grave.age.minutes", minutes).getString();
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return Component.translatable("gui.skyabyss.grave.age.hours", hours).getString();
        }
        return Component.translatable("gui.skyabyss.grave.age.days", hours / 24).getString();
    }

    static String dimensionName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("gui.skyabyss.grave.dim.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("gui.skyabyss.grave.dim.nether").getString();
            case "minecraft:the_end" -> Component.translatable("gui.skyabyss.grave.dim.end").getString();
            default -> id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        };
    }

    // 確認用 (台本のスクショ `open jp.revati.skyabyss.client.grave.GraveScreen$Sample`): 一覧の見本。サーバーとは話さない
    public static final class Sample extends GraveScreen {
        public Sample() {
            super(new GraveNetwork.Open(50, List.of(
                    new GraveNetwork.Entry(java.util.UUID.nameUUIDFromBytes("a".getBytes()), "minecraft:overworld", 112, 71, -240, 23, 410, 1200L * 190),
                    new GraveNetwork.Entry(java.util.UUID.nameUUIDFromBytes("b".getBytes()), "minecraft:overworld", -388, 12, 64, 9, 120, 1200L * 55),
                    new GraveNetwork.Entry(java.util.UUID.nameUUIDFromBytes("c".getBytes()), "minecraft:the_nether", 40, 64, 18, 31, 0, 1200L * 8))));
        }
    }

    @Override
    public int[] textBounds() {
        return new int[] {left, top, W, panelH};
    }
}
