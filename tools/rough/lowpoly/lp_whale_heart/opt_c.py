"""C 案「眼球」: 利用者の元の参考画像 (青白い肉の浮遊する眼球、赤い棘、細かい虹彩) に寄せた案。
殻はほぼ正確な面取りの球 (白っぽい石、赤い毛細血管を描く)、板ごとに赤い棘が生え、正面の継ぎ目はレンズ形の光る瞳孔、
そのまわりに虹彩を描く。段階 2 で虹彩が光り (棘の冠が立つ)、段階 3 の心臓は血管の走る眼球で、核が虹彩になる。"""
from __future__ import annotations

from common import *  # noqa
from parts import *  # noqa

NAME = "C 眼球"
COLS = (-20.0, -10.0, 0.0, 10.0, 20.0)
ROWS = (-9.0, -6.2, -3.1, 0.0, 3.1, 6.2, 9.0)
STONE_C = np.array([(104, 92, 94), (150, 136, 130), (190, 176, 164), (222, 210, 194), (242, 234, 218)])
SPIKE = np.array([(52, 6, 14), (104, 14, 24), (156, 28, 34), (198, 52, 48), (236, 100, 84)])
CAP = np.array([(110, 22, 32), (158, 40, 48), (198, 66, 62)])
IRIS_DIM = np.array([(34, 22, 24), (74, 44, 34), (116, 70, 40), (156, 104, 56), (196, 146, 76)])
IRIS_GLOW = np.array([(66, 12, 12), (126, 28, 14), (196, 58, 20), (246, 106, 34), (255, 166, 66), (255, 222, 132)])


def plate(bone, k, rad=R0, dmg=0, rows=ROWS, ragged=False):
    O = []
    nr, nc = len(rows), len(COLS)
    for i, y in enumerate(rows):
        row = []
        for j, a in enumerate(COLS):
            r = rad + (0.35 if 0 < j < nc - 1 else 0.0)
            yy = y
            if ragged and i == nr - 1:
                yy = y + jit(k, j, 8, amp=1.8)
            row.append(spa(a, yy, r))
        O.append(row)
    for n in range(dmg):
        i = 1 + int(abs(jit(k, n, 31)) * 10) % (nr - 2)
        j = 1 + int(abs(jit(k, n, 32)) * 10) % (nc - 2)
        O[i][j] = pull_in(O[i][j], 1.3 + abs(jit(k, n, 33)) * 1.0)
    holes = set()
    for n in range(max(dmg - 2, 0)):
        holes.add((int(abs(jit(k, n, 41)) * 10) % (nr - 1), int(abs(jit(k, n, 42)) * 10) % (nc - 1)))
    F = surface_grid(bone, "stone", O, holes)
    F += backing(bone, O, 2.6)
    return F


def plate_spikes(bone, k, ragged=False):
    F = []
    specs = [(0.0, 5.6, 1.5, 2), (-6.0, 3.4, 1.0, 1), (6.0, 3.4, 1.0, 1)]
    if ragged:
        specs = [(-6.4, 3.8, 1.1, 1)]
    for y, L, r, rg in specs:
        a = jit(k, int(y), 3, amp=3.0)
        base = spa(a, y, R0 - 0.5)
        d = nrm(base - CEN + V(0, 0.18 * (1 if y >= 0 else -0.4), 0))
        F += spike(bone, "spike", base, d, L, r, sides=4, rings=rg, phase=45)
    return F


def seam(bone, k, mat="seam", y0=-9.0, y1=9.0, lens=False, jag=0.0):
    ys = list(np.linspace(y0, y1, 7))
    O = []
    for i, y in enumerate(ys):
        s = math.sin(math.pi * (i + 0.5) / 7.0)
        w = (0.30 + 1.65 * s) if lens else (0.28 + 0.62 * s)
        cx = jag * jit(k, i, 5, amp=0.7)
        O.append([spx(cx - w, y, R0 - 0.9), spx(cx, y, R0 - 0.1), spx(cx + w, y, R0 - 0.9)])
    return surface_grid(bone, mat, O)


def crown():
    R = [ring(y, r, -r, r) for y, r in [(21.0, 7.4), (22.0, 7.7), (23.3, 6.8), (24.5, 4.8), (25.4, 2.2)]]
    F = loft("crown", "stone", R, caps=(False, "stone"))
    for i in range(4):
        a = 22.5 + 90 * i
        t = math.radians(a)
        base = V(5.6 * math.sin(t), 22.8, -5.6 * math.cos(t))
        d = V(0.55 * math.sin(t), 1.0, -0.55 * math.cos(t))
        F += spike("crown", "spike", base, d, 3.6, 1.2, sides=4, rings=1, phase=45)
    return F


def spikes():
    F = []
    for i in range(8):
        a = 45 * i
        t = math.radians(a)
        L = [10.0, 7.0][i % 2] + jit(i, 5, amp=0.7)
        base = V(3.8 * math.sin(t), 23.4, -3.8 * math.cos(t))
        d = V(0.38 * math.sin(t), 1.0, -0.38 * math.cos(t))
        F += spike("spikes", "spike", base, d, L, 1.5, sides=4, rings=2, bend=(1.6 * math.sin(t), 0, -1.6 * math.cos(t)))
    return F


def eye_veins(bone, c, radii, n, seed, lift=0.55, rad=0.55, d0=26.0, d1=80.0):
    F = []
    for i in range(n):
        th = math.radians(360.0 / n * i + 14 + jit(seed, i, amp=12))
        pts = []
        for d in (d0, d0 + (d1 - d0) * 0.35, d0 + (d1 - d0) * 0.68, d1):
            w = jit(seed, i, int(d), amp=6)
            pts.append((d * math.sin(th) * 0.9 + w * 0.3, d * math.cos(th) + w))
        F += veins(bone, c, radii, None, [pts], lift=lift, rad=rad)
    return F


HEART = {1: ((0, 12.5, 0), (5.0, 5.2, 4.0), 8, 3),
         2: ((0, 12.7, -0.5), (6.6, 6.6, 5.2), 12, 5),
         3: ((0, 13.8, -0.5), (7.8, 8.0, 5.8), 12, 5)}


def build(phase, pres):
    F = []
    for k in range(8):
        b = f"plate_{k}"
        if b in pres:
            F += plate(b, k, dmg=(0 if phase == 1 else 4))
            F += plate_spikes(b, k)
        b = f"seam_{k}"
        if b in pres:
            F += seam(b, k, lens=(k == 2))
        b = f"crack_{k}"
        if b in pres:
            F += seam(b, k, "crack", lens=(k == 2), jag=1.0)
        b = f"bowl_{k}"
        if b in pres:
            F += plate(b, k, R0 + 0.4, dmg=2, rows=(-9.0, -7.2, -5.4, -3.8), ragged=True)
            F += plate_spikes(b, k, ragged=True)
        b = f"bowl_seam_{k}"
        if b in pres:
            F += seam(b, k, y0=-9.0, y1=-3.6)
    if "crown" in pres:
        F += crown()
    if "spikes" in pres:
        F += spikes()
    if "base" in pres:
        F += base_ring()
    if "tendrils" in pres:
        F += tendrils(6, 2, thick=1.25)
    c, r, s, rg = HEART[phase]
    if "heart" in pres:
        F += ellipsoid("heart", "flesh", c, r, segs=s, rings=rg, phase=0)
        if phase >= 2:
            # 視神経: 後ろ下へ垂れる太い筋
            for i, ang in enumerate((-35, 30)):
                st = V(0, c[1] - 5.0, 3.0)
                F += tube("heart", "flesh", [st, st + V(math.sin(math.radians(ang)) * 2.0, -2.2, 3.0),
                                             st + V(math.sin(math.radians(ang)) * 4.5, -5.0, 3.2)], [1.9, 1.5, 0.5],
                          sides=4, hint=(0, 0, 1), phase=45, axis_fn=lambda p: V(0, p[1], 0))
    if "heart_veins" in pres:
        F += eye_veins("heart_veins", c, r, 6 if phase == 2 else 8, phase * 3 + 1, d0=(30 if phase == 2 else 26),
                       d1=82)
    if "core" in pres:
        F += disc("core", "core", (0.0, 13.8, -6.0), [(3.9, 0.0), (3.3, 0.45), (2.2, 0.8), (1.3, 0.5), (0.7, 0.2)], n=12, phase=15)
    if "chains" in pres:
        F += bone_frame() + side_chains()
    if "shard_0" in pres:
        F += claw("shard_0", "shard")
    if "shard_1" in pres:
        F += claw("shard_1", "shard")
    return F


def ctx(phase):
    c = HEART[phase][0]
    irises = [dict(c=(0.0, 12.0), R=5.8, fibres=26, fibamp=0.40, base=0.14, pal=(IRIS_DIM if phase == 1 else IRIS_GLOW), glow=(phase >= 2))]
    if phase == 2:
        irises.append(dict(c=(0.0, c[1]), R=3.4, fibres=16, pal=IRIS_GLOW, glow=True, mats=["flesh"], zmax=-2.0))
    return dict(stone=STONE_C, crack_p=[0.1, 0.3, 0.3], crack_glow=[False, True, True], moss_thr=1.2, moss_on=False,
                seam_w=0.9, seam_lens=["seam_2", "crack_2"], seam_w_lens=2.2, seam_peak=0.86,
                veins_pale=np.array([(110, 22, 34), (150, 36, 46), (190, 58, 60)]),
                irises=irises, spike_pal=SPIKE, bone=BONE, core_c=(0.0, 13.8, -6.3), core_r=3.7, core_iris=True,
                flesh=np.array([(70, 12, 24), (116, 22, 34), (160, 38, 48), (200, 66, 66), (230, 110, 98), (244, 160, 138)]),
                fl_band=False, vein_pal=np.array([(60, 20, 70), (90, 40, 110), (130, 76, 150)]), ember_thr=0.6, rim_dark=3,
                inner_pal=np.array([(40, 6, 16), (78, 12, 26), (122, 22, 34), (150, 34, 44)]))


OPT = dict(id="c", name=NAME, build=build, ctx=ctx, gcol=(255, 160, 80))
