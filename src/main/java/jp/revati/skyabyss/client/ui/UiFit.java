package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

// 固定の大きさの画面 (論理の幅 x 高さ) を、窓に合わせて一様に大きくする。
// 描くのも、マウスの当たりも、この一つの変換を通す: 画面の座標 → 論理の座標は x(sx) / y(sy)、描くときは begin / end で囲む。
// 窓より小さくはしない (1 倍まで)。cap は一番大きい倍率。
public final class UiFit {
    private float scale = 1f;
    private int ox;
    private int oy;

    // 論理の大きさを、画面の中央へ。縁に 6 画素ずつ余白を残す
    public void fit(int screenW, int screenH, int logicalW, int logicalH, float cap) {
        fit(screenW, screenH, logicalW, logicalH, cap, 1f);
    }

    // floor は一番小さい倍率 (1 より小さくして、窓の低い GUI の大きさにも入れる。文字は小さくなる)
    public void fit(int screenW, int screenH, int logicalW, int logicalH, float cap, float floor) {
        float s = Math.min((screenW - 12f) / Math.max(1, logicalW), (screenH - 12f) / Math.max(1, logicalH));
        scale = pixelExact(Mth.clamp(s, floor, cap));
        ox = Math.round((screenW - logicalW * scale) / 2f);
        oy = Math.round((screenH - logicalH * scale) / 2f);
    }

    // 画素ちょうど (docs/design/56): 論理の 1 画素が画面の整数の画素になる倍率へ下に丸める。GUI 3 なら 1 倍・4/3 倍、GUI 4 なら 0.75 倍・1 倍・1.25 倍。
    // 1.09 倍のような半端な倍率では、字の 1 画素が画面の 3 画素と 4 画素に分かれ、画の太さがそろわなかった
    public static float pixelExact(float s) {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        if (gui <= 0) {
            return s;
        }
        int px = Math.max(1, (int) Math.floor(s * gui + 1e-3));
        return (float) (px / gui);
    }

    public float scale() {
        return scale;
    }

    // 論理の 1 画素が画面の何画素か (画素ちょうどなら整数。PanZoomCanvas.setPixelUnit に渡す)
    public float pixelUnit() {
        return (float) (scale * net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
    }

    public void begin(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0f);
        g.pose().scale(scale, scale, 1f);
    }

    public void end(GuiGraphics g) {
        g.pose().popPose();
    }

    // 論理の矩形 {x, y, 幅, 高さ} を、画面 (GUI の座標) の矩形に直す (文字の範囲など、画面の座標で渡すもの)
    public int[] screenRect(int x, int y, int w, int h) {
        return new int[] {Math.round(ox + x * scale), Math.round(oy + y * scale), Math.round(w * scale), Math.round(h * scale)};
    }

    // 画面 (GUI の座標) の点を論理の座標へ
    public double x(double sx) {
        return (sx - ox) / scale;
    }

    public double y(double sy) {
        return (sy - oy) / scale;
    }
}
