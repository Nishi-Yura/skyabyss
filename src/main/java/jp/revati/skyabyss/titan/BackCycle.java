package jp.revati.skyabyss.titan;

import java.util.List;

// 巨神の背中の拍 (12 の 10.2節)。段階の並びと長さだけを持つ、状態のない部品。
// 潮殻の潮 (干潮 50, 満ちる 10, 満潮 50, 引く 10) と雷翼の嵐の拍 (凪 40, 荒れる 5, 嵐 40, 静まる 5) が使う。
// 時計の位置 (0〜length-1) の保存と進め方、止める (force) は巨神ごとの時計が持つ (TideClock・StormBackClock)。
public final class BackCycle {
    private final int[] lengths;
    private final int cycle;
    // 段階が変わる何 tick 前に知らせるか
    private final int warnTicks;

    public BackCycle(int warnTicks, int... lengths) {
        if (lengths.length == 0) {
            throw new IllegalArgumentException("no phases");
        }
        this.lengths = lengths.clone();
        int sum = 0;
        for (int l : lengths) {
            if (l <= 0) {
                throw new IllegalArgumentException("phase length must be positive");
            }
            sum += l;
        }
        this.cycle = sum;
        this.warnTicks = warnTicks;
    }

    public int length() {
        return cycle;
    }

    public int phases() {
        return lengths.length;
    }

    public int phaseLength(int index) {
        return lengths[index];
    }

    public int warnTicks() {
        return warnTicks;
    }

    // 周期の中の位置から段階の番号
    public int phaseAt(int t) {
        t = Math.floorMod(t, cycle);
        for (int i = 0; i < lengths.length; i++) {
            if (t < lengths[i]) {
                return i;
            }
            t -= lengths[i];
        }
        return lengths.length - 1;
    }

    // 段階の中の位置 (0 から)
    public int intoPhase(int t) {
        t = Math.floorMod(t, cycle);
        for (int length : lengths) {
            if (t < length) {
                return t;
            }
            t -= length;
        }
        return 0;
    }

    // 段階 index の始まりの位置
    public int startOf(int index) {
        int t = 0;
        for (int i = 0; i < index; i++) {
            t += lengths[i];
        }
        return t;
    }

    // 次に段階が変わるまで
    public int ticksLeft(int t) {
        return lengths[phaseAt(t)] - intoPhase(t);
    }

    // 位置 t で、段階 next が始まる warnTicks 前ちょうどなら true (知らせの音を一度だけ鳴らすため)
    public boolean warnsNow(int t, int next) {
        int at = Math.floorMod(startOf(next) - warnTicks, cycle);
        return Math.floorMod(t, cycle) == at;
    }

    public static List<Integer> seconds(BackCycle c) {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (int l : c.lengths) {
            out.add(l / 20);
        }
        return out;
    }
}
