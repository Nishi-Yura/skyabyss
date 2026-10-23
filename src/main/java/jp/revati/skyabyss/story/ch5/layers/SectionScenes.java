package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.cinematic.Ease;
import jp.revati.skyabyss.cinematic.Look;
import jp.revati.skyabyss.cinematic.Scene;
import jp.revati.skyabyss.cinematic.Shot;
import jp.revati.skyabyss.cinematic.Speaker;

// 深淵の区切りの短い場面 (8 秒)。野営 (または心臓の控えの間) に初めて着いたとき、着いた所から前 (野営の真ん中・門の方) を見渡す。
//   一つ目: 肩の後ろから少し上がりながら前を見る。二つ目: そこから切れ目なく横へ回りこんで野営の奥 (門) を見る (撮影は前の終わりから始める)。台詞は主人公の心の声を二つ
//   ID: abyss_section_<bone|salt|storm|ash|heart>。台詞: cinematic.skyabyss.abyss_v2.<区切り>.hero1 / hero2。両側 (Scenes の static) で登録する
public final class SectionScenes {
    public static final String PREFIX = "abyss_section_";
    public static final List<String> SECTIONS = List.of("bone", "salt", "storm", "ash", "heart");

    private SectionScenes() {
    }

    public static List<Scene> all() {
        List<Scene> out = new ArrayList<>();
        for (String s : SECTIONS) {
            out.add(Scene.of(PREFIX + s).handBack()
                    .shot(Shot.of(4f).pos(0.6, 2.0, -1.2).posTo(0.4, 3.6, -2.4)
                            .look(Look.frame(0, 1.6, 6)).lookTo(Look.frame(0, 1.2, 8)).fov(70f, 64f).ease(Ease.SMOOTH).sway(0.2f).build())
                    .shot(Shot.of(4.5f).pos(0.4, 3.6, -2.4).posTo(-2.8, 2.8, 1.8)
                            .look(Look.frame(0, 1.2, 8)).lookTo(Look.frame(0, 1.6, 11)).fov(64f, 70f).ease(Ease.SMOOTHER).sway(0.2f).build())
                    .line(0.6f, 3.4f, Speaker.HERO, "cinematic.skyabyss.abyss_v2." + s + ".hero1")
                    .line(4.4f, 3.6f, Speaker.HERO, "cinematic.skyabyss.abyss_v2." + s + ".hero2")
                    .build());
        }
        return out;
    }
}
