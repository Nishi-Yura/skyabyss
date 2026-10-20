package jp.revati.skyabyss.client.titan;

// 心臓・中ボス・雷獣・灰の鍛冶のボスバーを、バニラの位置 (y = 12) より少し上へ描く量。名前と顔の印は隠れない (印の上端は y = 4)。
// 下端の記録 (NoticeClient) もこの分だけ上げて、下の部品 (技の名前・上の帯) を詰める
public final class BossBarShift {
    public static final int UP = 8;

    private BossBarShift() {
    }
}
