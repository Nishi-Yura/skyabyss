# -*- coding: utf-8 -*-
"""巡礼者の目 (pilgrim_eye) 低ポリ 一品一案。ラスボス「雲上の巡礼者」の第 2 形態 (docs/design/50 の 4.3)。
雲が割れて現れる、巡礼者の大きな一つ目 (高さ 4 ブロック)。白目は雲の白、虹彩は朝の光の金、瞳は深い藍。
上下の瞼は厚い雲 (閉じると雲が合わさる)。まわりに金の光の睫毛 (放射の棘) と、後ろに細い金の輪。
骨: root > body > eye / lid_top / lid_bottom / halo。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

C = (0, 34, 0)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", C), "eye": ("body", C), "lid_top": ("body", (0, 42, 2)), "lid_bottom": ("body", (0, 26, 2)),
         "halo": ("body", C)}
PAL = make_pal({
    "white": (SCLERA, 0.7, dict(crack=0.0, noise=0.08)),
    "cloud": (CLOUDW, 0.8, dict(crack=0.0, noise=0.08)),
    "mist": (CLOUDW, 0.68, dict(crack=0.0, noise=0.12)),
    "gold": (GOLDW, 0.7, dict(crack=0.02, noise=0.1)),
    "iris": (GOLDW, 0.62, dict(crack=0.0, noise=0.16, min=1)),
    "pupil": (HOLLOW, 0.5, dict(crack=0.0, noise=0.04, min=0)),
    "dawn": (DAWN, 0.9, dict(crack=0.0, noise=0.04, min=1)),
}, glow=["iris", "dawn"])


def build(k):
    F = []
    # 目玉 (少し横長)
    F += part("ball", ellipsoid("eye", "white", C, (12.5, 10.5, 9.0), segs=16, rings=8))
    F += part("iris", ellipsoid("eye", "iris", (0, 34, -7.4), (6.6, 6.6, 2.4), segs=14, rings=4))
    F += part("pupil", ellipsoid("eye", "pupil", (0, 34, -9.1), (3.0, 4.2, 1.2), segs=10, rings=3))
    F += part("glint", ellipsoid("eye", "dawn", (2.4, 37.0, -9.6), (0.9, 0.9, 0.4), segs=6, rings=2))
    # 瞼の雲 (上と下。塊を重ねる)
    for lid, y0, s in (("lid_top", 43.0, 1), ("lid_bottom", 25.5, -1)):
        for i in range(7):
            x = -12 + i * 4.0
            yy = y0 + s * (1.6 - abs(x) * 0.06) + s * 1.2 * (i % 2)
            F += part(f"{lid}{i}", ellipsoid(lid, "cloud" if i % 2 else "mist", (x, yy, -3.0 + abs(x) * 0.25), (4.4, 3.4 + (i % 2), 4.2), segs=8, rings=3))
        F += part(lid + "b", ellipsoid(lid, "mist", (0, y0 + s * 3.4, 2.0), (15.0, 4.0, 6.0), segs=12, rings=3))
    # 脇の雲 (目の左右を包む)
    for sx in (-1, 1):
        for i in range(3):
            F += part(f"side{sx}{i}", ellipsoid("body", "mist" if i % 2 else "cloud", (sx * (13.5 + i * 1.5), 34 + (i - 1) * 4.0, 1.0), (4.5, 4.2, 4.6), segs=8, rings=3))
    # 金の光の睫毛 (放射の棘) と後ろの金の輪
    for i in range(16):
        a = math.radians(i * 22.5)
        ln = 7.0 if i % 2 == 0 else 4.5
        F += part(f"lash{i}", spike("halo", "gold", (math.cos(a) * 17.0, 34 + math.sin(a) * 15.0, 4.0), (math.cos(a), math.sin(a), 0.1), ln, 0.8, sides=4, rings=2))
    R = 18.5
    pts = [(math.cos(math.radians(a)) * R, 34 + math.sin(math.radians(a)) * (R * 0.88), 5.0) for a in range(0, 361, 12)]
    F += part("ring", tube("halo", "gold", pts, ra=0.7, rb=0.9, sides=4, hint=(0, 0, 1)))
    return F, []


POSES = {
    "idle": {},
    "closed": {"lid_top": (0, 0, 0), "lid_bottom": (0, 0, 0)},
}

SPEC = dict(
    id="pilgrim_eye", short="巡礼者の目", kind="ラスボス 第 2 形態 一品一案 (50 の 4.3)",
    title="巡礼者の目 (pilgrim_eye) — 雲が割れて現れる一つ目・低ポリ + ドット絵 一案",
    sub=["回る視線の筋 (見えていると重く、長いと傷)・凝視 (柱の陰なら当たらない) のあと目を閉じる (傷 ×1.5)。",
         "雲の白目に朝の光の金の虹彩と藍の瞳。瞼は厚い雲、まわりに金の睫毛と細い金の輪。高さ 4 ブロック。"],
    labels=["巡礼者の目"],
    rows=[("前", "idle", 180, 2, 520, (0, 34, 0), 6.0),
          ("前 3/4", "idle", 212, 8, 520, (0, 34, 0), 6.0),
          ("横", "idle", 270, 4, 520, (0, 34, 0), 6.0),
          ("後ろ 3/4", "idle", 32, 10, 520, (0, 34, 0), 6.0)],
    options={"a": dict(name="雲の瞼の一つ目", desc="雲の白目、金の虹彩、藍の瞳、雲の瞼、金の睫毛と輪。")},
    extra="雲海の言葉 (雲の白・淡い金・朝の光)。骨 6 本。光るのは虹彩と輝き。",
    budget="主 1500〜3000", pw=440,
    later="二段目 = 白目に金の血の筋。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
