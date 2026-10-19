package jp.revati.skyabyss.rebirth;

import net.neoforged.neoforge.common.ModConfigSpec;

// 転生の見せ方の設定 (C23)。色の段と記号だけは固定で、飾りは自分の側で切れる。
// クライアント: 自分の画面での見え方 (名札の効果・背中の飾り・倒れの光・プリズムを止める)。サーバー: チャットの接頭辞・墓の飾り
public final class RebirthConfig {
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue NAMETAG_EFFECTS = CLIENT_BUILDER
            .comment("名札の効果 (金の縁取り・プリズムの流れ)。近く (5 マス以内) の人だけに出る。false で記号と色だけ")
            .define("nametagEffects", true);
    public static final ModConfigSpec.BooleanValue NAMETAG_TITLE = CLIENT_BUILDER
            .comment("名札の横に称号を小さく出す (5 マス以内)")
            .define("nametagTitle", true);
    public static final ModConfigSpec.BooleanValue BACK_DECOR_SELF = CLIENT_BUILDER
            .comment("自分の背中の飾り (転生 5・10・20 回) を自分の画面で描く")
            .define("backDecorSelf", true);
    public static final ModConfigSpec.BooleanValue BACK_DECOR_OTHERS = CLIENT_BUILDER
            .comment("ほかの人の背中の飾りを描く")
            .define("backDecorOthers", true);
    public static final ModConfigSpec.BooleanValue BURST = CLIENT_BUILDER
            .comment("倒れたとき・立ち上がるときの段の色の光 (1 秒まで)")
            .define("downedBurst", true);
    public static final ModConfigSpec.BooleanValue PRISM_STILL = CLIENT_BUILDER
            .comment("プリズムの色を回さず止める (動きを減らす)。名札・背中の羽・一覧の枠に効く")
            .define("prismStill", false);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    public static final ModConfigSpec.BooleanValue CHAT_PREFIX = SERVER_BUILDER
            .comment("チャット・倒れた知らせ・一覧の名前の前に 「✦n」 を段の色で付ける。ほかの Mod の名前の書き換えには重ねる (置き換えない)")
            .define("chatPrefix", true);
    public static final ModConfigSpec.BooleanValue GRAVE_FLAIR = SERVER_BUILDER
            .comment("転生した人の墓に ✦ の数・色・花を付ける")
            .define("graveFlair", true);
    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private RebirthConfig() {
    }

    // 設定の読み出し (まだ読み込まれていない・自己テストの環境では既定値)
    public static boolean on(ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }
}
