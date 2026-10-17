package jp.revati.skyabyss.prologue;

// 見る者の段階。見た回数が増えるほど、少しずつ近くに、少しずつ長く立つようになる。
// 月の近さ (0〜1) が上がっても一段進む (ループと月が入ったときのための入口。今は 0)。
// 1 章以降も現れ続けるが、章が進んでも段階 2 (最も近い) より先へは進めない (00 の 2 = B)。
// 序章の段階を増やしても、1 章以降は CHAPTER_CAP で止まる
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

    // 1 章以降の上限 (段階 2)。章の数ではこれ以上近づけない
    public static final int CHAPTER_CAP = 2;

    public static WatcherStage of(int sightings, float moon) {
        return of(sightings, moon, 0);
    }

    // chapter: 0 = 序章、1 = 1 章に入った (巨神の背に乗った)。章は段階を進めず、上限だけを決める
    public static WatcherStage of(int sightings, float moon, int chapter) {
        int index = sightings <= 0 ? 0 : sightings < 3 ? 1 : 2;
        if (moon >= 0.5f) {
            index++;
        }
        int cap = chapter >= 1 ? Math.min(CHAPTER_CAP, STAGES.length - 1) : STAGES.length - 1;
        return STAGES[Math.min(index, cap)];
    }

    public static WatcherStage byIndex(int index) {
        return STAGES[Math.max(0, Math.min(index, STAGES.length - 1))];
    }
}
