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

    // 序章「見る者」(prologue/WatcherEvents)
    public static final ModConfigSpec.DoubleValue WATCHER_FREQUENCY = SERVER_BUILDER
            .comment("「見る者」(夜に一人でいると遠くに立つ人影) の現れる頻度の倍率 (0 = 現れない, 1 = 標準)。horrorEventFrequency にも掛かる")
            .defineInRange("watcherFrequency", 1.0, 0.0, 3.0);

    // 1章の空「削られた山頂」(sky/PeakScarEvents) と「雲の目」(sky/CloudEyeEvents)
    public static final ModConfigSpec.DoubleValue PEAK_SCAR_CHANCE = SERVER_BUILDER
            .comment("1章に入ったプレイヤーの近くを影が通ったとき、その下の自然の山頂を削る確率 (0 = 削らない)。プレイヤーの建築・拠点の近くは常に対象外。horrorEventFrequency が 0 なら削らない")
            .defineInRange("peakScarChance", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CLOUD_EYE_FREQUENCY = SERVER_BUILDER
            .comment("「雲の切れ間の目」(1章・月が半分以上近い夜) の頻度の倍率 (0 = 開かない, 1 = 標準)。horrorEventFrequency にも掛かる")
            .defineInRange("cloudEyeFrequency", 1.0, 0.0, 3.0);

    // 技の予告 (combat/Telegraph): 床の印はクライアントが描く。サーバーが粒も送るかどうか
    public static final ModConfigSpec.BooleanValue TELEGRAPH_SERVER_PARTICLES = SERVER_BUILDER
            .comment("技の予告の粒 (旧来の点線) を、床の印とは別にサーバーからも送るか。false = 送らない (床の印と、クライアントの telegraphStyle の粒だけ)")
            .define("telegraphServerParticles", false);

    static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    // --- クライアント設定: プレイヤーごとの見え方の好み ---
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE = CLIENT_BUILDER
            .comment("羽ばたきなどで画面を揺らすか")
            .define("screenShake", true);

    public static final ModConfigSpec.DoubleValue SCREEN_EFFECT_STRENGTH = CLIENT_BUILDER
            .comment("影の下での画面効果（暗さ・縁の暗がり・霧）の強さ (0 = なし, 1 = 標準)")
            .defineInRange("screenEffectStrength", 1.0, 0.0, 1.5);

    // 序章「見る者」(client/prologue/WatcherClient)
    public static final ModConfigSpec.BooleanValue WATCHER_VISIBLE = CLIENT_BUILDER
            .comment("「見る者」を自分の画面に出すか (false = サーバーが出そうとしても現れない)")
            .define("watcherVisible", true);

    // 1章の空「雲の目」(client/sky/CloudEyeClient)
    public static final ModConfigSpec.BooleanValue CLOUD_EYE_VISIBLE = CLIENT_BUILDER
            .comment("「雲の切れ間の目」を自分の画面に出すか (false = サーバーが出そうとしても開かない)")
            .define("cloudEyeVisible", true);

    public static final ModConfigSpec.DoubleValue CLOUD_EYE_INTENSITY = CLIENT_BUILDER
            .comment("「雲の切れ間の目」の強さ (光と音の大きさ。0.3 = 弱め, 1 = 標準)")
            .defineInRange("cloudEyeIntensity", 1.0, 0.3, 1.0);

    // 成長の表示 (rpg/client/GrowthHud) の置き場。マナのバー・技の枠・クエストの追跡もこれに合わせて動く (00 の 35)
    public enum GrowthHudPosition {
        TOP_LEFT,
        XP_BAR
    }

    public static final ModConfigSpec.EnumValue<GrowthHudPosition> GROWTH_HUD_POSITION = CLIENT_BUILDER
            .comment("成長の表示 (Lv と細いバー) の置き場。TOP_LEFT = 画面の左上, XP_BAR = 下の経験値バーの左 (画面が狭くて入らなければ左上)")
            .defineEnum("growthHudPosition", GrowthHudPosition.TOP_LEFT);

    // 画面の知らせ (client/notice/NoticeClient, docs/design/18_notices.md)
    public static final ModConfigSpec.DoubleValue NOTICE_SPEED = CLIENT_BUILDER
            .comment("字幕・中央の演出・上の帯の出ている長さの倍率 (0.5 = ゆっくり, 1 = 標準, 2 = 速い)")
            .defineInRange("noticeSpeed", 1.0, 0.5, 2.0);

    // 知らせの出し先: TOAST = 画面の演出だけ (既定)、CHAT = 今までどおりチャットだけ、BOTH = 両方
    public enum NotifyMode {
        TOAST,
        CHAT,
        BOTH
    }

    public static final ModConfigSpec.EnumValue<NotifyMode> NOTIFY_MODE = CLIENT_BUILDER
            .comment("知らせの出し先。TOAST = 画面の演出 (字幕・札・帯) だけ, CHAT = チャットだけ (今までどおり), BOTH = 両方。流れた知らせは N の「知らせの記録」で読み返せる")
            .defineEnum("notifyMode", NotifyMode.TOAST);

    public static final ModConfigSpec.BooleanValue OBJECTIVE_STRIP = CLIENT_BUILDER
            .comment("メインの目的を左上の細い帯に常に出すか (false = 出さない。全文は U のクエストログ)")
            .define("objectiveStrip", true);

    // 目的の道しるべ (client/guide): 左上の帯の矢印と、地面の道の出し方
    public enum TrailMode {
        ON,
        NEAR,
        OFF
    }

    public static final ModConfigSpec.BooleanValue QUEST_ARROW = CLIENT_BUILDER
            .comment("目的の帯の方角を、向いている向きに合わせて回る矢印にするか (false = 北東などの文字)")
            .define("questArrow", true);

    public static final ModConfigSpec.EnumValue<TrailMode> QUEST_TRAIL = CLIENT_BUILDER
            .comment("目的の場所へ向かう、地面の道しるべ。ON = いつも, NEAR = 目的が 64 ブロック以内のときだけ, OFF = 出さない (キーでも切り替えられる)")
            .defineEnum("questTrail", TrailMode.ON);

    public static final ModConfigSpec.BooleanValue CINEMATIC_SCENES = CLIENT_BUILDER
            .comment("大事な場面 (開幕・岩鯨の初見など) でカメラが動く演出を見るか (false = 見ない。物語の効果は変わらず、すぐ操作へ戻る)")
            .define("cinematicScenes", true);

    public static final ModConfigSpec.BooleanValue PARTY_MARKERS = CLIENT_BUILDER
            .comment("パーティの仲間の位置 (頭の上の札・画面の端の矢印・別の場所の一行) を画面に出すか (false = 出さない。サーバーからは届くが描かない)")
            .define("partyMarkers", true);

    public static final ModConfigSpec.BooleanValue GRAVE_MARKER = CLIENT_BUILDER
            .comment("自分の墓の標 (画面の中の札・画面の端の矢印と距離。8 ブロック以内で消える) を画面に出すか (false = 出さない。サーバーからは届くが描かない)")
            .define("graveMarker", true);

    // 技の予告の見せ方 (client/combat/TelegraphRenderer): DECAL = 床に満ちる面と縁 (既定)、PARTICLES = 点線の粒、BOTH = 両方
    public enum TelegraphStyle {
        DECAL,
        PARTICLES,
        BOTH
    }

    public static final ModConfigSpec.EnumValue<TelegraphStyle> TELEGRAPH_STYLE = CLIENT_BUILDER
            .comment("技の予告の見せ方。DECAL = 床に満ちる面と縁 (既定), PARTICLES = 点線の粒, BOTH = 両方")
            .defineEnum("telegraphStyle", TelegraphStyle.DECAL);

    public static final ModConfigSpec.BooleanValue TELEGRAPH_HIGH_CONTRAST = CLIENT_BUILDER
            .comment("技の予告の高コントラスト (面を濃く、縁を太く、縁の下に黒い縁取りを敷く)")
            .define("telegraphHighContrast", false);

    // 夜の歌の「星が欠ける」見せ方 (開発用の選定。ユーザーが選ぶまで既定は CURRENT)
    public enum StarEffectVariant {
        CURRENT,
        A,
        B,
        C
    }

    public static final ModConfigSpec.EnumValue<StarEffectVariant> STAR_EFFECT = CLIENT_BUILDER
            .comment("夜の歌で星が欠ける見せ方 (開発用の選定)。CURRENT = 今の帯, A = 暗い影の帯 (縁で星が瞬いて消える), B = 星空のゆがみ (レンズ), C = 淡い光の輪郭 + 低い音のうねり")
            .defineEnum("starEffect", StarEffectVariant.CURRENT);

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private Config() {
    }
}
