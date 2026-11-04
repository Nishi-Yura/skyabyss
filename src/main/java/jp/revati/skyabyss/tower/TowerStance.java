package jp.revati.skyabyss.tower;

// 週替わりの主の「構え」(docs/design/54 の 2.5。2026-11-05 のダンジョンの頁 2-1)。技そのものは変えず、よく使う技の組み合わせを変える。
// 四つを週ごとに巡る (週の数で決まる。全員同じ)。挑戦が始まったときの構えを、その挑戦のあいだ使う (TowerRun.stance)
//   星の週   星屑の雨をはじめから使い、二倍よく選ぶ。天の三矢は体力 6 割から (ふだんは 4 割)
//   月の週   落ちる月が速い (渡りきるまで 0.85 倍の時間)
//   淵の週   天と淵の群れを 3 技ごとに (ふだんは 4)。淵の吐息を二倍よく選ぶ
//   天秤の週 引き合いの高まりが早い (体力 75%・45%。ふだんは 60%・30%)
public enum TowerStance {
    STAR("star"), MOON("moon"), ABYSS("abyss"), BALANCE("balance");

    public static final double MOON_SPEED = 0.85;
    public static final float STAR_ARROWS = 0.6f;
    public static final float[] BALANCE_STOKE = {0.75f, 0.45f};
    public static final float[] STOKE = {0.6f, 0.3f};

    public final String id;

    TowerStance(String id) {
        this.id = id;
    }

    public static TowerStance thisWeek() {
        return of(TowerWagers.week().toEpochDay() / 7);
    }

    public static TowerStance of(long weekNumber) {
        return values()[(int) Math.floorMod(weekNumber, (long) values().length)];
    }

    public static TowerStance byId(String id) {
        for (TowerStance s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return STAR;
    }

    public String nameKey() {
        return "message.skyabyss.tower.stance." + id;
    }

    public String descKey() {
        return nameKey() + ".desc";
    }
}
