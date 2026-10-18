"""A 案「地層の板」: 殻は 4 段の斜めの板 (庇が出る) を積んだ八枚の楔。段の境に陰ができ、面取りした八角の冠と台座、
骨のように白い棘。心臓は解剖図のような低ポリの心臓 (心尖が細く、溝と心房と大動脈がある)。青灰の石 + 苔。"""
from __future__ import annotations

from common import *  # noqa
from parts import *  # noqa

NAME = "A 地層の板"
TIER_Y = [-9.0, -4.6, -0.2, 4.2, 9.0]
COLS = (-20.0, -7.0, 7.0, 20.0)
STONE_A = np.array([(30, 34, 44), (52, 58, 72), (80, 88, 102), (110, 118, 130), (142, 150, 160)])


def plate_rows(k, rad, tiers=(0, 1, 2, 3), ragged=False):
    rows = []
    for t in tiers:
        rlo = rad - 0.9 + jit(k, t, 1, amp=0.3)
        rhi = rlo + 1.1
        for kind, (y, r) in (("lo", (TIER_Y[t], rlo)), ("hi", (TIER_Y[t + 1], rhi))):
            row = []
            for j, a in enumerate(COLS):
                edge = j in (0, len(COLS) - 1)
                rr = r + (0.5 if not edge else 0.0) + jit(k, t, j, 7, amp=0.2)
                yy = y
                if ragged and kind == "hi" and t == tiers[-1]:
                    yy = y - 1.2 - 2.4 * (jit(k, j, 9, amp=1.0) * 0.5 + 0.5) - (0.0 if edge else 0.6)
                    rr -= 0.3
                aa = a + (0 if edge else jit(k, t, j, 3, amp=1.2))
                row.append(spa(aa, yy, rr))
            rows.append(row)
    return rows


def plate(bone, k, rad=R0, tiers=(0, 1, 2, 3), ragged=False, dmg=0):
    O = plate_rows(k, rad, tiers, ragged)
    if dmg:
        for n in range(dmg):
            i = int(abs(jit(k, n, 31)) * (len(O) - 1)) % len(O)
            j = int(abs(jit(k, n, 32)) * 10) % len(COLS)
            O[i][j] = pull_in(O[i][j], 1.6 + abs(jit(k, n, 33)) * 1.2)
    holes = set()
    for n in range(dmg - 1 if dmg > 1 else 0):
        t = int(abs(jit(k, n, 41)) * 10) % len(tiers)
        holes.add((2 * t, int(abs(jit(k, n, 42)) * 10) % (len(COLS) - 1)))
    F = []
    for i in range(len(O) - 1):
        for j in range(len(COLS) - 1):
            if i % 2 == 0:
                if (i, j) in holes:
                    continue
                F += poly(bone, "stone", [O[i][j], O[i][j + 1], O[i + 1][j + 1], O[i + 1][j]], CEN)
            else:
                F += quad_up(bone, "stone", [O[i][j], O[i][j + 1], O[i + 1][j + 1], O[i + 1][j]], up=True)
    F += backing(bone, O, 2.8)
    return F


def seam(bone, k, mat="seam", y0=-9.0, y1=9.0, jag=0.0):
    ys = list(np.linspace(y0, y1, 7))
    F, O = [], []
    for i, y in enumerate(ys):
        w = (0.3 + 1.0 * math.sin(math.pi * (i + 0.5) / 7.0)) if jag == 0 else (0.35 + 0.65 * math.sin(math.pi * (i + 0.5) / 7.0))
        cx = jag * jit(k, i, 5, amp=0.9)
        O.append([spx(cx - w, y, R0 - 1.1), spx(cx, y, R0 - 0.2), spx(cx + w, y, R0 - 1.1)])
    return surface_grid(bone, mat, O)


def crown():
    R = [ring(y, r, -r, r) for y, r in [(21.0, 7.5), (22.3, 7.9), (22.9, 7.0), (23.7, 6.6), (24.1, 4.0), (25.6, 2.8), (26.2, 1.0)]]
    return loft("crown", "stone", R, caps=(False, "bone"), mats=["stone", "stone", "stone", "stone", "bone", "bone"])


def spikes():
    F = []
    for i in range(8):
        a = 45 * i + 22.5
        t = math.radians(a)
        L = [6.2, 4.2, 6.8, 4.6][i % 4] + jit(i, 3, amp=0.5)
        base = V(5.0 * math.sin(t), 23.4, -5.0 * math.cos(t))
        d = V(0.42 * math.sin(t), 1.0, -0.42 * math.cos(t))
        F += spike("spikes", "bone", base, d, L, 1.3, sides=4, rings=2, bend=(0.9 * math.sin(t), 0, -0.9 * math.cos(t)))
    return F


def anat_deform(ry, lean=1.4, rx=6.5, cleft=2.3):
    def f(p):
        x, y, z = p
        t = float(np.clip(-y / ry, 0, 1))
        u = float(np.clip(y / ry, 0, 1))
        k = 1 - 0.58 * t ** 1.5
        x *= k * (1 + 0.16 * u)
        z *= k
        x -= lean * t ** 1.4
        y -= cleft * u ** 1.5 * math.exp(-(x / (0.3 * rx)) ** 2)
        if z < 0:
            z += 0.9 * math.exp(-(x / 1.2) ** 2) * (1 - 0.3 * abs(y) / ry)
        return V(x, y, z)
    return f


def heart_parts(bone, c, radii, segs, rings, atria=True):
    c = np.asarray(c, float)
    ry = radii[1]
    dfm = anat_deform(ry, rx=radii[0], cleft=0.3 * ry)
    F = ellipsoid(bone, "flesh", c, radii, segs=segs, rings=rings, deform=dfm)
    if atria:
        for s in (-1, 1):
            F += ellipsoid(bone, "flesh", c + V(s * radii[0] * 0.62, ry * 0.62, 0.3), (radii[0] * 0.4, ry * 0.32, radii[2] * 0.52),
                           segs=6, rings=2, rot=rot_z(s * -15))
    top = c + V(0, ry * 0.8, 0)
    F += tube(bone, "flesh", [top + V(0.4, -1.0, 0), top + V(0.2, 2.0, 0), top + V(-1.8, 4.0, 0.2), top + V(-3.8, 4.8, 0.2)],
              [radii[0] * 0.21, radii[0] * 0.2, radii[0] * 0.19, radii[0] * 0.16], sides=5, hint=(0, 0, 1), phase=36,
              axis_fn=lambda p: top)
    F += tube(bone, "flesh", [top + V(2.2, -0.8, -1.8), top + V(2.6, 1.6, -2.0), top + V(3.8, 3.2, -1.4)],
              [radii[0] * 0.17, radii[0] * 0.15, radii[0] * 0.13], sides=4, hint=(0, 0, 1), phase=45, axis_fn=lambda p: top)
    return F


def vein_specs():
    return [[(60, 5), (35, -18), (5, -26), (-30, -20), (-52, -6)],
            [(62, 25), (38, 12), (12, 8), (-18, 2), (-50, 0)],
            [(55, -40), (28, -50), (-5, -52), (-35, -38)],
            [(50, 52), (22, 48), (-8, 40)],
            [(35, 30), (10, 30), (-12, 26), (-36, 10)]]


HEART = {1: ((0, 12.5, 0), (5.2, 5.6, 3.4), 8, 3),
         2: ((0, 12.7, -0.5), (6.4, 6.8, 4.6), 10, 5),
         3: ((0, 13.8, -0.5), (7.8, 8.0, 5.4), 12, 6)}


def build(phase, pres):
    F = []
    for k in range(8):
        b = f"plate_{k}"
        if b in pres:
            F += plate(b, k, dmg=(0 if phase == 1 else 3))
        b = f"seam_{k}"
        if b in pres:
            F += seam(b, k)
        b = f"crack_{k}"
        if b in pres:
            F += seam(b, k, "crack", jag=1.0)
        b = f"bowl_{k}"
        if b in pres:
            F += plate(b, k, R0 + 0.4, tiers=(0, 1), ragged=True, dmg=2)
        b = f"bowl_seam_{k}"
        if b in pres:
            F += seam(b, k, y0=-9.0, y1=-2.5)
    if "crown" in pres:
        F += crown()
    if "spikes" in pres:
        F += spikes()
    if "base" in pres:
        F += base_ring()
    if "tendrils" in pres:
        F += tendrils(6, 1)
    c, r, s, rg = HEART[phase]
    if "heart" in pres:
        F += heart_parts("heart", c, r, s, rg, atria=phase > 1)
    if "heart_veins" in pres:
        F += veins("heart_veins", c, r, anat_deform(r[1], rx=r[0], cleft=0.3 * r[1]), vein_specs()[: (4 if phase == 2 else 5)], lift=0.5, rad=0.55)
    if "core" in pres:
        F += ellipsoid("core", "core", (-1.75, 13.5, -4.6), (2.5, 2.5, 1.8), segs=8, rings=3)
    if "chains" in pres:
        F += bone_frame() + side_chains()
    if "shard_0" in pres:
        F += claw("shard_0", "shard")
    if "shard_1" in pres:
        F += claw("shard_1", "shard")
    return F


def ctx(phase):
    c, r, _, _ = HEART[phase]
    return dict(stone=STONE_A, tier=(3.0, 4.4), crack_p=[0.2, 0.22, 0.22], crack_glow=[False, True, True],
                moss_thr=1.06, seam_w=1.3, bone=BONE,
                fl_band=False,
                core_c=(-1.75, 13.5, -4.6), core_r=2.6, ember_thr=0.56)


OPT = dict(id="a", name=NAME, build=build, ctx=ctx, gcol=(255, 150, 70))
