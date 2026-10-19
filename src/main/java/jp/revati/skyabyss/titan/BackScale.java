package jp.revati.skyabyss.titan;

// 巨神の背の拡大の倍率 (設計書 26 の 4.1)。倍率は配置の版ごとのコンパイル時の定数で、実行時の設定にしない
// (同じワールドで版が違って建て直しが毎回起きないように)。各 *Layout が自分の版の BackScale を持ち、
// 設計の単位 (版 12 までの座標) を S・V で世界の座標にする。倍率 1.0 は版 12 と同じ配置になる。
//
//   水平 (x・z): 体の軸と、東西南北の原点 (x = 0、z = 0) を動かさず、外へ広げる
//   垂直 (y)   : 腹の下端 (belly) を動かさず、上へ伸ばす。基準の y (旧) から、新しい y (新) へ写す
//
// 「部屋」の大きさは、広間・心臓・通路でそれぞれ倍率が違うので、拡大の既定 (S) のほかに、部屋ごとの倍率を渡す関数を用意している。
public record BackScale(double h, double v, int yOld, int yNew) {
    // 版 12 (拡大なし)。自己テスト scale sweep の基準
    public static final BackScale ONE = new BackScale(1.0, 1.0, 95, 95);

    // 水平の距離・座標 (旧 → 新)
    public int s(int old) {
        return (int) Math.round(old * h);
    }

    // 水平の距離を、部屋ごとの倍率 k で (広間は h より大きく、心臓は小さく)
    public int s(int old, double k) {
        return (int) Math.round(old * k);
    }

    // 垂直の長さ (高さ・深さ) (旧 → 新)
    public int dv(int old) {
        return (int) Math.round(old * v);
    }

    // 垂直の座標 (旧 → 新)。基準 yOld を yNew へ写し、そこからの差を v 倍する
    public int y(int old) {
        return yNew + (int) Math.round((old - yOld) * v);
    }

    // 中心 c (旧)、幅 w (旧) の範囲を、中心を動かさず幅だけ k 倍にした (最小, 最大)。両端を含む整数で、幅は奇数になる
    public int[] span(int centerOld, int widthOld, double k) {
        int c = s(centerOld);
        int half = (int) Math.round(widthOld * k / 2.0);
        return new int[] {c - half, c + half};
    }

    // 倍率 k を掛けたあとの部屋の面積の比 (測りの表示用)
    public double area() {
        return h * h;
    }
}
