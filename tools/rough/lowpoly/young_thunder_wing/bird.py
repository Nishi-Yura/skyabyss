"""雛の体の部品 (3 案で形の言葉を変えて使う)。色は成体 (lp_thunder_wing/common.py の PAL) の灰青・白・銅・稲妻の白金から。"""
from __future__ import annotations

from yt import *  # noqa

# ---- 成体の色 (灰青の羽・白い腹・銅の冠・黄の嘴・稲妻の青白と金)
BACK = [(36, 42, 62), (48, 56, 80), (62, 72, 100), (78, 90, 120), (96, 110, 140), (116, 130, 160)]
COV = [(50, 58, 84), (66, 76, 104), (84, 96, 126), (104, 118, 148), (126, 140, 170)]
SEC = [(34, 40, 58), (46, 54, 76), (60, 70, 96), (76, 88, 116), (94, 108, 138)]
PRIM = [(26, 30, 46), (36, 42, 62), (48, 56, 80), (62, 72, 100), (78, 90, 120)]
TAILF = [(40, 46, 66), (54, 62, 88), (70, 80, 108), (90, 102, 132), (112, 124, 154)]
BELLY = [(128, 134, 150), (160, 166, 182), (190, 196, 210), (214, 218, 228), (236, 238, 244)]
PLUME = [(90, 100, 128), (120, 132, 162), (156, 168, 196), (196, 206, 226), (230, 236, 248)]
COPPER = [(84, 46, 28), (128, 70, 38), (172, 100, 52), (212, 136, 76), (240, 176, 112)]
BEAK = [(70, 62, 48), (100, 88, 62), (132, 116, 80), (164, 146, 100), (196, 178, 124)]
LEG = [(96, 84, 58), (122, 108, 72), (148, 132, 88), (176, 160, 106)]
TALON = [(24, 24, 30), (38, 38, 46), (56, 56, 66), (80, 80, 92)]
BONE = [(120, 112, 100), (164, 156, 140), (200, 194, 178), (226, 222, 208), (244, 242, 232)]
GLASS = [(150, 196, 240), (190, 226, 255), (226, 244, 255), (250, 254, 255)]
GOLDR = [(210, 150, 40), (240, 186, 70), (255, 218, 120), (255, 240, 180)]
VEINR = [(120, 176, 240), (170, 214, 255), (220, 240, 255), (255, 255, 255)]
EYE = [(10, 10, 16), (20, 20, 28), (32, 32, 42), (44, 44, 56)]


def M(ramp, base=0.55, lit=0.3, noise=0.08, crack=0.0, mn=0, **kw):
    return dict(ramp=ramp, base=base, lit=lit, noise=noise, crack=crack, min=mn, **kw)


def palette(**over):
    mats = {
        "back": M(BACK, 0.5, streak=0.1), "down": M(PLUME, 0.45, 0.32, 0.14), "down2": M(PLUME, 0.62, 0.3, 0.12),
        "cov": M(COV, 0.52, streak=0.12), "sec": M(SEC, 0.5, streak=0.12), "prim": M(PRIM, 0.5, streak=0.15),
        "tailf": M(TAILF, 0.5, streak=0.12), "belly": M(BELLY, 0.55, 0.3, 0.1), "pad": M(BELLY, 0.5, 0.3, 0.06),
        "copper": M(COPPER, 0.55, 0.35, 0.08, 0.1), "beak": M(BEAK, 0.6, 0.3, 0.06), "beak_d": M(BEAK, 0.35, 0.3, 0.06),
        "leg": M(LEG, 0.55), "talon": M(TALON, 0.5), "bone": M(BONE, 0.55, 0.3, 0.1, 0.12), "bone2": M(BONE, 0.38, 0.3, 0.1),
        "eye": M(EYE, 0.5, 0.2, 0.05), "quill": M(GOLDR, 0.6, 0.3, 0.05), "mote": M(GOLDR, 0.65, 0.3, 0.04),
        "spark": M(VEINR, 0.6, 0.3, 0.04), "halo": M(GLASS, 0.62, 0.3, 0.04), "halo_d": M(COPPER, 0.5, 0.35, 0.06),
        "glass": M(GLASS, 0.6, 0.3, 0.04),
    }
    mats.update(over)
    return dict(mats=mats, glow_mats=["mote", "spark", "halo", "quill"])


# ====================================================================== 体

def body_loft(secs, mat="back", cut=0.5, mats=None):
    """体: z に沿う輪切り [(z, 中心 y, 幅, 高さ)] (上面 = y + 高さ/2 は 34 以下にして鞍の板の下へ沈める)。"""
    rings = [rrect(V(0, y, z), V(1, 0, 0), V(0, 1, 0), w, h, cut) for z, y, w, h in secs]
    return rloft("body", mat, rings, mats=mats)


def body_round(secs, mat="down", top=34.0, n=12, mats=None):
    """丸い体: z に沿う楕円の輪切り [(z, 中心 y, 幅, 高さ)]。上は top で平らに切る (鞍の板の下)。"""
    rings = []
    for z, y, w, h in secs:
        pts = []
        for k in range(n):
            a = 2 * math.pi * (k + 0.5) / n
            px, py = w / 2 * math.sin(a), y + h / 2 * math.cos(a)
            pts.append(V(px, min(py, top), z))
        rings.append(pts)
    return rloft("body", mat, rings, mats=mats)


def tufts(bone, mat, spots, length=6.0, r=2.4, sides=3):
    """産毛の房 (閉じた小さな棘)。spots = [(位置, 向き)]。"""
    F = []
    for p, d in spots:
        F += cspike(bone, mat, p, d, length, r, sides=sides, rings=1)
    return F


def flank_tufts(xs_of_z, zs, ys, mat="down2", length=6.0, r=2.6):
    """体の横腹の房 (左右)。xs_of_z(z, y) = その高さの体の半幅。"""
    sp = []
    for z in zs:
        for y in ys:
            x = xs_of_z(z, y) - 1.2
            for s in (1, -1):
                sp.append((V(s * x, y, z), V(s * 1.0, -0.35, 0.35)))
    return tufts("body", mat, sp, length, r)


# ====================================================================== 頭・首・脚・尾

def flank_feathers(xs_of_z, zs, ys, mats=("cov", "back"), length=12.0, w=9.0, drop=4.0):
    """横腹に寝た羽根の列 (左右)。根元は体へ沈め、先は後ろ下へ。"""
    F = []
    k = 0
    for y in ys:
        for z in zs:
            x = xs_of_z(z, y)
            for s in (1, -1):
                F += feather("body", mats[k % len(mats)], V(s * (x - 2.0), y, z), V(s * (x + 0.6), y - drop, z + length), w,
                             t=1.2, up=(s, 0.15, 0), bow=0.8, wtip=2.0)
            k += 1
    return F


def eye_pair(c, r=4.0, dx=10.5, dy=3.0, dz=-9.0, out=0.55):
    F, eyes = [], []
    for s in (1, -1):
        e = c + V(s * dx, dy, dz)
        F += ellipsoid("head", "eye", e, (r, r, r), segs=6, rings=2)
        eyes.append(dict(c=tuple(e), r=r, dir=(s * out, 0.1, -1), a=r * 0.62, b=r * 0.72, tilt=0.0, bone="head"))
    return F, eyes


def neck_tube(path, ra, mat="back", sides=8):
    return ctube("neck", mat, path, ra, sides=sides, hint=(1, 0, 0), phase=22.5)


def tucked_legs(foot_dz=6.0, r=3.0, chunky=False):
    F = []
    for s, side in ((1, "r"), (-1, "l")):
        b = f"leg_{side}"
        p = pv(b) + V(0, 2.0, 0)
        knee = p + V(s * 1.0, -6.0 if not chunky else -8.0, foot_dz * 0.5)
        foot = p + V(s * 1.5, -10.0 if not chunky else -11.0, foot_dz)
        F += ctube(b, "leg", [p, knee, foot], [r, r * 0.85, r * 0.75], sides=5)
        for a in (-28, 0, 28):
            d = rot_y(a) @ V(0, -0.2, -1.0)
            F += cspike(b, "talon" if not chunky else "leg", foot + V(0, -0.5, 0), d, 6.0 if not chunky else 7.0,
                        r * 0.55, sides=4, rings=2, bend=(0, -1.6, 0))
    return F


def fan(bone, base, n, spread, length, w, mats, y_tip=None, up=(0, 1, 0), bow=1.0, wtip=1.2, z_off=0.0):
    """扇に開く羽根 (尾・背もたれ)。spread = 開き (度)。"""
    F = []
    base = np.asarray(base, float)
    for i in range(n):
        a = math.radians(-spread / 2 + spread * i / max(n - 1, 1))
        L = length * (1.0 - 0.18 * abs(i - (n - 1) / 2) / max((n - 1) / 2, 1))
        tip = base + V(math.sin(a) * L, (y_tip if y_tip is not None else 0.0), math.cos(a) * L + z_off)
        root = base + V(math.sin(a) * 2.0, 0, 0)
        F += feather(bone, mats[i % len(mats)], root, tip, w, t=1.4, up=up, wtip=wtip, bow=bow)
    return F


def primaries(bone, roots, tips, w, mats, y=33.0, side=1, t=1.3, bow=1.0):
    F = []
    for i, ((rx, rz), (tx, tz)) in enumerate(zip(roots, tips)):
        F += feather(bone, mats[i % len(mats)], V(side * rx, y, rz), V(side * tx, y + 1.0, tz), w, t=t, bow=bow, wtip=1.0)
    return F


def trailing(bone, xs, z_root, length, w, mats, y=31.5, side=1, sweep=4.0, t=1.3):
    """翼の後ろ縁の羽根 (次列風切・雨覆)。"""
    F = []
    for i, x in enumerate(xs):
        F += feather(bone, mats[i % len(mats)], V(side * x, y, z_root(x)), V(side * (x + sweep), y - 0.8, z_root(x) + length),
                     w, t=t, bow=0.6, wtip=2.0)
    return F
