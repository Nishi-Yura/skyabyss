package jp.revati.skyabyss.cinematic;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;

// 場面の定義 (Java の台本。docs/design/19_cinematics.md)。場面の座標は (右, 上, 前) で、前は場面ごとに決める向き
//   opening           : 世界の始まり。前 = 最初の目的 (煙) の向き。約 36 秒
//   whale_first_sight : 岩鯨を初めて見る。前 = 岩鯨の向き。狙う物 = 岩鯨 (動いても追う)。約 20 秒
//   heart_<titan>     : 巨神の心臓が現れる (ボスの担当が TitanCinematics.heartAppears から呼ぶ)。前 = 心臓の向き。狙う物 = 心臓。約 8 秒
public final class Scenes {
    public static final String OPENING = "opening";
    public static final String WHALE_FIRST_SIGHT = "whale_first_sight";
    public static final String HEART_PREFIX = "heart_";
    // 設定の視野 (度)。撮影の fov はこの値との比で効かせる
    public static final float BASE_FOV = 70f;

    private static final Map<String, Scene> ALL = new LinkedHashMap<>();

    static {
        add(opening());
        add(whaleFirstSight());
        for (String titan : new String[] {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER}) {
            add(heartAppears(titan));
        }
    }

    private Scenes() {
    }

    @Nullable
    public static Scene get(String id) {
        return ALL.get(id);
    }

    public static Iterable<Scene> all() {
        return ALL.values();
    }

    public static String heartId(String titan) {
        return HEART_PREFIX + titan;
    }

    private static void add(Scene scene) {
        ALL.put(scene.id(), scene);
    }

    // 開幕 (36 秒)。0〜10 秒: 暗闇からまぶたが開き、地面に寝た目線で空を見上げる。10〜20 秒: 起き上がるように空へ昇って主人公を見下ろす
    // (題字)。20〜29 秒: 地平と空の高さを見渡す。29〜36 秒: 主人公の目に戻り、最初の目的 (煙の立つ方) を向いて操作へ
    private static Scene opening() {
        return Scene.of(OPENING).intro().handBack().seamless()
                .shot(Shot.of(10f).pos(1.1, 0.3, 0.8).posTo(1.4, 0.5, 1.0)
                        .look(Look.frame(0, 45, 10)).lookTo(Look.frame(0, 40, 24)).fov(62f, 66f).sway(0.5f).build())
                .shot(Shot.of(10f).pos(1.4, 0.5, 1.0).posTo(4.0, 6.0, -8.0)
                        .look(Look.frame(0, 40, 24)).lookTo(Look.frame(0, 1.3, 0)).fov(66f, 70f).ease(Ease.SMOOTHER).sway(0.2f).build())
                .shot(Shot.of(9f).pos(4.0, 6.0, -8.0).posTo(-5.0, 11.0, -14.0)
                        .look(Look.frame(0, 1.3, 0)).lookTo(Look.frame(0, 26, 180)).fov(70f, 78f).sway(0.2f).build())
                .shot(Shot.of(7f).pos(-5.0, 11.0, -14.0).posTo(0.0, CameraMath.EYE, 0.0)
                        .look(Look.frame(0, 26, 180)).lookTo(Look.frame(0, 8, 100)).fov(78f, 70f).ease(Ease.SMOOTH).build())
                .line(10.5f, 4.2f, Speaker.HERO, "opening.skyabyss.line")
                .line(15.2f, 4.6f, Speaker.HERO, "cinematic.skyabyss.opening.hero2")
                .line(21.0f, 4.6f, Speaker.HEAVEN, "cinematic.skyabyss.opening.heaven1")
                .line(26.2f, 2.8f, Speaker.HERO, "cinematic.skyabyss.opening.hero3")
                .line(30.0f, 5.0f, Speaker.HEAVEN, "cinematic.skyabyss.opening.heaven2")
                .build();
    }

    // 岩鯨の初見 (20 秒)。地平を見る → 鯨へ寄る → 鯨の全身を横から (巨大なので、離れた高い所から収める)。
    // カメラは本人より前 (鯨の側) に置き、本人の体が画面に入らないようにする
    private static Scene whaleFirstSight() {
        return Scene.of(WHALE_FIRST_SIGHT).handBack()
                .shot(Shot.of(5f).pos(0, 1.9, 0.5).posTo(0, 2.6, 1.0)
                        .look(Look.frame(0, 24, 120)).lookTo(Look.frame(0, 18, 150)).fov(70f, 70f).sway(0.3f).build())
                .shot(Shot.of(6f).pos(0, 2.6, 1.0).posTo(3.0, 6.0, 3.0)
                        .look(Look.frame(0, 18, 150)).lookTo(Look.target(0, 2, 0)).fov(70f, 62f).ease(Ease.SMOOTHER).sway(0.2f).build())
                .shot(Shot.of(9f).pos(3.0, 6.0, 3.0).posTo(-9.0, 10.0, 4.0)
                        .look(Look.target(0, 2, 0)).lookTo(Look.target(0, 2, 0)).fov(62f, 54f).ease(Ease.SMOOTH).sway(0.3f).build())
                .line(0.8f, 3.8f, Speaker.HERO, "cinematic.skyabyss.whale.hero1")
                .line(6.0f, 3.8f, Speaker.HERO, "cinematic.skyabyss.whale.hero2")
                .line(11.2f, 5.0f, Speaker.HEAVEN, "cinematic.skyabyss.whale.heaven")
                .line(16.4f, 3.2f, Speaker.HERO, "cinematic.skyabyss.whale.hero3")
                .build();
    }

    // 巨神の心臓が現れる (8 秒)。心臓を見上げ、横へ回り込んで全体を収める。台詞は巨神ごとに差し替えてよい (今は共通)
    private static Scene heartAppears(String titan) {
        return Scene.of(heartId(titan)).handBack()
                .shot(Shot.of(3.5f).pos(0, 1.9, 0.5).posTo(0, 3.0, 1.5)
                        .look(Look.target(0, 3, 0)).lookTo(Look.target(0, 4, 0)).fov(70f, 64f).sway(0.2f).build())
                .shot(Shot.of(4.5f).pos(0, 3.0, 1.5).posTo(7.0, 7.0, 4.0)
                        .look(Look.target(0, 4, 0)).lookTo(Look.target(0, 4, 0)).fov(64f, 58f).sway(0.2f).build())
                .line(0.6f, 3.4f, Speaker.HEAVEN, "cinematic.skyabyss.heart.heaven")
                .line(4.4f, 3.2f, Speaker.HERO, "cinematic.skyabyss.heart.hero")
                .build();
    }
}
