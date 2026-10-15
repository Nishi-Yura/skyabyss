package jp.revati.skyabyss.prologue;

// 見る者の段階。見た回数が増えるほど、少しずつ近くに、少しずつ長く立つようになる。
// 月の近さ (0〜1) が上がっても一段進む (ループと月が入ったときのための入口。今は 0)。
// 値はサーバーが決めてパケットで送るので、クライアントとずれない
public record WatcherStage(int index, int minDistance, int maxDistance, int stareTicks, int lifetimeTicks, float tilt) {
    static final WatcherStage[] STAGES = {
            // 初めて: 遠く (58〜70)、見つめて 4 秒で消える、2 分たっても気づかれなければ消える
            new WatcherStage(0, 58, 70, 80, 2400, 6f),
            // 1〜2 回見た: 少し近く
            new WatcherStage(1, 50, 64, 70, 2800, 10f),
            // 3 回以上: 40 ブロックまで寄る。首のかしげが深くなる
            new WatcherStage(2, 42, 56, 60, 3200, 14f),
    };

    public static WatcherStage of(int sightings, float moon) {
        int index = sightings <= 0 ? 0 : sightings < 3 ? 1 : 2;
        if (moon >= 0.5f) {
            index++;
        }
        return STAGES[Math.min(index, STAGES.length - 1)];
    }

    public static WatcherStage byIndex(int index) {
        return STAGES[Math.max(0, Math.min(index, STAGES.length - 1))];
    }
}
