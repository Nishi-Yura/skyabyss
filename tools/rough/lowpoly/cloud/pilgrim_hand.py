# -*- coding: utf-8 -*-
"""巡礼者の手 (pilgrim_hand) 低ポリ 一品一案。ラスボス「雲上の巡礼者」の第 1 形態 (docs/design/50 の 4.3)。
雲の下から伸びる、巡礼者の大きな手 (高さ 5 ブロック)。雲の白の肌に、指の筋に沿って金の光の線。手首に金の腕輪、その下は雲にほどける。
指は長く細い。掌を下に向けて床を叩き、輪の雲を指で削る。
骨: root > wrist > palm > f1..f4 / thumb。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "wrist": ("root", (0, 26, 6)), "palm": ("wrist", (0, 40, 2)),
         "f1": ("palm", (-7.5, 52, -1)), "f2": ("palm", (-2.5, 55, -1)), "f3": ("palm", (2.6, 54, -1)), "f4": ("palm", (7.2, 50, -1)),
         "thumb": ("palm", (-9.0, 40, -2))}
PAL = make_pal({
    "skin": (CLOUDW, 0.6, dict(crack=0.0, noise=0.12)),
    "skin2": (CLOUDW, 0.5, dict(crack=0.02, noise=0.16)),
    "mist": (CLOUDW, 0.42, dict(crack=0.0, noise=0.24)),
    "gold": (GOLDW, 0.7, dict(crack=0.02, noise=0.1)),
    "nail": (GOLDW, 0.82, dict(crack=0.0, noise=0.06)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])

# 指: (骨, 付け根, 長さ, 太さ, 少し外へ開く角度)
FINGERS = [("f1", (-7.5, 52, -1), 20, 2.0, -12), ("f2", (-2.5, 55, -1), 25, 2.2, -4), ("f3", (2.6, 54, -1), 23, 2.15, 4), ("f4", (7.2, 50, -1), 17, 1.8, 13)]


def build(k):
    F = []
    # 手首から下は雲にほどける (先が細い雲の帯)
    F += part("arm", tube("wrist", "skin2", [(0, 14, 8), (0, 26, 6), (0, 34, 4)], ra=[5.0, 5.4, 6.0], rb=[4.0, 4.4, 4.6], sides=8, hint=(1, 0, 0)))
    # 雲の塊 (腕を包んで下へ広がる。丸く重ねる)
    for i in range(9):
        a = i / 9 * math.pi * 2 + 0.3
        rr = 4.6 + (i % 3) * 1.2
        y = 6 + (i % 3) * 4.0
        F += part(f"puff{i}", ellipsoid("wrist", "mist" if i % 2 else "skin2", (math.cos(a) * rr * 0.8, y, 8 + math.sin(a) * rr * 0.7),
                                         (3.6 - (i % 3) * 0.6, 2.8, 3.4 - (i % 3) * 0.5), segs=8, rings=3))
    F += part("puffc", ellipsoid("wrist", "mist", (0, 4, 9), (6.5, 3.4, 5.6), segs=10, rings=3))
    F += part("cuff", tube("wrist", "gold", [(0, 27, 6), (0, 31, 5.2)], ra=[6.0, 6.2], rb=[4.9, 5.0], sides=10, hint=(1, 0, 0), caps=(True, True)))
    F += part("cuffg", tube("wrist", "dawn", [(0, 28.6, 6), (0, 29.4, 5.8)], ra=[6.3, 6.3], rb=[5.2, 5.2], sides=10, hint=(1, 0, 0), caps=(True, True)))
    # 掌 (幅広く、少し厚い)
    palm = [ring(34, 6.2, -3.2, 3.6, n=10), ring(40, 8.2, -3.0, 3.2, n=10), ring(46, 9.0, -2.6, 2.6, n=10), ring(51, 8.6, -2.2, 2.2, n=10), ring(54, 6.0, -1.8, 1.8, n=10)]
    F += part("palm", loft("palm", "skin", palm, caps=("skin", "skin")))
    # 掌の金の線 (指の筋へ)
    for i, (b, base, ln, r, ang) in enumerate(FINGERS):
        F += part(f"line{i}", tube("palm", "dawn", [(base[0] * 0.3, 38.0, -3.3), (base[0] * 0.8, 47.0, -2.9), (base[0], base[1] - 1, -2.4)], ra=0.3, rb=0.45, sides=3, hint=(0, 0, 1)))
    # 指 (三節。先に金の爪)
    for b, base, ln, r, ang in FINGERS:
        a = math.radians(ang)
        dx, dy = math.sin(a), math.cos(a)
        pts = [base, (base[0] + dx * ln * 0.4, base[1] + dy * ln * 0.4, base[2] - 0.6), (base[0] + dx * ln * 0.75, base[1] + dy * ln * 0.75, base[2] - 1.6),
               (base[0] + dx * ln, base[1] + dy * ln, base[2] - 3.0)]
        F += part(b + "s", tube(b, "skin", pts, ra=[r, r * 0.9, r * 0.8, r * 0.55], sides=6, hint=(1, 0, 0)))
        for j in (1, 2):
            F += part(f"{b}k{j}", ellipsoid(b, "skin2", pts[j], (r * 1.02, r * 0.75, r * 0.95), segs=6, rings=2))
        F += part(b + "n", spike(b, "nail", pts[3], (dx * 0.6, dy * 0.6, -0.8), 3.2, r * 0.6, sides=5, rings=2))
        F += part(b + "g", tube(b, "dawn", [(pts[0][0], pts[0][1], pts[0][2] - r * 0.85), (pts[2][0], pts[2][1], pts[2][2] - r * 0.75)], ra=0.25, rb=0.35, sides=3, hint=(0, 0, 1)))
    # 親指 (内へ寄る)
    tp = [(-9.0, 40, -2), (-13.0, 45, -4), (-15.0, 50, -6), (-15.8, 54, -8)]
    F += part("thumb", tube("thumb", "skin", tp, ra=[2.6, 2.2, 1.9, 1.3], sides=6, hint=(0, 0, 1)))
    F += part("thumbn", spike("thumb", "nail", tp[3], (-0.2, 0.6, -0.8), 2.8, 1.0, sides=5, rings=2))
    return F, []


POSES = {
    "idle": {},
    "slam": {"wrist": (-80, 0, 0), "f1": (-20, 0, 0), "f2": (-20, 0, 0), "f3": (-20, 0, 0), "f4": (-20, 0, 0)},
    "scrape": {"wrist": (-50, 0, -30), "f1": (-60, 0, 0), "f2": (-60, 0, 0), "f3": (-60, 0, 0), "f4": (-60, 0, 0), "thumb": (0, 0, -30)},
}

SPEC = dict(
    id="pilgrim_hand", short="巡礼者の手", kind="ラスボス 第 1 形態 一品一案 (50 の 4.3)",
    title="巡礼者の手 (pilgrim_hand) — 雲の下から伸びる大きな手・低ポリ + ドット絵 一案",
    sub=["座の輪の外から二つの手が伸び、床を叩き、輪の雲の足場を指で削る。叩いたあとは床に手を突いたまま (打てる隙)。",
         "雲の白の肌に指の筋の金の光、金の爪と腕輪。手首から下は雲にほどける。高さ 5 ブロック。"],
    labels=["巡礼者の手"],
    rows=[("前", "idle", 180, 4, 520, (0, 38, 0), 5.6),
          ("前 3/4", "idle", 212, 8, 520, (0, 38, 0), 5.6),
          ("叩く", "slam", 240, 8, 520, (0, 38, 0), 5.6),
          ("削る", "scrape", 150, 10, 520, (0, 38, 0), 5.6)],
    options={"a": dict(name="雲の白の大きな手", desc="長い指と金の爪、指の筋に金の光、金の腕輪、手首から下は雲。")},
    extra="雲海の言葉 (雲の白・淡い金・朝の光)。骨 8 本。",
    budget="主 1500〜3000", pw=440,
    later="二段目 = 金の線が手首から肘へ広がる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
