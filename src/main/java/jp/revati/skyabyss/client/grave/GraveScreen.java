package jp.revati.skyabyss.client.grave;

import java.util.List;

import jp.revati.skyabyss.grave.GraveNetwork;
import jp.revati.skyabyss.client.ui.Golden;
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
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 一行目に分け前、その下に墓を一つずつ欄に (高さ 34)、下の段に手引きと頁。
// 入りきらない分はホイールとキーで送る
public class GraveScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box BODY = LAYOUT.body();
    private static final int ROW_H = Golden.S34;
    private static final int ROW_STEP = ROW_H + Golden.S5;
    private static final int ROWS_Y = BODY.y() + Golden.ROW + Golden.S5;
    private static final int MAX_ROWS = (BODY.bottom() - ROWS_Y + Golden.S5) / ROW_STEP;
    private static final int TEAL = 0xFF000000 | Golden.TEAL;
    private static final int GOLD = 0xFF000000 | Golden.GOLD;
    private static final int TEXT = 0xFF000000 | Golden.TEXT;
    private static final int SOFT = 0xFF000000 | Golden.SOFT;
    private static final int DIM = 0xFF000000 | Golden.DIM;

    private GraveNetwork.Open data;
    private int scroll;
    private int shown;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

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
            s.scroll = Math.min(s.scroll, Math.max(0, payload.entries().size() - s.maxRows()));
            s.rebuildWidgets();
        } else if (!payload.entries().isEmpty() && (mc.screen == null || mc.screen instanceof jp.revati.skyabyss.client.npc.DialogueScreen)) {
            // 語り部の会話の画面と入れ替える
            mc.setScreen(new GraveScreen(payload));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9f, 0.6f));
        }
    }

    private int maxRows() {
        return MAX_ROWS;
    }

    private int rows() {
        return Math.min(maxRows(), Math.max(1, data.entries().size() - scroll));
    }

    private static Golden.Box row(int i) {
        return new Golden.Box(BODY.x(), ROWS_Y + i * ROW_STEP, BODY.w() - Golden.S8, ROW_H);
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        shown = rows();
        for (int i = 0; i < shown && scroll + i < data.entries().size(); i++) {
            GraveNetwork.Entry e = data.entries().get(scroll + i);
            Golden.Box r = row(i);
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.grave.pick"), b -> {
                b.active = false;
                PacketDistributor.sendToServer(new GraveNetwork.Pick(e.id()));
            }).bounds(r.right() - Golden.S8 - Golden.S55, r.y() + (ROW_H - Golden.BTN) / 2, Golden.S55, Golden.BTN).build());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // 送りのキー (上下・PageUp/PageDown) は墓の一覧を送る
        int dir = key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP ? -1
                : key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 1 : 0;
        if (dir != 0) {
            return shift(dir);
        }
        return super.keyPressed(key, scan, mods);
    }

    private boolean shift(int dir) {
        int max = Math.max(0, data.entries().size() - maxRows());
        int next = Math.max(0, Math.min(max, scroll + dir));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return shift(-(int) Math.signum(scrollY));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(fit.x(mx), fit.y(my), button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        draw(g, Minecraft.getInstance().font, mouseX, mouseY);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        fit.end(g);
    }

    private void draw(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), GOLD, false);
        Component count = Component.translatable("gui.skyabyss.grave.count", data.entries().size());
        g.drawString(font, count, head.right() - Golden.S5 - font.width(count), Golden.textY(head.y(), head.h()), TEAL, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.grave.share", data.percent()), BODY.x() + Golden.S5,
                Golden.textY(BODY.y(), Golden.ROW), BODY.w() - Golden.S13, SOFT, false);
        for (int i = 0; i < shown && scroll + i < data.entries().size(); i++) {
            drawRow(g, font, data.entries().get(scroll + i), row(i), mouseX, mouseY);
        }
        Golden.Box foot = LAYOUT.foot();
        int fy = Golden.textY(foot.y(), foot.h());
        if (data.entries().size() > shown) {
            // 送りの目盛り (行の数に対する、見えている所)
            int track = shown * ROW_STEP - Golden.S5;
            int total = data.entries().size();
            int thumb = Math.max(8, track * shown / total);
            int max = Math.max(1, total - shown);
            int bx = BODY.right() - 3;
            int ty = ROWS_Y + (track - thumb) * scroll / max;
            g.fill(bx, ROWS_Y, bx + 2, ROWS_Y + track, 0x40000000);
            g.fill(bx, ty, bx + 2, ty + thumb, 0x90F2C14E);
            String page = (scroll + 1) + "-" + Math.min(data.entries().size(), scroll + shown) + " / " + data.entries().size();
            g.drawString(font, page, foot.right() - Golden.S5 - font.width(page), fy, DIM, false);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.grave.footer"), foot.x() + Golden.S5, fy, DIM, false);
    }

    private void drawRow(GuiGraphics g, Font font, GraveNetwork.Entry e, Golden.Box r, int mouseX, int mouseY) {
        Golden.recess(g, r);
        if (r.contains(mouseX, mouseY)) {
            g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0x181E5552);
        }
        g.fill(r.x() + 1, r.y() + 1, r.x() + 3, r.bottom() - 1, TEAL);
        // 一行目: 場所。二行目: 品の数・経験値・経った時間
        int x = r.x() + Golden.S8;
        int tw = r.w() - Golden.S8 - Golden.S55 - Golden.S13;
        String place = Component.translatable("gui.skyabyss.grave.place", dimensionName(e.dimension()), e.x(), e.y(), e.z()).getString();
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(place), x, Golden.textY(r.y() + 4, Golden.ROW), tw, TEXT, false);
        String contents = Component.translatable("gui.skyabyss.grave.contents", e.items(), e.xp()).getString();
        String age = age(e.age());
        int ay = Golden.textY(r.y() + 4 + Golden.ROW, Golden.ROW);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(contents), x, ay, tw - font.width(age) - Golden.S8, SOFT, false);
        g.drawString(font, age, x + tw - font.width(age), ay, SOFT, false);
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
        return fit.screenRect(0, 0, Golden.L_W, Golden.L_H);
    }
}
