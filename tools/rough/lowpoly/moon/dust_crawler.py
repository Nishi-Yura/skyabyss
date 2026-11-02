"""砂這い (dust_crawler) 低ポリ ラフ 3 案。新月の谷の、月の砂に潜る這うもの (docs/design/46 の 4.3)。
砂の下を進み、足もとから跳び出して噛む。潜っている間は砂が盛り上がる (予告)。体の上半分が砂から出た姿が「跳び出す」の決め絵。
A 甲殻の掘り手 (平たい玄武岩の甲羅と、砂をかく大きな前脚の鋏。甲の縁に月の砂がこびりつく)
B 節の長虫 (五つの節の長い体。頭に放射状の顎、節ごとに短い脚。砂から弧を描いて跳ぶ)
C 砂の頭蓋 (砂でできた体に、昔の獣の頭蓋を被る。頭蓋の目の穴に星の光。脚は細い骨)
骨: root > body > head > jaw / tail / leg_l1 leg_l2 leg_l3 leg_r1 leg_r2 leg_r3。幅 1 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

LEGS = [("l", -1, 1, -2.4), ("l", -1, 2, 0.2), ("l", -1, 3, 2.8), ("r", 1, 1, -2.4), ("r", 1, 2, 0.2), ("r", 1, 3, 2.8)]
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 3.0, 0)), "head": ("body", (0, 3.4, -4.6)), "jaw": ("head", (0, 2.6, -6.4)),
         "tail": ("body", (0, 3.0, 5.0))}
for nm, s, i, z in LEGS:
    BONES[f"leg_{nm}{i}"] = ("body", (s * 2.8, 2.6, z))
PAL = make_pal({
    "basalt": (BASALT, 0.6, dict(crack=0.18, noise=0.22)),
    "dust": (DUST, 0.66, dict(crack=0.06, noise=0.3)),
    "tuff": (TUFFM, 0.6, dict(crack=0.16, noise=0.24)),
    "bone": (BONEC, 0.6, dict(crack=0.16, noise=0.22)),
    "hollow": (SHADOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])


def legs(F, m="basalt", r=0.5, reach=1.8):
    for nm, s, i, z in LEGS:
        b = f"leg_{nm}{i}"
        F += part(b, tube(b, m, [(s * 2.8, 2.6, z), (s * (3.9 + reach * 0.5), 3.6, z + 0.2 * (i - 2)), (s * (4.6 + reach), 0.0, z + 0.5 * (i - 2))],
                          ra=[r, r * 0.8, r * 0.35], sides=5, hint=(0, 1, 0)))


def build_a(k):
    """A 甲殻の掘り手: 平たく広い甲羅 (中央の稜と縁の段)、砂をかく大きな前脚の鋏、尾の先の掘り板。"""
    F = []
    R = [ring(1.6, 3.6, -4.0, 4.4, n=12), ring(3.4, 4.4, -4.6, 4.8, n=12), ring(4.9, 3.6, -3.8, 4.2, n=12), ring(5.7, 1.8, -2.0, 2.4, n=12)]
    F += part("shell", loft("body", "basalt", R, caps=("tuff", "basalt")))
    F += part("ridge", tube("body", "tuff", [(0, 5.6, -3.4), (0, 6.2, 0.0), (0, 5.6, 3.6)], ra=[0.35, 0.55, 0.3], sides=5, hint=(1, 0, 0)))
    for s in (-1, 1):
        for j, z in enumerate((-3.0, -0.6, 1.8, 3.8)):
            F += part(f"rim{s}{j}", ellipsoid("body", "dust", (s * (4.2 - abs(z) * 0.08), 3.4, z), (0.9, 0.55, 1.2), segs=6, rings=2))
        # 前の鋏 (砂をかく): 腕と二本の爪
        F += part(f"claw_arm{s}", tube("head", "basalt", [(s * 2.0, 2.8, -5.0), (s * 3.6, 2.6, -6.8), (s * 3.4, 1.6, -8.4)], ra=[0.7, 0.6, 0.5], sides=6, hint=(0, 1, 0)))
        F += part(f"claw_a{s}", spike("head", "tuff", (s * 3.4, 1.6, -8.4), (s * 0.3, -0.4, -1), 2.2, 0.55, sides=5, rings=2))
        F += part(f"claw_b{s}", spike("head", "tuff", (s * 3.2, 1.9, -8.2), (s * 0.7, -0.1, -0.7), 1.6, 0.4, sides=4, rings=2))
    F += part("head", ellipsoid("head", "basalt", (0, 3.0, -5.6), (2.2, 1.4, 1.8), segs=8, rings=4))
    F += part("jaw", ellipsoid("jaw", "tuff", (0, 2.2, -6.6), (1.4, 0.6, 1.0), segs=6, rings=2))
    F += part("tail", tube("tail", "basalt", [(0, 3.0, 4.6), (0, 2.6, 6.4), (0, 1.8, 7.6)], ra=[1.1, 0.8, 0.5], sides=6, hint=(1, 0, 0)))
    F += part("blade", ellipsoid("tail", "tuff", (0, 1.6, 8.2), (1.6, 0.35, 1.0), segs=6, rings=2))
    legs(F, "basalt", 0.45, 1.2)
    return F, [eye_at(-0.8, 3.5, -7.25, 0.34), eye_at(0.8, 3.5, -7.25, 0.34), eye_at(-1.5, 3.1, -6.95, 0.24), eye_at(1.5, 3.1, -6.95, 0.24)]


def build_b(k):
    """B 節の長虫: 五つの節 (前ほど大きい)。頭は放射状の顎 (六枚の牙) で口が丸く開く。節ごとに短い脚。"""
    F = []
    segs_ = [(-3.2, 3.6, 2.6), (-0.6, 3.4, 2.4), (1.9, 3.1, 2.1), (4.2, 2.8, 1.8), (6.2, 2.4, 1.4)]
    for i, (z, y, r) in enumerate(segs_):
        b = "body" if i < 3 else "tail"
        F += part(f"seg{i}", ellipsoid(b, "tuff" if i % 2 else "basalt", (0, y, z), (r * 1.15, r, r * 0.95), segs=9, rings=4))
        F += part(f"band{i}", tube(b, "dust", [(-r * 1.1, y + r * 0.2, z), (0, y + r * 1.02, z), (r * 1.1, y + r * 0.2, z)], ra=0.28, sides=4, hint=(0, 0, 1)))
    F += part("head", ellipsoid("head", "basalt", (0, 3.8, -5.6), (2.6, 2.4, 1.8), segs=10, rings=4))
    for i in range(6):
        a = i / 6 * 2 * math.pi
        x, y = math.cos(a) * 1.8, 3.8 + math.sin(a) * 1.7
        F += part(f"fang{i}", spike("jaw", "bone", (x, y, -6.8), (-math.cos(a) * 0.35, -math.sin(a) * 0.35, -1), 1.7, 0.36, sides=4, rings=2))
    F += part("mouth", ellipsoid("jaw", "hollow", (0, 3.8, -7.0), (1.1, 1.1, 0.4), segs=8, rings=2))
    legs(F, "basalt", 0.38, 0.6)
    return F, [eye_at(-1.6, 5.0, -6.95, 0.3), eye_at(1.6, 5.0, -6.95, 0.3)]


def build_c(k):
    """C 砂の頭蓋: 月の砂を固めた丸い体 (縁から砂がこぼれる) に、昔の獣の頭蓋を被る。目の穴に星の光。細い骨の脚。"""
    F = []
    F += part("body", ellipsoid("body", "dust", (0, 3.2, 0.6), (3.6, 2.6, 4.2), segs=11, rings=5))
    for i, (x, z) in enumerate(((-2.8, 2.6), (2.6, 3.2), (0.4, 4.4), (-1.6, -1.4))):
        F += part(f"spill{i}", ellipsoid("body", "dust", (x, 1.4, z), (1.4, 0.7, 1.4), segs=6, rings=2))
    F += part("skull", ellipsoid("head", "bone", (0, 3.8, -5.4), (2.4, 2.0, 2.6), segs=9, rings=4))
    F += part("snout", ellipsoid("head", "bone", (0, 3.0, -7.4), (1.4, 1.0, 1.4), segs=7, rings=3))
    F += part("jaw", ellipsoid("jaw", "bone", (0, 1.9, -7.0), (1.2, 0.5, 1.4), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"socket{s}", ellipsoid("head", "hollow", (s * 1.2, 4.3, -7.3), (0.75, 0.65, 0.35), segs=6, rings=2))
        F += part(f"horn{s}", spike("head", "bone", (s * 1.8, 5.0, -4.6), (s * 0.6, 0.6, 0.6), 3.0, 0.5, sides=5, rings=3))
        F += part(f"tooth{s}", spike("jaw", "bone", (s * 0.7, 1.6, -8.1), (0, -1, -0.2), 0.9, 0.22, sides=4, rings=1))
    legs(F, "bone", 0.3, 1.6)
    return F, [eye_at(-1.2, 4.3, -7.72, 0.36), eye_at(1.2, 4.3, -7.72, 0.36)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "leap": {"body": (-28, 0, 0), "head": (-12, 0, 0), "jaw": (30, 0, 0), "tail": (18, 0, 0),
             "leg_l1": (0, 0, 30), "leg_r1": (0, 0, -30), "leg_l3": (0, 0, -20), "leg_r3": (0, 0, 20)},
}

SPEC = dict(
    id="dust_crawler", short="砂這い", kind="新月の谷の敵 3案 (46 の 4.3)",
    title="砂這い (dust_crawler) — 月の砂に潜り、足もとから跳び出して噛む・低ポリ + ドット絵 3 案",
    sub=["月の砂の下を進み (砂が盛り上がるのが予告)、足もとから跳び出して噛む。上半分だけ砂から出た姿で待つことが多い。",
         "幅 1 ブロックほど。軽い重さの中で跳ぶので、跳び出しの姿 (二段目) がこの敵の決め絵。"],
    labels=["A 甲殻の掘り手", "B 節の長虫", "C 砂の頭蓋"],
    rows=[("前 3/4", "idle", 212, 18, 300, (0, 3.2, -1), 22.0),
          ("横", "idle", 270, 6, 300, (0, 3.2, -1), 22.0),
          ("跳び出す", "leap", 220, 10, 300, (0, 3.2, -1), 22.0),
          ("上から", "idle", 200, 60, 300, (0, 3.2, -1), 22.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 3.2, -1), 22.0)],
    options={
        "a": dict(name="A 甲殻の掘り手", desc="平たく広い玄武岩の甲羅と、砂をかく大きな前脚の鋏。甲の縁に月の砂。砂から甲羅だけが出て這う絵がいちばん「潜る」を伝える。"),
        "b": dict(name="B 節の長虫", desc="前ほど大きい五つの節と、放射状の牙の丸い口。砂から弧を描いて跳び出し、また潜る。動きがいちばん大きい。"),
        "c": dict(name="C 砂の頭蓋", desc="月の砂を固めた体に、昔の獣の頭蓋を被る。目の穴に星の光。砂に埋まると頭蓋だけが残り、骨のように見える (待ち伏せ)。")},
    extra="月の言葉 (玄武岩・月の砂・凝灰岩・骨・星の光)。骨 11 本 (root > body > head > jaw / tail / 脚 6)。",
    budget="中の敵 500〜900", atlas_h=150, pw=420,
    later="二段目 = 甲や節に星の光の割れ目が走る。三段目 = 体が大きくなり、背に月の欠片の棘が並ぶ。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
