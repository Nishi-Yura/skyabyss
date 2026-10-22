package jp.revati.skyabyss.client.ui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

// 送れる面 (長い字の一覧・本文)。枠の中だけを切って描き、マウスのホイール・右の細い目盛りのつまみのドラッグ・キー (上下・PageUp/PageDown・Home/End) で送る。
// 使い方: setViewport で枠を決め、setContentHeight で中身の高さを伝える。
//   begin(g) → 中身を描く (中身の一行目は top() の高さから描く) → end(g) → drawBar(g)
// 中身の幅は width() (右に目盛りの分の余白を残す)。画面の mouseScrolled / mouseClicked / mouseDragged / mouseReleased / keyPressed から、
// 戻り値が true なら、その画面はそのまま消費する。
public final class ScrollPanel {
    public static final int GUTTER = 8;
    private static final int BAR_W = 3;
    private static final int MIN_THUMB = 12;

    private int x;
    private int y;
    private int w;
    private int h;
    private int contentH;
    private int scroll;
    private final int step;
    private boolean dragging;
    private double grab;

    public ScrollPanel(int step) {
        this.step = Math.max(1, step);
    }

    public void setViewport(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = Math.max(0, w);
        this.h = Math.max(0, h);
        scroll = clamp(scroll);
    }

    public void setContentHeight(int contentHeight) {
        contentH = Math.max(0, contentHeight);
        scroll = clamp(scroll);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    // 中身の描く幅 (右に目盛りの余白を残す)
    public int width() {
        return Math.max(0, w - GUTTER);
    }

    public int height() {
        return h;
    }

    public int scroll() {
        return scroll;
    }

    public void setScroll(int s) {
        scroll = clamp(s);
    }

    public boolean scrollable() {
        return contentH > h;
    }

    public int maxScroll() {
        return Math.max(0, contentH - h);
    }

    // 中身の一行目を描く画面の y (枠の上端から送りの分だけ上へ)
    public int top() {
        return y - scroll;
    }

    // 送りの量を、一度に見せる行の数の割合で。上へ送るのは 0 まで
    public void page(int dir) {
        scroll = clamp(scroll + dir * Math.max(step, h - step));
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void begin(GuiGraphics g) {
        TextClip.clip(g, x, y, w, h);
    }

    public void end(GuiGraphics g) {
        TextClip.unclip(g);
    }

    public boolean mouseScrolled(double mx, double my, double dy) {
        if (!contains(mx, my) || !scrollable()) {
            return false;
        }
        scroll = clamp(scroll - (int) Math.signum(dy) * step);
        return true;
    }

    // 目盛りのつまみ (右の細い帯) を押したとき、そこから送りを握る。帯の外は見ない (画面の他の釦へ回す)
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !scrollable() || !inBar(mx, my)) {
            return false;
        }
        int th = thumbH();
        int ty = thumbY();
        dragging = true;
        // つまみの上なら掴んだ所をそのまま、外なら中央をつまみへ寄せる
        grab = my >= ty && my < ty + th ? my - ty : th / 2.0;
        drag(my);
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) {
            return false;
        }
        drag(my);
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    public boolean keyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll = clamp(scroll - step);
            case GLFW.GLFW_KEY_DOWN -> scroll = clamp(scroll + step);
            case GLFW.GLFW_KEY_PAGE_UP -> page(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> page(1);
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
            default -> {
                return false;
            }
        }
        return true;
    }

    // 右の細い目盛り (中身が枠に入りきらないときだけ)。track: 地の色、thumb: つまみの色 (0xAARRGGBB)
    public void drawBar(GuiGraphics g, int track, int thumb) {
        if (!scrollable()) {
            return;
        }
        int bx = x + w - BAR_W;
        g.fill(bx, y, bx + BAR_W, y + h, track);
        g.fill(bx, thumbY(), bx + BAR_W, thumbY() + thumbH(), thumb);
    }

    private boolean inBar(double mx, double my) {
        return mx >= x + w - BAR_W - 2 && mx < x + w && my >= y && my < y + h;
    }

    private int thumbH() {
        if (!scrollable()) {
            return h;
        }
        return Mth.clamp((int) ((long) h * h / contentH), MIN_THUMB, h);
    }

    private int thumbY() {
        int room = h - thumbH();
        if (maxScroll() == 0 || room <= 0) {
            return y;
        }
        return y + (int) ((long) scroll * room / maxScroll());
    }

    private void drag(double my) {
        int room = h - thumbH();
        if (room <= 0) {
            return;
        }
        double top = my - grab - y;
        scroll = clamp((int) Math.round(top * maxScroll() / room));
    }

    private int clamp(int s) {
        return Mth.clamp(s, 0, maxScroll());
    }
}
