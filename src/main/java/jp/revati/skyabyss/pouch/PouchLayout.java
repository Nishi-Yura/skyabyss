package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.pouch.PouchConfig.Category;
import jp.revati.skyabyss.pouch.PouchEntries.Entry;

// 旅嚢の画面の枠の並び (サーバーのメニューとクライアントの画面で同じ値を使う)。区分ごとに名札つきで、九列の升目に流し込む。
// 小さな区分 (書など) は、前の区分の行に空きがあれば同じ行に置く (間に一列あける)。絵は tools/gen_pouch_assets.py
public final class PouchLayout {
    public static final int W = 176;
    public static final int H = 200;
    public static final int COLS = 9;
    // 枠の中身の左端と、一列の幅
    public static final int X0 = 8;
    public static final int PITCH = 18;
    // 最初の名札の高さ。名札の下に枠が続く (名札 9 + 枠 18 = 一行 27)
    public static final int TOP = 19;
    public static final int ROW_H = 27;
    // 枠の並びの下端 (この下は説明と飾り)
    public static final int SEPARATOR_Y = 160;

    // 転生の余白の枠 (docs/design/31 の B13): 区分の枠の下、説明の線の上。名札はその 9 上
    public static final int SPARE_Y = SEPARATOR_Y - 21;

    public static int spareX(int i) {
        return X0 + i * PITCH;
    }

    private PouchLayout() {
    }

    public record Spot(Entry entry, int x, int y) {
    }

    public record Label(Category category, int x, int y) {
    }

    public record Result(List<Spot> spots, List<Label> labels, int bottom) {
    }

    // 登録の並び (PouchEntries.enabled()) を流し込む。入る品が増えて下端を越えたら bottom が SEPARATOR_Y を越える (自己テストが見る)
    public static Result compute(List<Entry> entries) {
        List<Spot> spots = new ArrayList<>();
        List<Label> labels = new ArrayList<>();
        int col = 0;
        int row = 0;
        int bottom = TOP;
        for (Category category : Category.values()) {
            List<Entry> group = new ArrayList<>();
            for (Entry e : entries) {
                if (e.category() == category) {
                    group.add(e);
                }
            }
            if (group.isEmpty()) {
                continue;
            }
            int n = group.size();
            if (col > 0 && col + 1 + n <= COLS) {
                col++;
            } else if (col > 0) {
                row++;
                col = 0;
            }
            labels.add(new Label(category, X0 + col * PITCH, TOP + row * ROW_H));
            for (Entry e : group) {
                if (col >= COLS) {
                    row++;
                    col = 0;
                }
                int y = TOP + row * ROW_H + 9;
                spots.add(new Spot(e, X0 + col * PITCH, y));
                bottom = Math.max(bottom, y + 18);
                col++;
            }
        }
        return new Result(spots, labels, bottom);
    }
}
