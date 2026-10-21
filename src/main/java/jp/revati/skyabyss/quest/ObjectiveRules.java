package jp.revati.skyabyss.quest;

// 目的の帯の、画面に依らない決まり (クライアントの ObjectiveStrip・QuestHud が使い、自己テストが見る。first_hour_log B-4・B-8)
public final class ObjectiveRules {
    // 距離の札を出さない近さ (ブロック)。10 ブロック単位に丸めると 5 未満は「0m」になる
    public static final double HIDE_UNDER = 5.0;

    private ObjectiveRules() {
    }

    // 距離の札 (「230m NE」)。近すぎれば空 (札を出さない)
    public static String distanceLabel(double distance, String dir) {
        return distance < HIDE_UNDER ? "" : ((Math.round(distance / 10.0) * 10) + "m " + dir).trim();
    }
}
