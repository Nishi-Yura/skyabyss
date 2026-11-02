package jp.revati.skyabyss.titan.vow;

// 誓い (周回だけの縛り)。数字だけで決まる部品の束で、効きは Vows / VowEvents が数字を読んで当てる。
// 新しい誓いは Vows.register(new Vow(...)) の一行で足せる (翻訳キー vow.skyabyss.<id> / vow.skyabyss.<id>.desc を忘れずに)。
//   points         : 誓いの重さ。選んだ誓いの合計が「誓いの段」で、報酬の倍率と称号を決める
//   foeHealth      : 敵 (背中の敵とボス) の体力の上乗せ (0.3 = +30%)。0 なら効かない
//   telegraphScale : 技の予告の長さの倍率 (0.75 = 25% 短い)。1 なら効かない。最短 0.8 秒 (Telegraph.MIN_WARN_TICKS) は割れない
//   damageTaken    : 人が受ける傷の上乗せ (0.25 = +25%)
//   noCamp         : 背中で野営の道具 (焚き火・たいまつ・灯り・箱) を置けない
//   timerTicks     : 時間制限 (tick)。0 なら無し。背中に入ってから心臓を止めるまでに超えると、この誓いは数えない
public record Vow(String id, int points, float foeHealth, float telegraphScale, float damageTaken, boolean noCamp, int timerTicks) {
    public Vow {
        if (points < 1) {
            throw new IllegalArgumentException("a vow must weigh at least 1: " + id);
        }
        if (telegraphScale <= 0 || telegraphScale > 1) {
            throw new IllegalArgumentException("telegraphScale must be in (0, 1]: " + id);
        }
    }

    public String nameKey() {
        return "vow.skyabyss." + id;
    }

    public String descKey() {
        return "vow.skyabyss." + id + ".desc";
    }
}
