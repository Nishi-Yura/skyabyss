package jp.revati.skyabyss;

import net.neoforged.neoforge.common.ModConfigSpec;

// Modの設定。設計書「ホラー演出の原則」の「設定で弱められる」に対応する。
// サーバー設定（ゲームの進み方）とクライアント設定（見え方の好み）を分け、マルチプレイでも各自が調整できるようにする。
public final class Config {
    // --- サーバー設定: ワールドごと。マルチプレイではサーバーの値が使われる ---
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue HORROR_EVENT_FREQUENCY = SERVER_BUILDER
            .comment("ホラー演出の発生頻度の倍率 (0 = 無効, 1 = 標準)")
            .defineInRange("horrorEventFrequency", 1.0, 0.0, 3.0);

    public static final ModConfigSpec.IntValue LOOP_LENGTH_DAYS = SERVER_BUILDER
            .comment("1ループの長さ (ゲーム内日数)。月はこの日数で落ちる")
            .defineInRange("loopLengthDays", 7, 3, 30);

    static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    // --- クライアント設定: プレイヤーごとの見え方の好み ---
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE = CLIENT_BUILDER
            .comment("羽ばたきなどで画面を揺らすか")
            .define("screenShake", true);

    public static final ModConfigSpec.DoubleValue SCREEN_EFFECT_STRENGTH = CLIENT_BUILDER
            .comment("影の下での画面効果（暗さ・縁の暗がり・霧）の強さ (0 = なし, 1 = 標準)")
            .defineInRange("screenEffectStrength", 1.0, 0.0, 1.5);

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private Config() {
    }
}
