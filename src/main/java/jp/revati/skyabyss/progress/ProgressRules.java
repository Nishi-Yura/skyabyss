package jp.revati.skyabyss.progress;

import java.util.List;

import net.minecraft.util.Mth;

// 物語の進みの数値。ゲームの状態に触れない計算だけを置く (自己テストで直接たしかめる)。
// 表と理由は docs/design/10_progression_multiplayer.md
public final class ProgressRules {
    // 1 章の巨神の順番 (前の巨神の心臓を止めないと、次の巨神は現れず、乗れない)。名前は巨神の記憶 (TitanMemoryData) と同じ
    public static final List<String> TITAN_ORDER = List.of("stone_whale", "tide_shell", "thunder_wing", "ember");
    // 巨神ごとの推奨レベル (00 の 1 番で決定: 12・18・24・30)
    public static final int[] TITAN_LEVEL = {12, 18, 24, 30};
    // 物語の段階 (止めた巨神の数 0〜4) ごとのレベル上限。次の巨神の推奨レベル +3 (経験値が減り始める所) に合わせる。
    // 巨神編を終えた後は 40。五章で心臓の番人を倒すと ABYSS_CAP (50。転生の Lv 50 に届く)
    public static final int[] LEVEL_CAP = {15, 21, 27, 33, 40};
    public static final int ABYSS_CAP = 50;
    // 番人を倒した記録の名前 (ProgressData の個人の進みに、巨神と並べて書く。TITAN_ORDER には入れない)
    public static final String WARDEN = "heart_warden";

    private ProgressRules() {
    }

    // 巨神の順番 (知らない名前は -1)
    public static int titanIndex(String titan) {
        return TITAN_ORDER.indexOf(titan);
    }

    // 前の巨神。最初の巨神と知らない名前は null
    public static String previousTitan(String titan) {
        int i = titanIndex(titan);
        return i <= 0 ? null : TITAN_ORDER.get(i - 1);
    }

    // 段階 stage (順番どおりに止めた巨神の数) の人がこの巨神に挑めるか
    public static boolean canChallenge(int stage, String titan) {
        int i = titanIndex(titan);
        return i >= 0 && stage >= i;
    }

    public static int levelCap(int stage) {
        return LEVEL_CAP[Mth.clamp(stage, 0, LEVEL_CAP.length - 1)];
    }

    // 四体を止め、心臓の番人も倒したら 50。番人だけ先に倒していても、四体がそろうまでは段階の上限のまま
    public static int levelCap(int stage, boolean wardenFelled) {
        return wardenFelled && stage >= TITAN_ORDER.size() ? ABYSS_CAP : levelCap(stage);
    }

    // 止めた巨神の集まりから、段階を数える (順番どおりに続いている所まで)。
    // 抜け道で後の巨神だけ止めていても、前が欠けていればそこで止まる
    public static int stage(java.util.function.Predicate<String> stopped) {
        int n = 0;
        for (String titan : TITAN_ORDER) {
            if (!stopped.test(titan)) {
                break;
            }
            n++;
        }
        return n;
    }
}
