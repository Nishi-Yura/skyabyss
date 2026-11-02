# -*- coding: utf-8 -*-
"""月秤 (moon_scale) 低ポリ ラフ 3 案。上弦の崖の主 (docs/design/46 の 4.4)。
崖の頂の主の間 (大きな天秤の皿の上) で戦う。皿を傾けて人を縁へ滑らせる・岩の錘を落とす・跳んで皿を叩き割る。
錘を自分の皿に落とさせると主の側が上がって崩れ、斬れる。どの案も「天秤 (棹・皿・錘)」を体に持ち、星の光の割れ目が弱点の印。
A 天秤を担ぐ巨人 (前かがみの石の巨人が、肩に長い棹を担ぐ。棹の両端から鎖で岩の皿が下がる。頭は三日月の仮面)
B 天秤の塔 (体そのものが支点の塔。塔の上に長い棹、両腕が皿を抱える。真ん中の上に三日月の顔)
C 皿を背負う甲虫 (大きな四つ足の甲虫。背の左右に二枚の皿、尾の先が丸い錘。前に二本の大きな角)
骨: root > body > head / beam (棹) / arm_l arm_r (皿の側) / leg_l leg_r (+ C は leg_l2 leg_r2) / tail (錘)。高さ 4〜5 ブロック。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 24, 0)), "head": ("body", (0, 50, -4)), "beam": ("body", (0, 46, 0)),
         "arm_l": ("body", (-10, 44, -2)), "arm_r": ("body", (10, 44, -2)), "leg_l": ("body", (-6, 22, 0)), "leg_r": ("body", (6, 22, 0)),
         "leg_l2": ("body", (-7, 20, 14)), "leg_r2": ("body", (7, 20, 14)), "tail": ("body", (0, 24, 14))}
PAL = make_pal({
    "basalt": (BASALT, 0.6, dict(crack=0.18, noise=0.2)),
    "tuff": ([(40, 46, 60), (66, 74, 90), (96, 104, 120), (128, 136, 150)], 0.62, dict(crack=0.14, noise=0.18)),
    "dust": (DUST, 0.66, dict(crack=0.05, noise=0.26)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "chain": (SILVER_D if "SILVER_D" in dir() else SILVER, 0.6, dict(crack=0.0, noise=0.1)),
    "hollow": (SHADOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "star": (STAR, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["star"])


def pan(F, bone, cx, cy, cz, r=5.0, m="tuff"):
    """岩の皿 (浅いお椀) と、縁から棹へ上がる三本の鎖"""
    R = [ring(cy - 1.4, r * 0.6, -r * 0.6, r * 0.6, n=12), ring(cy - 0.6, r * 0.95, -r * 0.95, r * 0.95, n=12), ring(cy, r, -r, r, n=12)]
    F += part(bone + "pan", loft(bone, m, [[(x + cx, y, z + cz) for x, y, z in rr] for rr in R], caps=("basalt", "dust")))
    return F


def chains(F, bone, top, cx, cy, cz, r=5.0):
    for i in range(3):
        a = i / 3 * 2 * math.pi + 0.5
        F += part(f"{bone}ch{i}", tube(bone, "silver", [(cx + math.cos(a) * r * 0.9, cy, cz + math.sin(a) * r * 0.9), top], ra=0.3, sides=4, hint=(0, 1, 0)))


def crescent_mask(F, bone, cx, cy, cz, s=1.0):
    outer = [(cx + math.cos(a) * 4.2 * s, cy + math.sin(a) * 4.2 * s, cz) for a in [math.pi * (0.5 + q / 10) for q in range(11)]]
    F += part("mask", tube(bone, "silver", outer, ra=[0.9 * s] * 3 + [1.6 * s] * 5 + [0.9 * s] * 3, sides=6, hint=(0, 0, 1)))


def build_a(k):
    """A 天秤を担ぐ巨人: 前かがみの石の巨人、肩に長い棹、両端から鎖で岩の皿。頭は三日月の仮面。"""
    F = []
    F += part("hips", ellipsoid("body", "basalt", (0, 24, 1), (7.0, 5.0, 5.0), segs=10, rings=4))
    F += part("chest", ellipsoid("body", "basalt", (0, 36, -2), (9.0, 8.0, 6.5), segs=11, rings=5))
    F += part("hump", ellipsoid("body", "tuff", (0, 42, 3), (7.0, 5.0, 5.0), segs=9, rings=4))
    F += part("head", ellipsoid("head", "basalt", (0, 46, -8), (3.6, 3.6, 3.4), segs=9, rings=4))
    crescent_mask(F, "head", 0, 47, -11.2)
    F += part("beam", tube("beam", "silver", [(-20, 46, 0), (0, 47.5, 0), (20, 46, 0)], ra=[1.0, 1.6, 1.0], sides=7, hint=(0, 1, 0)))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        # 腕は棹を押さえる
        F += part(b + "u", tube(b, "basalt", [(s * 9, 42, -2), (s * 14, 40, -4), (s * 15, 46, -1)], ra=[3.0, 2.4, 2.0], sides=7, hint=(0, 0, 1)))
        F += part(b + "h", ellipsoid(b, "tuff", (s * 15, 46.5, -0.5), (2.4, 2.0, 2.4), segs=7, rings=3))
        top = (s * 19.5, 45.6, 0)
        pan(F, b, s * 19.5, 22, 0, 6.5)
        chains(F, b, top, s * 19.5, 22, 0, 6.5)
        b2 = "leg_l" if s < 0 else "leg_r"
        F += part(b2 + "t", tube(b2, "basalt", [(s * 5, 22, 1), (s * 7, 11, -1), (s * 7, 2, 0)], ra=[3.6, 3.0, 2.6], sides=7, hint=(1, 0, 0)))
        F += part(b2 + "f", ellipsoid(b2, "tuff", (s * 7, 1.6, -1.5), (3.0, 1.8, 4.0), segs=7, rings=3))
    for i, pts in enumerate([[(-4, 42, -7.8), (-1, 36, -8.4), (-3, 30, -7.6)], [(4, 40, -8.0), (2, 33, -8.4)]]):
        F += part(f"crack{i}", tube("body", "star", pts, ra=0.35, sides=4, hint=(0, 0, 1)))
    return F, [eye_at(-1.4, 47.0, -11.3, 0.8), eye_at(1.4, 47.0, -11.3, 0.8)]


def build_b(k):
    """B 天秤の塔: 体そのものが支点の塔 (下が太く、上が細い)。塔の上に長い棹、両腕が皿を抱える。上の真ん中に三日月の顔。"""
    F = []
    rings = [ring(0.5, 9.0, -9.0, 9.0, n=10), ring(8.0, 7.6, -7.6, 7.6, n=10), ring(24.0, 5.4, -5.4, 5.4, n=10), ring(40.0, 4.2, -4.2, 4.2, n=10),
             ring(46.0, 3.0, -3.0, 3.0, n=10)]
    F += part("tower", loft("body", "basalt", rings, caps=("basalt", "tuff")))
    for i, y in enumerate((6.0, 18.0, 30.0)):
        r = 8.0 - i * 1.6
        F += part(f"band{i}", tube("body", "tuff", [(math.cos(a) * r, y, math.sin(a) * r) for a in [q / 10 * 2 * math.pi for q in range(11)]], ra=0.9, sides=5, hint=(0, 1, 0)))
    F += part("pivot", ellipsoid("beam", "silver", (0, 47.5, 0), (3.0, 3.0, 3.0), segs=8, rings=4))
    F += part("beam", tube("beam", "silver", [(-18, 47.5, 0), (18, 47.5, 0)], ra=1.2, sides=7, hint=(0, 1, 0)))
    F += part("head", ellipsoid("head", "hollow", (0, 53, -1), (2.6, 3.2, 2.0), segs=8, rings=4))
    crescent_mask(F, "head", 0, 53, -3.0, 1.0)
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(b + "u", tube(b, "basalt", [(s * 4, 40, -1), (s * 10, 36, -4), (s * 14, 28, -5)], ra=[2.2, 1.8, 1.6], sides=6, hint=(0, 0, 1)))
        F += part(b + "h", ellipsoid(b, "tuff", (s * 14, 27, -5), (2.0, 1.8, 2.0), segs=6, rings=3))
        top = (s * 17.5, 47, 0)
        pan(F, b, s * 17.5, 26, 0, 5.0)
        chains(F, b, top, s * 17.5, 26, 0, 5.0)
    F += part("crack", tube("body", "star", [(-2, 38, -4.3), (1, 30, -5.2), (-1, 22, -5.9), (2, 14, -6.9)], ra=0.35, sides=4, hint=(0, 0, 1)))
    return F, [eye_at(-1.0, 53.4, -2.95, 0.7), eye_at(1.0, 53.4, -2.95, 0.7)]


def build_c(k):
    """C 皿を背負う甲虫: 大きな四つ足の甲虫、背の左右に二枚の皿、尾の先が丸い錘、前に二本の大きな角。"""
    F = []
    F += part("thorax", ellipsoid("body", "basalt", (0, 22, -4), (9.0, 7.0, 9.0), segs=11, rings=5))
    F += part("abdomen", ellipsoid("body", "basalt", (0, 21, 9), (8.0, 6.5, 8.5), segs=11, rings=5))
    F += part("ridge", tube("body", "tuff", [(0, 29, -10), (0, 30, 0), (0, 28, 12)], ra=[1.0, 1.6, 1.0], sides=5, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "basalt", (0, 20, -14), (5.0, 4.0, 4.0), segs=9, rings=4))
    for s in (-1, 1):
        F += part(f"horn{s}", tube("head", "silver", [(s * 2.5, 22, -16), (s * 5, 28, -22), (s * 4, 33, -24)], ra=[1.4, 1.0, 0.3], sides=6, hint=(1, 0, 0)))
        b = "arm_l" if s < 0 else "arm_r"
        F += part(b + "post", tube(b, "silver", [(s * 6, 27, 2), (s * 13, 33, 2)], ra=0.8, sides=5, hint=(0, 0, 1)))
        pan(F, b, s * 16, 33, 2, 7.0)
        for b2, z in (("leg_l" if s < 0 else "leg_r", -4), ("leg_l2" if s < 0 else "leg_r2", 12)):
            F += part(b2 + "u", tube(b2, "basalt", [(s * 7, 18, z), (s * 13, 16, z), (s * 14, 1.5, z - 2)], ra=[2.4, 2.0, 1.6], sides=6, hint=(0, 1, 0)))
            F += part(b2 + "f", ellipsoid(b2, "tuff", (s * 14, 1.3, z - 2.5), (2.0, 1.2, 2.6), segs=6, rings=2))
    F += part("tail", tube("tail", "basalt", [(0, 20, 16), (0, 24, 22), (0, 30, 25)], ra=[3.0, 2.0, 1.4], sides=6, hint=(1, 0, 0)))
    F += part("weight", ellipsoid("tail", "silver", (0, 32, 26), (3.6, 3.6, 3.6), segs=9, rings=4))
    F += part("crack", tube("body", "star", [(-3, 28, -6), (0, 29.5, 0), (3, 28, 7)], ra=0.35, sides=4, hint=(1, 0, 0)))
    return F, [eye_at(-2.4, 21, -17.7, 0.8), eye_at(2.4, 21, -17.7, 0.8)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "tilt": {"beam": (0, 0, 18), "arm_l": (0, 0, 14), "arm_r": (0, 0, 14), "body": (0, 0, 6), "head": (0, 0, -6)},
}

SPEC = dict(
    id="moon_scale", short="月秤", kind="上弦の崖の主 3案 (46 の 4.4)",
    title="月秤 (moon_scale) — 上弦の崖の主。天秤の皿を傾けて落とす・低ポリ + ドット絵 3 案",
    sub=["崖の頂の天秤の皿の上で戦う。皿を傾けて縁へ滑らせる・岩の錘を落とす・跳んで皿を叩き割る。段 2 から皿の一部が崩れる。",
         "錘を主の側に落とさせると主が持ち上がって崩れ、斬れる。三段目は天秤を傾けた姿。"],
    labels=["A 天秤を担ぐ巨人", "B 天秤の塔", "C 皿を背負う甲虫"],
    rows=[("前 3/4", "idle", 212, 12, 300, (0, 28, -2), 4.4),
          ("横", "idle", 270, 4, 300, (0, 28, -2), 4.4),
          ("天秤を傾ける", "tilt", 200, 8, 300, (0, 28, -2), 4.4),
          ("後ろ 3/4", "idle", 32, 14, 300, (0, 28, -2), 4.4)],
    options={
        "a": dict(name="A 天秤を担ぐ巨人", desc="前かがみの石の巨人が肩に長い棹を担ぎ、両端から鎖で岩の皿が下がる。三日月の仮面。いちばん力強く、主らしい。"),
        "b": dict(name="B 天秤の塔", desc="体そのものが支点の塔。上に長い棹、両腕が皿を抱え、真ん中の上に三日月の顔。主の間の天秤と一体に見える。"),
        "c": dict(name="C 皿を背負う甲虫", desc="大きな四つ足の甲虫。背の左右に皿、尾の先に丸い錘、前に二本の角。動き回って皿を叩きつける。")},
    extra="月の言葉 (玄武岩・凝灰岩・銀の棹と鎖・星の光の割れ目)。骨 7〜11 本。",
    budget="区画の主 1200〜2400", atlas_h=150, pw=420,
    later="段 2 = 割れ目が広がり、皿の縁が欠ける。段 3 = 棹が折れて片腕で錘を振り回す。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
