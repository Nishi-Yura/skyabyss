# -*- coding: utf-8 -*-
"""崖這い (cliff_creeper) 低ポリ ラフ 3 案。上弦の崖の面に張り付いて登り降りし、上から岩を落とすもの (docs/design/46 の 4.4)。
落ちる所に影の円の予告。崖の面では腹を壁に付けて這う (描く側が向きを替える)。決め絵は岩を抱えて落とす姿。
A 平たい岩蜥蜴 (平たい玄武岩の体に、横へ広がる四本の脚と吸い付く指。尾の先が岩の瘤)
B 岩背負いの蟹 (丸い殻に大きな岩を背負い、六本の脚と二つの鋏。鋏で岩を外して落とす)
C 多腕の海星 (五本の腕の平たい星形。真ん中に一つの目、腕の裏に吸盤の星の光)
骨: root > body > head / tail / arm_l arm_r (前の脚・鋏) / leg_l leg_r (後ろの脚) / rock (抱えた岩)。幅 1.3 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 3, 0)), "head": ("body", (0, 3.4, -5)), "tail": ("body", (0, 3, 5)),
         "arm_l": ("body", (-3, 3, -3)), "arm_r": ("body", (3, 3, -3)), "leg_l": ("body", (-3, 3, 3)), "leg_r": ("body", (3, 3, 3)),
         "rock": ("body", (0, 7, 0))}
PAL = make_pal({
    "basalt": (BASALT, 0.6, dict(crack=0.16, noise=0.2)),
    "tuff": ([(40, 46, 60), (66, 74, 90), (96, 104, 120), (128, 136, 150)], 0.62, dict(crack=0.14, noise=0.18)),
    "dust": (DUST, 0.66, dict(crack=0.05, noise=0.26)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])


def splay_leg(F, b, x0, z0, side, front, m="basalt", r=0.7):
    """横へ広がる脚: 肩 → 膝 (上へ) → 指先 (下へ広げる)"""
    zf = -1 if front else 1
    F += part(b + "u", tube(b, m, [(x0, 3, z0), (x0 + side * 3.0, 4.6, z0 + zf * 1.4)], ra=[r, r * 0.8], sides=5, hint=(0, 1, 0)))
    F += part(b + "l", tube(b, m, [(x0 + side * 3.0, 4.6, z0 + zf * 1.4), (x0 + side * 4.6, 0.5, z0 + zf * 2.6)], ra=[r * 0.8, r * 0.55], sides=5, hint=(0, 1, 0)))
    for j in (-1, 0, 1):
        F += part(f"{b}t{j}", tube(b, "tuff", [(x0 + side * 4.6, 0.5, z0 + zf * 2.6), (x0 + side * (5.4 + 0.2 * j), 0.3, z0 + zf * 2.6 + j * 0.8)],
                                   ra=[0.28, 0.2], sides=4, hint=(0, 1, 0)))
        F += part(f"{b}p{j}", ellipsoid(b, "star", (x0 + side * (5.5 + 0.2 * j), 0.3, z0 + zf * 2.6 + j * 0.8), (0.25, 0.2, 0.25), segs=4, rings=2))


def held_rock(F, y=8.5):
    F += part("rock", ellipsoid("rock", "tuff", (0, y, 0.5), (2.6, 2.0, 2.4), segs=8, rings=4))
    F += part("rock2", ellipsoid("rock", "basalt", (1.2, y + 1.2, -0.4), (1.4, 1.2, 1.3), segs=6, rings=3))
    F += part("rockdust", ellipsoid("rock", "dust", (-0.6, y + 1.7, 0.8), (1.4, 0.5, 1.2), segs=6, rings=2))


def build_a(k):
    """A 平たい岩蜥蜴: 平たい玄武岩の体、横へ広がる四本の脚と吸い付く指、尾の先が岩の瘤。背に岩を一つ抱える。"""
    F = []
    F += part("torso", ellipsoid("body", "basalt", (0, 3.0, 0), (2.6, 1.4, 4.4), segs=10, rings=4))
    for i, z in enumerate((-2.6, -0.4, 1.8)):
        F += part(f"scute{i}", ellipsoid("body", "tuff", (0, 4.2, z), (1.4, 0.4, 1.0), segs=6, rings=2))
    F += part("head", ellipsoid("head", "basalt", (0, 3.4, -5.4), (1.8, 1.1, 2.0), segs=8, rings=3))
    F += part("jaw", ellipsoid("head", "tuff", (0, 2.7, -6.2), (1.3, 0.5, 1.3), segs=6, rings=2))
    F += part("tail", tube("tail", "basalt", [(0, 3.0, 4.0), (0, 2.8, 7.0), (0.6, 2.4, 10.0)], ra=[1.2, 0.8, 0.5], sides=6, hint=(0, 1, 0)))
    F += part("club", ellipsoid("tail", "tuff", (0.6, 2.5, 10.6), (1.2, 1.0, 1.3), segs=6, rings=3))
    for s in (-1, 1):
        splay_leg(F, "arm_l" if s < 0 else "arm_r", s * 2.2, -2.6, s, True)
        splay_leg(F, "leg_l" if s < 0 else "leg_r", s * 2.2, 2.6, s, False)
    held_rock(F, 6.6)
    return F, [eye_at(-1.25, 3.9, -6.6, 0.38), eye_at(1.25, 3.9, -6.6, 0.38)]


def build_b(k):
    """B 岩背負いの蟹: 丸い殻に大きな岩を背負い、六本の脚と前の二つの鋏。鋏で岩を外して落とす。"""
    F = []
    F += part("shell", ellipsoid("body", "basalt", (0, 4.0, 0), (4.2, 2.4, 3.6), segs=11, rings=5))
    F += part("rim", tube("body", "tuff", [(-4.0, 3.2, -1.6), (0, 3.0, -3.8), (4.0, 3.2, -1.6)], ra=0.5, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        for j, (b, z) in enumerate((("leg_l" if s < 0 else "leg_r", 2.6), ("body", 0.6), ("body", -1.2))):
            F += part(f"leg{s}{j}", tube(b, "basalt", [(s * 3.6, 3.4, z), (s * 6.0, 4.6, z * 1.2), (s * 7.0, 0.4, z * 1.4)], ra=[0.55, 0.45, 0.25], sides=5, hint=(0, 1, 0)))
        b = "arm_l" if s < 0 else "arm_r"
        F += part(b + "u", tube(b, "basalt", [(s * 3.0, 3.6, -3.0), (s * 4.6, 4.4, -5.6)], ra=[0.8, 0.7], sides=6, hint=(0, 1, 0)))
        F += part(b + "c1", ellipsoid(b, "tuff", (s * 4.8, 4.8, -7.0), (1.2, 0.9, 1.6), segs=7, rings=3))
        F += part(b + "c2", spike(b, "silver", (s * 4.6, 5.2, -8.2), (0, 0.3, -1), 1.8, 0.5, sides=4, rings=2))
        F += part(b + "c3", spike(b, "silver", (s * 5.2, 4.3, -8.0), (s * 0.3, -0.2, -1), 1.4, 0.4, sides=4, rings=2))
    F += part("head", ellipsoid("head", "tuff", (0, 4.4, -3.8), (1.6, 0.9, 0.9), segs=7, rings=3))
    for s in (-1, 1):
        F += part(f"stalk{s}", tube("head", "silver", [(s * 0.8, 4.8, -4.0), (s * 1.0, 6.4, -4.4)], ra=0.22, sides=4, hint=(1, 0, 0)))
    held_rock(F, 8.4)
    return F, [eye_at(-1.0, 6.6, -4.65, 0.36), eye_at(1.0, 6.6, -4.65, 0.36)]


def build_c(k):
    """C 多腕の海星: 五本の腕の平たい星形の体。真ん中に一つの目、腕の裏に星の光の吸盤。腕の二本で岩を抱える。"""
    F = []
    F += part("disc", ellipsoid("body", "basalt", (0, 3.0, 0), (3.2, 1.3, 3.2), segs=10, rings=4))
    bones = ["arm_l", "arm_r", "leg_l", "leg_r", "tail"]
    for i, b in enumerate(bones):
        a = -math.pi / 2 + i / 5 * 2 * math.pi
        dx, dz = math.cos(a), math.sin(a)
        pts = [(dx * 2.6, 3.0, dz * 2.6), (dx * 5.6, 3.6, dz * 5.6), (dx * 8.6, 2.2, dz * 8.6), (dx * 10.4, 0.6, dz * 10.4)]
        F += part(f"arm{i}", tube(b, "tuff" if i % 2 else "basalt", pts, ra=[1.5, 1.2, 0.8, 0.3], sides=6, hint=(0, 1, 0)))
        for j in range(3):
            t = 0.35 + j * 0.2
            F += part(f"suck{i}{j}", ellipsoid(b, "star", (dx * (2.6 + 7.8 * t), 1.6 + (1 - t) * 1.0, dz * (2.6 + 7.8 * t)), (0.35, 0.2, 0.35), segs=4, rings=2))
        F += part(f"ridge{i}", tube(b, "silver", [(dx * 3.0, 4.2, dz * 3.0), (dx * 7.0, 4.0, dz * 7.0)], ra=0.25, sides=4, hint=(0, 1, 0)))
    F += part("eye_ring", ellipsoid("head", "silver", (0, 4.2, 0), (1.4, 0.5, 1.4), segs=8, rings=2))
    F += part("eye_core", ellipsoid("head", "star", (0, 4.6, 0), (0.8, 0.4, 0.8), segs=6, rings=2))
    held_rock(F, 7.4)
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "drop": {"body": (-30, 0, 0), "rock": (-20, 0, 0), "arm_l": (-40, 0, 0), "arm_r": (-40, 0, 0), "head": (-15, 0, 0)},
}

SPEC = dict(
    id="cliff_creeper", short="崖這い", kind="上弦の崖の敵 3案 (46 の 4.4)",
    title="崖這い (cliff_creeper) — 崖の面に張り付いて這い、上から岩を落とす・低ポリ + ドット絵 3 案",
    sub=["崖の面に腹を付けて登り降りし、上にいる間に背の岩を落とす (落ちる所に影の円の予告)。地面に降りると脚で掻く。",
         "幅 1.3 ブロックほど。二段目は岩を落とす前に体を前へ傾けた姿。"],
    labels=["A 平たい岩蜥蜴", "B 岩背負いの蟹", "C 多腕の海星"],
    rows=[("前 3/4", "idle", 212, 18, 300, (0, 4, 0), 13.0),
          ("横", "idle", 270, 6, 300, (0, 4, 0), 13.0),
          ("岩を落とす", "drop", 222, 14, 300, (0, 4, 0), 13.0),
          ("上から (崖の面に張り付いた見え方)", "idle", 200, 70, 300, (0, 4, 0), 13.0)],
    options={
        "a": dict(name="A 平たい岩蜥蜴", desc="平たい玄武岩の体に、横へ広がる四本の脚と吸い付く指 (先が星の光)。尾の先が岩の瘤。崖の面でいちばん這う形。"),
        "b": dict(name="B 岩背負いの蟹", desc="丸い殻に大きな岩を背負い、六本の脚と二つの鋏。鋏で背の岩を外して落とす。何をするかが形で分かる。"),
        "c": dict(name="C 多腕の海星", desc="五本の腕の平たい星形、真ん中に一つの星の目、腕の裏に光る吸盤。崖の面で星の模様に見える。")},
    extra="月の言葉 (玄武岩・凝灰岩・月の砂・銀・星の光)。骨 9 本 (root > body > head / tail / 腕 2 / 脚 2 / rock)。",
    budget="中の敵 600〜1000", atlas_h=150, pw=420,
    later="二段目 = 星の割れ目。三段目 = 背の岩が二つ、落とす岩が割れて散る。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
