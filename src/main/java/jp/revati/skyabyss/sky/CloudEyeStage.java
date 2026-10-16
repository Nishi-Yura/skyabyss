package jp.revati.skyabyss.sky;

// 雲の目の段階。見た回数が増えるほど、少しずつ大きく、少しずつ長く開き、視界の端から真ん中へ寄ってくる。
// 月が落ちる直前 (0.85 以上) なら一段進む。値はサーバーが決めて送るので、クライアントとずれない
//   width     : 目の大きさの段階値。クライアントで視野の角度 (度) にする (0.92 倍: 44/52/60 → 40/48/55 度)
//   holdTicks : 開ききってから閉じ始めるまで (見つめられなければ)
//   elevation : 見上げる角度 (度)
//   offset    : 今見ている向きから左右にどれだけ外すか (度)。いきなり視界の真ん中には開かない
public record CloudEyeStage(int index, float width, int holdTicks, float elevationMin, float elevationMax, float offsetMin, float offsetMax) {
    static final CloudEyeStage[] STAGES = {
            new CloudEyeStage(0, 44f, 50, 46f, 60f, 60f, 110f),
            new CloudEyeStage(1, 52f, 70, 40f, 55f, 45f, 95f),
            new CloudEyeStage(2, 60f, 90, 34f, 50f, 35f, 80f),
    };
    // 気配が来てから開き始めるまで、開くのにかかる時間、閉じるのにかかる時間、雲が引くまで (tick)
    public static final int HUSH_TICKS = 30;
    public static final int OPEN_TICKS = 50;
    public static final int CLOSE_TICKS = 45;
    public static final int CLEAR_TICKS = 40;
    // 一回の長さの上限
    public static final int MAX_TICKS = HUSH_TICKS + OPEN_TICKS + 90 + CLOSE_TICKS + CLEAR_TICKS;

    public static CloudEyeStage of(int sightings, float moon) {
        int index = sightings <= 0 ? 0 : sightings < 3 ? 1 : 2;
        if (moon >= 0.85f) {
            index++;
        }
        return byIndex(index);
    }

    public static CloudEyeStage byIndex(int index) {
        return STAGES[Math.max(0, Math.min(index, STAGES.length - 1))];
    }
}
