package jp.revati.skyabyss.cinematic;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;

// 場面の定義 (Java の台本。docs/design/20_cinematics.md)。場面の座標は (右, 上, 前) で、前は場面ごとに決める向き
//   opening           : 世界の始まり。前 = 最初の目的 (煙) の向き。約 36 秒
//   whale_first_sight : 岩鯨を初めて見る。前 = 岩鯨の向き。狙う物 = 岩鯨 (動いても追う)。約 20 秒
//   titan_first_<titan>: 潮殻・雷翼・余燼を初めて見る (岩鯨の初見と同じ作り。CinematicTriggers)。前 = 巨神の向き。狙う物 = 巨神。約 20 秒
//   heart_<titan>     : 巨神の心臓が現れる (心臓の登場の演出が始まると TitanCinematics が見せる)。前 = 心臓の向き。狙う物 = 心臓。約 4 秒。台詞は巨神ごと
//   titan_end_<titan> : 心臓を止めた人が地上へ降ろされたあと、巨神が眠りへ沈むのを地上から見る (初回だけ。TitanEnd)。前 = 巨神の向き。狙う物 = 巨神。約 16 秒
public final class Scenes {
    public static final String OPENING = "opening";
    public static final String WHALE_FIRST_SIGHT = "whale_first_sight";
    public static final String FIRST_PREFIX = "titan_first_";
    public static final String HEART_PREFIX = "heart_";
    public static final String END_PREFIX = "titan_end_";
    // 土地の町に初めて着いたとき (9 秒)。ContinentAreas が起こす
    public static final String TOWN_PREFIX = "town_first_";
    public static final String[] TOWNS = {"whale", "tide", "storm", "ember"};
    // 試練を越えて真ん中へ戻ったとき、光った碑を見せる (約 3.4 秒。docs/design/29 の 4.6)。前 = 碑の方。台詞なし
    // 設定の視野 (度)。撮影の fov はこの値との比で効かせる
    public static final float BASE_FOV = 70f;

    // 岩鯨以外で、初見の場面を持つ巨神 (岩鯨は whale_first_sight)
    public static final String[] FIRST_SIGHT_TITANS = {Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};

    private static final Map<String, Scene> ALL = new LinkedHashMap<>();

    static {
        add(opening());
        add(ferryCrossing());
        add(capitalArrival());
        for (String t : TOWNS) {
            add(townFirst(t));
        }
        add(whaleFirstSight());
        for (String titan : FIRST_SIGHT_TITANS) {
            add(titanFirstSight(titan));
        }
        for (String titan : new String[] {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER}) {
            add(heartAppears(titan));
            add(titanEnd(titan));
        }
        // 深淵の区切り (五章の一〜四・終わり)。story/ch5/layers/AbyssSections
        for (Scene s : jp.revati.skyabyss.story.ch5.layers.SectionScenes.all()) {
            add(s);
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

    public static String firstId(String titan) {
        return FIRST_PREFIX + titan;
    }

    public static String heartId(String titan) {
        return HEART_PREFIX + titan;
    }

    public static String endId(String titan) {
        return END_PREFIX + titan;
    }

    // 背中の初回の場面など、あとから場面を足す (FirstRun.registerScene)。同じ名前は置き換える
    public static void register(Scene scene) {
        add(scene);
    }

    // 足した場面を外す (自己テスト用)
    public static void unregister(String id) {
        ALL.remove(id);
    }

    private static void add(Scene scene) {
        ALL.put(scene.id(), scene);
    }

    // 開幕 (36 秒)。0〜10 秒: 暗闇からまぶたが開き、地面に寝た目線で空を見上げる。10〜20 秒: 起き上がるように空へ昇って主人公を見下ろす
    // (題字)。20〜29 秒: 高く引いて、はじまりの島の谷と道の先を見渡す (2026-10-26)。29〜36 秒: 主人公の目に戻り、最初の目的を向いて操作へ
    private static Scene opening() {
        return Scene.of(OPENING).intro().handBack().seamless()
                .shot(Shot.of(10f).pos(1.1, 0.3, 0.8).posTo(1.4, 0.5, 1.0)
                        .look(Look.frame(0, 45, 10)).lookTo(Look.frame(0, 40, 24)).fov(62f, 66f).sway(0.5f).build())
                .shot(Shot.of(10f).pos(1.4, 0.5, 1.0).posTo(4.0, 6.0, -8.0)
                        .look(Look.frame(0, 40, 24)).lookTo(Look.frame(0, 1.3, 0)).fov(66f, 70f).ease(Ease.SMOOTHER).sway(0.2f).build())
                .shot(Shot.of(9f).pos(4.0, 6.0, -8.0).posTo(-16.0, 30.0, -24.0)
                        .look(Look.frame(0, 1.3, 0)).lookTo(Look.frame(0, 6, 160)).fov(70f, 80f).ease(Ease.SMOOTHER).sway(0.2f).build())
                .shot(Shot.of(7f).pos(-16.0, 30.0, -24.0).posTo(0.0, CameraMath.EYE, 0.0)
                        .look(Look.frame(0, 6, 160)).lookTo(Look.frame(0, 8, 100)).fov(80f, 70f).ease(Ease.SMOOTH).build())
                .line(10.5f, 4.2f, Speaker.HERO, "opening.skyabyss.line")
                .line(15.2f, 4.6f, Speaker.HERO, "cinematic.skyabyss.opening.hero2")
                .line(21.0f, 4.6f, Speaker.HEAVEN, "cinematic.skyabyss.opening.heaven1")
                .line(26.2f, 2.8f, Speaker.HERO, "cinematic.skyabyss.opening.hero3")
                .line(30.0f, 5.0f, Speaker.HEAVEN, "cinematic.skyabyss.opening.heaven2")
                .build();
    }

    // はじまりの島から鐘の都への渡り (22 秒。world/continent/IslandTrail)。人は海の上の舟の上 (渡し守と二人)、前 = 鐘の都の向き。
    // 舟と渡し守を後ろから → 横から水平線 (大陸の岸) → 高く上がって行く手。台詞は渡し守・主人公・天の声。終わると都の南の門の外へ
    private static Scene ferryCrossing() {
        return Scene.of(jp.revati.skyabyss.world.continent.IslandTrail.CROSSING)
                .shot(Shot.of(7f).pos(3.2, 3.2, -5.5).posTo(2.2, 3.8, -3.5)
                        .look(Look.frame(0, 1.6, 0)).lookTo(Look.frame(0, 3.5, 30)).fov(62f, 66f).sway(0.5f).build())
                .shot(Shot.of(8f).pos(2.2, 3.8, -3.5).posTo(-7.0, 6.0, 10.0)
                        .look(Look.frame(0, 3.5, 30)).lookTo(Look.frame(0, 6, 140)).fov(66f, 58f).ease(Ease.SMOOTHER).sway(0.3f).build())
                .shot(Shot.of(7f).pos(-7.0, 6.0, 10.0).posTo(0.0, 14.0, 22.0)
                        .look(Look.frame(0, 6, 140)).lookTo(Look.frame(0, 10, 220)).fov(58f, 70f).ease(Ease.SMOOTH).sway(0.2f).build())
                .npcLine(0.8f, 4.4f, "cinematic.skyabyss.speaker.ferryman", "cinematic.skyabyss.ferry.f1")
                .line(5.6f, 3.6f, Speaker.HERO, "cinematic.skyabyss.ferry.hero1")
                .npcLine(9.8f, 4.6f, "cinematic.skyabyss.speaker.ferryman", "cinematic.skyabyss.ferry.f2")
                .line(14.8f, 3.8f, Speaker.HEAVEN, "cinematic.skyabyss.ferry.heaven1")
                .npcLine(18.8f, 3.2f, "cinematic.skyabyss.speaker.ferryman", "cinematic.skyabyss.ferry.f3")
                .build();
    }

    // 土地の町に初めて着く (9 秒)。前 = 町の真ん中 (広場)。高く上がって広場と町の名物を見渡し、本人の目に戻る。台詞は主人公と天の声
    private static Scene townFirst(String town) {
        return Scene.of(TOWN_PREFIX + town).handBack().seamless()
                .shot(Shot.of(4.5f).pos(0, CameraMath.EYE, 0).posTo(2.0, 14.0, -6.0)
                        .look(Look.frame(0, 2, 20)).lookTo(Look.frame(0, 4, 45)).fov(70f, 74f).ease(Ease.SMOOTHER).sway(0.2f).build())
                .shot(Shot.of(4.5f).pos(2.0, 14.0, -6.0).posTo(0, CameraMath.EYE, 0)
                        .look(Look.frame(0, 4, 45)).lookTo(Look.frame(0, 2, 30)).fov(74f, 70f).ease(Ease.SMOOTH).build())
                .line(0.4f, 3.8f, Speaker.HERO, "cinematic.skyabyss.town." + town + ".hero")
                .line(4.6f, 3.8f, Speaker.HEAVEN, "cinematic.skyabyss.town." + town + ".heaven")
                .build();
    }

    // 鐘の都に着いて、南の門の外から都を見上げる (8 秒)。前 = 都の真ん中 (大鐘楼)。見上げて大鐘楼 → 本人の目に戻って操作へ
    private static Scene capitalArrival() {
        return Scene.of(jp.revati.skyabyss.world.continent.IslandTrail.ARRIVAL).handBack().seamless()
                .shot(Shot.of(4f).pos(0, CameraMath.EYE, 0).posTo(0.0, 18.0, -6.0)
                        .look(Look.frame(0, 3, 20)).lookTo(Look.frame(0, 26, 110)).fov(70f, 66f).ease(Ease.SMOOTHER).sway(0.2f).build())
                .shot(Shot.of(4f).pos(0.0, 18.0, -6.0).posTo(0, CameraMath.EYE, 0)
                        .look(Look.frame(0, 26, 110)).lookTo(Look.frame(0, 2, 20)).fov(66f, 70f).ease(Ease.SMOOTH).build())
                .line(0.4f, 3.2f, Speaker.HERO, "cinematic.skyabyss.arrival.hero1")
                .line(4.0f, 3.6f, Speaker.HEAVEN, "cinematic.skyabyss.arrival.heaven1")
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

    // 潮殻・雷翼・余燼の初見 (20 秒。岩鯨の初見と同じ流れ)。地平を見る → 巨神へ寄る → 全身を横から。
    // 見る点の高さ (巨神の位置から) と視野は巨神の大きさに合わせる。台詞は cinematic.skyabyss.first.<巨神>.hero1 / hero2 / heaven / hero3
    private static Scene titanFirstSight(String titan) {
        // 地平を見る高さ (場面の座標。本人から)、全身の中ほどの高さ、視野
        double horizon;
        double mid;
        float[] fov;
        switch (titan) {
            case Titans.TIDE_SHELL -> {
                horizon = 3;
                mid = 14;
                fov = new float[] {62f, 54f};
            }
            case Titans.THUNDER_WING -> {
                horizon = 26;
                mid = 22;
                fov = new float[] {62f, 52f};
            }
            default -> {
                horizon = 8;
                mid = 28;
                fov = new float[] {62f, 52f};
            }
        }
        return Scene.of(firstId(titan)).handBack()
                .shot(Shot.of(5f).pos(0, 1.9, 0.5).posTo(0, 2.6, 1.0)
                        .look(Look.frame(0, horizon, 120)).lookTo(Look.frame(0, horizon + 4, 150)).fov(70f, 70f).sway(0.3f).build())
                .shot(Shot.of(6f).pos(0, 2.6, 1.0).posTo(3.0, 6.0, 3.0)
                        .look(Look.frame(0, horizon + 4, 150)).lookTo(Look.target(0, mid, 0)).fov(70f, fov[0]).ease(Ease.SMOOTHER).sway(0.2f).build())
                .shot(Shot.of(9f).pos(3.0, 6.0, 3.0).posTo(-9.0, 10.0, 4.0)
                        .look(Look.target(0, mid, 0)).lookTo(Look.target(0, mid, 0)).fov(fov[0], fov[1]).ease(Ease.SMOOTH).sway(0.3f).build())
                .line(0.8f, 3.8f, Speaker.HERO, "cinematic.skyabyss.first." + titan + ".hero1")
                .line(6.0f, 3.8f, Speaker.HERO, "cinematic.skyabyss.first." + titan + ".hero2")
                .line(11.2f, 5.0f, Speaker.HEAVEN, "cinematic.skyabyss.first." + titan + ".heaven")
                .line(16.4f, 3.2f, Speaker.HERO, "cinematic.skyabyss.first." + titan + ".hero3")
                .build();
    }

    // 巨神の心臓が現れる (4 秒。心臓の登場の演出 HeartBoss.INTRO_TICKS と同じ長さ)。心臓を見上げ、横へ回り込んで全体を収める。台詞は巨神ごと (cinematic.skyabyss.heart.<巨神>.heaven / .hero)
    private static Scene heartAppears(String titan) {
        return Scene.of(heartId(titan)).handBack()
                .shot(Shot.of(1.8f).pos(0, 1.9, 0.5).posTo(0, 3.0, 1.5)
                        .look(Look.target(0, 3, 0)).lookTo(Look.target(0, 4, 0)).fov(70f, 64f).sway(0.2f).build())
                .shot(Shot.of(2.2f).pos(0, 3.0, 1.5).posTo(7.0, 7.0, 4.0)
                        .look(Look.target(0, 4, 0)).lookTo(Look.target(0, 4, 0)).fov(64f, 58f).sway(0.2f).build())
                .line(0.2f, 1.8f, Speaker.HEAVEN, "cinematic.skyabyss.heart." + titan + ".heaven")
                .line(2.1f, 1.9f, Speaker.HERO, "cinematic.skyabyss.heart." + titan + ".hero")
                .build();
    }

    // 巨神が眠りへ沈むのを地上から見る (16 秒。docs/design/20 の 3.4)。人は巨神に降ろされたばかりで、前 = 巨神の方。
    // 三つの撮影: ①眠りの始まり (歌・光が消える) ②沈む・畳まれる・埋まる ③引いて全体。見る点の高さ dy は、巨神の姿の見せ所に合わせて巨神ごとに決める
    private static Scene titanEnd(String titan) {
        // 見る点の高さ (巨神の位置から): ① 始め → 終わり、② → 終わり、③ → 終わり。視野 (度): ①、②、③
        double[] dy;
        float[][] fov;
        double[][] cam;
        Speaker[] who;
        switch (titan) {
            case Titans.TIDE_SHELL -> {
                // 海へ沈み、珊瑚の段だけが水の上に残る。甲羅の頂 (高さ 34) から水面まで
                dy = new double[] {26, 24, 16, 8, 4};
                fov = new float[][] {{54f, 50f}, {50f, 46f}, {46f, 58f}};
                cam = new double[][] {{0, 1.9, 0.5, 0, 2.4, 1.0}, {0, 2.4, 1.0, 5, 5, 3}, {5, 5, 3, -5, 8, 2}};
                who = new Speaker[] {Speaker.HERO, Speaker.HEAVEN, Speaker.HERO};
            }
            case Titans.THUNDER_WING -> {
                // 翼を畳み、峰に伏せて岩の峰になる。翼の幅 ±112、体の高さ 30
                dy = new double[] {30, 28, 22, 16, 10};
                fov = new float[][] {{58f, 54f}, {54f, 54f}, {54f, 66f}};
                cam = new double[][] {{0, 1.9, 0.5, 0, 2.6, 1.0}, {0, 2.6, 1.0, 4, 5, 2}, {4, 5, 2, -6, 10, 3}};
                who = new Speaker[] {Speaker.HEAVEN, Speaker.HERO, Speaker.HEAVEN};
            }
            case Titans.EMBER -> {
                // 土へ埋まり、尾根だけが冷えた丘になる。火山の縁は底から 68
                dy = new double[] {58, 52, 42, 30, 24};
                fov = new float[][] {{54f, 50f}, {50f, 48f}, {48f, 62f}};
                cam = new double[][] {{0, 1.9, 0.5, 0, 2.4, 1.0}, {0, 2.4, 1.0, 5, 5, 3}, {5, 5, 3, -6, 8, 2}};
                who = new Speaker[] {Speaker.HERO, Speaker.HEAVEN, Speaker.HERO};
            }
            default -> {
                // 岩鯨: 歌がやみ、光が消え、雲の海へゆっくり沈む。体の中ほど (高さ 20) から
                dy = new double[] {24, 22, 16, 8, 2};
                fov = new float[][] {{54f, 50f}, {50f, 46f}, {46f, 60f}};
                cam = new double[][] {{0, 1.9, 0.5, 0, 2.4, 1.0}, {0, 2.4, 1.0, 4, 5, 2}, {4, 5, 2, -6, 9, 3}};
                who = new Speaker[] {Speaker.HEAVEN, Speaker.HERO, Speaker.HEAVEN};
            }
        }
        float[] secs = {4.5f, 6.0f, 5.5f};
        Scene.Builder b = Scene.of(endId(titan)).handBack();
        for (int i = 0; i < 3; i++) {
            Shot.Builder shot = Shot.of(secs[i]).pos(cam[i][0], cam[i][1], cam[i][2]).posTo(cam[i][3], cam[i][4], cam[i][5])
                    .look(Look.target(0, dy[i], 0)).lookTo(Look.target(0, dy[i + 1], 0)).fov(fov[i][0], fov[i][1])
                    .ease(i == 0 ? Ease.SMOOTH : Ease.SMOOTHER).sway(0.25f);
            b.shot(shot.build());
        }
        b.line(0.8f, 3.6f, who[0], "cinematic.skyabyss.end." + titan + ".1");
        b.line(6.0f, 4.6f, who[1], "cinematic.skyabyss.end." + titan + ".2");
        b.line(11.4f, 4.2f, who[2], "cinematic.skyabyss.end." + titan + ".3");
        return b.build();
    }
}
