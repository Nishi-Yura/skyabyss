package jp.revati.skyabyss.client.pouch;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.pouch.PouchEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// 旅嚢の輪: 使える道具を円に並べ、マウスの向きで選び、キーを離すと使う (docs/design/22 の 10 章 B)。
// 中心で離すか Esc で取りやめ。札をクリックしてもすぐ使える。ゲームは止めない (マルチでも同じ)
public class PouchWheelScreen extends Screen {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/pouch/pouch.png");
    static final int TEX = 512;
    private static final int RADIUS = 52;
    // 窓が小さいとき (GUI 4 など) はこれまで縮める
    private static final int RADIUS_MIN = 28;
    private static final int PLATE = 28;
    private static final int PLATE_MIN = 18;
    // 中心のこの半径の中は「選ばない」
    private static final int DEAD = 16;
    private static final int GOLD = 0xFFF2C14E;
    private static final int TEXT = 0xFFD8DCE6;
    private static final int DIM = 0xFF9AA0B0;

    private final List<PouchEntries.Entry> items;
    private int selected = -1;
    private boolean finished;
    // 撮影用: キーを押していなくても閉じない (取りこぼし判定を止める)
    private final boolean preview;

    public PouchWheelScreen(List<PouchEntries.Entry> items) {
        this(items, false);
    }

    private PouchWheelScreen(List<PouchEntries.Entry> items, boolean preview) {
        super(Component.translatable("skyabyss_pouch.name"));
        this.items = items;
        this.preview = preview;
    }

    // 撮影用 (ShotRunner の pouchwheel): 先頭から n 個の札を、閉じないで開く
    public static PouchWheelScreen demo(int n) {
        List<PouchEntries.Entry> all = PouchEntries.enabled();
        return new PouchWheelScreen(all.subList(0, Math.min(n, all.size())), true);
    }

    // 輪の半径: 窓が小さければ縮め、札と上下の字が画面の外へ出ないように
    private int radius() {
        int room = Math.min(width, height) / 2 - PLATE / 2 - 20;
        return Math.max(RADIUS_MIN, Math.min(RADIUS, room));
    }

    // 札の大きさ: 隣の札と重ならない大きさ (札の数が多いと小さくする)
    private int plate() {
        int n = items.size();
        if (n < 2) {
            return PLATE;
        }
        int chord = (int) (2 * radius() * Math.sin(Math.PI / n));
        return Math.max(PLATE_MIN, Math.min(PLATE, chord - 4));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // 札の中心 (画面の座標)
    private int plateX(int i) {
        double a = -Math.PI / 2 + 2 * Math.PI * i / items.size();
        return width / 2 + (int) Math.round(Math.cos(a) * radius());
    }

    private int plateY(int i) {
        double a = -Math.PI / 2 + 2 * Math.PI * i / items.size();
        return height / 2 + (int) Math.round(Math.sin(a) * radius());
    }

    // マウスの向きで選ぶ。中心に近ければ -1 (取りやめ)
    private int pick(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < DEAD * DEAD) {
            return -1;
        }
        double a = Math.atan2(dy, dx) + Math.PI / 2;
        double step = 2 * Math.PI / items.size();
        int i = (int) Math.round(((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI) / step) % items.size();
        return i;
    }

    @Override
    public void tick() {
        super.tick();
        // キーを離した瞬間を取りこぼさない (画面が開く前に離していた場合も)
        if (!preview && !finished && !isUseKeyDown()) {
            finish(true);
        }
    }

    private boolean isUseKeyDown() {
        InputConstants.Key key = PouchClient.USE_KEY.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    private void finish(boolean use) {
        if (finished) {
            return;
        }
        finished = true;
        if (use && selected >= 0 && selected < items.size()) {
            PouchClient.useFromWheel(items.get(selected).id());
        }
        onClose();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (PouchClient.USE_KEY.matches(keyCode, scanCode)) {
            finish(true);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finish(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = pick(mouseX, mouseY);
        if (button == 0 && i >= 0) {
            selected = i;
            finish(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        selected = pick(mouseX, mouseY);
        if (selected >= 0 && selected < items.size()) {
            PouchNew.viewed(items.get(selected).id());
        }
        // 画面を少し暗く
        g.fill(0, 0, width, height, 0x66000000);
        int cx = width / 2;
        int cy = height / 2;
        int r = radius();
        int p = plate();
        // 中心の印は札の内側に収まる大きさ (札と重ならない)
        int hub = Math.min(40, (r - p / 2 - 2) * 2);
        g.blit(TEXTURE, cx - hub / 2, cy - hub / 2, hub, hub, 360f, 220f, 80, 80, TEX, TEX);
        for (int i = 0; i < items.size(); i++) {
            boolean on = i == selected;
            int px = plateX(i);
            int py = plateY(i);
            g.blit(TEXTURE, px - p / 2, py - p / 2, p, p, on ? 420f : 360f, 150f, 56, 56, TEX, TEX);
            g.renderItem(new ItemStack(items.get(i).item()), px - 8, py - 8);
            if (PouchNew.isNew(items.get(i).id())) {
                PouchNew.badge(g, font, px - 8, py - 8);
            }
        }
        // 中心: 選んだ道具の名前。選んでいなければ取りやめの案内
        Component name;
        int color;
        if (selected >= 0) {
            name = items.get(selected).item().getDescription();
            color = GOLD;
        } else {
            name = Component.translatable("skyabyss_pouch.wheel.cancel");
            color = DIM;
        }
        g.drawCenteredString(font, name, cx, cy - r - p / 2 - 14, color);
        g.drawCenteredString(font, Component.translatable("skyabyss_pouch.wheel.hint"), cx, cy + r + p / 2 + 6, TEXT);
        if (selected >= 0 && items.get(selected).id().equals(PouchClient.last())) {
            g.drawCenteredString(font, "*", cx, cy - 4, GOLD);
        }
    }
}
