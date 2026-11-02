# -*- coding: utf-8 -*-
"""月跳ね (moon_leaper) 低ポリ ラフ 3 案。上弦の崖の、長い脚で崖から崖へ跳ぶもの (docs/design/46 の 4.4)。
上から跳びかかる (落ちる所に影の円)。軽い重さで大きく跳ぶので、畳んだ脚と伸ばした脚の差が決め絵。
A 岩の跳び虫 (玄武岩の殻の胴に、折り畳んだ長い後ろ脚。前は短い鎌の腕。触角の先に星の光)
B 二本脚の跳ね獣 (太い二本の後ろ脚と長い尾で跳ぶ、頭の低い獣。背に凝灰岩の板)
C 発条脚の蜘蛛 (丸い胴に、ばねのように巻いた四対の細い脚。胴の下に星の光の目が一列)
骨: root > body > head / tail / leg_l / leg_r (後ろ脚) / arm_l / arm_r (前) / + C は leg_l2 leg_r2 leg_l3 leg_r3。高さ 1.2 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8, 0)), "head": ("body", (0, 10, -6)), "tail": ("body", (0, 8, 6)),
         "leg_l": ("body", (-3, 8, 3)), "leg_r": ("body", (3, 8, 3)), "arm_l": ("body", (-2.5, 7, -4)), "arm_r": ("body", (2.5, 7, -4)),
         "leg_l2": ("body", (-3, 8, 0)), "leg_r2": ("body", (3, 8, 0)), "leg_l3": ("body", (-3, 8, -3)), "leg_r3": ("body", (3, 8, -3))}
PAL = make_pal({
    "basalt": (BASALT, 0.6, dict(crack=0.16, noise=0.2)),
    "tuff": ([(40, 46, 60), (66, 74, 90), (96, 104, 120), (128, 136, 150)], 0.62, dict(crack=0.12, noise=0.18)),
    "dust": (DUST, 0.66, dict(crack=0.05, noise=0.26)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "hollow": (SHADOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])


def hind_leg(F, side, m="basalt", bone=None, r=1.0):
    b = bone or ("leg_l" if side < 0 else "leg_r")
    x = side * 3.0
    # 腿 (上へ折れて膝が背より高い) → 脛 (前下へ) → 長い足 (後ろへ)
    F += part(b + "th", tube(b, m, [(x, 8, 3), (x + side * 1.6, 13, 5.5)], ra=[1.6 * r, 1.0 * r], sides=6, hint=(1, 0, 0)))
    F += part(b + "sh", tube(b, m, [(x + side * 1.6, 13, 5.5), (x + side * 2.2, 1.6, 2.0)], ra=[0.9 * r, 0.6 * r], sides=6, hint=(1, 0, 0)))
    F += part(b + "ft", tube(b, "tuff", [(x + side * 2.2, 1.0, 2.0), (x + side * 2.4, 0.4, 7.0)], ra=[0.6 * r, 0.3 * r], sides=5, hint=(0, 1, 0)))


def build_a(k):
    """A 岩の跳び虫: 玄武岩の節の胴、折り畳んだ長い後ろ脚、短い鎌の前腕。触角の先に星。"""
    F = []
    F += part("thorax", ellipsoid("body", "basalt", (0, 8.5, -1.5), (2.8, 2.4, 3.6), segs=9, rings=4))
    F += part("abdomen", ellipsoid("body", "tuff", (0, 8.0, 4.0), (2.4, 2.1, 3.4), segs=9, rings=4))
    for i, z in enumerate((-3.0, -0.6, 1.8, 4.0)):
        F += part(f"plate{i}", tube("body", "silver" if i % 2 == 0 else "basalt", [(-2.5, 9.6, z), (0, 11.0, z), (2.5, 9.6, z)], ra=0.4, sides=4, hint=(0, 0, 1)))
    F += part("head", ellipsoid("head", "basalt", (0, 9.6, -6.0), (1.8, 1.6, 1.8), segs=8, rings=4))
    for s in (-1, 1):
        F += part(f"ant{s}", tube("head", "silver", [(s * 0.7, 10.8, -6.8), (s * 2.2, 13.5, -8.5), (s * 3.0, 14.6, -7.6)], ra=[0.2, 0.15, 0.1], sides=4, hint=(1, 0, 0)))
        F += part(f"anttip{s}", ellipsoid("head", "star", (s * 3.0, 14.6, -7.6), (0.4, 0.4, 0.4), segs=5, rings=2))
        hind_leg(F, s)
        b = "arm_l" if s < 0 else "arm_r"
        F += part(b + "u", tube(b, "basalt", [(s * 2.5, 7, -4), (s * 3.2, 4.4, -6.0)], ra=[0.6, 0.5], sides=5, hint=(1, 0, 0)))
        F += part(b + "k", spike(b, "silver", (s * 3.2, 4.4, -6.0), (0, -0.3, -1), 2.6, 0.45, sides=4, rings=2))
        F += part(f"mid{s}", tube("body", "basalt", [(s * 2.6, 7.6, 0.4), (s * 4.6, 5.0, 0.0), (s * 5.0, 0.3, -0.8)], ra=[0.45, 0.35, 0.2], sides=4, hint=(0, 1, 0)))
    return F, [eye_at(-1.0, 10.0, -7.6, 0.4), eye_at(1.0, 10.0, -7.6, 0.4)]


def build_b(k):
    """B 二本脚の跳ね獣: 太い二本の後ろ脚と長い尾で跳ぶ。頭は低く前へ、背に凝灰岩の板。前足は小さい。"""
    F = []
    F += part("torso", ellipsoid("body", "dust", (0, 9.0, 0.5), (2.6, 3.0, 3.6), segs=9, rings=4))
    F += part("chest", ellipsoid("body", "dust", (0, 9.0, -3.0), (2.0, 2.2, 2.2), segs=8, rings=4))
    F += part("neck", tube("head", "dust", [(0, 10.0, -4.0), (0, 10.4, -6.0)], ra=[1.3, 1.0], sides=6, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "dust", (0, 10.2, -7.2), (1.4, 1.3, 2.0), segs=8, rings=4))
    F += part("snout", ellipsoid("head", "silver", (0, 9.6, -9.0), (0.7, 0.5, 0.6), segs=5, rings=2))
    for s in (-1, 1):
        F += part(f"ear{s}", spike("head", "dust", (s * 0.9, 11.2, -6.6), (s * 0.4, 1, 0.6), 2.4, 0.5, sides=4, rings=2))
        # 跳ね獣の脚: 腿は前下へ太く、脛は後ろ下へ、長い足は前へ (かかとを上げて立つ)
        bl = "leg_l" if s < 0 else "leg_r"
        x = s * 3.0
        F += part(bl + "th", ellipsoid(bl, "dust", (x * 1.05, 6.6, 2.4), (1.5, 2.6, 2.2), segs=7, rings=3))
        F += part(bl + "sh", tube(bl, "dust", [(x * 1.1, 4.6, 1.6), (x * 1.15, 1.6, 5.2)], ra=[0.9, 0.55], sides=6, hint=(1, 0, 0)))
        F += part(bl + "ft", tube(bl, "tuff", [(x * 1.15, 1.0, 5.4), (x * 1.15, 0.45, 0.2)], ra=[0.55, 0.4], sides=5, hint=(0, 1, 0)))
        b = "arm_l" if s < 0 else "arm_r"
        F += part(b + "u", tube(b, "dust", [(s * 2.0, 7.6, -3.6), (s * 2.2, 5.4, -4.6)], ra=[0.5, 0.35], sides=5, hint=(1, 0, 0)))
    for i, z in enumerate((-1.6, 1.0, 3.4)):
        F += part(f"plate{i}", spike("body", "tuff", (0, 11.4 - abs(i - 1) * 0.4, z), (0, 1, 0.4), 2.4 - abs(i - 1) * 0.6, 1.4, sides=5, rings=2))
    F += part("tail", tube("tail", "dust", [(0, 8.4, 4.0), (0, 7.0, 8.0), (0, 4.0, 12.0), (0, 1.5, 15.0)], ra=[1.2, 0.9, 0.6, 0.3], sides=6, hint=(1, 0, 0)))
    F += part("tailtip", ellipsoid("tail", "star", (0, 1.4, 15.2), (0.5, 0.5, 0.5), segs=5, rings=2))
    return F, [eye_at(-0.85, 10.8, -8.55, 0.35), eye_at(0.85, 10.8, -8.55, 0.35)]


def build_c(k):
    """C 発条脚の蜘蛛: 丸い玄武岩の胴に、ばねのように巻いた四対の細い脚。胴の前に星の目が一列。"""
    F = []
    F += part("body", ellipsoid("body", "basalt", (0, 9.0, 0), (3.6, 2.8, 3.6), segs=10, rings=5))
    F += part("cap", ellipsoid("body", "tuff", (0, 11.0, 0.4), (2.6, 1.0, 2.6), segs=8, rings=3))
    F += part("head", ellipsoid("head", "basalt", (0, 9.0, -3.8), (2.0, 1.5, 1.4), segs=8, rings=3))
    legs = [("leg_l", -1, 3.0), ("leg_r", 1, 3.0), ("leg_l2", -1, 1.0), ("leg_r2", 1, 1.0), ("leg_l3", -1, -1.0), ("leg_r3", 1, -1.0),
            ("arm_l", -1, -3.0), ("arm_r", 1, -3.0)]
    for b, s, z in legs:
        # ばねの脚: 上へ持ち上がる腿のあと、脛を巻く (小さな螺旋)
        F += part(b + "t", tube(b, "basalt", [(s * 3.2, 8.6, z), (s * 6.0, 11.0, z * 1.3)], ra=[0.45, 0.35], sides=5, hint=(0, 1, 0)))
        pts = []
        for i in range(9):
            a = i / 8 * 2.5 * math.pi
            pts.append((s * (6.0 + 0.9 * math.cos(a)), 11.0 - i * 1.28, z * 1.3 + 0.9 * math.sin(a)))
        F += part(b + "s", tube(b, "silver", pts, ra=0.22, sides=4, hint=(0, 0, 1)))
        F += part(b + "f", ellipsoid(b, "tuff", (s * (6.0 + 0.9 * math.cos(2.5 * math.pi)), 0.55, z * 1.3 + 0.9 * math.sin(2.5 * math.pi)), (0.55, 0.45, 0.55), segs=5, rings=2))
    eyes = []
    for i in range(5):
        x = (i - 2) * 0.75
        eyes.append(eye_at(x, 9.3 - abs(i - 2) * 0.15, -5.1 + abs(i - 2) * 0.2, 0.3))
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "pounce": {"body": (-25, 0, 0), "head": (-10, 0, 0), "leg_l": (45, 0, 0), "leg_r": (45, 0, 0), "arm_l": (-50, 0, 0), "arm_r": (-50, 0, 0),
               "tail": (20, 0, 0)},
}

SPEC = dict(
    id="moon_leaper", short="月跳ね", kind="上弦の崖の敵 3案 (46 の 4.4)",
    title="月跳ね (moon_leaper) — 長い脚で崖から崖へ跳び、上から跳びかかる・低ポリ + ドット絵 3 案",
    sub=["軽い重さで崖から崖へ大きく跳ぶ。上から跳びかかる (落ちる所に影の円の予告)。崖の上で群れずに一体ずつ待つ。",
         "高さ 1.2 ブロックほど。二段目は跳びかかる姿 (脚を伸ばし、体を前へ倒す)。"],
    labels=["A 岩の跳び虫", "B 二本脚の跳ね獣", "C 発条脚の蜘蛛"],
    rows=[("前 3/4", "idle", 212, 14, 300, (0, 8, 0), 13.0),
          ("横", "idle", 270, 4, 300, (0, 8, 0), 13.0),
          ("跳びかかる", "pounce", 222, 10, 300, (0, 8, 0), 13.0),
          ("後ろ 3/4", "idle", 32, 14, 300, (0, 8, 0), 13.0)],
    options={
        "a": dict(name="A 岩の跳び虫", desc="玄武岩の殻の胴に、背より高く折れた長い後ろ脚。短い鎌の前腕、触角の先に星の光。虫らしく素早い。"),
        "b": dict(name="B 二本脚の跳ね獣", desc="太い後ろ脚と長い尾で跳ぶ、頭の低い獣。背に凝灰岩の板、尾の先に星。殻背の兎と並ぶ「月の獣」。"),
        "c": dict(name="C 発条脚の蜘蛛", desc="丸い胴に、ばねのように巻いた銀の脚が四対。胴の前に星の目が一列。いちばん月らしく不思議。")},
    extra="月の言葉 (玄武岩・凝灰岩・月の砂・銀・星の光)。骨 7〜13 本。",
    budget="中の敵 600〜1000", atlas_h=150, pw=420,
    later="二段目 = 星の割れ目。三段目 = 脚が長くなり、背に月の欠片の棘。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
