# -*- coding: utf-8 -*-
"""巡礼者の月 (pilgrim_moon) 低ポリ 一品一案。ラスボス「雲上の巡礼者」の第 3 形態 (docs/design/50 の 4.3)。
巡礼者が引きずってきた止まった月 (直径 4 ブロック)。青白い月の石にくぼみ、ひびから朝の光。
金の鎖が四方から月を縛り、上へ伸びて途切れる (雲の上の見えない巡礼者が引いている)。前に時計の輪 (逆に回る針)。
骨: root > moon / chains / clock。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

C = (0, 36, 0)
BONES = {"root": (None, (0, 0, 0)), "moon": ("root", C), "chains": ("root", C), "clock": ("root", (0, 36, -20))}
PAL = make_pal({
    "dust": (DUST, 0.6, dict(crack=0.1, noise=0.2)),
    "dust2": (DUST, 0.45, dict(crack=0.12, noise=0.22)),
    "basalt": (BASALT, 0.55, dict(crack=0.06, noise=0.16)),
    "gold": (GOLDW, 0.7, dict(crack=0.02, noise=0.1)),
    "dawn": (DAWN, 0.88, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def build(k):
    F = []
    R = 16.0
    F += part("moon", ellipsoid("moon", "dust", C, (R, R, R), segs=18, rings=10))
    # くぼみ (暗い浅い皿) とひびの光
    craters = [(-5, 42, 0.55), (6, 30, 0.45), (-8, 28, 0.35), (4, 45, 0.3), (9, 39, 0.3), (-2, 33, 0.25)]
    for i, (x, y, s) in enumerate(craters):
        dz = -math.sqrt(max(1.0, R * R - x * x - (y - C[1]) ** 2))
        F += part(f"cr{i}", ellipsoid("moon", "dust2" if i % 2 else "basalt", (x, y, dz + 0.6), (9 * s, 9 * s, 1.4), segs=10, rings=3))
    for i, (pts) in enumerate((((-12, 30, -9), (-4, 34, -14.6), (3, 31, -15.2)), ((5, 46, -9), (10, 41, -10.5), (14, 36, -6.5)))):
        F += part(f"crack{i}", tube("moon", "dawn", list(pts), ra=0.5, rb=0.7, sides=3, hint=(0, 0, 1)))
    # 金の鎖 (四方から月を縛り、上へ伸びる。輪を連ねた形)
    for j, a in enumerate((40, 140, 220, 320)):
        ang = math.radians(a)
        bx, bz = math.cos(ang), math.sin(ang)
        pts = [(bx * (R + 0.4) * math.cos(math.radians(t)), 36 + (R + 0.4) * math.sin(math.radians(t)), bz * (R + 0.4) * math.cos(math.radians(t)))
               for t in range(-60, 91, 30)]
        pts += [(bx * 4.0, 36 + R + 8, bz * 4.0), (bx * 7.0, 36 + R + 22, bz * 7.0)]
        for i in range(len(pts) - 1):
            F += part(f"ch{j}{i}", tube("chains", "gold", [pts[i], pts[i + 1]], ra=0.9, rb=0.6, sides=4, hint=(0, 1, 0) if i % 2 else (1, 0, 0)))
            F += part(f"lk{j}{i}", ellipsoid("chains", "gold", pts[i], (1.3, 1.3, 1.3), segs=6, rings=2))
        F += part(f"end{j}", ellipsoid("chains", "dawn", pts[-1], (1.4, 1.4, 1.4), segs=6, rings=2))
    # 前の時計の輪 (金の輪に十二の刻み、逆に回る二本の針)
    CR = 9.0
    rp = [(math.cos(math.radians(a)) * CR, 36 + math.sin(math.radians(a)) * CR, -20) for a in range(0, 361, 15)]
    F += part("cring", tube("clock", "gold", rp, ra=0.6, rb=0.6, sides=4, hint=(0, 0, 1)))
    for i in range(12):
        a = math.radians(i * 30)
        F += part(f"tick{i}", tube("clock", "dawn" if i % 3 == 0 else "gold", [(math.cos(a) * (CR - 1.6), 36 + math.sin(a) * (CR - 1.6), -20),
                                                                                (math.cos(a) * (CR - 0.4), 36 + math.sin(a) * (CR - 0.4), -20)], ra=0.35, rb=0.45, sides=3, hint=(0, 0, 1)))
    F += part("hand1", tube("clock", "gold", [(0, 36, -20.3), (0, 36 + CR - 2.4, -20.3)], ra=0.3, rb=0.6, sides=3, hint=(0, 0, 1)))
    F += part("hand2", tube("clock", "gold", [(0, 36, -20.5), (CR - 3.6, 36 - 1.5, -20.5)], ra=0.3, rb=0.5, sides=3, hint=(0, 0, 1)))
    F += part("hub", ellipsoid("clock", "dawn", (0, 36, -20.6), (1.1, 1.1, 0.5), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {},
    "pull": {"clock": (0, 0, 120), "moon": (0, 40, 0)},
}

SPEC = dict(
    id="pilgrim_moon", short="巡礼者の月", kind="ラスボス 第 3 形態 一品一案 (50 の 4.3)",
    title="巡礼者の月 (pilgrim_moon) — 金の鎖に縛られて引き寄せられた月・低ポリ + ドット絵 一案",
    sub=["月を引き寄せ時が乱れる: 打ちは同じ所でもう一度 (巻き戻り)、月の引き、満ち欠けの刃、月落とし。前の自分たち四人が加勢する。",
         "青白い月の石にくぼみとひびの光。四方から金の鎖が縛り、上へ伸びて途切れる。前に逆に回る時計の輪。直径 4 ブロック。"],
    labels=["巡礼者の月"],
    rows=[("前", "idle", 180, 4, 520, (0, 40, 0), 6.0),
          ("前 3/4", "idle", 212, 8, 520, (0, 40, 0), 6.0),
          ("横", "idle", 270, 4, 520, (0, 40, 0), 6.0),
          ("引き", "pull", 200, 6, 520, (0, 40, 0), 6.0)],
    options={"a": dict(name="鎖の月", desc="くぼみとひびの光の月、四方の金の鎖、前に逆に回る時計の輪。")},
    extra="月 (46) と雲海 (金) の言葉を合わせる。骨 4 本。光るのはひび・鎖の先・時計の刻み。",
    budget="主 1500〜3000", pw=440,
    later="二段目 = ひびが広がる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
