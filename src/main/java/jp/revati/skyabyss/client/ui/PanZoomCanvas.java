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
    // 最初の大きさの下限 (盤が大きいときに、字と節が小さくなりすぎないように)
    private float startMin = MIN_ZOOM;
    private double panX;
    private double panY;
    private boolean dragging;
    // 画素ちょうど (docs/design/56): 0 より大きければ、論理の 1 画素 = 画面の unit 画素として、拡大は 1/unit 刻み、
    // 位置は画面の 1 画素刻みにそろえる (字と節の画素の太さがそろう)
    private float unit;

    public void setPixelUnit(float unit) {
        this.unit = unit >= 1f ? Math.round(unit) : 0f;
    }

    // 拡大を画面の画素にそろえる (下へ丸め。MIN_ZOOM..MAX_ZOOM に入る 1/unit の倍数)
    private float snapZoom(float z) {
        if (unit <= 0) {
            return z;
        }
        int k = Mth.clamp((int) Math.floor(z * unit + 1e-3), minStep(), maxStep());
        return k / unit;
    }

    private int minStep() {
        return Math.max(1, (int) Math.ceil(MIN_ZOOM * unit - 1e-3));
    }

    private int maxStep() {
        return (int) Math.floor(MAX_ZOOM * unit + 1e-3);
    }

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

    // 最初の大きさの下限を決める (既定は MIN_ZOOM)。盤が枠に入らないときは、下限まで拡げて上から見せる
    public void setStartMin(float z) {
        startMin = Mth.clamp(z, MIN_ZOOM, 1f);
    }

    // 最初の大きさと位置: 盤が枠に入る大きさ (枠より小さい盤は 1 倍。下限まで) で、横は真ん中、縦は盤が枠より長ければ上から
    public void reset() {
        zoom = snapZoom(Mth.clamp(Math.min((float) w / cw, (float) h / ch), startMin, 1f));
        double sw = cw * zoom;
        double sh = ch * zoom;
        panX = (w - sw) / 2.0;
        panY = sh <= h ? (h - sh) / 2.0 : 0;
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

    // 窓の矩形は、今の変換 (UiFit など) の中の座標で与える。切りも変換を通す
    public void begin(GuiGraphics g) {
        TextClip.clipPosed(g, x, y, w, h);
        g.pose().pushPose();
        double px = panX;
        double py = panY;
        if (unit > 0) {
            px = Math.round(px * unit) / unit;
            py = Math.round(py * unit) / unit;
        }
        g.pose().translate((float) (x + px), (float) (y + py), 0f);
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
        float next;
        if (unit > 0) {
            // 画素ちょうど: 1/unit ずつ
            int k = Mth.clamp(Math.round(zoom * unit) + (dy > 0 ? 1 : -1), minStep(), maxStep());
            next = k / unit;
        } else {
            next = Mth.clamp(dy > 0 ? zoom * STEP : zoom / STEP, MIN_ZOOM, MAX_ZOOM);
        }
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
