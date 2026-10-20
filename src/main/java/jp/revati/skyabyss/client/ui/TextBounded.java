package jp.revati.skyabyss.client.ui;

// 文字の描ける範囲を持つ画面。画面を描くあいだ、この矩形の外へ出る字は GuiGraphicsMixin が切る (どの字も枠の外へ描かれない保証)。
// 返す値は {x, y, 幅, 高さ} (画面の寸法)。全面に描く画面は実装しない。ツールチップ (renderTooltip) は切られない。
public interface TextBounded {
    int[] textBounds();
}
