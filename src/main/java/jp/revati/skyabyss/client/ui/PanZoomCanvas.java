package jp.revati.skyabyss.client.ui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

// 動かせて拡大できる盤 (木・網・地図のような、場所の関係で見る画面)。旅の地図と同じ流儀。
//   左のボタンで押して引くと動く (盤の外へは出ない。内容が枠より小さければ真ん中)
//   ホイールで拡大・縮小 (カーソルの下の所を保つ。MIN_ZOOM..MAX_ZOOM)
//   右のボタン・R キーで最初の大きさと位置へ戻す
// 使い方: setViewport で枠を決め、setContent で盤の大きさ (盤の単位) を伝える。
//   begin(g) → 盤を描く (盤の単位の座標で描く。原点は盤の左上) → end(g)
//   盤の上の点は toContentX / toContentY で取る。画面の mouseClicked では、先に釦や印を見てから canvas.mouseClicked を呼ぶ。
public final class PanZoomCanvas {
    public static final float MIN_ZOOM = 0.5f;
    public static final float MAX_ZOOM = 3.0f;
    private static final float STEP = 1.2f;

    private int x;
    private int y;
    private int w;
    private int h;
    private int cw;
    private int ch;
    private float zoom = 1f;
    private double panX;
    private double panY;
    private boolean dragging;

    public void setViewport(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = Math.max(0, w);
        this.h = Math.max(0, h);
        clampPan();
    }

    public void setContent(int contentW, int contentH) {
        cw = Math.max(1, contentW);
        ch = Math.max(1, contentH);
        clampPan();
    }

    public float zoom() {
        return zoom;
    }

    // 最初の大きさと位置 (盤を枠の真ん中へ)
    public void reset() {
        zoom = 1f;
        panX = (w - cw) / 2.0;
        panY = (h - ch) / 2.0;
        clampPan();
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public double toContentX(double mx) {
        return (mx - x - panX) / zoom;
    }

    public double toContentY(double my) {
        return (my - y - panY) / zoom;
    }

    public void begin(GuiGraphics g) {
        TextClip.clip(g, x, y, w, h);
        g.pose().pushPose();
        g.pose().translate((float) (x + panX), (float) (y + panY), 0f);
        g.pose().scale(zoom, zoom, 1f);
    }

    public void end(GuiGraphics g) {
        g.pose().popPose();
        TextClip.unclip(g);
    }

    // 左で押したときは動かす握りを取る (true なら画面はこの押しを消費する)
    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) {
            return false;
        }
        if (button == 1) {
            reset();
            return true;
        }
        if (button == 0) {
            dragging = true;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging || button != 0) {
            return false;
        }
        panX += dx;
        panY += dy;
        clampPan();
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double dy) {
        if (!contains(mx, my) || dy == 0) {
            return false;
        }
        float next = Mth.clamp(dy > 0 ? zoom * STEP : zoom / STEP, MIN_ZOOM, MAX_ZOOM);
        if (next == zoom) {
            return true;
        }
        // カーソルの下の盤の点を動かさない
        double cx = toContentX(mx);
        double cy = toContentY(my);
        zoom = next;
        panX = mx - x - cx * zoom;
        panY = my - y - cy * zoom;
        clampPan();
        return true;
    }

    public boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_R) {
            reset();
            return true;
        }
        return false;
    }

    // 盤が枠より大きいときは枠の外へ出さない。小さいときは真ん中
    private void clampPan() {
        double sw = cw * zoom;
        double sh = ch * zoom;
        panX = sw <= w ? (w - sw) / 2 : Mth.clamp(panX, w - sw, 0);
        panY = sh <= h ? (h - sh) / 2 : Mth.clamp(panY, h - sh, 0);
    }
}
