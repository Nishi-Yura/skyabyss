package jp.revati.skyabyss.titan.storm.back;

// [仮の実装 / STUB] 嵐の拍 (12 の 3.4節。凪 40・荒れる 5・嵐 40・静まる 5 秒) の今の段階。
// 本物は背中の担当 (担当 1) が BackCycle で作る。ここは名前だけそろえた最小限のもの (いつも凪)。統合で置き換える
public final class StormBackClock {
    public enum Phase {
        CALM, RISING, STORM, FALLING
    }

    private StormBackClock() {
    }

    public static Phase phase() {
        return Phase.CALM;
    }
}
