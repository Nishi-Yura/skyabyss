package jp.revati.skyabyss.story.ch5.warden;

import java.util.List;

// 番人の悪夢の段 (docs/design/36 の 9)。段ごとに、体力・傷・灯籠・技の決まりを一つ積む (前の段の決まりは次の段へ残る)。
// NORMAL は普通の戦い。数字は今の番人と同じで、普通の戦いはこの表を読んでも変わらない。
//   hp        体力の倍率 (人数の増えの前にかける)
//   dmg       爪・踏み込み・闇の中の怒りの倍率
//   lanternLife 灯籠の持ちの倍率 (段の持ちにかける)
//   lanterns  使える灯籠の数 (番号の小さい順。残りは点かない)
//   clawTwice 爪のあと、同じ扇がもう一度 (一秒後・予告つき)
//   shadesStrike 影の写しが、本物の爪と踏み込みを使う
//   waves     闇の波の数 (二度目は体力 12% で)
//   darkRegen 闇の中で、六秒攻撃を受けないと毎秒 0.5% 戻る
//   lungeFollow 踏み込みのあと、少し間を置いて二度目 (予告つき)
//   snuffTicks 吹き消しの予告の長さ (tick。60 = 三秒)
//   darkShare 闇の中の傷の割合 (輪郭の術の 60% は変わらない)
//   heartRing 十二秒ごとに、心臓の台のまわりに赤い輪 (半径 5・予告 2 秒)
public record NightmareTier(int n, double hp, double dmg, double lanternLife, int lanterns, boolean clawTwice, boolean shadesStrike,
        int waves, boolean darkRegen, boolean lungeFollow, int snuffTicks, double darkShare, boolean heartRing) {

    public static final int MAX = 10;

    // 普通の戦い (悪夢の外)。HeartWarden の数字そのもの
    public static final NightmareTier NORMAL = new NightmareTier(0, 1.0, 1.0, 1.0, 8, false, false, 1, false, false, 60, 0.25, false);

    // 段 1〜10。上の段ほど体力と傷が増え、決まりが一つずつ増える
    public static final List<NightmareTier> TABLE = List.of(
            new NightmareTier(1, 1.10, 1.00, 0.75, 8, false, false, 1, false, false, 60, 0.25, false),
            new NightmareTier(2, 1.20, 1.00, 0.75, 8, true, false, 1, false, false, 60, 0.25, false),
            new NightmareTier(3, 1.30, 1.10, 0.75, 8, true, true, 1, false, false, 60, 0.25, false),
            new NightmareTier(4, 1.40, 1.10, 0.75, 6, true, true, 1, false, false, 60, 0.25, false),
            new NightmareTier(5, 1.50, 1.20, 0.75, 6, true, true, 2, false, false, 60, 0.25, false),
            new NightmareTier(6, 1.60, 1.20, 0.75, 6, true, true, 2, true, false, 60, 0.25, false),
            new NightmareTier(7, 1.70, 1.30, 0.75, 6, true, true, 2, true, true, 60, 0.25, false),
            new NightmareTier(8, 1.80, 1.30, 0.75, 6, true, true, 2, true, true, 40, 0.25, false),
            new NightmareTier(9, 1.90, 1.40, 0.75, 6, true, true, 2, true, true, 40, 0.15, false),
            new NightmareTier(10, 2.00, 1.50, 0.75, 6, true, true, 2, true, true, 40, 0.15, true));

    // 段 n (1〜10)。0 以下は普通の戦い、10 を超えた分は 10 (繰り返し)
    public static NightmareTier of(int n) {
        if (n <= 0) {
            return NORMAL;
        }
        return TABLE.get(Math.min(n, MAX) - 1);
    }

    public boolean nightmare() {
        return n > 0;
    }

    // 前の段から、決まり (技・灯り・波の数など) が一つでも変わったか (段ごとに一つ以上増えることの確かめ)
    public boolean changesFrom(NightmareTier o) {
        return clawTwice != o.clawTwice || shadesStrike != o.shadesStrike || waves != o.waves || darkRegen != o.darkRegen
                || lungeFollow != o.lungeFollow || snuffTicks != o.snuffTicks || darkShare != o.darkShare || heartRing != o.heartRing
                || lanterns != o.lanterns || lanternLife != o.lanternLife;
    }
}
