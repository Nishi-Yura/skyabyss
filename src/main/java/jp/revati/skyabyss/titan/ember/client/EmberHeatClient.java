package jp.revati.skyabyss.titan.ember.client;

import jp.revati.skyabyss.titan.ember.back.EmberHeat;

// クライアントが知っている自分の熱 (EmberNetwork.Heat で届く)。熱の目盛り (B の HeatHud)・画面の縁の揺れ・火照りの見た目が読む。
// サーバーのクラスを読まないので、専用サーバーでも読み込んでよい (描画のクラスは持たない)
public final class EmberHeatClient {
    private static float heat;
    private static float heatO;
    // 最後に届いてからの tick (目盛りを出すかの判断に使う)
    private static long receivedAt;

    private EmberHeatClient() {
    }

    public static void receive(float value) {
        heatO = heat;
        heat = value;
        receivedAt = System.currentTimeMillis();
    }

    public static float heat() {
        return heat;
    }


    public static EmberHeat.Band band() {
        return EmberHeat.Band.of(heat);
    }

}
