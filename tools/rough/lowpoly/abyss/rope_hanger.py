"""縄の人 (rope_hanger) 低ポリ ラフ 3 案。深淵だけの敵 (どの層にも出る)。吹き抜けの闇から縄で垂れ下がり、人を真ん中の吹き抜けへ引く。
A 垂れた一人 (人の形が縄で首から吊られ、腕は下へ垂れる。縄は闇の上へ伸びる) / B 縄の束の人 (体に縄が螺旋に巻き付いた人。縄の束が体の形を作る) / C 三人の列 (三人が一本の横縄に等間隔で吊られる。遠くから見ると一本の縄の飾り)
骨: root > figure (胴) > head / arm_l / arm_r ; root > leg_l / leg_r ; root > rope (縄は吹き抜けの上から)。縄の上端は y = 44 (闇の中へ続く)。
人の 1 倍。三角形は 400〜600 が目安 (C は三体で 800 前後)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "figure": ("root", (0, 20.0, 0)), "head": ("figure", (0, 24.2, 0)),
         "arm_l": ("figure", (3.4, 21.0, 0)), "arm_r": ("figure", (-3.4, 21.0, 0)),
         "leg_l": ("root", (1.6, 14.0, 0)), "leg_r": ("root", (-1.6, 14.0, 0)),
         "rope": ("root", (0, 44.0, 0))}
PAL = {"mats": {
    "slate": mat(SLATE, 0.5, crack=0.0, noise=0.14),
    "slate_b": mat(SLATE, 0.42, crack=0.0, noise=0.1),
    "void": mat(VOID, 0.5, crack=0.0, noise=0.1),
    "rope": mat([(40, 36, 30), (70, 62, 52), (104, 96, 82), (138, 128, 110)], 0.55, crack=0.0, noise=0.2),
    "bone": mat(BONEW, 0.55, crack=0.0, noise=0.12),
    "teal": mat(TEAL, 0.6, crack=0.0, noise=0.1),
}, "glow_mats": []}


def hanger(F, m, dx=0.0, tag="", rope_m="rope", wrap=False):
    """吊られた人 1 体 (頭の上に縄の付け根)。dx で横へずらす。wrap で縄の螺旋を巻く。"""
    R = [ring(1.6, 1.9, -1.0, 1.0, n=14, cx=dx), ring(9.0, 2.6, -1.4, 1.4, n=14, cx=dx),
         ring(15.5, 3.2, -1.6, 1.6, n=14, cx=dx), ring(20.5, 2.4, -1.2, 1.2, n=14, cx=dx)]
    F += part(tag + "body", loft("figure", m, R, caps=(m, m)))
    F += part(tag + "head", ellipsoid("head", m, (dx, 24.2, 0), (1.7, 2.0, 1.7), segs=8, rings=3))
    F += part(tag + "neck", tube("figure", m, [(dx, 19.6, 0), (dx, 22.6, 0)], ra=[1.0, 0.9], sides=6, caps=(True, True)))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(tag + b, tube(b, m, [(dx + s * 3.4, 21.0, 0), (dx + s * 4.2, 15.0, -0.4), (dx + s * 3.8, 9.5, -0.8)],
                                ra=[1.0, 0.75, 0.5], sides=9, caps=(True, True)))
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(tag + b, tube(b, m, [(dx + s * 1.6, 14.0, 0), (dx + s * 1.8, 7.0, 0), (dx + s * 1.8, 0.6, -0.6)],
                                ra=[1.1, 0.85, 0.7], sides=9, caps=(True, True)))
    if wrap:
        pts = []
        for i in range(28):
            t = i / 27.0
            a = 2 * math.pi * 2.5 * t
            pts.append((dx + 3.0 * math.cos(a), 3.0 + 20.0 * t, 2.0 * math.sin(a) * 0.9 - 0.0))
        F += part(tag + "wrap", tube("figure", rope_m, pts, ra=[0.55] * len(pts), sides=4, caps=(True, True)))
    return F


def build_a(k):
    """A 垂れた一人: 人の形が首の縄で吊られる。腕は下へ垂れ、縄は闇の上へ伸びる。"""
    F = []
    hanger(F, "slate")
    F += part("rope", tube("rope", "rope", [(0, 44.0, 0), (0, 36.0, 0.4), (0, 28.0, 0.2), (0, 26.2, 0)],
                           ra=[0.5, 0.4, 0.3, 0.2], sides=4, caps=(True, True)))
    return F, [eye(-0.7, 24.6, -1.8, 0.2), eye(0.7, 24.6, -1.8, 0.2)]


def build_b(k):
    """B 縄の束の人: 体に縄が螺旋に巻き付き、縄の束が体の形を作る。縄の上端は闇へ。"""
    F = []
    hanger(F, "void", wrap=True)
    F += part("rope", tube("rope", "rope", [(0, 44.0, 0), (0, 36.0, 0.4), (0, 28.0, 0.2), (0, 26.2, 0)],
                           ra=[0.5, 0.4, 0.3, 0.2], sides=4, caps=(True, True)))
    return F, [eye(-0.7, 24.6, -1.8, 0.2), eye(0.7, 24.6, -1.8, 0.2)]


def build_c(k):
    """C 三人の列: 三人が一本の横縄に吊られる。横縄の中央から縄が闇へ伸びる。"""
    F = []
    for i, dx in enumerate((-7.0, 0.0, 7.0)):
        hanger(F, "slate" if i != 1 else "slate_b", dx=dx, tag=f"p{i}_")
        F += part(f"hook{i}", tube("rope", "rope", [(dx, 27.0, 0), (dx, 26.0, 0), (dx, 25.0, 0)], ra=[0.25, 0.25, 0.25], sides=4, caps=(True, True)))
    F += part("cross", tube("rope", "rope", [(-7.0, 26.6, 0), (0.0, 26.6, 0), (7.0, 26.6, 0)], ra=[0.3, 0.3, 0.3], sides=4, caps=(True, True)))
    F += part("rope", tube("rope", "rope", [(0, 44.0, 0), (0, 36.0, 0.4), (0, 28.0, 0.2), (0, 26.9, 0)],
                           ra=[0.5, 0.4, 0.3, 0.3], sides=4, caps=(True, True)))
    return F, [eye(-0.7, 24.6, -1.8, 0.2), eye(0.7, 24.6, -1.8, 0.2)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "pull": {"figure": (0, 0, 0), "rope": (-14, 0, 0)}}

SPEC = dict(
    id="rope_hanger", short="縄の人", kind="深淵だけの敵 3案 (吊られた人)",
    title="縄の人 (rope_hanger) — 闇の上から縄で垂れ、人を吹き抜けへ引く・低ポリ 3 案",
    sub=["深淵だけ (どの層にも出る)。吹き抜けの闇から縄で垂れる。近づくと縄が伸び、人を真ん中の吹き抜けへ引く。",
         "左の目盛りは人 (1.8 ブロック)。縄の上端は闇の中へ続く (絵では途中まで)。"],
    labels=["A 垂れた一人", "B 縄の束の人", "C 三人の列"],
    poses=POSES,
    options={
        "a": dict(name="A 垂れた一人", desc="人の形が首の縄で吊られ、腕は下へ垂れる。縄は闇の上へ伸びて見えなくなる。一人だけなので、見上げた時に一番「人が吊られている」と分かる。"),
        "b": dict(name="B 縄の束の人", desc="体に縄が螺旋に巻き付き、縄の束が体の形を作る。縄の一本一本が体を締める形で、動くと束がほどけて見える。縄の色は石板の青灰で、人の影に溶ける。"),
        "c": dict(name="C 三人の列", desc="三人が一本の横縄に等間隔で吊られる。遠くから見ると一本の縄の飾りに見え、近づくと人の列だと分かる。引く時は中央の縄が闇へ伸びる。"),
    },
    extra="深淵だけの敵 (吊られる)。骨 9 本 (root > figure > head / arm_l / arm_r、root > leg_l / leg_r、root > rope)。縄は吹き抜けの上端 (y = 44) へ続く。",
    budget="人型 400〜600 (C は三体で 800 前後)",
    pw=380, atlas_h=170,
)
ROWS = [("前 3/4", "idle", 212, 18, 260, (0, 22, 0),6.0, None),
        ("横", "idle", 270, 6, 260, (0, 22, 0),6.0, None),
        ("斜め下から (見上げた時)", "idle", 200, -30, 260, (0, 22, 0),6.0, None),
        ("引かれる (縄がたわむ)", "pull", 212, 18, 260, (0, 22, 0),6.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=128)
