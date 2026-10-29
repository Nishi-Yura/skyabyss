# -*- coding: utf-8 -*-
"""最後の鐘守 (last_bellkeeper) 低ポリ 一品一案。鐘の島の主 (docs/design/50 の 4.2.1)。
前の都で最後まで鐘を鳴らし続けた人。身長 4.2 ブロック。まっすぐ立つ。
暗い外套に金の縁、漆喰の面 (目の切れ目から朝の光)、背に大きな青銅の鐘 (金の軛で肩から吊る。緑青の帯)、右手に鐘の舌の槌 (長い柄、先が青銅の玉)。
技: 舌の槌の振り下ろし (床の輪の予告)・鐘の波 (背の鐘を鳴らして輪の波)・無音 (左手を面にかざして鐘雲をほどく)。
骨: root > body > head / arm_l / arm_r > staff / bell / leg_l / leg_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 30, 0)), "head": ("body", (0, 55, -0.5)),
         "arm_l": ("body", (-7.2, 50, 0)), "arm_r": ("body", (7.2, 50, 0)), "staff": ("arm_r", (8.4, 33, -5.0)),
         "bell": ("body", (0, 60, 9.0)), "leg_l": ("body", (-2.8, 26, 0)), "leg_r": ("body", (2.8, 26, 0))}
PAL = make_pal({
    "robe": (ROBE, 0.5, dict(crack=0.04, noise=0.18)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "plaster": (PLASTER, 0.6, dict(crack=0.08, noise=0.16)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "bronze": (BRONZE, 0.66, dict(crack=0.08, noise=0.18)),
    "verdi": (VERDI, 0.55, dict(crack=0.06, noise=0.22)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def hoop(bone, mat, y, rx, rz, cx=0.0, zc=0.0, r=0.5, n=14):
    """水平の輪 (縁取り)"""
    pts = [(cx + math.cos(a) * rx, y, zc + math.sin(a) * rz) for a in [i / n * 2 * math.pi for i in range(n + 1)]]
    return tube(bone, mat, pts, ra=r, sides=4, hint=(0, 1, 0))


def build(k):
    F = []
    # 外套 (裾が広く、胸は張る)
    rings = [ring(1.0, 7.6, -7.0, 6.6, n=12), ring(10.0, 6.8, -6.2, 5.8, n=12), ring(24.0, 5.6, -5.0, 4.8, n=12),
             ring(36.0, 6.2, -5.0, 4.8, n=12), ring(47.0, 7.4, -5.2, 5.0, n=12), ring(52.0, 6.6, -4.4, 4.4, n=12), ring(54.6, 3.0, -2.6, 2.4, n=12)]
    F += part("robe", loft("body", "robe", rings, caps=("hollow", "robe")))
    # 金の縁: 裾・腰・襟と、正面の縦の合わせ
    F += part("hem", hoop("body", "gold", 1.6, 7.7, 6.9, zc=-0.2, r=0.6))
    F += part("waist", hoop("body", "gold", 28.0, 5.8, 4.95, r=0.55))
    F += part("collar", hoop("body", "gold", 52.4, 6.6, 4.5, r=0.6))
    F += part("seam", tube("body", "gold", [(0, 2.0, -7.1), (0, 10.0, -6.3), (0, 24.0, -5.1), (0, 36.0, -5.1), (0, 47.0, -5.3)], ra=0.6, rb=0.5, sides=4, hint=(1, 0, 0)))
    # 足先 (裾から少し出る)
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"foot{s}", ellipsoid(b, "robe", (s * 3.0, 1.0, -6.6), (1.6, 1.0, 2.4), segs=6, rings=2))
    # 肩当て (金の縁の丸い肩)
    for s in (-1, 1):
        F += part(f"pad{s}", ellipsoid("body", "robe", (s * 6.8, 50.4, 0), (3.0, 2.2, 3.6), segs=8, rings=3))
        F += part(f"padrim{s}", hoop("body", "gold", 49.0, 2.9, 3.4, cx=s * 6.8, r=0.4, n=10))
    # 頭巾と漆喰の面
    hood = [ring(53.0, 3.6, -3.6, 3.4, n=12), ring(58.0, 3.8, -3.8, 3.6, n=12, zc=-0.4), ring(62.4, 3.0, -2.8, 3.2, n=12, zc=-0.2),
            ring(65.6, 1.2, -0.8, 1.6, n=12, zc=0.4)]
    F += part("hood", loft("head", "robe", hood, caps=("hollow", "robe")))
    # 頭巾の口の金の縁 (面のまわりの上半分のアーチ)
    F += part("hoodrim", tube("head", "gold", [(math.cos(a) * 3.0, 58.0 + 3.2 * math.sin(a), -3.75) for a in [i / 10 * math.pi for i in range(11)]],
                              ra=0.4, sides=4, hint=(0, 0, 1)))
    F += part("mask", ellipsoid("head", "plaster", (0, 58.2, -3.5), (2.5, 3.0, 1.2), segs=10, rings=4))
    for s in (-1, 1):
        F += part(f"slit{s}", ellipsoid("head", "hollow", (s * 1.05, 59.0, -4.55), (0.75, 0.32, 0.3), segs=6, rings=2))
        F += part(f"eye{s}", ellipsoid("head", "dawn", (s * 1.05, 59.0, -4.72), (0.45, 0.2, 0.2), segs=5, rings=2))
    # 面の縦の割れ (額から頬へ)
    F += part("maskcrack", tube("head", "hollow", [(0.6, 61.0, -4.4), (0.2, 59.8, -4.62), (0.7, 57.6, -4.62), (0.3, 56.0, -4.3)], ra=0.16, sides=4, hint=(0, 0, 1)))
    # 金の軛 (両肩から背の鐘の頭へ)
    for s in (-1, 1):
        F += part(f"yoke{s}", tube("bell", "gold", [(s * 6.0, 52.6, 1.5), (s * 3.6, 60.8, 5.6), (0, 63.4, 9.0)], ra=0.8, sides=5, hint=(0, 0, 1)))
    # 背の大きな鐘 (口が下。緑青の帯と金の口縁)
    BZ = 9.0
    bell = [ring(y, r, BZ - r, BZ + r, n=16) for y, r in ((35.0, 9.4), (36.6, 8.7), (40.0, 7.3), (45.0, 6.2), (51.0, 5.6), (56.4, 5.2), (60.0, 3.9), (62.0, 1.8))]
    F += part("bellbody", loft("bell", "bronze", bell, caps=("hollow", "bronze")))
    F += part("bellcrown", ellipsoid("bell", "bronze", (0, 63.0, BZ), (1.8, 1.3, 1.8), segs=6, rings=2))
    F += part("bellverd", hoop("bell", "verdi", 52.0, 5.62, 5.62, zc=BZ, r=0.6, n=16))
    F += part("bellverd2", hoop("bell", "verdi", 54.0, 5.42, 5.42, zc=BZ, r=0.35, n=16))
    F += part("belllip", hoop("bell", "gold", 35.6, 9.15, 9.15, zc=BZ, r=0.7, n=16))
    # 背の鐘の割れ目 (後ろの面に縦の筋)
    F += part("bellcrack", tube("bell", "hollow", [(0.6, 55.0, BZ + 5.35), (-0.4, 50.0, BZ + 5.75), (0.5, 44.0, BZ + 6.6), (-0.2, 38.0, BZ + 8.2), (0.3, 35.4, BZ + 9.3)], ra=0.3, sides=4, hint=(0, 0, 1)))
    # 腕 (左は開いた手、右は槌を握る)
    F += part("upl", tube("arm_l", "robe", [(-7.6, 50, 0), (-9.0, 41.0, -0.8)], ra=[2.0, 1.6], sides=6, hint=(1, 0, 0)))
    F += part("fol", tube("arm_l", "robe", [(-9.0, 41.0, -0.8), (-9.2, 33.0, -3.4)], ra=[1.6, 1.9], sides=6, hint=(1, 0, 0)))
    F += part("cuffl", hoop("arm_l", "gold", 33.6, 1.95, 1.95, cx=-9.2, zc=-3.3, r=0.35, n=10))
    F += part("handl", ellipsoid("arm_l", "plaster", (-9.2, 31.4, -3.8), (1.7, 2.2, 1.6), segs=6, rings=2))
    F += part("upr", tube("arm_r", "robe", [(7.6, 50, 0), (9.0, 41.0, -1.4)], ra=[2.0, 1.6], sides=6, hint=(1, 0, 0)))
    F += part("for", tube("arm_r", "robe", [(9.0, 41.0, -1.4), (8.6, 34.6, -4.6)], ra=[1.6, 1.9], sides=6, hint=(1, 0, 0)))
    F += part("cuffr", hoop("arm_r", "gold", 35.0, 1.95, 1.95, cx=8.6, zc=-4.6, r=0.35, n=10))
    F += part("handr", ellipsoid("arm_r", "plaster", (8.4, 33.0, -5.0), (1.9, 2.1, 1.9), segs=6, rings=2))
    # 鐘の舌の槌: 長い柄 (下は金の石突き、上は青銅の玉と緑青の帯)
    F += part("shaft", tube("staff", "robe", [(8.4, 8.0, -5.0), (8.4, 52.0, -5.0)], ra=0.75, sides=6, hint=(1, 0, 0)))
    F += part("ferrule", tube("staff", "gold", [(8.4, 6.6, -5.0), (8.4, 8.6, -5.0)], ra=[0.5, 0.95], sides=6, hint=(1, 0, 0)))
    F += part("neck", tube("staff", "gold", [(8.4, 51.0, -5.0), (8.4, 54.0, -5.0)], ra=[0.95, 1.2], sides=6, hint=(1, 0, 0)))
    F += part("clapper", ellipsoid("staff", "bronze", (8.4, 57.0, -5.0), (2.7, 3.2, 2.7), segs=10, rings=4))
    F += part("clapband", hoop("staff", "verdi", 57.0, 2.72, 2.72, cx=8.4, zc=-5.0, r=0.45))
    return F, []


POSES = {
    "idle": {},
    "slam": {"arm_r": (-150, 0, 10), "staff": (170, 0, 0), "body": (-8, 0, 0), "head": (5, 0, 0)},
    "wave": {"arm_l": (-20, 0, -60), "arm_r": (-20, 0, 50), "bell": (0, 0, 8), "body": (10, 0, 0)},
    "silence": {"arm_l": (-95, 20, 25), "head": (12, 0, 0)},
}

SPEC = dict(
    id="last_bellkeeper", short="最後の鐘守", kind="鐘の島の主 一品一案 (50 の 4.2.1)",
    title="最後の鐘守 (last_bellkeeper) — 背に大鐘、手に舌の槌・低ポリ + ドット絵 一案",
    sub=["前の都で最後まで鐘を鳴らした人。舌の槌の振り下ろし・鐘の波・無音 (鐘雲をほどく)。二段目で亡者を呼ぶ。",
         "暗い外套に金の縁、漆喰の面に朝の光の切れ目、背に緑青の帯の大鐘。身長 4.2 ブロック。"],
    labels=["最後の鐘守"],
    rows=[("前 3/4", "idle", 212, 8, 480, (0, 34, 0), 6.2),
          ("振り下ろし", "slam", 200, 6, 480, (0, 38, 0), 5.8),
          ("無音", "silence", 220, 6, 480, (0, 34, 0), 6.2),
          ("後ろ 3/4", "idle", 32, 10, 480, (0, 34, 0), 6.2)],
    options={"a": dict(name="大鐘を背負う鐘守", desc="暗い外套に金の縁、漆喰の面、背に青銅の大鐘 (緑青の帯・金の口縁)、右手に舌の槌。")},
    extra="雲海の言葉 (鐘の青銅・金・朝の光)。骨 9 本。光るのは面の切れ目だけ。",
    budget="主 1500〜3000", pw=440,
    later="二段目 = 背の鐘の割れ目から朝の光。面の割れが広がる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
