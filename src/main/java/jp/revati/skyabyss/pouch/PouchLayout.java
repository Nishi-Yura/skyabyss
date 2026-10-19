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

    private PouchLayout() {
    }

    public record Spot(Entry entry, int x, int y) {
    }

    public record Label(Category category, int x, int y) {
    }

    // spare: 転生の余白の枠 (docs/design/31 の B13) の置き場 3 つ (x, y)。区分の枠の最後の行の空きに置く (空きがなければ次の行)。
    // spareLabel: その名札の置き場 (x, y)
    public record Result(List<Spot> spots, List<Label> labels, int bottom, List<int[]> spare, int[] spareLabel) {
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
        // 余白の枠: 最後の行に、名札の分を 1 列あけて 3 つ置ける間は同じ行、置けなければ次の行
        // 右 3 列があいている行を、下から探す (なければ最後の行の次)。名札は枠の上の行にあり、右の端なので重ならない
        int sRow = row + 1;
        int sCol = 0;
        for (int r = row; r >= 0; r--) {
            int used = 0;
            for (Spot sp : spots) {
                if ((sp.y() - TOP - 9) / ROW_H == r) {
                    used = Math.max(used, (sp.x() - X0) / PITCH + 1);
                }
            }
            for (Label lb : labels) {
                if ((lb.y() - TOP) / ROW_H == r) {
                    used = Math.max(used, (lb.x() - X0) / PITCH + 4);
                }
            }
            if (used <= COLS - 3) {
                sRow = r;
                sCol = COLS - 3;
                break;
            }
        }
        List<int[]> spare = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int y = TOP + sRow * ROW_H + 9;
            spare.add(new int[] {X0 + (sCol + i) * PITCH, y});
            bottom = Math.max(bottom, y + 18);
        }
        int[] spareLabel = {X0 + sCol * PITCH, TOP + sRow * ROW_H};
        return new Result(spots, labels, bottom, spare, spareLabel);
    }
}
