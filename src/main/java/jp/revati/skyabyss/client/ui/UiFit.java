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
        scale = Mth.clamp(s, floor, cap);
        ox = Math.round((screenW - logicalW * scale) / 2f);
        oy = Math.round((screenH - logicalH * scale) / 2f);
    }

    public float scale() {
        return scale;
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
