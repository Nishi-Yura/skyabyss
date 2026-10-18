"""B 案「割れ岩」: 殻は砕けかけた大きな岩の板。縁を斜めに落とし (面取り)、形も輪郭もいびつで、板のあいだに太い溶岩の溝が走る。
冠は荒い岩の帽子と立った岩の欠片、棘は黒い結晶、心臓は赤い宝石の塊 (面ごとに平らに光る) とそこから突き出る結晶。
色は焦げた鉄色の岩 + 錆 + 溶岩。"""
from __future__ import annotations

from common import *  # noqa
from parts import *  # noqa

NAME = "B 割れ岩"
COLS = (-17.5, -14.0, 0.0, 14.0, 17.5)
ROWS = (-9.0, -7.8, -4.0, 0.6, 4.8, 8.0, 9.0)
STONE_B = np.array([(22, 20, 24), (42, 38, 42), (66, 60, 62), (94, 86, 82), (126, 116, 106)])
RUST = np.array([(60, 34, 26), (96, 52, 32), (134, 76, 40), (170, 104, 52)])
GEM = np.array([(48, 4, 18), (88, 8, 30), (134, 18, 44), (184, 40, 62), (226, 84, 92), (250, 150, 150)])
OBS = np.array([(14, 12, 20), (30, 24, 38), (54, 42, 62), (84, 66, 90), (126, 104, 134)])


ROWS_R = (-9.0, -7.8, -6.2, -4.6)


def plate_rows(k, rad, rows=ROWS, ragged=False):
    O = []
    nr, nc = len(rows), len(COLS)
    for i, y in enumerate(rows):
        row = []
        for j, a in enumerate(COLS):
            bevel = i in (0, nr - 1) or j in (0, nc - 1)
            r = rad - (1.7 if bevel else 0.0) + jit(k, i, j, amp=(0.5 if bevel else 1.0))
            aa = a + (jit(k, i, j, 2, amp=2.0) if j in (0, nc - 1) else jit(k, i, j, 2, amp=1.5) * (0.6 if 0 < i < nr - 1 else 0))
            yy = y + (jit(k, i, j, 4, amp=0.8) if (0 < i < nr - 1) else 0)
            if ragged and i == nr - 1:
                yy = y + jit(k, j, 8, amp=2.2)
                r -= 0.8
            row.append(spa(aa, yy, r))
        O.append(row)
    return O


def plate(bone, k, rad=R0, dmg=0, ragged=False):
    rows = ROWS_R if ragged else ROWS
    O = plate_rows(k, rad, rows, ragged)
    nr, nc = len(O), len(O[0])
    for n in range(dmg):
        i = 1 + int(abs(jit(k, n, 31)) * 10) % (nr - 2)
        j = 1 + int(abs(jit(k, n, 32)) * 10) % (nc - 2)
        O[i][j] = pull_in(O[i][j], 1.8 + abs(jit(k, n, 33)) * 1.6)
    holes = set()
    for n in range(max(dmg - 1, 0)):
        holes.add((int(abs(jit(k, n, 41)) * 10) % (nr - 1), int(abs(jit(k, n, 42)) * 10) % (nc - 1)))
    F = surface_grid(bone, "stone", O, holes)
    F += backing(bone, O, 3.2)
    return F


def seam(bone, k, mat="seam", y0=-9.0, y1=9.0, wide=2.0):
    ys = list(np.linspace(y0, y1, 7))
    O = []
    for i, y in enumerate(ys):
        w = wide * (0.45 + 0.55 * math.sin(math.pi * (i + 0.5) / 7.0)) + jit(k, i, 5, amp=0.25)
        cx = jit(k, i, 6, amp=0.5)
        O.append([spx(cx - w, y, R0 - 2.8), spx(cx, y, R0 - 1.6 + jit(k, i, 7, amp=0.3)), spx(cx + w, y, R0 - 2.8)])
    return surface_grid(bone, mat, O)


def crown():
    F = []
    R = []
    for y, r in [(21.0, 7.4), (22.4, 7.9), (23.6, 6.6), (24.7, 4.6)]:
        pts = ring(y, r, -r, r)
        R.append([p + V(jit(int(y * 10), n, 1, amp=0.7) * 0.9, jit(int(y * 10), n, 2, amp=0.35), jit(int(y * 10), n, 3, amp=0.7) * 0.9)
                  if y > 21.5 else p for n, p in enumerate(pts)])
    F += loft("crown", "stone", R, caps=(False, "stone"))
    for i, (a, L, r) in enumerate([(20, 5.0, 1.9), (110, 3.4, 1.5), (200, 5.8, 2.0), (290, 3.8, 1.6)]):
        t = math.radians(a)
        base = V(4.4 * math.sin(t), 23.8, -4.4 * math.cos(t))
        d = V(0.35 * math.sin(t), 1.0, -0.35 * math.cos(t))
        F += spike("crown", "stone", base, d, L, r, sides=4, rings=1, phase=30 + 20 * i)
    return F


def spikes():
    F = []
    for i in range(6):
        a = 60 * i + 15
        t = math.radians(a)
        L = [9.0, 6.2, 8.0, 5.6, 9.4, 6.6][i]
        base = V(4.6 * math.sin(t), 24.4, -4.6 * math.cos(t))
        d = V(0.55 * math.sin(t), 1.0, -0.55 * math.cos(t))
        F += spike("spikes", "spike", base, d, L, 2.0, sides=3, rings=2, phase=20 + 30 * i,
                   bend=(1.3 * math.sin(t), 0, -1.3 * math.cos(t)))
    return F


def gem_deform(seed, amp):
    def f(p):
        n = np.linalg.norm(p)
        d = p / max(n, 1e-6)
        h = jit(seed, int(round(d[0] * 5)), int(round(d[1] * 5)), int(round(d[2] * 5)), amp=amp)
        return p * (1 + h)
    return f


def heart_gem(bone, c, radii, segs, rings, seed, nprongs, mat="gem"):
    c = np.asarray(c, float)
    F = ellipsoid(bone, mat, c, radii, segs=segs, rings=rings, deform=gem_deform(seed, 0.24), phase=10)
    for i in range(nprongs):
        lat = [55, 20, -25, 40, -5, 62, -40][i % 7]
        phi = [-35, 50, -70, 120, -150, 160, 25][i % 7] + 12 * (i // 7)
        base = ell_pt(c, (radii[0] * 0.9, radii[1] * 0.9, radii[2] * 0.9), lat, phi)
        d = nrm(base - c)
        F += spike(bone, mat, base, d, 3.6 + abs(jit(seed, i, 5, amp=3.4)), 1.7, sides=3, rings=1, phase=40 * i)
    return F


HEART = {1: ((0, 12.5, 0), (5.2, 5.6, 3.6), 6, 2, 0, 0),
         2: ((0, 12.7, -0.5), (6.2, 6.4, 4.6), 8, 4, 7, 8),
         3: ((0, 13.8, -0.5), (7.4, 7.6, 5.4), 8, 4, 9, 11)}


def build(phase, pres):
    F = []
    for k in range(8):
        b = f"plate_{k}"
        if b in pres:
            F += plate(b, k, dmg=(0 if phase == 1 else 4))
        b = f"seam_{k}"
        if b in pres:
            F += seam(b, k)
        b = f"crack_{k}"
        if b in pres:
            F += seam(b, k, "crack", wide=1.6)
        b = f"bowl_{k}"
        if b in pres:
            F += plate(b, k, R0 + 0.4, dmg=2, ragged=True)
        b = f"bowl_seam_{k}"
        if b in pres:
            F += seam(b, k, y0=-9.0, y1=-3.0)
    if "crown" in pres:
        F += crown()
    if "spikes" in pres:
        F += spikes()
    if "base" in pres:
        F += base_ring()
    if "tendrils" in pres:
        F += tendrils(7, 4, thick=1.35)
    c, r, s, rg, seed, npr = HEART[phase]
    if "heart" in pres:
        F += heart_gem("heart", c, r, s, rg, seed, npr)
    if "heart_veins" in pres:
        sp = [[(60, 5), (35, -18), (5, -26), (-30, -20), (-52, -6)], [(62, 25), (38, 12), (12, 8), (-18, 2), (-50, 0)],
              [(55, -40), (28, -50), (-5, -52)], [(50, 52), (22, 48), (-8, 40)]]
        F += veins("heart_veins", c, r, gem_deform(seed, 0.24), sp[: (3 if phase == 2 else 4)], lift=0.9, rad=0.7)
    if "core" in pres:
        F += ellipsoid("core", "core", (-1.75, 13.5, -4.8), (2.7, 3.0, 2.2), segs=5, rings=1, phase=10)
    if "chains" in pres:
        F += bone_frame() + side_chains()
    if "shard_0" in pres:
        F += claw("shard_0", "shard")
    if "shard_1" in pres:
        F += claw("shard_1", "shard")
    return F


def ctx(phase):
    return dict(stone=STONE_B, crack_p=[0.16, 0.42, 0.42], crack_glow=[True, True, True], moss_thr=1.05, moss_on=False,
                rust=RUST, rust_thr=0.80, seam_w=1.9, seam_peak=0.72, bone=BONE, spike_pal=OBS, gem=GEM, vein_pal=OBS,
                flesh=GEM, core_c=(-1.75, 13.5, -4.8), core_r=3.0, ember_thr=0.6, fl_band=False,
                inner_pal=np.array([(12, 10, 14), (26, 22, 26), (44, 38, 42)]))


OPT = dict(id="b", name=NAME, build=build, ctx=ctx, gcol=(255, 120, 50))
